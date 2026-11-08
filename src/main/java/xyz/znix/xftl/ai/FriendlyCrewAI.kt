package xyz.znix.xftl.ai

import org.jdom2.Element
import xyz.znix.xftl.XftlDebug

import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.drones.AbstractIndoorsDrone
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.layout.FireInstance
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.shipgen.ShipScaling
import xyz.znix.xftl.systems.*

/**
 * This AI controls the crew that are friendly to the ship.
 *
 * Friendly doesn't mean they're friendly to the player, this is used for
 * enemy crew aboard the enemy ship.
 *
 * Note this fairly closely matches the structure of vanilla's AI system,
 * as it's important that we match the subtle behaviour properly since edge
 * cases are important for tactics.
 */
class FriendlyCrewAI(private val ship: Ship) {
    // Set by ShipGenerator
    var initialCrewCount = 0

    /**
     * Set while hurt crew can't get to a medbay (it's broken, say): the
     * medbay's "requested", as in vanilla, so it's repaired sooner (see
     * AIUtils.systemPriority).
     */
    var medbayRequested: Boolean = false
        private set

    // Don't teleport crew over more than 3 times
    private var teleportCount = 0

    private val aiCrew = ArrayList<AbstractCrew>()

    private var assignments = HashMap<AbstractCrew, AITask>()
    private var newAssignments = HashMap<AbstractCrew, AITask>()

    // Why the boarders haven't been sent yet, and for how long (in game
    // time) that's been the reason, to log boarders that never go
    private var boardingWait: String? = null
    private var boardingWaitTime = 0f
    private var boardingWaitLogged = false

    // The boarders who've been left behind and logged, to only log them once
    private val leftBehindLogged = HashSet<AbstractCrew>()

    // The automatic playtest runs the player's crew with this AI too, a bit
    // more as a player would
    private val autoplayPlayer: Boolean get() = XftlDebug.autoplay && ship.isPlayerShip

    /**
     * The rooms the automatic playtest is venting (see
     * AutoplayDirector.ventRooms), which the crew keep out of: no tasks in
     * them, and those in them leave.
     */
    var keepOut: Set<Room> = emptySet()

