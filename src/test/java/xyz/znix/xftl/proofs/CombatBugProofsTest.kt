package xyz.znix.xftl.proofs

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Ship
import xyz.znix.xftl.drones.ShieldDrone
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.FTLSound
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.LoopHandle
import xyz.znix.xftl.game.MusicSpec
import xyz.znix.xftl.game.SoundInstance
import xyz.znix.xftl.game.SoundManager
import xyz.znix.xftl.game.SoundSpec
import xyz.znix.xftl.game.WingmanTest
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.systems.SelectedTarget
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.AbstractProjectile
import xyz.znix.xftl.weapons.AbstractWeaponProjectile
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.LaserBlueprint
import xyz.znix.xftl.weapons.MissileBlueprint
import xyz.znix.xftl.weapons.WeaponSizes
import kotlin.math.PI
import kotlin.random.Random

/**
 * Proofs for bugs reported in combat (see the changelog's fixes): one test per bug,
 * named after the behaviour that fixed it, with the report above it.
 *
 * Some of the reports can't be checked here: the shields critical warning
 * (and its sound) is worked out while the top bar is drawn
 * (PlayerShipUI.drawTopBar), and the looped sounds fade in
 * RealSoundManager, which needs OpenAL; neither runs without the game's
 * window. They're left out, rather than tested through a copy of their
 * logic, which would prove nothing.
 */
class CombatBugProofsTest {
    /**
     * "missiles do not give visual feedback when piercing shields"
     *
     * A shot passing through the shields makes them flash, as does one they
     * stop, so a missile getting through can be seen.
     */
    @Test
    fun testShieldsFlashForShotsThroughThemAndShotsTheyStop() {
        val kit = shieldsKit()
        val player = kit.player
        assertEquals(0f, player.flashTimeLeft, 0f)

        // A missile goes through: the shields flash, but aren't touched
        val missile = (kit.state.blueprintManager["PROOF_MISSILE"] as MissileBlueprint)
            .MissileProjectile(player.rooms[0])
        missile.crossShields()
        assertFalse(missile.hasDied)
        assertTrue("No flash for a missile going through", player.flashTimeLeft > 0f)

        // Once it's faded...
        repeat(10) { kit.updateSingle(0.1f) }
        assertEquals(0f, player.flashTimeLeft, 0f)

        // ...a laser the shields stop flashes them too
        val laser = (kit.state.blueprintManager["PROOF_LASER"] as LaserBlueprint)
            .LaserProjectile(player.rooms[0])
        laser.crossShields()
        assertTrue(laser.hasDied)
        assertTrue("No flash for a laser stopped", player.flashTimeLeft > 0f)
    }

    /**
     * "missiles evently hit shield barriers (pierce too low)"
     *
     * Missiles go through any amount of shields, as on a ship with two
     * shields systems: their piercing of 5 used to only beat five layers.
     */
    @Test
    fun testMissilesGoThroughEveryShieldLayer() {
        val kit = shieldsKit()
        val player = kit.player

        // Two shields systems, with six layers between them: more than a
        // missile's piercing of 5
        assertEquals(2, player.shieldSystems.size)
        assertEquals(6, player.activeShieldLayers)

        val missile = (kit.state.blueprintManager["PROOF_MISSILE"] as MissileBlueprint)
            .MissileProjectile(player.rooms[0])
        assertEquals(5, missile.type.shieldPiercing)
        missile.crossShields()

        // It's still flying, every layer's still up...
        assertFalse("The missile was stopped by the shields", missile.hasDied)
        assertEquals(6, player.activeShieldLayers)

        // ...and it hits the hull
        val hull = player.health
        missile.reachTarget()
        assertEquals(hull - 3, player.health)
    }

