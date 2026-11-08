package xyz.znix.xftl.drones

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.game.BossRewards
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.DroneSizes

/**
 * The combined drones (see [DroneSizes] and doc/game/drone-sizes.md): two
 * or three of a drone launched from one slot, a drone part each.
 */
class CombinedDroneTest {
    private fun kit(): InGameTest {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_DRONE_SIZES")
        kit.powerUpAll()
        return kit
    }

    private fun InGameTest.drone(size: Int) =
        state.blueprintManager[DroneSizes.nameFor("TEST_SIZED_DRONE", size)] as DroneBlueprint

    private fun InGameTest.equip(blueprint: DroneBlueprint): Drones {
        val drones = player.droneSystems.single()
        drones.drones[0] = Drones.DroneInfo(blueprint, null)
        return drones
    }

    @Test
    fun combinedVersionsAreMade() {
        val kit = kit()
        val twin = kit.drone(2)
        val triple = kit.drone(3)

        assertEquals(2, twin.combinedCount)
        assertEquals(listOf("TEST_SIZED_DRONE", "TEST_SIZED_DRONE"), twin.combinedOf)
        assertEquals(3, triple.combinedCount)

        // Dearer, and more power, but not twice or three times as much
        assertEquals(110, twin.cost)
        assertEquals(165, triple.cost)
        assertEquals(3, twin.power)
        assertEquals(4, triple.power)
        assertEquals(0, twin.rarity)

        // Its icon's two or three of the single one's
        val single = kit.state.blueprintManager["TEST_SIZED_DRONE"] as DroneBlueprint
        assertTrue(twin.iconSize.x > 2 * single.iconSize.x - 1)
        assertTrue(triple.iconSize.y > single.iconSize.y)
    }

    @Test
    fun itLaunchesACopyForEachDronePart() {
        val kit = kit()
        val drones = kit.equip(kit.drone(3))
        kit.player.dronesCount = 5

        assertTrue(drones.setDronePower(0, true))
        val info = drones.drones[0]!!
        assertEquals(3, info.liveCopies.size)
        assertEquals(listOf(0, 1, 2), info.liveCopies.map { it.copyIndex })
        assertTrue(info.liveCopies.all { it.isPowered })
        assertEquals(2, kit.player.dronesCount)
    }

    @Test
    fun withTooFewPartsItLaunchesWhatItCan() {
        val kit = kit()
        val drones = kit.equip(kit.drone(3))
        kit.player.dronesCount = 2

        assertTrue(drones.setDronePower(0, true))
        assertEquals(2, drones.drones[0]!!.liveCopies.size)
        assertEquals(0, kit.player.dronesCount)
    }

    @Test
    fun itRelaunchesOnlyTheCopiesItsLost() {
        val kit = kit()
        val drones = kit.equip(kit.drone(2))
        kit.player.dronesCount = 5
        assertTrue(drones.setDronePower(0, true))
        val info = drones.drones[0]!!

        // One's shot down: the other flies on, and the slot's still on
        info.copies[1]!!.destroy()
        assertEquals(1, info.liveCopies.size)
        assertNull(info.copies[1])
        assertTrue(info.instance!!.isPowered)

        // Turned off and on again, once it can be, it sends the lost one
        // only, for one part
        drones.setDronePower(0, false)
        repeat(((AbstractDrone.DRONE_DESTROYED_COOLDOWN + 1f) / 0.1f).toInt()) { kit.state.updateGameState(0.1f) }
        assertTrue(drones.setDronePower(0, true))
        assertEquals(2, info.liveCopies.size)
        assertEquals(2, kit.player.dronesCount)
    }

    @Test
    fun turningItOffTurnsThemAllOff() {
        val kit = kit()
        val drones = kit.equip(kit.drone(2))
        kit.player.dronesCount = 5
        assertTrue(drones.setDronePower(0, true))
        drones.setDronePower(0, false)

        val info = drones.drones[0]!!
        assertEquals(2, info.liveCopies.size)
        assertFalse(info.liveCopies.any { it.isPowered })
    }

