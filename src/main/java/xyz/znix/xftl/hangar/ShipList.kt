package xyz.znix.xftl.hangar

import xyz.znix.xftl.Constants
import xyz.znix.xftl.LazyShipBlueprint
import xyz.znix.xftl.f
import xyz.znix.xftl.game.UIUtils
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.Input

class ShipList(private val state: SelectShipState, private val close: (LazyShipBlueprint?) -> Unit) {
    private val pos = Point(0, 0)
    private val size = Point(0, 0)

    private val titleTab = state.getImg("img/map/side_beaconmap.png")
    private val titleFont = state.getFont("HL2").apply { scale = 2f }

    private val title = state.translator["shiplist_title"]

    /**
     * The ships listed, in order: every variant of the mods' big ships, then
     * each vanilla family's A variant, in vanilla's order.
     */
    val ships: List<LazyShipBlueprint>

    private var offsetY: Int = 0
    private var maxY: Int = 0

    private var hovered: LazyShipBlueprint? = null

    // The ships we've warned about missing their miniature image, so it's only once each
    private val warnedMissingImages = HashSet<String>()

    init {
        // Every ship is listed, vanilla's families' A, B and C variants
        // too, each family's together, in order. Ships bigger than vanilla go first, in a
        // row of their own, with a mod's default ship first of all, and the
        // big Lanius and then Crystal ships last (see LAST_RACES). Vanilla's
        // ships follow, in rows of their own, in the families' order (see
        // xftl_ships.xml), which is vanilla's hangar's.
        // (a mod's ship's B and C variants, X_2 and X_3, are listed too,
        // each after its A, as their names sort)
        val nonFamilyShips = state.ships.filter { !state.shipFamilies.byShipId.containsKey(it.name) }
        // (a family's ships the hangar doesn't have are left out)
        val familyShips = state.shipFamilies.families.flatMap { family ->
            family.ships.mapNotNull { name -> state.ships.firstOrNull { it.name == name } }
        }
        ships = (familyShips + nonFamilyShips).sortedWith(
            compareByDescending<LazyShipBlueprint> { it.shipSize > 1 }.thenByDescending { it.isDefaultShip }
                .thenBy { LAST_RACES.indexOfFirst { race -> it.name.startsWith("PLAYER_SHIP_XFTL_$race") } }
        )
    }

    fun draw(container: GameContainer, g: Graphics) {
        g.colour = Colour(25, 25, 25, 100)
        g.fillRect(0, 0, container.width, container.height)

        val xMargin = 100
        val yMargin = 75
        pos.set(xMargin, yMargin)
        size.set(container.width - xMargin * 2, container.height - yMargin * 2)

        // Build the title, copied from BlueprintSelector
        val startWidth = 20
        val endWidth = 38
        val textWidth = titleFont.getWidth(title)
        val titleTabWidth = startWidth + textWidth + endWidth

        val shipName = hovered?.shipClass?.let { state.translator[it] } ?: hovered?.name
        val shipNameWidth = shipName?.let { titleFont.getWidth(it) } ?: 0
        val shipNameTabWidth = startWidth + shipNameWidth + endWidth
        val shipNameX = pos.x + size.x + 7 - shipNameTabWidth

        // Subtract out the glow
        val tabX = pos.x - 7
        val tabY = pos.y - 7

        state.windowRenderer.renderMasked(pos.x, pos.y, size.x, size.y, 0, 0, {
            g.colour = Colour.red // Anything non-transparent will do

            // Draw the title tab on the left
            g.fillRect(tabX.f, tabY.f, titleTabWidth.f, titleTab.height.f)

            // Draw the ship name tab on the right
            if (shipName != null) {
                g.fillRect(shipNameX.f, tabY.f, shipNameTabWidth.f, titleTab.height.f)
            }
        }, {
            drawBody(g)
            drawScrollbar(g)
        })

        UIUtils.drawTab(titleFont, title, titleTab, tabX.f, tabY.f, startWidth.f, endWidth.f)
        titleFont.drawString(tabX + startWidth.f, pos.y + 24f, title, Constants.JUMP_DISABLED_TEXT)

        // Draw the ship name tab on the left
        if (shipName != null) {
            // Draw mirrored
            g.pushTransform()
            g.translate(pos.x + size.x + 7f, tabY.f)
            g.scale(-1f, 1f)
            UIUtils.drawTab(titleFont, shipName, titleTab, 0f, 0f, startWidth.f, endWidth.f)
            g.popTransform()

            // Space by endWidth, since it's the left-most one
            titleFont.drawString(shipNameX + endWidth.f, pos.y + 24f, shipName, Constants.JUMP_DISABLED_TEXT)
        }
    }

