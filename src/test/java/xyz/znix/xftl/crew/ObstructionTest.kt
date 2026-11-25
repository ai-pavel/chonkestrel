package xyz.znix.xftl.crew

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew

/**
 * No one stands on a medbay's (or clonebay's) obstructed cell: not even
 * boarders, who teleport to the cell they stood on at home if it's free.
 */
class ObstructionTest {
    @Test
    fun testBoardersDontLandOnTheMedbaysObstructedCell() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), extraEvents = listOf(EVENT_XML), playerShip = "TEST_OBSTRUCTION_SHIP")
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_OBSTRUCTION_ENEMY"), Difficulty.NORMAL, 0, 1)
        val enemy = kit.state.enemy!!
        val medbay = kit.player.rooms[1]
        val obstruction = ConstPoint(1, 0)
        assertTrue(medbay.isObstructed(obstruction))

        // Four boarders, the second of them standing on the cell at home
        // that's obstructed in the medbay, teleport into it, with room for
        // three
        val boarders = (0 until 4).map { kit.addCrew(enemy, enemy.rooms[2]) }
        assertEquals(obstruction, boarders[1].standingPosition!!.let { ConstPoint(it.x, it.y) })
        boarders.forEach { it.teleportAnimatedTo(medbay) }
        repeat(60) {
            kit.state.updateGameState(0.05f)
            for (boarder in boarders) {
                val at = boarder.standingPosition ?: continue
                assertFalse("${boarder.info.name} stood on it", at.room == medbay && medbay.isObstructed(at))
            }
        }
        assertTrue(boarders.all { it.room.ship == kit.player })
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <systemBlueprint name="medbay">
        <type>medbay</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <shipBlueprint name="TEST_OBSTRUCTION_SHIP" layout="test_big_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="0"/>
            <medbay power="1" max="3" room="1">
                <slot><number>1</number></slot>
            </medbay>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_OBSTRUCTION_ENEMY_SHIP" layout="test_big_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="0"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <ship name="TEST_OBSTRUCTION_ENEMY" blueprint="TEST_OBSTRUCTION_ENEMY_SHIP"/>
</FTL>
"""
    }
}
