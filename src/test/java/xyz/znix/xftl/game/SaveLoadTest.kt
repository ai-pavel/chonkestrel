package xyz.znix.xftl.game

import org.jdom2.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew

/**
 * A whole game saved and loaded again (as Continue does, without the UI,
 * which automated tests don't have), with a fight against several ships.
 */
class SaveLoadTest {
    private fun fight(playerShip: String): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(WingmanTest.BLUEPRINT_XML),
            extraEvents = listOf(WingmanTest.EVENT_XML),
            playerShip = playerShip
        )
        val spec = kit.state.eventManager.getShip("TEST_ENEMY")
        kit.state.debugSpawnShip(spec, Difficulty.NORMAL, 0, 1)
        // Wingmen are spawned when the game's updated
        kit.state.updateGameState(0.01f)
        return kit
    }

    private fun reload(state: InGameState) = InGameState(null, state.content, state.saveGameState())

    @Test
    fun testAnEnemyWhoseLayoutHasChangedStillLoads() {
        // As a game saved in a fight with one of the mod's generated ships,
        // before they were made again with different rooms and doors: more
        // doors and rooms saved than it has, and fewer, and a crewmember
        // manning a system that isn't in their room any more
        val kit = fight("PLAYER_SHIP_THREE_ENEMIES")
        val enemy = kit.state.enemy!!
        kit.addCrew(enemy, enemy.rooms.first())
        val empty = enemy.rooms.firstOrNull { it.system == null }
        val doc = kit.state.saveGameState()
        val ship = doc.rootElement.getDescendants(org.jdom2.filter.Filters.element())
            .first {
                // (its wingmen have the same name, but no crew)
                it.getAttributeValue("shipId") == enemy.name && it.getChild("doors") != null &&
                        it.getChild("crew")?.getChildren("crewMember")?.isNotEmpty() == true
            }

        val doors = ship.getChild("doors")
        doors.addContent(Element("door").setAttribute("doorIndex", "99").setAttribute("damage", "1"))
        doors.getChild("isOpen").text = "Y"
        ship.getChild("rooms").addContent(Element("room").setAttribute("id", "99"))
        val oxygen = ship.getChild("oxygenLevels")
        oxygen.text = oxygen.textTrim + " 1.0 1.0"
        val crew = ship.getChild("crew").getChildren("crewMember").first()
        crew.setAttribute("action", "MANNING")
        if (empty != null) {
            crew.setAttribute("x", empty.offsetX.toFloat().toString())
            crew.setAttribute("y", empty.offsetY.toFloat().toString())
        }

        val loaded = InGameState(null, kit.state.content, doc)
        assertEquals(enemy.name, loaded.enemy!!.name)
        repeat(20) { loaded.updateGameState(0.05f) }
    }

    @Test
    fun testFightWithSeveralShips() {
        val kit = fight("PLAYER_SHIP_THREE_ENEMIES")
        val enemyName = kit.state.enemy!!.name
        assertEquals(2, kit.state.wingmen.size)

        val loaded = reload(kit.state)
        assertEquals(enemyName, loaded.enemy!!.name)
        assertEquals(listOf(1, 2), loaded.wingmen.map { it.slot })
        assertTrue(loaded.wingmen.all { it.hostile })
        assertTrue(loaded.isInDanger)
        for (wingman in loaded.wingmen)
            assertTrue(loaded.isEnemyOf(loaded.player, wingman.ship))
        // The wingmen aren't spawned again on top of those loaded
        loaded.updateGameState(0.01f)
        assertEquals(2, loaded.wingmen.size)
    }

    @Test
    fun testBeatenWingmanStaysBeaten() {
        val kit = fight("PLAYER_SHIP_THREE_ENEMIES")
        kit.state.wingmen[0].hostile = false

        val loaded = reload(kit.state)
        assertEquals(listOf(false, true), loaded.wingmen.map { it.hostile })
        assertFalse(loaded.isEnemyOf(loaded.player, loaded.wingmen[0].ship))
        assertTrue(loaded.wingmen[0].ship.isAtPeace)
    }

    @Test
    fun testSavingAgainGivesTheSameGame() {
        // Saving what was loaded gives the same save, so nothing's lost or
        // changed by a round trip (apart from what's re-rolled anyway)
        val kit = fight("PLAYER_SHIP_TWO_ENEMIES")
        val loaded = reload(kit.state)
        val again = reload(loaded)
        assertEquals(loaded.enemy!!.name, again.enemy!!.name)
        assertEquals(loaded.wingmen.map { it.ship.name }, again.wingmen.map { it.ship.name })
        assertEquals(loaded.player.health, again.player.health)
        assertEquals(loaded.player.scrap, again.player.scrap)
        assertEquals(loaded.player.fuelCount, again.player.fuelCount)
    }

    @Test
    fun testBeatenEnemyShipIsKeptWithItsWingmen() {
        // A crew-killed main enemy ship stays where it is after loading,
        // rather than a wingman taking its place
        val kit = fight("PLAYER_SHIP_THREE_ENEMIES")
        val names = kit.state.opponents.map { it.ship.name }
        kit.state.debugBeatEnemy()

        val loaded = reload(kit.state)
        assertEquals(names, loaded.opponents.map { it.ship.name })
        assertEquals(listOf(false, true, true), loaded.opponents.map { it.hostile })
        assertEquals(listOf(0, 1, 2), loaded.opponents.map { it.slot })
    }

    @Test
    fun testNoFight() {
        val kit = InGameTest()
        val loaded = reload(kit.state)
        assertNull(loaded.enemy)
        assertTrue(loaded.wingmen.isEmpty())
        assertFalse(loaded.isInDanger)
    }

    @Test
    fun testOptionalAttributes() {
        // An attribute older saves don't have reads as null, rather than
        // failing to load, unlike getAttrIntOrNull
        val elem = Element("thing")
        assertNull(SaveUtil.getOptionalAttrInt(elem, "missing"))
        SaveUtil.addAttrInt(elem, "nothing", null)
        assertNull(SaveUtil.getOptionalAttrInt(elem, "nothing"))
        SaveUtil.addAttrInt(elem, "three", 3)
        assertEquals(3, SaveUtil.getOptionalAttrInt(elem, "three"))
    }
}
