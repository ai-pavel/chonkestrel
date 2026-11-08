package xyz.znix.xftl.environment

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.FTLSound
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.random
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.AbstractWeaponProjectile
import xyz.znix.xftl.weapons.MissileBlueprint
import xyz.znix.xftl.weapons.ProjectileLoadCallback
import kotlin.random.Random
import kotlin.random.nextInt

/**
 * A planet's (or the rebel fleet's) Anti-Ship Battery, from an event's
 * `<environment type="PDS" target="player"/>`: every so often, it fires a
 * shot up from below the screen at the ships it's targeting, the player's,
 * the enemy's (it's on the player's side), or everyone's.
 *
 * Vanilla's timings and damage are in its executable, not its data, so
 * these are estimates: a shot every [MIN_PERIOD] to [MAX_PERIOD] seconds,
 * of [AsbProjectile]'s damage, warned of [WARNING_TIME] seconds before.
 */
class AntiShipBattery(private val game: InGameState, val target: Target) {
    enum class Target(val xmlName: String) {
        PLAYER("player"), ENEMY("enemy"), ALL("all");

        companion object {
            fun parse(name: String?): Target =
                entries.firstOrNull { it.xmlName.equals(name, ignoreCase = true) } ?: PLAYER
        }
    }

    /** Whether it fires at the player, rather than only at the enemy. */
    val targetsPlayer: Boolean get() = target != Target.ENEMY

    // The first shot comes a little later than the rest, to give the player
    // time to get their bearings
    private var timeToShot = FIRST_SHOT_TIME

    // With target="all", it takes turns between the player and the enemy
    private var nextAtPlayer = target != Target.ENEMY

    private var hasPlayedWarning = false

    private val warningSound by lazy { game.sounds.getSample("environWarning") }

    /** True while the next shot's about to go at the player, for the 'ASB TARGET LOCKED' warning. */
    val showWarning: Boolean get() = nextAtPlayer && timeToShot < WARNING_TIME && game.player.health > 0

    fun update(dt: Float) {
        timeToShot -= dt

        if (showWarning && !hasPlayedWarning) {
            warningSound.play()
            hasPlayedWarning = true
        }

        if (timeToShot > 0f)
            return

        fire()
        timeToShot = Random.nextFloat() * (MAX_PERIOD - MIN_PERIOD) + MIN_PERIOD
        hasPlayedWarning = false
        if (target == Target.ALL)
            nextAtPlayer = !nextAtPlayer
    }

    private fun fire() {
        // Only at ships still fighting (one that's given up, or is breaking
        // up, is left alone)
        val ship: Ship = when {
            nextAtPlayer -> game.player
            else -> game.allEnemyShips.filter { !it.isDead && game.isEnemyOf(game.player, it) }.randomOrNull() ?: return
        }
        if (ship.isDead)
            return

        val shot = AsbProjectile(ship.rooms.random())

        // Up from the planet, below the ship (just inside the edge of the
        // space projectiles can be in, as an asteroid starts)
        val start = ConstPoint(Random.nextInt(-200..600), 790)
        shot.setInitialPath(start, shot.target.pixelCentre)
        ship.projectiles.add(shot)
    }

    fun saveToXML(elem: Element) {
        val asbElem = Element("antiShipBattery")
        SaveUtil.addAttrFloat(asbElem, "timeToShot", timeToShot)
        SaveUtil.addAttrBool(asbElem, "nextAtPlayer", nextAtPlayer)
        elem.addContent(asbElem)
    }

    fun loadFromXML(elem: Element) {
        val asbElem = elem.getChild("antiShipBattery") ?: return
        timeToShot = SaveUtil.getAttrFloat(asbElem, "timeToShot")
        nextAtPlayer = SaveUtil.getAttrBool(asbElem, "nextAtPlayer")
        hasPlayedWarning = showWarning
    }

    companion object {
        // (four times as long as first thought: a shot every minute or so)
        const val FIRST_SHOT_TIME = 30f
        const val MIN_PERIOD = 55f
        const val MAX_PERIOD = 70f
        const val WARNING_TIME = 3f
    }
}

/**
 * An Anti-Ship Battery's shot (see [AntiShipBattery]): through the shields
 * and a Zoltan super shield too, always breaching the hull where it hits,
 * and shot down by defence drones, as a missile is.
 */
class AsbProjectile(target: Room) : AbstractWeaponProjectile(TYPE, target) {
    override val defaultSpeed: Int get() = 45

    override val isMissileForDD: Boolean get() = true

    // A planet's battery is far too strong for a Zoltan shield to stop
    override val piercesSuperShield: Boolean get() = true

    // It picks ships still fighting itself (see AntiShipBattery.fire), so
    // the rule that shots at a ship that's given up miss doesn't apply
    override val missDeadEnemies: Boolean get() = false

    // Its blueprint isn't in the blueprint manager (see AsteroidProjectile)
    override val serialisationType: String get() = SERIALISATION_TYPE

    private val hitHullSound: FTLSound = ship.sys.sounds.getSample("hitHull${(1..3).random()}")
    private val hitShieldSound: FTLSound = ship.sys.sounds.getSample("hitShield${(1..3).random()}")

    init {
        drawUnderShip = false
    }

    override fun renderPreTranslated(g: Graphics) {
        // Vanilla's ASB shot, pointing right (along its direction)
        val sprite = ship.sys.animations["pds"].spriteAt(ship.sys, 0)
        val size = sprite.imageSize
        sprite.draw(ConstPoint(-size.x / 2, -size.y / 2))
    }

    override fun hitHull() {
        super.hitHull()
        hitHullSound.play()
    }

    override fun hitShields() {
        super.hitShields()
        hitShieldSound.play()
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)
        SaveUtil.addRoomRef(elem, "target", refs, target)
    }

    companion object {
        const val SERIALISATION_TYPE = "antiShipBattery"

        private val TYPE: AbstractWeaponBlueprint

        init {
            fun addElem(elem: Element, name: String, value: String) {
                val content = Element(name)
                content.text = value
                elem.addContent(content)
            }

            // As with AsteroidProjectile's
            val elem = Element("weaponBlueprint")
            elem.setAttribute("name", "!!INTERNAL Anti-Ship Battery")
            addElem(elem, "explosion", "explosion1")
            addElem(elem, "weaponArt", "<<ASB - NONE>>")
            addElem(elem, "damage", "2")
            // Through any shields, as a missile
            addElem(elem, "sp", "5")
            addElem(elem, "fireChance", "1")
            // (always breaching the hull, out of 10)
            addElem(elem, "breachChance", "10")

            TYPE = MissileBlueprint(elem)
        }

        fun loadFromXML(elem: Element, refs: RefLoader, callback: ProjectileLoadCallback) {
            SaveUtil.getRoomRef(elem, "target", refs) { target ->
                val projectile = AsbProjectile(target)
                projectile.loadPropertiesFromXML(elem, refs)
                callback(projectile)
            }
        }
    }
}
