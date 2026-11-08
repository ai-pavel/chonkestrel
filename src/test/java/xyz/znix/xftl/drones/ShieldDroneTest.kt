package xyz.znix.xftl.drones

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.DroneBlueprint

class ShieldDroneTest {
    private fun createKit(): InGameTest {
        return InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML),
            playerShip = "TEST_SHIP_DRONES"
        )
    }

    private fun deploy(kit: InGameTest): ShieldDrone {
        val type = kit.state.blueprintManager["TEST_SHIELD_DRONE"] as DroneBlueprint
        val drone = type.makeInstance()
        assertTrue("Got ${drone.javaClass.simpleName}", drone is ShieldDrone)
        drone.init(kit.player)
        drone.isPowered = true
        return drone as ShieldDrone
    }

    private fun run(drone: ShieldDrone, seconds: Float) {
        repeat((seconds * 10).toInt()) { drone.update(0.1f) }
    }

    @Test
    fun testAddsASuperShieldPointEveryCharge() {
        val kit = createKit()
        val player = kit.player
        player.superShield = 0
        val drone = deploy(kit)

        run(drone, 14.5f)
        assertEquals(0, player.superShield)
        run(drone, 1f)
        assertEquals(1, player.superShield)
        run(drone, 15f)
        assertEquals(2, player.superShield)
    }

    @Test
    fun testEachDroneRechargesOneRow() {
        val kit = createKit()
        val player = kit.player
        player.superShield = 0
        val drone = deploy(kit)

        // One drone only recharges a row of five
        assertEquals(5, player.maxSuperShield)
        run(drone, 200f)
        assertEquals(5, player.superShield)

        // Once a point is knocked off, it's replaced straight away, as it's
        // been charged up all this time
        player.superShield--
        run(drone, 0.2f)
        assertEquals(5, player.superShield)
    }

    @Test
    fun testDronesAddRows() {
        val kit = createKit()
        val player = kit.player
        player.superShield = 0
        val drones = List(3) { deploy(kit) }

        // Three drones, three rows
        assertEquals(15, player.maxSuperShield)
        repeat(1000) { drones.forEach { it.update(0.1f) } }
        assertEquals(15, player.superShield)

        // Turning a drone off loses its row
        drones[0].isPowered = false
        player.update(0.01f)
        assertEquals(10, player.maxSuperShield)
        assertEquals(10, player.superShield)

        // And so does losing one
        drones[1].removeInstance()
        player.update(0.01f)
        assertEquals(5, player.maxSuperShield)
        assertEquals(5, player.superShield)

        // The last drone only recharges its own row
        player.superShield = 0
        repeat(1000) { drones[2].update(0.1f) }
        assertEquals(5, player.superShield)

        // Turning one back on brings its row back, empty, to recharge
        drones[0].isPowered = true
        assertEquals(10, player.maxSuperShield)
        repeat(1000) { drones.forEach { it.update(0.1f) } }
        assertEquals(10, player.superShield)
    }

    @Test
    fun testDoesNotRechargeZoltanRows() {
        val kit = createKit()
        val player = kit.player
        repeat(3) { player.augments.add(kit.state.blueprintManager["ENERGY_SHIELD"] as AugmentBlueprint) }
        player.superShield = 2
        val drone = deploy(kit)

        // The Zoltan shields' three rows stay, but the drone only fills one
        assertEquals(15, player.maxSuperShield)
        run(drone, 200f)
        assertEquals(5, player.superShield)
    }

    @Test
    fun testDoesNothingUnpowered() {
        val kit = createKit()
        val player = kit.player
        player.superShield = 0
        val drone = deploy(kit)
        drone.isPowered = false

        run(drone, 30f)
        assertEquals(0, player.superShield)
    }

    @Test
    fun testDeploysOutOfCombat() {
        // Drones that don't need an enemy ship can be launched without one
        val kit = createKit()
        val player = kit.player
        player.dronesCount = 5
        kit.powerUpAll()
        val system = player.droneSystems.single()
        system.drones[0] = xyz.znix.xftl.systems.Drones.DroneInfo(
            kit.state.blueprintManager["TEST_SHIELD_DRONE"] as DroneBlueprint, null
        )
        assertTrue(!kit.state.isInDanger)
        assertTrue(system.setDronePower(0, true))
        repeat(5) { kit.updateSingle(0.1f) }
        assertTrue(system.drones[0]!!.instance?.isPowered == true)
        assertEquals(4, player.dronesCount)
    }

    @Test
    fun testDefenceDroneDeploysInAnAsteroidField() {
        // Out of combat, in an asteroid field, a defence drone can be
        // launched (to shoot the asteroids)
        val kit = createKit()
        val player = kit.player
        player.dronesCount = 5
        kit.powerUpAll()
        val beacon = kit.state.currentBeacon
        beacon.sectorHazards = listOf(xyz.znix.xftl.sector.Beacon.EnvironmentType.ASTEROID)
        beacon.clearEnvironment()
        assertTrue(kit.state.isInDanger)

        val type = kit.state.blueprintManager["TEST_DEFENSE_DRONE"] as DroneBlueprint
        type.finishSetup(kit.state.content)
        val system = player.droneSystems.single()
        system.drones[0] = xyz.znix.xftl.systems.Drones.DroneInfo(type, null)
        assertTrue(system.setDronePower(0, true))
        repeat(20) { kit.updateSingle(0.1f) }
        assertTrue(system.drones[0]!!.instance?.isPowered == true)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_DRONES" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <drones power="2" room="3"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <augBlueprint name="ENERGY_SHIELD">
        <cost>1</cost>
        <stackable>true</stackable>
    </augBlueprint>

    <weaponBlueprint name="TEST_DRONE_LASER">
        <type>LASER</type>
        <damage>1</damage>
        <shots>1</shots>
        <cooldown>1</cooldown>
        <power>1</power>
        <speed>40</speed>
        <image>laser_light1</image>
        <weaponArt>laser_burst_1</weaponArt>
        <cost>1</cost>
    </weaponBlueprint>

    <droneBlueprint name="TEST_DEFENSE_DRONE">
        <type>DEFENSE</type>
        <power>1</power>
        <cooldown>1000</cooldown>
        <speed>5</speed>
        <target>DRONES</target>
        <weaponBlueprint>TEST_DRONE_LASER</weaponBlueprint>
        <droneImage>drone_defense</droneImage>
    </droneBlueprint>

    <droneBlueprint name="TEST_SHIELD_DRONE">
        <type>SHIELD</type>
        <power>2</power>
        <cooldown>15000</cooldown>
        <speed>5</speed>
        <droneImage>drone_shield</droneImage>
    </droneBlueprint>
</FTL>
"""

        // The animations a drone needs to explode
        @Language("XML")
        private val ANIMATIONS_XML = (2..4).joinToString("\n", "<FTL>\n", "\n</FTL>") {
            """<anim name="explosion_big$it"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>"""
        }
    }
}
