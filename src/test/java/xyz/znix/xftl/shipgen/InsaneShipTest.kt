package xyz.znix.xftl.shipgen

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.testkit.InGameTest

/**
 * The enemy ships on insane (see Difficulty): still valid ships, from the
 * first sector to the last, and stronger than on normal, a sector ahead,
 * with more power and more of their optional systems.
 */
class InsaneShipTest {
    private val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), extraEvents = listOf(EVENT_XML))

    // The enemy ship [seed] gives in a (vanilla) sector
    private fun ship(difficulty: Difficulty, sector: Int, seed: Int): Ship {
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_INSANE_ENEMY"), difficulty, sector, seed)
        return kit.state.enemy!!
    }

    @Test
    fun testTheShipsAreValid() {
        for (sector in 0..7) {
            for (seed in 0 until 10) {
                val ship = ship(Difficulty.INSANE, sector, seed)
                val where = "sector $sector, seed $seed"
                assertTrue(where, ship.health > 0)
                for (system in ship.systems) {
                    assertTrue("$where: ${system.codename}", system.energyLevels in 1..system.blueprint.maxPower)
                }
                // with the reactor for all of them
                assertTrue(where, ship.purchasedReactorPower >= ship.mainSystems.sumOf { it.energyLevels })
            }
        }
    }

    @Test
    fun testTheShipsAreStrongerThanOnNormal() {
        fun bars(difficulty: Difficulty) = (0 until 40).sumOf { seed ->
            ship(difficulty, 2, seed).systems.sumOf { it.energyLevels }
        }

        fun optionalSystems(difficulty: Difficulty) = (0 until 40).count { seed ->
            ship(difficulty, 2, seed).sensors != null
        }

        val normal = bars(Difficulty.NORMAL)
        val insane = bars(Difficulty.INSANE)
        assertTrue("$normal $insane", insane > normal)
        val normalOptional = optionalSystems(Difficulty.NORMAL)
        val insaneOptional = optionalSystems(Difficulty.INSANE)
        assertTrue("$normalOptional $insaneOptional", insaneOptional > normalOptional)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """<FTL>
    <systemBlueprint name="shields">
        <type>shields</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <systemBlueprint name="sensors">
        <type>sensors</type>
        <startPower>1</startPower>
        <maxPower>4</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <shipBlueprint name="TEST_INSANE_SHIP" layout="test_ship" img="test_ship">
        <health amount="10"/>
        <maxPower amount="10"/>
        <!-- An automated ship, as tests can't spawn real crew -->
        <crewCount amount="0" max="0" class="test_crew"/>
        <systemList>
            <oxygen power="1" max="3" room="0" start="true"/>
            <pilot power="1" max="3" room="1" start="true"/>
            <engines power="1" max="8" room="2" start="true"/>
            <shields power="2" max="8" room="3" start="true"/>
            <!-- optional -->
            <sensors power="1" max="4" room="4" start="false"/>
        </systemList>
    </shipBlueprint>
</FTL>"""

        @Language("XML")
        private const val EVENT_XML = """<FTL>
    <ship name="TEST_INSANE_ENEMY" auto_blueprint="TEST_INSANE_SHIP"/>
</FTL>"""
    }
}
