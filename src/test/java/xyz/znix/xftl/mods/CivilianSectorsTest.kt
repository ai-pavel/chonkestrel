package xyz.znix.xftl.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.Translator
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.game.LootPool
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.EventList
import xyz.znix.xftl.sector.EventManager
import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.sector.IEvent
import xyz.znix.xftl.sector.SectorType
import java.io.File
import kotlin.random.Random

/**
 * The Chonkestrel mod's three act 2 civilian sectors (the Free Colonies, the
 * Anvil Yards and the Quiet): at most one of each on a map, stores with more
 * double and triple weapons, and one quest each.
 */
class CivilianSectorsTest {
    private val modded = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(MOD_DIR.toPath())))
    private val blueprints = BlueprintManager(modded, true)
    private val translator = Translator(modded, "en")
    private val events = EventManager(modded, translator, blueprints)

    // The quests an event can lead to, through its choices and lists
    private fun quests(event: IEvent, visited: MutableSet<IEvent> = HashSet()): Set<String> {
        if (!visited.add(event))
            return emptySet()
        return when (event) {
            is EventList -> event.events.flatMap { quests(it, visited) }.toSet()
            is Event -> setOfNotNull(event.questName) + event.choices.flatMap { quests(it.event, visited) }
            else -> emptySet()
        }
    }

    @Test
    fun testTheyEachComeUpOnceAtMost() {
        val types = HashMap<String, SectorType>()
        for (seed in 0 until 200) {
            val map = GameMap(modded, events, true, Random(seed))
            val sectors = map.sectors.flatten().filter { it.type.name in SECTORS }
            for (name in SECTORS)
                assertTrue("$name on map $seed", sectors.count { it.type.name == name } <= 1)
            sectors.forEach { types[it.type.name] = it.type }
        }
        assertEquals(SECTORS, types.keys)

        for (type in types.values) {
            assertTrue(type.name, type.unique)
            assertNotNull(type.name, type.storeDoubleChance)
            assertNotNull(type.name, type.storeTripleChance)

            // Exactly one quest, offered by the start event so it's always
            // met, and no vanilla ones
            assertTrue(type.name, type.events.none { quests(it.event).isNotEmpty() })
            assertTrue(type.name, type.events.none { it.name == "QUESTS" })
            val start = type.startEvent
            assertEquals(type.name, 1, quests(start).size)
            assertTrue(type.name, start.resolve().givesQuest)

            for (quest in quests(start))
                assertNotNull(quest, events[quest])
        }
    }

    @Test
    fun testTheQuestsRewardsFitOnTheScreen() {
        // The courier's two double or triple weapons, and a double or
        // triple drone
        val delivery = events["XFTL_REMNANT_COURIER_DELIVERY"] as Event
        assertEquals(
            listOf("xftl_rand_combined_weapon", "xftl_rand_combined_weapon", "xftl_rand_combined_drone"),
            delivery.blueprintRewards
        )
        assertEquals(null, delivery.autoRewards)

        // The prototype's pair of augments, a Zoltan Shield among them, and
        // the hermit, a well-trained crewmember with plenty of supplies. The
        // ships fight alone, so their rewards come once.
        val prototype = events.getShip("XFTL_ANVIL_PROTOTYPE_SALVAGER")
        val captor = events.getShip("XFTL_QUIET_HERMIT_CAPTOR")
        assertTrue(prototype.alone && captor.alone)
        for (outcome in listOfNotNull(prototype.destroyed, prototype.deadCrew).map { it as Event }) {
            assertEquals(listOf("xftl_rand_augment", "ENERGY_SHIELD"), outcome.blueprintRewards)
            assertEquals(null, outcome.autoRewards)
        }
        for (outcome in listOfNotNull(captor.destroyed, captor.deadCrew).map { it as Event }) {
            assertTrue(outcome.blueprintRewards.isEmpty())
            assertEquals(null, outcome.autoRewards)
            assertEquals(1, outcome.addedCrew.size)
        }

        assertFalse((events["XFTL_QUIET_GARDEN"] as Event).givesQuest)


        val loot = LootPool(blueprints, null)
        val rand = Random(1)
        val sizes = (0 until 300).map { loot.getCombinedWeapon(rand).combinedCount }
        assertTrue(sizes.all { it in 2..3 })
        assertTrue(3 in sizes)
    }

    companion object {
        private val MOD_DIR = File("mods/chonkestrel")
        private val SECTORS = setOf("XFTL_FEDERATION_REMNANT", "XFTL_ANVIL_YARDS", "XFTL_QUIET")
    }
}
