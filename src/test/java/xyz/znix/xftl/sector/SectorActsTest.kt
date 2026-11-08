package xyz.znix.xftl.sector

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest
import kotlin.random.Random

class SectorActsTest {
    private val kit = InGameTest(sectorData = SECTOR_DATA, extraEvents = listOf(EVENT_XML))

    private fun maps(kit: InGameTest = this.kit): List<GameMap> {
        val content = kit.state.content
        return (0 until 200).map { GameMap(content.datafile, kit.state.eventManager, true, Random(it)) }
    }

    @Test
    fun testMidpoint() {
        assumeTrue(GameMap.SECTOR_COUNT >= GameMap.MIN_SECTORS_FOR_ACTS)
        val midpoint = GameMap.SECTOR_COUNT / 2 - 1

        for (map in maps()) {
            // Every game goes through the midpoint, and only there
            val column = map.sectors[midpoint].single()
            assertEquals("MIDPOINT", column.type.name)
            assertTrue(map.sectors[midpoint - 1].all { it.nextSectors == listOf(column) })
            assertEquals(map.sectors[midpoint + 1], column.nextSectors)
            assertEquals(1, map.sectors.flatten().count { it.type.name == "MIDPOINT" })

            assertEquals(1, map.actOf(midpoint))
            assertEquals(2, map.actOf(midpoint + 1))

            // Hazards are stronger in the second act
            assertEquals(1, map.environmentIntensity(map.sectors[midpoint - 1].first()))
            assertEquals(1, map.environmentIntensity(column))
            assertEquals(2, map.environmentIntensity(map.sectors[midpoint + 1].first()))
        }
    }

