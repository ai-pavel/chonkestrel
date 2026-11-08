package xyz.znix.xftl.game

import xyz.znix.xftl.*
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A boss's ship on the sector map (see [BossManager]), as vanilla draws the
 * flagship: circling the beacon it's at, and when it's about to jump, a wide
 * line to the next one, with the ship flying along it.
 */
class BossMapIcon(private val game: InGameState) {
    private var rotation: Float = (0f..10f).random(VisualRandom)
    private val icon by lazy { game.getImg("img/map/map_icon_boss.png") }

    /** Draw it circling the beacon at [centre]. */
    fun drawCircling(g: Graphics, centre: IPoint) {
        val rotationSpeed = TWO_PI / 20f // Go around every 20 seconds
        rotation += game.renderingDeltaTime * rotationSpeed

        // These offsets are approximate.
        g.pushTransform()
        g.rotate(centre.x.f, centre.y.f, -rotation)
        icon.draw(centre.x - 8, centre.y - 32)
        g.popTransform()
    }

    /** Draw the wide line it'll jump along, from [from] to [to], with it flying along it. */
    fun drawJumpArc(g: Graphics, from: IPoint, to: IPoint) {
        val width = 10f
        val angle = atan2(from.y.f - to.y, from.x.f - to.x)
        val tangentX = cos(angle + PIf / 2f) * width / 2
        val tangentY = sin(angle + PIf / 2f) * width / 2

        g.drawCustomQuads { renderer ->
            renderer.pushVert(from.x + tangentX, from.y + tangentY, Constants.BEACON_LINE_FLAGSHIP)
            renderer.pushVert(from.x - tangentX, from.y - tangentY, Constants.BEACON_LINE_FLAGSHIP)
            renderer.pushVert(to.x - tangentX, to.y - tangentY, Constants.BEACON_LINE_FLAGSHIP)
            renderer.pushVert(to.x + tangentX, to.y + tangentY, Constants.BEACON_LINE_FLAGSHIP)
        }

        // Draw the animated ship on top of it.
        // Note these numbers are approximate.
        val period = 1_500_000_000
        val progress: Float = (System.nanoTime() % period).toFloat() / period
        val movement = 20f + progress * 20f
        val alpha = min(1f, 2f - progress * 2f)

        g.pushTransform()
        g.rotate(from.x.f, from.y.f, Math.toDegrees(angle.toDouble()).toFloat() - 90f)
        icon.draw(from.x - 32f, from.y - 32f - movement, Colour(1f, 1f, 1f, alpha))
        g.popTransform()
    }
}
