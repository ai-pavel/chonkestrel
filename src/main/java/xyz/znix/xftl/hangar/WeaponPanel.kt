package xyz.znix.xftl.hangar

import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint

class WeaponPanel(val editor: ShipEditor) : UIObject {
    override val selectPriority: Int get() = -1

    private val slots = (0 until MAX_SLOTS).map { WeaponSlot(it) }

    /**
     * The number of slots shown. This is normally four, but ships can have
     * more, in which case they're shown in extra rows of four.
     */
    private val visibleSlots: Int get() = editor.ship.weaponSlots.coerceIn(4, MAX_SLOTS)

    private val rows: Int get() = (visibleSlots + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW

    val width = 20 + SLOTS_PER_ROW * 115
    val height: Int get() = 135 + (rows - 1) * ROW_HEIGHT

    var baseX: Int = 0 // Set by ShipEditor

    private var baseY: Int = 0

    override fun draw(g: Graphics) {
        baseY = editor.editorHeight - height - 10
        val title = editor.state.translator["equipment_frame_weapons"]
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

    private inner class WeaponSlot(val slot: Int) : UIObject {
        override val selectPriority: Int get() = 0

        val x: Int get() = baseX + 10 + (slot % SLOTS_PER_ROW) * 115
        val y: Int get() = baseY + 40 + (slot / SLOTS_PER_ROW) * ROW_HEIGHT
        val isDisabled get() = editor.ship.weaponSlots <= slot
        val isHidden get() = slot >= visibleSlots

        val image = editor.state.getImg("img/upgradeUI/Equipment/box_weapons_on.png")
        val imageHover = editor.state.getImg("img/upgradeUI/Equipment/box_weapons_selected.png")
        val imageDisabled = editor.state.getImg("img/upgradeUI/Equipment/box_weapons_off.png")

        override fun draw(g: Graphics) {
            if (isHidden)
                return

            // Empty slots (before a later weapon) are empty names
            val weapon = editor.ship.weapons.getOrNull(slot)?.takeIf { it.isNotEmpty() }
                ?.let { editor.state.blueprints[it] as AbstractWeaponBlueprint }

            val hovered = editor.isHovered(this) && !isDisabled

            val img = when {
                isDisabled -> imageDisabled
                hovered -> imageHover
                else -> image
            }

            if (weapon == null) {
                img.draw(x, y)
            } else {
                BlueprintSelector.drawWeaponCard(g, editor, x, y, weapon, img)
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

            val controller = object : BlueprintSelector.SelectionController {
                override val title: String get() = "SELECT WEAPON SLOT ${slot + 1}"

                override fun select(blueprint: Blueprint) {
                    // Cast the blueprint back to a weapon
                    require(blueprint is AbstractWeaponBlueprint)

                    val shipWeapons = editor.ship.weapons

                    if (shipWeapons.size <= slot) {
                        shipWeapons.add(blueprint.name)
                    } else {
                        shipWeapons[slot] = blueprint.name
                    }
                }
            }

            editor.openMenu(BlueprintSelector(editor, editor.weaponBlueprints, controller))
        }

        override fun onRightClick(x: Int, y: Int) {
            val clearSlot: PopupMenu.Entry? = when (slot < editor.ship.weapons.size) {
                true -> PopupMenu.Entry("Clear slot", onClick = this::clearSlot)
                false -> null
            }

            val lockSlot = when (isDisabled) {
                true -> PopupMenu.Entry("Enable weapon slot", onClick = this::enableSlot)
                false -> PopupMenu.Entry("Disable weapon slot", onClick = this::disableSlot)
            }

            editor.openPopupMenu(
                clearSlot,
                lockSlot
            )
        }

        private fun enableSlot() {
            editor.ship.weaponSlots = slot + 1
        }

        private fun disableSlot() {
            editor.ship.weaponSlots = slot

            // Clear weapons from locked slots
            val weaponList = editor.ship.weapons
            while (weaponList.size > editor.ship.weaponSlots) {
                weaponList.remove(weaponList.last())
            }
        }

        private fun clearSlot() {
            // Check the index is still valid, in case the weapon was somehow
            // removed before the popup menu was closed.
            if (editor.ship.weapons.indices.contains(slot))
                editor.ship.weapons.removeAt(slot)
        }
    }

    companion object {
        const val MAX_SLOTS = 8
        private const val SLOTS_PER_ROW = 4

        // The weapon box images are 76px tall
        private const val ROW_HEIGHT = 85
    }
}
