package xyz.znix.xftl.augments

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.game.StoreData
import xyz.znix.xftl.testkit.InGameTest
import kotlin.random.Random

class ZoltanShieldTest {
    @Test
    fun testShieldsStack() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_ZOLTAN_SHIELDS")
        val player = kit.player

        // Two Zoltan shields give twice the super-shield
        assertEquals(10, player.maxSuperShield)
        assertEquals(10, player.superShield)

        // And it's recharged to the full amount when jumping
        player.superShield = 3
        player.resetAfterJump()
        assertEquals(10, player.superShield)
    }

    @Test
    fun testLosingAShieldLowersTheMaximum() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_ZOLTAN_SHIELDS")
        val player = kit.player

        // Sell one of the shields: the extra super-shield goes immediately
        player.removeBlueprint(player.augments.first())
        assertEquals(5, player.maxSuperShield)
        assertEquals(5, player.superShield)

        player.resetAfterJump()
        assertEquals(5, player.superShield)
    }

    @Test
    fun testSellingEveryShieldRemovesTheSuperShield() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_ZOLTAN_SHIELDS")
        val player = kit.player

        // The ship's base maximum is still 5, but there's nothing to
        // provide a super-shield anymore.
        player.removeBlueprint(player.augments.first())
        player.removeBlueprint(player.augments.first())
        assertEquals(0, player.superShield)

        player.resetAfterJump()
        assertEquals(0, player.superShield)
    }

    @Test
    fun testStackingIsCapped() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_ZOLTAN_SHIELDS")
        val player = kit.player

        val shield = player.augments.first()
        while (player.augments.size < AugZoltanShield.MAX_STACKED + 2) {
            player.augments.add(shield)
        }
        player.resetAfterJump()

        val max = AugZoltanShield.MAX_STACKED * AugZoltanShield.SHIELD_PER_AUGMENT
        assertEquals(max, player.maxSuperShield)
        assertEquals(max, player.superShield)
    }

    @Test
    fun testStoresSellItMoreOftenLaterOn() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML, FILLER_AUGMENTS_XML))
        fun storesWithIt(progress: Float): Int {
            var count = 0
            var withAugments = 0
            for (seed in 0 until 2000) {
                val store = StoreData()
                store.generateRandomContents(kit.state, Random(seed), progress = progress)
                if (StoreData.Section.AUGMENTS !in store.sections)
                    continue
                withAugments++
                if (store.augments.any { it?.name == AugZoltanShield.NAME })
                    count++
            }
            assertTrue(withAugments > 200)
            return count
        }

        val early = storesWithIt(0f)
        val late = storesWithIt(1f)
        assertTrue("early $early, late $late", late > early * 4)
        assertTrue(early > 0)
    }

    @Test
    fun testBoardingTwoShieldedShipsGivesOne() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_ZOLTAN_SHIELDS")
        val shielded = kit.player

        // The first ship taken doesn't, the second does, and only once
        assertNull(kit.state.zoltanShieldBoarded(shielded))
        assertEquals(AugZoltanShield.NAME, kit.state.zoltanShieldBoarded(shielded)?.name)
        assertNull(kit.state.zoltanShieldBoarded(shielded))
    }

    companion object {
        // Augments for the stores to fill their augments sections with, and
        // the systems stores always look for
        @Language("XML")
        private const val FILLER_AUGMENTS_XML = """
<FTL>
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
    <augBlueprint name="TEST_FILLER_AUG_1">
        <cost>10</cost>
        <rarity>1</rarity>
        <stackable>false</stackable>
    </augBlueprint>
    <augBlueprint name="TEST_FILLER_AUG_2">
        <cost>10</cost>
        <rarity>1</rarity>
        <stackable>false</stackable>
    </augBlueprint>
</FTL>
"""

        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_ZOLTAN_SHIELDS" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
        </systemList>

        <aug name="ENERGY_SHIELD"/>
        <aug name="ENERGY_SHIELD"/>
    </shipBlueprint>

    <augBlueprint name="ENERGY_SHIELD">
        <cost>1</cost>
        <stackable>true</stackable>
    </augBlueprint>
</FTL>
"""
    }
}
