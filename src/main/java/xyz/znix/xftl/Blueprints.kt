package xyz.znix.xftl

import org.jdom2.Document
import org.jdom2.Element
import xyz.znix.xftl.augments.AugEngiMedbots
import xyz.znix.xftl.augments.AugPreigniter
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.ShipBlueprint
import xyz.znix.xftl.systems.SystemBlueprint
import xyz.znix.xftl.weapons.*
import kotlin.random.Random

class BlueprintManager {
    val blueprints: Map<String, IBlueprint>
    val itemBlueprints: Map<String, ItemBlueprint>

    // The weapons' blueprint elements, to make the combined weapons from
    // (see WeaponSizes), and which of them are only in Advanced Edition
    private val weaponElements = HashMap<String, Element>()

    // The drones' blueprint elements, to make the combined drones from (see
    // DroneSizes)
    private val droneElements = HashMap<String, Element>()
    private val advancedEditionWeapons = HashSet<String>()
    // Whether the file being loaded is one of Advanced Edition's that
    // replaces blueprints (see loadFile)
    private var loadingOverwrite = false

    private var loadingAdvancedEdition = false

    private constructor(enableAE: Boolean, loader: (BlueprintManager) -> Unit) {
        blueprints = HashMap()
        itemBlueprints = HashMap()

        // Load all the blueprints
        loader(this)

        // If AE is enabled, rename all the OVERRIDE blueprints to
        // remove that prefix - thus they'll be used instead of the
        // original ones.
        if (enableAE) {
            // Use toList to duplicate the entries list, since we'll
            // be mutating the main blueprints map.
            for ((name, bp) in blueprints.entries.toList()) {
                if (!name.startsWith(AE_PREFIX))
                    continue
                blueprints[name.removePrefix(AE_PREFIX)] = bp
            }
        }

        // Remove the now-redundant override entries - this is probably
        // unnecessary, but it'll ensure we don't end up accidentally
        // using something from AE in non-AE mode.
        val toRemove = blueprints.keys.filter { it.startsWith(AE_PREFIX) }
        for (name in toRemove) {
            blueprints.remove(name)
        }

        addCombinedWeapons()
        addCombinedDrones()

        // Remove any blueprints from lists that don't actually exist
        for (bp in blueprints.values) {
            if (bp !is BlueprintList) continue
            bp.cleanup()
        }
    }

    constructor(df: Datafile, enableAE: Boolean) : this(enableAE, {
        it.loadFile(df, "blueprints.xml")
        it.loadFile(df, "autoBlueprints.xml")
        it.loadFile(df, "bosses.xml")
        if (enableAE) {
            it.loadFile(df, "dlcBlueprints.xml")
            it.loadFile(df, "dlcBlueprintsOverwrite.xml")
            it.loadFile(df, "dlcPirateBlueprints.xml")
        }
    })

    // Required to bind sounds to the weapons
    fun finishLoading(content: InGameState.GameContent) {
        for (bp in blueprints.values) {
            if (bp !is Blueprint)
                continue

            bp.finishSetup(content)
        }
    }

    operator fun get(name: String): IBlueprint = blueprints[name] ?: error("Unknown blueprint $name")

    fun getOrNull(name: String): IBlueprint? = blueprints[name]

    /**
     * The name of the player ship the hangar picks by default: a mod's ship
     * with xftlDefaultShip, otherwise the Kestrel.
     */
    val defaultPlayerShip: String
        get() = blueprints.values.filterIsInstance<LazyShipBlueprint>()
            .filter { it.name.startsWith("PLAYER_SHIP_") }
            .sortedBy { it.name }
            .firstOrNull { it.isDefaultShip }?.name ?: "PLAYER_SHIP_HARD"

    /**
     * The bigger versions of each ship (see [ShipShape]), by the name of the
     * ship they're versions of.
     */
    val shipVariants: Map<String, List<LazyShipBlueprint>>
        get() = shipVariantsCache ?: blueprints.values.filterIsInstance<LazyShipBlueprint>()
            .flatMap { variant -> variant.variantOf.map { it to variant } }
            .groupBy({ it.first }, { it.second })
            .also { shipVariantsCache = it }
    private var shipVariantsCache: Map<String, List<LazyShipBlueprint>>? = null

