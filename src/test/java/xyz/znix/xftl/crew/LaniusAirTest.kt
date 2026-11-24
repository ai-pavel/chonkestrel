package xyz.znix.xftl.crew

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.layout.OxygenTransfer
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew

/**
 * A Lanius drains the air from its room (see OxygenTransfer), but not an
 * enemy ship's own Lanius while it has crew of its own that breathe, so a
 * mixed crew (the Mainframe's, a hybrid's) don't suffocate each other.
 */
class LaniusAirTest {
    private fun createKit(): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML),
            extraEvents = listOf(EVENT_XML), playerShip = "PLAYER_SHIP_LANIUS_AIR_TEST"
        )
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_LANIUS_AIR_ENEMY"), Difficulty.NORMAL, 0, 1)
        return kit
    }

    // The air left in [room] after a second of OxygenTransfer, from full
    private fun airAfterASecond(ship: Ship, room: Room): Float {
        // Who's in which room, as the ship's update finds it
        ship.rooms.forEach { it.updateCrewInRoom() }
        ship.rooms.forEach { it.oxygen = 1f }
        repeat(20) { OxygenTransfer.update(ship, 0.05f) }
        return room.oxygen
    }

    @Test
    fun testAnEnemysLaniusSpareItsMixedCrew() {
        val kit = createKit()
        val enemy = kit.state.enemy!!
        val room = enemy.rooms[0]

        // With a human of its own aboard (the blueprint's crew), its Lanius
        // leave the air alone
        kit.addCrew(enemy, room, race = "anaerobic")
        assertEquals(1f, airAfterASecond(enemy, room), 0.001f)

        // Its crew all Lanius, they drain it, as in vanilla
        for (crew in enemy.crew.filter { it !is CrewAnaerobic })
            crew.health = 0f
        assertTrue(airAfterASecond(enemy, room) < 1f)
    }

    @Test
    fun testThePlayersLaniusStillDrainTheirShip() {
        val kit = createKit()
        val room = kit.player.rooms[0]
        kit.addCrew(kit.player, room)
        // (in the nearest room with space, if that one's full)
        val lanius = kit.addCrew(kit.player, room, race = "anaerobic")
        assertTrue(airAfterASecond(kit.player, lanius.room) < 1f)
    }

    @Test
    fun testBoardingLaniusStillDrainAnEnemysAir() {
        val kit = createKit()
        val enemy = kit.state.enemy!!
        val room = enemy.rooms[0]
        kit.addCrew(enemy, room, mode = AbstractCrew.SlotType.INTRUDER, owner = kit.player, race = "anaerobic")
        assertTrue(airAfterASecond(enemy, room) < 1f)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <crewBlueprint name="anaerobic">
        <cost>1</cost>
    </crewBlueprint>

    <shipBlueprint name="PLAYER_SHIP_LANIUS_AIR_TEST" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_LANIUS_AIR_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="1" max="1" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <ship name="TEST_LANIUS_AIR_ENEMY" blueprint="TEST_LANIUS_AIR_ENEMY_SHIP"/>
</FTL>
"""

        // The Lanius' animations, as the humans' in the test kit
        private val ANIMATIONS_XML = (
                listOf("portrait", "repair", "death_right", "teleport") +
                        listOf("up", "down", "left", "right").flatMap { dir ->
                            listOf("shoot", "walk", "punch", "type", "fire").map { "${it}_$dir" }
                        }
                ).joinToString("\n", "<FTL>\n", "\n</FTL>") {
                """<anim name="anaerobic_$it"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>"""
            }
    }
}
