package xyz.znix.xftl.systems

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

class OxygenTest {
    private fun createKit() = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_TWO_OXYGEN")

    @Test
    fun testTwoSystemsRefillAtTheirAverage() {
        val kit = createKit()
        val player = kit.player
        val (first, second) = player.oxygenSystems

        assertEquals(2, player.oxygenSystems.size)
        assertEquals(first, player.oxygen)

        // Both at the same power refill as fast as one would alone
        first.energyLevels = 2
        second.energyLevels = 2
        player.updateAvailablePower()
        assertEquals(first.refillRate, player.oxygenRefillRate, 0.0001f)

        // With one off, it's half that
        second.energyLevels = 0
        player.updateAvailablePower()
        assertEquals(first.refillRate / 2, player.oxygenRefillRate, 0.0001f)
    }

    @Test
    fun testOneSystemAtLevelOneOnlyKeepsTheAirIn() {
        val kit = createKit()
        val player = kit.player
        val (first, second) = player.oxygenSystems

        first.energyLevels = 1
        second.energyLevels = 0
        player.updateAvailablePower()
        assertEquals(Oxygen.ROOM_DRAIN_RATE, player.oxygenRefillRate, 0.0001f)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_TWO_OXYGEN" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <oxygen power="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <oxygen power="1" room="3"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""
    }
}
