package xyz.znix.xftl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

class BrokenBlueprintTest {
    /**
     * A blueprint missing a field (here, a drone without its power, as a
     * player's game data had) is skipped, rather than the game not starting.
     */
    @Test
    fun testBrokenBlueprintIsSkipped() {
        val kit = InGameTest(extraBlueprints = listOf("""<FTL>
            <droneBlueprint name="TEST_BROKEN_DRONE"><type>COMBAT</type></droneBlueprint>
            <droneBlueprint name="TEST_GOOD_DRONE"><type>COMBAT</type><power>2</power></droneBlueprint>
        </FTL>"""))
        val blueprints = kit.state.blueprintManager
        assertFalse(blueprints.blueprints.containsKey("TEST_BROKEN_DRONE"))
        assertTrue(blueprints.blueprints.containsKey("TEST_GOOD_DRONE"))
    }
}
