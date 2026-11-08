package xyz.znix.xftl.drones

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.DroneSizes

/**
 * A boarding drone breaks the enemy's systems by shooting at them with its
 * little laser, as vanilla's does, not with its fists (it can't punch), at
 * any size (see doc/game/drone-sizes.md).
 */
class BoardingDroneSabotageTest {
    // Whether, launched once, it got to breaking a system; it must never
    // punch, whatever it's doing
    private fun sabotages(size: Int): Boolean {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML),
            extraEvents = listOf(EVENT_XML), playerShip = "TEST_SABOTAGE_PLAYER_SHIP"
        )
        kit.powerUpAll()
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_SABOTAGE_ENEMY"), Difficulty.NORMAL, 0, 1)
        val enemy = kit.state.enemy!!
        // Its crew keep out of the way, in a room without a system
        kit.state.debugFlags.noCrewAI.set = true
        for (crew in enemy.crew.toList())
            crew.jumpTo(enemy.findSpaceForCrew(enemy.rooms[0], AbstractCrew.SlotType.CREW))
        val system = kit.player.droneSystems.single()
        kit.player.dronesCount = 10
        val name = if (size == 1) "BOARDER" else DroneSizes.nameFor("BOARDER", size)
        system.drones[0] = Drones.DroneInfo(kit.state.blueprintManager[name] as DroneBlueprint, null)
        assertTrue(system.setDronePower(0, true))
        val info = system.drones[0]!!

        var sabotaged = false
        repeat(1200) {
            kit.state.updateGameState(0.05f)
            for (ship in listOf(kit.player, enemy))
                ship.rooms.forEach { it.oxygen = 1f }
            for (drone in info.liveCopies) {
                val pawn = (drone as BoardingDrone).pawn ?: continue
                assertFalse(pawn.icon.spec.name, pawn.icon.spec.name.contains("punch"))
                if (pawn.currentAction == AbstractCrew.Action.SABOTAGE)
                    sabotaged = true
            }
        }
        return sabotaged
    }

    // (it lands in a random room, which may be the crew's, so it's given a
    // few goes at getting to a system)
    private fun checkItShoots(size: Int) {
        assertTrue((1..5).any { sabotages(size) })
    }

    @Test
    fun aSingleOneShootsAtTheSystem() = checkItShoots(1)

    @Test
    fun aTripleOnesCopiesShootToo() = checkItShoots(3)

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

    <shipBlueprint name="TEST_SABOTAGE_PLAYER_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <drones power="8" max="8" room="3"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <shipBlueprint name="TEST_SABOTAGE_ENEMY_SHIP" layout="test_ship" img="test_ship">
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
    <ship name="TEST_SABOTAGE_ENEMY" blueprint="TEST_SABOTAGE_ENEMY_SHIP"/>
</FTL>
"""

        // The boarding drone's animations (vanilla's are the anti-personnel
        // drone's, "battle"), punching ones included, as vanilla's has
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
