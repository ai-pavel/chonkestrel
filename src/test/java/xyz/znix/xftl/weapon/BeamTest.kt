package xyz.znix.xftl.weapon

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.systems.SelectedTarget
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.BeamBlueprint

open class BeamTest {
    @Test
    fun testStartTime() {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML),
            extraAnimations = listOf(ANIMATIONS_XML),
        )

        kit.givePlayerBP("LATE_FIRE")
        kit.powerUpAll()

        // Let the weapon charge
        kit.updateSingle(100f)

        val beam = kit.player.hardpoints[0].weapon as BeamBlueprint.BeamInstance
        val aim = SelectedTarget.BeamAim(beam, 0, kit.player, ConstPoint.ZERO)
        kit.player.weapons!!.selectedTargets.targetBeam(aim.weaponNumber, aim)

        // Make sure the beam starts firing at the correct time
        // If the whole animation (including the charge and firing frames) was
        // played back, it'd take 2sec. This sets the per-frame time.
        val frameTime = 2f / 10
        val preFireAnimationLength = 5 - 1 // fireFrame - chargedFrame
        kit.updateSingle(frameTime * preFireAnimationLength - 0.0001f)

        assertEquals(false, beam.isBeamOn)
        assertEquals(true, beam.isFiring)

        // Just cross over into the firing frame
        kit.updateSingle(0.0002f)

        assertEquals(true, beam.isBeamOn)
        assertEquals(true, beam.isFiring)
    }
}

@Language("XML")
private val BLUEPRINT_XML: String = """
    <?xml version="1.0" encoding="UTF-8" ?>
    <FTL>
    <weaponBlueprint name="LATE_FIRE">
        <type>BEAM</type>
        <length>1</length>
        <weaponArt>late_fire</weaponArt>
        <image>dummy_anim</image>
    </weaponBlueprint>
    </FTL>
""".trimIndent()

@Language("XML")
private val ANIMATIONS_XML: String = """
    <?xml version="1.0" encoding="UTF-8" ?>
    <FTL>
    <weaponAnim name="late_fire">
        <sheet>dummy_sheet</sheet>
        <desc length="10" x="0" y="0"/>
        <chargedFrame>1</chargedFrame>
        <fireFrame>5</fireFrame>
        <firePoint  x="0" y="0"/>
        <mountPoint x="0" y="0"/>
    </weaponAnim>
    </FTL>
""".trimIndent()
