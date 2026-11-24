package xyz.znix.xftl.drones

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.DroneSizes

/**
 * The bulwarks (see doc/game/drone-sizes.md): an enemy's twin or triple
 * anti-personnel drone is one big drone, standing on a square of cells in
 * a room big enough for it, and walking between such rooms through
 * passages as wide as itself, after boarders or on its rounds. The
 * player's is a squad.
 */
class BulwarkTest {
    private fun kit(enemy: String): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML),
            extraEvents = listOf(EVENT_XML), playerShip = "TEST_BULWARK_PLAYER_SHIP"
        )
        kit.powerUpAll()
        kit.state.debugSpawnShip(kit.state.eventManager.getShip(enemy), Difficulty.NORMAL, 0, 1)
        return kit
    }

    private fun InGameTest.launch(ship: Ship, size: Int): Drones.DroneInfo {
        val system = ship.droneSystems.single()
        system.drones.indices.forEach { system.drones[it] = null }
        val name = if (size == 1) "BATTLE" else DroneSizes.nameFor("BATTLE", size)
        val blueprint = state.blueprintManager[name] as DroneBlueprint
        system.drones[0] = Drones.DroneInfo(blueprint, null)
        // (with the power for it, on whichever ship it is)
        system.energyLevels = system.blueprint.maxPower
        ship.purchasedReactorPower = ship.mainSystems.sumOf { it.energyLevels }
        ship.updateAvailablePower()
        repeat(system.energyLevels) { system.increasePower() }
        ship.dronesCount = 10
        assertTrue(system.setDronePower(0, true))
        return system.drones[0]!!
    }

    private val Drones.DroneInfo.bulwark: AntiPersonnelDrone get() = liveCopies.single() as AntiPersonnelDrone

    @Test
    fun anEnemysTripleStandsGuardInItsBiggestRoom() {
        val kit = kit("TEST_BULWARK_ENEMY")
        val enemy = kit.state.enemy!!
        val info = kit.launch(enemy, 3)

        // One drone, for the three parts it's worth
        val drone = info.bulwark
        assertEquals(7, enemy.dronesCount)
        assertEquals(3, drone.scale)

        // In the corner of the 3x3 room, holding the rest of it
        val pawn = drone.pawn!!
        val big = enemy.rooms[2]
        assertEquals(big, pawn.room)
        assertTrue(pawn.standingPosition!!.let { it.x == 0 && it.y == 0 })
        for (x in 0 until 3) for (y in 0 until 3) {
            if (x != 0 || y != 0)
                assertFalse("$x,$y", big.isSlotFree(ConstPoint(x, y), xyz.znix.xftl.crew.AbstractCrew.SlotType.CREW))
        }
        assertEquals(150f * 9, pawn.maxHealth)
        assertEquals(3f, pawn.attackDamageMult)

        // It stays there
        repeat(40) { kit.state.updateGameState(0.05f) }
        assertEquals(big, pawn.room)
    }

    @Test
    fun noOneElseStandsOnItsCells() {
        val kit = kit("TEST_BULWARK_ENEMY")
        val enemy = kit.state.enemy!!
        val drone = kit.launch(enemy, 3).bulwark
        val big = enemy.rooms[2]

        // A big boarding party sent into its room ends up elsewhere: its
        // square's the whole room, so the next rooms
        kit.state.debugFlags.noCrewAI.set = true
        val boarders = (1..4).map {
            kit.addCrew(enemy, big, mode = xyz.znix.xftl.crew.AbstractCrew.SlotType.INTRUDER, owner = kit.player)
        }
        repeat(60) { kit.state.updateGameState(0.05f) }
        for (boarder in boarders) {
            val at = boarder.standingPosition ?: boarder.pathingTarget
            assertTrue("${boarder.room.id}", at?.room != big)
        }
        assertEquals(big, drone.pawn!!.room)
    }

    @Test
    fun aTwinStandsInAnEmptyRoomNotASystems() {
        val kit = kit("TEST_BULWARK_ENEMY")
        val enemy = kit.state.enemy!!
        val drone = kit.launch(enemy, 2).bulwark
        assertEquals(2, drone.scale)
        assertEquals(150f * 4, drone.pawn!!.maxHealth)

        // Not in the 2x2 room, which has the drones system, but the empty one
        assertEquals(enemy.rooms[2], drone.pawn!!.room)
        assertEquals(null, drone.pawn!!.room.system)
    }

    @Test
    fun withNoRoomForItItGoesAsASingleDrone() {
        val kit = kit("TEST_BULWARK_ENEMY")
        val enemy = kit.state.enemy!!
        // No room's big enough for a 4x4 one, and with someone in the 3x3
        // room, there's no square free for a triple (two of them, as one
        // goes off to man the drones system)
        assertFalse(AntiPersonnelDrone.bulwarkFits(enemy, 4))
        repeat(2) { kit.addCrew(enemy, enemy.rooms[2]) }
        kit.state.updateGameState(0.01f)
        val drone = kit.launch(enemy, 3).bulwark
        assertEquals(1, drone.scale)
    }

    @Test
    fun thePlayersIsASquad() {
        val kit = kit("TEST_BULWARK_ENEMY")
        val info = kit.launch(kit.player, 3)
        assertEquals(3, info.liveCopies.size)
        assertTrue(info.liveCopies.all { (it as AntiPersonnelDrone).scale == 1 })
    }

    @Test
    fun fireHurtsABulwarkButNotASingleDrone() {
        val kit = kit("TEST_BULWARK_ENEMY")
        val bulwark = kit.launch(kit.state.enemy!!, 3).bulwark.pawn!!
        assertEquals(1f, bulwark.fireDamageMult)
        // (once it's powered up)
        repeat(30) { kit.state.updateGameState(0.05f) }
        bulwark.room.spawnFireAt(0)
        val health = bulwark.health
        repeat(10) { kit.state.updateGameState(0.05f) }
        assertTrue(bulwark.health < health)

        val single = kit.launch(kit.player, 1).liveCopies.single() as AntiPersonnelDrone
        assertEquals(0f, single.pawn!!.fireDamageMult)
    }

    // TEST_BULWARK_WALKS: two empty 3x3 rooms (2 and 3) side by side, with a
    // passage two doors wide between them (a wide door), and an empty 2x2
    // room (4) under the first, through a single door
    private val InGameTest.roomA get() = state.enemy!!.rooms[2]
    private val InGameTest.roomB get() = state.enemy!!.rooms[3]
    private val InGameTest.roomC get() = state.enemy!!.rooms[4]

    private fun InGameTest.boarder(room: Room) =
        addCrew(state.enemy!!, room, mode = AbstractCrew.SlotType.INTRUDER, owner = player)

    // Run the game for [seconds], calling [each] after every update
    private fun InGameTest.play(seconds: Float, each: () -> Unit = {}) {
        repeat((seconds / 0.05f).toInt()) {
            state.updateGameState(0.05f)
            each()
        }
    }

    @Test
    fun thePassagesDoorsAreOneWideDoor() {
        val kit = kit("TEST_BULWARK_WALKS")
        val doors = kit.roomA.doors.filter { it.other(kit.roomA) == kit.roomB }
        assertEquals(2, doors.size)
        assertTrue(doors.all { it.group == doors.sortedBy { d -> d.position.y } })
        // (the single door down to the 2x2 room's its own)
        assertEquals(1, kit.roomC.doors.single { it.other(kit.roomC) == kit.roomA }.group.size)

        // They open and close as one
        doors[1].open = true
        assertTrue(doors.all { it.open })
        doors[0].open = false
        assertTrue(doors.none { it.open })

        // and are broken down as one
        repeat(doors[0].maxHealth) { doors[1].attackDoor() }
        assertTrue(doors.all { it.isBroken && it.open })
    }

    @Test
    fun aTwinGoesThroughAWidePassageAfterBoarders() {
        val kit = kit("TEST_BULWARK_WALKS")
        val drone = kit.launch(kit.state.enemy!!, 2).bulwark
        val pawn = drone.pawn!!
        assertEquals(kit.roomA, pawn.room)

        // Boarders in the next room, through the passage two doors wide: it
        // goes there, and fights them
        kit.state.debugFlags.noCrewAI.set = true
        val boarder = kit.boarder(kit.roomB)
        kit.play(8f)
        assertEquals(kit.roomB, pawn.room)
        assertTrue(pawn.standingPosition != null)
        assertTrue(boarder.health < boarder.maxHealth)

        // It holds the cells it stands on there, and none in the room it left
        val at = pawn.standingPosition!!
        for (x in 0 until 2) for (y in 0 until 2) {
            val cell = ConstPoint(at.x + x, at.y + y)
            assertFalse("$x,$y", kit.roomB.isSlotFree(cell, AbstractCrew.SlotType.CREW))
        }
        for (x in 0 until 3) for (y in 0 until 3)
            assertTrue("$x,$y", kit.roomA.isSlotFree(ConstPoint(x, y), AbstractCrew.SlotType.CREW))
    }

    @Test
    fun aTwinStopsAtASingleDoor() {
        val kit = kit("TEST_BULWARK_WALKS")
        val pawn = kit.launch(kit.state.enemy!!, 2).bulwark.pawn!!

        // Boarders in the 2x2 room, through a single door: it never goes in
        kit.state.debugFlags.noCrewAI.set = true
        kit.boarder(kit.roomC)
        kit.play(20f) { assertTrue(pawn.room != kit.roomC) }
    }

    @Test
    fun aTripleStopsAtAPassageTwoDoorsWide() {
        val kit = kit("TEST_BULWARK_WALKS")
        val pawn = kit.launch(kit.state.enemy!!, 3).bulwark.pawn!!
        assertEquals(kit.roomA, pawn.room)

        // Boarders in the next room: the passage's too narrow for it, and
        // the room it's in is all it can get to, so it stays put
        kit.state.debugFlags.noCrewAI.set = true
        kit.boarder(kit.roomB)
        kit.play(20f) {
            assertEquals(kit.roomA, pawn.room)
            assertTrue(pawn.nextTargetPos == null)
        }
    }

    @Test
    fun withNoBoardersItGoesOnItsRounds() {
        val kit = kit("TEST_BULWARK_WALKS")
        val pawn = kit.launch(kit.state.enemy!!, 2).bulwark.pawn!!

        // Round the corners of the first room, and on through the passage
        // into the next, but never through the single door
        val stoodOn = HashSet<Pair<Int, ConstPoint>>()
        kit.play(40f) {
            assertTrue(pawn.room != kit.roomC)
            pawn.standingPosition?.let { stoodOn += it.room.id to ConstPoint(it.x, it.y) }
        }
        for (corner in listOf(ConstPoint(0, 0), ConstPoint(1, 0), ConstPoint(1, 1), ConstPoint(0, 1)))
            assertTrue("$corner", (kit.roomA.id to corner) in stoodOn)
        assertTrue(stoodOn.any { it.first == kit.roomB.id })
    }

    @Test
    fun itsSavedOnItsWay() {
        val kit = kit("TEST_BULWARK_WALKS")
        val pawn = kit.launch(kit.state.enemy!!, 2).bulwark.pawn!!

        // Part way from one square to the next, on its rounds
        var steps = 0
        while (pawn.nextTargetPos == null) {
            kit.state.updateGameState(0.05f)
            assertTrue(++steps < 400)
        }
        repeat(5) { kit.state.updateGameState(0.05f) }
        assertTrue(pawn.nextTargetPos != null && pawn.standingPosition == null)
        val target = pawn.nextTargetPos!!.let { ConstPoint(it.x, it.y) }
        val position = ConstPoint(pawn.screenX, pawn.screenY)

        // It's loaded where it was, and goes on to where it was going
        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        val loadedPawn = loaded.enemy!!.droneSystems.single().drones[0]!!.bulwark.pawn!!
        assertEquals(position, ConstPoint(loadedPawn.screenX, loadedPawn.screenY))
        assertEquals(target, loadedPawn.nextTargetPos?.let { ConstPoint(it.x, it.y) })
        // (arriving on the square it was heading for: its x, as it's going
        // sideways, as its y on the screen is a little off its target's)
        repeat(100) {
            loaded.updateGameState(0.05f)
            if (loadedPawn.nextTargetPos == null && loadedPawn.standingPosition != null && loadedPawn.screenX == target.x)
                return
        }
        error("It didn't get to ${target.x},${target.y}")
    }

    @Test
    fun itsSaved() {
        val kit = kit("TEST_BULWARK_ENEMY")
        kit.launch(kit.state.enemy!!, 3)
        kit.state.updateGameState(0.05f)

        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        val drone = loaded.enemy!!.droneSystems.single().drones[0]!!.bulwark
        assertEquals(3, drone.scale)
        assertEquals(150f * 9, drone.pawn!!.maxHealth)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <crewBlueprint name="battle">
        <cost>1</cost>
    </crewBlueprint>

    <droneBlueprint name="BATTLE">
        <type>BATTLE</type>
        <power>2</power>
        <cost>35</cost>
    </droneBlueprint>

    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <shipBlueprint name="TEST_BULWARK_PLAYER_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <drones power="8" max="8" room="3"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_BULWARK_ENEMY_SHIP" layout="test_big_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>
        <crewCount amount="1" max="1" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="0"/>
            <drones power="8" max="8" room="1"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_BULWARK_WALKS_SHIP" layout="test_bulwark_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>
        <crewCount amount="1" max="1" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="0"/>
            <drones power="8" max="8" room="1"/>
        </systemList>
    </shipBlueprint>

</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <ship name="TEST_BULWARK_ENEMY" blueprint="TEST_BULWARK_ENEMY_SHIP"/>
    <ship name="TEST_BULWARK_WALKS" blueprint="TEST_BULWARK_WALKS_SHIP"/>
</FTL>
"""

        // The anti-personnel drone's animations
        private val ANIMATIONS_XML = (
                listOf("portrait", "repair", "death_right", "teleport", "power_up", "power_down") +
                        listOf("up", "down", "left", "right").flatMap { dir ->
                            listOf("shoot", "walk", "punch", "type", "fire").map { "${it}_$dir" }
                        }
                ).map { "battle_$it" }.plus("fire_large")
            .joinToString("\n", "<FTL>\n", "\n</FTL>") {
                """<anim name="$it"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>"""
            }
    }
}
