package xyz.znix.xftl.game

import org.jdom2.Element
import xyz.znix.xftl.*
import xyz.znix.xftl.ai.FriendlyCrewAI
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Rectangle
import java.util.*

class ShipBlueprint(private val elem: Element, df: Datafile) : Blueprint(elem) {
    val layout: String = elem.getAttributeValue("layout")
    val img: String = elem.getAttributeValue("img")

    // TODO do this properly, to avoid potentially breaking mods
    val isFlagship: Boolean = name.startsWith("BOSS_")

    val isPlayerShip: Boolean = name.startsWith("PLAYER_SHIP_")

    /**
     * The localisation key of this type of ship, shown on the right-hand
     * side of the target panel for enemies.
     */
    val shipClass: GameText? = elem.getGameTextChild("class")

    /**
     * The localisation key of the ship, defining the in-hangar title
     * for player ships (eg 'The Kestrel').
     */
    val shipTitle: GameText? = elem.getGameTextChild("name")

    val weaponSlots: Int? = elem.getChildTextTrim("weaponSlots")?.toInt()

    /**
     * The maximum number of crew this ship can have. This isn't part of
     * vanilla FTL, where it's always eight, but mods can raise it.
     */
    val maxCrew: Int = elem.getChildTextTrim("maxCrew")?.toInt() ?: Ship.DEFAULT_MAX_CREW

    /**
     * The maximum number of main (non-sub) systems this ship can have. This
     * is also an XFTL extension, as it's always eight in vanilla.
     */
    val maxSystems: Int = elem.getChildTextTrim("maxSystems")?.toInt() ?: Ship.DEFAULT_MAX_SYSTEMS

    /**
     * The number of cargo slots and augments this ship has. These are also
     * XFTL extensions, as they're always four and three in vanilla.
     */
    val maxCargo: Int = elem.getChildTextTrim("maxCargo")?.toInt() ?: Ship.DEFAULT_MAX_CARGO
    val maxAugments: Int = elem.getChildTextTrim("maxAugments")?.toInt() ?: Ship.DEFAULT_MAX_AUGMENTS

    /**
     * How big this ship is, compared to the vanilla ships, which are all
     * size one. This is an XFTL extension.
     *
     * The player fights as many copies of each hostile ship as it takes to
     * match the player's ship (see [Opponent]), so ships twice the size of
     * the vanilla ones fight two of them at once, but only one ship of
     * their own size.
     */
    val shipSize: Int = elem.getChildTextTrim("shipSize")?.toInt()?.coerceAtLeast(1) ?: 1

    /**
     * The shape of a hostile ship, which sets the size of its window (see
     * [ShipShape]). An XFTL extension, set with an xftlShape element.
     */
    val shape: ShipShape = ShipShape.parse(elem.getChildTextTrim("xftlShape"), shipSize, name)

    /**
     * For one of the size 3 bosses (see doc/game/bosses.md), which one it is,
     * set with an xftlBoss element (an XFTL extension): its mechanics (see
     * [ColossalBoss]). A boss is built exactly as its blueprint says, rather
     * than with random weapons and power (see ShipGenerator).
     */
    val bossKind: ColossalBoss.Kind? = elem.getChildTextTrim("xftlBoss")?.let { ColossalBoss.Kind.valueOf(it) }

    /**
     * The ships this is a bigger version of (see [ShipShape]), set with an
     * xftlVariantOf element (an XFTL extension), a comma-separated list.
     * Bigger players' fights can have it instead of those ships, see
     * [FightPlan].
     */
    val variantOf: List<String> = xyz.znix.xftl.parseVariantOf(elem)

    /**
     * True if this is a bigger version of another ship, see [variantOf].
     */
    val isVariant: Boolean get() = variantOf.isNotEmpty()

