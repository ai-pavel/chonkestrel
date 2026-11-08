package xyz.znix.xftl.sector

import org.jdom2.Element
import xyz.znix.xftl.game.BossManager
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Represents an in-game sector. Handles the placement of the beacons within it.
 */
class Sector {
    val info: GameMap.SectorInfo

    /**
     * The zero-indexed position of this sector in the sector map. Zero is the starting
     * sector, and the last one (7 in vanilla) is the last stand.
     */
    val sectorNumber: Int get() = info.columnNumber

    /**
     * The vanilla sector number (0-7) this sector is equivalent to. Use this
     * instead of [sectorNumber] for anything that scales with the sector, like
     * the difficulty or rewards, so it's spread out across longer games.
     */
    val difficultySectorNumber: Int get() = info.difficultySectorNumber

    /**
     * The vanilla sector number for the rewards, not rounded down (see
     * [GameMap.SectorInfo.rewardSectorNumber]).
     */
    val rewardSectorNumber: Float get() = info.rewardSectorNumber

    val type: SectorType get() = info.type

    val isLastStand: Boolean get() = info.type.name == "FINAL"

    val beacons = ArrayList<Beacon>()

    // The start and finish beacon. In the final sector, the finish
    // beacon represents the location of the rebel base.
    val startBeacon: Beacon
    val finishBeacon: Beacon

    // The position of the centre of the danger zone circle.
    // See the original XFTL's doc/sector-map (heading 'Rebel fleet advance') for more
    // information about this.
    val dangerZoneCentre: Point

    /**
     * This is added to by the <modifyPursuit> event tag.
     *
     * A positive number means the pursuit is doubled for that many
     * jumps, while a negative number means there's no pursuit for
     * that many jumps.
     */
    var fleetAdvanceModifier: Int = 0

    /**
     * If true, all beacons are displayed with their full information
     * visible (environmental hazards, ship presence, store/distress
     * labels, etc).
     *
     * This is set by events with the <reveal_map/> tag.
     */
    var mapRevealed: Boolean = false

    /**
     * If true, a <reveal_map/> event's scouting got through the mirages:
     * standing in a mirage nebula doesn't make the other beacons lie any
     * more, though those in a mirage still do (see [Mirage.isDeceptive]).
     * Each reveal has an even chance of this.
     */
    var mirageScouted: Boolean = false

    /**
     * In the Silent Capital, which of the Mainframe's phases is next to
     * fight, from 1 (see doc/game/mainframe.md and InGameState.colossalBeaten).
     */
    var mainframePhase: Int = 1

    /**
     * In the Silent Capital, the seconds left of the break after one of the
     * Mainframe's phases is beaten, before the next starts, or 0.
     */
    var mainframeBreak: Float = 0f

    /**
     * In the Silent Capital (the last stand), the beacons within
     * [CHORUS_HELD_JUMPS] jumps of the exit, where CONCORD's core is: the
     * map lies about them, as about a mirage nebula's beacons (see
     * [Mirage.isDeceptive]). Empty in every other sector. See
     * doc/game/mainframe.md.
     */
    val chorusHeld: Set<Beacon>
        get() = chorusHeldCache ?: findChorusHeld().also { chorusHeldCache = it }

    // (worked out the first time it's needed, once the beacons are linked)
    private var chorusHeldCache: Set<Beacon>? = null

    private fun findChorusHeld(): Set<Beacon> {
        if (!isLastStand)
            return emptySet()

        // The beacons the exit's neighbours lead to, and so on
        val held = HashSet<Beacon>()
        var edge = listOf(finishBeacon)
        repeat(CHORUS_HELD_JUMPS) {
            edge = edge.flatMap { it.neighbours }.filter { it != finishBeacon && held.add(it) }
        }
        return held - startBeacon
    }

    /**
     * The boss ships present in this sector.
     *
     * Without mods this is either empty or contains the flagship, but mods can
     * theoretically add multiple bosses.
     */
    val bosses = ArrayList<BossManager>()

    // Note there's another constructor for deserialising a sector
    // from XML down at the bottom of the class.

