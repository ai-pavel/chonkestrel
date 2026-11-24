package xyz.znix.xftl.drones

import org.jdom2.Element
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.RoomPoint
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.f
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.layout.Door
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.weapons.DroneBlueprint
import java.util.PriorityQueue

/**
 * The anti-personnel drone: it walks around its own ship, hunting down
 * intruders (boarders, and boarding drones) and fighting them, as vanilla's
 * does. It starts in the drones room, like the system repair drone.
 */
class AntiPersonnelDrone(type: DroneBlueprint) : AbstractIndoorsDrone(type) {
    override val occupancySlotType get() = AbstractCrew.SlotType.CREW

    // Anti-personnel and boarding drones use the same images
    override val pawnCodename: String get() = "battle"

    override fun init(ownerShip: Ship) {
        super.init(ownerShip)
        val dronesRoom = ownerShip.rooms.find { it.system is Drones }
        requireNotNull(dronesRoom) { "Owner ship '${ownerShip.name}' spawned an anti-personnel drone without a drones system" }

        // Don't spawn in a pawn if that's already been done by deserialisation.
        if (pawn == null) {
            // An enemy's twin or triple one is a bulwark, walking its big
            // rooms, if there's room for it, and otherwise goes as a single
            // drone
            val spot = if (bulwarkSize > 1) bulwarkSpot(ownerShip, bulwarkSize) else null
            if (spot != null) {
                standing = true
                spawnAt(spot)
                hold(pawn!!, listOf(spot))
                // (anyone on their way to its cells goes elsewhere)
                ship.updateCrewReservedSlots()
                // (it looks round before it sets off on its rounds)
                waitTime = PATROL_PAUSE
            } else {
                spawn(dronesRoom)
            }
        }
    }

    /**
     * How big a bulwark it'd be (see doc/game/drone-sizes.md): an enemy's
     * combined anti-personnel drone is one big drone walking its big rooms,
     * as wide and tall as it has copies; the player's is a squad of them (1
     * here).
     */
    private val bulwarkSize: Int
        get() = if (type.isCombined && ownerShip != ownerShip.sys.player) type.combinedCount else 1

    // Whether it's a bulwark, standing on a square of [bulwarkSize] cells
    private var standing = false

    /** How big it is: [bulwarkSize] for a bulwark, and 1 otherwise. */
    val scale: Int get() = if (standing) bulwarkSize else 1

    // A bulwark walks from square to square (each a square of [scale]
    // cells, named by its top-left one): a cell at a time across a room, or
    // the whole way through a passage as wide as it is into the next room.
    // While it's on its way from one to the next, these are the two.
    private var stepFrom: RoomPoint? = null
    private var stepTo: RoomPoint? = null

    // Whether it's after boarders (and walks faster), rather than on its rounds
    private var hunting = false

    // The corner of its rounds it's going to next (see patrolRoute)
    private var patrolIndex = 0

    // How long it stops at a corner of its rounds before it goes on
    private var waitTime = 0f

    // How long it waits, with nowhere to go just now, before it looks again
    private var lookTime = 0f

    // The cells of [square], its top-left one among them
    private fun cellsOf(square: IPoint): List<ConstPoint> =
        (0 until scale).flatMap { dx -> (0 until scale).map { dy -> ConstPoint(square.x + dx, square.y + dy) } }

    /**
     * Hold the cells of [squares] (where it stands, or the two it's walking
     * between), and only those: no one else, of either side, can stand on
     * any of them (see Room.holdCells).
     */
    private fun hold(pawn: Pawn, squares: List<RoomPoint>) {
        for (room in ship.rooms)
            room.releaseCells(pawn)
        for ((room, inRoom) in squares.groupBy { it.room })
            room.holdCells(pawn, inRoom.flatMap { cellsOf(it) }.filter { room.containsRelative(it) }.distinct())
    }

    override fun updatePawn(dt: Float) {
        val pawn = this.pawn!!

        // A bulwark walks its own way, from square to square
        if (standing) {
            updateBulwark(pawn, dt)
            return
        }

        // Busy getting somewhere, or fighting where it is
        if (pawn.pathingTarget != null)
            return
        if (hasIntruders(pawn.room))
            return

        // Go for the nearest room with intruders in it (the first one it
        // can get to), one the other copies of a combined drone aren't
        // after, if there is one
        val rooms = ship.rooms.filter { hasIntruders(it) }
            .sortedWith(compareBy<Room> { claimedBySibling(it) }
                .thenBy { (it.pixelCentre - pawn.room.pixelCentre).let { d -> d.x * d.x + d.y * d.y } })
        for (room in rooms) {
            if (pawn.setTargetRoom(room))
                return
        }

        returnHome()
    }

