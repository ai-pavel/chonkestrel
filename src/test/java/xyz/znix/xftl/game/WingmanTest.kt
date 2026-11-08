package xyz.znix.xftl.game

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.LazyShipBlueprint
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.shipgen.ShipScaling
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew
import kotlin.random.Random

class WingmanTest {
    private fun createKit(playerShip: String, enemy: String = "TEST_ENEMY"): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML),
            extraEvents = listOf(EVENT_XML),
            playerShip = playerShip
        )
        val spec = kit.state.eventManager.getShip(enemy)
        kit.state.debugSpawnShip(spec, Difficulty.NORMAL, 0, 1)

        // Wingmen are spawned when the game's updated
        kit.state.updateGameState(0.01f)
        return kit
    }

    @Test
    fun testWingmanIsSpawned() {
        val kit = createKit("PLAYER_SHIP_TWO_ENEMIES")
        val enemy = assertNotNull(kit.state.enemy).let { kit.state.enemy!! }
        val wingman = kit.state.wingmen.single()

        assertTrue(kit.state.isInDanger)
        assertTrue(kit.state.isShipPresent(wingman.ship))

        // The player can fight both ships, and both fight the player
        assertTrue(kit.state.isEnemyOf(kit.player, enemy))
        assertTrue(kit.state.isEnemyOf(kit.player, wingman.ship))
        assertSame(kit.player, kit.state.getEnemyOf(wingman.ship))
        assertSame(enemy, kit.state.getEnemyOf(kit.player))
    }

    @Test
    fun testWingmenArriveWithTheEvent() {
        // The wingmen are there as soon as an event brings in a hostile
        // ship, so they're on screen with its window, before the game's
        // updated again
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML),
            extraEvents = listOf(EVENT_XML),
            playerShip = "PLAYER_SHIP_TWO_ENEMIES"
        )
        kit.state.loadEventShip(kit.state.eventManager["TEST_FIGHT"].resolve())

        assertNotNull(kit.state.enemy)
        assertEquals(1, kit.state.wingmen.size)

        // And updating the game doesn't add any more
        kit.state.updateGameState(0.01f)
        assertEquals(1, kit.state.wingmen.size)
    }

    @Test
    fun testShipsAtPeaceTakeNoDamage() {
        // As in vanilla, once a ship's no longer fighting (it surrendered,
        // say), the shots still on their way do it no harm
        val kit = createKit("PLAYER_SHIP_TWO_ENEMIES")
        val wingman = kit.state.wingmen.single()
        val ship = wingman.ship
        val room = ship.rooms.first()

        val before = ship.health
        ship.damage(room, xyz.znix.xftl.weapons.Damage.hullOnly(1))
        assertEquals(before - 1, ship.health)

        wingman.hostile = false
        assertTrue(ship.isAtPeace)
        ship.damage(room, xyz.znix.xftl.weapons.Damage.hullOnly(1))
        assertEquals(before - 1, ship.health)
        assertFalse(kit.player.isAtPeace)
    }

    @Test
    fun testOpponentShips() {
        val kit = createKit("PLAYER_SHIP_TWO_ENEMIES")
        val enemy = kit.state.enemy!!
        val wingman = kit.state.wingmen.single().ship

        // The player fights both, and drones are sent to either
        assertEquals(listOf(enemy, wingman), kit.state.getOpponentShips(kit.player))
        assertEquals(listOf(enemy, wingman), kit.state.allEnemyShips)
        assertTrue(kit.state.pickDroneTarget(kit.player) in listOf(enemy, wingman))

        // Each of them only fights the player
        assertEquals(listOf(kit.player), kit.state.getOpponentShips(enemy))
        assertEquals(listOf(kit.player), kit.state.getOpponentShips(wingman))
        assertSame(kit.player, kit.state.pickDroneTarget(wingman))
    }

    @Test
    fun testEachSensorsSystemSeesOneShip() {
        val kit = createKit("PLAYER_SHIP_TWO_ENEMIES")
        val player = kit.player
        val sensors = player.sensorSystems
        assertEquals(2, sensors.size)

        // Left to right, as the windows are: the wingman's is left of the
        // enemy ship's
        assertSame(sensors[0], player.sensorsFor(kit.state.wingmen.single().ship))
        assertSame(sensors[1], player.sensorsFor(kit.state.enemy!!))
    }

    @Test
    fun testBigShipsHaveMoreHull() {
        val big = createKit("PLAYER_SHIP_TWO_ENEMIES").player
        assertEquals(2 * big.type.maxHealth, big.maxHealth)
        assertEquals(big.maxHealth, big.health)

        val vanilla = createKit("TEST_SHIP").player
        assertEquals(vanilla.type.maxHealth, vanilla.maxHealth)
    }

    @Test
    fun testBigShipsUseMoreFuel() {
        val big = createKit("PLAYER_SHIP_TWO_ENEMIES").player
        assertEquals(2, big.fuelPerJump)
        assertEquals(48, big.fuelCount)

        val vanilla = createKit("TEST_SHIP").player
        assertEquals(1, vanilla.fuelPerJump)

        // Half again as much as vanilla's, for the wider sector maps (the
        // test ship isn't one of the player ships, so it doesn't get it itself)
        assertEquals(24, Ship.startingFuel(1))
    }

    @Test
    fun testVanillaShipsFightOneShip() {
        val kit = createKit("TEST_SHIP")
        assertNotNull(kit.state.enemy)
        assertTrue(kit.state.wingmen.isEmpty())
    }

    @Test
    fun testBigShipsFightOneBigShip() {
        val kit = createKit("PLAYER_SHIP_TWO_ENEMIES", "TEST_BIG_ENEMY")
        assertNotNull(kit.state.enemy)
        assertTrue(kit.state.wingmen.isEmpty())
    }

    @Test
    fun testBoardingMultiplier() {
        // Same-size ships board like vanilla
        val vanilla = createKit("TEST_SHIP")
        assertEquals(1, ShipScaling.boardingMultiplier(vanilla.state, vanilla.state.enemy!!, 0))
        assertEquals(1, ShipScaling.boardingMultiplier(vanilla.state, vanilla.state.enemy!!, 7))

        // Against a ship twice their size, boarding parties start the usual
        // size in the first sector, and grow to four times by the last.
        val kit = createKit("PLAYER_SHIP_TWO_ENEMIES")
        val enemy = kit.state.enemy!!
        assertEquals(1, ShipScaling.boardingMultiplier(kit.state, enemy, 0))
        assertEquals(1, ShipScaling.boardingMultiplier(kit.state, enemy, 1))
        assertEquals(3, ShipScaling.boardingMultiplier(kit.state, enemy, 4))
        assertEquals(4, ShipScaling.boardingMultiplier(kit.state, enemy, 7))
    }

    @Test
    fun testThreeEnemyShips() {
        val kit = createKit("PLAYER_SHIP_THREE_ENEMIES")
        val enemy = kit.state.enemy!!
        val wingmen = kit.state.wingmen.map { it.ship }
        assertEquals(2, wingmen.size)
        assertEquals(listOf(1, 2), kit.state.wingmen.map { it.slot })

        // When the enemy ship is destroyed, the first wingman takes its place,
        // and the other is kept, moving over to the first wingman's window.
        enemy.health = 0
        repeat(5) { kit.state.updateGameState(0.5f) }
        assertSame(wingmen[0], kit.state.enemy)
        assertEquals(listOf(wingmen[1]), kit.state.wingmen.map { it.ship })
        assertEquals(listOf(1), kit.state.wingmen.map { it.slot })
        assertTrue(kit.state.isEnemyOf(kit.player, wingmen[1]))
    }

    @Test
    fun testWingmenKeepTheirAIWhenOneGoes() {
        // The ships left aren't made again when one goes, so their AI (and
        // windows) carry on, only moving over
        val kit = createKit("PLAYER_SHIP_THREE_ENEMIES")
        val second = kit.state.wingmen[1]
        val ai = second.ai
        kit.state.wingmen[0].ship.health = 0
        repeat(5) { kit.state.updateGameState(0.5f) }
        assertSame(second, kit.state.wingmen.single())
        assertSame(ai, second.ai)
        assertEquals(1, second.slot)
    }

    @Test
    fun testShipsLeftToRight() {
        // The windows go to the left of the main enemy ship's, so the
        // player's sensors look at the last wingman first, whether or not
        // the windows have been drawn
        val kit = createKit("PLAYER_SHIP_THREE_ENEMIES")
        val ships = kit.state.opponents.map { it.ship }
        assertEquals(ships.reversed(), kit.state.hostileShipsLeftToRight)
    }

    @Test
    fun testWingmenMoveOver() {
        val kit = createKit("PLAYER_SHIP_THREE_ENEMIES")
        val wingmen = kit.state.wingmen.map { it.ship }

        // Destroying the first wingman moves the second into its window
        wingmen[0].health = 0
        repeat(5) { kit.state.updateGameState(0.5f) }
        assertEquals(listOf(wingmen[1]), kit.state.wingmen.map { it.ship })
        assertEquals(listOf(1), kit.state.wingmen.map { it.slot })
    }

    @Test
    fun testEnemyShipCount() {
        val bp = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML)).state.blueprintManager
        fun ship(name: String) = (bp[name] as LazyShipBlueprint).real

        assertEquals(1, Opponent.enemyShipCount(ship("TEST_SHIP"), ship("TEST_ENEMY_SHIP")))
        assertEquals(2, Opponent.enemyShipCount(ship("PLAYER_SHIP_TWO_ENEMIES"), ship("TEST_ENEMY_SHIP")))
        assertEquals(1, Opponent.enemyShipCount(ship("PLAYER_SHIP_TWO_ENEMIES"), ship("TEST_BIG_ENEMY_SHIP")))

        // A small player ship still fights a big ship
        assertEquals(1, Opponent.enemyShipCount(ship("TEST_SHIP"), ship("TEST_BIG_ENEMY_SHIP")))

        // A ship can fight more vanilla ships than its size, but not more ships its own size
        assertEquals(3, Opponent.enemyShipCount(ship("PLAYER_SHIP_THREE_ENEMIES"), ship("TEST_ENEMY_SHIP")))
        assertEquals(1, Opponent.enemyShipCount(ship("PLAYER_SHIP_THREE_ENEMIES"), ship("TEST_BIG_ENEMY_SHIP")))
    }

    @Test
    fun testOpponentsRampUp() {
        val bp = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML)).state.blueprintManager
        fun ship(name: String) = (bp[name] as LazyShipBlueprint).real
        val player = ship("PLAYER_SHIP_RAMP")
        val enemy = ship("TEST_ENEMY_SHIP")
        val random = Random(1)
        fun average(sector: Int) = (0 until 1000).map {
            Opponent.rampedEnemyShipCount(player, enemy, sector, 16, random)
        }.average()

        // One ship at the start, three by the end of the first half, and
        // somewhere in between before that
        assertEquals(1.0, average(0), 0.0)
        assertEquals(3.0, average(7), 0.0)
        assertEquals(3.0, average(12), 0.0)
        assertEquals(1 + 2 * 3 / 7.0, average(3), 0.1)
        assertTrue(average(2) < average(5))

        // Without opponentShipsAtStart, there's no ramp
        val noRamp = ship("PLAYER_SHIP_THREE_ENEMIES")
        assertEquals(3, Opponent.rampedEnemyShipCount(noRamp, enemy, 0, 16, random))

        // And it never goes over the ship's own size
        assertEquals(1, Opponent.rampedEnemyShipCount(player, ship("TEST_BIG_ENEMY_SHIP"), 7, 16, random))
    }

    @Test
    fun testBigShipsAreUsedWhenThereAreSome() {
        // A size two player fights the big ship from the list on its own
        val kit = createKit("PLAYER_SHIP_THREE_ENEMIES", "TEST_BATTLEGROUP")
        assertEquals("TEST_BIG_ENEMY_SHIP", kit.state.enemy!!.type.name)
        assertTrue(kit.state.wingmen.isEmpty())

        // A vanilla player fights the vanilla ship
        val vanilla = createKit("TEST_SHIP", "TEST_BATTLEGROUP")
        assertEquals("TEST_ENEMY_SHIP", vanilla.state.enemy!!.type.name)

        // Until there are big ships in the list, the big player fights
        // several vanilla ships instead
        val empty = createKit("PLAYER_SHIP_THREE_ENEMIES", "TEST_EMPTY_BATTLEGROUP")
        assertEquals("TEST_ENEMY_SHIP", empty.state.enemy!!.type.name)
        assertEquals(2, empty.state.wingmen.size)
    }

    @Test
    fun testWingmanTakesOverWhenTheEnemyIsDestroyed() {
        val kit = createKit("PLAYER_SHIP_TWO_ENEMIES")
        val wingman = kit.state.wingmen.single().ship

        kit.state.enemy!!.health = 0
        repeat(5) { kit.state.updateGameState(0.5f) }

        // The wingman is now the enemy ship, and doesn't get a wingman of its own
        assertSame(wingman, kit.state.enemy)
        assertTrue(kit.state.wingmen.isEmpty())
        assertTrue(kit.state.isEnemyOf(kit.player, wingman))

        // Once it's destroyed too, the fight's over
        wingman.health = 0
        repeat(5) { kit.state.updateGameState(0.5f) }
        assertFalse(kit.state.isInDanger)
        assertEquals(null, kit.state.enemy)
    }

    private fun acceptSurrender(kit: InGameTest) =
        kit.state.loadEventShip(kit.state.eventManager["TEST_ACCEPT_SURRENDER"].resolve() as Event)

    @Test
    fun testAWingmansSurrenderIsForItself() {
        val kit = createKit("PLAYER_SHIP_TWO_ENEMIES", "TEST_SURRENDERING_ENEMY")
        val enemy = kit.state.enemy!!
        val wingman = kit.state.wingmen.single()

        // It holds out longer than the main ship
        assertEquals(4, enemy.surrenderHealth)
        assertEquals(2, wingman.ship.surrenderHealth)

        // Its surrender accepted, only it stops fighting
        kit.state.surrenderOffered(wingman.ship, true)
        acceptSurrender(kit)
        assertFalse(kit.state.isEnemyOf(kit.player, wingman.ship))
        assertTrue(kit.state.isEnemyOf(kit.player, enemy))
    }

    @Test
    fun testTheLeaderCanSurrenderForThemAll() {
        // Speaking only for itself...
        val alone = createKit("PLAYER_SHIP_TWO_ENEMIES", "TEST_SURRENDERING_ENEMY")
        alone.state.surrenderOffered(alone.state.enemy!!, false)
        acceptSurrender(alone)
        assertFalse(alone.state.isEnemyOf(alone.player, alone.state.enemy!!))
        assertTrue(alone.state.isEnemyOf(alone.player, alone.state.wingmen.single().ship))

        // ...or for them all
        val all = createKit("PLAYER_SHIP_TWO_ENEMIES", "TEST_SURRENDERING_ENEMY")
        all.state.surrenderOffered(all.state.enemy!!, true)
        acceptSurrender(all)
        assertFalse(all.state.isEnemyOf(all.player, all.state.enemy!!))
        assertFalse(all.state.isEnemyOf(all.player, all.state.wingmen.single().ship))
        assertFalse(all.state.isInDanger)
    }

    @Test
    fun testBoardersGoHomeAfterASurrender() {
        val kit = createKit("PLAYER_SHIP_TWO_ENEMIES", "TEST_SURRENDERING_ENEMY")
        val enemy = kit.state.enemy!!
        val wingman = kit.state.wingmen.single().ship
        val ours = kit.addCrew(enemy, enemy.rooms[0], mode = AbstractCrew.SlotType.INTRUDER, owner = kit.player)
        val theirs = kit.addCrew(kit.player, kit.player.rooms[0], mode = AbstractCrew.SlotType.INTRUDER, owner = enemy)
        val wingmans = kit.addCrew(kit.player, kit.player.rooms[1], mode = AbstractCrew.SlotType.INTRUDER, owner = wingman)

        // Only the main ship surrenders: the boarders on either side of
        // that go home, and the wingman's stay
        kit.state.surrenderOffered(enemy, false)
        acceptSurrender(kit)
        repeat(40) { kit.state.updateGameState(0.1f) }
        assertSame(kit.player, ours.room.ship)
        assertSame(enemy, theirs.room.ship)
        assertSame(kit.player, wingmans.room.ship)
    }

    @Test
    fun testWingmenEscape() {
        val kit = createKit("PLAYER_SHIP_TWO_ENEMIES", "TEST_SURRENDERING_ENEMY")
        val enemy = kit.state.enemy!!
        val wingman = kit.state.wingmen.single().ship

        // Badly hurt, it starts its FTL drive, and goes, leaving the main ship
        wingman.health = 5
        repeat(40) { kit.state.updateGameState(0.1f) }
        assertFalse(kit.state.isShipPresent(wingman))
        assertTrue(kit.state.isShipPresent(enemy))
        assertTrue(kit.state.isEnemyOf(kit.player, enemy))
    }

    companion object {
        @Language("XML")
        internal const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="PLAYER_SHIP_TWO_ENEMIES" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <shipSize>2</shipSize>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <weapons power="1" max="1" room="3"/>
            <sensors power="2" room="4"/>
            <sensors power="2" room="5"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PLAYER_SHIP_THREE_ENEMIES" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <shipSize>2</shipSize>
        <opponentShips>3</opponentShips>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <weapons power="1" max="1" room="3"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PLAYER_SHIP_RAMP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <shipSize>2</shipSize>
        <opponentShips>3</opponentShips>
        <opponentShipsAtStart>1</opponentShipsAtStart>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>

    <blueprintList name="TEST_BIG_SHIPS">
        <name>TEST_BIG_ENEMY_SHIP</name>
    </blueprintList>
    <blueprintList name="TEST_NO_BIG_SHIPS">
    </blueprintList>

    <systemBlueprint name="sensors">
        <type>sensors</type>
        <startPower>1</startPower>
        <maxPower>4</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <shipBlueprint name="TEST_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="10"/>
        <maxPower amount="10"/>
        <!-- An automated ship, as tests can't spawn real crew -->
        <crewCount amount="0" max="0" class="test_crew"/>

        <systemList>
            <oxygen power="1" max="1" room="0" start="true"/>
            <pilot power="1" max="1" room="1" start="true"/>
            <engines power="1" max="1" room="2" start="true"/>
        </systemList>
    </shipBlueprint>

    <!-- The same, but as big as the player's ship -->
    <shipBlueprint name="TEST_BIG_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="10"/>
        <maxPower amount="10"/>
        <shipSize>2</shipSize>
        <crewCount amount="0" max="0" class="test_crew"/>

        <systemList>
            <oxygen power="1" max="1" room="0" start="true"/>
            <pilot power="1" max="1" room="1" start="true"/>
            <engines power="1" max="1" room="2" start="true"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""

        @Language("XML")
        internal const val EVENT_XML = """
<FTL>
    <ship name="TEST_ENEMY" auto_blueprint="TEST_ENEMY_SHIP"/>
    <ship name="TEST_SURRENDERING_ENEMY" auto_blueprint="TEST_ENEMY_SHIP">
        <surrender chance="0" min="4" max="4">
            <text>They surrender.</text>
            <choice><text>Accept.</text><event name="TEST_ACCEPT_SURRENDER"/></choice>
        </surrender>
        <escape chance="0" min="6" max="6" timer="1">
            <text>They're escaping.</text>
        </escape>
    </ship>
    <event name="TEST_ACCEPT_SURRENDER">
        <text>Accepted.</text>
        <ship hostile="false"/>
    </event>
    <event name="TEST_FIGHT">
        <text>A fight.</text>
        <ship load="TEST_ENEMY" hostile="true"/>
    </event>
    <ship name="TEST_BIG_ENEMY" auto_blueprint="TEST_BIG_ENEMY_SHIP"/>
    <ship name="TEST_BATTLEGROUP" auto_blueprint="TEST_ENEMY_SHIP" xftlBigBlueprint="TEST_BIG_SHIPS"/>
    <ship name="TEST_EMPTY_BATTLEGROUP" auto_blueprint="TEST_ENEMY_SHIP" xftlBigBlueprint="TEST_NO_BIG_SHIPS"/>
</FTL>
"""
    }
}
