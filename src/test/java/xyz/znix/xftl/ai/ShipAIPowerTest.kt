package xyz.znix.xftl.ai

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.testkit.InGameTest

/**
 * Enemy ships have the reactor for all their systems, and when they're
 * short of it (as in an ion storm), their weapons are powered before
 * everything but the shields.
 */
class ShipAIPowerTest {
    private fun createKit(): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraEvents = listOf(EVENT_XML),
            playerShip = "PLAYER_SHIP_AI_POWER_TEST"
        )
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_AI_POWER_ENEMY"), Difficulty.NORMAL, 0, 1)
        return kit
    }

    @Test
    fun testEnemiesCanPowerEverything() {
        val kit = createKit()
        val enemy = kit.state.enemy!!
        assertTrue(enemy.purchasedReactorPower >= enemy.mainSystems.sumOf { it.energyLevels })
    }

    @Test
    fun testTheWeaponsComeBeforeTheEngines() {
        val kit = createKit()
        val enemy = kit.state.enemy!!
        enemy.hardpoints.forEach { it.weapon = null }
        enemy.addBlueprint(kit.state.blueprintManager["TEST_AI_POWER_LASER"] as Blueprint, true)
        val weapon = enemy.hardpoints[0].weapon!!
        enemy.weaponSystems.forEach { it.energyLevels = 2 }
        enemy.engines!!.energyLevels = 2

        // Only enough for the shields and the weapon: the engines, powered
        // first, give theirs up for it
        enemy.engines!!.increasePower()
        enemy.purchasedReactorPower = 4
        enemy.updateAvailablePower()
        repeat(5) { kit.state.updateGameState(0.05f) }

        assertTrue(weapon.isPowered)
        assertEquals(2, enemy.shieldSystems.single().powerSelected)
        assertEquals(0, enemy.engines!!.powerSelected)
    }

    @Test
    fun testADestroyedShipDoesntEatAsteroids() {
        val kit = createKit()
        kit.givePlayerBP("XFTL_HIVE_CORE")
        assertTrue(kit.player.eatsAsteroid())

        // It would come back to life
        kit.player.health = 0
        assertFalse(kit.player.eatsAsteroid())
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <systemBlueprint name="shields">
        <type>shields</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <weaponBlueprint name="TEST_AI_POWER_LASER">
        <type>LASER</type>
        <damage>1</damage>
        <shots>1</shots>
        <cooldown>10</cooldown>
        <power>2</power>
        <cost>10</cost>
        <rarity>0</rarity>
        <weaponArt>generic_test_weapon</weaponArt>
        <iconImage>dummy_anim</iconImage>
    </weaponBlueprint>

    <augBlueprint name="XFTL_HIVE_CORE">
        <value>1</value>
        <cost>10</cost>
        <rarity>0</rarity>
        <stackable>false</stackable>
    </augBlueprint>

    <shipBlueprint name="PLAYER_SHIP_AI_POWER_TEST" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_AI_POWER_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="2"/>
        <crewCount amount="2" max="2" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <shields power="2" max="8" room="0"/>
            <engines power="2" max="8" room="2"/>
            <weapons power="2" max="8" room="3"/>
        </systemList>
        <weaponList count="1" missiles="0">
            <weapon name="TEST_AI_POWER_LASER"/>
        </weaponList>
    </shipBlueprint>
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <ship name="TEST_AI_POWER_ENEMY" blueprint="TEST_AI_POWER_ENEMY_SHIP"/>
</FTL>
"""
    }
}