    fun update(dt: Float = 0f) {
        if (initialCrewCount == UNKNOWN_CREW_COUNT)
            initialCrewCount = ship.crew.count { it is LivingCrew && it.ownerShip == ship }

        // Update the list of AI-controllable crew
        aiCrew.clear()
        for (crew in ship.crew) {
            // TODO sort out the drone AI
            if (crew is AbstractIndoorsDrone.Pawn)
                continue

            if (crew.mode != AbstractCrew.SlotType.CREW)
                continue

            // Don't set tasks for dying crew. Vanilla has a separate task
            // for dying, so ignoring them does the same thing.
            if (crew.currentAction == AbstractCrew.Action.DYING)
                continue

            // We have to check playerControllable for mind-controlled crew.
            // In the automatic playtest, the AI runs the player's crew too
            // (fighting boarders, repairing, putting out fires), so there's
            // no one to click on them.
            if (crew.playerControllable && !autoplayPlayer)
                continue

            // Ignore crew in the flagship's artillery rooms
            // We assume that any crew can path to any room, and these crew
            // violate that assumption, which can lead to tasks getting locked up.
            if (isRoomDisabled(crew.room))
                continue

            aiCrew += crew
        }

        // Stop here if there's no crew to control, to save a tiny bit of CPU.
        if (aiCrew.isEmpty())
            return

        // Build a new list of tasks each update, rather than having one persistent list.
        // This means that an arbitrary number of crew can be assigned to the same task,
        // if you can find a way to get them into that state.
        // See the original XFTL's doc/crew-ai for more information.

        val tasks = ArrayList<AITask>()

        for (room in ship.rooms) {
            if (isRoomDisabled(room) || room in keepOut)
                continue

            val sys = room.system
            if (sys != null) {
                // Create manning tasks for all systems, even those where crew won't
                // do anything - those just have very low priorities, and serve
                // to disperse thee crew throughout the ship.
                tasks += ManningTask(this, room)

                if (sys.damaged) {
                    tasks += RepairTask(this, room)
                    // In the automatic playtest, two of the player's crew
                    // repair the oxygen, so it's fixed before the air runs out
                    if (sys is Oxygen && autoplayPlayer)
                        tasks += RepairTask(this, room)
                }
            }

            if (room.fires.any { it != null } && room.oxygen > FireInstance.OXYGEN_CUTOFF) {
                tasks += ExtinguishFireTask(this, room)
                tasks += ExtinguishFireTask(this, room)
            }

            if (room.breaches.any { it != null }) {
                tasks += RepairBreachTask(this, room)
            }

            // One task for each intruder (two in the automatic playtest, so
            // its crew outnumber the boarders rather than fighting them one
            // on one, as a player would)
            for (crew in room.crew) {
                if (crew.mode != AbstractCrew.SlotType.INTRUDER)
                    continue

                tasks += CombatTask(this, room)
                if (autoplayPlayer)
                    tasks += CombatTask(this, room)
            }
        }

        // Add tasks for teleporting, but don't teleport mind-controlled enemy
        // crew out in an attempt to board the enemy ship.
        val teleporter = ship.teleporter
        var teleportSend = false
        var teleportForceRecv = false
        // (the Zoltan Shield Bypass teleports through a super shield)
        val bypass = ship.hasAugment(AugmentBlueprint.ZOLTAN_BYPASS)
        if (teleporter != null && !ship.isPlayerShip && (bypass || ship.sys.getEnemyOf(ship)?.superShield == 0)) {
            // Don't create more tasks than the teleporter can fit.
            var toSend = getNumMoreBoarders().coerceAtMost(teleporter.room!!.cellCount)

            // Don't teleport crew over more than three times, or more for
            // ships boarding a bigger player ship, which have bigger
            // boarding parties to get across.
            val maxTeleports = 3 * ShipScaling.boardingMultiplier(ship.sys, ship)
            if (teleportCount >= maxTeleports && toSend > 0 && !ship.sys.debugFlags.noTeleportLimit.set) {
                toSend = 0
            }

            for (i in 1..toSend) {
                tasks += TeleportingTask(this, teleporter.room!!)
            }
            when {
                toSend < 0 -> teleportForceRecv = true
                toSend > 0 -> teleportSend = true
            }

            val escapeTimer = ship.escapeTimer
            if (escapeTimer != null && escapeTimer < 15f) {
                teleportForceRecv = true
            }

            if (ship.systems.count { it.broken } >= 3) {
                teleportForceRecv = true
            }
        }

        // Figure out if the ship is calm, used for healing crew
        // at nearly-full health.
        val isShipCalm = tasks.none {
            when (it) {
                is CombatTask -> true
                is RepairTask -> true
                is RepairBreachTask -> true
                is ExtinguishFireTask -> true
                else -> false
            }
        }

        // Set again below if any hurt crew can't get to a medbay
        var hurtCrewWaiting = false

        // Remove the tasks currently being performed, so we don't
        // assign the same task to multiple crew (unless multiple
        // of it are added to the tasks list).
        // At the same time, this removes any missing crew from
        // the assignments map.
        for (crew in aiCrew) {
            // Tasks can automatically reassign their crew, usually back to
            // idle. This matches the CrewAI::UpdateCrewMember logic. Idle
            // crew (with no task) still go to the medbay when they're hurt.
            var task: AITask? = assignments[crew]?.nextTask(crew)
            // (not in a room that's being vented)
            if (task != null && task.room in keepOut)
                task = null

            // If we're low on health, make our way to a medbay (bigger
            // ships can have several). In the automatic playtest, the
            // player's crew go sooner, at half health, as a player would,
            // rather than fighting boarders to the death.
            val healingThreshold = when {
                isShipCalm -> 0.99f
                autoplayPlayer -> AUTOPLAY_HEALING_THRESHOLD
                else -> 0.25f
            }
            // Those repairing the oxygen in the playtest keep at it, even if
            // it kills them, rather than going back and forth to the medbay
            // while everyone suffocates
            val repairingOxygen = autoplayPlayer && task is RepairTask && task.room.system is Oxygen
            if (
                task !is HealingTask && !repairingOxygen &&
                crew is LivingCrew &&
                crew.health / crew.maxHealth < healingThreshold
            ) {
                for (medbay in ship.medbaySystems) {
                    if (medbay.powerSelected <= 0 || AIUtils.isDangerous(crew, medbay.room!!))
                        continue

                    // Try to start pathfinding there immediately, and if
                    // there's no space, try the next one, or keep our
                    // current task so we can continue doing something useful
                    // while we wait for whoever is in there now to heal up
                    // and leave.
                    if (crew.setTargetRoom(medbay.room!!)) {
                        task = HealingTask(this, medbay.room!!)
                        break
                    }
                }

                // None could be used (such as one that's broken): it's
                // "requested", so it's repaired sooner
                if (task !is HealingTask)
                    hurtCrewWaiting = true
            }

            if (task == null)
                continue
            tasks.remove(task)
            newAssignments[crew] = task
        }
        medbayRequested = hurtCrewWaiting

        // Re-use the previous assignments hashmap, being paranoid about the GC.
        val oldAssignments = assignments
        assignments = newAssignments
        oldAssignments.clear()
        newAssignments = oldAssignments

        // Assign each task, if possible.
        for (task in tasks) {
            assignTask(task)
        }

        // Those left without a task in a room that's being vented leave it,
        // for the nearest room that isn't
        if (keepOut.isNotEmpty()) {
            for (crew in aiCrew) {
                if (crew in assignments || crew.room !in keepOut)
                    continue
                if (crew.pathingTarget?.room?.let { it !in keepOut } == true)
                    continue
                val safe = ship.rooms.filter { it !in keepOut && !isRoomDisabled(it) }
                    .minByOrNull { it.pixelCentre.distToSq(crew.room.pixelCentre) } ?: continue
                crew.setTargetRoom(safe)
            }
        }

        // Make sure all the crew are pathfinding to their target room.
        for ((crew, task) in assignments) {
            if (crew.room == task.room)
                continue

            if (crew.pathingTarget?.room == task.room)
                continue

            crew.setTargetRoom(task.room)
        }

        // If our boarders on the enemy ship are low on health, teleport them back.
        // Do this first, so receiving low-health crew takes priority over
        // sending more when the teleporter comes off cooldown.
        doTeleportRecv(teleportForceRecv)

        // If the crew are ready, activate the teleporter.
        val waitingFor = when (teleportSend) {
            true -> doTeleportSend()
            false -> null
        }
        updateBoardingWait(waitingFor, dt)
    }

