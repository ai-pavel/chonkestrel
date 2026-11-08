package xyz.znix.xftl.crew

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.WingmanTest
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.RoomPoint
import xyz.znix.xftl.systems.Teleporter
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew

/**
 * Boarders: breaking down doors, sabotaging systems, and being taken home by
 * the teleporter.
 */
class BoardersTest {
    // The test ship's layout, with doors (locked, at two bars) and a
    // teleporter, and the crew AI off so the crew go where they're sent
    private val kit = InGameTest(
        extraBlueprints = listOf(WingmanTest.BLUEPRINT_XML, BLUEPRINT_XML),
        extraEvents = listOf(WingmanTest.EVENT_XML),
        playerShip = "PLAYER_SHIP_BOARDING"
    ).also {
        it.state.debugFlags.noCrewAI.set = true
        it.powerUpAll()
    }
    private val player get() = kit.player
    private val bigRoom get() = player.rooms.first { it.width == 5 && it.height == 5 }

    private fun run(seconds: Float) = repeat((seconds * 20).toInt()) { kit.updateSingle(0.05f) }

    private fun place(ship: Ship, room: Room, mode: AbstractCrew.SlotType, owner: Ship?): LivingCrew {
        val crew = kit.addCrew(ship, room, mode, owner)
        val point = RoomPoint(room, ConstPoint(0, 0))
        crew.jumpTo(point)
        crew.setTargetRoom(room, point)
        return crew
    }

    private fun fight(): Ship {
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_ENEMY"), Difficulty.NORMAL, 0, 1)
        kit.state.updateGameState(0.01f)
        return kit.state.enemy!!
    }

    @Test
    fun testBoardersBreakDownALockedDoor() {
        val pilotRoom = player.rooms.first { it.system?.blueprint?.name == "pilot" }
        val door = pilotRoom.doors.single { it.other(pilotRoom) == bigRoom }
        val boarder = place(player, pilotRoom, AbstractCrew.SlotType.INTRUDER, null)
        assertTrue(door.isLockedFor(boarder))

        boarder.setTargetRoom(bigRoom)
        var broken = false
        repeat(60 * 20) {
            kit.updateSingle(0.05f)
            broken = broken || door.isBroken
        }
        assertTrue("the door was broken down", broken)
        assertSame(bigRoom, boarder.room)
    }

    @Test
    fun testDoorsShutWithTheDoorsSystemDestroyed() {
        val door = player.doors.first()
        door.open = true
        val doors = player.doorsSystem!!
        doors.dealDamage(doors.energyLevels, 0)
        run(0.5f)
        assertTrue(doors.broken)
        assertFalse(door.open)
        assertFalse(door.isControllable)
        door.toggle()
        assertFalse(door.open)
    }

    @Test
    fun testBoardersStopSabotagingABeatenShip() {
        val enemy = fight()
        val room = enemy.rooms.first { it.system != null && it.system!!.energyLevels > 0 }
        val system = room.system!!
        place(enemy, room, AbstractCrew.SlotType.INTRUDER, player)

        run(15f)
        val damaged = system.damagedEnergyLevels
        assertTrue("sabotaged while it's fighting", damaged > 0 || system.damageProgress > 0f)

        kit.state.debugBeatEnemy()
        val progress = system.damageProgress
        run(15f)
        assertEquals(damaged, system.damagedEnergyLevels)
        assertTrue(system.damageProgress <= progress)
    }

    @Test
    fun testTheTeleporterOnlyBringsHomeItsOwnCrew() {
        val enemy = fight()
        val room = enemy.rooms.first { it.width == 5 }
        val ours = place(enemy, room, AbstractCrew.SlotType.INTRUDER, player)
        val theirs = place(enemy, room, AbstractCrew.SlotType.CREW, enemy)
        // Someone else's boarder in the same room, which used to be taken too
        val stranger = place(enemy, room, AbstractCrew.SlotType.INTRUDER, null)

        val teleporter = player.systems.filterIsInstance<Teleporter>().single()
        // (once the rooms know who's in them)
        run(0.1f)
        teleporter.selectTeleportAction(false, room)
        run(3f)
        assertSame(player, ours.room.ship)
        assertSame(enemy, theirs.room.ship)
        assertSame(enemy, stranger.room.ship)
    }

    @Test
    fun testNoBoardingABeatenShip() {
        val enemy = fight()
        val teleporter = player.systems.filterIsInstance<Teleporter>().single()
        val crew = place(player, teleporter.room!!, AbstractCrew.SlotType.CREW, player)
        kit.state.debugBeatEnemy()

        teleporter.selectTeleportAction(true, enemy.rooms.first())
        run(3f)
        assertSame(player, crew.room.ship)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="PLAYER_SHIP_BOARDING" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>

        <systemList>
            <pilot power="1" max="1" room="0"/>
            <doors power="2" max="3" room="1"/>
            <engines power="1" max="1" room="2"/>
            <teleporter power="3" max="3" room="3"/>
            <oxygen power="1" max="1" room="4"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="doors">
        <type>doors</type>
        <startPower>2</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <systemBlueprint name="teleporter">
        <type>teleporter</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
</FTL>
"""
    }
}