    private fun hasIntruders(room: Room): Boolean {
        return room.crew.any { it.mode == AbstractCrew.SlotType.INTRUDER && it.health > 0f }
    }

    /**
     * A bulwark (see doc/game/drone-sizes.md): it fights whoever's in its
     * room; otherwise it goes after the nearest boarders it can get to,
     * through passages as wide as itself, into rooms it can stand in, and
     * with no boarders to go after, it walks slowly round the edges of the
     * rooms it can get to.
     */
    private fun updateBulwark(pawn: Pawn, dt: Float) {
        val to = stepTo
        if (to != null) {
            val target = ConstPoint(to.offsetX, to.offsetY)
            val from = stepFrom
            val standingAt = pawn.standingPosition
            val arrived = pawn.nextTargetPos == null && standingAt != null &&
                    standingAt.room == to.room && standingAt posEq to
            if (!arrived) {
                // On its way. Anything else that stops it (a lockdown's
                // crystal sealing the doors) leaves it waiting where it is,
                // until it can go on.
                if (pawn.nextTargetPos?.posEq(target) != true)
                    pawn.nextTargetPos = target
                hold(pawn, listOfNotNull(from, to))
                // The doors open for it, as for the ship's crew
                if (from != null && from.room != to.room)
                    moves(from).firstOrNull { it.to == to }?.doors?.forEach { it.crewRequestOpen(pawn) }
                return
            }
            stepFrom = null
            stepTo = null
        }

        // (lining itself up with a cell, it keeps what it held)
        val at = pawn.standingPosition ?: return

        // A square that doesn't fit in its room (as if it was stopped on
        // its way through a door): back into the room
        val fitted = RoomPoint(
            at.room,
            at.x.coerceIn(0, (at.room.width - scale).coerceAtLeast(0)),
            at.y.coerceIn(0, (at.room.height - scale).coerceAtLeast(0))
        )
        if (fitted != at) {
            startStep(pawn, at, Move(fitted, emptyList()))
            return
        }
        hold(pawn, listOf(at))

        // Fighting whoever's in its room
        if (hasIntruders(at.room))
            return

        if (waitTime > 0f)
            waitTime = (waitTime - dt).coerceAtLeast(0f)
        if (lookTime > 0f) {
            lookTime -= dt
            return
        }
        val move = chooseMove(pawn, at)
        if (move == null) {
            lookTime = RETHINK_TIME
            return
        }
        startStep(pawn, at, move)
    }

    // Set off from [from] to the next square
    private fun startStep(pawn: Pawn, from: RoomPoint, move: Move) {
        stepFrom = from
        stepTo = move.to
        hold(pawn, listOf(from, move.to))
        // (anyone on their way to its new cells goes elsewhere)
        ship.updateCrewReservedSlots()
        pawn.nextTargetPos = ConstPoint(move.to.offsetX, move.to.offsetY)
        move.doors.forEach { it.crewRequestOpen(pawn) }
    }

    // Where to go next from [at]: the first step towards the nearest
    // boarders it can get to, or failing that (once it's done stopping at
    // a corner), towards the next corner of its rounds, or null to stay
    // where it is for now
    private fun chooseMove(pawn: Pawn, at: RoomPoint): Move? {
        if (ship.rooms.any { hasIntruders(it) && canStandIn(it) }) {
            val move = firstMove(pawn, at) { hasIntruders(it.room) }
            hunting = move != null
            if (move != null)
                return move
        }
        hunting = false
        if (waitTime > 0f)
            return null

        val route = patrolRoute(at)
        if (route.size < 2)
            return null
        repeat(route.size) {
            patrolIndex %= route.size
            val corner = route[patrolIndex]
            if (corner == at) {
                // There: it stops to look round, and then goes on to the next
                patrolIndex = (patrolIndex + 1) % route.size
                waitTime = PATROL_PAUSE
                return null
            }
            firstMove(pawn, at) { it == corner }?.let { return it }
            // (someone's in the way: on to the next one)
            patrolIndex = (patrolIndex + 1) % route.size
        }
        return null
    }

