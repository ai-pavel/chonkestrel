package xyz.znix.xftl.sector

import org.jdom2.Element
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.FlagshipBoss
import xyz.znix.xftl.game.HunterBoss
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.InGameState.GameContent
import xyz.znix.xftl.game.StoreProfile
import xyz.znix.xftl.mapChildrenText
import xyz.znix.xftl.requireAttributeValue
import xyz.znix.xftl.savegame.ISerialReferencable
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.random.Random

/**
 * Represents the overall map of the game. This loads and parses the sector data (which defines all the
 * different types of sectors) and generates a random set of them to be used in the sector map.
 */
class GameMap private constructor(df: Datafile, private val eventManager: EventManager, enableAE: Boolean) {
    private val sectorClasses = HashMap<SectorClass, List<SectorType>>()
    private val sectorTypes = HashMap<String, SectorType>()

    private val specialEvents = SpecialEvents(
        eventManager["START_GAME"],
        eventManager["NEUTRAL"],
        eventManager["FINISH_BEACON"],
        eventManager["FINISH_BEACON_NEBULA"],
        eventManager["FEDERATION_BASE"]
    )

    val sectors: List<List<SectorInfo>> = ArrayList()

    init {
        val xml = df.parseXML(df["data/sector_data.xml"])

        val namedSectorTypes = HashMap<String, List<String>>()

        for (elem in xml.rootElement.children) {
            when (elem.name) {
                "sectorType" -> parseSectorType(elem, namedSectorTypes)
                "sectorDescription" -> parseSectorDescription(elem)
                // TODO sectorMap is used in Multiverse (but not vanilla), support it.
                //  (note it might come from Hyperspace)
                else -> println("[warn] Unknown node type '${elem.name}' in sector_data.xml")
            }
        }

        val sectorClassesAE = HashMap<SectorClass, List<SectorType>>()
        outer@ for ((name, sectors) in namedSectorTypes) {
            val sectorClass = when (name.removePrefix(BlueprintManager.AE_PREFIX)) {
                "CIVILIAN" -> SectorClass.CIVILIAN
                "HOSTILE" -> SectorClass.HOSTILE
                "NEBULA" -> SectorClass.NEBULA

                // Skip unknown sector classes
                else -> continue@outer
            }

            // Keep the AE stuff separate, so we can override
            // the non-AE versions later.
            val classes = if (name.startsWith(BlueprintManager.AE_PREFIX)) sectorClassesAE else sectorClasses

            classes[sectorClass] = sectors.map {
                sectorTypes[it] ?: error("Missing sector $it specificed in category $name")
            }
        }

        // Apply the AE overrides
        if (enableAE) {
            sectorClasses.putAll(sectorClassesAE)
        }
    }

    /**
     * Create a new, random game map.
     */
    constructor(df: Datafile, eventManager: EventManager, enableAE: Boolean, random: Random)
            : this(df, eventManager, enableAE) {

        // This assertion lets us modify the sectors array in the constructor
        require(sectors is ArrayList)

        // Add the starting sector
        val startingType = sectorTypes["CIVILIAN_SECTOR"] ?: error("Cannot find CIVILIAN_SECTOR for first sector")
        val startingSector = SectorInfo(0, 0, startingType, SectorClass.CIVILIAN)
        sectors.add(listOf(startingSector))

        // Keep track of what sector types we've used, to avoid duplicating
        // sectors like homewords that are unique.
        val usedSectorTypes = HashSet<SectorType>()

        // Add the intermediate columns: six in vanilla, between the
        // starting sector and the last stand.
        for (columnNum in 1..SECTOR_COUNT - 2) {
            val lastColumn = sectors.last()

            // Longer games have a sector between the two acts, which every
            // sector in the previous column leads to, like the last stand.
            if (columnNum == midpointColumn) {
                val midpoint = SectorInfo(columnNum, 0, midpointType!!, SectorClass.HOSTILE)
                for (sector in lastColumn) {
                    sector.nextSectors += midpoint
                }
                sectors.add(listOf(midpoint))
                continue
            }

            // Choose how many sectors there will be in this column, choosing
            // a number different to the previous one.
            // Only sizes we can link up with each sector having one or two
            // next sectors are allowed: one more or less, or double or half.
            // With up to four sectors per column (as in vanilla), that's
            // any size other than the previous one.
            // The second act is twice as big as the first: its columns have
            // four to eight sectors, rather than two to four, so it opens
            // up after the blockade (going from two to four sectors first).
            val sizeRange = columnSizeRange(columnNum)
            val sizeOptions = (2..MAX_SECTORS_PER_COLUMN).filter {
                val last = lastColumn.size
                abs(it - last) == 1 || it == last * 2 || it * 2 == last
            }
            var numInColumn = sizeOptions.filter { it in sizeRange }.ifEmpty { sizeOptions }.random(random)

            // Except the 2nd column is special, as it joins the starting
            // beacon and thus must only have two beacons to avoid giving
            // the player more than two options.
            if (lastColumn.size == 1) {
                numInColumn = 2
            }

            // Build all the sectors in this column. Their types are picked
            // once they're linked up, so they can avoid the types of the
            // sectors leading to them (see pickSectorType).
            val column = ArrayList<SectorInfo>()
            for (i in 0 until numInColumn) {
                column.add(SectorInfo(columnNum, i, startingType, SectorClass.random(random)))
            }

            // Add the connections between the previous sectors and
            // the new ones. This depends on the number of sectors
            // in this and the previous column, see sector-map for
            // more information.

            if (numInColumn == lastColumn.size * 2) {
                // Each sector leads to two sectors (2 to 4 in vanilla)
                for ((i, sector) in lastColumn.withIndex()) {
                    sector.nextSectors += column[i * 2]
                    sector.nextSectors += column[i * 2 + 1]
                }
            } else if (numInColumn * 2 == lastColumn.size) {
                // Each pair of sectors leads to one sector (4 to 2 in vanilla)
                for ((i, sector) in lastColumn.withIndex()) {
                    sector.nextSectors += column[i / 2]
                }
            } else {
                // This should only happen when we change size by one.
                require(abs(lastColumn.size - numInColumn) == 1)

                // Link each sector (except the edge ones in the column
                // with the most sectors) to two others.

                // Link the nth+a sector in the previous column to the nth+b
                // one in the current column.
                fun linkColumnsOffset(prevOffset: Int, currOffset: Int) {
                    var i = 0
                    while (prevOffset + i < lastColumn.size && currOffset + i < column.size) {
                        lastColumn[prevOffset + i].nextSectors += column[currOffset + i]
                        i += 1
                    }
                }

                if (lastColumn.size > numInColumn) {
                    // Less nodes in this column, we need the first and second
                    // sectors in the first column linking to the first sector
                    // in the second column, and so on.
                    linkColumnsOffset(0, 0)
                    linkColumnsOffset(1, 0)
                } else {
                    // There's more sectors in the new column, do the opposite
                    linkColumnsOffset(0, 0)
                    linkColumnsOffset(0, 1)
                }
            }

            val act = actOf(columnNum)
            for ((i, sector) in column.withIndex()) {
                pickSectorType(sector, column.subList(0, i), act, usedSectorTypes, random)
                usedSectorTypes.add(sector.type)
            }

            sectors.add(column)
        }

        placeRequiredSectors(usedSectorTypes, random)

        // Add the last stand (sector 8 in vanilla)
        val finalType = sectorTypes["FINAL"] ?: error("Cannot find FINAL sector for the boss fight")
        val finalSector = SectorInfo(SECTOR_COUNT - 1, 0, finalType, SectorClass.HOSTILE)
        sectors.add(listOf(finalSector))

        // ... and link up all the sectors from the previous column, so it's accessible
        for (sector in sectors[sectors.size - 2]) {
            sector.nextSectors += finalSector
        }
    }

