package xyz.znix.xftl.game

import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Constants
import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.ui.ImageView
import xyz.znix.xftl.ui.Label
import xyz.znix.xftl.ui.WidgetContainer
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import kotlin.math.min

/**
 * This contains the drag-and-drop equipment UI used in the equipment tab
 * of the player ship UI, and in the sell tab of the store.
 */
class ShipEquipmentPanel(private val game: InGameState, val ship: Ship) {
    var position: IPoint = ConstPoint.ZERO
        set(value) {
            if (field == value)
                return
            field = value
            updateButtons()
        }

    var sellUI: Boolean = false

    private val sectionFont = game.getFont("HL2", 2f)
    private val missingSystemFont = game.getFont("HL2", 3f)
    private val sellBoxFont = game.getFont("HL1", 2f)
    private val sellPriceFont = game.getFont("num_font")
    private val augmentFont = game.getFont("JustinFont12Bold")

    private val sellSound = game.sounds.getSample("sell")

    private val buttons = ArrayList<Button>()
    private val weaponButtons = ArrayList<Buttons.DragDropBlueprintButton>()
    private val droneButtons = ArrayList<Buttons.DragDropBlueprintButton>()

    // The drones system and slot of each of the drone buttons. Ships can
    // have several drones systems, which all show their slots in one row.
    private val droneButtonSlots = ArrayList<Pair<Drones, Int>>()
    private val augmentButtons = ArrayList<Buttons.DragDropBlueprintButton>()
    private val cargoButtons = ArrayList<Buttons.DragDropBlueprintButton>()
    private var sellButton: Button? = null // The box for selling and leaving behind items
    private val overflowItemButtons = ArrayList<Buttons.DragDropBlueprintButton>()
    private val overflowAugmentButtons = ArrayList<Buttons.DragDropBlueprintButton>()

    // The over capacity boxes' titles, which something the player doesn't
    // want can be dropped on, to leave it behind with the rest
    private var overflowItemBox: Button? = null
    private var overflowAugmentBox: Button? = null

    // The frame around the over capacity boxes (x, y, width, height,
    // relative to the panel), or null if there aren't any
    private var overflowFrame: IntArray? = null

    private var draggingBlueprint: Buttons.DragDropBlueprintButton? = null
        set(value) {
            field = value
            for (button in buttons) {
                if (button !is Buttons.DragDropBlueprintButton)
                    continue
                button.currentlyDraggedBlueprint = value?.blueprint
            }
        }

    /**
     * The number of rows of weapons. Ships with more than four weapon slots
     * get extra rows, which makes the panel taller.
     */
    private val weaponRows: Int
        get() = ((ship.weaponSlots ?: 0) + WEAPONS_PER_ROW - 1) / WEAPONS_PER_ROW

    /**
     * How much taller the weapons section is than in vanilla, to fit the
     * extra weapon rows. Everything below the weapons is moved down by this.
     */
    private val weaponsExtraHeight: Int get() = (weaponRows - 1).coerceAtLeast(0) * WEAPON_ROW_HEIGHT

    /**
     * The number of drones systems the ship has room for.
     */
    private val droneSystemSlots: Int
        get() = ship.systemSlots.count { it.system.info == Drones.INFO }

    /**
     * Whether each drones system's slots go in their own row. If there's
     * too many drone slots to fit in one row, as in vanilla, then each
     * drones system gets its own row.
     *
     * This is based on the systems the ship has room for, not those it has,
     * so the panel doesn't change size when a drones system is bought.
     */
    private val droneRowPerSystem: Boolean
        get() = droneSystemSlots * (ship.droneSlots ?: 0) > DRONES_PER_ROW

    /**
     * How much taller the drones section is than in vanilla, to fit the
     * extra drone rows.
     */
    private val dronesExtraHeight: Int
        get() = if (droneRowPerSystem) (droneSystemSlots - 1) * WEAPON_ROW_HEIGHT else 0

    /**
     * How much the cargo and augments sections are moved down by, to fit
     * the extra weapon and drone rows.
     */
    private val middleExtraHeight: Int get() = weaponsExtraHeight + dronesExtraHeight

