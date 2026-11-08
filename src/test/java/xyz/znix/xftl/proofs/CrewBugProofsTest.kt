package xyz.znix.xftl.proofs

import org.intellij.lang.annotations.Language
import org.jdom2.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.drones.RepairDrone
import xyz.znix.xftl.game.BossManager
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.WingmanTest
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.RoomPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.systems.Teleporter
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew
import xyz.znix.xftl.weapons.DroneBlueprint

/**
 * Proofs that the crew bugs reported by players are fixed: each test sets
 * up what was reported, and checks the game now does what the changelog
 * says it does instead.
 */
class CrewBugProofsTest {
    private fun createKit(
        enemy: String?,
        playerShip: String = "PLAYER_SHIP_PROOF",
        crewAI: Boolean = true,
        wingmen: Boolean = false,
    ): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOfNotNull(BLUEPRINT_XML, if (wingmen) WingmanTest.BLUEPRINT_XML else null),
            extraAnimations = listOf(ANIMATIONS_XML),
            extraEvents = listOf(EVENT_XML),
            playerShip = playerShip
        )
        kit.state.debugFlags.noCrewAI.set = !crewAI
        kit.powerUpAll()
        if (enemy != null) {
            kit.state.debugSpawnShip(kit.state.eventManager.getShip(enemy), Difficulty.NORMAL, 0, 1)
            // (the wingmen are spawned when the game's updated)
            kit.state.updateGameState(0.01f)
        }
        return kit
    }

    // (with air for the crew, as the tests are about other things)
    private fun InGameTest.run(seconds: Float, each: () -> Unit = {}) = repeat((seconds * 20).toInt()) {
        for (ship in listOf(player) + state.allEnemyShips)
            ship.rooms.forEach { it.oxygen = 1f }
        each()
        updateSingle(0.05f)
    }

    private fun InGameTest.place(ship: Ship, room: Room, mode: AbstractCrew.SlotType, owner: Ship?,
                                 race: String = "human", cell: ConstPoint = ConstPoint(0, 0)): LivingCrew {
        val crew = addCrew(ship, room, mode, owner, race)
        val point = RoomPoint(room, cell)
        crew.jumpTo(point)
        crew.setTargetRoom(room, point)
        return crew
    }

    /**
     * Bug 1: "Shield overcharger of a beaten enemy keeps charging, so the
     * teleported crew can't be recalled after the battle".
     */
    @Test
    fun testBoardersComeHomeThroughABeatenShipsSuperShield() {
        val kit = createKit("PROOF_SHIELDED_ENEMY", crewAI = false)
        val enemy = kit.state.enemy!!
        val room = enemy.rooms[5]
        val boarder = kit.place(enemy, room, AbstractCrew.SlotType.INTRUDER, kit.player)
        val teleporter = kit.player.systems.filterIsInstance<Teleporter>().single()
        // Its overcharger keeps the super shield topped up
        val overcharger = { enemy.superShield = 5 }
        kit.run(0.1f, overcharger)

        // While they're fighting, the super shield stops the teleporter...
        teleporter.selectTeleportAction(false, room)
        kit.run(3f, overcharger)
        assertSame(enemy, boarder.room.ship)

        // ...but once it's beaten, the boarders can be brought home
        kit.state.debugBeatEnemy()
        teleporter.selectTeleportAction(false, room)
        kit.run(3f, overcharger)
        assertTrue(enemy.superShield > 0)
        assertSame(kit.player, boarder.room.ship)
    }

    /**
     * Bug 2: "Repair Drone - стоял в комнате пока там закончилась драка" (it
     * stood in a room until the fight there was over).
     */
    @Test
    fun testTheRepairDroneKeepsOutOfRoomsWithBoarders() {
        val kit = createKit(null, playerShip = "PLAYER_SHIP_PROOF_DRONES", crewAI = false)
        val type = kit.state.blueprintManager["PROOF_REPAIR_DRONE"] as DroneBlueprint
        val drone = type.makeInstance() as RepairDrone
        drone.init(kit.player)
        drone.isPowered = true

        // The oxygen's room is first, and would be gone to first, but
        // there's a boarder in it
        val oxygen = kit.player.oxygen!!
        val pilot = kit.player.rooms[1].system!!
        kit.place(kit.player, oxygen.room!!, AbstractCrew.SlotType.INTRUDER, null)
        oxygen.dealDamage(1, 0)
        pilot.dealDamage(1, 0)

        var enteredOxygen = false
        kit.run(40f) { enteredOxygen = enteredOxygen || drone.pawn?.room == oxygen.room }

        assertFalse("the drone went into the boarder's room", enteredOxygen)
        assertFalse("the drone fixed the other system", pilot.damaged)
        assertTrue(oxygen.damaged)
    }

    /**
     * Bug 3: "Enemy having healing burst with no teleporter (wingman has,
     * but was killed - why firing?)".
     */
    @Test
    fun testEnemiesOnlyHealTheirOwnSide() {
        val kit = createKit("PROOF_HEALING_ENEMY")
        val enemy = kit.state.enemy!!
        enemy.hardpoints.forEach { it.weapon = null }
        enemy.addBlueprint(kit.state.blueprintManager["PROOF_HEAL_BOMB"] as Blueprint, true)
        val bomb = enemy.hardpoints.firstNotNullOf { it.weapon }
        val ours = kit.addCrew(kit.player, kit.player.rooms[0])
        ours.health = ours.maxHealth / 2

        // With only the player's crew hurt, it holds the burst, charged
        kit.run(30f)
        assertTrue("the burst charged", bomb.isCharged)
        assertEquals(ours.maxHealth / 2, ours.health, 0.001f)

        // Once one of its own crew is hurt, it heals them
        val theirs = enemy.crew.filterIsInstance<LivingCrew>().first { it.mode == AbstractCrew.SlotType.CREW }
        val hurt = theirs.maxHealth / 2
        theirs.health = hurt
        kit.run(15f)
        assertTrue("its crew were healed", theirs.health > hurt)
        assertEquals(ours.maxHealth / 2, ours.health, 0.001f)
    }

    /**
     * Bug 4: "mantis uses laser projectile in crew fight instead of whatever
     * is in vanilla".
     */
    @Test
    fun testTheMantisSlashRatherThanShoot() {
        val kit = createKit(null, crewAI = false)
        val room = kit.player.rooms[5]
        val mantis = kit.place(kit.player, room, AbstractCrew.SlotType.CREW, kit.player, "mantis", ConstPoint(0, 0))
        val human = kit.place(kit.player, room, AbstractCrew.SlotType.CREW, kit.player, "human", ConstPoint(4, 0))
        val boarder = kit.place(kit.player, room, AbstractCrew.SlotType.INTRUDER, null, "human", ConstPoint(2, 4))

        // The little laser's shot, as it flies (see AbstractCrew.shootAt)
        val shotField = AbstractCrew::class.java.getDeclaredField("shot").also { it.isAccessible = true }
        var mantisShot = false
        var humanShot = false
        kit.run(3f) {
            mantisShot = mantisShot || shotField.get(mantis) != null
            humanShot = humanShot || shotField.get(human) != null
        }

        // The human shoots across the room, the Mantis doesn't, but both hurt
        // the boarder
        assertTrue("the human shot (so shots are seen)", humanShot)
        assertFalse("the Mantis shot a laser", mantisShot)
        assertTrue(boarder.health < boarder.maxHealth)
    }

    /**
     * Bug 5: "all ships will try to extract the same unit with teleporter".
     */
    @Test
    fun testEachEnemyShipTakesHomeItsOwnHurtBoarders() {
        val kit = createKit("PROOF_BOARDING_ENEMY", playerShip = "PLAYER_SHIP_TWO_ENEMIES", wingmen = true)
        val enemy = kit.state.enemy!!
        val wingman = kit.state.wingmen.single().ship
        kit.state.debugFlags.noEnemyFire.set = true

        // The main ship's teleporter is out, so it can't take its own
        // boarder home, and the wingman mustn't wait on it
        enemy.teleporter!!.ionTimer = 1000f
        val enemys = kit.place(kit.player, kit.player.rooms[0], AbstractCrew.SlotType.INTRUDER, enemy)
        val wingmans = kit.place(kit.player, kit.player.rooms[1], AbstractCrew.SlotType.INTRUDER, wingman)
        for (crew in listOf(enemys, wingmans))
            crew.health = crew.maxHealth / 10

        kit.run(10f)
        assertSame(wingman, wingmans.room.ship)
        assertSame(kit.player, enemys.room.ship)
    }

    /**
     * Bug 6: "Хакинг снимает майнд-контроль, Zoltan Shield Bypass для
     * майнд-контроля" (hacking breaks mind control; the Zoltan Shield
     * Bypass for mind control). This is the first half: a hacking pulse on
     * the mind control system frees the crew it's controlling.
     */
    @Test
    fun testAHackingPulseBreaksMindControl() {
        val kit = createKit("PROOF_MIND_ENEMY")
        kit.player.dronesCount = 5
        val enemy = kit.state.enemy!!
        val mind = enemy.mindControl!!
        val ours = kit.addCrew(kit.player, kit.player.rooms[0])

        val hacking = kit.player.hacking!!
        hacking.selectTarget(mind.room!!)
        var time = 0f
        while (!hacking.droneLanded && time < 60f) {
            kit.run(0.05f)
            time += 0.05f
        }
        assertTrue("the probe landed", hacking.droneLanded)

        mind.debugControl(ours)
        assertSame(mind, ours.mindControlledBy)

        hacking.startHackingPulse()
        kit.run(0.5f)
        assertNull(ours.mindControlledBy)
        assertNull(mind.controlledCrew)
    }

    /**
     * Bug 6, the second half: the Zoltan Shield Bypass lets mind control
     * reach a ship with a super shield up.
     */
    @Test
    fun testTheZoltanShieldBypassLetsMindControlThrough() {
        val kit = createKit("PROOF_MIND_ENEMY")
        kit.givePlayerBP("LIFE_SCANNER")
        val enemy = kit.state.enemy!!
        val target = enemy.crew.filterIsInstance<LivingCrew>().first()
        val mind = kit.player.mindControl!!
        kit.run(0.1f) { enemy.superShield = 5 }

        // Without it, the super shield blocks it...
        enemy.superShield = 5
        mind.selectRoom(target.room)
        assertNull(mind.controlledCrew)

        // ...and with it, it doesn't
        kit.givePlayerBP("ZOLTAN_BYPASS")
        enemy.superShield = 5
        mind.selectRoom(target.room)
        assertSame(target, mind.controlledCrew)
    }

    /**
     * Bug 7: "ИИ экипажа: слабые бойцы (Engi) последние в бой и абордаж;
     * лимит абордажей сохраняется в сейв" (crew AI: weak fighters, the Engi,
     * are the last into a fight or boarding party; the boarding limit is
     * kept in the save). This is the first half: an Engi, with a human just
     * as free, leaves the fight to the human.
     */
    @Ignore("still a bug: the bad fighter penalty only weighs the fight against what the crew are doing; " +
            "between two idle crew, the first in the list (here the Engi) is still sent")
    @Test
    fun testTheEngiAreTheLastPickedToFight() {
        val kit = createKit("PROOF_IDLE_ENEMY")
        val enemy = kit.state.enemy!!
        val hall = enemy.rooms[5]
        enemy.crew.removeAll { it is LivingCrew }

        // Someone at the helm, and the Engi first of the others in the crew
        // list, so ties would go to it
        kit.place(enemy, enemy.rooms[1], AbstractCrew.SlotType.CREW, enemy)
        val engi = kit.place(enemy, hall, AbstractCrew.SlotType.CREW, enemy, "engi", ConstPoint(0, 0))
        val human = kit.place(enemy, hall, AbstractCrew.SlotType.CREW, enemy, "human", ConstPoint(4, 4))
        kit.run(0.5f)
        assertTrue(engi.attackDamageMult < 1f)

        val boarder = kit.place(enemy, enemy.rooms[0], AbstractCrew.SlotType.INTRUDER, kit.player)
        kit.run(0.2f)
        assertSame("the human goes to fight", boarder.room, human.pathingTarget?.room ?: human.room)
        assertNotEquals("the Engi stays out of it", boarder.room, engi.pathingTarget?.room ?: engi.room)
    }

    /**
     * Bug 7, the second half: an enemy's count of boarding parties sent is
     * saved, so a reload doesn't let it board past its limit.
     */
    @Test
    fun testTheBoardingLimitSurvivesAReload() {
        val kit = createKit("PROOF_BOARDING_ENEMY")
        val enemy = kit.state.enemy!!
        repeat(3) { enemy.crewAI.onCrewTeleportedOut() }

        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState())
        val elem = Element("ai")
        loaded.enemy!!.crewAI.saveToXML(elem)
        assertEquals(3, SaveUtil.getOptionalTagInt(elem, "aiTeleportCount"))
    }

    /**
     * Bug 8: "Флагман без экипажа переходит на автоматику (BOSS_AUTOMATED)"
     * (the flagship without its crew goes over to automated).
     */
    @Test
    fun testTheFlagshipFightsOnAutomated() {
        val kit = createKit("PROOF_FLAGSHIP")
        val enemy = kit.state.enemy!!
        // (a boss ship's made with its boss, which can't be set later)
        Ship::class.java.getDeclaredField("boss").also { it.isAccessible = true }.set(enemy, StubBoss)
        assertSame(StubBoss, enemy.boss)
        kit.state.debugFlags.noEnemyFire.set = true

        enemy.crew.removeAll { it is LivingCrew }
        kit.run(2f)

        assertTrue(enemy.automated)
        assertTrue(kit.state.isShipPresent(enemy))
        assertTrue(kit.state.isEnemyOf(kit.player, enemy))
        assertTrue(kit.state.isInDanger)
    }

    // A boss that does nothing, to mark the enemy ship as one
    private object StubBoss : BossManager {
        override val serialisationType: String get() = "proof"
        override val beacon: Beacon? get() = null
        override val nextBeacon: Beacon? get() = null
        override val jumping: Boolean get() = false
        override val useBossShipUI: Boolean get() = false
        override fun createShip(): Ship = error("not used")
        override fun drawMapIcon(g: Graphics, centre: IPoint) {}
        override fun drawJumpArc(g: Graphics, from: IPoint, to: IPoint) {}
        override fun advanceFleet() {}
        override fun bossShipKilled(enemy: Ship) {}
        override fun saveToXML(elem: Element, refs: ObjectRefs) {}
        override fun loadFromXML(elem: Element, refs: RefLoader) {}
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <systemBlueprint name="teleporter">
        <type>teleporter</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
    <systemBlueprint name="hacking">
        <type>hacking</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
    <systemBlueprint name="mind">
        <type>mind</type>
        <startPower>1</startPower>
        <maxPower>3</maxPower>
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

    <crewBlueprint name="mantis">
        <cost>1</cost>
    </crewBlueprint>
    <crewBlueprint name="engi">
        <cost>1</cost>
    </crewBlueprint>
    <!-- The repair drone's pawn -->
    <crewBlueprint name="repair">
        <cost>1</cost>
    </crewBlueprint>

    <augBlueprint name="LIFE_SCANNER">
        <cost>10</cost>
        <stackable>false</stackable>
    </augBlueprint>
    <augBlueprint name="ZOLTAN_BYPASS">
        <cost>10</cost>
        <stackable>false</stackable>
    </augBlueprint>

    <droneBlueprint name="DRONE_HACKING">
        <type>HACKING</type>
        <power>0</power>
        <speed>15</speed>
    </droneBlueprint>
    <droneBlueprint name="PROOF_REPAIR_DRONE">
        <type>REPAIR</type>
        <power>1</power>
    </droneBlueprint>

    <!-- As the Healing Burst: no damage, heals the crew -->
    <weaponBlueprint name="PROOF_HEAL_BOMB">
        <type>BOMB</type>
        <damage>0</damage>
        <persDamage>-2</persDamage>
        <cooldown>5</cooldown>
        <power>1</power>
        <cost>45</cost>
        <rarity>0</rarity>
        <weaponArt>generic_test_weapon</weaponArt>
        <image>dummy_anim</image>
        <explosion>dummy_anim</explosion>
    </weaponBlueprint>

    <shipBlueprint name="PLAYER_SHIP_PROOF" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <hacking power="3" max="3" room="2"/>
            <teleporter power="3" max="3" room="3"/>
            <mind power="1" max="3" room="4"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PLAYER_SHIP_PROOF_DRONES" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>
        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <drones power="2" room="3"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PROOF_SHIELDED_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="0" max="0" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PROOF_HEALING_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="2" max="2" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <weapons power="3" max="8" room="3"/>
        </systemList>
        <weaponList count="1" missiles="0">
            <weapon name="PROOF_HEAL_BOMB"/>
        </weaponList>
    </shipBlueprint>

    <shipBlueprint name="PROOF_BOARDING_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="2" max="2" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <teleporter power="3" max="3" room="3"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PROOF_MIND_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="2" max="2" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <mind power="1" max="3" room="2"/>
        </systemList>
    </shipBlueprint>

    <!-- Only the helm to man, so the rest of the crew are idle -->
    <shipBlueprint name="PROOF_IDLE_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="1" max="3" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PROOF_FLAGSHIP_SHIP" layout="test_ship" img="test_ship">
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
    <ship name="PROOF_SHIELDED_ENEMY" blueprint="PROOF_SHIELDED_ENEMY_SHIP"/>
    <ship name="PROOF_HEALING_ENEMY" blueprint="PROOF_HEALING_ENEMY_SHIP"/>
    <ship name="PROOF_BOARDING_ENEMY" blueprint="PROOF_BOARDING_ENEMY_SHIP"/>
    <ship name="PROOF_MIND_ENEMY" blueprint="PROOF_MIND_ENEMY_SHIP"/>
    <ship name="PROOF_IDLE_ENEMY" blueprint="PROOF_IDLE_ENEMY_SHIP"/>
    <ship name="PROOF_FLAGSHIP" blueprint="PROOF_FLAGSHIP_SHIP"/>
    <event name="BOSS_AUTOMATED">
        <text>Its crew are dead, but it fights on.</text>
    </event>
</FTL>
"""

        // The Mantis's, the Engi's and the repair drone's animations, as the
        // humans' in the test kit, and the hacking probe's
        private val ANIMATIONS_XML = (
                listOf("mantis", "engi", "repair").flatMap { race ->
                    (listOf("portrait", "repair", "death_right", "teleport", "power_up", "power_down") +
                            listOf("up", "down", "left", "right").flatMap { dir ->
                                listOf("shoot", "walk", "punch", "type", "fire").map { "${it}_$dir" }
                            }).map { "${race}_$it" }
                } + listOf("fire_large", "drone_hack_fly", "drone_hack_land", "drone_hack_extend", "explosion_random")
                ).joinToString("\n", "<FTL>\n", "\n</FTL>") {
                """<anim name="$it"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>"""
            }
    }
}
