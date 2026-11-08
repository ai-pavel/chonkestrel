package xyz.znix.xftl.rendering

import org.lwjgl.opengl.GL30.*

class BulkColourRenderer(val drawType: Int) : BulkRenderer() {
    init {
        glBindVertexArray(getOrCreateVAO())

        glEnableVertexAttribArray(posAttrib)
        glEnableVertexAttribArray(colourAttrib)

        glBindVertexArray(0)
    }

    fun pushVert(
        x: Float, y: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        checkSize(6 * 4) // 32-bit floats

        // Transform our position to reflect the Graphics translate calls.
        // 3x3 matrix multiply, with (baseX,baseY,1)
        val m = Graphics.getTextureTransformMatrix()
        val transformedX = x * m.m00 + y * m.m01 + m.m02
        val transformedY = x * m.m10 + y * m.m11 + m.m12
        // Don't need to calculate a W value

        data.putFloat(transformedX)
        data.putFloat(transformedY)

        data.putFloat(r)
        data.putFloat(g)
        data.putFloat(b)
        data.putFloat(a)

        numVerts++
    }

    fun pushVert(x: Float, y: Float, colour: Colour) {
        pushVert(
            x, y,
            colour.r, colour.g, colour.b, colour.a
        )
    }

    fun flush() {
        if (numVerts == 0)
            return

        glBindVertexArray(getOrCreateVAO())

        glBindBuffer(GL_ARRAY_BUFFER, getOrCreateVBO())
        data.flip()
        RenderStats.bytesUploaded += data.remaining()
        glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW)

        // Packed as position then colour
        val stride = 4 * (2 + 4)
        glVertexAttribPointer(posAttrib, 2, GL_FLOAT, false, stride, 0)
        glVertexAttribPointer(colourAttrib, 4, GL_FLOAT, false, stride, 2 * 4)

        glUseProgram(shader.handle)

        // Set the transform uniform
        updateTransformMatrix(posTransformLoc)

        if (drawType == GL_QUADS) {
            // Quads isn't supported in OpenGL core, so build
            // a quad-to-triangle indices buffer.
            buildQuadIndices()
            indices.flip()

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, getOrCreateEBO())
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STREAM_DRAW)

            glDrawElements(GL_TRIANGLES, indices.remaining() / 4, GL_UNSIGNED_INT, 0)
        } else {
            glDrawArrays(drawType, 0, numVerts)
        }
        RenderStats.drawCalls++

        // Cleanup
        glUseProgram(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)

        // Clear out the buffer for subsequent renders
        data.clear()
        numVerts = 0
    }

    companion object {
        private val shader by lazy { ShaderProgramme("shaders/colour_vert.glsl", "shaders/colour_frag.glsl") }

        private val posAttrib: Int by lazy { shader.getAttributeLocation("pos") }
        private val colourAttrib: Int by lazy { shader.getAttributeLocation("colour") }
        private val posTransformLoc: Int by lazy { shader.getUniformLocation("posTransform") }
    }
}
