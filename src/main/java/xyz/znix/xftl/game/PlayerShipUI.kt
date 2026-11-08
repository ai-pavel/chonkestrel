package xyz.znix.xftl.game

import org.jdom2.Element
import org.newdawn.slick.geom.Rectangle
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.*
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewCrystal
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.Skill
import xyz.znix.xftl.crew.SkillLevel
import xyz.znix.xftl.crew.skillBarWidth
import xyz.znix.xftl.drones.AbstractDrone
import xyz.znix.xftl.drones.AbstractIndoorsDrone
import xyz.znix.xftl.environment.AntiShipBattery
import xyz.znix.xftl.environment.PulsarEnvironment
import xyz.znix.xftl.environment.SunEnvironment
import xyz.znix.xftl.game.InGameState.RoomClickListener
import xyz.znix.xftl.layout.Door
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.*
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.Input
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.IRoomTargetingWeapon
import xyz.znix.xftl.weapons.ShadowTargets
import xyz.znix.xftl.weapons.WeaponSizes
import java.util.*
import kotlin.math.*

class PlayerShipUI(val ship: Ship, private val game: InGameState) {
    private val font = game.getFont("HL2", 2f)
    private val weaponNameText = game.getFont("JustinFont8")
    private val crewNameText = game.getFont(CREW_NAME_FONT)
    private val weaponNumberFont = game.getFont("c&c")
    private val numberFont = game.getFont("num_font")
    private val smallNumberFont = game.getFont("JustinFont12Bold")
    private val oxygenEvadeFont = game.getFont("JustinFont10")
    private val tooltipFont = game.getFont("JustinFont11Bold")
    private val lockdownFont = game.getFont("JustinFont8")

    private val numberDeltaFont = game.getFont("HL2", 3f)
    private val numberDeltaFontBg = game.getFont("HL1", 3f)

    private val powerUpSound = game.sounds.getSample("powerUpSystem")
    private val powerUpFailSound = game.sounds.getSample("powerUpFail")
    private val powerDownSound = game.sounds.getSample("powerDownSystem")

    private val cursorNormal = game.getCursor("img/mouse/pointerInvalid.png")
    private val cursorHover = game.getCursor("img/mouse/pointerValid.png")
    private val cursorTeleRetrieveInvalid = game.getCursor("img/mouse/mouse_teleport_in2.png")
    private val cursorTeleRetrieveValid = game.getCursor("img/mouse/mouse_teleport_in1.png")
    private val cursorTeleSendInvalid = game.getCursor("img/mouse/mouse_teleport_out2.png")
    private val cursorTeleSendValid = game.getCursor("img/mouse/mouse_teleport_out1.png")
    private val cursorMindInvalid = game.getCursor("img/mouse/mouse_mind_valid.png")
    private val cursorMindValid = game.getCursor("img/mouse/mouse_mind.png") // Valid doesn't have the _valid path
    private val cursorHacking = game.getCursor("img/mouse/mouse_hacking.png")

    private var selectWeaponClickEvent: RoomClickListener? = null
    private var targetingSelectedWeapon: Int? = null

    /**
     * A slot in the weapons or drones bar: a hardpoint for a weapon (with
     * [drones] null), or a drones system's slot.
     */
    private data class BarSlot(val drones: Drones?, val index: Int)

    // The weapon and drone slots' buttons, for dragging what's in them
    // between them (set up with the buttons)
    private val barSlotButtons = HashMap<BarSlot, WeaponDroneButton>()

    // The slot a weapon or drone is being dragged from, and where the mouse
    // was pressed, while the button's held down
    private var barDrag: BarSlot? = null
    private var barDragStart: IPoint = ConstPoint.ZERO

    // Set if the click on the drone being dragged is waiting for the mouse
    // to be released, so dragging a drone doesn't turn it on (and deploy it)
    private var barDragClickWaiting = false

    /**
     * If true, weapons keep firing at the room (or along the beam) they're
     * aimed at each time they're charged, until they're aimed somewhere
     * else. Holding the autofire modifier key (left control) while aiming
     * does the opposite for that weapon.
     */
    var autofire: Boolean = false
        private set

    /**
     * Whether the autofire modifier key is held, set each frame by the game.
     */
    var autofireModifierHeld: Boolean = false

    // Whether the weapon being aimed now will keep firing at its target
    private val aimingWithAutofire: Boolean get() = autofire != autofireModifierHeld

    private val selectedCrew: MutableList<AbstractCrew> = ArrayList()
    private val hoveredCrew: MutableList<AbstractCrew> = ArrayList()
    private var skillsHoveredCrew: AbstractCrew? = null

    private val buttons = ArrayList<Button>()

    /**
     * The currently shown window. When a window is shown, the main UI is darkened, buttons are locked
     * and all input is sent to the window.
     */
    private var currentWindow: Window? = null

    /**
     * The currently-open pause or options menu.
     *
     * This is handled separately from all other windows, since it can
     * be opened on top of them.
     */
    private var pauseWindow: Window? = null

    private val hullWarningLines = listOf(
        ConstPoint(361, 51),
        ConstPoint(393, 82),
        ConstPoint(482, 82)
    )
    private val hullWarning25 = WarningFlasher(game, ConstPoint(437, 100), "warning_hull_25", true, hullWarningLines)
    private val hullWarning50 = WarningFlasher(game, ConstPoint(437, 100), "warning_hull_50", true, hullWarningLines)
    private val hullWarning75 = WarningFlasher(game, ConstPoint(437, 100), "warning_hull_75", true, hullWarningLines)

    private val oxygenWarningLines = listOf(
        ConstPoint(99, 137),
        ConstPoint(116, 170),
        ConstPoint(216, 170)
    )
    private val oxygenWarning = WarningFlasher(
        game, ConstPoint(167, 188),
        "warning_oxygen", true, oxygenWarningLines, alwaysOn = true
    )

    // (under the scrap, rather than vanilla's place under the shields,
    // where a big ship's second shields system and the stacked Zoltan
    // shields' panel are)
    private val shieldsWarning = WarningFlasher(game, SHIELDS_WARNING, "warning_shields", true)

    // Flashes for a while when intruders come aboard, beside the crew list
    // (it's moved there each frame, as the list moves with the shields)
    private val intrudersWarning = WarningFlasher(game, ConstPoint.ZERO, "warning_intruders", false)
    private var lastIntruderCount = 0

    private val sunWarning = WarningFlasher(
        game, SUN_WARNING, "warning_solar_flare", false, alwaysOn = true
    )
    private val pulsarWarning = WarningFlasher(
        game, PULSAR_WARNING, "warning_ion_pulse", false, alwaysOn = true
    )
    // Just before an Anti-Ship Battery fires at the player
    private val asbWarning = WarningFlasher(
        game, PULSAR_WARNING, "warning_pds_locked", false, alwaysOn = true
    )

    // Shown for a while after arriving in one of XFTL's own nebulas, so the
    // player knows what kind of nebula it is
    private val nebulaWarnings = mapOf(
        Beacon.EnvironmentType.RADIATION to "xftl_warning_radiation",
        Beacon.EnvironmentType.MIRAGE to "xftl_warning_mirage",
        Beacon.EnvironmentType.DARK to "xftl_warning_dark",
    ).mapValues { (_, key) -> WarningFlasher(game, ConstPoint.ZERO, key, false, colour = GlowColour.WHITE) }
    // The beacon and nebula the warning was last shown for, so it shows again
    // if the beacon's nebula changes too
    private var nebulaWarningFor: Pair<Beacon, Beacon.EnvironmentType>? = null

    private val hullTooltip = FixedStandardTooltip(game, "tooltip_hull")
    private val scrapTooltip = FixedStandardTooltip(game, "tooltip_scrapCount")
    private val fuelTooltip = FixedStandardTooltip(game, "tooltip_fuelCount")
    private val missileTooltip = FixedStandardTooltip(game, "tooltip_missileCount")
    private val droneTooltip = FixedStandardTooltip(game, "tooltip_droneCount")
    private val shieldTooltip = FixedStandardTooltip(game, "tooltip_shieldDisplay")
    private val evasionTooltip = FixedStandardTooltip(game, "tooltip_evadeDisplay")
    private val oxygenTooltip = FixedStandardTooltip(game, "tooltip_oxygenDisplay")

    private var lastHealth: Int = -1

    val isWindowOpen: Boolean get() = currentWindow != null || pauseWindow != null

    private val lastResources = ResourceSet()
    private val resourceDeltaAnimations = ArrayList<ResourceDeltaText>()

    private var crewSelectionRectangle: Pair<ConstPoint, Point>? = null

    // The door the left mouse button was pressed on, if any (see mouseUp)
    private var pressedDoor: Door? = null
    private val isCrewSelectionPoint: Boolean
        get() = crewSelectionRectangle?.let { it.first.distToSq(it.second) < SELECTION_BOX_SIZE } ?: false

    // Non-null when the player is aiming a beam weapon
    private var beamTargeting: SelectedTarget.BeamAim? = null
        set(value) {
            field = value

            // Show the aiming-in-progress beam on the enemy ship
            for (system in ship.weaponSystems) {
                system.selectedTargets.beamAiming = value?.takeIf { system.ownsWeapon(it.weapon) }
            }
        }
    private val beamTargetingStartPos = Point(0, 0)

    // Set by render
    private var width: Int = 500
    private var height: Int = 500

    // The last position of the cursor. Try to avoid using this if possible, there's
    // usually a better way to do stuff with a button.
    private val mousePos = Point(0, 0)

    // The time remaining on the insufficient scrap flash animation
    private var insufficientScrapTimer: Float = 0f

    // If true, buttons that are tightly intertwined with the rest of the UI
    // (for example, the weapon and drone buttons) will be re-populated.
    private var updatingButtons = false

    // The longest time it takes to charge a weapon - this is used for drawing
    // the weapon charge bars.
    private var maxWeaponChargeTime: Float = 1f

    // The position of the weapons box
    val weaponBoxY get() = height - 113

    // How far above [weaponBoxY] the weapons and drones boxes reach, for ships
    // with several of either system, as their boxes stack upwards
    val weaponBoxStackHeight: Int
        get() {
            val weapons = ship.mainSystems.count { it is Weapons }
            val drones = ship.mainSystems.count { it is Drones }
            return (maxOf(weapons, drones) - 1).coerceAtLeast(0) * WEAPON_BOX_STACK_SPACING
        }

    // Whether or not we've previously seen a store UI at this sector.
    // This is used to auto-open the store when the dialogue is finished.
    private var storeAlreadyOpened = false

    private val crewX: Int = 10
    private var crewBaseY: Int = 0 // Set while rendering
    private var crewBoxSpacing: Int = CREW_BOX_SPACING // Set while rendering
    private var lastCrewCount: Int = 0

    // The reactor's columns when the buttons were last placed, as more
    // columns move the systems over (see reactorColumns)
    private var lastReactorColumns: Int = 0

    // These time the message that shows up for a second when you save/restore
    // your crew positions.
    private var saveCrewPosMessageTimer: Float = 0f
    private var saveCrewPosMessageText: String = ""

    /**
     * If the user is selecting a room to teleport to/from, this is non-null.
     * True if sending crew to an enemy ship, false if receiving.
     */
    val teleportMode: Boolean? get() = (game.clickEvent as? TeleportRoomListener)?.send

    /**
     * Check if the player is selecting a room for a given hacking or mind
     * control system to target (the ship may have several).
     */
    fun isSelectingHackingTarget(hacking: Hacking): Boolean =
        (game.clickEvent as? HackingRoomListener)?.hacking == hacking

    fun isSelectingMindControlTarget(mindControl: MindControl): Boolean =
        (game.clickEvent as? MindControlRoomListener)?.mindControl == mindControl

    init {
        updateButtons()
    }

    fun updateButtons() {
        barSlotButtons.clear()
        buttons.clear()
        updatingButtons = true

        var nextPos = ConstPoint(531, 29)

        val jump = Buttons.JumpButton(nextPos, ship, game) {
            openJumpMap()
        }
        nextPos += ConstPoint(101, 0)

        val ship = Buttons.ShipButton(nextPos, game) {
            showShipWindow(ShipWindow.Tab.UPGRADES)
        }
        nextPos += ConstPoint(ship.size.x + 17, 0)

        if (game.currentBeacon?.hasStore == true) {
            val store = Buttons.StoreButton(nextPos, game) {
                showStoreWindow()
            }
            nextPos += ConstPoint(store.size.x + 17, 0)
            buttons += store
        }

        val settings = SimpleButton(
            game, nextPos, ConstPoint(41, 41), ConstPoint(7, 7),
            game.getImg("img/statusUI/top_optionswrench_on.png"),
            game.getImg("img/statusUI/top_optionswrench_select2.png"),
            FixedDelayedTooltip(game, "open_options")
        ) {
            showPauseWindow()
        }

        buttons += jump
        buttons += ship
        buttons += settings
    }

    fun mouseClick(button: Int, x: Int, y: Int, playerShipPosition: IPoint) {
        pauseWindow?.let { win ->
            win.mouseClick(button, x, y)
            return
        }

        currentWindow?.let { win ->
            win.mouseClick(button, x, y)
            return
        }

        // When we're in beam targeting mode, block other mouse input.
        if (beamTargeting != null) {
            if (button == Input.MOUSE_LEFT_BUTTON) {
                targetBeamWeapon()
            } else if (button == Input.MOUSE_RIGHT_BUTTON) {
                beamTargeting = null
            }
            return
        }

        // Pressing on a weapon or drone may be the start of dragging it to
        // another slot (see dropInBar)
        if (button == Input.MOUSE_LEFT_BUTTON) {
            val slot = barSlotAt(x, y)
            if (slot != null && hasItem(slot)) {
                barDrag = slot
                barDragStart = ConstPoint(x, y)

                // A drone's click waits until the mouse is released without
                // dragging it
                barDragClickWaiting = slot.drones != null
                if (barDragClickWaiting)
                    return
            }
        }

        for (btn in buttons) {
            val hit = btn.mouseDown(button, x, y)
            if (hit) return
        }

        // A Crystal's lockdown button, in its open skills panel
        val lockdown = lockdownButton
        if (button == Input.MOUSE_LEFT_BUTTON && lockdown != null && lockdown.first == skillsHoveredCrew &&
            lockdown.second.contains(x.f, y.f)
        ) {
            lockdown.first.lockDown()
            return
        }

        // Select players. Pressing on a door may also be the start of
        // dragging a selection box, so the door only opens or closes when
        // it's released without dragging (see mouseUp).
        if (button == Input.MOUSE_LEFT_BUTTON) {
            pressedDoor = ship.doors.firstOrNull { it.isUnderMouse(x - playerShipPosition.x, y - playerShipPosition.y) }
            crewSelectionRectangle = Pair(ConstPoint(x, y), Point(x, y))
            if (pressedDoor != null)
                return
        }

        // Move players if they're right-clicking somewhere
        val shipMousePos = Point(x, y)
        shipMousePos -= playerShipPosition

        val roomPoint = Point(shipMousePos)
        roomPoint.divideFloor(ROOM_SIZE)
        roomPoint -= ship.offset
        for (room in ship.rooms) {
            if (!room.containsAbsolute(roomPoint))
                continue

            if (button == Input.MOUSE_RIGHT_BUTTON) {
                for (crew in selectedCrew) {
                    // Skip mind-controlled crew that remained selected.
                    if (!crew.playerControllable)
                        continue

                    // Exclude crew that are boarding an enemy ship, as
                    // this could cause a crash.
                    if (crew.room.ship == ship)
                        crew.setTargetRoom(room)
                }

                return
            }
        }
    }

    fun mouseUp(button: Int, x: Int, y: Int, playerShipPosition: IPoint) {
        pauseWindow?.let { win ->
            win.mouseReleased(button, x, y)
            return
        }

        currentWindow?.let { win ->
            win.mouseReleased(button, x, y)
            return
        }

        if (button == Input.MOUSE_LEFT_BUTTON && barDrag != null) {
            dropInBar(x, y)
        }

        crewSelectionRectangle?.let {
            if (button != Input.MOUSE_LEFT_BUTTON) return@let

            // A click on a door, without dragging, opens or closes it, and
            // leaves the selected crew as they are
            val door = pressedDoor
            pressedDoor = null
            if (door != null && isCrewSelectionPoint) {
                door.toggle()
                crewSelectionRectangle = null
                return@let
            }

            updateHoveredCrew(x, y, playerShipPosition)

            selectedCrew.clear()
            selectedCrew.addAll(hoveredCrew)

            crewSelectionRectangle = null
        }
    }

    private fun barSlotAt(x: Int, y: Int): BarSlot? = barSlotButtons.entries.firstOrNull { (_, btn) ->
        x in btn.pos.x until btn.pos.x + btn.size.x && y in btn.pos.y until btn.pos.y + btn.size.y
    }?.key

    private fun hasItem(slot: BarSlot): Boolean = when (val drones = slot.drones) {
        null -> ship.hardpoints[slot.index].weapon != null
        else -> drones.drones.getOrNull(slot.index) != null
    }

    // Whether a weapon or drone's being dragged: the mouse has moved a
    // little since it was pressed on it
    private val isDraggingInBar: Boolean
        get() = barDrag != null && barDragStart.distToSq(mousePos) > WEAPON_DRAG_DISTANCE * WEAPON_DRAG_DISTANCE

