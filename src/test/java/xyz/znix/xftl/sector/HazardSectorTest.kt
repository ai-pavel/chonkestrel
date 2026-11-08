package xyz.znix.xftl.sector

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.testkit.InGameTest

/**
 * Check beacons get more hazards at once further into the game, and that
 * every beacon of a hazardous sector (`xftlHazards`) gets some, besides the
 * start, stores and events marked `xftlNoHazards`.
 */
class HazardSectorTest {
    private fun createKit(hazards: String, startEvent: String? = null) = InGameTest(
        sectorData = sectorData(hazards, startEvent), extraEvents = listOf(EVENT_XML)
    )

    /**
     * Generate the first sector many times, as if it were in the given column,
     * and count how many beacons have each number of hazards.
     */
    private fun countHazards(kit: InGameTest, column: Int): Map<Int, Int> {
        val map = kit.state.gameMap
        val first = map.sectors.first().single()
        val info = map.SectorInfo(column, 0, first.type, first.sectorClass)

        val counts = HashMap<Int, Int>()
        repeat(40) {
            val sector = map.generateSector(info, kit.state)
            for (beacon in sector.beacons) {
                val types = beacon.environmentTypes
                val exempt = beacon == sector.startBeacon || beacon.hasStore ||
                        beacon.event.debugId.contains("SPECIAL")
                if (exempt) {
                    assertEquals(listOf(Beacon.EnvironmentType.NORMAL), types)
                    continue
                }

                // Never two of a kind, and a nebula event keeps its nebula
                assertTrue("$types", types.size in 1..Beacon.MAX_HAZARDS)
                assertEquals("$types", types.size, types.map { it.family }.distinct().size)
                if (beacon.event.environment == Beacon.EnvironmentType.NEBULA)
                    assertTrue("$types", types.first().isNebula)

                val count = if (types == listOf(Beacon.EnvironmentType.NORMAL)) 0 else types.size
                counts[count] = (counts[count] ?: 0) + 1
            }
        }
        return counts
    }

    // The first column where beacons can have [hazards] at once (see
    // GameMap.maxHazards)
    private fun firstColumnWith(kit: InGameTest, hazards: Int): Int {
        val map = kit.state.gameMap
        return (0 until map.sectors.size).first { map.maxHazards(it, Difficulty.NORMAL) >= hazards }
    }

    @Test
    fun everyBeaconOfAHazardousSectorHasHazards() {
        val kit = createKit("true")
        val counts = countHazards(kit, firstColumnWith(kit, 3))

        assertEquals(null, counts[0])
        assertTrue(counts.toString(), (1..3).all { (counts[it] ?: 0) > 0 })
    }

    @Test
    fun moreHazardsAtOnceLaterOn() {
        val kit = createKit("false")
        val map = kit.state.gameMap
        val lastColumn = map.sectors.size - 2

        // The plain beacons don't get any in a normal sector
        val early = countHazards(kit, 0)
        val late = countHazards(kit, lastColumn)
        assertTrue(early.toString(), (early[0] ?: 0) > 0)

        // Early on, most of the hazards are single; late on, triples are the
        // most common, and all four at once turn up
        assertTrue(early.toString(), early[1]!! > (early[2] ?: 0) + (early[3] ?: 0))
        assertTrue(late.toString(), late[3]!! > late[1]!!)
        assertTrue(late.toString(), (late[4] ?: 0) > 0)
    }

    @Test
    fun catastrophicSectorsHaveTwoOrThree() {
        val kit = createKit("catastrophic")
        val counts = countHazards(kit, firstColumnWith(kit, 3))

        assertEquals(null, counts[0])
        assertEquals(null, counts[1])
        assertTrue(counts.toString(), counts[3]!! > counts[2]!!)
    }

    @Test
    fun theGameIsSplitIntoQuarters() {
        val map = createKit("false").state.gameMap
        val stages = (0 until map.sectors.size - 1).map { map.hazardStage(it) }

        // Sixteen sectors: 1-4, 5-8, 9-12 and 13-15, with the last stand left out
        if (map.sectors.size == 16) {
            assertEquals(List(4) { 0 } + List(4) { 1 } + List(4) { 2 } + List(3) { 3 }, stages)
        }
        assertEquals(stages.sorted(), stages)
        assertEquals(setOf(0, 1, 2, 3), stages.toSet())
    }

    @Test
    fun neverFourAtOnceInTheFirstQuarter() {
        for (hazards in listOf("false", "true", "catastrophic", "apocalyptic")) {
            val kit = createKit(hazards)
            assertEquals(hazards, null, countHazards(kit, 0)[4])
        }
    }

    @Test
    fun apocalypticSectorsHaveThreeOrFour() {
        val kit = createKit("apocalyptic")
        val counts = countHazards(kit, kit.state.gameMap.sectors.size - 2)

        assertEquals(null, counts[0])
        assertEquals(null, counts[1])
        assertEquals(null, counts[2])
        assertTrue(counts.toString(), counts[4]!! > counts[3]!!)
    }

    @Test
    fun questsAndStoresDontHaveTheHazards() {
        val kit = createKit("true")
        val info = kit.state.gameMap.sectors.first().single()
        val sector = kit.state.gameMap.generateSector(info, kit.state)
        val beacon = sector.beacons.first { it.sectorHazards.isNotEmpty() && it.event.environment == null }

        beacon.hasQuest = true
        assertEquals(listOf(Beacon.EnvironmentType.NORMAL), beacon.environmentTypes)
        beacon.hasQuest = false
        beacon.hasStore = true
        assertEquals(listOf(Beacon.EnvironmentType.NORMAL), beacon.environmentTypes)
    }

