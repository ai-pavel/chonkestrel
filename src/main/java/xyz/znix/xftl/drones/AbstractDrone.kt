package xyz.znix.xftl.drones

import xyz.znix.xftl.systems.Drones
import org.jdom2.Element
import xyz.znix.xftl.AnimationSpec
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.FTLSound
import xyz.znix.xftl.rollChance
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.weapons.DroneBlueprint
import kotlin.random.Random

abstract class AbstractDrone(val type: DroneBlueprint) {
    var isPowered: Boolean = false
        set(value) {
            val changed = value != field
            field = value
            if (changed)
                onPowerChanged()
        }

    /**
     * Like [isPowered], but also false if the drone is stunned.
     */
    val isRunning: Boolean get() = isPowered && !isStunned

    lateinit var ownerShip: Ship
        private set

    /**
     * Which of its slot's copies this is (see Drones.DroneInfo.copies): 0
     * for a normal drone, and 0 to 2 for a combined one's (see
     * doc/game/drone-sizes.md).
     */
    var copyIndex: Int = 0

    protected var initialised: Boolean = false
        private set

    /** Whether it's been set up for its ship (see [init]). */
    val isInitialised: Boolean get() = initialised

    protected lateinit var explodeAnimation: AnimationSpec
    protected lateinit var explodeSound: FTLSound

    /**
     * The drones system this drone was launched from. Ships can have
     * several, so this is the one with this drone in one of its slots.
     */
    protected val droneSystem: Drones
        get() = ownerShip.dronesSystemFor(this) ?: ownerShip.drones!!

    private val droneInfo: Drones.DroneInfo?
        get() = ownerShip.droneSystems.flatMap { it.drones }.firstOrNull { it?.holds(this) == true }

    /**
     * The other copies of a combined drone (see Drones.DroneInfo.copies)
     * that are out with this one, which it shares the work with (see
     * doc/game/drone-sizes.md); none for a normal drone.
     */
    val siblingCopies: List<AbstractDrone>
        get() = droneInfo?.liveCopies?.filter { it !== this } ?: emptyList()

    open val isStunned: Boolean get() = droneSystem.isHackActive

    private var stunTotalTimer: Float = 0f
    private var stunDestroyTimer: Float = 0f

    open fun init(ownerShip: Ship) {
        if (initialised)
            error("Cannot re-initialise drone ${type.name}")

        explodeAnimation = ownerShip.sys.animations["explosion_random"]
        explodeSound = ownerShip.sys.sounds.getSample("smallExplosion")

        this.ownerShip = ownerShip
        initialised = true
    }

    /**
     * Remove this drone instance, setting a cooldown before
     * it can be re-deployed.
     *
     * This should be called when a drone is destroyed by something,
     * for example a repair drone destroyed by boarders.
     */
    open fun destroy() {
        // Do this before calling removeInstance, otherwise we won't
        // be able to find the info instance.
        val info = droneInfo
        info?.cooldown = DRONE_DESTROYED_COOLDOWN

        removeInstance()
    }

    /**
     * Remove this instance of this drone.
     *
     * This does less than [destroy]: it doesn't set a cooldown or show
     * an explosion animation or anything like that, it only removes
     * the drone instance.
     */
    open fun removeInstance() {
        // Drones that have been moved to cargo won't have an associated
        // info any more, so we have to use firstOrNull.
        val info = droneInfo
        info?.remove(this)
    }

    open fun update(dt: Float) {
        if (isStunned) {
            stunTotalTimer += dt

            // After one second, there's a 15% chance to destroy
            // the drone once per second.
            // It appears this is how both hacking and the anti-drone
            // can destroy drones.
            if (stunTotalTimer >= 1f) {
                // Add a small margin so level 1 hacking can get three
                // attempts at it.
                val waitPeriod = 0.99f
                stunDestroyTimer += dt
                if (stunDestroyTimer >= waitPeriod) {
                    stunDestroyTimer -= waitPeriod

                    if (Random.rollChance(15)) {
                        destroy()
                    }
                }
            }

            return
        }
        stunTotalTimer = 0f
        stunDestroyTimer = 0f
    }

    protected open fun onPowerChanged() {
    }

    /**
     * Called once a new drone's been launched (and initialised), but not
     * when one's loaded from a saved game.
     */
    open fun onLaunched() {
    }

    /**
     * Called whenever something potentially important happens to the enemy
     * ship, such as it being destroyed or changing as we jump away.
     */
    open fun onEnemyShipUpdated() {
    }

    open fun saveToXML(elem: Element, refs: ObjectRefs) {
        SaveUtil.addAttr(elem, "type", type.name)
        SaveUtil.addAttrRef(elem, "owner", refs, ownerShip)
        SaveUtil.addAttrBool(elem, "powered", isPowered)

        SaveUtil.addTagFloat(elem, "stunTotalTimer", stunTotalTimer, 0f)
        SaveUtil.addTagFloat(elem, "stunDestroyTimer", stunDestroyTimer, 0f)

        // Save a reference to the slot of our owning drones system that we're in.
        // This is how we link ourselves back up with the DroneInfo object.
        // Ships can have several drones systems, so save which one too.
        val system = ownerShip.dronesSystemFor(this)
        val slot = system?.drones?.indexOfFirst { it?.holds(this) == true } ?: -1
        SaveUtil.addAttrInt(elem, "slot", if (slot == -1) null else slot)
        if (copyIndex != 0)
            SaveUtil.addAttrInt(elem, "copy", copyIndex)
        val systemIndex = system?.let { ownerShip.droneSystems.indexOf(it) }
        SaveUtil.addAttrInt(elem, "droneSystem", systemIndex?.takeIf { it > 0 })
    }

    // containingShip is the ship that the drone was serialised inside of.
    // This is not always the same as ownerShip - for example, boarding drones
    // or combat drones are serialised as part of the ship they're attacking,
    // but obviously belong to the other ship.
    open fun loadFromXML(elem: Element, refs: RefLoader, containingShip: Ship) {
        require(type.name == SaveUtil.getAttr(elem, "type"))

        isPowered = SaveUtil.getAttrBool(elem, "powered")

        stunTotalTimer = SaveUtil.getOptionalTagFloat(elem, "stunTotalTimer") ?: 0f
        stunDestroyTimer = SaveUtil.getOptionalTagFloat(elem, "stunDestroyTimer") ?: 0f

        // This runs after all the other initialisation
        val slot = SaveUtil.getAttrIntOrNull(elem, "slot")
        copyIndex = SaveUtil.getOptionalAttrInt(elem, "copy") ?: 0
        // (missing in saves from before ships could have several)
        val systemIndex = SaveUtil.getOptionalAttrInt(elem, "droneSystem") ?: 0
        SaveUtil.getAttrRef(elem, "owner", refs, Ship::class.java) {
            init(it!!)

            if (slot == null) {
                return@getAttrRef
            }

            val info = it.droneSystems[systemIndex].drones[slot]!!

            // A couple of simple checks to make sure we're not ending up in the wrong slot.
            require(info.type == type)
            info.place(this, copyIndex)
        }
    }

    companion object {
        const val DRONE_DESTROYED_COOLDOWN: Float = 10f
    }
}