    /**
     * Make sure every map has the sectors it always should (see
     * [SectorType.always], such as the hazardous sectors with bosses): one
     * that didn't turn up takes the place of an ordinary sector (not
     * another unique one) in a column it can go in, at random.
     */
    private fun placeRequiredSectors(used: MutableSet<SectorType>, random: Random) {
        for (type in sectorTypes.values.filter { it.always && it !in used }) {
            val sectorClass = SectorClass.values().firstOrNull { type in sectorClasses[it].orEmpty() } ?: continue
            val places = sectors.flatten().filter { sector ->
                val column = sector.columnNumber
                column != 0 && column != midpointColumn && !sector.type.unique && !sector.type.always &&
                        (type.act == null || type.act == actOf(column)) &&
                        (type.sectors == null || nominalSectorNumber(column) in type.sectors) &&
                        vanillaSectorNumber(column, SECTOR_COUNT) >= type.minSector
            }
            val sector = places.randomOrNull(random) ?: continue
            sector.type = type
            sector.sectorClass = sectorClass
            used += type
        }
    }

    /**
     * The sector type with the given name, or null if there isn't one.
     */
    fun sectorType(name: String): SectorType? = sectorTypes[name]

    /** Every type of sector, by name. */
    val allSectorTypes: Collection<SectorType> get() = sectorTypes.values

    /**
     * The sector type between the two acts, if there is one.
     */
    private val midpointType: SectorType? get() = sectorTypes.values.firstOrNull { it.isMidpoint }

    /**
     * The column of the sector between the two acts, or null if this game
     * doesn't have acts: it needs at least [MIN_SECTORS_FOR_ACTS] sectors,
     * and a sector type to put there. This is the last sector of the first
     * act, sector 8 of 16.
     */
    private val midpointColumn: Int?
        get() = if (SECTOR_COUNT >= MIN_SECTORS_FOR_ACTS && midpointType != null) SECTOR_COUNT / 2 - 1 else null

    /**
     * How many sectors a column of the map can have: two to four in act 1
     * (as in vanilla), and four to eight in act 2, so it's twice as big.
     */
    fun columnSizeRange(columnNumber: Int): IntRange = when (actOf(columnNumber)) {
        1 -> 2..VANILLA_MAX_SECTORS_PER_COLUMN
        else -> VANILLA_MAX_SECTORS_PER_COLUMN..MAX_SECTORS_PER_COLUMN
    }

    /**
     * The act (1 or 2) of a column of the map. Games without acts are all
     * act 1, so the sectors only for act 2 never appear in them.
     */
    fun actOf(columnNumber: Int): Int {
        val midpoint = midpointColumn ?: return 1
        return if (columnNumber <= midpoint) 1 else 2
    }