    @Test
    fun questsCanGoWhereANebulaWasAdded() {
        val kit = createKit("true")
        val info = kit.state.gameMap.sectors.first().single()
        val sector = kit.state.gameMap.generateSector(info, kit.state)

        // A beacon whose only nebula came from the sector can take a quest,
        // and then loses the sector's hazards
        val beacon = sector.beacons.first {
            it.event.debugId.contains("PLAIN") && it != sector.startBeacon
        }
        beacon.sectorHazards = listOf(Beacon.EnvironmentType.NEBULA, Beacon.EnvironmentType.SUN)
        assertTrue(beacon.isNebula)
        for (other in sector.beacons) {
            if (other != beacon && other != sector.startBeacon)
                other.hasQuest = true
        }
        assertTrue(sector.addQuest(sector.startBeacon, beacon.event, ignoreDistance = true))
        assertTrue(beacon.hasQuest)
        assertEquals(listOf(Beacon.EnvironmentType.NORMAL), beacon.environmentTypes)
    }

    @Test
    fun normalNebulaSectorsAreLeftAlone() {
        // Without xftlHazards, the plain beacons don't get hazards, even in a
        // nebula sector
        val kit = createKit("false")
        val map = kit.state.gameMap
        val first = map.sectors.first().single()
        val info = map.SectorInfo(0, 0, first.type, GameMap.SectorClass.NEBULA)
        val sector = map.generateSector(info, kit.state)
        assertTrue(sector.beacons.filter { it.event.debugId.contains("PLAIN") }
            .all { it.environmentTypes == listOf(Beacon.EnvironmentType.NORMAL) })
    }

    @Test
    fun fewHazardsAtOnceInTheFirstSectors() {
        // One at most in the first sector, and two in the second, even in
        // the most hazardous sectors; on easy, a sector later
        val kit = createKit("apocalyptic")
        val map = kit.state.gameMap
        assertEquals(1, map.maxHazards(0, Difficulty.NORMAL))
        assertEquals(1, map.maxHazards(1, Difficulty.EASY))
        assertEquals(2, map.maxHazards(1, Difficulty.NORMAL))
        assertEquals(4, map.maxHazards(map.sectors.size - 2, Difficulty.EASY))
        val first = countHazards(kit, 0)
        assertEquals(first.toString(), setOf(1), first.keys)
    }

    @Test
    fun theStartBeaconIsCalm() {
        // Whatever the sector's start event, and however hazardous the
        // sector, the start beacon has at most a plain nebula (see
        // GameMap.calmStart)
        val expected = mapOf(
            // An event list is re-picked until it's calm
            "STORMY_START" to listOf(Beacon.EnvironmentType.NORMAL),
            // Another kind of nebula becomes a plain one, other hazards go
            "RADIATION_START" to listOf(Beacon.EnvironmentType.NEBULA),
            "ASTEROID_START" to listOf(Beacon.EnvironmentType.NORMAL),
        )
        for ((start, environment) in expected) {
            val kit = createKit("apocalyptic", start)
            val map = kit.state.gameMap
            val first = map.sectors.first().single()
            for (column in listOf(1, map.sectors.size - 2)) {
                val info = map.SectorInfo(column, 0, first.type, first.sectorClass)
                repeat(20) {
                    val sector = map.generateSector(info, kit.state)
                    assertEquals(start, environment, sector.startBeacon.environmentTypes)
                }
            }
        }
    }

    @Test
    fun everySectorHasAStartAndAnExit() {
        // The start and exit beacons' cells are never left out, so a sector
        // can't be left without them
        val kit = createKit("false")
        val map = kit.state.gameMap
        val first = map.sectors.first().single()
        val info = map.SectorInfo(1, 0, first.type, first.sectorClass)
        repeat(300) {
            val sector = map.generateSector(info, kit.state)
            assertEquals(0, sector.startBeacon.gridPos.x)
            assertTrue(sector.finishBeacon.gridPos.x in Sector.LAST_TWO_COLUMNS)
        }
    }

    companion object {
        @Language("XML")
        private fun sectorData(hazards: String, startEvent: String? = null) = """
<FTL>
    <sectorDescription name="CIVILIAN_SECTOR" minSector="0" unique="false" xftlHazards="$hazards">
        <nameList><name>Hazards</name></nameList>
        <trackList/>
        ${startEvent?.let { "<startEvent>$it</startEvent>" } ?: ""}
        <event name="PLAIN" min="8" max="8"/>
        <event name="IN_NEBULA" min="3" max="3"/>
        <event name="ASTEROIDS" min="3" max="3"/>
        <event name="SHOP" min="1" max="1"/>
        <event name="SPECIAL" min="2" max="2" xftlNoHazards="true"/>
    </sectorDescription>
    <sectorDescription name="FINAL" minSector="0" unique="false">
        <nameList><name>Last stand</name></nameList>
        <trackList/>
    </sectorDescription>
    ${listOf("CIVILIAN", "HOSTILE", "NEBULA").joinToString("\n") { """
    <sectorType name="$it">
        <sector>CIVILIAN_SECTOR</sector>
    </sectorType>""" }}
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <event name="PLAIN"/>
    <event name="IN_NEBULA"><environment type="nebula"/></event>
    <event name="ASTEROIDS"><environment type="asteroid"/></event>
    <event name="SHOP"><store/></event>
    <event name="SPECIAL"/>
    <eventList name="STORMY_START">
        <event load="ASTEROIDS"/>
        <event load="PLAIN"/>
    </eventList>
    <event name="RADIATION_START"><environment type="radiation"/></event>
    <event name="ASTEROID_START"><environment type="asteroid"/></event>
</FTL>
"""
    }
}
