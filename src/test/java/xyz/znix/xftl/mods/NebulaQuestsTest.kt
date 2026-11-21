package xyz.znix.xftl.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.Translator
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.EventList
import xyz.znix.xftl.sector.EventManager
import xyz.znix.xftl.sector.IEvent
import java.io.File

/**
 * The Chonkestrel mod's nebula sectors' quests (see doc/game/sectors.md):
 * each sector's start event marks its first beacon, which leads on to the
 * second, whose reward is an augment found nowhere else.
 */
class NebulaQuestsTest {
    private val modded = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(File("mods/chonkestrel").toPath())))
    private val blueprints = BlueprintManager(modded, true)
    private val translator = Translator(modded, "en")
    private val events = EventManager(modded, translator, blueprints)

    // Every event an event can lead to: through its choices, the lists they
    // load, and the ships it fights (their rewards)
    private fun reachable(event: IEvent, seen: MutableSet<IEvent> = HashSet()): Set<Event> {
        if (!seen.add(event))
            return emptySet()
        val found = HashSet<Event>()
        when (event) {
            is Event -> {
                found += event
                for (choice in event.choices)
                    found += reachable(choice.event, seen)
                event.loadShipName?.let { name ->
                    val ship = events.getShip(name)
                    for (end in listOfNotNull(ship.destroyed, ship.deadCrew))
                        found += reachable(end, seen)
                }
            }
            is EventList -> event.events.forEach { found += reachable(it, seen) }
        }
        return found
    }

    @Test
    fun eachSectorsQuestEndsInItsOwnAugment() {
        for ((start, first, second, augment) in QUESTS) {
            val startEvent = events[start] as Event
            assertEquals(start, first, startEvent.questName)
            assertTrue(start, startEvent.questAhead)

            // The first beacon leads on to the second
            val firstEvents = reachable(events[first])
            assertTrue(first, firstEvents.any { it.questName == second })

            // whose reward is the augment, found nowhere else
            assertTrue(second, reachable(events[second]).any { augment in it.blueprintRewards })
            val blueprint = blueprints[augment] as AugmentBlueprint
            assertEquals(augment, 0, blueprint.rarity)
        }
    }

    private data class Quest(val start: String, val first: String, val second: String, val augment: String)

    companion object {
        private val QUESTS = listOf(
            Quest("XFTL_RADFIELDS_START", "XFTL_RADFIELDS_STATION", "XFTL_RADFIELDS_CORE", AugmentBlueprint.RADIATION_SHIELDING),
            Quest("XFTL_MIRAGE_EXPANSE_START", "XFTL_MIRAGE_GHOSTS", "XFTL_MIRAGE_DERELICT", AugmentBlueprint.PHANTOM_PROJECTOR),
            Quest("XFTL_DARK_REACH_START", "XFTL_DARK_HULK_TRAIL", "XFTL_DARK_HULK", AugmentBlueprint.DEEP_FIELD_SENSORS),
        )
    }
}
