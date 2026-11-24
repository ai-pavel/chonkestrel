package xyz.znix.xftl.game

import xyz.znix.xftl.Constants
import xyz.znix.xftl.Language
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics

/**
 * The language options, from the options window: a button for each
 * [Language], with its name in its own language. Picking one remembers it in
 * the profile (see [MainGame.setLanguage]): in the main menu, the menu's
 * shown again in it straight away; in a game, it's used once the game goes
 * back to the main menu, or a new one's started, which the window says.
 */
class LanguageWindow(private val game: InGameState, private val close: () -> Unit) : Window() {
    private val main = game.mainGame
    private val titleFont = game.getFont("HL2", 2f)
    private val titleTab = game.getImg("img/map/side_beaconmap.png")
    private val textFont = game.getFont("JustinFont12Bold")
    private val buttonFont = game.getFont("JustinFont12Bold")

    private val languages = Language.entries

    // In a game, the new language waits for the main menu, so the window
    // says so (below the description)
    private val laterLines: List<String> =
        if (game.isMenuScene) emptyList()
        else textFont.wrapString(game.translator["xftl_language_later"], WIDTH - 2 * PADDING)

    private val top = TOP + laterLines.size * LINE

    override val size: IPoint = ConstPoint(WIDTH, top + (languages.size + 1) * ROW + PADDING)

    private val languageButtons = languages.mapIndexed { i, language ->
        object : Buttons.BasicButton(
            game, ConstPoint(PADDING, top + i * ROW), ConstPoint(WIDTH - 2 * PADDING, ROW - 4),
            language.nativeName, 4, buttonFont, BUTTON_TEXT_Y, { main.language = language }
        ) {
            // The one picked can't be picked again
            override val disabled: Boolean get() = main.language == language
        }
    }

    private val closeButton = Buttons.BasicButton(
        game, ConstPoint(PADDING, top + languages.size * ROW + 6), ConstPoint(WIDTH - 2 * PADDING, ROW - 4),
        game.translator["xftl_button_back"], 4, buttonFont, BUTTON_TEXT_Y, close
    )

    init {
        buttons += languageButtons
        buttons += closeButton
    }

    override fun draw(g: Graphics) {
        val x = position.x.f
        val y = position.y.f

        // The game's own window, with its title on a tab, like the display
        // and hotkeys windows
        game.windowRenderer.renderWithTitleTab(
            g, titleTab, titleFont, position.x, position.y, size.x, size.y,
            game.translator["xftl_language_title"]
        )

        textFont.drawString(x + PADDING, y + 58f, game.translator["xftl_language_desc"], Constants.SECTOR_CUTOUT_TEXT)
        for ((i, line) in laterLines.withIndex())
            textFont.drawString(x + PADDING, y + 58f + (i + 1) * LINE, line, Constants.SECTOR_CUTOUT_TEXT)

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
        private const val LINE = 20
        private const val BUTTON_TEXT_Y = 22
    }
}