    /**
     * A version of the ship [name] with the given [shape], or null if it
     * doesn't have one (or it's [ShipShape.NORMAL]).
     */
    fun shipVariant(name: String, shape: ShipShape, random: Random): LazyShipBlueprint? {
        if (shape == ShipShape.NORMAL)
            return null
        return shipVariants[name]?.filter { it.shape == shape }?.randomOrNull(random)
    }

    fun getShip(name: String): ShipBlueprint {
        val blueprint = this[name] as LazyShipBlueprint
        return blueprint.real
    }


    private fun loadFile(df: Datafile, name: String) {
        val file = df["data/$name"]
        val parseXML = df.parseXML(file)
        loadingAdvancedEdition = name.startsWith("dlc")
        // Advanced Edition's *Overwrite.xml files replace blueprints on purpose
        loadingOverwrite = name.contains("Overwrite")
        loadDocument(df, parseXML.rootElement)
        loadingAdvancedEdition = false
        loadingOverwrite = false
    }

    /**
     * Add the combined weapons (see [WeaponSizes]): two and three of each
     * vanilla weapon that can be, in one slot.
     */
    private fun addCombinedWeapons() {
        val mutableBlueprints = blueprints as HashMap<String, IBlueprint>
        for ((name, elem) in weaponElements) {
            if (blueprints[name] !is AbstractWeaponBlueprint)
                continue
            if (!WeaponSizes.canCombine(elem, name in advancedEditionWeapons))
                continue
            for (size in WeaponSizes.SIZES) {
                val combined = WeaponSizes.combinedElement(elem, size)
                mutableBlueprints[combined.getAttributeValue("name")] = buildWeaponBlueprint(combined)
            }
        }
    }

    /**
     * Add the combined drones (see [DroneSizes]): two and three of each
     * drone that can be, launched from one slot.
     */
    private fun addCombinedDrones() {
        val mutableBlueprints = blueprints as HashMap<String, IBlueprint>
        for ((name, elem) in droneElements) {
            if (blueprints[name] !is DroneBlueprint || !DroneSizes.canCombine(elem))
                continue
            for (size in DroneSizes.SIZES) {
                val combined = DroneSizes.combinedElement(elem, size)
                mutableBlueprints[combined.getAttributeValue("name")] = buildDroneBlueprint(combined)
            }
        }
    }

    private fun loadDocument(df: Datafile, rootElement: Element) {
        val mutableBlueprints = blueprints as HashMap<String, IBlueprint>
        for (elem in rootElement.children) {
            // The name 'drones' conflicts with the drones system, and it's the only such name
            // conflict. Since we only need them for stores, put them in a separate list.
            if (elem.name == "itemBlueprint") {
                val item = ItemBlueprint(elem)
                (itemBlueprints as HashMap)[item.name] = item
                continue
            }

            // A blueprint that can't be read (such as one missing a field,
            // from a different version of the game's data or another mod) is
            // skipped with a warning, rather than stopping the game starting:
            // if it has the same name as one that's loaded, that one's kept.
            val bp = try {
                when (elem.name) {
                    "blueprintList" -> buildList(elem)
                    "weaponBlueprint" -> buildWeaponBlueprint(elem)
                    "droneBlueprint" -> buildDroneBlueprint(elem)
                    "systemBlueprint" -> buildSystemBlueprint(elem)
                    "crewBlueprint" -> buildCrewBlueprint(elem)
                    "augBlueprint" -> buildAugmentBlueprint(elem)

                    // We don't load the whole ShipBlueprint now for performance
                    // reasons, as it loads another XML file.
                    "shipBlueprint" -> LazyShipBlueprint(elem, df)

                    // Ignore unknown blueprints
                    else -> null
                }
            } catch (ex: Exception) {
                println("Warning: skipping ${elem.name} '${elem.getAttributeValue("name")}', which can't be read: $ex")
                null
            } ?: continue

            val bpName = elem.requireAttributeValue("name")

            // Remember the weapons' elements, for the combined weapons, and
            // which are new in Advanced Edition (rather than changed by it)
            if (elem.name == "weaponBlueprint" && !bpName.startsWith(AE_PREFIX) && bpName !in weaponElements) {
                weaponElements[bpName] = elem
                if (loadingAdvancedEdition)
                    advancedEditionWeapons += bpName
            }
            // (Advanced Edition's drones are combined too: the design
            // covers every vanilla drone, see DroneSizes)
            if (elem.name == "droneBlueprint" && !bpName.startsWith(AE_PREFIX) && bpName !in droneElements)
                droneElements[bpName] = elem

            if (mutableBlueprints.containsKey(bpName) && !loadingOverwrite) {
                println("Warning: duplicate blueprint name '$bpName'")
            }
            mutableBlueprints[bpName] = bp
        }
    }

