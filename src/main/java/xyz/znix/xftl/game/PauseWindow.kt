package xyz.znix.xftl.game

import xyz.znix.xftl.Constants
import xyz.znix.xftl.XftlDebug
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.ITooltipProvider
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.rendering.StandardTooltip
import xyz.znix.xftl.sys.Input
import xyz.znix.xftl.ui.ImageView
import xyz.znix.xftl.ui.Label
import xyz.znix.xftl.ui.Widget
import xyz.znix.xftl.ui.WidgetContainer

class PauseWindow(val game: InGameState, val close: () -> Unit) : Window() {
    // There's no controls window yet, so the statistics button is in its
    // place (the display options are in the options window). The hangar button is only there with
    // XFTL_SHOW_HANGAR, and XFTL_SKIP_MENU also hides the main menu button,
    // as the game's played without it then.
    private val widgetTree: WidgetContainer = game.uiLoader.load(
        "escape_menu",
        setOfNotNull("main_menu".takeIf { XftlDebug.skipMenu }, "hangar".takeIf { !XftlDebug.showHangar })
    ).mainWidget
    private val lockIcon: Image = game.getImg("img/customizeUI/box_lock_on.png")
    private val mousePos = Point(0, 0)
    private val shipFamily: ShipFamily? = game.content.shipFamilies.byShipId[game.player.type.name]
    private val tracker: AchievementTracker = game.achievementTracker

    // The ship's achievements: the six XFTL tracks for the mod's ships (see
    // AchievementTracker), or a vanilla ship's family's three
    private val achievements: List<Achievement> = tracker.achievements.ifEmpty {
        shipFamily?.achievements?.map { game.content.achievements[it] } ?: emptyList()
    }

    // The achievements box's title, which opens every ship's achievements
    private val achievementsTitle = widgetTree.byId["ach_title"] as Label
    private val titleColour: Colour = achievementsTitle.colour
    private val titleButton: TitleButton

    override val size: IPoint = widgetTree.root.size

    init {
        makeRoomForTheButtons()
        buttons += widgetTree.buildButtons(game, this, ConstPoint.ZERO)

        widgetTree.addButtonListener("continue", this::continueClicked)
        if (!XftlDebug.skipMenu)
            widgetTree.addButtonListener("main_menu", this::mainMenuClicked)
        if (XftlDebug.showHangar)
            widgetTree.addButtonListener("hangar", this::hangarClicked)
        widgetTree.addButtonListener("restart", this::restartClicked)
        widgetTree.addButtonListener("options", this::optionsClicked)
        widgetTree.addButtonListener("statistics", this::statisticsClicked)
        widgetTree.addButtonListener("achievements_button", this::achievementsClicked)
        widgetTree.addButtonListener("quit", this::quitClicked)

        val box = widgetTree.byId["achievements"]!!
        titleButton = TitleButton(box.position, ConstPoint(box.size.x, TITLE_HEIGHT))
        buttons += titleButton

        // Set the difficulty label
        val difficultyLabel = widgetTree.byId["difficulty"] as Label
        difficultyLabel.text = game.translator[game.difficulty.startButtonKey]

        // Set the advanced edition status
        val aeLabel = widgetTree.byId["ae-status"] as Label
        val aeKey = when (game.content.enableAdvancedEdition) {
            true -> "advanced_on_button"
            false -> "advanced_off_button"
        }
        aeLabel.text = game.translator[aeKey]

        // Set the achievement icons
        for (idx in 0 until ICONS) {
            val ach = achievements.getOrNull(idx)
            val img = widgetTree.byId["ach${idx + 1}"] as ImageView
            if (ach != null) {
                img.image = game.getImg(ach.img)
            } else {
                img.isVisible = false
            }
        }

        val questWidget = widgetTree.byId["quest"] as ImageView
        questWidget.isVisible = shipFamily?.hasQuest == true
        if (shipFamily != null && shipFamily.hasQuest) {
            val questImagePath = when (game.mainGame.profile.getShipUnlock(shipFamily)) {
                null -> "img/achievements/S_Q_off.png"
                else -> "img/achievements/S_Q_on.png"
            }
            questWidget.image = game.getImg(questImagePath)
        }

        // The victory mark's lit once the ship's won (its victory achievement)
        val victoryWidget = widgetTree.byId["victory"] as ImageView
        if (victoryAchievement?.let { game.mainGame.profile.getAchievement(it) } != null)
            victoryWidget.image = game.getImg("img/achievements/S_V_on.png")

        // Keep this game's progress in the profile, so the best's up to date
        tracker.recordBest()
    }

