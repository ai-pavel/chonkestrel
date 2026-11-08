package xyz.znix.xftl.rendering

/**
 * Counts of what the renderers do, for the performance log (see
 * LWJGLGameContainer).
 */
object RenderStats {
    var drawCalls = 0L
    var bytesUploaded = 0L
    var filterChanges = 0L
}
