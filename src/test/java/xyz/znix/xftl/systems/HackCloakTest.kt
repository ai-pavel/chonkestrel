package xyz.znix.xftl.systems

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.testkit.InGameTest

/**
 * A hacking pulse on a ship's cloaking ends its cloak, and it can't cloak
 * again until the pulse is over.
 */
class HackCloakTest {
    @Test
    fun testThePulseEndsTheCloak() {
        val kit = createKit("PLAYER_SHIP_HACK_TEST")
        checkPulse(kit, kit.state.enemy!!)
    }

    @Test
    fun testThePulseEndsAWingmansCloak() {
        val kit = createKit("PLAYER_SHIP_HACK_TEST_BIG")
        checkPulse(kit, kit.state.wingmen.single().ship)
    }

    @Test
    fun testAProbeHeldOffByACloakIsPending() {
        val kit = createKit("PLAYER_SHIP_HACK_TEST")
        val enemy = kit.state.enemy!!
        val cloaking = enemy.cloaking!!
        val hacking = kit.player.hacking!!
        val room = cloaking.room!!

        // Launched at a cloaked ship, the probe waits, and shows where it's going
        repeat(5) { kit.state.updateGameState(0.05f) }
        cloaking.activateCloak()
        kit.state.updateGameState(0.05f)
        assertTrue(enemy.isCloakActive)
        hacking.selectTarget(room)
        assertEquals(room, hacking.pendingRoom)
        repeat(20) { kit.state.updateGameState(0.05f) }
        assertTrue(hacking.droneInFlight)
        assertEquals(room, hacking.pendingRoom)

        // Once the cloak's down, it flies on and lands
        cloaking.timeRemaining = 0f
        var time = 0f
        while (!hacking.droneLanded && time < 60f) {
            cloaking.timeRemaining = null
            kit.state.updateGameState(0.05f)
            time += 0.05f
        }
        assertTrue(hacking.droneLanded)
        assertNull(hacking.pendingRoom)
    }

    private fun createKit(playerShip: String): InGameTest {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML),
            extraEvents = listOf(EVENT_XML), playerShip = playerShip
        )
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_CLOAKING_ENEMY"), Difficulty.NORMAL, 0, 1)
        kit.state.updateGameState(0.01f)
        kit.powerUpAll()
        kit.player.dronesCount = 5
        return kit
    }

    private fun checkPulse(kit: InGameTest, enemy: Ship) {
        val cloaking = enemy.cloaking!!
        val hacking = kit.player.hacking!!

        // Land the drone on its cloaking, before it cloaks
        hacking.selectTarget(cloaking.room!!)
        var time = 0f
        while (!hacking.droneLanded && time < 60f) {
            cloaking.timeRemaining = null
            kit.state.updateGameState(0.05f)
            time += 0.05f
        }
        assertTrue("The drone didn't land", hacking.droneLanded)

        // It cloaks...
        repeat(10) { kit.state.updateGameState(0.05f) }
        cloaking.activateCloak()
        repeat(2) { kit.state.updateGameState(0.05f) }
        assertTrue("It didn't cloak", enemy.isCloakActive)

        // ...and the pulse ends it
        hacking.startHackingPulse()
        repeat(2) { kit.state.updateGameState(0.05f) }
        assertFalse(enemy.isCloakActive)
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
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
    <droneBlueprint name="DRONE_HACKING">
        <type>HACKING</type>
        <power>0</power>
        <speed>15</speed>
    </droneBlueprint>

    <shipBlueprint name="PLAYER_SHIP_HACK_TEST" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <hacking power="3" max="3" room="2"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="PLAYER_SHIP_HACK_TEST_BIG" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <shipSize>2</shipSize>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <hacking power="3" max="3" room="2"/>
        </systemList>
    </shipBlueprint>

    <shipBlueprint name="TEST_CLOAKING_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="2" max="2" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <cloaking power="3" max="3" room="2"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <ship name="TEST_CLOAKING_ENEMY" blueprint="TEST_CLOAKING_ENEMY_SHIP"/>
</FTL>
"""

        private val ANIMATIONS_XML = listOf("drone_hack_fly", "drone_hack_land", "drone_hack_extend", "explosion_random")
            .joinToString("\n", "<FTL>\n", "\n</FTL>") {
                """<anim name="$it"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>"""
            }
    }
}
