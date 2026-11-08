package xyz.znix.xftl.game

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.Constants
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewAnaerobic
import xyz.znix.xftl.crew.CrewCrystal
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.systems.Teleporter
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import kotlin.math.ceil
import kotlin.math.max

/**
 * The mechanics of one of the size 3 bosses (see doc/game/bosses.md), which
 * each hold their sector's exit. A boss ship ([Ship.colossal]) is built
 * exactly as its blueprint says (see ShipGenerator), and always fights the
 * player alone. This adds what makes it more than a big ship:
 *
 * - its first boarding party, lined up in its teleporter before the fight,
 *   which goes at once;
 * - a linked crew (the Vigilance's and the Stormbreaker's), who fight and
 *   repair as if fully skilled, until the link breaks and stuns them;
 * - the Devourer eats asteroids, and the Stormbreaker charges its ion
 *   weapons from the storm, and sends its swarm over with a second teleporter;
 * - the Ossuary's slipway rebuilds its drones;
 * - the Mainframe's three phases (see doc/game/mainframe.md): the
 *   Firewall's super shield that comes back, the Splicing Bays' drones,
 *   and the Mind's Chorus pulses;
 * - repairs while the player's away from its beacon.
 */
class ColossalBoss(val kind: Kind, val ship: Ship) {
    /**
     * The bosses, by their blueprint's xftlBoss element, and their [tier]:
     * how far through the game they are, which scales their rewards (see
     * [BossRewards]).
     */
    enum class Kind(val tier: Int, val mainframePhase: Int = 0) {
        /** The Maw's: asteroids repair it, and there's no air aboard. */
        DEVOURER(1),

        /** The Blockade Line's: its crew are linked, and it has two Zoltan shields. */
        VIGILANCE(2),

        /** The Gullet's: its relay (mind control) links its swarm of a crew. */
        STORMBREAKER(3),

        /** The Belly's: its slipway rebuilds its drones. */
        OSSUARY(4),

        /**
         * CONCORD's core, at the end of the game (see doc/game/mainframe.md),
         * in three phases, each a station that never dodges. Beating the
         * last wins the game. The first, the Firewall: its Firewall core
         * brings its super shield back.
         */
        MAINFRAME(5, 1),

        /** The Mainframe's second phase: its bays build more drones as it fights. */
        MAINFRAME_BAYS(5, 2),

        /**
         * The Mainframe's third: its weapons look like others, its mind
         * control is quick, and it sends Chorus pulses.
         */
        MAINFRAME_MIND(5, 3),
    }

    // How many crew it started with, for the Vigilance's link (a third of
    // them killed breaks it)
    private var startingCrew: Int = 0

    // The Vigilance's link, once broken, stays broken
    private var linkBroken: Boolean = false

    // Whether the Stormbreaker's relay was working last time it was checked,
    // so its crew are stunned once as it breaks
    private var relayWasWorking: Boolean = true

    // How long the Stormbreaker's swarm pad has been ready to send its next
    // wave, in seconds (its first goes as the fight starts)
    private var swarmTime: Float = SWARM_TIME

    // Whether its first boarding party, lined up in its teleporter before
    // the fight (see lineUpBoarders), has gone
    private var openingWaveSent: Boolean = false

    // How long the Mainframe's Firewall core has been bringing its super
    // shield back, in seconds (see updateFirewall)
    private var firewallTime: Float = 0f

    // How far each of the Splicing Bays is through building its hybrid, in
    // seconds (see updateBays)
    private val bayTimes = FloatArray(MAX_BAYS)

    // How long since the Mind's last Chorus pulse, in seconds (see updateChorus)
    private var chorusTime: Float = 0f

    /**
     * True while its crew are linked by the Chorus: they fight and repair
     * as if fully skilled (see LivingCrew).
     */
    val isLinked: Boolean
        get() = when (kind) {
            Kind.VIGILANCE -> !linkBroken
            Kind.STORMBREAKER -> isRelayWorking
            else -> false
        }

