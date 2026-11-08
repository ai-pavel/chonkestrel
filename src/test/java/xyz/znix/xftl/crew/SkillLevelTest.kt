package xyz.znix.xftl.crew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.systems.Engines
import xyz.znix.xftl.systems.Piloting
import xyz.znix.xftl.systems.Shields
import xyz.znix.xftl.systems.Weapons

/**
 * XFTL's six skill levels (vanilla has three), and how long they take.
 */
class SkillLevelTest {
    @Test
    fun testLevelsFromProgress() {
        assertEquals(SkillLevel.BASE, SkillLevel.of(0f))
        assertEquals(SkillLevel(1), SkillLevel.of(SkillLevel.STEP))
        // An older save's vanilla green (half way) is the third level
        assertEquals(SkillLevel(2), SkillLevel.of(0.5f))
        assertEquals(SkillLevel.MAX, SkillLevel.of(1f))
        for (level in 0..SkillLevel.MAX_LEVEL)
            assertEquals(SkillLevel(level), SkillLevel.of(SkillLevel.progressAt(level)))
    }

    @Test
    fun testEveryLevelGivesMore() {
        for (bonuses in listOf(
            Piloting.SKILL_BONUSES, Engines.SKILL_BONUSES, Shields.SKILL_BONUSES, Weapons.SKILL_BONUSES,
            LivingCrew.REPAIR_SKILL_BONUS, LivingCrew.COMBAT_SKILL_BONUS
        )) {
            assertEquals(SkillLevel.COUNT, bonuses.size)
            assertTrue(bonuses.zipWithNext().all { (a, b) -> b > a })
        }
    }

    @Test
    fun testItTakesLongerThanVanilla() {
        for (skill in Skill.entries) {
            // Vanilla's two colour changes, twice over
            val actions = Math.round(1f / skill.amountPerAction)
            assertEquals(skill.actionsPerLevel * 2 * SkillLevel.TIME_FACTOR.toInt(), actions)
        }
    }
}
