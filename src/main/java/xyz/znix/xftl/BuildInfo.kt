package xyz.znix.xftl

import java.util.Properties

/**
 * The game's version and when it was built, which the build writes into the
 * jar (see the buildInfo task in build.gradle.kts).
 */
object BuildInfo {
    private val properties = Properties().apply {
        BuildInfo::class.java.getResourceAsStream("/xftl-build.properties")?.use { load(it) }
    }

    val version: String = properties.getProperty("version") ?: "dev"
    val date: String? = properties.getProperty("date")

    /**
     * The version and build date, as shown in the corner of the screen.
     */
    val label: String get() = if (date != null) "v$version  $date" else "v$version"
}
