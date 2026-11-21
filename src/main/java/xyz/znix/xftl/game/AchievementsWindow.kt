package xyz.znix.xftl.game

import xyz.znix.xftl.Constants
import xyz.znix.xftl.LazyShipBlueprint
import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.StandardTooltip
import xyz.znix.xftl.sys.Input
import kotlin.math.roundToInt

/**
 * Every ship's achievements (the ones XFTL tracks, see AchievementTracker:
 * the mod's six for each of its ships), two ships to a row, in the
 * hangar's order (the default ship first, then the others as the mod's
 * achievements list them, which is the hangar's order, so each race's A
 * is beside its B): each ship's hangar miniature, its name, how many of its
 * achievements are unlocked, and their icons, dimmed and locked until
 * they're unlocked. Hovering over an icon shows its name, description, the
 * best progress in any game (for one with a number to reach) and the
 * difficulty it was unlocked on. It scrolls, if there are too many ships
 * to fit.
 *
 * It opens from the main menu and the pause menu. Back (or escape) calls
 * [close].
 */
class AchievementsWindow(private val game: InGameState, private val close: () -> Unit) : Window() {
    private val titleFont = game.getFont("HL2", 2f)
    private val titleTab = game.getImg("img/map/side_beaconmap.png")
    private val nameFont = game.getFont("JustinFont12Bold")
    private val textFont = game.getFont("JustinFont11Bold")
    private val bigButtonFont = game.getFont("HL2", 2f)
    private val lockIcon = game.getImg("img/customizeUI/box_lock_on.png")

    private val profile: SaveProfile = game.mainGame.profile

    /** A ship with achievements: its blueprint (if the game has it), name and achievements. */
    private class ShipRow(val name: String, val blueprint: LazyShipBlueprint?, val achievements: List<Achievement>)

    private val ships: List<ShipRow>

    override val size: IPoint = ConstPoint(WIDTH, HEIGHT)

    init {
        val table: Achievement.AchievementTable? = game.content.achievements
        ships = (table?.trackedByShip() ?: emptyMap()).map { (name, achievements) ->
            ShipRow(name, game.blueprintManager.getOrNull(name) as? LazyShipBlueprint, achievements)
        }.sortedByDescending { it.blueprint?.isDefaultShip == true }

        buttons += BackButton(
            ConstPoint(PADDING, HEIGHT - PADDING - BIG_BUTTON_HEIGHT),
            ConstPoint(BIG_BUTTON_WIDTH, BIG_BUTTON_HEIGHT)
        )
    }

    // The visible part of the list, on the screen
    private val listTop: Int get() = position.y + LIST_TOP
    private val listBottom: Int get() = position.y + HEIGHT - PADDING - BIG_BUTTON_HEIGHT - 10

    // How far the list is scrolled down, in pixels, and how tall it is
    private var scroll = 0f
    private val contentHeight: Int get() = (ships.size + 1) / 2 * ROW
    private val maxScroll: Int get() = (contentHeight - (listBottom - listTop)).coerceAtLeast(0)

    private var mouseX = 0
    private var mouseY = 0

    override fun draw(g: Graphics) {
        val unlocked = ships.sumOf { row -> row.achievements.count { profile.getAchievement(it) != null } }
        val total = ships.sumOf { it.achievements.size }
        game.windowRenderer.renderWithTitleTab(
            g, titleTab, titleFont, position.x, position.y, size.x, size.y,
            "${game.translator["xftl_ach_title"]} $unlocked/$total"
        )

        for (button in buttons)
            button.draw(g)

        if (ships.isEmpty()) {
            nameFont.drawString(position.x + PADDING.f, listTop + 20f, game.translator["xftl_ach_none"], Constants.SECTOR_CUTOUT_TEXT)
            return
        }

        var hovered: Achievement? = null
        for ((index, row) in ships.withIndex()) {
            val x = position.x + PADDING + (index % 2) * (CELL_WIDTH + CELL_GAP)
            val y = listTop + (index / 2) * ROW - scroll.roundToInt()
            // (only those wholly in view, as there's no clipping)
            if (y < listTop || y + CELL_HEIGHT > listBottom)
                continue
            drawShip(g, row, x, y)?.let { hovered = it }
        }

        // The scrollbar, if it doesn't all fit
        val view = listBottom - listTop
        if (contentHeight > view) {
            val barX = position.x + WIDTH - PADDING - SCROLLBAR_WIDTH
            g.colour = Constants.JUMP_DISABLED
            g.fillRect(barX, listTop, SCROLLBAR_WIDTH, view)
            val thumb = (view.toFloat() * view / contentHeight).toInt().coerceAtLeast(20)
            val thumbTop = listTop + ((view - thumb) * scroll / maxScroll).toInt()
            g.colour = Constants.SECTOR_CUTOUT_TEXT
            g.fillRect(barX, thumbTop, SCROLLBAR_WIDTH, thumb)
        }

        hovered?.let { ach ->
            val text = tooltipText(ach)
            g.tooltip = object : StandardTooltip(game) {
                override val customMaxWidth: Int get() = TOOLTIP_WIDTH
                override fun getText(): String = text
            }
        }
    }

