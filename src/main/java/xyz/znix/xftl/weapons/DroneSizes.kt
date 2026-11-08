package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.ShipShape
import kotlin.random.Random
import kotlin.math.roundToInt

/**
 * Combined drones: two or three of the same drone launched from one slot
 * (a drone's size), which share the work out between them. See
 * doc/game/drone-sizes.md.
 *
 * As the combined weapons are (see [WeaponSizes]), they're made from the
 * vanilla drones when the blueprints are loaded, as blueprints of their own
 * (named [nameFor]), with an xftlCombined element listing the drones
 * they're made of. Each copy is an instance of the combined drone's own
 * blueprint (see Drones.DroneInfo).
 */
object DroneSizes {
    /** The sizes of drone there are, beyond a normal one. */
    val SIZES = WeaponSizes.SIZES

    /** How many times its single drone's price a combined one costs, by size (as the weapons'). */
    private val COST_FACTOR = mapOf(2 to 2.2f, 3 to 3.3f)

    /**
     * The drone types that can be combined: all of vanilla's that are
     * launched as drones. (The hacking system's drone isn't a drone part's.)
     */
    private val COMBINABLE_TYPES = setOf("COMBAT", "DEFENSE", "BOARDER", "BATTLE", "REPAIR", "SHIP_REPAIR", "SHIELD")

    fun nameFor(base: String, size: Int): String = WeaponSizes.nameFor(base, size)

    /** The combined version of [drone] of [size], or null if there isn't one. */
    fun combined(blueprints: BlueprintManager, drone: DroneBlueprint, size: Int): DroneBlueprint? =
        blueprints.getOrNull(nameFor(drone.name, size)) as? DroneBlueprint

    /**
     * The drone an enemy ship gets in place of [drone], with [remainingPower]
     * the most its drones system can give it (including what it can be
     * upgraded by): maybe a combined one, as often as the weapons are (see
     * [WeaponSizes.enemyWeapon]), if there's the power for it.
     */
    fun enemyDrone(
        blueprints: BlueprintManager, drone: DroneBlueprint, remainingPower: Int,
        shape: ShipShape, sector: Int, triplesAllowed: Boolean, random: Random
    ): DroneBlueprint {
        if (triplesAllowed && random.nextFloat() < WeaponSizes.EPIC_TRIPLE_CHANCE) {
            combined(blueprints, drone, 3)?.takeIf { it.power <= remainingPower }?.let { return it }
        }
        if (random.nextFloat() < WeaponSizes.doubleChance(shape, sector)) {
            combined(blueprints, drone, 2)?.takeIf { it.power <= remainingPower }?.let { return it }
        }
        return drone
    }

    /**
     * How many of a size 3 boss of [tier]'s drones are triples (see
     * ColossalBoss): the rest are twins, as all its weapons are twins and
     * triples.
     */
    fun bossTriples(tier: Int): Int = when (tier) {
        1 -> 0
        2 -> 1
        3 -> 2
        else -> 4
    }

    /** The power a combined drone of [size] needs: as a combined weapon's (see [WeaponSizes.powerFor]). */
    fun powerFor(power: Int, size: Int): Int = WeaponSizes.powerFor(power, size)

    /**
     * Whether the drone with this blueprint element can be combined: one of
     * the [COMBINABLE_TYPES], that isn't combined already.
     */
    fun canCombine(elem: Element): Boolean {
        if (elem.getChildTextTrim("type") !in COMBINABLE_TYPES)
            return false
        if (elem.getAttributeValue("name").startsWith("DRONE_HACK"))
            return false
        return elem.getChild("xftlCombined") == null
    }

    /**
     * The blueprint element for [size] of the drone with blueprint element
     * [base]: a copy of it, with its own name, and the price and power scaled
     * up. Each launch takes a drone part for each copy (see Drones). It's not
     * in the loot pool itself (see [WeaponSizes.LOOT_DOUBLE_CHANCE]).
     */
    fun combinedElement(base: Element, size: Int): Element {
        val baseName = base.getAttributeValue("name")
        val elem = base.clone()
        elem.setAttribute("name", nameFor(baseName, size))

        fun scale(child: String, transform: (String) -> String) {
            val e = elem.getChild(child) ?: return
            e.text = transform(e.textTrim)
        }
        scale("cost") { (it.toInt() * COST_FACTOR.getValue(size)).roundToInt().toString() }
        scale("power") { powerFor(it.toInt(), size).toString() }
        scale("rarity") { "0" }

        val combined = Element("xftlCombined")
        combined.setAttribute("count", size.toString())
        repeat(size) { combined.addContent(Element("drone").setAttribute("name", baseName)) }
        elem.addContent(combined)
        return elem
    }
}
