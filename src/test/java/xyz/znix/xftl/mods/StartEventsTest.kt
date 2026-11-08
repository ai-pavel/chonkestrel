package xyz.znix.xftl.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.Translator
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.EventManager
import xyz.znix.xftl.sector.GameMap
import java.io.File
import kotlin.random.Random

/**
 * With the Chonkestrel mod, every type of sector has its own start event,
 * which only describes it: no fights, rewards or hazards, at most a plain
 * nebula (see doc/game/extreme-hazards.md).
 */
class StartEventsTest {
    private val modded = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(MOD_DIR.toPath())))
    private val blueprints = BlueprintManager(modded, true)
    private val translator = Translator(modded, "en")
    private val events = EventManager(modded, translator, blueprints)
    private val map = GameMap(modded, events, true, Random(0))

    @Test
    fun everySectorHasItsOwn() {
        val types = map.allSectorTypes
        assertTrue(types.size > 50)
        val shared = types.groupBy { it.startEventName }.filterValues { it.size > 1 }
        assertEquals(emptyMap<String, Any>(), shared.mapValues { (_, sectors) -> sectors.map { it.name } })
    }

    @Test
    fun theyOnlyDescribeTheSector() {
        for (type in map.allSectorTypes) {
            val name = type.startEventName
            val event = events[name]
            assertTrue("$name (${type.name}) is picked at random", event is Event)
            event as Event

            val environment = event.environment
            assertTrue(
                "$name has $environment",
                environment == null || environment == Beacon.EnvironmentType.NORMAL ||
                        environment == Beacon.EnvironmentType.NEBULA
            )
            assertEquals(name, null, event.loadShipName)
            assertEquals(name, null, event.autoRewards)
            assertTrue(name, event.blueprintRewards.isEmpty())
            assertTrue(name, event.addedCrew.isEmpty() && event.removedCrew.isEmpty())
            assertTrue(name, event.hullDamage.isEmpty())
            if (name !in WITH_CHOICES)
                assertTrue("$name has choices", event.choices.isEmpty())
        }
    }

    @Test
    fun theHazardousSectorsTakeMoreFuel() {
        // The Maw, the Gullet and the Belly take a unit more fuel
        // a jump, and their start events say so
        for (name in listOf("XFTL_MAW", "XFTL_GULLET", "XFTL_BELLY")) {
            val type = map.sectorType(name)!!
            assertEquals(name, 1, type.extraFuel)
            val start = events[type.startEventName] as Event
            assertTrue(name, start.text.toString().isNotEmpty())
        }
        assertEquals(0, map.sectorType("CIVILIAN_SECTOR")!!.extraFuel)
    }

    companion object {
        private val MOD_DIR = File("mods/chonkestrel")

        // Vanilla's start events with a choice, that are kept: the Crystal
        // homeworlds' (whose continue starts the Crystal quest) and the last
        // stand's (the Federation's welcome, with fuel and repairs)
        // (the act 2 civilian sectors' offer their quests, see CivilianSectorsTest)
        private val WITH_CHOICES = setOf(
            "START_BEACON_CRYSTAL", "LAST_STAND_START", "XFTL_REMNANT_START", "XFTL_ANVIL_START", "XFTL_QUIET_START",
        )
    }
}
