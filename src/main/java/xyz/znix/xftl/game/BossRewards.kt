package xyz.znix.xftl.game

import org.jdom2.Element
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.DroneSizes
import xyz.znix.xftl.weapons.WeaponSizes
import kotlin.random.Random

/**
 * The rewards for beating one of the size 3 bosses (see ColossalBoss and
 * doc/game/bosses.md), given by an event's `<xftlBossReward>` element (an
 * XFTL extension):
 *
 * ```xml
 * <xftlBossReward tier="2" weapon="XFTL_X2_LASER_HEAVY_2" augment="XFTL_COMMAND_CODES"/>
 * ```
 *
 * The tier sets how much of everything there is ([TIERS]): resources, twin
 * and triple weapons (the first of them the boss's own [weapon]), rare
 * drones and augments, and the boss's own [augment].
 */
class BossRewards(elem: Element) {
    val tier: Int = elem.getAttributeValue("tier").toInt()
    val weapon: String? = elem.getAttributeValue("weapon")
    val augment: String? = elem.getAttributeValue("augment")

    /**
     * What one tier of boss gives: the resources for a size 2 ship (they
     * scale with the player's ship's size), and how many of each kind of item.
     */
    data class Tier(
        val scrap: Int, val fuel: Int, val missiles: Int, val droneParts: Int,
        val twins: Int, val triples: Int, val drones: Int, val augments: Int,
    )

    /**
     * Add the rewards to [resources], for a player ship of [size].
     */
    fun addTo(resources: ResourceSet, game: InGameState, rand: Random, size: Int) {
        val amounts = TIERS.getValue(tier.coerceIn(TIERS.keys.min(), TIERS.keys.max()))
        resources.scrap += amounts.scrap * size / NOMINAL_SIZE
        resources.fuel += amounts.fuel * size / NOMINAL_SIZE
        resources.missiles += amounts.missiles * size / NOMINAL_SIZE
        resources.droneParts += amounts.droneParts * size / NOMINAL_SIZE

        val manager = game.blueprintManager
        val items = resources.items

        // The weapons: the boss's own first, then random twins and triples
        // of the stronger vanilla weapons
        val sizes = List(amounts.triples) { 3 } + List(amounts.twins) { 2 }
        val signature = weapon?.let { manager.getOrNull(it) as? AbstractWeaponBlueprint }
        if (signature != null)
            items += signature
        val pool = strongWeapons(game)
        for (count in sizes.drop(if (signature != null) 1 else 0)) {
            val base = pool.randomOrNull(rand) ?: break
            items += WeaponSizes.combined(manager, base, count) ?: base
        }

        // The rarer drones, each a twin or a triple one, as the weapons are
        // (see DroneSizes): the stronger the boss, the more of them triples
        val drones = manager.blueprints.values.filterIsInstance<DroneBlueprint>()
            .filter { (it.rarity ?: 0) >= RARE }
        for (size in droneSizes(tier).take(amounts.drones)) {
            val base = drones.randomOrNull(rand) ?: break
            items += DroneSizes.combined(manager, base, size) ?: base
        }

        // Rare augments the player doesn't have yet (unless they stack),
        // and never a race's
        val owned = game.player.augments.map { it.name }.toSet()
        val augments = manager.blueprints.values.filterIsInstance<AugmentBlueprint>()
            .filter { (it.rarity ?: 0) >= RARE && it.name !in RACE_AUGMENTS }
            .filter { it.stackable || it.name !in owned }
            .shuffled(rand)
        items += augments.take(amounts.augments)

        // The boss's own augment
        augment?.let { manager.getOrNull(it) as? Blueprint }?.let { items += it }

        // Now and then, a Zoltan Shield too (which stacks, see
        // AugZoltanShield), on top of the rest
        val shield = manager.getOrNull(AugZoltanShield.NAME) as? AugmentBlueprint
        val shields = game.player.augments.count { it == shield }
        if (shield != null && shields < AugZoltanShield.MAX_STACKED && rand.nextFloat() < ZOLTAN_SHIELD_CHANCE)
            items += shield
    }

    /**
     * The stronger half of the vanilla weapons that have twin and triple
     * versions (see WeaponSizes), by price, from those sold in stores.
     */
    private fun strongWeapons(game: InGameState): List<AbstractWeaponBlueprint> {
        val manager = game.blueprintManager
        val sold = manager.blueprints.values.filterIsInstance<AbstractWeaponBlueprint>()
            .filter { !it.isCombined && (it.rarity ?: 0) > 0 }
            .filter { WeaponSizes.combined(manager, it, 3) != null }
            .sortedByDescending { it.cost }
        return sold.take((sold.size + 1) / 2)
    }

    companion object {
        /** The ship size the [TIERS]' resources are for. */
        const val NOMINAL_SIZE = 2

        /** The least rarity of a "rare" drone or augment. */
        const val RARE = 3

        /**
         * The sizes of the drones a boss of [tier] gives (see DroneSizes): a
         * twin from the first two, a triple from the third, and a triple
         * and a twin from the fourth.
         */
        fun droneSizes(tier: Int): List<Int> = when {
            tier <= 2 -> listOf(2)
            tier == 3 -> listOf(3)
            else -> listOf(3, 2)
        }

        /** The chance of a boss giving a Zoltan Shield as well. */
        const val ZOLTAN_SHIELD_CHANCE = 1f / 3

        val TIERS = mapOf(
            1 to Tier(150, 6, 6, 4, twins = 1, triples = 0, drones = 1, augments = 1),
            2 to Tier(200, 8, 8, 6, twins = 2, triples = 0, drones = 1, augments = 1),
            3 to Tier(300, 10, 10, 8, twins = 1, triples = 1, drones = 1, augments = 1),
            4 to Tier(400, 12, 12, 10, twins = 0, triples = 2, drones = 2, augments = 2),
        )

        /**
         * The races' augments, which the rewards leave out: they're for
         * their races' sectors.
         */
        val RACE_AUGMENTS = setOf(
            "CREW_STIMS", "CRYSTAL_SHARDS", "DRONE_SPEED", "ENERGY_SHIELD", "NANO_MEDBAY", "ROCK_ARMOR", "SLUG_GEL",
            "STASIS_POD",
        )
    }
}
