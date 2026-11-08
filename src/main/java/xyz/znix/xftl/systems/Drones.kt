package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.SystemInfo
import xyz.znix.xftl.Translator
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.drones.AbstractDrone
import xyz.znix.xftl.drones.AbstractExternalDrone
import xyz.znix.xftl.drones.BoardingDrone
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.utils.WeaponPowerManager
import xyz.znix.xftl.weapons.DroneBlueprint
import kotlin.math.max

class Drones(blueprint: SystemBlueprint) : MainSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.DRONES

    val drones = ArrayList<DroneInfo?>()

    private val droneLaunchSound by onInit { it.sounds.getSample("droneLaunch") }

    private var playDroneLaunchSound = false

    private lateinit var powerManager: WeaponPowerManager

    // This is kinda horrible, but it's a convenient way to prevent
    // zoltan power from deploying drones automatically.
    private var allowDroneDeploy: DroneInfo? = null

    override fun initialise(ship: Ship) {
        super.initialise(ship)

        // Resize the blueprints list to match the ship
        // On some enemy ships (eg REBEL_FAT) there's a drone system but
        // no number of drone slots - in that case assume it's three, though
        // maybe there's no limit on it? TODO find out.
        val droneSlots = ship.droneSlots ?: 3
        while (drones.size < droneSlots)
            drones.add(null)

        powerManager = WeaponPowerManager(this, DronePowerAccess())
    }

    override fun powerStateChanged() {
        super.powerStateChanged()

        // Don't adjust the power if the drones haven't yet been loaded.
        if (ship.sys.isCurrentlyLoadingSave)
            return

        powerManager.powerStateChanged()
    }

    // The items it had on come back after a repair, rather than a level
    override val restoresByLevel: Boolean get() = false

    override fun powerLimitChanged() {
        super.powerLimitChanged()
        // (not before the items are set up)
        if (this::powerManager.isInitialized)
            powerManager.restorePower()
    }

    override fun increasePower() {
        powerManager.increasePower()
    }

    override fun decreasePower() {
        powerManager.decreasePower()
    }

    override fun update(dt: Float) {
        super.update(dt)

        for (info in drones) {
            if (info == null)
                continue

            for (copy in info.liveCopies)
                copy.update(dt)

            val oldCooldown = info.cooldown
            if (oldCooldown != null) {
                val newCooldown = oldCooldown - dt
                if (newCooldown <= 0) {
                    info.cooldown = null
                } else {
                    info.cooldown = newCooldown
                }
            }
        }

        // If a drone has turned itself on or off, find out why and fix it.
        if (powerSelected != powerManager.currentPower) {
            powerStateChanged()
        }

        if (playDroneLaunchSound) {
            droneLaunchSound.play()
            playDroneLaunchSound = false
        }
    }

    /**
     * Attempt to set the power status of the drone in the nth slot,
     * considering the system and reactor power limits.
     *
     * @return True if the given change was made.
     */
    fun setDronePower(slot: Int, power: Boolean): Boolean {
        allowDroneDeploy = drones[slot]
        return powerManager.setItemPower(slot, power)
    }

    private fun setDronePowerInternal(slot: Int, power: Boolean) {
        val info = drones[slot] ?: return

        // If we're allowed to deploy a drone, it only counts once
        val allowDeploy = allowDroneDeploy == info
        allowDroneDeploy = null

        if (!power) {
            for (copy in info.liveCopies)
                copy.isPowered = false
            return
        }

        // If the drone is already powered, with all its copies out, nothing
        // needs to be done.
        val bodies = bodiesFor(info)
        if (info.liveCopies.size >= bodies && info.liveCopies.all { it.isPowered })
            return

        // If this drone attacks hostile ships but there isn't
        // one present, we can't turn it on.
        val hostileShip = ship.sys.getEnemyOf(ship)
        if (info.type.type.needsHostileShip && hostileShip == null) {
            return
        }

        // Check the drone isn't on it's destroy cooldown
        if (info.cooldown != null) {
            return
        }

        // Launch the copies that aren't out (all of them, the first time;
        // after that, the ones it's lost), a drone part each, if we're
        // allowed to: zoltan power doesn't deploy drones.
        // Boarding drones aren't launched while every ship they could board
        // has a super shield up, which would destroy them: the slot stays
        // off (unless some are out already) until one's down
        val boardingBlocked = info.type.type == DroneBlueprint.DroneType.BOARDER &&
                BoardingDrone.boardableShips(ship).isEmpty()

        if (allowDeploy && !boardingBlocked) {
            // (a bulwark's one drone takes as many parts as it's worth)
            val parts = info.copies.size / bodies
            for (index in 0 until bodies) {
                if (info.copies[index] != null)
                    continue

                // Consume a drone part.
                if (!ship.sys.debugFlags.infiniteDrones.set) {
                    if (ship.dronesCount < parts)
                        break
                    ship.dronesCount -= parts
                }

                val copy = info.type.makeInstance()
                copy.copyIndex = index
                info.copies[index] = copy
                copy.init(ship)
                copy.onLaunched()

                // We should only play the launch sound when unpaused, both
                // to match vanilla and to only play the sound once if multiple
                // drones are selected in the same update.
                playDroneLaunchSound = true
            }
        }

        for (copy in info.liveCopies)
            copy.isPowered = true
    }

    /**
     * How many drones [info] launches: one for each copy (see
     * DroneBlueprint.combinedCount), except for an enemy's combined
     * anti-personnel drone, which is one big bulwark (see AntiPersonnelDrone).
     */
    private fun bodiesFor(info: DroneInfo): Int = when {
        info.type.type == DroneBlueprint.DroneType.BATTLE && info.type.isCombined && ship != ship.sys.player -> 1
        else -> info.copies.size
    }

    override fun onJump() {
        super.onJump()

        // The drones flying outside, and boarding drones, are left behind
        // by the jump. The Drone Recovery Arm gets back the parts of those
        // that weren't destroyed.
        if (!ship.hasAugment(AugmentBlueprint.DRONE_RECOVERY_ARM))
            return
        for (info in drones) {
            for (copy in info?.liveCopies ?: continue) {
                if (copy is AbstractExternalDrone || copy is BoardingDrone)
                    ship.dronesCount++
            }
        }
    }

    fun enemyShipUpdated() {
        for (drone in drones) {
            for (copy in drone?.liveCopies ?: continue)
                copy.onEnemyShipUpdated()
        }
    }

    fun getDroneForcedPower(slot: Int): Int {
        return powerManager.getForcedPower(slot)
    }

    // The drone instances are all serialised separate, but we store the blueprints here.
    override fun saveSystem(elem: Element, refs: ObjectRefs) {
        for (drone in drones) {
            if (drone == null)
                continue

            val droneElem = Element("drone")
            SaveUtil.addAttr(droneElem, "type", drone.type.name)
            elem.addContent(droneElem)
        }
    }

    override fun loadSystem(elem: Element, refs: RefLoader) {
        for (droneElem in elem.getChildren("drone")) {
            val type = SaveUtil.getAttr(droneElem, "type")
            val blueprint = ship.sys.blueprintManager[type] as DroneBlueprint

            // If there aren't enough slots, make a new one
            // This is required for the flagship at least, and any modded ships
            // which have more drones than drone slots.
            // Note that loadDefaultContents does the same thing, adding slots if required.
            var firstSlot = drones.indexOf(null)
            if (firstSlot == -1) {
                firstSlot = drones.size
                drones.add(null)
            }
            drones[firstSlot] = DroneInfo(blueprint, null)
        }
    }

    class DroneInfo(val type: DroneBlueprint, instance: AbstractDrone? = null) {
        /**
         * Its drones: one for each copy (see [DroneBlueprint.combinedCount],
         * one for a normal drone), or null for each that isn't out, either
         * not launched yet, or lost since. Each copy is an instance of
         * [type], with its [AbstractDrone.copyIndex].
         */
        val copies: Array<AbstractDrone?> = arrayOfNulls<AbstractDrone>(type.combinedCount).also { it[0] = instance }

        /** Its drones that are out. */
        val liveCopies: List<AbstractDrone> get() = copies.filterNotNull()

        /**
         * Its first drone that's out, or null if none is: for a normal drone,
         * its drone. Whether it's powered goes for all of them.
         */
        val instance: AbstractDrone? get() = copies.firstOrNull { it != null }

        /** Whether [drone] is one of its copies. */
        fun holds(drone: AbstractDrone): Boolean = copies.any { it === drone }

        /** Take [drone] out of its copies, when it's lost or removed. */
        fun remove(drone: AbstractDrone) {
            val index = copies.indexOfFirst { it === drone }
            if (index != -1)
                copies[index] = null
        }

        /** Put [drone] in as its copy [index], as it's loaded. */
        fun place(drone: AbstractDrone, index: Int) {
            require(copies[index] == null)
            copies[index] = drone
        }

        /** Remove all its drones (see [AbstractDrone.removeInstance]). */
        fun removeAll() {
            for (copy in liveCopies)
                copy.removeInstance()
        }

        /**
         * The cooldown (counting down) for how long until the player can
         * deploy this drone again, after it was destroyed.
         */
        var cooldown: Float? = null
    }

    private inner class DronePowerAccess : WeaponPowerManager.ItemAccess {
        // The flagship has more drones than slots, so slots are added either
        // during deserialisation or in loadDefaultContents, and it's critical that this
        // field doesn't change. Thus we lie and say there's more drones than
        // there actually are, so it's OK to add them later.
        override val count: Int = max(drones.size, 8)

        override fun hasItem(slot: Int): Boolean {
            return drones.getOrNull(slot) != null
        }

        override fun getItemPowerDraw(slot: Int): Int {
            return drones.getOrNull(slot)?.type?.power ?: 0
        }

        override fun isItemPowered(slot: Int): Boolean {
            return drones.getOrNull(slot)?.instance?.isPowered ?: false
        }

        override fun setItemPowered(slot: Int, powered: Boolean) {
            setDronePowerInternal(slot, powered)
        }

        override fun setSystemPower(level: Int): Boolean {
            return this@Drones.setSystemPower(level)
        }
    }

    companion object {
        val INFO: SystemInfo = DronesInfo
    }
}

private object DronesInfo : SystemInfo("drones") {
    override val canBeManned: Boolean get() = false

    override fun create(blueprint: SystemBlueprint) = Drones(blueprint)

    override fun getLevelName(level: Int, translator: Translator): String {
        return translator["system_power"]
    }
}
