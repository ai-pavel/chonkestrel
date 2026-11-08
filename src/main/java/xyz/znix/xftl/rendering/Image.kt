package xyz.znix.xftl.rendering

import org.lwjgl.opengl.GL11
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import kotlin.math.floor

/**
 * A custom image class, whose functions generally match that of [org.newdawn.slick.Image].
 *
 * See it's JavaDoc for descriptions of these methods.
 */
class Image(
    // This is in pixels, relative to the texture pixel size.
    val textureOffsetX: Int,
    val textureOffsetY: Int,

    val width: Int,
    val height: Int,

    val texture: Texture
) : Renderable {

    val imageSize: IPoint get() = ConstPoint(width, height)

    fun draw() {
        draw(0, 0)
    }

    fun draw(pos: IPoint, filter: Colour = Colour.white) {
        draw(pos.x, pos.y, filter)
    }

    @JvmOverloads
    fun draw(x: Int, y: Int, filter: Colour = Colour.white) {
        draw(x.f, y.f, filter)
    }

    fun draw(x: Int, y: Int, alpha: Float) {
        draw(x.f, y.f, alpha)
    }

    override fun draw(x: Float, y: Float) {
        draw(x, y, 1f)
    }

    override fun draw(x: Float, y: Float, filter: Colour) {
        draw(
            x, y,
            x + width, y + height,

            0f, 0f,
            width.f, height.f,

            1f, filter
        )
    }

    fun draw(x: Float, y: Float, alpha: Float) {
        draw(
            x, y,
            x + width, y + height,

            0f, 0f,
            width.f, height.f,

            alpha, Colour.white
        )
    }

    override fun draw(x: Float, y: Float, width: Float, height: Float) {
        draw(x, y, width, height, Colour.white)
    }

    override fun draw(x: Float, y: Float, width: Float, height: Float, filter: Colour) {
        draw(
            x, y,
            x + width, y + height,

            0f, 0f,
            this.width.f, this.height.f,

            1f, filter
        )
    }

    fun draw(x: Float, y: Float, x2: Float, y2: Float, srcx: Float, srcy: Float, srcx2: Float, srcy2: Float) {
        draw(x, y, x2, y2, srcx, srcy, srcx2, srcy2, 1f, Colour.white)
    }

    fun draw(
        x: Float, y: Float,
        x2: Float, y2: Float,
        srcX1: Float, srcY1: Float,
        srcX2: Float, srcY2: Float,
        alpha: Float,
        filter: Colour
    ) {
        val finalAlpha = alpha * filter.a

        Graphics.internalDrawImage(
            this,
            x, y, x2, y2,
            srcX1, srcY1, srcX2, srcY2,
            filter, finalAlpha
        )
    }

    /**
     * Draw this image, made taller by inserting [extraHeight] pixels at [splitY].
     *
     * The inserted part is filled by repeating the [chunkHeight] rows of the
     * image just above [splitY], so this is useful for stretching a window
     * background with a plain section in it.
     */
    fun drawWithInsertedRows(x: Int, y: Int, splitY: Int, chunkHeight: Int, extraHeight: Int) {
        drawWithInsertedSpace(x, y, 0, 0, 0, splitY, chunkHeight, extraHeight)
    }

    /**
     * Draw this image, made wider by inserting [extraWidth] pixels at [splitX].
     *
     * This is the same as [drawWithInsertedRows], but horizontally.
     */
    fun drawWithInsertedColumns(x: Int, y: Int, splitX: Int, chunkWidth: Int, extraWidth: Int) {
        drawWithInsertedSpace(x, y, splitX, chunkWidth, extraWidth, 0, 0, 0)
    }

    /**
     * Draw this image made both wider and taller, by inserting columns and
     * rows as [drawWithInsertedColumns] and [drawWithInsertedRows] do.
     */
    fun drawWithInsertedSpace(
        x: Int, y: Int,
        splitX: Int, chunkWidth: Int, extraWidth: Int,
        splitY: Int, chunkHeight: Int, extraHeight: Int
    ) {
        drawWithInsertions(x, y, listOf(Insertion(splitX, chunkWidth, extraWidth)), listOf(Insertion(splitY, chunkHeight, extraHeight)))
    }

    /**
     * A gap of [extra] pixels added at [split], which is filled by repeating
     * the [chunk] pixels before the split.
     */
    data class Insertion(val split: Int, val chunk: Int, val extra: Int)

    /**
     * Draw this image with any number of column and row insertions. This is
     * the general form of [drawWithInsertedRows] and [drawWithInsertedColumns].
     */
    fun drawWithInsertions(
        x: Int, y: Int, columns: List<Insertion>, rows: List<Insertion>, filter: Colour = Colour.white
    ) {
        // Split each axis into segments of (source start, length, destination offset)
        fun segments(size: Int, insertions: List<Insertion>): List<Triple<Int, Int, Int>> {
            val result = ArrayList<Triple<Int, Int, Int>>()
            var srcPos = 0
            var offset = 0
            for (insertion in insertions.filter { it.extra > 0 }.sortedBy { it.split }) {
                // Everything up to the split
                result += Triple(srcPos, insertion.split - srcPos, srcPos + offset)

                // Then copies of the chunk before it
                var filled = 0
                while (filled < insertion.extra) {
                    val length = minOf(insertion.chunk, insertion.extra - filled)
                    result += Triple(insertion.split - insertion.chunk, length, insertion.split + offset + filled)
                    filled += insertion.chunk
                }

                srcPos = insertion.split
                offset += insertion.extra
            }
            result += Triple(srcPos, size - srcPos, srcPos + offset)
            return result
        }

        val xSegments = segments(width, columns)
        val ySegments = segments(height, rows)
        if (xSegments.size == 1 && ySegments.size == 1) {
            draw(x, y, filter)
            return
        }

        for ((srcX, w, dstX) in xSegments) {
            for ((srcY, h, dstY) in ySegments) {
                val x1 = (x + dstX).f
                val y1 = (y + dstY).f
                draw(x1, y1, x1 + w, y1 + h, srcX.f, srcY.f, (srcX + w).f, (srcY + h).f, 1f, filter)
            }
        }
    }

    fun drawAlignedCentred(centreX: Int, centreY: Int, filter: Colour = Colour.white) {
        drawAlignedCentred(centreX, centreY, width.f, height.f, filter)
    }

    fun drawAlignedCentred(centreX: Int, centreY: Int, width: Float, height: Float, filter: Colour = Colour.white) {
        draw(
            centreX - floor(width / 2f), centreY - floor(height / 2f),
            width, height,
            filter
        )
    }

    fun drawSection(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        offsetX: Int = 0,
        offsetY: Int = 0,
        stretchX: Int? = null,
        stretchY: Int? = null,
        colour: Colour? = null
    ) {
        val screenWidth = stretchX ?: width
        val screenHeight = stretchY ?: height

        draw(
            x.f, y.f, x.f + screenWidth, y.f + screenHeight,
            offsetX.f, offsetY.f, offsetX + width.f, offsetY + height.f,
            1f, colour ?: Colour.white
        )
    }

    fun getSubImage(x: Int, y: Int, width: Int, height: Int): Image {
        return Image(
            textureOffsetX + x,
            textureOffsetY + y,
            width, height,
            texture
        )
    }

    companion object {
        // While nearest filtering kinda sucks for rotation, it's what vanilla
        // uses for that - anything else looks terrible because the images have
        // arbitrary colours on transparent regions, so artefacts appear around
        // edges.
        // Linear scaling also looks bad on high-DPI displays, as the game goes
        // from being pixel art to a blurry mess.
        const val DEFAULT_TEXTURE_FILTERING = GL11.GL_NEAREST
    }
}