    @Test
    fun theCopiesAreSaved() {
        val kit = kit()
        val drones = kit.equip(kit.drone(3))
        kit.player.dronesCount = 5
        assertTrue(drones.setDronePower(0, true))
        drones.drones[0]!!.copies[1]!!.destroy()

        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        val info = loaded.player.droneSystems.single().drones[0]!!
        assertEquals(kit.drone(3).name, info.type.name)
        assertNotNull(info.copies[0])
        assertNull(info.copies[1])
        assertNotNull(info.copies[2])
        assertEquals(2, info.copies[2]!!.copyIndex)
    }

    @Test
    fun theCopiesAreSpreadRoundTheOrbit() {
        val kit = kit()
        val drones = kit.equip(kit.drone(3))
        kit.player.dronesCount = 5
        assertTrue(drones.setDronePower(0, true))

        val thetas = drones.drones[0]!!.liveCopies.map {
            ((it as AbstractExternalDrone).flightController as OrbitFlightController).theta
        }
        val third = (2 * Math.PI / 3).toFloat()
        for (i in 1 until 3) {
            val gap = (thetas[i] - thetas[0]).mod(2 * Math.PI.toFloat())
            assertEquals(i * third, gap, 0.001f)
        }
    }

    @Test
    fun aBankOfOverchargersLooksAfterARowEachAndChargesInTurn() {
        val kit = kit()
        val drones = kit.equip(kit.drone(3))
        kit.player.dronesCount = 5
        assertTrue(drones.setDronePower(0, true))
        kit.state.updateGameState(0.01f)

        assertEquals(3, kit.player.shieldDroneCount)
        val first = drones.drones[0]!!.copies.map { (it as ShieldDrone) }
        val charges = first.map { it.debugCooldown }
        assertTrue("$charges", charges[0] < charges[1] && charges[1] < charges[2])
    }

    @Test
    fun aCombinedRewardIsAlwaysATwinOrTriple() {
        val kit = kit()
        val rand = kotlin.random.Random(1)
        repeat(20) {
            val drone = kit.state.lootPool.getCombinedDrone(rand)
            assertTrue(drone.name, drone.combinedCount in 2..3)
        }
    }

    @Test
    fun theBossesGiveTwinsAndTriples() {
        assertEquals(listOf(2), BossRewards.droneSizes(1))
        assertEquals(listOf(2), BossRewards.droneSizes(2))
        assertEquals(listOf(3), BossRewards.droneSizes(3))
        assertEquals(listOf(3, 2), BossRewards.droneSizes(4))
    }

    @Test
    fun everyVanillaDroneIsCombinedButNotTheHackingOne() {
        val blueprints = BlueprintManager(Datafile(VanillaDatafile.createWithDefaultPath(), emptyList()), true)
        val combat = blueprints[DroneSizes.nameFor("COMBAT_1", 3)] as DroneBlueprint
        assertEquals(3, combat.combinedCount)
        assertEquals(165, combat.cost)
        assertEquals(4, combat.power)
        for (name in listOf("DEFENSE_1", "ANTI_DRONE", "BOARDER_ION", "BATTLE", "REPAIR", "SHIP_REPAIR", "DRONE_SHIELD_PLAYER")) {
            for (size in DroneSizes.SIZES)
                assertNotNull("$name x$size", blueprints.getOrNull(DroneSizes.nameFor(name, size)))
        }
        assertNull(blueprints.getOrNull(DroneSizes.nameFor("DRONE_HACKING", 2)))
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="PLAYER_SHIP_DRONE_SIZES" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>
        <systemList>
            <pilot power="1" max="1" room="0"/>
            <drones power="4" max="8" room="3"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>4</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <droneBlueprint name="TEST_SIZED_DRONE">
        <type>SHIELD</type>
        <power>2</power>
        <cost>50</cost>
        <rarity>1</rarity>
        <cooldown>15000</cooldown>
        <speed>5</speed>
        <droneImage>drone_shield</droneImage>
    </droneBlueprint>
</FTL>
"""
    }
}
