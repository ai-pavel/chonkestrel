package xyz.znix.xftl.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.systems.Cloaking
import xyz.znix.xftl.systems.SystemBlueprint
import java.io.File

/**
 * The Chonkestrel mod's cloaking system: one that can be upgraded to six
 * bars, at a steep price and for less and less, rather than several that
 * could take it in turns to cloak all the time.
 */
class CloakingUpgradeTest {
    @Test
    fun testItGoesToSixBars() {
        val df = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(File("mods/chonkestrel").toPath())))
        val cloaking = BlueprintManager(df, true)["cloaking"] as SystemBlueprint
        assertEquals(6, cloaking.maxPower)

        // A price for each level after the first, vanilla's for 2 and 3,
        // then each one dearer than the last, by far, but under a thousand
        assertEquals(listOf(30, 50, 150, 300, 600), cloaking.upgradeCost)
        assertTrue(cloaking.upgradeCost.zipWithNext().drop(2).all { (a, b) -> b >= 2 * a })
        assertTrue(cloaking.upgradeCost.all { it < 1000 })
    }

    @Test
    fun testEachBarPastThreeAddsLess() {
        // Vanilla's five seconds a bar, up to three
        assertEquals(listOf(5f, 10f, 15f), (1..3).map { Cloaking.durationFor(it) })

        // Then less and less a bar, so six bars is only 20 seconds
        assertEquals(listOf(17f, 19f, 20f), (4..6).map { Cloaking.durationFor(it) })
        val gains = (1..6).map { Cloaking.durationFor(it) }.zipWithNext { a, b -> b - a }
        assertTrue(gains.drop(2).all { it < Cloaking.TIME_PER_POWER })
    }
}