    /**
     * How far through act 2 a column of the map is: 0 at its first sector,
     * just after the blockade, and 1 at its last, before the last stand (and
     * 0 in act 1, or a game without acts).
     */
    fun actTwoProgress(columnNumber: Int): Float {
        val midpoint = midpointColumn ?: return 0f
        val first = midpoint + 1
        val last = SECTOR_COUNT - 2
        if (columnNumber < first || last <= first)
            return 0f
        return ((columnNumber - first).toFloat() / (last - first)).coerceIn(0f, 1f)
    }

    /**
     * The number of a column's sector (from 1) as if the game had
     * [NOMINAL_SECTOR_COUNT] sectors, for [SectorType.sectors]: in a
     * sixteen-sector game, it's just the sector's number.
     */
    fun nominalSectorNumber(columnNumber: Int): Int {
        if (SECTOR_COUNT <= 1)
            return columnNumber + 1
        return columnNumber * (NOMINAL_SECTOR_COUNT - 1) / (SECTOR_COUNT - 1) + 1
    }

    /**
     * The sector types of a [sectorClass] that can go in a column of the map.
     */
    private fun availableTypes(sectorClass: SectorClass, columnNum: Int, act: Int, used: Set<SectorType>): List<SectorType> {
        val allTypes = sectorClasses[sectorClass] ?: error("No sectors for sector class $sectorClass")

        return allTypes.filter {
            // Gated to later sectors. If the game is longer than
            // vanilla, these are scaled to match.
            if (vanillaSectorNumber(columnNum, SECTOR_COUNT) < it.minSector)
                return@filter false

            // Some sectors only appear in one act, or some sectors
            if (it.act != null && it.act != act)
                return@filter false
            if (it.sectors != null && nominalSectorNumber(columnNum) !in it.sectors)
                return@filter false

            // If this is a unique sector, make sure it's not already been used
            if (it.unique && used.contains(it))
                return@filter false

            return@filter true
        }
    }

    /**
     * How strong the environmental hazards (asteroids, suns and pulsars) are
     * in a sector: 1 in act 1, as in vanilla, and 2 in act 2. See doc/game/lore.md.
     */
    fun environmentIntensity(sector: SectorInfo): Int = actOf(sector.columnNumber)

    /**
     * Pick the type of a new [sector], which has already been linked to the
     * sectors leading to it, and the [earlier] sectors in its column have
     * already been picked.
     *
     * With 16 sectors in a game, there's not enough sector types to go round,
     * so this avoids the same type coming up twice in a row on the player's
     * path: it avoids the types of the sectors leading to this one, the
     * sectors before them, and the other sectors the player could pick
     * instead of this one. If that's not possible in its sector class, it
     * tries without the sectors two columns back, then tries the other
     * sector classes, then gives up on avoiding the repeats.
     *
     * In act 2, the sectors only for act 2 are [ACT_ONLY_WEIGHT] times as
     * likely, so they aren't drowned out by all the vanilla ones.
     */
    private fun pickSectorType(
        sector: SectorInfo, earlier: List<SectorInfo>,
        act: Int, used: Set<SectorType>, random: Random
    ) {
        val previous = sectors.last().filter { sector in it.nextSectors }
        val beforeThat = sectors.getOrNull(sectors.size - 2).orEmpty()
            .filter { s -> s.nextSectors.any { it in previous } }
        val alternatives = earlier.filter { s -> previous.any { s in it.nextSectors } }

        fun options(sectorClass: SectorClass, avoid: List<SectorInfo>): List<SectorType> {
            val avoidTypes = avoid.map { it.type }.toSet()
            return availableTypes(sectorClass, sector.columnNumber, act, used).filter { it !in avoidTypes }
        }

        // In act 2, the vanilla civilian sectors are rare: the Federation's
        // space is behind the Rebel lines by then. (The Chonkestrel mod keeps
        // them to act 1 altogether.) The homeworlds aren't: one that didn't
        // turn up in act 1 is as likely as any other sector.
        fun isRare(sectorClass: SectorClass, type: SectorType) =
            act > 1 && sectorClass == SectorClass.CIVILIAN && type.act == null && !type.unique

        fun weightOf(sectorClass: SectorClass, type: SectorType): Double = when {
            type.weight != null -> type.weight.toDouble()
            isRare(sectorClass, type) -> ACT_2_VANILLA_CIVILIAN_WEIGHT
            act > 1 && type.act == act -> ACT_ONLY_WEIGHT.toDouble()
            else -> 1.0
        }

        fun pick(sectorClass: SectorClass, types: List<SectorType>) {
            var roll = random.nextDouble() * types.sumOf { weightOf(sectorClass, it) }
            sector.sectorClass = sectorClass
            sector.type = types.firstOrNull { type ->
                roll -= weightOf(sectorClass, type)
                roll < 0
            } ?: types.last()
        }

        val avoidNearby = previous + beforeThat + alternatives
        val avoidAdjacent = previous + alternatives
        val otherClasses = SectorClass.values().filter { it != sector.sectorClass }.shuffled(random)

        // First, look for types that aren't rare: in this sector's class,
        // then in the others. The rare ones can still be picked alongside
        // them, with their low weight.
        for (avoid in listOf(avoidNearby, avoidAdjacent)) {
            val types = options(sector.sectorClass, avoid)
            if (types.any { !isRare(sector.sectorClass, it) })
                return pick(sector.sectorClass, types)
        }
        for (otherClass in otherClasses) {
            val types = options(otherClass, avoidAdjacent)
            if (types.any { !isRare(otherClass, it) })
                return pick(otherClass, types)
        }

        // Then, take what there is
        for (avoid in listOf(avoidNearby, avoidAdjacent)) {
            val types = options(sector.sectorClass, avoid)
            if (types.isNotEmpty())
                return pick(sector.sectorClass, types)
        }

        for (otherClass in otherClasses) {
            val types = options(otherClass, avoidAdjacent)
            if (types.isNotEmpty())
                return pick(otherClass, types)
        }

        val types = options(sector.sectorClass, previous).ifEmpty { options(sector.sectorClass, emptyList()) }
        pick(sector.sectorClass, types)
    }

