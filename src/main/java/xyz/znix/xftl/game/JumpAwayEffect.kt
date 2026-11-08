package xyz.znix.xftl.game

import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Image

/**
 * An enemy ship jumping away (escaping with its FTL drive): its hull
 * stretches out ahead of it and fades, where its window was. It's only
 * for show, as the ship's already gone from the fight (see
 * InGameState.removeOpponent).
 */
class JumpAwayEffect(private val hull: Image, hullPos: IPoint) {
    private val pos = ConstPoint(hullPos)
    private var time = 0f

    val isFinished: Boolean get() = time >= DURATION

    fun update(dt: Float) {
        time += dt
    }

    fun render() {
        if (isFinished)
            return

        // It flashes white as the drive kicks in, then streaks off forwards
        // (up, the way the enemy ships point in their windows): its bottom
        // rises by half as much as it grows, so its nose races ahead
        val progress = time / DURATION
        val stretch = 1f + STRETCH * progress * progress
        val height = hull.height * stretch
        val width = hull.width * (1f - SQUASH * progress)
        val x = pos.x + (hull.width - width) / 2f
        val y = pos.y - (height - hull.height) * 1.5f
        val alpha = 1f - progress
        hull.draw(x, y, width, height, Colour(1f, 1f, 1f, alpha))

        // The white flash, fading quickly
        val flash = (1f - progress / FLASH_PART).coerceIn(0f, 1f)
        if (flash > 0f) {
            for (i in 1..2)
                hull.draw(x, y, width, height, Colour(1f, 1f, 1f, flash * alpha))
        }
    }

    companion object {
        // How long it takes, how many times its length it stretches to, how
        // much narrower it gets, and how much of it the white flash takes
        private const val DURATION = 0.8f
        private const val STRETCH = 3f
        private const val SQUASH = 0.6f
        private const val FLASH_PART = 0.35f
    }
}
