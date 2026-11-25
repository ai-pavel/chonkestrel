package xyz.znix.xftl.hangar

import xyz.znix.xftl.Constants
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.f
import xyz.znix.xftl.game.ShipBlueprint
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.rendering.Colour

/**
 * Where a ship's starting crew stand when a game starts, for the hangar to
 * show them there: the same places [xyz.znix.xftl.Ship.addCrewMember] puts
 * them in, worked out from the hangar's [EditableShip] rather than a real
 * ship. A crewmember with a room (see [ShipBlueprint.InitialCrewSpec]) mans
 * its system's console, if it has one and no one's there yet; one without
 * mans the piloting's, the engines', the weapons' or the shields', the first
 * that's free; and anyone else stands in the first free cell, of their room
 * or the ship.
 */
object StartingCrew {
    /** A crewmember of [race] in cell [cell] of room [room], typing at a console facing [facing], if they man one. */
    class Spot(val race: String, val room: Int, val cell: ConstPoint, val facing: Direction?)

    // The systems whose consoles the crew without a room go to, in order
    private val MAIN_SYSTEMS = listOf("pilot", "engines", "weapons", "shields")

    // How far out of step each crewmember's typing is with the one before (in seconds)
    private const val TYPING_STAGGER = 0.37f

    private val DIRECTIONS = listOf(Direction.LEFT, Direction.RIGHT, Direction.UP, Direction.DOWN)

    // The systems with a console to man (see SystemInfo.canBeManned)
    private val MANNED = setOf(
        "pilot", "engines", "shields", "weapons", "sensors", "doors", "drones",
        "hacking", "mind", "oxygen", "artillery"
    )

    fun spots(blueprint: ShipBlueprint, ship: EditableShip): List<Spot> {
        val taken = HashSet<Pair<Int, ConstPoint>>()
        val result = ArrayList<Spot>()

        // The console of the system the ship starts with in [room], if it
        // can be manned and no one's at it yet
        fun freeConsole(room: Int): Spot? {
            val system = ship.rooms.getOrNull(room)?.system ?: return null
            if (system.startingPower <= 0 || system.type !in MANNED)
                return null
            val (cell, facing) = console(ship, ship.rooms[room], system) ?: return null
            if (room to cell in taken)
                return null
            return Spot("", room, cell, facing)
        }

        fun freeCell(room: Int): ConstPoint? {
            val r = ship.rooms[room]
            // (not the medbay's or clonebay's obstructed cell)
            val obstructed = r.system?.takeIf { it.type == "medbay" || it.type == "clonebay" }
                ?.let { console(ship, r, it)?.first }
            for (y in 0 until r.h) for (x in 0 until r.w) {
                val cell = ConstPoint(x, y)
                if (cell != obstructed && room to cell !in taken)
                    return cell
            }
            return null
        }

        val pilotRoom = ship.rooms.indexOfFirst { it.system?.type == "pilot" }.coerceAtLeast(0)

        for (spec in blueprint.initialCrew) {
            repeat(spec.amount) {
                val room = spec.room?.takeIf { it in ship.rooms.indices }
                var spot = room?.let { freeConsole(it) }
                if (spot == null && room != null)
                    spot = freeCell(room)?.let { Spot("", room, it, null) }
                if (spot == null && room == null) {
                    spot = MAIN_SYSTEMS.firstNotNullOfOrNull { type ->
                        ship.rooms.indices.filter { ship.rooms[it].system?.type == type }
                            .firstNotNullOfOrNull { freeConsole(it) }
                    }
                }
                if (spot == null) {
                    val rooms = listOf(pilotRoom) + ship.rooms.indices.filter { it != pilotRoom }
                    spot = rooms.firstNotNullOfOrNull { r -> freeCell(r)?.let { Spot("", r, it, null) } }
                }
                spot ?: return@repeat
                taken += spot.room to spot.cell
                result += Spot(spec.race, spot.room, spot.cell, spot.facing)
            }
        }
        return result
    }

