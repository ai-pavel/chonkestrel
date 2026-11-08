package xyz.znix.xftl.augments

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.WingmanTest
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.RoomPoint
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.systems.Cloaking
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew
import xyz.znix.xftl.weapons.Damage
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.MissileBlueprint
import kotlin.random.Random

/**
 * What the augments do. Those that work by chance have a value of 1 here
 * (the test's own blueprints), so they always do.
 */
class AugmentEffectsTest {
    private fun kit(vararg augments: String): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(WingmanTest.BLUEPRINT_XML, BLUEPRINT_XML),
            extraEvents = listOf(WingmanTest.EVENT_XML, EVENT_XML),
            playerShip = "PLAYER_SHIP_AUGMENTS"
        )
        kit.state.debugFlags.noCrewAI.set = true
        kit.powerUpAll()
        for (name in augments)
            kit.player.augments += kit.state.blueprintManager[name] as AugmentBlueprint
        kit.player.cargoUpdated()
        return kit
    }

    private fun InGameTest.fight() {
        state.debugSpawnShip(state.eventManager.getShip("TEST_ENEMY"), Difficulty.NORMAL, 0, 1)
        state.updateGameState(0.01f)
    }

    private fun InGameTest.run(seconds: Float) = repeat((seconds * 20).toInt()) { updateSingle(0.05f) }

    private fun InGameTest.room(system: String) = player.rooms.first { it.system?.blueprint?.name == system }

    @Test
    fun testTitaniumSystemCasing() {
        for ((augments, damaged) in listOf(listOf<String>() to 1, listOf("SYSTEM_CASING") to 0)) {
            val kit = kit(*augments.toTypedArray())
            val engines = kit.room("engines")
            kit.player.damage(engines, Damage.hullAndSys(1))
            assertEquals("$augments", damaged, engines.system!!.damagedEnergyLevels)
            assertEquals("the hull's still damaged", kit.player.maxHealth - 1, kit.player.health)
        }
    }

    @Test
    fun testRockPlating() {
        for ((augments, hull) in listOf(listOf<String>() to 1, listOf("ROCK_ARMOR") to 0)) {
            val kit = kit(*augments.toTypedArray())
            val engines = kit.room("engines")
            kit.player.damage(engines, Damage.hullAndSys(1))
            assertEquals("$augments", kit.player.maxHealth - hull, kit.player.health)
            assertEquals("the system's still damaged", 1, engines.system!!.damagedEnergyLevels)
        }
    }

    @Test
    fun testReverseIonField() {
        for ((augments, ionised) in listOf(listOf<String>() to true, listOf("ION_ARMOR") to false)) {
            val kit = kit(*augments.toTypedArray())
            val engines = kit.room("engines")
            kit.player.damage(engines, Damage().apply { ionDamage = 1 })
            assertEquals("$augments", ionised, engines.system!!.isIonised)
        }
    }

    @Test
    fun testReverseIonFieldOnTheSuperShield() {
        for ((augments, left) in listOf(listOf<String>() to 3, listOf("ION_ARMOR") to 5)) {
            val kit = kit(*augments.toTypedArray())
            kit.player.superShield = 5
            kit.player.attackShields(Damage().apply { ionDamage = 1 }, null)
            assertEquals("$augments", left, kit.player.superShield)
        }
    }

    @Test
    fun testWeaponsStunTheCrew() {
        val kit = kit()
        val crew = kit.addCrew(kit.player, kit.room("engines"))
        val room = crew.room
        // (the room's crew list is filled in as the ship updates)
        kit.player.update(0.01f)

        // The Stun Bomb's always does, for as long as it says...
        kit.player.damage(room, Damage().apply { stunDuration = 15f })
        assertEquals(15f, crew.stunTime, 0.001f)

        // ...and a stun chance does when it comes up, for a few seconds
        crew.stunTime = 0f
        kit.player.damage(room, Damage().apply { stunChance = 100 })
        assertEquals(Damage.CHANCE_STUN_TIME, crew.stunTime, 0.001f)
    }

    @Test
    fun testCrystalVengeance() {
        val kit = kit("CRYSTAL_SHARDS")
        kit.fight()
        assertTrue(kit.player.projectiles.none { it is MissileBlueprint.MissileProjectile })
        kit.player.damage(kit.room("engines"), Damage.hullOnly(1))
        val shard = kit.player.projectiles.filterIsInstance<MissileBlueprint.MissileProjectile>().single()
        assertEquals("CRYSTAL_1", shard.type.name)
        assertEquals(kit.state.enemy, shard.target.ship)
    }

    @Test
    fun testFtlRechargeBooster() {
        fun chargedIn(vararg augments: String): Float {
            val kit = kit(*augments)
            kit.fight()
            // (someone has to fly the ship)
            kit.addCrew(kit.player, kit.room("pilot"))
            kit.player.update(0.01f)
            kit.player.ftlChargeProgress = 0f
            kit.player.update(1f)
            return kit.player.ftlChargeProgress
        }
        assertEquals(1.25f, chargedIn("FTL_BOOSTER") / chargedIn(), 0.001f)
    }

    @Test
    fun testFtlJammer() {
        fun escapeLeft(vararg augments: String): Float {
            val kit = kit(*augments)
            kit.fight()
            kit.state.enemy!!.escapeTimer = 10f
            kit.run(1f)
            return kit.state.enemy!!.escapeTimer!!
        }
        assertEquals(9f, escapeLeft(), 0.1f)
        assertEquals(9.5f, escapeLeft("FTL_JAMMER"), 0.1f)
    }

    @Test
    fun testMantisPheromones() {
        fun stepsToCross(vararg augments: String): Int {
            val kit = kit(*augments)
            val room = kit.player.rooms.first { it.width == 5 }
            val crew = kit.addCrew(kit.player, room)
            crew.jumpTo(RoomPoint(room, ConstPoint(0, 0)))
            crew.setTargetRoom(room, ConstPoint(4, 0))
            var steps = 0
            while (crew.standingPosition?.let { it.x == 4 && it.y == 0 } != true && steps < 1000) {
                kit.updateSingle(0.05f)
                steps++
            }
            return steps
        }
        val normal = stepsToCross()
        val faster = stepsToCross("CREW_STIMS")
        assertEquals(normal / 1.25f, faster.toFloat(), 2f)
    }

    @Test
    fun testSlugRepairGel() {
        for ((augments, sealed) in listOf(listOf<String>() to false, listOf("SLUG_GEL") to true)) {
            val kit = kit(*augments.toTypedArray())
            val room = kit.room("pilot")
            room.spawnBreachAt(0)
            kit.run(15f)
            assertEquals("$augments", sealed, room.breaches[0] == null)
        }
    }

    @Test
    fun testEngiMedBots() {
        for ((augments, heals) in listOf(listOf<String>() to false, listOf("NANO_MEDBAY") to true)) {
            val kit = kit(*augments.toTypedArray())
            val crew = kit.addCrew(kit.player, kit.player.rooms.first { it.width == 5 })
            crew.health = 50f
            kit.run(5f)
            assertEquals("$augments", heals, crew.health > 50f)
        }

        // Only while the medbay's powered
        val kit = kit("NANO_MEDBAY")
        val crew = kit.addCrew(kit.player, kit.player.rooms.first { it.width == 5 })
        crew.health = 50f
        while (kit.player.medbay!!.powerSelected > 0)
            kit.player.medbay!!.decreasePower()
        kit.run(5f)
        assertEquals(50f, crew.health, 0.001f)
    }

    @Test
    fun testStealthWeapons() {
        for ((augments, cut) in listOf(listOf<String>() to true, listOf("CLOAK_FIRE") to false)) {
            val kit = kit(*augments.toTypedArray())
            val cloak = kit.player.systems.filterIsInstance<Cloaking>().single()
            cloak.activateCloak()
            val before = cloak.timeRemaining!!
            cloak.weaponFired()
            assertEquals("$augments", cut, cloak.timeRemaining!! < before)
        }
    }

    @Test
    fun testDroneRecoveryArm() {
        for ((augments, back) in listOf(listOf<String>() to 0, listOf("DRONE_RECOVERY") to 1)) {
            val kit = kit(*augments.toTypedArray())
            val drones = kit.player.droneSystems.single()
            drones.drones[0] = Drones.DroneInfo(kit.state.blueprintManager["TEST_OUTSIDE_DRONE"] as DroneBlueprint, null)
            kit.player.dronesCount = 3
            assertTrue(drones.setDronePower(0, true))
            assertNotNull(drones.drones[0]!!.instance)
            assertEquals(2, kit.player.dronesCount)

            // Jumping leaves the drone behind, but the arm gets its part back
            kit.player.resetAfterJump()
            assertEquals("$augments", 2 + back, kit.player.dronesCount)
        }
    }

    @Test
    fun testRepairArm() {
        fun resolve(vararg augments: String): xyz.znix.xftl.game.ResourceSet {
            val kit = kit(*augments)
            kit.player.health -= 5
            return (kit.state.eventManager["TEST_SCRAP"] as Event).resolveResources(kit.state, Random(0))
        }
        val plain = resolve()
        assertEquals(20, plain.scrap)
        assertTrue(plain.damage.isEmpty())

        // 15% less scrap, rounded down, and two hull repaired
        val repaired = resolve("REPAIR_ARM")
        assertEquals(17, repaired.scrap)
        assertEquals(listOf(-2), repaired.damage.map { it.amount })
    }

    @Test
    fun testAdvFtlNavigation() {
        for ((augments, canJump) in listOf(listOf<String>() to false, listOf("FTL_JUMPER") to true)) {
            val kit = kit(*augments.toTypedArray())
            val here = kit.state.currentBeacon
            val far = here.sector.beacons.first { it != here && it !in here.neighbours }
            assertFalse(kit.state.canJumpTo(far))
            far.visited = true
            assertEquals("$augments", canJump, kit.state.canJumpTo(far))
        }
    }

    @Test
    fun testDistractionBuoys() {
        for ((augments, held) in listOf(listOf<String>() to 0, listOf("FLEET_DISTRACTION") to -1)) {
            val kit = kit(*augments.toTypedArray())
            val sector = kit.state.currentBeacon.sector
            sector.fleetAdvanceModifier = 0
            kit.state.sectorEntered(sector)
            assertEquals("$augments", held, sector.fleetAdvanceModifier)
        }
    }

    companion object {
        // The augments that work by chance always do (a value of 1)
        @Language("XML")
        private val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="PLAYER_SHIP_AUGMENTS" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>

        <systemList>
            <pilot power="1" max="1" room="0"/>
            <cloaking power="1" max="3" room="1"/>
            <engines power="2" max="3" room="2"/>
            <drones power="2" max="3" room="3"/>
            <medbay power="1" max="1" room="4"/>
            <oxygen power="1" max="1" room="5"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="cloaking">
        <type>cloaking</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <droneBlueprint name="TEST_OUTSIDE_DRONE">
        <type>SHIELD</type>
        <power>1</power>
        <cooldown>15000</cooldown>
        <speed>5</speed>
        <droneImage>drone_shield</droneImage>
    </droneBlueprint>

    <weaponBlueprint name="CRYSTAL_1">
        <type>MISSILES</type>
        <damage>1</damage>
        <missiles>0</missiles>
        <shots>1</shots>
        <cooldown>9</cooldown>
        <power>1</power>
        <cost>1</cost>
        <image>dummy_anim</image>
        <weaponArt>dummy_anim</weaponArt>
    </weaponBlueprint>
""" + listOf(
            "SYSTEM_CASING" to 1f, "ROCK_ARMOR" to 1f, "ION_ARMOR" to 1f, "CRYSTAL_SHARDS" to 1f,
            "FTL_BOOSTER" to 0.25f, "FTL_JAMMER" to 2f, "CREW_STIMS" to 0.25f, "SLUG_GEL" to 0.25f,
            "NANO_MEDBAY" to 0.25f, "CLOAK_FIRE" to 0f, "DRONE_RECOVERY" to 0f, "REPAIR_ARM" to 0.15f,
            "FTL_JUMPER" to 0f, "FLEET_DISTRACTION" to 1f,
        ).joinToString("\n") { (name, value) ->
            """<augBlueprint name="$name"><cost>1</cost><rarity>1</rarity><stackable>false</stackable><value>$value</value></augBlueprint>"""
        } + "\n</FTL>"

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <event name="TEST_SCRAP">
        <item_modify>
            <item type="scrap" min="20" max="20"/>
        </item_modify>
    </event>
</FTL>
"""
    }
}
