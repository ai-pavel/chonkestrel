package xyz.znix.xftl.rendering

import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.newdawn.slick.opengl.ImageDataFactory
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.FTLFile
import xyz.znix.xftl.sys.INativeResource
import xyz.znix.xftl.sys.ResourceContext

/**
 * Represents a GLFW mouse cursor.
 */
class Cursor(
    context: ResourceContext,
    df: Datafile,
    val mainFile: FTLFile,
    overlayFile: FTLFile?,

    /**
     * For a strip of square frames (the door cursor's animation), the frame
     * to use, or null to use the whole image.
     */
    frame: Int? = null
) : INativeResource {
    override var freed: Boolean = false
        private set

    private var cursorPtr: Long = MemoryUtil.NULL

    init {
        val imageData = ImageDataFactory.getImageDataFor(mainFile.name)
        df.open(mainFile).use { imageData.loadImage(it, false, null) }

        // Overlay the second image on top, which is required for the weapon cursors
        if (overlayFile != null) {
            val overlay = ImageDataFactory.getImageDataFor(overlayFile.name)
            df.open(overlayFile).use { overlay.loadImage(it, false, null) }

            check(overlay.width == imageData.width)
            check(overlay.height == imageData.height)
            val overlayData = overlay.imageBufferData
            val pixels = imageData.imageBufferData

            for (y in 0 until imageData.height) {
                for (x in 0 until imageData.width) {
                    val idx = (y * imageData.width + x) * 4

                    // Is alpha=0
                    if (overlayData.get(idx + 3) == 0.toByte())
                        continue

                    pixels.put(idx, overlayData.get(idx)) // R
                    pixels.put(idx + 1, overlayData.get(idx + 1)) // G
                    pixels.put(idx + 2, overlayData.get(idx + 2)) // B
                    pixels.put(idx + 3, overlayData.get(idx + 3)) // A
                }
            }
        }

        MemoryStack.stackPush().use { stack ->
            val imageArray = GLFWImage.Buffer(stack.malloc(GLFWImage.SIZEOF))
            val image = imageArray[0]

            if (frame == null) {
                image.set(imageData.width, imageData.height, imageData.imageBufferData)
            } else {
                // Copy the frame out of the strip, row by row. Its rows are
                // the texture's width apart (padded to a power of two), not
                // the image's: a 160 pixel strip's are 256 apart.
                val size = imageData.height
                val stride = imageData.texWidth
                val pixels = imageData.imageBufferData
                val cropped = stack.malloc(size * size * 4)
                for (y in 0 until size) {
                    for (i in 0 until size * 4) {
                        cropped.put(y * size * 4 + i, pixels.get((y * stride + frame * size) * 4 + i))
                    }
                }
                image.set(size, size, cropped)
            }

            cursorPtr = GLFW.glfwCreateCursor(image, 0, 0)
            check(cursorPtr != MemoryUtil.NULL) { "Failed to create mouse cursor '${mainFile.name}'" }
        }

        context.register(this)
    }

    fun setActive(glfwWindow: Long) {
        GLFW.glfwSetCursor(glfwWindow, cursorPtr)
    }

    override fun free() {
        check(!freed) { "Cannot double-free cursor '${mainFile.name}'" }
        freed = true

        // If the cursor is active for a window, it reverts to the default cursor.
        GLFW.glfwDestroyCursor(cursorPtr)
    }
}
