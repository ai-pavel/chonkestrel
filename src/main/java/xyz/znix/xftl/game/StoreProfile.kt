package xyz.znix.xftl.game

import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.sector.SectorType
import xyz.znix.xftl.weapons.WeaponSizes
import kotlin.math.ceil

/**
 * What a sector's stores are like (see doc/game/stores.md): the busier and safer
 * the sector, the more a store has, but the more ordinary it is; the more
 * dangerous, the fewer stores there are and the less they have, but the
 * rarer it is.
 */
enum class StoreProfile(
    /** How many sections a store has (more than two take two pages). */
    val sections: IntRange,
    /** How many things are in each section (up to a full shelf of six). */
    val itemsPerSection: IntRange,
    /** How much fuel, missiles and drone parts it has, from a civilian store's. */
    val resources: Float,
    /** Whether the rarer weapons, drones, augments and crew are likelier, rather than the common ones. */
    val rareBias: Boolean,
    /** The chance of a weapon being a double one at the start and end of the game (see WeaponSizes). */
    private val doubleStart: Float,
    private val doubleEnd: Float,
    /** The chance of a weapon being a triple one. */
    val tripleChance: Float,
    /**
     * How many of their skills the crew for hire have two levels further
     * on, and how many fully learnt (see StoreData.trainCrew).
     */
    val crewPartialSkills: Int,
    val crewMaxSkills: Int,
    /** What share of the sector's stores it keeps (see [keptStores]). */
    private val storeShare: Float,
    /** The most stores the sector has, if there's a limit. */
    private val maxStores: Int?,
) {
    /**
     * Civilian sectors: packed full, two pages, of ordinary things, with
     * only the odd double or triple weapon.
     */
    CIVILIAN(4..4, 6..6, 1f, false, 0.03f, 0.15f, 0.01f, 0, 0, 1f, null),

    /** Hostile sectors: a page, with a few double weapons, and some trained crew. */
    HOSTILE(2..2, 6..6, 0.75f, false, 0.1f, 0.3f, 0.02f, 1, 0, 0.75f, null),

    /** Nebula sectors: half-empty shelves, but rarer things, more double weapons and better crew. */
    NEBULA(2..2, 3..3, 0.5f, true, 0.25f, 0.5f, 0.05f, 2, 1, 0.5f, null),

    /**
     * Hazardous sectors (every beacon a hazard): at most one store, with one
     * or two things, but the rarest, often double or triple, and the best crew.
     */
    HAZARD(1..1, 1..2, 0.25f, true, 0.5f, 0.5f, 0.3f, 2, 3, 1f, 1);

    /** The chance of a weapon being a double one, [progress] through the game (0 to 1). */
    fun doubleChance(progress: Float): Float = doubleStart + (doubleEnd - doubleStart) * progress

    /** How many of a sector's [stores] stores this keeps. */
    fun keptStores(stores: Int): Int {
        val kept = ceil(stores * storeShare).toInt()
        return if (maxStores != null) kept.coerceAtMost(maxStores) else kept
    }

    companion object {
        fun of(sector: GameMap.SectorInfo): StoreProfile = when {
            sector.type.hazards != SectorType.HazardLevel.NORMAL -> HAZARD
            sector.sectorClass == GameMap.SectorClass.NEBULA -> NEBULA
            sector.sectorClass == GameMap.SectorClass.HOSTILE -> HOSTILE
            else -> CIVILIAN
        }

        /** How far through the game (0 to 1) the sector is, for the double weapons' chance. */
        fun progress(sector: GameMap.SectorInfo): Float = WeaponSizes.progress(sector.difficultySectorNumber)
    }
}
