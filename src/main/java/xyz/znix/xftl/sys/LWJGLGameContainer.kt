package xyz.znix.xftl.sys


import org.lwjgl.Version
import xyz.znix.xftl.XftlDebug
import org.lwjgl.glfw.Callbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWDropCallback
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.KHRDebug
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.newdawn.slick.InputListener
import org.newdawn.slick.KeyListener
import org.newdawn.slick.MouseListener
import org.newdawn.slick.opengl.ImageDataFactory
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Cursor
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.RenderStats
import java.io.BufferedInputStream
import java.nio.file.Path


class LWJGLGameContainer(private val game: Game) : GameContainer {
    companion object {
        /**
         * How many frames a second the game runs (and is drawn) at while its
         * window isn't focused, to save CPU (see renderWhenUnfocused).
         */
        const val UNFOCUSED_FPS = 20.0

        /**
         * The game sizes offered in the display options, if they fit: none
         * smaller than the big ships' interface needs (1920x1080), and at
         * most MAX_DISPLAY_MODES of them.
         */
        val DISPLAY_SIZES = listOf(
            1920 to 1080, 1920 to 1200, 2048 to 1152, 2560 to 1080, 2560 to 1440, 2560 to 1600,
            2880 to 1620, 2880 to 1800, 3200 to 1800, 3440 to 1440, 3840 to 2160, 3840 to 2400,
            5120 to 2160, 5120 to 2880,
        )
        const val MAX_DISPLAY_MODES = 15
    }

    private lateinit var lwjglInput: LWJGLInput
    override val input: Input get() = lwjglInput

    override var width: Int = 400
        private set
    override var height: Int = 400
        private set

    private lateinit var g: Graphics

    /**
     * The graphics the game's drawn with, for the icon renderer to draw
     * its own frames (see IconDirector).
     */
    val graphics: Graphics get() = g

    /**
     * If true, this is the game's own size rather than one that was asked
     * for, and the window's made smaller (and the game's [width] and
     * [height] with it, so there's less room for the UI) if it doesn't fit
     * in the primary monitor's work area (the screen minus the
     * taskbar/dock/menu bar) with its title bar and borders.
     */
    private var defaultSize: Boolean = false

    /**
     * The GLFW window handle.
     */
    private var window: Long = 0

    /**
     * Called after each frame is rendered, before it's shown, with the size
     * of the framebuffer. The screenshot generator uses this to capture the
     * frame (see ScreenshotDirector).
     */
    var afterRender: ((framebufferWidth: Int, framebufferHeight: Int) -> Unit)? = null

    /**
     * If set, every frame advances the game by exactly this many seconds,
     * however long it really took. The video recorder uses this, so the video
     * plays smoothly even though recording each frame is slow.
     */
    var fixedDeltaSec: Float? = null

    /**
     * How many times the game is updated each frame, to run it faster than
     * real time (with [fixedDeltaSec]), for the automatic playtest (see
     * AutoplayDirector).
     */
    var updatesPerFrame: Int = 1

    /**
     * Called with any exception thrown while updating the game (which is
     * otherwise just printed), for the automatic playtest to count.
     */
    var onUpdateError: ((Exception) -> Unit)? = null

    /**
     * Keep running at full speed while the window isn't focused, for the
     * screenshot generator, which shouldn't slow down if the player switches
     * windows. Otherwise the game still updates and renders while the window
     * isn't focused (so it can still be seen), but slower, to save CPU.
     */
    var renderWhenUnfocused: Boolean = false

    override var onFilesDropped: ((List<Path>) -> Unit)? = null

    /**
     * Logs how fast the game's running, for the packaged game's log (or
     * with XFTL_PERF), to find out why it's slow for someone.
     */
    private val performance: PerformanceLog? =
        if (PackagedGame.isPackaged || System.getenv("XFTL_PERF") != null) PerformanceLog() else null

    override val isDefaultSize: Boolean get() = defaultSize