    /**
     * Drop the weapon or drone being dragged in the bar at ([x], [y]): onto
     * another slot of the same kind, it swaps with whatever's there. Like in
     * the ship's equipment window, this can only be done out of danger.
     */
    private fun dropInBar(x: Int, y: Int) {
        val from = barDrag ?: return
        val dragged = isDraggingInBar
        barDrag = null

        // Just a click on a drone, which waited until now
        if (!dragged && barDragClickWaiting)
            barSlotButtons[from]?.mouseDown(Input.MOUSE_LEFT_BUTTON, barDragStart.x, barDragStart.y)
        barDragClickWaiting = false

        if (!dragged)
            return

        val to = barSlotAt(x, y) ?: return
        if (to == from)
            return

        val fromDrones = from.drones
        val toDrones = to.drones
        when {
            fromDrones == null && toDrones == null -> if (!swapWeapons(from.index, to.index)) return
            fromDrones != null && toDrones != null -> swapDrones(fromDrones, from.index, toDrones, to.index)
            else -> return
        }

        // The click that started the drag picked the weapon to target with
        targetingSelectedWeapon = null
        updateButtons()
    }

    /**
     * Swap the weapons on hardpoints [a] and [b], keeping their charge (and
     * a charger's stored shots and a chain weapon's count), so they can be
     * moved mid-fight. Each one stays powered if it was, as long as the
     * weapons system it's moved to has the power. Not while one's firing,
     * as its shots still to go would be lost: false if they weren't swapped.
     */
    private fun swapWeapons(a: Int, b: Int): Boolean {
        val weaponA = ship.hardpoints[a].weapon
        val weaponB = ship.hardpoints[b].weapon
        if (weaponA?.isFiring == true || weaponB?.isFiring == true)
            return false
        val poweredA = weaponA?.isPowered == true
        val poweredB = weaponB?.isPowered == true

        // Turn them both off first, so the power's free for either
        fun systemFor(hardpoint: Int) = ship.weaponSystems.firstOrNull { it.ownsHardpoint(hardpoint) }
        if (poweredA) systemFor(a)?.setWeaponPower(weaponA!!, false)
        if (poweredB) systemFor(b)?.setWeaponPower(weaponB!!, false)

        // New instances, as a weapon's bound to its hardpoint (where its shots
        // start from), with the old ones' charge
        fun moved(weapon: AbstractWeaponInstance?) = weapon?.type?.buildInstance(ship)?.also {
            it.timeCharged = weapon.timeCharged
            it.extraCharges = weapon.extraCharges
            it.chainCount = weapon.chainCount
        }
        // (their aim's dropped: it was for the weapon that was there)
        for (system in ship.weaponSystems) {
            system.selectedTargets.unTarget(a)
            system.selectedTargets.unTarget(b)
        }
        val newA = moved(weaponB)
        val newB = moved(weaponA)
        ship.hardpoints[a].weapon = newA
        ship.hardpoints[b].weapon = newB
        ship.cargoUpdated()

        if (poweredA && newB != null) systemFor(b)?.setWeaponPower(newB, true)
        if (poweredB && newA != null) systemFor(a)?.setWeaponPower(newA, true)
        return true
    }

    /**
     * Swap the drones in [dronesA]'s slot [a] and [dronesB]'s slot [b],
     * keeping the drones themselves (so a deployed one isn't lost). Each
     * one stays powered if it was, as long as its new system has the power.
     */
    private fun swapDrones(dronesA: Drones, a: Int, dronesB: Drones, b: Int) {
        val infoA = dronesA.drones[a]
        val infoB = dronesB.drones[b]
        val poweredA = infoA?.instance?.isPowered == true
        val poweredB = infoB?.instance?.isPowered == true

        if (poweredA) dronesA.setDronePower(a, false)
        if (poweredB) dronesB.setDronePower(b, false)

        dronesA.drones[a] = infoB
        dronesB.drones[b] = infoA
        ship.cargoUpdated()

        if (poweredA) dronesB.setDronePower(b, true)
        if (poweredB) dronesA.setDronePower(a, true)
    }

    fun mouseScroll(change: Int) {
        pauseWindow?.let { win ->
            win.mouseScroll(change)
            return
        }

        currentWindow?.let { win ->
            win.mouseScroll(change)
            return
        }
    }

    fun crewScreenPos(crew: AbstractCrew, playerShipPosition: IPoint): IPoint {
        if (crew.room.ship == ship) {
            return ConstPoint(crew.screenX, crew.screenY) + playerShipPosition
        }

        // The crewmember must be on an enemy ship, add that position on
        return ConstPoint(crew.screenX, crew.screenY) + game.getEnemyPosition(crew.room.ship)
    }

    fun isCrewHovered(crew: AbstractCrew, mouseX: Int, mouseY: Int, crewPos: IPoint): Boolean {
        // Check if the crewmember's box is being hovered
        val index = game.playerCrew.indexOf(crew)
        if (index != -1) {
            val boxY = crewBaseY + index * crewBoxSpacing
            if (mouseX in crewX..crewX + CREW_BOX_WIDTH && mouseY in boxY..boxY + CREW_BOX_HEIGHT) {
                skillsHoveredCrew = crew
                return true
            }
        }

        // Check if the physical crewmember is being hovered
        return mouseX in crewPos.x..crewPos.x + crew.icon.width &&
                mouseY in crewPos.y..crewPos.y + crew.icon.height
    }

    // Called by SlickGame, used for controlling boarders.
    fun enemyRoomRightClicked(room: Room, enemyShip: Ship) {
        for (crew in selectedCrew) {
            // Skip mind-controlled crew that remained selected.
            if (!crew.playerControllable)
                continue

            if (crew.room.ship == enemyShip)
                crew.setTargetRoom(room)
        }
    }

    fun weaponHotkeyPressed(id: Int, shiftPressed: Boolean) {
        if (pauseWindow != null) return

        // Temporary hack to make the option hotkeys work
        (currentWindow as? DialogueWindow)?.let {
            it.selectOption(id)
            return
        }

        if (currentWindow != null) return

        // Note that modded ships might not have a weapons system, and
        // ships can have several, each controlling some of the weapons.
        val weapons = ship.weaponsFor(id) ?: return

        // Block interactions with hacked weapons
        if (weapons.isHackActive)
            return

        // If the user was previously targeting a beam, cancel that.
        beamTargeting = null

        val weapon = ship.hardpoints.getOrNull(id)?.weapon ?: return

        if (shiftPressed) {
            // Holding shift turns a weapon off
            if (weapon.isPowered && weapons.setWeaponPower(weapon, false)) {
                powerDownSound.play()
            }
            return
        }

        if (!weapon.isPowered) {
            if (weapons.setWeaponPower(weapon, true)) {
                powerUpSound.play()
            } else {
                powerUpFailSound.play()
            }
            return
        }

        weapons.selectedTargets.unTarget(id)

        targetingSelectedWeapon = id
        selectWeaponClickEvent = RoomClickListener { room, gc ->
            // Prevent the player from shooting at the enemy ship once
            // their crew is dead. There may be several enemy ships.
            if (room.ship != ship && !game.isEnemyOf(ship, room.ship)) {
                return@RoomClickListener
            }

            if (room.ship == ship && !weapon.canTargetOwnShip) {
                return@RoomClickListener
            }

            if (weapon is IRoomTargetingWeapon) {
                weapons.selectedTargets.targetRoom(id, room, aimingWithAutofire)
            } else if (weapon is BeamBlueprint.BeamInstance) {
                val mousePos = ConstPoint(gc.input.mouseX, gc.input.mouseY)
                val shipPos = mousePos - game.getEnemyPosition(room.ship)
                val beam = SelectedTarget.BeamAim(weapon, id, room.ship, shipPos)
                // A combined beam goes on through the other enemy ships'
                // windows, or is longer in this one (see WeaponSizes)
                beam.length = WeaponSizes.beamLength(beam.length, weapon.type.combinedCount)
                beam.autofire = aimingWithAutofire
                beamTargeting = beam
                beamTargetingStartPos.set(mousePos)
            }
        }
        game.clickEvent = selectWeaponClickEvent
    }

    /**
     * Turn autofire on or off, for the weapons aimed from now on and those
     * already aimed.
     */
    fun toggleAutofire() {
        autofire = !autofire
        game.sounds.getSampleOrWarn(if (autofire) "autofireOn" else "autofireOff")?.play()

        for (system in ship.weaponSystems) {
            for (target in system.selectedTargets) {
                target.autofire = autofire
            }
        }
    }

    fun systemPowerHotkeyPressed(type: Class<*>, powerUp: Boolean) {
        for (sys in ship.mainSystems) {
            if (sys.javaClass != type) continue

            changeSystemPower(sys, powerUp)
        }
    }

    private fun changeSystemPower(sys: MainSystem, increasePower: Boolean) {
        val prevPower = sys.powerSelected

        if (increasePower) {
            sys.increasePower()
        } else {
            sys.decreasePower()
        }

        if (sys.powerSelected > prevPower) {
            powerUpSound.play()
        } else if (sys.powerSelected < prevPower) {
            powerDownSound.play()
        } else if (increasePower) {
            powerUpFailSound.play()
        }
    }

    private fun updateBeamTargeting(mouseX: Int, mouseY: Int) {
        val beam = beamTargeting ?: return

        val point = Point(beamTargetingStartPos)

        val delta = ConstPoint(mouseX, mouseY) - point

        // If the mouse is too close to where the beam starts, no beam is drawn.
        beam.visible = delta.distToSq(ConstPoint.ZERO) > MIN_BEAM_LENGTH * MIN_BEAM_LENGTH
        if (!beam.visible)
            return

        beam.angle = atan2(delta.y.toDouble(), delta.x.toDouble()).toFloat()

        beam.updateHitRooms()
    }

    private fun targetBeamWeapon() {
        val beam = beamTargeting ?: error("Called targetBeamWeapon without beam")
        beamTargeting = null

        val weaponIndex = ship.hardpoints.indexOfFirst { it.weapon == beam.weapon }
        ship.weaponsFor(weaponIndex)!!.selectedTargets.targetBeam(weaponIndex, beam)
    }

    // Dispatch actions from the UI - this is only called when the game is not paused, so dispatch
    // everything from here so a player can cancel their actions if they remain paused.
    fun update() {
        for (system in ship.weaponSystems) {
            system.selectedTargets.fireChargedWeapons()
        }
    }

    // Whether the shields critical warning was on last frame, for its sound
    private var shieldsCritical = false

    // The time, for the crew boxes of badly hurt crew to blink in
    private var lowHealthBlinkTime = 0f

    fun updateAlways(dt: Float) {
        // Always called, whether or not the player is paused.
        // This is therefore only to be used for UI stuff.
        lowHealthBlinkTime = (lowHealthBlinkTime + dt) % (2 * LOW_HEALTH_BLINK)
        insufficientScrapTimer -= dt
        if (insufficientScrapTimer < 0)
            insufficientScrapTimer = 0f

        // The resource number animations run while paused
        for (anim in resourceDeltaAnimations) {
            anim.update(dt)
        }
        resourceDeltaAnimations.removeIf { it.finished }
    }

    fun render(gc: GameContainer, g: Graphics) {
        // An enemy (or drone) under the mouse, where the player can see it:
        // its name and health. Set first, so the interface over it wins.
        hoveredEnemyUnit?.let { unit -> g.tooltip = enemyUnitTooltip(unit) }

        // The buttons are placed from the bottom of the screen, so they move
        // when the game's size does (in the display options)
        val resized = width != gc.width || height != gc.height
        width = gc.width
        height = gc.height

        // Update the buttons if crew have been added or removed, as this
        // moves around the save/load position buttons.
        // We have to do this here since we can't safely call updateButtons
        // mid-way through drawing.
        if (lastCrewCount != game.playerCrew.size || resized || lastReactorColumns != reactorColumns) {
            updateButtons()
            lastCrewCount = game.playerCrew.size
            lastReactorColumns = reactorColumns
        }

        drawTopBar(g)
        drawSystems(g)
        drawSubSystems(gc)

        // Draw the buttons last, so they don't disappear for a frame
        // when they're updated.
        for (button in buttons) {
            button.draw(g)
        }

        // Draw the slot of a weapon or drone being dragged to another one under the
        // mouse (as it looks unpowered)
        val dragButton = barDrag?.let { barSlotButtons[it] }
        if (dragButton != null && isDraggingInBar) {
            g.pushTransform()
            g.translate((mousePos.x - barDragStart.x).f, (mousePos.y - barDragStart.y).f)
            dragButton.dragPreview = true
            dragButton.draw(g)
            dragButton.dragPreview = false
            g.popTransform()
        }

        // Draw the floating numbers that show a change in a resource
        for (anim in resourceDeltaAnimations) {
            anim.draw(g)
        }

        updatingButtons = false

        // If the player's ship is full of crew, open the window to dismiss one.
        // Don't override another window to do this, however.
        if (currentWindow == null && game.playerHasTooManyCrew()) {
            showShipWindow(ShipWindow.Tab.CREW, true)
        }

        // Likewise for an item or augment the ship has no room for, once
        // the event that gave it is closed (but it can be closed, leaving
        // the item behind)
        if (currentWindow == null && overCapacityPending) {
            overCapacityPending = false
            showShipWindow(ShipWindow.Tab.EQUIPMENT, true)
        }
    }

    private var overCapacityPending = false

    /**
     * Show the equipment window once the current one's closed, to swap the
     * item or augment the ship has no room for (see Ship.overflowItem).
     */
    fun showOverCapacity() {
        overCapacityPending = true
    }