    /**
     * How much taller the cargo and augments sections are than in vanilla,
     * to fit ships with more cargo slots (in rows of two) or augments.
     */
    private val bottomExtraHeight: Int
        get() {
            val cargoRows = (ship.cargoBlueprints.size + CARGO_PER_ROW - 1) / CARGO_PER_ROW
            val cargoExtra = (cargoRows - VANILLA_CARGO_ROWS).coerceAtLeast(0) * CARGO_ROW_HEIGHT
            val augmentsExtra = (ship.maxAugments - Ship.DEFAULT_MAX_AUGMENTS).coerceAtLeast(0) * AUGMENT_ROW_HEIGHT
            return maxOf(cargoExtra, augmentsExtra)
        }

    /**
     * How much taller this panel is than in vanilla, to fit the extra
     * weapon slots, drone slots, cargo slots and augments.
     */
    val extraHeight: Int get() = middleExtraHeight + bottomExtraHeight

    /**
     * Draw the window's background image, stretching its weapons, cargo and
     * augments sections to fit [extraHeight].
     *
     * [imagePos] is where the image would normally be drawn.
     */
    fun drawBackground(image: Image, imagePos: IPoint) {
        // Convert the split positions from panel to image coordinates
        val offset = position.y - imagePos.y
        image.drawWithInsertions(
            imagePos.x, imagePos.y, emptyList(), listOf(
                Image.Insertion(offset + BACKGROUND_SPLIT_Y, BACKGROUND_CHUNK_HEIGHT, weaponsExtraHeight),
                Image.Insertion(offset + DRONES_BACKGROUND_SPLIT_Y, BACKGROUND_CHUNK_HEIGHT, dronesExtraHeight),
                Image.Insertion(offset + BOTTOM_BACKGROUND_SPLIT_Y, BOTTOM_BACKGROUND_CHUNK_HEIGHT, bottomExtraHeight),
            )
        )
    }

    // Note: the buttons are first populated when the position is set

    fun draw(g: Graphics) {
        sectionFont.drawString(
            position.x + 11f,
            position.y + 60f,
            game.translator["equipment_frame_weapons"],
            Constants.JUMP_DISABLED_TEXT
        )
        sectionFont.drawString(
            position.x + 11f,
            position.y + 170f + weaponsExtraHeight,
            game.translator["equipment_frame_drones"],
            Constants.JUMP_DISABLED_TEXT
        )
        sectionFont.drawString(
            position.x + 11f,
            position.y + 280f + middleExtraHeight,
            game.translator["equipment_frame_cargo"],
            Constants.JUMP_DISABLED_TEXT
        )
        sectionFont.drawString(
            position.x + 300f,
            position.y + 280f + middleExtraHeight,
            game.translator["equipment_frame_augments"],
            Constants.JUMP_DISABLED_TEXT
        )

        if (ship.drones == null) {
            missingSystemFont.drawString(
                position.x + 108f,
                position.y + 227f + weaponsExtraHeight,
                game.translator["equipment_no_system"],
                Constants.SECTOR_CUTOUT_TEXT
            )
        }

        overflowFrame?.let { (x, y, w, h) ->
            game.windowRenderer.render(position.x + x, position.y + y, w, h)
        }

        for (button in buttons) {
            button.draw(g)
        }
    }

    fun drawDrag(g: Graphics) {
        // Draw the dragged bit of equipment above everything else
        draggingBlueprint?.drawDrag(g)
    }

    fun shipModified() {
        updateButtons()
    }