    // The ship's achievement for winning the game, if it has one
    private val victoryAchievement: Achievement?
        get() = achievements.firstOrNull { it.goal?.kind == AchievementGoal.Kind.WIN }

    override fun draw(g: Graphics) {
        g.pushTransform()
        g.translate(position.x.f, position.y.f)

        // Draw the base UI, with the achievements' title lit up while it's hovered
        achievementsTitle.colour = if (titleButton.hovered) Constants.UI_BUTTON_HOVER else titleColour
        widgetTree.draw(g)

        for (idx in 0 until ICONS)
            decorateAchievement(g, widgetTree.byId["ach${idx + 1}"] as ImageView, idx)

        if (titleButton.hovered)
            g.tooltip = textTooltip(game.translator["xftl_escape_achievements_tip"])

        val questBox = widgetTree.byId["quest"]!!
        if (mousePos.containedInBox(questBox.position, questBox.size) && questBox.isVisible) {
            g.colour = Constants.UI_BUTTON_HOVER
            drawOutline(g, questBox)

            // TODO tooltip
        }

        val victoryBox = widgetTree.byId["victory"]!!
        if (mousePos.containedInBox(victoryBox.position, victoryBox.size)) {
            g.colour = Constants.UI_BUTTON_HOVER
            drawOutline(g, victoryBox)

            victoryAchievement?.let { g.tooltip = textTooltip(achievementTooltip(it)) }
        }

        // TODO show the dots that indicate the player has won with a given layout

        g.popTransform()
    }

    private fun decorateAchievement(g: Graphics, img: ImageView, index: Int) {
        val hovering = mousePos.containedInBox(img.position, img.size)

        // If the achievement isn't populated - such as for modded ships - then
        // there's nothing to draw.
        val achievement = achievements.getOrNull(index) ?: return
        val unlockInfo = game.mainGame.profile.getAchievement(achievement)

        // Draw the padlock icon, if this achievement is locked
        if (unlockInfo == null) {
            g.colour = Colour(0, 0, 0, 200)
            g.fillRect(img.position.x, img.position.y, img.size.x, img.size.y)

            val x = img.position.x + (img.size.x - lockIcon.width) / 2
            val y = img.position.y + (img.size.y - lockIcon.height) / 2
            lockIcon.draw(x, y)

            // Crossed out in red, if it can't be unlocked in this game any
            // more (as Into the Breach does)
            if (tracker.isOutOfReach(achievement))
                drawCross(g, img)
        }

        // Draw the outline
        g.colour = when (hovering) {
            true -> Constants.ACHIEVEMENT_OUTLINE_HIGHLIGHT
            false -> Constants.ACHIEVEMENT_OUTLINE
        }
        drawOutline(g, img)

        if (hovering)
            g.tooltip = textTooltip(achievementTooltip(achievement))
    }

    // A red cross over [img], in squares down its diagonals, so it's as
    // blocky as the rest of the UI
    private fun drawCross(g: Graphics, img: ImageView) {
        val size = minOf(img.size.x, img.size.y) - CROSS_MARGIN * 2
        val x0 = img.position.x + (img.size.x - size) / 2
        val y0 = img.position.y + (img.size.y - size) / 2
        g.colour = CROSS_COLOUR
        for (i in 0..size - CROSS_THICKNESS) {
            g.fillRect(x0 + i, y0 + i, CROSS_THICKNESS, CROSS_THICKNESS)
            g.fillRect(x0 + size - CROSS_THICKNESS - i, y0 + i, CROSS_THICKNESS, CROSS_THICKNESS)
        }
    }

