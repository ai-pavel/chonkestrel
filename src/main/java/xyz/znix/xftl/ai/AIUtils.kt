package xyz.znix.xftl.ai

import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.*

object AIUtils {
    /**
     * How much less keen crew who are bad at fighting (who hit softer than
     * a human, as the Engi do) are to fight or board: they're picked last,
     * leaving it to better fighters (a lower priority's more important).
     */
    fun badFighterPenalty(crew: AbstractCrew): Int =
        if (crew.attackDamageMult < 1f) BAD_FIGHTER_PENALTY else 0

    private const val BAD_FIGHTER_PENALTY = 16

    /**
     * How soon the crew repair [system] (0 first). [medbayRequested] is set
     * while hurt crew can't get to a medbay (see FriendlyCrewAI), which
     * makes it more urgent.
     */
    fun systemPriority(system: AbstractSystem?, medbayRequested: Boolean = false): Int {
        // Copied from FTL, see the original XFTL's doc/crew-ai
        return when (system) {
            is Shields -> 0
            is Weapons -> 1
            is Clonebay -> 1
            is Medbay -> if (medbayRequested) 1 else 2
            is Artillery -> 2
            is Oxygen -> 3
            is Engines, is Drones, is Piloting -> 4
            is Cloaking, is MindControl, is Hacking -> 5
            is Sensors, is BackupBattery -> 6
            is Doors -> 7
            null -> 8
            else -> 8
        }
    }

    @Suppress("RedundantIf")
    fun isDangerous(crew: AbstractCrew, room: Room): Boolean {
        // As per the original XFTL's doc/crew-ai, the room is dangerous:
        // - If a room is an airlock and an airlock door is open, it's dangerous
        // - If health<25% and oxygen<10% it's dangerous, if we can suffocate (ie, except lanius)
        // - If fireCount>0 and health<20%, it's dangerous unless we're immune to fire
        // - If fireCount>=4 then it's dangerous unless we're immune to fire
        // - This room is the medbay, and it's being hacked

        val healthFraction = crew.health / crew.maxHealth
        if (healthFraction < 0.25f && room.oxygen < 0.10f && crew.suffocationMultiplier > 0f) {
            return true
        }

        // Whether this is required is a bit dubious, as with the exception of
        // boarding onto a player ship with an airlock in a system, this won't
        // change anything - and even then, this behaviour is a bit odd.
        if (room.doors.any { it.open && it.isAirlock }) {
            return true
        }

        // Unless the crew is fireproof, fire is dangerous if we're <20%
        // health, and it's always dangerous if there's four fires in a room.
        val fireCount = room.fires.count { it != null }
        if (fireCount > 0 && crew.fireDamageMult > 0f && (fireCount >= 4 || healthFraction < 0.20f)) {
            return true
        }

        // A hacked medbay is, unsurprisingly, dangerous
        val medbay = room.system as? Medbay
        if (medbay?.isHackActive == true) {
            return true
        }

        return false
    }
}
