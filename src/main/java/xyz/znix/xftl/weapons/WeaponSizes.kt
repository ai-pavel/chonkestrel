package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.ShipShape
import xyz.znix.xftl.math.ConstPoint
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Combined weapons: two or three of the same weapon in one slot (a weapon's
 * size), each firing at a different enemy ship. See doc/game/weapon-sizes.md.
 *
 * They're made from the vanilla weapons when the blueprints are loaded, as
 * blueprints of their own (named [nameFor]), so they can be bought, saved
 * and given to enemies like any other. A combined weapon's blueprint has an
 * xftlCombined element saying what it's made of: for now, always that many
 * of the same weapon, but it's a list of weapons so different ones can be
 * put together later.
 */
object WeaponSizes {
    /**
     * The sizes of weapon there are, beyond a normal one.
     */
    val SIZES = listOf(2, 3)

    /**
     * How many times its single weapon's price a combined one costs, by size.
     */
    private val COST_FACTOR = mapOf(2 to 2.2f, 3 to 3.3f)

    /**
     * How many times as long it takes to charge, by size.
     */
    private val CHARGE_FACTOR = mapOf(2 to 1.25f, 3 to 1.5f)

    /**
     * The weapon types that can be combined. Flak is left out, as it's from
     * Advanced Edition (as are all the weapons from it, see [canCombine]).
     */
    private val COMBINABLE_TYPES = setOf("LASER", "MISSILES", "BEAM", "BOMB")

    fun nameFor(base: String, size: Int): String = "XFTL_X${size}_$base"

    /**
     * The combined version of [weapon] of [size], or null if there isn't one.
     */
    fun combined(blueprints: BlueprintManager, weapon: AbstractWeaponBlueprint, size: Int): AbstractWeaponBlueprint? =
        blueprints.getOrNull(nameFor(weapon.name, size)) as? AbstractWeaponBlueprint

    /**
     * How far through the game [sector] is (vanilla's sector number, 0 to 7,
     * which the longer games' sectors are spread over), from 0 to 1.
     */
    fun progress(sector: Int): Float = (sector / 7f).coerceIn(0f, 1f)

    /**
     * The chance of an enemy ship's weapon being a double one (size 2) in
     * [sector] (see [progress]): for normal ships, from 5% at the start of
     * the game to 30% at the end; for long and wide ships, 25% to 60%. They
     * can still have several single weapons.
     */
    fun doubleChance(shape: ShipShape, sector: Int): Float = when (shape) {
        ShipShape.NORMAL -> 0.05f + 0.25f * progress(sector)
        else -> 0.25f + 0.35f * progress(sector)
    }

    /**
     * From how far through the game (see [progress]) a long or wide enemy
     * ship always has at least one double weapon.
     */
    const val BIG_SHIP_DOUBLE_FROM = 0.25f

    /**
     * In the most dangerous sectors (the Gullet and the Belly), the
     * chance of each of an enemy's weapons being a triple one, and how many
     * a ship can have at most.
     */
    const val EPIC_TRIPLE_CHANCE = 0.35f
    const val EPIC_MAX_TRIPLES = 2

    /**
     * The weapon an enemy ship gets in place of [weapon], picked for it with
     * [remainingPower] the most its weapons system can give it (including
     * what it can be upgraded by): maybe a combined one, if there's the power
     * for it (see [doubleChance], and in the most dangerous sectors,
     * [EPIC_TRIPLE_CHANCE] when [triplesAllowed]).
     */
    fun enemyWeapon(
        blueprints: BlueprintManager, weapon: AbstractWeaponBlueprint, remainingPower: Int,
        shape: ShipShape, sector: Int, triplesAllowed: Boolean, random: Random
    ): AbstractWeaponBlueprint {
        if (triplesAllowed && random.nextFloat() < EPIC_TRIPLE_CHANCE) {
            combined(blueprints, weapon, 3)?.takeIf { it.power <= remainingPower }?.let { return it }
        }
        if (random.nextFloat() < doubleChance(shape, sector)) {
            combined(blueprints, weapon, 2)?.takeIf { it.power <= remainingPower }?.let { return it }
        }
        return weapon
    }

    /**
     * How long a beam from a combined beam weapon of [size] is: for each
     * extra copy, as long again as the beam or as far as the next enemy
     * window (side by side), whichever's longer, so it reaches on through
     * that many other ships' windows, or a long way through a single ship
     * (see BeamBlueprint.BeamInstance). It's the same however many ships
     * there are, and whether they're cloaked.
     */
    fun beamLength(baseLength: Int, size: Int): Int =
        baseLength + (size - 1) * max(baseLength, BEAM_REACH_PER_COPY)

    /**
     * How many times bigger a combined weapon of [size]'s shots are drawn:
     * half as big again for each extra copy.
     */
    fun projectileScale(size: Int): Float = 1f + (size - 1) * 0.5f

    /**
     * How much thicker a combined beam of [size] is drawn than a single one
     * (in the beam's power, which sets its width).
     */
    fun beamThickness(size: Int): Int = size - 1