    private fun parseSectorType(elem: Element, namedSectorTypes: HashMap<String, List<String>>) {
        val name = elem.requireAttributeValue("name")
        if (namedSectorTypes.containsKey(name)) {
            println("[WARN] Overwriting sector type '$name'")
        }
        namedSectorTypes[name] = elem.mapChildrenText("sector")
    }

    private fun parseSectorDescription(elem: Element) {
        val name = elem.requireAttributeValue("name")

        // Specialcase a couple of unused sectors which don't have names set, and should never appear otherwise anyway
        // Note that abandoned sector doesn't refer to the lanius-filled in-game sector - that's LANIUS_SECTOR
        if (name == "ABANDONED_SECTOR" || name == "DEEP_SPACE_SECTOR") return

        // Sectors can be overwritten by mods
        if (sectorTypes.containsKey(name)) {
            println("Overwriting existing sector $name")
        }

        val sector = SectorType(eventManager, elem)
        check(name == sector.name)
        sectorTypes[name] = sector
    }

    /**
     * The events a sector can have, for a player ship of the given size: some
     * are only for big ships, one act, or some sectors.
     */
    fun eventsFor(sectorInfo: SectorInfo, playerShipSize: Int): List<SectorType.EventInfo> {
        val act = actOf(sectorInfo.columnNumber)
        val sectorNumber = nominalSectorNumber(sectorInfo.columnNumber)
        return sectorInfo.type.events.filter {
            playerShipSize >= it.minShipSize && (it.act == null || it.act == act) &&
                    (it.sectors == null || sectorNumber in it.sectors)
        }
    }

    fun generateSector(sectorInfo: SectorInfo, game: InGameState): Sector {
        val eventPool = ArrayList<Event>()
        // The events that don't get a hazardous sector's hazards
        val noHazardEvents: MutableSet<Event> = Collections.newSetFromMap(IdentityHashMap())
        for (ev in eventsFor(sectorInfo, game.playerBlueprint.shipSize)) {
            // There's more beacons than in vanilla, so scale up the number
            // of events to fill them. Don't scale events that only appear
            // once, though, since they're usually special.
            val scale = if (ev.count.last > 1) Sector.BEACON_COUNT_SCALE else 1
            val count = ev.count.random() * scale
            for (i in 1..count) {
                val event = ev.event.resolve()
                eventPool += event
                if (ev.noHazards)
                    noHazardEvents += event
            }
        }

        // The more dangerous the sector, the fewer stores it has (see
        // StoreProfile): drop some of them, at random
        val stores = eventPool.filter { it.isStore }
        val dropped = stores.size - StoreProfile.of(sectorInfo).keptStores(stores.size)
        for (store in stores.shuffled().take(dropped.coerceAtLeast(0))) {
            eventPool.remove(store)
        }

        // Try ten times to generate a suitable sector
        var attemptCount = 0
        var sector: Sector
        while (true) {
            sector = Sector(sectorInfo, eventPool, specialEvents, game.difficulty)

            val path = sector.findShortestPath(sector.startBeacon, sector.finishBeacon)

            // If there isn't a path to the finish beacon, try again regardless
            // of how many attempts we've previously made.
            if (path == null) {
                attemptCount++
                continue
            }

            // Otherwise try and require at least a five-beacon path
            if (path.size < 5 && attemptCount < 10) {
                attemptCount++
                continue
            }

            break
        }

        // Turn some of the vanilla nebulas into XFTL's own kinds
        if (!sector.isLastStand) {
            addStrangeNebulas(sector, sectorInfo, Random.Default)
        }

        // More hazards, and several at once, further into the game. The
        // last stand is left as it is.
        if (!sector.isLastStand) {
            addHazards(sector, sectorInfo, noHazardEvents, Random.Default, game.difficulty)
        }
        calmStart(sector)

        // A hunting boss sets out from the far end of the sector, its exit
        // (see HunterBoss)
        if (sectorInfo.type.hunterPeriod != null && !sector.isLastStand)
            sector.bosses.add(HunterBoss(sector, game, sector.finishBeacon))

        // Set up the flagship, if required. (Where the last stand has its
        // own boss at the base, its finish event, there's no flagship: see
        // doc/game/mainframe.md.)
        if (sector.isLastStand && sectorInfo.type.finishEventName == null) {
            attemptCount = 0
            while (true) {
                attemptCount++

                // The flagship goes on one of the two right-most columns.
                val flagshipBeacon = sector.beacons.filter { it.gridPos.x in Sector.LAST_TWO_COLUMNS }.random()

                // Make sure we have a path to the base!
                val path = sector.findShortestPath(flagshipBeacon, sector.finishBeacon) ?: continue

                // Try and pick a flagship position that gives the player
                // enough time to get there!
                // Note that the path length doesn't include the first beacon,
                // so the limits here are one lower than specified in the original XFTL's doc/sector-map.
                if (path.size !in 3..5 && attemptCount < 10)
                    continue

                sector.bosses.add(FlagshipBoss(sector, game, flagshipBeacon))
                break
            }
        }

        return sector
    }

