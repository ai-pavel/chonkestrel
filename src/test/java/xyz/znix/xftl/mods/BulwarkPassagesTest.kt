package xyz.znix.xftl.mods

import org.jdom2.input.SAXBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringReader

/**
 * The walls between the big empty rooms of the mod's bigger enemy ships
 * have a door on every cell (see tools/wide_passages.py), so their bulwarks
 * can walk between them (see doc/game/drone-sizes.md): every pair of rooms
 * side by side that a bulwark can stand in (no system the ship starts with
 * in either, in any ship with that layout, and each at least 2x2) has a passage as wide as
 * the biggest bulwark that fits through, up to 3.
 */
class BulwarkPassagesTest {
    private class Room(val x: Int, val y: Int, val w: Int, val h: Int)

    // The rooms and doors (x, y, vertical) in a layout's .txt
    private fun layout(name: String): Pair<Map<Int, Room>, Set<Triple<Int, Int, Boolean>>> {
        val words = File(MOD_DIR, "data/$name.txt").readText().split(Regex("\\s+"))
        val rooms = HashMap<Int, Room>()
        val doors = HashSet<Triple<Int, Int, Boolean>>()
        for ((i, word) in words.withIndex()) {
            val v = { n: Int -> words[i + n].toInt() }
            when (word) {
                "ROOM" -> rooms[v(1)] = Room(v(2), v(3), v(4), v(5))
                "DOOR" -> doors += Triple(v(1), v(2), v(5) == 1)
            }
        }
        return rooms to doors
    }

    @Test
    fun bulwarkRoomsHavePassagesAsWideAsTheyCan() {
        // The bigger enemies' blueprints, and the rooms with a system they
        // start with in any of those with each layout
        val text = File(MOD_DIR, "data/autoBlueprints.xml.append").readText()
        val root = SAXBuilder().build(StringReader("<root>$text</root>")).rootElement
        val systems = HashMap<String, MutableSet<Int>>()
        for (bp in root.getChildren("shipBlueprint")) {
            val size = bp.getChildTextTrim("shipSize")?.toIntOrNull() ?: 1
            if (size < 2 || bp.getAttributeValue("name").startsWith("PLAYER_SHIP_"))
                continue
            val inRooms = systems.getOrPut(bp.getAttributeValue("layout")) { HashSet() }
            for (system in bp.getChild("systemList").children) {
                if (system.getAttributeValue("start") != "false")
                    system.getAttributeValue("room")?.let { inRooms += it.toInt() }
            }
        }
        assertTrue(systems.size > 100)

        var pairs = 0
        for ((name, withSystems) in systems) {
            val (rooms, doors) = layout(name)
            val bulwarkRooms = rooms.filter { (id, r) -> id !in withSystems && r.w >= 2 && r.h >= 2 }
            for ((a, ra) in bulwarkRooms) for ((b, rb) in bulwarkRooms) {
                // The cells along the wall between them: b to the right of
                // a, or under it
                val vertical = rb.x == ra.x + ra.w
                val along = when {
                    vertical -> (maxOf(ra.y, rb.y) until minOf(ra.y + ra.h, rb.y + rb.h))
                        .map { Triple(rb.x, it, true) }
                    rb.y == ra.y + ra.h -> (maxOf(ra.x, rb.x) until minOf(ra.x + ra.w, rb.x + rb.w))
                        .map { Triple(it, rb.y, false) }
                    else -> emptyList()
                }
                if (along.size < 2)
                    continue
                pairs++
                val need = minOf(3, along.size, ra.w, ra.h, rb.w, rb.h)
                var run = 0
                var longest = 0
                for (cell in along) {
                    run = if (cell in doors) run + 1 else 0
                    longest = maxOf(longest, run)
                }
                assertTrue("$name: rooms $a and $b have a passage $longest wide, not $need", longest >= need)
            }
        }
        assertTrue(pairs > 100)
    }

    companion object {
        private val MOD_DIR = File("mods/chonkestrel")
    }
}
