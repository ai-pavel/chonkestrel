package xyz.znix.xftl.weapons

import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.SelectedTarget

/**
 * The "shadow" targets of a combined weapon (see [WeaponSizes]): the player
 * aims it at one room, and each of its other copies fires at a room in
 * another of the ships fighting the player, picked to match. See
 * doc/game/weapon-sizes.md.
 */
object ShadowTargets {
    /**
     * The rooms the other copies of a weapon of [size] aimed at [room] fire
     * at: one in each of the other ships fighting the player, left to right,
     * as many as there are copies to spare. A copy with no other ship to aim
     * at fires at [room] too.
     */
    fun pick(game: InGameState, room: Room, size: Int): List<Room> {
        if (size <= 1)
            return emptyList()
        return otherShips(game, room.ship)
            .take(size - 1)
            .mapNotNull { matchingRoom(room, it) }
    }

    /**
     * The other ships fighting the player, left to right, that a combined
     * weapon aimed at [ship] can also aim at: not cloaked ones, which
     * can't be aimed at (a copy fires at the main target instead).
     */
    fun otherShips(game: InGameState, ship: Ship): List<Ship> =
        game.hostileShipsLeftToRight.filter { it != ship && !it.isCloakActive }

    /**
     * The room in [ship] that matches [room] (in another ship): the one with
     * the same system, if it has one, or else the one at the nearest place
     * in the ship, measured across each ship's rooms.
     */
    fun matchingRoom(room: Room, ship: Ship): Room? {
        val systemType = room.system?.blueprint?.type
        if (systemType != null) {
            ship.rooms.firstOrNull { it.system?.blueprint?.type == systemType }?.let { return it }
        }

        val (fx, fy) = relativePosition(room)
        return ship.rooms.minByOrNull { other ->
            val (ox, oy) = relativePosition(other)
            (ox - fx) * (ox - fx) + (oy - fy) * (oy - fy)
        }
    }

    /**
     * A combined beam's [aim] (see [WeaponSizes]), as it crosses [ship]: the
     * same line across the screen, starting from where it does in the ship
     * it's aimed at, moved into [ship]'s space (it's in another window).
     */
    fun beamAimIn(game: InGameState, aim: SelectedTarget.BeamAim, ship: Ship): SelectedTarget.BeamAim {
        val offset = game.getEnemyPosition(aim.targetShip) - game.getEnemyPosition(ship)
        val moved = SelectedTarget.BeamAim(aim.beamWeapon, -1, ship, aim.startShipPoint + offset)
        moved.angle = aim.angle
        moved.length = aim.length
        moved.updateHitRooms()
        return moved
    }

    /**
     * Whether a shadow target in [room] is still worth firing at: its ship
     * is still there, and still fighting the player. Otherwise it's dropped,
     * and that copy fires at the main target.
     */
    fun isStillValid(game: InGameState, room: Room): Boolean =
        !room.ship.isDead && game.isEnemyOf(game.player, room.ship)

    // Where the middle of a room is in its ship, from 0 to 1 across its rooms
    private fun relativePosition(room: Room): Pair<Float, Float> {
        val rooms = room.ship.rooms
        val minX = rooms.minOf { it.x }
        val maxX = rooms.maxOf { it.x + it.width }
        val minY = rooms.minOf { it.y }
        val maxY = rooms.maxOf { it.y + it.height }
        fun fraction(centre: Float, min: Int, max: Int) = if (max > min) (centre - min) / (max - min) else 0.5f
        return Pair(
            fraction(room.x + room.width / 2f, minX, maxX),
            fraction(room.y + room.height / 2f, minY, maxY)
        )
    }
}
