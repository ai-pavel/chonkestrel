package xyz.znix.xftl.crew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest
import kotlin.random.Random

class CrewNamePresetTest {
    private val df = InGameTest().state.content.datafile

    @Test
    fun testPresets() {
        for (preset in CrewNameManager.PRESETS) {
            val manager = CrewNameManager(df, "en", preset)
            val male = manager.names.filter { it.isMale }.flatMap { it.names }
            val female = manager.names.filter { !it.isMale }.flatMap { it.names }

            assertEquals(preset, 100, male.size)
            assertEquals(preset, 100, female.size)
            assertEquals(preset, (male + female).size, (male + female).toSet().size)

            // Short enough to fit in the UI
            assertTrue(preset, (male + female).all { it.isNotBlank() && it.length <= 14 })

            // And names are picked from the preset
            val name = manager.getForGender(true, Random(1))
            assertTrue("$preset: $name", name in male)
        }
    }

    @Test
    fun testAllPresets() {
        val all = CrewNameManager(df, "en", CrewNameManager.ALL_PRESET)
        val names = all.names.flatMap { it.names }.toSet()

        // Every preset's names are in it
        for (preset in CrewNameManager.PRESETS) {
            val presetNames = CrewNameManager(df, "en", preset).names.flatMap { it.names }
            assertTrue(preset, names.containsAll(presetNames))
        }

        // And names come from lots of presets, not just one
        val picked = (0 until 200).map { all.getForGender(it % 2 == 0, Random(it)) }.toSet()
        val presetsPicked = CrewNameManager.PRESETS.count { preset ->
            CrewNameManager(df, "en", preset).names.flatMap { it.names }.any { it in picked }
        }
        assertTrue(presetsPicked > CrewNameManager.PRESETS.size / 2)
    }

    @Test
    fun testDefaultIsTheGamesNames() {
        val default = CrewNameManager(df, "en", CrewNameManager.DEFAULT_PRESET)
        val unknown = CrewNameManager(df, "en", "no-such-preset")
        assertEquals(default.names.map { it.names }, unknown.names.map { it.names })
    }
}
