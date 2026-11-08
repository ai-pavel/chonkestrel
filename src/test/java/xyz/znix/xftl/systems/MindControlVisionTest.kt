package xyz.znix.xftl.systems

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.CrewSlug
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew

/**
 * Mind control only reaches the crew a ship can see (see Ship.canSeeInto):
 * through its sensors, or anywhere with the Lifeform Scanner or a Slug of
 * its own, for the player and enemies alike.
 */
class MindControlVisionTest {
    private fun createKit(enemy: String): InGameTest {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), extraEvents = listOf(EVENT_XML),
            playerShip = "PLAYER_SHIP_MIND_TEST")
        kit.state.debugSpawnShip(kit.state.eventManager.getShip(enemy), Difficulty.NORMAL, 0, 1)
        kit.powerUpAll()
        return kit
    }

    private fun powerUp(ship: Ship) {
        ship.purchasedReactorPower = ship.mainSystems.sumOf { it.energyLevels }
        ship.updateAvailablePower()
        for (system in ship.mainSystems)
            repeat(system.energyLevels) { system.increasePower() }
    }

    private fun InGameTest.update() = repeat(2) { state.updateGameState(0.01f) }

    @Test
    fun testThePlayerNeedsToSeeTheRoom() {
        val kit = createKit("TEST_BLIND_ENEMY")
        val enemy = kit.state.enemy!!
        val target = kit.addCrew(enemy, enemy.rooms[0])
        kit.update()

        // Without sensors that reach the enemy ship, the room can't be seen
        val mind = kit.player.mindControl!!
        mind.selectRoom(target.room)
        assertNull(mind.controlledCrew)
    }

    @Test
    fun testASlugLetsThePlayerSeeEveryone() {
        val kit = createKit("TEST_BLIND_ENEMY")
        val enemy = kit.state.enemy!!
        val target = kit.addCrew(enemy, enemy.rooms[0])

        // A Slug of the player's own, anywhere, senses everyone
        val type = kit.state.blueprintManager["test_crew"] as CrewBlueprint
        val slug = CrewSlug(type, kit.state.animations, kit.player.rooms[0], AbstractCrew.SlotType.CREW)
        kit.player.crew.add(slug)
        slug.jumpTo(kit.player.findSpaceForCrew(kit.player.rooms[0], AbstractCrew.SlotType.CREW))
        kit.update()

        val mind = kit.player.mindControl!!
        mind.selectRoom(target.room)
        assertEquals(target, mind.controlledCrew)
    }

    @Test
    fun testTheLifeformScannerLetsThePlayerSeeEveryone() {
        val kit = createKit("TEST_BLIND_ENEMY")
        val enemy = kit.state.enemy!!
        val target = kit.addCrew(enemy, enemy.rooms[0])
        kit.givePlayerBP("LIFE_SCANNER")
        kit.update()

        val mind = kit.player.mindControl!!
        mind.selectRoom(target.room)
        assertEquals(target, mind.controlledCrew)
    }

    @Test
    fun testAnEnemyNeedsItsSensors() {
        // Without sensors, an enemy's AI can't see the player's crew to
        // control them
        val blind = createKit("TEST_BLIND_ENEMY")
        val blindEnemy = blind.state.enemy!!
        powerUp(blindEnemy)
        blind.addCrew(blind.player, blind.player.rooms[0])
        repeat(10) { blind.state.updateGameState(0.1f) }
        assertNull(blindEnemy.mindControl!!.controlledCrew)

        // With its sensors at level 2, it can
        val seeing = createKit("TEST_SEEING_ENEMY")
        val seeingEnemy = seeing.state.enemy!!
        powerUp(seeingEnemy)
        val crew = listOf(
            seeing.addCrew(seeing.player, seeing.player.rooms[0]),
            seeing.addCrew(seeing.player, seeing.player.rooms[4]),
        )
        repeat(10) { seeing.state.updateGameState(0.1f) }
        assertTrue(seeingEnemy.mindControl!!.controlledCrew in crew)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <systemBlueprint name="mind">
        <type>mind</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
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
    <augBlueprint name="LIFE_SCANNER">
        <cost>10</cost>
        <stackable>false</stackable>
    </augBlueprint>

    <shipBlueprint name="PLAYER_SHIP_MIND_TEST" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <mind power="1" max="3" room="2"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_BLIND_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="2" max="2" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <mind power="1" max="3" room="2"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_SEEING_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="2" max="2" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <mind power="1" max="3" room="2"/>
            <sensors power="2" max="4" room="3"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <ship name="TEST_BLIND_ENEMY" blueprint="TEST_BLIND_ENEMY_SHIP"/>
    <ship name="TEST_SEEING_ENEMY" blueprint="TEST_SEEING_ENEMY_SHIP"/>
</FTL>
"""
    }
}
