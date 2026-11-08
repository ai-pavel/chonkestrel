package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Ship
import xyz.znix.xftl.drones.CombatDrone
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.systems.Weapons

abstract class AbstractProjectileWeaponInstance(type: AbstractWeaponBlueprint, ship: Ship) :
    AbstractWeaponInstance(type, ship), IRoomTargetingWeapon {

    // The list of all the rooms to fire at. This is required since artillery
    // weapons and all enemy-fired weapons aim at different rooms for each shot.
    protected val targets = ArrayList<Room>()

    // This isn't derived from the targets list, since after we fire our last
    // shot we have to keep running the firing animation until that ends.
    override var isFiring: Boolean = false

    // The number of shots we've fired on this firing cycle (from when the
    // user or AI selected the weapon to be fired until it stops shooting).
    // This is for charge weapon animations, NOT for chain weapons.
    private var shotsFired: Int = 0

    // This is the value of chainCount before it was incremented as
    // weapon started firing.
    private var firingChainCount: Int = 0

    private var firingAnimationTimer: Float = 0f
    protected var waitingToFireAt: Room? = null

    // For a combined weapon (see WeaponSizes), the rooms its other copies
    // fire at alongside waitingToFireAt, one for each (copy 1 first)
    private val copiesWaitingToFireAt = ArrayList<Room>()

    // How many copies of the weapon fire together: more than one for a
    // combined weapon, but only the one when fired by artillery
    private var copiesFiring: Int = 1

    // To support weapons that fire multiple shots per charge (assuming
    // a mod adds one), we have to divide out the number of shots to get
    // the index of the charge we're currently on.
    private val chargeIndex: Int get() = shotsFired / type.shots

    private val fireAnimationFrame: Int
        get() {
            require(isFiring)

            // firingAnimationTimer resets for each shot, so we have to add
            // an extra offset to get the time in the sequence for charger weapons.
            val time = firingAnimationTimer + chargeIndex * Animations.PROJECTILE_WEAPON_FIRE_TIME

            return animation.fireIndex(time, totalAnimationTime)
        }

    private val totalAnimationTime: Float get() = Animations.PROJECTILE_WEAPON_FIRE_TIME * maxTotalCharges

    protected var entryAngle: Float = 0f

    // Doesn't need to be serialised, as it's set by the weapons or artillery system.
    protected lateinit var projectileSpawnPos: IPoint
    protected lateinit var projectileSpawnChargeOffset: IPoint

    // Where a combined weapon's copies' shots start (copy 0, the one in
    // front, first), as they're drawn beside each other (see
    // WeaponSizes.copyOffsets)
    private var copySpawnPositions: List<IPoint> = emptyList()

    override fun update(dt: Float, chargeTime: Float, canCharge: Boolean) {
        super.update(dt, chargeTime, canCharge)

        // We shouldn't need to check targets.isEmpty here, but do
        // so anyway just in case to avoid getting in a situation where
        // we haven't fired all of our shots.
        if (!isFiring && targets.isEmpty()) {
            return
        }

        // If we're powered off, instantly stop firing.
        if (!isPowered) {
            stopFiring()
            return
        }

        firingAnimationTimer += dt

        // Surprisingly, weapons charge while firing.

        // Calculate the current frame, without taking into account multiple charges.
        // Otherwise all but the first shot would fire instantly.
        val singleChargeFrame = animation.fireIndex(firingAnimationTimer, totalAnimationTime)

        if (waitingToFireAt != null && singleChargeFrame >= animation.fireFrame) {
            fireFrameHit()
            waitingToFireAt = null
            fireCopies()
        }

        if (firingAnimationTimer >= Animations.PROJECTILE_WEAPON_FIRE_TIME) {
            if (targets.isEmpty()) {
                stopFiring()
            } else {
                shotsFired++
                primeShot()
            }
        }
    }

    override fun render(g: Graphics) {
        if (isFiring) {
            val frame = shownSprite(fireAnimationFrame)
            frame.draw(0f, 0f)

            // Keep rendering the chain lights even when firing
            renderChainChargeLights()
        } else {
            super.render(g)
        }
    }

    override fun bindToWeaponsSystem(weapons: Weapons) {
        super.bindToWeaponsSystem(weapons)

        if (!this::projectileSpawnPos.isInitialized) {
            val firePos = animation.firePoint
            projectileSpawnPos = weapons.getProjectileSpawnPos(this, firePos)

            // Find the change in position for each subsequent shot in a charge weapon.
            val offsetFirePos = firePos + ConstPoint(-animation.chargeOffset, 0)
            val offsetSpawn = weapons.getProjectileSpawnPos(this, offsetFirePos)
            projectileSpawnChargeOffset = offsetSpawn - projectileSpawnPos
        }

        // Each copy's shots come out of its own barrel (see barrelOffsets)
        if (type.isCombined) {
            copySpawnPositions = type.barrelOffsets(animation).map { offset ->
                weapons.getProjectileSpawnPos(this, animation.firePoint + offset)
            }
        }
    }

    /**
     * Fire a combined weapon's other copies' shots, at the same time as its
     * main one, each from its own barrel.
     */
    private fun fireCopies() {
        for ((index, room) in copiesWaitingToFireAt.withIndex()) {
            val projectile = buildProjectile(room)
            projectile.entryAngle = entryAngle
            val spawnPos = copySpawnPositions.getOrNull(index + 1) ?: projectileSpawnPos
            val endPos = spawnPos + ship.weaponFireDirection * 5000
            projectile.setInitialPath(spawnPos, endPos)
            ship.projectiles += projectile
        }
        copiesWaitingToFireAt.clear()
    }

    protected open fun fireFrameHit() {
        val projectile = buildProjectile(waitingToFireAt!!)
        projectile.entryAngle = entryAngle
        launchProjectile(projectile)

        // For the chain ion, mark the projectile as doing more damage
        if (type.boost?.type == AbstractWeaponBlueprint.BoostType.DAMAGE) {
            projectile.chainDamage = (type.boost.perShot * firingChainCount).toInt()
        }

        type.launchSounds?.get()?.play()
    }

    protected fun launchProjectile(projectile: AbstractProjectile) {
        // Charge weapons shift a bit on each launch. A combined weapon's
        // main shot comes from the copy in front.
        val basePos = if (copiesFiring > 1) copySpawnPositions.firstOrNull() ?: projectileSpawnPos else projectileSpawnPos
        val spawnPos = basePos + projectileSpawnChargeOffset * chargeIndex

        // Depending on whether we're the player or enemy ship, we need
        // to fly in different directions as they're angled differently.
        val endPos = spawnPos + ship.weaponFireDirection * 5000

        projectile.setInitialPath(spawnPos, endPos)

        ship.projectiles += projectile
    }

    override fun fire(targetSource: () -> Room) {
        fireCombined { targetSource() }
    }

    override fun fireCombined(targetFor: (copy: Int) -> Room) {
        check(!isFiring) { "Cannot file while already firing!" }

        // Each shot's rooms, one for each copy: they're taken off the end
        // (see primeShot), so the last copy's goes in first
        copiesFiring = type.combinedCount
        for (i in 0 until totalReadyCharges * type.shots) {
            for (copy in copiesFiring - 1 downTo 0) {
                targets.add(targetFor(copy))
            }
        }

        // Store the current chain count, in case we need it for the ion charger.
        // (we need the pre-firing value, as it determines the projectile damage)
        firingChainCount = chainCount

        entryAngle = (Math.random() * Math.PI * 2).toFloat()
        fire()
        primeShot()
    }

    override fun fireFromDrone(drone: CombatDrone, target: Room) {
        // If this is a multi-shot weapon, only fire a single shot.
        // We don't have any way of doing more than that.

        val projectile = buildProjectile(target)
        target.ship.projectiles += projectile

        // Draw the projectile on top of the ship. By default it's
        // set to draw under the ship, as it expects to be launched
        // from one ship area to another, at which point it switches this.
        projectile.drawUnderShip = false

        // Prevent defence drones from firing on this shot.
        projectile.firedByDrone = true

        projectile.setInitialPath(drone.flightController.position, projectile.calculateTargetPosition())

        type.launchSounds?.get()?.play()
    }

    open fun fireFromArtillery(possibleTargets: List<Room>, origin: IPoint) {
        projectileSpawnPos = origin
        projectileSpawnChargeOffset = ConstPoint.ZERO

        val remaining = ArrayList(possibleTargets)
        copiesFiring = 1

        // Pick as many shots as required, while avoiding targeting the
        // same room with more than one projectile.
        // (Note that flak overrides this function since it doesn't
        //  have this requirement)
        for (i in 0 until type.shots) {
            val target = remaining.random()
            remaining.remove(target)
            targets += target
        }

        entryAngle = (Math.random() * Math.PI * 2).toFloat()

        // This applies boost for artillery weapons
        // Eg multiverse's playable flagship.
        fire()

        primeShot()
    }

    protected fun damageCloak() {
        // We only damage the cloak if we're fired from the weapons system. If
        // we're fired via the artillery system, from a drone, etc then we
        // don't affect the cloak.
        if (ship.hardpoints.none { it.weapon == this })
            return

        ship.cloaking?.weaponFired()
    }

    protected fun stopFiring() {
        isFiring = false
        targets.clear()
        copiesWaitingToFireAt.clear()

        // Save space in the savefile, as these aren't written if they're zero.
        entryAngle = 0f
        firingAnimationTimer = 0f
        shotsFired = 0
        firingChainCount = 0
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        // A target on a ship that's gone (such as an enemy that was just
        // destroyed) isn't saved, as its ship isn't in the save
        val fireAt = waitingToFireAt?.takeIf { refs.contains(it.ship) }
        if (fireAt != null) {
            val fireAtElem = Element("waitingToFireAt")
            SaveUtil.addAttr(fireAtElem, "ship", refs[fireAt.ship])
            SaveUtil.addAttrInt(fireAtElem, "roomId", fireAt.id)
            elem.addContent(fireAtElem)
        }

        for (room in copiesWaitingToFireAt.filter { refs.contains(it.ship) }) {
            val copyElem = Element("copyWaitingToFireAt")
            SaveUtil.addAttr(copyElem, "ship", refs[room.ship])
            SaveUtil.addAttrInt(copyElem, "roomId", room.id)
            elem.addContent(copyElem)
        }
        SaveUtil.addTagInt(elem, "copiesFiring", copiesFiring, 1)

        SaveUtil.addTagFloat(elem, "entryAngle", entryAngle, 0f)
        SaveUtil.addTagBoolIfTrue(elem, "isFiring", isFiring)
        SaveUtil.addTagFloat(elem, "fireAnimationTimer", firingAnimationTimer, 0f)
        SaveUtil.addTagInt(elem, "shotsFired", shotsFired, 0)
        SaveUtil.addTagInt(elem, "firingChainCount", firingChainCount, 0)

        for (target in targets.filter { refs.contains(it.ship) }) {
            val targetElem = Element("target")
            SaveUtil.addAttr(targetElem, "ship", refs[target.ship])
            SaveUtil.addAttrInt(targetElem, "roomId", target.id)
            elem.addContent(targetElem)
        }
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        super.loadFromXML(elem, refs)

        entryAngle = SaveUtil.getOptionalTagFloat(elem, "entryAngle") ?: 0f
        firingAnimationTimer = SaveUtil.getOptionalTagFloat(elem, "fireAnimationTimer") ?: 0f
        isFiring = SaveUtil.getOptionalTagBool(elem, "isFiring") ?: false
        shotsFired = SaveUtil.getOptionalTagInt(elem, "shotsFired") ?: 0
        firingChainCount = SaveUtil.getOptionalTagInt(elem, "firingChainCount") ?: 0
        copiesFiring = SaveUtil.getOptionalTagInt(elem, "copiesFiring") ?: 1

        for (copyElem in elem.getChildren("copyWaitingToFireAt")) {
            val roomId = SaveUtil.getAttrInt(copyElem, "roomId")
            val shipRef = SaveUtil.getAttr(copyElem, "ship")
            refs.asyncResolve(Ship::class.java, shipRef) { copiesWaitingToFireAt += it!!.rooms[roomId] }
        }

        val waitingToFireAtElem = elem.getChild("waitingToFireAt")
        if (waitingToFireAtElem != null) {
            val roomId = SaveUtil.getAttrInt(waitingToFireAtElem, "roomId")
            val shipRef = SaveUtil.getAttr(waitingToFireAtElem, "ship")
            refs.asyncResolve(Ship::class.java, shipRef) { waitingToFireAt = it!!.rooms[roomId] }
        }

        for (targetElem in elem.getChildren("target")) {
            val roomId = SaveUtil.getAttrInt(targetElem, "roomId")
            val shipRef = SaveUtil.getAttr(targetElem, "ship")
            refs.asyncResolve(Ship::class.java, shipRef) { targets += it!!.rooms[roomId] }
        }
    }

    private fun primeShot() {
        isFiring = true
        waitingToFireAt = targets.removeAt(targets.lastIndex)
        // A combined weapon's other copies fire at the same time
        copiesWaitingToFireAt.clear()
        repeat(copiesFiring - 1) {
            if (targets.isNotEmpty())
                copiesWaitingToFireAt += targets.removeAt(targets.lastIndex)
        }
        firingAnimationTimer = 0f

        // Damage the cloak when the last shot is fired
        if (targets.isEmpty()) {
            damageCloak()
        }
    }

    protected abstract fun buildProjectile(target: Room): AbstractWeaponProjectile
}
