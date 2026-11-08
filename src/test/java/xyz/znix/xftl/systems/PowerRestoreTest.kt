package xyz.znix.xftl.systems

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

/**
 * A system that loses power to damage or an ion gets it back once it's
 * repaired (or the ion wears off), as far as the reactor allows, even if
 * the player never set its power themselves; the weapons and drones
 * systems turn back on the items they had on.
 */
class PowerRestoreTest {
    private fun createKit() = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_POWER_TEST")

    @Test
    fun testARepairedSystemGetsItsPowerBack() {
        val kit = createKit()
        val engines = kit.player.engines!!
        kit.player.purchasedReactorPower = 10
        kit.player.updateAvailablePower()
        // (the power it started with, which the player never set)
        repeat(3) { engines.increasePower() }
        engines.targetPower = 0
        assertEquals(3, engines.powerSelected)

        engines.dealDamage(2, 0)
        kit.player.update(0.1f)
        assertEquals(1, engines.powerSelected)

        // Each bar repaired comes back on
        engines.repair(1f)
        assertEquals(2, engines.powerSelected)
        engines.repair(1f)
        assertEquals(3, engines.powerSelected)
    }

    @Test
    fun testTheRestComesBackOnceThereIsPower() {
        val kit = createKit()
        val engines = kit.player.engines!!
        kit.player.purchasedReactorPower = 3
        kit.player.updateAvailablePower()
        repeat(3) { engines.increasePower() }

        engines.dealDamage(2, 0)
        kit.player.update(0.1f)
        assertEquals(1, engines.powerSelected)

        // Short of reactor power while it's repaired, it gets what there is...
        kit.player.purchasedReactorPower = 2
        kit.player.updateAvailablePower()
        engines.repair(1f)
        assertEquals(2, engines.powerSelected)

        // ...and keeps its level, to get the rest with the next repair
        kit.player.purchasedReactorPower = 3
        kit.player.updateAvailablePower()
        engines.repair(1f)
        assertEquals(3, engines.powerSelected)
    }

    @Test
    fun testShieldsKeepWholeLayers() {
        val kit = createKit()
        val shields = kit.player.shieldSystems.single()
        kit.player.purchasedReactorPower = 10
        kit.player.updateAvailablePower()
        repeat(2) { shields.increasePower() }
        assertEquals(4, shields.powerSelected)

        // An ion takes a bar, and the other bar of that layer goes back to
        // the reactor, rather than sitting in the shields doing nothing...
        shields.dealDamage(0, 1)
        kit.player.update(0.1f)
        assertEquals(2, shields.powerSelected)

        // ...and both come back as it wears off
        repeat(300) { kit.player.update(0.1f) }
        assertEquals(4, shields.powerSelected)
    }

    @Test
    fun testWeaponsComeBackOnAfterAnIon() {
        val kit = createKit()
        val weapons = kit.player.weapons!!
        kit.player.purchasedReactorPower = 10
        kit.player.updateAvailablePower()
        val weapon = kit.player.hardpoints[0].weapon!!
        assertTrue(weapons.setWeaponPower(weapon, true))
        assertTrue(weapon.isPowered)

        // An ion takes the weapon's power...
        weapons.dealDamage(0, 1)
        assertFalse(weapon.isPowered)

        // ...and it comes back on as the ion wears off
        repeat(200) { kit.player.update(0.1f) }
        assertTrue(weapon.isPowered)
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

    <shipBlueprint name="PLAYER_SHIP_POWER_TEST" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="10"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <engines power="3" max="8" room="2"/>
            <weapons power="1" max="8" room="3"/>
            <shields power="4" max="8" room="4"/>
        </systemList>
        <weaponList count="1" missiles="0">
            <weapon name="TEST_LASER"/>
        </weaponList>
    </shipBlueprint>
</FTL>
"""
    }
}
