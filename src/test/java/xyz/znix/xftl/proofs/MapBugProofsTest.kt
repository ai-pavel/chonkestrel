package xyz.znix.xftl.proofs

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sun.misc.Unsafe
import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.drones.AbstractIndoorsDrone
import xyz.znix.xftl.game.PlayerShipUI
import xyz.znix.xftl.game.StoreData
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.DroneBlueprint
import kotlin.random.Random

/**
 * Proofs that bugs reported by players are fixed: each test is named after
 * what the game should do, and has the bug's report above it.
 */
class MapBugProofsTest {
    private fun kit(vararg augments: String): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML),
            extraAnimations = listOf(ANIMATIONS_XML),
            extraEvents = listOf(EVENT_XML),
            playerShip = "PROOF_SHIP"
        )
        kit.state.debugFlags.noCrewAI.set = true
        kit.powerUpAll()
        for (name in augments)
            kit.player.augments += kit.state.blueprintManager[name] as AugmentBlueprint
        kit.player.cargoUpdated()
        return kit
    }

    private fun InGameTest.run(seconds: Float) = repeat((seconds * 20).toInt()) { updateSingle(0.05f) }

    /**
     * Wait at the current beacon without the fuel to jump, as the jump
     * map's wait button does (PlayerShipUI.waitOutOfFuel), with the fleet
     * kept well away so the beacon isn't overtaken.
     *
     * The tests don't make a PlayerShipUI (it loads fonts and images), so
     * this makes one without running its constructor, with only the ship
     * and the game set: enough for everything waiting does before it opens
     * the event's window. Opening the window can't be done without the
     * fonts (loading one needs OpenGL, and aborts the JVM without it), so
     * every font is an empty one, which throws as soon as it's used; the
     * beacon's environment is then cleared as it would have been once the
     * window was open.
     */
    private fun InGameTest.waitOutOfFuel() {
        state.currentBeacon.sector.dangerZoneCentre.x = -100_000

        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as Unsafe

        val emptyFont = unsafe.allocateInstance(SILFontLoader::class.java) as SILFontLoader
        val fontsField = state.content.javaClass.getDeclaredField("fonts")
        fontsField.isAccessible = true
        fontsField.set(state.content, object : HashMap<String, SILFontLoader>() {
            override fun get(key: String): SILFontLoader = emptyFont
        })

        val ui = unsafe.allocateInstance(PlayerShipUI::class.java) as PlayerShipUI
        for ((name, value) in listOf("ship" to player, "game" to state)) {
            val field = PlayerShipUI::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.set(ui, value)
        }

        try {
            ui.waitOutOfFuel(false)
        } catch (e: Exception) {
            // (the event window)
            state.currentBeacon.clearEnvironment()
        }
    }

    /**
     * Купил Zoltan Shield он появился только после прыжка
     * (bought a Zoltan Shield, it only appeared after a jump)
     */
    @Test
    fun aBoughtZoltanShieldIsChargedStraightAway() {
        val kit = kit()
        val player = kit.player
        assertEquals(0, player.superShield)

        // Bought (or got from an event): the super shield's up at once,
        // five more for each one, without a jump
        val shield = kit.state.blueprintManager[AugZoltanShield.NAME] as AugmentBlueprint
        assertTrue(player.addBlueprint(shield, false))
        assertEquals(AugZoltanShield.SHIELD_PER_AUGMENT, player.superShield)

        assertTrue(player.addBlueprint(shield, false))
        assertEquals(2 * AugZoltanShield.SHIELD_PER_AUGMENT, player.superShield)
    }

    /**
     * Drone Control with Anti-Personnel 3 times in a row
     */
    @Test
    fun theDronesSystemsFreeDroneIsOneTheShipHasntGot() {
        val kit = InGameTest(extraBlueprints = listOf(FREE_DRONE_XML), playerShip = "PROOF_DRONELESS_SHIP")
        val blueprints = kit.state.blueprintManager
        val antiPersonnel = blueprints["PROOF_ANTI_PERSONNEL"] as DroneBlueprint
        val defence = blueprints["PROOF_DEFENCE"] as DroneBlueprint

        fun freeDrones(): Set<String?> {
            val free = HashSet<String?>()
            for (seed in 0 until 100) {
                val store = StoreData()
                store.generateRandomContents(kit.state, Random(seed))
                if (store.systems.any { it?.name == "drones" })
                    free += store.freeDrone?.name
            }
            return free
        }

        // With neither, either can come free
        assertEquals(setOf("PROOF_ANTI_PERSONNEL", "PROOF_DEFENCE"), freeDrones())

        // With an Anti-Personnel Drone already, it's always the other one
        kit.player.cargoBlueprints[0] = antiPersonnel
        assertEquals(setOf("PROOF_DEFENCE"), freeDrones())

        // With both, there's still one
        kit.player.cargoBlueprints[1] = defence
        assertTrue(freeDrones().all { it != null })
    }

    /**
     * Drone Reactor Booster (DRONE_SPEED) doesn't work: the ship's drones
     * should walk faster with it.
     */
    @Test
    fun theDroneReactorBoosterSpeedsUpTheShipsDrones() {
        fun stepsHome(vararg augments: String): Int {
            val kit = kit(*augments)
            val drones = kit.player.droneSystems.single()
            drones.drones[0] = Drones.DroneInfo(kit.state.blueprintManager["PROOF_REPAIR_DRONE"] as DroneBlueprint, null)
            kit.player.dronesCount = 3
            assertTrue(drones.setDronePower(0, true))
            val drone = drones.drones[0]!!.instance as AbstractIndoorsDrone

            // Let it power up, then put it at the far corner of the big room
            // and see how long it takes to walk back to the drones system
            kit.run(5f)
            val pawn = drone.pawn!!
            val home = drones.room
            val far = kit.player.rooms.first { it.width == 5 }
            pawn.jumpTo(xyz.znix.xftl.math.RoomPoint(far, xyz.znix.xftl.math.ConstPoint(4, 4)))
            var steps = 0
            while (pawn.room != home && steps < 2000) {
                kit.updateSingle(0.05f)
                steps++
            }
            assertEquals(home, pawn.room)
            return steps
        }
        val normal = stepsHome()
        val boosted = stepsHome("DRONE_SPEED")
        assertEquals(normal / 1.25f, boosted.toFloat(), 2f)
    }

    /**
     * Explosive Replicator (EXPLOSIVE_REPLICATOR) doesn't work: a missile
     * weapon should have a chance (here always) of using no missile.
     */
    @Test
    fun theExplosiveReplicatorSavesMissiles() {
        fun missilesLeft(vararg augments: String): Int {
            val kit = kit(*augments)
            kit.givePlayerBP("PROOF_MISSILE")
            kit.powerUpAll()
            val weapon = kit.player.hardpoints[0].weapon!!
            kit.player.missilesCount = 10
            assertTrue(kit.player.weapons!!.setWeaponPower(weapon, true))

            // Charge it, and fire it once
            var time = 0f
            while (!weapon.isCharged && time < 60f) {
                kit.updateSingle(0.05f)
                time += 0.05f
            }
            assertTrue(weapon.isCharged)
            kit.player.weapons!!.selectedTargets.targetRoom(0, kit.player.rooms[0])
            kit.updateSingle(0.05f)
            assertFalse("it fired", weapon.isCharged)
            return kit.player.missilesCount
        }
        assertEquals(9, missilesLeft())
        assertEquals(10, missilesLeft("EXPLOSIVE_REPLICATOR"))
    }

    /**
     * When out of fuel drone recovery arm will not work on defence drones
     */
    @Test
    fun waitingOutOfFuelRecoversTheDronesOutsideWithTheRecoveryArm() {
        for ((augments, back) in listOf(listOf<String>() to 0, listOf("DRONE_RECOVERY") to 1)) {
            val kit = kit(*augments.toTypedArray())
            val drones = kit.player.droneSystems.single()
            val defence = kit.state.blueprintManager["PROOF_DEFENCE_DRONE"] as DroneBlueprint
            // (the game does this as it loads, for its laser)
            defence.finishSetup(kit.state.content)
            drones.drones[0] = Drones.DroneInfo(defence, null)
            kit.player.dronesCount = 3
            assertTrue(drones.setDronePower(0, true))
            assertNotNull(drones.drones[0]!!.instance)
            assertEquals(2, kit.player.dronesCount)

            // Waiting is a jump to the same beacon: the drone's left behind,
            // and the arm gets its part back
            kit.waitOutOfFuel()
            assertEquals("$augments", 2 + back, kit.player.dronesCount)
        }
    }

    /**
     * When out of fuel you have a makeshift weapon pre igniter
     */
    @Test
    fun waitingOutOfFuelLosesTheWeaponsCharge() {
        val kit = kit()
        kit.givePlayerBP("PROOF_MISSILE")
        kit.powerUpAll()
        val weapon = kit.player.hardpoints[0].weapon!!
        kit.player.missilesCount = 10
        assertTrue(kit.player.weapons!!.setWeaponPower(weapon, true))
        kit.run(2f)
        assertTrue(weapon.timeCharged > 0f)

        kit.waitOutOfFuel()
        assertEquals(0f, weapon.timeCharged, 0f)
        assertFalse(weapon.isCharged)
    }

    /**
     * Последний сектор: флот захватывает 2 маяка за прыжок
     * (the last sector: the fleet takes two beacons a jump)
     */
    @Test
    fun theLastStandsFleetTakesTwoBeaconsAJump() {
        val kit = InGameTest(extraEvents = listOf(LAST_STAND_EVENT_XML))
        val info = kit.state.gameMap.sectors.last().single()
        val sector = kit.state.gameMap.generateSector(info, kit.state)
        assertTrue(sector.isLastStand)
        // (arriving there needs the UI, so the player's put there directly)
        val current = kit.state.javaClass.getDeclaredField("currentBeacon")
        current.isAccessible = true
        current.set(kit.state, sector.startBeacon)

        repeat(3) {
            val before = sector.beacons.count { it.isOvertaken }
            kit.state.advanceFleet()
            assertEquals(before + 2, sector.beacons.count { it.isOvertaken })
        }
    }

    /**
     * Выход из сектора в туманности (FINISH_BEACON_NEBULA)
     * (a sector's exit in a nebula)
     */
    @Test
    fun anExitInANebulaCloudIsANebula() {
        val kit = InGameTest(sectorData = NEBULA_SECTOR_XML, extraEvents = listOf(NEBULA_EVENT_XML))
        var nebulaExits = 0
        repeat(100) {
            val info = kit.state.gameMap.sectors.first().single()
            val sector = kit.state.gameMap.generateSector(info, kit.state)
            val exit = sector.finishBeacon
            val nebulas = exit.neighbours.count { it.isOwnNebula }
            val inCloud = nebulas * 2 > exit.neighbours.size

            assertEquals(inCloud, exit.event.debugId == "FINISH_BEACON_NEBULA")
            if (inCloud) {
                nebulaExits++
                assertTrue(exit.isOwnNebula)
                assertEquals(Beacon.EnvironmentType.NEBULA, exit.environmentType)
            } else {
                assertFalse(exit.isOwnNebula)
            }
        }
        assertTrue(nebulaExits > 0)
    }

    /**
     * События побега из опасной среды при ожидании без топлива
     * (the events for escaping a hazard, waiting without fuel)
     */
    @Test
    fun waitingOutOfFuelInAHazardGetsTheShipAway() {
        val kit = kit()
        val beacon = kit.state.currentBeacon
        beacon.event = kit.state.eventManager["PROOF_PLAIN"] as Event
        beacon.sectorHazards = listOf(Beacon.EnvironmentType.ASTEROID)
        beacon.clearEnvironment()
        assertTrue(kit.state.isInDanger)

        kit.waitOutOfFuel()
        assertFalse(beacon.isOvertaken)
        assertEquals("FUEL_ESCAPE_ASTEROIDS", beacon.event.debugId)
        assertFalse(Beacon.EnvironmentType.ASTEROID in beacon.environmentTypes)
        assertFalse(kit.state.isInDanger)

        // A storm leaves a plain nebula behind
        val storm = kit()
        val stormBeacon = storm.state.currentBeacon
        stormBeacon.event = storm.state.eventManager["PROOF_STORM"] as Event
        stormBeacon.clearEnvironment()
        storm.waitOutOfFuel()
        assertEquals("FUEL_ESCAPE_STORM", stormBeacon.event.debugId)
        assertEquals(Beacon.EnvironmentType.NEBULA, stormBeacon.environmentType)
    }

    /**
     * Can upgrade? (опасность поверх миража не блокировала окно корабля)
     * (a hazard on top of a mirage didn't stop the ship window opening)
     *
     * The ship window (and so upgrading) is closed while InGameState.isInDanger.
     */
    @Test
    fun aHazardInAMirageNebulaClosesTheShipWindow() {
        val kit = kit()
        val beacon = kit.state.currentBeacon

        // A mirage on its own is harmless: the ship window opens
        beacon.event = kit.state.eventManager["PROOF_MIRAGE"] as Event
        beacon.clearEnvironment()
        assertFalse(kit.state.isInDanger)

        // Asteroids on top of it aren't, though the mirage is still the
        // beacon's own environment
        beacon.sectorHazards = listOf(Beacon.EnvironmentType.ASTEROID)
        beacon.clearEnvironment()
        assertEquals(Beacon.EnvironmentType.MIRAGE, beacon.environmentType)
        assertTrue(kit.state.isInDanger)

        // As are those in a plain nebula
        beacon.event = kit.state.eventManager["PROOF_NEBULA"] as Event
        beacon.clearEnvironment()
        assertNotEquals(Beacon.EnvironmentType.MIRAGE, beacon.environmentType)
        assertTrue(kit.state.isInDanger)
    }

    companion object {
        // The augments that work by chance always do (a value of 1)
        @Language("XML")
        private val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="PROOF_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>
        <maxAugments>3</maxAugments>

        <systemList>
            <pilot power="1" max="1" room="0"/>
            <engines power="1" max="3" room="1"/>
            <weapons power="3" max="8" room="2"/>
            <drones power="2" max="8" room="3"/>
            <oxygen power="1" max="1" room="4"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <weaponBlueprint name="PROOF_MISSILE">
        <type>MISSILES</type>
        <damage>1</damage>
        <missiles>1</missiles>
        <shots>1</shots>
        <cooldown>5</cooldown>
        <power>1</power>
        <cost>1</cost>
        <image>dummy_anim</image>
        <weaponArt>generic_test_weapon</weaponArt>
    </weaponBlueprint>

    <droneBlueprint name="PROOF_DEFENCE_DRONE">
        <type>DEFENSE</type>
        <power>1</power>
        <cooldown>1000</cooldown>
        <speed>5</speed>
        <droneImage>drone_defense</droneImage>
        <weaponBlueprint>TEST_LASER</weaponBlueprint>
    </droneBlueprint>

    <droneBlueprint name="PROOF_REPAIR_DRONE">
        <type>REPAIR</type>
        <power>1</power>
    </droneBlueprint>

    <!-- The repair drone's pawn -->
    <crewBlueprint name="repair">
        <cost>1</cost>
    </crewBlueprint>

    <augBlueprint name="ENERGY_SHIELD">
        <cost>1</cost>
        <stackable>true</stackable>
    </augBlueprint>
""" + listOf(
            "DRONE_RECOVERY" to 0f, "DRONE_SPEED" to 0.25f, "EXPLOSIVE_REPLICATOR" to 1f,
        ).joinToString("\n") { (name, value) ->
            """<augBlueprint name="$name"><cost>1</cost><rarity>1</rarity><stackable>false</stackable><value>$value</value></augBlueprint>"""
        } + "\n</FTL>"

        // The repair drone's pawn's animations, as stand-ins
        @Language("XML")
        private val ANIMATIONS_XML = "<FTL>" + listOf(
            "portrait", "repair", "teleport", "death_right", "power_up", "power_down",
            "walk_up", "walk_down", "walk_left", "walk_right",
            "shoot_up", "shoot_down", "shoot_left", "shoot_right",
            "punch_up", "punch_down", "punch_left", "punch_right",
            "type_up", "type_down", "type_left", "type_right",
            "fire_up", "fire_down", "fire_left", "fire_right",
        ).joinToString("") {
            """<anim name="repair_$it"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>"""
        } + "</FTL>"

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <event name="PROOF_PLAIN"/>
    <event name="PROOF_NEBULA"><environment type="nebula"/></event>
    <event name="PROOF_STORM"><environment type="storm"/></event>
    <event name="PROOF_MIRAGE"><environment type="mirage"/></event>

    <event name="NO_FUEL"><text>Waiting.</text></event>
    <event name="NO_FUEL_DISTRESS"><text>Waiting with the beacon on.</text></event>
    <event name="NO_FUEL_FLEET"><text>The fleet.</text></event>
    <event name="NO_FUEL_FLEET_DLC"><text>The fleet.</text></event>

    <event name="FUEL_ESCAPE_SUN"><text>Away from the sun.</text></event>
    <event name="FUEL_ESCAPE_PULSAR"><text>Away from the pulsar.</text></event>
    <event name="FUEL_ESCAPE_ASTEROIDS"><text>Out of the asteroids.</text></event>
    <event name="FUEL_ESCAPE_STORM"><text>Out of the storm.</text></event>
</FTL>
"""

        // A ship with room for a drones system, and two common drones
        @Language("XML")
        private const val FREE_DRONE_XML = """
<FTL>
    <shipBlueprint name="PROOF_DRONELESS_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <medbay power="1" max="1" room="4"/>
            <drones power="2" max="8" room="5" start="false"/>
        </systemList>
    </shipBlueprint>
    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
    <systemBlueprint name="shields">
        <type>shields</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
    <droneBlueprint name="PROOF_ANTI_PERSONNEL">
        <type>BATTLE</type>
        <power>1</power>
        <cost>30</cost>
        <rarity>1</rarity>
    </droneBlueprint>
    <droneBlueprint name="PROOF_DEFENCE">
        <type>DEFENSE</type>
        <power>1</power>
        <cost>30</cost>
        <rarity>2</rarity>
    </droneBlueprint>
</FTL>
"""

        // A sector with lots of nebulas, so its exit's often in a cloud
        private const val NEBULA_SECTOR_XML = """
<FTL>
    <sectorDescription name="CIVILIAN_SECTOR" minSector="0" unique="false">
        <nameList><name>Clouds</name></nameList>
        <trackList/>
        <event name="PLAIN" min="4" max="4"/>
        <event name="IN_NEBULA" min="8" max="8"/>
    </sectorDescription>
    <sectorDescription name="FINAL" minSector="0" unique="false">
        <nameList><name>Last stand</name></nameList>
        <trackList/>
    </sectorDescription>
    <sectorType name="CIVILIAN"><sector>CIVILIAN_SECTOR</sector></sectorType>
    <sectorType name="HOSTILE"><sector>CIVILIAN_SECTOR</sector></sectorType>
    <sectorType name="NEBULA"><sector>CIVILIAN_SECTOR</sector></sectorType>
</FTL>
"""

        @Language("XML")
        private const val LAST_STAND_EVENT_XML = """
<FTL>
    <event name="LAST_STAND_START"/>
</FTL>
"""

        private const val NEBULA_EVENT_XML = """
<FTL>
    <event name="PLAIN"/>
    <event name="IN_NEBULA"><environment type="nebula"/></event>
</FTL>
"""
    }
}