    override val screens: List<Screen>
        get() {
            val monitors = glfwGetMonitors() ?: return emptyList()
            val primary = glfwGetPrimaryMonitor()
            val handles = (0 until monitors.limit()).map { monitors[it] }.sortedBy { it != primary }
            // In pixels: on a high-DPI screen (such as a Mac's Retina
            // display) the video mode is in points, each several pixels,
            // and the game can use them all (see fitMonitorWorkArea)
            return handles.mapNotNull { monitor ->
                val mode = glfwGetVideoMode(monitor) ?: return@mapNotNull null
                val scale = stackPush().use { stack ->
                    val xScale = stack.mallocFloat(1)
                    val yScale = stack.mallocFloat(1)
                    glfwGetMonitorContentScale(monitor, xScale, yScale)
                    minOf(xScale[0], yScale[0]).coerceAtLeast(1f)
                }
                Screen(glfwGetMonitorName(monitor) ?: "?", (mode.width() * scale).toInt(), (mode.height() * scale).toInt())
            }
        }

    override val pixelScale: Float
        get() = stackPush().use { stack ->
            val fbWidth = stack.mallocInt(1)
            val fbHeight = stack.mallocInt(1)
            glfwGetFramebufferSize(window, fbWidth, fbHeight)
            fitGameArea(fbWidth[0], fbHeight[0]).third
        }

    override fun exit() {
        glfwSetWindowShouldClose(window, true)
    }

    override fun setCursor(cursor: Cursor?) {
        if (cursor != null) {
            cursor.setActive(window)
        } else {
            glfwSetCursor(window, NULL)
        }
    }

    fun start() {
        println("Using LWJGL version ${Version.getVersion()}")
        init()

        try {
            loop()
        } finally {
            // Call shutdown if there was an exception, to avoid a VM crash
            // from not freeing native resources.
            game.shutdown()
        }

        // Free the window callbacks and destroy the window
        Callbacks.glfwFreeCallbacks(window)
        glfwDestroyWindow(window)

        // Terminate GLFW and free the error callback
        glfwTerminate()
        glfwSetErrorCallback(null)!!.free()
    }

    private fun init() {
        // Set up an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set()

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        check(glfwInit()) { "Unable to initialize GLFW" }

        // Configure GLFW
        glfwDefaultWindowHints() // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE) // the window will be resizable

        // Set the OpenGL stuff
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

        // A debug context, for OpenGL's error messages, but not for players,
        // since some drivers can't make one
        val debugContext = !PackagedGame.isPackaged
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, if (debugContext) GLFW_TRUE else GLFW_FALSE)

