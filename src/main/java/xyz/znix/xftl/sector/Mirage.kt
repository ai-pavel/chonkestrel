package xyz.znix.xftl.sector

import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.CrewSlug
import xyz.znix.xftl.game.ColossalBoss
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.weapons.*
import kotlin.random.Random

/**
 * Mirage nebulas (see NebulaEnvironment) fool the beacon map: the beacons in
 * them, and every beacon while the player's in one, show false ships and
 * hazards, and don't show their stores or distress signals. In a fight in
 * one, the enemy's weapons look like other weapons (see [weaponDisguise]).
 *
 * The false information is picked from the beacon's seed, so it doesn't
 * change every time the map's opened.
 *
 * Revealing the map (such as hiring a mercenary to scout the sector) has a
 * [SCOUTING_CHANCE] of getting through the mirages, so the player's own
 * mirage doesn't make the other beacons lie any more (see
 * Sector.mirageScouted); the beacons in a mirage still do.
 */
object Mirage {
    /**
     * Whether a [beacon] is shown with false information, when the player's
     * at the [current] beacon. The Silent Capital's Chorus-held beacons (see
     * Sector.chorusHeld) lie too, unless the player [seesThroughChorus]
     * (see [seesThroughChorus]), or scouting has got through (as through
     * the mirages, see Sector.mirageScouted).
     */
    fun isDeceptive(beacon: Beacon, current: Beacon, seesThroughChorus: Boolean = false): Boolean {
        if (beacon.visited || beacon == current)
            return false
        if (!seesThroughChorus && !beacon.sector.mirageScouted && beacon in beacon.sector.chorusHeld)
            return true
        if (beacon.nebulaType == Beacon.EnvironmentType.MIRAGE)
            return true
        return current.nebulaType == Beacon.EnvironmentType.MIRAGE && !current.sector.mirageScouted
    }

    /**
     * Whether the [player] can tell what's really at the Silent Capital's
     * Chorus-held beacons: with sensors upgraded to level 3, or a Slug
     * aboard, whose telepathy hears through the Chorus.
     */
    fun seesThroughChorus(player: Ship): Boolean =
        (player.sensors?.energyLevels ?: 0) >= CHORUS_SENSORS_LEVEL ||
                player.friendlyCrew.any { it is CrewSlug && it.ownerShip == player }

    /**
     * Whether a beacon falsely shows a ship.
     */
    fun showsFakeShip(beacon: Beacon): Boolean = random(beacon).nextInt(100) < FAKE_SHIP_CHANCE

    /**
     * Whether a beacon falsely shows a hazard.
     */
    fun showsFakeHazard(beacon: Beacon): Boolean = random(beacon).let { it.nextInt(100); it.nextInt(100) < FAKE_HAZARD_CHANCE }

    private fun random(beacon: Beacon) = Random(beacon.environmentSeed xor SALT)

    /**
     * In a mirage nebula, the enemy ships' sensors aren't the only thing
     * fooled: each of their weapons looks like a weapon of another kind,
     * such as a laser that fires missiles. This is the weapon it looks like,
     * or null if it looks like itself (the player's always do, as do any
     * outside a mirage). The Mainframe's Mind disguises its weapons the same
     * way, wherever it is, unless there's a Slug aboard the player's ship,
     * who can tell what's real. It's picked from the beacon's seed, the ship and the
     * weapon's slot, so it doesn't change during a fight. A double or triple
     * weapon looks like another double or triple one (see [disguisesFor]).
     */
    fun weaponDisguise(weapon: AbstractWeaponInstance): AbstractWeaponBlueprint? {
        val game = weapon.ship.sys
        if (weapon.ship.isPlayerShip)
            return null
        val beacon = game.currentBeacon
        val mind = weapon.ship.colossal?.kind == ColossalBoss.Kind.MAINFRAME_MIND
        if (mind && game.player.friendlyCrew.any { it is CrewSlug && it.ownerShip == game.player })
            return null
        if (beacon.nebulaType != Beacon.EnvironmentType.MIRAGE && !mind)
            return null

        val candidates = disguisesFor(weapon.type, disguises(game), game.blueprintManager)
        if (candidates.isEmpty())
            return null

        val slot = weapon.ship.hardpoints.indexOfFirst { it.weapon == weapon }
        val seed = beacon.environmentSeed xor WEAPON_SALT xor weapon.ship.type.name.hashCode() xor (slot * 7919)
        return candidates.random(Random(seed))
    }

    /**
     * The weapons a weapon of [type] can be disguised as, of the [weapons]
     * that can turn up in the game: those of another kind. A double or
     * triple weapon (see WeaponSizes) is disguised as another of the same
     * size, made from one of them, so it's drawn with that one's image of
     * its own, rather than a single weapon's drawn over again for each copy
     * (which would show double or triple weapons that don't exist, such as
     * a twin flak gun, as Advanced Edition's weapons aren't combined).
     */
    fun disguisesFor(
        type: AbstractWeaponBlueprint, weapons: List<AbstractWeaponBlueprint>, blueprints: BlueprintManager
    ): List<AbstractWeaponBlueprint> {
        val kind = weaponKind(type)
        val others = weapons.filter { weaponKind(it) != kind }
        if (!type.isCombined)
            return others
        return others.mapNotNull { WeaponSizes.combined(blueprints, it, type.combinedCount) }
    }

    /**
     * The kinds of weapon a disguise has to differ by.
     */
    fun weaponKind(type: AbstractWeaponBlueprint): String = when (type) {
        is LaserBlueprint -> if (type.ionDamage > 0) "ion" else "laser"
        is MissileBlueprint -> "missile"
        is BeamBlueprint -> "beam"
        is BombBlueprint -> "bomb"
        is FlakBlueprint -> "flak"
        else -> type.javaClass.simpleName
    }

    // The weapons that can turn up in the game, to disguise weapons as
    private var disguisesFor: InGameState? = null
    private var disguiseList: List<AbstractWeaponBlueprint> = emptyList()

    private fun disguises(game: InGameState): List<AbstractWeaponBlueprint> {
        if (disguisesFor !== game) {
            disguisesFor = game
            disguiseList = game.blueprintManager.blueprints.values
                .filterIsInstance<AbstractWeaponBlueprint>()
                .filter { (it.rarity ?: 0) > 0 }
                .sortedBy { it.name }
        }
        return disguiseList
    }

    const val FAKE_SHIP_CHANCE = 50
    const val FAKE_HAZARD_CHANCE = 35

    /**
     * The chance, in percent, that revealing the map gets through the
     * mirages (see Sector.mirageScouted).
     */
    const val SCOUTING_CHANCE = 50

    /** The sensors level that sees through the Silent Capital's Chorus (see [seesThroughChorus]). */
    const val CHORUS_SENSORS_LEVEL = 3

    private const val SALT = 0x6d697261
    private const val WEAPON_SALT = 0x77656170
}