    /**
     * Randomly generate a new sector.
     */
    constructor(
        info: GameMap.SectorInfo,

        /**
         * The list of events that should be used in this sector. Each beacon will be assigned an event from
         * this list, but no two beacons will share an event (unless that event appears twice in the list).
         *
         * This assignment is random, and the order of the items in the list does not matter. If there are
         * more events in the list than beacons in the sector, a random set of the events will go unused.
         *
         * If the list of events is shorter than the number of beacons we generate, those beacons will all
         * have the filler event.
         */
        events: List<Event>,

        specialEvents: GameMap.SpecialEvents,
        difficulty: Difficulty
    ) {
        this.info = info

        val eventPool = ArrayDeque(events.shuffled())

        val rand = Random.Default

        // Generate a random grid (see GRID_SIZE). Each beacon to be placed will be offset from one position on the grid.
        val grid = ArrayList<IPoint>()
        for (x in 0 until GRID_SIZE.x) {
            for (y in 0 until GRID_SIZE.y) {
                grid += Point(x, y)
            }
        }

        // See https://twitter.com/subsetgames/status/1234309658854084608
        // In vanilla FTL, the map is split into a 6x4 grid, each cell may or
        // may not contain a beacon. XFTL's maps are bigger, see GRID_SIZE.

        val cells = HashMap<Beacon, IPoint>()

        var tmpStartBeacon: Beacon? = null
        var tmpFinishBeacon: Beacon? = null

        // If we're at the right-most side of the map, add an exit beacon.
        // (Note this applies to the last two columns, which is how you can
        //  get obnoxiously early exit beacons)
        // (Its event's swapped for the nebula one later if it ends up in a
        // nebula cloud, see nebulaExit.)
        // In the last stand, these are the vanilla columns scaled to the
        // size of the map.
        val exitRange = when {
            !isLastStand -> LAST_TWO_COLUMNS
            difficulty == Difficulty.HARD -> scaleVanillaColumns(3..4)
            else -> scaleVanillaColumns(2..3)
        }

        // The cells the start and exit beacons go in, placed first and never
        // left out, so every sector has both (leaving every cell in a
        // column out could leave a sector without one)
        val startCell = grid.filter { it.x == 0 }.random(rand)
        val finishCell = grid.filter { it.x in exitRange }.random(rand)

        // A sector with several exits (see SectorType.exitCount) has the
        // others at the end of the sector too: in the last column, if
        // there's room, or else the last two
        val extraExitCells = if (isLastStand) emptyList() else {
            val others = grid.filter { it.x in exitRange && it != finishCell }.shuffled(rand)
            others.sortedByDescending { it.x }.take(type.exitCount - 1)
        }
        val exitCells = listOf(finishCell) + extraExitCells
        val fixedCells = listOf(startCell) + exitCells

        var skipped = 0
        val beaconCount = GRID_SIZE.x * GRID_SIZE.y - rand.nextInt(3)
        for (i in 1..beaconCount) {
            val gridPos = fixedCells.getOrNull(i - 1) ?: grid.random()
            grid.remove(gridPos)

            // There's a 20% chance to omit a given beacon. See the original XFTL's doc/sector-map.
            if (gridPos !in fixedCells && rand.nextInt(5) == 0) {
                // Find the number of beacons that haven't been skipped.
                // -1 here so we don't count the one we're currently generating.
                val alreadyGenerated = i - 1 - skipped

                // Use this to avoid skipping >= 20% of the beacons.
                // (Note 20% is n/4 since n doesn't include skipped)
                if (skipped == 0 || skipped < alreadyGenerated / 4) {
                    skipped++
                    continue
                }
            }

            val pos = Point(gridPos.x * CELL_SIZE.x, gridPos.y * CELL_SIZE.y)
            pos.x += rand.nextInt(PADDING, CELL_SIZE.x - PADDING)
            pos.y += rand.nextInt(PADDING, CELL_SIZE.y - PADDING)

            // Limit y to at most 415 (on a vanilla-sized map) so it cleanly fits on the map
            pos.y = min(pos.y, MAP_SIZE.y - 25)

            // In the two top-right-most cells, limit the Y to at least 30
            // to keep space clear for the next sector button.
            if (gridPos.y == 0 && gridPos.x >= GRID_SIZE.x - 2) {
                pos.y = max(pos.y, 30)
            }

            var event: Event? = null
            var isFinish = false
            var isStart = false

            // The exit beacon (see finishCell), or one of several. We also
            // re-use this to spawn the rebel base in the last stand.
            if (gridPos in exitCells) {
                // Some sectors have their own exit event (an XFTL extension),
                // for the boss that holds it; a hunter's (see HunterBoss)
                // comes to the player instead, and its sector's exit is an
                // ordinary one
                val bossEvent = if (type.bossHoldsExit) type.finishEvent else null
                event = (bossEvent ?: specialEvents.exit).resolve()
                isFinish = true

                // (or, in the Silent Capital, the Mainframe, which its
                // finish event brings, see doc/game/mainframe.md)
                if (isLastStand) {
                    event = (type.finishEvent ?: specialEvents.fedBase).resolve()
                }
            }

            // Same goes for the start beacon (see startCell), with the
            // sector's start event, without a hazard (see calmStartEvent)
            if (gridPos == startCell) {
                event = calmStartEvent()
                isStart = true

                // If this is the very first beacon in the game, it's replaced
                // with the START_GAME event.
                if (sectorNumber == 0) {
                    event = specialEvents.startGame.resolve()
                }
            }

            // Otherwise, just use a standard beacon from our event pool - or
            // if we ran out of those, use a filler event.
            if (event == null) {
                event = eventPool.pollFirst() ?: specialEvents.filler.resolve()
            }

            val beacon = Beacon(
                pos.const, event,
                isFinish && !isLastStand,
                isFinish && isLastStand,
                Random.nextInt()
            )
            require(beacon.gridPos posEq gridPos)
            beacons += beacon
            cells[beacon] = gridPos

            if (isFinish && gridPos == finishCell)
                tmpFinishBeacon = beacon
            if (isStart)
                tmpStartBeacon = beacon
        }

        // We'll check in GameMap whether this sector has a path from start
        // to end, so we don't need to do anything like that here.

        val links = beacons.associateWith { b ->
            // Find the position of our beacon on the grid
            val bPos = cells[b]!!

            beacons.filter {
                // Beacons must be adjacent or on a diagonal on the grid.
                if (cells[it]!!.distToSq(bPos) > 2)
                    return@filter false

                // Beacons must be within 165px of each other (see the original XFTL's doc/sector-map)
                if (it.pos.distToSq(b.pos) > CONNECTION_DISTANCE * CONNECTION_DISTANCE)
                    return@filter false

                // Don't link beacons to themselves
                return@filter it != b
            }.toMutableList()
        }
        tmpStartBeacon?.let { linkStrandedBeacons(it, links, cells) }
        clusterNebulas(links, setOfNotNull(tmpStartBeacon) + beacons.filter { it.isExit }, rand)
        if (!isLastStand && !type.bossHoldsExit)
            tmpFinishBeacon?.let { nebulaExit(it, links.getValue(it), specialEvents) }
        for (b in beacons) {
            b.bindSector(this, links.getValue(b))
        }

        startBeacon = tmpStartBeacon ?: error("Failed to place a starting beacon!")
        finishBeacon = tmpFinishBeacon ?: error("Failed to place a finish beacon!")

        dangerZoneCentre = Point(-959, rand.nextInt(DANGER_ZONE_MIN_Y, DANGER_ZONE_MAX_Y))

        // The flagship is set up by GameMap.generateSector
    }