        // Create the window
        window = glfwCreateWindow(width, height, game.title, NULL, NULL)
        if (window == NULL && debugContext) {
            println("Failed to create the window with a debug OpenGL context, trying without one")
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_FALSE)
            window = glfwCreateWindow(width, height, game.title, NULL, NULL)
        }
        if (window == NULL)
            throw RuntimeException("Failed to create the GLFW window: the graphics driver may not support OpenGL 3.3")

        // A window that's been shrunk to fit is placed in the work area
        // instead, clear of the taskbar
        if (!defaultSize || !fitMonitorWorkArea()) {
            centreWindow()
        }

        // The size it starts with is the recommended one, in the display options
        stackPush().use { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            glfwGetWindowSize(window, w, h)
            val scale = primaryMonitorScale()
            startingMode = DisplayMode(width, height, w[0], h[0],
                kotlin.math.round(w[0] * scale).toInt(), kotlin.math.round(h[0] * scale).toInt(), scale > 1f, true)
        }

        // Set the window's icon
        setupWindowIcon()

        // Make the OpenGL context current
        glfwMakeContextCurrent(window)
        // Enable v-sync
        glfwSwapInterval(1)

        // Make the window visible
        glfwShowWindow(window)

        lwjglInput = LWJGLInput(window, this::windowToGameCoords)

        glfwSetDropCallback(window) { _, count, names ->
            val listener = onFilesDropped ?: return@glfwSetDropCallback
            listener((0 until count).map { Path.of(GLFWDropCallback.getName(names, it)) })
        }
        g = Graphics()
        g.markCurrentImageTransformSource()

        if (game is KeyListener)
            lwjglInput.keyListeners.add(game)
        if (game is MouseListener)
            lwjglInput.mouseListeners.add(game)

        // Set up OpenGL
        initOpenGL()
    }

    private fun centreWindow() {
        stackPush().use { stack ->
            val pWidth = stack.mallocInt(1) // int*
            val pHeight = stack.mallocInt(1) // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight)

            // Get the resolution of the primary monitor
            val vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!

            // Center the window
            glfwSetWindowPos(
                window,
                (vidmode.width() - pWidth[0]) / 2,
                (vidmode.height() - pHeight[0]) / 2
            )
        }
    }

    /**
     * Shrink the (still hidden) window, if it's needed, so that it, including
     * its title bar and borders, fits in the primary monitor's work area. On
     * a high-DPI screen with the pixels for the whole game, only the window
     * shrinks (see below), and the game keeps its size.
     * The game's [width] and [height] change to match, so it's drawn at the
     * screen's own size, not scaled. The window's centred in the work area.
     *
     * Returns false if it fits without that, and hasn't been moved.
     */
    // The display mode the game started with (see displayModes)
    private var startingMode: DisplayMode? = null

    override val currentDisplayMode: DisplayMode?
        get() = stackPush().use { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            glfwGetWindowSize(window, w, h)
            displayModes.firstOrNull { it.gameWidth == width && it.gameHeight == height && it.windowWidth == w[0] && it.windowHeight == h[0] }
                ?: primaryMonitorScale().let { scale ->
                    DisplayMode(width, height, w[0], h[0], kotlin.math.round(w[0] * scale).toInt(),
                        kotlin.math.round(h[0] * scale).toInt(), scale > 1f, false)
                }
        }

    /**
     * The room for the window's content in the main screen's work area (in
     * its units), and how many pixels each unit is.
     */
    private fun windowRoom(): Triple<Int, Int, Float> = stackPush().use { stack ->
        val areaX = stack.mallocInt(1)
        val areaY = stack.mallocInt(1)
        val areaWidth = stack.mallocInt(1)
        val areaHeight = stack.mallocInt(1)
        glfwGetMonitorWorkarea(glfwGetPrimaryMonitor(), areaX, areaY, areaWidth, areaHeight)
        val left = stack.mallocInt(1)
        val top = stack.mallocInt(1)
        val right = stack.mallocInt(1)
        val bottom = stack.mallocInt(1)
        glfwGetWindowFrameSize(window, left, top, right, bottom)
        Triple(areaWidth[0] - left[0] - right[0], areaHeight[0] - top[0] - bottom[0], primaryMonitorScale())
    }

    /**
     * How many pixels each of the main screen's units is: the window's put
     * on that screen (see fitMonitorWorkArea), whichever it starts on.
     */
    private fun primaryMonitorScale(): Float = stackPush().use { stack ->
        val xScale = stack.mallocFloat(1)
        val yScale = stack.mallocFloat(1)
        glfwGetMonitorContentScale(glfwGetPrimaryMonitor(), xScale, yScale)
        minOf(xScale[0], yScale[0]).coerceAtLeast(1f)
    }

    override val displayModes: List<DisplayMode>
        get() {
            val (roomWidth, roomHeight, scale) = windowRoom()
            val retina = scale > 1f
            val modes = ArrayList<DisplayMode>()
            startingMode?.let { modes += it }
            // Each size is in the screen's pixels, drawn a game pixel to a
            // screen pixel, in a window that many screen units big
            for ((w, h) in DISPLAY_SIZES) {
                val windowWidth = kotlin.math.ceil(w / scale).toInt()
                val windowHeight = kotlin.math.ceil(h / scale).toInt()
                if (windowWidth > roomWidth || windowHeight > roomHeight)
                    continue
                val (gameWidth, gameHeight) = gameSizeFor(w, h)
                modes += DisplayMode(gameWidth, gameHeight, windowWidth, windowHeight, w, h, retina, false)
            }
            val unique = ArrayList<DisplayMode>()
            for (mode in modes) {
                if (unique.none { it.windowWidth == mode.windowWidth && it.windowHeight == mode.windowHeight })
                    unique += mode
            }
            return unique.take(MAX_DISPLAY_MODES)
        }

    /**
     * The game's size for [pixelWidth] by [pixelHeight] screen pixels: the
     * interface is laid out for 1920x1080, so on anything bigger it's scaled
     * up as far as it fits, and the rest of the room is the game's.
     */
    private fun gameSizeFor(pixelWidth: Int, pixelHeight: Int): Pair<Int, Int> {
        val scale = minOf(pixelWidth / 1920f, pixelHeight / 1080f).coerceAtLeast(1f)
        return kotlin.math.round(pixelWidth / scale).toInt() to kotlin.math.round(pixelHeight / scale).toInt()
    }

    override fun setDisplay(mode: DisplayMode) {
        width = mode.gameWidth
        height = mode.gameHeight
        glfwSetWindowSize(window, mode.windowWidth, mode.windowHeight)
        stackPush().use { stack ->
            val areaX = stack.mallocInt(1)
            val areaY = stack.mallocInt(1)
            val areaWidth = stack.mallocInt(1)
            val areaHeight = stack.mallocInt(1)
            glfwGetMonitorWorkarea(glfwGetPrimaryMonitor(), areaX, areaY, areaWidth, areaHeight)
            val left = stack.mallocInt(1)
            val top = stack.mallocInt(1)
            val right = stack.mallocInt(1)
            val bottom = stack.mallocInt(1)
            glfwGetWindowFrameSize(window, left, top, right, bottom)
            glfwSetWindowPos(
                window,
                areaX[0] + left[0] + (areaWidth[0] - (mode.windowWidth + left[0] + right[0])) / 2,
                areaY[0] + top[0] + (areaHeight[0] - (mode.windowHeight + top[0] + bottom[0])) / 2
            )
        }
        println("Display: ${mode.label}, a ${mode.windowWidth}x${mode.windowHeight} window")
    }

    private fun fitMonitorWorkArea(): Boolean {
        stackPush().use { stack ->
            val areaX = stack.mallocInt(1)
            val areaY = stack.mallocInt(1)
            val areaWidth = stack.mallocInt(1)
            val areaHeight = stack.mallocInt(1)
            glfwGetMonitorWorkarea(glfwGetPrimaryMonitor(), areaX, areaY, areaWidth, areaHeight)

            // The size of the title bar and borders, which aren't part
            // of the window's content area.
            val left = stack.mallocInt(1)
            val top = stack.mallocInt(1)
            val right = stack.mallocInt(1)
            val bottom = stack.mallocInt(1)
            glfwGetWindowFrameSize(window, left, top, right, bottom)

            val roomWidth = areaWidth[0] - left[0] - right[0]
            val roomHeight = areaHeight[0] - top[0] - bottom[0]
            if (width <= roomWidth && height <= roomHeight)
                return false

            // On a high-DPI screen (such as a Mac's Retina display), the
            // window's size is in points, each several pixels. If the room
            // there is, in pixels, still has space for the whole game, the
            // game keeps its size, and the window is as big as fits.
            // (The screen's, not the window's: it may not be on that
            // screen yet, if there are several with different scales)
            val scale = primaryMonitorScale()
            if (scale > 1f && width <= roomWidth * scale && height <= roomHeight * scale) {
                // The window's made as big as fits on the screen, keeping the
                // game's shape, and the game's scaled up to fill it (it's
                // never smaller than the game's own size in pixels)
                val fit = minOf(roomWidth.toFloat() / width, roomHeight.toFloat() / height)
                val pointsWidth = (width * fit).toInt()
                val pointsHeight = (height * fit).toInt()
                glfwSetWindowSize(window, pointsWidth, pointsHeight)
                glfwSetWindowPos(
                    window,
                    areaX[0] + left[0] + (areaWidth[0] - (pointsWidth + left[0] + right[0])) / 2,
                    areaY[0] + top[0] + (areaHeight[0] - (pointsHeight + top[0] + bottom[0])) / 2
                )
                println("High-DPI screen (${scale}x): a ${pointsWidth}x$pointsHeight window, for the game's ${width}x$height")
                return true
            }

            val fitWidth = minOf(width, roomWidth)
            val fitHeight = minOf(height, roomHeight)

            width = fitWidth
            height = fitHeight
            glfwSetWindowSize(window, width, height)

            val outerWidth = width + left[0] + right[0]
            val outerHeight = height + top[0] + bottom[0]
            glfwSetWindowPos(
                window,
                areaX[0] + left[0] + (areaWidth[0] - outerWidth) / 2,
                areaY[0] + top[0] + (areaHeight[0] - outerHeight) / 2
            )
            return true
        }
    }

    private fun setupWindowIcon() {
        // Windows on macOS don't have icons (the app's icon is in its
        // bundle), and GLFW reports an error if one's set
        if (System.getProperty("os.name").contains("Mac OS"))
            return

        val path = "assets/img/xftl_icon.png"

        // The icon's made by `make icon` and isn't kept in git, so a build
        // without it has the default window icon
        val stream = javaClass.classLoader.getResourceAsStream(path)
        if (stream == null) {
            println("[WARN] No window icon ($path): run `make icon` to make it")
            return
        }

        val imageData = ImageDataFactory.getImageDataFor(path)
        stream.use { stream ->
            imageData.loadImage(BufferedInputStream(stream), false, null)
        }

        stackPush().use { stack ->
            val imageArray = GLFWImage.Buffer(stack.malloc(GLFWImage.SIZEOF))
            val image = imageArray[0]

            image.set(imageData.width, imageData.height, imageData.imageBufferData)

            glfwSetWindowIcon(window, imageArray)
        }
    }

    private fun initOpenGL() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities()

        // Which graphics driver this is, for bug reports
        println("OpenGL ${glGetString(GL_VERSION)}, ${glGetString(GL_RENDERER)} (${glGetString(GL_VENDOR)})")

        // From Slick's ImmediateModeOGLRenderer
        glDisable(GL_DEPTH_TEST)

        glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        glClearDepth(1.0)

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        updateViewport()

        // Set up debug output
        glEnable(KHRDebug.GL_DEBUG_OUTPUT)

        // Don't fill the console with errors if there's an issue each frame
        val maxErrorCount = 50
        var errorCount = 0

        // OSX lacks debug capabilities, we'll get an exception if we try to set them
        if (!GL.getCapabilities().GL_KHR_debug) {
            return
        }

        // Disable the flood of notification messages
        KHRDebug.glDebugMessageControl(
            GL_DONT_CARE,
            GL_DONT_CARE,
            KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION,
            null as IntArray?,
            false
        )

        KHRDebug.glDebugMessageCallback({ source, type, id, severity, length, message, _ ->
            errorCount++
            if (errorCount == maxErrorCount) {
                println("Got too many OpenGL debug messages, stopping output")
            }
            if (errorCount >= maxErrorCount) {
                return@glDebugMessageCallback
            }

            val messageString = MemoryUtil.memUTF8(message, length)

            // Filtering for end users?
            println("Got GL debug message (id=$id src=$source type=$type sev=$severity): $messageString")
        }, 0)
    }

    private fun loop() {
        game.init(this)

        var lastNanos = System.nanoTime()

        // Run the rendering loop until the user has attempted to close
        // the window, or exit is called.
        while (!glfwWindowShouldClose(window)) {
            // Read any changes to the input system
            lwjglInput.update()

            // Find the delta-time, in seconds
            val thisUpdateTime = System.nanoTime()
            val deltaNS = thisUpdateTime - lastNanos
            val deltaSec = fixedDeltaSec ?: (deltaNS / 1_000_000_000f)
            lastNanos = thisUpdateTime

            val isFocused = glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE

            // Update the game state
            // If there's an exception, catch it instead of crashing the whole game.
            // If we're in an invalid state this won't help, but on-click/on-keypress
            // stuff should generally be OK.
            val updateStart = System.nanoTime()
            repeat(updatesPerFrame) {
                try {
                    game.update(this, deltaSec)
                } catch (ex: Exception) {
                    println("Exception during game update")
                    ex.printStackTrace()
                    onUpdateError?.invoke(ex)
                }
            }
            val updateNanos = System.nanoTime() - updateStart

            // Render out the game
            val renderStart = System.nanoTime()
            updateViewport()
            game.render(this, g)

            afterRender?.let { hook ->
                stackPush().use { stack ->
                    val fbWidth = stack.mallocInt(1)
                    val fbHeight = stack.mallocInt(1)
                    glfwGetFramebufferSize(window, fbWidth, fbHeight)
                    hook(fbWidth[0], fbHeight[0])
                }
            }

            // Swap the colour buffers, presenting the newly-drawn one
            // to the screen.
            val swapStart = System.nanoTime()
            glfwSwapBuffers(window)
            performance?.frame(updateNanos, swapStart - renderStart, System.nanoTime() - swapStart)

            // Poll for window events. The key callback above will only be
            // invoked during this call. While the window isn't focused, the
            // game runs (and is drawn) at ~20 frames a second, to save CPU.
            if (!isFocused && !renderWhenUnfocused)
                glfwWaitEventsTimeout(1.0 / UNFOCUSED_FPS)
            else
                glfwPollEvents()
        }
    }

    /**
     * Set the size of the window, and the game's coordinate space.
     *
     * If [defaultSize] is set, this is the game's own size rather than one
     * that was asked for, and it's made smaller if the window doesn't fit
     * on the screen with its title bar and the taskbar.
     *
     * The framebuffer may be larger than this (on a high-DPI display), in
     * which case the game is scaled up to fit.
     */
    fun setDisplayMode(width: Int, height: Int, defaultSize: Boolean) {
        this.width = width
        this.height = height
        this.defaultSize = defaultSize
    }

    /**
     * Find the area of a [areaWidth] by [areaHeight] region the game should
     * be drawn into, centred and scaled as large as fits.
     *
     * Returns (x, y, scale).
     */
    private fun fitGameArea(areaWidth: Int, areaHeight: Int): Triple<Float, Float, Float> {
        val scale = minOf(areaWidth.toFloat() / width, areaHeight.toFloat() / height)
        val x = (areaWidth - width * scale) / 2
        val y = (areaHeight - height * scale) / 2
        return Triple(x, y, scale)
    }

    /**
     * Point the OpenGL viewport at the part of the framebuffer the game is
     * drawn into. Anything outside it stays black.
     */
    private fun updateViewport() {
        stackPush().use { stack ->
            val fbWidth = stack.mallocInt(1)
            val fbHeight = stack.mallocInt(1)
            glfwGetFramebufferSize(window, fbWidth, fbHeight)

            val (x, y, scale) = fitGameArea(fbWidth[0], fbHeight[0])
            glViewport(x.toInt(), y.toInt(), (width * scale).toInt(), (height * scale).toInt())
        }
    }

    /**
     * Convert a cursor position (in GLFW window coordinates, which are
     * not necessarily pixels) into the game's coordinate space.
     */
    private fun windowToGameCoords(windowX: Double, windowY: Double): Point {
        stackPush().use { stack ->
            val winWidth = stack.mallocInt(1)
            val winHeight = stack.mallocInt(1)
            glfwGetWindowSize(window, winWidth, winHeight)

            val (x, y, scale) = fitGameArea(winWidth[0], winHeight[0])
            return Point(((windowX - x) / scale).toInt(), ((windowY - y) / scale).toInt())
        }
    }
}

