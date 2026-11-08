package xyz.znix.xftl.sys

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * Searches for FTL installations: next to the game (in the directory it's
 * run from, or the one it's installed in), Steam (including its other
 * library folders), GOG, Epic Games, and apps installed on macOS.
 */
object FTLFinder {
    /**
     * An FTL installation's `ftl.dat`, and where it was found (Steam,
     * GOG and so on).
     */
    data class Installation(val path: Path, val source: String) {
        override fun toString(): String = "$source: $path"
    }

    /**
     * Where `ftl.dat` is inside a game directory: in it on Windows, under
     * `data` on Linux (with GOG's under `game`), and in the app's resources
     * on macOS.
     */
    private val DAT_LOCATIONS = listOf(
        "ftl.dat",
        "data/ftl.dat",
        "game/data/ftl.dat",
        "Contents/Resources/ftl.dat",
        "FTL.app/Contents/Resources/ftl.dat",
    )

    /**
     * Where Steam itself might be installed, in the home directory. On
     * Linux, that's different for older installs, Flatpak and Snap.
     */
    private val STEAM_HOME_DIRS = listOf(
        ".local/share/Steam",
        ".steam/steam",
        ".steam/root",
        ".var/app/com.valvesoftware.Steam/.local/share/Steam",
        "snap/steam/common/.local/share/Steam",
        "Library/Application Support/Steam",
    )

    private const val STEAM_GAME_DIR = "steamapps/common/FTL Faster Than Light"

    /**
     * The names GOG installs FTL under.
     */
    private val GOG_GAME_DIRS = listOf("FTL Advanced Edition", "FTL Faster Than Light")

    /**
     * A library folder in Steam's `libraryfolders.vdf`. Newer files have
     * `"path" "..."` entries, older ones `"1" "..."`.
     */
    private val VDF_REGEX = Pattern.compile("\\s*\"(?:path|\\d+)\"\\s+\"([^\"]*)\"\\s*")

    private val DAT_MAGIC = "PKG\n".toByteArray(Charsets.US_ASCII)

    fun findInstallations(): List<Installation> {
        val results = LinkedHashMap<Path, Installation>()
        fun add(source: String, gameDirs: List<Path>) {
            for (dir in gameDirs) {
                val dat = findDatIn(dir) ?: continue
                val key = try {
                    dat.toRealPath()
                } catch (ex: IOException) {
                    dat
                }
                results.putIfAbsent(key, Installation(dat, source))
            }
        }

        add("This folder", localGameDirs())
        add("Steam", steamGameDirs())
        add("GOG", gogGameDirs())
        add("Epic Games", epicGameDirs())
        add("Installed app", appGameDirs())

        return results.values.toList()
    }

    /**
     * The ftl.dat next to the game (see [localGameDirs]), if there is one.
     */
    fun findNextToGame(): Path? {
        return localGameDirs().firstNotNullOfOrNull { findDatIn(it) }
    }

    fun findRunningInstance(): Path? {
        return PlatformSpecific.INSTANCE.findRunningInstanceDat()
    }

    /**
     * Find the `ftl.dat` the player meant, when they pick or drop a file:
     * `ftl.dat` itself, the game's executable, the game's directory, or
     * the FTL app on macOS. Returns null if it isn't (or doesn't lead to) a
     * valid `ftl.dat`.
     */
    fun resolveDatafile(picked: Path): Path? {
        if (Files.isRegularFile(picked)) {
            if (isFtlDat(picked))
                return picked
            // The executable, or some other file in the game's directory
            return picked.parent?.let { findDatIn(it) }
        }
        return findDatIn(picked)
    }

    /**
     * Whether this is a valid FTL datafile (it starts with `PKG\n`).
     */
    fun isFtlDat(path: Path): Boolean {
        if (!Files.isRegularFile(path))
            return false
        return try {
            RandomAccessFile(path.toFile(), "r").use { file ->
                val header = ByteArray(DAT_MAGIC.size)
                file.length() > header.size && file.read(header) == header.size && header.contentEquals(DAT_MAGIC)
            }
        } catch (ex: IOException) {
            false
        }
    }

    private fun findDatIn(gameDir: Path): Path? {
        if (!Files.isDirectory(gameDir))
            return null
        return DAT_LOCATIONS.map { gameDir.resolve(it) }.firstOrNull { isFtlDat(it) }
    }

