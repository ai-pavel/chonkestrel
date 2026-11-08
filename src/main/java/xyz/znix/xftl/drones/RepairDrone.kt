package xyz.znix.xftl.drones

import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.weapons.DroneBlueprint

class RepairDrone(type: DroneBlueprint) : AbstractIndoorsDrone(type) {
    override val occupancySlotType get() = AbstractCrew.SlotType.CREW

    override val pawnCodename: String get() = "repair"

    override fun init(ownerShip: Ship) {
        super.init(ownerShip)
        val dronesRoom = ownerShip.rooms.find { it.system is Drones }
        requireNotNull(dronesRoom) { "Owner ship '${ownerShip.name}' spawned a ship repair drone without a drones system" }

        // Don't spawn in a pawn if that's already been done by deserialisation.
        if (pawn == null) {
            spawn(dronesRoom)
        }
    }

    override fun updatePawn(dt: Float) {
        // Check if we're idle and should scan for damaged systems.
        if (pawn!!.pathingTarget != null)
            return
        // It can't fight, so it stays out of the rooms with boarders in them:
        // its repairs there would only be undone, while it stood there
        if (needsWork(pawn!!.room) && !hasHostiles(pawn!!.room))
            return

        // (one the other copies of a combined drone aren't mending, first)
        for (room in ship.rooms.sortedBy { claimedBySibling(it) }) {
            if (!needsWork(room) || hasHostiles(room) || room == pawn!!.room)
                continue

            // Try to path to this room. If we can then stop,
            // otherwise continue to the next room.
            if (pawn!!.setTargetRoom(room))
                return
        }

        returnHome()
    }

    // Whether a room has something to fix: a damaged system, a fire or a
    // hull breach (it puts out fires and patches breaches before the system,
    // as crew do)
    private fun needsWork(room: Room): Boolean =
        room.system?.damaged == true || room.fires.any { it != null } || room.breaches.any { it != null }

    // Whether there's anyone in a room fighting this ship's crew
    private fun hasHostiles(room: Room): Boolean =
        room.crew.any { it.mode != AbstractCrew.SlotType.CREW && it.currentAction != AbstractCrew.Action.DYING }

    override fun drawPawn() {
        // (its glow's drawn by the pawn, see Pawn.drawImage)
    }

    override fun makePawn(room: Room): Pawn = RepairPawn(room)

    private inner class RepairPawn(room: Room) : AbstractIndoorsDrone.Pawn(room) {
        override val canRepair: Boolean get() = true
        override val repairSpeed: Float get() = 2f
    }
}
