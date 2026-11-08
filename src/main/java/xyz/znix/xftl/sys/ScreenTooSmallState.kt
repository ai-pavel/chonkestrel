package xyz.znix.xftl.sys

import xyz.znix.xftl.Translator
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.replaceArg

/**
 * Shown instead of starting the game when the main screen is too small to
 * fit the big ships' interface. It says how big the screen is and how big
 * it needs to be, and the player can only quit.
 *
 * Only for the game's own window size: a window size that's asked for
 * (such as with XFTL_WINDOW_SIZE) is used whatever the screen's size.
 */
class ScreenTooSmallState(private val game: MainGame, private val screens: List<Screen>) : MainGame.GameState() {
    private val context = ResourceContext()

    private lateinit var style: LauncherStyle
    private lateinit var translator: Translator

    private lateinit var title: String
    private lateinit var messageLines: List<String>
    private lateinit var fixLines: List<String>
    private lateinit var quitText: String

    private var quitHovered = false

    override fun init(container: GameContainer) {
        style = LauncherStyle(context)
        translator = Translator("assets/lang/en.xml")

        val main = screens.first()
        title = translator["xftl_screen_small_title"]
        messageLines = style.wrap(
            translator["xftl_screen_small_desc"]
                .replaceArg("${MIN_WIDTH}x$MIN_HEIGHT", 1)
                .replaceArg(main.name, 2)
                .replaceArg(main.sizeText, 3)
        )

        // If another screen is big enough, the game can use that one
        val bigEnough = screens.drop(1).firstOrNull { it.isAtLeast(MIN_WIDTH, MIN_HEIGHT) }
        val fix = when (bigEnough) {
            null -> translator["xftl_screen_small_fix"]
            else -> translator["xftl_screen_small_other"]
                .replaceArg(bigEnough.name, 1)
                .replaceArg(bigEnough.sizeText, 2)
        }
        fixLines = style.wrap(fix)
        quitText = translator["xftl_screen_small_quit"]
    }

    override fun update(container: GameContainer, delta: Float) {
    }

    override fun render(container: GameContainer, g: Graphics) {
        val contentHeight = TITLE_HEIGHT + GAP +
                messageLines.size * LINE_HEIGHT + SECTION_GAP +
                fixLines.size * LINE_HEIGHT + SECTION_GAP +
                BUTTON_HEIGHT
        val panel = style.drawPanel(container, g, contentHeight, 0)

        val x = panel.x + PADDING
        var y = panel.y + PADDING

        style.drawTitle(container, x, y, title)
        y += TITLE_HEIGHT + GAP

        y = style.drawCentredLines(x, y, messageLines, TEXT)
        y += SECTION_GAP
        y = style.drawCentredLines(x, y, fixLines, MUTED_TEXT)
        y += SECTION_GAP

        val quit = LauncherRect(x + (CONTENT_WIDTH - QUIT_WIDTH) / 2, y, QUIT_WIDTH, BUTTON_HEIGHT)
        quitHovered = quit.contains(container.input.mouseX, container.input.mouseY)
        style.drawButton(g, quit, quitHovered, quitText)
    }

    override fun mouseClicked(button: Int, x: Int, y: Int, clickCount: Int) {
        if (button == Input.MOUSE_LEFT_BUTTON && quitHovered)
            game.quitGame()
    }

    override fun shutdown() {
        context.freeAll()
    }

    companion object {
        /**
         * The smallest screen the big ships' interface fits on, such as the
         * Chonkestrel's with three enemy ships.
         */
        const val MIN_WIDTH = 1920
        const val MIN_HEIGHT = 1080

        private const val QUIT_WIDTH = 160

        /**
         * The screens, if the main one is too small for the game, or null if
         * it's big enough (or the window's size was asked for).
         */
        @JvmStatic
        fun screensIfTooSmall(container: GameContainer): List<Screen>? {
            if (!container.isDefaultSize)
                return null
            val screens = container.screens
            val main = screens.firstOrNull() ?: return null
            if (main.isAtLeast(MIN_WIDTH, MIN_HEIGHT))
                return null
            return screens
        }
    }
}