    /**
     * The Stormbreaker's relay is its mind control system: it works until
     * it's broken.
     */
    val isRelayWorking: Boolean
        get() = kind == Kind.STORMBREAKER && ship.mindControlSystems.any { !it.broken }

    /**
     * How much longer its mind control waits between uses: the
     * Stormbreaker's relay makes it twice as often.
     */
    val mindControlCooldownFactor: Float
        get() = when {
            isRelayWorking -> 0.5f
            kind == Kind.MAINFRAME_MIND -> MIND_CONTROL_FACTOR
            else -> 1f
        }

    /**
     * The Ossuary's slipway is its first drones system: while it works, the
     * Ossuary never runs short of drone parts.
     */
    val isSlipwayWorking: Boolean
        get() = kind == Kind.OSSUARY && ship.droneSystems.firstOrNull()?.broken == false

    /**
     * The Mainframe is a station: it never dodges (see Ship.evasion), nor
     * runs.
     */
    val isStation: Boolean get() = kind.mainframePhase > 0

    /**
     * The Mainframe's Firewall core is its first shields system: while it
     * works, its super shield comes back (see [updateFirewall]).
     */
    val isFirewallCoreWorking: Boolean
        get() = kind == Kind.MAINFRAME && ship.shieldSystems.firstOrNull()?.broken == false

    /**
     * Put its first boarding party in its teleporter, ready for the fight:
     * as many as fit, up to half its crew. Ordinary ships gather theirs
     * once the fight's started (see FriendlyCrewAI), but a boss's are
     * waiting, and go at once (see [update]).
     */
    fun lineUpBoarders() {
        val pad = ship.teleporter?.room ?: return
        val wanted = minOf(pad.cellCount, ship.crew.size / 2)
        for (crew in ship.crew.filterIsInstance<LivingCrew>().toList()) {
            if (ship.crew.count { it.room == pad } >= wanted)
                break
            if (crew.room == pad)
                continue
            val spot = ship.findSpaceForCrew(pad, AbstractCrew.SlotType.CREW)
            if (spot.room != pad)
                break
            crew.jumpTo(spot)
        }
        // (the room's list of its crew is otherwise only updated with the ship)
        pad.updateCrewInRoom()
    }

    /**
     * Called as the fight with it starts, once its crew are aboard.
     */
    fun fightStarted() {
        if (startingCrew == 0)
            startingCrew = ownCrew().size
    }

    /**
     * Start the fight the way an event says (its `<xftlBossOpening>`):
     * `ambush`, with its shields ionised (so the player's first volleys get
     * through), or `noSuperShield`, with its Zoltan shields down.
     */
    fun opening(how: String) {
        when (how) {
            "ambush" -> {
                for (shields in ship.shieldSystems)
                    shields.dealDamage(0, AMBUSH_ION)
                ship.superShield = 0
            }

            "noSuperShield" -> ship.superShield = 0
            else -> println("[WARN] Unknown boss opening '$how'")
        }
    }

    fun update(dt: Float) {
        if (startingCrew == 0)
            fightStarted()

        // The Vigilance's link breaks once a third of its crew are dead
        if (kind == Kind.VIGILANCE && !linkBroken && startingCrew > 0 &&
            ownCrew().size <= startingCrew * 2 / 3
        ) {
            linkBroken = true
            stunCrew(LINK_BREAK_STUN)
        }

        // The Stormbreaker's crew are stunned when its relay breaks
        if (kind == Kind.STORMBREAKER) {
            val working = isRelayWorking
            if (relayWasWorking && !working)
                stunCrew(RELAY_BREAK_STUN)
            relayWasWorking = working
        }

        sendOpeningWave()

        if (kind == Kind.STORMBREAKER)
            updateSwarm(dt)

        // The Ossuary's slipway rebuilds its drones from what it salvages
        if (isSlipwayWorking && ship.dronesCount < SLIPWAY_DRONE_PARTS)
            ship.dronesCount = SLIPWAY_DRONE_PARTS

        updateFirewall(dt)
        if (kind == Kind.MAINFRAME_BAYS)
            updateBays(dt)
        if (kind == Kind.MAINFRAME_MIND)
            updateChorus(dt)
    }

