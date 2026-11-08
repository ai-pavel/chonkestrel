package xyz.znix.xftl.projectile

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.Ship
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.AbstractProjectile
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint

/**
 * Makes sure collisions that would only be detected with CCD indeed are.
 */
class CCDTest {
    @Test
    fun testSimpleCCD() {
        // Fire projectiles at right-angles, crossing between frames.
        val expectedHits = runTest { a, b ->
            a.setInitialPath(ConstPoint(250, 0), ConstPoint(250, 250))
            b.setInitialPath(ConstPoint(0, 250), ConstPoint(250, 250))
        }

        // The projectiles should have hit each other
        assertEquals(2, expectedHits)

        // Fire projectiles in parallel next to each other, where they
        // don't hit each other.
        val expectedMiss = runTest { a, b ->
            a.setInitialPath(ConstPoint(250, 0), ConstPoint(250, 0))
            b.setInitialPath(ConstPoint(250, 2), ConstPoint(250, 2))
        }
        assertEquals(0, expectedMiss)
    }

    private fun runTest(setup: (DummyProjectile, DummyProjectile) -> Unit): Int {
        val kit = InGameTest()

        val speed = 100 // px/sec

        var hits = 0
        val a = DummyProjectile(speed, null) { hits++ }
        val b = DummyProjectile(speed, kit.player) { hits++ }

        setup(a, b)

        kit.player.projectiles.add(a)
        kit.player.projectiles.add(b)

        // Long enough to fly off a 1920x1080 screen, as in the player's space
        // they go to its edge
        for (i in 1..25) {
            kit.updateSingle(1f)
        }

        // Whether they hit of missed and flew offscreen, the projectiles
        // should long since be gone.
        assertEquals(0, kit.player.projectiles.size)

        return hits
    }
}

private class DummyProjectile(
    override val speed: Int,
    targetShip: Ship?,
    val onHit: () -> Unit
) : AbstractProjectile(targetShip) {

    override val antiDroneBP: AbstractWeaponBlueprint? get() = null
    override val antiDroneExemption: Ship? get() = null

    override val hitboxRadius: Int get() = 1

    override fun reachedTarget() {
    }

    override fun hitOtherProjectile(currentSpace: Ship) {
        onHit()
    }

    // Not used by the test
    override val serialisationType: String
        get() {
            throw UnsupportedOperationException()
        }

    override fun renderPreTranslated(g: Graphics) {
        throw UnsupportedOperationException()
    }
}
