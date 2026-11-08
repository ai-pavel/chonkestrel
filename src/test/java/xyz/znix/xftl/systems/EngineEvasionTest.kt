package xyz.znix.xftl.systems

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Ship
import xyz.znix.xftl.testkit.InGameTest

class EngineEvasionTest {
    private fun createKit(ship: String) = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = ship)

    private fun Ship.setEnginePower(vararg power: Int) {
        for ((engines, level) in engineSystems.zip(power.toList())) {
            engines.energyLevels = 8
            while (engines.powerSelected > level)
                engines.decreasePower()
            while (engines.powerSelected < level)
                engines.increasePower()
            assertEquals(level, engines.powerSelected)
        }
    }

    // Automated ships get a manning bonus too, so compare the evasion
    // changes rather than the exact numbers.
    @Test
    fun testVanillaEvasion() {
        val player = createKit("TEST_SHIP_ONE_ENGINE").player
        assertEquals(1, player.engineSystems.size)

        player.setEnginePower(1)
        val base = player.evasion
        for (level in 1..8) {
            player.setEnginePower(level)
            assertEquals(Engines.EVASIONS[level - 1] - Engines.EVASIONS[0], player.evasion - base)
        }

        // Turning the engines off loses all the evasion
        player.setEnginePower(0)
        assertEquals(0, player.evasion)
    }

    @Test
    fun testBigShipsNeedTwoEngines() {
        val vanilla = createKit("TEST_SHIP_ONE_ENGINE").player
        val player = createKit("TEST_SHIP_TWO_ENGINES").player
        assertEquals(2, player.engineSystems.size)

        fun vanillaEvasion(level: Int): Int {
            vanilla.setEnginePower(level)
            return vanilla.evasion
        }

        // Both engines at a level give the evasion one engine at that level
        // does on a vanilla ship, but bigger ships are easier to hit, so
        // they get 2/3 of it.
        player.setEnginePower(4, 4)
        assertEquals(vanillaEvasion(4) * 2 / 3, player.evasion)

        // Every bar of power counts, in either engine
        player.setEnginePower(6, 2)
        assertEquals(vanillaEvasion(4) * 2 / 3, player.evasion)

        // With one engine off, the other's power still counts (though only
        // the running one's crew give their share of the manning bonus),
        // and the ship can still charge its FTL drive
        player.setEnginePower(4, 4)
        val both = player.evasion
        player.setEnginePower(8, 0)
        assertTrue(player.evasion > vanillaEvasion(1) * 2 / 3)
        assertTrue(player.evasion <= both)
        assertTrue(player.areEnginesRunning)
    }

    @Test
    fun testFractionalLevels() {
        assertEquals(10f, Engines.evasionAt(2f), 0.001f)
        assertEquals(12.5f, Engines.evasionAt(2.5f), 0.001f)
        assertEquals(2.5f, Engines.evasionAt(0.5f), 0.001f)
        assertEquals(0f, Engines.evasionAt(0f), 0.001f)
        assertEquals(35f, Engines.evasionAt(10f), 0.001f)
        assertEquals(1.26f, Engines.chargeRateAt(2f), 0.001f)
    }

    companion object {
        // These have no crew, so they're automated and always piloted
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_ONE_ENGINE" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="0" class="test_crew"/>

        <systemList>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" room="2"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_SHIP_TWO_ENGINES" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="0" class="test_crew"/>
        <shipSize>2</shipSize>

        <systemList>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" room="2"/>
            <engines power="1" room="3"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""
    }
}