    /**
     * Log it when the boarders have waited for the same reason for a
     * while, as they sometimes stand in the teleporter and never go.
     */
    private fun updateBoardingWait(reason: String?, dt: Float) {
        if (reason != boardingWait) {
            boardingWait = reason
            boardingWaitTime = 0f
            boardingWaitLogged = false
            return
        }
        if (reason == null)
            return

        boardingWaitTime += dt
        if (!boardingWaitLogged && boardingWaitTime >= BOARDING_WAIT_LOG_TIME) {
            boardingWaitLogged = true
            println("Boarding: ${ship.type.name}'s boarders have waited ${BOARDING_WAIT_LOG_TIME.toInt()}s, as $reason")
        }
    }

    /**
     * Returns true if a given crewmember will be assigned to a manning task
     * in the next update. This is used to detect if two crew are both manning
     * a system.
     *
     * Since it's called while [newAssignments] is being written, it needs to
     * use that to check, otherwise all crew would have their manning tasks
     * cancelled.
     */
    fun hasAlreadyAssignedManning(crew: AbstractCrew): Boolean {
        return newAssignments[crew] is ManningTask
    }

    private fun assignTask(task: AITask) {
        // Matches up to CrewAI::AttemptToAssign in FTL.
        // I really don't like copying the structure of FTL's executable, but
        // it's hard not to if we're going to get the exact same behaviour.

        // Find the most suitable crewmember for this task.
        // This is based on the priority of the crewmember's current task,
        // so we pick the crewmember who is currently doing the least
        // important task.
        // This also accounts for the danger a crewmember faces in a room.
        var bestCrew: AbstractCrew? = null
        var bestCrewPriority = -1

        for (crew in aiCrew) {
            // Exclude crew that can't do a task
            if (!task.isSuitable(crew))
                continue

            val currentTask = assignments[crew]

            // If a crewmember is already performing this task, don't
            // re-assign them. This lets multiple crew perform identical
            // tasks, for example fighting fires has two tasks.
            val currentPriority: Int
            if (currentTask != null) {
                currentPriority = currentTask.priorityFor(crew)
            } else if (AIUtils.isDangerous(crew, crew.room)) {
                // The room danger does affect idle crew
                currentPriority = 100
            } else {
                currentPriority = 1000
            }

            val newPriority = task.priorityFor(crew)

            // Skip crew who are doing something more important.
            // Don't reject crew with an equal priority here, as that'd
            // change the instant assignment stuff.
            if (currentPriority < newPriority)
                continue

            // TODO instant assignment

            // At this point, ignore crew whose current task is the same
            // priority as the new task, to avoid crew ping-ponging between
            // equally important tasks.
            if (currentPriority == newPriority)
                continue

            // Keep track of the most suitable crew, picking the first
            // one if there's a tie. For boarding and manning, though, a tie
            // goes to crew already in the room: enemy ships boarding a bigger
            // player ship get extra crew, who fill most of the ship from the
            // start, including the teleporter, so boarders from elsewhere
            // might never get a place on it; and someone walking over to a
            // station would only find it taken by whoever's there already.
            val alreadyThere = (task is TeleportingTask || task is ManningTask) &&
                    crew.room == task.room && bestCrew?.room != task.room
            if (currentPriority > bestCrewPriority || (currentPriority == bestCrewPriority && alreadyThere)) {
                bestCrewPriority = currentPriority
                bestCrew = crew
            }
        }

        // Couldn't find a suitable crewmember?
        if (bestCrew == null)
            return

        // Assign this task to the chosen crew.
        assignments[bestCrew] = task
    }