    /**
     * "x3 halberd beam sometimes changes sizes [smaller range with one ship,
     * or if the other ship is cloaked]"
     *
     * Double and triple beams are always their full length (see
     * [WeaponSizes.beamLength]), however many enemy ships there are and
     * whether they're cloaked. The length used to depend on the number of
     * other ships to reach, so with one ship (or the other cloaked) a
     * triple beam was only three times the beam long, rather than reaching
     * on as far as two more windows.
     *
     * The player's aiming (a click in PlayerShipUI) can't be run here, but
     * it takes its length from [WeaponSizes.beamLength], which no longer
     * knows about the other ships. This checks that length, and that firing
     * a beam that long keeps it, alone or with a cloaked wingman.
     */
    @Test
    fun testCombinedBeamsAreAlwaysFullLength() {
        // As long again as the beam, or the distance to the next window,
        // for each extra copy: the same however many ships there are
        val base = 60
        assertEquals(base + 2 * 400, WeaponSizes.beamLength(base, 3))
        assertEquals(base + 400, WeaponSizes.beamLength(base, 2))

        // Against one ship, the beam's fired its whole length, and all its
        // copies stand for it in that ship
        val alone = beamKit("TEST_SHIP", "PROOF_ENEMY")
        val enemy = alone.state.enemy!!
        val (beam, aim) = fireTripleBeam(alone, enemy)
        assertEquals(WeaponSizes.beamLength(base, 3), aim.length)
        assertEquals(WeaponSizes.beamLength(base, 3), beam.firedAim!!.length)
        assertEquals(3, beam.copiesInTarget)
        assertEquals(listOf(beam), enemy.inboundBeams)

        // Against a ship whose wingman's cloaked, the same: no copy goes into
        // the cloaked ship, and the beam's no shorter for it
        val cloaked = beamKit("PLAYER_SHIP_TWO_ENEMIES", "PROOF_CLOAKING_ENEMY")
        val main = cloaked.state.enemy!!
        val wingman = cloaked.state.wingmen.single().ship
        wingman.cloaking!!.activateCloak()
        cloaked.state.updateGameState(0.05f)
        assertTrue("The wingman didn't cloak", wingman.isCloakActive)

        val (cloakedBeam, cloakedAim) = fireTripleBeam(cloaked, main)
        assertEquals(WeaponSizes.beamLength(base, 3), cloakedAim.length)
        assertEquals(WeaponSizes.beamLength(base, 3), cloakedBeam.firedAim!!.length)
        assertEquals(3, cloakedBeam.copiesInTarget)
        assertTrue(wingman.inboundBeams.isEmpty())
    }

    /**
     * "overcharger drone is always glowing when on"
     *
     * The Shield Overcharger only glows while it's charging a point, not
     * while the shield's full.
     *
     * The glow's drawn in ShieldDrone.onRender, which needs OpenGL, so this
     * checks what it's drawn from: the glow's skipped while the drone's
     * row is full (isFull), and its brightness is the charge so far, which
     * stays at its brightest (fully charged) while it waits on a full
     * shield. Before, only that brightness was looked at, so a waiting
     * drone glowed all the time.
     */
    @Test
    fun testOverchargerOnlyGlowsWhileCharging() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_PROOF_DRONES")
        val player = kit.player
        player.superShield = 0
        val type = kit.state.blueprintManager["PROOF_SHIELD_DRONE"] as DroneBlueprint
        val drone = type.makeInstance() as ShieldDrone
        drone.init(player)
        drone.isPowered = true

        // Charging its first point: it glows
        repeat(50) { drone.update(0.1f) }
        assertFalse(drone.isRowFull)
        assertTrue(drone.debugCooldown > 0f)

        // With its row of five full, it's charged and waiting, at what would
        // be its brightest, but the shield's full, so there's no glow
        repeat(2000) { drone.update(0.1f) }
        assertEquals(5, player.superShield)
        assertEquals(0f, drone.debugCooldown, 0f)
        assertTrue("A full overcharger still glows", drone.isRowFull)