    private fun drawSystems(g: Graphics) {
        // Draw the power tree's energy bars
        val (fullPowerHeight, availablePowerHeight) = drawReactorPower(g)

        // Reactors with more than one column of power bars push the power
        // wire and the systems over to the right.
        // This is how much wider the stack is than vanilla's 28px.
        val reactorExtraWidth = when (reactorColumns) {
            1 -> 0
            else -> (reactorColumns - 1) * REACTOR_COLUMN_SPACING + (REACTOR_COLUMN_SPACING - 2) - 28
        }
        val powerTreeX = 86 - 53 + reactorExtraWidth
        val powerTreeY = height - 21 - 302
        val powerTreeMaskY = powerTreeY + 27
        val powerIsAvailable = ship.powerAvailable > 0
        val powerMaskImg = game.getImg("img/wire_left_mask.png")
        val powerWireImg = game.getImg("img/wireUI/wire_full.png")

        Utils.drawStenciled(Utils.StencilMode.BLOCKING, {
            powerMaskImg.draw(powerTreeX.f + 4, powerTreeMaskY.f - fullPowerHeight)
        }) {
            powerWireImg.draw(powerTreeX.f, powerTreeY.f, 0.5f)
        }

        // Have to check since this draws the first system's power wire too,
        // and it's not masked off.
        if (powerIsAvailable) {
            Utils.drawStenciled(Utils.StencilMode.BLOCKING, {
                powerMaskImg.draw(powerTreeX.f + 4, powerTreeMaskY.f - availablePowerHeight)
            }) {
                // The power wire is taller than the mask (and way taller
                // than it needs to be), so the top shows if almost all
                // the ship's power is used.
                val maxHeight = powerMaskImg.height
                val exclusion = powerWireImg.height - maxHeight
                powerWireImg.draw(
                    powerTreeX.f, powerTreeY.f + exclusion,
                    powerTreeX.f + powerWireImg.width, powerTreeY.f + powerWireImg.height,
                    0f, exclusion.f,
                    powerWireImg.width.f, powerWireImg.height.f
                )
            }
        }

        // Draw the systems and the wires under them
        // If there's several weapons systems, their power buttons go next to
        // each other, followed by their boxes stacked on top of each other.
        val weaponSystemsInUI = ship.mainSystems.filterIsInstance<Weapons>()
        val lastWeapons = weaponSystemsInUI.lastOrNull()
        val weaponBoxSlots = weaponSystemsInUI.maxOfOrNull { it.uiSlots.count() } ?: 0
        var weaponBoxX: Int? = null

        // The same goes for drones systems
        val droneSystemsInUI = ship.mainSystems.filterIsInstance<Drones>()
        val lastDrones = droneSystemsInUI.lastOrNull()
        var droneBoxX: Int? = null
        var powerX = 58 + reactorExtraWidth
        val powerY = height - 69
        for ((i, system) in ship.mainSystems.withIndex()) {
            // Draw the power bar to the next item
            val wireX = powerX + 31
            val wireY = powerY +
                    45 + // Top of the power button image to the bottom
                    12 + // Distance between the power button and the main horizontal wire
                    -26 // Go back up to the top of the wire image

            // Save the position of the weapon and drone power icons, since
            // they're used to position the weapon and drone selection boxes.
            if (system == lastWeapons)
                weaponBoxX = wireX + 1
            if (system == lastDrones)
                droneBoxX = wireX + 1

            if (updatingButtons) {
                val powerPos = ConstPoint(powerX + 19, powerY + 19)
                buttons += SystemPowerButton(powerPos, system)

                buttons += system.makeExtraButtons(powerPos)
            }

            powerX += when {
                system == lastWeapons -> 48 + weaponBoxSlots * 97
                system is Weapons -> 36
                system == lastDrones -> 48 + ship.droneSlots!! * 97
                system is Drones -> 36
                system.insertButtonSpace -> 54
                else -> 36
            }

            // Don't draw the bar under the last item - since each item draws the
            // wire to the next item, this would result in a wire sticking out.
            if (i == ship.mainSystems.size - 1)
                continue

            val lastSystem = i == ship.mainSystems.size - 2

            val wireWidth = when {
                // Remember that we'll only run this if this isn't the last system,
                // so we won't be drawing a wire under weapons if drones isn't installed.
                // Draw the wire that goes under the weapons to the drone system
                system == lastWeapons -> boxWireName(weaponBoxSlots)
                system is Weapons -> "36"

                // In vanilla the drones are always last, but if there's more
                // systems after them, the wire has to go under their box too.
                system == lastDrones -> boxWireName(ship.droneSlots!!)
                system is Drones -> "36"

                system.insertButtonSpace -> "54"
                else -> "36"
            }
            // Note there's no non-cap version of the wires that go under the
            // weapons and drones boxes, as those are always last in vanilla,
            // so use the cap one instead.
            val image = when (lastSystem) {
                true -> game.getImg("img/wireUI/wire_${wireWidth}_cap.png")
                false -> game.getImgIfExists("img/wireUI/wire_$wireWidth.png")
                    ?: game.getImg("img/wireUI/wire_${wireWidth}_cap.png")
            }

            // The wire does indeed get drawn twice if power is available, the colours match vanilla.
            image.draw(wireX.f, wireY.f, 0.5f)
            if (powerIsAvailable) {
                image.draw(wireX.f, wireY.f)
            }
        }

        // If a weapon is being targeted, check that our click event is still the game's active click
        // event. This ensures that if the click event is cancelled or overridden, targeting mode
        // is disabled.
        if (targetingSelectedWeapon != null && selectWeaponClickEvent != game.clickEvent) {
            targetingSelectedWeapon = null
            selectWeaponClickEvent = null
        }

        // Find the longest charge time of all equipped weapons
        maxWeaponChargeTime = ship.hardpoints.stream()
            .map { it.weapon }
            .filter { Objects.nonNull(it) }
            .map { it!!.chargeTime }
            .reduce { a, b -> Math.max(a, b) }
            .orElse(1f)

        // The autofire button is a tab under the right-hand corner of the
        // (bottom) weapons box, like its label on the left
        if (updatingButtons && weaponSystemsInUI.isNotEmpty()) {
            val slots = weaponSystemsInUI.last().uiSlots.count()
            // Just past the right of the box's outline
            val boxRight = weaponBoxX!! + 25 + slots * 97
            buttons += AutofireButton(boxRight, weaponBoxY + 61)
        }

        // Draw the weapons boxes, one for each weapons system.
        // Note that some modded ships don't have a weapons system
        for ((index, weapons) in weaponSystemsInUI.withIndex()) {
            // The first system's box is on top, and the last one's in the normal position
            val boxX = weaponBoxX!!
            val boxY = weaponBoxY - (weaponSystemsInUI.size - 1 - index) * WEAPON_BOX_STACK_SPACING
            drawWeaponBox(boxX, "weapons_label", weapons.uiSlots.count(), boxY, weapons.uiIndex)

            // i is the hardpoint index, slotInBox is the position in this box
            for ((slotInBox, i) in weapons.uiSlots.withIndex()) {
                if (!updatingButtons)
                    continue

                // The origin position of the weapon button
                val wx = boxX + WEAPON_BOX_GLOW + 12 + slotInBox * 97
                val wy = boxY + 12 + 4

                val weapon = ship.hardpoints[i].weapon

                buttons += object : WeaponDroneButton(ConstPoint(wx, wy), i, ConstPoint(WEAPON_BUTTON_WIDTH, 39)) {
                    init {
                        barSlotButtons[BarSlot(null, i)] = this
                    }

                    override val empty: Boolean get() = weapon == null
                    override val name: String get() = weapon!!.type.translateShort(game)
                    override val requiredPower: Int get() = weapon!!.type.power
                    override val chargeTime: Float get() = weapon!!.chargeTime
                    override val chargeProgress: Float get() = weapon!!.chargeProgress
                    override val zoltanPower: Int get() = weapons.getWeaponForcedPower(i)
                    override val isPowered: Boolean get() = weapon!!.isPowered
                    override val isCharged: Boolean get() = weapon!!.isCharged
                    override val isTargeted: Boolean get() = weapons.selectedTargets.getTarget(i) != null
                    override val isOnAutofire: Boolean get() = weapons.selectedTargets.getTarget(i)?.autofire == true
                    override val isSelectingTarget: Boolean get() = targetingSelectedWeapon == i
                    override val hasChargeBar: Boolean get() = true
                    override val isBeingHacked: Boolean get() = weapons.isHackActive
                    override val droneCooldownProgress: Float? get() = null
                    override val tooltip: ITooltipProvider? get() = weapon?.weaponBarTooltip

                    override fun click(button: Int) {
                        when (button) {
                            Input.MOUSE_LEFT_BUTTON -> weaponHotkeyPressed(i, false)
                            Input.MOUSE_RIGHT_BUTTON -> weaponHotkeyPressed(i, true)
                        }
                    }

                    override fun draw(g: Graphics) {
                        super.draw(g)

                        if (weapon != null && weapon.type.chargeLevels != null) {
                            drawChargedShots()
                        }

                        if (weapon != null && weapon.type.boost != null) {
                            drawChainIcon()
                        }
                    }

                    private fun drawChargedShots() {
                        requireNotNull(weapon)

                        val filter = mainColour
                        val max = weapon.maxTotalCharges
                        val ready = weapon.totalReadyCharges
                        val width = 11
                        val baseX = pos.x + (size.x - max * 11) / 2
                        val y = pos.y + 25

                        for (charge in 0 until max) {
                            val suffix = when {
                                charge < ready -> "use"
                                else -> "on"
                            }

                            val img = game.getImg("img/combatUI/icon_chain_$suffix.png")
                            val x = baseX + charge * width
                            img.draw(x, y, filter)
                        }
                    }

                    private fun drawChainIcon() {
                        requireNotNull(weapon)
                        val boost = weapon.type.boost
                        requireNotNull(boost)

                        val clockOn = when {
                            weapon.chainCount == 0 -> false
                            !isPowered -> false
                            else -> true
                        }

                        val mode = when (clockOn) {
                            true -> "on"
                            false -> "off"
                        }
                        val iconName = when (boost.type) {
                            AbstractWeaponBlueprint.BoostType.COOLDOWN -> "img/combatUI/icon_clock_$mode.png"
                            AbstractWeaponBlueprint.BoostType.DAMAGE -> "img/combatUI/icon_arrow_$mode.png"
                        }
                        val icon = game.getImg(iconName)

                        val filter = when (clockOn) {
                            true -> mainColour
                            false -> Colour.white // Don't filter the already-greyed image
                        }
                        icon.draw(pos.x + 32, pos.y + 20, filter)

                        if (clockOn) {
                            val text = "+" + weapon.chainCount
                            weaponNameText.drawString(pos.x + 50f, pos.y + 31f, text, filter)
                        }
                    }
                }
            }
        }

        // Draw the drone areas, if they're installed. Like the weapons, if
        // there's several drones systems their boxes are stacked up, in the
        // ship's order of them (Ship.droneSystems), the first at the top, as
        // in the ship window's equipment and where bought drones go
        val droneBoxes = ship.droneSystems
        for ((index, drones) in droneBoxes.withIndex()) {
            val boxX = droneBoxX!!
            val boxY = weaponBoxY - (droneBoxes.size - 1 - index) * WEAPON_BOX_STACK_SPACING
            drawWeaponBox(boxX, "drones_label", ship.droneSlots!!, boxY, drones.uiIndex)

            // Draw the in-operation drone boxes - this seems very similar to weapons
            for ((i, info) in drones.drones.withIndex()) {
                val drone = info?.type
                if (!updatingButtons)
                    break

                // The origin position of the drone button
                val pos = ConstPoint(
                    boxX + WEAPON_BOX_GLOW + 4 + i * 97,
                    boxY + 12 + 4
                )

                buttons += object : WeaponDroneButton(pos, i, ConstPoint(95, 39)) {
                    init {
                        barSlotButtons[BarSlot(drones, i)] = this
                    }

                    override val empty: Boolean get() = drone == null
                    override val name: String get() = drone!!.translateShort(game)
                    override val requiredPower: Int get() = drone!!.power
                    override val chargeTime: Float get() = error("Can't get charge time for drone")
                    override val chargeProgress: Float get() = error("Can't get charge progress for drone")
                    override val zoltanPower: Int get() = drones.getDroneForcedPower(i)
                    override val isPowered: Boolean get() = info!!.instance?.isPowered == true
                    override val isCharged: Boolean get() = isPowered // Always use the charged colour
                    override val isTargeted: Boolean get() = false
                    override val isSelectingTarget: Boolean get() = false
                    override val hasChargeBar: Boolean get() = false
                    override val isBeingHacked: Boolean get() = drones.isHackActive && info?.instance != null
                    override val droneCooldownProgress: Float?
                        get() = info?.cooldown?.let { it / AbstractDrone.DRONE_DESTROYED_COOLDOWN }
                    override val tooltip: ITooltipProvider? get() = null // TODO

                    override fun click(button: Int) {
                        val wasPowered = info?.instance?.isPowered ?: false

                        if (button == Input.MOUSE_LEFT_BUTTON) {
                            drones.setDronePower(i, true)
                        }
                        if (button == Input.MOUSE_RIGHT_BUTTON) {
                            drones.setDronePower(i, false)
                        }

                        val nowPowered = info?.instance?.isPowered ?: false
                        when {
                            wasPowered && !nowPowered -> powerDownSound.play()
                            !wasPowered && nowPowered -> powerUpSound.play()
                            !wasPowered && button == Input.MOUSE_LEFT_BUTTON -> powerUpFailSound.play()
                        }
                    }

                    override fun draw(g: Graphics) {
                        super.draw(g)

                        if (info?.instance != null) {
                            game.getImg("img/ui_icons/icon_drones.png").draw(pos.x + 43 - 9, pos.y + 18 - 8, mainColour)
                        }
                    }
                }
            }
        }
    }

    /**
     * Draw the crew selection rectangle, if appropriate. This is done after
     * the enemy windows, so it isn't hidden under them while selecting
     * boarders.
     */
    fun renderCrewSelection(g: Graphics) {
        val csr = crewSelectionRectangle
        if (csr != null && !isCrewSelectionPoint) {
            g.colour = Colour.white
            val size = csr.second - csr.first
            g.drawRect(csr.first.x.f, csr.first.y.f, size.x.f, size.y.f)
            g.drawRect(csr.first.x + 1f, csr.first.y + 1f, size.x - 2f, size.y - 2f)
        }
    }

    // How much wider the subsystems box is than vanilla, to fit extra
    // batteries and sensors.
    private val subsystemBoxExtraWidth: Int
        get() {
            val extraBatteries = (ship.batterySystems.size - 1).coerceAtLeast(0)
            val extraSensors = (ship.sensorSystems.size - 1).coerceAtLeast(0)
            return extraBatteries * BATTERY_SPACING + extraSensors * SUBSYSTEM_SPACING
        }

    private fun drawSubSystems(gc: GameContainer) {
        // The box position, not including the glow. It's moved left as it's
        // widened, so it stays on the screen.
        val boxX = gc.width - 252 - subsystemBoxExtraWidth
        val boxY = gc.height - 47

        // The box is widened to fit any extra sensors and batteries. Its top
        // edge has a notch for each power button, so this repeats a notch
        // (and the bit after it) for each extra one: the sensors' before the
        // doors' notch, and the battery's after its own.
        val extraBatteries = (ship.batterySystems.size - 1).coerceAtLeast(0)
        val extraSensors = (ship.sensorSystems.size - 1).coerceAtLeast(0)
        game.getImg("img/box_subsystems4.png").drawWithInsertions(
            boxX - 6, boxY - 6, listOf(
                Image.Insertion(SUBSYSTEM_BOX_DOORS_X, SUBSYSTEM_SPACING, extraSensors * SUBSYSTEM_SPACING),
                Image.Insertion(SUBSYSTEM_BOX_BATTERY_END_X, BATTERY_SPACING, extraBatteries * BATTERY_SPACING),
            ), emptyList()
        )

        // Draw the subsystems label
        val label = game.translator["subsystems_label"]
        font.drawString(boxX + 7f, boxY + 23f, label, UI_TEXT_COLOUR_1)

        // Add all the system buttons
        if (!updatingButtons) {
            return
        }

        fun addSubSys(x: Int, sys: SubSystem?) {
            if (sys == null)
                return

            val powerPos = ConstPoint(boxX + x, boxY - 21)
            buttons += SystemPowerButton(powerPos, sys)
            buttons += sys.makeExtraButtons(powerPos)
        }

        // Any extra sensors systems go next to the first, and everything
        // after them moves over to make room.
        addSubSys(6, ship.piloting)
        val sensors = ship.sensorSystems.ifEmpty { listOf(null) }
        for ((i, system) in sensors.withIndex()) {
            addSubSys(42 + i * SUBSYSTEM_SPACING, system)
        }
        val extraSensorsWidth = (sensors.size - 1) * SUBSYSTEM_SPACING
        addSubSys(78 + extraSensorsWidth, ship.doorsSystem)
        for ((i, battery) in ship.batterySystems.withIndex()) {
            addSubSys(129 + extraSensorsWidth + i * BATTERY_SPACING, battery)
        }
    }

    /**
     * How many Zoltan shields' worth of super-shield the ship has (several
     * Zoltan shields, or shield overcharger drones), a row of points each.
     */
    private val superShieldRows: Int
        get() = (ship.maxSuperShield + AugZoltanShield.SHIELD_PER_AUGMENT - 1) / AugZoltanShield.SHIELD_PER_AUGMENT

    /**
     * True if the super-shield gets its own panel (see
     * drawStackedSuperShield): it's more than [MAX_SUPER_SHIELD_ROWS_IN_BOXES]
     * Zoltan shields, or more than there are shields indicators. Otherwise
     * each row's points go in a shields indicator, under its bubbles, where
     * the recharge bar would be.
     */
    private val hasStackedSuperShield: Boolean
        get() = superShieldRows > MAX_SUPER_SHIELD_ROWS_IN_BOXES ||
                superShieldRows > ship.shieldSystems.size.coerceAtLeast(1)

    /**
     * Draw the super-shield of a ship with several Zoltan shields, with a
     * row of points for each shield. The bottom row is used up first.
     *
     * [x] and [y] are the panel's top-left corner.
     */
    private fun drawStackedSuperShield(g: Graphics, x: Int, y: Int) {
        val perShield = AugZoltanShield.SHIELD_PER_AUGMENT
        val rows = (ship.maxSuperShield + perShield - 1) / perShield
        val rowSpacing = 8
        val padding = 4

        // The points are as wide as the vanilla super-shield bar, inside a panel
        val panelX = x
        val panelWidth = 92 + padding * 2
        val panelHeight = rows * rowSpacing - 2 + padding * 2

        // A white two-pixel frame, like the shield indicator's
        g.colour = SUPER_SHIELD_PANEL_BACKGROUND
        g.fillRect(panelX, y, panelWidth, panelHeight)
        g.colour = SYS_ENERGY_DEPOWERED
        g.drawRect(panelX, y, panelWidth - 1, panelHeight - 1)
        g.drawRect(panelX + 1, y + 1, panelWidth - 3, panelHeight - 3)

        for (row in 0 until rows) {
            val rowY = y + padding + row * rowSpacing
            val pointsInRow = (ship.superShield - row * perShield).coerceIn(0, perShield)

            var pointX = panelX + padding
            for (i in 0 until perShield) {
                // The middle point is one pixel shorter, as in vanilla
                val width = if (i == 2) 16 else 17

                if (i < pointsInRow) {
                    g.colour = SYS_ENERGY_ACTIVE
                    g.fillRect(pointX, rowY, width, 6)
                } else {
                    g.colour = SYS_ENERGY_DEPOWERED
                    g.drawRect(pointX, rowY, width - 1, 6 - 1)
                }
                pointX += width + 2
            }
        }

        if (mousePos.x in panelX..panelX + panelWidth && mousePos.y in y..y + panelHeight) {
            g.tooltip = shieldTooltip
        }
    }

    /**
     * Draw the stack of reactor power icons.
     *
     * Returns the height of the stack of power bars, and the height of the top
     * of the highest non-empty power bar.
     */
    private fun drawReactorPower(g: Graphics): Pair<Int, Int> {
        // Top-left corner of the bottom power bar
        val x = 12
        val baseY = height - 34

        val barHeight = 7
        val spacing = barHeight + 2

        val availablePower = ship.powerAvailable
        val totalPower = reactorTotalPower

        // Reactors bigger than vanilla allows would be tall enough to run into
        // the crew list, so draw them in extra columns of 25 to the right,
        // half the width of vanilla's. Each column is filled from the bottom
        // before the next one.
        val barWidth = when (reactorColumns) {
            1 -> 28
            else -> REACTOR_COLUMN_SPACING - 2
        }
        fun barX(index: Int) = x + (index / SINGLE_COLUMN_REACTOR_POWER) * REACTOR_COLUMN_SPACING
        fun barY(index: Int) = baseY - spacing * (index % SINGLE_COLUMN_REACTOR_POWER)

        var nextPowerIdx = 0
        for (type in EnergySource.GLOBAL_TYPES) {
            val available = ship.powerAvailableTypes[type] ?: 0

            for (i in 0 until available) {
                type.drawReactorPowerBar(g, barX(nextPowerIdx), barY(nextPowerIdx), barWidth, barHeight)

                nextPowerIdx++
            }
        }

        // The power the reactor can still be upgraded to is shown faintly,
        // so the stack (and the systems right of it) don't move as it grows
        g.colour = REACTOR_UNPURCHASED
        for (index in totalPower until reactorCapacity) {
            g.drawRect(barX(index), barY(index), barWidth - 1, 7 - 1)
        }

        while (nextPowerIdx < totalPower) {
            val barX = barX(nextPowerIdx)
            val y = barY(nextPowerIdx)

            // If this power is blocked (eg by an ion storm), show it as blue
            // with a line through it.
            if (nextPowerIdx >= totalPower - ship.blockedReactorPower) {
                g.colour = SYS_ENERGY_ION_STORM
                g.drawRect(barX, y, barWidth - 1, 7 - 1)
                g.drawLine(barX, y + 7, barX + barWidth, y)
            } else {
                g.colour = SYS_ENERGY_DEPOWERED
                g.drawRect(barX, y, barWidth - 1, 7 - 1)
            }

            nextPowerIdx++
        }

        // The power wire next to the stack is lit up as far as the available
        // power, so with several columns, scale that to the stack's height.
        val rows = totalPower.coerceAtMost(SINGLE_COLUMN_REACTOR_POWER)
        val poweredRows = when {
            totalPower <= 0 -> 0
            else -> (availablePower * rows + totalPower - 1) / totalPower
        }
        val topOfStack = (rows - 1) * spacing
        val topOfPowered = (poweredRows - 1) * spacing
        return Pair(topOfStack, topOfPowered)
    }

