package xyz.znix.xftl.game

import xyz.znix.xftl.Constants
import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrewInfo
import xyz.znix.xftl.crew.Skill
import xyz.znix.xftl.crew.SkillLevel
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.sys.Input
import xyz.znix.xftl.systems.SystemBlueprint
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * The statistics screen: the best finished games by score (see
 * [GameRecord], kept in the profile, SaveProfile.topGames), in a list on
 * the left, and the one picked on the right: how it went (when it started,
 * how long it took and so on, in two columns), and the ship at the end: its
 * crew (with their skills in a tooltip), systems, weapons, drones and
 * augments, drawn as the game draws them. It scrolls, if it doesn't fit.
 *
 * It opens from the main menu and the game over screen ([highlight] is the
 * game just finished, picked first and marked in the list). Back (or
 * escape) calls [close].
 */
class StatisticsWindow(
    private val game: InGameState,
    private val games: List<GameRecord>,
    private val highlight: GameRecord? = null,
    private val close: () -> Unit
) : Window() {
    private val titleFont = game.getFont("HL2", 2f)
    private val titleTab = game.getImg("img/map/side_beaconmap.png")
    private val rowFont = game.getFont("JustinFont12Bold")
    private val textFont = game.getFont("JustinFont11Bold")
    private val headingFont = game.getFont("JustinFont12Bold")
    private val bigButtonFont = game.getFont("HL2", 2f)

    private var selected: GameRecord? = highlight?.takeIf { it in games } ?: games.firstOrNull()

    override val size: IPoint = ConstPoint(WIDTH, HEIGHT)

    init {
        for ((index, record) in games.withIndex()) {
            buttons += RowButton(
                ConstPoint(PADDING, LIST_TOP + index * ROW),
                ConstPoint(LIST_WIDTH, ROW - ROW_GAP),
                index, record
            )
        }
        buttons += BackButton(
            ConstPoint(PADDING, HEIGHT - PADDING - BIG_BUTTON_HEIGHT),
            ConstPoint(BIG_BUTTON_WIDTH, BIG_BUTTON_HEIGHT)
        )
        // Reset, across from it at the other end of the list, if there's
        // anything to reset
        if (games.isNotEmpty()) {
            buttons += ResetButton(
                ConstPoint(PADDING + LIST_WIDTH - BIG_BUTTON_WIDTH, HEIGHT - PADDING - BIG_BUTTON_HEIGHT),
                ConstPoint(BIG_BUTTON_WIDTH, BIG_BUTTON_HEIGHT)
            )
        }
    }

    // Reset's confirmation, while it's open, drawn over this screen (see
    // ResetButton), and given its clicks and escape
    private var confirm: ConfirmWindow? = null

    override fun draw(g: Graphics) {
        drawStatistics(g)

        val confirm = confirm ?: return
        g.colour = Colour(0f, 0f, 0f, 0.65f)
        g.fillRect(position.x, position.y, size.x, size.y)
        confirm.position = ConstPoint(
            position.x + (size.x - confirm.size.x) / 2,
            position.y + (size.y - confirm.size.y) / 2
        )
        confirm.draw(g)
    }

    private fun drawStatistics(g: Graphics) {
        game.windowRenderer.renderWithTitleTab(
            g, titleTab, titleFont, position.x, position.y, size.x, size.y,
            game.translator["xftl_stats_title"]
        )

        for (button in buttons)
            button.draw(g)

        val record = selected
        if (record == null) {
            rowFont.drawString(position.x + PADDING.f, position.y + LIST_TOP + 20f,
                game.translator["xftl_stats_none"], Constants.SECTOR_CUTOUT_TEXT)
            return
        }
        drawDetails(g, record)
    }

    override fun mouseClick(button: Int, x: Int, y: Int) {
        confirm?.let { return it.mouseClick(button, x, y) }
        super.mouseClick(button, x, y)
    }

    override fun mouseReleased(button: Int, x: Int, y: Int) {
        confirm?.let { return it.mouseReleased(button, x, y) }
        super.mouseReleased(button, x, y)
    }

    // The visible part of the details column, on the screen
    private val detailsTop: Int get() = position.y + LIST_TOP
    private val detailsBottom: Int get() = position.y + HEIGHT - PADDING
    private val detailsLeft: Int get() = position.x + DETAILS_LEFT

    // How far the details are scrolled down, and how tall they were last
    // time they were drawn, in pixels
    private var scroll = 0f
    private var contentHeight = 0

    // Where the mouse is, and the crewmembers drawn, on the screen, for
    // their skills' tooltip
    private var mouseX = 0
    private var mouseY = 0
    private val crewSpots = ArrayList<Pair<IntArray, GameRecord.CrewRecord>>()

    // The weapons, drones and augments drawn, on the screen, for their
    // descriptions' tooltip (see drawItemTooltip)
    private val itemSpots = ArrayList<Pair<IntArray, Any>>()
    private val infoPanel = InfoPanel(game)

    // Each record's crew, to draw them (see crewInfo)
    private val crewInfos = HashMap<GameRecord.CrewRecord, LivingCrewInfo?>()

    /**
     * The picked game's details, in the right-hand column: how it went, in
     * two columns, then its crew (drawn as the game draws them, with their
     * skills in a tooltip; none for a game lost with the crew dead), systems, weapons, drones and augments, as the
     * game draws them too. It scrolls, if it's too long to fit.
     */
    private fun drawDetails(g: Graphics, record: GameRecord) {
        val t = game.translator
        val left = detailsLeft
        val width = WIDTH - DETAILS_LEFT - PADDING - SCROLLBAR_WIDTH - 6
        val half = width / 2
        val top = detailsTop
        val bottom = detailsBottom
        var y = top - scroll.toInt()
        crewSpots.clear()
        itemSpots.clear()

        // Whether something from y0 to y1 is in view: it's drawn only if so,
        // as there's no clipping
        fun inView(y0: Int, y1: Int): Boolean = y0 >= top && y1 <= bottom

        fun text(font: SILFontLoader, x: Int, rowTop: Int, string: String, colour: Colour = Constants.SECTOR_CUTOUT_TEXT) {
            if (inView(rowTop, rowTop + TEXT_ROW))
                font.drawString(x.f, rowTop + TEXT_BASELINE.f, string, colour)
        }

        fun heading(key: String) {
            y += SECTION_GAP
            text(headingFont, left, y, t[key], Constants.UI_BUTTON_HOVER)
            y += TEXT_ROW + HEADING_GAP
        }

        // How it went
        text(headingFont, left, y, "${record.shipName}: ${outcomeText(record)}", Constants.UI_BUTTON_HOVER)
        y += TEXT_ROW + 4
        val stats = listOf(
            "xftl_stats_started" to DATE_FORMAT.format(Date(record.startTime)),
            "xftl_stats_duration" to duration(record.durationSeconds),
            "xftl_stats_difficulty" to t[record.difficulty.startButtonKey],
            "xftl_stats_names" to t["xftl_names_${record.namePreset}"],
            "xftl_stats_sector" to record.sector.toString(),
            "xftl_stats_beacons" to record.beacons.toString(),
            "xftl_stats_ships" to (record.shipsDestroyed + record.shipsCrewKilled).toString(),
            // (how many of them were beaten by killing their crews, on the
            // line under it)
            "xftl_stats_crews_killed" to record.shipsCrewKilled.toString(),
            "xftl_stats_scrap" to record.scrap.toString(),
            "xftl_stats_crew_lost" to record.crewLost.toString(),
        )
        val statRows = (stats.size + 1) / 2
        for ((index, stat) in stats.withIndex()) {
            val (key, value) = stat
            val x = left + (index / statRows) * half
            text(textFont, x, y + (index % statRows) * TEXT_ROW, "${t[key]}: $value")
        }
        y += statRows * TEXT_ROW

        // The crew, a few to a row, each with their portrait; not for a game
        // lost with the crew dead, which would show only whoever was
        // last left (dying)
        if (record.outcome != GameOverWindow.Outcome.LOOSE_CREW) {
            heading("xftl_stats_crew")
            val crewColumns = (width / CREW_CELL_WIDTH).coerceAtLeast(1)
            for ((index, member) in record.crew.withIndex()) {
                val x = left + (index % crewColumns) * CREW_CELL_WIDTH
                val cellTop = y + (index / crewColumns) * CREW_ROW
                if (!inView(cellTop, cellTop + CREW_ROW))
                    continue
                val info = crewInfo(member)
                info?.drawPortrait(x, cellTop)
                rowFont.drawString(x + PORTRAIT_WIDTH + 4f, cellTop + CREW_ROW / 2 + 6f,
                    fitted(rowFont, member.name, CREW_CELL_WIDTH - PORTRAIT_WIDTH - 8), Constants.SECTOR_CUTOUT_TEXT)
                crewSpots += intArrayOf(x, cellTop, CREW_CELL_WIDTH, CREW_ROW) to member
            }
            y += (record.crew.size + crewColumns - 1) / crewColumns * CREW_ROW
        }

        // The systems, as the power bar shows them: each one's icon, under
        // its level's bars, in the power bar's order (see SYSTEM_ORDER), the
        // subsystems starting a row of their own after the main systems
        heading("xftl_stats_systems")
        // (each group's rows as tall as its most bars need)
        val systemColumns = (width / SYSTEM_CELL_WIDTH).coerceAtLeast(1)
        val (subsystems, mainSystems) = record.systems.sortedBy { systemOrder(it.name) }
            .partition { it.name in SUBSYSTEMS }
        for (group in listOf(mainSystems, subsystems)) {
            val systemRow = SYSTEM_ICON + (group.maxOfOrNull { it.level } ?: 0) * SYSTEM_BAR_STEP
            for ((index, system) in group.withIndex()) {
                val x = left + (index % systemColumns) * SYSTEM_CELL_WIDTH
                val cellTop = y + (index / systemColumns) * (systemRow + ITEM_GAP)
                if (inView(cellTop, cellTop + systemRow))
                    drawSystem(g, system, x, cellTop + systemRow)
            }
            y += (group.size + systemColumns - 1) / systemColumns * (systemRow + ITEM_GAP)
        }

        // The weapons, drones and augments, two to a row, as the game draws them
        for ((key, names) in listOf(
            "xftl_stats_weapons" to record.weapons, "xftl_stats_drones" to record.drones,
            "xftl_stats_augments" to record.augments
        )) {
            if (names.isEmpty())
                continue
            heading(key)
            for (row in names.chunked(2)) {
                val blueprints = row.map { game.blueprintManager.getOrNull(it) }
                val height = blueprints.maxOf { itemHeight(it) }
                for ((column, name) in row.withIndex()) {
                    val x = left + column * half
                    if (inView(y, y + height)) {
                        drawItem(g, blueprints[column], name, x, y, height, half - ITEM_GAP)
                        blueprints[column]?.let { itemSpots += intArrayOf(x, y, half - ITEM_GAP, height) to it }
                    }
                }
                y += height + ITEM_GAP
            }
        }

        contentHeight = y + scroll.toInt() - top
        scroll = scroll.coerceIn(0f, maxScroll.f)

        // The scrollbar, if it doesn't all fit
        val view = bottom - top
        if (contentHeight > view) {
            val barX = position.x + WIDTH - PADDING - SCROLLBAR_WIDTH
            g.colour = Constants.JUMP_DISABLED
            g.fillRect(barX, top, SCROLLBAR_WIDTH, view)
            val thumb = (view.toFloat() * view / contentHeight).toInt().coerceAtLeast(20)
            val thumbTop = top + ((view - thumb) * scroll / maxScroll).toInt()
            g.colour = Constants.SECTOR_CUTOUT_TEXT
            g.fillRect(barX, thumbTop, SCROLLBAR_WIDTH, thumb)
        }

        // (not under Reset's confirmation)
        if (confirm == null) {
            drawSkillsTooltip(g)
            drawItemTooltip()
        }
    }

    // The hovered weapon's, drone's or augment's description, as the game
    // shows it (the ship window's and the stores' info panel), by the mouse
    private fun drawItemTooltip() {
        val blueprint = itemSpots.firstOrNull { (rect, _) ->
            mouseX in rect[0] until rect[0] + rect[2] && mouseY in rect[1] until rect[1] + rect[3]
        }?.second ?: return

        // (as tall as it usually is, without the tip the game has under it:
        // a longer description may go a little further)
        val height = when (blueprint) {
            is AbstractWeaponBlueprint -> InfoPanel.INFO_HEIGHT_WEAPON
            is DroneBlueprint -> InfoPanel.INFO_HEIGHT_DRONE
            else -> InfoPanel.INFO_HEIGHT_AUGMENT
        }
        var x = mouseX + 16
        if (x + INFO_PANEL_WIDTH > position.x + WIDTH)
            x = mouseX - 16 - INFO_PANEL_WIDTH
        val y = (mouseY + 16).coerceAtMost(position.y + HEIGHT - height).coerceAtLeast(position.y)
        infoPanel.position = ConstPoint(x, y)
        when (blueprint) {
            is AbstractWeaponBlueprint -> infoPanel.drawWeapon(blueprint, withTip = false)
            is DroneBlueprint -> infoPanel.drawDrone(blueprint, withTip = false)
            is AugmentBlueprint -> infoPanel.drawAugment(blueprint)
        }
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

    private val maxScroll: Int get() = (contentHeight - (detailsBottom - detailsTop)).coerceAtLeast(0)

    // A crewmember, made to draw as the game draws them, with their
    // skills, or null if the game no longer has their race
    private fun crewInfo(member: GameRecord.CrewRecord): LivingCrewInfo? = crewInfos.getOrPut(member) {
        val race = game.blueprintManager.getOrNull(member.race) as? CrewBlueprint ?: return@getOrPut null
        LivingCrewInfo(game, race, member.name, member.colour).also { info ->
            for ((skill, level) in Skill.entries.zip(member.skills))
                info.skills[skill] = SkillLevel.progressAt(level)
        }
    }

    /**
     * Draw a system as the power bar does: its icon (green, as it's
     * working), with a bar for each of its levels stacked above it, from
     * [bottom] up; or its name, if the game no longer has it.
     */
    private fun drawSystem(g: Graphics, system: GameRecord.SystemRecord, x: Int, bottom: Int) {
        if (game.blueprintManager.getOrNull(system.name) !is SystemBlueprint) {
            textFont.drawString(x.f, bottom - 6f, "${system.name} ${system.level}", Constants.SECTOR_CUTOUT_TEXT)
            return
        }
        val iconTop = bottom - SYSTEM_ICON
        // (the icons have 19 pixels of glow around them, see AbstractSystem.drawIconAndPower)
        game.getImg("img/icons/s_${system.name}_green1.png").draw(x - SYSTEM_ICON_PADDING, iconTop - SYSTEM_ICON_PADDING)
        g.colour = Constants.SYS_ENERGY_ACTIVE
        for (level in 0 until system.level) {
            val barTop = iconTop - (level + 1) * SYSTEM_BAR_STEP
            g.fillRect(x + (SYSTEM_ICON - SYSTEM_BAR_WIDTH) / 2, barTop, SYSTEM_BAR_WIDTH, SYSTEM_BAR_STEP - 2)
        }
    }

    // How tall a weapon, drone or augment is, as [drawItem] draws it
    private fun itemHeight(blueprint: Any?): Int = when (blueprint) {
        is AbstractWeaponBlueprint -> maxOf(TEXT_ROW, weaponSpan(blueprint))
        is DroneBlueprint -> maxOf(TEXT_ROW, blueprint.iconSize.y)
        is AugmentBlueprint -> AUGMENT_HEIGHT
        else -> TEXT_ROW
    }

    // How tall a weapon's launcher is, lying on its side, with its copies
    // (as the event window lays it out)
    private fun weaponSpan(bp: AbstractWeaponBlueprint): Int {
        val launcher = bp.getLauncher(game)
        val offsets = bp.copyOffsets(launcher)
        return launcher.getChargedImage(game).width + offsets.maxOf { it.x } - offsets.minOf { it.x }
    }

    /**
     * Draw a weapon (its launcher, lying on its side), a drone (its icon) or
     * an augment (in a box), with its name, in a row [height] tall from
     * [top], as the event window draws rewards; or just its name, if the
     * game no longer has it. The name's cut short to fit in [width].
     */
    private fun drawItem(g: Graphics, blueprint: Any?, name: String, x: Int, top: Int, height: Int, width: Int) {
        val nameY = top + (height - TEXT_ROW) / 2 + TEXT_BASELINE.f
        val colour = Constants.SECTOR_CUTOUT_TEXT
        when (blueprint) {
            is AbstractWeaponBlueprint -> {
                val charged = blueprint.getLauncher(game).getChargedImage(game)
                blueprint.drawLauncherUI(game, g, x.f, top + (height - charged.width) / 2f)
                val textX = charged.height + 10
                textFont.drawString(x + textX.f, nameY, fitted(textFont, blueprint.translateTitle(game), width - textX), colour)
            }

            is DroneBlueprint -> {
                blueprint.drawIconUI(game, ConstPoint(x + blueprint.iconSize.x / 2, top + height / 2))
                val textX = blueprint.iconSize.x + 8
                textFont.drawString(x + textX.f, nameY, fitted(textFont, blueprint.translateTitle(game), width - textX), colour)
            }

            is AugmentBlueprint -> {
                g.colour = colour
                Buttons.drawRounded(g, x, top, AUGMENT_WIDTH, height, 4)
                g.colour = Constants.JUMP_DISABLED_TEXT
                Buttons.drawRounded(g, x + 2, top + 2, AUGMENT_WIDTH - 4, height - 4, 3)
                textFont.drawString(x + 8f, nameY, fitted(textFont, blueprint.translateTitle(game), AUGMENT_WIDTH - 16), colour)
            }

            else -> textFont.drawString(x.f, nameY, fitted(textFont, name, width), colour)
        }
    }

    // The hovered crewmember's skills, as the crew window shows them: each
    // skill's icon, lit up by its level, and its bar
    private fun drawSkillsTooltip(g: Graphics) {
        val hovered = crewSpots.firstOrNull { (rect, _) ->
            mouseX in rect[0] until rect[0] + rect[2] && mouseY in rect[1] until rect[1] + rect[3]
        }?.second ?: return
        val info = crewInfo(hovered) ?: return

        var x = mouseX + 16
        var y = mouseY + 16
        if (x + TOOLTIP_WIDTH > position.x + WIDTH)
            x = mouseX - 16 - TOOLTIP_WIDTH
        if (y + TOOLTIP_HEIGHT > position.y + HEIGHT)
            y = mouseY - 16 - TOOLTIP_HEIGHT
        game.windowRenderer.render(x, y, TOOLTIP_WIDTH, TOOLTIP_HEIGHT)
        headingFont.drawString(x + 14f, y + 26f, "${hovered.name} (${info.race.translateTitle(game)})", Colour.white)

        // (two to a row, the last row's one, the seventh, centred)
        val skills = Skill.DISPLAY_ORDER
        for ((index, skill) in skills.withIndex()) {
            val column = if (index == skills.size - 1 && index % 2 == 0) 0.5f else (index % 2).f
            val iconX = x + 12 + (column * (TOOLTIP_WIDTH / 2 - 6)).toInt()
            val iconY = y + 38 + index / 2 * 34
            val level = info.getSkillLevel(skill)
            val iconColour = when {
                level.isMax -> Constants.SYS_ENERGY_REPAIR
                level.isPartial -> Constants.SYS_ENERGY_ACTIVE
                else -> Constants.UI_BACKGROUND_GLOW_COLOUR
            }
            game.getImg(skill.iconPath).draw(iconX, iconY, iconColour)
            info.drawSkillProgressBar(g, iconX + 30, iconY + 8, 111, 9, skill)
        }
    }

    override fun mouseScroll(change: Int) {
        if (confirm != null)
            return
        scroll = (scroll - change / 3f).coerceIn(0f, maxScroll.f)
    }

    override fun updateUI(x: Int, y: Int) {
        mouseX = x
        mouseY = y
        confirm?.let { return it.updateUI(x, y) }
        super.updateUI(x, y)
    }

    private fun outcomeText(record: GameRecord): String = game.translator[when (record.outcome) {
        GameOverWindow.Outcome.WIN -> "xftl_stats_won"
        GameOverWindow.Outcome.LOOSE_HULL -> "xftl_stats_destroyed"
        GameOverWindow.Outcome.LOOSE_CREW -> "xftl_stats_crew_dead"
        GameOverWindow.Outcome.LOOSE_BASE_DESTROYED -> "xftl_stats_lost"
    }]

    // A duration in hours and minutes, like 1:05
    private fun duration(seconds: Int): String = "%d:%02d".format(seconds / 3600, seconds / 60 % 60)

    override fun escapePressed() {
        confirm?.let { return it.escapePressed() }
        close()
    }

    /** A game in the list: its rank, score, ship, the sector it reached and how it ended. */
    private inner class RowButton(pos: IPoint, size: IPoint, private val index: Int, private val record: GameRecord) :
        Button(game, pos, size) {

        private val textY: Int = centredBaseline(rowFont, size.y)

        override fun draw(g: Graphics) {
            val picked = record == selected
            val colour = when {
                picked || hovered -> Constants.UI_BUTTON_HOVER
                else -> Constants.SECTOR_CUTOUT_TEXT
            }
            g.colour = colour
            Buttons.drawRounded(g, pos.x, pos.y, size.x, size.y, 4)

            val text = Constants.JUMP_DISABLED_TEXT
            val y = pos.y + textY.f
            val rank = "#${index + 1}" + if (record == highlight) " ${game.translator["new_score"]}" else ""
            rowFont.drawString(pos.x + 8f, y, rank, text)
            rowFont.drawString(pos.x + 90f, y, record.score.toString(), text)
            // (the ship's name cut short, if it would run into the end)
            val end = "${game.translator["xftl_stats_sector"]} ${record.sector}: ${outcomeText(record)}"
            val endX = size.x - 8 - rowFont.getWidth(end)
            rowFont.drawString(pos.x + 170f, y, fitted(rowFont, record.shipName, endX - 170 - 12), text)
            rowFont.drawString(pos.x + endX.f, y, end, text)
        }

        override fun click(button: Int) {
            if (button == Input.MOUSE_LEFT_BUTTON) {
                selected = record
                scroll = 0f
            }
        }
    }

    /** Back, at the bottom: closes the window. */
    private inner class BackButton(pos: IPoint, size: IPoint) : BigButton(pos, size, "xftl_button_back") {
        override fun click(button: Int) {
            if (button == Input.MOUSE_LEFT_BUTTON)
                close()
        }
    }

    /**
     * Reset: forget every game in the statistics (the profile's best
     * games; the achievements stay), once the player's said yes to the
     * confirmation (over this screen, see [confirm]), then show the screen
     * again, empty. No, or escape, goes back to the screen as it was.
     */
    private inner class ResetButton(pos: IPoint, size: IPoint) : BigButton(pos, size, "xftl_button_reset") {
        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return
            val ui = game.shipUI
            val profile = game.mainGame.profile
            confirm = ConfirmWindow(game, game.translator["xftl_stats_reset_confirm"], {
                profile.clearGameRecords()
                ui.showMenuWindow(StatisticsWindow(game, profile.shownTopGames, null, close))
            }) { confirm = null }
        }
    }

    // A big rounded button, as the new game window's, labelled [labelKey]
    private abstract inner class BigButton(pos: IPoint, size: IPoint, labelKey: String) : Button(game, pos, size) {
        private val label = game.translator[labelKey]
        private val textY: Int = centredBaseline(bigButtonFont, size.y)

        override fun draw(g: Graphics) {
            g.colour = if (hovered) Constants.UI_BUTTON_HOVER else Constants.SECTOR_CUTOUT_TEXT
            Buttons.drawRounded(g, pos.x, pos.y, size.x, size.y, 4)
            val textX = (size.x - bigButtonFont.getWidth(label)) / 2f
            bigButtonFont.drawString(pos.x + textX, pos.y + textY.f, label, Constants.JUMP_DISABLED_TEXT)
        }
    }

    // The text's baseline, so it's in the middle of something [height] tall
    // (as the new game window's buttons lay out theirs)
    private fun centredBaseline(font: SILFontLoader, height: Int): Int =
        ((height - (font.baselineToTop + font.trueBaselineOffset) * font.scale) / 2 +
                font.baselineToTop * font.scale).roundToInt()

    companion object {
        /**
         * The order the game's power bar has the systems in, by their
         * codenames: the main systems', as Ship sorts them by
         * MainSystem.SortingType, then the subsystems', by
         * SubSystem.SortingType (on the right of the screen). A system the
         * list hasn't (a mod's) goes last; the same systems keep the order
         * they were recorded in.
         */
        private val SYSTEM_ORDER = listOf(
            "shields", "engines", "medbay", "clonebay", "oxygen", "teleporter", "cloaking", "artillery",
            "mind", "hacking", "weapons", "drones",
            "pilot", "sensors", "doors", "battery",
        )

        // The subsystems' codenames, the end of SYSTEM_ORDER
        private val SUBSYSTEMS = setOf("pilot", "sensors", "doors", "battery")

        private fun systemOrder(codename: String): Int =
            SYSTEM_ORDER.indexOf(codename).let { if (it < 0) SYSTEM_ORDER.size else it }

        private const val WIDTH = 1240
        private const val HEIGHT = 760
        private const val PADDING = 20

        // The list of games, on the left, below the title tab
        private const val LIST_TOP = 70
        private const val LIST_WIDTH = 560
        private const val ROW = 29
        private const val ROW_GAP = 4

        // The details of the picked game, on the right: a line of text, and
        // its baseline in it; the gap above each section; a crewmember's
        // cell (and their portrait in it); the gap under each row of
        // weapons, drones or augments, and an augment's box
        private const val DETAILS_LEFT = PADDING + LIST_WIDTH + 30
        private const val TEXT_ROW = 20
        private const val TEXT_BASELINE = 15
        private const val SECTION_GAP = 12

        // Below a section's heading, before what's in it
        private const val HEADING_GAP = 8
        private const val CREW_CELL_WIDTH = 118
        private const val CREW_ROW = 34
        private const val PORTRAIT_WIDTH = 30
        private const val ITEM_GAP = 6
        private const val AUGMENT_WIDTH = 280
        private const val AUGMENT_HEIGHT = 28
        private const val SCROLLBAR_WIDTH = 6

        // A system: its cell, its icon (without its glow, and the glow), and
        // each of its level's bars, and the space they take with the gap
        // between them
        private const val SYSTEM_CELL_WIDTH = 38
        private const val SYSTEM_ICON = 26
        private const val SYSTEM_ICON_PADDING = 19
        private const val SYSTEM_BAR_WIDTH = 14
        private const val SYSTEM_BAR_STEP = 6

        // The crew's skills' tooltip: its title, and a 34 pixel row for
        // each two skills (four, for the seven)
        private const val TOOLTIP_WIDTH = 330

        // The info panel's width (see InfoPanel.drawDescriptionBox)
        private const val INFO_PANEL_WIDTH = 333
        private const val TOOLTIP_HEIGHT = 184

        private const val BIG_BUTTON_WIDTH = 140
        private const val BIG_BUTTON_HEIGHT = 40

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm")
    }
}