    /**
     * Turn some of a sector's plain nebula beacons (from vanilla's events,
     * which don't say what kind of nebula they are) into XFTL's own kinds:
     * radiation, mirage and dark nebulas. See doc/game/nebulas.md.
     *
     * Sectors of one kind of nebula ([SectorType.nebula]) turn most of their
     * beacons (90%) into that kind instead, whatever their events.
     */
    fun addStrangeNebulas(sector: Sector, sectorInfo: SectorInfo, random: Random) {
        val sectorNebula = sectorInfo.type.nebula
        if (sectorNebula != null) {
            for (beacon in sector.beacons) {
                if (random.nextFloat() < sectorInfo.type.nebulaChance) {
                    beacon.nebulaOverride = sectorNebula
                }
            }
            return
        }

        val chance = if (actOf(sectorInfo.columnNumber) > 1) STRANGE_NEBULA_CHANCE_ACT_2 else STRANGE_NEBULA_CHANCE_ACT_1
        for (beacon in sector.beacons) {
            if (beacon.event.environment != Beacon.EnvironmentType.NEBULA)
                continue
            if (random.nextFloat() < chance) {
                beacon.nebulaOverride = STRANGE_NEBULAS.random(random)
            }
        }
    }

    /**
     * Take any hazard off the sector's start beacon: it's somewhere calm to
     * arrive in, with the sector's description, and at most a plain nebula.
     * Any other kind of nebula (or an ion storm) becomes a plain one, and
     * the other hazards go.
     */
    fun calmStart(sector: Sector) {
        val start = sector.startBeacon
        start.sectorHazards = emptyList()
        val own = start.nebulaOverride ?: start.event.environment ?: Beacon.EnvironmentType.NORMAL
        if (own != Beacon.EnvironmentType.NORMAL && own != Beacon.EnvironmentType.NEBULA) {
            start.nebulaOverride = if (own.isNebula) Beacon.EnvironmentType.NEBULA else Beacon.EnvironmentType.NORMAL
        }
    }

    /**
     * Which quarter of the game a sector is in (0 to 3), not counting the
     * last stand: sectors 1-4, 5-8, 9-12 and 13-15 in a sixteen-sector game.
     * Beacons get more hazards at once further in, see [hazardCountWeights].
     */
    fun hazardStage(columnNumber: Int): Int {
        val sectorsBeforeLastStand = (sectors.size - 1).coerceAtLeast(1)
        return (columnNumber * HAZARD_STAGES / sectorsBeforeLastStand).coerceIn(0, HAZARD_STAGES - 1)
    }

    /**
     * The chances (as weights) of a hazardous beacon in this sector having
     * one, two, three and four hazards at once. All four at once never
     * happen in the first quarter of the game, whatever the sector, and
     * the first sectors have at most [maxHazards] at once.
     */
    fun hazardCountWeights(sectorInfo: SectorInfo, difficulty: Difficulty = Difficulty.NORMAL): List<Int> {
        val stage = hazardStage(sectorInfo.columnNumber)
        var weights = when (hazardLevelOf(sectorInfo)) {
            SectorType.HazardLevel.NORMAL -> HAZARD_COUNT_WEIGHTS[stage]
            SectorType.HazardLevel.EVERY_BEACON -> HAZARD_SECTOR_COUNT_WEIGHTS[stage]
            SectorType.HazardLevel.CATASTROPHIC -> CATASTROPHIC_COUNT_WEIGHTS
            SectorType.HazardLevel.APOCALYPTIC -> APOCALYPTIC_COUNT_WEIGHTS
        }
        if (stage == 0)
            weights = weights.dropLast(1) + 0
        val max = maxHazards(sectorInfo.columnNumber, difficulty)
        weights = weights.mapIndexed { index, weight -> if (index < max) weight else 0 }
        return if (weights.sum() > 0) weights else listOf(1, 0, 0, 0)
    }

    /**
     * The most hazards a beacon can have at once in the sector in
     * [columnNumber]: one in the first, two in the second, three from the
     * third and all four from the fifth (see [effectiveSectorNumber]).
     */
    fun maxHazards(columnNumber: Int, difficulty: Difficulty): Int =
        when (effectiveSectorNumber(columnNumber, difficulty)) {
            in Int.MIN_VALUE..1 -> 1
            2 -> 2
            3, 4 -> 3
            else -> 4
        }

    /**
     * The sector in [columnNumber], numbered from 1 as in a sixteen-sector
     * game (see [nominalSectorNumber]), as the difficulty plays it, for how
     * hard the first sectors are: a sector earlier on easy, and a sector
     * later on hard.
     */
    fun effectiveSectorNumber(columnNumber: Int, difficulty: Difficulty): Int {
        val offset = when (difficulty) {
            Difficulty.EASY -> -1
            Difficulty.NORMAL -> 0
            Difficulty.HARD -> 1
        }
        return nominalSectorNumber(columnNumber) + offset
    }

