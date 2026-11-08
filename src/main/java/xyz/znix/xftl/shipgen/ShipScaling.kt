package xyz.znix.xftl.shipgen

import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.ShipBlueprint
import kotlin.math.roundToInt

/**
 * Scaling for enemy ships fighting a player ship that's bigger than them
 * (see [ShipBlueprint.shipSize]).
 */
object ShipScaling {
    /**
     * How many times bigger the player's ship is than the enemy ship, which
     * is one for vanilla ships.
     */
    fun sizeRatio(player: ShipBlueprint, enemy: ShipBlueprint): Int {
        return (player.shipSize / enemy.shipSize).coerceAtLeast(1)
    }

    /**
     * How many times bigger an enemy ship's boarding parties are, so they're
     * still a threat to a bigger player ship (with more crew).
     *
     * This is one if the ships are the same size. Otherwise it starts at one
     * in the first sector, so the player's starting crew can hold off one
     * ship's boarders, and rises to twice the size ratio by the last sector
     * (or a sector later on easy, and earlier on hard): one to four times
     * for a ship twice the size of the vanilla ships.
     *
     * [sector] is the vanilla sector number, as used for the difficulty.
     */
    @JvmStatic
    fun boardingMultiplier(game: InGameState, enemy: Ship, sector: Int): Int {
        val player = game.player ?: return 1
        val ratio = sizeRatio(player.type, enemy.type)
        if (ratio <= 1)
            return 1

        val difficultyOffset = when (game.difficulty) {
            Difficulty.EASY -> -1
            Difficulty.NORMAL -> 0
            Difficulty.HARD -> 1
        }
        val progress = ((sector + difficultyOffset) / LAST_SECTOR).coerceIn(0f, 1f)
        return (1 + (2 * ratio - 1) * progress).roundToInt()
    }

    /**
     * The same, for the sector the player is currently in.
     */
    @JvmStatic
    fun boardingMultiplier(game: InGameState, enemy: Ship): Int {
        return boardingMultiplier(game, enemy, game.currentBeacon.sector.difficultySectorNumber)
    }

    // The last vanilla sector number
    private const val LAST_SECTOR = 7f
}
