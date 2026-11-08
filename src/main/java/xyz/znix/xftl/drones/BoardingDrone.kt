package xyz.znix.xftl.drones

import org.jdom2.Element
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.Constants
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.FlyingDroneProjectile
import kotlin.math.max

class BoardingDrone(type: DroneBlueprint) : AbstractIndoorsDrone(type) {
    /**
     * The Ion Intruder (BOARDER_ION, from AE), told apart only by its name:
     * rather than breaking systems and fighting the crew, it goes from system
     * to system ionising them, and leaves the crew alone.
     */
    val isIon: Boolean get() = type.baseName == ION_INTRUDER

    override val occupancySlotType get() = AbstractCrew.SlotType.INTRUDER

    // It looks like anti-personnel and boarding drones use the same images?
    override val pawnCodename: String get() = "battle"

    // The Ion Intruder has its own (vanilla's boarder_ion animations)
    override val pawnLook: String get() = if (isIon) ION_LOOK else pawnCodename

    private var projectile: FlyingDrone? = null

    override fun init(ownerShip: Ship) {
        super.init(ownerShip)

        // If we're being deserialised, don't launch another drone.
        if (projectile == null && pawn == null) {
            launchDroneProjectile()
        }
    }

    // The room it's flying to, or has boarded
    private val boardedRoom: Room? get() = projectile?.target ?: pawn?.room

    /** The ship it's flying to, or has boarded. */
    val boardingShip: Ship? get() = boardedRoom?.ship

    private fun launchDroneProjectile() {
        // There may be several enemy ships, so spread the drones out. A
        // combined drone's copies go one to each ship (the main one first),
        // and to rooms of their own on the same ship (see drone-sizes.md).
        // Never to a ship with a super shield up, which would destroy them
        // (see Drones, which doesn't launch any if every ship has one).
        val siblingRooms = siblingCopies.mapNotNull { (it as? BoardingDrone)?.boardedRoom }
        val ships = boardableShips(ownerShip).ifEmpty { ownerShip.sys.getOpponentShips(ownerShip) }
        val enemyShip = ships.minBy { ship ->
            siblingRooms.count { it.ship == ship } * ships.size * 10 +
                    ship.externalDrones.count { it.ownerShip == ownerShip }
        }
        val target = enemyShip.rooms.filter { it !in siblingRooms }.ifEmpty { enemyShip.rooms }.random()
        projectile = FlyingDrone(target)

        // Start in the centre of the drones room, per vanilla
        val startingPoint = droneSystem.room!!.pixelCentre

        // The direction the drone flies in depends on whether this is
        // a player ship or an enemy ship, as it's supposed to go
        // forwards out of both of them.
        val endPoint = startingPoint + ownerShip.weaponFireDirection * 5000

        projectile!!.setInitialPath(startingPoint, endPoint)

        ownerShip.projectiles += projectile!!
    }

    override fun updatePawn(dt: Float) {
        val pawn = this.pawn!!

        // Are we moving somewhere
        if (pawn.pathingTarget != null)
            return

        if (isIon) {
            updateIonPawn(pawn)
            return
        }

        // Do we have unfinished business here?
        if (roomHasWorkingSystem(pawn.room))
            return
        if (roomContainsHostileCrew(pawn.room))
            return

        // Pick a random room to go and punch up the contents of.
        // It can either have a non-broken system or it can have friendly (to
        // the ship, not to the drone) crew.
        val candidates = ship.rooms.filter { roomContainsHostileCrew(it) || roomHasWorkingSystem(it) }

        // Nothing more to break and kill :(
        if (candidates.isEmpty())
            return

        // If the room is full this won't do anything, but it doesn't matter
        // as we'll just try again next update.
        pawn.setTargetRoom(candidates.random())
    }