    /**
     * How hazardous a sector is ([SectorType.hazards]).
     */
    fun hazardLevelOf(sectorInfo: SectorInfo): SectorType.HazardLevel = sectorInfo.type.hazards

    /**
     * Give the sector's beacons more hazards: one, two or three at once,
     * counting the event's own environment, with more of them further into
     * the game ([hazardCountWeights]). There can't be two of a kind, such as
     * two nebulas.
     *
     * In most sectors, only the beacons whose events have an environment get
     * any, as in vanilla. In hazardous sectors ([hazardLevelOf]), every
     * beacon does: those without one of their own mostly get the sector's
     * own kind of hazard (its nebula, or whatever's most common there), and
     * sometimes another.
     *
     * The start beacon, stores, quests and the [noHazardEvents] don't get
     * any (stores and quests are left out by [Beacon.environmentTypes], as
     * quests are added later).
     */
    fun addHazards(
        sector: Sector, sectorInfo: SectorInfo, noHazardEvents: Set<Event>, random: Random,
        difficulty: Difficulty = Difficulty.NORMAL
    ) {
        val everyBeacon = hazardLevelOf(sectorInfo) != SectorType.HazardLevel.NORMAL
        val weights = hazardCountWeights(sectorInfo, difficulty)
        val theme = if (everyBeacon) sectorTheme(sector, sectorInfo, random) else null

        for (beacon in sector.beacons) {
            if (beacon == sector.startBeacon || beacon.hasStore || beacon.event in noHazardEvents)
                continue
            // A boss's beacon (its sector's exit, see ColossalBoss) has the
            // environment its event gives it: its nebula (of the sector's own
            // kind, for a nebula sector; never one picked at random, as a
            // dark one would stop the drones the fight's about), and its
            // extra hazards
            if (beacon.isExit && sectorInfo.type.bossHoldsExit) {
                val sectorNebula = sectorInfo.type.nebula
                if (beacon.event.environment == Beacon.EnvironmentType.NEBULA && sectorNebula != null)
                    beacon.nebulaOverride = sectorNebula
                beacon.sectorHazards = beacon.event.extraHazards
                continue
            }

            val own = beacon.environmentType
            if (own == Beacon.EnvironmentType.NORMAL && !everyBeacon)
                continue

            val count = pickWeighted(weights, random) + 1
            val types = ArrayList<Beacon.EnvironmentType>()
            if (own != Beacon.EnvironmentType.NORMAL) {
                types += own
            } else {
                types += if (random.nextFloat() < SECTOR_THEME_CHANCE) theme!! else randomHazard(HAZARD_FAMILIES, random)
            }

            val others = HAZARD_FAMILIES.filter { family -> types.none { it.family == family } }.shuffled(random)
            for (family in others.take((count - types.size).coerceAtLeast(0))) {
                types += randomHazard(listOf(family), random)
            }

            beacon.sectorHazards = types.filter { it != own }
        }
    }

    /**
     * The kind of hazard a hazardous sector is themed around: its nebula, or
     * the kind its beacons' events have most, or a random one if they don't
     * have any.
     */
    private fun sectorTheme(sector: Sector, sectorInfo: SectorInfo, random: Random): Beacon.EnvironmentType {
        sectorInfo.type.nebula?.let { return it }
        return sector.beacons.map { it.environmentType }
            .filter { it != Beacon.EnvironmentType.NORMAL }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
            ?: randomHazard(HAZARD_FAMILIES, random)
    }

    // A random hazard from one of the families, with a random kind of nebula
    private fun randomHazard(families: List<Beacon.EnvironmentType>, random: Random): Beacon.EnvironmentType {
        val family = families.random(random)
        return if (family == Beacon.EnvironmentType.NEBULA) ALL_NEBULAS.random(random) else family
    }

    private fun pickWeighted(weights: List<Int>, random: Random): Int {
        var roll = random.nextInt(weights.sum())
        for ((index, weight) in weights.withIndex()) {
            roll -= weight
            if (roll < 0)
                return index
        }
        return weights.lastIndex
    }

    fun saveToXML(elem: Element, refs: ObjectRefs) {
        // Register all the sectors now, since they reference each other.
        for (column in sectors) {
            for (sector in column) {
                refs.register(sector, "sectorInfo")
            }
        }

        for (column in sectors) {
            val columnElem = Element("column")
            for (sector in column) {
                val sectorElem = Element("sectorInfo")
                sector.saveToXML(sectorElem, refs)
                columnElem.addContent(sectorElem)
            }
            elem.addContent(columnElem)
        }
    }

    /**
     * Deserialise a game map from XML.
     */
    constructor(content: GameContent, elem: Element, refs: RefLoader)
            : this(content.datafile, content.eventManager, content.enableAdvancedEdition) {

        // This assertion lets us modify the sectors array in the constructor
        require(sectors is ArrayList)

        val postLoad = ArrayList<(GameMap) -> Unit>()

        for ((columnNumber, columnElem) in elem.getChildren("column").withIndex()) {
            val column = ArrayList<SectorInfo>()
            for ((columnIndex, sectorElem) in columnElem.getChildren("sectorInfo").withIndex()) {
                val sector = deserialiseSectorInfo(sectorElem, refs, columnNumber, columnIndex, postLoad)
                column += sector
            }
            sectors += column
        }

        // This is required to link up all the neighbouring sectors, since they're
        // specified by their index in the next column.
        for (fn in postLoad) {
            fn(this)
        }
    }

