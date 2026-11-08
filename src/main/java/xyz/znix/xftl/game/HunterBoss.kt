package xyz.znix.xftl.game

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.Sector

/**
 * A sector boss that hunts the player across the map (see
 * doc/game/bosses.md), rather than holding its sector's exit: the Maw's,
 * the Gullet's and the Belly's (a sector's `xftlHunter`, see
 * [xyz.znix.xftl.sector.SectorType.hunterPeriod]). There's no Rebel fleet
 * in its sector; it's what the player has to keep ahead of.
 *
 * It sets out from the sector's exit, and every [period] of the player's
 * jumps, it jumps one beacon towards the beacon the player was at, drawn on
 * the map as the flagship is. If it ends up where the player does, they
 * fight: the first time, its sector's boss event ([event]) is shown, as it
 * was at the exit; after that, it's the same ship, as the player left it
 * (its ship moves with it, from beacon to beacon). It brings its own
 * environment with it ([environment]), the one its beacon had at the exit,
 * and repairs on the jumps it doesn't move (see ColossalBoss.repairWhileAway).
 * Once beaten, it's gone from the map.
 */
class HunterBoss private constructor(val sector: Sector, val game: InGameState) : BossManager {
    override val serialisationType: String get() = SERIALISATION_TYPE

    override val useBossShipUI: Boolean get() = false

    // Can't make beacon lateinit since the parent is nullable
    private lateinit var realBeacon: Beacon

    /** Where it is, or null once it's beaten. */
    override val beacon: Beacon? get() = if (defeated) null else realBeacon

    override var nextBeacon: Beacon? = null
        private set

    /** True if it jumps (to [nextBeacon]) along with the player's next jump. */
    override val jumping: Boolean
        get() = !defeated && nextBeacon != null && turnsWaited + 1 >= period

    private val period: Int get() = sector.type.hunterPeriod ?: 1

    // How many of the player's jumps it's waited through since its last
    private var turnsWaited = 0

    // Whether the player's met it (so its ship's been built, and is at its
    // beacon), and whether it's been beaten since
    private var met = false
    private var defeated = false

    private val mapIcon = BossMapIcon(game)

    constructor(sector: Sector, game: InGameState, start: Beacon) : this(sector, game) {
        realBeacon = start
        start.hunterEnvironment = environment
        start.clearEnvironment()
    }

    /** The event for meeting it the first time: its sector's boss event. */
    val event: Event get() = sector.type.finishEvent!!.resolve()

    /**
     * The environment it brings: its event's own (its nebula being the
     * sector's kind, as the exit's was, see GameMap.addHazards), and the
     * event's extra hazards on top.
     */
    val environment: List<Beacon.EnvironmentType>
        get() {
            val event = event
            var own = event.environment ?: Beacon.EnvironmentType.NORMAL
            val sectorNebula = sector.type.nebula
            if (own == Beacon.EnvironmentType.NEBULA && sectorNebula != null)
                own = sectorNebula

            val types = ArrayList<Beacon.EnvironmentType>()
            if (own != Beacon.EnvironmentType.NORMAL)
                types += own
            for (hazard in event.extraHazards) {
                if (types.size < Beacon.MAX_HAZARDS && types.none { it.family == hazard.family })
                    types += hazard
            }
            return types.ifEmpty { listOf(Beacon.EnvironmentType.NORMAL) }
        }

    /** Its ship, if the player's met it and it's still fighting. */
    private val ship: Ship?
        get() = realBeacon.ship?.takeIf { it.colossal != null && !it.isGone }

    override fun createShip(): Ship = error("A hunting boss's ship comes from its event (see HunterBoss)")

    override fun drawMapIcon(g: Graphics, centre: IPoint) = mapIcon.drawCircling(g, centre)

    override fun drawJumpArc(g: Graphics, from: IPoint, to: IPoint) = mapIcon.drawJumpArc(g, from, to)

    /**
     * Called as the player jumps away from the beacon they're at (before
     * they arrive at the next): it jumps to [nextBeacon], if it's its turn,
     * and otherwise repairs.
     */
    override fun advanceFleet() {
        if (defeated)
            return

        val next = nextBeacon
        if (!jumping || next == null) {
            turnsWaited++
            ship?.colossal?.repairWhileAway()
            return
        }

        // Its ship goes with it, if it's been built
        val old = realBeacon
        val ship = ship
        if (ship != null) {
            old.ship = null
            next.ship = ship
        }

        old.hunterEnvironment = null
        old.clearEnvironment()
        next.hunterEnvironment = environment
        next.clearEnvironment()

        realBeacon = next
        nextBeacon = null
        turnsWaited = 0
    }

    /**
     * Called once the player's arrived at [playerBeacon] (after any fight
     * there's started): it picks the next beacon on its way to them.
     */
    fun playerArrived(playerBeacon: Beacon) {
        if (defeated || realBeacon == playerBeacon) {
            nextBeacon = null
            return
        }
        nextBeacon = sector.findShortestPath(realBeacon, playerBeacon)?.firstOrNull()
    }

    /** Whether it's at [beacon], and the player hasn't met it yet (so its event's due there). */
    fun isWaitingAt(beacon: Beacon): Boolean = !defeated && !met && realBeacon == beacon

    /**
     * The player's arrived where it's waiting (see [isWaitingAt]). Returns
     * its event, to show.
     */
    fun startEncounter(): Event {
        met = true
        return event
    }

    /**
     * Called when a size 3 boss is beaten (destroyed, or its crew killed)
     * in its sector, which can only be this one: it's gone from the map.
     */
    fun bossBeaten() {
        if (defeated)
            return
        defeated = true
        nextBeacon = null
        realBeacon.hunterEnvironment = null
        realBeacon.clearEnvironment()
    }

    override fun bossShipKilled(enemy: Ship) {
        // Its ship's an ordinary event ship, not one of this manager's (see
        // createShip): InGameState calls bossBeaten instead
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        SaveUtil.addAttrRef(elem, "beacon", refs, realBeacon)
        SaveUtil.addAttrRef(elem, "next", refs, nextBeacon)
        SaveUtil.addAttrInt(elem, "turnsWaited", turnsWaited)
        SaveUtil.addAttrBool(elem, "met", met)
        SaveUtil.addAttrBool(elem, "defeated", defeated)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        turnsWaited = SaveUtil.getAttrInt(elem, "turnsWaited")
        met = SaveUtil.getAttrBool(elem, "met")
        defeated = SaveUtil.getAttrBool(elem, "defeated")
        SaveUtil.getAttrRef(elem, "beacon", refs, Beacon::class.java) {
            realBeacon = it!!
            // (not saved with the beacon; the environment that's already
            // been made for it, if the player's there, is loaded with it)
            if (!defeated)
                it.hunterEnvironment = environment
        }
        SaveUtil.getAttrRef(elem, "next", refs, Beacon::class.java) { nextBeacon = it }
    }

    companion object {
        const val SERIALISATION_TYPE = "xftl.hunter"

        fun createForDeserialisation(sector: Sector, game: InGameState): HunterBoss = HunterBoss(sector, game)
    }
}