    private fun isRoomDisabled(room: Room): Boolean {
        // Don't set any tasks in inaccessible rooms, since crew can't go there.
        // This prevents flagship crew from getting stuck when sent to the artillery rooms.
        return room.doors.isEmpty()
    }

    private fun getNumMoreBoarders(): Int {
        // See the original XFTL's doc/ship-ai for information about this.

        // Find the number of boarders to send, from our initial crew.
        val sendTotal = when (ship.type.boardingStrategy) {
            BoardingStrategy.NONE -> return 0
            BoardingStrategy.SABOTAGE -> initialCrewCount / 2
            BoardingStrategy.INVASION -> initialCrewCount * 3 / 4
        }

        // Use this to figure out how many crew to keep behind.
        val toKeep = initialCrewCount - sendTotal

        // Now use this to calculate how many crew we can safely send.
        // This can be negative if we should recall some boarders, due to
        // crew on our ship dying.
        return aiCrew.size - toKeep
    }

    /**
     * Send the boarders, if they're all in the teleporter. Returns why
     * they're still waiting (for the log), or null if they aren't.
     */
    private fun doTeleportSend(): String? {
        val teleporter = ship.teleporter!!
        val enemy = ship.sys.getEnemyOf(ship) ?: return "there's no ship to board"

        // Check that there aren't any crew still pathing to the teleporter
        for (crew in aiCrew) {
            // Stop if any crew are currently being teleported
            if (crew.currentAction == AbstractCrew.Action.TELEPORTING)
                return null

            if (assignments[crew] !is TeleportingTask)
                continue

            if (crew.standingPosition?.room == teleporter.room)
                continue

            // Go without anyone who can't get there, such as when other
            // crew are in the way, taking up its places. Otherwise the rest
            // would wait for them for ever.
            if (crew.pathingTarget?.room != teleporter.room) {
                if (leftBehindLogged.add(crew)) {
                    val inTeleporter = teleporter.room!!.crew.joinToString { it.logName }
                    println("Boarding: ${ship.type.name}'s boarders are going without ${crew.logName}, " +
                            "who can't get into the teleporter (room ${teleporter.room!!.id}, with $inTeleporter in it)")
                }
                continue
            }

            // Don't teleport until all the crew are ready
            return "${crew.logName} hasn't reached the teleporter (room ${teleporter.room!!.id}): " +
                    "they're in room ${crew.room.id}, ${crew.currentAction.name.lowercase()}"
        }

        teleporter.selectTeleportAction(true, enemy.rooms.random())

        // The teleporter sends them on its next update, unless something stops it
        return teleporter.sendProblem
    }