    /**
     * The sector's start event, re-picked (if it's an event list, as a
     * mod's might be) until it has no hazard, or at most a plain nebula, if
     * it can: the start beacon is somewhere calm to arrive in. With the
     * Chonkestrel mod, every sector has its own, which only describes it.
     * GameMap.calmStart takes any hazard that's left away.
     */
    private fun calmStartEvent(): Event {
        var event = type.startEvent.resolve()
        repeat(CALM_START_TRIES) {
            val environment = event.environment
            if (environment == null || environment == Beacon.EnvironmentType.NORMAL ||
                environment == Beacon.EnvironmentType.NEBULA
            ) return event
            event = type.startEvent.resolve()
        }
        return event
    }

    /**
     * How far the rebel fleet advances when the player jumps from a beacon,
     * with a ship of the given size.
     */
    fun getFleetAdvanceFor(beacon: Beacon, playerShipSize: Int): Int {
        val base = baseFleetAdvance(
            beacon.isNebula,
            info.sectorClass == GameMap.SectorClass.NEBULA,
            playerShipSize
        )

        // (faster in a boss's sector, see SectorType.fleetSpeed)
        val advance = (base * type.fleetSpeed).toInt()
        return when {
            fleetAdvanceModifier < 0 -> 0
            fleetAdvanceModifier > 0 -> advance * 2
            else -> advance
        }
    }

