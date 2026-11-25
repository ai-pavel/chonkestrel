package xyz.znix.xftl.math

class Rectangle(val pos: IPoint, val size: IPoint) {
    val centre get() = pos + size.divideTruncate(2f)

    /** Whether ([x], [y]) is inside this rectangle (its right and bottom edges aren't). */
    fun contains(x: Int, y: Int): Boolean =
        x >= pos.x && y >= pos.y && x < pos.x + size.x && y < pos.y + size.y
}