        // A point knocked off is put back at once, and it charges (and
        // glows) again for the next
        player.superShield--
        drone.update(0.1f)
        assertEquals(5, player.superShield)
        player.superShield--
        assertFalse(drone.isRowFull)
    }

    /**
     * "hacking engines or pilot does not revive dodge chance"
     *
     * A hacking pulse on the engines or the piloting takes all the ship's
     * evasion, on ships with several engines systems too.
     */
    @Test
    fun testHackingPulseOnEnginesTakesAllEvasion() {
        // The second of two engines systems: the first's still running
        val kit = hackKit()
        val enemy = kit.state.enemy!!
        assertEquals(2, enemy.engineSystems.size)
        hackPulse(kit, enemy.engineSystems[1].room!!)
        assertTrue(enemy.engineSystems[1].isHackActive)
        assertFalse(enemy.engineSystems[0].isHackActive)
        assertEquals(0, enemy.evasion)
    }

    /** See [testHackingPulseOnEnginesTakesAllEvasion]. */
    @Test
    fun testHackingPulseOnPilotingTakesAllEvasion() {
        val kit = hackKit()
        val enemy = kit.state.enemy!!
        hackPulse(kit, enemy.piloting!!.room!!)
        assertTrue(enemy.piloting!!.isHackActive)
        assertEquals(0, enemy.evasion)
    }

    /**
     * "2x beam did not do multi ship if there was 3 ships total"
     *
     * A twin beam against three ships hits the one it's swept through: its
     * copy went to the leftmost of the other ships, even when the beam went
     * through the middle one.
     */
    @Test
    fun testTwinBeamCopyGoesIntoTheShipItsSweptThrough() {
        val kit = InGameTest(
            extraBlueprints = listOf(WingmanTest.BLUEPRINT_XML, BLUEPRINT_XML),
            extraEvents = listOf(WingmanTest.EVENT_XML, EVENT_XML),
            extraAnimations = listOf(ANIMATIONS_XML),
            playerShip = "PLAYER_SHIP_THREE_ENEMIES"
        )
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_ENEMY"), Difficulty.NORMAL, 0, 1)
        kit.state.updateGameState(0.01f)

        val (left, middle, right) = kit.state.hostileShipsLeftToRight
        assertSame(kit.state.enemy, right)

        // Put the windows side by side, as they're drawn (nothing's drawn in
        // the tests, so they're all at the same place otherwise)
        for ((i, ship) in listOf(left, middle, right).withIndex()) {
            (kit.state.getEnemyPosition(ship) as Point).set(ConstPoint(i * 400, 0))
        }

        // Aim at the right-hand ship's top-left room, sweeping left: the
        // line goes on through the middle ship's top row, but stops short
        // of the left-hand one
        kit.givePlayerBP(WeaponSizes.nameFor("PROOF_BEAM", 2))
        val beam = kit.player.hardpoints[0].weapon as BeamBlueprint.BeamInstance
        val aim = SelectedTarget.BeamAim(beam, 0, right, ConstPoint(17, 17))
        aim.angle = PI.toFloat()
        aim.length = WeaponSizes.beamLength(aim.length, 2)
        aim.updateHitRooms()
        beam.fire(aim)

        assertEquals("The copy isn't in the ship the beam's swept through", 1, middle.inboundBeams.size)
        assertTrue("The copy went to the leftmost ship", left.inboundBeams.isEmpty())
    }

    /**
     * "Звук урона по корпусу от событий" (no sound for an event's hull
     * damage)
     *
     * An event's hull damage makes the sound of a hit on the hull.
     */
    @Test
    fun testEventHullDamagePlaysTheHullHitSound() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), extraEvents = listOf(EVENT_XML))
        val sounds = RecordingSoundManager()
        kit.state.useSounds(sounds)

        // An event that only gives scrap makes no such sound
        kit.state.givePlayerResources(kit.resolve("PROOF_NO_DAMAGE"))
        assertTrue(sounds.played.none { it.startsWith("hitHull") })

        // One that damages the hull does, once
        val hull = kit.player.health
        kit.state.givePlayerResources(kit.resolve("PROOF_HULL_DAMAGE"))
        assertEquals(hull - 3, kit.player.health)
        assertEquals(1, sounds.played.count { it.startsWith("hitHull") })
    }

    // The player's ship with six layers of shields, in two systems
    private fun shieldsKit(): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML),
            playerShip = "PLAYER_SHIP_PROOF_SIX_LAYERS"
        )
        kit.powerUpAll()
        repeat(10) { kit.updateSingle(1f) }
        return kit
    }

    private fun beamKit(playerShip: String, enemy: String): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(WingmanTest.BLUEPRINT_XML, BLUEPRINT_XML),
            extraEvents = listOf(EVENT_XML),
            extraAnimations = listOf(ANIMATIONS_XML),
            playerShip = playerShip
        )
        kit.state.debugSpawnShip(kit.state.eventManager.getShip(enemy), Difficulty.NORMAL, 0, 1)
        kit.state.updateGameState(0.01f)
        return kit
    }

    // Fire a triple beam its full length (as the player aims it) at the
    // middle of [target]
    private fun fireTripleBeam(kit: InGameTest, target: Ship): Pair<BeamBlueprint.BeamInstance, SelectedTarget.BeamAim> {
        kit.givePlayerBP(WeaponSizes.nameFor("PROOF_BEAM", 3))
        val beam = kit.player.hardpoints[0].weapon as BeamBlueprint.BeamInstance
        val aim = SelectedTarget.BeamAim(beam, 0, target, ConstPoint(17, 17))
        aim.length = WeaponSizes.beamLength(aim.length, 3)
        aim.updateHitRooms()
        beam.fire(aim)
        return beam to aim
    }

    // The player with a hacking system, against a ship with two engines
    // systems, both running
    private fun hackKit(): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML),
            extraEvents = listOf(EVENT_XML), playerShip = "PLAYER_SHIP_PROOF_HACKING"
        )
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("PROOF_TWIN_ENGINES_ENEMY"), Difficulty.NORMAL, 0, 1)
        kit.state.updateGameState(0.01f)
        kit.powerUpAll()
        kit.player.dronesCount = 5

        val enemy = kit.state.enemy!!
        for (engines in enemy.engineSystems) {
            repeat(engines.energyLevels) { engines.increasePower() }
        }
        kit.state.updateGameState(0.05f)
        assertTrue("The enemy can't dodge before the hack", enemy.evasion > 0)
        return kit
    }

    // Land the player's hacking probe in [room], and start a pulse
    private fun hackPulse(kit: InGameTest, room: xyz.znix.xftl.layout.Room) {
        val hacking = kit.player.hacking!!
        hacking.selectTarget(room)
        var time = 0f
        while (!hacking.droneLanded && time < 60f) {
            kit.state.updateGameState(0.05f)
            time += 0.05f
        }
        assertTrue("The probe didn't land", hacking.droneLanded)
        hacking.startHackingPulse()
        repeat(2) { kit.state.updateGameState(0.05f) }
    }

    private fun InGameTest.resolve(event: String) =
        (state.eventManager[event] as Event).resolveResources(state, Random(0))

    /** A sound manager that keeps the names of the samples played. */
    private class RecordingSoundManager : SoundManager {
        val played = ArrayList<String>()

        override fun getSample(name: String): FTLSound = object : FTLSound {
            override fun play(): SoundInstance {
                played += name
                return object : SoundInstance {
                    override var isPaused: Boolean = false
                    override var volume: Float = 1f
                    override fun stop() {}
                }
            }
        }

        override fun getSampleOrWarn(name: String): FTLSound = getSample(name)

        override fun getLoop(name: String): LoopHandle =
            LoopHandle(SoundSpec(org.jdom2.Element(name).setAttribute("volume", "0")), null)

        override fun getTrack(name: String): MusicSpec = error("Not used")

        override fun switchMusicList(tracks: List<MusicSpec>) {}
    }

    companion object {
        // The kit makes its own (silent) sound manager, and the game reads
        // it from its content each time a sound's played, so swap it there
        private fun InGameState.useSounds(sounds: SoundManager) {
            val field = InGameState.GameContent::class.java.getDeclaredField("sounds")
            field.isAccessible = true
            field.set(content, sounds)
        }

        // The rest are private to the game's classes: reach them by reflection

        private val Ship.flashTimeLeft: Float
            get() = Ship::class.java.getDeclaredField("shieldFlash").let {
                it.isAccessible = true
                it.getFloat(this)
            }

        private val AbstractProjectile.hasDied: Boolean
            get() = AbstractProjectile::class.java.getDeclaredField("dead").let {
                it.isAccessible = true
                it.getBoolean(this)
            }

        private fun AbstractWeaponProjectile.crossShields() {
            AbstractWeaponProjectile::class.java.getDeclaredMethod("crossedShieldLine").let {
                it.isAccessible = true
                it.invoke(this)
            }
        }

        private fun AbstractWeaponProjectile.reachTarget() {
            AbstractWeaponProjectile::class.java.getDeclaredMethod("reachedTarget").let {
                it.isAccessible = true
                it.invoke(this)
            }
        }

        private val BeamBlueprint.BeamInstance.firedAim: SelectedTarget.BeamAim?
            get() = BeamBlueprint.BeamInstance::class.java.getDeclaredField("target").let {
                it.isAccessible = true
                it.get(this) as SelectedTarget.BeamAim?
            }

        private val BeamBlueprint.BeamInstance.copiesInTarget: Int
            get() = BeamBlueprint.BeamInstance::class.java.getDeclaredField("superShieldCopies").let {
                it.isAccessible = true
                it.getInt(this)
            }

        private val ShieldDrone.isRowFull: Boolean
            get() = ShieldDrone::class.java.getDeclaredMethod("isFull").let {
                it.isAccessible = true
                it.invoke(this) as Boolean
            }

        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <systemBlueprint name="shields">
        <type>shields</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
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
    <systemBlueprint name="cloaking">
        <type>cloaking</type>
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

    <!-- Six layers, in two shields systems, and no engines, so it can't
         dodge -->
    <shipBlueprint name="PLAYER_SHIP_PROOF_SIX_LAYERS" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <shields power="8" room="3"/>
            <shields power="4" room="5"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PLAYER_SHIP_PROOF_HACKING" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <hacking power="3" max="3" room="2"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PLAYER_SHIP_PROOF_DRONES" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <drones power="2" room="3"/>
        </systemList>
    </shipBlueprint>

    <!-- Automated, as tests can't spawn real crew -->
    <shipBlueprint name="PROOF_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="10"/>
        <maxPower amount="10"/>
        <crewCount amount="0" max="0" class="test_crew"/>
        <systemList>
            <pilot power="1" max="1" room="1" start="true"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PROOF_TWIN_ENGINES_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="0" max="0" class="test_crew"/>
        <systemList>
            <pilot power="1" max="1" room="1" start="true"/>
            <engines power="2" room="2" start="true"/>
            <engines power="2" room="3" start="true"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PROOF_CLOAKING_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="2" max="2" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <cloaking power="3" max="3" room="2"/>
        </systemList>
    </shipBlueprint>

    <droneBlueprint name="DRONE_HACKING">
        <type>HACKING</type>
        <power>0</power>
        <speed>15</speed>
    </droneBlueprint>

    <droneBlueprint name="PROOF_SHIELD_DRONE">
        <type>SHIELD</type>
        <power>2</power>
        <cooldown>15000</cooldown>
        <speed>5</speed>
        <droneImage>drone_shield</droneImage>
    </droneBlueprint>

    <weaponBlueprint name="PROOF_MISSILE">
        <type>MISSILES</type>
        <weaponArt>generic_test_weapon</weaponArt>
        <image>dummy_anim</image>
        <explosion>dummy_anim</explosion>
        <damage>3</damage>
        <sp>5</sp>
        <missiles>1</missiles>
    </weaponBlueprint>

    <weaponBlueprint name="PROOF_LASER">
        <type>LASER</type>
        <weaponArt>generic_test_weapon</weaponArt>
        <image>dummy_anim</image>
        <explosion>dummy_anim</explosion>
        <damage>1</damage>
    </weaponBlueprint>

    <!-- Combined into its twin and triple versions, as it has a rarity -->
    <weaponBlueprint name="PROOF_BEAM">
        <type>BEAM</type>
        <weaponArt>generic_test_weapon</weaponArt>
        <iconImage>dummy_anim</iconImage>
        <image>dummy_anim</image>
        <damage>2</damage>
        <length>60</length>
        <power>3</power>
        <cooldown>10</cooldown>
        <cost>60</cost>
        <rarity>2</rarity>
    </weaponBlueprint>
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <ship name="PROOF_ENEMY" auto_blueprint="PROOF_ENEMY_SHIP"/>
    <ship name="PROOF_TWIN_ENGINES_ENEMY" auto_blueprint="PROOF_TWIN_ENGINES_ENEMY_SHIP"/>
    <ship name="PROOF_CLOAKING_ENEMY" blueprint="PROOF_CLOAKING_ENEMY_SHIP"/>
    <event name="PROOF_HULL_DAMAGE">
        <damage amount="3"/>
    </event>
    <event name="PROOF_NO_DAMAGE">
        <item_modify>
            <item type="scrap" min="10" max="10"/>
        </item_modify>
    </event>
</FTL>
"""

        // What the hacking probe and the explosions need
        private val ANIMATIONS_XML = listOf("drone_hack_fly", "drone_hack_land", "drone_hack_extend", "explosion_random")
            .joinToString("\n", "<FTL>\n", "\n</FTL>") {
                """<anim name="$it"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>"""
            }
    }
}
