package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.SystemInfo
import xyz.znix.xftl.Translator
import xyz.znix.xftl.replaceArg
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.game.*
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.sys.Input

class MindControl(blueprint: SystemBlueprint) : MainSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.MIND_CONTROL
    override val insertButtonSpace: Boolean get() = true

    /**
     * The time remaining on the cloak, or null if the cloak is inactive.
     */
    var timeRemaining: Float? = null

    val active: Boolean get() = timeRemaining != null

    var controlledCrew: LivingCrew? = null
        private set

    /**
     * The crew taken by a Chorus pulse (the Mainframe's Mind, see
     * ColossalBoss.updateChorus), as well as [controlledCrew], and how long
     * each has left. They aren't saved: loading a game frees them.
     */
    private val pulsedCrew = HashMap<LivingCrew, Float>()

    override val isPowerLocked: Boolean get() = super.isPowerLocked || active
    override val hasWhiteLockingBox: Boolean get() = active

    val ready: Boolean get() = powerSelected > 0 && !isPowerLocked && !isHackActive

    private val startSound by onInit { it.sounds.getSample("mindControl") }
    private val endSound by onInit { it.sounds.getSample("mindControlEnd") }

    private var button: MindControlButton? = null

    val duration: Float
        get() = when (powerSelected) {
            0 -> 0.001f // Dummy value
            else -> durationFor(powerSelected)
        }

    /**
     * The room picked on a cloaked ship: as with the teleporter, it waits
     * until the cloak's down (see [selectRoom]).
     */
    var pendingRoom: Room? = null
        private set

    override fun update(dt: Float) {
        super.update(dt)

        // The Chorus pulse's crew are freed as their time runs out (see
        // LivingCrew.update), or once the fight's over
        val pulseOver = ship.sys.getEnemyOf(ship) == null
        pulsedCrew.entries.removeAll { entry ->
            entry.setValue(entry.value - dt)
            entry.value <= 0f || pulseOver || entry.key.mindControlledBy != this
        }

        pendingRoom?.let { room ->
            if (!ship.sys.isShipPresent(room.ship) || !ready) {
                pendingRoom = null
            } else if (!room.ship.isCloakActive) {
                pendingRoom = null
                selectRoom(room)
            }
        }

        // As with the teleporter and the cloak, a hacking pulse breaks the
        // mind control it's keeping up
        if (isHackActive && controlledCrew != null) {
            switchToCooldown()
            return
        }

        val oldTime = timeRemaining
        if (oldTime == null || controlledCrew == null) {
            timeRemaining = null
            controlledCrew = null
            return
        }

        checkEnemyGone()
        if (timeRemaining == null) {
            // If checkEnemyGone reset the timer
            return
        }

        // Clamp the timer, so if the system is damaged it cuts down
        // the remaining time.
        val clamped = oldTime.coerceAtMost(duration)

        val newTime = clamped - dt
        if (newTime <= 0) {
            switchToCooldown()
            return
        }

        timeRemaining = newTime
    }

    private fun switchToCooldown() {
        // Only play the ending sound if a crew was mind-controlled.
        // If we counter enemy mind control, this should only play
        // for the enemy ship's system.
        if (controlledCrew != null) {
            endSound.play()
        }

        controlledCrew?.mindControlledBy = null
        timeRemaining = null
        controlledCrew = null

        // (the Stormbreaker's relay makes it twice as often, see ColossalBoss)
        ionTimer = 20f * (ship.colossal?.mindControlCooldownFactor ?: 1f)
    }

    private fun checkEnemyGone() {
        val crew = controlledCrew ?: return

        // If the ship the crew was on has gone, we're probably not
        // in combat and we can reset immediately, without triggering
        // a cooldown.
        // This also avoids playing the end sound effect when the
        // enemy ship is destroyed.
        if (!ship.sys.isShipPresent(crew.room.ship)) {
            controlledCrew = null
            timeRemaining = null
            return
        }

        // If the crew disappears (most notably because they died), then
        // go to cooldown right away.
        if (!crew.room.ship.crew.contains(crew)) {
            switchToCooldown()
            return
        }

        // If the crew is no longer mind-controlled by us, something odd
        // happened - so go onto cooldown.
        if (crew.mindControlledBy != this) {
            switchToCooldown()
            return
        }
    }

    fun isControlling(crew: LivingCrew): Boolean {
        // Check if one of the ships has jumped away, or is no longer
        // hostile towards the other.
        // This doesn't apply to intruders, since they can always
        // be mind-controlled, regardless of what ship they belong to.
        if (ship.sys.getEnemyOf(ship) == null && crew.room.ship != ship)
            return false

        return (active && crew == controlledCrew) || crew in pulsedCrew
    }

    /**
     * A Chorus pulse: take all of [crew] for [time] seconds, as if mind
     * controlled, without using the system or putting it on cooldown.
     */
    fun chorusPulse(crew: List<LivingCrew>, time: Float) {
        for (member in crew) {
            member.mindControlledBy = this
            pulsedCrew[member] = time
        }
        if (crew.isNotEmpty())
            startSound.play()
    }

    override fun makeExtraButtons(powerPos: IPoint): List<Button> {
        button = MindControlButton(powerPos)
        return listOf(button!!)
    }

    /**
     * Mind control a crew member directly, without picking a room the player
     * can see (for the video recorder, as nebulas block the sensors).
     */
    fun debugControl(crew: LivingCrew) {
        if (!ready || active)
            return
        crew.mindControlledBy = this
        controlledCrew = crew
        timeRemaining = duration
        startSound.play()
    }

    fun selectRoom(room: Room) {
        if (!ready)
            return

        // Nothing on a cloaked ship can be reached until it uncloaks
        if (room.ship != ship && room.ship.isCloakActive) {
            pendingRoom = room
            return
        }
        pendingRoom = null

        // A ship with the Chorus Relay Shard hears it coming: its crew can't
        // be mind controlled for the start of the fight
        val shard = room.ship.getAugmentValue(AugmentBlueprint.RELAY_SHARD)
        if (room.ship != ship && shard > 0f && ship.hostileTime < shard)
            return

        // Block mind-control on enemy ships with a super shield, unless
        // this ship has the Zoltan Shield Bypass
        if (room.ship != ship && room.ship.superShield > 0 && !ship.hasAugment(AugmentBlueprint.ZOLTAN_BYPASS))
            return

        // Only rooms it can see the crew in (with its sensors, or everywhere
        // with the Lifeform Scanner or a Slug, see Ship.canSeeInto), for the
        // player and enemies alike
        if (!ship.canSeeInto(room))
            return

        // Ships can have several mind control systems, but they can't target
        // the same room. Otherwise, they could pick the same crewmember, which
        // would count as countering each other's mind control.
        val otherSystems = ship.mindControlSystems.filter { it != this }
        if (otherSystems.any { it.controlledCrew?.room == room })
            return

        // If we're attacking a room on the player's ship, look for intruders.
        // Otherwise, look for crewmembers on enemy ships.
        val targetMode = when (room.ship) {
            ship -> AbstractCrew.SlotType.INTRUDER
            else -> AbstractCrew.SlotType.CREW
        }

        val suitableCrew = room.crew.filterIsInstance(LivingCrew::class.java)
            .filter { it.mode == targetMode }
            .filter { !it.isMindControlResistant }
        if (suitableCrew.isEmpty())
            return

        // Note we don't prefer countering mind control for our own
        // crew over controlling enemy crew.
        val crew = suitableCrew.random()

        // Countering mind control immediately puts both systems on cooldown
        val otherMC = crew.mindControlledBy
        if (otherMC != this && otherMC != null) {
            otherMC.switchToCooldown()
            switchToCooldown()
            return
        }

        crew.mindControlledBy = this
        controlledCrew = crew
        timeRemaining = duration

        startSound.play()
    }

    override fun hotkeyPressed(key: Hotkey) {
        super.hotkeyPressed(key)

        if (key.id == VanillaHotkeys.SYS_ACTION_MIND) {
            // This is a bit hacky, but it properly shows the super-shield warning
            button?.click(Input.MOUSE_LEFT_BUTTON)
        }
    }

    override fun saveSystem(elem: Element, refs: ObjectRefs) {
        // To avoid serialising a reference to a ship that's gone, check
        // if the enemy has jumped away.
        checkEnemyGone()

        SaveUtil.addTagFloat(elem, "timeRemaining", timeRemaining)

        // (a crewmember on a ship that isn't in the save, such as one of
        // several enemies that's gone, isn't saved as controlled)
        val crew = controlledCrew
        if (crew != null && refs.contains(crew.room.ship)) {
            val shipWithEnemy = crew.room.ship
            SaveUtil.addRef(elem, "shipWithEnemy", refs, shipWithEnemy)
            SaveUtil.addTagInt(elem, "crewId", shipWithEnemy.crew.indexOf(crew))
        }
    }

    override fun loadSystem(elem: Element, refs: RefLoader) {
        timeRemaining = SaveUtil.getTagFloatOrNull(elem, "timeRemaining")

        SaveUtil.getOptionalRef(elem, "shipWithEnemy", refs, Ship::class.java) { shipWithEnemy ->
            if (shipWithEnemy == null)
                return@getOptionalRef

            val crewId = SaveUtil.getTagInt(elem, "crewId")
            controlledCrew = shipWithEnemy.crew[crewId] as LivingCrew
            controlledCrew!!.mindControlledBy = this
        }
    }

    companion object {
        val INFO: SystemInfo = MindControlInfo

        /** Vanilla's most bars; the Chonkestrel mod lets it go to six. */
        const val VANILLA_MAX_POWER = 3

        // From the wiki: 14, 20 and 28 seconds, then, as with the cloak (see
        // Cloaking.durationFor), less and less for each bar past three:
        // four seconds more, three, then two
        private val DURATIONS = listOf(14f, 20f, 28f, 32f, 35f, 37f)

        /** How long mind control lasts with [power] bars (as six, for more). */
        fun durationFor(power: Int): Float = DURATIONS[power.coerceIn(1, DURATIONS.size) - 1]
    }

    // Note the mind control button always uses the two-power height
    private inner class MindControlButton(powerPos: IPoint) : SystemPowerButton(ship.sys, 2, powerPos) {
        override val timeRemaining: Float? get() = this@MindControl.timeRemaining
        override val duration: Float get() = this@MindControl.duration
        override val isOff: Boolean get() = !ready

        private val superShieldWarning = WarningFlasher(
            game, powerPos + ConstPoint(35, -62),
            "warning_super_shield_mind",
            false, colour = GlowColour.WHITE
        )

        override val forceHighlight: Boolean
            get() = game.shipUI.isSelectingMindControlTarget(this@MindControl)

        public override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            if (disabled)
                return

            // If the enemy ship has a zoltan shield but there are intruders
            // on the player's ship, they can activate mind control and place
            // it on the enemy ship - it just doesn't do anything once un-paused.
            // (The Zoltan Shield Bypass gets through, see selectRoom.)
            val hasIntruders = ship.intruders.any { (it as? LivingCrew)?.isMindControlResistant == false }
            val enemyShips = game.getOpponentShips(ship)
            val bypass = ship.hasAugment(AugmentBlueprint.ZOLTAN_BYPASS)
            if (!bypass && !hasIntruders && enemyShips.isNotEmpty() && enemyShips.all { it.superShield > 0 }) {
                superShieldWarning.startFor(3.5f)
                return
            }

            // As with the teleporter, a room waiting for a cloak to drop is
            // forgotten, so it can be picked again or cancelled
            pendingRoom = null

            game.shipUI.mindControlSelected(this@MindControl)
        }

        override fun draw(g: Graphics) {
            super.draw(g)
            superShieldWarning.draw(g)
        }
    }
}

private object MindControlInfo : SystemInfo("mind") {
    override val canBeManned: Boolean get() = false

    override fun create(blueprint: SystemBlueprint) = MindControl(blueprint)

    override fun getLevelName(level: Int, translator: Translator): String {
        return when (level) {
            0 -> translator["mind_1"]
            1 -> translator["mind_2"]
            2 -> translator["mind_3"]
            // Past vanilla's three, the bars make it last longer
            else -> translator["xftl_mind_duration"].replaceArg(MindControl.durationFor(level + 1).toInt())
        }
    }
}
