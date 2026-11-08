package xyz.znix.xftl

/**
 * Debug options, for testing.
 *
 * Set the XFTL_DEBUG environment variable (or the xftl.debug system
 * property) to start new games with everything maxed out: every system the
 * player's ship has room for, fully upgraded, a reactor of 80 (short of the
 * biggest, so the backup batteries can be tested),
 * lots of resources, and the ship's debug loadout (see [xyz.znix.xftl.game.ShipBlueprint]).
 * Everything starts powered, including the weapons and drones. Set it to 2
 * for the same, but with the systems and the reactor at the ship's starting
 * levels (see [starterLevels]).
 *
 * With debug mode on, set XFTL_RANDOMIZE (or xftl.randomize) to replace the
 * debug loadout's weapons and drones with different random ones, as many as
 * there are slots for (a triple and a double one of each kind: beam, bomb,
 * laser and missile, see doc/game/weapon-sizes.md), fill the cargo hold with more
 * weapons, and fill the augment slots with the Weapon Pre-Igniter, a Zoltan
 * shield and different random augments.
 * It never picks the bombs that only target your own ship (see
 * Ship.RANDOMIZE_EXCLUDED).
 *
 * Separately, set the XFTL_ZOLTAN environment variable (or the xftl.zoltan
 * system property) to fill all the player's augment slots with Zoltan shields.
 *
 * Set XFTL_SKIP_MENU (or xftl.skip-menu) to skip the main menu (see
 * [xyz.znix.xftl.game.MainMenuState]): continue the saved game if there is
 * one, or start a new game straight away with the ship the hangar would pick
 * by default (see [BlueprintManager.defaultPlayerShip]). The pause and game
 * over menus then don't have the main menu and hangar buttons.
 *
 * Set XFTL_SHOW_HANGAR (or xftl.show-hangar) to show the hangar buttons in
 * the pause and game over menus, which are hidden otherwise (see [showHangar]).
 *
 * Set XFTL_SCREENSHOTS (or xftl.screenshots) to a directory to run the
 * screenshot generator: it starts a game in debug mode with the default ship,
 * takes screenshots of the main features into that directory, and quits (see
 * [xyz.znix.xftl.devutil.ScreenshotDirector]). Set XFTL_SCREENSHOTS_SIZE (or
 * xftl.screenshots-size) to a size like 1920x1080 for the window, and
 * XFTL_SCREENSHOTS_SET (or xftl.screenshots-set) to `shape`, `hybrid` or
 * `mixed` to take screenshots of the long and wide enemy ships, the hybrid
 * ships or the long and wide hybrids instead (see doc/game/ship-shapes.md and
 * doc/game/hybrid-ships.md).
 *
 * Set XFTL_AUTOPLAY (or xftl.autoplay) for the game to play itself, faster
 * than real time, to find crashes (see
 * [xyz.znix.xftl.devutil.AutoplayDirector]): XFTL_AUTOPLAY_SPEED sets how
 * many times as fast (8 by default), XFTL_AUTOPLAY_SECTORS and
 * XFTL_AUTOPLAY_MINUTES when it stops, and XFTL_AUTOPLAY_DIR where its
 * report goes (dist/playtest).
 *
 * Set XFTL_VIDEO (or xftl.video) to a directory to record the trailer into,
 * and XFTL_VIDEO_SIZE (or xftl.video-size) for the window size (see
 * [xyz.znix.xftl.devutil.VideoDirector]).
 */
object XftlDebug {
    /**
     * The directory to save screenshots into, if the screenshot generator is on.
     */
    val screenshotDir: String? = (System.getenv("XFTL_SCREENSHOTS") ?: System.getProperty("xftl.screenshots"))
        ?.takeIf { it.isNotBlank() }

    val screenshots: Boolean get() = screenshotDir != null

    /**
     * Which screenshots to take: the site's (by default), `shape`, the long
     * and wide enemy ships, `hybrid`, the hybrid ships, or `mixed`, the
     * long and wide hybrids.
     */
    val screenshotSet: String = (System.getenv("XFTL_SCREENSHOTS_SET") ?: System.getProperty("xftl.screenshots-set"))
        ?.takeIf { it.isNotBlank() }?.trim()?.lowercase() ?: "site"

    /**
     * The directory to record the trailer into, if the video recorder is on.
     */
    val videoDirectory: String? = (System.getenv("XFTL_VIDEO") ?: System.getProperty("xftl.video"))
        ?.takeIf { it.isNotBlank() }

    val video: Boolean get() = videoDirectory != null

    /**
     * The PNG to render the default ship into, as it looks in the hangar,
     * for the game's icon (see [xyz.znix.xftl.devutil.IconDirector]).
     */
    val iconFile: String? = (System.getenv("XFTL_ICON") ?: System.getProperty("xftl.icon"))
        ?.takeIf { it.isNotBlank() }

    /**
     * True if the game's being recorded, by the screenshot generator or the
     * video recorder, so the debug-only UI and tooltips are hidden.
     */
    val recording: Boolean get() = screenshots || video