    private fun doTeleportRecv(teleportForceRecv: Boolean) {
        // See the original XFTL's doc/ship-ai for this logic

        val teleporter = ship.teleporter ?: return

        val enemy = ship.sys.getEnemyOf(ship) ?: return
        // Only this ship's own boarders: with several enemy ships, each
        // would otherwise go for the same one, which only its own ship's
        // teleporter can take home
        val boarders = enemy.crew.filterIsInstance<LivingCrew>()
            .filter { it.mode == AbstractCrew.SlotType.INTRUDER && it.ownerShip == ship }

        val minHealthFraction = when (ship.type.boardingStrategy) {
            BoardingStrategy.INVASION -> -1f // Never teleport back
            else -> 0.25f
        }

        for (crew in boarders) {
            val healthFraction = crew.health / crew.maxHealth

            if (healthFraction > minHealthFraction && !teleportForceRecv)
                continue

            // Teleport the crew from this room
            teleporter.selectTeleportAction(false, crew.room)
            return
        }
    }

    fun onCrewTeleportedOut() {
        // Only increment teleportCount when someone has actually been teleported.
        // If we did this before calling selectTeleportAction, anything that blocked
        // teleporting (eg cloaking, super shield) would increment this every frame
        // until we hit the limit.
        teleportCount++

        if (boardingWaitLogged)
            println("Boarding: ${ship.type.name}'s boarders went after all")
    }

    /**
     * Save what the AI has to remember through a reload: how many crew the
     * ship started with, and how many times it's sent boarders, so it
     * doesn't board again past its limit.
     */
    fun saveToXML(elem: Element) {
        SaveUtil.addTagInt(elem, "aiInitialCrew", initialCrewCount, 0)
        SaveUtil.addTagInt(elem, "aiTeleportCount", teleportCount, 0)
    }

    fun loadFromXML(elem: Element) {
        // (an older save without it: the crew it has now, see update)
        initialCrewCount = SaveUtil.getOptionalTagInt(elem, "aiInitialCrew") ?: UNKNOWN_CREW_COUNT
        teleportCount = SaveUtil.getOptionalTagInt(elem, "aiTeleportCount") ?: 0
    }

    companion object {
        // initialCrewCount in a save from before it was saved
        private const val UNKNOWN_CREW_COUNT = -1

        // How long the boarders wait for one reason before it's logged
        private const val BOARDING_WAIT_LOG_TIME = 5f

        // How hurt the player's crew get before going to a medbay, in the
        // automatic playtest (otherwise it's the vanilla quarter)
        private const val AUTOPLAY_HEALING_THRESHOLD = 0.5f
    }

    enum class BoardingStrategy {
        NONE,
        SABOTAGE, // Most ships
        INVASION, // Flagship
        ;
    }
}

abstract class AITask(val ai: FriendlyCrewAI, val room: Room) {
    /**
     * Find this task's priority.
     */
    open fun priorityFor(crew: AbstractCrew): Int {
        if (AIUtils.isDangerous(crew, room))
            return 100

        return priorityWithoutDanger(crew)
    }

    // Doesn't include the danger check
    protected abstract fun priorityWithoutDanger(crew: AbstractCrew): Int

    open fun nextTask(crew: AbstractCrew): AITask? {
        if (crew.currentAction.isIdle) {
            return null
        }

        return this
    }

    // Have one equals/hashCode for all our subclasses

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AITask) return false
        if (other.javaClass != javaClass) return false

        return room == other.room
    }

    override fun hashCode(): Int {
        return room.hashCode() xor javaClass.hashCode()
    }

    abstract fun isSuitable(crew: AbstractCrew): Boolean

    override fun toString(): String {
        return "${javaClass.simpleName}(id=${room.id},sys=${room.system?.codename})"
    }
}

