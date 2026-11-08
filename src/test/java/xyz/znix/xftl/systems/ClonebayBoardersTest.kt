package xyz.znix.xftl.systems

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew

/**
 * The player's boarders still aboard an enemy ship that's destroyed, or
 * that gets away, die with it, and the clonebay brings them back.
 */
class ClonebayBoardersTest {
    private fun createKit(playerShip: String = "PLAYER_SHIP_CLONEBAY_TEST"): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraEvents = listOf(EVENT_XML), playerShip = playerShip
        )
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_CLONEBAY_ENEMY"), Difficulty.NORMAL, 0, 1)
        kit.powerUpAll()
        return kit
    }

    private fun InGameTest.board() =
        addCrew(state.enemy!!, state.enemy!!.rooms[0], mode = AbstractCrew.SlotType.INTRUDER, owner = player)

    @Test
    fun testBoardersOnADestroyedShipAreCloned() {
        val kit = createKit()
        val boarder = kit.board()
        val enemy = kit.state.enemy!!

        enemy.health = 0
        repeat(200) { kit.state.updateGameState(0.1f) }
        assertFalse(kit.state.isShipPresent(enemy))
        assertTrue(boarder in kit.player.clonebay!!.queue || boarder in kit.player.crew)
    }

    @Test
    fun testBoardersOnAShipThatGetsAwayAreCloned() {
        val kit = createKit()
        val boarder = kit.board()
        val enemy = kit.state.enemy!!

        enemy.escapeTimer = 0f
        repeat(2) { kit.state.updateGameState(0.1f) }
        assertFalse(kit.state.isShipPresent(enemy))
        assertTrue(boarder in kit.player.clonebay!!.queue)

        // And then cloned, on the player's ship
        var steps = 0
        while (boarder !in kit.player.crew && steps++ < 600)
            kit.state.updateGameState(0.1f)
        assertTrue(boarder in kit.player.crew)
        assertTrue(boarder.room.ship == kit.player)
    }

    @Test
    fun testWithoutAClonebayTheyreLost() {
        val kit = createKit("PLAYER_SHIP_NO_CLONEBAY_TEST")
        val boarder = kit.board()
        kit.state.enemy!!.escapeTimer = 0f
        repeat(2) { kit.state.updateGameState(0.1f) }
        assertFalse(boarder in kit.player.crew)
        assertFalse(boarder in kit.state.playerCrew)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <systemBlueprint name="clonebay">
        <type>clonebay</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <shipBlueprint name="PLAYER_SHIP_CLONEBAY_TEST" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <clonebay power="1" max="3" room="2"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PLAYER_SHIP_NO_CLONEBAY_TEST" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_CLONEBAY_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="2" max="2" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <ship name="TEST_CLONEBAY_ENEMY" blueprint="TEST_CLONEBAY_ENEMY_SHIP"/>
</FTL>
"""
    }
}
