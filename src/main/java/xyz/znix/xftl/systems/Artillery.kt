package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.*
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.Skill
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.ShipBlueprint
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.weapons.AbstractProjectileWeaponInstance
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import xyz.znix.xftl.weapons.BeamBlueprint
import kotlin.math.max
import kotlin.math.roundToInt

class Artillery(blueprint: SystemBlueprint) : MainSystem(blueprint) {

    private lateinit var weapon: AbstractWeaponInstance

    // Charging appears to work in vanilla based on preserving your charge
    // progress fraction while changing power. This means that if you charge
    // the bar to half way on one power then finish it on four power, it's
    // going to take the average of 50 and 20 seconds.
    private var chargeProgress: Float = 0f
        set(value) {
            field = value.coerceIn(0f..1f)
        }

    override val sortingType: SortingType get() = SortingType.ARTILLERY

    override val title: GameText? get() = weapon.type.title
    override val description: GameText? get() = weapon.type.desc

    private val barImages: List<Image> by onInit { game ->
        // (vanilla's four, and the mod's five and six, see COOLDOWN_FACTORS)
        (1..COOLDOWN_FACTORS.size).map { game.getImg("img/systemUI/button_artillery_$it.png") }
    }

    // The blueprint's cooldown is correct for a level-2 artillery system.
    // Someone manning it takes off half what they'd take off a weapons
    // system's (see manningBonus).
    private val cooldown: Float get() = weapon.chargeTime * cooldownFactor(powerSelected) * (1f - manningBonus / 100f)

    /**
     * How much faster it charges, in percent, while it's manned (an XFTL
     * extension; vanilla's artillery can't be): half of what the
     * crewmember's Weapons skill gives a weapons system, 5% at the least
     * and 12.5% at the most. Its shots train the skill.
     */
    val manningBonus: Float
        get() = getSkillLevel(Skill.WEAPONS)?.let { Weapons.SKILL_BONUSES[it.level] * MANNING_SHARE } ?: 0f

    override fun drawManningIcon(g: Graphics, x: Int, y: Int) {
        drawManningSkillIcon(x, y, getSkillLevel(Skill.WEAPONS))
    }

    private val hardpoint by lazy {
        // Find out what our index is, in all the artillery systems specified
        // in the ship's XML.
        // Note we can't use the ship's list of artillery systems, in case
        // (in a mod) there's a ship where some artillery systems have to be
        // purchased, in which case the index in the purchased systems might
        // not match that in the XML.
        val allSystemSlots = ship.rooms.flatMap { it.systemSlots }
        val artillerySlots = allSystemSlots.filter { it.system.type == INFO.name }.sortedBy { it.spec.systemIndex }
        val index = artillerySlots.indexOf(configuration)
        require(index != -1) { "Artillery system is not in ship systems list!" }

        // Hardpoints 4 and up (zero-indexed) are used for artillery.
        // If there's more than four regular weapons however, it starts
        // after the last weapon hardpoint. An artillery the ship's bought
        // (see BuyableArtillery) on a ship without a mount for it fires from
        // its room, without its gun drawn.
        ship.hardpoints.getOrNull(max(4, ship.weaponSlots ?: 4) + index)?.spec
            ?: ShipBlueprint.ParsedHardpoint(configuration.room.pixelCentre, false, false, 0, null).also { mounted = false }
    }

    // Whether its gun's on a mount of the ship's (see hardpoint), and drawn
    private var mounted = true

    override fun initialise(ship: Ship) {
        super.initialise(ship)

        val weaponBlueprint = ship.sys.blueprintManager[configuration.spec.weapon!!] as AbstractWeaponBlueprint
        weapon = weaponBlueprint.buildInstance(ship)
        weapon.bindToArtillery(this)
    }

