package xyz.znix.xftl.sys

import org.lwjgl.util.tinyfd.TinyFileDialogs
import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.Translator
import xyz.znix.xftl.f
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.replaceArg
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Shown when there's no `ftl.dat` to use: when [MainGame.findFtlDat] finds
 * none, or several installations to pick from. It lists the installations
 * [FTLFinder] finds, and FTL if it's running, and the player can browse for
 * `ftl.dat` or drop it onto the window.
 *
 * Everything is in a panel in the middle of the window: a title and
 * description, the installations, and a drop zone to drop `ftl.dat` on or
 * click to browse for it.
 */
class DatafileSelectState(private val game: MainGame) : MainGame.GameState() {
    private val context = ResourceContext()

    private lateinit var container: GameContainer
    private lateinit var style: LauncherStyle
    private val font: SILFontLoader get() = style.font
    private lateinit var translator: Translator

    private lateinit var baseOptions: List<FTLFinder.Installation>
    private val options = ArrayList<FTLFinder.Installation>()

    private lateinit var descriptionLines: List<String>
    private lateinit var pathFileLines: List<String>

    /**
     * Why the last file the player picked or dropped can't be used.
     */
    private var errorLines: List<String> = emptyList()

    // Where the options were drawn, for the mouse
    private val optionRects = ArrayList<LauncherRect>()

    private var hoveredOption: Int? = null
    private var dropHovered = false

    private var scanThread: Thread? = null
    private var timeToProcessScan: Float = 0f

    @Volatile
    private var scanResult: Path? = null

    @Volatile
    private var hasScanResult = false

    // The file dialog blocks, so it runs on its own thread
    private var browseThread: Thread? = null

    @Volatile
    private var browseResult: String? = null

    @Volatile
    private var hasBrowseResult = false

    private val isBrowsing: Boolean get() = browseThread?.isAlive == true

    override fun init(container: GameContainer) {
        this.container = container

        style = LauncherStyle(context)

        translator = Translator("assets/lang/en.xml")

        baseOptions = FTLFinder.findInstallations()
        options.addAll(baseOptions)

        descriptionLines = style.wrap(translator["xftl_select_datafile_desc"])
        val pathFile = translator["xftl_select_datafile_path_file"]
            .replaceArg(PlatformSpecific.INSTANCE.ftlDatPathFile.toAbsolutePath().toString())
        pathFileLines = style.wrap(pathFile)

        container.onFilesDropped = { files ->
            // If several files are dropped, use the first one that works
            val datafile = files.firstNotNullOfOrNull { FTLFinder.resolveDatafile(it) }
            if (datafile != null) {
                select(datafile)
            } else {
                showInvalid(files.firstOrNull())
            }
        }
    }

    override fun update(container: GameContainer, delta: Float) {
        if (hasBrowseResult) {
            hasBrowseResult = false
            browseResult?.let { picked(it) }
        }

        // If we're currently scanning, don't keep running the timer - wait
        // three seconds between scans, so if a scan takes longer than that
        // we shouldn't start another one in parallel.
        if (scanThread?.isAlive == true) {
            return
        }

        if (hasScanResult) {
            hasScanResult = false

            options.clear()
            options.addAll(baseOptions)
            scanResult?.let { running ->
                if (options.none { it.path == running })
                    options.add(FTLFinder.Installation(running, translator["xftl_select_datafile_running"]))
            }
        }

        // Every 3 seconds, check if FTL is running - and if so, grab its path
        // to ftl.dat.
        timeToProcessScan -= delta
        if (timeToProcessScan > 0)
            return
        timeToProcessScan = 3f

        // Scanning can take some time, put it on its own thread.
        scanThread = Thread {
            scanResult = FTLFinder.findRunningInstance()
            hasScanResult = true
        }
        scanThread!!.name = "FTL Instance Scanner"
        scanThread!!.isDaemon = true
        scanThread!!.start()

        println("Scanning for FTL processes, to find their ftl.dat path")
    }

    /**
     * How tall the panel's contents are, so it can be centred.
     */
    private fun contentHeight(): Int {
        var height = TITLE_HEIGHT + GAP
        height += descriptionLines.size * LINE_HEIGHT + SECTION_GAP
        height += LINE_HEIGHT + GAP // The installations heading
        height += when (options.isEmpty()) {
            true -> LINE_HEIGHT
            false -> options.size * (OPTION_HEIGHT + GAP) - GAP
        }
        height += SECTION_GAP + DROP_HEIGHT
        if (errorLines.isNotEmpty())
            height += GAP + errorLines.size * LINE_HEIGHT
        return height
    }

