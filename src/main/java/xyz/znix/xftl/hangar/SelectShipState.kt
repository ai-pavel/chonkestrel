package xyz.znix.xftl.hangar

import org.jdom2.JDOMException
import org.jdom2.input.SAXBuilder
import xyz.znix.xftl.*
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.game.ShipBlueprint
import xyz.znix.xftl.game.ShipFamily
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.rendering.WindowRenderer
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.Input
import xyz.znix.xftl.sys.ResourceContext
import xyz.znix.xftl.ui.*
import java.io.IOException

/**
 * The hangar, where the player picks the ship to start with. From the main
 * menu's new game window, it's opened with the [difficulty] picked there
 * (picked here to start with) and the crew names [namePreset] the game uses;
 * escape goes back to the menu.
 */
class SelectShipState @JvmOverloads constructor(
    private val datafile: Datafile,
    private val main: MainGame,
    val difficulty: Difficulty = Difficulty.NORMAL,
    private val namePreset: String? = null,
) : MainGame.GameState() {
    private val resourceContext = ResourceContext()

    val mousePos = Point(0, 0)
    val shipOffset = Point(0, 0)

    private val shipSelectorPos = ConstPoint(20, 50)

    // This is the size of the game window, not the full screen - but 'window'
    // also refers to in-game windows.
    val screenSize = Point(0, 0)

    val blueprints: BlueprintManager = BlueprintManager(datafile, true)
    // In the language picked in the options (see MainGame.setLanguage)
    val translator: Translator = Translator(datafile, main.language.code)
    val animations: Animations = Animations(datafile)

    val roomImageMeta: RoomImageMeta = RoomImageMeta.loadFromResource()

    val windowRenderer: WindowRenderer

    val shipFamilies: ShipFamily.FamilyTable
    val ships: List<LazyShipBlueprint>

    // Each big ship's vanilla ship (see ShipBlueprint.vanillaShip), and each
    // vanilla ship's big version, for the Normal and Chonky buttons
    private val vanillaOf: Map<String, String>
    private val bigOf: Map<String, String>

    private val current: EditableShip get() = editor.ship
    private val currentBlueprint: ShipBlueprint get() = blueprints.getShip(current.baseBlueprint)

    var isShipEdited = false
        private set

    /**
     * Whether the ship's shown with its rooms hidden, just its hull (the
     * hide rooms button), while it's not being edited.
     */
    var roomsHidden = false
        private set

    private val images = HashMap<String, Image>()
    private val fonts = HashMap<String, SILFontLoader>()

    private val editFileControls: EditFileControls

    private lateinit var editor: ShipEditor

    private val startGameButton: StartGameButton

    private val uiProvider: UIProvider = ShipSelectUIProvider()
    private val shipSelector: WidgetContainer

    private val shipList: ShipList
    private var shipListVisible = false

    val font = getFont("c&c")
    val fontHL2 = getFont("HL2")

    val missingImage = getImgOrNull("img/nullResource.png") ?: error("Missing nullResource.png")

    init {
        startGameButton = StartGameButton(this)
        editFileControls = EditFileControls(this)

        val background = getImg("img/window_base.png")
        val outline = getImg("img/window_outline.png")
        val mask = getImg("img/window_mask.png")
        windowRenderer = WindowRenderer(background, outline, mask)

        // TODO load this via the usual asset system, so mods can override it
        // TODO de-duplicate with InGameState
        val builder = SAXBuilder()
        builder.setExpandEntities(false)
        try {
            val doc = builder.build(javaClass.getResourceAsStream("/assets/data/xftl_ships.xml"))
            shipFamilies = ShipFamily.FamilyTable(doc)
        } catch (e: IOException) {
            throw RuntimeException("Failed to load ship data", e);
        } catch (e: JDOMException) {
            throw RuntimeException("Failed to parse ship data", e);
        }
        val familyNames = shipFamilies.families.map { it.ships[0] }

        // Both the mods' ships and vanilla's
        ships = blueprints.blueprints.values
            .asSequence()
            .filterIsInstance(LazyShipBlueprint::class.java)
            .filter { it.name.startsWith("PLAYER_SHIP_") && it.name !in HIDDEN_SHIPS }
            .sortedBy { it.name }
            .sortedBy { ship -> familyNames.indexOfFirst { ship.name.startsWith(it) } }
            .toList()

        // (a big ship whose vanilla ship isn't shown has none; if two name
        // the same vanilla ship, it goes to the first)
        val shown = ships.map { it.name }.toSet()
        vanillaOf = ships.filter { it.shipSize > 1 }
            .mapNotNull { ship -> ship.vanillaShip?.takeIf { it in shown }?.let { ship.name to it } }
            .toMap()
        bigOf = LinkedHashMap<String, String>().apply {
            for ((big, vanilla) in vanillaOf)
                putIfAbsent(vanilla, big)
        }

        shipSelector = SpecDeserialiser(uiProvider).load("ship_list_abc").mainWidget

        // A random ship as big as the current one (another big ship, of any
        // layout, or another vanilla one), keeping to what the Normal and
        // Chonky buttons picked
        shipSelector.addButtonListener("random") {
            val big = currentBlueprint.shipSize > 1
            selectShip(ships.filter { (it.shipSize > 1) == big }.ifEmpty { ships }.random().real)
        }

        shipSelector.addButtonListener("type_a") { selectShipVariant(0) }
        shipSelector.addButtonListener("type_b") { selectShipVariant(1) }
        shipSelector.addButtonListener("type_c") { selectShipVariant(2) }

        shipSelector.addButtonListener("size_normal") { selectShipSize(false) }
        shipSelector.addButtonListener("size_chonky") { selectShipSize(true) }

        shipSelector.addButtonListener("list") { shipListVisible = true }

        // Hide (and show again) the ship's rooms, to see its hull. Its
        // button's laid out for its wider label, show rooms (see
        // ship_list_abc.xml), and starts as hide rooms.
        val roomsLabel = shipSelector.byId["hide_rooms_label"] as Label
        roomsLabel.text = translator["ship_hiderooms_button"]
        shipSelector.addButtonListener("hide_rooms") {
            roomsHidden = !roomsHidden
            roomsLabel.text = translator[if (roomsHidden) "ship_showrooms_button" else "ship_hiderooms_button"]
        }
        // The arrows either side of the list button go through the ships in
        // the list's order (the big ships, then vanilla's), round and round
        (shipSelector.byId["previous_ship"] as ImageButton).clickListener = { stepShip(-1) }
        (shipSelector.byId["next_ship"] as ImageButton).clickListener = { stepShip(1) }

        shipList = ShipList(this) {
            shipListVisible = false
            if (it != null) {
                selectShip(it.real)
            }
        }

        // Start with a mod's ship if it asks for that, otherwise the Kestrel
        // (or, if that isn't shown, the first ship there is)
        val defaultShip = ships.firstOrNull { it.isDefaultShip }
            ?: ships.firstOrNull { it.name == shipFamilies.families.first().ships[0] }
            ?: ships.first()
        selectShip(defaultShip.real)
    }

    override fun shutdown() {
        resourceContext.freeAll()
    }

    override fun render(container: GameContainer, g: Graphics) {
        g.clear(Colour.darkGray)

        screenSize.x = container.width
        screenSize.y = container.height

        // The hangar's background, covering the screen (see tools/hangar_art.py)
        val background = getImgOrNull(BACKGROUND)
        var padCentreX = 250
        var padCentreY = 100
        if (background != null) {
            val scale = maxOf(container.width.f / background.width, container.height.f / background.height)
            val left = (container.width - background.width * scale) / 2
            val top = (container.height - background.height * scale) / 2
            background.draw(
                left, top, left + background.width * scale, top + background.height * scale,
                0f, 0f, background.width.f, background.height.f, 1f, Colour.white
            )
            padCentreX = (left + PAD_CENTRE.x * scale).toInt()
            padCentreY = (top + PAD_CENTRE.y * scale).toInt()
        }

        g.pushTransform()
        g.translate(shipSelectorPos.x.f, shipSelectorPos.y.f)
        shipSelector.draw(g)
        g.popTransform()

        startGameButton.draw(g)

        // Editing ships is for debugging (and not shown in the screenshots)
        if (XftlDebug.enabled && !XftlDebug.recording)
            editFileControls.draw(g)

        g.pushTransform()
        // The ship's in the middle of the landing pad, or where it always
        // was without the background
        val hull = currentBlueprint.hullImage.firstNotNullOfOrNull { getImgOrNull(it) }
        if (background != null && hull != null) {
            shipOffset.x = padCentreX - hull.width / 2 - currentBlueprint.hullOffset.x
            shipOffset.y = padCentreY - hull.height / 2 - currentBlueprint.hullOffset.y
        } else {
            shipOffset.x = 250 - currentBlueprint.hullOffset.x
            shipOffset.y = 100 - currentBlueprint.hullOffset.y
        }
        g.translate(shipOffset.x.f, shipOffset.y.f)
        val hideRooms = roomsHidden && !isShipEdited
        current.draw(g, this, false, hideRooms)
        editor.editorWidth = container.width - shipOffset.x
        editor.editorHeight = container.height - shipOffset.y
        if (!hideRooms)
            editor.draw(g)
        g.popTransform()

        if (shipListVisible) {
            shipList.draw(container, g)
        }
    }

    override fun update(container: GameContainer, delta: Float) {
        val input = container.input
        mousePos.x = input.mouseX
        mousePos.y = input.mouseY

        shipSelector.updateMouse(mousePos.x - shipSelectorPos.x, mousePos.y - shipSelectorPos.y)
    }

    override fun mouseClicked(button: Int, x: Int, y: Int, clickCount: Int) {
        if (shipListVisible) {
            shipList.mouseClicked(button)
            return
        }

        // Block editor interactions if we're only inspecting a ship
        if (isShipEdited) {
            editor.mouseClicked(button, x - shipOffset.x, y - shipOffset.y, clickCount)
        }

        startGameButton.mouseClicked(button)

        if (XftlDebug.enabled && !XftlDebug.recording)
            editFileControls.mouseClicked(button)

        shipSelector.mouseClicked(button)
    }

    override fun mousePressed(button: Int, x: Int, y: Int) {
        if (shipListVisible)
            return

        if (isShipEdited) {
            editor.mousePressed(button, x - shipOffset.x, y - shipOffset.y)
        }
    }

    override fun mouseReleased(button: Int, x: Int, y: Int) {
        if (shipListVisible)
            return

        if (isShipEdited) {
            editor.mouseReleased(button, x - shipOffset.x, y - shipOffset.y)
        }
    }

    override fun mouseDragged(oldX: Int, oldY: Int, newX: Int, newY: Int) {
        if (shipListVisible)
            return

        if (isShipEdited) {
            editor.mouseDragged(oldX - shipOffset.x, oldY - shipOffset.y, newX - shipOffset.x, newY - shipOffset.y)
        }
    }

    override fun mouseMoved(oldX: Int, oldY: Int, newX: Int, newY: Int) {
        if (shipListVisible)
            return

        if (isShipEdited) {
            editor.mouseMoved(newX - shipOffset.x, newY - shipOffset.y)
        }
    }

    override fun mouseWheelMoved(change: Int) {
        if (shipListVisible) {
            shipList.mouseWheelMoved(change)
            return
        }

        if (isShipEdited) {
            editor.mouseWheelMoved(change)
        }
    }

    override fun keyReleased(key: Int, c: Char) {
        if (shipListVisible) {
            if (key == Input.KEY_ESCAPE)
                shipListVisible = false
            return
        }

        // Back to the main menu, as it brought the player here (but not
        // while the ship's being edited, where escape's the editor's)
        if (key == Input.KEY_ESCAPE && !isShipEdited) {
            main.switchToMainMenu()
            return
        }

        editor.keyReleased(key, c)
    }

    fun getImg(path: String): Image {
        getImgOrNull(path)?.let { return it }

        println("[WARN] Invalid path for image: '$path'")
        images[path] = missingImage // Don't repeat the warning for each getImg call
        return missingImage
    }

    fun getImgOrNull(path: String): Image? {
        images[path]?.let { return it }

        val file = datafile.getOrNull(path) ?: return null
        val img = datafile.readImage(resourceContext, file)
        images[path] = img
        return img
    }

    fun getFont(name: String): SILFontLoader {
        // Always return a copy of the font, so our instance doesn't get broken
        // when a consumer sets their instance's scale property or anything
        // like that.
        fonts[name]?.let { return SILFontLoader(it) }

        val font = SILFontLoader(resourceContext, datafile, datafile["fonts/$name.font"])
        fonts[name] = font
        return SILFontLoader(font)
    }

    fun startGame(selected: Difficulty) {
        val editedShip = if (isShipEdited) editor.ship else null
        main.startNewGame(currentBlueprint.name, selected, editedShip, namePreset)
    }

    fun startEditingShip() {
        isShipEdited = true
    }

    fun stopEditingShip() {
        isShipEdited = false

        // Re-create the editor to throw away the changes
        editor = ShipEditor(this, EditableShip.fromBlueprint(currentBlueprint))
    }

    /**
     * Draw just a ship, as it looks in the hangar, with its hull's top left
     * at the origin. The icon renderer uses this (see IconDirector).
     */
    fun drawShipOnly(g: Graphics, shipName: String) {
        val blueprint = blueprints.getShip(shipName)
        g.pushTransform()
        g.translate(-blueprint.hullOffset.x.toFloat(), -blueprint.hullOffset.y.toFloat())
        EditableShip.fromBlueprint(blueprint).draw(g, this, false)
        g.popTransform()
    }

    /**
     * The A, B and C variants of the ship [name] (null for one that isn't
     * shown): its family's (see ShipFamily), or for a mod's ship that isn't
     * in one, those named after it, X, X_2 and X_3, as vanilla's are.
     */
    fun variantsOf(name: String): List<String?> {
        val shown = ships.map { it.name }.toSet()
        val family = shipFamilies.byShipId[name]?.ships
            ?: VARIANT_SUFFIX.replace(name, "").let { base -> listOf(base, "${base}_2", "${base}_3") }
        return (0 until 3).map { family.getOrNull(it)?.takeIf { id -> id in shown } }
    }

    /**
     * Go [step] ships along the ship list (see ShipList.ships) from the
     * current one, round and round. A vanilla ship's B or C (which isn't
     * listed) counts as its A.
     */
    private fun stepShip(step: Int) {
        val listed = shipList.ships
        if (listed.isEmpty())
            return
        val variants = variantsOf(current.baseBlueprint)
        val here = listed.indexOfFirst { it.name == current.baseBlueprint }
            .takeIf { it != -1 } ?: listed.indexOfFirst { it.name in variants }
        val next = listed[Math.floorMod(here + step, listed.size)]
        selectShip(next.real)
    }

    /**
     * The ship the Normal and Chonky buttons switch [name] to: a big ship's
     * vanilla ship, or a vanilla ship's big version (see
     * ShipBlueprint.vanillaShip), so it's the same race and layout letter.
     * The big ships are only Type A and B, so a Type C goes to its family's
     * big Type A (or if there's none, its B). Null for a ship with neither.
     */
    fun counterpartOf(name: String): String? {
        vanillaOf[name]?.let { return it }
        bigOf[name]?.let { return it }
        val ship = ships.firstOrNull { it.name == name } ?: return null
        if (ship.shipSize > 1)
            return null
        return variantsOf(name).firstNotNullOfOrNull { variant -> variant?.let { bigOf[it] } }
    }

    /** Show the ship [name] (for the screenshots, see MainMenuState.showHangar). */
    fun showShip(name: String) = selectShip(ships.first { it.name == name }.real)

    private fun selectShip(blueprint: ShipBlueprint) {
        editor = ShipEditor(this, EditableShip.fromBlueprint(blueprint))

        // Update the A/B/C variant buttons
        val variants = variantsOf(blueprint.name)
        val typeId = variants.indexOf(blueprint.name).coerceAtLeast(0)

        fun updateTypeButton(id: String, idx: Int) {
            val button = shipSelector.byId[id] as UIKitButton

            if (idx == typeId) {
                // Disable to prevent clicking, but still show as selected.
                button.forceSelected = true
                button.disabled = true
            } else if (variants[idx] == null) {
                button.forceSelected = false
                button.disabled = true
            } else {
                button.forceSelected = false
                button.disabled = false
            }
        }

        updateTypeButton("type_a", 0)
        updateTypeButton("type_b", 1)
        updateTypeButton("type_c", 2)

        // And the Normal and Chonky buttons, the same way: the current one
        // selected, the other disabled if the ship has no counterpart
        val big = blueprint.shipSize > 1
        val counterpart = counterpartOf(blueprint.name)
        for ((id, forBig) in listOf("size_normal" to false, "size_chonky" to true)) {
            val button = shipSelector.byId[id] as UIKitButton
            button.forceSelected = forBig == big
            button.disabled = forBig == big || counterpart == null
        }
    }

    /**
     * Switch between the A/B/C variants of the current ship.
     */
    private fun selectShipVariant(index: Int) {
        val shipId = variantsOf(current.baseBlueprint)[index] ?: return

        val blueprint = ships.firstOrNull { it.name == shipId } ?: return
        selectShip(blueprint.real)
    }

    /**
     * Switch the current ship to its vanilla ship, or its big version, if
     * [big] (see [counterpartOf]).
     */
    private fun selectShipSize(big: Boolean) {
        if ((currentBlueprint.shipSize > 1) == big)
            return
        val shipId = counterpartOf(current.baseBlueprint) ?: return

        val blueprint = ships.firstOrNull { it.name == shipId } ?: return
        selectShip(blueprint.real)
    }

    private inner class ShipSelectUIProvider : UIProvider {
        override fun getFont(name: String): SILFontLoader {
            return this@SelectShipState.getFont(name)
        }

        override fun getImg(path: String): Image {
            return this@SelectShipState.getImg(path)
        }

        override fun translate(key: String): String? {
            return translator.translations[key]
        }

        override fun getDebugOutlineColour(widget: Widget): Colour? {
            return null
        }

        override fun getWindowRenderer(): WindowRenderer {
            return windowRenderer
        }
    }

    companion object {
        /**
         * Player ships in the game's files that aren't meant to be picked:
         * an old debug version of the Kestrel, and the tutorial's ship. Vanilla
         * doesn't show them either.
         */
        private val HIDDEN_SHIPS = setOf("PLAYER_SHIP_EASY", "PLAYER_SHIP_TUTORIAL")

        // The hangar's background (made by tools/hangar_art.py), and the
        // middle of its landing pad, in its pixels
        private const val BACKGROUND = "img/customizeUI/xftl_hangar_background.png"
        private val PAD_CENTRE = ConstPoint(950, 390)

        // A ship's B or C variant's name ends with this (see variantsOf)
        val VARIANT_SUFFIX = Regex("_[23]$")
    }
}
