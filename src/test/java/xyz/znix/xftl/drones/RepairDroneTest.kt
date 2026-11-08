package xyz.znix.xftl.drones

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.DroneBlueprint

/**
 * The system repair drone goes to the rooms with fires and hull breaches in
 * them, not only those with damaged systems, and fixes them.
 */
class RepairDroneTest {
    private fun createKit(): InGameTest {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML),
            playerShip = "TEST_SHIP_REPAIR_DRONE")
        val type = kit.state.blueprintManager["TEST_REPAIR_DRONE"] as DroneBlueprint
        val drone = type.makeInstance()
        assertTrue("Got ${drone.javaClass.simpleName}", drone is RepairDrone)
        drone.init(kit.player)
        drone.isPowered = true
        return kit
    }

    // A room without a system, away from the drones room
    private fun InGameTest.emptyRoom() = player.rooms.first { it.system == null }

    private fun InGameTest.run(seconds: Float) = repeat((seconds * 10).toInt()) { state.updateGameState(0.1f) }

    @Test
    fun testPutsOutFires() {
        val kit = createKit()
        val room = kit.emptyRoom()
        room.spawnFireAt(0)
        // The fire would otherwise spread, and the air run out before the
        // drone gets there
        kit.player.oxygen?.let { repeat(it.energyLevels) { _ -> it.increasePower() } }

        kit.run(120f)
        assertTrue(room.fires.all { it == null })
    }

    @Test
    fun testPatchesBreaches() {
        val kit = createKit()
        val room = kit.emptyRoom()
        room.spawnBreachAt(0)

        kit.run(60f)
        assertTrue(room.breaches.all { it == null })
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_REPAIR_DRONE" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <drones power="2" room="3"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <!-- The drone's pawn -->
    <crewBlueprint name="repair">
        <cost>1</cost>
    </crewBlueprint>

    <droneBlueprint name="TEST_REPAIR_DRONE">
        <type>REPAIR</type>
        <power>1</power>
    </droneBlueprint>
</FTL>
"""

        // The pawn's animations, as the test crew's, and the fire's
        private val ANIMATIONS_XML = (
                listOf("portrait", "repair", "death_right", "teleport", "power_up", "power_down") +
                        listOf("up", "down", "left", "right").flatMap { dir ->
                            listOf("shoot", "walk", "punch", "type", "fire").map { "${it}_$dir" }
                        }
                ).map { "repair_$it" }.plus("fire_large").joinToString("\n", "<FTL>\n", "\n</FTL>") {
                """<anim name="$it"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>"""
            }
    }
}
