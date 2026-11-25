package xyz.znix.xftl.rendering

import xyz.znix.xftl.Datafile
import xyz.znix.xftl.FTLFile
import xyz.znix.xftl.sys.INativeResource
import xyz.znix.xftl.sys.ResourceContext

/**
 * The browser version of the game's mouse cursors (in place of the game's
 * own Cursor, which uses GLFW): for now, the browser's own cursor.
 */
class Cursor(
    context: ResourceContext,
    df: Datafile,
    val mainFile: FTLFile,
    overlayFile: FTLFile?,
    frame: Int? = null
) : INativeResource {
    override var freed: Boolean = false
        private set

    init {
        context.register(this)
    }

    fun setActive(glfwWindow: Long) {
    }

    override fun free() {
        check(!freed) { "Cannot double-free cursor '${mainFile.name}'" }
        freed = true
    }
}