    override fun update(dt: Float) {
        super.update(dt)

        if (powerSelected <= 0) {
            chargeProgress -= dt / DISCHARGE_TIME
            pendingTargets.clear()

            // Make the beam disappear if we were shooting when we were turned off.
            weapon.forceSetPowered(false)
            weapon.update(dt, dt, false)

            return
        }

        var amount = dt / cooldown
        if (ship.sys.debugFlags.fastWeaponCharge.set)
            amount *= 5
        if (ship.opponentCloakActive)
            amount = 0f
        chargeProgress += amount * (1f + ship.getAugmentValue(AugmentBlueprint.AUTOMATED_RELOADERS))

        // The rest of the last volley's ships, one at a time, as the weapon's
        // done firing at the one before
        if (pendingTargets.isNotEmpty() && !weapon.isFiring) {
            val next = pendingTargets.removeFirst()
            if (isStillATarget(next))
                fireAt(next)
        }

        if (chargeProgress >= 1f) {
            val targets = nextTargets()
            if (targets.isNotEmpty()) {
                fireAt(targets.first())
                pendingTargets.clear()
                pendingTargets.addAll(targets.drop(1))
                chargeProgress = 0f
                addSkillPoint(Skill.WEAPONS)
            }
        }

        weapon.forceSetPowered(true)
        weapon.update(dt, dt, true)

        // Set the weapon's time charged to match our charge progress. This is
        // so the flagship's weapons show their usual charging animation.
        weapon.timeCharged = weapon.chargeTime * chargeProgress
    }

    override fun drawBackground(g: Graphics) {
        super.drawBackground(g)

        // Draw the weapon launcher animation (not for one fired from its
        // room, see hardpoint)
        hardpoint // (finding its mount)
        if (!mounted)
            return
        g.pushTransform()

        // As it's shown, which may be a disguise (see Mirage)
        val anim = weapon.shownAnimation

        Weapons.translateHardpoint(g, hardpoint)
        g.translate(-anim.mountPoint.x.f, -anim.mountPoint.y.f)

        weapon.render(g)

        Weapons.drawEnemyChargeBar(ship, weapon, hackedBy?.isPoweredUp == true, isHackActive)

        g.popTransform()
    }

    override fun drawIconAndPower(
        game: InGameState,
        g: Graphics,
        isPlayer: Boolean,
        drawPower: Boolean,
        hoverGlow: Boolean,
        x: Int,
        y: Int
    ) {
        super.drawIconAndPower(game, g, isPlayer, drawPower, hoverGlow, x, y)

        if (!isPlayer)
            return

        val boxX = x + 22 - 6
        val boxY = y - 53 - 6

        val imageId = max(0, powerSelected - 1)

        // Draw the image of the box the charge bar sits in
        barImages[imageId].draw(boxX, boxY)

        val barX = boxX + 9
        val barBaseY = boxY + 59

        // one pixel per second of charging, at vanilla's 40 seconds at two bars
        val maxHeight = (40 * cooldownFactor(imageId + 1)).roundToInt()
        val barHeight = (chargeProgress * maxHeight).toInt()

        g.colour = Colour.white
        g.fillRect(barX.f, barBaseY.f - barHeight, 5f, barHeight.f)
    }

    override fun onJump() {
        super.onJump()

        chargeProgress = 0f
        pendingTargets.clear()
    }

    // The ships still to be shot at in the last volley (see nextTargets)
    private val pendingTargets = ArrayDeque<Ship>()

    /**
     * The ships the next volley's for: an enemy's at the player; the
     * player's take the enemy ships still fighting in turn, each of the
     * player's volleys (from any of its artillery) starting at the next one,
     * so with several artillery systems and several enemies they're all shot
     * at. At five bars, a volley's at two of them, and at six at three (see
     * [SHIPS_PER_VOLLEY]), if there are that many, fired one after another.
     */
    private fun nextTargets(): List<Ship> {
        if (!ship.isPlayerShip)
            return listOfNotNull(ship.sys.getEnemyOf(ship))
        val targets = playerTargets()
        if (targets.isEmpty())
            return emptyList()
        val first = ship.artilleryVolleys++ % targets.size
        val count = shipsPerVolley(powerSelected).coerceAtMost(targets.size)
        return (0 until count).map { targets[(first + it) % targets.size] }
    }

    private fun playerTargets(): List<Ship> =
        ship.sys.opponents.filter { it.hostile && !it.ship.isGone }.map { it.ship }

