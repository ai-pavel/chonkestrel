package xyz.znix.xftl.sys

import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.f
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import kotlin.math.roundToInt

// The layout and colours of the screens shown before the game starts (see
// DatafileSelectState and ScreenTooSmallState): a panel in the middle of
// the window, with a title, text and buttons.

internal const val PANEL_WIDTH = 640
internal const val PADDING = 36
internal const val CONTENT_WIDTH = PANEL_WIDTH - PADDING * 2

internal const val TITLE_HEIGHT = 34
internal const val LINE_HEIGHT = 20
internal const val BASELINE = 15
internal const val GAP = 8
internal const val SECTION_GAP = 24
internal const val BUTTON_HEIGHT = 36

// A dark, spacey theme, with FTL's green for highlights
internal val BACKGROUND = Colour(12, 16, 26)
internal val PANEL = Colour(24, 30, 44)
internal val PANEL_BORDER = Colour(58, 70, 92)
internal val OPTION = Colour(32, 40, 58)
internal val OPTION_HOVER = Colour(40, 54, 70)
internal val ACCENT = Colour(120, 220, 140)
internal val TEXT = Colour(235, 240, 248)
internal val MUTED_TEXT = Colour(160, 172, 192)
internal val FAINT_TEXT = Colour(105, 116, 138)
internal val ERROR = Colour(255, 120, 110)

internal class LauncherRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(px: Int, py: Int): Boolean = px in x until x + width && py in y until y + height
}

/**
 * The fonts, and drawing the parts every screen before the game has.
 */
internal class LauncherStyle(private val context: ResourceContext) {
    /**
     * A badly-converted copy of the Roboto font, since we can't access FTL
     * fonts until we select ftl.dat. Note the font is 1px too high above the
     * baseline, but that doesn't matter.
     */
    val font: SILFontLoader

    // The baked font is too small for titles, and blurry if scaled up, so
    // they're rendered from the TrueType font instead, at the screen's
    // resolution.
    private val titleFont = TrueTypeText("roboto-font/Roboto-Regular.ttf")
    private val titles = HashMap<String, Image>()
    private var titleScale = 0f

    init {
        val fontData = javaClass.classLoader.getResourceAsStream("baked/roboto.font").readAllBytes()
        font = SILFontLoader(context, null, fontData)
    }

    fun wrap(text: String): List<String> = font.wrapString(text, CONTENT_WIDTH)

    /**
     * Clear the window and draw the panel in the middle of it, for
     * [contentHeight] of contents, leaving room for [footerHeight] below it.
     * Returns the panel.
     */
    fun drawPanel(container: GameContainer, g: Graphics, contentHeight: Int, footerHeight: Int): LauncherRect {
        g.clear(BACKGROUND)

        val height = contentHeight + PADDING * 2
        val x = (container.width - PANEL_WIDTH) / 2
        val y = ((container.height - height - footerHeight) / 2).coerceAtLeast(GAP)

        g.colour = PANEL
        g.fillRect(x, y, PANEL_WIDTH, height)
        g.colour = PANEL_BORDER
        g.drawRect(x, y, PANEL_WIDTH - 1, height - 1)
        // An accent along the top
        g.colour = ACCENT
        g.fillRect(x, y, PANEL_WIDTH, 3)

        return LauncherRect(x, y, PANEL_WIDTH, height)
    }

    /**
     * Draw a title, [TITLE_HEIGHT] tall, centred in the panel's contents.
     */
    fun drawTitle(container: GameContainer, x: Int, y: Int, text: String) {
        // Render them again if the window moves to a screen with a
        // different resolution
        val scale = container.pixelScale
        if (scale != titleScale) {
            titles.clear()
            titleScale = scale
        }
        val image = titles.getOrPut(text) { titleFont.render(context, text, TITLE_HEIGHT * scale) }

        // Draw it one image pixel to one screen pixel, lined up with the
        // screen's pixels so it stays sharp
        val width = image.width / scale
        val height = image.height / scale
        val left = snap(x + (CONTENT_WIDTH - width) / 2, scale)
        image.draw(left, snap(y.f, scale), width, height, TEXT)
    }

    /**
     * Draw lines of text, centred in the panel's contents, returning the Y
     * below them.
     */
    fun drawCentredLines(x: Int, y: Int, lines: List<String>, colour: Colour): Int {
        var lineY = y
        for (line in lines) {
            font.drawStringCentred(x.f, (lineY + BASELINE).f, CONTENT_WIDTH.f, line, colour)
            lineY += LINE_HEIGHT
        }
        return lineY
    }

    /**
     * Draw a button with its text in the middle.
     */
    fun drawButton(g: Graphics, rect: LauncherRect, hover: Boolean, text: String) {
        g.colour = if (hover) OPTION_HOVER else OPTION
        g.fillRect(rect.x, rect.y, rect.width, rect.height)
        g.colour = if (hover) ACCENT else PANEL_BORDER
        g.drawRect(rect.x, rect.y, rect.width - 1, rect.height - 1)
        val textY = rect.y + (rect.height + LINE_HEIGHT) / 2 - LINE_HEIGHT + BASELINE
        font.drawStringCentred(rect.x.f, textY.f, rect.width.f, text, if (hover) ACCENT else TEXT)
    }

    private fun snap(value: Float, scale: Float): Float = (value * scale).roundToInt() / scale
}
