package xyz.znix.xftl.game

import xyz.znix.xftl.Constants
import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.crew.CrewNameManager
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.sys.Input
import kotlin.math.roundToInt

/**
 * The main menu's new game window: the difficulty, and the crew names
 * preset (see [CrewNameManager]), in the game's own window with its title on
 * a tab, like the options windows.
 *
 * There's a fourth difficulty's button after hard, which can't be picked
 * yet. The presets are in three groups, with a gap between them: the game's
 * own names (the default), a random preset (picked when the game starts)
 * and all of them at once; then the countries' and languages' names; then
 * the characters' names, from films, series and games.
 *
 * Start calls [onStart] with what was picked, and back (or escape) [close].
 */
class NewGameWindow(
    private val game: InGameState,
    private val onStart: (Difficulty, String) -> Unit,
    private val close: () -> Unit
) : Window() {
    private val titleFont = game.getFont("HL2", 2f)
    private val titleTab = game.getImg("img/map/side_beaconmap.png")
    private val labelFont = game.getFont("JustinFont12Bold")
    private val buttonFont = game.getFont("JustinFont12Bold")
    private val bigButtonFont = game.getFont("HL2", 2f)

    private var difficulty = Difficulty.NORMAL
    private var namePreset = CrewNameManager.DEFAULT_PRESET

    // The difficulties, and the one to come (null), which can't be picked yet
    private val difficulties: List<Difficulty?> = Difficulty.entries + null

    private val presetGroups = listOf(
        listOf(CrewNameManager.DEFAULT_PRESET, RANDOM_PRESET, CrewNameManager.ALL_PRESET),
        CrewNameManager.ETHNIC_PRESETS,
        CrewNameManager.THEMED_PRESETS
    )

    private val presetsTop = DIFFICULTY_TOP + ROW + SECTION_GAP

    // Where each group of presets starts, and where the last one ends
    private val groupTops: List<Int>
    private val bottomTop: Int

    init {
        var y = presetsTop
        groupTops = presetGroups.map { group ->
            val top = y
            y += (group.size + PRESET_COLUMNS - 1) / PRESET_COLUMNS * ROW + GROUP_GAP
            top
        }
        bottomTop = y - GROUP_GAP + SECTION_GAP - ROW_GAP
    }

    override val size: IPoint = ConstPoint(WIDTH, bottomTop + BIG_BUTTON_HEIGHT + PADDING)

    init {
        val difficultyWidth = columnWidth(difficulties.size)
        for ((i, choice) in difficulties.withIndex()) {
            val label = game.translator[choice?.startButtonKey ?: FOURTH_DIFFICULTY_KEY]
            buttons += ChoiceButton(
                ConstPoint(PADDING + i * (difficultyWidth + COLUMN_GAP), DIFFICULTY_TOP),
                ConstPoint(difficultyWidth, ROW - ROW_GAP),
                label, buttonFont,
                selected = { choice != null && choice == difficulty },
                enabled = choice != null
            ) { difficulty = choice!! }
        }

        val presetWidth = columnWidth(PRESET_COLUMNS)
        for ((group, groupTop) in presetGroups.zip(groupTops)) {
            for ((i, preset) in group.withIndex()) {
                val column = i % PRESET_COLUMNS
                val row = i / PRESET_COLUMNS
                buttons += ChoiceButton(
                    ConstPoint(PADDING + column * (presetWidth + COLUMN_GAP), groupTop + row * ROW),
                    ConstPoint(presetWidth, ROW - ROW_GAP),
                    game.translator["xftl_names_$preset"], buttonFont,
                    selected = { preset == namePreset }
                ) { namePreset = preset }
            }
        }

        val bigSize = ConstPoint(BIG_BUTTON_WIDTH, BIG_BUTTON_HEIGHT)
        buttons += ChoiceButton(
            ConstPoint(PADDING, bottomTop), bigSize,
            game.translator["xftl_button_back"], bigButtonFont,
            selected = { false }, bordered = true, pick = close
        )
        buttons += ChoiceButton(
            ConstPoint(WIDTH - PADDING - BIG_BUTTON_WIDTH, bottomTop), bigSize,
            game.translator["start_button"], bigButtonFont,
            selected = { false }, bordered = true
        ) {
            // A random preset is picked now, so the game keeps that one
            val preset = if (namePreset == RANDOM_PRESET) CrewNameManager.PRESETS.random() else namePreset
            onStart(difficulty, preset)
        }
    }

    // The width of each of [columns] buttons side by side across the window
    private fun columnWidth(columns: Int): Int = (WIDTH - 2 * PADDING - (columns - 1) * COLUMN_GAP) / columns

    override fun draw(g: Graphics) {
        val x = position.x.f
        val y = position.y.f

        game.windowRenderer.renderWithTitleTab(
            g, titleTab, titleFont, position.x, position.y, size.x, size.y,
            game.translator["mainmenu_newgame"]
        )

        labelFont.drawString(x + PADDING, y + DIFFICULTY_TOP - LABEL_GAP, game.translator["menu_status_difficulty"],
            Constants.SECTOR_CUTOUT_TEXT)
        labelFont.drawString(x + PADDING, y + presetsTop - LABEL_GAP, game.translator["xftl_new_game_names"],
            Constants.SECTOR_CUTOUT_TEXT)

        for (button in buttons)
            button.draw(g)
    }

    override fun escapePressed() {
        close()
    }

    /**
     * One of a group of choices, like the game's other buttons: lit up while
     * it's the one picked (or hovered), and greyed out if it can't be picked.
     * A [bordered] one (back and start) has a light outline around it, with
     * a dark gap, so it stands out from the choices.
     */
    private inner class ChoiceButton(
        pos: IPoint, size: IPoint,
        private val label: String,
        private val font: SILFontLoader,
        private val selected: () -> Boolean,
        private val enabled: Boolean = true,
        private val bordered: Boolean = false,
        private val pick: () -> Unit
    ) : Button(game, pos, size) {
        override val disabled: Boolean get() = !enabled

        // The text's baseline, so it's in the middle of the button: the
        // font's text is as tall as its top above the baseline, plus the
        // offset some fonts (like HL2) have below it, as a label lays it out
        private val textY: Int = ((size.y - (font.baselineToTop + font.trueBaselineOffset) * font.scale) / 2 +
                font.baselineToTop * font.scale).roundToInt()

        override fun draw(g: Graphics) {
            val colour = when {
                disabled -> Constants.JUMP_DISABLED
                selected() || hovered -> Constants.UI_BUTTON_HOVER
                else -> Constants.SECTOR_CUTOUT_TEXT
            }

            var inset = 0
            if (bordered) {
                g.colour = colour
                Buttons.drawRounded(g, pos.x, pos.y, size.x, size.y, 4)
                g.colour = Constants.JUMP_DISABLED_TEXT
                Buttons.drawRounded(g, pos.x + BORDER, pos.y + BORDER, size.x - 2 * BORDER, size.y - 2 * BORDER, 3)
                inset = 2 * BORDER
            }
            g.colour = colour
            Buttons.drawRounded(g, pos.x + inset, pos.y + inset, size.x - 2 * inset, size.y - 2 * inset, 4 - inset / 2)

            val textX = (size.x - font.getWidth(label)) / 2f
            font.drawString(pos.x + textX, pos.y + textY.f, label, Constants.JUMP_DISABLED_TEXT)
        }

        override fun click(button: Int) {
            // A disabled button still gets clicks
            if (enabled && button == Input.MOUSE_LEFT_BUTTON)
                pick()
        }
    }

    companion object {
        // The fourth difficulty's name, for its button, which can't be picked yet
        private const val FOURTH_DIFFICULTY_KEY = "xftl_difficulty_fourth"

        // Picks one of the presets at random when the game starts
        private const val RANDOM_PRESET = "random"

        private const val WIDTH = 560
        private const val PADDING = 20
        private const val PRESET_COLUMNS = 3

        // Each row of buttons, including the gap under it
        private const val ROW = 36
        private const val ROW_GAP = 6
        private const val COLUMN_GAP = 8

        // Where the difficulties start (below the title tab), the room
        // between the sections (for each one's label), and how far above
        // its buttons a label's baseline is
        private const val DIFFICULTY_TOP = 80
        private const val SECTION_GAP = 34

        // The room between the groups of presets
        private const val GROUP_GAP = 14
        private const val LABEL_GAP = 10

        // Back and start, at the bottom
        private const val BIG_BUTTON_WIDTH = 140
        private const val BIG_BUTTON_HEIGHT = 40

        // The width of a bordered button's outline, and the gap inside it
        private const val BORDER = 2
    }
}
