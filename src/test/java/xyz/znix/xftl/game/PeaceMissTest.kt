package xyz.znix.xftl.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

/**
 * Shots still on their way when a fight ends miss: the player's at an
 * enemy that's been beaten, and an enemy's at the player once it's stopped
 * fighting (made peace, or surrendered).
 */
class PeaceMissTest {
    private fun fight(): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(WingmanTest.BLUEPRINT_XML),
            extraEvents = listOf(WingmanTest.EVENT_XML),
            playerShip = "PLAYER_SHIP_THREE_ENEMIES"
        )
        val spec = kit.state.eventManager.getShip("TEST_ENEMY")
        kit.state.debugSpawnShip(spec, Difficulty.NORMAL, 0, 1)
        kit.state.updateGameState(0.01f)
        return kit
    }

    @Test
    fun anEnemysShotsMissThePlayerOnceItsMadePeace() {
        val kit = fight()
        val player = kit.player
        val enemy = kit.state.enemy!!
        assertFalse(player.isOutOfTheFightWith(enemy))

        kit.state.opponents.single { it.ship == enemy }.hostile = false
        assertTrue(player.isOutOfTheFightWith(enemy))
        repeat(20) { assertTrue(player.pickMissed(shooter = enemy)) }

        // (but not what has no shooter, or the player's own bombs)
        assertFalse(player.isOutOfTheFightWith(null))
        assertFalse(player.isOutOfTheFightWith(player))
    }
}
