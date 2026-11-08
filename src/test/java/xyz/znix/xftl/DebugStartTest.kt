package xyz.znix.xftl

import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.znix.xftl.augments.AugPreigniter
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.systems.Clonebay
import xyz.znix.xftl.systems.Medbay
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.systems.Oxygen
import xyz.znix.xftl.systems.Weapons
import xyz.znix.xftl.testkit.InGameTest

class DebugStartTest {
    private var wasEnabled = false
    private var wasZoltan = false
    private var wasRandomize = false

    @Before
    fun enableDebug() {
        wasEnabled = XftlDebug.enabled
        wasZoltan = XftlDebug.zoltan
        wasRandomize = XftlDebug.randomize
        XftlDebug.enabled = true
    }

    @After
    fun restoreDebug() {
        XftlDebug.enabled = wasEnabled
        XftlDebug.zoltan = wasZoltan
        XftlDebug.randomize = wasRandomize
    }

    @Test
    fun testEverythingIsMaxedOut() {
        // Debug mode only applies to the player's ships, so this needs a player ship name
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_DEBUG_TEST")
        val player = kit.player

        // The system that isn't installed by default is installed
        assertEquals(1, player.weaponSystems.size)

        // Every system is fully upgraded
        for (system in player.systems) {
            assertEquals(system.blueprint.maxPower, system.energyLevels)
        }

        assertEquals(XftlDebug.REACTOR_POWER, player.purchasedReactorPower)
        assertEquals(XftlDebug.RESOURCE_COUNT, player.fuelCount)
        assertEquals(XftlDebug.RESOURCE_COUNT, player.missilesCount)
        assertEquals(XftlDebug.RESOURCE_COUNT, player.dronesCount)
        assertEquals(XftlDebug.SCRAP, player.scrap)

        // And the debug loadout is used
        assertEquals("TEST_LASER", player.hardpoints[0].weapon?.type?.name)
        assertEquals("TEST_LASER", player.hardpoints[1].weapon?.type?.name)
    }

    @Test
    fun testEverythingIsPowered() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_DEBUG_TEST")
        val player = kit.player

        // Every system is turned all the way up, as the reactor's big enough,
        // except the oxygen and medbay, which only get one power.
        // Weapons use as much power as their weapons need.
        for (system in player.mainSystems) {
            when (system) {
                is Weapons -> continue
                is Oxygen, is Medbay, is Clonebay -> assertEquals(system.toString(), 1, system.powerSelected)
                else -> assertEquals(system.toString(), system.energyLevels, system.powerSelected)
            }
        }