    // The ship's reactor power, including any from backup batteries, which
    // can't take it past the biggest reactor
    private val reactorTotalPower: Int
        get() {
            val withBatteries = ship.purchasedReactorPower + ship.batterySystems.sumOf { it.contributedPower }
            return withBatteries.coerceAtMost(maxOf(ship.maxReactorPower, ship.purchasedReactorPower))
        }

    // The most power bars the reactor's stack has room for: as much as it
    // can be upgraded to, or has now (such as in debug mode)
    private val reactorCapacity: Int
        get() = maxOf(ship.maxReactorPower, reactorTotalPower)

    // The number of columns the reactor's power bars are drawn in. This is
    // for the biggest the reactor can be, so it doesn't change as it's
    // upgraded, moving the systems over.
    private val reactorColumns: Int
        get() = ((reactorCapacity + SINGLE_COLUMN_REACTOR_POWER - 1) / SINGLE_COLUMN_REACTOR_POWER).coerceAtLeast(1)

    /**
     * Split the filled cells of a hull bar into [parts] parts each, for
     * bigger ships whose cells each show several points of hull.
     *
     * The bar's [image] (which is drawn at [x], [y] and has the shape of the
     * cells in it) is used to draw the lines, so they're the same shape as
     * the cells. The cells are [cellWidth] wide, every [pitch].
     */
    fun drawHullCellDividers(
        image: Image, x: Float, y: Float, filledWidth: Float,
        pitch: Int, cellWidth: Int, parts: Int
    ) {
        if (parts <= 1)
            return

        val height = image.height.f
        var cellX = 0
        while (cellX < filledWidth) {
            for (part in 1 until parts) {
                val dividerX = (cellX + cellWidth * part / parts).f
                if (dividerX < filledWidth) {
                    image.draw(
                        x + dividerX, y, x + dividerX + 1, y + height,
                        dividerX, 0f, dividerX + 1, height,
                        1f, HULL_CELL_DIVIDER
                    )
                }
            }
            cellX += pitch
        }
    }

    fun renderMenus(container: GameContainer, g: Graphics) {
        currentWindow?.let { renderSingleMenu(container, g, it) }
        pauseWindow?.let { renderSingleMenu(container, g, it) }
    }

    private fun renderSingleMenu(container: GameContainer, g: Graphics, window: Window) {
        if (!window.appliesSelfTint) {
            drawWindowBackgroundTint(g)
        }

        // Don't let tooltips show up through the tint
        g.tooltip = null

        // Centre the window.
        window.position = window.windowCentreOffset + ConstPoint(
            (container.width - window.size.x) / 2,
            (container.height - window.size.y) / 2
        )

        window.draw(g)
    }

    fun drawWindowBackgroundTint(g: Graphics) {
        // Add the tint over all the regular game stuff to make the window clearer.
        g.colour = Colour(0f, 0f, 0f, 0.65f)
        g.fillRect(0, 0, width, height)
    }

    private fun drawTopBar(g: Graphics) {
        val isHullBoxRed = hullWarning25.isFlashingHigh || hullWarning50.isFlashingHigh || hullWarning75.isFlashingHigh
        val hullBoxImage = when {
            isHullBoxRed -> game.getImg("img/statusUI/top_hull_red.png")
            else -> game.getImg("img/statusUI/top_hull.png")
        }
        hullBoxImage.draw(0f, 0f)

        val hullLabelImg = when {
            isHullBoxRed -> game.getImg("img/statusUI/top_hull_red_label.png")
            else -> game.getImg("img/statusUI/top_hull_label.png")
        }
        val hullText = game.translator["status_hull"]
        UIUtils.drawTab(font, hullText, hullLabelImg, 0f, 0f, 10f, 30f)
        font.drawString(9f, 25f, hullText, UI_TEXT_COLOUR_1)

        if (mousePos.x in 10..370 && mousePos.y in 20..45) {
            g.tooltip = hullTooltip
        }

        // Draw the hull bar

        // TODO support ships with more or less health

        // Bigger ships have more hull, so each cell shows that many points
        // of it, and is split into parts to show that.
        val perCell = ship.hullScale
        val cells = ship.health.f / perCell
        val hpW = 12f * cells
        val healthColour = when {
            cells < 10 -> SHIP_HEALTH_LOW
            cells < 20 -> SHIP_HEALTH_MED
            else -> SHIP_HEALTH_HIGH
        }
        val mask = game.getImg("img/statusUI/top_hull_bar_mask.png")
        val hpH = mask.height.f
        mask.draw(11f, 0f, 11f + hpW, hpH, 0f, 0f, hpW, hpH, 1f, healthColour)
        drawHullCellDividers(mask, 11f, 0f, hpW, 12, 11, perCell)

        val lastFraction = lastHealth.f / ship.maxHealth
        val newFraction = ship.health.f / ship.maxHealth
        lastHealth = ship.health

        if (newFraction < 0.25f) {
            // Constantly trigger the hull critical warning
            hullWarning25.startFor(5f)
        } else if (lastFraction > 0.50f && newFraction < 0.50f) {
            hullWarning50.startFor(9f)
        } else if (lastFraction > 0.75f && newFraction < 0.75f) {
            hullWarning75.startFor(9f)
        }

        // Stop animations when we're healed, or our health drops
        // enough to trigger the next animation.
        if (newFraction > 0.25f) {
            hullWarning25.stop()
        }
        if (newFraction !in 0.25f..0.5f) {
            hullWarning50.stop()
        }
        if (newFraction !in 0.5f..0.75f) {
            hullWarning75.stop()
        }

        hullWarning25.draw(g)
        hullWarning50.draw(g)
        hullWarning75.draw(g)

        // Draw the scrap indicator
        val isScrapRed = insufficientScrapTimer != 0f &&
                (insufficientScrapTimer / INSUFFICIENT_SCRAP_FLASH_TIME).toInt() % 2 == 0
        val scrapPath = if (isScrapRed) "img/statusUI/top_scrap_red.png" else "img/statusUI/top_scrap.png"
        game.getImg(scrapPath).draw(374f + 8 - 5, 0f)
        numberFont.drawStringCentred(416f, 35f, 87f, ship.scrap.toString(), UI_SCRAP_TEXT_COLOUR)

        val deltaScrap = ship.scrap - lastResources.scrap
        if (deltaScrap != 0) {
            lastResources.scrap = ship.scrap
            val rdt = ResourceDeltaText(460f, 30f, deltaScrap)
            rdt.x += VisualRandom.nextInt(-10, 10)
            resourceDeltaAnimations.add(rdt)
        }

        if (mousePos.x in 383..505 && mousePos.y in 11..46) {
            g.tooltip = scrapTooltip
        }

        // Going down the side, for the shields/oxygen
        val shieldY = 43

        // Draw a shields indicator for each shields system, one under the
        // other. Most ships have one (or none, in which case the indicator
        // is shown turned off).
        val shieldSystems: List<Shields?> = ship.shieldSystems.ifEmpty { listOf(null) }
        // The warning's only for when none of the shields systems can raise
        // a layer: a ship with two still has its shields with one broken
        var allShieldsBroken = true
        var anyShields = false
        for ((index, shields) in shieldSystems.withIndex()) {
            val boxY = shieldY + index * SHIELD_BOX_SPACING

            val broken = shields != null
                    && shields.energyLevels >= 2 // Don't warn for Zoltan B, which starts on 1
                    && shields.energyLevels - shields.damagedEnergyLevels < 2
            if (shields != null && shields.energyLevels >= 2) {
                anyShields = true
                allShieldsBroken = allShieldsBroken && broken
            }

            val shieldBox = when {
                broken -> game.getImg("img/statusUI/top_shields4_red.png")
                shields == null || shields.selectedShieldBars == 0 -> game.getImg("img/statusUI/top_shields4_off.png")
                else -> game.getImg("img/statusUI/top_shields4_on.png")
            }
            shieldBox.draw(0, boxY)
            val hacked = shields?.isHackActive == true
            if (hacked) {
                game.getImg("img/statusUI/top_shields4_purple.png").draw(31 - 8, boxY)
            }

            // The super-shield is shared by the whole ship: each Zoltan
            // shield's points go under the bubbles of an indicator of its
            // own, the bottom one's used up first. If there are too many of
            // them, they get their own panel instead.
            val superShieldPoints = (ship.superShield - index * AugZoltanShield.SHIELD_PER_AUGMENT)
                .coerceIn(0, AugZoltanShield.SHIELD_PER_AUGMENT)
            if (superShieldPoints > 0 && !hasStackedSuperShield) {
                // Draw the super-shield bar where the recharge
                // bar would normally go.
                g.colour = SYS_ENERGY_ACTIVE
                var x = 33
                for (i in 0 until superShieldPoints) {
                    // The middle bar is one pixel shorter to make everything
                    // else line up properly.
                    val width = if (i == 2) 16 else 17

                    g.fillRect(x.f, boxY + 34f + 2f, width.f, 6f)
                    x += width + 2
                }
            } else if (shields != null && shields.rechargeTimer != 0f) {
                // Draw the recharge bar
                val progress = (shields.rechargeTimer / shields.rechargeDelay).coerceIn(0f..1f)
                g.colour = when (hacked) {
                    true -> SHIELD_BAR_HACKED
                    false -> SHIELD_BAR_NORMAL
                }
                g.fillRect(
                    33f, boxY + 34f + 2f,
                    92f * progress, 6f
                )
            }

            if (shields != null) {
                // Draw the shield bubble indicators
                val shieldOnImg = game.getImg("img/statusUI/top_shieldsquare1_on.png")
                val shieldOffImg = game.getImg("img/statusUI/top_shieldsquare1_off.png")
                val shieldHackedOnImg = game.getImg("img/statusUI/top_shieldsquare1_hacked_charged.png")
                val shieldHackedOffImg = game.getImg("img/statusUI/top_shieldsquare1_hacked.png")

                for (i in 0 until shields.selectedShieldBars) {
                    val charged = shields.activeShields > i

                    val img = when {
                        hacked && charged -> shieldHackedOnImg
                        hacked -> shieldHackedOffImg

                        charged -> shieldOnImg
                        else -> shieldOffImg
                    }
                    img.draw(30 + 23 * i, boxY + 4)
                }
            }

            if (mousePos.x in 12..128 && mousePos.y in boxY + 7..boxY + 34) {
                g.tooltip = shieldTooltip
            }
        }

        if (ship.shieldSystems.isNotEmpty()) {
            val critical = anyShields && allShieldsBroken
            when {
                critical -> shieldsWarning.startFor(1f)
                else -> shieldsWarning.stop()
            }
            // (with vanilla's warning sound as it comes on)
            if (critical && !shieldsCritical)
                game.sounds.getSample("warning").play()
            shieldsCritical = critical
            shieldsWarning.draw(g)
        }

        val shieldsEndX = 122

        // With several Zoltan shields, show each one's points in its own row,
        // in a panel under the fuel and missiles counters, right of the
        // shields indicators.
        if (hasStackedSuperShield) {
            drawStackedSuperShield(g, SUPER_SHIELD_PANEL_X, shieldY + SUPER_SHIELD_PANEL_Y)
        }

        // Everything below the shields moves down to fit any extra indicators
        val extraShieldsHeight = (shieldSystems.size - 1) * SHIELD_BOX_SPACING

        fun drawSmallCounter(
            name: String,
            x: Int,
            numAreaOffset: Int,
            count: Int, delta: Int,
            redThreshold: Int,
            tooltip: ITooltipProvider
        ) {
            val suffix = when {
                count <= redThreshold -> "_red"
                else -> ""
            }
            game.getImg("img/statusUI/top_${name}_on$suffix.png").draw(x, shieldY)

            val areaWidth = 35
            val areaStart = x.f + 33 + numAreaOffset

            // Three digits don't fit in the box in the usual font, so they're
            // a little smaller
            val text = count.toString()
            val font = if (count >= 100) smallNumberFont else numberFont
            val textWidth = font.getWidth(text)
            val textInternalX = ceil((areaWidth - textWidth) / 2f).toInt()
            val textY = if (font === numberFont) shieldY + 27f else shieldY + 25f

            font.drawString(areaStart + textInternalX, textY, text, Colour.white)

            if (delta != 0) {
                val rdt = ResourceDeltaText(x + 40f, 65f, delta)
                resourceDeltaAnimations.add(rdt)
            }

            if (mousePos.x in x + 7..x + 7 + 59 && mousePos.y in shieldY + 7..shieldY + 7 + 26) {
                g.tooltip = tooltip
            }
        }

        // Say what kind of nebula the player just arrived in
        val nebulaHere = game.currentBeacon.nebulaType?.let { Pair(game.currentBeacon, it) }
        if (nebulaWarningFor != nebulaHere) {
            nebulaWarningFor = nebulaHere
            nebulaWarnings.values.forEach { it.stop() }
            nebulaHere?.let { nebulaWarnings[it.second]?.startFor(NEBULA_WARNING_TIME) }
        }
        // (or all the time, with every warning shown at once, and a dark
        // nebula's all the time it's there, as the drones are off all that time)
        if (XftlDebug.showAllHazardWarnings || nebulaHere?.second == Beacon.EnvironmentType.DARK)
            nebulaHere?.let { nebulaWarnings[it.second]?.startFor(NEBULA_WARNING_TIME) }
        // The environmental hazard imminent warnings (eg 'ion pulse imminent').
        // There may be a sun and a pulsar at once, with both warnings at
        // the same time, so the pulsar's then goes under the sun's
        val parts = game.currentBeacon.getEnvironment(game).parts
        val all = XftlDebug.showAllHazardWarnings
        val sunFlare = parts.any { it is SunEnvironment && (it.showWarning || all) }
        val pulsarPulse = parts.any { it is PulsarEnvironment && (it.showWarning || all) }

        for (warning in nebulaWarnings.values) {
            // Centred at the top of the screen, clear of the enemy windows
            // and the settings button. In a nebula with a sun or pulsar too
            // (an extreme hazard), it moves over to make room for their
            // warnings, just left of the middle.
            val shift = if (sunFlare || pulsarPulse) NEBULA_WARNING_SHIFT else 0
            warning.centre = ConstPoint(width / 2 + shift, NEBULA_WARNING_Y)
            warning.draw(g)
        }

        // An Anti-Ship Battery's icon, as vanilla's, between the ships at the
        // top, with what it is under the mouse: the warnings go below it
        val battery = game.currentBeacon.getAntiShipBattery(game)
        val iconShift = if (battery != null) drawBatteryIcon(g, battery) else 0
        // Its 'target locked' warning goes right under it, and the others
        // under that
        val batteryWarning = battery != null && (battery.showWarning || all && battery.targetsPlayer)
        val warningShift = iconShift + if (batteryWarning) BATTERY_WARNING_HEIGHT else 0
        fun below(point: ConstPoint) = ConstPoint(point.x, point.y + warningShift)

        if (sunFlare) {
            sunWarning.centre = below(SUN_WARNING)
            sunWarning.draw(g)
        }
        if (pulsarPulse) {
            pulsarWarning.centre = below(if (sunFlare) PULSAR_WARNING_BELOW_SUN else PULSAR_WARNING)
            pulsarWarning.draw(g)
        }
        if (batteryWarning) {
            asbWarning.centre = BATTERY_WARNING
            asbWarning.draw(g)
        }

        val deltaFuel = ship.fuelCount - lastResources.fuel
        val deltaMissiles = ship.missilesCount - lastResources.missiles
        val deltaDrones = ship.dronesCount - lastResources.droneParts
        lastResources.fuel = ship.fuelCount
        lastResources.missiles = ship.missilesCount
        lastResources.droneParts = ship.dronesCount

        // Fuel shows as red when there's three or less jumps' worth left.
        drawSmallCounter("fuel", shieldsEndX, 0, ship.fuelCount, deltaFuel, 3 * ship.fuelPerJump, fuelTooltip)

        // Missiles only show as red when there's none left.
        drawSmallCounter("missiles", shieldsEndX + 66, 1, ship.missilesCount, deltaMissiles, 0, missileTooltip)

        // Same for drones.
        drawSmallCounter("drones", shieldsEndX + 66 + 70, -2, ship.dronesCount, deltaDrones, 0, droneTooltip)

        // Oxygen and evasion indicator

        // Note we hide the oxygen critical warning if the player doesn't
        // have an oxygen system. This is Hyperspace behaviour, but it doesn't
        // affect vanilla, and it's hard to imagine a mod where this is a problem.
        val avgOxygen = round(ship.averageOxygen * 100).toInt()
        val oxygenRed: Boolean
        if (avgOxygen < 25 && ship.oxygen != null) {
            oxygenWarning.draw(g)
            oxygenRed = oxygenWarning.isFlashingHigh
        } else {
            oxygenRed = false
        }

        val evasion = ship.evasion
        val evasionRed = evasion == 0

        val evasionOxyPath = when {
            evasionRed && oxygenRed -> "img/statusUI/top_evade_oxygen_both_red.png"
            evasionRed -> "img/statusUI/top_evade_oxygen_up_red.png"
            oxygenRed -> "img/statusUI/top_evade_oxygen_down_red.png"
            else -> "img/statusUI/top_evade_oxygen.png"
        }
        val oxyY = 96 + extraShieldsHeight
        game.getImg(evasionOxyPath).draw(1f, oxyY - 7f)

        val evadeTextRight = 92f

        // Evade
        oxygenEvadeFont.drawStringLeftAligned(evadeTextRight, oxyY + 15f, "$evasion%", Colour.white)

        // Oxygen
        oxygenEvadeFont.drawStringLeftAligned(evadeTextRight, oxyY + 37f, "$avgOxygen%", Colour.white)

        if (mousePos.x in 8..98 && mousePos.y in oxyY..oxyY + 20) {
            g.tooltip = evasionTooltip
        }
        if (mousePos.x in 8..98 && mousePos.y in oxyY + 22..oxyY + 42) {
            g.tooltip = oxygenTooltip
        }

        // Draw all the crew boxes, with the crew stations buttons below
        // them, clear of the reactor's power bars (see layOutCrewList)
        layOutCrewList(oxyY + 59)

        // Warn when more intruders come aboard
        val intruders = ship.crew.count { it.mode == AbstractCrew.SlotType.INTRUDER }
        if (intruders > lastIntruderCount)
            intrudersWarning.startFor(5f)
        if (intruders == 0)
            intrudersWarning.stop()
        lastIntruderCount = intruders
        intrudersWarning.centre = ConstPoint(INTRUDERS_WARNING_X, crewBaseY + INTRUDERS_WARNING_DY)
        intrudersWarning.draw(g)
        for ((index, crew) in ship.sys.playerCrew.withIndex()) {
            val crewY = crewBaseY + index * crewBoxSpacing

            drawCrewBox(g, crew, crewX, crewY)
        }

        // Add the save/restore crew position buttons
        val lastCrewBoxY = crewBaseY + (ship.sys.playerCrew.size - 1).coerceAtLeast(0) * crewBoxSpacing
        val crewSaveLoadY = lastCrewBoxY + 32
        if (updatingButtons) {
            val saveImg = ButtonImageSet.select2(game, "img/statusUI/button_station_assign")
            val loadImg = ButtonImageSet.select2(game, "img/statusUI/button_station_return")
            val base = game.getImg("img/statusUI/button_station_base.png")

            buttons += object : Button(game, ConstPoint(19 + 5, crewSaveLoadY + 5), saveImg.normal.imageSize) {
                val tooltip = HotkeyDelayedTooltip(game, "save_stations_tooltip")

                override fun draw(g: Graphics) {
                    // 7px glow, and the button is 5px inside the wrapper
                    base.draw(pos.x - 5 - 7, crewSaveLoadY - 7)

                    saveImg.pick(this).draw(pos)

                    if (hovered) {
                        g.tooltip = tooltip
                    }
                }

                override fun click(button: Int) {
                    if (button != Input.MOUSE_LEFT_BUTTON)
                        return

                    saveCrewPositions()
                }
            }

            buttons += object : Button(game, ConstPoint(58 + 5, crewSaveLoadY + 5), loadImg.normal.imageSize) {
                val tooltip = HotkeyDelayedTooltip(game, "ret_stations_tooltip")

                override fun draw(g: Graphics) {
                    // 7px glow, and the button is 5px inside the wrapper
                    base.draw(pos.x - 5 - 7, crewSaveLoadY - 7)

                    loadImg.pick(this).draw(pos)

                    if (hovered) {
                        g.tooltip = tooltip
                    }
                }

                override fun click(button: Int) {
                    if (button != Input.MOUSE_LEFT_BUTTON)
                        return

                    loadCrewPositions()
                }
            }
        }

        // The save/load stations messages, which fade out after a second
        if (saveCrewPosMessageTimer > 0f) {
            saveCrewPosMessageTimer -= game.renderingDeltaTime

            val message = game.translator[saveCrewPosMessageText]
            oxygenEvadeFont.drawString(11f, crewSaveLoadY + 55f, message, Colour.white)
        }
    }

