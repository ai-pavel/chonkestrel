package xyz.znix.xftl.game

import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.sector.SectorType
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.DroneSizes
import xyz.znix.xftl.weapons.WeaponSizes
import kotlin.math.min
import kotlin.random.Random

class LootPool(private val bpManager: BlueprintManager, sector: SectorType?) {
    val pool: List<Blueprint>

    // Each blueprint's rarity (1 the commonest, 5 the rarest), for picking
    // the rarer ones (see getManyRandom)
    private val rarities = HashMap<Blueprint, Int>()

    init {
        pool = ArrayList()

        val overrides = sector?.rarityOverrides ?: emptyMap()

        for (bp in bpManager.blueprints.values) {
            if (bp !is Blueprint) continue

            // First check if a BP's rarity has been overridden by the sector. If not, use
            // it's standard rarity. If that's not defined then it's inaccessible.
            val rarity = overrides[bp.name] ?: bp.rarity ?: continue

            // See https://subsetgames.com/forum/viewtopic.php?t=33471
            check(rarity in 0..5) { "Invalid rarity $rarity for blueprint ${bp.name}" }
            if (rarity == 0) continue
            rarities[bp] = rarity
            val entries = 6 - rarity

            for (i in 1..entries) {
                pool += bp
            }
        }
    }

    fun getWeapon(rand: Random): AbstractWeaponBlueprint {
        val weapon = getRandom(rand) { it is AbstractWeaponBlueprint } ?: error("No available weapons!")
        return maybeDouble(weapon as AbstractWeaponBlueprint, rand)
    }

    /**
     * Now and then, a double version of [weapon] in its place (see
     * [WeaponSizes]): they aren't in the pool themselves, so they don't
     * crowd out the single ones.
     */
    private fun maybeDouble(
        weapon: AbstractWeaponBlueprint, rand: Random,
        doubleChance: Float = WeaponSizes.LOOT_DOUBLE_CHANCE, tripleChance: Float = 0f
    ): AbstractWeaponBlueprint {
        if (tripleChance > 0f && rand.nextFloat() < tripleChance)
            WeaponSizes.combined(bpManager, weapon, 3)?.let { return it }
        if (rand.nextFloat() >= doubleChance)
            return weapon
        return WeaponSizes.combined(bpManager, weapon, 2) ?: weapon
    }

    /**
     * A weapon that's certainly a double one, or (a [COMBINED_TRIPLE_CHANCE]
     * of the time) a triple one, such as an event's reward with
     * `xftlCombined="true"`. Not every weapon has a combined version, so it
     * picks again until it finds one that does.
     */
    fun getCombinedWeapon(rand: Random): AbstractWeaponBlueprint {
        val size = if (rand.nextFloat() < COMBINED_TRIPLE_CHANCE) 3 else 2
        repeat(COMBINED_WEAPON_TRIES) {
            val weapon = getRandom(rand) { it is AbstractWeaponBlueprint } as AbstractWeaponBlueprint?
                ?: error("No available weapons!")
            (WeaponSizes.combined(bpManager, weapon, size) ?: WeaponSizes.combined(bpManager, weapon, 2))
                ?.let { return it }
        }
        return getWeapon(rand)
    }

    fun getDrone(rand: Random): DroneBlueprint {
        val drone = getRandom(rand) { it is DroneBlueprint } ?: error("No available drones!")
        return maybeDoubleDrone(drone as DroneBlueprint, rand)
    }

    /**
     * Now and then, a double version of [drone] in its place, as with the
     * weapons (see [maybeDouble] and DroneSizes).
     */
    private fun maybeDoubleDrone(
        drone: DroneBlueprint, rand: Random,
        doubleChance: Float = WeaponSizes.LOOT_DOUBLE_CHANCE, tripleChance: Float = 0f
    ): DroneBlueprint {
        if (tripleChance > 0f && rand.nextFloat() < tripleChance)
            DroneSizes.combined(bpManager, drone, 3)?.let { return it }
        if (rand.nextFloat() >= doubleChance)
            return drone
        return DroneSizes.combined(bpManager, drone, 2) ?: drone
    }

    /**
     * A drone that's certainly a double one, or (a [COMBINED_TRIPLE_CHANCE]
     * of the time) a triple one, as [getCombinedWeapon] is for the weapons.
     */
    fun getCombinedDrone(rand: Random): DroneBlueprint {
        val size = if (rand.nextFloat() < COMBINED_TRIPLE_CHANCE) 3 else 2
        repeat(COMBINED_WEAPON_TRIES) {
            val drone = getRandom(rand) { it is DroneBlueprint } as DroneBlueprint? ?: error("No available drones!")
            (DroneSizes.combined(bpManager, drone, size) ?: DroneSizes.combined(bpManager, drone, 2))
                ?.let { return it }
        }
        return getDrone(rand)
    }

    fun getAugment(rand: Random) = getRandom(rand) { it is AugmentBlueprint } ?: error("No available augments!")

    fun getCrewOrRandom(rand: Random, race: String): CrewBlueprint {
        if (race != "random") {
            return bpManager[race].resolve(rand) as CrewBlueprint
        }

        return getRandom(rand) { it is CrewBlueprint } as CrewBlueprint? ?: error("No available crew!")
    }

    fun getRandom(rand: Random, filter: (Blueprint) -> Boolean): Blueprint? {
        val candidates = pool.asSequence().filter(filter).toList()
        if (candidates.isEmpty()) return null
        return candidates.random(rand)
    }

    /**
     * Up to [limit] different random blueprints of [type], for a store's
     * section, say. The commoner ones are likelier, or with [rareBias], the
     * rarer ones. A weapon may be a double one ([doubleChance]) or a triple
     * one ([tripleChance]) in place of the single one (see WeaponSizes).
     */
    fun <T> getManyRandom(
        type: Class<T>, rand: Random, limit: Int,
        doubleChance: Float = WeaponSizes.LOOT_DOUBLE_CHANCE, tripleChance: Float = 0f, rareBias: Boolean = false
    ): List<T> {
        // The pool has several copies of the more common blueprints, so remove
        // the duplicates after shuffling it, so the same thing doesn't
        // appear twice (in a store, for example). For the rarer ones, each is
        // picked as likely as its rarity, instead.
        val ordered = when (rareBias) {
            false -> pool.filterIsInstance(type).shuffled(rand).distinct()
            true -> pool.filterIsInstance(type).distinct().sortedByDescending { bp ->
                val weight = rarities[bp as Blueprint] ?: 1
                Math.pow(rand.nextDouble(), 1.0 / weight)
            }
        }
        val matching = ordered.take(limit).map {
            when (it) {
                is AbstractWeaponBlueprint -> type.cast(maybeDouble(it, rand, doubleChance, tripleChance))
                is DroneBlueprint -> type.cast(maybeDoubleDrone(it, rand, doubleChance, tripleChance))
                else -> it
            }
        }
        return matching.subList(0, min(matching.size, limit))
    }

    companion object {
        /** How often a certainly combined weapon (see getCombinedWeapon) is a triple one. */
        const val COMBINED_TRIPLE_CHANCE = 1f / 3

        // How many weapons getCombinedWeapon tries for one with a combined version
        private const val COMBINED_WEAPON_TRIES = 50
    }
}