    /**
     * The window size for the screenshot generator, or null for the usual one.
     */
    val screenshotSize: Pair<Int, Int>? =
        (System.getenv("XFTL_SCREENSHOTS_SIZE") ?: System.getProperty("xftl.screenshots-size"))
            ?.let { parseSize(it) }

    /**
     * The window size for the video recorder, or null for the usual one.
     */
    val videoSize: Pair<Int, Int>? =
        (System.getenv("XFTL_VIDEO_SIZE") ?: System.getProperty("xftl.video-size"))
            ?.let { parseSize(it) }

    /**
     * The fixed window size to use while recording, if any.
     */
    val recordingSize: Pair<Int, Int>? get() = if (video) videoSize else screenshotSize

    // This is only a var so tests can turn it on.
    var enabled: Boolean = isSet(System.getenv("XFTL_DEBUG")) || isSet(System.getProperty("xftl.debug")) || recording

    /**
     * With XFTL_DEBUG=2 (or xftl.debug=2), debug mode still gives the
     * player's ship every system there's room for, its debug loadout and
     * lots of resources, but each system starts at the level the ship's
     * blueprint gives it, and the reactor at the ship's own starting power,
     * rather than everything maxed out.
     */
    val starterLevels: Boolean = (System.getenv("XFTL_DEBUG") ?: System.getProperty("xftl.debug"))?.trim() == "2"

    /**
     * The video recorder's trailer (see [xyz.znix.xftl.devutil.Trailer])
     * starts with the player's ship as a new game has it, rather than with
     * debug mode's loadout, and builds it up itself.
     */
    val plainPlayerShip: Boolean get() = video

    // This is only a var so tests can turn it on. The video recorder always
    // uses random weapons, drones and augments, to show off more of them.
    var randomize: Boolean = isSet(System.getenv("XFTL_RANDOMIZE")) || isSet(System.getProperty("xftl.randomize")) || video

    // This is only a var so tests can turn it on.
    var zoltan: Boolean = isSet(System.getenv("XFTL_ZOLTAN")) || isSet(System.getProperty("xftl.zoltan"))

    /**
     * Shows the enemy ships' rooms and crew even where the player's sensors
     * couldn't (in a nebula). Only the bigger ships' screenshots turn it on.
     */
    var showEnemyCrew: Boolean = false

    /**
     * Where the mouse is, while recording screenshots (in the game's
     * coordinates), to show something it hovers over, such as a store's info
     * panel (see BugProofs); null, as usual, for nowhere.
     */
    var recordingMouse: xyz.znix.xftl.math.IPoint? = null

    /**
     * A caption drawn over the screenshot, saying what it shows (see
     * BugProofs); null for none.
     */
    var recordingCaption: String? = null

    /**
     * Shows every environment's warning at once (a sun's, a pulsar's and a
     * nebula's), not just when a flare or pulse is coming. Only the bigger
     * ships' screenshots turn it on.
     */
    var showAllHazardWarnings: Boolean = false

    /**
     * The automatic playtest (XFTL_AUTOPLAY, or xftl.autoplay): the game plays
     * itself, faster than real time, to find crashes (see
     * [xyz.znix.xftl.devutil.AutoplayDirector]).
     */
    val autoplay: Boolean = isSet(System.getenv("XFTL_AUTOPLAY")) || isSet(System.getProperty("xftl.autoplay"))

    // The screenshots, videos and the automatic playtest always skip it
    val skipMenu: Boolean = isSet(System.getenv("XFTL_SKIP_MENU")) || isSet(System.getProperty("xftl.skip-menu")) ||
            recording || autoplay

    /**
     * XFTL_SHOW_HANGAR (or xftl.show-hangar) shows the hangar buttons in the
     * pause and game over menus. They're hidden otherwise, as the game's
     * played from the main menu, with the Chonkestrel. XFTL_SKIP_MENU hides
     * them anyway.
     */
    val showHangar: Boolean =
        (isSet(System.getenv("XFTL_SHOW_HANGAR")) || isSet(System.getProperty("xftl.show-hangar"))) && !skipMenu

    const val RESOURCE_COUNT = 99
    const val SCRAP = 5000

    // Debug mode's reactor: short of the biggest (100), so the backup
    // batteries (which can't go past it) can be tested
    const val REACTOR_POWER = 80
    // The list of the hybrid scouts and bombers, for the screenshots and
    // the trailer (see doc/game/hybrid-ships.md)
    const val SCOUT_SHIP = "XFTL_HYBRIDS"

    /**
     * Parse a size like "1920x1080", or return null if it isn't one.
     */
    fun parseSize(value: String): Pair<Int, Int>? {
        val parts = value.trim().lowercase().split("x")
        if (parts.size != 2)
            return null
        val width = parts[0].toIntOrNull() ?: return null
        val height = parts[1].toIntOrNull() ?: return null
        if (width <= 0 || height <= 0)
            return null
        return Pair(width, height)
    }

    private fun isSet(value: String?): Boolean {
        if (value == null)
            return false
        return value.isNotBlank() && value != "0" && !value.equals("false", ignoreCase = true)
    }
}