    /**
     * The directory the game is run from, and the one its jar is in, for
     * when the player puts ftl.dat (or the game) next to it.
     */
    private fun localGameDirs(): List<Path> {
        val dirs = mutableListOf<Path>()
        path(System.getProperty("user.dir") ?: "")?.let { dirs.add(it.toAbsolutePath()) }

        val code = try {
            Path.of(FTLFinder::class.java.protectionDomain.codeSource.location.toURI())
        } catch (ex: Exception) {
            null
        }
        // The jar's directory, or the classes directory when run from a build
        val codeDir = when {
            code == null -> null
            Files.isRegularFile(code) -> code.parent
            else -> code
        }
        codeDir?.let { dirs.add(it) }

        // The packaged game's jar is in app/: on Windows, next to the
        // executable, and on macOS, inside the app, which ftl.dat would be
        // next to
        if (codeDir != null && PackagedGame.isPackaged) {
            codeDir.parent?.let { dirs.add(it) }
            generateSequence(codeDir) { it.parent }
                .firstOrNull { it.fileName?.toString()?.endsWith(".app") == true }
                ?.parent?.let { dirs.add(it) }
        }

        return dirs.distinct()
    }

    private fun steamGameDirs(): List<Path> {
        val steamDirs = ArrayList<Path>()
        steamDirs += homeDirs(STEAM_HOME_DIRS)
        steamDirs += programFilesDirs().map { it.resolve("Steam") }

        // Steam stores a list of its library folders (which may be on
        // other drives) in VDF files
        val libraries = LinkedHashSet<Path>()
        for (steamDir in steamDirs) {
            if (!Files.isDirectory(steamDir))
                continue
            libraries.add(steamDir)
            for (vdf in listOf("steamapps/libraryfolders.vdf", "config/libraryfolders.vdf")) {
                libraries.addAll(searchSteamVDF(steamDir.resolve(vdf)))
            }
        }

        // Libraries on other Windows drives, in case the VDF files are
        // somewhere we don't know about
        libraries.addAll(driveRoots().map { it.resolve("SteamLibrary") })

        return libraries.map { it.resolve(STEAM_GAME_DIR) }
    }

    private fun searchSteamVDF(vdf: Path): List<Path> {
        if (!Files.isRegularFile(vdf))
            return emptyList()

        val lines = try {
            Files.readAllLines(vdf)
        } catch (ex: IOException) {
            println("Exception while searching Steam VDF file for FTL installation: " + ex.localizedMessage)
            return emptyList()
        }

        return lines.mapNotNull { line ->
            val matcher = VDF_REGEX.matcher(line)
            if (!matcher.matches())
                return@mapNotNull null
            // VDF escapes backslashes, as in "C:\\Program Files (x86)\\Steam"
            path(matcher.group(1).replace("\\\\", "\\"))
        }
    }

    private fun gogGameDirs(): List<Path> {
        val parents = ArrayList<Path>()
        parents += driveRoots().map { it.resolve("GOG Games") }
        parents += programFilesDirs().flatMap {
            listOf(it.resolve("GOG Galaxy/Games"), it.resolve("GOG.com"), it.resolve("GOG Games"))
        }
        // The GOG installer on Linux, and the Heroic launcher
        parents += homeDirs(listOf("GOG Games", "Games", "Games/Heroic", "Games/gog"))

        return parents.flatMap { parent -> GOG_GAME_DIRS.map { parent.resolve(it) } }
    }

    private fun epicGameDirs(): List<Path> {
        val parents = ArrayList<Path>()
        parents += programFilesDirs().map { it.resolve("Epic Games") }
        parents += driveRoots().map { it.resolve("Epic Games") }
        return parents.map { it.resolve("FasterThanLight") }
    }

    /**
     * FTL apps installed on macOS, from GOG, Humble or anywhere else.
     */
    private fun appGameDirs(): List<Path> {
        val appDirs = listOf(Path.of("/Applications")) + homeDirs(listOf("Applications"))
        return appDirs.flatMap { dir ->
            if (!Files.isDirectory(dir))
                return@flatMap emptyList()
            try {
                Files.list(dir).use { stream ->
                    stream.filter {
                        val name = it.fileName.toString()
                        name.endsWith(".app") && name.contains("FTL", ignoreCase = true)
                    }.toList()
                }
            } catch (ex: IOException) {
                emptyList()
            }
        }
    }

    private fun homeDirs(relative: List<String>): List<Path> {
        val home = System.getProperty("user.home") ?: return emptyList()
        return relative.mapNotNull { path(home)?.resolve(it) }
    }

    private fun programFilesDirs(): List<Path> {
        return listOf("ProgramFiles(x86)", "ProgramFiles", "ProgramW6432")
            .mapNotNull { System.getenv(it) }
            .distinct()
            .mapNotNull { path(it) }
    }

    /**
     * The drives on Windows (C:\, D:\ and so on). Elsewhere, there's just
     * the root directory, which never has games in it.
     */
    private fun driveRoots(): List<Path> {
        if (File.separatorChar != '\\')
            return emptyList()
        return File.listRoots().map { it.toPath() }
    }

    private fun path(raw: String): Path? {
        return try {
            Path.of(raw)
        } catch (ex: InvalidPathException) {
            null
        }
    }
}

// Put this here to make testing this a bit more convenient
private fun main() {
    println("TESTING: Searching for FTL installations")
    val result = FTLFinder.findInstallations()
    println(result)
    println("Running instance:")
    println(FTLFinder.findRunningInstance())
}
