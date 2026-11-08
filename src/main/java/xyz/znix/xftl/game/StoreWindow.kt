package xyz.znix.xftl.game

import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Constants
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.HotkeyDelayedTooltip
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.sys.Input
import xyz.znix.xftl.replaceArg
import xyz.znix.xftl.systems.Clonebay
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.systems.Medbay

class StoreWindow(val game: InGameState, val ship: Ship, val store: StoreData, private val close: () -> Unit) :
    Window() {

    // Sized to fit the taller of the buy and sell tabs, so the window
    // stays centred and on-screen. The buy tab is stretched to fit the extra
    // items in each section, and the sell tab on ships with lots of weapons,
    // cargo or augments.
    override val size: IPoint get() = ConstPoint(587, 423 + maxOf(BUY_EXTRA_HEIGHT, sellPanel.extraHeight))

    // Make the description box visible
    override val windowCentreOffset = ConstPoint(-50, 0)

    private val buyImage = game.getImg("img/storeUI/store_buy_main.png")
    private val sellImage = game.getImg("img/storeUI/store_sell_main.png")
    private val closeButtonOutline = game.getImg("img/storeUI/store_close_base.png")

    private val buySellTabFont = game.getFont("HL2", 3f)
    private val sectionFont = game.getFont("HL2", 2f)
    private val repairFont = game.getFont("HL1", 2f)
    private val numberFont = game.getFont("num_font")
    private val augmentNameFont = game.getFont("c&cnew", 2f)
    private val crewNameFont = game.getFont("JustinFont8")

    private val buySound = game.sounds.getSample("buy")

    private val sellPanel = ShipEquipmentPanel(game, ship).apply { sellUI = true }
    private val infoPanel = InfoPanel(game)

    private val buyTabButton = SimpleButton(
        game, ConstPoint(0, 0), ConstPoint(170, 46), ConstPoint(0, 0),
        game.getImg("img/upgradeUI/Equipment/tabButtons/sell_buy_on.png"),
        game.getImg("img/upgradeUI/Equipment/tabButtons/sell_buy_select2.png"),
        null
    ) {
        sellTab = false
    }

    private val sellTabButton = SimpleButton(
        game, ConstPoint(163, 0), ConstPoint(179, 46), ConstPoint(0, 0),
        game.getImg("img/upgradeUI/Equipment/tabButtons/buy_sell_on.png"),
        game.getImg("img/upgradeUI/Equipment/tabButtons/buy_sell_select2.png"),
        null
    ) {
        sellTab = true
    }

    private val buySubTab1Button = SimpleButton.byRegion(
        game, ConstPoint(GLOW_WIDTH + 348 - 3, GLOW_WIDTH + 9 - 5),
        ConstPoint(5, 5), ConstPoint(111, 24),
        game.getImg("img/upgradeUI/Equipment/tabButtons/store_page2_on.png"),
        game.getImg("img/upgradeUI/Equipment/tabButtons/store_page2_select2.png"),
        HotkeyDelayedTooltip(game, "hotkey")
    ) {
        secondBuyTab = false
    }
    private val buySubTab2Button = SimpleButton.byRegion(
        game, ConstPoint(GLOW_WIDTH + 348 - 3, GLOW_WIDTH + 9 - 5),
        ConstPoint(119, 5), ConstPoint(111, 24),
        game.getImg("img/upgradeUI/Equipment/tabButtons/store_page1_on.png"),
        game.getImg("img/upgradeUI/Equipment/tabButtons/store_page1_select2.png"),
        HotkeyDelayedTooltip(game, "hotkey")
    ) {
        secondBuyTab = true
    }

    private val fuelButton = ResourceButton(ConstPoint(GLOW_WIDTH + 11, GLOW_WIDTH + 76), Resource.FUEL)
    private val missilesButton = ResourceButton(ConstPoint(GLOW_WIDTH + 11, GLOW_WIDTH + 126), Resource.MISSILES)
    private val dronesButton = ResourceButton(ConstPoint(GLOW_WIDTH + 11, GLOW_WIDTH + 175), Resource.DRONES)

    // Trades missiles for drone parts, right under them (an XFTL extension,
    // as big ships go through drone parts fast)
    private val tradeButton = object : Buttons.BasicButton(
        game, ConstPoint(GLOW_WIDTH + 11, GLOW_WIDTH + 222), ConstPoint(169, 24),
        "%d missiles: 1 drone part".format(StoreData.MISSILES_PER_DRONE_PART),
        3, game.getFont("JustinFont10"), 16,
        {
            if (StoreData.tradeMissilesForDrones(ship) > 0)
                buySound.play()
        }
    ) {
        override val disabled: Boolean get() = ship.missilesCount < StoreData.MISSILES_PER_DRONE_PART
    }

    private val repairButtons = ArrayList<Button>()

    // To avoid having separate fields for all the different things that
    // can be bought, have lists of buttons for the top and bottom buy panels.
    // These will be populated during drawing which is a bit ugly, but means
    // we can keep all the section-related stuff together.
    private val buyButtonsUpper = ArrayList<BuyButton>()
    private val buyButtonsLower = ArrayList<BuyButton>()
    private var updatingBuyButtons = false

    private val closeButton = Buttons.BasicButton(
        game, position + ConstPoint(466, 472),
        ConstPoint(103, 32), game.translator["button_close"],
        4, buySellTabFont, 25,
        this::escapePressed
    )

    var sellTab: Boolean = false
        set(value) {
            field = value
            updateButtons()
        }

    // If there's two pages of stuff to buy, this is true if the second one is selected.
    var secondBuyTab: Boolean = false
        set(value) {
            field = value
            updateButtons()
        }

    init {
        updateButtons()
    }

    private fun updateButtons() {
        buttons.clear()
        buttons.add(closeButton)

        if (sellTab) {
            buttons.add(buyTabButton)

            // Our position is centred at the 0,0 of the image, whereas
            // the sell panel excludes the glow part of the image.
            // Also note ShipEquipmentPanel is aligned properly with the ship
            // equipment menu, so we need to shift it around a little to make it
            // line up properly here.
            sellPanel.position = position + ConstPoint(GLOW_WIDTH, GLOW_WIDTH - 7)
        } else {
            buttons.add(sellTabButton)
            buttons.add(fuelButton)
            buttons.add(missilesButton)
            buttons.add(dronesButton)
            if (StoreData.SHOW_MISSILE_TRADE)
                buttons.add(tradeButton)

            if (store.sections.size > 2) {
                buttons.add(if (secondBuyTab) buySubTab1Button else buySubTab2Button)
            }

            // Re-add the top and bottom buttons next draw
            updatingBuyButtons = true
        }

        // Re-position all the buttons
        positionUpdated()
    }

    override fun draw(g: Graphics) {
        if (sellTab) {
            drawSell(g)
        } else {
            drawBuy(g)
        }

        // See JumpWindow
        // Note the corners should have one step more roundness than the current
        // buttons, so the current ones look a bit ugly.
        closeButtonOutline.draw(closeButton.pos + ConstPoint(-23, -7))
        closeButton.draw(g)

        buySellTabFont.drawString(
            position.x + GLOW_WIDTH + 47f,
            position.y + GLOW_WIDTH + 25f,
            game.translator["store_tab_buy"],
            Constants.JUMP_DISABLED_TEXT
        )

        buySellTabFont.drawString(
            position.x + GLOW_WIDTH + 47f + 161f,
            position.y + GLOW_WIDTH + 25f,
            game.translator["store_tab_sell"],
            Constants.JUMP_DISABLED_TEXT
        )

        // Draw the info for whatever the player is hovering
        val hoveredBuyButton = buttons.filterIsInstance<BuyButton>().firstOrNull { it.hovered }
        hoveredBuyButton?.drawInfoPanel(g)

        sellPanel.drawDrag(g)
    }

    private fun drawBuy(g: Graphics) {
        // Stretch both the top and bottom sections to fit their extra items
        buyImage.drawWithInsertions(
            position.x, position.y, emptyList(), listOf(
                Image.Insertion(TOP_BACKGROUND_SPLIT_Y, TOP_BACKGROUND_CHUNK_HEIGHT, SECTION_EXTRA_HEIGHT),
                Image.Insertion(BOTTOM_BACKGROUND_SPLIT_Y, BOTTOM_BACKGROUND_CHUNK_HEIGHT, SECTION_EXTRA_HEIGHT),
            )
        )

        sellTabButton.draw(g)

        // Draw the consumable items section (fuel, missiles, drones)
        sectionFont.drawString(
            position.x + GLOW_WIDTH + 11f,
            position.y + GLOW_WIDTH + 54f,
            game.translator["store_title_items"],
            Constants.JUMP_DISABLED_TEXT
        )

        fuelButton.draw(g)
        missilesButton.draw(g)
        dronesButton.draw(g)
        if (StoreData.SHOW_MISSILE_TRADE)
            tradeButton.draw(g)

        // Draw the hull repair section
        drawRepairSection(g)

        // If there's more than two sections, show the tab buttons to swap between them.
        if (store.sections.size > 2) {
            val swapButton = if (secondBuyTab) buySubTab1Button else buySubTab2Button
            swapButton.draw(g)

            sectionFont.drawString(
                position.x + GLOW_WIDTH + 374f,
                position.y + GLOW_WIDTH + 27f,
                game.translator["store_tab_page1"],
                Constants.JUMP_DISABLED_TEXT
            )
            sectionFont.drawString(
                position.x + GLOW_WIDTH + 374f + 104f,
                position.y + GLOW_WIDTH + 27f,
                game.translator["store_tab_page2"],
                Constants.JUMP_DISABLED_TEXT
            )
        }

        // Draw the two (top and bottom) main purchase sections
        val sectionOffset = if (secondBuyTab) 2 else 0
        drawBuySection(g, sectionOffset + 0, 59, buyButtonsUpper)
        drawBuySection(g, sectionOffset + 1, 268 + SECTION_EXTRA_HEIGHT, buyButtonsLower)

        // We've just drawn both buy sections, so if we're supposed to
        // re-add the buy buttons, that's now done.
        updatingBuyButtons = false
    }

    private fun drawRepairSection(g: Graphics) {
        // This is moved down by the stretched top section, and the current
        // hull is at the bottom, so it's moved down by the bottom section too.
        val repairSectionY = position.y + GLOW_WIDTH + 269 + SECTION_EXTRA_HEIGHT
        val currentHullY = repairSectionY + SECTION_EXTRA_HEIGHT
        sectionFont.drawString(
            position.x + GLOW_WIDTH + 11f,
            repairSectionY - 6f,
            game.translator["store_title_repair"],
            Constants.JUMP_DISABLED_TEXT
        )

        // Draw the current hull label and number
        val currentHullLines = game.translator["store_current_hull"].split("\n")
        repairFont.drawStringCentred(
            position.x + GLOW_WIDTH + 4f,
            currentHullY + 159f,
            125f,
            currentHullLines[0],
            Constants.SECTOR_CUTOUT_TEXT
        )
        repairFont.drawStringCentred(
            position.x + GLOW_WIDTH + 4f,
            currentHullY + 159f + 18f,
            125f,
            currentHullLines[1],
            Constants.SECTOR_CUTOUT_TEXT
        )

        numberFont.drawStringLeftAligned(
            position.x + GLOW_WIDTH + 4f + 154,
            currentHullY + 168f,
            ship.health.toString(),
            Constants.SECTOR_CUTOUT_TEXT
        )

        for (button in repairButtons) {
            button.draw(g)
        }

        // Add the fix buttons
        if (!updatingBuyButtons) {
            return
        }

        repairButtons.clear()

        val repairImg = ButtonImageSet.select2(game, "img/storeUI/store_repair")
        val buttonX = GLOW_WIDTH + 11
        val buttonBaseY = repairSectionY - position.y
        val repairPricePos = ConstPoint(130, 28)

        val repairPrice = when (game.currentBeacon.sector.difficultySectorNumber) {
            0, 1, 2 -> 2
            3, 4, 5 -> 3
            else -> 4
        }

        repairButtons += object : RepairButton(ConstPoint(buttonX, buttonBaseY + 15), repairImg, repairPricePos) {
            override val nameKey: String get() = "repair_one_button"
            override val price: Int get() = repairPrice

            override fun buy() {
                ship.health++
            }
        }

        repairButtons += object : RepairButton(ConstPoint(buttonX, buttonBaseY + 70), repairImg, repairPricePos) {
            override val nameKey: String get() = "repair_all_button"
            override val price: Int get() = (ship.maxHealth - ship.health) * repairPrice

            override fun buy() {
                ship.health = ship.maxHealth
            }
        }

        for (button in repairButtons) {
            buttons += button
            button.windowOffset = position
        }
    }

    private fun drawBuySection(g: Graphics, sectionId: Int, y: Int, buyButtons: ArrayList<BuyButton>) {
        val section = store.sections.getOrNull(sectionId) ?: return

        val pos = ConstPoint(GLOW_WIDTH + 191, GLOW_WIDTH + y)

        sectionFont.drawString(
            position.x + pos.x + 7f,
            position.y + pos.y - 5f,
            game.translator[section.localisationKey],
            Constants.JUMP_DISABLED_TEXT
        )

        if (updatingBuyButtons)
            buyButtons.clear()

        when (section) {
            StoreData.Section.AUGMENTS -> drawBuyAugments(pos, buyButtons)
            StoreData.Section.CREW -> drawBuyCrew(pos, buyButtons)
            StoreData.Section.DRONES -> drawBuyDrones(pos, buyButtons)
            StoreData.Section.SYSTEMS -> drawBuySystems(pos, buyButtons)
            StoreData.Section.WEAPONS -> drawBuyWeapons(pos, buyButtons)
        }

        for (button in buyButtons) {
            if (updatingBuyButtons) {
                buttons.add(button)

                // Be sure to set the window position before drawing the button,
                // to avoid it flickering in the wrong position for the first
                // frame it's drawn.
                button.windowOffset = position
            }

            button.draw(g)
        }
    }

    private fun drawBuySystems(pos: IPoint, buyButtons: ArrayList<BuyButton>) {
        // We only need to add buy buttons, no custom drawing.
        if (!updatingBuyButtons)
            return

        for (i in 0 until StoreData.ITEMS_PER_SECTION) {
            val system = store.systems.getOrNull(i)
            val isSubSystem = system?.info?.isSubSystem == true

            val images = when {
                // The disabled image still has the cutout for the system icon, so
                // use the sold-out weapon image instead.
                system == null -> ButtonImageSet.static(game, "img/storeUI/store_weapons_off.png")

                isSubSystem -> ButtonImageSet.select2(game, "img/storeUI/store_subsystems")
                else -> ButtonImageSet.select2(game, "img/storeUI/store_systems")
            }

            // Ships can have several slots for a system, so use one that's free
            val systemSlot = system?.let { ship.findFreeSystemSlot(it) }
            val buttonPos = pos + ConstPoint(5, 17 + i * LIST_ROW_HEIGHT)
            buyButtons.add(object : BuyButton(buttonPos, images, ConstPoint(345, 27)) {
                override val blueprint: Blueprint? get() = system

                override val price: Int get() = system?.cost ?: 0

                override val customDisabled: Boolean
                    get() {
                        // Stop the user from buying a system their ship doesn't support
                        if (systemSlot == null)
                            return true

                        // Stop the user from installing more than eight systems (or
                        // however many the ship allows), unless this replaces an
                        // existing system (clonebay/medbay).
                        val numSystems = ship.mainSystems.size
                        if (numSystems >= ship.maxMainSystems && systemSlot.room.system == null && isSubSystem != true)
                            return true

                        // The same goes for subsystems
                        if (isSubSystem && ship.subSystems.size >= ship.maxSubSystems)
                            return true

                        // Stop the user from installing the same system twice
                        return systemSlot.isInstalled
                    }

                // Buying a clonebay replaces the medbay, and the other way round
                override val confirmKey: String?
                    get() = when (systemSlot?.room?.system) {
                        is Medbay -> "confirm_buy_clonebay"
                        is Clonebay -> "confirm_buy_medbay"
                        else -> null
                    }

                override fun buy() {
                    store.systems[i] = null
                    updateButtons() // Make this button show as sold out

                    // A clonebay bought in place of the medbay (or the other
                    // way round) is upgraded as far as the one it replaces, so
                    // its upgrades aren't lost
                    val replaced = systemSlot!!.room.system
                    systemSlot.room.setSystem(systemSlot)
                    val bought = systemSlot.room.system
                    if (bought != null && (replaced is Medbay || replaced is Clonebay)) {
                        bought.energyLevels = maxOf(
                            bought.energyLevels,
                            replaced.energyLevels.coerceAtMost(bought.blueprint.maxPower)
                        )
                    }

                    // The drones system comes with a free drone, as in vanilla
                    val drone = store.freeDrone
                    if (system?.info == Drones.INFO && drone != null) {
                        store.freeDrone = null
                        ship.addBlueprint(drone, true)
                    }
                }

                override fun drawInfoPanel(g: Graphics) {
                    if (system == null)
                        return

                    val drone = store.freeDrone
                    val freeDrone = if (system.info == Drones.INFO && drone != null)
                        listOf(game.translator["free_drone"].replaceArg(game.translator[drone.title!!]))
                    else emptyList()
                    val extraHeight = infoPanel.drawDescriptionBoxSystem(system, freeDrone)
                    infoPanel.drawSystemPowerBox(g, system, system.startPower, 0, extraHeight)
                }
            })
        }
    }

    private fun drawBuyCrew(pos: IPoint, buttons: ArrayList<BuyButton>) {
        // We only need to add buy buttons, no custom drawing.
        if (!updatingBuyButtons)
            return

        val images = ButtonImageSet.select2(game, "img/storeUI/store_buy_crew")

        for (index in 0 until StoreData.ITEMS_PER_SECTION) {
            val crew = store.crew.getOrNull(index)

            val buttonPos = pos + gridOffset(index) + ConstPoint(19, 46)
            buttons += object : BuyButton(buttonPos, images, ConstPoint(33, 89)) {
                override val blueprint: Blueprint? get() = crew?.race
                override val price: Int get() = crew!!.race.cost

                override val customDisabled: Boolean = game.isPlayerCrewFull

                override fun buy() {
                    store.crew[index] = null
                    ship.addCrewMember(crew!!, false)
                    updateButtons()
                }

                override fun draw(g: Graphics) {
                    val image = when {
                        disabled -> image.off
                        hovered -> image.hover
                        else -> image.normal
                    }
                    // 9px of padding
                    image.draw(this.pos.x - 9, this.pos.y - 9)

                    if (crew == null) {
                        return
                    }

                    crewNameFont.drawStringCentred(
                        this.pos.x + 2f, this.pos.y + 61f, 96f,
                        crew.name,
                        textColour
                    )

                    val yOffset = when (crew.race.name) {
                        "rock", "mantis", "crystal" -> -10
                        else -> -5
                    }

                    val scale = 2
                    val portraitX = this.pos.x + (96 - 35 * scale) / 2
                    crew.drawPortrait(portraitX, this.pos.y + yOffset, scale.f)

                    numberFont.drawString(
                        this.pos.x + priceOffset.x.f,
                        this.pos.y + priceOffset.y.f,
                        price.toString(),
                        textColour
                    )
                }

                override fun drawInfoPanel(g: Graphics) {
                    if (crew == null)
                        return

                    infoPanel.drawCrew(g, crew)
                }
            }
        }
    }

    @Suppress("DuplicatedCode") // Duplicated with BuyDrones
    private fun drawBuyWeapons(pos: ConstPoint, buyButtons: ArrayList<BuyButton>) {
        // We only need to add buy buttons, no custom drawing.
        if (!updatingBuyButtons)
            return

        val images = ButtonImageSet.select2(game, "img/storeUI/store_buy_weapons")

        for (i in 0 until StoreData.ITEMS_PER_SECTION) {
            val weapon = store.weapons.getOrNull(i)

            val buttonPos = pos + gridOffset(i) + ConstPoint(10, 37)
            buyButtons.add(object : BuyButton(buttonPos, images, ConstPoint(42, 98)) {
                override val blueprint: Blueprint? get() = weapon
                override val price: Int get() = weapon?.cost ?: 0

                override val customDisabled: Boolean
                    get() = weapon != null && !ship.hasRoomFor(weapon)

                override fun buy() {
                    store.weapons[i] = null
                    updateButtons() // Make this button show as sold out

                    if (!ship.addBlueprint(weapon!!, false))
                        error("Couldn't find space to place purchased weapon!")
                }

                override fun drawInfoPanel(g: Graphics) {
                    if (weapon == null)
                        return

                    infoPanel.drawWeapon(weapon)
                }
            })
        }
    }

    @Suppress("DuplicatedCode") // Duplicated with BuyWeapons
    private fun drawBuyDrones(pos: ConstPoint, buttons: ArrayList<BuyButton>) {
        // We only need to add buy buttons, no custom drawing.
        if (!updatingBuyButtons)
            return

        val images = ButtonImageSet.select2(game, "img/storeUI/store_buy_drones")

        for (i in 0 until StoreData.ITEMS_PER_SECTION) {
            val drone = store.drones.getOrNull(i)

            val buttonPos = pos + gridOffset(i) + ConstPoint(10, 37)
            buttons.add(object : BuyButton(buttonPos, images, ConstPoint(42, 98)) {
                override val blueprint: Blueprint? get() = drone
                override val price: Int get() = drone?.cost ?: 0

                // (in a drone slot or the cargo, not a weapon slot, as the
                // weapons' button checked)
                override val customDisabled: Boolean
                    get() = drone != null && !ship.hasRoomFor(drone)

                override fun buy() {
                    store.drones[i] = null
                    updateButtons() // Make this button show as sold out

                    if (!ship.addBlueprint(drone!!, false))
                        error("Couldn't find space to place purchased drone!")
                }

                override fun drawInfoPanel(g: Graphics) {
                    if (drone == null)
                        return

                    infoPanel.drawDrone(drone)
                }
            })
        }
    }

    private fun drawBuyAugments(pos: ConstPoint, buyButtons: ArrayList<BuyButton>) {
        // We only need to add buy buttons, no custom drawing.
        if (!updatingBuyButtons)
            return

        val images = ButtonImageSet.select2(game, "img/storeUI/store_weapons")

        for (i in 0 until StoreData.ITEMS_PER_SECTION) {
            val augment = store.augments.getOrNull(i)

            val buttonPos = pos + ConstPoint(5, 17 + i * LIST_ROW_HEIGHT)
            buyButtons.add(object : BuyButton(buttonPos, images, ConstPoint(345, 27)) {
                override val blueprint: Blueprint? get() = augment

                override val price: Int get() = augment?.cost ?: 0

                override val customDisabled: Boolean
                    get() {
                        // Don't let the player buy multiple of a non-stackable augment.
                        if (augment?.stackable == false) {
                            if (ship.augments.contains(augment))
                                return true
                        }

                        // Check if the user's augments are full
                        return ship.augments.size >= ship.maxAugments
                    }

                override fun buy() {
                    store.augments[i] = null
                    updateButtons() // Make this button show as sold out

                    if (!ship.addBlueprint(augment!!, false))
                        error("Couldn't find space to place purchased augment!")
                }

                override fun draw(g: Graphics) {
                    // There's no one way to draw augments, here for example they're just text.
                    // Thus we have to override it because BlueprintButton doesn't know how to.

                    val image = when {
                        disabled -> image.off
                        hovered -> image.hover
                        else -> image.normal
                    }
                    image.draw(this.pos)

                    if (empty)
                        return

                    val name = augment!!.translateTitle(game)
                    augmentNameFont.drawString(
                        this.pos.x + 48f,
                        this.pos.y + 26f,
                        name,
                        textColour
                    )

                    // Draw the price
                    if (!customDisabled) {
                        numberFont.drawString(
                            this.pos.x + priceOffset.x.f,
                            this.pos.y + priceOffset.y.f,
                            price.toString(),
                            textColour
                        )
                    }
                }

                override fun drawInfoPanel(g: Graphics) {
                    if (augment == null)
                        return

                    infoPanel.drawAugment(augment)
                }
            })
        }
    }

    /**
     * Find where the [index]th item goes, relative to the first, in sections
     * with a grid of items (weapons, drones and crew).
     */
    private fun gridOffset(index: Int): ConstPoint {
        val column = index % GRID_COLUMNS
        val row = index / GRID_COLUMNS
        return ConstPoint(column * GRID_COLUMN_WIDTH, row * GRID_ROW_HEIGHT)
    }

    private fun drawSell(g: Graphics) {
        // This is stretched if there's lots of weapons
        sellPanel.drawBackground(sellImage, position)

        buyTabButton.draw(g)

        sellPanel.draw(g)

        // Draw the information for the currently-hovered blueprint.
        sellPanel.drawInfoPanel(infoPanel)
    }

    override fun escapePressed() {
        close()
    }

    override fun mouseClick(button: Int, x: Int, y: Int) {
        super.mouseClick(button, x, y)

        if (sellTab)
            sellPanel.mouseClick(button, x, y)
    }

    override fun mouseReleased(button: Int, x: Int, y: Int) {
        super.mouseReleased(button, x, y)

        if (sellTab)
            sellPanel.mouseReleased(button, x, y)
    }

    override fun updateUI(x: Int, y: Int) {
        super.updateUI(x, y)

        if (sellTab)
            sellPanel.updateUI(x, y)
    }

    override fun shipModified() {
        super.shipModified()
        sellPanel.shipModified()
    }

    override fun positionUpdated() {
        super.positionUpdated()
        infoPanel.position = position + ConstPoint(size.x + 13, 32)

        // Keep the close button at the bottom of the window, which moves
        // down as the buy tab is stretched to fit the extra items, and if the
        // sell panel is stretched to fit extra weapon slots.
        val bottomOffset = when (sellTab) {
            true -> sellPanel.extraHeight
            false -> BUY_EXTRA_HEIGHT
        }
        closeButton.windowOffset = position + ConstPoint(0, bottomOffset)
    }

    abstract inner class BuyButton(pos: IPoint, images: ButtonImageSet, val priceOffset: IPoint) :
        Buttons.BlueprintButton(pos, game, images) {

        abstract val price: Int

        abstract fun buy()

        /**
         * Vanilla's question to ask before buying this (such as
         * confirm_buy_clonebay, when it replaces the medbay), if any.
         */
        open val confirmKey: String? get() = null

        override fun draw(g: Graphics) {
            super.draw(g)

            // Don't draw the price text if we've already sold it
            // (or in the case of resources, sold out).
            if (empty)
                return

            numberFont.drawString(
                pos.x + priceOffset.x.f,
                pos.y + priceOffset.y.f,
                price.toString(),
                textColour
            )
        }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            if (disabled)
                return

            if (ship.scrap < price) {
                game.shipUI.playInsufficientScrapAnimation()
                return
            }

            val key = confirmKey
            if (key != null) {
                game.shipUI.confirm(key) { purchase() }
                return
            }
            purchase()
        }

        private fun purchase() {
            // Check again, in case anything changed while confirming
            if (disabled || ship.scrap < price)
                return
            ship.scrap -= price

            buySound.play()

            buy()
        }

        abstract fun drawInfoPanel(g: Graphics)
    }

    inner class ResourceButton(pos: IPoint, val resource: Resource) : BuyButton(
        pos,
        ButtonImageSet.select2(game, "img/storeUI/store_items_${resourceTextureName(resource)}"),
        ConstPoint(169, 40)
    ) {
        override val blueprint = resource.getBlueprint(game)!!

        val numAvailable: Int get() = store.availableResources[resource] ?: 0

        override val disabled: Boolean get() = numAvailable == 0

        override val price: Int get() = blueprint.cost

        override fun draw(g: Graphics) {
            val img = when {
                numAvailable == 0 -> image.off
                hovered -> image.hover
                else -> image.normal
            }
            img.draw(pos)

            // Don't draw the available quantity if we've sold out.
            if (numAvailable == 0)
                return

            val textColour = when {
                hovered -> Constants.STORE_BUY_HOVER
                else -> Constants.SECTOR_CUTOUT_TEXT
            }

            numberFont.drawString(pos.x + 130f, pos.y + 26f, price.toString(), textColour)

            numberFont.drawString(pos.x + 70f, pos.y + 26f, numAvailable.toString(), textColour)
        }

        override fun buy() {
            if (numAvailable == 0)
                return

            val resourceSet = ResourceSet()
            resourceSet[resource] = 1
            game.givePlayerResources(resourceSet)

            store.availableResources[resource] = numAvailable - 1
        }

        override fun drawInfoPanel(g: Graphics) {
            if (numAvailable == 0)
                return

            infoPanel.drawItem(blueprint)
        }
    }

    private abstract inner class RepairButton(pos: IPoint, val repairImg: ButtonImageSet, priceOffset: IPoint) :
        BuyButton(pos, repairImg, priceOffset) {

        override val customDisabled: Boolean get() = ship.health >= ship.maxHealth
        override val blueprint: Blueprint get() = error("Repairs don't use blueprints")
        override val empty: Boolean get() = false

        abstract val nameKey: String

        override fun draw(g: Graphics) {
            val image = when {
                disabled -> repairImg.off
                hovered -> repairImg.hover
                else -> repairImg.normal
            }
            image.draw(this.pos)

            val ourTextColour = when {
                disabled -> Constants.JUMP_DISABLED
                hovered -> Constants.STORE_BUY_HOVER
                else -> Constants.SECTOR_CUTOUT_TEXT
            }

            repairFont.drawStringCentred(
                pos.x + 2f, pos.y + 28f, 99f,
                game.translator[nameKey],
                ourTextColour
            )

            if (!disabled) {
                numberFont.drawString(
                    pos.x + priceOffset.x.f,
                    pos.y + priceOffset.y.f,
                    price.toString(),
                    ourTextColour
                )
            } else {
                repairFont.drawStringCentred(
                    pos.x + 103f, pos.y + 28f, 64f,
                    game.translator["repair_max"],
                    ourTextColour
                )
            }
        }

        override fun drawInfoPanel(g: Graphics) {
            // No message
        }
    }

    companion object {
        const val GLOW_WIDTH: Int = 7

        // Systems and augments are listed in rows
        private const val LIST_ROW_HEIGHT = 53

        // Weapons, drones and crew are in a grid
        private const val GRID_COLUMNS = 3
        private const val GRID_COLUMN_WIDTH = 125
        private const val GRID_ROW_HEIGHT = 145

        /**
         * How much taller each of the two buy sections is than in vanilla,
         * to fit the extra items. That's either more rows in a list, or more
         * rows in a grid, whichever needs more space.
         */
        private val SECTION_EXTRA_HEIGHT: Int = run {
            val items = StoreData.ITEMS_PER_SECTION
            val vanillaItems = StoreData.VANILLA_ITEMS_PER_SECTION
            val listExtra = (items - vanillaItems) * LIST_ROW_HEIGHT
            val gridRows = (items + GRID_COLUMNS - 1) / GRID_COLUMNS
            val gridExtra = (gridRows - 1) * GRID_ROW_HEIGHT
            maxOf(listExtra, gridExtra, 0)
        }

        // How much taller the whole buy tab is than in vanilla
        private val BUY_EXTRA_HEIGHT: Int = 2 * SECTION_EXTRA_HEIGHT

        // Where to split the buy tab's background to stretch the top and
        // bottom sections, in image coordinates. These are near the bottom of
        // each section, where it's plain. The bottom one is above the current
        // hull box, so it's moved down with the bottom section.
        private const val TOP_BACKGROUND_SPLIT_Y = 228
        private const val TOP_BACKGROUND_CHUNK_HEIGHT = 30
        private const val BOTTOM_BACKGROUND_SPLIT_Y = 400
        private const val BOTTOM_BACKGROUND_CHUNK_HEIGHT = 40

        private fun resourceTextureName(resource: Resource): String {
            return when (resource) {
                Resource.FUEL -> "fuel"
                Resource.MISSILES -> "missiles"
                Resource.DRONES -> "drones"
                Resource.SCRAP -> error("Can't buy resource $resource")
            }
        }
    }
}