    /**
     * Draw a ship's cell, its top left at [x], [y]: its miniature, name,
     * how many achievements are unlocked, and their icons. Returns the
     * achievement under the mouse, if there is one.
     */
    private fun drawShip(g: Graphics, row: ShipRow, x: Int, y: Int): Achievement? {
        g.colour = Constants.SECTOR_CUTOUT_TEXT
        Buttons.drawRounded(g, x, y, CELL_WIDTH, CELL_HEIGHT, 4)
        g.colour = Constants.JUMP_DISABLED_TEXT
        Buttons.drawRounded(g, x + 2, y + 2, CELL_WIDTH - 4, CELL_HEIGHT - 4, 3)

        // The hangar's miniature, at half its size
        val blueprint = row.blueprint
        val mini = blueprint?.let { game.getImgIfExists("img/customizeUI/miniship_${it.img}.png") }
        if (mini != null) {
            val w = mini.width * MINI_SCALE
            val h = mini.height * MINI_SCALE
            mini.draw(x + 8f, y + (CELL_HEIGHT - h) / 2f, w, h)
        }

        val title = blueprint?.shipTitle?.let { game.translator[it] }?.removePrefix("The ") ?: row.name
        val textX = x + NAME_LEFT
        val nameWidth = ICONS_LEFT - NAME_LEFT - 8
        nameFont.drawString(textX.f, y + 26f, fitted(nameFont, title, nameWidth), Constants.SECTOR_CUTOUT_TEXT)
        val count = row.achievements.count { profile.getAchievement(it) != null }
        val countColour = if (count == row.achievements.size) Constants.UI_BUTTON_HOVER else Constants.SECTOR_CUTOUT_TEXT
        textFont.drawString(textX.f, y + 46f, "$count/${row.achievements.size}", countColour)

        var hovered: Achievement? = null
        val iconTop = y + (CELL_HEIGHT - ICON) / 2
        for ((index, ach) in row.achievements.withIndex()) {
            val iconX = x + ICONS_LEFT + index * (ICON + ICON_GAP)
            val unlocked = profile.getAchievement(ach) != null
            val icon = game.getImg(ach.img)
            if (unlocked) {
                icon.draw(iconX.f, iconTop.f, ICON.f, ICON.f)
            } else {
                // Dimmed, with the padlock, as the pause menu shows a locked one
                icon.draw(iconX.f, iconTop.f, ICON.f, ICON.f, LOCKED_TINT)
                lockIcon.draw(iconX + (ICON - lockIcon.width) / 2, iconTop + (ICON - lockIcon.height) / 2)
            }

            val over = mouseX in iconX until iconX + ICON && mouseY in iconTop until iconTop + ICON
            if (over)
                hovered = ach
            g.colour = if (over) Constants.ACHIEVEMENT_OUTLINE_HIGHLIGHT else Constants.ACHIEVEMENT_OUTLINE
            g.drawRect(iconX, iconTop, ICON - 1, ICON - 1)
            g.drawRect(iconX + 1, iconTop + 1, ICON - 3, ICON - 3)
        }
        return hovered
    }