    private fun buildList(elem: Element): IBlueprint {
        val items = ArrayList<String>()

        for (node in elem.children) {
            check(node.name == "name")
            items += node.textTrim
        }

        return BlueprintList(items, this)
    }

    private fun buildWeaponBlueprint(elem: Element): IBlueprint {
        val type = elem.getChildTextTrim("type")

        return when (type) {
            "LASER" -> LaserBlueprint(elem)
            "MISSILES" -> MissileBlueprint(elem)
            "BEAM" -> BeamBlueprint(elem)
            "BOMB" -> BombBlueprint(elem)
            "BURST" -> FlakBlueprint(elem)
            else -> UknWeaponBlueprint(elem)
        }
    }

    private fun buildDroneBlueprint(elem: Element): IBlueprint {
        return DroneBlueprint(elem)
    }

    private fun buildAugmentBlueprint(elem: Element): IBlueprint {
        return when (val name = elem.getAttributeValue("name")) {
            AugEngiMedbots.NAME -> AugEngiMedbots(elem)
            AugPreigniter.NAME -> AugPreigniter(elem)
            AugZoltanShield.NAME -> AugZoltanShield(elem)

            // Augments that use the default AugmentBlueprint and are implemented
            // as part of another system should be explicitly handled here.
            AugmentBlueprint.AUTOMATED_RELOADERS -> AugmentBlueprint(elem)
            AugmentBlueprint.LONG_RANGE_SCANNERS -> AugmentBlueprint(elem)
            AugmentBlueprint.RECONSTRUCTIVE_TELEPORT -> AugmentBlueprint(elem)
            AugmentBlueprint.OXYGEN_MASKS -> AugmentBlueprint(elem)
            AugmentBlueprint.BACKUP_DNA -> AugmentBlueprint(elem)
            AugmentBlueprint.BATTERY_CHARGER -> AugmentBlueprint(elem)
            AugmentBlueprint.SHIELD_CHARGE_BOOSTER -> AugmentBlueprint(elem)
            AugmentBlueprint.STEALTH_WEAPONS -> AugmentBlueprint(elem)
            AugmentBlueprint.ZOLTAN_BYPASS -> AugmentBlueprint(elem)
            AugmentBlueprint.DEFENSE_SCRAMBLER -> AugmentBlueprint(elem)
            AugmentBlueprint.FIRE_SUPPRESSION -> AugmentBlueprint(elem)
            AugmentBlueprint.SCRAP_RECOVERY_ARM -> AugmentBlueprint(elem)
            AugmentBlueprint.REPAIR_ARM -> AugmentBlueprint(elem)
            AugmentBlueprint.ADV_FTL_NAVIGATION -> AugmentBlueprint(elem)
            AugmentBlueprint.FTL_RECHARGE_BOOSTER -> AugmentBlueprint(elem)
            AugmentBlueprint.FTL_JAMMER -> AugmentBlueprint(elem)
            AugmentBlueprint.DISTRACTION_BUOYS -> AugmentBlueprint(elem)
            AugmentBlueprint.EXPLOSIVE_REPLICATOR -> AugmentBlueprint(elem)
            AugmentBlueprint.TITANIUM_SYSTEM_CASING -> AugmentBlueprint(elem)
            AugmentBlueprint.DRONE_RECOVERY_ARM -> AugmentBlueprint(elem)
            AugmentBlueprint.DRONE_REACTOR_BOOSTER -> AugmentBlueprint(elem)
            AugmentBlueprint.HACKING_STUN -> AugmentBlueprint(elem)
            AugmentBlueprint.LIFEFORM_SCANNER -> AugmentBlueprint(elem)
            AugmentBlueprint.MANTIS_PHEROMONES -> AugmentBlueprint(elem)
            AugmentBlueprint.CRYSTAL_VENGEANCE -> AugmentBlueprint(elem)
            AugmentBlueprint.ROCK_PLATING -> AugmentBlueprint(elem)
            AugmentBlueprint.SLUG_REPAIR_GEL -> AugmentBlueprint(elem)
            AugmentBlueprint.REVERSE_ION_FIELD -> AugmentBlueprint(elem)
            // A quest's item, which does nothing by itself
            AugmentBlueprint.DAMAGED_STASIS_POD -> AugmentBlueprint(elem)

            else -> {
                println("WARNING: Adding unknown augment '$name'")
                AugmentBlueprint(elem)
            }
        }
    }