    /**
     * How many times as long a combined beam of [size] takes to sweep as a
     * single one, however long it is: a long beam sweeps faster.
     */
    fun beamDurationFactor(size: Int): Float = 1f + (size - 1) * 0.25f

    // How much longer a combined beam is for each extra copy: about the
    // distance from one enemy window to the next
    private const val BEAM_REACH_PER_COPY = 400

    /**
     * Where each of a combined weapon's [count] copies is drawn, relative to
     * where a single one would be, in its launcher image's space (where the
     * barrel points up), back to front.
     *
     * Two are side by side, overlapping closely (a single one's
     * [frameWidth] apart would take up too much of the hull), with the left
     * one set back a little. Three are a triangle: two behind, either side, and one in
     * front in the middle, taking up no more room across than two. A single
     * weapon has just the one, at (0, 0).
     */
    fun copyOffsets(count: Int, frameWidth: Int): List<ConstPoint> {
        if (count <= 1)
            return SINGLE_OFFSET
        if (count == 2) {
            val half = (frameWidth * TWIN_SPACING / 2).roundToInt().coerceAtLeast(2)
            return listOf(ConstPoint(-half, TWIN_SETBACK), ConstPoint(half, 0))
        }
        val side = (frameWidth * TRIPLE_SPACING).roundToInt().coerceAtLeast(2)
        return listOf(ConstPoint(-side, TRIPLE_SETBACK), ConstPoint(side, TRIPLE_SETBACK), ConstPoint.ZERO)
    }

    private val SINGLE_OFFSET = listOf(ConstPoint.ZERO)

    // How far apart two copies are, as a fraction of a single one's width,
    // and how far back the left one's set (in pixels)
    private const val TWIN_SPACING = 0.4f
    private const val TWIN_SETBACK = 3

    // How far to either side the back two of three copies are, as a
    // fraction of a single one's width (so they're no wider than two), and
    // how far back they're set (in pixels)
    private const val TRIPLE_SPACING = 0.3f
    private const val TRIPLE_SETBACK = 6

    /**
     * The power a combined weapon of [size] needs: half as much again as its
     * single weapon for each extra one, rounded up, so it's cheaper to power
     * than that many separate weapons.
     */
    fun powerFor(power: Int, size: Int): Int = power + ceil(power * (size - 1) / 2f).toInt()

    /**
     * Whether the weapon with this blueprint element can be combined: it
     * has to be a vanilla weapon (not from Advanced Edition, which are left
     * for later), of a type that can be, with a rarity (even if it's 0, as
     * many of the enemies' weapons are, such as the Burst Laser II), and
     * not an artillery, drone or boss weapon.
     */
    fun canCombine(elem: Element, isAdvancedEdition: Boolean): Boolean {
        if (isAdvancedEdition)
            return false
        if (elem.getChildTextTrim("type") !in COMBINABLE_TYPES)
            return false
        val name = elem.getAttributeValue("name")
        if (EXCLUDED_PREFIXES.any { name.startsWith(it) })
            return false
        return elem.getChild("rarity") != null && elem.getChild("xftlCombined") == null
    }

    // The weapons that are part of something else, rather than weapons of
    // their own: the bosses' artillery and the drones' guns
    private val EXCLUDED_PREFIXES = listOf("ARTILLERY_", "DRONE_", "ANTI_DRONE", "BOSS_")

    /**
     * How many more layers of shields a combined beam gets through for each
     * extra copy: a beam gets through as many layers as its damage (and
     * shield piercing) is more than them, so a Halberd Beam (2 damage)
     * gets through one, a twin one two and a triple one three.
     */
    const val BEAM_PIERCING_PER_COPY = 1

    /**
     * The chance of a weapon in a store or a reward being a double one, in
     * place of the single one. Triple ones are never found, only given by
     * events, and to enemies in the most dangerous sectors.
     */
    const val LOOT_DOUBLE_CHANCE = 0.1f

    /**
     * The blueprint element for [size] of the weapon with blueprint element
     * [base]: a copy of it, with its own name, and the price, charge time,
     * missiles and power scaled up. It's not in the loot pool itself (see
     * [LOOT_DOUBLE_CHANCE]).
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
        scale("cooldown") { (it.toFloat() * CHARGE_FACTOR.getValue(size)).toString() }
        scale("missiles") { (it.toInt() * size).toString() }
        scale("power") { powerFor(it.toInt(), size).toString() }
        // They're not in the loot pool themselves: see LOOT_DOUBLE_CHANCE
        scale("rarity") { "0" }

        // A combined beam gets through more of the shields: each extra copy
        // pierces another layer (see BEAM_PIERCING_PER_COPY)
        if (elem.getChildTextTrim("type") == "BEAM") {
            val sp = elem.getChild("sp") ?: Element("sp").also { it.text = "0"; elem.addContent(it) }
            sp.text = ((sp.textTrim.toIntOrNull() ?: 0) + (size - 1) * BEAM_PIERCING_PER_COPY).toString()
        }

        val combined = Element("xftlCombined")
        combined.setAttribute("count", size.toString())
        repeat(size) { combined.addContent(Element("weapon").setAttribute("name", baseName)) }
        elem.addContent(combined)
        return elem
    }
}
