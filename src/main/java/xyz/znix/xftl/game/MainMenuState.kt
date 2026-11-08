package xyz.znix.xftl.game

import xyz.znix.xftl.Constants
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrewInfo
import xyz.znix.xftl.systems.Cloaking
import xyz.znix.xftl.systems.Teleporter
import xyz.znix.xftl.environment.AbstractEnvironment
import xyz.znix.xftl.environment.MixedEnvironment
import xyz.znix.xftl.f
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Cursor
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.ITooltipProvider
import xyz.znix.xftl.sector.Beacon.EnvironmentType
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.Input

/**
 * The main menu, which the game starts in, as in FTL: the game's name, the ship
 * the hangar picks by default (the Chonkestrel, with the mod) full of crew,
 * manning its stations or standing about, in front of a random extreme
 * hazard's backdrop (without its effects), and the menu in the bottom right
 * corner.
 *
 * The ship's a new game's (see [InGameState.forMainMenu]), which also
 * gives the menu the game's options, display and confirmation windows.
 *
 * Escape does nothing here, only closing the windows. XFTL_SKIP_MENU skips
 * the menu (see [xyz.znix.xftl.XftlDebug.skipMenu]).
 */
class MainMenuState(private val main: MainGame, content: InGameState.GameContent) : MainGame.GameState() {
    private val scene: InGameState =
        InGameState.forMainMenu(main, content, content.blueprintManager.defaultPlayerShip)
    private val ship: Ship get() = scene.player
    private val ui: PlayerShipUI get() = scene.shipUI

    private val backdrop: AbstractEnvironment

    private val titleFont = scene.getFont("HL2", 5f)
    private val itemFont = scene.getFont("HL2", 3f)

    private val items: List<Item>
    private var hoveredItem: Item? = null

    private val shipOffset = Point(0, 0)
    private val mousePos = Point(0, 0)
    private var lastTooltip: ITooltipProvider? = null

    init {
        fillCrew()
        backdrop = randomBackdrop()

        val hasSave = ContinueSave.exists()
        items = listOf(
            Item("mainmenu_continue", hasSave, this::continueClicked),
            Item("mainmenu_newgame", true) { newGameClicked(hasSave) },
            Item("mainmenu_options", true) { ui.showOptionsWindow() },
            Item("xftl_mainmenu_statistics", false) {},
            Item("mainmenu_credits", false) {},
            Item("xftl_mainmenu_exit", true) { main.quitGame() },
        )
    }

    /**
     * Crew the ship: the systems that can be manned are all put in (as if
     * the player had bought them), with someone at each station, and a
     * boarding party of [BOARDING_PARTY] waits in the teleporter, also put
     * in; nobody stands about with nothing to do. The crew are of all the
     * races, but the one Lanius has the cloaking room (put in too, and with
     * no station for anyone else to come to) to themselves, as they drain
     * the air from their room, which would suffocate anyone in it with them.
     */
    private fun fillCrew() {
        val races = CREW_RACES.mapNotNull { scene.blueprintManager.getOrNull(it) as? CrewBlueprint }
        fun newCrew() = LivingCrewInfo.generateRandom(races.random(), scene)

        // The systems to man, and the teleporter, in any room still empty
        for (slot in ship.systemSlots) {
            val wanted = slot.system.info?.canBeManned == true ||
                    slot.system.info == Teleporter.INFO || slot.system.info == Cloaking.INFO
            if (wanted && !slot.isInstalled && slot.room.system == null)
                slot.room.setSystem(slot)
        }
        ship.updateAvailableSystems()

        val lanius = scene.blueprintManager.getOrNull(LANIUS) as? CrewBlueprint
        val laniusRoom = if (lanius != null) ship.cloaking?.room else null
        if (lanius != null && laniusRoom != null)
            ship.addCrewMember(LivingCrewInfo.generateRandom(lanius, scene), false, false, laniusRoom)

        for (system in ship.systems) {
            if (ship.crew.size >= ship.maxCrew)
                break
            val room = system.room ?: continue
            if (room == laniusRoom || system.info?.canBeManned != true)
                continue
            val station = system.configuration.computerPoint ?: continue
            if (room.isSlotFree(station, AbstractCrew.SlotType.CREW))
                ship.addCrewMember(newCrew(), false, false, room)
        }

        // The boarding party, in the teleporter
        val teleporter = ship.teleporter?.room ?: return
        repeat(BOARDING_PARTY) {
            if (ship.crew.size < ship.maxCrew && teleporter.firstFreeSlot(AbstractCrew.SlotType.CREW) != null)
                ship.addCrewMember(newCrew(), false, false, teleporter)
        }
    }