    private fun updateButtons() {
        buttons.clear()

        // Draw the weapons
        // Note: I think all the playable ships have weaponSlots set?
        weaponButtons.clear()
        for (i in 0 until ship.weaponSlots!!) {
            val weapon = ship.hardpoints[i].weapon

            val images = ButtonImageSet.selected(game, "img/upgradeUI/Equipment/box_weapons", true)
            val padding = 8
            // Ships with more than four weapon slots have them split
            // into rows of four.
            val perRow = min(ship.weaponSlots!!, WEAPONS_PER_ROW)
            val baseX = (579 - (images.normal.width + padding) * perRow) / 2
            val buttonPos = ConstPoint(
                baseX + (i % WEAPONS_PER_ROW) * (images.normal.width + padding),
                70 + (i / WEAPONS_PER_ROW) * WEAPON_ROW_HEIGHT
            )

            // Use a separate variable so we can use the button in its callback.
            lateinit var button: Buttons.DragDropBlueprintButton
            button = Buttons.DragDropBlueprintButton(
                buttonPos, game, images,
                { it is AbstractWeaponBlueprint },
                weapon?.type
            ) {
                draggingBlueprint = button
            }
            buttons += button
            weaponButtons += button
        }

        // Draw the drones
        droneButtons.clear()
        droneButtonSlots.clear()
        // Either all the drones systems' slots go in one row, or each
        // system gets its own row.
        val droneRows = ship.droneSystems
            .map { system -> system.drones.indices.map { Pair(system, it) } }
            .let { if (droneRowPerSystem) it else listOf(it.flatten()) }
        for ((row, droneSlots) in droneRows.withIndex()) {
            val images = ButtonImageSet.selected(game, "img/upgradeUI/Equipment/box_drones", true)
            val padding = 8
            val baseX = (579 - (images.normal.width + padding) * droneSlots.size) / 2

            for ((i, systemSlot) in droneSlots.withIndex()) {
                val (droneSystem, slot) = systemSlot
                val buttonPos = ConstPoint(
                    baseX + i * (images.normal.width + padding),
                    180 + weaponsExtraHeight + row * WEAPON_ROW_HEIGHT
                )
                val drone = droneSystem.drones[slot]?.type

                // Use a separate variable so we can use the button in it's callback.
                lateinit var button: Buttons.DragDropBlueprintButton
                button = Buttons.DragDropBlueprintButton(buttonPos, game, images, { it is DroneBlueprint }, drone) {
                    draggingBlueprint = button
                }
                buttons += button
                droneButtons += button
                droneButtonSlots += systemSlot
            }
        }

        // Draw the augments area
        augmentButtons.clear()
        for (i in 0 until ship.maxAugments) {
            val augment = ship.augments.getOrNull(i)

            val buttonPos = ConstPoint(323, 293 + middleExtraHeight + i * 60)

            val button = augmentButton(buttonPos, augment)
            buttons += button
            augmentButtons += button
        }

        // Draw the cargo area, in rows of two
        cargoButtons.clear()
        for (i in ship.cargoBlueprints.indices) {
            val blueprint = ship.cargoBlueprints[i]

            val buttonPos = ConstPoint(
                23 + 130 * (i % CARGO_PER_ROW),
                293 + middleExtraHeight + CARGO_ROW_HEIGHT * (i / CARGO_PER_ROW)
            )

            val button = cargoButton(buttonPos, blueprint)
            buttons += button
            cargoButtons += button
        }

        // Draw the sell/leave behind box - do this here since
        // the sell and leave behind (when you get a piece of
        // equipment but your cargo is full) UIs are similar.
        sellButton = null
        if (sellUI) {
            sellButton = SellDropBox.create(
                game,
                SellDropBox.Type.SELL_EQUIPMENT,
                ConstPoint(-275, 107)
            ) { draggingBlueprint?.blueprint }

            buttons += sellButton!!
        }

        // The over capacity boxes, for the items and augments the ship got
        // with no room for them (see Ship.overflowItems), together in a
        // frame left of the window, below the sell box if there is one
        overflowItemButtons.clear()
        overflowAugmentButtons.clear()
        overflowItemBox = null
        overflowAugmentBox = null
        overflowFrame = null
        if (ship.overflowItems.isNotEmpty() || ship.overflowAugments.isNotEmpty()) {
            val top = OVERFLOW_TOP + (sellButton?.let { it.size.y + OVERFLOW_SPACING * 3 } ?: 0)
            var y = top
            var width = 0
            if (ship.overflowItems.isNotEmpty()) {
                val box = SellDropBox.create(game, SellDropBox.Type.TOO_MUCH_EQUIPMENT, ConstPoint(OVERFLOW_X, y)) {
                    draggingBlueprint?.blueprint?.takeIf { it !is AugmentBlueprint }
                }
                buttons += box
                overflowItemBox = box
                width = maxOf(width, box.size.x)
                y += box.size.y + OVERFLOW_SPACING
                // In rows of two, like the cargo
                val cardWidth = game.getImg("img/upgradeUI/Equipment/box_base_off.png").width
                val rowWidth = cardWidth + OVERFLOW_CARGO_SPACING
                for ((i, item) in ship.overflowItems.withIndex()) {
                    val inRow = minOf(CARGO_PER_ROW, ship.overflowItems.size - i / CARGO_PER_ROW * CARGO_PER_ROW)
                    val rowX = OVERFLOW_X + (box.size.x - (inRow * rowWidth - OVERFLOW_CARGO_SPACING)) / 2
                    val pos = ConstPoint(rowX + i % CARGO_PER_ROW * rowWidth, y + i / CARGO_PER_ROW * CARGO_ROW_HEIGHT)
                    val button = cargoButton(pos, item)
                    buttons += button
                    overflowItemButtons += button
                }
                y += (ship.overflowItems.size + CARGO_PER_ROW - 1) / CARGO_PER_ROW * CARGO_ROW_HEIGHT + OVERFLOW_SPACING
            }
            if (ship.overflowAugments.isNotEmpty()) {
                val box = SellDropBox.create(game, SellDropBox.Type.TOO_MANY_AUGMENTS, ConstPoint(OVERFLOW_X, y)) {
                    draggingBlueprint?.blueprint as? AugmentBlueprint
                }
                buttons += box
                overflowAugmentBox = box
                width = maxOf(width, box.size.x)
                y += box.size.y + OVERFLOW_SPACING
                for (augment in ship.overflowAugments) {
                    val button = augmentButton(ConstPoint(OVERFLOW_X + (box.size.x - AUGMENT_BOX_WIDTH) / 2, y), augment)
                    buttons += button
                    overflowAugmentButtons += button
                    y += AUGMENT_ROW_HEIGHT
                }
            }
            overflowFrame = intArrayOf(
                OVERFLOW_X - OVERFLOW_FRAME_PADDING, top - OVERFLOW_FRAME_PADDING,
                width + 2 * OVERFLOW_FRAME_PADDING, y - top + OVERFLOW_FRAME_PADDING
            )
        }

        for (button in buttons) {
            button.windowOffset = position
        }
    }

