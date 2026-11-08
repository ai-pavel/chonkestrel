package xyz.znix.xftl.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Ship
import xyz.znix.xftl.ShipShape
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.testkit.InGameTest

/**
 * The size 3 bosses' mechanics (see ColossalBoss and doc/game/bosses.md),
 * on a small test boss: it's built as its blueprint says, always fights
 * alone, holds the exit, mends while the player's away, and its linked
 * crew fall apart.
 */
class ColossalBossTest {
    private fun createKit(): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(WingmanTest.BLUEPRINT_XML, BLUEPRINT_XML),
            extraEvents = listOf(WingmanTest.EVENT_XML, EVENT_XML),
            playerShip = "PLAYER_SHIP_THREE_ENEMIES"
        )
        kit.state.loadEventShip(kit.state.eventManager["TEST_BOSS_FIGHT"].resolve())
        kit.state.updateGameState(0.01f)
        return kit
    }

    private val InGameTest.boss: Ship get() = state.enemy!!

    @Test
    fun testItsBuiltAsItsBlueprintSays() {
        val kit = createKit()
        val boss = kit.boss
        val colossal = assertNotNull(boss.colossal).let { boss.colossal!! }

        assertEquals(ColossalBoss.Kind.VIGILANCE, colossal.kind)
        assertEquals(ShipShape.COLOSSAL, boss.type.shape)
        // Its hull is its blueprint's times its size, and it has the most
        // crew its blueprint allows
        assertEquals(3 * 20, boss.maxHealth)
        assertEquals(6, boss.crew.size)
        // Its weapons are the ones its blueprint lists
        assertEquals(listOf("TEST_LASER", "TEST_BEAM"), boss.hardpoints.mapNotNull { it.weapon?.type?.name })
        // It never gives up
        assertEquals(0, boss.surrenderHealth)
        assertEquals(0, boss.escapeHealth)
    }

    @Test
    fun testItsDronesAreTwinsAndTriples() {
        val kit = createKit()
        val drones = kit.boss.droneSystems.single()

        // The Vigilance's a tier II boss: one triple, and the rest twins,
        // with the power and drone parts for them
        val sizes = drones.drones.mapNotNull { it?.type?.combinedCount }
        assertEquals(listOf(3, 2), sizes)
        val power = drones.drones.sumOf { it?.type?.power ?: 0 }
        assertTrue("${drones.energyLevels} < $power", drones.energyLevels >= power)
        // (enough for each copy twice, counting those it's launched already)
        val launched = drones.drones.sumOf { it?.liveCopies?.size ?: 0 }
        assertTrue("${kit.boss.dronesCount} + $launched", kit.boss.dronesCount + launched >= 2 * 5)
    }

    @Test
    fun testItAlwaysFightsAlone() {
        val kit = createKit()
        val boss = kit.boss
        // Even badly hurt, and once the fleet's overtaken its beacon, it
        // has nobody beside it
        boss.health = boss.maxHealth / 4
        repeat(5) { kit.state.updateGameState(1f) }
        assertTrue(kit.state.wingmen.isEmpty())
    }

    @Test
    fun testItHoldsTheExit() {
        val kit = createKit()
        val beacon = kit.state.currentBeacon
        assertTrue(kit.state.isHeldByBoss(beacon))

        kit.state.debugBeatEnemy()
        assertFalse(kit.state.isHeldByBoss(beacon))
    }

    @Test
    fun testItMendsWhileThePlayersAway() {
        val kit = createKit()
        val boss = kit.boss
        boss.health = 10
        val shields = boss.shieldSystems.first()
        shields.dealDamage(1, 0)
        assertTrue(shields.damaged)

        boss.colossal!!.repairWhileAway()
        // A tenth of its hull, and all its systems
        assertEquals(10 + 6, boss.health)
        assertFalse(shields.damaged)
    }

    @Test
    fun testItsLinkBreaksWhenAThirdOfItsCrewDie() {
        val kit = createKit()
        val boss = kit.boss
        val colossal = boss.colossal!!
        assertTrue(colossal.isLinked)

        // Linked crew fight and repair as if fully skilled
        val crew = boss.crew.filterIsInstance<LivingCrew>()
        assertEquals(1f + LivingCrew.COMBAT_SKILL_BONUS.last() / 100f, crew.first().attackDamageMult, 0.001f)

        for (dead in crew.take(2))
            boss.crew.remove(dead)
        kit.state.updateGameState(0.01f)

        assertFalse(colossal.isLinked)
        val rest = boss.crew.filterIsInstance<LivingCrew>()
        assertTrue(rest.all { it.stunTime > 0f })
        assertEquals(1f, rest.first().attackDamageMult, 0.001f)
    }

    @Test
    fun testTheAmbushIonisesItsShields() {
        val kit = createKit()
        val boss = kit.boss
        boss.colossal!!.opening("ambush")
        assertTrue(boss.shieldSystems.all { it.isIonised })
    }

    @Test
    fun testItsStateIsSaved() {
        val kit = createKit()
        val boss = kit.boss
        val crew = boss.crew.filterIsInstance<LivingCrew>()
        for (dead in crew.take(2))
            boss.crew.remove(dead)
        kit.state.updateGameState(0.01f)
        assertFalse(boss.colossal!!.isLinked)

        val elem = org.jdom2.Element("colossalBoss")
        boss.colossal!!.saveToXML(elem)
        val copy = ColossalBoss(ColossalBoss.Kind.VIGILANCE, boss)
        copy.loadFromXML(elem)
        assertFalse(copy.isLinked)
    }

    @Test
    fun testTheOssuarysSlipwayRebuildsItsDrones() {
        val kit = createKit()
        val ossuary = ColossalBoss(ColossalBoss.Kind.OSSUARY, kit.boss)
        // While its first drones system works, it never runs short of
        // drone parts
        kit.boss.dronesCount = 0
        ossuary.update(0.1f)
        assertEquals(ColossalBoss.SLIPWAY_DRONE_PARTS, kit.boss.dronesCount)

        // Once it's broken, it stops
        val slipway = kit.boss.droneSystems.first()
        slipway.dealDamage(slipway.energyLevels, 0)
        kit.boss.dronesCount = 0
        ossuary.update(0.1f)
        assertEquals(0, kit.boss.dronesCount)
    }

    private fun createSwarmKit(): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(WingmanTest.BLUEPRINT_XML, BLUEPRINT_XML),
            extraEvents = listOf(WingmanTest.EVENT_XML, EVENT_XML),
            playerShip = "PLAYER_SHIP_THREE_ENEMIES"
        )
        kit.state.loadEventShip(kit.state.eventManager["TEST_SWARM_FIGHT"].resolve())
        return kit
    }

    // Its crew on their way to the player's ship, or already there
    private fun InGameTest.boarding(): Int =
        boss.crew.count { it.isTeleporting } +
                player.crew.count { (it as? LivingCrew)?.ownerShip == boss }

    @Test
    fun testItsBoardersAreReadyAndGoAtOnce() {
        val kit = createSwarmKit()
        val boss = kit.boss
        // Its first boarding party is waiting in its teleporter...
        val pad = boss.teleporter!!.room!!
        assertEquals(minOf(pad.cellCount, boss.crew.size / 2), pad.crew.size)

        // ...and goes as soon as the fight starts
        repeat(4) { kit.state.updateGameState(0.25f) }
        assertTrue("${kit.boarding()} boarding", kit.boarding() >= pad.cellCount.coerceAtMost(8))
    }

    @Test
    fun testTheStormbreakersSwarmBoardsThePlayer() {
        val kit = createSwarmKit()
        val boss = kit.boss
        assertEquals(2, boss.teleporterSystems.size)

        // Its second teleporter sends a wave of its crew over as the fight
        // starts, and every so often after, keeping enough aboard to crew it
        val padCrew = boss.teleporter!!.room!!.crew.size
        repeat(4) { kit.state.updateGameState(0.25f) }
        assertTrue("${kit.boarding()} boarding", kit.boarding() > padCrew)
        assertTrue(boss.crew.size >= ColossalBoss.SWARM_KEEP)
    }

    companion object {
        const val BLUEPRINT_XML = """
<FTL>
    <systemBlueprint name="shields">
        <type>shields</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <shipBlueprint name="TEST_BOSS_SHIP" layout="test_ship" img="test_ship">
        <shipSize>3</shipSize>
        <xftlBoss>VIGILANCE</xftlBoss>
        <health amount="20"/>
        <maxPower amount="20"/>
        <maxSystems>10</maxSystems>
        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <shields power="2" max="8" room="2"/>
            <weapons power="4" max="8" room="3"/>
            <drones power="2" max="8" room="4"/>
        </systemList>
        <weaponList count="2" missiles="0">
            <weapon name="TEST_LASER"/>
            <weapon name="TEST_BEAM"/>
        </weaponList>
        <droneList count="2" drones="2">
            <drone name="TEST_BOSS_DRONE"/>
            <drone name="TEST_BOSS_DRONE"/>
        </droneList>
        <crewCount amount="6" max="6" class="human"/>
    </shipBlueprint>

    <droneBlueprint name="TEST_BOSS_DRONE">
        <type>SHIELD</type>
        <power>1</power>
        <cooldown>15000</cooldown>
        <speed>5</speed>
        <droneImage>drone_shield</droneImage>
    </droneBlueprint>

    <systemBlueprint name="teleporter">
        <type>teleporter</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <shipBlueprint name="TEST_SWARM_SHIP" layout="test_ship" img="test_ship">
        <shipSize>3</shipSize>
        <xftlBoss>STORMBREAKER</xftlBoss>
        <health amount="20"/>
        <maxPower amount="20"/>
        <maxSystems>10</maxSystems>
        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <teleporter power="3" max="3" room="4"/>
            <teleporter power="3" max="3" room="5"/>
        </systemList>
        <crewCount amount="16" max="16" class="human"/>
    </shipBlueprint>
</FTL>
"""

        const val EVENT_XML = """
<FTL>
    <ship name="TEST_BOSS" blueprint="TEST_BOSS_SHIP"/>
    <ship name="TEST_SWARM" blueprint="TEST_SWARM_SHIP"/>
    <event name="TEST_SWARM_FIGHT">
        <text>A swarm.</text>
        <ship load="TEST_SWARM" hostile="true"/>
    </event>
    <event name="TEST_BOSS_FIGHT">
        <text>A boss.</text>
        <ship load="TEST_BOSS" hostile="true"/>
    </event>
</FTL>
"""
    }
}