    /**
     * For player ships, how many vanilla-sized hostile ships they fight at
     * once, set with an opponentShips element (an XFTL extension). This is
     * the ship's size by default. See [Opponent.enemyShipCount].
     */
    val opponentShips: Int = elem.getChildTextTrim("opponentShips")?.toInt()?.coerceAtLeast(1) ?: shipSize

    /**
     * How many vanilla-sized hostile ships player ships fight at once in the
     * first sector, set with an opponentShipsAtStart element (an XFTL
     * extension). This grows to [opponentShips] by the end of the first half
     * of the game. It's the same as [opponentShips] by default, so there's no
     * ramp. See [Opponent.rampedEnemyShipCount].
     */
    val opponentShipsAtStart: Int = elem.getChildTextTrim("opponentShipsAtStart")?.toInt()
        ?.coerceIn(1, opponentShips) ?: opponentShips
    val droneSlots: Int? = elem.getChildTextTrim("droneSlots")?.toInt()

    val isAutoScout = elem.getChild("crewCount")?.getAttributeValue("amount")?.trim() == "0"

    val maxHealth = elem.getChild("health").getAttributeValue("amount").toInt()

    /**
     * Weapons an enemy ship built from this blueprint always has, an XFTL
     * extension (`<xftlWeapon name="BEAM_BIO"/>`): they go in its first
     * weapons system before the random ones (see ShipGenerator).
     */
    val requiredWeapons: List<String> = elem.getChildren("xftlWeapon").map { it.getAttributeValue("name") }

    /**
     * Drones an enemy ship built from this blueprint always has, an XFTL
     * extension (`<xftlDrone name="BATTLE"/>`): they go in its drones
     * systems before the random ones, with more power for them if they need
     * it (see ShipGenerator).
     */
    val requiredDrones: List<String> = elem.getChildren("xftlDrone").map { it.getAttributeValue("name") }

    val startingReactorPower: Int = elem.getChild("maxPower").getAttributeValue("amount").toInt()

    val boardingStrategy: FriendlyCrewAI.BoardingStrategy =
        when (val name = elem.getChildTextTrim("boardingAI")) {
            "invasion" -> FriendlyCrewAI.BoardingStrategy.INVASION
            "sabotage" -> FriendlyCrewAI.BoardingStrategy.SABOTAGE
            null -> FriendlyCrewAI.BoardingStrategy.NONE
            else -> error("Invalid boardingAI tag '$name'")
        }

    // These are the paths to the relevant images, to keep this class lightweight.
    // For hullImage, there's multiple possible image paths - try them
    // and use the first one that exists.
    val hullImage: List<String>
    val floorImage: String?
    val cloakImage: String?

    val shieldImage: String

    // Note that hullOffset doesn't match up with Ship.hullOffset, since it's one
    // has the room offset (times ROOM_SIZE) added to it.
    val hullOffset: ConstPoint
    val floorOffset: ConstPoint
    val cloakOffset: ConstPoint

    val gibs: List<ShipGib>
    val hardpoints: List<ParsedHardpoint>
    val systems: List<ParsedSystem>

    /**
     * The ship's starting equipment and crew, and in debug mode, the one
     * from its xftlDebug element (an XFTL extension), if it has one. Which
     * one is used is decided when a ship is created, so debug mode can be
     * turned off to start a normal game (as the screenshots do).
     */
    private val normalLoadout: Loadout
    private val debugLoadout: Loadout?
    private val loadout: Loadout get() = debugLoadout?.takeIf { XftlDebug.enabled && !XftlDebug.plainPlayerShip } ?: normalLoadout

    /**
     * The ship's starting weapons, by slot. Slots can be left empty (null)
     * with the slot attribute on weapon elements, an XFTL extension, which
     * is how a ship can start with a weapon in its second weapons system.
     */
    val initialWeapons: List<String?> get() = loadout.weapons
    val initialDrones: List<String> get() = loadout.drones
    val initialAugments: List<String> get() = loadout.augments