    /**
     * The Splicing Bays: its last [MAX_BAYS] drones systems, which start
     * empty (see tools/boss_ships.py).
     */
    private val bays: List<Drones> get() = ship.droneSystems.takeLast(MAX_BAYS)

    /**
     * The Splicing Bays: while one works, has an empty slot, and the
     * player's fighting it, it builds a drone (one of [BAY_DRONES], at
     * random) in [BAY_BUILD_TIME] seconds, which goes in the slot, for its
     * AI to launch. A broken bay starts again.
     */
    private fun updateBays(dt: Float) {
        val game = ship.sys
        if (game.getEnemyOf(ship) == null)
            return
        for ((index, bay) in bays.withIndex()) {
            val slot = bay.drones.indexOfFirst { it == null }
            if (bay.broken || slot == -1) {
                bayTimes[index] = 0f
                continue
            }
            bayTimes[index] += dt
            if (bayTimes[index] < BAY_BUILD_TIME)
                continue
            bayTimes[index] = 0f
            val drone = game.blueprintManager.getOrNull(BAY_DRONES.random()) as? DroneBlueprint ?: continue
            bay.drones[slot] = Drones.DroneInfo(drone, null)
            ship.cargoUpdated()
        }
    }

    /** How far the bay at [index] is through building its hybrid, from 0 to 1. */
    fun bayProgress(index: Int): Float = bayTimes.getOrElse(index) { 0f } / BAY_BUILD_TIME

    /**
     * The Mind's Chorus pulses: every [CHORUS_TIME] seconds while its mind
     * control system works, the most crowded room on the player's ship
     * joins the Chorus for [CHORUS_PULSE_TIME] seconds, everyone in it (as
     * if mind controlled). The Lanius never do, nor Slugs, and a Crystal in
     * the room sings back, so nobody in it does. A super shield blocks it,
     * as it does mind control.
     */
    private fun updateChorus(dt: Float) {
        val game = ship.sys
        val target = game.getEnemyOf(ship) ?: return
        val mind = ship.mindControlSystems.firstOrNull { !it.broken && !it.isHackActive } ?: return

        chorusTime += dt
        if (chorusTime < CHORUS_TIME)
            return
        chorusTime = 0f

        if (target.superShield > 0 && !ship.hasAugment(AugmentBlueprint.ZOLTAN_BYPASS))
            return
        val room = target.rooms.maxByOrNull { room -> chorusTargets(room, target).size } ?: return
        if (room.crew.any { it is CrewCrystal && it.mode == AbstractCrew.SlotType.CREW })
            return
        val crew = chorusTargets(room, target)
        if (crew.isNotEmpty())
            mind.chorusPulse(crew, CHORUS_PULSE_TIME)
    }

    // The player's crew in [room] a Chorus pulse can take
    private fun chorusTargets(room: Room, target: Ship): List<LivingCrew> =
        room.crew.filterIsInstance<LivingCrew>().filter {
            it.ownerShip == target && it.mindControlledBy == null && !it.isMindControlResistant &&
                    it !is CrewAnaerobic && it.currentAction != AbstractCrew.Action.DYING
        }

    /**
     * The Mainframe's Firewall: while its core works, its super shield
     * comes back, [FIREWALL_SHIELD] every [FIREWALL_TIME] seconds it's
     * short of full.
     */
    private fun updateFirewall(dt: Float) {
        if (!isFirewallCoreWorking || ship.superShield >= ship.maxSuperShield) {
            firewallTime = 0f
            return
        }
        firewallTime += dt
        if (firewallTime < FIREWALL_TIME)
            return
        firewallTime = 0f
        ship.superShield += FIREWALL_SHIELD
    }

    /**
     * Send the boarding party lined up in its teleporter (see
     * [lineUpBoarders]) as soon as the teleporter has power.
     */
    private fun sendOpeningWave() {
        if (openingWaveSent)
            return
        val teleporter = ship.teleporter
        val target = ship.sys.getEnemyOf(ship)
        if (teleporter == null || ship.crew.none { it.room == teleporter.room }) {
            openingWaveSent = true
            return
        }
        if (target == null || target.isCloakActive || !teleporter.isSendAvailable)
            return
        teleporter.selectTeleportAction(true, target.rooms.random())
        openingWaveSent = true
    }