class RepairTask(ai: FriendlyCrewAI, room: Room) : AITask(ai, room) {
    override fun priorityFor(crew: AbstractCrew): Int {
        // In the automatic playtest, the player's crew repair the oxygen
        // even when the air's so thin it's killing them, for the others' sake
        if (room.system is Oxygen && XftlDebug.autoplay && room.ship.isPlayerShip)
            return priorityWithoutDanger(crew)
        return super.priorityFor(crew)
    }

    override fun priorityWithoutDanger(crew: AbstractCrew): Int {
        // Oxygen gets a very high priority when it's low
        if (room.system is Oxygen && room.ship.averageOxygen < 0.25f)
            return 0

        // Shields is special
        if (room.system is Shields)
            return 1

        return AIUtils.systemPriority(room.system!!, ai.medbayRequested) + 48
    }

    override fun isSuitable(crew: AbstractCrew): Boolean {
        return crew.canRepair
    }
}

class RepairBreachTask(ai: FriendlyCrewAI, room: Room) : AITask(ai, room) {
    override fun priorityWithoutDanger(crew: AbstractCrew): Int {
        return AIUtils.systemPriority(room.system) + 32
    }

    override fun isSuitable(crew: AbstractCrew): Boolean {
        return crew.canRepair
    }
}

class ExtinguishFireTask(ai: FriendlyCrewAI, room: Room) : AITask(ai, room) {
    override fun priorityWithoutDanger(crew: AbstractCrew): Int {
        return AIUtils.systemPriority(room.system) + 16
    }

    override fun isSuitable(crew: AbstractCrew): Boolean {
        return crew.canRepair
    }
}

class ManningTask(ai: FriendlyCrewAI, room: Room) : AITask(ai, room) {
    override fun priorityWithoutDanger(crew: AbstractCrew): Int {
        val isHard = room.ship.sys.difficulty == Difficulty.HARD

        // See the original XFTL's doc/crew-ai
        val systemPriority = when (room.system) {
            is Piloting -> 64
            is Shields -> if (isHard) 67 else 65
            is Engines -> if (isHard) 65 else 67
            is Weapons, is Artillery -> 66
            is Oxygen -> 68
            is Sensors -> 69
            is Doors, is Cloaking -> 70
            is Teleporter -> 71
            is Medbay -> 72
            else -> 1000
        }

        return systemPriority
    }

    override fun nextTask(crew: AbstractCrew): AITask? {
        if (!crew.currentAction.isIdle)
            return this

        // Check if any other crew in the room are assigned to manning.
        for (otherCrew in room.crew) {
            if (ai.hasAlreadyAssignedManning(otherCrew)) {
                return null
            }
        }

        return this
    }

    override fun isSuitable(crew: AbstractCrew): Boolean {
        return crew.canRepair
    }
}

class CombatTask(ai: FriendlyCrewAI, room: Room) : AITask(ai, room) {
    override fun priorityWithoutDanger(crew: AbstractCrew): Int {
        return AIUtils.systemPriority(room.system) + 16 + AIUtils.badFighterPenalty(crew)
    }

    override fun isSuitable(crew: AbstractCrew): Boolean {
        return crew.canFight
    }
}

class HealingTask(ai: FriendlyCrewAI, room: Room) : AITask(ai, room) {
    override fun priorityWithoutDanger(crew: AbstractCrew): Int {
        return 0
    }

    override fun isSuitable(crew: AbstractCrew): Boolean {
        return crew is LivingCrew
    }

    override fun nextTask(crew: AbstractCrew): AITask? {
        val medbay = room.system as Medbay

        if (medbay.isHackActive || crew.health == crew.maxHealth || medbay.powerSelected == 0)
            return null

        return this
    }
}

class TeleportingTask(ai: FriendlyCrewAI, room: Room) : AITask(ai, room) {
    override fun priorityWithoutDanger(crew: AbstractCrew): Int {
        // There's an urgent teleporting flag in vanilla which forces
        // a priority of 0, but it seems to never be set.
        return when {
            crew.health / crew.maxHealth < 0.5f -> 100
            room.ship.teleporter!!.ionTimer > 0f -> 65
            else -> 32
        } + AIUtils.badFighterPenalty(crew)
    }

    override fun isSuitable(crew: AbstractCrew): Boolean {
        return crew is LivingCrew && crew.canFight
    }
}