private class LWJGLInput(
    val window: Long,
    val windowToGameCoords: (Double, Double) -> Point,
) : Input {
    // While recording screenshots or videos, the mouse is off the window,
    // so nothing's hovered wherever it happens to be (see update)
    override var mouseX: Int = if (XftlDebug.recording) -1 else 0
        private set
    override var mouseY: Int = if (XftlDebug.recording) -1 else 0
        private set

    private val mouseClickPos = Point(0, 0)

    private var scrollLeftover: Double = 0.0

    // Note that GLFW_KEY_LAST is the maximum possible ID, not one past it.
    private val pendingKeyPresses = BooleanArray(GLFW_KEY_LAST + 1)

    val keyListeners = ArrayList<KeyListener>()
    val mouseListeners = ArrayList<MouseListener>()

    init {
        // Set up a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(
            window
        ) { _: Long, key: Int, scancode: Int, action: Int, mods: Int ->
            keyCallback(key, scancode, action, mods)
        }
        glfwSetCharCallback(window) { _, codepoint -> charCallback(codepoint.toChar()) }
        glfwSetMouseButtonCallback(window) { _, button, action, mods -> mouseButtonCallback(button, action, mods) }
        glfwSetScrollCallback(window) { _, xOffset, yOffset -> mouseWheelCallback(xOffset, yOffset) }
    }

    fun update() {
        // Block updates while unfocused, so the player doesn't hear
        // the mouse-over sounds for buttons through another window.
        // This happens on Linux/X11 without this check.
        // (while recording, the mouse is wherever the recording puts it,
        // or off the window)
        if (XftlDebug.recording) {
            mouseX = XftlDebug.recordingMouse?.x ?: -1
            mouseY = XftlDebug.recordingMouse?.y ?: -1
            return
        }
        if (glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_FALSE)
            return

        val lastX = mouseX
        val lastY = mouseY

        stackPush().use { stack ->
            val x = stack.mallocDouble(1)
            val y = stack.mallocDouble(1)
            glfwGetCursorPos(window, x, y)
            val pos = windowToGameCoords(x[0], y[0])
            mouseX = pos.x
            mouseY = pos.y
        }

        if (lastX == mouseX && lastY == mouseY)
            return

        val dragging = isMouseButtonDown(Input.MOUSE_LEFT_BUTTON) || isMouseButtonDown(Input.MOUSE_RIGHT_BUTTON)

        iterateAllowingMutation(mouseListeners) { listener ->
            when (dragging) {
                true -> listener.mouseDragged(lastX, lastY, mouseX, mouseY)
                false -> listener.mouseMoved(lastX, lastY, mouseX, mouseY)
            }
        }
    }

    private fun keyCallback(rawKey: Int, scancode: Int, action: Int, mods: Int) {
        // Convert between the different codes used by the "|" UK/ISO key on different platforms
        val key = remapKey(rawKey)

        // Ignore unsupported keys
        if (key == GLFW_KEY_UNKNOWN) {
            return
        }

        if (action == GLFW_PRESS) {
            pendingKeyPresses[key] = true
        }

        // Keys don't map 1-1 with characters (most characters on most European
        // keyboards do, but you can't rely on that!) so send 0 for the character
        // now, and then send the character by itself as a second press.
        iterateAllowingMutation(keyListeners) { listener ->
            when (action) {
                GLFW_PRESS -> listener.keyPressed(key, 0.toChar())
                GLFW_RELEASE -> listener.keyReleased(key, 0.toChar())
            }
        }
    }

    private fun charCallback(codepoint: Char) {
        iterateAllowingMutation(keyListeners) { listener ->
            listener.keyPressed(-1, codepoint)
        }
    }

    private fun mouseButtonCallback(button: Int, action: Int, mods: Int) {
        iterateAllowingMutation(mouseListeners) { listener ->
            when (action) {
                GLFW_PRESS -> listener.mousePressed(button, mouseX, mouseY)
                GLFW_RELEASE -> listener.mouseReleased(button, mouseX, mouseY)
            }
        }

        if (action == GLFW_PRESS) {
            mouseClickPos.set(mouseX, mouseY)
        }

        // Check if this is a 'click' event, with little to no dragging.
        // We don't support double-clicking, but that's not used anywhere.
        if (action == GLFW_RELEASE) {
            val dist = mouseClickPos.distToSq(mouseX, mouseY)
            if (dist <= CLICK_DISTANCE * CLICK_DISTANCE) {
                iterateAllowingMutation(mouseListeners) { listener ->
                    listener.mouseClicked(button, mouseX, mouseY, 1)
                }
            }
        }
    }

    private fun mouseWheelCallback(xOffset: Double, yOffset: Double) {
        val input = yOffset + scrollLeftover
        val change = (input * SCROLL_SCALE).toInt()

        // Store any integer round-off away, to be added onto the next scroll.
        scrollLeftover = input - change / SCROLL_SCALE

        iterateAllowingMutation(mouseListeners) { listener ->
            listener.mouseWheelMoved(change)
        }
    }

    /**
     * Iterate through a list, while accepting that it might be mutated while doing so.
     *
     * If this happens an input might be delivered twice or missed, but in
     * the case this is dealing with - clicking a button or pressing a key
     * that causes the game's state to switch - it's not a problem.
     */
    private inline fun <T> iterateAllowingMutation(list: List<T>, callback: (T) -> Unit) {
        var i = 0
        while (i < list.size) {
            callback(list[i])
            i++
        }
    }

    override fun isMouseButtonDown(button: Int): Boolean {
        return glfwGetMouseButton(window, button) == GLFW_PRESS
    }

    override fun isKeyPressed(key: Int): Boolean {
        val oldValue = pendingKeyPresses[key]
        pendingKeyPresses[key] = false
        return oldValue
    }

    override fun isKeyDown(key: Int): Boolean {
        return glfwGetKey(window, remapKey(key)) == GLFW_PRESS
    }

    override fun addListener(listener: InputListener) {
        keyListeners.add(listener)
        mouseListeners.add(listener)
    }

    override fun removeAllListeners() {
        keyListeners.clear()
        mouseListeners.clear()
    }

    override fun clearInputPressedRecord() {
        for (i in pendingKeyPresses.indices) {
            pendingKeyPresses[i] = false
        }
    }

    private fun remapKey(original: Int): Int {
        // See the documentation for KEY_BAR.
        if (original == GLFW_KEY_WORLD_2) {
            return Input.KEY_BAR
        }

        return original
    }

    companion object {
        // The maximum movement in pixels that doesn't count as dragging
        private const val CLICK_DISTANCE = 4

        // How much one movement of the scroll wheel equates to in Slick's units
        private const val SCROLL_SCALE = 120
    }
}