    /**
     * A step from one square to the next: [doors] are those of the passage
     * it goes through, if it changes rooms.
     */
    private class Move(val to: RoomPoint, val doors: List<Door>)

    // Whether a bulwark can stand in [room]: one without a system, as big as
    // it is, and without another bulwark in it (one to a room)
    private fun canStandIn(room: Room): Boolean = room.system == null && room.width >= scale && room.height >= scale &&
            room.crew.none { it !== pawn && it.cellsWide > 1 }

    // Whether no one else is standing on, or on their way to, any of the
    // cells of [square]: of its own side, and with [intruders], of either
    private fun isFree(pawn: Pawn, square: RoomPoint, intruders: Boolean): Boolean = cellsOf(square).all {
        square.room.isSlotFree(it, AbstractCrew.SlotType.CREW, pawn) &&
                (!intruders || square.room.isSlotFree(it, AbstractCrew.SlotType.INTRUDER, pawn))
    }

    // The steps from [square], whoever's in the way: a cell across its room
    // each way, and through a passage into the next room, where there's one
    private fun moves(square: RoomPoint): List<Move> {
        val room = square.room
        val out = ArrayList<Move>()
        for ((dx, dy) in DIRECTIONS) {
            val x = square.x + dx
            val y = square.y + dy
            if (x >= 0 && y >= 0 && x + scale <= room.width && y + scale <= room.height)
                out += Move(RoomPoint(room, x, y), emptyList())
            else
                passage(square, dx, dy)?.let { out += it }
        }
        return out
    }

    /**
     * The step through the wall of [square]'s room [dx], [dy] of it: if the
     * square's against that wall, and there's a door on each of the cells
     * along that side of it (a passage as wide as the bulwark), all into the
     * same room, which it can stand in, where it comes out lined up with
     * where it went in.
     */
    private fun passage(square: RoomPoint, dx: Int, dy: Int): Move? {
        val room = square.room
        val against = when {
            dx > 0 -> square.x + scale == room.width
            dx < 0 -> square.x == 0
            dy > 0 -> square.y + scale == room.height
            else -> square.y == 0
        }
        if (!against)
            return null

        // (doors are on the grid line between the cells either side)
        val vertical = dx != 0
        val doors = (0 until scale).map { i ->
            val position = when {
                dx > 0 -> ConstPoint(room.x + room.width, room.y + square.y + i)
                dx < 0 -> ConstPoint(room.x, room.y + square.y + i)
                dy > 0 -> ConstPoint(room.x + square.x + i, room.y + room.height)
                else -> ConstPoint(room.x + square.x + i, room.y)
            }
            room.doors.firstOrNull { it.isVertical == vertical && it.position == position && !it.isAirlock }
                ?: return null
        }
        val other = doors.first().other(room) ?: return null
        if (doors.any { it.other(room) != other } || !canStandIn(other))
            return null

        val x = when {
            dx > 0 -> 0
            dx < 0 -> other.width - scale
            else -> room.x + square.x - other.x
        }
        val y = when {
            dy > 0 -> 0
            dy < 0 -> other.height - scale
            else -> room.y + square.y - other.y
        }
        if (x < 0 || y < 0 || x + scale > other.width || y + scale > other.height)
            return null
        return Move(RoomPoint(other, x, y), doors)
    }

    /**
     * The first step on the shortest way from [start] to a square that
     * [isGoal], or null if there's no way there just now. It only walks
     * over squares no one else is on (or on their way to), and through
     * doors that aren't locked against it (see Door.isLockedFor); the
     * square it ends on may have boarders on it, if there's no free one
     * nearer (they're moved off its cells, see Room.holdCells).
     */
    private fun firstMove(pawn: Pawn, start: RoomPoint, isGoal: (RoomPoint) -> Boolean): Move? {
        val distance = HashMap<RoomPoint, Int>()
        val first = HashMap<RoomPoint, Move>()
        val queue = PriorityQueue<Pair<Int, RoomPoint>>(compareBy { it.first })
        distance[start] = 0
        queue += 0 to start
        while (queue.isNotEmpty()) {
            val (d, square) = queue.poll()
            if (d > distance.getValue(square))
                continue
            if (square != start && isGoal(square))
                return first[square]
            for (move in moves(square)) {
                if (move.doors.any { it.isLockedFor(pawn) })
                    continue
                // (crossing a room's as far as the bulwark is wide)
                var cost = d + if (move.doors.isEmpty()) 1 else scale
                if (!isFree(pawn, move.to, true)) {
                    if (!isGoal(move.to) || !isFree(pawn, move.to, false))
                        continue
                    cost += BOARDERS_IN_THE_WAY
                }
                if (cost >= (distance[move.to] ?: Int.MAX_VALUE))
                    continue
                distance[move.to] = cost
                first[move.to] = first[square] ?: move
                queue += cost to move.to
            }
        }
        return null
    }

