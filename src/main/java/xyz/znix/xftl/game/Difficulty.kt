package xyz.znix.xftl.game

/**
 * The game's difficulties, easiest first, and everything each one changes,
 * so the rest of the game asks the difficulty rather than checking which one
 * it is. Insane is XFTL's own, after vanilla's three. See
 * doc/game/difficulty.md for the whole table.
 *
 * The difficulty's saved by its name (in the game, the profile and the
 * statistics), so new ones only go on the end.
 */
enum class Difficulty(
    /**
     * The offset applied to the sector number when calculating
     * ship difficulty from the sector number (see ShipGenerator.buildShip).
     *
     * This is -1 on easy, which delays everything by a sector, and +1 on
     * insane, where the enemies are a sector ahead.
     */
    val sectorOffset: Int,

    /**
     * The offset applied to the sector number when calculating
     * scrap rewards (see LootDropGenerator): +1 on easy and -1 on hard, so
     * rewards are bigger on easy and smaller on hard, and smaller still on
     * insane.
     */
    val rewardSectorOffset: Int,

    /**
     * The offset applied to the sector number for how hard the map is (see
     * GameMap.effectiveSectorNumber): how many hazards a beacon can have at
     * once, and the early boarders.
     */
    val mapSectorOffset: Int,

    /**
     * The offset applied to the sector number for how many boarders a
     * bigger enemy ship sends (see ShipScaling.boardingMultiplier).
     */
    val boardingSectorOffset: Int,

    /** The scrap the player starts with, for each of their ship's size (see Ship.startingScrap). */
    val startingScrapPerSize: Int,

    /**
     * The chance (in percent) of an enemy ship getting each of its optional
     * systems in the first sector, rising by ten a sector (see ShipGenerator).
     */
    val optionalSystemBaseChance: Int,

    /** The extra power an enemy ship gets for its general systems (see ShipGenerator). */
    val extraGeneralPower: Int,

    /**
     * Whether an enemy ship's optional systems (other than the offensive
     * ones, which always are) only cost it one power, rather than two.
     */
    val cheapOptionalSystems: Boolean,

    /**
     * The most an enemy ship's system can be powered past its sector's level
     * at random (see ShipGenerator.pickSystemPowerLimit), after the first
     * sector on easy.
     */
    val systemPowerOffsetMax: Int,

    /**
     * The number of attacks it takes to break a level 2, 3 and 4 door (see
     * Door.maxHealth).
     */
    val doorHealths: List<Int>,

    /** The score's multiplier (see GameRecord.score). */
    val scoreMultiplier: Float,

    /**
     * How many jumps' worth closer the Rebel fleet starts in each sector
     * (see Sector, and InGameState.advanceFleet for the last stand).
     */
    val fleetHeadStartJumps: Int,

    /**
     * Every this many jumps in a sector, the Rebel fleet advances an extra
     * step (see Sector.isFleetExtraStep), or 0 for never.
     */
    val fleetExtraStepEvery: Int,

    /**
     * Whether the enemies aim their weapons at the player's most valuable
     * systems rather than at random (see ShipAI.keySystemTarget).
     */
    val enemiesTargetKeySystems: Boolean,

    /** The localisation key for the start button for this difficulty, and its name. */
    val startButtonKey: String,

    /**
     * The localisation key for an achievement unlocked on this difficulty
     * (vanilla's ach_easy and so on), for the achievements' tooltips.
     */
    val achievementUnlockKey: String,
) {
    EASY(
        sectorOffset = -1, rewardSectorOffset = 1, mapSectorOffset = -1, boardingSectorOffset = -1,
        startingScrapPerSize = 30,
        optionalSystemBaseChance = 10, extraGeneralPower = 0, cheapOptionalSystems = false, systemPowerOffsetMax = 1,
        doorHealths = listOf(12, 16, 20),
        scoreMultiplier = 0.5f,
        fleetHeadStartJumps = 0, fleetExtraStepEvery = 0,
        enemiesTargetKeySystems = false,
        startButtonKey = "easy_button", achievementUnlockKey = "ach_easy",
    ),
    NORMAL(
        sectorOffset = 0, rewardSectorOffset = 0, mapSectorOffset = 0, boardingSectorOffset = 0,
        startingScrapPerSize = 10,
        optionalSystemBaseChance = 20, extraGeneralPower = 0, cheapOptionalSystems = false, systemPowerOffsetMax = 2,
        doorHealths = listOf(8, 12, 18),
        scoreMultiplier = 1f,
        fleetHeadStartJumps = 0, fleetExtraStepEvery = 0,
        enemiesTargetKeySystems = false,
        startButtonKey = "normal_button", achievementUnlockKey = "ach_normal",
    ),
    HARD(
        sectorOffset = 0, rewardSectorOffset = -1, mapSectorOffset = 1, boardingSectorOffset = 1,
        startingScrapPerSize = 10,
        optionalSystemBaseChance = 30, extraGeneralPower = 1, cheapOptionalSystems = true, systemPowerOffsetMax = 2,
        doorHealths = listOf(6, 10, 15),
        scoreMultiplier = 1.5f,
        fleetHeadStartJumps = 0, fleetExtraStepEvery = 0,
        enemiesTargetKeySystems = false,
        startButtonKey = "hard_button", achievementUnlockKey = "ach_hard",
    ),

    /**
     * XFTL's own: harder than hard in every way, with the Rebel fleet
     * closer behind, and the enemies aiming for the player's key systems.
     */
    INSANE(
        sectorOffset = 1, rewardSectorOffset = -2, mapSectorOffset = 2, boardingSectorOffset = 2,
        startingScrapPerSize = 10,
        optionalSystemBaseChance = 40, extraGeneralPower = 2, cheapOptionalSystems = true, systemPowerOffsetMax = 2,
        doorHealths = listOf(5, 8, 12),
        scoreMultiplier = 2f,
        fleetHeadStartJumps = 1, fleetExtraStepEvery = 4,
        enemiesTargetKeySystems = true,
        startButtonKey = "xftl_insane_button", achievementUnlockKey = "xftl_ach_insane",
    );

    /**
     * True if this is at least as hard as [other]: insane does what hard
     * does where it's not given its own value (the crew's manning, the last
     * stand's exit, the win on hard achievement).
     */
    fun atLeast(other: Difficulty): Boolean = ordinal >= other.ordinal
}