    /**
     * The tooltip for [ach]: its name and description, and, for one with a
     * number to reach, this game's progress and the best in any game; then
     * the difficulty it was unlocked on.
     */
    private fun achievementTooltip(ach: Achievement): String {
        val t = game.translator
        val lines = arrayListOf(t[ach.name], ach.description(t))
        val goal = ach.goal
        val progress = tracker.progress(ach)
        if (goal != null && progress != null) {
            val best = maxOf(progress, game.mainGame.profile.getAchievementProgress(ach))
            lines += t["xftl_ach_this_game"].replace("\\1", "${progress.coerceAtMost(goal.target)}/${goal.target}")
            lines += t["xftl_ach_best"].replace("\\1", "${best.coerceAtMost(goal.target)}/${goal.target}")
        }
        val unlock = game.mainGame.profile.getAchievement(ach)
        lines += if (unlock != null) t[unlock.difficulty.achievementUnlockKey] else t["xftl_ach_locked"]
        if (unlock == null && tracker.isOutOfReach(ach))
            lines += t["xftl_ach_out_of_reach"]
        return lines.joinToString("\n")
    }

    private fun textTooltip(text: String): ITooltipProvider = object : StandardTooltip(game) {
        override val customMaxWidth: Int get() = TOOLTIP_WIDTH
        override fun getText(): String = text
    }

    /** The achievements box's title: opens every ship's achievements (see AchievementsWindow). */
    /**
     * The buttons are as wide as their labels, which in some languages are
     * wider than the space left for them (Russian's, say): then the boxes
     * right of them (the difficulty, the DLC and the achievements) are moved
     * right, and the window's widened, rather than the buttons running
     * under them.
     */
    private fun makeRoomForTheButtons() {
        val column = widgetTree.byId["column"] ?: return
        val rightSide = listOf("info_difficulty", "info_dlc", "achievements").mapNotNull { widgetTree.byId[it] }
        val left = rightSide.mapNotNull { it.parentLeft }.minOrNull() ?: return
        val shift = column.size.x + COLUMN_GAP - left
        if (shift <= 0)
            return
        for (widget in rightSide)
            widget.parentLeft = widget.parentLeft?.plus(shift)
        widgetTree.updateLayout()
    }

    private inner class TitleButton(pos: IPoint, size: IPoint) : Button(game, pos, size) {
        // (the title's label is drawn with the rest of the box)
        override fun draw(g: Graphics) {}

        override fun click(button: Int) {
            if (button == Input.MOUSE_LEFT_BUTTON)
                achievementsClicked()
        }
    }

    // Every ship's achievements (the achievements button, or the box's
    // title), as the main menu shows them, which come back here
    private fun achievementsClicked() {
        val ui = game.shipUI
        ui.showMenuWindow(AchievementsWindow(game) { ui.showPauseWindow() })
    }

    private fun drawOutline(g: Graphics, widget: Widget) {
        val pos = widget.position
        val size = widget.size

        g.drawRect(pos.x, pos.y, size.x - 1, size.y - 1)
        g.drawRect(pos.x + 1, pos.y + 1, size.x - 3, size.y - 3)
    }

    override fun updateUI(x: Int, y: Int) {
        super.updateUI(x, y)

        mousePos.set(x - position.x, y - position.y)
    }

    override fun escapePressed() {
        close()
    }

    // Button handlers
    private fun continueClicked() {
        close()
    }

    private fun mainMenuClicked() {
        // The game's saved first, to continue it from there
        game.mainGame.switchToMainMenu()
    }

    private fun hangarClicked() {
        // TODO warning box
        game.mainGame.switchToShipSelect()
    }

    private fun restartClicked() {
        // TODO warning box
        game.mainGame.restartGame()
    }

    private fun optionsClicked() {
        game.shipUI.showOptionsWindow()
    }

    // The statistics screen, as the main menu's, which comes back here
    private fun statisticsClicked() {
        val ui = game.shipUI
        ui.showMenuWindow(StatisticsWindow(game, game.mainGame.profile.shownTopGames) { ui.showPauseWindow() })
    }

    // TODO a controls window, and show its button again (see widgetTree)

    private fun quitClicked() {
        game.mainGame.quitGame()
    }

    companion object {
        // The least gap between the buttons and the boxes right of them
        // (see makeRoomForTheButtons): about what's left in English
        private const val COLUMN_GAP = 12

        // The achievement icons' places in the box (ach1 to ach6)
        private const val ICONS = 6

        // How tall the achievements box's title bar is, as a button
        private const val TITLE_HEIGHT = 24

        private const val TOOLTIP_WIDTH = 330

        // The red cross over an achievement out of reach (see drawCross):
        // how far in from the icon's edges, and how thick
        private const val CROSS_MARGIN = 6
        private const val CROSS_THICKNESS = 4
        private val CROSS_COLOUR = Colour(220, 40, 40)
    }
}
