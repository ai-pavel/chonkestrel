package xyz.znix.xftl.game

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.XftlDebug
import xyz.znix.xftl.systems.BatteryEnergySource
import xyz.znix.xftl.testkit.InGameTest

class BackupBatteryTest {
    @Test
    fun testBatteriesAddUp() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_TWO_BATTERIES")
        val player = kit.player

        assertEquals(2, player.batterySystems.size)

        player.updateAvailablePower()
        assertEquals(0, player.powerAvailableTypes[BatteryEnergySource] ?: 0)

        // Each level-one battery gives two power, and they add together
        for (battery in player.batterySystems) {
            battery.startBattery()
        }
        player.updateAvailablePower()
        assertEquals(4, player.powerAvailableTypes[BatteryEnergySource])
    }

    @Test
    fun testBatteriesDoNotGoPastTheBiggestReactor() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_TWO_BATTERIES")
        val player = kit.player

        // With one bar of room left, the batteries only fill that one
        player.purchasedReactorPower = player.maxReactorPower - 1
        for (battery in player.batterySystems) {
            battery.startBattery()
        }
        player.updateAvailablePower()
        assertEquals(1, player.powerAvailableTypes[BatteryEnergySource])

        // And with a maxed-out reactor, they give nothing
        player.purchasedReactorPower = player.maxReactorPower
        player.updateAvailablePower()
        assertEquals(0, player.powerAvailableTypes[BatteryEnergySource])
    }

    @Test
    fun testDebugReactorLeavesRoomForTheBatteries() {
        val wasEnabled = XftlDebug.enabled
        XftlDebug.enabled = true
        try {
            val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_TWO_BATTERIES")
            val player = kit.player

            // Two batteries at level two give eight power, which all fits
            assertEquals(XftlDebug.REACTOR_POWER, player.purchasedReactorPower)
            for (battery in player.batterySystems) {
                battery.startBattery()
            }
            player.updateAvailablePower()
            assertEquals(8, player.powerAvailableTypes[BatteryEnergySource])
        } finally {
            XftlDebug.enabled = wasEnabled
        }
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="PLAYER_SHIP_TWO_BATTERIES" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <battery power="1" room="3"/>
            <battery power="1" room="4"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="battery">
        <type>battery</type>
        <startPower>1</startPower>
        <maxPower>2</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
</FTL>
"""
    }
}
