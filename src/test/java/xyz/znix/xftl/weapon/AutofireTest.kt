package xyz.znix.xftl.weapon

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.systems.SelectedTarget
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.BeamBlueprint

/**
 * Check weapons on autofire keep firing at their target each time they're
 * charged, and the others fire once and forget it.
 */
class AutofireTest {
    private class Result(val shots: Int, val target: SelectedTarget?)

    /**
     * Aim a beam at the player's own ship, and count how many times it
     * fires in half a minute.
     */
    private fun fireFor30Seconds(autofire: Boolean): Result {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML),
            extraAnimations = listOf(ANIMATIONS_XML),
        )
        kit.givePlayerBP("AUTOFIRE_BEAM")
        kit.powerUpAll()

        val beam = kit.player.hardpoints[0].weapon as BeamBlueprint.BeamInstance
        val targets = kit.player.weapons!!.selectedTargets
        val aim = SelectedTarget.BeamAim(beam, 0, kit.player, ConstPoint.ZERO)
        aim.autofire = autofire
        targets.targetBeam(aim.weaponNumber, aim)

        // Each shot empties the charge
        var shots = 0
        var lastCharge = beam.chargeProgress
        repeat(300) {
            kit.updateSingle(0.1f)
            if (beam.chargeProgress < lastCharge)
                shots++
            lastCharge = beam.chargeProgress
        }
        return Result(shots, targets.getTarget(0))
    }

    @Test
    fun keepsFiringOnAutofire() {
        val result = fireFor30Seconds(autofire = true)
        // It charges for 5 seconds, then fires for 2
        assertTrue("Fired ${result.shots} times", result.shots >= 4)
        assertNotNull(result.target)
    }

    @Test
    fun firesOnceWithoutAutofire() {
        val result = fireFor30Seconds(autofire = false)
        assertEquals(1, result.shots)
        assertNull(result.target)
    }
}

@Language("XML")
private val BLUEPRINT_XML: String = """
    <?xml version="1.0" encoding="UTF-8" ?>
    <FTL>
    <weaponBlueprint name="AUTOFIRE_BEAM">
        <type>BEAM</type>
        <length>1</length>
        <weaponArt>autofire_beam</weaponArt>
        <image>dummy_anim</image>
    </weaponBlueprint>
    </FTL>
""".trimIndent()

@Language("XML")
private val ANIMATIONS_XML: String = """
    <?xml version="1.0" encoding="UTF-8" ?>
    <FTL>
    <weaponAnim name="autofire_beam">
        <sheet>dummy_sheet</sheet>
        <desc length="10" x="0" y="0"/>
        <chargedFrame>1</chargedFrame>
        <fireFrame>5</fireFrame>
        <firePoint  x="0" y="0"/>
        <mountPoint x="0" y="0"/>
    </weaponAnim>
    </FTL>
""".trimIndent()
