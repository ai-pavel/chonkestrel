package xyz.znix.xftl.rendering

import org.lwjgl.opengl.GL30.*
import xyz.znix.xftl.f

/**
 * Instances of this class accumulate a bunch of stuff
 * to render, and then send it to the graphics API when flushed.
 *
 * This can be used to improve rendering performance for
 * stuff like text where there's otherwise a silly number
 * of draw calls.
 */
class BulkImageRenderer : BulkRenderer() {
    var imageFiltering: Int = Image.DEFAULT_TEXTURE_FILTERING

    private val transformMatData = generateBuffer(4 * 9).asFloatBuffer()

    init {
        glBindVertexArray(getOrCreateVAO())

        // Packed as position then UV
        glEnableVertexAttribArray(posAttrib)
        glEnableVertexAttribArray(uvAttrib)
        glEnableVertexAttribArray(colourAttrib)

        glBindVertexArray(0)
    }

    fun pushImage(
        x1: Float, y1: Float, // Corner A on-screen
        x2: Float, y2: Float, // Corner B on-screen
        u1: Float, v1: Float, // Corner A in-texture
        u2: Float, v2: Float, // Corner B in-texture
        colour: Colour
    ) {
        pushImage(
            x1, y1, x2, y2,
            u1, v1, u2, v2,
            colour.r, colour.g, colour.b, colour.a
        )
    }

    fun pushImage(
        x1: Float, y1: Float, // Corner A on-screen
        x2: Float, y2: Float, // Corner B on-screen
        u1: Float, v1: Float, // Corner A in-texture
        u2: Float, v2: Float, // Corner B in-texture
        r: Float, g: Float, b: Float, a: Float // Modulate colour
    ) {
        pushVert(x1, y1, u1, v1, r, g, b, a)
        pushVert(x2, y2, u2, v2, r, g, b, a)
        pushVert(x2, y1, u2, v1, r, g, b, a)

        pushVert(x1, y1, u1, v1, r, g, b, a)
        pushVert(x1, y2, u1, v2, r, g, b, a)
        pushVert(x2, y2, u2, v2, r, g, b, a)
    }

    fun pushVert(x: Float, y: Float, u: Float, v: Float) {
        pushVert(x, y, u, v, 1f, 1f, 1f, 1f)
    }

    fun pushVert(
        x: Float, y: Float,
        u: Float, v: Float,
        r: Float, g: Float, b: Float, a: Float // Modulate colour
    ) {
        checkSize(8 * 4) // 8 32-bit floats

        // Transform our position to reflect the Graphics translate calls.
        // 3x3 matrix multiply, with (baseX,baseY,1)
        val m = Graphics.getTextureTransformMatrix()
        val transformedX = x * m.m00 + y * m.m01 + m.m02
        val transformedY = x * m.m10 + y * m.m11 + m.m12
        // Don't need to calculate a W value

        data.putFloat(transformedX)
        data.putFloat(transformedY)
        data.putFloat(u)
        data.putFloat(v)

        data.putFloat(r)
        data.putFloat(g)
        data.putFloat(b)
        data.putFloat(a)

        numVerts++
    }

    fun flush(image: Image) {
        if (numVerts == 0)
            return

        image.texture.bind(imageFiltering) // Binds to GL_TEXTURE_2D

        glBindVertexArray(getOrCreateVAO())

        glBindBuffer(GL_ARRAY_BUFFER, getOrCreateVBO())
        data.flip()
        RenderStats.bytesUploaded += data.remaining()
        glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW)

        glVertexAttribPointer(posAttrib, 2, GL_FLOAT, false, 32, 0)
        glVertexAttribPointer(uvAttrib, 2, GL_FLOAT, false, 32, 8)
        glVertexAttribPointer(colourAttrib, 4, GL_FLOAT, false, 32, 16)

        glUseProgram(shader.handle)

        // Set the transform uniforms
        updateTransformMatrix(posTransformLoc)
        updateUvTransformMatrix(image)

        glDrawArrays(GL_TRIANGLES, 0, numVerts)
        RenderStats.drawCalls++

        // Cleanup
        glUseProgram(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)

        // Clear out the buffer for subsequent renders
        data.clear()
        numVerts = 0

        return
    }

    private fun updateUvTransformMatrix(image: Image) {
        // UVs are 0-1 in both axes.

        transformMatData.clear()

        transformMatData.put(1f / image.texture.rawTextureWidth)
        transformMatData.put(0f)
        transformMatData.put(0f)

        transformMatData.put(0f)
        transformMatData.put(1f / image.texture.rawTextureHeight)
        transformMatData.put(0f)

        // Transform column, 1 is multiplied into this in the shader.
        transformMatData.put(image.textureOffsetX.f / image.texture.rawTextureWidth)
        transformMatData.put(image.textureOffsetY.f / image.texture.rawTextureHeight)
        transformMatData.put(0f)

        transformMatData.flip()
        glUniformMatrix3fv(uvTransformLoc, false, transformMatData)
    }

    companion object {
        private val shader by lazy {
            ShaderProgramme("shaders/image_rect_vert.glsl", "shaders/image_rect_frag.glsl")
        }

        private val posAttrib: Int by lazy { shader.getAttributeLocation("pos") }
        private val uvAttrib: Int by lazy { shader.getAttributeLocation("uv") }
        private val colourAttrib: Int by lazy { shader.getAttributeLocation("filterColour") }
        private val posTransformLoc: Int by lazy { shader.getUniformLocation("posTransform") }
        private val uvTransformLoc: Int by lazy { shader.getUniformLocation("uvTransform") }
    }
}