    /**
     * Gather the sector's nebula beacons into clouds of neighbouring beacons,
     * as in vanilla, rather than leaving them scattered across the map: the
     * clouds (one for every [BEACONS_PER_NEBULA_CLOUD] nebula beacons) grow
     * from random beacons, a neighbour at a time, until they're as big as
     * there are nebula beacons, and the nebulas' events swap places with the
     * others' to fill them. The start and exit ([fixed]) stay where they are.
     */
    private fun clusterNebulas(links: Map<Beacon, List<Beacon>>, fixed: Set<Beacon>, rand: Random) {
        val movable = links.keys.filter { it !in fixed }
        val nebulas = movable.filter { it.event.environment?.isNebula == true }
        if (nebulas.size < 2 || nebulas.size >= movable.size)
            return

        val clouds = (1 + nebulas.size / BEACONS_PER_NEBULA_CLOUD).coerceAtMost(nebulas.size)
        val inCloud = LinkedHashSet<Beacon>()
        val frontiers = movable.shuffled(rand).take(clouds).map { seed ->
            inCloud += seed
            arrayListOf(seed)
        }.toMutableList()
        while (inCloud.size < nebulas.size) {
            val growing = frontiers.filter { it.isNotEmpty() }
            if (growing.isEmpty()) {
                // (a cloud boxed in by the start and exit starts again elsewhere)
                val seed = movable.filter { it !in inCloud }.random(rand)
                inCloud += seed
                frontiers += arrayListOf(seed)
                continue
            }
            val frontier = growing.random(rand)
            val from = frontier.random(rand)
            val next = links.getValue(from).filter { it in movable && it !in inCloud }
            if (next.isEmpty()) {
                frontier.remove(from)
                continue
            }
            val beacon = next.random(rand)
            inCloud += beacon
            frontier += beacon
        }

        // Swap the events: each nebula outside the clouds with a beacon in
        // them that isn't one
        val outside = nebulas.filter { it !in inCloud }.toMutableList()
        for (beacon in inCloud) {
            if (beacon.event.environment?.isNebula == true)
                continue
            val nebula = outside.removeAt(outside.lastIndex)
            val event = beacon.event
            beacon.event = nebula.event
            nebula.event = event
            // (a store goes with its event)
            beacon.hasStore = beacon.event.isStore
            nebula.hasStore = nebula.event.isStore
        }
    }

    /**
     * As in vanilla, an exit beacon in the middle of a nebula cloud (most of
     * its neighbours being nebulas) is a nebula itself, with vanilla's
     * FINISH_BEACON_NEBULA event. (That event doesn't say it's a nebula, as
     * vanilla's nebulas are drawn on the map, so this does.)
     */
    private fun nebulaExit(exit: Beacon, neighbours: List<Beacon>, specialEvents: GameMap.SpecialEvents) {
        val nebulas = neighbours.count { it.event.environment?.isNebula == true }
        if (neighbours.isEmpty() || nebulas * 2 <= neighbours.size)
            return
        exit.event = specialEvents.exitNebula.resolve()
        exit.nebulaOverride = Beacon.EnvironmentType.NEBULA
    }