    override fun render(container: GameContainer, g: Graphics) {
        val input = container.input
        val mouseX = input.mouseX
        val mouseY = input.mouseY

        // The panel, in the middle of the window, with the path file note
        // below it
        val footerHeight = GAP * 2 + pathFileLines.size * LINE_HEIGHT
        val panel = style.drawPanel(container, g, contentHeight(), footerHeight)

        val x = panel.x + PADDING
        var y = panel.y + PADDING

        // Title and description
        style.drawTitle(container, x, y, translator["xftl_select_datafile_title"])
        y += TITLE_HEIGHT + GAP

        y = style.drawCentredLines(x, y, descriptionLines, MUTED_TEXT)
        y += SECTION_GAP

        // The installations found
        font.drawString(x.f, (y + BASELINE).f, translator["xftl_select_datafile_found"], TEXT)
        y += LINE_HEIGHT + GAP

        optionRects.clear()
        hoveredOption = null
        if (options.isEmpty()) {
            font.drawString(x.f, (y + BASELINE).f, translator["xftl_select_datafile_not_found"], MUTED_TEXT)
            y += LINE_HEIGHT
        } else {
            for ((index, opt) in options.withIndex()) {
                val rect = LauncherRect(x, y, CONTENT_WIDTH, OPTION_HEIGHT)
                optionRects += rect
                val hover = !isBrowsing && rect.contains(mouseX, mouseY)
                if (hover)
                    hoveredOption = index

                g.colour = if (hover) OPTION_HOVER else OPTION
                g.fillRect(rect.x, rect.y, rect.width, rect.height)
                g.colour = if (hover) ACCENT else PANEL_BORDER
                g.drawRect(rect.x, rect.y, rect.width - 1, rect.height - 1)

                font.drawString(rect.x + 12f, rect.y + 19f, opt.source, ACCENT)
                font.drawStringTruncated(
                    rect.x + 12f, rect.y + 38f, rect.width - 24f,
                    opt.path.toString(), MUTED_TEXT
                )

                y += OPTION_HEIGHT + GAP
            }
            y -= GAP
        }
        y += SECTION_GAP

        // The drop zone
        val drop = LauncherRect(x, y, CONTENT_WIDTH, DROP_HEIGHT)
        dropHovered = !isBrowsing && drop.contains(mouseX, mouseY)
        renderDropZone(g, drop, dropHovered)
        y += DROP_HEIGHT

        if (errorLines.isNotEmpty())
            style.drawCentredLines(x, y + GAP, errorLines, ERROR)

        // Below the panel: where else the path can go
        style.drawCentredLines(x, panel.y + panel.height + GAP * 2, pathFileLines, FAINT_TEXT)
    }

    private fun renderDropZone(g: Graphics, rect: LauncherRect, hover: Boolean) {
        g.colour = if (hover) DROP_HOVER else DROP
        g.fillRect(rect.x, rect.y, rect.width, rect.height)

        val border = if (hover || isBrowsing) ACCENT else DROP_BORDER
        g.colour = border
        drawDashedRect(g, rect)

        // An upload icon: an arrow into a tray
        val centreX = rect.x + rect.width / 2
        val iconTop = rect.y + 30
        g.fillRect(centreX - 1, iconTop, 3, 30)
        for (i in 0..2) {
            g.drawLine(centreX - 10 + i, iconTop + 10, centreX + i, iconTop)
            g.drawLine(centreX + i, iconTop, centreX + 10 + i, iconTop + 10)
        }
        val trayY = iconTop + 38
        g.fillRect(centreX - 22, trayY, 45, 3)
        g.fillRect(centreX - 22, trayY - 10, 3, 10)
        g.fillRect(centreX + 20, trayY - 10, 3, 10)

        val textY = trayY + 32
        if (isBrowsing) {
            font.drawStringCentred(rect.x.f, textY.f, rect.width.f, translator["xftl_select_datafile_browsing"], TEXT)
        } else {
            font.drawStringCentred(rect.x.f, textY.f, rect.width.f, translator["xftl_select_datafile_drop"], TEXT)
            font.drawStringCentred(
                rect.x.f, (textY + LINE_HEIGHT).f, rect.width.f,
                translator["xftl_select_datafile_browse"], if (hover) ACCENT else MUTED_TEXT
            )
        }
    }

    private fun drawDashedRect(g: Graphics, rect: LauncherRect) {
        val right = rect.x + rect.width - 2
        val bottom = rect.y + rect.height - 2
        var dx = rect.x
        while (dx < right) {
            val len = minOf(DASH, right - dx)
            g.fillRect(dx, rect.y, len, 2)
            g.fillRect(dx, bottom, len, 2)
            dx += DASH + DASH_GAP
        }
        var dy = rect.y
        while (dy < bottom) {
            val len = minOf(DASH, bottom - dy)
            g.fillRect(rect.x, dy, 2, len)
            g.fillRect(right, dy, 2, len)
            dy += DASH + DASH_GAP
        }
    }

    override fun mouseClicked(button: Int, x: Int, y: Int, clickCount: Int) {
        if (button != Input.MOUSE_LEFT_BUTTON)
            return
        if (clickCount != 1)
            return

        if (dropHovered) {
            browse()
            return
        }

        val hovered = hoveredOption ?: return
        select(options[hovered].path)
    }

    private fun browse() {
        if (isBrowsing)
            return

        val dialogTitle = translator["xftl_select_datafile_browse_title"]
        browseThread = Thread {
            // No file type filter: on macOS, the player picks the FTL app,
            // since the dialog can't look inside it.
            browseResult = TinyFileDialogs.tinyfd_openFileDialog(dialogTitle, null, null, null, false)
            hasBrowseResult = true
        }
        browseThread!!.name = "ftl.dat File Dialog"
        browseThread!!.isDaemon = true
        browseThread!!.start()
    }

    private fun picked(rawPath: String) {
        val path = try {
            Path.of(rawPath)
        } catch (ex: InvalidPathException) {
            null
        }
        val datafile = path?.let { FTLFinder.resolveDatafile(it) }
        if (datafile != null) {
            select(datafile)
        } else {
            showInvalid(path)
        }
    }

    private fun showInvalid(path: Path?) {
        val message = translator["xftl_select_datafile_invalid"].replaceArg(path?.toString() ?: "")
        errorLines = style.wrap(message)
    }

    private fun select(datafile: Path) {
        MainGame.saveFtlDatPath(datafile)
        game.datafileSelected()
    }

    override fun shutdown() {
        container.onFilesDropped = null
        context.freeAll()
    }

    companion object {
        private const val OPTION_HEIGHT = 48
        private const val DROP_HEIGHT = 170
        private const val DASH = 10
        private const val DASH_GAP = 6

        private val DROP = Colour(20, 26, 38)
        private val DROP_HOVER = Colour(28, 42, 46)
        private val DROP_BORDER = Colour(90, 104, 130)
    }
}