    private fun augmentButton(buttonPos: IPoint, augment: Blueprint?): Buttons.DragDropBlueprintButton {
        val size = ConstPoint(AUGMENT_BOX_WIDTH, 40)

        // Use a separate variable so we can use the button in its callback.
        lateinit var button: Buttons.DragDropBlueprintButton
        button = object : Buttons.DragDropBlueprintButton(
            buttonPos, game, null, size,
            { it is AugmentBlueprint },
            augment,
            { draggingBlueprint = button }
        ) {
            override fun draw(g: Graphics) {
                // Draw the empty box
                g.colour = Constants.AUGMENT_EMPTY_OUTLINE
                g.fillRect(pos.x.f, pos.y.f, size.x.f, size.y.f)
                g.colour = Constants.AUGMENT_EMPTY_INSIDE
                g.fillRect(pos.x + 3f, pos.y + 3f, size.x - 6f, size.y - 6f)

                // Draw the semi-transparent augment on top of it
                if (dragPosition == null) {
                    drawAugment(g, pos)
                }
            }

            override fun drawDrag(g: Graphics) {
                drawAugment(g, dragPosition ?: return)
            }

            private fun drawAugment(g: Graphics, pos: IPoint) {
                val aug = blueprint as AugmentBlueprint? ?: return

                // Draw the borders. Since the middle is semi-transparent, we can't
                // just fill in the whole thing twice to get our border easily.
                g.colour = when {
                    dragPosition != null -> Constants.AUGMENT_BOX_OUTLINE
                    hovered -> Constants.AUGMENT_BOX_OUTLINE_HOVER
                    else -> Constants.AUGMENT_BOX_OUTLINE
                }
                // Left and right
                g.fillRect(pos.x + 0f, pos.y + 0f, 3f, size.y.f)
                g.fillRect(pos.x + size.x - 3f, pos.y + 0f, 3f, size.y.f)

                // Top and bottom
                g.fillRect(pos.x + 3f, pos.y + 0f, size.x - 6f, 3f)
                g.fillRect(pos.x + 3f, pos.y + size.y - 3f, size.x - 6f, 3f)

                // Fill in the background
                g.colour = Constants.AUGMENT_BOX_INSIDE
                g.fillRect(pos.x + 3f, pos.y + 3f, size.x - 6f, size.y - 6f)

                // Draw the name
                val name = aug.translateTitle(game)
                // (its capitals are 12px tall, centred in the 40px box)
                augmentFont.drawStringCentred(pos.x.f, pos.y.f + 26f, size.x.f, name, Constants.AUGMENT_NAME_TEXT)
            }
        }
        return button
    }

