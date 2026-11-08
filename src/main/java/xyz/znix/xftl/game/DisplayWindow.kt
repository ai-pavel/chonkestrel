package xyz.znix.xftl.game

import xyz.znix.xftl.Constants
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.sys.DisplayMode

/**
 * The display options, from the options window: the sizes the game can be
 * shown at (see [xyz.znix.xftl.sys.GameContainer.displayModes]), one button
 * each. Clicking one changes the game's size and its window straight away.
 */
class DisplayWindow(private val game: InGameState, private val close: () -> Unit) : Window() {
    private val container = game.mainGame.gameContainer
    private val modes: List<DisplayMode> = container.displayModes
    private val titleFont = game.getFont("HL2", 2f)
    private val titleTab = game.getImg("img/map/side_beaconmap.png")
    private val textFont = game.getFont("JustinFont12Bold")
    private val buttonFont = game.getFont("JustinFont12Bold")

    override val size: IPoint = ConstPoint(WIDTH, TOP + (modes.size + 1) * ROW + PADDING)

    private val modeButtons = modes.mapIndexed { i, mode ->
        object : Buttons.BasicButton(
            game, ConstPoint(PADDING, TOP + i * ROW), ConstPoint(WIDTH - 2 * PADDING, ROW - 4),
            mode.label, 4, buttonFont, BUTTON_TEXT_Y, { choose(mode) }
        ) {
            // The size it's at now can't be picked again
            override val disabled: Boolean get() = mode.sameAs(container.currentDisplayMode)
        }
    }

    private val closeButton = Buttons.BasicButton(
        game, ConstPoint(PADDING, TOP + modes.size * ROW + 6), ConstPoint(WIDTH - 2 * PADDING, ROW - 4),
        game.translator["xftl_button_back"], 4, buttonFont, BUTTON_TEXT_Y, close
    )

    init {
        buttons += modeButtons
        buttons += closeButton
    }

    private fun choose(mode: DisplayMode) {
        container.setDisplay(mode)
        // Remember it for next time; the one it started at needn't be, so it
        // still fits if the screen changes
        game.mainGame.profile.displayWindowSize =
            if (mode.recommended) null else mode.windowWidth to mode.windowHeight
    }

    override fun draw(g: Graphics) {
        val x = position.x.f
        val y = position.y.f

        // The game's own window, with its title on a tab, like the options
        // and hotkeys windows
        game.windowRenderer.renderWithTitleTab(
            g, titleTab, titleFont, position.x, position.y, size.x, size.y,
            game.translator["xftl_display_title"]
        )

        textFont.drawString(x + PADDING, y + 58f, game.translator["xftl_display_desc"], Constants.SECTOR_CUTOUT_TEXT)

        for (button in buttons)
            button.draw(g)
    }

    override fun escapePressed() {
        close()
    }

    companion object {
        private const val WIDTH = 420
        private const val PADDING = 16
        private const val TOP = 72
        private const val ROW = 36
        private const val BUTTON_TEXT_Y = 22
    }
}