    /**
     * Place the crew list, which would start at [usualTop], so the crew
     * stations buttons below it stay [CREW_REACTOR_GAP] clear of the top of
     * the reactor's power bars: moving it up a little (as far as there's
     * room below the evasion and oxygen box), and then if it's still too
     * long, putting the crew boxes closer together.
     */
    private fun layOutCrewList(usualTop: Int) {
        val count = ship.sys.playerCrew.size
        val reactorRows = reactorCapacity.coerceIn(1, SINGLE_COLUMN_REACTOR_POWER)
        val reactorTop = height - 34 - (reactorRows - 1) * 9
        fun buttonsBottom(top: Int, spacing: Int) =
            top + (count - 1).coerceAtLeast(0) * spacing + 32 + CREW_STATIONS_HEIGHT

        val overflow = buttonsBottom(usualTop, CREW_BOX_SPACING) + CREW_REACTOR_GAP - reactorTop
        val shift = overflow.coerceIn(0, MAX_CREW_LIST_SHIFT)
        crewBaseY = usualTop - shift
        crewBoxSpacing = CREW_BOX_SPACING

        val stillOver = overflow - shift
        if (stillOver > 0 && count > 1) {
            val squeeze = (stillOver + count - 2) / (count - 1)
            crewBoxSpacing = (CREW_BOX_SPACING - squeeze).coerceAtLeast(CREW_BOX_HEIGHT)
        }
    }

    private fun drawCrewBox(g: Graphics, crew: LivingCrew, x: Int, y: Int) {
        val clonebay = ship.clonebay

        val isMindControlled = crew.mindControlledBy != null
        val isStunned = crew.stunTime > 0f
        val hpFraction = crew.health / crew.maxHealth
        val isFlashingHealth = hpFraction > 0f && hpFraction < LOW_HEALTH && lowHealthBlinkTime < LOW_HEALTH_BLINK
        // Just after levelling up a skill, the box flashes white
        val skillUpFlash = crew.skillUpTime > 0f && (crew.skillUpTime / SKILL_UP_FLASH).toInt() % 2 == 0
        val isCloning = clonebay?.queue?.contains(crew) ?: false
        val cloneDyingProgress =
            if (clonebay?.queue?.lastOrNull() == crew)
                clonebay.dyingProgress
            else
                0f

        val colour = when {
            isMindControlled -> CREW_BOX_MIND_CONTROLLED
            skillUpFlash -> Colour.white
            crew in selectedCrew -> CREW_BOX_SELECT
            isStunned -> CREW_BOX_STUNNED
            crew in hoveredCrew -> CREW_BOX_HOVER
            isFlashingHealth -> CREW_BOX_LOW_HEALTH
            isCloning -> CREW_BOX_CLONING
            else -> CREW_BOX_NORMAL
        }

        val drawSkills = crew == skillsHoveredCrew

        if (!drawSkills) {
            // Draw the semi-transparent background
            g.colour = Colour(colour.r, colour.g, colour.b, CREW_BOX_BG_ALPHA)
            g.fillRect(x.f, y.f, CREW_BOX_WIDTH.f, CREW_BOX_HEIGHT.f)

            // Draw the solid outline, via two unfilled rectangles.
            g.colour = colour
            g.drawRect(x.f, y.f, CREW_BOX_WIDTH - 1f, CREW_BOX_HEIGHT - 1f)
            g.drawRect(x + 1f, y + 1f, CREW_BOX_WIDTH - 3f, CREW_BOX_HEIGHT - 3f)

            // A skill just levelled up: its icon beside the box, fading
            val skillUp = crew.skillUp
            if (skillUp != null && crew.skillUpTime > 0f) {
                val alpha = (crew.skillUpTime / LivingCrew.SKILL_UP_TIME).coerceIn(0f, 1f)
                val rise = (1f - alpha) * SKILL_UP_RISE
                game.getImg(skillUp.iconPath).draw(
                    x + CREW_BOX_WIDTH + 1f, y + 1f - rise, Colour(1f, 1f, 1f, alpha)
                )
            }
        } else {
            // Draw the semi-transparent background
            g.colour = Colour(colour.r, colour.g, colour.b, 0.25f)
            val columnX = x + SKILLS_COLUMN_X
            g.fillRect(x.f, y.f, SKILLS_COLUMN_X.f, CREW_BOX_HEIGHT.f)
            g.fillRect(columnX.f, y.f, SKILLS_COLUMN_WIDTH.f, SKILLS_PANEL_HEIGHT.f)

            // Draw the outline, which we do with line drawing by rectangles.
            g.colour = colour
            g.fillRect(x.f, y.f, 2f, CREW_BOX_HEIGHT.f)
            g.fillRect(x.f, y.f, SKILLS_PANEL_WIDTH.f, 2f)
            g.fillRect(x.f, y + 25f, SKILLS_COLUMN_X + 2f, 2f)
            g.fillRect(columnX.f, y + 27f, 2f, SKILLS_PANEL_HEIGHT - 27f)
            g.fillRect(x + SKILLS_PANEL_WIDTH - 2f, y.f, 2f, SKILLS_PANEL_HEIGHT.f)
            // (a Crystal's column goes on down to take in its lockdown
            // button, without a line between them)
            if (!hasLockdownButton(crew))
                g.fillRect(columnX + 2f, y + SKILLS_PANEL_HEIGHT - 2f, SKILLS_COLUMN_WIDTH - 4f, 2f)

            for (skill in Skill.entries)
                drawSkillBar(g, columnX + 4, y, crew, skill)

            if (crew is CrewCrystal && hasLockdownButton(crew))
                drawLockdownButton(g, crew, x, y, colour)
        }

        // Draw the health bar
        val maxHpWidth = 49
        var hpWidth = (maxHpWidth * hpFraction).toInt()

        // Don't go below one pixel of health unless they're dead.
        // This is particularly notable when the crew is cloning.
        if (hpFraction > 0 && hpWidth == 0) {
            hpWidth = 1
        }

        if (crew.health == crew.maxHealth) {
            g.colour = Colour.green
        } else {
            g.colour = Colour(
                1f,
                (2f * hpFraction).coerceIn(0f..1f),
                0f
            )
        }
        g.fillRect(x + 33f, y + 19f, hpWidth.f, 4f)

        // Draw the crew portrait, mostly so you can see what race they are.
        crew.drawPortrait(x - 1, y - 3, true)

        // Draw the crew name
        crewNameText.drawString(x + 33f, y + 14f, crew.info.shortName, CREW_BOX_NAME_COLOUR)

        // A badly hurt crewmember's box blinks red, as in vanilla
        if (isFlashingHealth) {
            g.colour = CREW_BOX_CLONE_DYING_OVERLAY
            g.fillRect(x.f, y.f, CREW_BOX_WIDTH.f, CREW_BOX_HEIGHT.f)
        }

        // If the crew is dying in a clonebay, draw the red overlay
        if (cloneDyingProgress > 0f) {
            g.colour = CREW_BOX_CLONE_DYING_OVERLAY
            g.fillRect(x.f, y.f, CREW_BOX_WIDTH.f, CREW_BOX_HEIGHT * cloneDyingProgress)
        }

        // Add the tooltip
        if (drawSkills) {
            g.tooltip = object : ITooltipProvider {
                override fun drawTooltip(
                    g: Graphics,
                    mouseX: Int, mouseY: Int,
                    firstFrame: Boolean,
                    screenWidth: Int, screenHeight: Int
                ) {
                    // Over one of the skills, what it gives them, and how
                    // far they are to the next level
                    val skill = Skill.entries.firstOrNull { skill ->
                        val rowY = y + skill.ordinal * SKILL_ROW_HEIGHT
                        mouseX in x + SKILLS_COLUMN_X until x + SKILLS_PANEL_WIDTH &&
                                mouseY in rowY until rowY + SKILL_ROW_HEIGHT
                    }
                    val lines = when (skill) {
                        null -> listOf(
                            game.translator["health_tooltip"]
                                .replaceArg(crew.health.roundToInt())
                                .replaceArg(crew.maxHealth.roundToInt(), 2),
                            game.translator["hotkey"] // TODO
                        )

                        else -> skillTooltip(crew, skill)
                    }
                    val tipY = if (skill == null) y else y + skill.ordinal * SKILL_ROW_HEIGHT

                    val width = TooltipConstants.widthOf(lines, tooltipFont)
                    val height = TooltipConstants.heightOf(lines)

                    TooltipConstants.drawTooltip(g, tooltipFont, x + SKILLS_PANEL_WIDTH + 12, tipY, width, height, lines)
                }
            }
        }
    }

    /**
     * The tooltip for one of a crewmember's skills in their skills panel,
     * as vanilla's: what it gives them at the level they're at, and (short
     * of the top level) how many more times they need to use it to get to
     * the next.
     */
    private fun skillTooltip(crew: LivingCrew, skill: Skill): List<String> {
        val key = when (skill) {
            Skill.PILOTING -> "pilot"
            Skill.ENGINES -> "engines"
            Skill.SHIELDS -> "shields"
            Skill.WEAPONS -> "weapons"
            Skill.REPAIRS -> "repair"
            Skill.COMBAT -> "combat"
        }
        val level = crew.getSkillLevel(skill)
        val bonus = game.translator[key + "_skill_infobox"].replaceArg(skill.getDescription(game, level))
        val lines = game.translator[key + "_skill_tooltip"].replaceArg(bonus)
            .split("\n").toMutableList()

        if (!level.isMax) {
            val progress = crew.info.skills.getValue(skill)
            val inLevel = progress - SkillLevel.progressAt(level.level)
            val done = (inLevel / skill.amountPerAction).roundToInt().coerceIn(0, skill.actionsPerStep)
            lines += game.translator["next_level"].replaceArg(done).replaceArg(skill.actionsPerStep, 2)
        }
        return lines
    }

    /**
     * Draw the current beacon's Anti-Ship [battery]'s icon (vanilla's, red
     * when it fires at the player, green when it's on their side), with its
     * tooltip under the mouse. Returns how far the warnings under it move down.
     */
    private fun drawBatteryIcon(g: Graphics, battery: AntiShipBattery): Int {
        val icon = game.getImg(if (battery.targetsPlayer) "img/warnings/danger_pds.png" else "img/warnings/danger_pds_green.png")
        val x = BATTERY_ICON_CENTRE_X - icon.width / 2
        val y = BATTERY_ICON_Y
        icon.draw(x, y)

        if (mousePos.x in x until x + icon.width && mousePos.y in y until y + icon.height) {
            val key = when {
                game.currentBeacon.isOvertaken -> "tooltip_PDS_FLEET"
                battery.target == AntiShipBattery.Target.ENEMY -> "tooltip_PDS_ENEMY"
                battery.target == AntiShipBattery.Target.ALL -> "tooltip_PDS_ALL"
                else -> "tooltip_PDS_PLAYER"
            }
            g.tooltip = FixedStandardTooltip(game, key)
        }
        return y + icon.height + BATTERY_ICON_GAP - SUN_WARNING.y + WARNING_TEXT_HEIGHT
    }

    // Where the lockdown button in the open skills panel is, and whose it is
    // (see drawLockdownButton), for clicking it
    private var lockdownButton: Pair<CrewCrystal, Rectangle>? = null

    private fun hasLockdownButton(crew: AbstractCrew): Boolean = crew is CrewCrystal && crew.playerControllable

    /**
     * A Crystal's lockdown button, under its skills in the open skills
     * panel: it locks down the room the Crystal's in (see
     * [CrewCrystal.lockDown]), and fills up again as it recharges.
     */
    private fun drawLockdownButton(g: Graphics, crew: CrewCrystal, x: Int, y: Int, colour: Colour) {
        // The skills column goes down to take it in
        val top = y + SKILLS_PANEL_HEIGHT
        g.colour = Colour(colour.r, colour.g, colour.b, 0.25f)
        val columnX = x + SKILLS_COLUMN_X
        g.fillRect(columnX.f, top.f, SKILLS_COLUMN_WIDTH.f, LOCKDOWN_BOX_HEIGHT.f)
        g.colour = colour
        g.fillRect(columnX.f, top.f, 2f, LOCKDOWN_BOX_HEIGHT.f)
        g.fillRect(x + SKILLS_PANEL_WIDTH - 2f, top.f, 2f, LOCKDOWN_BOX_HEIGHT.f)
        g.fillRect(columnX.f, top + LOCKDOWN_BOX_HEIGHT - 2f, SKILLS_COLUMN_WIDTH.f, 2f)

        val bx = columnX + (SKILLS_COLUMN_WIDTH - LOCKDOWN_BUTTON_WIDTH) / 2
        val by = top + (LOCKDOWN_BOX_HEIGHT - 2 - LOCKDOWN_BUTTON_HEIGHT) / 2
        val ready = crew.canLockDown
        val hovered = mousePos.x in bx until bx + LOCKDOWN_BUTTON_WIDTH &&
                mousePos.y in by until by + LOCKDOWN_BUTTON_HEIGHT
        // White with rounded corners when it's ready (a little grey under
        // the mouse); while it recharges, dark, with the white growing
        // across it
        val charge = if (ready) 1f else 1f - crew.lockdownCooldown / CrewCrystal.LOCKDOWN_COOLDOWN
        if (!ready) {
            g.colour = LOCKDOWN_BUTTON_CHARGING
            fillRoundedRect(g, bx, by, LOCKDOWN_BUTTON_WIDTH, LOCKDOWN_BUTTON_HEIGHT)
        }
        g.colour = if (ready && hovered) LOCKDOWN_BUTTON_HOVER else Colour.white
        val filled = (LOCKDOWN_BUTTON_WIDTH * charge).toInt()
        if (filled > 0)
            fillRoundedRect(g, bx, by, max(filled, 2 * LOCKDOWN_BUTTON_RADIUS), LOCKDOWN_BUTTON_HEIGHT)

        // (its capitals are 8px tall, centred in the button)
        val text = game.translator["button_lockdown"]
        lockdownFont.drawStringCentred(
            bx.f, by + 10f, LOCKDOWN_BUTTON_WIDTH.f, text,
            // (grey while it recharges, to show over the dark and the white)
            if (ready) Colour.black else LOCKDOWN_BUTTON_CHARGING_TEXT
        )

        lockdownButton = crew to Rectangle(bx.f, by.f, LOCKDOWN_BUTTON_WIDTH.f, LOCKDOWN_BUTTON_HEIGHT.f)
    }

