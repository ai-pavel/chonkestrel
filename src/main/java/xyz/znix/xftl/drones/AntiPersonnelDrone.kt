package xyz.znix.xftl.drones

import org.jdom2.Element
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.RoomPoint
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.f
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.weapons.DroneBlueprint

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
            // An enemy's twin or triple one stands guard as a bulwark, if
            // there's room for it, and otherwise goes as a single drone
            val spot = if (bulwarkSize > 1) bulwarkSpot(ownerShip, bulwarkSize) else null
            if (spot != null) {
                standing = true
                spawnAt(spot)
                pawn!!.room.holdCells(pawn!!, heldCells(pawn!!))
                // (anyone on their way to its cells goes elsewhere)
                ship.updateCrewReservedSlots()
            } else {
                spawn(dronesRoom)
            }
        }
    }

    /**
     * How big a bulwark it'd be (see doc/game/drone-sizes.md): an enemy's
     * combined anti-personnel drone is one big drone standing guard, as wide
     * and tall as it has copies; the player's is a squad of them (1 here).
     */
    private val bulwarkSize: Int
        get() = if (type.isCombined && ownerShip != ownerShip.sys.player) type.combinedCount else 1

    // Whether it's a bulwark, standing on a square of [bulwarkSize] cells
    private var standing = false

    /** How big it is: [bulwarkSize] for a bulwark that's standing guard, and 1 otherwise. */
    val scale: Int get() = if (standing) bulwarkSize else 1

    // The cells it stands on, its own (the top-left one) among them: no one
    // else, of either side, can stand on any of them
    private fun heldCells(pawn: Pawn): List<ConstPoint> {
        val at = pawn.standingPosition ?: return emptyList()
        return (0 until scale).flatMap { dx -> (0 until scale).map { dy -> ConstPoint(at.x + dx, at.y + dy) } }
    }

    override fun updatePawn(dt: Float) {
        val pawn = this.pawn!!

        // A bulwark holds its cells, and stays where it is, fighting
        // whoever comes into its room
        if (standing) {
            pawn.room.holdCells(pawn, heldCells(pawn))
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

    override fun drawPawn() {
        // (its glow's drawn by the pawn, see Pawn.drawImage)
    }

    override fun makePawn(room: Room): Pawn = AntiPersonnelPawn(room)

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)
        SaveUtil.addAttrBool(elem, "bulwark", standing)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader, containingShip: Ship) {
        super.loadFromXML(elem, refs, containingShip)
        standing = elem.getAttributeValue("bulwark")?.toBoolean() ?: false
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
            room.releaseCells(this)
            super.removeFromShip()
        }

        // As slow as a Rockman, rather than walking as fast as a human
        override val droneSpeed: Float get() = BASE_MOVEMENT_SPEED * 0.5f
    }

    companion object {
        /**
         * Where a bulwark of [size] can stand on [ship]: the top-left cell of
         * a free square of [size] cells, in the biggest room without a
         * system it fits in (it never stands in the way of a system), or
         * null if it fits nowhere.
         */
        fun bulwarkSpot(ship: Ship, size: Int): RoomPoint? {
            for (room in bulwarkRooms(ship, size).sortedByDescending { it.cellCount }) {
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
