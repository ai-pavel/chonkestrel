package xyz.znix.xftl.sector

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest
import kotlin.random.Random

/**
 * Check the xftlScale="size" event extension.
 */
class EventScaleTest {
    private fun resolve(playerShip: String, event: String): xyz.znix.xftl.game.ResourceSet {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), extraEvents = listOf(EVENT_XML), playerShip = playerShip)
        return (kit.state.eventManager[event] as Event).resolveResources(kit.state, Random(0))
    }

    @Test
    fun testScaledBySize() {
        val vanilla = resolve("TEST_SHIP", "SCALED")
        assertEquals(10, vanilla.scrap)
        assertEquals(-2, vanilla.fuel)
        assertEquals(listOf(3, -5), vanilla.damage.map { it.amount })

        val big = resolve("BIG_SHIP", "SCALED")
        assertEquals(20, big.scrap)
        assertEquals(-4, big.fuel)
        assertEquals(listOf(6, -10), big.damage.map { it.amount })
    }

    @Test
    fun testNotScaledByDefault() {
        val big = resolve("BIG_SHIP", "UNSCALED")
        assertEquals(10, big.scrap)
        assertEquals(listOf(3), big.damage.map { it.amount })
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="BIG_SHIP" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <shipSize>2</shipSize>
        <systemList>
            <oxygen power="1" max="1" room="0"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""

        @Language("XML")
        private const val EVENT_XML = """
<FTL>
    <event name="SCALED">
        <item_modify xftlScale="size">
            <item type="scrap" min="10" max="10"/>
            <item type="fuel" min="-2" max="-2"/>
        </item_modify>
        <damage amount="3" xftlScale="size"/>
        <damage amount="-5" xftlScale="size"/>
    </event>
    <event name="UNSCALED">
        <item_modify>
            <item type="scrap" min="10" max="10"/>
        </item_modify>
        <damage amount="3"/>
    </event>
</FTL>
"""
    }
}