    // A rectangle with its corners cut off to a [LOCKDOWN_BUTTON_RADIUS]
    // pixel round, a row at a time, to keep it crisp
    private fun fillRoundedRect(g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val r = LOCKDOWN_BUTTON_RADIUS
        g.fillRect(x, y + r, width, height - 2 * r)
        for (i in 0 until r) {
            val inset = r - i
            g.fillRect(x + inset, y + i, width - 2 * inset, 1)
            g.fillRect(x + inset, y + height - 1 - i, width - 2 * inset, 1)
        }
    }

    private fun drawSkillBar(
        g: Graphics,
        x: Int, baseY: Int,
        crew: LivingCrew, skill: Skill
    ) {
        val y = baseY + skill.ordinal * SKILL_ROW_HEIGHT // Not 26, which is the icon height
        val skillLevel = crew.getSkillLevel(skill)

        // Draw the icon
        val icon = game.getImg(skill.iconPath)
        val iconColour = when {
            skillLevel.isMax -> SYS_ENERGY_REPAIR
            skillLevel.isPartial -> SYS_ENERGY_ACTIVE
            else -> UI_BACKGROUND_GLOW_COLOUR
        }
        icon.draw(x, y, iconColour)

        // Draw the progress bar
        crew.info.drawSkillProgressBar(g, x + 30, y + 9, SKILL_BAR_WIDTH, 8, skill)
    }

    // Draw the box containing the weapon or drone selection buttons
    /**
     * The name of the wire that goes under a weapons or drones box with a
     * given number of slots.
     */
    private fun boxWireName(slots: Int): String = when (slots) {
        4 -> "456"
        else -> "456_${slots}weapon"
    }

    private fun drawWeaponBox(x: Int, label: String, size: Int, y: Int = weaponBoxY, index: Int? = null) {
        game.getImg("img/box_weapons_bottom$size.png").draw(x.f, y.f)

        val textX = x + 18
        val textY = y + 61

        // Draw the 'weapons' or 'drones' label, with the system's number if
        // there's several (matching the number on its icon)
        val name = game.translator[label] + (index?.let { " $it" } ?: "")
        val textWidth = font.getWidth(name)
        val img = game.getImg("img/box_weapons_bottom_label.png")

        // Stretch part of the label to cover the width of the text
        img.draw(
            textX.f, textY.f, textX + textWidth - 13f, textY + img.height.f,
            0f, 0f, 3f, img.height.f
        )

        // Draw the 'ramp' at the end of the label.
        img.draw((textX + textWidth - 13).f, textY.f)

        font.drawString(textX + 1f, textY + 15f, name, UI_TEXT_COLOUR_1)
    }

    // The enemy crewmember or drone under the mouse, if the player can see it
    // (see findHoveredEnemyUnit)
    private var hoveredEnemyUnit: AbstractCrew? = null

    /**
     * The enemy unit under the mouse at [mouseX], [mouseY]: one of the crew
     * or drones of the ships the player's facing, aboard them, or their
     * boarders aboard the player's ship, in a room the player can see into.
     * A bulwark's caught anywhere over the cells it stands on.
     */
    private fun findHoveredEnemyUnit(mouseX: Int, mouseY: Int, playerShipPosition: IPoint): AbstractCrew? {
        val candidates = ArrayList<AbstractCrew>()
        for (opponent in game.opponents) {
            if (!opponent.isInteriorVisible(ship))
                continue
            candidates += opponent.ship.crew.filter { it.mode == AbstractCrew.SlotType.CREW }
        }
        candidates += ship.intruders.filter { !it.playerControllable }

        return candidates.lastOrNull { unit ->
            if (!unit.room.playerHasVision || unit.currentAction == AbstractCrew.Action.DYING)
                return@lastOrNull false
            val pos = crewScreenPos(unit, playerShipPosition)
            val size = ROOM_SIZE * unit.cellsWide
            mouseX in pos.x until pos.x + size && mouseY in pos.y until pos.y + size
        }
    }

    // The tooltip for an enemy unit: its name, and its health
    private fun enemyUnitTooltip(unit: AbstractCrew): ITooltipProvider = object : StandardTooltip(game) {
        override fun getText(): String {
            val name = when (unit) {
                is LivingCrew -> unit.info.name
                is AbstractIndoorsDrone.Pawn -> unit.drone.type.translateTitle(game)
                else -> unit.codename
            }
            val health = game.translator["health_tooltip"]
                .replaceArg(unit.health.roundToInt())
                .replaceArg(unit.maxHealth.roundToInt(), 2)
            return "$name\n$health"
        }
    }

    fun updateUI(x: Int, y: Int, playerShipPosition: IPoint) {
        mousePos.set(x, y)
        hoveredEnemyUnit = findHoveredEnemyUnit(x, y, playerShipPosition)

        pauseWindow?.let { win ->
            win.updateUI(x, y)
            return
        }

        currentWindow?.let { win ->
            win.updateUI(x, y)
            return
        }

        for (button in buttons) {
            button.update(x, y)
        }

        if (beamTargeting != null) {
            updateBeamTargeting(x, y)
        }

        crewSelectionRectangle?.second?.set(x, y)

        updateHoveredCrew(x, y, playerShipPosition)

        // Update the door hover markers
        for (door in ship.doors) {
            door.updateMouseHover(x - playerShipPosition.x, y - playerShipPosition.y)
        }
    }

    fun showEventDialogue(event: Event, seed: Int) {
        openEventDialogue(event, seed)
    }

    /** Add a paragraph to the event window that's open, if one is. */
    fun appendToEventDialogue(text: String) {
        (currentWindow as? DialogueWindow)?.appendText(text)
    }

    private fun openEventDialogue(event: Event?, seed: Int) {
        var hasImmediatelyClosed = false

        currentWindow = DialogueWindow(game, ship, event, seed) {
            hasImmediatelyClosed = true
            eventDialogueClosed()
        }

        // It's possible for DialogueWindow to call close in its constructor,
        // in which case we have to close it not to avoid it being rendered
        // while in an invalid state.
        // (This is because the constructor would run before currentWindow
        //  is set).
        // This can be tested with the TRADER_LIST event, or any other
        // event list whose purpose is to pick one of several events and
        // close without any text set.
        if (hasImmediatelyClosed) {
            currentWindow = null
        }
    }

    fun showSyntheticDialogue(syntheticEvent: DialogueWindow.SyntheticEvent) {
        var dialogue = currentWindow as? DialogueWindow

        // Open a new, event-less window.
        // This would crash on the next render if we didn't add
        // the synthetic event.
        if (dialogue == null) {
            openEventDialogue(null, 0)
            dialogue = currentWindow as DialogueWindow
        }

        dialogue.addSyntheticEvent(syntheticEvent)
    }

    private fun eventDialogueClosed() {
        currentWindow = null
        game.eventDialogueClosed()

        // If a store was made available by the dialogue, open it
        if (game.currentBeacon.hasStore && !storeAlreadyOpened) {
            updateButtons() // Make the store button show up
            showStoreWindow()
        }
    }

    private fun showShipWindow(initialTab: ShipWindow.Tab, force: Boolean = false) {
        if (!force && game.isInDanger)
            return

        currentWindow = ShipWindow(game, ship, initialTab) {
            currentWindow = null
        }
    }

    private fun showStoreWindow() {
        if (game.isInDanger)
            return

        val store = game.currentBeacon.getStore(game) ?: return

        storeAlreadyOpened = true

        currentWindow = StoreWindow(game, ship, store) {
            currentWindow = null
        }
    }

    // These are for the screenshot generator (see ScreenshotDirector), and
    // open the windows whether or not the player could right now.

    fun debugCloseWindow() {
        currentWindow = null
    }

    // These are for the automatic playtest (see AutoplayDirector)

    /**
     * The window that's open, if any.
     */
    val openWindow: Window? get() = currentWindow

    /**
     * Jump to [target], as the jump map does.
     */
    fun autoJump(target: Beacon) {
        currentWindow = null
        storeAlreadyOpened = false
        game.player.fuelCount -= game.player.fuelPerJump
        game.advanceFleet()
        game.currentBeacon = target
        updateButtons()
    }

    /**
     * Wait at the beacon without the fuel to jump, as the jump map's wait
     * button does: the fleet advances, and an event comes (with
     * [distress], the distress beacon's, which brings more help).
     */
    fun waitOutOfFuel(distress: Boolean) {
        // Waiting counts as jumping to the same beacon: the weapons lose
        // their charge, the drones outside are left behind (or recovered
        // by the Drone Recovery Arm), the shields are refilled and so on
        ship.resetAfterJump()

        game.advanceFleet()

        // Waiting in a hazard (a sun, a pulsar, asteroids or an ion storm),
        // the ship gets away from it on its impulse engines, as in vanilla:
        // vanilla's escape event, and the hazard's gone from the beacon
        if (!game.currentBeacon.isOvertaken) {
            val escape = escapeFromHazard()
            if (escape != null) {
                showEventDialogue(escape, kotlin.random.Random.nextInt())
                game.currentBeacon.clearEnvironment()
                return
            }
        }

        // Pick a suitable event
        val eventName: String = when {
            // Rebels have overtaken this beacon
            game.currentBeacon.isOvertaken -> when (game.content.enableAdvancedEdition) {
                true -> "NO_FUEL_FLEET_DLC"
                false -> "NO_FUEL_FLEET"
            }

            distress -> "NO_FUEL_DISTRESS"
            else -> "NO_FUEL"
        }
        val event = game.eventManager[eventName].resolve()

        // TODO make this seed reproducable if the game is reloaded?
        showEventDialogue(event, kotlin.random.Random.nextInt())

        // The fleet's event is the beacon's now, so its rebel ships show
        // in the background (as at any beacon the fleet's taken)
        if (game.currentBeacon.isOvertaken && event.fleetBackground != null)
            game.currentBeacon.event = event

        // Clear the environment, so if the beacon was overtaken it now
        // has the enemy background etc there.
        game.currentBeacon.clearEnvironment()
    }

    /**
     * Leave the hazards at the current beacon behind, waiting out of fuel
     * (see [waitOutOfFuel]): returns vanilla's event for getting away from
     * the worst of them, or null if there aren't any it has one for. A
     * storm leaves a plain nebula behind.
     */
    private fun escapeFromHazard(): Event? {
        val beacon = game.currentBeacon
        val types = beacon.environmentTypes
        val name = ESCAPE_EVENTS.entries.firstOrNull { it.key in types }?.value ?: return null
        val escape = game.eventManager.getOrNull(name)?.resolve() ?: return null

        // The beacon's own hazard goes with its event (the escape event has
        // none), but a nebula stays a nebula
        beacon.sectorHazards = emptyList()
        val wasNebula = beacon.isOwnNebula
        beacon.event = escape
        beacon.nebulaOverride = if (wasNebula) Beacon.EnvironmentType.NEBULA else null
        return escape
    }

    /**
     * Wait for fuel, as [waitOutOfFuel], for the automatic playtest, with
     * the distress beacon on.
     */
    fun autoWaitOutOfFuel() {
        currentWindow = null
        storeAlreadyOpened = false
        updateButtons()
        waitOutOfFuel(true)
    }

    /**
     * Go to the next sector, as the sector map does.
     */
    fun autoEnterSector(sectorInfo: GameMap.SectorInfo) {
        currentWindow = null
        enterSector(sectorInfo)
    }

    fun debugShowJumpMap() = showJumpMap()

    fun debugShowShipWindow(tab: ShipWindow.Tab) = showShipWindow(tab, force = true)

    fun debugShowStore() {
        val store = game.currentBeacon.getStore(game) ?: return
        storeAlreadyOpened = true
        currentWindow = StoreWindow(game, ship, store) {
            currentWindow = null
        }
    }

    fun showPauseWindow() {
        pauseWindow = PauseWindow(game) { pauseWindow = null }
    }

    fun showOptionsWindow() {
        // This uses the pause window slot, so it doesn't dismiss
        // a regular window, such as the dialogue or game-over windows.
        pauseWindow = OptionsWindow(game) { pauseWindow = null }
    }

    fun showDisplayWindow() {
        // It's opened from the options window, and goes back to it, like
        // the keybinds window
        pauseWindow = DisplayWindow(game) { showOptionsWindow() }
    }

    /**
     * Show a window of the main menu's (see MainMenuState), in the pause
     * window's slot, like the options window.
     */
    fun showMenuWindow(window: Window) {
        pauseWindow = window
    }

    fun closeMenuWindow() {
        pauseWindow = null
    }

    /**
     * Ask the player vanilla's yes or no question [messageKey] (such as
     * confirm_leave_crew) over everything else, and if they say yes, run
     * [onYes].
     */
    fun confirm(messageKey: String, onYes: () -> Unit) {
        showMenuWindow(ConfirmWindow(game, game.translator[messageKey], onYes) { closeMenuWindow() })
    }

    /**
     * Whether any of the player's crew are on another ship, and would be
     * left behind by jumping away.
     */
    val crewLeftOnOtherShips: Boolean
        get() = game.opponents.any { !it.ship.isGone && it.ship.hasCrewOwnedByShip(ship) }

    /**
     * Draw just the open windows, for the main menu, which draws the rest
     * itself.
     */
    fun renderMenuWindows(container: GameContainer, g: Graphics) {
        width = container.width
        height = container.height
        renderMenus(container, g)
    }

    fun showKeybindWindow() {
        // Switch back to the options window when it's closed, that's how
        // vanilla behaves.
        pauseWindow = HotkeysWindow(game) { showOptionsWindow() }
    }

    // Hack used to draw the selected crew from the ship, rather than some cleaner solution
    fun isCrewHighlighted(crew: AbstractCrew): Boolean {
        return selectedCrew.contains(crew) || hoveredCrew.contains(crew)
    }

    fun escapePressed() {
        pauseWindow?.let { win ->
            win.escapePressed()
            return
        }

        currentWindow?.let { win ->
            win.escapePressed()
            return
        }

        // The main menu has no pause window
        if (!game.isMenuScene)
            showPauseWindow()
    }

    fun playInsufficientScrapAnimation() {
        // Three flashes: on-off-on-off-on
        insufficientScrapTimer = INSUFFICIENT_SCRAP_FLASH_TIME * 5

        // I think this is the right sound?
        powerUpFailSound.play()
    }

    fun shipModified() {
        updateButtons()
        currentWindow?.shipModified()
    }

    /**
     * Called by [Teleporter] when one of its buttons are clicked.
     *
     * When the 'send to enemy ship' button is clicked, [send] is true.
     * Otherwise it's false.
     */
    fun teleportSelected(send: Boolean) {
        game.clickEvent = TeleportRoomListener(send)
    }

    /**
     * Called by [Hacking] when we're supposed to select the room to hack.
     */
    fun hackSelected(hacking: Hacking) {
        game.clickEvent = HackingRoomListener(hacking)
    }

    /**
     * Called by [MindControl] when we're supposed to select the room to
     * apply mind control to.
     */
    fun mindControlSelected(mindControl: MindControl) {
        game.clickEvent = MindControlRoomListener(mindControl)
    }

    fun openJumpMap() {
        if (!ship.isFtlReady || !ship.canChargeFTL)
            return

        showJumpMap()
    }

    private fun showJumpMap() {
        currentWindow = JumpWindow(game, ::openSectorMap) {
            if (it != null) {
                // Reset stuff after jumping
                storeAlreadyOpened = false
            }

            currentWindow = null
            updateButtons() // A store may now be available
        }
    }

    fun openSectorMap() {
        currentWindow = SectorMapWindow(game) { sectorInfo ->
            currentWindow = null

            // Null just means the window was closed
            if (sectorInfo == null)
                return@SectorMapWindow

            if (crewLeftOnOtherShips)
                confirm("confirm_leave_crew") { enterSector(sectorInfo) }
            else
                enterSector(sectorInfo)
        }
    }

    private fun enterSector(sectorInfo: GameMap.SectorInfo) {
        // The jump to the next sector takes fuel, as any jump does (as much
        // as one from the sector it leaves, see Ship.fuelPerJump)
        game.player.fuelCount = (game.player.fuelCount - game.player.fuelPerJump).coerceAtLeast(0)

        val sector = game.gameMap.generateSector(sectorInfo, game)
        game.sectorEntered(sector)
        game.currentBeacon = sector.startBeacon
        storeAlreadyOpened = false

        // The Distraction Buoys say they've been deployed, as in vanilla, at
        // the end of the sector's start event (or on their own, if it has
        // none to show)
        if (game.player.hasAugment(AugmentBlueprint.DISTRACTION_BUOYS)) {
            val message = game.translator["distraction"]
            val start = currentWindow as? DialogueWindow
            if (start != null)
                start.appendText(message)
            else
                showSyntheticDialogue(DialogueWindow.SyntheticEvent(message))
        }

        // In case we were at a store
        // TODO move this into an on-jump handler function
        updateButtons()
    }

