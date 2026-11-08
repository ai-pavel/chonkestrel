package xyz.znix.xftl.sector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest
import kotlin.random.Random

class SectorCountTest {
    @Test
    fun testMapHasAllTheSectors() {
        val kit = InGameTest()
        val sectors = kit.state.gameMap.sectors

        assertEquals(GameMap.SECTOR_COUNT, sectors.size)
        assertEquals(GameMap.SECTOR_COUNT, kit.state.gameMap.sectorCount)

        // One starting sector, and one last stand
        assertEquals(1, sectors.first().size)
        assertEquals(1, sectors.last().size)
        assertEquals("FINAL", sectors.last().single().type.name)

        // Every sector except the last stand leads somewhere
        for (column in sectors.dropLast(1)) {
            for (sector in column) {
                assertTrue(sector.nextSectors.isNotEmpty())
            }
        }
    }

    @Test
    fun testDifficultyIsSpreadOut() {
        val kit = InGameTest()
        val difficulties = kit.state.gameMap.sectors.map { it.first().difficultySectorNumber }

        assertEquals(0, difficulties.first())
        assertEquals(GameMap.VANILLA_SECTOR_COUNT - 1, difficulties.last())

        // The difficulty never goes down
        assertEquals(difficulties.sorted(), difficulties)
    }

    @Test
    fun testColumnsAreLinkedProperly() {
        val kit = InGameTest()
        val content = kit.state.content

        // Try lots of maps, as they're random
        for (seed in 0 until 200) {
            val map = GameMap(content.datafile, kit.state.eventManager, true, Random(seed))

            for ((columnNumber, column) in map.sectors.withIndex()) {
                assertTrue(column.size in 1..GameMap.MAX_SECTORS_PER_COLUMN)

                // Each sector leads to one or two others (vanilla saves only
                // store two), apart from the last stand.
                if (columnNumber == map.sectors.size - 1)
                    continue

                val next = map.sectors[columnNumber + 1]
                for (sector in column) {
                    assertTrue(sector.nextSectors.size in 1..2)
                    assertTrue(sector.nextSectors.all { it in next })
                }

                // Every sector in the next column is reachable
                for (sector in next) {
                    assertTrue(column.any { sector in it.nextSectors })
                }

                // The links don't cross over each other
                for ((i, a) in column.withIndex()) {
                    for (b in column.drop(i + 1)) {
                        assertTrue(a.nextSectors.maxOf { it.columnIndex } <= b.nextSectors.minOf { it.columnIndex })
                    }
                }
            }
        }
    }

    @Test
    fun testVanillaLengthIsUnchanged() {
        for (i in 0 until GameMap.VANILLA_SECTOR_COUNT) {
            assertEquals(i, GameMap.vanillaSectorNumber(i, GameMap.VANILLA_SECTOR_COUNT))
            assertEquals(i.toFloat(), GameMap.vanillaSectorProgress(i, GameMap.VANILLA_SECTOR_COUNT), 0.0001f)
        }
    }

    @Test
    fun testRewardsGrowEverySector() {
        // In a sixteen-sector game, the rewards' sector number grows with
        // every sector (rather than every other one, as the difficulty's
        // does), from the first vanilla sector to the last, and rounds down
        // to the difficulty's
        val count = 16
        var last = -1f
        for (i in 0 until count) {
            val progress = GameMap.vanillaSectorProgress(i, count)
            assertTrue("sector $i: $progress after $last", progress > last)
            assertEquals(GameMap.vanillaSectorNumber(i, count), progress.toInt())
            last = progress
        }
        assertEquals(0f, GameMap.vanillaSectorProgress(0, count), 0.0001f)
        assertEquals((GameMap.VANILLA_SECTOR_COUNT - 1).toFloat(), GameMap.vanillaSectorProgress(count - 1, count), 0.0001f)
    }
}
