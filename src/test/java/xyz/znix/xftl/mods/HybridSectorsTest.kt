package xyz.znix.xftl.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.LazyShipBlueprint
import xyz.znix.xftl.Translator
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.EventList
import xyz.znix.xftl.sector.EventManager
import xyz.znix.xftl.sector.IEvent
import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.shipgen.EnemyShipSpec
import xyz.znix.xftl.shipgen.ShipGenerator
import java.io.File
import kotlin.random.Random

/**
 * The Chonkestrel mod's hybrid sectors (see doc/game/sectors.md, made by
 * tools/hybrid_sectors.py): one for each pair of the eight races with
 * hybrid ships, in act 2, with that pair's hybrids to fight.
 */
class HybridSectorsTest {
    private val modded = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(MOD_DIR.toPath())))
    private val blueprints = BlueprintManager(modded, true)
    private val translator = Translator(modded, "en")
    private val events = EventManager(modded, translator, blueprints)
    private val map = GameMap(modded, events, true, Random(0))

    // Every pair of races, the more hostile first, as the sectors are named
    private val pairs = RACES.keys.toList().let { races ->
        races.indices.flatMap { i -> (i + 1 until races.size).map { races[i] to races[it] } }
    }

    @Test
    fun testOneSectorForEachPair() {
        assertEquals(28, pairs.size)
        for ((first, second) in pairs) {
            val name = "XFTL_HYBRID_${first}_$second"
            val type = map.sectorType(name)
            assertNotNull(name, type)
            type!!
            assertEquals(name, listOf(first, second), type.hybridRaces)
            assertEquals(name, 2, type.act)
            assertTrue(name, !type.unique)
            assertNotNull(name, type.startEvent)
            assertEquals(
                "${RACES[first]}-${RACES[second]} Controlled Sector",
                translator["sectorname_$name"]
            )
        }

        // ...and none the other way round, or for the Federation
        for ((first, second) in pairs)
            assertEquals(null, map.sectorType("XFTL_HYBRID_${second}_$first"))
        assertTrue(RACES.keys.none { map.sectorType("XFTL_HYBRID_FED_$it") != null })
    }

    @Test
    fun testEachPairHasHybridsToFight() {
        // A scout and a bomber of the pair's, one way round or the other, as
        // ShipGenerator.hybridFor picks them
        for ((first, second) in pairs) {
            for (prefix in listOf(ShipGenerator.HYBRID_PREFIX, ShipGenerator.HYBRID_BOMBER_PREFIX)) {
                val ships = listOf("$prefix${first}_$second", "$prefix${second}_$first")
                    .mapNotNull { blueprints.getOrNull(it) as? LazyShipBlueprint }
                assertTrue("$prefix $first $second", ships.isNotEmpty())
            }
        }
    }

    @Test
    fun testEachShipsHybridsAreScoutsOrBombers() {
        // hybridFor tells a scout from a bomber by its list of hybrids: a
        // bomber's are all bombers, and a scout's are all scouts
        val lists = BASES.map { blueprints[ShipGenerator.HYBRIDS_OF + it].list().map { bp -> bp.name } }
        for ((base, names) in BASES.zip(lists)) {
            assertTrue(base, names.isNotEmpty())
            val bombers = names.count { it.startsWith(ShipGenerator.HYBRID_BOMBER_PREFIX) }
            assertTrue("$base: $names", bombers == 0 || bombers == names.size)
        }
    }

    @Test
    fun testOnlyInActTwoAndHostile() {
        val midpoint = GameMap.SECTOR_COUNT / 2 - 1
        val seen = HashSet<String>()
        for (seed in 0 until 100) {
            val map = GameMap(modded, events, true, Random(seed))
            for ((column, sectors) in map.sectors.withIndex()) {
                for (sector in sectors.filter { it.type.hybridRaces != null }) {
                    assertTrue(sector.type.name, column > midpoint)
                    assertEquals(sector.type.name, GameMap.SectorClass.HOSTILE, sector.sectorClass)
                    // (this loads its events, so they all exist)
                    assertTrue(map.eventsFor(sector, 2).isNotEmpty())
                    seen += sector.type.name
                }
            }
        }
        assertEquals(pairs.map { (a, b) -> "XFTL_HYBRID_${a}_$b" }.toSet(), seen)
    }

    @Test
    fun testTheirOwnEvents() {
        for ((first, second) in pairs) {
            val sector = (0 until 200).asSequence()
                .flatMap { GameMap(modded, events, true, Random(it)).sectors.flatten() }
                .first { it.type.name == "XFTL_HYBRID_${first}_$second" }
            val name = sector.type.name
            assertEquals(name, "XFTL_HYBRIDSEC_START_${first}_$second", sector.type.startEventName)

            val counts = map.eventsFor(sector, 2).associate { it.name to it.count }
            // The hybrid sectors' own events, with both races' pools
            for (own in listOf("HOSTILE", "COMMON", "NOTHING", first, second))
                assertTrue("$name: $own", ("XFTL_HYBRIDSEC_$own" in counts))
            assertTrue(name, counts.getValue("XFTL_HYBRIDSEC_HOSTILE").first >= 5)

            // ...and the races' vanilla events (and their new ones for the
            // vanilla sectors) only now and then
            val vanilla = counts.filterKeys { !it.startsWith("XFTL_") && it != "STORE" && it != "ITEMS" }
            assertTrue("$name: $vanilla", vanilla.isNotEmpty() && vanilla.values.all { it.last <= 1 })
            assertTrue(name, counts.filterKeys { it.startsWith("XFTL_MORE_") }.values.all { it.last <= 1 })
        }
    }

    @Test
    fun testEachRacesPool() {
        for (race in RACES.keys) {
            val pool = events["XFTL_HYBRIDSEC_$race"] as EventList
            assertEquals(race, 4, pool.events.size)
        }

        // Every event they can lead to loads, with its ships
        val ships = HashSet<EnemyShipSpec>()
        val visited = HashSet<IEvent>()
        fun visit(event: IEvent) {
            if (!visited.add(event))
                return
            when (event) {
                is EventList -> event.events.forEach(::visit)
                is Event -> {
                    event.loadShipName?.let { events.getShip(it) }?.let { ship ->
                        ships += ship
                        listOfNotNull(ship.destroyed, ship.deadCrew, ship.surrender, ship.escape, ship.gotaway)
                            .forEach(::visit)
                    }
                    event.choices.forEach { visit(it.event) }
                }
            }
        }
        val lists = RACES.keys.map { "XFTL_HYBRIDSEC_$it" } +
                listOf("HOSTILE", "COMMON", "NOTHING").map { "XFTL_HYBRIDSEC_$it" } +
                pairs.map { (a, b) -> "XFTL_HYBRIDSEC_START_${a}_$b" }
        lists.forEach { visit(events[it]) }
        assertTrue(visited.size > 50)

        // Their ships are CONCORD's joined ships (mostly the sector's pair's
        // hybrids), or ones CONCORD hasn't joined, which never are hybrids
        assertTrue(ships.size > 10)
        val joinedShips = blueprints["XFTL_HYBRID_SECTOR_SHIPS"]
        for (ship in ships) {
            val joined = ship.autoBlueprint === joinedShips
            assertTrue(ship.name, joined || ship.noHybrid)
            assertTrue(ship.name, ship.destroyed != null && ship.deadCrew != null)
        }
        assertTrue(ships.any { it.noHybrid })
    }

    companion object {
        private val MOD_DIR = File("mods/chonkestrel")

        // The races with hybrid sectors, from the most hostile, and their names
        private val RACES = linkedMapOf(
            "REBEL" to "Rebel", "MANTIS" to "Mantis", "ROCK" to "Rock", "SLUG" to "Slug",
            "LANIUS" to "Lanius", "CRYSTAL" to "Crystal", "ENGI" to "Engi", "ZOLTAN" to "Zoltan",
        )

        // The vanilla ships that hybrids are made from
        private val BASES = listOf(
            "FED_SCOUT", "REBEL_SKINNY", "CIRCLE_SCOUT", "ZOLTAN_FIGHTER", "MANTIS_FIGHTER", "JELLY_BUTTON",
            "ROCK_SCOUT", "CRYSTAL_SCOUT", "ANAEROBIC_SCOUT", "FED_BOMBER", "REBEL_FAT", "CIRCLE_BOMBER",
            "ZOLTAN_BOMBER", "MANTIS_BOMBER", "JELLY_CROISSANT", "ROCK_FIGHT", "CRYSTAL_BOMBER", "ANAEROBIC_BOMBER",
        )
    }
}
