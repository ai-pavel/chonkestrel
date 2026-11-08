package xyz.znix.xftl.weapon

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

class MultipleDronesSystemsTest {
    private fun createKit(): InGameTest {
        return InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_TWIN_DRONES")
    }

    @Test
    fun testEachSystemHasItsOwnSlots() {
        val kit = createKit()
        val (first, second) = kit.player.droneSystems

        assertEquals(2, kit.player.droneSystems.size)
        assertSame(first, kit.player.drones)
        assertEquals(2, first.drones.size)
        assertEquals(2, second.drones.size)
    }

    @Test
    fun testNewDronesFillTheSystemsInOrder() {
        val kit = createKit()
        val (first, second) = kit.player.droneSystems

        kit.givePlayerBP("TEST_DRONE")
        kit.givePlayerBP("TEST_DRONE")
        kit.givePlayerBP("TEST_DRONE")

        assertEquals(2, first.drones.count { it != null })
        assertEquals(1, second.drones.count { it != null })
    }

    @Test
    fun testDronesDoNotMoveBetweenSystems() {
        val kit = createKit()
        val (first, second) = kit.player.droneSystems

        kit.givePlayerBP("TEST_DRONE")
        kit.givePlayerBP("TEST_DRONE")
        kit.givePlayerBP("TEST_DRONE")

        // Emptying the first system shouldn't pull drones over from the second
        first.drones[0] = null
        first.drones[1] = null
        kit.player.cargoUpdated()

        assertEquals(0, first.drones.count { it != null })
        assertEquals(1, second.drones.count { it != null })
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_TWIN_DRONES" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <drones power="2" room="3"/>
            <drones power="2" room="5"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <droneBlueprint name="TEST_DRONE">
        <type>REPAIR</type>
        <power>1</power>
    </droneBlueprint>
</FTL>
"""
    }
}