    override fun update(dt: Float) {
        super.update(dt)

        // Check if the player ship jumped away or shot down the projectile
        if (pawn != null)
            return

        val projectile = this.projectile
        if (projectile == null) {
            // No projectile and no pawn? This probably shouldn't happen.
            destroy()
            return
        }

        // Check if the enemy ship changed, for example if it was killed.
        val enemyShip = projectile.targetShip
        if (enemyShip == null || !ownerShip.sys.isEnemyOf(ownerShip, enemyShip)) {
            destroy()
            return
        }

        if (!ownerShip.projectiles.contains(projectile) && !enemyShip.projectiles.contains(projectile)) {
            // The projectile has vanished, for example if it was
            // greeted by a defence drone.
            destroy()
        }
    }

    // The Ion Intruder stays with a system until it's well ionised, then
    // moves on to another that isn't (or any other, once they all are)
    private fun updateIonPawn(pawn: Pawn) {
        if (needsIonising(pawn.room))
            return
        // Not while it's raising or lowering its wings
        if ((pawn as? BoardingPawn)?.ionBusy == true)
            return
        // (with every system ionised, it waits for one to come back)
        val target = ship.rooms.filter { it != pawn.room && needsIonising(it) }.randomOrNull() ?: return
        pawn.setTargetRoom(target)
    }

    // A system it hasn't ionised yet, or that's come back since
    private fun needsIonising(room: Room): Boolean {
        val system = room.system ?: return false
        return !system.isIonised
    }

    private fun roomContainsHostileCrew(room: Room): Boolean {
        return room.crew.any { it.mode == AbstractCrew.SlotType.CREW }
    }

    private fun roomHasWorkingSystem(room: Room): Boolean {
        val system = room.system ?: return false

        // Can we damage it further?
        return system.damagedEnergyLevels != system.energyLevels
    }

    override fun removeInstance() {
        super.removeInstance()

        // If a flying drone was sent out, get rid of it.
        projectile?.destroyFlying()
    }

    // (its glow's drawn by the pawn, see Pawn.drawImage)
    override fun drawPawn() {
    }

