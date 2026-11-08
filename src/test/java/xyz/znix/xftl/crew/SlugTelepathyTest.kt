package xyz.znix.xftl.crew

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.testkit.InGameTest

/**
 * Check the player's slugs let them see into the rooms next to theirs, even
 * without sensors, such as in a nebula.
 */
class SlugTelepathyTest {
    @Test
    fun slugsSeeIntoTheRoomsNextToThem() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_SLUG_TEST")
        val player = kit.player

        // In a nebula, the sensors don't work
        val beacon = kit.state.currentBeacon
        beacon.sectorHazards = listOf(Beacon.EnvironmentType.NEBULA)
        beacon.clearEnvironment()

        // The test ship has five small rooms in a row along the top (0-4),
        // with a big room (5) under them all
        val rooms = player.rooms
        assertEquals(setOf(rooms[1], rooms[5]), rooms[0].adjacentRooms.toSet())

        val type = kit.state.blueprintManager["test_crew"] as CrewBlueprint
        val slug = CrewSlug(type, kit.state.animations, rooms[0], AbstractCrew.SlotType.CREW)
        player.crew.add(slug)
        slug.jumpTo(player.findSpaceForCrew(rooms[0], AbstractCrew.SlotType.CREW))

        repeat(2) { player.update(0.01f) }
        assertTrue(slug.givesPlayerTelepathy)
        assertTrue(rooms[0].playerHasVision) // The slug's own room
        assertTrue(rooms[1].playerHasVision)
        assertTrue(rooms[5].playerHasVision)
        assertFalse(rooms[2].playerHasVision)
        assertFalse(rooms[4].playerHasVision)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="PLAYER_SHIP_SLUG_TEST" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""
    }
}
