package xyz.znix.xftl.shipgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.shipgen.ShipGenerator.Companion.PAIR_HYBRID_CHANCE
import xyz.znix.xftl.shipgen.ShipGenerator.Companion.VANILLA_CHANCE_END
import xyz.znix.xftl.shipgen.ShipGenerator.Companion.VANILLA_CHANCE_START
import xyz.znix.xftl.shipgen.ShipGenerator.Companion.hybridSectorShip
import xyz.znix.xftl.shipgen.ShipGenerator.Companion.hybridSectorVanillaChance
import xyz.znix.xftl.shipgen.ShipGenerator.HybridSectorShip

/**
 * What the enemy ships are in a hybrid sector (see doc/game/sectors.md): mostly
 * the sector's pair's hybrids, some other hybrids, and fewer and fewer
 * vanilla ships through act 2.
 */
class HybridSectorShipTest {
    // The share of each kind of ship, over evenly spread rolls
    private fun shares(progress: Float): Map<HybridSectorShip, Float> {
        val rolls = 10_000
        return (0 until rolls).map { hybridSectorShip(progress, (it + 0.5f) / rolls) }
            .groupingBy { it }.eachCount().mapValues { it.value.toFloat() / rolls }
    }

    @Test
    fun testThePairsHybridsAreMostOfThem() {
        for (progress in listOf(0f, 0.5f, 1f)) {
            val shares = shares(progress)
            assertEquals(PAIR_HYBRID_CHANCE, shares.getValue(HybridSectorShip.PAIR), 0.001f)
            // More than everything else put together
            assertTrue(shares.getValue(HybridSectorShip.PAIR) > 0.5f)
        }
    }

    @Test
    fun testThereAreAlwaysSomeOthers() {
        for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val shares = shares(progress)
            assertTrue("$progress: $shares", shares.getValue(HybridSectorShip.VANILLA) > 0f)
            assertTrue("$progress: $shares", shares.getValue(HybridSectorShip.OTHER_HYBRID) > 0f)
        }
    }

    @Test
    fun testVanillaShipsGetRarerThroughActTwo() {
        assertEquals(VANILLA_CHANCE_START, hybridSectorVanillaChance(0f), 0.0001f)
        assertEquals(VANILLA_CHANCE_END, hybridSectorVanillaChance(1f), 0.0001f)

        // Every sector of act 2 has fewer vanilla ships than the one before,
        // and more other hybrids in their place
        val steps = (0..6).map { it / 6f }
        for ((before, after) in steps.zipWithNext()) {
            assertTrue(hybridSectorVanillaChance(after) < hybridSectorVanillaChance(before))
            val sharesBefore = shares(before)
            val sharesAfter = shares(after)
            assertTrue(sharesAfter.getValue(HybridSectorShip.VANILLA) < sharesBefore.getValue(HybridSectorShip.VANILLA))
            assertTrue(sharesAfter.getValue(HybridSectorShip.OTHER_HYBRID) > sharesBefore.getValue(HybridSectorShip.OTHER_HYBRID))
        }

        // Out of range, it's as at the start or the end
        assertEquals(VANILLA_CHANCE_START, hybridSectorVanillaChance(-1f), 0.0001f)
        assertEquals(VANILLA_CHANCE_END, hybridSectorVanillaChance(2f), 0.0001f)
    }

    @Test
    fun testTheRollsSplitInOrder() {
        assertEquals(HybridSectorShip.PAIR, hybridSectorShip(0f, 0f))
        assertEquals(HybridSectorShip.PAIR, hybridSectorShip(0f, PAIR_HYBRID_CHANCE - 0.001f))
        assertEquals(HybridSectorShip.VANILLA, hybridSectorShip(0f, PAIR_HYBRID_CHANCE + 0.001f))
        assertEquals(HybridSectorShip.OTHER_HYBRID, hybridSectorShip(0f, 0.999f))
        assertEquals(HybridSectorShip.OTHER_HYBRID, hybridSectorShip(1f, PAIR_HYBRID_CHANCE + VANILLA_CHANCE_END + 0.001f))
    }
}