    class SpecialEvents(
        /**
         * The very first event that appears in the first beacon of the game.
         */
        val startGame: IEvent,

        /**
         * The event to be used when we've run out of events, but still have more beacons
         * to be filled. After modifying the vanilla FTL event XMLs, this is hardcoded
         * to the 'NEUTRAL' event/eventList, so we should obviously behave likewise.
         *
         * Note that I haven't confirmed these should *only* be used after running out of
         * other events - it's perfectly possible the base game peppers these in on a
         * random chance.
         *
         * The filler will be resolved once per element, so if it's an [EventList] each beacon
         * will a random (though certainly not guaranteed to be unique) event.
         */
        val filler: IEvent,

        /**
         * The event to show on regular (non-nebula-covered) exit beacons. This seems
         * to be the same for all sectors.
         *
         * Whether this or [exitNebula] is used depends on whether the exit beacon
         * is situated in a nebula itself, not whether the sector type is a nebula.
         */
        val exit: IEvent,

        val exitNebula: IEvent,

        /**
         * This is the event the player sees when they jump to the federation base
         * in the last stand, without the flagship present.
         */
        val fedBase: IEvent
    )

    /**
     * This stores the information about an available sector as it appears
     * on the sector map.
     */
    inner class SectorInfo(
        /**
         * The index of this column throughout the game (0-7 in vanilla)
         * - this sets the sector's X position.
         */
        val columnNumber: Int,

        /**
         * The index of this sector within it's column - this sets it's Y position.
         *
         * Row doesn't seem like the appropriate word since they're not
         * in a grid, but it's pretty much that.
         */
        val columnIndex: Int,

        /**
         * The exact type of sector.
         */
        var type: SectorType,

        var sectorClass: SectorClass
    ) : ISerialReferencable {
        /**
         * The sectors the player can jump to from this one.
         */
        val nextSectors = ArrayList<SectorInfo>()

        /**
         * The vanilla sector (0-7) this sector is equivalent to, in terms
         * of difficulty. This is the same as [columnNumber] in a vanilla
         * eight-sector game, and spreads the difficulty out in longer games.
         */
        val difficultySectorNumber: Int get() = vanillaSectorNumber(columnNumber, sectors.size)

        /**
         * The vanilla sector number, as for [difficultySectorNumber], but
         * not rounded down, for the rewards: they grow a little with every
         * sector, rather than every other one.
         */
        val rewardSectorNumber: Float get() = vanillaSectorProgress(columnNumber, sectors.size)

        // Note we don't store the actual Sector object here - that's
        // only needed when the player is inside it, so it can be stored
        // separately and discarded when not in use to save memory.

        fun saveToXML(elem: Element, refs: ObjectRefs) {
            SaveUtil.addObjectId(elem, refs, this)
            elem.setAttribute("type", type.name)
            elem.setAttribute("class", sectorClass.name)

            // There's a  lot of SectorInfo objects, so put the next sectors
            // in attributes to save space.
            require(nextSectors.size <= 2)
            SaveUtil.addAttrInt(elem, "next1", nextSectors.getOrNull(0)?.columnIndex)
            SaveUtil.addAttrInt(elem, "next2", nextSectors.getOrNull(1)?.columnIndex)
        }
    }

    // Since the sector map relies on the identity of SectorInfo objects,
    // other classes shouldn't be deserialising their own copies.
    private fun deserialiseSectorInfo(
        elem: Element, refs: RefLoader,
        columnNumber: Int, columnIndex: Int,
        postLoad: ArrayList<(GameMap) -> Unit>
    ): SectorInfo {
        val typeName = elem.getAttributeValue("type")
        val sectorClass = SectorClass.valueOf(elem.getAttributeValue("class"))

        val type = sectorTypes[renamedId(typeName)] ?: error("Missing sector type '$typeName'")
        val sector = SectorInfo(columnNumber, columnIndex, type, sectorClass)

        for (i in 1..2) {
            // The index in the column of the next beacon is specified
            val index = SaveUtil.getAttrIntOrNull(elem, "next$i") ?: continue
            postLoad += {
                val nextColumn = it.sectors[columnNumber + 1]
                sector.nextSectors += nextColumn[index]
            }
        }

        SaveUtil.registerObjectId(elem, refs, sector)
        return sector
    }

    /**
     * The broad types of sector, which determines the colour of the sector on the map.
     */
    enum class SectorClass {
        CIVILIAN,
        HOSTILE,
        NEBULA;

        companion object {
            fun random(rand: Random): SectorClass {
                // 20% chance of a nebula
                if (rand.nextInt(5) == 0) {
                    return NEBULA
                }

                // Otherwise, 60% civilian and 40% hostile
                return if (rand.nextInt(10) < 4) HOSTILE else CIVILIAN
            }
        }
    }

    /**
     * The number of sectors in the game, including the starting sector and
     * the last stand.
     */
    val sectorCount: Int get() = sectors.size

    /**
     * The number of sectors in the biggest column of the map.
     */
    val maxColumnSize: Int get() = sectors.maxOfOrNull { it.size } ?: 0