    /**
     * The items in the ship's cargo hold when it's created. This is an XFTL
     * extension, set with cargo elements (normally in the debug loadout).
     */
    val initialCargo: List<String> get() = loadout.cargo

    /**
     * A list of crew specifications, which can each spawn
     * a variable number of crew.
     */
    val initialCrew: List<InitialCrewSpec> get() = loadout.crew

    val initialMissiles: Int get() = loadout.missiles
    val initialDroneParts: Int get() = loadout.droneParts

    private class Loadout(
        val weapons: List<String?>,
        val missiles: Int,
        val drones: List<String>,
        val droneParts: Int,
        val augments: List<String>,
        val cargo: List<String>,
        val crew: List<InitialCrewSpec>,
    )

    // Layout-related stuff
    val rooms: List<ParsedRoom> = ArrayList()
    val doors: List<ParsedDoor> = ArrayList()
    lateinit var shieldEllipse: Rectangle
        private set
    lateinit var roomOffset: ConstPoint
        private set

    init {
        val layoutName = elem.getAttributeValue("layout")
        val layoutElem = df.parseXML(df["data/$layoutName.xml"]).rootElement

        hullImage = listOf(
            "img/ship/${img}_base.png",
            "img/ships_glow/${img}_base.png"
        )
        hullOffset = Utils.parsePosElem(layoutElem.getChild("img"))

        // Note that the stage-2 and stage-3 boss layouts don't have
        // an offsets tag, but everything else does.
        val offsets: Element? = layoutElem.getChild("offsets")

        val floorImageName = elem.getChildTextTrim("floorImage") ?: img
        floorImage = validatePath(df, "img/ship/${floorImageName}_floor.png")
        floorOffset = offsets?.getChild("floor")?.let { Utils.parsePosElem(it) } ?: ConstPoint.ZERO

        // Load the cloak image - if one is set by name use that, otherwise
        // guess based on the ship name (this is required on the Kestrel, for example).
        val customCloakName = elem.getChildTextTrim("cloakImage")
        cloakImage = validatePath(df, "img/ship/${customCloakName ?: img}_cloak.png")
        cloakOffset = offsets?.getChild("cloak")?.let { Utils.parsePosElem(it) } ?: ConstPoint.ZERO

        val overrideShieldImage = elem.getChildTextTrim("shieldImage")
        shieldImage = when (isPlayerShip) {
            true -> "img/ship/${overrideShieldImage ?: img}_shields1.png"
            false -> "img/ship/enemy_shields.png"
        }

        // Load the room/door layout, as it's required for loading the systems.
        parseLayoutTxt(df.readString(df["data/$layout.txt"]))

        gibs = layoutElem.getChild("explosion").children.map { ShipGib(this, it) }

        // Load the hardpoints
        hardpoints = ArrayList()

        for (node in layoutElem.getChild("weaponMounts").children) {
            // In rebel_long.xml the hardpoint direction is missing for some testing stuff.
            // Artillery (used in the boss and federation cruiser) has slide set to 'no'
            val dirName = node.getAttributeValue("slide")?.toUpperCase(Locale.UK) ?: continue

            // To get this into the proper ship space, we have to use the ship's
            // hullOffset, which includes the room offset. This is a bit ugly, but it'll do.
            val pos = Utils.parsePosElem(node) + hullOffset + roomOffset * Constants.ROOM_SIZE

            val dir = if (dirName == "NO") null else dirName.let(Direction::valueOf)
            hardpoints += ParsedHardpoint(
                pos,
                node.getAttributeValue("rotate")!!.toBoolean(),
                node.getAttributeValue("mirror")!!.toBoolean(),
                node.getAttributeValue("gib").toInt(),
                dir
            )
        }

        // Parse the systems
        systems = ArrayList()
        for ((index, systemElem) in elem.getChild("systemList").children.withIndex()) {
            val room = rooms[systemElem.getAttributeValue("room").toInt()]

            val slotElem: Element? = systemElem.getChild("slot")
            val slotDirection: Direction? = slotElem?.getChildTextTrim("direction")
                ?.let { Direction.valueOf(it.toUpperCase(Locale.UK)) }
            val slotNumber: Int? = slotElem?.getChildTextTrim("number")?.toInt()

            systems += ParsedSystem(room, slotNumber, slotDirection, index, systemElem, isPlayerShip)
        }

        // Load the starting equipment

        // In debug mode, the player's ship can use a different starting
        // loadout (weapons, drones and crew), given in an xftlDebug element.
        // This is an XFTL extension. Anything it doesn't set is loaded as normal.
        normalLoadout = parseLoadout(elem, null)
        debugLoadout = elem.getChild("xftlDebug")?.let { parseLoadout(elem, it) }
    }

