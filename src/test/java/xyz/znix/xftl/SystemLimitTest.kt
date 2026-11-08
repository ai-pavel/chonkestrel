package xyz.znix.xftl

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

class SystemLimitTest {
    @Test
    fun testDefaultLimit() {
        val kit = InGameTest()
        assertEquals(8, kit.player.maxMainSystems)
        assertEquals(4, kit.player.cargoBlueprints.size)
        assertEquals(3, kit.player.maxAugments)
    }

    @Test
    fun testRaisedLimit() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_MORE_SYSTEMS")
        assertEquals(10, kit.player.maxMainSystems)
        assertEquals(8, kit.player.cargoBlueprints.size)
        assertEquals(6, kit.player.maxAugments)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_MORE_SYSTEMS" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <maxSystems>10</maxSystems>
        <maxCargo>8</maxCargo>
        <maxAugments>6</maxAugments>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""
    }
}
