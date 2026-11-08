package xyz.znix.xftl.sector

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.VisualRandom
import xyz.znix.xftl.environment.*
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.StoreData
import xyz.znix.xftl.game.StoreProfile
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.savegame.ISerialReferencable
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import kotlin.random.Random

/**
 * Represents a single beacon on the star map.
 */
class Beacon(
    /**
     * The beacon's position on the sector map
     */
    val pos: ConstPoint,

    /**
     * The event at this beacon. Even empty beacons have events, which set stuff like
     * the background and flavour text.
     */
    var event: Event,

    /**
     * True if this is the exit beacon to progress to the next sector.
     */
    val isExit: Boolean,

    /**
     * True if this is the location of the rebel base, in the last stand.
     */
    val isBase: Boolean,

    /**
     * The seed used for this beacon to generate the seeds for everything
     * else (environment, store, etc).
     */
    private val masterSeed: Int,
) : ISerialReferencable {

    /**
     * This beacon's position on the 6x4 grid that all the sectors fit within.
     */
    val gridPos: ConstPoint = pos.divideTruncate(Sector.CELL_SIZE).const

    /**
     * Without long-range scanners, what should this beacon be displayed as?
     *
     * This stores the state of the beacon as the player left it, so they can see if they've previously visited
     * a beacon and if so whether they jumped out of a fight.
     */
    val state: State
        get() = when {
            isOvertaken -> State.OVERTAKEN
            !visited -> State.UNVISITED
            ship != null -> State.VISITED_DANGER
            else -> State.VISITED_CLEAR
        }

    /**
     * The kind of nebula this beacon is, overriding its event's plain nebula.
     * This is how XFTL's own nebulas turn up at vanilla nebula beacons, see
     * [GameMap.generateSector].
     */
    var nebulaOverride: EnvironmentType? = null

    /**
     * The hazards added to this beacon, on top of its event's environment,
     * up to [MAX_HAZARDS] in all (see [GameMap.addHazards]). They aren't used
     * at stores or quests.
     */
    var sectorHazards: List<EnvironmentType> = emptyList()

    /**
     * The environment a hunting boss brings with it while it's at this
     * beacon (see HunterBoss), in place of the beacon's own, or null. It's
     * set again from the boss when a game's loaded, so it isn't saved.
     * Call [clearEnvironment] after changing it (except while loading).
     */
    var hunterEnvironment: List<EnvironmentType>? = null

    /**
     * The environments at this beacon: one, or two, three or four at once
     * (an extreme, catastrophic or apocalyptic hazard, see [isExtremeHazard]),
     * the first being the event's own.
     */
    val environmentTypes: List<EnvironmentType>
        get() {
            // A hunting boss brings its own (see HunterBoss)
            hunterEnvironment?.let { return it }

            var own = nebulaOverride ?: event.environment ?: EnvironmentType.NORMAL

            // A store sits in a plain nebula, whatever kind the rest of the
            // sector's are, so shopping there is safe
            if (hasStore && own.isNebula)
                own = EnvironmentType.NEBULA

            val types = ArrayList<EnvironmentType>(MAX_HAZARDS)
            if (own != EnvironmentType.NORMAL)
                types += own
            if (!hasStore && !hasQuest) {
                for (hazard in sectorHazards) {
                    if (types.size < MAX_HAZARDS && types.none { it.family == hazard.family })
                        types += hazard
                }
            }

            return when {
                types.isEmpty() -> listOf(EnvironmentType.NORMAL)
                !isOvertaken -> types

                // If this beacon has been overtaken, clear any special events,
                // though preserving the nebula-ness of this beacon.
                types.any { it.isNebula } -> listOf(EnvironmentType.NEBULA)
                else -> listOf(EnvironmentType.NORMAL)
            }
        }

    /**
     * The main environment at this beacon: the event's own, if it has one.
     */
    val environmentType: EnvironmentType get() = environmentTypes.first()

    /**
     * True if there are several environments at this beacon at once (two or
     * more), which makes them all stronger.
     */
    val isExtremeHazard: Boolean get() = environmentTypes.size > 1

    /**
     * True if there are three or more environments at this beacon at once:
     * a catastrophic hazard.
     */
    val isCatastrophicHazard: Boolean get() = environmentTypes.size >= 3

    /**
     * True if there are four environments at this beacon at once, the most
     * there can be (a nebula, asteroids, a sun and a pulsar): an apocalyptic
     * hazard.
     */
    val isApocalypticHazard: Boolean get() = environmentTypes.size >= MAX_HAZARDS

    /**
     * The kind of nebula this beacon's in, or null if it isn't in one.
     */
    val nebulaType: EnvironmentType? get() = environmentTypes.firstOrNull { it.isNebula }

    val isNebula: Boolean get() = nebulaType != null

    /**
     * True if this beacon's own environment (its event's, or the nebula it
     * was turned into) is a nebula, not counting any added hazards.
     */
    val isOwnNebula: Boolean
        get() = (nebulaOverride ?: event.environment)?.isNebula == true

    /**
     * True if this beacon has a dangerous environment, as shown on the map.
     */
    val isDangerous: Boolean
        get() = isExtremeHazard || environmentType.isDangerous || antiShipBatteryTarget?.let { it != AntiShipBattery.Target.ENEMY } == true

    /**
     * The sector this beacon resides within.
     *
     * It can only be set once, via [bindSector], and will throw an exception if used before it is set.
     */
    lateinit var sector: Sector
        private set

    /**
     * The beacons connected this beacon on the star map, to which the player can jump from here.
     */
    lateinit var neighbours: List<Beacon>
        private set

    val environmentSeed: Int
    val storeSeed: Int
    val eventSeed: Int

    /**
     * The ship remaining here. Either the player jumped off while fighting it, or it was/became
     * neutral from an event. Currently only the former is supported, when coming back the ship
     * will be gone.
     */
    var ship: Ship? = null
        set(value) {
            field = value

            // Bosses aren't saved at a beacon, since if you jump away and back again they're re-created.
            require(value?.boss == null) { "Cannot save the flagship at a beacon!" }
        }

    /**
     * Has this beacon been previously visited?
     */
    var visited: Boolean = false

    /**
     * Is a store available at this beacon? This isn't the same as event.isStore, as events
     * where a store becomes available as a result of player actions (eg a quest to save
     * a store from the rebels, or saving a merchant that was under attack by pirates) can
     * reveal a store.
     */
    var hasStore: Boolean = event.isStore

    private var internalStore: StoreData? = null

    /**
     * True if the event at this beacon was created by an event.
     *
     * This is used to display the quest label in the map screen,
     * if this beacon is unvisited.
     */
    var hasQuest: Boolean = false

    /**
     * For all the status effects applied at this beacon, this states what
     * the final power limit is for each system by name.
     *
     * Enemy ship effects are left in place and never cleared, while the player
     * can jump between beacons and these need to be preserved.
     */
    val powerLimitEffects = HashMap<String, Int>()

    /**
     * This is a random value between 0-1 that's used to offset
     * the timings of the 'this beacon will be overtaken' flash
     * animation on the beacon map, to ensure all the beacons
     * aren't flashing in sync.
     */
    val overtakeFlashAnimationOffset: Float = VisualRandom.nextFloat()

    var isOvertaken: Boolean = false
        set(value) {
            field = value
            if (value) {
                hasStore = false
                hasQuest = false
            }
        }

    private var actualEnvironment: AbstractEnvironment? = null
    private var actualBattery: AntiShipBattery? = null

    /**
     * Who this beacon's Anti-Ship Battery fires at, or null if it hasn't
     * got one (see [AntiShipBattery]). An overtaken beacon's is the fleet's,
     * from its new event.
     */
    val antiShipBatteryTarget: AntiShipBattery.Target?
        get() = if (isOvertaken) fleetBatteryTarget else event.antiShipBattery

    /**
     * Who the Anti-Ship Battery of the fleet's event fires at, once the
     * fleet's overtaken this beacon and the player's met it here (vanilla's
     * fleet events have one, at the player), or null (see InGameState).
     */
    var fleetBatteryTarget: AntiShipBattery.Target? = null

    init {
        val rand = Random(masterSeed)
        environmentSeed = rand.nextInt()
        storeSeed = rand.nextInt()
        eventSeed = rand.nextInt()
    }

    fun getStore(game: InGameState): StoreData? {
        if (!hasStore)
            return null
        if (internalStore != null)
            return internalStore

        // What it has depends on the sector (see StoreProfile)
        internalStore = StoreData()
        internalStore!!.generateRandomContents(
            game, Random(storeSeed), StoreProfile.of(sector.info), StoreProfile.progress(sector.info),
            sector.info.type
        )
        return internalStore
    }

    fun bindSector(sector: Sector, neighbours: List<Beacon>) {
        synchronized(this) {
            check(!this::sector.isInitialized) { "Sector already set!" }
            this.sector = sector
            this.neighbours = neighbours
        }
    }

    /** This beacon's Anti-Ship Battery, or null if it hasn't got one (see [antiShipBatteryTarget]). */
    fun getAntiShipBattery(game: InGameState): AntiShipBattery? {
        val target = antiShipBatteryTarget ?: return null
        actualBattery?.takeIf { it.target == target }?.let { return it }
        return AntiShipBattery(game, target).also { actualBattery = it }
    }

    fun getEnvironment(game: InGameState): AbstractEnvironment {
        actualEnvironment?.let { return it }

        val env = createEnvironment(game, environmentTypes)
        actualEnvironment = env
        return env
    }

    private fun createEnvironment(game: InGameState, types: List<EnvironmentType>): AbstractEnvironment {
        val parts = types.map { it.create(game, this) }
        return parts.singleOrNull() ?: MixedEnvironment(game, this, parts)
    }

    /**
     * Remove the current environment associated with the beacon, so it'll be
     * re-created next time it's used.
     *
     * This should be called after changing the beacon's event, so it reflects
     * the new event's environment.
     */
    fun clearEnvironment() {
        actualEnvironment = null
        actualBattery = null
    }

    /**
     * FOR USE WITH THE DEBUG CONSOLE ONLY!
     */
    fun debugSetEnvironment(environment: AbstractEnvironment) {
        actualEnvironment = environment
    }

    fun saveToXML(elem: Element, refs: ObjectRefs) {
        SaveUtil.addObjectId(elem, refs, this)

        SaveUtil.addAttrInt(elem, "x", pos.x)
        SaveUtil.addAttrInt(elem, "y", pos.y)

        SaveUtil.addAttrBool(elem, "visited", visited)
        SaveUtil.addTagBoolIfTrue(elem, "isExit", isExit)
        SaveUtil.addTagBoolIfTrue(elem, "isBase", isBase)
        SaveUtil.addTagBoolIfTrue(elem, "hasStore", hasStore)
        SaveUtil.addTagBoolIfTrue(elem, "hasQuest", hasQuest)
        SaveUtil.addTagBoolIfTrue(elem, "overtaken", isOvertaken)
        fleetBatteryTarget?.let { SaveUtil.addAttr(elem, "fleetBattery", it.name) }

        // Save the event by name - events are immutable and are
        // loaded from the game's XML, so we only need to uniquely
        // identify them, not store all their data.
        SaveUtil.addAttr(elem, "eventId", event.deserialisationId)

        // Save the master seed, so we can regenerate all the other seeds
        // without storing them separately.
        SaveUtil.addAttrInt(elem, "seed", masterSeed)
        nebulaOverride?.let { SaveUtil.addAttr(elem, "nebulaOverride", it.name) }
        if (sectorHazards.isNotEmpty())
            SaveUtil.addAttr(elem, "sectorHazards", sectorHazards.joinToString(",") { it.name })

        // This both marks the set environment, and whether the environment
        // object needs to be deserialised. A mixed environment's other ones
        // are in env2 and env3.
        actualEnvironment?.let { env ->
            SaveUtil.addAttr(elem, "env", env.type.name)
            for ((index, part) in env.parts.withIndex().drop(1)) {
                SaveUtil.addAttr(elem, ENV_ATTRS[index], part.type.name)
            }
            env.saveToXML(elem)
        }
        actualBattery?.saveToXML(elem)

        if (internalStore != null) {
            val storeElem = Element("storeData")
            internalStore!!.saveToXML(storeElem)
            elem.addContent(storeElem)
        }

        // Save the power limits
        for ((system, limit) in powerLimitEffects) {
            val powerLimit = Element("powerLimit")
            powerLimit.setAttribute("system", system)
            powerLimit.setAttribute("limit", limit.toString())
            elem.addContent(powerLimit)
        }

        // If there's a ship at this beacon, save it.
        // This is how the ship the player is fighting is loaded.
        if (ship != null) {
            val shipElem = Element("enemyShip")
            ship!!.saveToXML(shipElem, refs)
            elem.addContent(shipElem)
        }

        // Note we don't save the neighbours - the sector does that using a more
        // compact encoding than what we can easily use.
    }

    companion object Deserialiser {
        /**
         * The most environments a beacon can have at once.
         */
        const val MAX_HAZARDS = 4

        // The attributes each of a beacon's environments is saved in
        private val ENV_ATTRS = listOf("env", "env2", "env3", "env4")

        fun loadFromXML(elem: Element, refs: RefLoader, game: InGameState): Beacon {
            // To create our beacon, we need to load a few things first.
            // Everything else goes in mutable variables, so we can set them later.
            val eventId = SaveUtil.getAttr(elem, "eventId")
            val seed = SaveUtil.getAttrInt(elem, "seed")
            val event = game.eventManager.getByDeserialisationId(eventId)
            val isExit = SaveUtil.getOptionalTagBool(elem, "isExit") ?: false
            val isBase = SaveUtil.getOptionalTagBool(elem, "isBase") ?: false

            val pos = ConstPoint(
                SaveUtil.getAttrInt(elem, "x"),
                SaveUtil.getAttrInt(elem, "y")
            )

            // This gets us all the information we need to create and start
            // populating our beacon.
            val beacon = Beacon(pos, event, isExit, isBase, seed)
            SaveUtil.registerObjectId(elem, refs, beacon)

            beacon.visited = SaveUtil.getAttrBool(elem, "visited")
            beacon.nebulaOverride = elem.getAttributeValue("nebulaOverride")?.let { EnvironmentType.valueOf(it) }
            beacon.sectorHazards = elem.getAttributeValue("sectorHazards")
                ?.split(",")?.map { EnvironmentType.valueOf(it) } ?: emptyList()
            beacon.hasStore = SaveUtil.getOptionalTagBool(elem, "hasStore") ?: false
            beacon.hasQuest = SaveUtil.getOptionalTagBool(elem, "hasQuest") ?: false
            // (an older save's overtaken beacons are taken again as the fleet
            // advances, besides the last stand's)
            beacon.isOvertaken = SaveUtil.getOptionalTagBool(elem, "overtaken") ?: false
            beacon.fleetBatteryTarget = elem.getAttributeValue("fleetBattery")?.let { AntiShipBattery.Target.valueOf(it) }

            // Load environment-specific data, if we've visited this beacon.
            val envTypes = ENV_ATTRS.mapNotNull { name ->
                elem.getAttributeValue(name)?.let { EnvironmentType.valueOf(it) }
            }
            if (envTypes.isNotEmpty()) {
                beacon.actualEnvironment = beacon.createEnvironment(game, envTypes)
                beacon.actualEnvironment!!.loadFromXML(elem)
            }
            beacon.getAntiShipBattery(game)?.loadFromXML(elem)

            val storeElem = elem.getChild("storeData")
            if (storeElem != null) {
                beacon.internalStore = StoreData()
                beacon.internalStore!!.loadFromXML(game, storeElem)
            }

            // Deserialise the enemy ship, if present
            val shipElem = elem.getChild("enemyShip")
            beacon.ship = shipElem?.let { game.deserialiseSingleShip(it, refs, null) }

            for (powerLimit in elem.getChildren("powerLimit")) {
                val system = powerLimit.getAttributeValue("system")
                val limit = powerLimit.getAttributeValue("limit").toInt()
                beacon.powerLimitEffects[system] = limit
            }

            return beacon
        }
    }

    enum class State {
        UNVISITED,
        VISITED_CLEAR,
        VISITED_DANGER,

        /**
         * Overtaken by the rebel fleet.
         */
        OVERTAKEN,
    }

    enum class EnvironmentType(
        val isDangerous: Boolean,
        val create: (InGameState, Beacon) -> AbstractEnvironment
    ) {
        NORMAL(false, ::NormalEnvironment),
        ASTEROID(true, ::AsteroidEnvironment),
        SUN(true, ::SunEnvironment),
        PULSAR(true, ::PulsarEnvironment),
        NEBULA(false, { game, beacon -> NebulaEnvironment(game, beacon, NEBULA) }),
        ION_STORM(false, { game, beacon -> NebulaEnvironment(game, beacon, ION_STORM) }),

        // XFTL's own nebulas, see NebulaEnvironment and doc/game/nebulas.md
        RADIATION(true, { game, beacon -> NebulaEnvironment(game, beacon, RADIATION) }),
        MIRAGE(false, { game, beacon -> NebulaEnvironment(game, beacon, MIRAGE) }),
        DARK(false, { game, beacon -> NebulaEnvironment(game, beacon, DARK) });

        // TODO how should we represent PDS/ABSes, given they can be targed at the player or enemy (or both?)

        /**
         * True for all the kinds of nebula, which all block sensors and slow
         * the rebel fleet down.
         */
        val isNebula: Boolean get() = this == NEBULA || this == ION_STORM || this == RADIATION || this == MIRAGE || this == DARK

        /**
         * The kinds of nebula are all one family, and the other hazards are
         * each their own: a beacon can't have two of the same family.
         */
        val family: EnvironmentType get() = if (isNebula) NEBULA else this
    }
}
