package xyz.znix.xftl

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which of the player's sensors systems look at the enemy ships, when there
 * are more of them than ships (see Ship.sensorsFor): the best ones, in
 * their order, so a manned one isn't left looking at nothing.
 */
class SensorsWatchingTest {
    // A sensors system, by its name, and its power (with manning)
    private data class Sensor(val name: String, val power: Int)

    private fun watching(sensors: List<Sensor>, ships: Int) = Ship.bestInOrder(sensors, ships) { it.power }.map { it.name }

    @Test
    fun testTheBestOnesWatch() {
        // Three sensors systems at level 2, the first and third manned (3),
        // against two ships: the manned ones watch them, left to right
        val sensors = listOf(Sensor("first", 3), Sensor("second", 2), Sensor("third", 3))
        assertEquals(listOf("first", "third"), watching(sensors, 2))

        // Against one ship, the first of the best
        assertEquals(listOf("first"), watching(sensors, 1))

        // Against three or more, all of them, in order
        assertEquals(listOf("first", "second", "third"), watching(sensors, 3))
        assertEquals(listOf("first", "second", "third"), watching(sensors, 4))
    }

    @Test
    fun testEquallyGoodOnesWatchInOrder() {
        // As before the best ones were picked: the first ones
        val sensors = listOf(Sensor("first", 2), Sensor("second", 2), Sensor("third", 2))
        assertEquals(listOf("first", "second"), watching(sensors, 2))

        // A better one takes the place of the last of them, keeping the order
        val upgraded = listOf(Sensor("first", 2), Sensor("second", 2), Sensor("third", 4))
        assertEquals(listOf("first", "third"), watching(upgraded, 2))
        val first = listOf(Sensor("first", 1), Sensor("second", 2), Sensor("third", 2))
        assertEquals(listOf("second", "third"), watching(first, 2))
    }
}
