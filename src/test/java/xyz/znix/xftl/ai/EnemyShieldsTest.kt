package xyz.znix.xftl.ai

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

/**
 * Check a new enemy ship has its shields up straight away, so the player
 * can't get a shot in (with the weapon pre-igniter, say) before they charge.
 */
class EnemyShieldsTest {
    @Test
    fun startsWithShieldsUp() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_SHIELDS")
        val ship = kit.player
        ship.purchasedReactorPower = 30
        ship.updateAvailablePower()

        // Like a new enemy ship (the player's starts with them up)
        ship.shields!!.activeShields = 0

        // The AI only needs a ship to fight, which can be its own here
        val ai = ShipAI(ship, ship)
        ai.update(0.01f)

        assertEquals(2, ship.shields!!.activeShields)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_SHIELDS" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <shields power="4" room="3"/>
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
