package xyz.znix.xftl.ai

import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.systems.Hacking
import xyz.znix.xftl.systems.MindControl
import xyz.znix.xftl.systems.Weapons
import xyz.znix.xftl.systems.Shields
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.IRoomTargetingWeapon
import xyz.znix.xftl.weapons.WeaponSizes
import kotlin.random.Random

/**
 * The overall enemy ship AI.
 *
 * This controls stuff like firing the weapons, activating systems/drones,
 * and other stuff like that.
 *
 * It specifically does not handle crew - see [FriendlyCrewAI] for that.
 */
class ShipAI(val ship: Ship, val player: Ship) {
    private val fireAtChargeLevel = HashMap<AbstractWeaponInstance, Int>()

    // A ship loaded from a save already has its shields in the state they
    // were saved in, so only a new ship gets them charged up
    private var hadFirstUpdate = ship.sys.isCurrentlyLoadingSave

    fun update(dt: Float) {
        // If the ship is dying, don't do anything.
        if (ship.isDead)
            return

        if (!hadFirstUpdate) {
            hadFirstUpdate = true

            // Immediately turn the ship's shields on, so the player can't get a sneaky shot in
            // FIXME do this properly
            for (shields in ship.shieldSystems) {
                for (i in 1..shields.energyLevels) {
                    shields.increasePower()
                    shields.update(10f) // Horrible hack
                }
            }
        }

        // Power up all the systems: the shields, then the weapons, then the
        // rest, so when it's short of power (such as in an ion storm, which
        // halves the reactor), the weapons get it first
        for (shields in ship.shieldSystems) {
            for (i in 1..shields.energyLevels) shields.increasePower()
        }
        powerWeapons()
        for (sys in ship.mainSystems) {
            for (i in 1..sys.energyLevels) sys.increasePower()
        }

        updateWeapons()
        updateDrones()

        // Constantly try to trigger cloaking, it'll be ignored
        // whenever it's unavailable.
        ship.cloaking?.activateCloak()

        // Same for the backup battery.
        for (battery in ship.batterySystems) {
            battery.startBattery()
        }

        updateHacking()

        updateMindControl()

        // Check if we need to escape or surrender
        if (ship.health <= ship.surrenderHealth) {
            // Don't surrender repeatedly
            ship.surrenderHealth = 0

            // The surrender event must exist, it's XML tag did
            // for surrenderHealth to be set. A wingman's is for itself, and
            // the main ship's sometimes for them all (see surrenderOffered).
            ship.sys.surrenderOffered(ship)

            // Use an else-if so we don't try and surrender and escape in
            // the same update cycle.
        } else if (ship.health <= ship.escapeHealth) {
            // Show the event and start the timer running
            ship.escapeHealth = 0
            // A wingman just starts its FTL drive, without the escape
            // event, which is about "the enemy" (the main ship)
            if (ship.sys.enemy == ship)
                ship.sys.shipUI?.showEventDialogue(ship.spec!!.escape!!.resolve(), Random.nextInt())
            ship.escapeTimer = ship.spec!!.escapeTimer
        }

        val escapeTimer = ship.escapeTimer
        if (escapeTimer != null) {
            if (ship.canChargeFTL) {
                // Thick nebulas slow the FTL drive down, as for the player
                var multiplier = ship.sys.currentBeacon.getEnvironment(ship.sys).ftlChargeMultiplier
                // The player's FTL Jammer makes it take twice as long
                val jammer = ship.sys.player.getAugmentValue(AugmentBlueprint.FTL_JAMMER)
                if (jammer > 0f)
                    multiplier /= jammer
                ship.escapeTimer = escapeTimer - dt * multiplier
            }

            // SlickGame will check the escape timer, and remove
            // the ship if it runs out.
        }
    }

    /**
     * Try powering up all the weapons. Do it this way rather than by using
     * weapons.increasePower, since if there's an out-of-ammo missile that'd
     * block any later weapons from turning on. A weapon the reactor's too
     * short for takes power from the other systems (not the shields), the
     * least important first.
     */
    private fun powerWeapons() {
        for ((slot, hp) in ship.hardpoints.withIndex()) {
            val weapon = hp.weapon ?: continue
            val weapons = ship.weaponsFor(slot) ?: continue
            if (weapon.isPowered || !weapon.hasEnoughMissiles)
                continue

            while (!weapons.setWeaponPower(weapon, true)) {
                // Only for want of reactor power, rather than the weapons
                // system being ionised or damaged
                val needed = weapon.type.power
                if (weapons.isPowerLocked || weapons.undamagedEnergy - weapons.powerSelected < needed)
                    break
                val donor = ship.mainSystems.lastOrNull {
                    it !is Shields && it !is Weapons && it.powerSelected > it.forcedPower && !it.isPowerLocked
                } ?: break
                val before = donor.powerSelected
                donor.decreasePower()
                // (Zoltan power, say, can't be taken away)
                if (donor.powerSelected == before)
                    break
            }
        }
    }

