package xyz.znix.xftl.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.testkit.InGameTest

/**
 * The rewards of a fight with several ships (see FleetRewards): an event that
 * does more than give resources (a choice, say) is kept to show once, and
 * the other ships' copies of it only give their resources.
 */
class FleetRewardsTest {
    private val kit = InGameTest(extraEvents = listOf(EVENTS))
    private val rewards = FleetRewards(kit.state)

    private fun event(name: String) = kit.state.eventManager[name] as Event

    @Test
    fun plainRewardsWait() {
        assertTrue(rewards.add(kit.player, event("PLAIN_REWARD")))
        assertFalse(rewards.isEmpty)
        assertNull(rewards.takeSpecial())
    }

    @Test
    fun aSpecialEventIsShownOnce() {
        val special = event("SPECIAL_REWARD")
        assertFalse(rewards.add(kit.player, special))

        // The first is kept to show, the others' only give their resources
        rewards.deferSpecial(kit.player, special)
        rewards.deferSpecial(kit.player, special)
        rewards.deferSpecial(kit.player, special)
        assertEquals(special, rewards.takeSpecial()?.second)
        assertNull(rewards.takeSpecial())
        assertFalse(rewards.isEmpty)
    }

    companion object {
        private const val EVENTS = """
<FTL>
    <event name="PLAIN_REWARD">
        <text>The ship breaks apart.</text>
        <item_modify><item type="scrap" min="10" max="10"/></item_modify>
    </event>
    <event name="SPECIAL_REWARD">
        <text>The ship breaks apart. You hasten to contact the civilian ship.</text>
        <item_modify><item type="scrap" min="10" max="10"/></item_modify>
        <choice><text>Contact the civilian ship.</text><event/></choice>
    </event>
</FTL>
"""
    }
}