    /**
     * Link up any beacons the player couldn't reach from the [start] (such
     * as one whose neighbours are all just too far away): each group of
     * them gets a link from its beacon nearest to one the player can reach,
     * in a neighbouring cell of the grid if there is one, however far away
     * it is.
     */
    private fun linkStrandedBeacons(
        start: Beacon, links: Map<Beacon, MutableList<Beacon>>, cells: Map<Beacon, IPoint>
    ) {
        while (true) {
            val reached = HashSet<Beacon>()
            val queue = ArrayDeque(listOf(start))
            while (queue.isNotEmpty()) {
                val beacon = queue.removeFirst()
                if (reached.add(beacon))
                    queue += links.getValue(beacon)
            }
            val stranded = links.keys.filter { it !in reached }
            if (stranded.isEmpty())
                return

            val pairs = stranded.flatMap { from -> reached.map { to -> from to to } }
            val (from, to) = pairs.filter { (from, to) -> cells.getValue(from).distToSq(cells.getValue(to)) <= 2 }
                .ifEmpty { pairs }
                .minBy { (from, to) -> from.pos.distToSq(to.pos) }
            links.getValue(from) += to
            links.getValue(to) += from
        }
    }

    /**
     * Attempt to add a quest to this sector.
     *
     * This may fail if it can't find space for the event, there isn't
     * anywhere to put it where the player can reach it before the fleet
     * overruns it, etc.
     *
     * If [ignoreDistance] is not set, then beacons that the player can't
     * reach before they're overtaken will not be used.
     *
     * If [ahead] is set (see Event.questAhead), the beacon has to be on the
     * way to the exit, nearer it than the [currentBeacon] is, and at least
     * [AHEAD_QUEST_MIN_JUMPS] jumps away; of those, one nearest the exit.
     */
    @JvmOverloads
    fun addQuest(
        currentBeacon: Beacon, questEvent: Event, ignoreDistance: Boolean = false, ahead: Boolean = false
    ): Boolean {
        // See the original XFTL's doc/sector-map for how this works.

        // How many jumps each beacon is from the nearest exit
        val exits = beacons.filter { it.isExit }
        fun jumpsToExit(beacon: Beacon): Int =
            exits.mapNotNull { findShortestPath(beacon, it)?.size }.minOrNull() ?: Int.MAX_VALUE
        val currentToExit = if (ahead) jumpsToExit(currentBeacon) else 0

        var suitable = beacons.filter {
            // Quests never go in a nebula, as in vanilla, but other hazards
            // a beacon got from the sector don't count, as a quest beacon
            // doesn't have them (see Beacon.environmentTypes).
            if (it.visited || it.isOwnNebula || it == currentBeacon)
                return@filter false
            if (it.isExit || it.hasQuest || it.hasStore || it.event.isDistressBeacon)
                return@filter false
            // Nor over a beacon whose own event gives a quest
            if (it.event.givesQuest)
                return@filter false
            if (it.isOvertaken)
                return@filter false

            // Check the path length, to check that we can reach the beacon before
            // the rebel fleet does, and to check there is a valid path.
            val path = findShortestPath(currentBeacon, it) ?: return@filter false

            if (ahead && (path.size < AHEAD_QUEST_MIN_JUMPS || jumpsToExit(it) >= currentToExit))
                return@filter false

            // (with no fleet, where a hunter is, any beacon's in reach)
            if (!ignoreDistance && type.hasFleet) {
                val distToFleet = it.pos.distTo(dangerZoneCentre) - DANGER_ZONE_RADIUS
                val timeUntilFleet = distToFleet / (DANGER_ZONE_ADVANCE * type.fleetSpeed)

                // We must be able to reach the beacon *before* it's overrun
                if (timeUntilFleet <= path.size)
                    return@filter false
            }

            return@filter true
        }

        if (suitable.isEmpty())
            return false

        // As far along as it can be: one of those nearest the exit
        if (ahead) {
            val nearest = suitable.minOf { jumpsToExit(it) }
            suitable = suitable.filter { jumpsToExit(it) == nearest }
        }

        val beacon = suitable.random()
        beacon.hasQuest = true
        beacon.event = questEvent
        beacon.clearEnvironment()

        return true
    }