    @Test
    fun testActTwoProgress() {
        assumeTrue(GameMap.SECTOR_COUNT >= GameMap.MIN_SECTORS_FOR_ACTS)
        val midpoint = GameMap.SECTOR_COUNT / 2 - 1
        val map = maps().first()

        // Nothing in act 1, then from 0 just after the midpoint to 1 at the
        // sector before the last stand, a step every sector
        for (column in 0..midpoint)
            assertEquals(0f, map.actTwoProgress(column), 0f)
        assertEquals(0f, map.actTwoProgress(midpoint + 1), 0f)
        assertEquals(1f, map.actTwoProgress(GameMap.SECTOR_COUNT - 2), 0f)
        val progress = (midpoint + 1..GameMap.SECTOR_COUNT - 2).map { map.actTwoProgress(it) }
        assertTrue(progress.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun testActTwoIsTwiceAsBig() {
        assumeTrue(GameMap.SECTOR_COUNT >= GameMap.MIN_SECTORS_FOR_ACTS)
        val midpoint = GameMap.SECTOR_COUNT / 2 - 1

        var actOne = 0
        var actTwo = 0
        for (map in maps()) {
            val columns = map.sectors.dropLast(1)
            for ((columnNumber, column) in columns.withIndex()) {
                when {
                    columnNumber == 0 || columnNumber == midpoint -> continue

                    // Act 1 is vanilla-sized, two to four sectors a column
                    columnNumber < midpoint -> {
                        assertTrue("${column.size}", column.size in 2..4)
                        actOne += column.size
                    }

                    // Act 2 opens up from two sectors after the blockade, to
                    // four, then four to eight
                    columnNumber == midpoint + 1 -> assertEquals(2, column.size)
                    else -> {
                        assertTrue("${column.size}", column.size in 4..8)
                        actTwo += column.size
                    }
                }
            }
        }
        assertTrue("$actOne, $actTwo", actTwo > actOne * 3 / 2)
    }

    @Test
    fun testActTwoSectors() {
        assumeTrue(GameMap.SECTOR_COUNT >= GameMap.MIN_SECTORS_FOR_ACTS)
        val midpoint = GameMap.SECTOR_COUNT / 2 - 1

        var seen = 0
        for (map in maps()) {
            for ((columnNumber, column) in map.sectors.withIndex()) {
                for (sector in column.filter { it.type.name == "ACT_TWO" }) {
                    assertTrue(columnNumber > midpoint)
                    seen++
                }
            }
        }
        assertTrue(seen > 0)
    }

    @Test
    fun testNoRepeatsInARow() {
        for (map in maps()) {
            for (column in map.sectors) {
                for (sector in column) {
                    // The next sector is never the same type as this one
                    for (next in sector.nextSectors) {
                        assertNotEquals(sector.type, next.type)
                    }

                    // And the player's two choices are different
                    if (sector.nextSectors.size == 2) {
                        assertNotEquals(sector.nextSectors[0].type, sector.nextSectors[1].type)
                    }
                }
            }
        }
    }

    @Test
    fun testFinishEvent() {
        assumeTrue(GameMap.SECTOR_COUNT >= GameMap.MIN_SECTORS_FOR_ACTS)
        val type = kit.state.gameMap.sectors.flatten().map { it.type }.first { it.name == "MIDPOINT" }
        assertEquals("MIDPOINT_EXIT", type.finishEventName)
        assertEquals(null, kit.state.gameMap.sectors.first().single().type.finishEventName)
    }

    @Test
    fun testEventFilters() {
        assumeTrue(GameMap.SECTOR_COUNT >= GameMap.MIN_SECTORS_FOR_ACTS)
        val map = maps().first()

        // Find a TYPE_ sector in each act
        val actOne = (1 until GameMap.SECTOR_COUNT / 2 - 1).first { c -> map.sectors[c].any { it.type.name.startsWith("TYPE_") } }
        val actTwo = (GameMap.SECTOR_COUNT / 2 until GameMap.SECTOR_COUNT - 1).first { c -> map.sectors[c].any { it.type.name.startsWith("TYPE_") } }
        fun eventsIn(column: Int, size: Int): List<String> {
            val sector = map.sectors[column].first { it.type.name.startsWith("TYPE_") }
            return map.eventsFor(sector, size).map { it.name }
        }

        assertEquals(listOf("ANY_EVENT"), eventsIn(actOne, 1))
        assertEquals(listOf("ANY_EVENT", "BIG_EVENT"), eventsIn(actOne, 2))
        assertEquals(listOf("ANY_EVENT", "ACT_TWO_EVENT"), eventsIn(actTwo, 1))
        assertEquals(listOf("ANY_EVENT", "BIG_EVENT", "ACT_TWO_EVENT"), eventsIn(actTwo, 2))
    }

    @Test
    fun testNoActsWithoutAMidpoint() {
        // Without a midpoint sector type, the act 2 sectors never appear
        val noMidpoint = InGameTest(
            sectorData = SECTOR_DATA.replace("""xftlMidpoint="true"""", ""),
            extraEvents = listOf(EVENT_XML)
        )
        for (map in maps(noMidpoint)) {
            assertTrue(map.sectors.flatten().none { it.type.name == "ACT_TWO" })
            assertEquals(1, map.actOf(GameMap.SECTOR_COUNT - 2))
            assertEquals(1, map.environmentIntensity(map.sectors[GameMap.SECTOR_COUNT - 2].first()))
        }
    }

    companion object {
        @Language("XML")
        private val SECTOR_DATA = """
<FTL>
    <sectorDescription name="CIVILIAN_SECTOR" minSector="0" unique="false">
        <nameList><name>Civilian</name></nameList>
        <trackList/>
    </sectorDescription>
    <sectorDescription name="FINAL" minSector="0" unique="false">
        <nameList><name>Last stand</name></nameList>
        <trackList/>
    </sectorDescription>
    ${(1..4).joinToString("\n") { """
    <sectorDescription name="TYPE_$it" minSector="0" unique="false">
        <nameList><name>Type $it</name></nameList>
        <trackList/>
        <event name="ANY_EVENT" min="1" max="1"/>
        <event name="BIG_EVENT" min="1" max="1" xftlMinShipSize="2"/>
        <event name="ACT_TWO_EVENT" min="1" max="1" xftlAct="2"/>
    </sectorDescription>""" }}
    <sectorDescription name="ACT_TWO" minSector="0" unique="false" xftlAct="2">
        <nameList><name>Act two</name></nameList>
        <trackList/>
    </sectorDescription>
    <sectorDescription name="MIDPOINT" minSector="0" unique="true" xftlMidpoint="true">
        <nameList><name>Midpoint</name></nameList>
        <trackList/>
        <xftlFinishEvent>MIDPOINT_EXIT</xftlFinishEvent>
    </sectorDescription>

    ${listOf("CIVILIAN", "HOSTILE", "NEBULA").joinToString("\n") { """
    <sectorType name="$it">
        <sector>CIVILIAN_SECTOR</sector>
        <sector>TYPE_1</sector>
        <sector>TYPE_2</sector>
        <sector>TYPE_3</sector>
        <sector>TYPE_4</sector>
        <sector>ACT_TWO</sector>
    </sectorType>""" }}
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <event name="MIDPOINT_EXIT"/>
    <event name="ANY_EVENT"/>
    <event name="BIG_EVENT"/>
    <event name="ACT_TWO_EVENT"/>
</FTL>
"""
    }
}
