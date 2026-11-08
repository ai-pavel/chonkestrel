package xyz.znix.xftl.drones

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.DroneSizes

/**
 * Boarding drones (and Ion Intruders) never fly at a ship with a Zoltan
 * super shield up, which would destroy them: a combined one's copies all
 * go to the ships without one, and with every ship shielded, none are
 * launched (and no drone parts used) until a shield's down.
 */
class ZoltanBoardingTest {
    private fun kit(): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML),
            extraEvents = listOf(EVENT_XML), playerShip = "TEST_ZOLTAN_PLAYER_SHIP"
        )
        kit.powerUpAll()
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_ZOLTAN_ENEMY"), Difficulty.NORMAL, 0, 1)
        // (the wingmen arrive when the game's updated)
        kit.state.updateGameState(0.01f)
        return kit
    }

    private fun InGameTest.equip(name: String): Drones {
        val system = player.droneSystems.single()
        system.drones[0] = Drones.DroneInfo(state.blueprintManager[name] as DroneBlueprint, null)
        player.dronesCount = 10
        return system
    }

    @Test
    fun withEveryShipShieldedNoneAreLaunched() {
        val kit = kit()
        val ships = listOf(kit.state.enemy!!) + kit.state.wingmen.map { it.ship }
        assertEquals(3, ships.size)
        ships.forEach { it.superShield = 5 }

        for (name in listOf("BOARDER_ION", DroneSizes.nameFor("BOARDER", 3))) {
            val system = kit.equip(name)
            system.setDronePower(0, true)
            assertTrue(name, system.drones[0]!!.liveCopies.isEmpty())
            assertEquals(name, 10, kit.player.dronesCount)
        }

        // Once one's down, they go, all to that ship
        ships[2].superShield = 0
        val system = kit.equip(DroneSizes.nameFor("BOARDER", 3))
        assertTrue(system.setDronePower(0, true))
        val copies = system.drones[0]!!.liveCopies.map { it as BoardingDrone }
        assertEquals(3, copies.size)
        assertTrue(copies.all { it.boardingShip == ships[2] })
    }

    @Test
    fun aTripleGoesToTheShipsWithoutShields() {
        val kit = kit()
        val ships = listOf(kit.state.enemy!!) + kit.state.wingmen.map { it.ship }
        ships[0].superShield = 5

        val system = kit.equip(DroneSizes.nameFor("BOARDER_ION", 3))
        assertTrue(system.setDronePower(0, true))
        val targets = system.drones[0]!!.liveCopies.map { (it as BoardingDrone).boardingShip }
        assertEquals(3, targets.size)
        assertTrue(targets.none { it == ships[0] })
        // (one to each of the others, and the third doubling up)
        assertEquals(setOf(ships[1], ships[2]), targets.toSet())
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

    <shipBlueprint name="TEST_ZOLTAN_PLAYER_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <shipSize>2</shipSize>
        <opponentShips>3</opponentShips>
        <droneSlots>2</droneSlots>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <drones power="8" max="8" room="3"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_ZOLTAN_ENEMY_SHIP" layout="test_ship" img="test_ship">
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
    <ship name="TEST_ZOLTAN_ENEMY" blueprint="TEST_ZOLTAN_ENEMY_SHIP"/>
</FTL>
"""

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
