package xyz.znix.xftl.weapon

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.Damage

class MultipleShieldsSystemsTest {
    private fun createKit(): InGameTest {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_TWIN_SHIELDS")
        kit.powerUpAll()

        // Let all the shield layers charge up
        for (i in 0 until 10) {
            kit.updateSingle(1f)
        }
        return kit
    }

    @Test
    fun testLayersStack() {
        val kit = createKit()
        val (first, second) = kit.player.shieldSystems

        assertEquals(2, kit.player.shieldSystems.size)
        assertSame(first, kit.player.shields)
        assertEquals(2, first.activeShields)
        assertEquals(1, second.activeShields)
        assertEquals(3, kit.player.activeShieldLayers)
    }

    @Test
    fun testHitsTakeTheMostChargedSystem() {
        val kit = createKit()
        val (first, second) = kit.player.shieldSystems
        val hit = Damage().apply { hullDamage = 1 }

        // The first system has the most layers, so it's hit first
        kit.player.attackShields(hit, null)
        assertEquals(1, first.activeShields)
        assertEquals(1, second.activeShields)

        kit.player.attackShields(hit, null)
        kit.player.attackShields(hit, null)
        assertEquals(0, kit.player.activeShieldLayers)

        // Jumping recharges both of them
        kit.player.resetAfterJump()
        assertEquals(2, first.activeShields)
        assertEquals(1, second.activeShields)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_TWIN_SHIELDS" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <shields power="4" room="3"/>
            <shields power="2" room="5"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="shields">
        <type>shields</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
</FTL>
"""
    }
}
