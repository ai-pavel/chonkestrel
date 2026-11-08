package xyz.znix.xftl.weapon

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

class WeaponSlotTest {
    @Test
    fun testWeaponsCanSkipSlots() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_SKIPPED_SLOTS")
        val hardpoints = kit.player.hardpoints

        // The first slot's skipped, so the weapon goes in the second
        // weapons system, which controls the second slot.
        assertNull(hardpoints[0].weapon)
        assertEquals("TEST_BEAM", hardpoints[1].weapon?.type?.name)
        assertEquals(kit.player.weaponSystems[1], kit.player.weaponsFor(1))
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_SKIPPED_SLOTS" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>

        <systemList>
            <pilot power="1" max="1" room="1"/>
            <weapons power="2" room="3"/>
            <weapons power="2" room="4"/>
        </systemList>

        <weaponList>
            <weapon name="TEST_BEAM" slot="2"/>
        </weaponList>
    </shipBlueprint>
</FTL>
"""
    }
}