    override fun makePawn(room: Room): Pawn = BoardingPawn(room)

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        if (projectile != null) {
            val projectileElem = Element("projectile")
            SaveUtil.addRoomRef(elem, "targetRoom", refs, projectile!!.target)
            projectile!!.saveToXML(projectileElem, refs)
            elem.addContent(projectileElem)
        }
    }

    override fun loadFromXML(elem: Element, refs: RefLoader, containingShip: Ship) {
        // Load the flying projectile first, before onInit is called.
        val projectileElem = elem.getChild("projectile")
        if (projectileElem != null) {
            SaveUtil.getRoomRef(elem, "targetRoom", refs) { target ->
                projectile = FlyingDrone(target)
                projectile!!.loadPropertiesFromXML(projectileElem, refs)
                containingShip.projectiles.add(projectile!!)
            }
        }

        super.loadFromXML(elem, refs, containingShip)
    }

    /**
     * The projectile that represents the drone flying through space towards
     * the target ship.
     */
    inner class FlyingDrone(val target: Room) : FlyingDroneProjectile(target.ship) {
        // We have to use our target ship for initialisation, as we're deserialised
        // before ownerShip is set.
        private val game = target.ship.sys

        // The portrait frame of the robot, which is shown on top
        // of the thruster sprite
        val portrait = game.animations["${pawnLook}_portrait"].spriteAt(game, 0)

        val thruster = game.getImg("img/ship/drones/boarder_engine.png")

        // Fished out with x32dbg as I couldn't be bothered to find
        // it via static analysis, and it's not guaranteed to be correct
        // if I got some addresses swapped.
        // Note it has a 16x multiplier, to convert it from pixels per
        // SpeedFactor to pixels per second. See the original XFTL's doc/reveng-general.md.
        override val speed: Int
            get() = when {
                // Pause when the drone is powered off
                !isPowered -> 0

                // Pause when the target is cloaked
                target.ship.isCloakActive -> 0

                // Regular speed
                else -> 18 * 16
            }

        override var drawUnderShip: Boolean = true

        override val isMissileForDD: Boolean get() = true

        // Can't collide with drones
        override val antiDroneBP: AbstractWeaponBlueprint? get() = null
        override val antiDroneExemption: Ship? get() = null

        override val drone: AbstractDrone get() = this@BoardingDrone

        override fun reachedTarget() {
            // We've hit our target room, punching a hole in the hull
            // where it came in, like in vanilla.

            destroyFlying()
            target.spawnBreach()

            // Spawn the drone pawn in the target room
            spawn(target)

            // If the drone ended up in a different room due to
            // the target being full, move it back but leave it
            // pathing to somewhere else.
            if (pawn!!.room != target) {
                pawn!!.jumpTo(target, ConstPoint.ZERO)
            }
        }

        fun destroyFlying() {
            dead = true
            projectile = null

            // If there's even a single update where both the drone and projectile
            // are alive, this will cause a great deal of trouble since the drone
            // will be serialised twice - once due to being in the projectiles list,
            // and once from being in the crew list.
            // Thus we have to remove ourselves immediately.
            target.ship.projectiles.remove(this)
        }

        override fun renderPreTranslated(g: Graphics) {
            // Centre the drone on the supplied x,y coordinates.
            val size = portrait.imageSize
            val offset = ConstPoint(-size.x / 2, -size.y / 2)

            // 'up' in the sprite is forwards, rotate it so that is the case.
            val angleOffset = 90 // In degrees because that's what Slick uses.

            g.rotate(0f, 0f, angleOffset.f)

            portrait.draw(offset)
            thruster.draw(offset)
        }

        override fun onSwitchedToTarget() {
            super.onSwitchedToTarget()

            drawUnderShip = false
        }

        override fun calculateTargetPosition(): IPoint {
            // Copied from AbstractWeaponProjectile.

            // Aim for the centre of the target room.
            return target.pixelCentre
        }

        override fun hitOtherProjectile(currentSpace: Ship) {
            currentSpace.playCentredAnimation(explodeAnimation, position)
        }

        override fun crossedShieldLine() {
            super.crossedShieldLine()

            // Hitting a super shield doesn't damage it, but does destroy the drone.
            if (target.ship.superShield > 0) {
                destroy()
                target.ship.playCentredAnimation(explodeAnimation, position)
            }
        }

        override fun saveToXML(elem: Element, refs: ObjectRefs) {
            super.saveToXML(elem, refs)

            SaveUtil.addAttrBool(elem, "drawUnderShip", drawUnderShip)
        }

        override fun loadPropertiesFromXML(elem: Element, refs: RefLoader) {
            super.loadPropertiesFromXML(elem, refs)

            drawUnderShip = SaveUtil.getAttrBool(elem, "drawUnderShip")
        }
    }

    private inner class BoardingPawn(room: Room) : AbstractIndoorsDrone.Pawn(room) {
        // Always fight by shooting a laser (the Ion Intruder doesn't fight)
        override val canPunch: Boolean get() = false
        override val canFight: Boolean get() = !isIon
        override val attackDamageMult: Float get() = 1.2f

        override val maxHealth: Float get() = 150f

        // The Ion Intruder walks slower than the boarding drone
        override val droneSpeed: Float get() = if (isIon) BASE_MOVEMENT_SPEED * ION_SPEED else super.droneSpeed

        // How long it's been charging its next zap
        private var ionCharge = 0f

        // How much longer it's lowering its wings after a zap
        private var wingsLowering = 0f

        // How much longer it counts as ionising a system since it last was,
        // so a frame doing something else doesn't restart its wings
        private var ionising = 0f

        /**
         * Whether the Ion Intruder is raising its wings for a zap, or
         * lowering them after one: it doesn't walk off until they're down.
         */
        val ionBusy: Boolean get() = ionCharge >= ION_INTERVAL - WING_TIME || wingsLowering > 0f

        // The Ion Intruder ionises any system, broken or not, once, and not
        // again until the ion's worn off
        override fun canSabotage(system: AbstractSystem): Boolean =
            if (isIon) !system.isIonised else super.canSabotage(system)

        override fun sabotage(system: AbstractSystem, dt: Float) {
            if (!isIon) {
                super.sabotage(system, dt)
                return
            }

            ionising = IONISING_GRACE
            ionCharge += dt
            if (ionCharge < ION_INTERVAL)
                return
            ionCharge = 0f
            wingsLowering = WING_TIME
            system.dealDamage(0, ION_ZAP_DAMAGE)
            room.showDamageText(ION_ZAP_DAMAGE, Constants.DAMAGE_COLOUR_ION)
            // Its discharge: vanilla's ion glow around it, and an ion flash
            room.ship.playCentredAnimation(game.animations["${ION_LOOK}_glow"], getPixelPositionCentre())
            room.ship.playCentredAnimation(game.animations[ION_ZAP_ANIMATION], getPixelPositionCentre())

            // The discharge stuns the crew in the room with it
            for (crew in room.crew) {
                if (crew != this && crew.mode != mode && crew.currentAction != Action.DYING)
                    crew.stunTime = max(crew.stunTime, ION_STUN_TIME)
            }
        }

        override fun update(dt: Float) {
            super.update(dt)
            if (!isIon)
                return
            wingsLowering = max(0f, wingsLowering - dt)
            ionising = max(0f, ionising - dt)
            // (one that's walked off, or stopped, isn't charging any more)
            if (ionising == 0f && wingsLowering == 0f)
                ionCharge = 0f
            updateWings()
        }

        // The wings, as it ionises a system: down while it charges, raised
        // once, slowly, as it's about to zap, and lowered as slowly after
        private var wings: Wings? = null

        private fun updateWings() {
            val wanted = when {
                wingsLowering > 0f -> Wings.LOWERING
                ionising == 0f -> null
                ionCharge >= ION_INTERVAL - WING_TIME -> Wings.RAISING
                else -> Wings.DOWN
            }
            if (wanted == wings)
                return
            wings = wanted

            val spec = ionAnimation
            val speed = spec.totalTime / WING_TIME
            when (wanted) {
                null -> super.updateAnimation()
                Wings.RAISING -> icon = spec.startSingle(game, speed, false)
                Wings.LOWERING -> icon = spec.startSingle(game, speed, true)
                Wings.DOWN -> icon = spec.startSingle(game, speed, false).also { it.isPaused = true }
            }
        }

        // While it's ionising a system (or lowering its wings after), its
        // wings are animated by updateWings
        override fun updateAnimation() {
            if (isIon && (ionising > 0f || wingsLowering > 0f))
                return
            super.updateAnimation()
        }

        private val ionAnimation get() = game.animations["${ION_LOOK}_ion"]
    }

    // The Ion Intruder's wings (see BoardingPawn.updateWings)
    private enum class Wings { DOWN, RAISING, LOWERING }

    companion object {
        const val ION_INTRUDER = "BOARDER_ION"

        /**
         * The ships [owner]'s boarding drones can board: those it's fighting
         * without a super shield up (a Zoltan shield, which destroys a
         * boarding drone as it crosses it, Zoltan Shield Bypass or not).
         */
        fun boardableShips(owner: Ship): List<Ship> = owner.sys.getOpponentShips(owner)
            .filter { it.superShield == 0 && owner.sys.isEnemyOf(owner, it) && !it.isDead }

        // How long the Ion Intruder charges its discharge, and how much ion
        // damage it does to the system, all at once: three points, fifteen
        // seconds (a point's five), about four charges' worth
        private const val ION_INTERVAL = 4f
        const val ION_ZAP_DAMAGE = 3

        // How long each discharge stuns the crew in its room for, so they're
        // stunned as long as it stays (see BoardingPawn.sabotage)
        const val ION_STUN_TIME = ION_INTERVAL + 0.5f

        // The Ion Intruder's animations and images (see pawnLook)
        const val ION_LOOK = "boarder_ion"

        // How long it takes to raise its wings for a zap, or to lower them
        // after (vanilla's animation takes a second), the flash of the zap,
        // and how fast it walks, from a crewmember's speed
        private const val WING_TIME = 1.25f
        private const val ION_ZAP_ANIMATION = "explosion_small_ion"
        private const val ION_SPEED = 0.6f
        private const val IONISING_GRACE = 0.3f
    }
}