    /**
     * The cell [system]'s console is in, in [room] of [ship], and which way
     * it faces, as the game works it out (see SystemInstallConfiguration),
     * or null if it has none. (For the medbay and clonebay, it's their
     * obstructed cell.)
     */
    private fun console(ship: EditableShip, room: EditableRoom, system: EditableSystem): Pair<ConstPoint, Direction?>? {
        val (defaultSlot, defaultDir) = when (system.type) {
            "weapons" -> 1 to Direction.UP
            "engines" -> 2 to Direction.DOWN
            "shields" -> 0 to Direction.LEFT
            "pilot" -> 0 to Direction.RIGHT
            "medbay", "clonebay" -> 1 to null
            else -> null to null
        }
        var facing = system.computerDirection ?: defaultDir
        val outside = ConstPoint(999, 999)
        var point: ConstPoint? = system.computerPoint ?: when (val special = system.specialSlot) {
            // (-2 is no console, or no obstructed cell)
            -2 -> null
            null -> defaultSlot?.let { if (it >= room.w * room.h) 0 else it }?.let { ConstPoint(it % room.w, it / room.w) }
            else -> outside
        }
        // A system to man always has a console somewhere
        if (point == null && system.type in MANNED)
            point = outside

        // One outside the room (or not set) goes on an edge of it, not where
        // a door is, and if it can, not in a cell with a door
        if (point != null && (point.x !in 0 until room.w || point.y !in 0 until room.h)) {
            fun hasDoor(x: Int, y: Int, dir: Direction): Boolean {
                val ax = room.x + x
                val ay = room.y + y
                return ship.doors.any { door ->
                    when (dir) {
                        Direction.LEFT -> door.isVertical && door.x == ax && door.y == ay
                        Direction.RIGHT -> door.isVertical && door.x == ax + 1 && door.y == ay
                        Direction.UP -> !door.isVertical && door.x == ax && door.y == ay
                        else -> !door.isVertical && door.x == ax && door.y == ay + 1
                    }
                }
            }

            val edges = DIRECTIONS
            val places = (0 until room.w).flatMap { x ->
                (0 until room.h).flatMap { y ->
                    listOfNotNull(
                        Direction.LEFT.takeIf { x == 0 },
                        Direction.RIGHT.takeIf { x == room.w - 1 },
                        Direction.UP.takeIf { y == 0 },
                        Direction.DOWN.takeIf { y == room.h - 1 },
                    ).map { ConstPoint(x, y) to it }
                }
            }.filter { (cell, dir) -> !hasDoor(cell.x, cell.y, dir) }
                .sortedBy { (cell, _) -> if (edges.any { hasDoor(cell.x, cell.y, it) }) 1 else 0 }
            val place = places.firstOrNull() ?: return null
            point = place.first
            facing = place.second
        }
        return point?.let { it to facing }
    }

    /**
     * Draw the crew at their [spots] on [ship], as the game does: typing at
     * their consoles (their animation [time] seconds in, each a little out
     * of step with the others), or standing, outlined as in the game.
     */
    fun draw(state: SelectShipState, ship: EditableShip, spots: List<Spot>, time: Float) {
        for ((index, spot) in spots.withIndex()) {
            val race = state.blueprints.getOrNull(spot.race) as? CrewBlueprint ?: continue
            val name = when (val dir = spot.facing) {
                null -> "${race.name}_portrait"
                else -> "${race.name}_type_" + when (dir) {
                    // (as the game names them, see AbstractCrew.dirAsString)
                    Direction.UP, Direction.LEFT_UP, Direction.UP_RIGHT -> "up"
                    Direction.DOWN -> "down"
                    Direction.LEFT, Direction.DOWN_LEFT -> "left"
                    Direction.RIGHT, Direction.RIGHT_DOWN -> "right"
                }
            }
            val anim = runCatching { state.animations[name] }.getOrNull() ?: continue
            val sheet = state.getImgOrNull(anim.sheet.sheetPath) ?: continue
            val step = if (anim.time > 0f) ((time + index * TYPING_STAGGER) / anim.time).toInt() else 0
            val frame = anim.sheet.getSprite(sheet, anim.x + step % anim.length.coerceAtLeast(1), anim.y)

            val room = ship.rooms[spot.room]
            // (a little down from the cell's corner, as in the game)
            val x0 = (room.pixelX + spot.cell.x * ROOM_SIZE).f
            val y0 = (room.pixelY + spot.cell.y * ROOM_SIZE + 3).f
            val x1 = x0 + frame.width
            val y1 = y0 + frame.height
            val texX0 = frame.textureOffsetX.f
            val texY0 = frame.textureOffsetY.f
            val texX1 = texX0 + frame.width
            val texY1 = texY0 + frame.height

            // The outline behind them, as an unselected crewmember's (see
            // AbstractCrew.drawBackground), then the sprite itself, without
            // the colour layers, which the game only puts on the portraits
            // (see LivingCrew.drawImage)
            state.getImgOrNull("img/people/${race.name}_color.png")
                ?.draw(x0, y0, x1, y1, texX0, texY0, texX1, texY1, 1f, Constants.CREW_DESELECTED_BG)
            sheet.draw(x0, y0, x1, y1, texX0, texY0, texX1, texY1, 1f, Colour.white)
        }
    }
}
