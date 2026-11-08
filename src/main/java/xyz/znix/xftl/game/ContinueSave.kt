package xyz.znix.xftl.game

import org.jdom2.Document
import org.jdom2.input.SAXBuilder
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import xyz.znix.xftl.XftlDebug
import xyz.znix.xftl.sys.PlatformSpecific
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The game in progress, saved so it can be continued, as in FTL: it's saved
 * after every jump and when the game's closed, and deleted when the game's
 * lost or won, or a new one is started. The main menu's continue button
 * loads it (see MainMenuState), and when the game skips the main menu, it
 * continues this game if there is one (see MainGame).
 *
 * It isn't used while recording screenshots or videos, which always start
 * a new game, or in the tests.
 *
 * It's in the saves directory, or the directory in XFTL_SAVE_DIR (or
 * xftl.save-dir), to play a separate game without touching that one. The
 * rest (the profile, mods and so on) stays in the saves directory.
 */
object ContinueSave {
    private val directory: Path
        get() = (System.getenv("XFTL_SAVE_DIR") ?: System.getProperty("xftl.save-dir"))
            ?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: PlatformSpecific.INSTANCE.saveGamePath

    val file: Path get() = directory.resolve("continue.xml")

    // Where a save that couldn't be loaded is moved to, so it isn't lost
    private val brokenFile: Path get() = directory.resolve("continue-broken.xml")

    // Not while recording, or in the tests (which would save over the game
    // being played)
    val enabled: Boolean get() = !XftlDebug.recording && !System.getProperty("xftl.tests").toBoolean()

    // Told about a save that failed, as well as it being printed (the
    // automatic playtest counts them, see AutoplayDirector)
    var onSaveError: ((Exception) -> Unit)? = null

    @JvmStatic
    fun save(game: InGameState) {
        if (!enabled || game.isFinished)
            return

        try {
            val doc = game.saveGameState()
            // Write it to another file first, so a crash part way through
            // doesn't leave half a save
            val temp = file.resolveSibling("continue.xml.tmp")
            Files.createDirectories(temp.parent)
            Files.newBufferedWriter(temp, Charsets.UTF_8).use { writer ->
                XMLOutputter(Format.getCompactFormat()).output(doc, writer)
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (ex: Exception) {
            println("Failed to save the game to $file")
            ex.printStackTrace()
            onSaveError?.invoke(ex)
        }
    }

    /**
     * True if there's a saved game to continue (though it might not load).
     */
    @JvmStatic
    fun exists(): Boolean = enabled && Files.isRegularFile(file)

    /**
     * The saved game, or null if there isn't one (or it can't be read).
     */
    @JvmStatic
    fun load(): Document? {
        if (!enabled || !Files.isRegularFile(file))
            return null

        return try {
            Files.newBufferedReader(file, Charsets.UTF_8).use { reader ->
                val builder = SAXBuilder()
                builder.expandEntities = true
                builder.build(reader)
            }
        } catch (ex: Exception) {
            println("Failed to read the saved game in $file")
            ex.printStackTrace()
            discardBroken()
            null
        }
    }

    @JvmStatic
    fun delete() {
        if (!enabled)
            return
        try {
            Files.deleteIfExists(file)
        } catch (ex: Exception) {
            println("Failed to delete the saved game in $file")
            ex.printStackTrace()
        }
    }

    /**
     * Move a save that couldn't be loaded out of the way, keeping it in case
     * it's needed to find out what went wrong.
     */
    @JvmStatic
    fun discardBroken() {
        try {
            Files.move(file, brokenFile, StandardCopyOption.REPLACE_EXISTING)
            println("Moved the saved game that couldn't be loaded to $brokenFile")
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
