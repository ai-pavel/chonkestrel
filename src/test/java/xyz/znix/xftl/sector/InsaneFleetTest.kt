package xyz.znix.xftl.sector

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.testkit.InGameTest

/**
 * The Rebel fleet on insane (see Difficulty.fleetHeadStartJumps and
 * fleetExtraStepEvery): it starts each sector a jump closer, and every
 * fourth jump it takes an extra step.
 */
class InsaneFleetTest {
    private val InGameTest.sector: Sector get() = state.currentBeacon.sector

    // How far the fleet advances on each of [jumps] jumps (from the start beacon)
    private fun advances(kit: InGameTest, jumps: Int): List<Int> = (1..jumps).map {
        val before = kit.sector.dangerZoneCentre.x
        kit.state.advanceFleet()
        kit.sector.dangerZoneCentre.x - before
    }

    // One step of the fleet, from the start beacon
    private fun step(kit: InGameTest): Int = kit.sector.getFleetAdvanceFor(kit.state.currentBeacon, kit.player.type.shipSize)

    @Test
    fun itStartsAJumpCloser() {
        val normal = InGameTest()
        val insane = InGameTest(difficulty = Difficulty.INSANE)
        assertTrue(normal.sector.hasFleet)

        val jump = (Sector.DANGER_ZONE_ADVANCE * insane.sector.type.fleetSpeed).toInt()
        assertEquals(normal.sector.dangerZoneCentre.x + jump, insane.sector.dangerZoneCentre.x)
    }

    @Test
    fun everyFourthJumpItTakesAnExtraStep() {
        val kit = InGameTest(difficulty = Difficulty.INSANE)
        val step = step(kit)
        assertFalse(kit.sector.isFleetExtraStep(Difficulty.INSANE))
        assertEquals(listOf(step, step, step, 2 * step, step, step, step, 2 * step), advances(kit, 8))
        assertEquals(8, kit.sector.fleetJumps)
    }

    @Test
    fun notOnTheOtherDifficulties() {
        for (difficulty in listOf(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
            val kit = InGameTest(difficulty = difficulty)
            val step = step(kit)
            assertEquals(List(8) { step }, advances(kit, 8))
        }
    }

    @Test
    fun theJumpsAreSaved() {
        val kit = InGameTest(difficulty = Difficulty.INSANE)
        val step = step(kit)
        advances(kit, 3)

        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        val sector = loaded.currentBeacon.sector
        assertEquals(3, sector.fleetJumps)
        assertTrue(sector.isFleetExtraStep(loaded.difficulty))
        val before = sector.dangerZoneCentre.x
        loaded.advanceFleet()
        assertEquals(before + 2 * step, sector.dangerZoneCentre.x)
    }

    /**
     * In the last stand, where the fleet takes two beacons a jump, it takes
     * one more on the first jump (as it starts a jump closer) and on each
     * fourth jump.
     */
    @Test
    fun theLastStandsFleetTakesMore() {
        val kit = InGameTest(extraEvents = listOf(LAST_STAND_EVENT_XML), difficulty = Difficulty.INSANE)
        val info = kit.state.gameMap.sectors.last().single()
        val sector = kit.state.gameMap.generateSector(info, kit.state)
        assertTrue(sector.isLastStand)
        // (arriving there needs the UI, so the player's put there directly)
        val current = kit.state.javaClass.getDeclaredField("currentBeacon")
        current.isAccessible = true
        current.set(kit.state, sector.startBeacon)

        val taken = (1..4).map {
            val before = sector.beacons.count { it.isOvertaken }
            kit.state.advanceFleet()
            sector.beacons.count { it.isOvertaken } - before
        }
        assertEquals(listOf(3, 2, 2, 3), taken)
    }

    companion object {
        @Language("XML")
        private const val LAST_STAND_EVENT_XML = """
<FTL>
    <event name="LAST_STAND_START"/>
</FTL>
"""
    }
}