    private fun cargoButton(buttonPos: IPoint, blueprint: Blueprint?): Buttons.DragDropBlueprintButton {
        val weapons = ButtonImageSet.selected(game, "img/upgradeUI/Equipment/box_weapons", true)
        val drones = ButtonImageSet.selected(game, "img/upgradeUI/Equipment/box_drones", true)

        val base = ButtonImageSet(
            game.getImg("img/upgradeUI/Equipment/box_base_off.png"),
            game.getImg("img/upgradeUI/Equipment/box_base_off.png"), // There's no 'on' image
            game.getImg("img/upgradeUI/Equipment/box_base_off_selected.png")
        )

        // Use a separate variable so we can use the button in it's callback.
        lateinit var button: Buttons.DragDropBlueprintButton
        button = object : Buttons.DragDropBlueprintButton(
            buttonPos, game, base,
            { it !is AugmentBlueprint },
            blueprint,
            { draggingBlueprint = button }) {

            // Change the card image depending on our contents
            override val image: ButtonImageSet
                get() {
                    // If our contents is being dragged, don't keep the weapon/drone
                    // icon on the card.
                    if (dragPosition != null)
                        return base

                    return when (this.blueprint) {
                        is AbstractWeaponBlueprint -> weapons
                        is DroneBlueprint -> drones
                        else -> base
                    }
                }
        }
        return button
    }

    fun updateUI(x: Int, y: Int) {
        for (button in buttons) {
            button.update(x, y)
        }

        // If we're dragging a blueprint around, update it.
        draggingBlueprint?.dragPosition = ConstPoint(x, y)
    }

