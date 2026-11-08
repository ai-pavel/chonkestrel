package xyz.znix.xftl.game

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.systems.SystemBlueprint
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import kotlin.random.Random

class StoreSizeTest {
    @Test
    fun testSectionsAreFull() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML))
        var sawAugments = false

        for (seed in 0 until 20) {
            val store = StoreData()
            store.generateRandomContents(kit.state, Random(seed))

            val items = mapOf(
                StoreData.Section.WEAPONS to Pair(store.weapons, AbstractWeaponBlueprint::class.java),
                StoreData.Section.DRONES to Pair(store.drones, DroneBlueprint::class.java),
                StoreData.Section.AUGMENTS to Pair(store.augments, AugmentBlueprint::class.java),
                StoreData.Section.CREW to Pair(store.crew, CrewBlueprint::class.java),
            )
            for ((section, value) in items) {
                if (section !in store.sections)
                    continue

                // Each section is full, unless there's not enough things to sell
                val (list, type) = value
                val available = kit.state.lootPool.getManyRandom(type, Random(0), Int.MAX_VALUE).size
                val expected = minOf(StoreData.ITEMS_PER_SECTION, available)
                assertEquals("$section with seed $seed", expected, list.size)

                // Nothing's in a section twice
                assertEquals(list.toSet().size, list.size)
            }

            // There's enough augments to fill a section, so this checks
            // a section is actually filled.
            if (StoreData.Section.AUGMENTS in store.sections) {
                assertEquals(StoreData.ITEMS_PER_SECTION, store.augments.size)
                sawAugments = true
            }

            // The systems section can be shorter if there aren't enough systems to sell
            assertTrue(store.systems.size <= StoreData.ITEMS_PER_SECTION)
        }

        assertTrue(sawAugments)
    }

    @Test
    fun testOnlySystemsTheShipCanBuyAreOnSale() {
        // As in vanilla, a store's systems section leaves out the systems
        // the ship already has, and those it can't take at all.
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML))
        val player = kit.player
        val installed = player.systems.map { it.blueprint }.toSet()
        val noRoom = kit.state.blueprintManager.blueprints.values
            .filterIsInstance<SystemBlueprint>()
            .filter { system -> player.systemSlots.none { it.system == system } }
            .toSet()

        val sold = HashSet<SystemBlueprint>()
        for (seed in 0 until 50) {
            val store = StoreData()
            store.generateRandomContents(kit.state, Random(seed))
            sold += store.systems.filterNotNull()
        }
        assertTrue("$sold", sold.none { it in installed })
        assertTrue("$sold", sold.none { it in noRoom })
    }

    @Test
    fun testTheDronesSystemComesWithAFreeDrone() {
        // As in vanilla, a store selling the drones system gives a common
        // drone with it, and remembers which across a save
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML, FREE_DRONE_XML), playerShip = "TEST_DRONELESS_SHIP")
        var sawOne = false
        for (seed in 0 until 50) {
            val store = StoreData()
            store.generateRandomContents(kit.state, Random(seed))
            val sellsDrones = store.systems.any { it?.name == "drones" }
            if (!sellsDrones) {
                assertEquals(null, store.freeDrone)
                continue
            }
            sawOne = true
            assertEquals("TEST_COMMON_DRONE", store.freeDrone?.name)

            val elem = org.jdom2.Element("store")
            store.saveToXML(elem)
            val loaded = StoreData()
            loaded.loadFromXML(kit.state, elem)
            assertEquals(store.freeDrone, loaded.freeDrone)
        }
        assertTrue(sawOne)
    }

    companion object {
        // A ship with room for a drones system, and a common drone and a
        // rare one (only the common one comes free)
        @Language("XML")
        private const val FREE_DRONE_XML = """
<FTL>
    <shipBlueprint name="TEST_DRONELESS_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <medbay power="1" max="1" room="4"/>
            <drones power="2" max="8" room="5" start="false"/>
        </systemList>
    </shipBlueprint>
    <droneBlueprint name="TEST_COMMON_DRONE">
        <type>REPAIR</type>
        <power>1</power>
        <cost>30</cost>
        <rarity>1</rarity>
    </droneBlueprint>
    <droneBlueprint name="TEST_RARE_DRONE">
        <type>REPAIR</type>
        <power>1</power>
        <cost>30</cost>
        <rarity>4</rarity>
    </droneBlueprint>
</FTL>
"""

        // Stores always look for these systems
        @Language("XML")
        private const val SYSTEMS_XML = """
    <systemBlueprint name="drones">
        <type>drones</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
    <systemBlueprint name="shields">
        <type>shields</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>
"""

        @Language("XML")
        private val BLUEPRINT_XML = "<FTL>" + SYSTEMS_XML + (1..StoreData.ITEMS_PER_SECTION).joinToString("") {
            "<augBlueprint name=\"TEST_STORE_AUG_$it\"><cost>10</cost><rarity>1</rarity><stackable>false</stackable></augBlueprint>"
        } + "</FTL>"
    }
}