    fun openAllDoors() {
        val internalDoors = ship.doors.filter { !it.isAirlock }
        val allInternalDoorsOpen = internalDoors.all { it.open }

        // Pressing the open key with all the internal doors open
        // opens all the airlocks, otherwise only the internal
        // doors are opened.
        for (door in ship.doors) {
            if (!allInternalDoorsOpen && door.isAirlock)
                continue
            if (!door.isControllable)
                continue

            door.open = true
        }
    }

    fun closeAllDoors() {
        for (door in ship.doors) {
            if (door.isControllable)
                door.open = false
        }
    }

    /**
     * Called whenever a hotkey is pressed.
     *
     * This is the proper way to handle hotkey input, as it supports control rebinding.
     */
    fun hotkeyPressed(key: Hotkey, input: Input) {
        // If a window is open, it absorbs all the input.
        // This is particularly important for keybinds like the jump/ship
        // ones as they open a window - the user could otherwise use them
        // to replace the currently-open dialogue window, to avoid the
        // result of an undesirable event.
        pauseWindow?.let {
            it.hotkeyPressed(key)
            return
        }
        currentWindow?.let {
            it.hotkeyPressed(key)
            return
        }

        for (system in ship.mainSystems) {
            if (key.id == "sys_power_${system.codename}") {
                val powerUp: Boolean = !input.isKeyDown(Input.KEY_LSHIFT)
                changeSystemPower(system, powerUp)
            }
            if (key.id == "sys_power_${system.codename}_off") {
                changeSystemPower(system, false)
            }
        }

        for (system in ship.systems) {
            system.hotkeyPressed(key)
        }

        // The select-one-crewmember hotkeys
        for ((index, id) in VanillaHotkeys.SELECT_CREW.withIndex()) {
            if (key.id != id)
                continue

            val crew = game.playerCrew.getOrNull(index) ?: continue
            if (!crew.playerControllable)
                continue

            selectedCrew.clear()
            selectedCrew += crew
        }

        when (key.id) {
            VanillaHotkeys.WEAPON_AUTOFIRE_TOGGLE -> toggleAutofire()
            VanillaHotkeys.FTL_JUMP -> openJumpMap()
            VanillaHotkeys.SHIP_UPGRADES -> showShipWindow(ShipWindow.Tab.UPGRADES)
            VanillaHotkeys.SHIP_CREW -> showShipWindow(ShipWindow.Tab.CREW)
            VanillaHotkeys.SHIP_INVENTORY -> showShipWindow(ShipWindow.Tab.EQUIPMENT)
            VanillaHotkeys.OPEN_STORE -> showStoreWindow()
            VanillaHotkeys.OPEN_OPTIONS -> showOptionsWindow()

            VanillaHotkeys.SYS_ACTION_DOOR_OPEN -> openAllDoors()
            VanillaHotkeys.SYS_ACTION_DOOR_CLOSE -> closeAllDoors()

            VanillaHotkeys.SELECT_CREW_ALL -> {
                // Should this include mind-controlled enemies?
                selectedCrew.clear()
                selectedCrew += game.playerCrew.filter { it.playerControllable }
            }

            // The selected Crystals lock their rooms down
            VanillaHotkeys.LOCKDOWN_ACTIVATE -> {
                for (crew in selectedCrew) {
                    if (crew is CrewCrystal && crew.playerControllable)
                        crew.lockDown()
                }
            }

            VanillaHotkeys.SAVE_CREW_POS -> saveCrewPositions()
            VanillaHotkeys.LOAD_CREW_POS -> loadCrewPositions()

            // These hotkey applies to both medbays and clonebays
            VanillaHotkeys.SYS_POWER_MEDICAL -> {
                val powerUp: Boolean = !input.isKeyDown(Input.KEY_LSHIFT)
                ship.medbay?.let { changeSystemPower(it, powerUp) }
                ship.clonebay?.let { changeSystemPower(it, powerUp) }
            }

            VanillaHotkeys.SYS_POWER_MEDICAL_OFF -> {
                ship.medbay?.let { changeSystemPower(it, false) }
                ship.clonebay?.let { changeSystemPower(it, false) }
            }
        }
    }

    /**
     * FOR DEBUG USE ONLY!
     *
     * See [InGameState.debugContinuousSaveRestore] for more information.
     */
    fun debugContinuousSaveRestore(prev: PlayerShipUI) {
        crewSelectionRectangle = prev.crewSelectionRectangle

        // Copy over the selected crew, based on the index into the ship
        // crew list. This is hacky but it's fine for a debug flag.
        for (prevCrew in prev.selectedCrew) {
            val index = prev.ship.crew.indexOf(prevCrew)

            // Don't bother with crew on the enemy ship.
            if (index == -1)
                continue

            selectedCrew += ship.crew[index]
        }

        // Reloading should keep the store open, if it's run manually.
        val oldStore = prev.currentWindow as? StoreWindow
        if (oldStore != null) {
            showStoreWindow()
            val store = currentWindow as StoreWindow
            store.sellTab = oldStore.sellTab
            store.secondBuyTab = oldStore.secondBuyTab
        }
    }

    private fun updateHoveredCrew(mouseX: Int, mouseY: Int, playerShipPosition: IPoint) {
        hoveredCrew.clear()
        val previousSkillsCrew = skillsHoveredCrew
        skillsHoveredCrew = null

        // Allow for smart-casting
        val csr = crewSelectionRectangle

        // If a rectangle is visible on screen (ie, the player isn't clicking a point) then build a rectangle
        val rect = if (csr != null && !isCrewSelectionPoint) {
            val pos = csr.first.min(csr.second)
            val size = (csr.second - csr.first).abs()
            Rectangle(pos.x.f, pos.y.f, size.x.f, size.y.f)
        } else null

        // Any intruders on the enemy ship should also be controllable,
        // including mind controlled enemies (once that's implemented).
        // Mind-controlled crew are then filtered out in the for loop.
        val controllableCrew = ArrayList(ship.friendlyCrew)
        for (opponent in game.opponents) {
            controllableCrew.addAll(opponent.ship.intruders)
        }

        for (crew in controllableCrew) {
            // Skip drones, mind controlled crew and the like which the
            // player isn't allowed to control.
            if (!crew.playerControllable)
                continue

            val crewPos = crewScreenPos(crew, playerShipPosition)

            @Suppress("IfThenToElvis")
            val hovered = if (rect != null) {
                // If we're in rectangle mode, check that it intersects the centre of the player's body
                // TODO also check if the player is selecting the crew name box
                rect.contains(crewPos.x + ROOM_SIZE / 2f, crewPos.y + ROOM_SIZE / 2f)
            } else {
                // Otherwise check the point overlaps the player
                isCrewHovered(crew, mouseX, mouseY, crewPos)
            }

            if (hovered) {
                hoveredCrew += crew
            }
        }

        // The skills panel stays open while the mouse is over its skills
        // column (to the right of the crew boxes, so it doesn't get in the
        // way of opening another's), and a Crystal's lockdown button under
        // them
        // That's anywhere right of the crew boxes, gap and all (the boxes stop
        // a few pixels short of the column's line), so the mouse can't drop
        // out of it on the way across to the column or its lockdown button.
        if (rect == null && skillsHoveredCrew == null && previousSkillsCrew != null) {
            val index = game.playerCrew.indexOf(previousSkillsCrew)
            val boxY = crewBaseY + index * crewBoxSpacing
            val height = SKILLS_PANEL_HEIGHT + if (hasLockdownButton(previousSkillsCrew)) LOCKDOWN_BOX_HEIGHT else 0
            val inColumn = mouseX in crewX + CREW_BOX_WIDTH until crewX + SKILLS_PANEL_WIDTH &&
                    mouseY in boxY until boxY + height
            // (and the panel's top row, beside the box it's for)
            val inTopRow = mouseX in crewX until crewX + SKILLS_PANEL_WIDTH &&
                    mouseY in boxY until boxY + CREW_BOX_HEIGHT
            if (index != -1 && (inColumn || inTopRow)) {
                skillsHoveredCrew = previousSkillsCrew
            }
        }
    }

    fun saveCrewPositions() {
        ship.saveCrewPositions()

        // Show the popup text
        saveCrewPosMessageTimer = 1f
        saveCrewPosMessageText = "stations_saved"
    }

    fun loadCrewPositions() {
        ship.loadCrewPositions()

        // Show the popup text
        saveCrewPosMessageTimer = 1f
        saveCrewPosMessageText = "return_stations"
    }

    fun saveToXML(elem: Element, refs: ObjectRefs) {
        if (autofire)
            SaveUtil.addAttrBool(elem, "autofire", true)

        val window = currentWindow

        // If the player is currently playing through an event, save that.
        if (window is DialogueWindow) {
            val dialogueElem = Element("dialogue")
            window.saveToXML(dialogueElem, refs)
            elem.addContent(dialogueElem)
        }
    }

    fun loadFromXML(elem: Element, refs: RefLoader) {
        autofire = elem.getAttributeValue("autofire")?.toBoolean() ?: false

        val dialogueElem = elem.getChild("dialogue")
        if (dialogueElem != null) {
            currentWindow = DialogueWindow(game, ship, dialogueElem, refs, this::eventDialogueClosed)
        }
    }

    fun showGameOverScreen(outcome: GameOverWindow.Outcome) {
        game.markFinished()
        currentWindow = GameOverWindow(game, outcome)
    }

    fun getCurrentCursor(): Cursor {
        // Use the hacking/teleport/mind control cursor
        (game.clickEvent as? RoomClickCursor)?.getCursor()?.let { return it }

        targetingSelectedWeapon?.let { id ->
            val valid = game.hoveredRoom != null
            val autofire = aimingWithAutofire

            // Combine the cursor and crosshairs icon
            val baseName = when {
                valid -> "img/mouse/mouse_crosshairs.png"
                autofire -> "img/mouse/mouse_crosshairs_valid2.png"
                else -> "img/mouse/mouse_crosshairs_valid.png"
            }
            // There's only numbered crosshairs for the first four weapons,
            // so the others (on ships with more weapon slots) get none.
            if (id >= 4)
                return game.getCursor(baseName)

            val overlayName = when {
                autofire -> "img/mouse/mouse_crosshairs3_${id + 1}.png"
                else -> "img/mouse/mouse_crosshairs2_${id + 1}.png"
            }
            return game.getCursor("$baseName,$overlayName")
        }

        // Over a door, vanilla's door cursor plays its animation
        if (ship.doors.any { it.isHovered }) {
            val frame = (System.nanoTime() / DOOR_CURSOR_FRAME_NANOS % DOOR_CURSOR_FRAMES).toInt()
            return game.getCursor("img/mouse/mouse_door_strip.png@$frame")
        }

        return when {
            buttons.any { it.hovered } -> cursorHover
            hoveredCrew.isNotEmpty() -> cursorHover
            else -> cursorNormal
        }
    }

    fun onTextInput(key: Int, c: Char): Boolean {
        pauseWindow?.let { return it.onTextInput(key, c) }
        currentWindow?.let { return it.onTextInput(key, c) }

        return false
    }

    /**
     * Turns [autofire] on and off. It's a tab hanging from the weapons box,
     * the 'weapons' label's mirror image, with its right edge at [right]
     * and its top at [labelY]: grey while autofire's off, and green while
     * it's on. It's a bit taller than the label, so its text isn't right
     * under the weapons.
     */
    private inner class AutofireButton(
        private val right: Int,
        private val labelY: Int,
        private val text: String = game.translator["button_autofire"],
        private val textLeft: Int = right - AUTOFIRE_TEXT_PADDING - font.getWidth(text)
    ) : Button(game, ConstPoint(textLeft - 20, labelY), ConstPoint(right - (textLeft - 20), 20 + AUTOFIRE_TOP_PADDING)) {
        private val tooltip = object : DelayedTooltip(game) {
            override fun getText(): String {
                val toggleKey = game.getButtonNameForHotkey(VanillaHotkeys.WEAPON_AUTOFIRE_TOGGLE, "-")
                val modifierKey = game.getButtonNameForHotkey(VanillaHotkeys.WEAPON_AUTOFIRE_TARGET, "-")
                return game.translator["tooltip_autofire"].replaceArg(toggleKey).replaceArg(modifierKey, 2)
            }

            override val customMaxWidth: Int get() = 300
        }

        override fun draw(g: Graphics) {
            val filter = when {
                autofire -> WEAPONS_ITEM_CHARGED
                hovered -> Colour.white
                else -> WEAPONS_ITEM_DESELECTED
            }

            // The label image is a tab with a sloped right end, which is
            // drawn flipped: the slope on the left, ending at the text, and
            // a plain part stretched out to the box's corner.
            // The padding at the top repeats the image's top row, for both
            // parts, so their bottoms line up.
            val img = game.getImg("img/box_weapons_bottom_label.png")
            val rampX = textLeft - 42
            val stretchX = rampX + img.width
            val imgY = labelY + AUTOFIRE_TOP_PADDING
            // Each part's left and right, and the column of the image its
            // left edge is from (the image is flipped)
            val parts = listOf(Triple(rampX, stretchX, img.width), Triple(stretchX, right, 3))
            for ((left, partRight, srcLeft) in parts) {
                img.draw(
                    left.f, labelY.f, partRight.f, imgY.f,
                    srcLeft.f, 0f, 0f, 1f,
                    1f, filter
                )
                img.draw(
                    left.f, imgY.f, partRight.f, imgY + img.height.f,
                    srcLeft.f, 0f, 0f, img.height.f,
                    1f, filter
                )
            }

            font.drawString(textLeft.f, imgY + 15f, text, UI_TEXT_COLOUR_1)

            if (hovered) {
                g.tooltip = tooltip
            }
        }

        override fun click(button: Int) {
            if (button == Input.MOUSE_LEFT_BUTTON)
                toggleAutofire()
        }
    }

