package xyz.znix.xftl.crew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.RoomPoint
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew

/**
 * Living crew fighting each other aboard a ship: fist fights, shooting
 * across a room, being stunned, and the crew list.
 */
class CrewCombatTest {
    // Without the crew AI, which would send them off to their stations (and
    // the boarders to the systems), so they stay where they're put
    private val kit = InGameTest().also { it.state.debugFlags.noCrewAI.set = true }
    private val player get() = kit.player

    // The test ship's big room, 5x5 (the others are 1x1)
    private val bigRoom get() = player.rooms.first { it.width == 5 && it.height == 5 }

    private fun crewAt(x: Int, y: Int, mode: AbstractCrew.SlotType = AbstractCrew.SlotType.CREW): LivingCrew {
        val crew = kit.addCrew(player, bigRoom, mode)
        val point = RoomPoint(bigRoom, ConstPoint(x, y))
        crew.jumpTo(point)
        // Stay there (boarders are given somewhere to head for when they arrive)
        crew.setTargetRoom(bigRoom, point)
        return crew
    }

    private fun run(seconds: Float) = repeat((seconds * 20).toInt()) { kit.updateSingle(0.05f) }

    private fun alive(crew: AbstractCrew) = player.crew.contains(crew) && crew.health > 0f

    @Test
    fun testFistFightEndsWithOneDead() {
        // A crewmember and a boarder in the same cell fight until one dies
        val crew = crewAt(2, 2)
        val boarder = crewAt(2, 2, AbstractCrew.SlotType.INTRUDER)
        run(60f)
        assertTrue("one of them is dead", !alive(crew) || !alive(boarder))
        assertTrue("one of them is alive", alive(crew) || alive(boarder))
    }

    @Test
    fun testBoardersAreShotAcrossTheRoom() {
        // Crew in the same room, not the same cell, still hurt each other
        val crew = crewAt(0, 0)
        val boarder = crewAt(4, 4, AbstractCrew.SlotType.INTRUDER)
        run(3f)
        assertTrue(boarder.health < boarder.maxHealth)
        assertTrue(crew.health < crew.maxHealth)
    }

    @Test
    fun testStunnedCrewDontFightBack() {
        val crew = crewAt(2, 2)
        val boarder = crewAt(2, 2, AbstractCrew.SlotType.INTRUDER)
        crew.stunTime = 3f
        run(2f)
        // The boarder hits the stunned crewmember, who doesn't hit back
        assertEquals(boarder.maxHealth, boarder.health, 0.001f)
        assertTrue(crew.health < crew.maxHealth)
        assertTrue(crew.stunTime > 0f)

        // Once it wears off, they do
        run(3f)
        assertEquals(0f, crew.stunTime, 0f)
        assertTrue(boarder.health < boarder.maxHealth)
    }

    @Test
    fun testStunIsSaved() {
        val crew = crewAt(1, 1)
        crew.stunTime = 4f
        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        val loadedCrew = loaded.player.crew.filterIsInstance<LivingCrew>().single { it.info.name == crew.info.name }
        assertEquals(4f, loadedCrew.stunTime, 0.001f)
    }

    @Test
    fun testCrewListKeepsTheOrderTheyJoined() {
        val first = crewAt(0, 0)
        val second = crewAt(1, 0)
        kit.state.updatePlayerCrew()
        assertEquals(listOf(first, second), kit.state.playerCrew.filter { it in listOf(first, second) })

        // Even if they move about in the ship's list (as boarders coming back
        // do), they're listed in the same order
        player.crew.remove(first)
        player.crew.add(first)
        kit.state.updatePlayerCrew()
        assertEquals(listOf(first, second), kit.state.playerCrew.filter { it in listOf(first, second) })
    }

    @Test
    fun testCrewSavedOutsideTheRoomsArePutBack() {
        // A save made with a crewmember outside every room (it happened once)
        // still loads, with them put in the nearest room
        val crew = crewAt(0, 0)
        val doc = kit.state.saveGameState()
        val elem = doc.rootElement.getChild("playerShip").getChild("crew").children
            .single { it.getAttributeValue("selectedName") == crew.info.name }
        elem.setAttribute("x", "-200.0")
        elem.setAttribute("y", "-200.0")

        val loaded = InGameState(null, kit.state.content, doc)
        val loadedCrew = loaded.player.crew.filterIsInstance<LivingCrew>().single { it.info.name == crew.info.name }
        assertNotNull(loadedCrew.room)
        assertTrue(loadedCrew.room.containsShipSpace(loadedCrew.getPixelPositionCentre()))
        assertFalse(loadedCrew.health <= 0f)
    }
}
