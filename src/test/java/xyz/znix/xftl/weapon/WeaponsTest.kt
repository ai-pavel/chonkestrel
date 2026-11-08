package xyz.znix.xftl.weapon

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

class WeaponsTest {
    @Test
    fun testPowerManagement() {
        val kit = InGameTest()

        kit.givePlayerBP("TEST_LASER")
        kit.givePlayerBP("TEST_BEAM")
        kit.upgradeAll()
        kit.powerUpAll()

        val laser = kit.player.hardpoints[0].weapon!!
        val beam = kit.player.hardpoints[1].weapon!!

        val weapons = kit.player.weapons!!

        assertEquals(2, weapons.powerSelected)
        assertEquals(true, laser.isPowered)
        assertEquals(true, beam.isPowered)

        // Use up all the buffer points
        weapons.dealDamage(weapons.energyLevels - weapons.powerSelected, 0)
        kit.updateSingle(0f)

        assertEquals(2, weapons.powerSelected)
        assertEquals(true, laser.isPowered)
        assertEquals(true, beam.isPowered)

        // Knock the second weapon (the beam) offline
        weapons.dealDamage(1, 0)
        kit.updateSingle(0f)

        assertEquals(1, weapons.powerSelected)
        assertEquals(true, laser.isPowered)
        assertEquals(false, beam.isPowered)

        weapons.dealDamage(1, 0)
        kit.updateSingle(0f)

        assertEquals(0, weapons.powerSelected)
        assertEquals(false, laser.isPowered)
        assertEquals(false, beam.isPowered)
    }
}