    private abstract inner class WeaponDroneButton(pos: IPoint, slotNumber: Int, size: ConstPoint) :
        Button(game, pos, size) {

        abstract val empty: Boolean
        abstract val name: String
        abstract val chargeTime: Float
        abstract val chargeProgress: Float
        abstract val requiredPower: Int
        abstract val zoltanPower: Int
        abstract val isPowered: Boolean
        abstract val isCharged: Boolean
        abstract val isTargeted: Boolean

        // Whether the weapon's aimed with autofire, for a yellow charge bar
        open val isOnAutofire: Boolean get() = false
        abstract val isSelectingTarget: Boolean
        abstract val hasChargeBar: Boolean
        abstract val isBeingHacked: Boolean
        abstract val droneCooldownProgress: Float?
        abstract val tooltip: ITooltipProvider?

        override val disabled: Boolean get() = empty

        /**
         * Set while this is drawn under the mouse, as it's dragged to
         * another slot: it's drawn as if it's unpowered and not hovered.
         */
        var dragPreview = false

        protected val mainColour: Colour
            get() = when {
                empty || dragPreview -> WEAPONS_ITEM_DESELECTED
                isBeingHacked -> SYSTEM_HACKED
                droneCooldownProgress != null -> WEAPONS_ITEM_DRONE_COOLDOWN
                !isPowered -> WEAPONS_ITEM_DESELECTED
                isSelectingTarget -> WEAPONS_ITEM_TARGETING
                isCharged -> WEAPONS_ITEM_CHARGED
                else -> WEAPONS_ITEM_SELECTED
            }

        private var hackingSparks: FTLAnimation? = null
        private var hackingOffsetX: Int = 0
        private var hackingMaskY: Int = 0
        private var hackingMirror: Boolean = false
        private var hackingLastNS: Long = 0

        // Move these out to save a few allocations
        // (it's probably well into paranoid territory, but why not)
        // (cut down to fit in two lines, see ShortNames)
        private val nameLines by lazy {
            weaponNameText.wrapString(ShortNames.fit(name, weaponNameText, WEAPON_NAME_WIDTH, 2), WEAPON_NAME_WIDTH)
        }
        private val weaponNumberString = (slotNumber + 1).toString()

        override fun draw(g: Graphics) {
            if (hovered && !dragPreview) {
                g.tooltip = tooltip

                // Draw the glow behind everything else. It's made for a
                // weapon's box, so for the wider drone boxes, its two halves
                // are moved apart, with its middle stretched to fill the gap.
                // The glow also covers a weapon's charge bar, left of its box,
                // so without one (on drones) it's moved right to stay centred.
                val glow = game.getImg("img/bar_weapon_minibox_glow.png")
                val barSpace = if (hasChargeBar) 0 else CHARGE_BAR_SPACE
                val glowX = pos.x - 18f + barSpace
                val glowY = pos.y - 10f
                val half = glow.width / 2f
                val extra = (size.x - WEAPON_BUTTON_WIDTH - barSpace).coerceAtLeast(0).f
                val h = glow.height.f
                glow.draw(glowX, glowY, glowX + half, glowY + h, 0f, 0f, half, h)
                if (extra > 0f) {
                    glow.draw(glowX + half, glowY, glowX + half + extra, glowY + h, half - 1f, 0f, half, h)
                }
                glow.draw(glowX + half + extra, glowY, glowX + glow.width + extra, glowY + h, half, 0f, glow.width.f, h)
            }

            // Draw the drone cooldown progress, if applicable
            val cooldownProgress = droneCooldownProgress
            if (cooldownProgress != null) {
                val height = (size.y * cooldownProgress).roundToInt()
                g.colour = DRONE_COOLDOWN_BACKGROUND
                g.fillRect(pos.x, pos.y + size.y - height, size.x, height)
            }

            // Draw the outline box
            g.colour = mainColour
            g.drawRect(pos.x.f, pos.y.f, (size.x - 1).f, (size.y - 1).f)
            g.drawRect((pos.x + 1).f, (pos.y + 1).f, (size.x - 3).f, (size.y - 3).f)

            if (empty)
                return

            if (hasChargeBar) {
                val maxBarSize = 35 - 2
                val barSize = (maxBarSize * chargeTime / maxWeaponChargeTime).toInt()

                // The Y position of the inside of the charge bar, relative to the main weapons box
                val top = maxBarSize - barSize

                // The top point of the triangle
                val triangleTop = top - 7

                for (j in 8 downTo 1) {
                    val triangleY = j + triangleTop
                    if (triangleY < 0)
                        continue
                    val y = pos.y + triangleY
                    g.drawLine((pos.x - j).f, y.f, pos.x.f, y.f)
                }

                if (!dragPreview) {
                    if (isOnAutofire)
                        g.colour = WEAPONS_ITEM_AUTOFIRE
                    else if (isTargeted)
                        g.colour = WEAPONS_ITEM_TARGETING
                }

                val chargePx = if (dragPreview) 0 else (barSize * chargeProgress).toInt()
                g.fillRect((pos.x - 5).f, (pos.y + 36 - chargePx).f, 4f, chargePx.f)

                g.colour = mainColour

                g.lineWidth = 2f
                g.drawLine(pos.x - 7.5f, pos.y.f + top.f + 1.5f, pos.x - 7.5f, pos.y + 39 - 1.5f)
                g.drawLine(pos.x - 7.5f, pos.y + 39 - 1.5f, pos.x - 0.5f, pos.y + 39 - 1.5f)
                g.lineWidth = 1f
            }

            // Draw the weapon number box
            val numBoxX = pos.x + size.x - 12
            val numBoxY = pos.y + size.y - 15
            g.lineWidth = 2f
            g.drawLine(numBoxX + 0.5f, numBoxY + 0.5f, numBoxX + 0.5f, numBoxY + 12f + 0.5f)
            g.drawLine(numBoxX + 0.5f, numBoxY + 0.5f, numBoxX + 10f + 0.5f, numBoxY + 0.5f)
            g.lineWidth = 1f

            // Draw the weapon/drone number itself
            val weaponNumberWidth = weaponNumberFont.getWidth(weaponNumberString)
            weaponNumberFont.drawString(
                (numBoxX + 2 + 1 + (8 - weaponNumberWidth) / 2).f,
                pos.y + size.y - 4f,
                weaponNumberString,
                mainColour
            )

            // Draw the item name, which is split across
            // multiple lines to fit in the box.
            var lineY = pos.y + 15
            for (line in nameLines) {
                weaponNameText.drawString(pos.x + 26f, lineY.f, line, mainColour)
                lineY += 15
            }

            // A weapon that needs more than four power (a combined one, see
            // WeaponSizes) has its power bars in two columns of up to four,
            // half as wide, so they still fit: the left one's filled first
            val twoColumns = requiredPower > 4
            val barWidth = if (twoColumns) 7 else 16
            for (bar in 0 until requiredPower) {
                val column = if (twoColumns) bar / 4 else 0
                val x = pos.x + 4 + column * 9
                val y = pos.y + size.y - 11 - (bar % 4) * 8

                if (zoltanPower > bar) {
                    g.colour = WEAPONS_ITEM_ENERGY_ZOLTAN
                } else if (droneCooldownProgress != null) {
                    g.colour = WEAPONS_ITEM_DRONE_COOLDOWN
                    g.drawRect(x.f, y.f, (barWidth - 1).f, (7 - 1).f)
                    continue
                } else if (!isPowered || dragPreview) {
                    g.colour = WEAPONS_ITEM_ENERGY_UNPOWERED
                    g.drawRect(x.f, y.f, (barWidth - 1).f, (7 - 1).f)
                    continue
                } else if (isBeingHacked) {
                    g.colour = SYSTEM_HACKED
                } else if (isSelectingTarget) {
                    g.colour = WEAPONS_ITEM_TARGETING
                } else if (isCharged) {
                    g.colour = WEAPONS_ITEM_ENERGY_CHARGED
                } else {
                    g.colour = WEAPONS_ITEM_ENERGY_POWERED
                }
                g.fillRect(x.f, y.f, barWidth.f, 7f)
            }

            drawHackingSparks()
        }

        private fun drawHackingSparks() {
            if (!isBeingHacked) {
                hackingSparks = null
                return
            }

            val currentNS = System.nanoTime()
            val dt = (currentNS - hackingLastNS) / 1_000_000_000f
            hackingLastNS = currentNS

            if (hackingSparks == null || hackingSparks?.isStopped == true) {
                hackingSparks = ship.sys.animations["stun_spark_big"].startSingle(game)

                hackingOffsetX = VisualRandom.nextInt(25)
                hackingMaskY = VisualRandom.nextInt(31)
                hackingMirror = VisualRandom.nextBoolean()
            }

            // Update the animation regardless of whether we're paused.
            hackingSparks!!.update(dt)

            val frame = hackingSparks!!.currentFrame
            val cutoutWidth = 69
            val cutoutHeight = 39
            frame.draw(
                // Screen points
                pos.x.f + hackingOffsetX, pos.y.f,
                pos.x.f + hackingOffsetX + cutoutWidth, pos.y.f + cutoutHeight,

                // Image points
                if (hackingMirror) cutoutWidth.f else 0f, hackingMaskY.f,
                if (hackingMirror) 0f else cutoutWidth.f, hackingMaskY.f + cutoutHeight
            )
        }
    }

    /**
     * This is the button that adjusts a system's power.
     */
    private inner class SystemPowerButton(pos: IPoint, val system: AbstractSystem) :
        Button(game, pos, ConstPoint(26, 26)) {

        // Subsystems can't be clicked, so don't play their hover noise.
        // This will also keep hovered set to false, since we don't want
        // to activate any hovering-related stuff for something you can't click.
        override val disabled: Boolean get() = system !is MainSystem

        private val tooltip = system.createTooltip()

        override fun draw(g: Graphics) {
            system.drawIconAndPower(game, g, true, true, hovered, pos.x, pos.y)

            if (rawHovered) {
                g.tooltip = tooltip
            }
        }

        override fun click(button: Int) {
            // You can't adjust the power of subsystems
            if (system !is MainSystem)
                return

            if (button == Input.MOUSE_LEFT_BUTTON) {
                changeSystemPower(system, true)
            } else if (button == Input.MOUSE_RIGHT_BUTTON) {
                changeSystemPower(system, false)
            }
        }
    }

    /**
     * A version of [RoomClickListener] that also sets the mouse cursor.
     */
    private interface RoomClickCursor : RoomClickListener {
        fun getCursor(): Cursor?
    }

    private inner class TeleportRoomListener(val send: Boolean) : RoomClickCursor {
        override fun roomClicked(room: Room, gc: GameContainer) {
            // Can't teleport to/from our own ship
            if (room.ship == ship)
                return

            ship.teleporter!!.selectTeleportAction(send, room)
        }

        override fun getCursor(): Cursor {
            val valid = game.hoveredRoom != null

            return when {
                send && valid -> cursorTeleSendValid
                send -> cursorTeleSendInvalid
                valid -> cursorTeleRetrieveValid
                else -> cursorTeleRetrieveInvalid
            }
        }

        override fun canTargetRoom(room: Room): Boolean {
            // We can only teleport to/from the enemy ship, and only send
            // crew to one that's still fighting
            if (send && !game.isEnemyOf(ship, room.ship))
                return false
            return room.ship != ship
        }
    }

    private inner class HackingRoomListener(val hacking: Hacking) : RoomClickCursor {
        override fun roomClicked(room: Room, gc: GameContainer) {
            // Can't hack our own ship
            if (room.ship == ship)
                return

            hacking.selectTarget(room)
        }

        override fun getCursor(): Cursor {
            return cursorHacking
        }
    }

    private inner class MindControlRoomListener(val mindControl: MindControl) : RoomClickCursor {
        override fun roomClicked(room: Room, gc: GameContainer) {
            mindControl.selectRoom(room)
        }

        override fun getCursor(): Cursor {
            val room = game.hoveredRoom
            return when {
                room != null && game.isEnemyOf(ship, room.ship) && ship.canSeeInto(room) -> cursorMindValid
                else -> cursorMindInvalid
            }
        }
    }

    private inner class ResourceDeltaText(var x: Float, var y: Float, amount: Int) {
        private var age: Float = 0f

        private val text: String = when {
            amount > 0 -> "+$amount"
            else -> amount.toString()
        }

        val finished: Boolean get() = age >= 1f

        init {
            x -= numberDeltaFont.getWidth(text) / 2f
            y += numberDeltaFont.baselineToTop * numberDeltaFont.scale
        }

        fun draw(g: Graphics) {
            numberDeltaFont.drawStringPaired(numberDeltaFontBg, x, y, text, Colour.black, Colour.white)
        }

        fun update(dt: Float) {
            y += 15f * dt
            age += dt
        }
    }

    companion object {
        /**
         * Below this share of their health, a crewmember's box in the crew
         * list blinks red, this many seconds on and off.
         */
        const val LOW_HEALTH = 0.25f
        const val LOW_HEALTH_BLINK = 0.4f

        // A levelled-up skill: how fast the crew box flashes, and how far
        // its icon rises as it fades
        private const val SKILL_UP_FLASH = 0.15f
        private const val SKILL_UP_RISE = 6f

        // Vanilla's events for getting away from a hazard, waiting out of
        // fuel, worst first (the pulsar's is AE's)
        private val ESCAPE_EVENTS = linkedMapOf(
            Beacon.EnvironmentType.SUN to "FUEL_ESCAPE_SUN",
            Beacon.EnvironmentType.PULSAR to "FUEL_ESCAPE_PULSAR",
            Beacon.EnvironmentType.ASTEROID to "FUEL_ESCAPE_ASTEROIDS",
            Beacon.EnvironmentType.ION_STORM to "FUEL_ESCAPE_STORM",
        )

        // The door cursor's animation (img/mouse/mouse_door_strip.png): its
        // frames, and how long each is shown
        private const val DOOR_CURSOR_FRAMES = 5
        private const val DOOR_CURSOR_FRAME_NANOS = 120_000_000L

        /**
         * How long the warning for one of XFTL's nebulas shows, in seconds.
         */
        const val NEBULA_WARNING_TIME = 8f

        /**
         * The Y position of the last line of the warning for one of XFTL's nebulas.
         */
        const val NEBULA_WARNING_Y = 64

        // How far the mouse moves with a weapon held before it's dragged
        const val WEAPON_DRAG_DISTANCE = 6

        // How wide the name in a weapon or drone's box in the weapons bar is
        const val WEAPON_NAME_WIDTH = 58

        // How far a weapon's charge bar sticks out left of its box
        const val CHARGE_BAR_SPACE = 8

        // Where the intruders warning is, right of the crew list's top
        const val INTRUDERS_WARNING_X = 240
        const val INTRUDERS_WARNING_DY = 60

        // How far right the nebula warning moves when a sun or pulsar's
        // warning is showing too
        const val NEBULA_WARNING_SHIFT = 200

        /**
         * The diagonal distance (squared) that the crew selection box has to be before it appears. If the
         * player drags out an area smaller than this, it'll be treated as a click and select whoever is
         * standing underneath.
         */
        val SELECTION_BOX_SIZE = 6f.pow(2f).toInt()

        /**
         * This is the font used for truncating crew names, so expose it here.
         */
        const val CREW_NAME_FONT = "JustinFont8"
        const val MAX_NAME_WIDTH = 50

        private const val INSUFFICIENT_SCRAP_FLASH_TIME = 0.35f
        private const val WEAPON_BOX_GLOW = 12

        // The width of a weapon's box in the weapons box (a drone's is wider)
        private const val WEAPON_BUTTON_WIDTH = 87

        private const val MIN_BEAM_LENGTH = 10f

        private const val CREW_BOX_WIDTH = 86
        private const val CREW_BOX_HEIGHT = 27

        // The open skills panel (see drawCrewBox), and a Crystal's lockdown
        // button in it (see drawLockdownButton)
        private const val SKILLS_PANEL_HEIGHT = 146
        private const val SKILLS_COLUMN_X = 89

        // Wider than vanilla's (whose bar was 40 pixels), for the six levels'
        // cells: each skill's icon, then its bar, of five 14 pixel cells
        private val SKILL_BAR_WIDTH = skillBarWidth(14)
        private val SKILLS_COLUMN_WIDTH = 4 + 30 + SKILL_BAR_WIDTH + 6
        private val SKILLS_PANEL_WIDTH = SKILLS_COLUMN_X + SKILLS_COLUMN_WIDTH

        // As wide as the column it's in, less a margin either side (vanilla's
        // was 70 in an 80 pixel column)
        private val LOCKDOWN_BUTTON_WIDTH = SKILLS_COLUMN_WIDTH - 10
        private const val SKILL_ROW_HEIGHT = 24
        private const val LOCKDOWN_BOX_HEIGHT = 20
        private const val LOCKDOWN_BUTTON_HEIGHT = 12
        private const val LOCKDOWN_BUTTON_RADIUS = 2
        private val LOCKDOWN_BUTTON_HOVER = Colour(210, 210, 210)
        private val LOCKDOWN_BUTTON_CHARGING = Colour(70, 80, 90)
        private val LOCKDOWN_BUTTON_CHARGING_TEXT = Colour(150, 150, 150)
        private const val CREW_BOX_SPACING = 30

        // How far the crew list can move up, into the space below the
        // evasion and oxygen box, to keep it clear of the reactor
        private const val MAX_CREW_LIST_SHIFT = 10

        // The gap kept between the crew stations buttons and the reactor,
        // and how far below their top those buttons reach
        private const val CREW_REACTOR_GAP = 8
        private const val CREW_STATIONS_HEIGHT = 30

        // The vertical distance between the indicators of ships with
        // multiple shields systems.
        private const val SHIELD_BOX_SPACING = 47

        // The stacked super-shield panel's position. It's as far from the
        // shields indicator's frame (which ends at x=127) and the fuel
        // counter's (which ends 32px below the top of the shields) as
        // those two are from each other: 4px.
        private const val SUPER_SHIELD_PANEL_MARGIN = 4

        /** The most Zoltan shields shown under the shields indicators' bubbles, rather than in a panel. */
        private const val MAX_SUPER_SHIELD_ROWS_IN_BOXES = 2
        private const val SUPER_SHIELD_PANEL_X = 127 + 1 + SUPER_SHIELD_PANEL_MARGIN
        private const val SUPER_SHIELD_PANEL_Y = 32 + 1 + SUPER_SHIELD_PANEL_MARGIN

        // The horizontal distance between the buttons of ships with
        // multiple backup batteries, and where the subsystems box is widened
        // to fit them.
        private const val BATTERY_SPACING = 48

        // The distance between the other subsystems' power buttons
        private const val SUBSYSTEM_SPACING = 36
        // Where the doors' notch starts in the subsystems box image, and near
        // where the battery's ends (just before the bottom-right corner's
        // bevel, so that's not repeated).
        private const val SUBSYSTEM_BOX_DOORS_X = 85
        private const val SUBSYSTEM_BOX_BATTERY_END_X = 154

        // The vertical distance between the boxes of ships with multiple
        // weapons systems, which are stacked on top of each other.
        private const val WEAPON_BOX_STACK_SPACING = 80

        // Where the pulsar's warning goes, and with a sun's warning too:
        // under the FTL button, as right under the sun's it would be covered
        // by the enemy windows when there are several of them
        private val PULSAR_WARNING = ConstPoint(825, 64)
        private val PULSAR_WARNING_BELOW_SUN = ConstPoint(640, 110)
        private val SUN_WARNING = ConstPoint(825, 64)

        // The shields critical warning, under the scrap counter
        private val SHIELDS_WARNING = ConstPoint(445, 98)

        // The Anti-Ship Battery's icon (see drawBatteryIcon), over where the
        // hazards' warnings are, which move down under it, and the room the
        // top line of a warning takes above its centre
        private const val BATTERY_ICON_CENTRE_X = 825
        private const val BATTERY_ICON_Y = 4
        private const val BATTERY_ICON_GAP = 6
        private const val WARNING_TEXT_HEIGHT = 14

        // The Anti-Ship Battery's two-line 'target locked' warning, a little
        // under its icon (its centre's at its second line), and the room it
        // takes, which the other warnings move down by
        private val BATTERY_WARNING = ConstPoint(BATTERY_ICON_CENTRE_X, BATTERY_ICON_Y + 58 + 8 + 12 + 18)
        private const val BATTERY_WARNING_HEIGHT = 44

        // The gap between the autofire button's text and the right of its tab
        private const val AUTOFIRE_TEXT_PADDING = 5

        // How much taller the autofire button's tab is than the label's
        private const val AUTOFIRE_TOP_PADDING = 2

        private val SUPER_SHIELD_PANEL_BACKGROUND = Colour(0f, 0f, 0f, 0.6f)

        // The largest reactor that's drawn in a single column. This is the
        // vanilla limit, so vanilla ships are drawn as normal.
        private const val SINGLE_COLUMN_REACTOR_POWER = 25

        // The lines splitting a bigger ship's hull cells
        val HULL_CELL_DIVIDER = Colour(0f, 0f, 0f, 0.55f)

        // The distance between the columns of a bigger reactor's power bars
        private const val REACTOR_COLUMN_SPACING = 16

        // The reactor power that's still to be bought, faint
        private val REACTOR_UNPURCHASED = Colour(251, 251, 251, 50)
    }
}
