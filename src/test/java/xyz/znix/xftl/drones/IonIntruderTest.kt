package xyz.znix.xftl.drones

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.FTLAnimation
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.DroneSizes

/**
 * The Ion Intruder (BOARDER_ION) ionises the enemy's systems, each with one
 * strong discharge: its wings stay down as it charges, and rise once,
 * slowly, for each.
 */
class IonIntruderTest {
    @Test
    fun testItRaisesItsWingsOnceAZap() {
        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML), extraEvents = listOf(EVENT_XML),
            playerShip = "TEST_ION_PLAYER_SHIP"
        )
        kit.powerUpAll()
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_ION_ENEMY"), Difficulty.NORMAL, 0, 1)
        val enemy = kit.state.enemy!!
        // Launched from the drones system, as the player would
        val system = kit.player.droneSystems.single()
        kit.player.dronesCount = 10
        system.drones[0] = Drones.DroneInfo(kit.state.blueprintManager["BOARDER_ION"] as DroneBlueprint, null)
        assertTrue(system.setDronePower(0, true))
        val drone = system.drones[0]!!.instance as BoardingDrone

        var raises = 0
        var everPawn = false
        var lastIcon: FTLAnimation? = null
        var mostIon = 0f
        repeat(600) {
            kit.state.updateGameState(0.05f)
            for (ship in listOf(kit.player, enemy))
                ship.rooms.forEach { it.oxygen = 1f }
            mostIon = maxOf(mostIon, enemy.systems.maxOf { it.ionTimer })
            val pawn = drone.pawn ?: return@repeat
            everPawn = true
            val icon = pawn.icon
            if (icon !== lastIcon && icon.spec.name == "boarder_ion_ion" && !icon.backwards && !icon.isPaused)
                raises++
            lastIcon = icon
        }

        // One strong discharge ionises the system for a long while (the
        // enemy's only one), and it doesn't zap it again until that's worn
        // off: over 30 seconds, once or twice, raising its wings once each
        assertTrue("ever $everPawn, hostile ${kit.state.isEnemyOf(kit.player, enemy)}, powered ${drone.isPowered}, " +
                "enemy crew ${enemy.crew.size}", drone.pawn != null)
        assertTrue("most ion $mostIon", mostIon >= BoardingDrone.ION_ZAP_DAMAGE * 5f - 1f)
        assertTrue("raised its wings $raises times", raises in 1..2)
    }

    @Test
    fun itsTwinsAndTriplesAreIonIntrudersToo() {
        for (size in DroneSizes.SIZES) {
            val kit = InGameTest(
                extraBlueprints = listOf(BLUEPRINT_XML), extraAnimations = listOf(ANIMATIONS_XML), extraEvents = listOf(EVENT_XML),
                playerShip = "TEST_ION_PLAYER_SHIP"
            )
            kit.powerUpAll()
            kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_ION_ENEMY"), Difficulty.NORMAL, 0, 1)
            val enemy = kit.state.enemy!!
            val system = kit.player.droneSystems.single()
            system.energyLevels = system.blueprint.maxPower
            kit.player.purchasedReactorPower = kit.player.mainSystems.sumOf { it.energyLevels }
            kit.player.updateAvailablePower()
            repeat(system.energyLevels) { system.increasePower() }
            kit.player.dronesCount = 10
            val name = DroneSizes.nameFor("BOARDER_ION", size)
            system.drones[0] = Drones.DroneInfo(kit.state.blueprintManager[name] as DroneBlueprint, null)
            assertTrue(system.setDronePower(0, true))

            // Each copy has the Ion Intruder's look, and ionises the systems
            val copies = system.drones[0]!!.liveCopies.map { it as BoardingDrone }
            assertEquals(size, copies.size)
            assertTrue(copies.all { it.isIon && it.pawnLook == BoardingDrone.ION_LOOK })
            var mostIon = 0f
            repeat(600) {
                kit.state.updateGameState(0.05f)
                for (ship in listOf(kit.player, enemy))
                    ship.rooms.forEach { it.oxygen = 1f }
                mostIon = maxOf(mostIon, enemy.systems.maxOf { it.ionTimer })
            }
            assertTrue("x$size: most ion $mostIon", mostIon > 0f)
        }
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <crewBlueprint name="battle">
        <cost>1</cost>
    </crewBlueprint>

    <droneBlueprint name="BOARDER_ION">
        <type>BOARDER</type>
        <power>3</power>
        <speed>18</speed>
    </droneBlueprint>

    <shipBlueprint name="TEST_ION_PLAYER_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <droneSlots>2</droneSlots>
        <systemList>
            <pilot power="1" max="1" room="1"/>
            <drones power="4" max="8" room="3"/>
        </systemList>
    </shipBlueprint>

    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <shipBlueprint name="TEST_ION_ENEMY_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <crewCount amount="1" max="1" class="human"/>
        <systemList>
            <pilot power="1" max="1" room="1"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""

        // The Ion Intruder's animations, and its discharge's
        private val ANIMATIONS_XML = (
                listOf("portrait", "repair", "death_right", "teleport", "power_up", "power_down", "ion", "glow", "attack") +
                        listOf("up", "down", "left", "right").flatMap { dir ->
                            listOf("shoot", "walk", "punch", "type", "fire").map { "${it}_$dir" }
                        }
                ).map { "boarder_ion_$it" }.plus(listOf("explosion_small_ion", "battle_portrait"))
            .joinToString("\n", "<FTL>\n", "\n</FTL>") {
                """<anim name="$it"><sheet>dummy_sheet</sheet><desc length="1" x="0" y="0"/><time>1</time></anim>"""
            }

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <ship name="TEST_ION_ENEMY" blueprint="TEST_ION_ENEMY_SHIP"/>
</FTL>
"""
    }
}
