package xyz.znix.xftl.sector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.testkit.InGameTest

/**
 * A quest marked `xftlAhead` (see Event.questAhead) goes on the way to the
 * sector's exit, a few jumps on, or else waits for the next sector; one
 * marked `xftlHidden` (see Event.questHidden) isn't marked on the map.
 */
class QuestPlacementTest {
    private fun Sector.jumpsToExit(beacon: Beacon): Int =
        beacons.filter { it.isExit }.mapNotNull { findShortestPath(beacon, it)?.size }.min()

    @Test
    fun testAheadQuestsGoTowardsTheExit() {
        val kit = InGameTest()
        val map = kit.state.gameMap
        val info = map.sectors.first().single()
        repeat(30) {
            val sector = map.generateSector(info, kit.state)
            val start = sector.startBeacon
            assertTrue(sector.addQuest(start, start.event, ignoreDistance = true, ahead = true))

            val quest = sector.beacons.single { it.hasQuest }
            assertTrue(sector.findShortestPath(start, quest)!!.size >= Sector.AHEAD_QUEST_MIN_JUMPS)
            assertTrue(sector.jumpsToExit(quest) < sector.jumpsToExit(start))

            // And as near the exit as it can be: no other beacon it could
            // have used is nearer it
            val nearer = sector.beacons.filter {
                !it.hasQuest && !it.isExit && !it.hasStore && !it.isOwnNebula && !it.event.isDistressBeacon &&
                        sector.findShortestPath(start, it)!!.size >= Sector.AHEAD_QUEST_MIN_JUMPS &&
                        sector.jumpsToExit(it) < sector.jumpsToExit(quest)
            }
            assertTrue(nearer.isEmpty())
        }
    }

    @Test
    fun testNotBehindThePlayer() {
        val kit = InGameTest()
        val map = kit.state.gameMap
        val info = map.sectors.first().single()
        repeat(30) {
            val sector = map.generateSector(info, kit.state)

            // Next to the exit, there's nowhere ahead to put it
            val exit = sector.beacons.first { it.isExit }
            val nearExit = exit.neighbours.first { !it.isExit }
            assertFalse(sector.addQuest(nearExit, nearExit.event, ignoreDistance = true, ahead = true))

            // A plain quest still goes anywhere
            assertTrue(sector.addQuest(nearExit, nearExit.event, ignoreDistance = true))
        }
    }

    @Test
    fun testHiddenQuestsArentMarked() {
        val kit = InGameTest()
        val start = kit.state.currentBeacon
        val sector = start.sector
        val quest = sector.beacons.first { it != start && !it.event.isStore }.event
        assertTrue(sector.addQuest(start, quest, ignoreDistance = true, hidden = true))

        // Its beacon has the quest's event, but isn't marked as a quest's
        val beacon = sector.beacons.single { it.hasHiddenQuest }
        assertEquals(quest, beacon.event)
        assertTrue(sector.beacons.none { it.hasQuest })

        // Another quest, hidden or not, goes elsewhere
        assertTrue(sector.addQuest(start, quest, ignoreDistance = true))
        assertTrue(sector.beacons.single { it.hasQuest } != beacon)

        // It's saved with the game
        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        val index = sector.beacons.indexOf(beacon)
        val again = loaded.currentBeacon.sector.beacons[index]
        assertTrue(again.hasHiddenQuest)
        assertFalse(again.hasQuest)
        assertEquals(quest.deserialisationId, again.event.deserialisationId)
    }
}
