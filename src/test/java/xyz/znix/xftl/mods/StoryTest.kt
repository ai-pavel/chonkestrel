package xyz.znix.xftl.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.LazyShipBlueprint
import xyz.znix.xftl.ShipShape
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.Translator
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.game.ColossalBoss
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.EventList
import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.sector.EventManager
import xyz.znix.xftl.sector.IEvent
import java.io.File
import kotlin.random.Random

/**
 * Check the Chonkestrel mod's story about CONCORD (see doc/game/lore.md): the hints
 * in act 1, the reveal at the Blockade Line, and the act 2 sectors.
 */
class StoryTest {
    private val modded = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(MOD_DIR.toPath())))
    private val blueprints = BlueprintManager(modded, true)
    private val translator = Translator(modded, "en")
    private val events = EventManager(modded, translator, blueprints)

    /**
     * Every event reachable from [event], through lists, choices, and the
     * ships' outcomes.
     */
    private fun reachable(event: IEvent, visited: MutableSet<IEvent> = HashSet()): Set<IEvent> {
        if (!visited.add(event))
            return visited
        when (event) {
            is EventList -> event.events.forEach { reachable(it, visited) }
            is Event -> {
                event.loadShipName?.let { events.getShip(it) }?.let { ship ->
                    listOfNotNull(ship.destroyed, ship.deadCrew, ship.surrender, ship.escape, ship.gotaway)
                        .forEach { reachable(it, visited) }
                }
                event.choices.forEach { reachable(it.event, visited) }
            }
        }
        return visited
    }

    @Test
    fun testNewSectors() {
        val newSectors = setOf("XFTL_QUIET")
        val seen = HashSet<String>()
        for (seed in 0 until 100) {
            val map = GameMap(modded, events, true, Random(seed))
            for ((column, sectors) in map.sectors.withIndex()) {
                for (sector in sectors.filter { it.type.name in newSectors }) {
                    // Only in act 2
                    assertEquals(sector.type.name, 2, map.actOf(column))
                    seen += sector.type.name
                }
            }
        }
        assertEquals(newSectors, seen)

        // Their names, and the renamed sectors
        assertEquals("The Quiet", translator["sectorname_XFTL_QUIET"])
        assertEquals("The Free Colonies", translator["sectorname_XFTL_FEDERATION_REMNANT"])
    }

    @Test
    fun testMoreEventsFurtherIn() {
        // The vanilla sectors' new events (XFTL_MORE_*) start out a few a
        // sector, and there are more of them further in
        val map = GameMap(modded, events, true, Random(0))
        val vanilla = map.sectors.flatten().filter { !it.type.name.startsWith("XFTL_") && it.type.name != "FINAL" }
        val counts = HashMap<Int, IntRange>()
        for (sector in vanilla) {
            val more = map.eventsFor(sector, 1).filter { it.name.startsWith("XFTL_MORE_") }
            assertEquals(sector.type.name, 1, more.size)
            counts[map.nominalSectorNumber(sector.columnNumber)] = more.single().count
        }
        for ((number, count) in counts) {
            val expected = when (number) {
                in 1..5 -> 1..2
                else -> 2..3
            }
            assertEquals("sector $number", expected, count)
        }
        assertTrue(counts.keys.containsAll(listOf(1, 3, 6, 9)))
    }

    @Test
    fun testOmensAndChorus() {
        // The vanilla sectors have hints of CONCORD in act 1, and its events in
        // act 2, and never the other way round
        val map = GameMap(modded, events, true, Random(0))
        // (apart from the last stand)
        val vanilla = map.sectors.flatten().filter { !it.type.name.startsWith("XFTL_") && it.type.name != "FINAL" }
        assertTrue(vanilla.isNotEmpty())
        for (sector in vanilla) {
            val names = map.eventsFor(sector, 1).map { it.name }
            val act = map.actOf(sector.columnNumber)
            assertEquals(sector.type.name, act == 1, "XFTL_OMENS" in names)
            assertEquals(sector.type.name, act == 2, "XFTL_CHORUS" in names)
        }

        // The hybrids turn up in act 1 already, as an omen
        val omens = reachable(events["XFTL_OMENS"]).filterIsInstance<Event>()
        assertTrue(omens.any { it.loadShipName == "XFTL_JOINED_SHIP" })
        assertTrue(events.getShip("XFTL_JOINED_SHIP").autoBlueprint.list().all { it.name.startsWith("XFTL_HYBRID_") })
    }

    @Test
    fun testReveal() {
        // Every way out of the Blockade Line leads to the reveal: beating the
        // command ship (or killing its crew), or slipping past it under cloak
        val reveal = events["XFTL_BLOCKADE_REVEAL"]
        val exit = events["XFTL_BLOCKADE_EXIT"] as Event
        val command = events.getShip("XFTL_BLOCKADE_COMMAND")
        for (outcome in listOf(command.destroyed!!, command.deadCrew!!)) {
            assertTrue(reveal in reachable(outcome))
        }
        val cloak = exit.choices.single { it.text.resolve(Random(0)).contains("cloak") }.event
        assertTrue(reveal in reachable(cloak))

        // ...and through it, to the end of act 1
        assertTrue(events["XFTL_BLOCKADE_REVEAL_END"] in reachable(reveal))
    }

    @Test
    fun testSilentCapital() {
        // Sector 16 is the Silent Capital (see doc/game/mainframe.md): its
        // own events in place of vanilla's Last Stand's, two stores, and
        // the repair stations
        val map = GameMap(modded, events, true, Random(0))
        val capital = map.sectors.last().single()
        assertEquals("FINAL", capital.type.name)
        assertEquals("The Silent Capital", translator["sectorname_FINAL"])

        val counts = map.eventsFor(capital, 1).groupBy { it.name }.mapValues { (_, infos) -> infos.sumOf { it.count.last } }
        assertTrue(counts.keys.none { it == "BOSS_HOSTILE" || it == "BOSS_NEUTRAL" })
        assertEquals(2, counts["STORE"])
        assertEquals(3, counts["BOSS_REPAIR_STATION"])
        for (name in listOf("XFTL_CAPITAL_TRAFFIC", "XFTL_CAPITAL_YARD", "XFTL_CAPITAL_CHOIR", "XFTL_CAPITAL_SAL"))
            assertEquals(name, 1, counts[name])

        // Every event loads, with the ships it spawns, and its fights are
        // CONCORD's
        val ships = counts.keys.filter { it.startsWith("XFTL_CAPITAL_") }
            .flatMap { reachable(events[it]) }.filterIsInstance<Event>().mapNotNull { it.loadShipName }.toSet()
        assertTrue(ships.containsAll(listOf("XFTL_CAPITAL_JOINED_SHIP", "XFTL_CAPITAL_AUTO_SHIP", "XFTL_CAPITAL_REBEL_SHIP")))
        assertTrue(events.getShip("XFTL_CAPITAL_JOINED_SHIP").autoBlueprint.list().all { it.name.startsWith("XFTL_HYBRID_") })

        // At the end, in place of the Federation base and the flagship, is
        // the Mainframe: a size 4 boss. Its first phase, the Firewall, is one
        // of four, each the Rebel flagship with half of two races' bombers
        assertEquals("XFTL_MAINFRAME", capital.type.finishEventName)
        val core = reachable(events["XFTL_MAINFRAME"]).filterIsInstance<Event>().mapNotNull { it.loadShipName }.toSet()
        assertEquals(setOf("XFTL_MAINFRAME_CORE"), core)
        val firewalls = events.getShip("XFTL_MAINFRAME_CORE").autoBlueprint.list().map { (it as LazyShipBlueprint).real }
        assertEquals(4, firewalls.size)
        for (firewall in firewalls) {
            assertEquals(ColossalBoss.Kind.MAINFRAME, firewall.bossKind)
            assertEquals(3, firewall.loadElem().getChild("systemList").getChildren("shields").size)
        }

        // Its other two phases come from their own events, after the one
        // before is beaten (see InGameState.startMainframePhase)
        val phases = mapOf(
            2 to ("XFTL_MAINFRAME_BAYS" to ColossalBoss.Kind.MAINFRAME_BAYS),
            3 to ("XFTL_MAINFRAME_MIND" to ColossalBoss.Kind.MAINFRAME_MIND),
        )
        for ((phase, expected) in phases) {
            val (shipName, kind) = expected
            val event = events[InGameState.MAINFRAME_PHASE_EVENT + phase]
            val ships = reachable(event).filterIsInstance<Event>().mapNotNull { it.loadShipName }.toSet()
            assertEquals(setOf(shipName), ships)
            val blueprint = (events.getShip(shipName).autoBlueprint.list().single() as LazyShipBlueprint).real
            assertEquals(kind, blueprint.bossKind)
            assertEquals(phase, kind.mainframePhase)
        }

        // Every phase is size 4, as wide as the row of enemy windows, crewed
        // by every race at once (six of each), and swarming with drones:
        // anti-personnel ones (bulwarks among them) and repair drones
        // inside, and defence and combat ones
        val phaseShips = firewalls + listOf("XFTL_MAINFRAME_BAYS", "XFTL_MAINFRAME_MIND").map {
            (events.getShip(it).autoBlueprint.list().single() as LazyShipBlueprint).real
        }
        for (blueprint in phaseShips) {
            val shipName = blueprint.name
            assertEquals(shipName, ShipShape.TITAN, blueprint.shape)
            assertEquals(shipName, 4, blueprint.shipSize)
            val races = blueprint.loadElem().getChildren("crewCount").map { it.getAttributeValue("class") }.toSet()
            assertEquals(shipName, setOf("human", "engi", "mantis", "rock", "energy", "slug", "crystal", "anaerobic"), races)
            assertEquals(shipName, 48, blueprint.initialCrew.sumOf { it.amount })
            val drones = blueprint.initialDrones
            assertTrue(shipName, drones.count { it.endsWith("BATTLE") || it == "REPAIR" } >= 12)
            assertTrue(shipName, "REPAIR" in drones)
            assertTrue(shipName, drones.any { it.startsWith("XFTL_X3_") && it.endsWith("BATTLE") })
            assertTrue(shipName, drones.any { "DEFENSE" in it } && drones.any { "COMBAT" in it })
        }
        assertEquals(InGameState.MAINFRAME_PHASES, ColossalBoss.Kind.MAINFRAME_MIND.mainframePhase)

        // The Splicing Bays' last two drones systems are its bays, which
        // start empty (three drones to each of the others), and the drones
        // they build are all real
        val bays = (events.getShip("XFTL_MAINFRAME_BAYS").autoBlueprint.list().single() as LazyShipBlueprint).real
        val bayDronesSystems = bays.loadElem().getChild("systemList").getChildren("drones").size
        assertEquals((bayDronesSystems - ColossalBoss.MAX_BAYS) * 3, bays.initialDrones.size)
        assertTrue(ColossalBoss.BAY_DRONES.all { blueprints.getOrNull(it) is DroneBlueprint })

        // The Mind has a mind control system for its Chorus pulses, and the
        // Zoltan Shield Bypass, so the player's super shield doesn't stop them
        val mind = (events.getShip("XFTL_MAINFRAME_MIND").autoBlueprint.list().single() as LazyShipBlueprint).real
        assertEquals(1, mind.loadElem().getChild("systemList").getChildren("mind").size)
        assertTrue("ZOLTAN_BYPASS" in mind.initialAugments)
    }

    @Test
    fun testStoryEventsLoad() {
        // All the story's events load, including the ones behind choices
        // (which are loaded lazily), and the ships they spawn
        val names = listOf(
            "XFTL_OMENS", "XFTL_CHORUS", "XFTL_BLOCKADE_HUM",
            "XFTL_QUIET_START",
        )
        for (name in names) {
            assertTrue(name, reachable(events[name]).isNotEmpty())
        }
        val sectorEvents = GameMap(modded, events, true, Random(0)).let { map ->
            map.sectors.flatten().filter { it.type.name in STORY_SECTORS }.flatMap { map.eventsFor(it, 2) }
        }
        sectorEvents.forEach { reachable(it.event) }
    }

    companion object {
        private val MOD_DIR = File("mods/chonkestrel")
        private val STORY_SECTORS = setOf("XFTL_QUIET")
    }
}
