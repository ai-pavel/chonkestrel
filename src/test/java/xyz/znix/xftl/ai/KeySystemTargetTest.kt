package xyz.znix.xftl.ai

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.testkit.InGameTest
import kotlin.random.Random

/**
 * On insane, the enemies aim at the player's most valuable systems (see
 * ShipAI.keySystemTarget): the shields, then the weapons, then the
 * piloting, then the rest, skipping those already destroyed.
 */
class KeySystemTargetTest {
    private val kit = InGameTest(
        extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_KEY_SYSTEMS_TEST",
        difficulty = Difficulty.INSANE
    )
    private val player get() = kit.player

    private fun destroy(system: AbstractSystem) {
        system.damagedEnergyLevels = system.energyLevels
    }

    // Where [count] shots go
    private fun targets(count: Int = 20) = (0 until count).map { ShipAI.keySystemTarget(player, Random(it)) }.toSet()

    @Test
    fun theOrder() {
        val shields = player.shieldSystems.single()
        val weapons = player.weapons!!
        val piloting = player.piloting!!

        assertEquals(setOf(shields.room), targets())
        destroy(shields)
        assertEquals(setOf(weapons.room), targets())
        destroy(weapons)
        assertEquals(setOf(piloting.room), targets())
        destroy(piloting)
        // then the rest, at random
        val rest = setOf(player.engines!!.room, player.oxygen!!.room)
        assertEquals(rest, targets())

        // Once they're all destroyed, the shields again
        for (system in player.systems)
            destroy(system)
        assertEquals(setOf(shields.room), targets())

        // Once the shields are mended, they come first again
        weapons.damagedEnergyLevels = 0
        shields.damagedEnergyLevels = 0
        assertEquals(setOf(shields.room), targets())
    }

    @Test
    fun onlyOnInsane() {
        assertEquals(listOf(Difficulty.INSANE), Difficulty.entries.filter { it.enemiesTargetKeySystems })
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <systemBlueprint name="shields">
        <type>shields</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <shipBlueprint name="PLAYER_SHIP_KEY_SYSTEMS_TEST" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <engines power="1" max="1" room="0"/>
            <oxygen power="1" max="1" room="1"/>
            <pilot power="1" max="1" room="2"/>
            <weapons power="1" max="1" room="3"/>
            <shields power="2" max="8" room="4"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""
    }
}
