package xyz.znix.xftl.environment

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.Mirage
import xyz.znix.xftl.sector.Sector
import xyz.znix.xftl.systems.Clonebay
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew

/**
 * Check XFTL's own nebulas: radiation, mirage and dark.
 */
class NebulaTest {
    private val kit = InGameTest(
        extraBlueprints = listOf(BLUEPRINT_XML),
        extraEvents = listOf(EVENT_XML),
        playerShip = "TEST_SHIP_DRONES"
    )

    /**
     * Put the player at a beacon with the given event's environment.
     */
    private fun enter(event: String): Beacon {
        val beacon = kit.state.currentBeacon
        beacon.event = kit.state.eventManager[event] as Event
        beacon.clearEnvironment()
        return beacon
    }

    @Test
    fun testNebulaTypes() {
        val events = kit.state.eventManager
        fun env(name: String) = (events[name] as Event).environment!!

        assertEquals(Beacon.EnvironmentType.RADIATION, env("RADIATION"))
        assertEquals(Beacon.EnvironmentType.MIRAGE, env("MIRAGE"))
        assertEquals(Beacon.EnvironmentType.DARK, env("DARK"))

        // They're all nebulas, but only radiation is a hazard
        for (type in listOf(env("RADIATION"), env("MIRAGE"), env("DARK"))) {
            assertTrue(type.isNebula)
        }
        assertTrue(env("RADIATION").isDangerous)
        assertFalse(env("MIRAGE").isDangerous)
        assertFalse(env("DARK").isDangerous)

        // And they look different
        val colours = Beacon.EnvironmentType.values().filter { it.isNebula }
            .map { NebulaEnvironment.mapCircleColour(it).let { c -> listOf(c.r, c.g, c.b) } }
        assertEquals(4, colours.toSet().size) // The ion storm's is the normal nebula's
    }

    @Test
    fun testDarkNebulaCutsOffDrones() {
        val drones = kit.player.droneSystems.single()
        assertEquals(2, drones.undamagedEnergy)

        enter("DARK")
        assertEquals(0, drones.undamagedEnergy)
        // The hacking system's probe is a drone too
        assertEquals(0, kit.player.hacking!!.undamagedEnergy)

        // Other systems are fine
        assertEquals(1, kit.player.systems.first { it.codename == "oxygen" }.undamagedEnergy)

        // A normal nebula doesn't affect them
        enter("NEBULA")
        assertEquals(2, drones.undamagedEnergy)
        assertEquals(1, kit.player.hacking!!.undamagedEnergy)
    }

    @Test
    fun testThickNebulas() {
        // The first sector's in act 1, so nebulas don't slow the FTL drive
        val env = enter("DARK").getEnvironment(kit.state)
        assertEquals(1, env.intensity)
        assertEquals(1f, env.ftlChargeMultiplier)
        assertEquals(1f, enter("NORMAL").getEnvironment(kit.state).ftlChargeMultiplier)
    }

    @Test
    fun testMirage() {
        val current = enter("MIRAGE")
        val others = current.sector.beacons.filter { it != current }

        // While the player's in a mirage, every other beacon they haven't
        // visited shows false information, which doesn't change
        assertTrue(others.all { Mirage.isDeceptive(it, current) })
        assertFalse(Mirage.isDeceptive(current, current))
        others.first().visited = true
        assertFalse(Mirage.isDeceptive(others.first(), current))
        assertTrue(others.all { Mirage.showsFakeShip(it) == Mirage.showsFakeShip(it) })

        // Some beacons show false ships, and some don't
        val fakes = others.map { Mirage.showsFakeShip(it) }
        assertTrue(fakes.any { it } && fakes.any { !it })

        // Outside a mirage, beacons are shown as they are
        val normal = enter("NEBULA")
        assertTrue(others.drop(1).none { Mirage.isDeceptive(it, normal) })
    }

    @Test
    fun testMirageScouting() {
        val current = enter("MIRAGE")
        val (inMirage, outside) = current.sector.beacons.filter { it != current }.let { it.first() to it.drop(1) }
        inMirage.event = kit.state.eventManager["MIRAGE"] as Event
        inMirage.clearEnvironment()

        // Once scouting gets through the mirages, the player's mirage
        // doesn't make the other beacons lie, but those in a mirage still do
        current.sector.mirageScouted = true
        assertTrue(outside.none { Mirage.isDeceptive(it, current) })
        assertTrue(Mirage.isDeceptive(inMirage, current))
    }

    @Test
    fun testHazardInMirageIsDangerous() {
        // A mirage on its own isn't a danger, so the ship window's open...
        val beacon = enter("MIRAGE")
        assertFalse(kit.state.isInDanger)

        // ...but asteroids added on top of it are, though the mirage is
        // still the beacon's main environment
        beacon.sectorHazards = listOf(Beacon.EnvironmentType.ASTEROID)
        beacon.clearEnvironment()
        assertEquals(Beacon.EnvironmentType.MIRAGE, beacon.environmentType)
        assertTrue(kit.state.isInDanger)
    }

    @Test
    fun testTheClonebayShieldsFromRadiation() {
        val inClonebay = kit.addCrew(kit.player, kit.player.clonebay!!.room!!) as LivingCrew
        val outside = kit.addCrew(kit.player, kit.player.rooms[2]) as LivingCrew
        // (only the nebula's updated, so the crew stay where they are)
        val nebula = enter("RADIATION").getEnvironment(kit.state)
        repeat(50) { nebula.update(0.1f) }
        assertTrue(inClonebay.room.system is Clonebay)
        assertEquals(inClonebay.maxHealth, inClonebay.health, 0.001f)
        assertTrue(outside.health < outside.maxHealth)
    }