    /**
     * Two to four hazards of different kinds, as an extreme hazard beacon
     * has (see [MixedEnvironment]), with any kind of nebula.
     */
    private fun randomBackdrop(): AbstractEnvironment {
        val nebula = NEBULAS.random()
        val types = listOf(nebula, EnvironmentType.ASTEROID, EnvironmentType.SUN, EnvironmentType.PULSAR)
            .shuffled()
            .take((2..4).random())
        val beacon = scene.currentBeacon
        return MixedEnvironment(scene, beacon, types.map { it.create(scene, beacon) })
    }

    override fun shutdown() {
        // The scene's resources belong to the game content, like a game's
    }

    override fun update(container: GameContainer, delta: Float) {
        scene.updateMenuScene(delta)

        // Nothing should be looping here, but keep the volumes up to date
        // for the options window
        (scene.sounds as? RealSoundManager)?.let { sounds ->
            sounds.updateLoopedSounds(true)
            sounds.soundEffectVolume = main.profile.soundVolume
            sounds.musicVolume = main.profile.musicVolume
        }

        val input = container.input
        mousePos.set(input.mouseX, input.mouseY)

        if (ui.isWindowOpen) {
            hoveredItem = null
            ui.updateUI(mousePos.x, mousePos.y, shipOffset)
        } else {
            hoveredItem = items.firstOrNull { it.enabled && it.contains(mousePos.x, mousePos.y) }
        }
    }

    override fun render(container: GameContainer, g: Graphics) {
        g.clear(Colour.black)
        g.tooltip = null

        // Just the backdrop and scenery: no flares, pulses or lightning
        backdrop.renderBackground(container, g)

        // The ship a bit left of the middle, out of the way of the menu
        val hullCentreX = ship.hullOffset.x + ship.hullImage.width / 2
        val hullCentreY = ship.hullOffset.y + ship.hullImage.height / 2
        shipOffset.set(container.width * 2 / 5 - hullCentreX, container.height / 2 - hullCentreY)
        g.pushTransform()
        g.translate(shipOffset.x.f, shipOffset.y.f)
        ship.render(g, true, null)
        g.popTransform()

        drawTitle(container)
        drawItems(container)

        ui.renderMenuWindows(container, g)

        g.tooltip?.let { tooltip ->
            tooltip.drawTooltip(
                g, mousePos.x, mousePos.y, tooltip != lastTooltip,
                container.width, container.height
            )
        }
        lastTooltip = g.tooltip
    }

    // The game's name in the top right, a word to a line ("PROJECT" over
    // "CHONKESTREL"), right-aligned like the menu
    private fun drawTitle(container: GameContainer) {
        val right = container.width - MARGIN
        var y = TITLE_TOP + (titleFont.baselineToTop * titleFont.scale).toInt()
        for (line in main.title.uppercase().split(' ')) {
            titleFont.drawStringLeftAligned(right + SHADOW_OFFSET.f, y + SHADOW_OFFSET.f, line, SHADOW_COLOUR)
            titleFont.drawStringLeftAligned(right.f, y.f, line, Colour.white)
            y += TITLE_LINE_SPACING
        }
    }

    // The menu, in the bottom right corner, the first item at the top, above
    // the build's version
    private fun drawItems(container: GameContainer) {
        val right = container.width - MARGIN
        var y = container.height - BOTTOM_MARGIN - (items.size - 1) * ITEM_SPACING
        for (item in items) {
            val text = scene.translator[item.key]
            val width = itemFont.getWidth(text)
            val top = y - (itemFont.baselineToTop * itemFont.scale).toInt()
            item.setBox(right - width - ITEM_PADDING, top - ITEM_PADDING, width + 2 * ITEM_PADDING, y - top + 2 * ITEM_PADDING)

            val colour = when {
                !item.enabled -> DISABLED_COLOUR
                item == hoveredItem -> Constants.UI_BUTTON_HOVER
                else -> Colour.white
            }
            itemFont.drawStringLeftAligned(right + SHADOW_OFFSET.f, y + SHADOW_OFFSET.f, text, SHADOW_COLOUR)
            itemFont.drawStringLeftAligned(right.f, y.f, text, colour)

            y += ITEM_SPACING
        }
    }