    /**
     * The Stormbreaker's swarm: its first teleporter is used by its crew's
     * AI as any ship's is, and its second sends a wave of its crew (as many
     * as fit on its pad, up to [SWARM_SIZE]) straight from its decks to the
     * player's ship every [SWARM_TIME] seconds while it's working, keeping
     * [SWARM_KEEP] aboard to crew it.
     */
    private fun updateSwarm(dt: Float) {
        val pad = ship.teleporterSystems.getOrNull(1) ?: return
        val game = ship.sys
        val target = game.getEnemyOf(ship) ?: return
        val blocked = target.superShield > 0 && !ship.hasAugment(AugmentBlueprint.ZOLTAN_BYPASS)
        if (!pad.isSendAvailable || target.isCloakActive || blocked) {
            return
        }

        swarmTime += dt
        if (swarmTime < SWARM_TIME)
            return
        swarmTime = 0f

        val aboard = ownCrew().filter { it.room.ship == ship && !it.isTeleporting }
        val count = minOf(pad.room!!.cellCount, SWARM_SIZE, aboard.size - SWARM_KEEP)
        if (count <= 0)
            return
        for (crew in aboard.shuffled().take(count))
            crew.teleportAnimatedTo(target.rooms.random())
        pad.ionTimer += Teleporter.cooldownTime(pad.powerSelected).toFloat()
    }

    /**
     * How much faster one of its weapons charges: the Stormbreaker charges
     * its ion weapons from an ion storm.
     */
    fun chargeFactor(weapon: AbstractWeaponInstance, beacon: Beacon?): Float {
        if (kind != Kind.STORMBREAKER || beacon == null)
            return 1f
        if (Beacon.EnvironmentType.ION_STORM !in beacon.environmentTypes)
            return 1f
        return if (weapon.type.ionDamage > 0) STORM_CHARGE_FACTOR else 1f
    }

    /**
     * True if an asteroid hitting it repairs it rather than hurting it:
     * the Devourer eats them.
     */
    val eatsAsteroids: Boolean get() = kind == Kind.DEVOURER

    /**
     * Called each time the player jumps while it's at another beacon (its
     * sector's exit): it repairs a share of its hull and all its systems.
     * Its dead crew stay dead.
     */
    fun repairWhileAway() {
        repairHull(ceil(ship.maxHealth * AWAY_REPAIR).toInt())
        for (system in ship.systems) {
            system.damagedEnergyLevels = 0
        }
    }

    fun repairHull(amount: Int) {
        if (ship.isDead)
            return
        ship.health = (ship.health + amount).coerceAtMost(ship.maxHealth)
    }

    private fun ownCrew(): List<LivingCrew> {
        val game = ship.sys
        val ships = listOfNotNull(ship, game.player).distinct()
        return ships.flatMap { it.crew }
            .filterIsInstance<LivingCrew>()
            .filter { it.ownerShip == ship && it.currentAction != AbstractCrew.Action.DYING }
    }

    private fun stunCrew(time: Float) {
        for (crew in ownCrew())
            crew.stunTime = max(crew.stunTime, time)
    }

    fun saveToXML(elem: Element) {
        SaveUtil.addAttrInt(elem, "startingCrew", startingCrew)
        SaveUtil.addAttrBool(elem, "linkBroken", linkBroken)
        SaveUtil.addAttrBool(elem, "relayWasWorking", relayWasWorking)
        SaveUtil.addAttrFloat(elem, "swarmTime", swarmTime)
        SaveUtil.addAttrBool(elem, "openingWaveSent", openingWaveSent)
        SaveUtil.addAttrFloat(elem, "firewallTime", firewallTime)
        for ((index, time) in bayTimes.withIndex())
            SaveUtil.addAttrFloat(elem, "bayTime$index", time)
        SaveUtil.addAttrFloat(elem, "chorusTime", chorusTime)
    }

