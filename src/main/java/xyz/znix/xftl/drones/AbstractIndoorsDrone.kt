package xyz.znix.xftl.drones

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.AbstractCrewDamage
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.RoomPoint
import xyz.znix.xftl.f
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.weapons.DroneBlueprint

/**
 * A drone that operates on the inside of a ship, like a repair or boarding drone.
 */
abstract class AbstractIndoorsDrone(type: DroneBlueprint) : AbstractDrone(type) {
    /**
     * The type of slot that represents whether this drone counts as a crewmember
     * or an enemy for the purposes of occupying a cell in a room (this is where
     * you can have an enemy and a crewmember standing in the same cell, but not
     * two crewmembers in the same cell).
     */
    abstract val occupancySlotType: AbstractCrew.SlotType

    /**
     * The name of the blueprint for the fake crew race that's used for drawing the drone.
     *
     * This matches up with the spritesheets in img/ship/drones, and with
     * the crewBlueprint in the XML.
     */
    abstract val pawnCodename: String

    /**
     * The animations and images the pawn uses (`<pawnLook>_walk_down` and so
     * on), if they aren't the [pawnCodename]'s: the Ion Intruder has its own.
     */
    open val pawnLook: String get() = pawnCodename

    /**
     * The ship this drone is aboard. This may be different to [ownerShip],
     * in the case of boarding drones.
     */
    lateinit var ship: Ship
        private set

    var pawn: Pawn? = null
        private set

    /**
     * Spawn in [targetRoom], or one of the nearby rooms if that's full.
     */
    protected fun spawn(targetRoom: Room) {
        ship = targetRoom.ship

        val emptySpace = ship.findSpaceForCrew(targetRoom, occupancySlotType)

        pawn = makePawn(emptySpace.room)
        ship.crew += pawn!!

        // Move the drone to it's starting position
        pawn!!.jumpTo(emptySpace)
    }

    /** Spawn exactly at [point] (which must be free). */
    protected fun spawnAt(point: RoomPoint) {
        ship = point.room.ship
        pawn = makePawn(point.room)
        ship.crew += pawn!!
        pawn!!.jumpTo(point)
    }

    protected open fun makePawn(room: Room): Pawn {
        return Pawn(room)
    }

    override fun removeInstance() {
        super.removeInstance()

        // If the pawn was never spawned, there's nothing to clean up.
        val pawn = this.pawn ?: return
        this.pawn = null

        // Destroy the pawn
        pawn.removeFromShip()
    }

    override fun update(dt: Float) {
        super.update(dt)

        // If something weird happens and our pawn disappears
        // from the enemy ship, assume it was destroyed.
        val pawn = this.pawn
        if (pawn != null && !pawn.room.ship.crew.contains(pawn)) {
            destroy()
        }
    }

    override fun onEnemyShipUpdated() {
        super.onEnemyShipUpdated()

        // If we're on the former enemy ship, destroy ourselves. The Drone
        // Recovery Arm gets its part back, as it's left behind as if by a
        // jump (see Drones.onJump).
        if (pawn != null && ship != ownerShip && !ownerShip.sys.isEnemyOf(ownerShip, ship)) {
            if (ownerShip.hasAugment(AugmentBlueprint.DRONE_RECOVERY_ARM))
                ownerShip.dronesCount++
            destroy()
        }
    }

    protected abstract fun updatePawn(dt: Float)

    /**
     * Whether another copy of this combined drone (see [siblingCopies]) is
     * in [room], or on its way there: the copies share the rooms out
     * between them, each going somewhere the others aren't, if there's
     * somewhere.
     */
    protected fun claimedBySibling(room: Room): Boolean = siblingCopies.any { other ->
        val pawn = (other as? AbstractIndoorsDrone)?.pawn ?: return@any false
        pawn.pathingTarget?.room == room || (pawn.pathingTarget == null && pawn.room == room)
    }

    /**
     * The room of the drones system this drone belongs to (the first, if
     * the ship has several and it's somehow in none of them).
     */
    private val homeRoom: Room?
        get() {
            val systems = ship.droneSystems
            val own = systems.firstOrNull { system -> system.drones.any { it?.holds(this) == true } }
            return (own ?: systems.firstOrNull())?.room
        }

    /**
     * With nothing to do, walk back to the drones system's room, rather
     * than standing about wherever the last job was.
     */
    protected fun returnHome() {
        val pawn = pawn ?: return
        if (pawn.pathingTarget != null)
            return
        val home = homeRoom ?: return
        if (pawn.room != home)
            pawn.setTargetRoom(home)
    }

    protected abstract fun drawPawn()

    private fun getPawnBlueprint(game: InGameState): CrewBlueprint {
        return game.blueprintManager[pawnCodename] as CrewBlueprint
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        if (pawn != null) {
            val pawnElem = Element("pawn")
            pawn!!.saveToXML(pawnElem, refs)
            elem.addContent(pawnElem)
        }
    }

    override fun loadFromXML(elem: Element, refs: RefLoader, containingShip: Ship) {
        super.loadFromXML(elem, refs, containingShip)

        ship = containingShip

        val pawnElem = elem.getChild("pawn")
        if (pawnElem != null) {
            // Pick an arbitrary room to spawn in, we'll move while loading the pawn's XML anyway.
            pawn = makePawn(ship.rooms[0])
            pawn!!.loadFromXML(pawnElem, refs)

            // Don't add the pawn to the crew list until we've loaded
            // our owner ship, since that's required for most things.
            refs.addOnResolveFunction {
                ship.crew.add(pawn!!)
            }
        }
    }

