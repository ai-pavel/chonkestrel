package xyz.znix.xftl.sys

import xyz.znix.xftl.game.SaveProfile
import java.nio.file.Path

/**
 * The browser version of the game's platform-specific bits (in place of
 * the game's own PlatformSpecific, which the web build leaves out, see
 * web/build.gradle.kts): its saves go in TeaVM's in-memory file system.
 */
interface PlatformSpecific {
    val saveGamePath: Path

    val saveProfilePath: Path get() = saveGamePath.resolve(SaveProfile.PROFILE_NAME)
    val modsDirectory: Path get() = saveGamePath.resolve("mods")
    val ftlDatPathFile: Path get() = saveGamePath.resolve("ftl-path.txt")
    val xmlCacheDirectory: Path get() = saveGamePath.resolve("mod-xml-cache")

    fun findRunningInstanceDat(): Path?

    companion object {
        @JvmField
        val INSTANCE: PlatformSpecific = WebPlatform()
    }
}

private class WebPlatform : PlatformSpecific {
    override val saveGamePath: Path = Path.of("/saves")

    override fun findRunningInstanceDat(): Path? = null
}
