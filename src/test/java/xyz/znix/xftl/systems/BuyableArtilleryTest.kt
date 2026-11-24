package xyz.znix.xftl.systems

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import xyz.znix.xftl.BuyableArtillery
import xyz.znix.xftl.crew.Skill
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew

/**
 * The Artillery Beam the player's ship can buy in its hacking room (see
 * BuyableArtillery) is installed working, at a bar, as a system the ship's
 * blueprint lists is. It used to be installed at none: broken (red), and
 * the upgrades screen crashed.
 */
class BuyableArtilleryTest {
    @Test
    fun testItsBoughtAtABar() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_ARTILLERY_TEST")
        val slot = kit.player.systemSlots.firstOrNull { it.spec is BuyableArtillery }
        assertNotNull(slot)

        // As the store buys it
        slot!!.room.setSystem(slot)
        val artillery = slot.room.system as Artillery
        assertEquals(1, artillery.energyLevels)
        assertFalse(artillery.broken)
    }

    @Test
    fun testMannedItChargesFasterByHalfTheWeaponsSkill() {
        // Someone at its console speeds it up by half what their Weapons
        // skill gives a weapons system (see Artillery.manningBonus)
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_ARTILLERY_TEST")
        kit.powerUpAll()
        val slot = kit.player.systemSlots.first { it.spec is BuyableArtillery }
        slot.room.setSystem(slot)
        val artillery = slot.room.system as Artillery
        kit.powerUpAll()
        assertEquals(0f, artillery.manningBonus, 0.001f)

        val crew = kit.addCrew(kit.player, slot.room)
        var steps = 0
        while (artillery.manningCrew == null && steps++ < 200)
            kit.updateSingle(0.1f)
        assertEquals(crew, artillery.manningCrew)
        val bonus = Weapons.SKILL_BONUSES[crew.getSkillLevel(Skill.WEAPONS).level]
        assertEquals(bonus * Artillery.MANNING_SHARE, artillery.manningBonus, 0.001f)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <systemBlueprint name="hacking">
        <type>hacking</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
    <systemBlueprint name="artillery">
        <type>artillery</type>
        <startPower>2</startPower>
        <maxPower>4</maxPower>
        <cost>150</cost>
        <rarity>4</rarity>
        <upgradeCost><level>30</level><level>50</level><level>80</level></upgradeCost>
    </systemBlueprint>

    <weaponBlueprint name="ARTILLERY_FED">
        <type>BEAM</type>
        <damage>1</damage>
        <length>1</length>
        <cooldown>40</cooldown>
        <power>1</power>
        <weaponArt>generic_test_weapon</weaponArt>
        <image>dummy_anim</image>
    </weaponBlueprint>

    <shipBlueprint name="PLAYER_SHIP_ARTILLERY_TEST" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <hacking power="1" room="2" start="false"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""
    }
}
