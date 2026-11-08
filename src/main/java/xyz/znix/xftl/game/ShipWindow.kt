package xyz.znix.xftl.game

import xyz.znix.xftl.*
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.sys.Input
import xyz.znix.xftl.systems.SubSystem
import xyz.znix.xftl.systems.SystemBlueprint
import xyz.znix.xftl.ui.Label
import xyz.znix.xftl.ui.WidgetContainer
import kotlin.math.max

private typealias UndoFn = () -> Unit

class ShipWindow(val game: InGameState, val ship: Ship, initialTab: Tab, private val close: () -> Unit) :
    Window() {
    // Sized to fit the tallest tab, so the window doesn't move around when
    // switching tabs on ships with lots of crew or weapon slots.
    override val size: IPoint
        get() = ConstPoint(
            587,
            464 + maxOf(crewExtraHeight(ship.maxCrew + 1), equipmentPanel.extraHeight, upgradesExtraHeight)
        )

    // Make the system power stuff visible
    override val windowCentreOffset = ConstPoint(-10, 0)

    override val appliesSelfTint: Boolean get() = crewToDismiss != null

    private val acceptButtonImage = game.getImg("img/upgradeUI/buttons_accept_base.png")
    private val undoButtonImage = game.getImg("img/upgradeUI/buttons_undo_base.png")

    private val shipNameFont = game.getFont("c&c", 3f)
    private val numberFont = game.getFont("num_font")
    private val reactorFont = game.getFont("HL1", 2f)
    private val crewNameFont = game.getFont("JustinFont8")
    private val crewMessageFont = game.getFont("JustinFont10")
    private val dismissFont = game.getFont("HL1")
    private val maxFont = game.getFont("HL1", 2f)

    private val upgradeSystemSound = game.sounds.getSample("upgradeSystem")
    private val downgradeSystemSound = game.sounds.getSample("downgradeSystem")

    private var tab: Tab = initialTab

    private val equipmentPanel = ShipEquipmentPanel(game, ship)

    private val infoPanel = InfoPanel(game)

    // These store functions that each undo the last
    // upgrade of a system or the reactor.
    private val systemUpgradeUndos = HashMap<AbstractSystem, ArrayList<UndoFn>>()
    private val reactorUpgradeUndo = ArrayList<UndoFn>()

    private val crewDismissWidget: WidgetContainer = game.uiLoader.load("confirm").mainWidget
    private val crewDismissWidgetPos = Point(0, 0)
    private var crewToDismiss: LivingCrew? = null
    private var crewDismissWidgetButtons: List<Button> = emptyList()
    private val dismissHighlightButtons = ArrayList<Button>()

    // If non-null, this is the crewmember whose name we're changing
    private var renamingCrew: LivingCrew? = null
    private var cursorFlashTimer = 0f

    // If true, the drawing code should re-create any buttons it added
    private var updatingButtons = false

    private val closeButton = Buttons.BasicButton(
        // As an interesting note, in the base game it's one pixel too far right so
        // where it meets up with the side of the ship panel has a small ledge.
        game, position + ConstPoint(428, 471),
        ConstPoint(132, 32), game.translator["button_accept"],
        4, game.getFont("HL2", 3f), 25,
        this::escapePressed
    )

    init {
        // TODO too many items

        updateButtons()

        (crewDismissWidget.byId["message"] as Label).text = game.translator["confirm_dismiss"]

        // Re-perform the layout to fit the message
        crewDismissWidget.root.updateSizes()
        crewDismissWidget.root.expandToParent(ConstPoint.ZERO)
        crewDismissWidget.root.updateLayout()

        crewDismissWidget.addButtonListener("yes") {
            // If the crewmember is in the cloning queue, remove them from there.
            ship.clonebay?.queue?.remove(crewToDismiss!!)

            crewToDismiss!!.removeFromShip()
            crewToDismiss = null
            updateButtons()
        }
        crewDismissWidget.addButtonListener("no") {
            crewToDismiss = null
        }
    }

    private fun updateButtons() {
        buttons.clear()
        dismissHighlightButtons.clear()

        buttons += closeButton

        // The buttons can change their icon depending on which of the other two
        // tabs are selected, so re-create them whenever a different tab is selected.

        if (tab != Tab.UPGRADES)
            buttons += TabButton(Tab.UPGRADES, ConstPoint(-GLOW_WIDTH, -GLOW_WIDTH), ConstPoint(109, 48))
        if (tab != Tab.CREW)
            buttons += TabButton(Tab.CREW, ConstPoint(75, -GLOW_WIDTH), ConstPoint(127, 48))
        if (tab != Tab.EQUIPMENT)
            buttons += TabButton(Tab.EQUIPMENT, ConstPoint(175, -GLOW_WIDTH), ConstPoint(123, 48))

        // Switching tabs away from upgrades commits the system upgrades
        if (tab != Tab.UPGRADES) {
            systemUpgradeUndos.clear()
            reactorUpgradeUndo.clear()
        }

        // Update the button window offsets
        positionUpdated()

        // Update any buttons added in the drawing code
        updatingButtons = true
    }

    override fun draw(g: Graphics) {
        val backgroundImage = game.getImg("img/upgradeUI/Equipment/${tab.textureName}_main.png")
        val backgroundPos = position - ConstPoint(GLOW_WIDTH, GLOW_WIDTH)

        // The crew and equipment tabs are stretched if there's lots of
        // crew or weapons.
        when (tab) {
            Tab.EQUIPMENT -> equipmentPanel.drawBackground(backgroundImage, backgroundPos)
            Tab.CREW -> backgroundImage.drawWithInsertedRows(
                backgroundPos.x, backgroundPos.y,
                CREW_BACKGROUND_SPLIT_Y, CREW_BACKGROUND_CHUNK_HEIGHT,
                crewExtraHeight(crewBoxCount)
            )

            Tab.UPGRADES -> backgroundImage.drawWithInsertions(
                backgroundPos.x, backgroundPos.y, emptyList(), listOf(
                    Image.Insertion(UPGRADES_BACKGROUND_SPLIT_Y, UPGRADES_BACKGROUND_CHUNK_HEIGHT, systemsExtraHeight),
                    Image.Insertion(SUBSYSTEMS_BACKGROUND_SPLIT_Y, UPGRADES_BACKGROUND_CHUNK_HEIGHT, maxOf(subsystemsExtraHeight, reactorExtraHeight)),
                )
            )

            else -> backgroundImage.draw(backgroundPos)
        }

        acceptButtonImage.draw(position.x + 405f, position.y + 464f + bottomOffset)

        when (tab) {
            Tab.UPGRADES -> drawUpgrades(g)
            Tab.CREW -> drawCrew(g)
            Tab.EQUIPMENT -> drawEquipment(g)
        }

        if (updatingButtons) {
            // Update any newly-created button positions
            positionUpdated()
            updatingButtons = false
        }

        // When we're dismissing crew we grey out everything else, this
        // means fiddling with how the buttons are drawn.
        if (crewToDismiss != null) {
            drawButtonsDismissing(g)
        } else {
            for (button in buttons) {
                button.draw(g)
            }
        }

        equipmentPanel.drawDrag(g)
    }

    private fun drawButtonsDismissing(g: Graphics) {
        for (button in buttons) {
            if (dismissHighlightButtons.contains(button))
                continue

            button.draw(g)
        }

        // The dismiss popup has to be drawn over the buttons that make up other crewmembers
        game.shipUI.drawWindowBackgroundTint(g)

        for (button in dismissHighlightButtons) {
            button.draw(g)
        }

        g.pushTransform()
        g.translate(position.x + crewDismissWidgetPos.x.f, position.y + crewDismissWidgetPos.y.f)
        crewDismissWidget.draw(g)
        g.popTransform()
    }

    override fun shipModified() {
        super.shipModified()
        equipmentPanel.shipModified()
    }

    private fun drawUpgrades(g: Graphics) {
        // Draw the ship name
        val name = game.translator[ship.type.shipTitle!!]

        val nameWidth = shipNameFont.getWidth(name)

        shipNameFont.drawString(
            position.x + 138f + (327f - nameWidth) / 2,
            position.y + 75f,
            name,
            Colour.white
        )

        // Everything below the systems moves down to fit any extra rows of
        // them, and the buttons at the bottom move down further for any extra
        // rows of subsystems.
        val extra = upgradesExtraHeight
        val systemsExtra = systemsExtraHeight

        undoButtonImage.draw(position.x + 3f, position.y + 464f + extra)
        if (updatingButtons) {
            buttons += object : Buttons.BasicButton(
                game, ConstPoint(26, 471 + extra), ConstPoint(97, 32),
                game.translator["button_undo"],
                4, game.getFont("HL2", 3f), 25,
                this::undoAllSystems
            ) {
                // Grey the button out when there's nothing to undo
                override val disabled: Boolean get() = systemUpgradeUndos.all { it.value.isEmpty() } && reactorUpgradeUndo.isEmpty()
            }
        }

        // Draw the system power info.
        // Note the reactor button draws this itself.
        val hoveredButton = buttons.filterIsInstance(UpgradeButton::class.java).firstOrNull { it.hovered }
        val hoveredSystem = hoveredButton?.system
        if (hoveredSystem != null) {
            val undoablePower = systemUpgradeUndos[hoveredSystem]?.size ?: 0
            infoPanel.drawDescriptionBoxSystem(hoveredSystem)
            infoPanel.drawSystemPowerBox(g, hoveredSystem.blueprint, hoveredSystem.energyLevels, undoablePower)
        }

        // Draw the systems, in rows of eight
        for (i in 0 until systemSlotCount) {
            if (!updatingButtons)
                continue

            val system = ship.mainSystems.getOrNull(i)

            val price: Int? = when {
                system == null -> null
                system.energyLevels == system.blueprint.maxPower -> null
                else -> system.blueprint.upgradeCost[system.energyLevels - 1]
            }

            val isFullyUpgraded = system != null && price == null

            val baseImage = when {
                system == null -> "img/upgradeUI/upgrade_system_bar_none.png"
                isFullyUpgraded -> "img/upgradeUI/upgrade_system_bar_max_on.png"
                else -> "img/upgradeUI/upgrade_system_bar_on.png"
            }
            val selectImage = when {
                system == null -> "img/upgradeUI/upgrade_system_bar_none.png"
                isFullyUpgraded -> "img/upgradeUI/upgrade_system_bar_max_select2.png"
                else -> "img/upgradeUI/upgrade_system_bar_select2.png"
            }

            val column = i % SYSTEMS_PER_ROW
            val row = i / SYSTEMS_PER_ROW
            buttons += UpgradeButton(
                ConstPoint(32 + 66 * column, 115 + SYSTEM_ROW_HEIGHT * row), ConstPoint(66, 150),
                game.getImg(selectImage), game.getImg(baseImage),
                system, price
            )
        }

        // Draw the subsystems
        // TODO only go until 3 on non-AE?
        // These are in rows of four, for ships with more subsystems (like
        // several backup batteries).
        val subsystems = ship.rooms.mapNotNull { it.system as? SubSystem }.sortedBy { it.sortingType }
        for (i in 0 until subsystemSlotCount) {
            if (!updatingButtons)
                continue

            val system = if (i < subsystems.size) subsystems[i] else null

            val price: Int? = when {
                system == null -> null
                system.energyLevels == system.blueprint.maxPower -> null
                else -> system.blueprint.upgradeCost[system.energyLevels - 1]
            }

            val isFullyUpgraded = system != null && price == null

            val baseImage = when {
                system == null -> "img/upgradeUI/upgrade_subsystem_bar_none.png"
                isFullyUpgraded -> "img/upgradeUI/upgrade_subsystem_bar_max_on.png"
                else -> "img/upgradeUI/upgrade_subsystem_bar_on.png"
            }
            val selectImage = when {
                system == null -> "img/upgradeUI/upgrade_subsystem_bar_none.png"
                isFullyUpgraded -> "img/upgradeUI/upgrade_subsystem_bar_max_select2.png"
                else -> "img/upgradeUI/upgrade_subsystem_bar_select2.png"
            }

            buttons += UpgradeButton(
                ConstPoint(
                    9 + 66 * (i % SUBSYSTEMS_PER_ROW),
                    330 + systemsExtra + SUBSYSTEM_ROW_HEIGHT * (i / SUBSYSTEMS_PER_ROW)
                ), ConstPoint(66, 113),
                game.getImg(selectImage), game.getImg(baseImage),
                system, price
            )
        }

        // Add the reactor button
        if (updatingButtons) {
            val reactorImg = game.getImg("img/upgradeUI/Equipment/equipment_reactor_on.png")
            val reactorHighlight = game.getImg("img/upgradeUI/Equipment/equipment_reactor_select2.png")
            // One button for all its power bars, however many there are: a
            // reactor of more than 50 has its bars in tiers of 50, one above
            // the other, with the button made taller for them (see
            // reactorExtraHeight). It's as tall as the subsystems beside it,
            // if they're taller, with more rows of bars in the room that leaves.
            run {
                val levels = 0 until ship.maxReactorPower
                val extra = reactorPanelExtraHeight
                val size = ConstPoint(reactorImg.imageSize.x, reactorImg.imageSize.y + extra)
                fun drawPanel(image: Image, pos: IPoint) = image.drawWithInsertedRows(
                    pos.x, pos.y, REACTOR_IMAGE_SPLIT_Y, REACTOR_IMAGE_CHUNK_HEIGHT, extra
                )
                buttons += object : Button(game, ConstPoint(298, 327 + systemsExtra), size) {

                    val currentPrice: Int
                        get() {
                            // Expensive first power on the Cernkov
                            if (ship.purchasedReactorPower < 5)
                                return 30

                            return (ship.purchasedReactorPower / 5) * 5 + 15
                        }

                    override fun draw(g: Graphics) {
                        if (hovered) {
                            drawPanel(reactorHighlight, pos)

                            infoPanel.drawDescriptionBox(
                                GameText.localised("upgrade_reactor"),
                                GameText.localised("reactor_desc"),
                                null, emptyList(),
                                InfoPanel.INFO_HEIGHT_SYSTEM
                            )
                        } else {
                            drawPanel(reactorImg, pos)
                        }

                        val fontColour = when (hovered) {
                            true -> Constants.STORE_BUY_HOVER
                            false -> Constants.SECTOR_CUTOUT_TEXT
                        }

                        // Figure out the power range that should show as downgrades
                        val lastNonRefundablePower = ship.purchasedReactorPower - reactorUpgradeUndo.size
                        val refundRange = lastNonRefundablePower until ship.purchasedReactorPower

                        // Draw the energy bars, filled up a column at a time
                        // from the left, from the bottom. Vanilla's 25 are in
                        // five columns of five; a bigger reactor's are in ten
                        // columns (of ten, for 100), with the bars sized to fill
                        // the same width and the button's whole height.
                        val layout = reactorBarLayout(levels.count(), extra)
                        for (level in levels) {
                            g.colour = when {
                                level in refundRange -> Constants.SYS_ENERGY_PURCHASE_UNDOABLE
                                ship.purchasedReactorPower > level -> Constants.SYS_ENERGY_ACTIVE
                                hovered -> Constants.SYS_ENERGY_PURCHASE_HOVER
                                else -> Constants.SYS_ENERGY_PURCHASE
                            }

                            val column = level / layout.rows
                            val row = level % layout.rows
                            g.fillRect(
                                pos.x + layout.left + column * layout.columnPitch,
                                pos.y + layout.bottom - layout.barHeight - row * layout.rowPitch,
                                layout.barWidth, layout.barHeight
                            )
                        }

                        // Draw the current price, or two dashes if it's maxed out.
                        val priceStr = when (ship.purchasedReactorPower < ship.maxReactorPower) {
                            true -> currentPrice.toString()
                            false -> "--"
                        }
                        numberFont.drawString(pos.x + 235f, pos.y + 105f + extra, priceStr, fontColour)

                        // Draw the 'n power bars' text - this is annoyingly mixed between two fonts
                        // TODO mix them properly to work in languages where the power number doesn't
                        //  come first - is this something FTL does?
                        val text = game.translator["upgrade_reactor_power"].replaceArg("")
                        reactorFont.drawStringLeftAligned(pos.x + 179f, pos.y + 105f + extra, text, fontColour)
                        numberFont.drawStringLeftAligned(
                            pos.x + 47f,
                            pos.y + 105f + extra,
                            ship.purchasedReactorPower.toString(),
                            fontColour
                        )
                    }

                    override fun click(button: Int) {
                        if (button == Input.MOUSE_RIGHT_BUTTON) {
                            val undoLast = reactorUpgradeUndo.lastOrNull() ?: return
                            reactorUpgradeUndo.remove(undoLast)
                            undoLast()
                            downgradeSystemSound.play()
                            return
                        }

                        if (button != Input.MOUSE_LEFT_BUTTON)
                            return

                        if (ship.purchasedReactorPower >= ship.maxReactorPower)
                            return

                        // Store the price so undos work properly.
                        val price = currentPrice

                        if (ship.scrap < price) {
                            game.shipUI.playInsufficientScrapAnimation()
                            return
                        }
                        ship.scrap -= price

                        ship.purchasedReactorPower++

                        // This is how the user can revert an upgrade
                        reactorUpgradeUndo += {
                            ship.scrap += price
                            ship.purchasedReactorPower--
                        }

                        upgradeSystemSound.play()
                    }
                }
            }
        }
    }

    inner class UpgradeButton(
        pos: ConstPoint, size: ConstPoint,
        private val selectImage: Image,
        private val baseImage: Image,
        val system: AbstractSystem?,
        private val upgradePrice: Int?
    ) : Button(game, pos, size) {
        override val disabled: Boolean get() = system == null

        override fun draw(g: Graphics) {
            // Draw the main button image
            val image = if (hovered) selectImage else baseImage
            image.draw(pos.x, pos.y)

            if (system == null)
                return

            val fontColour = when (hovered) {
                true -> Constants.STORE_BUY_HOVER
                false -> Constants.SECTOR_CUTOUT_TEXT
            }

            // Draw the system icon
            val systemIcon = game.getImg(system.blueprint.onIconPath)
            systemIcon.draw(
                pos.x - SystemBlueprint.ICON_GLOW + 19f,
                pos.y + size.y - SystemBlueprint.ICON_GLOW - 65f
            )

            // Figure out the power range that should show as downgrades
            val undoablePower = systemUpgradeUndos[system]?.size ?: 0
            val lastNonRefundablePower = system.energyLevels - undoablePower + 1 // Power here is 1-indexed
            val refundRange = lastNonRefundablePower..system.energyLevels

            // Draw the energy bars
            for (level in 1..system.blueprint.maxPower) {
                g.colour = when {
                    level in refundRange -> Constants.SYS_ENERGY_PURCHASE_UNDOABLE
                    system.energyLevels >= level -> Constants.SYS_ENERGY_ACTIVE
                    hovered -> Constants.SYS_ENERGY_PURCHASE_HOVER
                    else -> Constants.SYS_ENERGY_PURCHASE
                }

                g.fillRect(
                    pos.x + 24f,
                    pos.y + size.y - 76f - (level - 1) * 8f,
                    15f, 6f
                )
            }

            // Draw the scrap amount
            if (upgradePrice != null) {
                numberFont.drawString(pos.x + 30f, pos.y + size.y - 8f, upgradePrice.toString(), fontColour)
            }

            if (system.energyLevels == system.blueprint.maxPower) {
                val maxStr = game.translator["upgrade_max"]
                maxFont.drawStringCentred(pos.x.f, pos.y + size.y - 8f, size.x.f, maxStr, fontColour)
            }
        }

        override fun click(button: Int) {
            if (button == Input.MOUSE_RIGHT_BUTTON) {
                val undos = systemUpgradeUndos[system] ?: return
                val undoLast = undos.lastOrNull() ?: return
                undos.remove(undoLast)
                undoLast()
                downgradeSystemSound.play()
                return
            }

            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            if (upgradePrice == null)
                return

            if (ship.scrap < upgradePrice) {
                game.shipUI.playInsufficientScrapAnimation()
                return
            }
            ship.scrap -= upgradePrice

            system!!.energyLevels++

            val undos = systemUpgradeUndos.getOrPut(system) { ArrayList() }
            undos += {
                system.energyLevels--
                ship.scrap += upgradePrice
                updateButtons()
            }

            // Replace this button to reflect the upgrade
            updateButtons()

            upgradeSystemSound.play()
        }
    }

    private fun drawCrew(g: Graphics) {
        val crew = ship.sys.playerCrew

        crewMessageFont.drawString(position.x + 19f, position.y + 67f, game.translator["rename_note"], Colour.white)

        // Draw the crew boxes in rows, with the last row centred.
        // Vanilla ships have eight boxes, or nine (a full 3x3 grid) if the
        // player has too many crew and has to dismiss one.
        val layout = crewLayout
        val boxCount = crewBoxCount
        for (id in 0 until boxCount) {
            val row = id / layout.perRow
            val column = id % layout.perRow
            val inRow = minOf(layout.perRow, boxCount - row * layout.perRow)
            val rowOffset = (layout.perRow - inRow) * layout.halfSpacing

            val x = layout.startX + layout.spacing * column + rowOffset
            drawCrewBox(g, crew, ConstPoint(x, 88 + CREW_ROW_HEIGHT * row), id)
        }

        if (crewToDismiss != null && updatingButtons) {
            crewDismissWidgetButtons = crewDismissWidget.buildButtons(game, this, crewDismissWidgetPos)
            for (button in crewDismissWidgetButtons) {
                button.windowOffset = position
            }
        }

        if (game.playerHasTooManyCrew() && updatingButtons) {
            buttons += ShipEquipmentPanel.SellDropBox.create(
                game,
                ShipEquipmentPanel.SellDropBox.Type.TOO_MANY_CREW,
                ConstPoint(-275, 107)
            ) { null }
        }
    }

    private fun drawCrewBox(g: Graphics, crewList: List<LivingCrew>, boxPos: ConstPoint, id: Int) {
        val crew = crewList.getOrNull(id)

        if (crew == null) {
            val boxEmpty = game.getImg("img/upgradeUI/Equipment/box_crew_off.png")
            boxEmpty.draw(boxPos + position)
            return
        }

        val dismissButtonBox = game.getImg("img/customizeUI/box_crewcustom_on.png")
        val dismissButtonBoxHover = game.getImg("img/customizeUI/box_crewcustom_selected.png")

        val boxNormal = game.getImg("img/upgradeUI/Equipment/box_crew_on.png")
        val boxHover = game.getImg("img/upgradeUI/Equipment/box_crew_selected.png")

        if (crewToDismiss == crew) {
            val margin = 22 // Min space between window and warning
            val boxCentre = boxPos.x + boxNormal.width / 2
            val boxWidth = crewDismissWidget.root.size.x
            val baseRelX = boxCentre - boxWidth / 2
            val relX = baseRelX.coerceIn(margin..size.x - boxWidth - margin)

            crewDismissWidgetPos.x = relX
            crewDismissWidgetPos.y = boxPos.y + 70 + 23
        }

        if (!updatingButtons)
            return

        val mainButton = object : Button(game, boxPos, boxNormal.imageSize) {
            override val makesHoverNoise: Boolean get() = false

            var nameHover = false

            override fun draw(g: Graphics) {
                val box = if (hovered) boxHover else boxNormal
                box.draw(pos)

                val dismissBox = if (hovered) dismissButtonBoxHover else dismissButtonBox
                dismissBox.draw(pos.x.f, pos.y + 62f)

                if (renamingCrew == crew) {
                    // Draw the rename box
                    val width = 150
                    val height = 23
                    val boxX = pos.x + (size.x - width) / 2
                    val boxY = pos.y + 43

                    g.colour = Colour.white
                    g.fillRect(boxX, boxY, width, height)
                    g.colour = Colour.black
                    g.fillRect(boxX + 1, boxY + 1, width - 2, height - 2)

                    val name = crew.info.name
                    val nameWidth = crewMessageFont.getWidth(name) + 2 + 2 // 2+2 for the gap and cursor
                    val nameX = boxX + (width - nameWidth) / 2
                    crewMessageFont.drawString(nameX.f, pos.y + 61f, name, Colour.white)

                    // Draw the cursor
                    cursorFlashTimer += game.renderingDeltaTime
                    val period = 0.5f
                    if (cursorFlashTimer > period)
                        cursorFlashTimer -= period

                    if (cursorFlashTimer < period / 2f) {
                        g.colour = Colour.yellow
                        g.fillRect(nameX + nameWidth - 2, boxY + 8, 2, 13)
                    }
                } else {
                    // Draw the name
                    val name = crew.info.shortName
                    val nameX = (96 - crewNameFont.getWidth(name)) / 2
                    val nameColour = when (nameHover) {
                        true -> Constants.CREW_RENAME_TEXT_COLOUR
                        false -> Colour.white
                    }
                    crewNameFont.drawString(pos.x + 2f + nameX, pos.y + 61f, name, nameColour)
                }

                // Draw the crewmember portrait
                // TODO align properly
                val portrait = crew.icon.currentFrame
                crew.drawPortrait(
                    pos.x + 2 + (96 - portrait.width * 2) / 2,
                    pos.y + 2 + (45 - portrait.height * 2) / 2,
                    false,
                    2f
                )

                // Draw the information about this crewmember while hovering over them
                if (hovered) {
                    infoPanel.drawCrew(g, crew.info)
                }
            }

            override fun update(x: Int, y: Int, blockHover: Boolean) {
                super.update(x, y, blockHover)

                nameHover = x in (pos.x..pos.x + 100) && y in (pos.y + 49..pos.y + 65)
            }

            override fun click(button: Int) {
                if (button != Input.MOUSE_LEFT_BUTTON || !nameHover)
                    return

                renamingCrew = crew
            }
        }

        val dismissButton = Buttons.BasicButton(
            game, boxPos + ConstPoint(4, 70), ConstPoint(92, 12),
            game.translator["crewbox_dismiss"],
            2, dismissFont, 9
        ) {
            crewToDismiss = crew
            updateButtons()
        }

        buttons += mainButton
        buttons += dismissButton

        // Don't grey out the buttons corresponding to crew we're about to dismiss
        if (crewToDismiss == crew) {
            dismissHighlightButtons += mainButton
            dismissHighlightButtons += dismissButton
        }
    }

    private fun drawEquipment(g: Graphics) {
        equipmentPanel.draw(g)

        // Draw the information for the currently-hovered blueprint.
        equipmentPanel.drawInfoPanel(infoPanel)
    }

    override fun mouseClick(button: Int, x: Int, y: Int) {
        if (crewToDismiss != null) {
            for (btn in crewDismissWidgetButtons) {
                btn.mouseDown(button, x, y)
            }
            return
        }

        // If we were renaming a crewmember, we're not any more
        renamingCrew = null

        super.mouseClick(button, x, y)

        if (tab == Tab.EQUIPMENT)
            equipmentPanel.mouseClick(button, x, y)
    }

    override fun mouseReleased(button: Int, x: Int, y: Int) {
        if (crewToDismiss != null) {
            return
        }

        super.mouseReleased(button, x, y)

        if (tab == Tab.EQUIPMENT)
            equipmentPanel.mouseReleased(button, x, y)
    }

    override fun updateUI(x: Int, y: Int) {
        // If the crew dismiss window is up, block all other interactions.
        if (crewToDismiss != null) {
            for (button in crewDismissWidgetButtons) {
                button.update(x, y, false)
            }
            return
        }

        super.updateUI(x, y)

        if (tab == Tab.EQUIPMENT)
            equipmentPanel.updateUI(x, y)
    }

    override fun escapePressed() {
        close()
    }

    /**
     * How the crew boxes are laid out. This matches vanilla for ships with
     * the normal amount of crew, but ships with more use rows of four so
     * the window doesn't get too tall.
     */
    private val crewLayout: CrewLayout
        get() = when {
            ship.maxCrew > Ship.DEFAULT_MAX_CREW -> CrewLayout.WIDE
            else -> CrewLayout.VANILLA
        }

    /**
     * The number of crew boxes to show: one for each crew slot, plus an
     * extra one if the player has too many crew.
     */
    private val crewBoxCount: Int
        get() = ship.maxCrew + if (game.playerHasTooManyCrew()) 1 else 0

    /**
     * The number of system slots in the upgrades tab: eight in vanilla, but
     * more for ships that can have more systems.
     */
    private val systemSlotCount: Int
        get() = maxOf(ship.maxMainSystems, ship.mainSystems.size, Ship.DEFAULT_MAX_SYSTEMS)

    /**
     * How much taller the systems section of the upgrades tab is than in
     * vanilla, to fit the extra rows of systems.
     */
    private val systemsExtraHeight: Int
        get() {
            val rows = (systemSlotCount + SYSTEMS_PER_ROW - 1) / SYSTEMS_PER_ROW
            return (rows - 1) * SYSTEM_ROW_HEIGHT
        }

    /**
     * The number of subsystem slots in the upgrades tab: four in vanilla, but
     * more for ships with room for more (like several backup batteries).
     * This counts the rooms that can have subsystems, so it doesn't change
     * when one's bought.
     */
    private val subsystemSlotCount: Int
        get() {
            val subsystemRooms = ship.systemSlots.filter { it.system.info?.isSubSystem == true }.map { it.room }.distinct()
            val installed = ship.subSystems.size
            return maxOf(VANILLA_SUBSYSTEM_SLOTS, subsystemRooms.size, installed)
        }

    /**
     * How much taller the subsystems section of the upgrades tab is than in
     * vanilla, to fit the extra rows of subsystems.
     */
    private val subsystemsExtraHeight: Int
        get() {
            val rows = (subsystemSlotCount + SUBSYSTEMS_PER_ROW - 1) / SUBSYSTEMS_PER_ROW
            return (rows - 1) * SUBSYSTEM_ROW_HEIGHT
        }

    /**
     * How much taller the upgrades tab is than in vanilla, to fit the extra
     * rows of systems and subsystems.
     */
    /**
     * How many tiers of power bars the reactor has in the upgrades tab: one
     * for each [REACTOR_TIER_BARS] bars it can have.
     */
    private val reactorTiers: Int
        get() = ((ship.maxReactorPower + REACTOR_TIER_BARS - 1) / REACTOR_TIER_BARS).coerceAtLeast(1)

    /**
     * How much taller the reactor's button is, for its tiers past the first.
     * It usually fits next to the subsystems' rows.
     */
    private val reactorExtraHeight: Int
        get() = (reactorTiers - 1) * REACTOR_TIER_HEIGHT

    /**
     * Where the reactor's power bars go in its button, which is [extra]
     * pixels taller than vanilla's: [rows] bars in a column, the first
     * column's left at [left] and the bottom one's bottom at [bottom] (from
     * the button's top left), each [barWidth] by
     * [barHeight], and [columnPitch] and [rowPitch] apart.
     */
    private class ReactorBarLayout(
        val rows: Int, val left: Float, val bottom: Float,
        val barWidth: Float, val barHeight: Float,
        val columnPitch: Float, val rowPitch: Float
    )

    /**
     * Lay out [bars] power bars in a reactor button [extra] pixels taller than
     * vanilla's: as vanilla does (five columns of five) for up to its 25,
     * and otherwise in [REACTOR_COLUMNS] columns, filling the area vanilla's
     * bars take up (and the extra height below it).
     */
    private fun reactorBarLayout(bars: Int, extra: Int): ReactorBarLayout {
        val bottom = REACTOR_BARS_BOTTOM + extra
        if (bars <= VANILLA_REACTOR_BARS) {
            return ReactorBarLayout(
                VANILLA_REACTOR_ROWS, VANILLA_BARS_X, bottom.f, VANILLA_BAR_WIDTH, VANILLA_BAR_HEIGHT,
                VANILLA_COLUMN_PITCH, REACTOR_BAR_PITCH.f
            )
        }

        val rows = (bars + REACTOR_COLUMNS - 1) / REACTOR_COLUMNS

        // From the first bar's edge to the last one's, across (most of the
        // button's width, a small margin from each side) and down
        val width = REACTOR_BUTTON_WIDTH - 2 * REACTOR_BARS_MARGIN
        val height = (bottom - REACTOR_BARS_TOP).f

        // The columns are close together, a small gap apart, while the rows
        // are spaced as vanilla's are
        val barWidth = (width - (REACTOR_COLUMNS - 1) * REACTOR_COLUMN_GAP) / REACTOR_COLUMNS
        val barHeightRatio = VANILLA_BAR_HEIGHT / REACTOR_BAR_PITCH
        val rowPitch = height / (rows - 1 + barHeightRatio)
        return ReactorBarLayout(
            rows, REACTOR_BARS_MARGIN, bottom.f,
            barWidth, rowPitch * barHeightRatio,
            barWidth + REACTOR_COLUMN_GAP, rowPitch
        )
    }

    /**
     * How much taller the reactor's button is drawn: down to the bottom of
     * the subsystems beside it, if they're taller than its tiers need.
     */
    private val reactorPanelExtraHeight: Int
        get() = maxOf(subsystemsExtraHeight, reactorExtraHeight)

    private val upgradesExtraHeight: Int
        get() = systemsExtraHeight + maxOf(subsystemsExtraHeight, reactorExtraHeight)

    /**
     * How much taller the crew tab has to be than in vanilla to fit
     * [boxCount] crew boxes.
     */
    private fun crewExtraHeight(boxCount: Int): Int {
        val perRow = crewLayout.perRow
        val rows = (boxCount + perRow - 1) / perRow
        return (rows - VANILLA_CREW_ROWS).coerceAtLeast(0) * CREW_ROW_HEIGHT
    }

    /**
     * How far the bottom of the window is moved down, due to the crew or
     * equipment tabs being stretched to fit extra crew or weapon slots.
     */
    private val bottomOffset: Int
        get() = when (tab) {
            Tab.UPGRADES -> upgradesExtraHeight
            Tab.CREW -> crewExtraHeight(crewBoxCount)
            Tab.EQUIPMENT -> equipmentPanel.extraHeight
            else -> 0
        }

    override fun positionUpdated() {
        super.positionUpdated()

        infoPanel.position = position + ConstPoint(size.x + 13, 74)
        equipmentPanel.position = position

        // Keep the accept button at the bottom of the window
        closeButton.windowOffset = position + ConstPoint(0, bottomOffset)
    }

    override fun onTextInput(key: Int, c: Char): Boolean {
        val info = renamingCrew?.info ?: return false

        if (key == Input.KEY_BACK) {
            val endPos = max(0, info.name.length - 1)
            info.name = info.name.substring(0, endPos)
            return true
        }

        if (key == Input.KEY_ENTER) {
            renamingCrew = null
            return true
        }

        if (c != 0.toChar()) {
            if (info.name.length < 15)
                info.name += c
            return true
        }

        return false
    }

    private fun undoAllSystems() {
        for (undos in systemUpgradeUndos.values) {
            for (undo in undos) {
                undo()
            }
        }
        for (undo in reactorUpgradeUndo) {
            undo()
        }
        systemUpgradeUndos.clear()
        reactorUpgradeUndo.clear()

        downgradeSystemSound.play()
    }

    enum class Tab(val textureName: String) {
        UPGRADES("upgrades"),
        CREW("crew"),
        EQUIPMENT("equipment"),
    }

    private inner class TabButton(val newTab: Tab, pos: ConstPoint, size: ConstPoint) : Button(game, pos, size) {
        val images =
            ButtonImageSet.select2(game, "img/upgradeUI/Equipment/tabButtons/${tab.textureName}_${newTab.textureName}")

        override val disabled: Boolean
            get() = when (tab) {
                Tab.CREW -> game.playerHasTooManyCrew()
                else -> false
            }

        override fun draw(g: Graphics) {
            val image = when {
                disabled -> images.off
                hovered -> images.hover
                else -> images.normal
            }
            image.draw(pos)
        }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON || disabled)
                return

            tab = newTab
            updateButtons()
        }
    }

    companion object {
        const val GLOW_WIDTH: Int = 7

        private const val CREW_ROW_HEIGHT = 133

        private const val SYSTEMS_PER_ROW = 8
        private const val SYSTEM_ROW_HEIGHT = 165

        // Where to split the upgrades tab background to fit extra rows of
        // systems, in image coordinates. This is inside the systems section,
        // below the first row, and the rows above it are repeated to fill the gap.
        private const val UPGRADES_BACKGROUND_SPLIT_Y = 285
        private const val UPGRADES_BACKGROUND_CHUNK_HEIGHT = 100

        private const val SUBSYSTEMS_PER_ROW = 4
        private const val VANILLA_SUBSYSTEM_SLOTS = 4
        private const val SUBSYSTEM_ROW_HEIGHT = 120

        // The reactor's power bars in the upgrades tab: how many in each
        // tier, and how tall a tier is (five rows of bars), and where its
        // button's image is made taller for them, repeating its background
        // pattern (five rows high)
        private const val REACTOR_TIER_BARS = 50
        private const val VANILLA_REACTOR_ROWS = 5
        private const val REACTOR_BAR_PITCH = 13

        // Vanilla's reactor bars (see reactorBarLayout): how many there can
        // be, where they start and the area they fill in the button, each
        // bar's size and how far apart the columns are. A bigger reactor's
        // are in ten columns.
        private const val VANILLA_REACTOR_BARS = 25
        private const val REACTOR_COLUMNS = 10
        private const val VANILLA_BARS_X = 30f
        private const val REACTOR_BUTTON_WIDTH = 272f
        private const val REACTOR_BARS_MARGIN = 20f
        private const val REACTOR_COLUMN_GAP = 4f
        private const val REACTOR_BARS_TOP = 14
        private const val REACTOR_BARS_BOTTOM = 74
        private const val VANILLA_BAR_WIDTH = 32f
        private const val VANILLA_BAR_HEIGHT = 8f
        private const val VANILLA_COLUMN_PITCH = 44f
        private const val REACTOR_TIER_HEIGHT = VANILLA_REACTOR_ROWS * REACTOR_BAR_PITCH
        private const val REACTOR_IMAGE_SPLIT_Y = 40
        private const val REACTOR_IMAGE_CHUNK_HEIGHT = 5

        // Where to split the upgrades tab background to fit extra rows of
        // subsystems, in image coordinates. This is just below the first row
        // of them, in the bottom part of the window.
        private const val SUBSYSTEMS_BACKGROUND_SPLIT_Y = 455
        private const val VANILLA_CREW_ROWS = 3

        // Where to split the crew tab background to make it taller, in
        // image coordinates. The rows above this are repeated to fill the gap.
        private const val CREW_BACKGROUND_SPLIT_Y = 300
        private const val CREW_BACKGROUND_CHUNK_HEIGHT = 100
    }
}

/**
 * The positions of the crew boxes in the crew tab, relative to the window.
 *
 * [halfSpacing] is how far the last row is shifted for each missing box,
 * to centre it. The boxes are 100px wide.
 */
private enum class CrewLayout(val perRow: Int, val startX: Int, val spacing: Int, val halfSpacing: Int) {
    VANILLA(3, 68, 170, 86),
    WIDE(4, 36, 135, 68),
}
