package xyz.znix.xftl.devutil

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import xyz.znix.xftl.XftlDebug
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.hangar.SelectShipState
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.LWJGLGameContainer
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Renders the default ship (the Chonkestrel), as it looks in the hangar, into
 * a PNG with a transparent background, then quits. This is for the game's
 * icon (see tools/release/make_icon.py). Turn it on with XFTL_ICON (see
 * [XftlDebug]).
 *
 * The game can't draw onto a transparent background, so the ship is drawn
 * twice, on black and on white, and each pixel's transparency is worked out
 * from how much it differs between them.
 */
class IconDirector(private val main: MainGame, private val container: LWJGLGameContainer, private val file: File) {
    private var frames = 0
    private var finished = false

    fun afterRender(framebufferWidth: Int, framebufferHeight: Int) {
        if (finished)
            return
        val hangar = main.currentState as? SelectShipState ?: return

        // Wait for the hangar's images to load
        if (++frames < SETTLE_FRAMES)
            return
        finished = true

        val ship = hangar.blueprints.defaultPlayerShip
        val onBlack = render(hangar, ship, Colour.black, framebufferWidth, framebufferHeight)
        val onWhite = render(hangar, ship, Colour.white, framebufferWidth, framebufferHeight)

        file.absoluteFile.parentFile?.mkdirs()
        ImageIO.write(combine(onBlack, onWhite, framebufferWidth, framebufferHeight), "png", file)
        println("[icon] Saved $ship to ${file.absolutePath}")

        // Leave the frame on black, rather than half-drawn
        main.quitGame()
    }

    private fun render(hangar: SelectShipState, ship: String, background: Colour, width: Int, height: Int): ByteBuffer {
        val g = container.graphics
        g.clear(background)
        g.pushTransform()
        g.translate(MARGIN, MARGIN)
        hangar.drawShipOnly(g, ship)
        g.popTransform()
        GL11.glFinish()

        val pixels = BufferUtils.createByteBuffer(width * height * 4)
        GL11.glReadBuffer(GL11.GL_BACK)
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
        return pixels
    }

    companion object {
        // How many frames to wait for, and the gap round the ship
        private const val SETTLE_FRAMES = 30
        private const val MARGIN = 40f

        @JvmStatic
        fun install(main: MainGame, container: GameContainer) {
            val path = XftlDebug.iconFile ?: return
            val lwjgl = container as? LWJGLGameContainer ?: return
            lwjgl.renderWhenUnfocused = true
            lwjgl.afterRender = IconDirector(main, lwjgl, File(path))::afterRender
        }

        /**
         * Work out the ship's colours and transparency from it drawn on black
         * and on white, cropped to the ship. A pixel the ship fully covers is
         * the same on both, and one it doesn't cover is black on one and
         * white on the other.
         */
        fun combine(onBlack: ByteBuffer, onWhite: ByteBuffer, width: Int, height: Int): BufferedImage {
            val argb = IntArray(width * height)
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            for (y in 0 until height) {
                // OpenGL's rows go from the bottom up
                val row = (height - 1 - y) * width * 4
                for (x in 0 until width) {
                    val i = row + x * 4
                    var difference = 0
                    for (c in 0..2)
                        difference += (onWhite.get(i + c).toInt() and 0xff) - (onBlack.get(i + c).toInt() and 0xff)
                    val alpha = (255 - difference / 3f).coerceIn(0f, 255f)
                    if (alpha < 1f)
                        continue

                    var colour = alpha.roundToInt() shl 24
                    for (c in 0..2) {
                        // On black, the colour's been multiplied by the alpha
                        val value = (onBlack.get(i + c).toInt() and 0xff) * 255f / alpha
                        colour = colour or (value.roundToInt().coerceIn(0, 255) shl (16 - c * 8))
                    }
                    argb[y * width + x] = colour
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }

            check(maxX >= 0) { "Nothing was drawn" }
            val image = BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB)
            for (y in minY..maxY) {
                for (x in minX..maxX)
                    image.setRGB(x - minX, y - minY, argb[y * width + x])
            }
            return image
        }
    }
}
