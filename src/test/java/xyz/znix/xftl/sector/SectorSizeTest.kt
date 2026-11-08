package xyz.znix.xftl.sector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

class SectorSizeTest {
    @Test
    fun testSectorsAreBigger() {
        val kit = InGameTest()

        repeat(20) {
            val info = kit.state.gameMap.sectors.first().single()
            val sector = kit.state.gameMap.generateSector(info, kit.state)

            // About twice as many beacons as vanilla's 20-24
            assertTrue("${sector.beacons.size} beacons", sector.beacons.size > 30)

            // They all fit on the map
            for (beacon in sector.beacons) {
                assertTrue(beacon.pos.x in 0 until Sector.MAP_SIZE.x)
                assertTrue(beacon.pos.y in 0 until Sector.MAP_SIZE.y)
            }

            // The start is on the left, the exit in the last two columns, and
            // there's a path between them.
            assertTrue(sector.startBeacon.gridPos.x == 0)
            assertTrue(sector.finishBeacon.gridPos.x >= Sector.GRID_SIZE.x - 2)
            assertNotNull(sector.findShortestPath(sector.startBeacon, sector.finishBeacon))
        }
    }

    @Test
    fun testEveryBeaconCanBeReached() {
        // A beacon whose neighbours are all too far away to be linked to
        // gets linked anyway, so none (such as a store) is out of reach
        val kit = InGameTest()
        repeat(300) {
            val info = kit.state.gameMap.sectors.first().single()
            val sector = kit.state.gameMap.generateSector(info, kit.state)
            for (beacon in sector.beacons) {
                assertNotNull("${beacon.gridPos} out of reach", sector.findShortestPath(sector.startBeacon, beacon))
                // (links go both ways)
                for (neighbour in beacon.neighbours)
                    assertTrue(beacon in neighbour.neighbours)
            }
        }
    }

    @Test
    fun testNebulasAreInClouds() {
        // The nebula beacons are gathered into a cloud or a few, of beacons
        // next to each other, rather than scattered
        val kit = InGameTest(sectorData = NEBULA_SECTOR_XML, extraEvents = listOf(NEBULA_EVENT_XML))
        var checked = 0
        repeat(100) {
            val info = kit.state.gameMap.sectors.first().single()
            val sector = kit.state.gameMap.generateSector(info, kit.state)
            val nebulas = sector.beacons.filter {
                it.event.environment?.isNebula == true && it != sector.startBeacon && it != sector.finishBeacon
            }.toSet()
            if (nebulas.size < 2)
                return@repeat
            checked++

            // Count the groups of nebula beacons linked to each other
            val left = nebulas.toMutableSet()
            var clouds = 0
            while (left.isNotEmpty()) {
                clouds++
                val queue = ArrayDeque(listOf(left.first()))
                while (queue.isNotEmpty()) {
                    val beacon = queue.removeFirst()
                    if (left.remove(beacon))
                        queue += beacon.neighbours.filter { it in left }
                }
            }
            val most = 1 + nebulas.size / Sector.BEACONS_PER_NEBULA_CLOUD
            assertTrue("${nebulas.size} nebulas in $clouds clouds", clouds <= most + 1)
        }
        assertTrue(checked > 0)
    }

    @Test
    fun testVanillaColumnsAreScaled() {
        // The whole map maps to the whole map
        assertEquals(0 until Sector.GRID_SIZE.x, Sector.scaleVanillaColumns(0..5))

        // And the middle of the map stays in the middle
        val middle = Sector.scaleVanillaColumns(2..3)
        assertTrue(middle.first > 0 && middle.last < Sector.GRID_SIZE.x - 1)
    }

    companion object {
        // A sector with a few nebula beacons among plain ones
        private const val NEBULA_SECTOR_XML = """
<FTL>
    <sectorDescription name="CIVILIAN_SECTOR" minSector="0" unique="false">
        <nameList><name>Clouds</name></nameList>
        <trackList/>
        <event name="PLAIN" min="8" max="8"/>
        <event name="IN_NEBULA" min="4" max="4"/>
    </sectorDescription>
    <sectorDescription name="FINAL" minSector="0" unique="false">
        <nameList><name>Last stand</name></nameList>
        <trackList/>
    </sectorDescription>
    <sectorType name="CIVILIAN"><sector>CIVILIAN_SECTOR</sector></sectorType>
    <sectorType name="HOSTILE"><sector>CIVILIAN_SECTOR</sector></sectorType>
    <sectorType name="NEBULA"><sector>CIVILIAN_SECTOR</sector></sectorType>
</FTL>
"""

        private const val NEBULA_EVENT_XML = """
<FTL>
    <event name="PLAIN"/>
    <event name="IN_NEBULA"><environment type="nebula"/></event>
</FTL>
"""
    }
}
