package xyz.znix.xftl.sys

import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.*
import org.lwjgl.system.MemoryStack
import org.newdawn.slick.opengl.ImageData
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.rendering.TextureLoader
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders a line of text from a TrueType font into an image, at the
 * screen's resolution, so it's sharp at any size.
 *
 * This is for the ftl.dat selection screen, where we don't have FTL's fonts,
 * and the baked Roboto font would be blurry or pixelated if scaled up.
 */
class TrueTypeText(resource: String) {
    // stb_truetype reads the font from this, so it must stay alive
    private val fontData: ByteBuffer
    private val info = STBTTFontinfo.create()

    init {
        val bytes = javaClass.classLoader.getResourceAsStream(resource)?.use { it.readAllBytes() }
            ?: error("Missing font: $resource")
        fontData = BufferUtils.createByteBuffer(bytes.size).put(bytes).flip()
        check(stbtt_InitFont(info, fontData)) { "Invalid font: $resource" }
    }

    /**
     * Render [text], [pixelHeight] pixels tall (from the top of the tallest
     * letters to the bottom of the lowest). The text is white, so draw it
     * with a colour filter to colour it.
     */
    fun render(context: ResourceContext, text: String, pixelHeight: Float): Image {
        val scale = stbtt_ScaleForPixelHeight(info, pixelHeight)

        MemoryStack.stackPush().use { stack ->
            val ascent = stack.mallocInt(1)
            val descent = stack.mallocInt(1)
            val lineGap = stack.mallocInt(1)
            stbtt_GetFontVMetrics(info, ascent, descent, lineGap)
            val baseline = (ascent[0] * scale).roundToInt()
            val height = ceil((ascent[0] - descent[0]) * scale).toInt().coerceAtLeast(1)

            // Where each letter goes along the line, with kerning
            val positions = FloatArray(text.length)
            var x = 0f
            val advance = stack.mallocInt(1)
            val bearing = stack.mallocInt(1)
            for ((i, ch) in text.withIndex()) {
                positions[i] = x
                stbtt_GetCodepointHMetrics(info, ch.code, advance, bearing)
                x += advance[0] * scale
                if (i + 1 < text.length)
                    x += stbtt_GetCodepointKernAdvance(info, ch.code, text[i + 1].code) * scale
            }
            val width = ceil(x).toInt().coerceAtLeast(1) + 2

            // Draw each letter into its own bitmap, then combine them, since
            // kerned letters can overlap.
            val alpha = ByteArray(width * height)
            val x0 = stack.mallocInt(1)
            val y0 = stack.mallocInt(1)
            val x1 = stack.mallocInt(1)
            val y1 = stack.mallocInt(1)
            for ((i, ch) in text.withIndex()) {
                val shiftX = positions[i] - floor(positions[i])
                stbtt_GetCodepointBitmapBoxSubpixel(info, ch.code, scale, scale, shiftX, 0f, x0, y0, x1, y1)
                val glyphWidth = x1[0] - x0[0]
                val glyphHeight = y1[0] - y0[0]
                if (glyphWidth <= 0 || glyphHeight <= 0)
                    continue

                val glyph = BufferUtils.createByteBuffer(glyphWidth * glyphHeight)
                stbtt_MakeCodepointBitmapSubpixel(
                    info, glyph, glyphWidth, glyphHeight, glyphWidth,
                    scale, scale, shiftX, 0f, ch.code
                )

                val left = floor(positions[i]).toInt() + x0[0] + 1
                val top = baseline + y0[0]
                for (gy in 0 until glyphHeight) {
                    val py = top + gy
                    if (py !in 0 until height)
                        continue
                    for (gx in 0 until glyphWidth) {
                        val px = left + gx
                        if (px !in 0 until width)
                            continue
                        val value = glyph.get(gy * glyphWidth + gx).toInt() and 0xff
                        val index = py * width + px
                        alpha[index] = max(alpha[index].toInt() and 0xff, value).toByte()
                    }
                }
            }

            return TextureLoader.loadImage(context, AlphaImage(width, height, alpha))
        }
    }

    /**
     * White pixels with the given alpha, padded out to a power-of-two
     * texture.
     */
    private class AlphaImage(private val w: Int, private val h: Int, alpha: ByteArray) : ImageData {
        private val texW = nextPowerOfTwo(w)
        private val texH = nextPowerOfTwo(h)
        private val data: ByteBuffer = BufferUtils.createByteBuffer(texW * texH * 4)

        init {
            for (y in 0 until h) {
                data.position(y * texW * 4)
                for (x in 0 until w) {
                    data.put(-1).put(-1).put(-1)
                    data.put(alpha[y * w + x])
                }
            }
            data.position(0)
        }

        override fun getWidth(): Int = w
        override fun getHeight(): Int = h
        override fun getTexWidth(): Int = texW
        override fun getTexHeight(): Int = texH
        override fun getDepth(): Int = 32
        override fun getImageBufferData(): ByteBuffer = data

        private fun nextPowerOfTwo(value: Int): Int {
            var result = 1
            while (result < value)
                result *= 2
            return result
        }
    }
}