    /**
     * Its rounds, from [at]: each corner of every room it can get to
     * (whoever's in the way), clockwise round each room from its top-left,
     * the rooms in the order a walk from the first of them finds them, so
     * it goes round the edges of each room, and on into the next.
     */
    private fun patrolRoute(at: RoomPoint): List<RoomPoint> {
        // The rooms it can get to, and which lead into which
        val next = HashMap<Room, MutableSet<Room>>()
        val seen = hashSetOf(at)
        val queue = ArrayDeque(listOf(at))
        while (queue.isNotEmpty()) {
            val square = queue.removeFirst()
            next.getOrPut(square.room) { sortedSetOf(compareBy<Room> { it.id }) }
            for (move in moves(square)) {
                if (move.to.room != square.room)
                    next.getValue(square.room) += move.to.room
                if (seen.add(move.to))
                    queue += move.to
            }
        }

        val rooms = ArrayList<Room>()
        fun visit(room: Room) {
            if (room in rooms)
                return
            rooms += room
            next[room]?.forEach(::visit)
        }
        visit(next.keys.minBy { it.id })

        return rooms.flatMap { room ->
            val right = room.width - scale
            val bottom = room.height - scale
            listOf(ConstPoint(0, 0), ConstPoint(right, 0), ConstPoint(right, bottom), ConstPoint(0, bottom))
                .distinct().map { RoomPoint(room, it) }
        }
    }

    override fun drawPawn() {
        // (its glow's drawn by the pawn, see Pawn.drawImage)
    }

