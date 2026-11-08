package xyz.znix.xftl.game

import xyz.znix.xftl.Ship
import xyz.znix.xftl.ai.ShipAI
import xyz.znix.xftl.sector.GameMap
import kotlin.math.floor
import kotlin.random.Random

/**
 * A ship the player's facing at a beacon: the main enemy ship (slot 0, see
 * [InGameState.getEnemy]), or one of the extra ships fighting alongside it
 * (its wingmen, from slot 1, see [InGameState.getWingmen]), with its AI and
 * window.
 *
 * Bigger player ships fight several copies of each smaller hostile ship
 * (see [ShipBlueprint.shipSize] and [enemyShipCount]), so that ships much
 * stronger than vanilla still get a fight without having to scale up every
 * enemy ship.
 *
 * Wingmen escape too (without the escape and got-away events, which are
 * about the main ship), and surrender (later, at half the main ship's
 * hull), their surrender making only them stop fighting; the main ship's
 * surrender is sometimes for all of them (see
 * [InGameState.surrenderOffered]). Each one gives its own rewards when it's
 * destroyed or its crew are killed.
 */
class Opponent(val ship: Ship, player: Ship, slot: Int, game: InGameState) {
    val ai = ShipAI(ship, player)
    val ui = HostileShipUI(game, ship, slot)

    /**
     * Which window it's in: 0 for the main enemy ship, then counting up to
     * the left. It changes as the ships before it go, without the AI or
     * window being made again.
     */
    var slot: Int = slot
        set(value) {
            field = value
            ui.slot = value
        }

    /**
     * Whether it's fighting the player: until it surrenders, its crew are
     * all killed, or (for the main enemy ship) an event makes it friendly.
     */
    var hostile: Boolean = true

    /**
     * Whether the player can see inside it: while it's fighting, or while
     * the player's boarders are still aboard.
     */
    fun isInteriorVisible(player: Ship): Boolean = hostile || ship.hasCrewOwnedByShip(player)

    /**
     * Whether its crew are all dead (including its boarders on the [player]'s
     * ship), so it's beaten: automated ships have no crew to kill. The
     * crew's owners are checked, so mind controlling the last of them
     * doesn't count. They're dead as soon as they're killed, not once
     * their dying animation's done, so the shots still on their way miss.
     */
    fun isCrewKilled(player: Ship): Boolean =
        !ship.isAutoScout && !ship.hasLivingCrewOwnedBy(ship) && !player.hasLivingCrewOwnedBy(ship)

    companion object {
        /**
         * The most enemy ships there can be at once, including the main one.
         * This is limited by the space for their windows on the screen.
         */
        const val MAX_ENEMY_SHIPS = 3

        /**
         * How many copies of the [enemy] ship the [player] fights at once,
         * including the main one: the player ship's number of opponents (see
         * [ShipBlueprint.opponentShips], which is its size by default), divided
         * by the enemy ship's size.
         */
        @JvmStatic
        fun enemyShipCount(player: ShipBlueprint, enemy: ShipBlueprint): Int {
            return (player.opponentShips / enemy.shipSize).coerceIn(1, MAX_ENEMY_SHIPS)
        }

        /**
         * How many copies of the [enemy] ship the [player] fights at once in
         * a given sector (zero-indexed, out of [sectorCount]).
         *
         * Player ships can start the game fighting fewer ships (see
         * [ShipBlueprint.opponentShipsAtStart]), so the early game isn't too
         * hard. The number grows to [enemyShipCount] by the end of the first
         * half of the game (the end of act 1, see [GameMap.actOf]). Between
         * whole numbers, the fraction is the chance of fighting one more ship,
         * so the change is gradual.
         */
        @JvmStatic
        fun rampedEnemyShipCount(
            player: ShipBlueprint, enemy: ShipBlueprint,
            sector: Int, sectorCount: Int, random: Random
        ): Int = rampedEnemyShipCount(player, enemy.shipSize, sector, sectorCount, random)

        /**
         * The same, for an enemy ship of the given size.
         */
        @JvmStatic
        fun rampedEnemyShipCount(
            player: ShipBlueprint, enemySize: Int,
            sector: Int, sectorCount: Int, random: Random
        ): Int {
            val max = (player.opponentShips / enemySize).coerceIn(1, MAX_ENEMY_SHIPS)
            val expected = expectedEnemyShipCount(player, enemySize, sector, sectorCount)

            val whole = floor(expected).toInt()
            val extra = if (random.nextFloat() < expected - whole) 1 else 0
            return (whole + extra).coerceIn(1, max)
        }

        /**
         * How many ships of the given size the [player] fights at once on
         * average in a sector, see [rampedEnemyShipCount].
         */
        @JvmStatic
        fun expectedEnemyShipCount(player: ShipBlueprint, enemySize: Int, sector: Int, sectorCount: Int): Float {
            val max = (player.opponentShips / enemySize).coerceIn(1, MAX_ENEMY_SHIPS)
            val start = (player.opponentShipsAtStart / enemySize).coerceIn(1, max)

            val rampEnd = (sectorCount / 2 - 1).coerceAtLeast(1)
            val progress = sector.coerceIn(0, rampEnd).toFloat() / rampEnd
            return start + (max - start) * progress
        }

        /**
         * The most enemy ships the [player] can fight at once.
         */
        @JvmStatic
        fun maxEnemyShipCount(player: ShipBlueprint): Int {
            return player.opponentShips.coerceIn(1, MAX_ENEMY_SHIPS)
        }
    }
}
