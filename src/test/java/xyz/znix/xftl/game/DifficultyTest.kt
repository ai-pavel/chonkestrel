package xyz.znix.xftl.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Ship

/**
 * The four difficulties' values (see Difficulty and doc/game/difficulty.md):
 * vanilla's three as they were, and insane harder than hard in every way.
 */
class DifficultyTest {
    @Test
    fun theOrder() {
        // Saved by name, so the new one only goes on the end
        assertEquals(listOf("EASY", "NORMAL", "HARD", "INSANE"), Difficulty.entries.map { it.name })
    }

    @Test
    fun theValues() {
        fun <T> row(expected: List<T>, value: (Difficulty) -> T) =
            assertEquals(expected, Difficulty.entries.map(value))

        row(listOf(-1, 0, 0, 1)) { it.sectorOffset }
        row(listOf(1, 0, -1, -2)) { it.rewardSectorOffset }
        row(listOf(-1, 0, 1, 2)) { it.mapSectorOffset }
        row(listOf(-1, 0, 1, 2)) { it.boardingSectorOffset }
        row(listOf(30, 10, 10, 10)) { it.startingScrapPerSize }
        row(listOf(10, 20, 30, 40)) { it.optionalSystemBaseChance }
        row(listOf(0, 0, 1, 2)) { it.extraGeneralPower }
        row(listOf(false, false, true, true)) { it.cheapOptionalSystems }
        row(listOf(1, 2, 2, 2)) { it.systemPowerOffsetMax }
        row(listOf(listOf(12, 16, 20), listOf(8, 12, 18), listOf(6, 10, 15), listOf(5, 8, 12))) { it.doorHealths }
        row(listOf(0.5f, 1f, 1.5f, 2f)) { it.scoreMultiplier }
        row(listOf(0, 0, 0, 1)) { it.fleetHeadStartJumps }
        row(listOf(0, 0, 0, 4)) { it.fleetExtraStepEvery }
        row(listOf(false, false, false, true)) { it.enemiesTargetKeySystems }
        row(listOf("easy_button", "normal_button", "hard_button", "xftl_insane_button")) { it.startButtonKey }
        row(listOf("ach_easy", "ach_normal", "ach_hard", "xftl_ach_insane")) { it.achievementUnlockKey }
    }

    @Test
    fun insaneDoesWhatHardDoes() {
        // (the crew's manning, the last stand's exit, the win on hard achievement)
        assertTrue(Difficulty.INSANE.atLeast(Difficulty.HARD))
        assertTrue(Difficulty.HARD.atLeast(Difficulty.HARD))
        assertFalse(Difficulty.NORMAL.atLeast(Difficulty.HARD))
        assertTrue(Difficulty.NORMAL.atLeast(Difficulty.NORMAL))
        assertFalse(Difficulty.EASY.atLeast(Difficulty.NORMAL))
    }

    @Test
    fun theStartingScrap() {
        assertEquals(60, Ship.startingScrap(Difficulty.EASY, 2))
        assertEquals(20, Ship.startingScrap(Difficulty.HARD, 2))
        assertEquals(20, Ship.startingScrap(Difficulty.INSANE, 2))
    }
}
