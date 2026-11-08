package xyz.znix.xftl.game

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.jdom2.Element
import org.junit.Test
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.testkit.InGameTest

class OverCapacityTest {
    @Test
    fun testEveryAugmentWithNoRoomWaitsUntilTheJump() {
        // Augments the ship gets with no room for them all wait in the over
        // capacity box (not just the first), and are left behind on jumping
        val kit = InGameTest(extraBlueprints = listOf(AUGMENTS_XML))
        val player = kit.player
        for (i in 0 until player.maxAugments)
            kit.givePlayerBP("TEST_OVER_AUG_$i")
        kit.givePlayerBP("TEST_OVER_AUG_NEW_1")
        kit.givePlayerBP("TEST_OVER_AUG_NEW_2")

        assertEquals(player.maxAugments, player.augments.size)
        assertEquals(
            listOf("TEST_OVER_AUG_NEW_1", "TEST_OVER_AUG_NEW_2"),
            player.overflowAugments.map(AugmentBlueprint::name)
        )

        // They're kept in a saved game
        val elem = Element("ship")
        val refs = ObjectRefs()
        refs.register(player, "player")
        player.saveToXML(elem, refs)
        val loaded = kit.state.deserialiseSingleShip(elem, RefLoader(), null)
        assertEquals(
            listOf("TEST_OVER_AUG_NEW_1", "TEST_OVER_AUG_NEW_2"),
            loaded.overflowAugments.map(AugmentBlueprint::name)
        )

        player.resetAfterJump()
        assertTrue(player.overflowAugments.isEmpty())
    }

    companion object {
        @Language("XML")
        private val AUGMENTS_XML = "<FTL>" +
                ((0 until 6).map { "TEST_OVER_AUG_$it" } + listOf("TEST_OVER_AUG_NEW_1", "TEST_OVER_AUG_NEW_2"))
                    .joinToString("") {
                        "<augBlueprint name=\"$it\"><cost>10</cost><rarity>1</rarity><stackable>false</stackable></augBlueprint>"
                    } + "</FTL>"
    }
}