    private fun parseLoadout(elem: Element, debugElem: Element?): Loadout {
        fun loadoutChild(name: String): Element? = debugElem?.getChild(name) ?: elem.getChild(name)

        // Load all the weapon blueprints
        val weaponsElem: Element? = loadoutChild("weaponList")
        val weapons = ArrayList<String?>()
        if (weaponsElem != null) {
            // Weapons go in the next slot, unless they say which one (from 1)
            for (weaponElem in weaponsElem.children) {
                val slot = weaponElem.getAttributeValue("slot")?.toInt()?.minus(1) ?: weapons.size
                require(slot >= weapons.size) { "Weapon slot ${slot + 1} in ship $name is already used" }
                while (weapons.size < slot)
                    weapons.add(null)
                weapons.add(weaponElem.getAttributeValue("name"))
            }
        }
        val missiles = weaponsElem?.getAttributeValue("missiles")?.toInt() ?: 0

        // Load all the drone blueprints
        val dronesElem: Element? = loadoutChild("droneList")
        val drones = dronesElem?.children?.map { it.getAttributeValue("name") } ?: emptyList()
        val droneParts = dronesElem?.getAttributeValue("drones")?.toInt() ?: 0

        // Load any augments and cargo, either of which can come from the debug loadout
        val augmentsElem = if (debugElem?.getChild("aug") != null) debugElem else elem
        val augments = augmentsElem.getChildren("aug").map { it.getAttributeValue("name") }
        require(augments.size <= maxAugments) { "Ship $name has too many augments: $augments" }

        val cargoElem = if (debugElem?.getChild("cargo") != null) debugElem else elem
        val cargo = cargoElem.getChildren("cargo").map { it.getAttributeValue("name") }
        require(cargo.size <= maxCargo) { "Ship $name has too much cargo: $cargo" }

        // Load the initial crew
        val crewElem = if (debugElem?.getChild("crewCount") != null) debugElem else elem
        val crew = crewElem.getChildren("crewCount").map {
            val amount = it.requireAttributeValueInt("amount")
            val max = it.getAttributeValue("max")?.toInt()
            val race = it.getAttributeValue("class") ?: "human"
            val room = it.getAttributeValue("room")?.toInt()
            InitialCrewSpec(amount, race, max, room)
        }

        return Loadout(weapons, missiles, drones, droneParts, augments, cargo, crew)
    }

