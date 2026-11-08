package xyz.znix.xftl.shipgen

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.testkit.InGameTest

/**
 * The enemies' cloaking gets more bars further in, as far as the mod lets
 * it go (six, see Cloaking.durationFor), unless their ship holds it to less
 * than vanilla's three.
 */
class CloakingScalingTest {
    private val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), extraEvents = listOf(EVENT_XML))

    // The cloaking system's bars on [count] enemy ships in a (vanilla) sector
    private fun cloaking(ship: String, sector: Int, count: Int = 40): List<Int> = (0 until count).map { seed ->
        kit.state.debugSpawnShip(kit.state.eventManager.getShip(ship), Difficulty.NORMAL, sector, seed)
        kit.state.enemy!!.cloaking!!.energyLevels
    }

    @Test
    fun testCloakingGrowsThroughTheGame() {
        val early = cloaking("TEST_CLOAKER", 0)
        val late = cloaking("TEST_CLOAKER", 7)
        assertTrue("$early", early.all { it <= 3 })
        assertTrue("$late", late.any { it > 3 })
        assertTrue("$late", late.any { it == 6 })
        assertTrue("$early $late", late.average() > early.average())
    }

    @Test
    fun testAShipCanHoldItBack() {
        assertTrue(cloaking("TEST_CLOAKER_HELD", 7).all { it <= 2 })
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """<FTL>
    <systemBlueprint name="cloaking">
        <type>cloaking</type>
        <startPower>1</startPower>
        <maxPower>6</maxPower>
        <cost>150</cost>
        <upgradeCost><level>30</level><level>50</level><level>150</level><level>300</level><level>600</level></upgradeCost>
    </systemBlueprint>

    <shipBlueprint name="TEST_CLOAK_SHIP" layout="test_ship" img="test_ship">
        <health amount="10"/>
        <maxPower amount="30"/>
        <crewCount amount="0" max="0" class="test_crew"/>
        <systemList>
            <oxygen power="1" max="1" room="0" start="true"/>
            <pilot power="1" max="1" room="1" start="true"/>
            <engines power="1" max="1" room="2" start="true"/>
            <cloaking power="1" room="3" start="true"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_CLOAK_SHIP_HELD" layout="test_ship" img="test_ship">
        <health amount="10"/>
        <maxPower amount="30"/>
        <crewCount amount="0" max="0" class="test_crew"/>
        <systemList>
            <oxygen power="1" max="1" room="0" start="true"/>
            <pilot power="1" max="1" room="1" start="true"/>
            <engines power="1" max="1" room="2" start="true"/>
            <cloaking power="1" max="2" room="3" start="true"/>
        </systemList>
    </shipBlueprint>
</FTL>"""

        @Language("XML")
        private const val EVENT_XML = """<FTL>
    <ship name="TEST_CLOAKER" auto_blueprint="TEST_CLOAK_SHIP"/>
    <ship name="TEST_CLOAKER_HELD" auto_blueprint="TEST_CLOAK_SHIP_HELD"/>
</FTL>"""
    }
}