    /**
     * Find the shortest path between the two beacons. This contains
     * each beacon the ship must jump to on the way - that is, when
     * there's a path between two different beacons [start] and [end],
     * the path won't contain [start] but will contain [end].
     *
     * If there is no suitable path, null is returned.
     */
    fun findShortestPath(start: Beacon, end: Beacon): List<Beacon>? {
        // Find the path using a simple wavefront system

        val distances = HashMap<Beacon, Int>()
        val wavefront = HashSet<Beacon>()
        val nextWavefront = HashSet<Beacon>()
        wavefront += end

        // Build a map of distances from any given beacon to the end
        while (wavefront.isNotEmpty()) {
            for (beacon in wavefront) {
                // Update this beacon's weight
                val distance: Int = if (beacon == end) {
                    0
                } else {
                    val lowestNeighbour = beacon.neighbours.mapNotNull { distances[it] }.minOrNull()
                    requireNotNull(lowestNeighbour) { "Beacon in path search was in wavefront, but had no set neighbours!" }
                    lowestNeighbour + 1
                }

                if (distances[beacon] == distance)
                    continue

                distances[beacon] = distance
                nextWavefront += beacon.neighbours
            }

            wavefront.clear()
            wavefront += nextWavefront
            nextWavefront.clear()
        }

        // If the start beacon doesn't have a distance, there's no valid path.
        if (!distances.containsKey(start))
            return null

        // Follow the lowest weight to find the path.
        val path = ArrayList<Beacon>()
        var current = start
        while (current != end) {
            current = current.neighbours.sortedBy { distances[it] }.first()
            path += current
        }

        return path
    }

    fun saveToXML(elem: Element, globalRefs: ObjectRefs): ObjectRefs {
        // External stuff shouldn't be able to reference beacons
        val refs = ObjectRefs(globalRefs)

        // Register all the beacons, as they reference each other
        for (beacon in beacons) {
            refs.register(beacon, "beacon")
        }

        SaveUtil.addAttrRef(elem, "sectorInfo", refs, info)

        SaveUtil.addAttrInt(elem, "fleetAdvanceModifier", fleetAdvanceModifier)
        SaveUtil.addAttrBool(elem, "mapRevealed", mapRevealed)
        SaveUtil.addAttrBool(elem, "mirageScouted", mirageScouted)
        if (mainframePhase != 1)
            SaveUtil.addAttrInt(elem, "mainframePhase", mainframePhase)
        if (mainframeBreak > 0f)
            SaveUtil.addAttrFloat(elem, "mainframeBreak", mainframeBreak)
        SaveUtil.addPoint(elem, "dangerZoneCentre", dangerZoneCentre)

        SaveUtil.addAttrRef(elem, "startBeacon", refs, startBeacon)
        SaveUtil.addAttrRef(elem, "finishBeacon", refs, finishBeacon)

        for (boss in bosses) {
            val bossElem = Element("boss")
            SaveUtil.addAttr(bossElem, "type", boss.serialisationType)
            // Note we don't register the boss with ObjectRefs - that's done by InGameState,
            // since it needs the object ID if it's saving the boss ship layout before
            // this sector is serialised.
            SaveUtil.addObjectId(bossElem, globalRefs, boss)
            boss.saveToXML(bossElem, refs)
            elem.addContent(bossElem)
        }

        // Serialise all the beacons
        for (beacon in beacons) {
            val beaconElem = Element("beacon")
            beacon.saveToXML(beaconElem, refs)
            elem.addContent(beaconElem)
        }

        // Serialise all the neighbour connections between beacons. Since there's
        // a huge number of these, we process them here rather than inside the
        // beacons themselves. This lets us use a much more compact (particularly
        // in terms of number of lines) format, which makes the pretty-printed
        // save state easier to read.
        val indexes = HashMap<Beacon, Int>()
        for ((i, beacon) in beacons.withIndex()) {
            indexes[beacon] = i
        }

        val serialisedNeighbours = StringBuilder()
        for ((i, beacon) in beacons.withIndex()) {
            // Only include neighbours that come later in the beacons
            // list - if they come earlier, we'll already have specified
            // this pair of neighbours when serialising the other one.
            val neighbours = beacon.neighbours.filter { i < indexes.getValue(it) }

            if (neighbours.isEmpty())
                continue

            serialisedNeighbours.append(i)
            for (neighbour in neighbours) {
                serialisedNeighbours.append(',')
                serialisedNeighbours.append(indexes.getValue(neighbour))
            }

            serialisedNeighbours.append(' ')
        }

        val neighboursElem = Element("neighbours")
        serialisedNeighbours.trim()
        neighboursElem.addContent(serialisedNeighbours.toString())
        elem.addContent(neighboursElem)

        // The game needs to make a reference to the player's current beacon.
        return refs
    }