    @Test
    fun testStoresAreInPlainNebulas() {
        // A radiation nebula's a hazard...
        val beacon = enter("RADIATION")
        assertTrue(kit.state.isInDanger)

        // ...but with a store, it's a plain nebula, as is any other kind
        for (type in listOf(Beacon.EnvironmentType.RADIATION, Beacon.EnvironmentType.MIRAGE,
                Beacon.EnvironmentType.DARK, Beacon.EnvironmentType.ION_STORM)) {
            beacon.nebulaOverride = type
            beacon.hasStore = true
            beacon.clearEnvironment()
            assertEquals(listOf(Beacon.EnvironmentType.NEBULA), beacon.environmentTypes)
            assertFalse(kit.state.isInDanger)
        }
    }

    @Test
    fun testStrangeNebulas() {
        val sector = kit.state.currentBeacon.sector
        val nebula = kit.state.eventManager["NEBULA"] as Event
        val storm = kit.state.eventManager["STORM"] as Event
        val (nebulas, storms) = sector.beacons.drop(1).withIndex().partition { it.index % 2 == 0 }.let { (a, b) -> a.map { it.value } to b.map { it.value } }
        nebulas.forEach { it.event = nebula }
        storms.forEach { it.event = storm }

        // Over lots of tries, about 10% of the plain nebulas in act 1 become
        // XFTL's own kinds, and never the ion storms
        var converted = 0
        var total = 0
        val seen = HashSet<Beacon.EnvironmentType>()
        for (seed in 0 until 200) {
            sector.beacons.forEach { it.nebulaOverride = null }
            kit.state.gameMap.addStrangeNebulas(sector, sector.info, kotlin.random.Random(seed))
            converted += nebulas.count { it.nebulaOverride != null }
            total += nebulas.size
            assertTrue(storms.all { it.nebulaOverride == null })
            nebulas.mapNotNullTo(seen) { it.nebulaOverride }
        }
        assertEquals(0.1, converted.toDouble() / total, 0.03)
        assertEquals(setOf(Beacon.EnvironmentType.RADIATION, Beacon.EnvironmentType.MIRAGE, Beacon.EnvironmentType.DARK), seen)

        // The override is the beacon's environment
        val beacon = nebulas.first()
        beacon.nebulaOverride = Beacon.EnvironmentType.DARK
        assertEquals(Beacon.EnvironmentType.DARK, beacon.environmentType)
    }

    @Test
    fun testNebulaSectors() {
        // A sector of dark nebulas
        val sectorData = String(javaClass.classLoader.getResourceAsStream("test_assets/data/sector_data.xml")!!.readBytes())
            .replace("""name="CIVILIAN_SECTOR" minSector="0" unique="false"""", """name="CIVILIAN_SECTOR" minSector="0" unique="false" xftlNebula="DARK"""")
        val darkKit = InGameTest(sectorData = sectorData, extraEvents = listOf(EVENT_XML))
        val sector = darkKit.state.currentBeacon.sector
        assertEquals(Beacon.EnvironmentType.DARK, sector.type.nebula)

        // About 90% of its beacons are dark nebulas, whatever their events
        var dark = 0
        var total = 0
        for (seed in 0 until 100) {
            sector.beacons.forEach { it.nebulaOverride = null }
            darkKit.state.gameMap.addStrangeNebulas(sector, sector.info, kotlin.random.Random(seed))
            dark += sector.beacons.count { it.environmentType == Beacon.EnvironmentType.DARK }
            total += sector.beacons.size
        }
        assertEquals(0.9, dark.toDouble() / total, 0.03)
    }

    @Test
    fun testBigShipsLeaveAWake() {
        val full = Sector.DANGER_ZONE_ADVANCE

        // Vanilla ships, as in vanilla
        assertEquals(full, Sector.baseFleetAdvance(false, false, 1))
        assertEquals(full / 2, Sector.baseFleetAdvance(true, false, 1))
        assertEquals((full * 0.8f).toInt(), Sector.baseFleetAdvance(true, true, 1))

        // Size two ships are slowed down half as much
        assertEquals(full, Sector.baseFleetAdvance(false, false, 2))
        assertEquals((full * 0.75f).toInt(), Sector.baseFleetAdvance(true, false, 2))
        assertEquals((full * 0.9f).toInt(), Sector.baseFleetAdvance(true, true, 2))
        assertNotEquals(Sector.baseFleetAdvance(true, false, 1), Sector.baseFleetAdvance(true, false, 2))
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_DRONES" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <drones power="2" room="3"/>
            <hacking power="1" room="4"/>
            <clonebay power="1" max="3" room="5"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="clonebay">
        <type>clonebay</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <systemBlueprint name="hacking">
        <type>hacking</type>
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
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <event name="NORMAL"/>
    <event name="NEBULA"><environment type="nebula"/></event>
    <event name="STORM"><environment type="storm"/></event>
    <event name="RADIATION"><environment type="radiation"/></event>
    <event name="MIRAGE"><environment type="mirage"/></event>
    <event name="DARK"><environment type="dark"/></event>
</FTL>
"""
    }
}