    fun loadFromXML(elem: Element) {
        startingCrew = SaveUtil.getAttrInt(elem, "startingCrew")
        linkBroken = SaveUtil.getAttrBool(elem, "linkBroken")
        relayWasWorking = SaveUtil.getAttrBool(elem, "relayWasWorking")
        swarmTime = SaveUtil.getAttrFloat(elem, "swarmTime")
        openingWaveSent = SaveUtil.getAttrBool(elem, "openingWaveSent")
        firewallTime = elem.getAttributeValue("firewallTime")?.toFloat() ?: 0f
        for (index in bayTimes.indices)
            bayTimes[index] = elem.getAttributeValue("bayTime$index")?.toFloat() ?: 0f
        chorusTime = elem.getAttributeValue("chorusTime")?.toFloat() ?: 0f
    }

    /**
     * Draw what it's up to over its rooms, on top of its ship ([g] is in the
     * ship's own coordinates): the Splicing Bays' build bars.
     */
    fun renderOverlay(g: Graphics) {
        if (kind != Kind.MAINFRAME_BAYS)
            return
        for ((index, bay) in bays.withIndex()) {
            val room = bay.room ?: continue
            val x = room.offsetX + BAR_MARGIN
            val y = room.offsetY + room.pixelHeight - BAR_MARGIN - BAR_HEIGHT
            val width = room.pixelWidth - BAR_MARGIN * 2
            g.colour = Constants.UPGRADE_DETAILS_POWER_OFF
            g.fillRect(x.f, y.f, width.f, BAR_HEIGHT.f)
            g.colour = if (bay.broken) Constants.SYS_ENERGY_BROKEN else Constants.SYS_ENERGY_ACTIVE
            g.fillRect(x.f, y.f, width * bayProgress(index), BAR_HEIGHT.f)
        }
    }

    companion object {
        /** How often the Stormbreaker's swarm pad sends a wave, how many at most, and how many stay aboard. */
        const val SWARM_TIME = 25f
        const val SWARM_SIZE = 6
        const val SWARM_KEEP = 12

        /** The ion damage an ambush does to each of a boss's shields systems. */
        const val AMBUSH_ION = 3

        /** The fewest drone parts the Ossuary has while its slipway works. */
        const val SLIPWAY_DRONE_PARTS = 10

        /** How long the crew are stunned when the Vigilance's link breaks. */
        const val LINK_BREAK_STUN = 5f

        /** How long the crew are stunned when the Stormbreaker's relay breaks. */
        const val RELAY_BREAK_STUN = 10f

        /** How much faster the Stormbreaker's ion weapons charge in an ion storm. */
        const val STORM_CHARGE_FACTOR = 1.5f

        /** How many of the Splicing Bays' drones systems are bays (its last ones). */
        const val MAX_BAYS = 2

        /** How long a bay takes to build a drone, and the drones it builds. */
        const val BAY_BUILD_TIME = 40f
        val BAY_DRONES = listOf(
            "BATTLE", "XFTL_X2_BATTLE", "XFTL_X3_BATTLE", "COMBAT_1", "XFTL_X2_COMBAT_1",
            "COMBAT_BEAM", "DEFENSE_1", "XFTL_X2_DEFENSE_1", "BOARDER",
        )

        /** The bays' build bars: their height, and how far in from the room's edge. */
        private const val BAR_HEIGHT = 4
        private const val BAR_MARGIN = 3

        /** How often the Mind sends a Chorus pulse, and how long it lasts. */
        const val CHORUS_TIME = 30f
        const val CHORUS_PULSE_TIME = 8f

        /** How much longer the Mind's mind control waits between uses: 12 seconds rather than 20. */
        const val MIND_CONTROL_FACTOR = 0.6f

        /** How often the Mainframe's Firewall core brings its super shield back, and by how much. */
        const val FIREWALL_TIME = 25f
        const val FIREWALL_SHIELD = 5

        /** The share of its hull a boss repairs each jump the player's away. */
        const val AWAY_REPAIR = 0.1f
    }
}
