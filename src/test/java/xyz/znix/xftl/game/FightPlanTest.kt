package xyz.znix.xftl.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.ShipShape
import xyz.znix.xftl.ShipShape.LONG
import xyz.znix.xftl.ShipShape.NORMAL
import xyz.znix.xftl.ShipShape.WIDE
import kotlin.random.Random

class FightPlanTest {
    @Test
    fun testWhatFits() {
        // The windows take up at most 3 places, and a wide one takes up two
        assertTrue(FightPlan.fits(listOf(LONG, NORMAL, NORMAL)))
        assertFalse(FightPlan.fits(listOf(WIDE, NORMAL, NORMAL)))
        assertTrue(FightPlan.fits(listOf(LONG, LONG)))
        assertTrue(FightPlan.fits(listOf(LONG, WIDE)))
        assertFalse(FightPlan.fits(listOf(WIDE, WIDE)))
        assertTrue(FightPlan.fits(listOf(LONG, LONG, NORMAL)))
        assertTrue(FightPlan.fits(listOf(LONG, LONG, LONG)))
        assertFalse(FightPlan.fits(listOf(LONG, WIDE, NORMAL)))
    }

    @Test
    fun testPlansOnlyHaveTheAllowedFights() {
        // Sorted, so the order of the ships doesn't matter
        fun key(shapes: List<ShipShape>) = shapes.map { it.name }.sorted().joinToString("+")

        val allowed = setOf(
            listOf(NORMAL), listOf(LONG), listOf(WIDE),
            listOf(NORMAL, NORMAL), listOf(LONG, NORMAL), listOf(WIDE, NORMAL),
            listOf(LONG, LONG), listOf(LONG, WIDE),
            listOf(NORMAL, NORMAL, NORMAL), listOf(LONG, NORMAL, NORMAL),
            listOf(LONG, LONG, NORMAL), listOf(LONG, LONG, LONG),
        ).map(::key).toSet()

        val seen = HashSet<String>()
        val random = Random(1)
        repeat(3000) {
            val plan = FightPlan.plan(random.nextInt(1, 4), 0.5f, random)
            val name = key(plan.shapes)
            assertTrue(name, name in allowed)
            seen += name
        }
        assertEquals(allowed, seen)
    }

    @Test
    fun testWideShipsInFightsWithThreeShips() {
        // A wide ship takes the place of two, so it turns up in fights that
        // would have three ships too
        val random = Random(3)
        val plans = List(2000) { FightPlan.plan(3, FightPlan.BIG_SHIP_CHANCE, random) }
        val wide = plans.count { WIDE in it.shapes }
        assertTrue("$wide", wide > 300)
        for (plan in plans) {
            assertTrue(plan.shapes.toString(), FightPlan.fits(plan.shapes))
            if (WIDE in plan.shapes)
                assertEquals(2, plan.size)
        }
    }

    @Test
    fun testNoBigShipsWithoutTheChance() {
        val random = Random(2)
        repeat(200) {
            assertTrue(FightPlan.plan(3, 0f, random).shapes.all { it == NORMAL })
        }
    }

    @Test
    fun testFightsThroughTheGame() {
        val random = Random(4)
        fun fights(sector: Int, progress: Float) = List(300) { FightPlan.forSector(3, 1, sector, progress, random).shapes }
        fun strength(shapes: List<ShipShape>) = shapes.sumOf { it.strength }

        // The first sector is one ship at a time
        assertTrue(fights(1, 0.5f).all { it == listOf(NORMAL) })

        // The second starts out with one, then two, then one long one or two
        assertTrue(fights(2, 0f).all { it == listOf(NORMAL) })
        assertTrue(fights(2, 0.5f).all { it == listOf(NORMAL, NORMAL) })
        val end = fights(2, 1f).toSet()
        assertEquals(setOf(listOf(NORMAL, NORMAL), listOf(LONG)), end)

        // No wide ships before sector 5, and every fight fits
        for (sector in 1..16) {
            for (progress in listOf(0f, 0.5f, 1f)) {
                for (shapes in fights(sector, progress)) {
                    assertTrue(shapes.toString(), FightPlan.fits(shapes))
                    if (sector < FightPlan.WIDE_FROM_SECTOR)
                        assertFalse("sector $sector: $shapes", WIDE in shapes)
                }
            }
        }
        assertTrue(fights(5, 0f).any { WIDE in it })

        // The fights get stronger through each sector, and from sector to
        // sector (each one starting out a little easier than the last one
        // ended), up to three long ships
        for (progress in listOf(0f, 0.5f, 1f)) {
            var last = 0
            for (sector in 1..16) {
                val s = strength(fights(sector, progress).first())
                assertTrue("sector $sector at $progress: $s after $last", s >= last)
                last = s
            }
        }
        for (sector in 1..16) {
            val parts = listOf(0f, 0.5f, 1f).map { strength(fights(sector, it).first()) }
            assertEquals("sector $sector: $parts", parts.sorted(), parts)
        }
        assertTrue(fights(15, 1f).all { it == listOf(LONG, LONG, LONG) })

        // A player ship that fights fewer ships at once gets fewer, and one
        // that starts out fighting more gets them from the start
        assertTrue(List(100) { FightPlan.forSector(2, 1, 15, 1f, random).shapes }.all { it.size <= 2 })
        assertTrue(List(100) { FightPlan.forSector(2, 2, 1, 0f, random).shapes }.all { it == listOf(NORMAL, NORMAL) })
    }
}