    private fun updateWeapons() {
        for ((slot, hp) in ship.hardpoints.withIndex()) {
            val weapon = hp.weapon ?: continue
            if (!weapon.isPowered)
                continue

            if (!weapon.isCharged)
                continue

            // No firing at the player while they're cloaked (what's already
            // on its way still flies on)
            if (player.isCloakActive)
                continue

            if (weapon.maxTotalCharges > 1) {
                // Pick a random level to charge up to
                var fireAt = fireAtChargeLevel[weapon]
                if (fireAt == null) {
                    fireAt = (1..weapon.maxTotalCharges).random()
                    fireAtChargeLevel[weapon] = fireAt
                }

                if (weapon.totalReadyCharges < fireAt)
                    continue

                // Pick a new number of shots next time
                fireAtChargeLevel.remove(weapon)
            }

            if (ship.sys.debugFlags.noEnemyFire.set)
                continue

            // A weapon that helps its own side (a Healing Burst, say) goes
            // where it can, or waits until it can: never at the player's crew
            if (weapon is IRoomTargetingWeapon && isSupportWeapon(weapon.type)) {
                val target = supportTarget(weapon.type) ?: continue
                weapon.fire { target }
                continue
            }

            when (weapon) {
                is IRoomTargetingWeapon -> {
                    val remainingRooms = ArrayList(player.rooms)
                    weapon.fire { pickTarget(remainingRooms) }
                }

                is BeamBlueprint.BeamInstance -> {
                    // A beam that only hurts crew (the anti-bio beam) is
                    // swept through the rooms the crew are in, rather than
                    // between two random rooms
                    val crewOnly = weapon.type.damage <= 0 && weapon.type.personnelDamage > 0
                    val crewRooms = player.rooms.filter { room -> room.crew.any { it.mode == AbstractCrew.SlotType.CREW } }
                    val remainingRooms = ArrayList(if (crewOnly && crewRooms.isNotEmpty()) crewRooms else player.rooms)
                    val startRoom = pickTarget(remainingRooms)
                    val endRoom = pickTarget(remainingRooms)
                    val aim = weapon.buildAim(startRoom, endRoom, false)
                    // A combined beam is that many beams long, as the
                    // player's the only ship to fire at (see WeaponSizes)
                    if (weapon.type.isCombined) {
                        aim.length = WeaponSizes.beamLength(aim.length, weapon.type.combinedCount)
                        aim.updateHitRooms()
                    }

                    // Set the weapon to start firing
                    weapon.fire(aim)

                    // Play the beam sound effect
                    weapon.type.launchSounds?.get()?.play()
                }
            }
        }
    }

    private fun updateDrones() {
        // Ships can have several drones systems (such as the bigger
        // versions of the vanilla ships), so use them all
        for (drones in ship.droneSystems) {
            updateDrones(drones)
        }
    }

    private fun updateDrones(drones: Drones) {
        // Try and deploy all the drones we can, except for boarders
        // when there's an enemy super-shield, as they just explode when
        // they fly into it.
        for ((i, drone) in drones.drones.withIndex()) {
            if (drone == null)
                continue

            if (drone.type.type == DroneBlueprint.DroneType.BOARDER && player.superShield > 0) {
                continue
            }

            drones.setDronePower(i, true)
        }
    }

    // A weapon that does no damage, only heals crew or repairs systems
    private fun isSupportWeapon(type: AbstractWeaponBlueprint): Boolean =
        type.damage <= 0 && (type.personnelDamage < 0 || type.sysDamage < 0) &&
                type.personnelDamage <= 0 && type.sysDamage <= 0

    /**
     * Where a weapon that helps this ship's side should go: for one that
     * heals, the room with the most hurt of its side (its own crew aboard
     * it, or its boarders on the player's ship); for one that repairs, its
     * most damaged system's room. Null if there's nothing for it to do.
     */
    private fun supportTarget(type: AbstractWeaponBlueprint): Room? {
        if (type.personnelDamage < 0) {
            fun hurt(room: Room, mode: AbstractCrew.SlotType) = room.crew.count {
                it.mode == mode && it.currentAction != AbstractCrew.Action.DYING && it.health < it.maxHealth
            }
            val rooms = ship.rooms.map { it to hurt(it, AbstractCrew.SlotType.CREW) } +
                    player.rooms.map { it to hurt(it, AbstractCrew.SlotType.INTRUDER) }
            return rooms.filter { it.second > 0 }.maxByOrNull { it.second }?.first
        }
        return ship.rooms.filter { it.system?.damaged == true }.maxByOrNull { it.system!!.damagedEnergyLevels }
    }

    private fun pickTarget(remainingRooms: ArrayList<Room>): Room {
        // If we run out of rooms, start over
        if (remainingRooms.isEmpty()) {
            remainingRooms.addAll(player.rooms)
        }

        // Enemies shoot at different targets with each shot
        // TODO hard-mode targeting
        val room = remainingRooms.random()
        remainingRooms.remove(room)
        return room
    }

    private fun updateHacking() {
        for (hacking in ship.hackingSystems) {
            updateHacking(hacking)
        }
    }

    private fun updateHacking(hacking: Hacking) {
        // Fire the probe at a random system, if it's not already launched.
        // With several hacking systems, they go for different systems, as
        // they can't hack the same one.
        if (!hacking.droneLaunched) {
            val targets = player.systems.filter { it.hackedBy == null }.ifEmpty { return }
            hacking.selectTarget(targets.random().room!!)
            return
        }

        // Run this all the time and it'll only start one
        // when it's ready.
        hacking.startHackingPulse()
    }

    private fun updateMindControl() {
        for (mindControl in ship.mindControlSystems) {
            updateMindControl(mindControl)
        }
    }

    private fun updateMindControl(mindControl: MindControl) {
        if (!mindControl.ready)
            return

        // Go through all the rooms, and try triggering on them all.
        // The system will ignore calls once it starts, so it won't
        // control multiple crew.
        val shuffled = player.rooms.shuffled()
        for (room in shuffled) {
            mindControl.selectRoom(room)
        }
    }
}
