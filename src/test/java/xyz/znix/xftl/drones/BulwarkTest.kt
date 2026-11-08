package xyz.znix.xftl.drones

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.DroneSizes

/**
 * The bulwarks (see doc/game/drone-sizes.md): an enemy's twin or triple
 * anti-personnel drone is one big drone, standing guard on a square of
 * cells in a room big enough for it. The player's is a squad.
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
        val blueprint = state.blueprintManager[DroneSizes.nameFor("BATTLE", size)] as DroneBlueprint
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
        // room, there's no square free for a triple
        assertFalse(AntiPersonnelDrone.bulwarkFits(enemy, 4))
        kit.addCrew(enemy, enemy.rooms[2])
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

</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <ship name="TEST_BULWARK_ENEMY" blueprint="TEST_BULWARK_ENEMY_SHIP"/>
</FTL>
"""

        // The anti-personnel drone's animations
        private val ANIMATIONS_XML = (
                listOf("portrait", "repair", "death_right", "teleport", "power_up", "power_down") +
                        listOf("up", "down", "left", "right").flatMap { dir ->
                            listOf("shoot", "walk", "punch", "type", "fire").map { "${it}_$dir" }
                        }
                ).map { "battle_$it" }
            .joinToString("\n", "<FTL>\n", "\n</FTL>") {
                """<anim name="$it"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>"""
            }
    }
}