        // And all the weapons are on
        for (hardpoint in player.hardpoints) {
            assertTrue(hardpoint.weapon!!.isPowered)
        }
    }

    @Test
    fun testRandomWeapons() {
        XftlDebug.randomize = true

        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML, RANDOM_WEAPONS_XML),
            playerShip = "PLAYER_SHIP_DEBUG_TEST"
        )
        val player = kit.player

        // Every slot is filled, whether or not there's the power for them all
        // (see Ship.randomizeDebugWeapons)
        val weapons = player.hardpoints.mapNotNull { it.weapon?.type }
        assertEquals(player.hardpoints.size, weapons.size)

        // all different
        assertEquals(weapons.size, weapons.toSet().size)
    }

    @Test
    fun testRandomDrones() {
        XftlDebug.randomize = true

        val kit = InGameTest(
            extraBlueprints = listOf(RANDOM_WEAPONS_XML, RANDOM_DRONES_XML),
            playerShip = "PLAYER_SHIP_DEBUG_DRONES"
        )
        val drones = kit.player.droneSystems.single()

        // Every slot, and the whole cargo hold, has a twin or triple drone
        // (see Ship.fillDebugDrones), each of a different one while there
        // are enough
        val types = drones.drones.map { it?.type }
        assertTrue(types.all { it != null && it.combinedCount in 2..3 })
        assertTrue(types.all { it!!.combinedOf.first().startsWith("TEST_RANDOM_DRONE_") })
        assertEquals(types.size, types.map { it!!.combinedOf.first() }.toSet().size)
        val cargo = kit.player.cargoBlueprints
        assertTrue(cargo.all { it is DroneBlueprint && it.isCombined })
    }

    @Test
    fun testRandomizeSkipsSelfTargetingBombs() {
        XftlDebug.randomize = true

        // However many times it's tried, the Healing Burst and Repair Burst
        // never turn up, though the other weapons do
        val seen = HashSet<String>()
        repeat(10) {
            val kit = InGameTest(
                extraBlueprints = listOf(BLUEPRINT_XML, RANDOM_WEAPONS_XML, SELF_TARGETING_XML),
                playerShip = "PLAYER_SHIP_DEBUG_TEST"
            )
            val player = kit.player
            seen += player.hardpoints.mapNotNull { it.weapon?.type?.name }
            seen += player.cargoBlueprints.filterNotNull().map { it.name }
        }
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.toString(), seen.none { it in Ship.RANDOMIZE_EXCLUDED })
    }

    @Test
    fun testRandomAugments() {
        XftlDebug.randomize = true

        val kit = InGameTest(
            extraBlueprints = listOf(BLUEPRINT_XML, RANDOM_AUGMENTS_XML),
            playerShip = "PLAYER_SHIP_DEBUG_TEST"
        )
        val augments = kit.player.augments.map { it.name }

        // The augment slots are filled with the Pre-Igniter and different
        // random augments
        assertEquals(kit.player.maxAugments, augments.size)
        assertEquals(augments.size, augments.toSet().size)
        assertTrue(augments.toString(), AugPreigniter.NAME in augments)
        assertTrue(augments.toString(), augments.all {
            it.startsWith("TEST_RANDOM_AUG_") || it == "ENERGY_SHIELD" || it == AugPreigniter.NAME
        })
    }

    @Test
    fun testZoltanFillsTheAugments() {
        XftlDebug.enabled = false
        XftlDebug.zoltan = true

        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_DEBUG_TEST")
        val player = kit.player

        assertEquals(player.maxAugments, player.augments.size)
        assertTrue(player.augments.all { it is AugZoltanShield })
    }

    @Test
    fun testTheLoadoutIsPickedWhenTheShipIsMade() {
        // The blueprint's loaded in debug mode, but debug mode can be turned
        // off later, to start a normal game (as the screenshots do)
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "PLAYER_SHIP_DEBUG_TEST")
        val blueprint = kit.state.blueprintManager.getShip("PLAYER_SHIP_DEBUG_TEST")
        assertEquals(listOf("TEST_LASER", "TEST_LASER"), blueprint.initialWeapons)

        XftlDebug.enabled = false
        assertEquals(emptyList<String?>(), blueprint.initialWeapons)
    }

    @Test
    fun testDebugRespectsTheSystemLimit() {
        // Two main systems at most: they're installed in the order the
        // blueprint lists them, rather than by room, so the weapons (listed
        // last, but in an earlier room) are left out
        val kit = InGameTest(extraBlueprints = listOf(LIMITED_XML), playerShip = "PLAYER_SHIP_LIMITED")
        val player = kit.player

        assertEquals(2, player.mainSystems.size)
        assertTrue(player.oxygen != null)
        assertTrue(player.engines != null)
        assertTrue(player.weapons == null)

        // The subsystems have their own limit
        assertEquals(1, player.subSystems.size)
        assertEquals(8, player.maxSubSystems)
    }

    companion object {
        @Language("XML")
        private const val LIMITED_XML = """<FTL>
    <shipBlueprint name="PLAYER_SHIP_LIMITED" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="5"/>
        <maxSystems>2</maxSystems>
        <systemList>
            <oxygen power="1" room="5"/>
            <pilot power="1" room="2"/>
            <engines power="1" room="0" start="false"/>
            <weapons power="1" room="1" start="false"/>
        </systemList>
    </shipBlueprint>
</FTL>"""

        // Plenty of weapons that can turn up in the game, to pick from
        @Language("XML")
        private val RANDOM_WEAPONS_XML = "<FTL>" + (1..12).joinToString("") {
            "<weaponBlueprint name=\"TEST_RANDOM_WEAPON_$it\"><type>LASER</type><power>${it % 3 + 1}</power>" +
                    "<rarity>1</rarity><weaponArt>generic_test_weapon</weaponArt><iconImage>dummy_anim</iconImage></weaponBlueprint>"
        } + "</FTL>"

        // The bombs that only target your own ship, which can turn up in the game
        @Language("XML")
        private val SELF_TARGETING_XML = "<FTL>" + listOf("BOMB_HEAL", "BOMB_HEAL_SYSTEM").joinToString("") {
            "<weaponBlueprint name=\"$it\"><type>BOMB</type><power>1</power>" +
                    "<rarity>5</rarity><weaponArt>generic_test_weapon</weaponArt><iconImage>dummy_anim</iconImage></weaponBlueprint>"
        } + "</FTL>"

        // Plenty of augments to pick from
        @Language("XML")
        private val RANDOM_AUGMENTS_XML = "<FTL>" + (1..8).joinToString("") {
            "<augBlueprint name=\"TEST_RANDOM_AUG_$it\"><cost>10</cost><rarity>1</rarity><stackable>false</stackable></augBlueprint>"
        } + "<augBlueprint name=\"${AugPreigniter.NAME}\"><cost>10</cost><rarity>1</rarity><stackable>false</stackable></augBlueprint>" +
            "</FTL>"

        // Some drones too, and a ship with a drones system for them
        @Language("XML")
        private val RANDOM_DRONES_XML = "<FTL>" + (1..6).joinToString("") {
            "<droneBlueprint name=\"TEST_RANDOM_DRONE_$it\"><type>COMBAT</type><power>${it % 2 + 1}</power><rarity>1</rarity></droneBlueprint>"
        } + """
    <droneBlueprint name="TEST_DEBUG_DRONE"><type>COMBAT</type><power>1</power></droneBlueprint>
    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>4</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
    <shipBlueprint name="PLAYER_SHIP_DEBUG_DRONES" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="5"/>
        <droneSlots>3</droneSlots>
        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <drones power="2" room="3"/>
        </systemList>
        <xftlDebug>
            <droneList count="3" drones="5">
                <drone name="TEST_DEBUG_DRONE"/>
                <drone name="TEST_DEBUG_DRONE"/>
                <drone name="TEST_DEBUG_DRONE"/>
            </droneList>
        </xftlDebug>
    </shipBlueprint>
</FTL>"""

        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="PLAYER_SHIP_DEBUG_TEST" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="5"/>
        <weaponSlots>2</weaponSlots>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
            <weapons power="1" room="3" start="false"/>
        </systemList>

        <xftlDebug>
            <weaponList>
                <weapon name="TEST_LASER"/>
                <weapon name="TEST_LASER"/>
            </weaponList>
        </xftlDebug>
    </shipBlueprint>

    <augBlueprint name="ENERGY_SHIELD">
        <cost>1</cost>
        <stackable>true</stackable>
    </augBlueprint>
</FTL>
"""
    }
}