    private fun drawBody(g: Graphics) {
        // Each ship's cell, left to right, starting a new row when the next
        // doesn't fit. The bigger ships' are half as big again, with their
        // list pictures (made by tools/hangar_art.py from their hulls, as
        // crisp as their miniatures), a little apart from the others.
        var cellX = 0
        var cellY = 0
        var rowHeight = 0

        hovered = null

        var previousWasBig = false
        for (ship in ships) {
            val isBig = ship.shipSize > 1
            // (a bigger ship without a list picture has its miniature, in a usual cell)
            val bigImg = if (isBig) state.getImgOrNull("img/customizeUI/xftl_list_${ship.img}.png") else null
            val cellWidth = if (bigImg != null) BIG_CELL_WIDTH else CELL_WIDTH
            val cellHeight = if (bigImg != null) BIG_CELL_HEIGHT else CELL_HEIGHT

            // Start a new row after the bigger ships, or when it's full
            if (previousWasBig && !isBig) {
                if (cellX > 0) {
                    cellX = 0
                    cellY += rowHeight
                    rowHeight = 0
                }
                cellY += BIG_SHIPS_GAP
            } else if (cellX > 0 && 30 + cellX + cellWidth > size.x) {
                cellX = 0
                cellY += rowHeight
                rowHeight = 0
            }
            previousWasBig = isBig

            val x = pos.x + 30 + cellX
            val y = pos.y + 50 + cellY - offsetY

            // Filter out any ships with missing images here, though there
            // hopefully shouldn't be any. Mods adding a ship need to add one.
            val miniPath = "img/customizeUI/miniship_${ship.img}.png"
            val img = bigImg ?: state.getImgOrNull(miniPath)
            if (img == null) {
                if (warnedMissingImages.add(ship.name))
                    println("[WARN] Ship '${ship.name}' isn't in the ship list, as it's missing '$miniPath'")
                continue
            }
            img.draw(x, y)

            cellX += cellWidth
            rowHeight = maxOf(rowHeight, cellHeight)

            // The last ship to set this will always be the lowest one.
            // Also, cancel out the scroll here - this is used to determine.
            // how much we're allowed to scroll.
            maxY = y + img.height + offsetY

            if (
                state.mousePos.x - pos.x in 0..size.x && state.mousePos.y - pos.y in 0..size.y &&
                state.mousePos.x - x in 0 until cellWidth && state.mousePos.y - y in 0 until cellHeight
            ) {
                hovered = ship
            }
        }
    }

    /**
     * A bar on the right showing how far down the list it's scrolled (with
     * the mouse wheel), when it's longer than the window.
     */
    private fun drawScrollbar(g: Graphics) {
        val listHeight = maxY - pos.y + SCROLL_MARGIN
        if (listHeight <= size.y)
            return
        val trackTop = pos.y + 40
        val trackHeight = size.y - 50
        val thumbHeight = (trackHeight * size.y / listHeight).coerceAtLeast(20)
        val thumbTop = trackTop + (trackHeight - thumbHeight) * offsetY / (listHeight - size.y)
        val x = pos.x + size.x - 14f
        g.colour = Colour(255, 255, 255, 40)
        g.fillRect(x, trackTop.f, SCROLLBAR_WIDTH.f, trackHeight.f)
        g.colour = Constants.UI_BUTTON_HOVER
        g.fillRect(x, thumbTop.f, SCROLLBAR_WIDTH.f, thumbHeight.f)
    }

    fun mouseClicked(button: Int) {
        if (button != Input.MOUSE_LEFT_BUTTON)
            return

        // Clicking outside the window closes it
        if (state.mousePos.x - pos.x !in 0..size.x || state.mousePos.y - pos.y !in 0..size.y) {
            close(null)
            return
        }

        hovered?.let { close(it) }
    }

    fun mouseWheelMoved(change: Int) {
        val maxScroll = maxY - (pos.y + size.y) + SCROLL_MARGIN
        if (maxScroll <= 0) {
            offsetY = 0
            return
        }
        offsetY = (offsetY - change / 5).coerceIn(0..maxScroll)
    }

    companion object {
        /**
         * The races whose big ships (PLAYER_SHIP_XFTL_<race>, and their B
         * variants) are listed last, in this order; the rest keep the order
         * their blueprints are in.
         */
        private val LAST_RACES = listOf("LANIUS", "CRYSTAL")

        // How far past the last row the list scrolls, and the scrollbar's width
        private const val SCROLL_MARGIN = 30

        // The gap between the bigger ships' rows and the others'
        private const val BIG_SHIPS_GAP = 30

        // A ship's cell in the list (a miniature's 191x121), and a bigger
        // ship's, half as big again (its list picture's 286x182)
        private const val CELL_WIDTH = 200
        private const val CELL_HEIGHT = 120
        private const val BIG_CELL_WIDTH = 300
        private const val BIG_CELL_HEIGHT = 185
        private const val SCROLLBAR_WIDTH = 6
    }
}
