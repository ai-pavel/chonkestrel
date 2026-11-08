package xyz.znix.xftl.sys

import xyz.znix.xftl.rendering.Cursor
import java.nio.file.Path

interface GameContainer {
    val input: Input
    val width: Int
    val height: Int

    fun exit()

    /**
     * Switch to the given cursor image, or the default image if [cursor]
     * is null.
     */
    fun setCursor(cursor: Cursor?)

    /**
     * How many screen pixels there are to each pixel of the game, such as 2
     * on a Retina display, for drawing things at the screen's resolution.
     */
    val pixelScale: Float get() = 1f

    /**
     * Whether the window is the game's own size (1920x1080), rather than
     * a size that was asked for (such as with XFTL_WINDOW_SIZE).
     */
    val isDefaultSize: Boolean get() = false

    /**
     * The connected screens, the main one first, with their sizes in the
     * same units as the game's (so a Retina screen's size is what it looks
     * like, not its pixels).
     */
    val screens: List<Screen> get() = emptyList()

    /**
     * Called with the files the player drags and drops onto the window.
     */
    var onFilesDropped: ((List<Path>) -> Unit)?

    /**
     * The sizes the game can be shown at on the main screen (see
     * [DisplayMode]), the one it started with first.
     */
    val displayModes: List<DisplayMode> get() = emptyList()

    /**
     * The size the game's shown at now.
     */
    val currentDisplayMode: DisplayMode? get() = null

    /**
     * Change the game's size and its window's, straight away.
     */
    fun setDisplay(mode: DisplayMode) {}
}

/**
 * A size the game can be shown at: the game's own size (what it lays its
 * interface out in, at least 1920x1080: bigger screens scale it up), its
 * window's size on the screen (in the screen's units: points, on a Retina
 * screen), and how many screen pixels that is. [recommended] is the size
 * it started with.
 */
data class DisplayMode(
    val gameWidth: Int, val gameHeight: Int,
    val windowWidth: Int, val windowHeight: Int,
    val pixelWidth: Int, val pixelHeight: Int,
    val retina: Boolean, val recommended: Boolean,
) {
    val label: String
        get() = "${pixelWidth}x$pixelHeight" +
                (if (retina) " (Retina)" else "") +
                (if (recommended) " (Recommended)" else "")

    fun sameAs(other: DisplayMode?): Boolean = other != null && gameWidth == other.gameWidth &&
            gameHeight == other.gameHeight && windowWidth == other.windowWidth && windowHeight == other.windowHeight
}

data class Screen(val name: String, val width: Int, val height: Int) {
    fun isAtLeast(minWidth: Int, minHeight: Int): Boolean = width >= minWidth && height >= minHeight

    val sizeText: String get() = "${width}x$height"
}
