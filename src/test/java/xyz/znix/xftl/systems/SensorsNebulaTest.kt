package xyz.znix.xftl.systems

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.game.SystemButtonTooltip
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.testkit.InGameTest

/**
 * Check the sensors show they're blocked in a nebula: a blue icon, crossed
 * out bars, and a line in their tooltip.
 */
class SensorsNebulaTest {
    @Test
    fun sensorsShowTheyreBlockedInANebula() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_SENSORS")
        val sensors = kit.player.sensors!!
        val beacon = kit.state.currentBeacon
        val nebulaLine = "-" + kit.state.translator["xftl_sensors_nebula"]

        assertFalse(sensors.isInNebula)
        assertEquals("green", sensors.iconColourName)
        assertFalse(SystemButtonTooltip.textFor(kit.state, sensors).lines().contains(nebulaLine))

        // Any kind of nebula blocks them, even one added to the beacon
        beacon.sectorHazards = listOf(Beacon.EnvironmentType.DARK)
        beacon.clearEnvironment()
        assertTrue(sensors.isInNebula)
        assertTrue(sensors.blockedByEnvironment)
        assertFalse(sensors.providesPlayerVision)
        assertEquals("blue", sensors.iconColourName)
        assertTrue(SystemButtonTooltip.textFor(kit.state, sensors).lines().contains(nebulaLine))
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_SENSORS" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <sensors power="1" room="3"/>
        </systemList>
    </shipBlueprint>
    <systemBlueprint name="sensors">
        <type>sensors</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
</FTL>
"""
    }
}
