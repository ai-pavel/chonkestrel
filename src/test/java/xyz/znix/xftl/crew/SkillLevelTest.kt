package xyz.znix.xftl.crew

import org.jdom2.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.systems.Engines
import xyz.znix.xftl.systems.Manning
import xyz.znix.xftl.systems.Piloting
import xyz.znix.xftl.systems.Shields
import xyz.znix.xftl.systems.Weapons
import xyz.znix.xftl.testkit.InGameTest

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
            LivingCrew.REPAIR_SKILL_BONUS, LivingCrew.COMBAT_SKILL_BONUS, Drones.SKILL_BONUSES, Manning.SKILL_BONUSES
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

    @Test
    fun testXftlsSkillsAreLastAndShownAfterTheWeapons() {
        // The game records keep the levels in the enum's order, so the
        // older ones stay where they were
        assertEquals(
            listOf(
                Skill.PILOTING, Skill.ENGINES, Skill.SHIELDS, Skill.WEAPONS, Skill.REPAIRS, Skill.COMBAT,
                Skill.DRONES, Skill.LIFE_SUPPORT
            ),
            Skill.entries
        )
        assertEquals(Skill.entries.toSet(), Skill.DISPLAY_ORDER.toSet())
        assertEquals(Skill.WEAPONS.displayIndex + 1, Skill.DRONES.displayIndex)
        assertEquals(Skill.DRONES.displayIndex + 1, Skill.LIFE_SUPPORT.displayIndex)
    }

    @Test
    fun testACrewmemberSavedWithoutTheDronesSkillLoads() {
        // A save from before the Drones skill: their other skills load,
        // and they haven't learnt it
        val kit = InGameTest()
        val elem = Element("crew")
        elem.setAttribute("race", "human")
        elem.setAttribute("selectedName", "Old Hand")
        elem.setAttribute("colour", "0")
        elem.addContent(Element("skill").setAttribute("id", "WEAPONS").setAttribute("value", "0.5"))
        elem.addContent(Element("skill").setAttribute("id", "COMBAT").setAttribute("value", "1.0"))

        val info = LivingCrewInfo.loadFromXML(elem, kit.state)
        assertEquals(0.5f, info.skills.getValue(Skill.WEAPONS), 0.0001f)
        assertEquals(1f, info.skills.getValue(Skill.COMBAT), 0.0001f)
        assertEquals(0f, info.skills.getValue(Skill.DRONES), 0.0001f)
        assertEquals(SkillLevel.BASE, info.getSkillLevel(Skill.DRONES))
        assertEquals(SkillLevel.BASE, info.getSkillLevel(Skill.LIFE_SUPPORT))

        // and once learnt, it's saved and loaded with the rest
        info.skills[Skill.DRONES] = SkillLevel.progressAt(3)
        val saved = Element("crew")
        info.saveToXML(saved)
        val loaded = LivingCrewInfo.loadFromXML(saved, kit.state)
        assertEquals(SkillLevel(3), loaded.getSkillLevel(Skill.DRONES))
        assertEquals(0.5f, loaded.skills.getValue(Skill.WEAPONS), 0.0001f)
    }

    @Test
    fun testOldAndUnknownSkillNamesLoad() {
        // The Life Support skill's old name loads as it, and a skill this
        // game doesn't know is left out, rather than failing the save
        val kit = InGameTest()
        val elem = Element("crew")
        elem.setAttribute("race", "human")
        elem.setAttribute("selectedName", "Old Hand")
        elem.setAttribute("colour", "0")
        elem.addContent(Element("skill").setAttribute("id", "OPS").setAttribute("value", "0.4"))
        elem.addContent(Element("skill").setAttribute("id", "NOT_A_SKILL").setAttribute("value", "1.0"))
        elem.addContent(Element("skill").setAttribute("id", "COMBAT").setAttribute("value", "1.0"))

        val info = LivingCrewInfo.loadFromXML(elem, kit.state)
        assertEquals(0.4f, info.skills.getValue(Skill.LIFE_SUPPORT), 0.0001f)
        assertEquals(1f, info.skills.getValue(Skill.COMBAT), 0.0001f)
    }
}
