package xyz.znix.xftl.hangar

import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.weapons.DroneBlueprint

// TODO this is copy-pasted from WeaponPanel, deduplicate.

class DronePanel(val editor: ShipEditor) : UIObject {
    override val selectPriority: Int get() = -1

    // Ships can have several drones systems, each with its own drone slots
    // (droneSlots of them), which are shown in a row each.
    private val slots = (0 until MAX_ROWS).flatMap { row -> (0 until MAX_COLUMNS).map { DroneSlot(row, it) } }

    /**
     * The number of drones systems the ship has room for, which is one
     * row of slots each.
     */
    private val rows: Int
        get() = editor.ship.rooms.count { it.system?.type == "drones" }.coerceIn(1, MAX_ROWS)

    private val columns: Int get() = editor.ship.droneSlots.coerceIn(3, MAX_COLUMNS)

    val width: Int get() = 20 + columns * 115
    val height: Int get() = 135 + (rows - 1) * ROW_HEIGHT

    var baseX: Int = 0 // Set by ShipEditor

    private var baseY: Int = 0

    override fun draw(g: Graphics) {
        baseY = editor.editorHeight - height - 10
        val title = editor.state.translator["equipment_frame_drones"]
        editor.state.windowRenderer.renderWithTitleTab(
            g, editor.titleTab, editor.titleFont,
            baseX, baseY,
            width, height,
            title
        )
    }

    override fun canSelectFrom(mouseX: Int, mouseY: Int): Boolean {
        return false
    }

    fun addObjects(newObjects: ArrayList<UIObject>) {
        newObjects += this
        newObjects += slots
    }

    private inner class DroneSlot(val row: Int, val column: Int) : UIObject {
        override val selectPriority: Int get() = 0

        val x: Int get() = baseX + 10 + column * 115
        val y: Int get() = baseY + 40 + row * ROW_HEIGHT
        val isDisabled get() = editor.ship.droneSlots <= column
        val isHidden get() = row >= rows || column >= columns

        /**
         * This slot's index in the ship's list of drones. The drones fill
         * each drones system in turn, as they do when the game starts.
         */
        val slot: Int get() = row * editor.ship.droneSlots + column

        val image = editor.state.getImg("img/upgradeUI/Equipment/box_drones_on.png")
        val imageHover = editor.state.getImg("img/upgradeUI/Equipment/box_drones_selected.png")
        val imageDisabled = editor.state.getImg("img/upgradeUI/Equipment/box_drones_off.png")

        override fun draw(g: Graphics) {
            if (isHidden)
                return

            val drone = editor.ship.drones.getOrNull(slot)
                ?.takeIf { !isDisabled && it != EMPTY_SLOT }
                ?.let { editor.state.blueprints[it] as DroneBlueprint }

            val hovered = editor.isHovered(this) && !isDisabled

            val img = when {
                isDisabled -> imageDisabled
                hovered -> imageHover
                else -> image
            }

            if (drone == null) {
                img.draw(x, y)
            } else {
                BlueprintSelector.drawDroneCard(g, editor, x, y, drone, img)
            }
        }

        override fun canSelectFrom(mouseX: Int, mouseY: Int): Boolean {
            return false
        }

        override fun canHover(mouseX: Int, mouseY: Int): Boolean {
            if (isHidden)
                return false
            return mouseX - x in 9..image.width && mouseY - y in 9..image.height
        }

        override fun onLeftClick(x: Int, y: Int) {
            if (!canHover(x, y) || isDisabled)
                return

            // Capture the index now, in case the slots change while the menu is open
            val slot = this.slot

            val controller = object : BlueprintSelector.SelectionController {
                override val title: String get() = "SELECT DRONE SLOT ${slot + 1}"

                override fun select(blueprint: Blueprint) {
                    // Cast the blueprint back to a DRONE
                    require(blueprint is DroneBlueprint)

                    val shipDrones = editor.ship.drones

                    // Pad out the list, so this can fill a slot in the second
                    // drones system before the first one's full.
                    while (shipDrones.size < slot) {
                        shipDrones.add(EMPTY_SLOT)
                    }

                    if (shipDrones.size == slot) {
                        shipDrones.add(blueprint.name)
                    } else {
                        shipDrones[slot] = blueprint.name
                    }
                }
            }

            editor.openMenu(BlueprintSelector(editor, editor.droneBlueprints, controller))
        }

        override fun onRightClick(x: Int, y: Int) {
            if (isHidden)
                return

            val hasDrone = editor.ship.drones.getOrNull(slot).let { it != null && it != EMPTY_SLOT }
            val clearSlot: PopupMenu.Entry? = when (hasDrone && !isDisabled) {
                true -> PopupMenu.Entry("Clear slot", onClick = this::clearSlot)
                false -> null
            }

            val lockSlot = when (isDisabled) {
                true -> PopupMenu.Entry("Enable drone slot", onClick = this::enableSlot)
                false -> PopupMenu.Entry("Disable drone slot", onClick = this::disableSlot)
            }

            editor.openPopupMenu(
                clearSlot,
                lockSlot
            )
        }

        // The number of slots is the same for every drones system, so these
        // change all the rows at once.
        private fun enableSlot() {
            setDroneSlots(column + 1)
        }

        private fun disableSlot() {
            setDroneSlots(column)
        }

        private fun clearSlot() {
            // Check the index is still valid, in case the drone was somehow
            // removed before the popup menu was closed. Leave a gap, so the
            // drones after it stay in the same slots.
            val drones = editor.ship.drones
            if (drones.indices.contains(slot))
                drones[slot] = EMPTY_SLOT
            while (drones.lastOrNull() == EMPTY_SLOT)
                drones.removeAt(drones.lastIndex)
        }
    }

    /**
     * Change the number of slots in each drones system, keeping each
     * drone in the same system, and dropping those that no longer fit.
     */
    private fun setDroneSlots(newSlots: Int) {
        val ship = editor.ship
        val old = ship.droneSlots
        val byRow = (0 until rows).map { row ->
            (0 until minOf(old, newSlots)).map { ship.drones.getOrNull(row * old + it) ?: EMPTY_SLOT }
        }

        ship.droneSlots = newSlots
        ship.drones.clear()
        for (row in byRow) {
            ship.drones += row
            repeat(newSlots - row.size) { ship.drones += EMPTY_SLOT }
        }
        while (ship.drones.lastOrNull() == EMPTY_SLOT)
            ship.drones.removeAt(ship.drones.lastIndex)
    }

    companion object {
        private const val MAX_ROWS = 2
        private const val MAX_COLUMNS = 4

        // The drone box images are 76px tall, the same as the weapon ones
        private const val ROW_HEIGHT = 85

        /**
         * The name used in the ship's drone list for an empty slot, so the
         * drones after it stay in the same slots.
         */
        const val EMPTY_SLOT = ""
    }
}