    companion object {
        const val VANILLA_SECTOR_COUNT = 8

        // The hazardous sectors' old names, before the Maelstrom, the
        // Tempest Arm and the Rift became the Maw, the Gullet and the Belly,
        // so older saves still load. The events' IDs start with them too.
        private val RENAMED_IDS = listOf(
            Regex("\\bXFTL_TEMPEST_ARM\\b") to "XFTL_GULLET",
            Regex("\\bXFTL_TEMPEST_") to "XFTL_GULLET_",
            Regex("\\bXFTL_MAELSTROM") to "XFTL_MAW",
            Regex("\\bXFTL_RIFT") to "XFTL_BELLY",
        )

        /**
         * The current name of a sector type or event an older save
         * names by its old ID, see [RENAMED_IDS].
         */
        fun renamedId(id: String): String =
            RENAMED_IDS.fold(id) { name, (old, new) -> old.replace(name, new) }

        /**
         * The most sectors there can be in a single column of the sector
         * map. This is four in vanilla, but bigger maps give more choice.
         */
        const val MAX_SECTORS_PER_COLUMN = 8

        /**
         * The most sectors in a column in vanilla, and in act 1.
         */
        const val VANILLA_MAX_SECTORS_PER_COLUMN = 4

        /**
         * The fewest sectors a game can have to be split into two acts.
         */
        const val MIN_SECTORS_FOR_ACTS = 12

        /**
         * How likely each vanilla civilian sector is in act 2, compared to
         * the vanilla hostile and nebula ones, see [pickSectorType].
         */
        const val ACT_2_VANILLA_CIVILIAN_WEIGHT = 0.1

        /**
         * The length of game [SectorType.sectors] numbers its sectors for.
         */
        const val NOMINAL_SECTOR_COUNT = 16

        /**
         * How much more likely the sectors only for act 2 are, in act 2.
         */
        const val ACT_ONLY_WEIGHT = 2

        /**
         * The chance of a plain nebula beacon being one of XFTL's own kinds
         * of nebula instead, in each act. See [addStrangeNebulas].
         */
        const val STRANGE_NEBULA_CHANCE_ACT_1 = 0.1f
        const val STRANGE_NEBULA_CHANCE_ACT_2 = 0.3f

        /**
         * The game is split into this many stages for the hazards
         * (quarters), see [hazardStage].
         */
        const val HAZARD_STAGES = 4

        /**
         * The chances (in percent) of a hazardous beacon having one, two,
         * three or four hazards at once, in each quarter of the game, see
         * [addHazards]. Several at once get more common further in.
         */
        val HAZARD_COUNT_WEIGHTS = listOf(
            listOf(85, 12, 3, 0),
            listOf(60, 27, 11, 2),
            listOf(35, 35, 23, 7),
            listOf(15, 30, 37, 18),
        )

        /**
         * The same, for the hazardous sectors (the new nebula sectors and
         * the Maw), which have more at once than the others.
         */
        val HAZARD_SECTOR_COUNT_WEIGHTS = listOf(
            listOf(60, 30, 10, 0),
            listOf(35, 40, 22, 3),
            listOf(15, 35, 40, 10),
            listOf(5, 25, 45, 25),
        )

        /**
         * The same, for the catastrophic sectors (the Gullet), wherever
         * they are.
         */
        val CATASTROPHIC_COUNT_WEIGHTS = listOf(0, 20, 70, 10)

        /**
         * The same, for the apocalyptic sectors (the Belly).
         */
        val APOCALYPTIC_COUNT_WEIGHTS = listOf(0, 0, 25, 75)

        /**
         * The chance of a beacon without an environment of its own in a
         * hazardous sector getting the sector's own kind, rather than a
         * random one, see [addHazards].
         */
        const val SECTOR_THEME_CHANCE = 0.75f

        // The kinds of hazard a hazardous sector's beacons can have: any
        // kind of nebula, asteroids, a sun or a pulsar
        private val HAZARD_FAMILIES = listOf(
            Beacon.EnvironmentType.NEBULA,
            Beacon.EnvironmentType.ASTEROID,
            Beacon.EnvironmentType.SUN,
            Beacon.EnvironmentType.PULSAR
        )

        private val ALL_NEBULAS = Beacon.EnvironmentType.entries.filter { it.isNebula }

        val STRANGE_NEBULAS = listOf(
            Beacon.EnvironmentType.RADIATION,
            Beacon.EnvironmentType.MIRAGE,
            Beacon.EnvironmentType.DARK
        )

        /**
         * The number of sectors in a new game. XFTL's default is double
         * vanilla's; run with -Dxftl.sectors=8 for vanilla-length games.
         */
        val SECTOR_COUNT: Int = System.getProperty("xftl.sectors")?.toInt()?.coerceAtLeast(3) ?: 16

        /**
         * Convert a sector number in a game with [sectorCount] sectors to the
         * vanilla sector (0-7) at the same point through the game. This is used
         * to scale the difficulty, rewards and so on to the length of the game.
         */
        fun vanillaSectorNumber(sector: Int, sectorCount: Int): Int {
            if (sectorCount <= 1)
                return sector
            return sector * (VANILLA_SECTOR_COUNT - 1) / (sectorCount - 1)
        }

        /**
         * [vanillaSectorNumber], not rounded down: how far through the
         * vanilla sectors (0-7) [sector] is.
         */
        fun vanillaSectorProgress(sector: Int, sectorCount: Int): Float {
            if (sectorCount <= 1)
                return sector.toFloat()
            return sector * (VANILLA_SECTOR_COUNT - 1).toFloat() / (sectorCount - 1)
        }
    }
}
