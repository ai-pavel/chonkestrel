package xyz.znix.xftl.drones

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.DroneBlueprint

/**
 * Behind an enemy's locked doors (a doors system at level 2 or more), the
 * player's boarding drones and Ion Intruders don't walk through: they stop
 * at the door and shoot at it until it breaks, then go on to the system.
 * And the later enemies always have such doors.
 */
class DoorBashingTest {
    private fun kit(enemy: String, sector: Int = 0): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML),
            extraEvents = listOf(EVENT_XML), playerShip = "TEST_DOORS_PLAYER_SHIP"
        )
        kit.powerUpAll()
        kit.state.debugSpawnShip(kit.state.eventManager.getShip(enemy), Difficulty.NORMAL, sector, 1)
        return kit
    }

    // Launch [name] at the locked enemy, until it lands in one of the small
    // rooms without a system, behind a door from the pilot's; then whether
    // it shot at the door, and got through to that room
    private fun bashesThrough(name: String): Boolean {
        repeat(8) {
            val kit = kit("TEST_DOORS_ENEMY")
            val enemy = kit.state.enemy!!
            // Its crew stay put in the system's room, so they don't open
            // the doors on their way to fight the drone
            kit.state.debugFlags.noCrewAI.set = true
            for (crew in enemy.crew.toList())
                crew.jumpTo(enemy.findSpaceForCrew(enemy.rooms[5], AbstractCrew.SlotType.CREW))
            val system = kit.player.droneSystems.single()
            kit.player.dronesCount = 10
            system.drones[0] = Drones.DroneInfo(kit.state.blueprintManager[name] as DroneBlueprint, null)
            assertTrue(system.setDronePower(0, true))
            val drone = system.drones[0]!!.instance as BoardingDrone

            var landed: Int? = null
            var bashed = false
            var through = false
            repeat(1600) {
                kit.state.updateGameState(0.05f)
                for (ship in listOf(kit.player, enemy))
                    ship.rooms.forEach { it.oxygen = 1f }
                val pawn = drone.pawn ?: return@repeat
                if (landed == null)
                    landed = pawn.room.id
                if (pawn.currentAction == AbstractCrew.Action.ATTACKING_DOOR)
                    bashed = true
                if (bashed && pawn.room == enemy.rooms[5])
                    through = true
            }
            // (landing in the pilot's room, or the doors system's, where it
            // breaks that, it's nothing to get through)
            if (landed != null && landed in 0..3)
                return bashed && through
        }
        error("It never landed in a small room")
    }

    @Test
    fun aBoardingDroneShootsThroughTheDoor() {
        assertTrue(bashesThrough("BOARDER"))
    }

    @Test
    fun anIonIntruderShootsThroughTheDoor() {
        assertTrue(bashesThrough("BOARDER_ION"))
    }

    @Test
    fun theLaterEnemiesAlwaysHaveLockedDoors() {
        // Early on, it's up to chance; late in the game, they always have
        // them, at level 3
        val late = kit("TEST_DOORS_OPTIONAL_ENEMY", sector = 7).state.enemy!!
        assertNotNull(late.doorsSystem)
        assertEquals(3, late.doorsSystem!!.energyLevels)

        val mid = kit("TEST_DOORS_OPTIONAL_ENEMY", sector = 4).state.enemy!!
        assertTrue(mid.doorsSystem!!.energyLevels >= 2)
    }

    @Test
    fun aShipWithNoRoomForThemGoesWithout() {
        val late = kit("TEST_DOORS_NO_SLOT_ENEMY", sector = 7).state.enemy!!
        assertNull(late.doorsSystem)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <crewBlueprint name="battle">
        <cost>1</cost>
    </crewBlueprint>

    <droneBlueprint name="BOARDER">
        <type>BOARDER</type>
        <power>3</power>
        <speed>18</speed>
    </droneBlueprint>

    <droneBlueprint name="BOARDER_ION">
        <type>BOARDER</type>
        <power>3</power>
        <speed>18</speed>
    </droneBlueprint>

    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <systemBlueprint name="doors">
        <type>doors</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <shipBlueprint name="TEST_DOORS_PLAYER_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <drones power="8" max="8" room="3"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_DOORS_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="1" max="1" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="5"/>
            <doors power="3" max="3" room="4"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_DOORS_OPTIONAL_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="1" max="1" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="5"/>
            <doors power="1" max="3" room="4" start="false"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_DOORS_NO_SLOT_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="1" max="1" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="5"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <ship name="TEST_DOORS_ENEMY" blueprint="TEST_DOORS_ENEMY_SHIP"/>
    <ship name="TEST_DOORS_OPTIONAL_ENEMY" blueprint="TEST_DOORS_OPTIONAL_ENEMY_SHIP"/>
    <ship name="TEST_DOORS_NO_SLOT_ENEMY" blueprint="TEST_DOORS_NO_SLOT_ENEMY_SHIP"/>
</FTL>
"""

        // The boarding drone's and the Ion Intruder's animations
        private val ANIMATIONS_XML = (
                listOf("portrait", "repair", "death_right", "teleport", "power_up", "power_down", "ion", "glow", "attack") +
                        listOf("up", "down", "left", "right").flatMap { dir ->
                            listOf("shoot", "walk", "punch", "type", "fire").map { "${it}_$dir" }
                        }
                ).flatMap { listOf("battle_$it", "boarder_ion_$it") }.plus("explosion_small_ion")
            .joinToString("\n", "<FTL>\n", "\n</FTL>") {
                """<anim name="$it"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>"""
            }
    }
}