    /**
     * An indoors drone comprises two objects: the drone object itself, and
     * a crewmember 'pawn'. This is to allow drones like boarding drones that
     * first cross the screen and can be shot down, or otherwise don't
     * immediately spawn. Also, [AbstractCrew] needs the initial room in its
     * constructor, which is inconvenient for us.
     */
    open inner class Pawn(room: Room) :
        AbstractCrew(getPawnBlueprint(room.ship.sys), room.ship.sys.animations, room) {

        val drone: AbstractIndoorsDrone = this@AbstractIndoorsDrone

        override val codename: String get() = pawnLook

        var powerUpDuration: Float = 0f
        var runningLastUpdate: Boolean = false

        override val canManSystem: Boolean get() = false

        /** How fast this kind of drone walks, before the Drone Reactor Booster. */
        open val droneSpeed: Float get() = BASE_MOVEMENT_SPEED

        // The owner's Drone Reactor Booster speeds them up
        override val movementSpeed: Float
            get() = droneSpeed * (1f + ownerShip.getAugmentValue(AugmentBlueprint.DRONE_REACTOR_BOOSTER))
        override val canFight: Boolean get() = false
        override val canRepair: Boolean get() = false
        override val hasDyingAnimation: Boolean get() = false
        override val suffocationMultiplier: Float get() = 0f
        override val fireDamageMult: Float get() = 0f

        override val mode: SlotType get() = occupancySlotType

        override val providesPlayerVision: Boolean get() = isPowered && ownerShip.isPlayerShip

        override fun update(dt: Float) {
            // If the ship powering this drone has jumped away, destroy it.
            // Also self-destruct if the pawn field no longer points to this
            // pawn for whatever reason. It shouldn't happen, but this could
            // avoid a drone getting stuck on the player ship.
            if (!ship.sys.isShipPresent(ownerShip) || pawn != this) {
                removeFromShip()
                return
            }

            // If the drone was turned off or on again, we only apply that
            // when the game unpauses.
            if (isRunning != runningLastUpdate) {
                if (isRunning) {
                    powerUpDuration = ship.sys.animations["${codename}_power_up"].totalTime
                } else {
                    powerUpDuration = 0f
                }

                runningLastUpdate = isRunning
                updateAnimation()
            }

            // Only progress on movement, fighting, repairs, etc if we're powered,
            // and the power-up/power-down animation isn't playing.
            val on = isRunning && powerUpDuration == 0f
            if (on) {
                super.update(dt)

                // Note the pawn can be null here if it was killed
                // during the update cycle.
                if (pawn != null)
                    updatePawn(dt)
            } else if (health == 0f) {
                // Dying is implemented by super.update, so we have to call it
                // when we're dead, otherwise we'd become invincible while
                // powered off.
                super.update(dt)
            } else {
                icon.update(dt)

                if (powerUpDuration != 0f && isRunning) {
                    powerUpDuration -= dt
                    if (powerUpDuration < 0f) {
                        powerUpDuration = 0f
                        updateAnimation()
                    }
                }
            }
        }

        override fun updateAnimation() {
            if (powerUpDuration != 0f) {
                icon = ship.sys.animations["${codename}_power_up"].startSingle(game)
                return
            }
            if (!runningLastUpdate) {
                icon = ship.sys.animations["${codename}_power_down"].startSingle(game)
                return
            }

            super.updateAnimation()
        }

        override fun draw(g: Graphics) {
            super.draw(g)
            drawPawn()
        }

        // While it's on, its lights glow: vanilla's layer1 sheet (laid out
        // as the base one) drawn over it, as the crew's colour layers are
        override fun drawImage(x0: Float, y0: Float, x1: Float, y1: Float, baseFrame: Image, alpha: Float) {
            super.drawImage(x0, y0, x1, y1, baseFrame, alpha)

            if (!runningLastUpdate || powerUpDuration != 0f)
                return
            val base = game.getImg("img/people/${pawnLook}_base.png")
            val glow = game.getImg("img/people/${pawnLook}_layer1.png")
            val frameX = baseFrame.textureOffsetX - base.textureOffsetX
            val frameY = baseFrame.textureOffsetY - base.textureOffsetY
            if (frameX < 0 || frameY < 0 || frameX + baseFrame.width > glow.width || frameY + baseFrame.height > glow.height)
                return
            val layer = glow.getSubImage(frameX, frameY, baseFrame.width, baseFrame.height)
            layer.draw(x0, y0, x1, y1, 0f, 0f, layer.width.f, layer.height.f, alpha, Colour.white)
        }

        override fun dealDamage(damage: AbstractCrewDamage) {
            if (damage.halvedForDrone) {
                damage.amount /= 2f
            }

            super.dealDamage(damage)
        }

        override fun removeFromShip() {
            super.removeFromShip()

            // Cleanly kill the drone
            // If pawn==null then we're being called by removeInstance,
            // in which case we're not supposed to explode.
            if (drone.pawn == null) {
                // Call it again to be certain, though.
                removeInstance()
                return
            }

            drone.pawn = null
            destroy()

            // Play the explosion animation whenever a drone is killed.
            ship.playCentredAnimation(explodeAnimation, getPixelPositionCentre())
            explodeSound.play()
        }

        override fun saveToXML(elem: Element, refs: ObjectRefs) {
            super.saveToXML(elem, refs)

            SaveUtil.addAttrFloat(elem, "powerUpDuration", powerUpDuration)
            SaveUtil.addAttrBool(elem, "onLastUpdate", runningLastUpdate)
        }

        override fun loadFromXML(elem: Element, refs: RefLoader) {
            // These are used to set the animation, so we have to load them first.
            powerUpDuration = SaveUtil.getAttrFloat(elem, "powerUpDuration")
            runningLastUpdate = SaveUtil.getAttrBool(elem, "onLastUpdate")

            super.loadFromXML(elem, refs)
        }
    }
}
