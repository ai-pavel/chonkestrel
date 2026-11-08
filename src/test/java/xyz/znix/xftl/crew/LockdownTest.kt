package xyz.znix.xftl.crew

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.Damage

/**
 * A Crystal's lockdown (and the Lockdown Bomb's) seals a room's doors: no
 * one gets in or out until it wears off.
 */
class LockdownTest {
    private fun createKit(): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML),
            extraEvents = listOf(EVENT_XML), playerShip = "PLAYER_SHIP_LOCKDOWN_TEST"
        )
        kit.powerUpAll()
        return kit
    }

    // (with air for the crew, as the test ship has no oxygen system)
    private fun InGameTest.run(seconds: Float) = repeat((seconds * 20).toInt()) {
        for (ship in listOfNotNull(player, state.enemy))
            ship.rooms.forEach { it.oxygen = 1f }
        state.updateGameState(0.05f)
    }

    @Test
    fun testALockdownSealsTheRoom() {
        val kit = createKit()
        val hall = kit.player.rooms[5]
        val side = kit.player.rooms[0]
        val crystal = kit.addCrew(kit.player, hall, race = "crystal") as CrewCrystal
        val door = hall.doors.single { it.other(hall) == side }
        door.toggle()
        assertTrue(door.open)

        assertTrue(crystal.lockDown())
        assertTrue(hall.isLockedDown)

        // The door's shut, and stays shut
        assertTrue(door.isSealed)
        assertFalse(door.open)
        door.toggle()
        assertFalse(door.open)

        // Until it wears off, when the door's open again, as it was
        kit.run(CrewCrystal.LOCKDOWN_TIME + 0.5f)
        assertFalse(hall.isLockedDown)
        assertTrue(door.open)
    }

    @Test
    fun testCrewShootAtItInVain() {
        val kit = createKit()
        val room = kit.player.rooms[0]
        val hall = kit.player.rooms[5]
        val outside = kit.addCrew(kit.player, hall)
        val inside = kit.addCrew(kit.player, kit.player.rooms[1])
        kit.run(0.5f)
        room.lockDown(CrewCrystal.LOCKDOWN_TIME)
        kit.player.rooms[1].lockDown(CrewCrystal.LOCKDOWN_TIME)

        // Sent in or out, they go, but stop at the door and shoot at it...
        assertTrue(outside.setTargetRoom(room))
        assertTrue(inside.setTargetRoom(hall))
        kit.run(5f)
        assertEquals(hall, outside.room)
        assertEquals(AbstractCrew.Action.ATTACKING_DOOR, outside.currentAction)
        assertEquals(kit.player.rooms[1], inside.room)
        assertEquals(AbstractCrew.Action.ATTACKING_DOOR, inside.currentAction)
        assertTrue(room.doors.none { it.isBroken })

        // ...and go on through once it's lifted
        kit.run(CrewCrystal.LOCKDOWN_TIME)
        assertEquals(room, outside.room)
        assertEquals(hall, inside.room)
    }

    @Test
    fun testCrewCanBeSentIntoALockedRoom() {
        val kit = createKit()
        val room = kit.player.rooms[0]
        val hall = kit.player.rooms[5]
        val human = kit.addCrew(kit.player, kit.player.rooms[2])
        kit.run(0.5f)
        room.lockDown(CrewCrystal.LOCKDOWN_TIME)

        // Sent from the far side of the ship, they go as far as the door,
        // and keep at it until the lockdown's lifted
        assertTrue(human.setTargetRoom(room))
        kit.run(5f)
        assertEquals(hall, human.room)
        assertEquals(AbstractCrew.Action.ATTACKING_DOOR, human.currentAction)
        assertEquals(room, human.pathingTarget?.room)

        kit.run(CrewCrystal.LOCKDOWN_TIME)
        assertEquals(room, human.room)
    }

    @Test
    fun testASealedDoorCantBeBrokenDown() {
        val kit = createKit()
        val room = kit.player.rooms[0]
        val door = room.doors.single { it.other(room) == kit.player.rooms[5] }
        room.lockDown(CrewCrystal.LOCKDOWN_TIME)

        repeat(100) { door.attackDoor() }
        assertFalse(door.isBroken)
        assertFalse(door.open)
    }

    @Test
    fun testItRecharges() {
        val kit = createKit()
        val crystal = kit.addCrew(kit.player, kit.player.rooms[0], race = "crystal") as CrewCrystal

        assertTrue(crystal.lockDown())
        assertFalse(crystal.lockDown())
        kit.run(CrewCrystal.LOCKDOWN_COOLDOWN - 1f)
        assertFalse(crystal.lockDown())
        kit.run(1.5f)
        assertTrue(crystal.lockDown())
    }

    @Test
    fun testAJumpRechargesIt() {
        val kit = createKit()
        val crystal = kit.addCrew(kit.player, kit.player.rooms[0], race = "crystal") as CrewCrystal

        assertTrue(crystal.lockDown())
        assertFalse(crystal.lockDown())
        kit.player.resetAfterJump()
        assertEquals(0f, crystal.lockdownCooldown, 0f)
        assertTrue(crystal.canLockDown)
    }

    @Test
    fun testItsSaved() {
        val kit = createKit()
        val hall = kit.player.rooms[5]
        val ready = kit.addCrew(kit.player, hall, race = "crystal") as CrewCrystal
        val used = kit.addCrew(kit.player, kit.player.rooms[0], race = "crystal") as CrewCrystal
        assertTrue(used.lockDown())
        kit.run(5f)

        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        fun crystal(c: CrewCrystal) = loaded.player.crew.filterIsInstance<CrewCrystal>().single { it.info.name == c.info.name }

        // One that's ready (with nothing saved for it) and one recharging,
        // and the room it locked down
        assertEquals(0f, crystal(ready).lockdownCooldown, 0.001f)
        assertEquals(used.lockdownCooldown, crystal(used).lockdownCooldown, 0.001f)
        assertEquals(kit.player.rooms[0].lockdownTime, loaded.player.rooms[0].lockdownTime, 0.001f)
        assertTrue(loaded.player.rooms[0].isLockedDown)
        assertFalse(loaded.player.rooms[5].isLockedDown)
    }

    @Test
    fun testTheLockdownBomb() {
        val kit = createKit()
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_LOCKDOWN_ENEMY"), Difficulty.NORMAL, 0, 1)
        val enemy = kit.state.enemy!!
        val bomb = kit.state.blueprintManager["TEST_LOCKDOWN_BOMB"] as AbstractWeaponBlueprint
        assertTrue(bomb.lockdown)

        val room = enemy.rooms[2]
        enemy.damage(room, Damage(bomb))
        assertTrue(room.isLockedDown)
        assertFalse(enemy.rooms[3].isLockedDown)
    }

    @Test
    fun testTheEnemysCrystalsLockDownAFight() {
        val kit = createKit()
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_LOCKDOWN_ENEMY"), Difficulty.NORMAL, 0, 1)
        val enemy = kit.state.enemy!!
        val room = enemy.rooms[2]
        val crystal = kit.addCrew(enemy, room, race = "crystal") as CrewCrystal
        kit.run(1f)

        // Nothing to lock down yet
        assertFalse(room.isLockedDown)

        // A boarder of the player's comes in, and it's locked in with them
        kit.addCrew(enemy, room, mode = AbstractCrew.SlotType.INTRUDER, owner = kit.player)
        kit.run(0.5f)
        assertTrue(room.isLockedDown)
        assertTrue(crystal.lockdownCooldown > 0f)
    }

    @Test
    fun testThePlayersCrystalsWaitToBeTold() {
        val kit = createKit()
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_LOCKDOWN_ENEMY"), Difficulty.NORMAL, 0, 1)
        val room = kit.player.rooms[2]
        kit.addCrew(kit.player, room, race = "crystal")
        kit.addCrew(kit.player, room, mode = AbstractCrew.SlotType.INTRUDER, owner = kit.state.enemy)
        kit.run(0.5f)
        assertFalse(room.isLockedDown)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <crewBlueprint name="crystal">
        <cost>1</cost>
    </crewBlueprint>

    <weaponBlueprint name="TEST_LOCKDOWN_BOMB">
        <type>BOMB</type>
        <damage>0</damage>
        <lockdown>1</lockdown>
        <missiles>1</missiles>
        <cooldown>15</cooldown>
        <power>1</power>
        <cost>45</cost>
        <rarity>0</rarity>
        <weaponArt>generic_test_weapon</weaponArt>
        <explosion>dummy_anim</explosion>
    </weaponBlueprint>

    <shipBlueprint name="PLAYER_SHIP_LOCKDOWN_TEST" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_LOCKDOWN_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="2" max="2" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <ship name="TEST_LOCKDOWN_ENEMY" blueprint="TEST_LOCKDOWN_ENEMY_SHIP"/>
</FTL>
"""

        // The Crystals' animations, as the humans' in the test kit
        private val ANIMATIONS_XML = (
                listOf("portrait", "repair", "death_right", "teleport") +
                        listOf("up", "down", "left", "right").flatMap { dir ->
                            listOf("shoot", "walk", "punch", "type", "fire").map { "${it}_$dir" }
                        }
                ).joinToString("\n", "<FTL>\n", "\n</FTL>") {
                """<anim name="crystal_$it"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>"""
            }
    }
}