    /**
     * Deserialise this sector from XML.
     */
    constructor(elem: Element, refs: RefLoader, game: InGameState, mapRefs: RefLoader) {
        info = SaveUtil.getAttrRefImmediate(elem, "sectorInfo", mapRefs, GameMap.SectorInfo::class.java)!!

        fleetAdvanceModifier = SaveUtil.getAttrInt(elem, "fleetAdvanceModifier")
        mapRevealed = SaveUtil.getAttrBool(elem, "mapRevealed")
        // Older saves don't have it
        mirageScouted = elem.getAttributeValue("mirageScouted")?.toBoolean() ?: false
        mainframePhase = elem.getAttributeValue("mainframePhase")?.toInt() ?: 1
        mainframeBreak = elem.getAttributeValue("mainframeBreak")?.toFloat() ?: 0f
        dangerZoneCentre = Point(SaveUtil.getPoint(elem, "dangerZoneCentre"))

        // Use a separate RefLoader to find the start and finish beacons
        val beaconRefs = RefLoader()

        // Load all the beacons
        val neighboursFor = HashMap<Beacon, ArrayList<Beacon>>()
        for (beaconElem in elem.getChildren("beacon")) {
            val beacon = Beacon.loadFromXML(beaconElem, refs, game)
            SaveUtil.registerObjectId(beaconElem, beaconRefs, beacon)
            beacons += beacon
            neighboursFor[beacon] = ArrayList()
        }

        // Calculate all the neighbours for each beacon
        val neighbourTuples = elem.getChildTextTrim("neighbours").split(' ', '\t')
        for (part in neighbourTuples) {
            // Each entry in the neighbours list of a comma-separated list of beacon indexes.
            // The first beacon in each entry is then connected to all the subsequent beacons.
            val partBeacons = part.split(',').map { beacons[it.toInt()] }
            val mainBeacon = partBeacons[0]
            val neighbours = partBeacons.subList(1, partBeacons.size)

            val mainNeighbours = neighboursFor.getValue(mainBeacon)

            for (neighbour in neighbours) {
                val neighbourNeighbours = neighboursFor.getValue(neighbour)

                // Make sure this connection is unique
                require(!mainNeighbours.contains(neighbour))
                require(!neighbourNeighbours.contains(mainBeacon))

                mainNeighbours.add(neighbour)
                neighbourNeighbours.add(mainBeacon)
            }
        }

        for (beacon in beacons) {
            val neighbours = neighboursFor.getValue(beacon)
            beacon.bindSector(this, neighbours)
        }

        beaconRefs.switchToResolveMode()
        startBeacon = SaveUtil.getAttrRefImmediate(elem, "startBeacon", beaconRefs, Beacon::class.java)!!
        finishBeacon = SaveUtil.getAttrRefImmediate(elem, "finishBeacon", beaconRefs, Beacon::class.java)!!

        for (bossElem in elem.getChildren("boss")) {
            val type = SaveUtil.getAttr(bossElem, "type")
            val boss = BossManager.createBySerialisationType(type, this, game)
            SaveUtil.registerObjectId(bossElem, refs, boss)
            boss.loadFromXML(bossElem, refs)
            bosses.add(boss)
        }
    }