    override fun mousePressed(button: Int, x: Int, y: Int) {
        if (ui.isWindowOpen) {
            ui.mouseClick(button, x, y, shipOffset)
            return
        }

        if (button != Input.MOUSE_LEFT_BUTTON)
            return
        items.firstOrNull { it.enabled && it.contains(x, y) }?.action?.invoke()
    }

    override fun mouseReleased(button: Int, x: Int, y: Int) {
        if (ui.isWindowOpen)
            ui.mouseUp(button, x, y, shipOffset)
    }

    override fun mouseWheelMoved(change: Int) {
        if (ui.isWindowOpen)
            ui.mouseScroll(change)
    }

    override fun keyPressed(key: Int, c: Char) {
        // Escape does nothing on the menu itself
        if (!ui.isWindowOpen)
            return

        // Such as a key being bound in the hotkeys window
        if (ui.onTextInput(key, c))
            return

        if (key == Input.KEY_ESCAPE)
            ui.escapePressed()
    }

    override fun getCurrentCursor(): Cursor = ui.getCurrentCursor()

    private fun continueClicked() {
        // If it can't be loaded, it's moved out of the way, so show the menu
        // again without it
        if (!main.continueFromMainMenu())
            main.switchToMainMenu()
    }

    /**
     * Open the new game window, as New Game does (for the screenshots, see
     * [xyz.znix.xftl.devutil.ScreenshotDirector]).
     */
    fun showNewGameWindow() = newGameClicked(ContinueSave.exists())

    /**
     * Pick the difficulty and crew names, then start, after asking first if
     * that would replace the saved game (no goes back to the choices).
     */
    private fun newGameClicked(hasSave: Boolean) {
        lateinit var setup: NewGameWindow
        setup = NewGameWindow(scene, { difficulty, namePreset ->
            val start = { main.startNewGameFromMenu(difficulty, namePreset) }
            if (hasSave) {
                val message = scene.translator["xftl_confirm_overwrite_save"]
                ui.showMenuWindow(ConfirmWindow(scene, message, start) { ui.showMenuWindow(setup) })
            } else {
                start()
            }
        }, ui::closeMenuWindow)
        ui.showMenuWindow(setup)
    }

    private class Item(val key: String, val enabled: Boolean, val action: () -> Unit) {
        // Where it was last drawn, for the mouse
        private var x = 0
        private var y = 0
        private var width = 0
        private var height = 0

        fun setBox(x: Int, y: Int, width: Int, height: Int) {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
        }

        fun contains(mouseX: Int, mouseY: Int): Boolean =
            mouseX in x until x + width && mouseY in y until y + height
    }

    companion object {
        // The races the crew are picked from, and the Lanius, who has a room
        // to themselves (only with Advanced Edition's data, see fillCrew)
        private val CREW_RACES = listOf("human", "engi", "mantis", "rock", "energy", "slug", "crystal")
        private const val LANIUS = "anaerobic"

        // How many crew wait in the teleporter
        private const val BOARDING_PARTY = 4

        private val NEBULAS = listOf(
            EnvironmentType.NEBULA, EnvironmentType.ION_STORM,
            EnvironmentType.RADIATION, EnvironmentType.MIRAGE, EnvironmentType.DARK
        )

        private const val MARGIN = 60
        private const val TITLE_TOP = 50
        private const val TITLE_LINE_SPACING = 80

        // Leaves room for the build's version under the menu
        private const val BOTTOM_MARGIN = 50
        private const val ITEM_SPACING = 52
        private const val ITEM_PADDING = 6

        private const val SHADOW_OFFSET = 2
        private val SHADOW_COLOUR = Colour(0f, 0f, 0f, 0.7f)
        private val DISABLED_COLOUR = Colour(1f, 1f, 1f, 0.3f)
    }
}