    private fun buildSystemBlueprint(elem: Element): IBlueprint {
        return SystemBlueprint(elem)
    }

    private fun buildCrewBlueprint(elem: Element): IBlueprint {
        return CrewBlueprint(elem)
    }

    companion object {
        const val AE_PREFIX = "OVERRIDE_"

        /**
         * Create a version of [BlueprintManager] for use in automated tests,
         * loading a custom set of blueprint XML files.
         */
        fun createForTests(df: Datafile, files: List<Document>): BlueprintManager {
            return BlueprintManager(true) {
                for (doc in files) {
                    it.loadDocument(df, doc.rootElement)
                }
            }
        }
    }
}

interface IBlueprint {
    fun resolve(random: Random = Random): Blueprint
    fun list(): List<Blueprint>
}

class BlueprintList(private val blueprints: ArrayList<String>, private val manager: BlueprintManager) : IBlueprint {
    override fun resolve(random: Random): Blueprint = manager[blueprints.random(random)].resolve(random)
    override fun list(): List<Blueprint> = blueprints.flatMap { manager[it].list() }

    fun cleanup() {
        val toRemove = blueprints.filter { !manager.blueprints.containsKey(it) }

        for (item in toRemove) {
            // (vanilla's own lists name a few that were never finished)
            if (item !in VANILLA_MISSING)
                println("Warning: Removing non-existant blueprint $item from blueprint list!")
            blueprints.remove(item)
        }
    }

    companion object {
        /**
         * Blueprints that vanilla's blueprint lists name, but that vanilla
         * never defines, so they're left out quietly.
         */
        private val VANILLA_MISSING = setOf("BOMB_BREACH", "HULL_ARMOR", "ADV_HULL_ARMOR")
    }
}

open class Blueprint(elem: Element) : IBlueprint {
    val name = elem.requireAttributeValue("name")
    val rarity: Int? = elem.getChild("rarity")?.textTrim?.toInt()

    open val title = elem.getGameTextChild("title")
    open val short = elem.getGameTextChild("short")
    val desc = elem.getGameTextChild("desc")

    // Note the tip is always localised
    val tip = elem.getChildTextTrim("tip")?.let { GameText.localised(it) }

    /**
     * Get this item's purchase price (the price you can buy it at, not the
     * price you sell it at) in scrap. Returns null if the item doesn't
     * have a price.
     */
    open val cost: Int? = null

    fun translateTitle(game: InGameState): String = title?.let { game.translator[it] } ?: "MISSING TITLE: $name"
    fun translateShort(game: InGameState): String = short?.let { game.translator[it] } ?: "MISSING SHORT: $name"

    override fun resolve(random: Random): Blueprint = this
    override fun list(): List<Blueprint> = listOf(this)