    // An achievement's tooltip: its name, description, best progress (if
    // it has a number to reach) and the difficulty it was unlocked on
    private fun tooltipText(ach: Achievement): String {
        val t = game.translator
        val lines = arrayListOf(t[ach.name], ach.description(t))
        val goal = ach.goal
        if (goal != null && goal.kind.isCounted) {
            // (with this game's, if one's going on)
            val current = game.achievementTracker.takeIf { !game.isMenuScene }?.progress(ach) ?: 0
            val best = maxOf(profile.getAchievementProgress(ach), current)
            lines += t["xftl_ach_best"].replace("\\1", "${best.coerceAtMost(goal.target)}/${goal.target}")
        }
        val unlock = profile.getAchievement(ach)
        lines += if (unlock != null) t["ach_" + unlock.difficulty.name.lowercase()] else t["xftl_ach_locked"]
        return lines.joinToString("\n")
    }

    // [text], cut short with an ellipsis if it's wider than [width]
    private fun fitted(font: SILFontLoader, text: String, width: Int): String {
        if (font.getWidth(text) <= width)
            return text
        var cut = text
        while (cut.isNotEmpty() && font.getWidth("$cut...") > width)
            cut = cut.dropLast(1)
        return "$cut..."
    }

    override fun mouseScroll(change: Int) {
        scroll = (scroll - change / 3f).coerceIn(0f, maxScroll.f)
    }

    override fun updateUI(x: Int, y: Int) {
        mouseX = x
        mouseY = y
        super.updateUI(x, y)
    }

    override fun escapePressed() {
        close()
    }

    /** Back, at the bottom: closes the window. */
    private inner class BackButton(pos: IPoint, size: IPoint) : Button(game, pos, size) {
        private val label = game.translator["xftl_button_back"]
        private val textY: Int = centredBaseline(bigButtonFont, size.y)

        override fun draw(g: Graphics) {
            g.colour = if (hovered) Constants.UI_BUTTON_HOVER else Constants.SECTOR_CUTOUT_TEXT
            Buttons.drawRounded(g, pos.x, pos.y, size.x, size.y, 4)
            val textX = (size.x - bigButtonFont.getWidth(label)) / 2f
            bigButtonFont.drawString(pos.x + textX, pos.y + textY.f, label, Constants.JUMP_DISABLED_TEXT)
        }

        override fun click(button: Int) {
            if (button == Input.MOUSE_LEFT_BUTTON)
                close()
        }
    }

    // The text's baseline, so it's in the middle of something [height] tall
    // (as the statistics window lays out its buttons)
    private fun centredBaseline(font: SILFontLoader, height: Int): Int =
        ((height - (font.baselineToTop + font.trueBaselineOffset) * font.scale) / 2 +
                font.baselineToTop * font.scale).roundToInt()

    companion object {
        private const val WIDTH = 1240
        // (tall enough for ten rows, the twenty ships, without scrolling)
        private const val HEIGHT = 820
        private const val PADDING = 20

        // The list of ships, below the title tab, two to a row: each
        // ship's cell, and the gaps between them
        private const val LIST_TOP = 70
        private const val CELL_GAP = 16
        private const val SCROLLBAR_WIDTH = 6
        private const val CELL_WIDTH = (WIDTH - 2 * PADDING - CELL_GAP - SCROLLBAR_WIDTH - 8) / 2
        private const val CELL_HEIGHT = 62
        private const val ROW = CELL_HEIGHT + 6

        // In a cell: the miniature's scale (the hangar's are 191x121), where
        // the name starts, and the icons (vanilla's are 64x64, shown smaller)
        private const val MINI_SCALE = 0.5f
        private const val NAME_LEFT = 112
        private const val ICON = 48
        private const val ICON_GAP = 6
        private const val ICONS_LEFT = CELL_WIDTH - 8 - 6 * ICON - 5 * ICON_GAP

        private val LOCKED_TINT = Colour(0.35f, 0.35f, 0.35f, 1f)

        private const val TOOLTIP_WIDTH = 330

        private const val BIG_BUTTON_WIDTH = 140
        private const val BIG_BUTTON_HEIGHT = 40
    }
}