    fun mouseClick(button: Int, x: Int, y: Int) {
        // Copied from Window.
        // Mouse clicking may change the buttons array (eg in the store
        // window when switching tabs), so copy it.
        for (btn in ArrayList(buttons)) {
            btn.mouseDown(button, x, y)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun mouseReleased(button: Int, x: Int, y: Int) {
        if (draggingBlueprint != null)
            dropBlueprint()
    }

    fun getHoveredBlueprint(): Blueprint? {
        // Don't show anything while a blueprint is being dragged.
        if (draggingBlueprint != null)
            return null

        val hoveredButton = buttons.firstOrNull { it.hovered }
        if (hoveredButton !is Buttons.DragDropBlueprintButton)
            return null

        return hoveredButton.blueprint
    }

    fun drawInfoPanel(infoPanel: InfoPanel) {
        val blueprint = getHoveredBlueprint() ?: return
        return when (blueprint) {
            is DroneBlueprint -> infoPanel.drawDrone(blueprint)
            is AugmentBlueprint -> infoPanel.drawAugment(blueprint)
            is AbstractWeaponBlueprint -> infoPanel.drawWeapon(blueprint)
            else -> error("Cannot draw info panel for $blueprint")
        }
    }

    private fun dropBlueprint() {
        val drag = draggingBlueprint!!
        drag.dragPosition = null
        draggingBlueprint = null

        data class SlotAccess(
            val get: () -> Blueprint?,
            val set: (Blueprint?) -> Unit
        )

        fun getAccess(target: Button): SlotAccess? {
            // Check the weapons
            for ((i, button) in weaponButtons.withIndex()) {
                if (button != target)
                    continue

                return SlotAccess(
                    { ship.hardpoints[i].weapon?.type },
                    { ship.hardpoints[i].weapon = (it as? AbstractWeaponBlueprint)?.buildInstance(ship) }
                )
            }

            // Check the drone system
            for ((i, button) in droneButtons.withIndex()) {
                if (button != target)
                    continue

                val (drones, slot) = droneButtonSlots[i]

                return SlotAccess(
                    { drones.drones[slot]?.type },
                    { drone ->
                        // If the current slot contains an already-deployed drone, get rid of it.
                        drones.drones[slot]?.removeAll()

                        drones.drones[slot] = drone?.let { Drones.DroneInfo(it as DroneBlueprint, null) }
                    }
                )
            }

            // Check the augments
            for ((i, button) in augmentButtons.withIndex()) {
                if (button != target)
                    continue

                // Grab the augment here, in case the ordering changes
                val aug = ship.augments.getOrNull(i)

                return SlotAccess(
                    { aug },
                    {
                        require(it is AugmentBlueprint?)
                        if (aug != null) {
                            require(ship.augments.remove(aug)) { "Augment '${aug.name}' disappeared while dragging!" }
                        }
                        if (it != null) {
                            val index = min(ship.augments.size, i)
                            ship.augments.add(index, it)
                        }
                    }
                )
            }

            // Check the cargo bay
            for ((i, button) in cargoButtons.withIndex()) {
                if (button != target)
                    continue

                return SlotAccess(
                    { ship.cargoBlueprints[i] },
                    { ship.cargoBlueprints[i] = it }
                )
            }

            // Check the over capacity boxes
            fun <T : Blueprint> overflowAccess(list: MutableList<T>, i: Int) = SlotAccess(
                { list.getOrNull(i) },
                {
                    @Suppress("UNCHECKED_CAST")
                    if (it == null) list.removeAt(i) else list[i] = it as T
                }
            )
            val itemIndex = overflowItemButtons.indexOf(target)
            if (itemIndex >= 0)
                return overflowAccess(ship.overflowItems, itemIndex)
            val augmentIndex = overflowAugmentButtons.indexOf(target)
            if (augmentIndex >= 0)
                return overflowAccess(ship.overflowAugments, augmentIndex)
            if (target == overflowItemBox)
                return SlotAccess({ null }, { if (it != null) ship.overflowItems += it })
            if (target == overflowAugmentBox)
                return SlotAccess({ null }, { if (it != null) ship.overflowAugments += it as AugmentBlueprint })

            // Check the sell button
            if (target == sellButton) {
                return SlotAccess({ null }) {
                    // Give the player scrap for the item
                    ship.scrap += (it?.cost ?: 0) / 2

                    // Do nothing with it, thus destroying it.

                    sellSound.play()
                }
            }

            return null
        }

        // Find the blueprint the user is trying to drop the item into
        val hovered = buttons.firstOrNull { it.hovered } ?: return

        // Get access to where this blueprint is being dragged to and from
        val src = getAccess(drag) ?: return
        val dst = getAccess(hovered) ?: return

        // Make sure something crazy hasn't happened
        require(drag.blueprint == src.get())

        // Check if the blueprint will fit - the sell button is the exception
        // here, you can sell anything you can drag.
        if (hovered is Buttons.DragDropBlueprintButton && !hovered.compatible(drag.blueprint!!))
            return
        // The over capacity boxes only take what they're for, and not what
        // came from them
        if (hovered == overflowItemBox && (drag.blueprint is AugmentBlueprint || drag in overflowItemButtons))
            return
        if (hovered == overflowAugmentBox && (drag.blueprint !is AugmentBlueprint || drag in overflowAugmentButtons))
            return

        // If the user drops one blueprint onto another, they swap.
        // Make sure that'll work.
        val replacing = dst.get()
        if (replacing != null && !drag.compatible(replacing))
            return

        // Swap them
        dst.set(drag.blueprint)
        src.set(replacing)

        ship.cargoUpdated()
    }

    class SellDropBox private constructor(
        game: InGameState, pos: IPoint,
        private val type: Type,
        private val container: WidgetContainer,
        private val getDrag: () -> Blueprint?,
    ) :
        Button(game, pos, container.root.size) {

        override val makesHoverNoise: Boolean get() = false

        private val images: List<ImageView> = container.allWidgets.filterIsInstance(ImageView::class.java)

        private val highlightingLabels: List<Label> = listOfNotNull(
            container.byId["message"] as Label,
            container.byId["value_label"] as Label?,
            container.byId["price"] as Label?,
        )

        private val price = container.byId["price"] as Label?

        override fun draw(g: Graphics) {
            val normalPath = when (type) {
                Type.SELL_EQUIPMENT -> "img/dropbox_sell_on.png"
                else -> "img/dropbox_on.png"
            }
            val hoverPath = when (type) {
                Type.SELL_EQUIPMENT -> "img/dropbox_sell_select2.png"
                else -> "img/dropbox_select2.png"
            }

            val draggingBlueprint = getDrag()

            val hoverActive = hovered && draggingBlueprint != null
            val image = when {
                hoverActive -> game.getImg(hoverPath)
                else -> game.getImg(normalPath)
            }
            for (view in images) {
                view.image = image
            }

            val colour = when {
                hoverActive -> Constants.UI_BUTTON_HOVER
                else -> Constants.SECTOR_CUTOUT_TEXT
            }
            for (label in highlightingLabels) {
                label.colour = colour
            }

            if (price == null) {
                // For the over-capacity boxes
            } else if (draggingBlueprint != null) {
                // You only get half of what you paid for it
                val sellPrice = (draggingBlueprint.cost ?: 0) / 2
                price.text = sellPrice.toString()
            } else {
                price.text = ""
            }

            g.pushTransform()
            g.translate(pos.x.f, pos.y.f)
            container.draw(g)
            g.popTransform()
        }

        override fun click(button: Int) {
            // Clicking the sell box doesn't do anything
        }

        companion object {
            fun create(game: InGameState, type: Type, pos: IPoint, getDrag: () -> Blueprint?): SellDropBox {
                val widgetName = when (type) {
                    Type.SELL_EQUIPMENT -> "sell_drop_box"
                    // (the same box, with the item's message)
                    Type.TOO_MUCH_EQUIPMENT, Type.TOO_MANY_AUGMENTS -> "crew_over_capacity"
                    Type.TOO_MANY_CREW -> "crew_over_capacity"
                }

                val widget: WidgetContainer = game.uiLoader.load(widgetName).mainWidget

                val titleKey = when (type) {
                    Type.SELL_EQUIPMENT -> "sell_box_title"
                    Type.TOO_MUCH_EQUIPMENT -> "overcapacity_item_title"
                    Type.TOO_MANY_AUGMENTS -> "overcapacity_aug_title"
                    Type.TOO_MANY_CREW -> "overcapacity_crew_title"
                }
                val messageKey = when (type) {
                    Type.SELL_EQUIPMENT -> "sell_box_text"
                    Type.TOO_MUCH_EQUIPMENT -> "overcapacity_item_text1"
                    Type.TOO_MANY_AUGMENTS -> "overcapacity_aug_text1"
                    Type.TOO_MANY_CREW -> "overcapacity_crew_text"
                }

                (widget.byId["title"] as Label).text = game.translator[titleKey]
                // The item boxes also say how to swap
                val secondKey = when (type) {
                    Type.TOO_MUCH_EQUIPMENT -> "overcapacity_item_text2"
                    Type.TOO_MANY_AUGMENTS -> "overcapacity_aug_text2"
                    else -> null
                }
                (widget.byId["message"] as Label).text = game.translator[messageKey] +
                        (secondKey?.let { "\n\n" + game.translator[it] } ?: "")

                widget.updateLayout()

                return SellDropBox(game, pos, type, widget, getDrag)
            }
        }

        enum class Type {
            SELL_EQUIPMENT,
            TOO_MUCH_EQUIPMENT,
            TOO_MANY_AUGMENTS,
            TOO_MANY_CREW,
        }
    }

    companion object {
        private const val WEAPONS_PER_ROW = 4

        // The weapon box images are 76px tall
        private const val WEAPON_ROW_HEIGHT = 80

        // Where to split the background to insert the extra weapon rows,
        // in panel coordinates. This is inside the weapons section, and
        // the chunk above it is repeated to fill the gap.
        private const val BACKGROUND_SPLIT_Y = 140
        private const val BACKGROUND_CHUNK_HEIGHT = 50

        // The drone boxes are the same size as the weapon boxes, and four
        // fit in the panel's width.
        private const val DRONES_PER_ROW = 4

        // Where to split the background to insert the extra drone rows,
        // like for the weapons. This is inside the drones section.
        private const val DRONES_BACKGROUND_SPLIT_Y = 250

        private const val CARGO_PER_ROW = 2
        private const val CARGO_ROW_HEIGHT = 80
        private const val VANILLA_CARGO_ROWS = 2
        private const val AUGMENT_ROW_HEIGHT = 60

        // Where the over capacity boxes go, left of the window (where the
        // store's sell box is), and the gaps between them and their slots
        private const val OVERFLOW_X = -275
        private const val OVERFLOW_TOP = 107
        private const val OVERFLOW_SPACING = 10
        private const val AUGMENT_BOX_WIDTH = 235
        private const val OVERFLOW_CARGO_SPACING = 14
        private const val OVERFLOW_FRAME_PADDING = 16

        // Where to split the background to fit extra cargo and augments, in
        // panel coordinates. This is near the bottom of those sections, below
        // the vanilla slots.
        private const val BOTTOM_BACKGROUND_SPLIT_Y = 433
        private const val BOTTOM_BACKGROUND_CHUNK_HEIGHT = 100
    }
}