    companion object {
        /** The fewest jumps away an ahead quest's beacon can be (see addQuest). */
        const val AHEAD_QUEST_MIN_JUMPS = 3

        val OFFSET = ConstPoint(45, 40)

        /**
         * The size of the grid the beacons are placed on. This is 9x6, with
         * two and a quarter times as many cells as vanilla's 6x4, so sectors
         * have about that many times the beacons.
         */
        val GRID_SIZE = ConstPoint(9, 6)
        val VANILLA_GRID_SIZE = ConstPoint(6, 4)

        val CELL_SIZE = ConstPoint(110, 110)
        const val PADDING = 10
        const val CONNECTION_DISTANCE = 165

        /**
         * How many nebula beacons a sector has for each cloud of them (see
         * clusterNebulas): a sector with a few has one cloud, and one with
         * more, two or three.
         */
        const val BEACONS_PER_NEBULA_CLOUD = 6

        /**
         * The size of the area the beacons are placed in.
         */
        val MAP_SIZE = ConstPoint(GRID_SIZE.x * CELL_SIZE.x, GRID_SIZE.y * CELL_SIZE.y)

        /**
         * How much bigger the map is than in vanilla, in each direction.
         */
        val EXTRA_MAP_SIZE = ConstPoint(
            (GRID_SIZE.x - VANILLA_GRID_SIZE.x) * CELL_SIZE.x,
            (GRID_SIZE.y - VANILLA_GRID_SIZE.y) * CELL_SIZE.y
        )

        /**
         * How many times more beacons there are than in vanilla, roughly.
         * The number of events in each sector is scaled by this.
         */
        /** How many jumps from the Silent Capital's exit the beacons are [chorusHeld]. */
        const val CHORUS_HELD_JUMPS = 2

        val BEACON_COUNT_SCALE: Int = (GRID_SIZE.x * GRID_SIZE.y) / (VANILLA_GRID_SIZE.x * VANILLA_GRID_SIZE.y)

        const val DANGER_ZONE_RADIUS = 767
        const val DANGER_ZONE_RADIUS_SQUARED = DANGER_ZONE_RADIUS * DANGER_ZONE_RADIUS

        /**
         * The amount the x position of the danger zone increases by
         * per jump in a normal sector.
         *
         * Vanilla's 64 is scaled down for wider maps, so the fleet takes the
         * same number of jumps to cross each column. That gives the player
         * time to visit more of the extra beacons.
         */
        val DANGER_ZONE_ADVANCE = 64 * VANILLA_GRID_SIZE.x / GRID_SIZE.x

        /**
         * How far the rebel fleet advances in a jump, before any events that
         * change it.
         *
         * Nebulas slow it down: it advances half as far from nebula beacons
         * in normal sectors, and 80% as far in nebula sectors, as in vanilla.
         * Big ships leave a wake through the nebula that the fleet can
         * follow, so they're slowed down by less: a size 2 ship slows it
         * by half as much (75% and 90%).
         */
        fun baseFleetAdvance(fromNebula: Boolean, nebulaSector: Boolean, playerShipSize: Int): Int {
            if (!fromNebula)
                return DANGER_ZONE_ADVANCE

            val nebulaAdvance = if (nebulaSector) 0.8f else 0.5f
            val slowdown = (1f - nebulaAdvance) / playerShipSize.coerceAtLeast(1)
            return (DANGER_ZONE_ADVANCE * (1f - slowdown)).toInt()
        }

        // The fleet's front is drawn as an image 996px tall, centred on the
        // danger zone's centre, so keep that inside a range where it covers
        // the whole map window (which extends 40px above the map, and 54px
        // below it). In vanilla this is 50-300.
        private const val DANGER_ZONE_IMAGE_HALF_HEIGHT = 498
        val DANGER_ZONE_MIN_Y = maxOf(50, MAP_SIZE.y + 54 - DANGER_ZONE_IMAGE_HALF_HEIGHT)
        val DANGER_ZONE_MAX_Y = minOf(300 * MAP_SIZE.y / 440, DANGER_ZONE_IMAGE_HALF_HEIGHT - 40)

        // How many times to re-pick a start event with a hazard
        const val CALM_START_TRIES = 20

        /**
         * The two right-most columns of the map, where the exit and flagship go.
         */
        val LAST_TWO_COLUMNS = (GRID_SIZE.x - 2) until GRID_SIZE.x

        /**
         * Convert a range of columns on vanilla's 6-column map to the
         * equivalent columns on ours.
         */
        fun scaleVanillaColumns(columns: IntRange): IntRange {
            val first = columns.first * GRID_SIZE.x / VANILLA_GRID_SIZE.x
            val last = (columns.last + 1) * GRID_SIZE.x / VANILLA_GRID_SIZE.x - 1
            return first..last
        }
    }
}