    private fun parseLayoutTxt(layout: String) {
        // Use this trick so we can mutate the arrays here, but nowhere else (without a cast).
        require(rooms is ArrayList)
        require(doors is ArrayList)

        val l = layout.replace("\r\n", "\n").split('\n')
        var i = 0

        var foundOffsetX = 0
        var foundOffsetY = 0

        var foundVertical = 0
        var foundHorizontal = 0

        fun roomByIdOrNull(id: Int): ParsedRoom? = if (id == -1) null else rooms[id]

        while (i < l.size) {
            val line = l[i++]
            when (line) {
                "X_OFFSET" -> foundOffsetX = l[i++].toInt()
                "Y_OFFSET" -> foundOffsetY = l[i++].toInt()
                "HORIZONTAL" -> foundHorizontal = l[i++].toInt()
                "VERTICAL" -> foundVertical = l[i++].toInt()
                "ELLIPSE" -> {
                    val size = ConstPoint(l[i++].toInt(), l[i++].toInt())
                    val pos = ConstPoint(l[i++].toInt(), l[i++].toInt())
                    shieldEllipse = Rectangle(pos, size)
                }

                "ROOM" -> {
                    val id = l[i++].toInt()
                    check(id == rooms.size)
                    val pos = ConstPoint(l[i++].toInt(), l[i++].toInt())
                    val size = ConstPoint(l[i++].toInt(), l[i++].toInt())
                    rooms += ParsedRoom(id, pos, size)
                    check(rooms[id].id == id)
                }

                "DOOR" -> {
                    val x = l[i++].toInt()
                    val y = l[i++].toInt()
                    val left = roomByIdOrNull(l[i++].toInt())
                    val right = roomByIdOrNull(l[i++].toInt())
                    val vertical = l[i++].toInt() == 1
                    doors += ParsedDoor(ConstPoint(x, y), left, right, vertical)
                }

                "" -> {
                }

                else -> error("Unknown line '$line'")
            }
        }

        // TODO figure out what these are for
        // The run block suppresses the unused variable warning
        run { foundVertical + foundHorizontal }

        roomOffset = ConstPoint(foundOffsetX, foundOffsetY)

        require(this::shieldEllipse.isInitialized) { "Shield ellipse not specified!" }
    }

    // Return the given path if valid, else null.
    private fun validatePath(df: Datafile, path: String): String? {
        if (df.getOrNull(path) == null)
            return null
        return path
    }

    // TODO remove this, after sorting out how the weaponList tags are
    //  loaded for both ShipGenerator and for player ships.
    @Deprecated("Load element attributes in ShipBlueprint directly")
    fun loadElem(): Element {
        return elem
    }

    class ParsedRoom(val id: Int, val pos: IPoint, val size: IPoint)
    class ParsedDoor(val pos: ConstPoint, val left: ParsedRoom?, val right: ParsedRoom?, val isVertical: Boolean)

    class ParsedHardpoint(
        val position: IPoint,
        val rotate: Boolean,
        val mirror: Boolean,
        val gib: Int,
        val slide: Direction?
    )

    /**
     * [room] is an XFTL extension, setting which room the player's crew start
     * in (manning its system, if there's one). If it's not set, they're put
     * at the ship's main computers, and then wherever there's space.
     */
    class InitialCrewSpec(val amount: Int, val race: String, val max: Int?, val room: Int?)

    class ParsedSystem(
        val room: ParsedRoom,

        override val slotNumber: Int?,
        override val slotDirection: Direction?,
        override val systemIndex: Int,

        elem: Element,
        useDefaultImage: Boolean
    ) : ISystemConfiguration {
        // The system name comes from the name of the element declaring it
        override val systemName: String = elem.name

        override val startingPower = elem.getAttributeValue("power").toInt()

        // Note that if not specified, the system is included by default. This
        // is commonly found with enemy ships.
        override val availableByDefault = elem.getAttributeValue("start")?.toBoolean() != false

        override val startsOff = elem.getAttributeValue("xftlStartOff")?.toBoolean() == true

        override val aiMaxPower: Int? = elem.getAttributeValue("max")?.toInt()

        override val interiorImage: String?

        override val weapon: String? = elem.getAttributeValue("weapon")

        init {
            var imagePath = elem.getAttributeValue("img")?.let { "img/ship/interior/$it.png" }

            // Use the default image, eg for Engi A's pilot and weapons.
            if (imagePath == null && useDefaultImage && systemName != "clonebay" && systemName != "teleporter") {
                imagePath = "img/ship/interior/room_$systemName.png"
            }

            interiorImage = imagePath
        }
    }
}
