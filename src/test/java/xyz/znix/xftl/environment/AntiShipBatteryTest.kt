package xyz.znix.xftl.environment

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.testkit.InGameTest

/**
 * An event's `<environment type="PDS"/>` gives its beacon an Anti-Ship
 * Battery, which fires at the ships it's told to.
 */
class AntiShipBatteryTest {
    private val kit = InGameTest(
        extraBlueprints = listOf(BLUEPRINT_XML), extraEvents = listOf(EVENT_XML), extraAnimations = listOf(FIRE_XML)
    )

    private fun enter(event: String): Beacon {
        val beacon = kit.state.currentBeacon
        beacon.event = kit.state.eventManager[event] as Event
        beacon.clearEnvironment()
        return beacon
    }

    // How many of the battery's shots are on their way to the ship
    private fun shotsAt(ship: xyz.znix.xftl.Ship) =
        (listOf(kit.player) + kit.state.allEnemyShips).sumOf { s -> s.projectiles.count { it is AsbProjectile && it.targetShip == ship } }

    @Test
    fun testEventsHaveBatteries() {
        assertEquals(AntiShipBattery.Target.PLAYER, enter("ASB_PLAYER").antiShipBatteryTarget)
        assertEquals(AntiShipBattery.Target.ENEMY, enter("ASB_ENEMY").antiShipBatteryTarget)
        assertEquals(AntiShipBattery.Target.ALL, enter("ASB_ALL").antiShipBatteryTarget)
        assertNull(enter("NO_ASB").antiShipBatteryTarget)
        assertNull(kit.state.currentBeacon.getAntiShipBattery(kit.state))

        // One firing at the player is a danger, and one on their side isn't
        assertTrue(enter("ASB_PLAYER").isDangerous)
        assertFalse(enter("ASB_ENEMY").isDangerous)
    }

    @Test
    fun testTheFleetsBeaconsHaveTheFleetsBattery() {
        // Once overtaken, a beacon's own battery goes, and it has the
        // fleet's, once the player's met the fleet there
        val beacon = enter("ASB_ENEMY")
        beacon.isOvertaken = true
        assertNull(beacon.antiShipBatteryTarget)
        beacon.fleetBatteryTarget = AntiShipBattery.Target.PLAYER
        assertEquals(AntiShipBattery.Target.PLAYER, beacon.getAntiShipBattery(kit.state)?.target)
        assertTrue(beacon.isDangerous)

        // And both are saved
        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        assertTrue(loaded.currentBeacon.isOvertaken)
        assertEquals(AntiShipBattery.Target.PLAYER, loaded.currentBeacon.antiShipBatteryTarget)
    }

    @Test
    fun testItGoesThroughAZoltanShieldAndBreaches() {
        enter("ASB_PLAYER")
        val shield = kit.state.blueprintManager["ENERGY_SHIELD"] as AugmentBlueprint
        kit.player.augments.add(shield)
        kit.player.superShield = AugZoltanShield.SHIELD_PER_AUGMENT
        val health = kit.player.health

        // Until its first shot hits
        var shots = 0
        repeat(1500) {
            if (kit.player.health < health)
                return@repeat
            kit.state.updateGameState(0.1f)
            kit.player.rooms.forEach { it.oxygen = 1f }
            shots = maxOf(shots, shotsAt(kit.player))
        }
        assertTrue("shots $shots, super ${kit.player.superShield}/${kit.player.maxSuperShield}, evasion ${kit.player.evasion}",
            kit.player.health < health)
        assertEquals(AugZoltanShield.SHIELD_PER_AUGMENT, kit.player.superShield)
        assertTrue(kit.player.rooms.any { room -> room.breaches.any { it != null } })
    }

    @Test
    fun testItFiresAtThePlayer() {
        enter("ASB_PLAYER")
        var fired = false
        repeat((AntiShipBattery.FIRST_SHOT_TIME * 10).toInt() + 5) {
            kit.state.updateGameState(0.1f)
            fired = fired || shotsAt(kit.player) > 0
        }
        assertTrue(fired)
    }

    @Test
    fun testOnThePlayersSideItFiresAtTheEnemy() {
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_ASB_ENEMY"), Difficulty.NORMAL, 0, 1)
        val enemy = kit.state.enemy!!
        enter("ASB_ENEMY")
        var atEnemy = false
        repeat((AntiShipBattery.FIRST_SHOT_TIME * 10).toInt() + 5) {
            kit.state.updateGameState(0.1f)
            // (with air for the enemy's crew, as the test ship has no oxygen)
            enemy.rooms.forEach { room -> room.oxygen = 1f }
            assertEquals(0, shotsAt(kit.player))
            atEnemy = atEnemy || shotsAt(enemy) > 0
        }
        assertTrue(atEnemy)
    }

    @Test
    fun testItsSaved() {
        enter("ASB_PLAYER")
        repeat(30) { kit.state.updateGameState(0.1f) }

        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        val battery = loaded.currentBeacon.getAntiShipBattery(loaded)
        assertNotNull(battery)
        assertEquals(AntiShipBattery.Target.PLAYER, battery!!.target)
    }

    companion object {
        // The fire a shot may start
        @Language("XML")
        private const val FIRE_XML = """
<FTL>
    <anim name="fire_large"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>
</FTL>
"""

        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <augBlueprint name="ENERGY_SHIELD">
        <cost>1</cost>
        <stackable>true</stackable>
    </augBlueprint>
    <shipBlueprint name="TEST_ASB_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="2" max="2" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <event name="ASB_PLAYER"><environment type="PDS" target="player"/></event>
    <event name="ASB_ENEMY"><environment type="PDS" target="enemy"/></event>
    <event name="ASB_ALL"><environment type="PDS" target="all"/></event>
    <event name="NO_ASB"/>
    <ship name="TEST_ASB_ENEMY" blueprint="TEST_ASB_ENEMY_SHIP"/>
</FTL>
"""
    }
}
