package xyz.znix.xftl.rendering

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * FTL only has numbered placed-crosshair images (`crosshairs_placed1-4.png`)
 * for the first four weapons. This draws the numbers for weapons 5-8 (on
 * ships with more weapon slots), in the same pixel style.
 *
 * The digits are drawn over the unnumbered crosshairs image, and are
 * positioned relative to a frame the size of the numbered images.
 */
object CrosshairNumbers {
    /**
     * The size of the numbered crosshairs images.
     */
    const val FRAME_SIZE = 54

    // In the vanilla images, the digits go around the crosshair, so they
    // don't overlap when several weapons target the same room: '1' is
    // below it (at 89 degrees, with y pointing down), and each one after
    // is about 26 degrees further anticlockwise, ending with '4' at its
    // right. The digits' centres are about 21px from the crosshair's
    // centre. Weapons 5-8 carry on around the circle, ending at the top.
    private const val FIRST_ANGLE = 88.7
    private const val ANGLE_STEP = -26.3
    private const val RADIUS = 21.0
    private const val FRAME_CENTRE = FRAME_SIZE / 2.0

    private const val DIGIT_WIDTH = 5
    private const val DIGIT_HEIGHT = 6

    private val FILL = Colour(0xf0 / 255f, 0x40 / 255f, 0x40 / 255f, 0.75f)

    // For weapons on autofire, like the yellow crosshairs images
    private val AUTOFIRE_FILL = Colour(0xff / 255f, 0xe1 / 255f, 0x64 / 255f, 0.75f)
    private val OUTLINE = Colour(0f, 0f, 0f, 0.75f)

    // 5x6 pixel digits, in the style of the vanilla '4'.
    private val DIGITS = mapOf(
        5 to listOf(
            "#####",
            "#....",
            "####.",
            "....#",
            "#...#",
            ".###.",
        ),
        6 to listOf(
            ".###.",
            "#....",
            "####.",
            "#...#",
            "#...#",
            ".###.",
        ),
        7 to listOf(
            "#####",
            "....#",
            "...#.",
            "..#..",
            "..#..",
            "..#..",
        ),
        8 to listOf(
            ".###.",
            "#...#",
            ".###.",
            "#...#",
            "#...#",
            ".###.",
        ),
    )

    fun hasNumber(number: Int): Boolean = number in DIGITS

    /**
     * Draw [number] for a crosshair whose frame's top-left is at [frameX],[frameY],
     * in yellow for a weapon on [autofire].
     */
    fun drawNumber(g: Graphics, frameX: Int, frameY: Int, number: Int, autofire: Boolean = false) {
        val digit = DIGITS[number] ?: return

        val pixels = HashSet<Pair<Int, Int>>()
        for ((y, row) in digit.withIndex()) {
            for ((x, c) in row.withIndex()) {
                if (c == '#')
                    pixels += Pair(x, y)
            }
        }

        // The outline is every pixel touching (including diagonally) the
        // digit, that isn't part of the digit itself.
        val outline = HashSet<Pair<Int, Int>>()
        for ((x, y) in pixels) {
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val point = Pair(x + dx, y + dy)
                    if (point !in pixels)
                        outline += point
                }
            }
        }

        val angle = Math.toRadians(FIRST_ANGLE + ANGLE_STEP * (number - 1))
        val centreX = FRAME_CENTRE + RADIUS * cos(angle)
        val centreY = FRAME_CENTRE + RADIUS * sin(angle)
        val baseX = frameX + (centreX - DIGIT_WIDTH / 2.0).roundToInt()
        val baseY = frameY + (centreY - DIGIT_HEIGHT / 2.0).roundToInt()

        g.colour = OUTLINE
        for ((x, y) in outline) {
            g.fillRect(baseX + x, baseY + y, 1, 1)
        }

        g.colour = if (autofire) AUTOFIRE_FILL else FILL
        for ((x, y) in pixels) {
            g.fillRect(baseX + x, baseY + y, 1, 1)
        }
    }
}
