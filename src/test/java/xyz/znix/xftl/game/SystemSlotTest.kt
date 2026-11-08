package xyz.znix.xftl.game

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.znix.xftl.systems.SystemBlueprint
import xyz.znix.xftl.testkit.InGameTest

class SystemSlotTest {
    private fun createKit(ship: String) = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = ship)

    private fun InGameTest.system(name: String) = state.blueprintManager[name] as SystemBlueprint

    @Test
    fun testSecondSlotIsUsed() {
        val kit = createKit("TEST_SHIP_TWO_SHIELDS")
        val shields = kit.system("shields")

        // The first shields system is installed, so the second slot is free
        assertEquals(4, kit.player.findFreeSystemSlot(shields)?.room?.id)

        // And once that's installed too, there's nowhere left
        val slot = kit.player.findFreeSystemSlot(shields)!!
        slot.room.setSystem(slot)
        assertNull(kit.player.findFreeSystemSlot(shields))
        assertEquals(2, kit.player.shieldSystems.size)
    }

    @Test
    fun testEmptyRoomsArePreferred() {
        val kit = createKit("TEST_SHIP_CLONEBAY")
        val clonebay = kit.system("clonebay")

        // The clonebay goes in its own room, rather than replacing the medbay
        assertEquals(4, kit.player.findFreeSystemSlot(clonebay)?.room?.id)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_TWO_SHIELDS" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <shields power="2" room="3" start="true"/>
            <shields power="2" room="4" start="false"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_SHIP_CLONEBAY" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <medbay power="1" room="3" start="true"/>
            <clonebay power="1" room="3" start="false"/>
            <clonebay power="1" room="4" start="false"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="shields">
        <type>shields</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <systemBlueprint name="clonebay">
        <type>clonebay</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
</FTL>
"""
    }
}
