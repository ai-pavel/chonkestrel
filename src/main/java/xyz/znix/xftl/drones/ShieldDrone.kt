package xyz.znix.xftl.drones

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.weapons.DroneBlueprint
import kotlin.math.max

/**
 * The shield overcharger: it flies around its ship's shields, and adds a
 * point of super-shield (as a Zoltan shield gives) every time it charges up.
 *
 * Each one looks after a row of five points, as a Zoltan shield gives (see
 * [Ship.maxSuperShield]), so the overchargers together recharge the
 * super-shield up to five points each.
 */
class ShieldDrone(type: DroneBlueprint) : AbstractExternalDrone(type, false) {
    override val flightController = OrbitFlightController(this)

    private lateinit var offImage: Image
    private lateinit var chargedImage: Image
    private lateinit var glowImage: Image

    private val typeCooldown = (type.cooldown ?: 15000) / 1000f

    // The time left until the next point of super-shield
    private var cooldown: Float = typeCooldown

    /** For tests: the time left until its next point of super-shield. */
    val debugCooldown: Float get() = cooldown

    private val isFull: Boolean
        get() {
            val limit = targetShip.shieldDroneCount * AugZoltanShield.SHIELD_PER_AUGMENT
            return targetShip.superShield >= minOf(limit, targetShip.maxSuperShield)
        }

    override fun onLaunched() {
        super.onLaunched()

        // A combined overcharger's copies charge one after another, a
        // share of the way through each other's charge (see
        // doc/game/drone-sizes.md), rather than all at once
        if (copyIndex > 0)
            cooldown = typeCooldown * (1f + copyIndex.toFloat() / type.combinedCount)
    }

    override fun onInit() {
        offImage = game.getImg("img/ship/drones/${type.droneImage}_off.png")
        chargedImage = game.getImg("img/ship/drones/${type.droneImage}_charged.png")
        glowImage = game.getImg("img/ship/drones/${type.droneImage}_glow.png")
    }

    override fun onRender(g: Graphics) {
        if (!isRunning) {
            drawCentred(offImage)
            return
        }

        drawCentred(chargedImage)

        // The glow brightens as it charges up a point, and there's none
        // while the shield's full and there's nothing for it to do
        if (isFull)
            return
        val chargeProgress = max(0f, (typeCooldown - cooldown) / typeCooldown)
        drawCentred(glowImage, Colour(1f, 1f, 1f, chargeProgress))
    }

    override fun update(dt: Float) {
        super.update(dt)

        if (!isRunning) {
            cooldown = typeCooldown
            return
        }

        // Stay charged while the super-shield is full, and add a point as
        // soon as it's hit.
        cooldown = max(0f, cooldown - dt)

        // It stops dead on its way round for the last of its charging, as
        // in vanilla, and flies on as soon as it's charged the shields
        if (!isFull && cooldown > 0f && cooldown <= CHARGE_PAUSE)
            flightController.pause(cooldown)

        if (cooldown > 0f || isFull)
            return

        targetShip.superShield++
        cooldown = typeCooldown
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        SaveUtil.addAttrFloat(elem, "cooldown", cooldown)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader, containingShip: Ship) {
        super.loadFromXML(elem, refs, containingShip)

        cooldown = SaveUtil.getAttrFloat(elem, "cooldown")
    }

    companion object {
        /** How long it stops for at the end of charging each point, in seconds. */
        const val CHARGE_PAUSE = 1f
    }
}