    override fun makePawn(room: Room): Pawn = AntiPersonnelPawn(room)

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)
        SaveUtil.addAttrBool(elem, "bulwark", standing)
        if (!standing)
            return
        // Where it's walking, and the rest of what it's doing
        stepFrom?.let { SaveUtil.addRoomPoint(elem, "bulwarkFrom", it) }
        stepTo?.let { SaveUtil.addRoomPoint(elem, "bulwarkTo", it) }
        SaveUtil.addAttrBool(elem, "hunting", hunting)
        SaveUtil.addAttrInt(elem, "patrolIndex", patrolIndex)
        SaveUtil.addAttrFloat(elem, "waitTime", waitTime)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader, containingShip: Ship) {
        super.loadFromXML(elem, refs, containingShip)
        standing = elem.getAttributeValue("bulwark")?.toBoolean() ?: false
        // (a game saved before bulwarks walked has none of these)
        stepFrom = elem.getChild("bulwarkFrom")?.let { SaveUtil.getRoomPoint(elem, "bulwarkFrom", containingShip) }
        stepTo = elem.getChild("bulwarkTo")?.let { SaveUtil.getRoomPoint(elem, "bulwarkTo", containingShip) }
        hunting = elem.getAttributeValue("hunting")?.toBoolean() ?: false
        patrolIndex = elem.getAttributeValue("patrolIndex")?.toIntOrNull() ?: 0
        waitTime = elem.getAttributeValue("waitTime")?.toFloatOrNull() ?: 0f
    }

    private inner class AntiPersonnelPawn(room: Room) : AbstractIndoorsDrone.Pawn(room) {
        // Always fight by shooting a laser, as the boarding drone does
        override val canPunch: Boolean get() = false
        override val canFight: Boolean get() = true

        // A bulwark has as much health as it covers cells, and does as many
        // times the damage as it's wide
        override val maxHealth: Float get() = 150f * scale * scale
        override val attackDamageMult: Float get() = scale.toFloat()
        override val cellsWide: Int get() = scale

        // Drones don't burn, but a bulwark's big enough for a fire to get
        // into its works: it takes fire damage as the crew do
        override val fireDamageMult: Float get() = if (scale > 1) 1f else 0f

        // A bulwark's drawn as big as it is, over the cells it stands on,
        // from its own sheets (the anti-personnel drone's, scaled up by a
        // pixel-art scaler, see tools/drone_sizes.py), or failing that, the
        // usual one stretched
        override fun drawImage(x0: Float, y0: Float, x1: Float, y1: Float, baseFrame: Image, alpha: Float) {
            val x2 = x0 + (x1 - x0) * scale
            val y2 = y0 + (y1 - y0) * scale
            val big = bulwarkSheet("base")
            if (scale == 1 || big == null) {
                super.drawImage(x0, y0, x2, y2, baseFrame, alpha)
                return
            }

            // The same frame, in the scaled-up sheet
            val sheet = game.getImg("img/people/${pawnLook}_base.png")
            val frameX = (baseFrame.textureOffsetX - sheet.textureOffsetX) * scale
            val frameY = (baseFrame.textureOffsetY - sheet.textureOffsetY) * scale
            fun drawFrom(image: Image) {
                val w = baseFrame.width * scale
                val h = baseFrame.height * scale
                if (frameX < 0 || frameY < 0 || frameX + w > image.width || frameY + h > image.height)
                    return
                val frame = image.getSubImage(frameX, frameY, w, h)
                frame.draw(x0, y0, x2, y2, 0f, 0f, w.f, h.f, alpha, Colour.white)
            }
            drawFrom(big)

            // Its lights glowing while it's on, as the pawn's are
            if (runningLastUpdate && powerUpDuration == 0f)
                bulwarkSheet("layer1")?.let(::drawFrom)
        }

        // One of a bulwark's sheets (its "base" or "layer1"), if the mod has it
        private fun bulwarkSheet(layer: String): Image? {
            val path = "img/people/xftl_bulwark_x${scale}_$layer.png"
            if (scale == 1 || game.content.datafile.getOrNull(path) == null)
                return null
            return game.getImg(path)
        }

        override fun removeFromShip() {
            // (on its way through a door, it holds cells in two rooms)
            for (each in room.ship.rooms)
                each.releaseCells(this)
            super.removeFromShip()
        }

        // As slow as a Rockman, rather than walking as fast as a human, and
        // a bulwark on its rounds slower still
        override val droneSpeed: Float
            get() = BASE_MOVEMENT_SPEED * if (scale > 1 && !hunting) PATROL_SPEED else 0.5f
    }

    companion object {
        // How fast a bulwark walks on its rounds (of a human's speed), and
        // how long it stops at each corner of them, in seconds
        const val PATROL_SPEED = 0.3f
        const val PATROL_PAUSE = 2f

        // How long a bulwark with nowhere to go waits before it looks again
        private const val RETHINK_TIME = 0.5f

        // How much further a bulwark goes for a square no boarder's
        // standing on, before it takes one they are (in cells)
        private const val BOARDERS_IN_THE_WAY = 4

        private val DIRECTIONS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        /**
         * Where a bulwark of [size] can stand on [ship]: the top-left cell of
         * a free square of [size] cells, in the biggest room without a
         * system it fits in (it never stands in the way of a system), and
         * without another bulwark in it (one to a room), or null if it fits
         * nowhere.
         */
        fun bulwarkSpot(ship: Ship, size: Int): RoomPoint? {
            for (room in bulwarkRooms(ship, size).filter { r -> r.crew.none { it.cellsWide > 1 } }
                .sortedByDescending { it.cellCount }) {
                for (y in 0..room.height - size) for (x in 0..room.width - size) {
                    // (no one standing there, of either side: boarders have
                    // slots of their own)
                    val free = (0 until size).all { dx ->
                        (0 until size).all { dy ->
                            val cell = ConstPoint(x + dx, y + dy)
                            room.isSlotFree(cell, AbstractCrew.SlotType.CREW) &&
                                    room.isSlotFree(cell, AbstractCrew.SlotType.INTRUDER)
                        }
                    }
                    if (free)
                        return RoomPoint(room, x, y)
                }
            }
            return null
        }

        /** Whether a bulwark of [size] could stand anywhere on [ship] (see [bulwarkRooms]). */
        fun bulwarkFits(ship: Ship, size: Int): Boolean = bulwarkRooms(ship, size).isNotEmpty()

        /** The rooms a bulwark of [size] can stand in: those without a system, at least [size] cells each way. */
        fun bulwarkRooms(ship: Ship, size: Int): List<Room> =
            ship.rooms.filter { it.system == null && it.width >= size && it.height >= size }
    }

}