    // Whether a ship left in the volley's still there to shoot at
    private fun isStillATarget(target: Ship): Boolean =
        if (ship.isPlayerShip) target in playerTargets() else target == ship.sys.getEnemyOf(ship)

    private fun fireAt(targetShip: Ship) {
        if (ship.sys.debugFlags.noEnemyFire.set && !ship.isPlayerShip)
            return

        when (val weapon = this.weapon) {
            is BeamBlueprint.BeamInstance -> {
                val startRoom = targetShip.rooms.random()
                val aim = weapon.buildLongestAim(startRoom)

                weapon.fire(aim)
            }

            // This handles flak, missiles, and lasers.
            is AbstractProjectileWeaponInstance -> {
                // The firing position won't not be right for the player ship (because
                // of the weapon rotation) if there's a non-zero weapon position,
                // but it'll be fine for vanilla.
                val anim = weapon.animation
                val firePos = hardpoint.position - anim.mountPoint + anim.firePoint

                weapon.fireFromArtillery(targetShip.rooms, firePos)
            }

            else -> error("Artillery system doesn't support weapon ${weapon.type.name} instance $weapon")
        }
    }

    override fun saveSystem(elem: Element, refs: ObjectRefs) {
        SaveUtil.addAttrFloat(elem, "charge", chargeProgress)

        val weaponElem = Element("weapon")
        weapon.saveToXML(weaponElem, refs)
        elem.addContent(weaponElem)
    }

    override fun loadSystem(elem: Element, refs: RefLoader) {
        chargeProgress = SaveUtil.getAttrFloat(elem, "charge")

        val weaponElem = elem.getChild("weapon")
        weapon.loadFromXML(weaponElem, refs)
    }

    companion object {
        private const val DISCHARGE_TIME = 10f

        /** The most bars vanilla's artillery has, which the enemies' keep to (see ShipGenerator). */
        const val VANILLA_MAX_POWER = 4

        /** The share of the Weapons skill's bonus that manning it gives (see [manningBonus]). */
        const val MANNING_SHARE = 0.5f

        /**
         * How long the charge takes at each number of bars, from one, as a
         * share of the weapon's: vanilla's 1.25 to 0.5 (a quarter less a
         * bar), then the mod's five and six bars, which the player's can be
         * upgraded to, taking less off each (17 and 14 seconds rather than
         * 20, for the Federation's).
         */
        val COOLDOWN_FACTORS = listOf(1.25f, 1f, 0.75f, 0.5f, 0.42f, 0.36f)

        fun cooldownFactor(power: Int): Float = COOLDOWN_FACTORS[(power - 1).coerceIn(0, COOLDOWN_FACTORS.size - 1)]

        /**
         * How many of the enemy ships each volley's at, at each number of
         * bars, from one: one, as vanilla's, up to four bars, then two at the
         * mod's five, and three at six (only the player's go past four).
         */
        val SHIPS_PER_VOLLEY = listOf(1, 1, 1, 1, 2, 3)

        fun shipsPerVolley(power: Int): Int = SHIPS_PER_VOLLEY[(power - 1).coerceIn(0, SHIPS_PER_VOLLEY.size - 1)]

        val INFO: SystemInfo = ArtilleryInfo
    }
}

private object ArtilleryInfo : SystemInfo("artillery") {
    // (an XFTL extension, see Artillery.manningBonus)
    override val canBeManned: Boolean get() = true

    override fun create(blueprint: SystemBlueprint) = Artillery(blueprint)

    override fun getLevelName(level: Int, translator: Translator): String {
        return when (level) {
            0 -> translator["artillery_1"]
            1 -> translator["artillery_2"]
            2 -> translator["artillery_3"]
            3 -> translator["artillery_4"]
            // (the mod's two more levels, see COOLDOWN_FACTORS: as
            // vanilla's, for a 40-second weapon, and at two and three ships,
            // see SHIPS_PER_VOLLEY)
            4 -> translator["xftl_artillery_5"]
            5 -> translator["xftl_artillery_6"]
            else -> "INVALID LEVEL ${level + 1}"
        }
    }
}