/**
 * Every [INTERVAL_NANOS], prints how many frames a second the game managed,
 * and how long the frames' parts took on average: updating the game,
 * rendering it, and swapping the buffers (which includes waiting for the
 * screen, with vsync, and for the graphics driver to catch up).
 */
private class PerformanceLog {
    private var start = System.nanoTime()
    private var frames = 0
    private var update = 0L
    private var render = 0L
    private var swap = 0L
    private var slowest = 0L
    private var drawCalls = RenderStats.drawCalls
    private var bytesUploaded = RenderStats.bytesUploaded
    private var filterChanges = RenderStats.filterChanges

    fun frame(updateNanos: Long, renderNanos: Long, swapNanos: Long) {
        frames++
        update += updateNanos
        render += renderNanos
        swap += swapNanos
        slowest = maxOf(slowest, updateNanos + renderNanos + swapNanos)

        val now = System.nanoTime()
        if (now - start < INTERVAL_NANOS)
            return

        fun ms(nanos: Long) = "%.1f".format(nanos / 1e6 / frames)
        val fps = frames * 1e9 / (now - start)
        val draws = (RenderStats.drawCalls - drawCalls) / frames
        val kilobytes = (RenderStats.bytesUploaded - bytesUploaded) / 1024 / frames
        val filters = (RenderStats.filterChanges - filterChanges) / frames
        println(
            ("Performance: %.0f fps, per frame: update %s ms, render %s ms, swap %s ms, slowest %.0f ms, " +
                    "%d draws, %d KB, %d filter changes")
                .format(fps, ms(update), ms(render), ms(swap), slowest / 1e6, draws, kilobytes, filters)
        )
        drawCalls = RenderStats.drawCalls
        bytesUploaded = RenderStats.bytesUploaded
        filterChanges = RenderStats.filterChanges

        start = now
        frames = 0
        update = 0
        render = 0
        swap = 0
        slowest = 0
    }

    companion object {
        const val INTERVAL_NANOS = 10_000_000_000L
    }
}
