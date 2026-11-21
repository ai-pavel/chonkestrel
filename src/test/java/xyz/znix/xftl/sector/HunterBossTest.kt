package xyz.znix.xftl.sector

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.game.HunterBoss
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.testkit.InGameTest

/**
 * A hazardous sector's boss that hunts the player (`xftlHunter`, see
 * HunterBoss) rather than holding the exit, and the Blockade Line's
 * several exits (`xftlExits`), all held by the same boss.
 */
class HunterBossTest {
    private fun createKit(sectorAttrs: String) = InGameTest(
        sectorData = sectorData(sectorAttrs), extraEvents = listOf(EVENT_XML)
    )

    private val InGameTest.sector: Sector get() = state.currentBeacon.sector
    private val InGameTest.hunter: HunterBoss get() = sector.bosses.single() as HunterBoss

    @Test
    fun itSetsOutFromAnOrdinaryExit() {
        val kit = createKit("""xftlHunter="1"""")
        val sector = kit.sector
        assertEquals(sector.finishBeacon, kit.hunter.beacon)
        assertNotEquals("HUNT", sector.finishBeacon.event.debugId)

        // It brings its own environment
        assertEquals(HUNT_ENVIRONMENT, sector.finishBeacon.environmentTypes)

        // and there's no fleet behind the player
        assertFalse(sector.type.hasFleet)
        val fleet = sector.dangerZoneCentre.x
        kit.state.advanceFleet()
        assertEquals(fleet, sector.dangerZoneCentre.x)
    }

    @Test
    fun itComesForThePlayerAJumpAtATime() {
        val kit = createKit("""xftlHunter="1"""")
        val sector = kit.sector
        val hunter = kit.hunter
        val start = sector.startBeacon
        val path = sector.findShortestPath(sector.finishBeacon, start)!!

        hunter.playerArrived(start)
        for (step in path) {
            assertTrue(hunter.jumping)
            assertEquals(step, hunter.nextBeacon)
            val from = hunter.beacon!!
            hunter.advanceFleet()
            hunter.playerArrived(start)
            assertEquals(step, hunter.beacon)

            // Its environment goes with it
            assertEquals(HUNT_ENVIRONMENT, step.environmentTypes)
            assertNotEquals(HUNT_ENVIRONMENT, from.environmentTypes)
        }

        // It's caught up: its event's due, and it waits there
        assertTrue(hunter.isWaitingAt(start))
        assertNull(hunter.nextBeacon)
    }

    @Test
    fun aSlowerOneJumpsEverySecondTurn() {
        val kit = createKit("""xftlHunter="2"""")
        val hunter = kit.hunter
        val exit = kit.sector.finishBeacon
        hunter.playerArrived(kit.sector.startBeacon)

        assertFalse(hunter.jumping)
        hunter.advanceFleet()
        assertEquals(exit, hunter.beacon)
        assertTrue(hunter.jumping)
        hunter.advanceFleet()
        assertNotEquals(exit, hunter.beacon)
    }

    @Test
    fun onceBeatenItsGone() {
        val kit = createKit("""xftlHunter="1"""")
        val hunter = kit.hunter
        val exit = kit.sector.finishBeacon
        hunter.startEncounter()
        hunter.bossBeaten()

        assertNull(hunter.beacon)
        assertFalse(hunter.isWaitingAt(exit))
        assertNotEquals(HUNT_ENVIRONMENT, exit.environmentTypes)
    }

    @Test
    fun onceBeatenHalfTheHazardousBeaconsHaveAHazardFewer() {
        val kit = createKit("""xftlHunter="1"""")
        val sector = kit.sector
        // Four catastrophic beacons, and no added hazards elsewhere
        val plain = sector.beacons.filter { !it.isExit && !it.hasStore && !it.hasQuest && it != kit.hunter.beacon }
        for (beacon in sector.beacons) {
            beacon.nebulaOverride = null
            beacon.sectorHazards = emptyList()
        }
        val hazardous = plain.filter { it.event.environment == null }.take(4)
        for (beacon in hazardous) {
            beacon.nebulaOverride = Beacon.EnvironmentType.NEBULA
            beacon.sectorHazards = listOf(Beacon.EnvironmentType.ASTEROID, Beacon.EnvironmentType.SUN)
        }
        assertEquals(4, hazardous.size)

        kit.hunter.startEncounter()
        kit.hunter.bossBeaten()

        // What each beacon had before (with the boss gone)
        fun own(beacon: Beacon): List<Beacon.EnvironmentType> {
            val eased = beacon.hazardsEased
            beacon.hazardsEased = false
            return beacon.environmentTypes.also { beacon.hazardsEased = eased }
        }
        val normal = listOf(Beacon.EnvironmentType.NORMAL)
        val candidates = sector.beacons.filter { own(it) != normal }
        assertTrue(candidates.containsAll(hazardous))

        // Half of them (rounding up) lose their last added hazard; the rest
        // are as they were
        val easedBeacons = sector.beacons.filter { it.hazardsEased }
        assertEquals((candidates.size + 1) / 2, easedBeacons.size)
        for (beacon in candidates) {
            val expected = if (beacon.hazardsEased) own(beacon).dropLast(1).ifEmpty { normal } else own(beacon)
            assertEquals(expected, beacon.environmentTypes)
        }

        // and that's saved
        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        val loadedSector = loaded.currentBeacon.sector
        for (beacon in hazardous) {
            val again = loadedSector.beacons.single { it.pos == beacon.pos }
            assertEquals(beacon.environmentTypes, again.environmentTypes)
        }
    }

    @Test
    fun onceBeatenTheFleetTakesUpTheChase() {
        val kit = createKit("""xftlHunter="1"""")
        val sector = kit.sector
        val at = kit.hunter.beacon!!
        val start = sector.dangerZoneCentre.x
        assertFalse(sector.hasFleet)

        kit.hunter.startEncounter()
        kit.hunter.bossBeaten()

        // It turns up a few jumps behind where the boss was beaten, and
        // advances from there
        assertTrue(sector.hasFleet)
        val behind = at.pos.x - Sector.DANGER_ZONE_RADIUS - Sector.FLEET_CHASE_JUMPS * Sector.DANGER_ZONE_ADVANCE
        assertEquals(maxOf(behind, start), sector.dangerZoneCentre.x)
        val fleet = sector.dangerZoneCentre.x
        kit.state.advanceFleet()
        assertTrue(sector.dangerZoneCentre.x > fleet)

        // and that's saved
        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        assertTrue(loaded.currentBeacon.sector.hasFleet)
    }

    @Test
    fun aGameSavedAfterBeatingItBeforeThisCatchesUpOnTheNextJump() {
        val kit = createKit("""xftlHunter="1"""")
        val sector = kit.sector
        kit.hunter.startEncounter()
        kit.hunter.bossBeaten()

        // As an older game, saved with the boss beaten, but without the
        // fleet or the calmer beacons
        sector.fleetPursuing = false
        for (beacon in sector.beacons)
            beacon.hazardsEased = false
        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        val loadedSector = loaded.currentBeacon.sector
        assertFalse(loadedSector.hasFleet)

        loaded.advanceFleet()
        assertTrue(loadedSector.hasFleet)
    }

    @Test
    fun itsSaved() {
        val kit = createKit("""xftlHunter="2"""")
        val hunter = kit.hunter
        hunter.playerArrived(kit.sector.startBeacon)
        hunter.advanceFleet()
        hunter.advanceFleet()
        hunter.playerArrived(kit.sector.startBeacon)
        val at = hunter.beacon!!

        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        val sector = loaded.currentBeacon.sector
        val again = sector.bosses.single() as HunterBoss
        val beacon = sector.beacons.single { it.pos == at.pos }
        assertEquals(beacon, again.beacon)
        assertEquals(hunter.nextBeacon?.pos, again.nextBeacon?.pos)
        assertEquals(hunter.jumping, again.jumping)
        assertEquals(HUNT_ENVIRONMENT, beacon.environmentTypes)
    }

    @Test
    fun theBlockadeHasThreeExitsAllWithItsBoss() {
        val kit = createKit("""xftlExits="3"""")
        val sector = kit.sector
        assertTrue(sector.bosses.isEmpty())
        assertTrue(sector.type.bossHoldsExit)

        val exits = sector.beacons.filter { it.isExit }
        assertEquals(3, exits.size)
        assertTrue(sector.finishBeacon in exits)
        for (exit in exits) {
            assertEquals("HUNT", exit.event.debugId)
            assertTrue(exit.gridPos.x in Sector.LAST_TWO_COLUMNS)
            assertTrue(kit.state.isBossBeacon(exit))
        }
        assertFalse(kit.state.isExitBossBeaten(sector))
    }

    @Test
    fun beatingItSendsTheFleetOverTheOtherExits() {
        val kit = createKit("""xftlExits="3" """)
        val sector = kit.sector
        val exits = sector.beacons.filter { it.isExit }
        val here = exits.first()
        val fleetBefore = sector.dangerZoneCentre.x
        val behind = sector.beacons.count { it.isOvertaken }

        kit.state.exitHoldBroken(sector, here)

        // The other exits are the fleet's, and the player's isn't
        assertFalse(here.isOvertaken)
        assertTrue(exits.filter { it != here }.all { it.isOvertaken })
        // And the fleet's surged forward, taking more of the sector
        assertTrue(sector.dangerZoneCentre.x > fleetBefore)
        assertTrue(sector.beacons.count { it.isOvertaken } > behind + 2)
    }

    companion object {
        private val HUNT_ENVIRONMENT = listOf(Beacon.EnvironmentType.NEBULA, Beacon.EnvironmentType.ASTEROID)

        @Language("XML")
        private fun sectorData(attrs: String) = """
<FTL>
    <sectorDescription name="CIVILIAN_SECTOR" minSector="0" unique="false" $attrs>
        <nameList><name>Hunted</name></nameList>
        <trackList/>
        <xftlFinishEvent>HUNT</xftlFinishEvent>
        <event name="PLAIN" min="14" max="14"/>
    </sectorDescription>
    <sectorDescription name="FINAL" minSector="0" unique="false">
        <nameList><name>Last stand</name></nameList>
        <trackList/>
    </sectorDescription>
    ${listOf("CIVILIAN", "HOSTILE", "NEBULA").joinToString("\n") { """
    <sectorType name="$it">
        <sector>CIVILIAN_SECTOR</sector>
    </sectorType>""" }}
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <event name="PLAIN"/>
    <event name="HUNT"><environment type="nebula" xftlHazards="asteroid"/></event>
</FTL>
"""
    }
}
