package xyz.znix.xftl.sys

import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * The packaged game has no console, so it copies what it prints (including
 * any crash) into a log file, for players to send with bug reports:
 * `%LOCALAPPDATA%\Chonkestrel\log.txt` on Windows, and
 * `~/Library/Logs/Chonkestrel/log.txt` on macOS. Each run replaces the last
 * one's log.
 *
 * The log's somewhere that can be found without the OS-specific code (see
 * PlatformSpecific), in case that's what goes wrong.
 */
object PackagedLog {
    val file: Path?
        get() {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home") ?: return null
            return when {
                os.contains("win") -> (System.getenv("LOCALAPPDATA")?.let { Path.of(it) }
                    ?: Path.of(home, "AppData", "Local")).resolve("Chonkestrel/log.txt")

                os.contains("mac") -> Path.of(home, "Library/Logs/Chonkestrel/log.txt")
                else -> Path.of(home, ".local/state/Chonkestrel/log.txt")
            }
        }

    /**
     * Start copying the output into the log, if this is the packaged game.
     */
    @JvmStatic
    fun start() {
        if (!PackagedGame.isPackaged)
            return
        val path = file ?: return

        val log = try {
            Files.createDirectories(path.parent)
            FileOutputStream(path.toFile())
        } catch (ex: Exception) {
            System.err.println("Failed to open the log file $path: $ex")
            return
        }

        System.setOut(PrintStream(Tee(System.out, log), true))
        System.setErr(PrintStream(Tee(System.err, log), true))
        val version = System.getProperty("xftl.version") ?: ""
        val build = System.getProperty("jpackage.app-version") ?: ""
        println("Chonkestrel $version (build $build), started ${LocalDateTime.now()}")
        println("Java ${System.getProperty("java.version")} on ${System.getProperty("os.name")} " +
                "${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
    }

    /**
     * Writes to both the original stream and the log.
     */
    private class Tee(private val original: OutputStream, private val log: OutputStream) : OutputStream() {
        override fun write(b: Int) {
            original.write(b)
            log.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            original.write(b, off, len)
            log.write(b, off, len)
        }

        override fun flush() {
            original.flush()
            log.flush()
        }
    }
}
