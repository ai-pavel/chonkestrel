package xyz.znix.xftl.game

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint

/**
 * The store only sells what the ship has room for (Ship.hasRoomFor): a
 * drone needs a drone slot or the cargo, whatever weapon slots are free.
 */
class StoreRoomTest {
    @Test
    fun aDroneNeedsADroneSlotOrTheCargo() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML))
        val ship = kit.player
        val drone = kit.state.blueprintManager["TEST_STORE_DRONE"] as DroneBlueprint
        val weapon = kit.state.blueprintManager["TEST_LASER"] as AbstractWeaponBlueprint
        assertTrue(ship.droneSystems.isEmpty())

        // With a weapon slot free but the cargo full, a weapon fits and a
        // drone doesn't (this used to sell it, and then crash)
        ship.hardpoints.forEach { it.weapon = null }
        for (slot in ship.cargoBlueprints.indices)
            ship.cargoBlueprints[slot] = weapon
        assertTrue(ship.hasRoomFor(weapon))
        assertFalse(ship.hasRoomFor(drone))
        assertFalse(ship.addBlueprint(drone, false))

        // Room in the cargo takes either
        ship.cargoBlueprints[0] = null
        assertTrue(ship.hasRoomFor(drone))
        assertTrue(ship.addBlueprint(drone, false))
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <droneBlueprint name="TEST_STORE_DRONE"><type>COMBAT</type><power>1</power></droneBlueprint>
</FTL>
"""
    }
}
