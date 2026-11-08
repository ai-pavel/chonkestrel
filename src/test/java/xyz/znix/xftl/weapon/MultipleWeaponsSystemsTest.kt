package xyz.znix.xftl.weapon

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

class MultipleWeaponsSystemsTest {
    private fun createKit(): InGameTest {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_TWIN_WEAPONS")
        kit.givePlayerBP("TEST_LASER")
        kit.givePlayerBP("TEST_LASER")
        kit.upgradeAll()
        kit.powerUpAll()
        return kit
    }

    @Test
    fun testHardpointsAreSplit() {
        val kit = createKit()
        val (first, second) = kit.player.weaponSystems

        assertEquals(2, kit.player.weaponSystems.size)
        assertSame(first, kit.player.weapons)
        assertEquals(0..0, first.hardpointIndices)
        assertEquals(1..1, second.hardpointIndices)

        assertSame(first, kit.player.weaponsFor(0))
        assertSame(second, kit.player.weaponsFor(1))
    }

    @Test
    fun testSystemsPowerTheirOwnWeapons() {
        val kit = createKit()
        val (first, second) = kit.player.weaponSystems
        val firstLaser = kit.player.hardpoints[0].weapon!!
        val secondLaser = kit.player.hardpoints[1].weapon!!

        assertEquals(true, firstLaser.isPowered)
        assertEquals(true, secondLaser.isPowered)
        assertEquals(1, first.powerSelected)
        assertEquals(1, second.powerSelected)

        // Damaging the second system only turns off its own weapon
        second.dealDamage(second.energyLevels, 0)
        kit.updateSingle(0f)

        assertEquals(true, firstLaser.isPowered)
        assertEquals(false, secondLaser.isPowered)
        assertEquals(1, first.powerSelected)
        assertEquals(0, second.powerSelected)
    }

    @Test
    fun testWeaponsDoNotMoveBetweenSystems() {
        val kit = createKit()
        val secondLaser = kit.player.hardpoints[1].weapon!!

        // Removing the first weapon would normally pack the second one
        // into the first slot, but that'd move it to the other system.
        kit.player.hardpoints[0].weapon = null
        kit.player.cargoUpdated()

        assertEquals(null, kit.player.hardpoints[0].weapon)
        assertSame(secondLaser, kit.player.hardpoints[1].weapon)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_TWIN_WEAPONS" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <weaponSlots>2</weaponSlots>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <weapons power="1" max="1" room="3"/>
            <weapons power="1" max="1" room="5"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""
    }
}