    /**
     * Finish setting up this blueprint, loading any other
     * required resources (eg, sounds).
     */
    open fun finishSetup(content: InGameState.GameContent) {
    }
}

/**
 * This is for fuel/drones/missiles, and serves to make their descriptions available.
 */
class ItemBlueprint(elem: Element) : Blueprint(elem) {
    override val cost: Int = elem.getChildTextTrim("cost")!!.toInt()
}

/**
 * This is a stub that's created instead of a real ship blueprint,
 * which is significantly slower to load.
 *
 * Those can and should only be loaded when they're required.
 */
class LazyShipBlueprint(
    private val elem: Element, private val df: Datafile
) : Blueprint(elem) {

    val real: ShipBlueprint by lazy { ShipBlueprint(elem, df) }

    // Load a few bits and pieces that are used in places where
    // we don't need to load the full ship.
    val layout: String = elem.getAttributeValue("layout")
    val img: String = elem.getAttributeValue("img")
    val shipClass: GameText? = elem.getGameTextChild("class")
    val shipTitle: GameText? = elem.getGameTextChild("name")

    /**
     * True if this ship is picked by default in the hangar, set with an
     * xftlDefaultShip element (an XFTL extension). This is for mods that
     * add a ship of their own.
     */
    val isDefaultShip: Boolean = elem.getChild("xftlDefaultShip") != null

    // See ShipBlueprint.shipSize
    val shipSize: Int = elem.getChildTextTrim("shipSize")?.toIntOrNull()?.coerceAtLeast(1) ?: 1

    // See ShipBlueprint.shape and variantOf
    val shape: ShipShape = ShipShape.parse(elem.getChildTextTrim("xftlShape"), shipSize, name)
    val variantOf: List<String> = parseVariantOf(elem)
}

/**
 * The ships a bigger version of a ship is a version of, from its
 * xftlVariantOf element: a comma-separated list of ship names, for ships
 * that share a layout (such as a ship and its pirate or DLC versions).
 */
fun parseVariantOf(elem: Element): List<String> =
    elem.getChildTextTrim("xftlVariantOf")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

/**
 * The shape of a hostile ship, which sets how much room its window takes up
 * (see [xyz.znix.xftl.game.FightPlan]). An XFTL extension: the vanilla ships
 * are all [NORMAL], and size 2 ships are [LONG] or [WIDE], set with an
 * xftlShape element.
 *
 * Hostile ships are drawn nose up, so a long ship needs a taller window, and
 * a wide one a window twice as wide, which takes up two places in the row of
 * enemy windows. As a wide one has more room, it has more systems and crew
 * than a long one: [strength] is how many vanilla ships its power, hull,
 * crew and rewards are worth.
 *
 * Size 3 ships are the bosses (see doc/game/bosses.md): [COLOSSAL], both long
 * and wide, so their window is as tall as a long ship's and takes up two
 * places, leaving one for an escort. They're never part of a random fight.
 *
 * Size 4 is the Mainframe (see doc/game/mainframe.md): [TITAN], as tall as a
 * long ship's window and as wide as the whole row, three places.
 */
enum class ShipShape(val size: Int, val slots: Int, val strength: Int) {
    NORMAL(1, 1, 1),
    LONG(2, 1, 2),
    WIDE(2, 2, 3),
    COLOSSAL(3, 2, 6),
    TITAN(4, 3, 10);

    /** True if its window is taller than usual (see HostileShipUI). */
    val isTall: Boolean get() = this == LONG || this == COLOSSAL || this == TITAN

    companion object {
        fun parse(value: String?, shipSize: Int, shipName: String): ShipShape = when (value?.lowercase()) {
            null -> when (shipSize) {
                2 -> LONG
                3 -> COLOSSAL
                4 -> TITAN
                else -> NORMAL
            }
            "long" -> LONG
            "wide" -> WIDE
            "colossal" -> COLOSSAL
            "titan" -> TITAN
            else -> error("Invalid xftlShape '$value' on ship $shipName")
        }
    }
}
