package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.*
import xyz.znix.xftl.crew.Skill
import xyz.znix.xftl.game.ShipBlueprint
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.utils.WeaponPowerManager
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.IRoomTargetingWeapon
import xyz.znix.xftl.weapons.ShadowTargets
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class Weapons(blueprint: SystemBlueprint) : MainSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.WEAPONS

    val selectedTargets = TargetList()

    /**
     * The indices of the ship's hardpoints that this system controls.
     *
     * Ships normally have a single weapons system, which controls all the
     * hardpoints. A ship can have several though, in which case each one
     * controls its own share of the weapon slots. This is set by
     * [Ship.updateAvailableSystems].
     *
     * Note these are indices into [Ship.hardpoints], which are also used
     * as the weapon IDs for targeting.
     */
    var hardpointIndices: IntRange = IntRange.EMPTY
        set(value) {
            if (field == value)
                return
            field = value
            _powerManager = null
        }

    /**
     * The slots that should be shown in the UI. This is [hardpointIndices],
     * limited to the ship's weapon slots (the hardpoints past those are
     * used for artillery).
     */
    val uiSlots: IntRange
        get() {
            val slots = ship.weaponSlots ?: ship.hardpoints.size
            return hardpointIndices.first..min(hardpointIndices.last, slots - 1)
        }

    private var _powerManager: WeaponPowerManager? = null

    // The power manager is sized by the number of hardpoints we control,
    // so rebuild it if that changes.
    val powerManager: WeaponPowerManager
        get() {
            _powerManager?.let { return it }
            val manager = WeaponPowerManager(this, WeaponPowerAccess())
            _powerManager = manager
            return manager
        }

    private val ownedHardpoints: List<Ship.Hardpoint>
        get() = hardpointIndices.map { ship.hardpoints[it] }

    fun ownsHardpoint(index: Int): Boolean = index in hardpointIndices

    fun ownsWeapon(weapon: AbstractWeaponInstance): Boolean {
        return ownedHardpoints.any { it.weapon == weapon }
    }

    override fun update(dt: Float) {
        super.update(dt)

        var chargeMult = when (ship.sys.debugFlags.fastWeaponCharge.set) {
            true -> 10f
            false -> 1f
        }

        // When hacked, the weapons charge backwards at the same
        // speed as they charge normally.
        if (isHackActive) {
            chargeMult *= -1
        } else {
            // Don't let manning speed up discharging the weapons.

            val manningChargeSub = getSkillLevel(Skill.WEAPONS)?.let { SKILL_BONUSES[it.level] / 100f } ?: 0f

            // This gets applied weirdly - 1-manningChargeSub is multiplied to the
            // weapon charge time, which alternatively means dividing
            // the charge multiplier by it.
            chargeMult /= 1f - manningChargeSub
        }

        val chargeTime = dt * chargeMult

        for (hp in ownedHardpoints) {
            val weapon = hp.weapon ?: continue
            weapon.bindToWeaponsSystem(this)
            // As in vanilla, a weapon doesn't charge while the ship it's
            // aimed at is cloaked (and none do while every ship the player's
            // fighting is, see opponentCloakActive)
            val aimedAtCloaked = selectedTargets.targetShipOf(ship.hardpoints.indexOf(hp))?.isCloakActive == true
            weapon.update(dt, chargeTime, !ship.opponentCloakActive && !aimedAtCloaked)

            // Out of missiles, it turns off, but not until it's finished
            // firing: the shot that used the last one is still to leave (the
            // missile's taken as it starts firing), and turning it off would
            // stop it there
            if (!weapon.hasEnoughMissiles && !weapon.isFiring)
                setWeaponPower(weapon, false)

            // Update the weapon slide
            val slideSpeed = dt * 2
            if (weapon.isPowered) {
                weapon.slide = (weapon.slide + slideSpeed).coerceAtMost(1f)
            } else {
                weapon.slide = (weapon.slide - slideSpeed).coerceAtLeast(0f)
            }
        }

        selectedTargets.update()
    }

    override fun drawBackground(g: Graphics) {
        for (shipHardpoint in ownedHardpoints) {
            val weapon = shipHardpoint.weapon ?: continue
            val hp = shipHardpoint.spec

            g.pushTransform()

            run {
                // As it's shown, which may be a disguise (see Mirage)
                val anim = weapon.shownAnimation

                // Apply the slide
                if (hp.slide != null) {
                    val dist = (12 * (weapon.slide - 1)).roundToInt()
                    g.translate(hp.slide.x * dist.f, hp.slide.y * dist.f)
                }

                // We start in ship space - x and y are relative to the hull

                translateHardpoint(g, hp)

                // We are now in image space - any given XY value here will line up to the pixel
                // with the same XY on the launcher image.

                g.translate(-anim.mountPoint.x.f, -anim.mountPoint.y.f)

                // A combined weapon is drawn once for each of its copies,
                // side by side (see WeaponSizes)
                val offsets = weapon.type.copyOffsets(anim)
                for ((index, offset) in offsets.withIndex()) {
                    weapon.renderingCopy = offsets.size - 1 - index
                    weapon.renderingCopyOffset = offset
                    weapon.renderingFrontOffset = offsets.last()
                    g.pushTransform()
                    g.translate(offset.x.f, offset.y.f)
                    weapon.render(g)
                    g.popTransform()
                }
                weapon.renderingCopy = 0
                weapon.renderingCopyOffset = ConstPoint.ZERO
                weapon.renderingFrontOffset = ConstPoint.ZERO

                drawEnemyChargeBar(ship, weapon, hackedBy?.isPoweredUp == true, isHackActive)
            }

            g.popTransform()
        }
    }

    fun findHardpoint(weapon: AbstractWeaponInstance): Ship.Hardpoint {
        for (hp in ship.hardpoints) {
            if (hp.weapon == weapon)
                return hp
        }

        throw IllegalArgumentException("No matching hardpoint for weapon $weapon")
    }

    /**
     * Get the Zoltan power going into a weapon, by its hardpoint index.
     */
    fun getWeaponForcedPower(hardpointIndex: Int): Int {
        return powerManager.getForcedPower(hardpointIndex - hardpointIndices.first)
    }

    fun getProjectileSpawnPos(weapon: AbstractWeaponInstance, firePoint: IPoint): IPoint {
        val hp = findHardpoint(weapon).spec
        val anim = weapon.animation

        // Fly off-screen, so it jumps over to the target ship.

        // Find the exact position to start at.
        // Take the difference between the fire and mounting positions
        // on the animation. The latter, when converted into ship-space,
        // is equal to hp.position.
        val startPos = Point(0, 0)
        startPos += firePoint
        startPos -= anim.mountPoint

        // Convert from hardpoint-space to ship-space.
        // This does everything that translateHardpoint does, but backwards
        // since matrix transforms (which the Slick operations in
        // translateHardpoint are) apply the last operation first.
        if (hp.mirror) {
            startPos.x *= -1
        }
        if (hp.rotate) {
            // To rotate counter-clockwise, set:
            // Y to X (so a right-hand line points up)
            // X to -Y (so an upwards line points left)
            val tmp = startPos.x
            startPos.x = -startPos.y
            startPos.y = tmp
        }
        startPos += hp.position

        return startPos
    }

    override fun powerStateChanged() {
        super.powerStateChanged()

        // Don't adjust the power if the weapons haven't yet been loaded.
        if (ship.sys.isCurrentlyLoadingSave)
            return

        powerManager.powerStateChanged()
    }

    // The items it had on come back after a repair, rather than a level
    override val restoresByLevel: Boolean get() = false

    override fun powerLimitChanged() {
        super.powerLimitChanged()
        powerManager.restorePower()
    }

    override fun increasePower() {
        powerManager.increasePower()
    }

    override fun decreasePower() {
        powerManager.decreasePower()
    }

    override fun drawManningIcon(g: Graphics, x: Int, y: Int) {
        drawManningSkillIcon(x, y, getSkillLevel(Skill.WEAPONS))
    }

    /**
     * Turns a weapon on or off.
     *
     * Returns true if successful.
     */
    fun setWeaponPower(weapon: AbstractWeaponInstance, newPower: Boolean): Boolean {
        val idx = ship.hardpoints.indexOfFirst { it.weapon == weapon }
        require(ownsHardpoint(idx)) { "Weapon $weapon isn't controlled by weapons system in room ${room?.id}" }
        return powerManager.setItemPower(idx - hardpointIndices.first, newPower)
    }

    private fun onWeaponFired() {
        addSkillPoint(Skill.WEAPONS)
    }

    // The weapons are all serialised individually by the ship, we only
    // have to serialise the selected targets.
    override fun saveSystem(elem: Element, refs: ObjectRefs) {
        for (target in selectedTargets) {
            val targetElem = Element("target")
            target.saveToXML(targetElem, refs)
            elem.addContent(targetElem)
        }
    }

    override fun loadSystem(elem: Element, refs: RefLoader) {
        val getWeapon: (Int) -> AbstractWeaponInstance = { ship.hardpoints[it].weapon!! }

        for (targetElem in elem.getChildren("target")) {
            SelectedTarget.loadFromXML(targetElem, refs, getWeapon) { target ->
                when (target) {
                    is SelectedTarget.RoomAim -> selectedTargets.targetRoom(target.weaponNumber, target.room, target.autofire)
                    is SelectedTarget.BeamAim -> selectedTargets.targetBeam(target.weaponNumber, target)
                }
            }
        }
    }

    inner class TargetList {
        /**
         * The ship the weapon in [weaponId] is aimed at, if it's aimed.
         */
        fun targetShipOf(weaponId: Int): Ship? = targets[weaponId]?.targetShip

        /**
         * For a weapon that's aimed at a specific room (notably not beams),
         * target it onto a given room. With [autofire], it keeps firing at
         * that room each time it's charged, rather than just once.
         */
        fun targetRoom(weaponId: Int, room: Room, autofire: Boolean = false) {
            val weapon = ship.hardpoints[weaponId].weapon!!
            check(weapon is IRoomTargetingWeapon)

            val target = SelectedTarget.RoomAim(room, weapon, weaponId)
            target.autofire = autofire
            // A combined weapon's other copies aim at the other ships (only
            // for the player, who's fighting several)
            if (ship == ship.sys.player)
                target.shadowRooms += ShadowTargets.pick(ship.sys, room, weapon.type.combinedCount)
            targets[weaponId] = target
        }

        fun targetBeam(weaponId: Int, beam: SelectedTarget.BeamAim) {
            val weapon = ship.hardpoints[weaponId].weapon!!
            require(weapon == beam.weapon)

            targets[weaponId] = beam
        }

        fun unTarget(weaponId: Int) {
            targets.remove(weaponId)
        }

        operator fun iterator(): MutableIterator<SelectedTarget> {
            return targets.values.iterator()
        }

        fun getTarget(weaponId: Int): SelectedTarget? {
            return targets[weaponId]
        }

        fun update() {
            // Un-target all unpowered weapons
            selectedTargets.targets.values.removeIf { !it.weapon.isPowered }

            // Drop the shadow targets on ships that have been destroyed or
            // given up (that copy fires at the main target instead)
            for (target in targets.values) {
                if (target is SelectedTarget.RoomAim)
                    target.shadowRooms.removeIf { !ShadowTargets.isStillValid(ship.sys, it) }
            }
        }

        fun fireChargedWeapons() {
            var fired: MutableList<SelectedTarget>? = null

            for (tgt in this) {
                // There's some weapons (eg the charge laser) that will be ready
                // before they've finished firing when the fast weapon charge
                // debug flag is set. Thus block them here to avoid crashing.
                val weapon = tgt.weapon.asWeaponInstance()
                if (!weapon.isCharged || weapon.isFiring)
                    continue

                // A cloaked ship can be aimed at, but not fired at: the
                // weapon keeps its target, and fires once the cloak's gone
                // (what's already on its way when it cloaks still flies on)
                if (tgt.targetShip.isCloakActive && tgt.targetShip != ship)
                    continue

                if (fired == null)
                    fired = ArrayList()

                fired.add(tgt)

                when (tgt) {
                    is SelectedTarget.BeamAim -> tgt.beamWeapon.fire(tgt)
                    // (a copy aimed at another ship that's cloaked fires at
                    // the main target instead)
                    is SelectedTarget.RoomAim -> tgt.roomTargetingWeapon.fireCombined { copy ->
                        tgt.roomForCopy(copy).takeUnless { it.ship.isCloakActive } ?: tgt.room
                    }
                }

                this@Weapons.onWeaponFired()
            }

            // Weapons on autofire keep their target, to fire at it again
            // once they've charged
            fired?.forEach { tgt ->
                if (!tgt.autofire)
                    unTarget(tgt.weaponNumber)
            }
        }

        private val targets = HashMap<Int, SelectedTarget>()

        // If the user is currently aiming a beam, this marks it so the ship being targeted
        // can draw it. It's a bit ugly, but keeps all the target-drawing stuff in one place.
        var beamAiming: SelectedTarget.BeamAim? = null
    }

    // The slots here are relative to the start of hardpointIndices.
    private inner class WeaponPowerAccess : WeaponPowerManager.ItemAccess {
        // Fixed when this is created, since WeaponPowerManager sizes its
        // arrays from it. The power manager is rebuilt if the range changes.
        private val firstIndex = hardpointIndices.first
        override val count: Int = hardpointIndices.count()

        private fun hardpoint(slot: Int) = ship.hardpoints[firstIndex + slot]

        override fun hasItem(slot: Int): Boolean {
            return hardpoint(slot).weapon != null
        }

        override fun getItemPowerDraw(slot: Int): Int {
            return hardpoint(slot).weapon?.type?.power ?: 0
        }

        override fun isItemPowered(slot: Int): Boolean {
            return hardpoint(slot).weapon?.isPowered ?: false
        }

        override fun setItemPowered(slot: Int, powered: Boolean) {
            val weapon = hardpoint(slot).weapon ?: return

            // If a weapon runs out of missiles, it turns off even if Zoltan-powered.
            if (weapon.hasEnoughMissiles) {
                weapon.forceSetPowered(powered)
            } else {
                weapon.forceSetPowered(false)
            }
        }

        override fun setSystemPower(level: Int): Boolean {
            return this@Weapons.setSystemPower(level)
        }
    }

    companion object {
        val INFO: SystemInfo = WeaponsInfo
        // For each skill level (see SkillLevel; vanilla's three were 10, 15 and 20)
        val SKILL_BONUSES = listOf(10, 13, 16, 19, 22, 25)

        fun translateHardpoint(g: Graphics, hp: ShipBlueprint.ParsedHardpoint) {
            g.translate(hp.position.x.f, hp.position.y.f)

            // We are now in hardpoint-xy space - x and y are relative to the hardpoint's xy,
            // but rotation is independant

            if (hp.rotate)
                g.rotate(0f, 0f, 90f)

            // We are now properly in hardpoint space, y+ is always forward and y- is always backwards

            if (hp.mirror) {
                g.scale(-1f, 1f)
            }
        }

        fun drawEnemyChargeBar(
            ship: Ship,
            weapon: AbstractWeaponInstance,
            passiveHacking: Boolean,
            activeHacking: Boolean
        ) {
            // The bars are only drawn on enemy weapons
            if (ship.isPlayerShip)
                return

            // If the weapon is off, it's charge bar is instantly hidden.
            if (!weapon.isPowered)
                return

            // Nor on a ship that's given up or lost its crew, as its
            // weapons are out of the fight (though the player's sensors
            // keep watching it while their boarders are aboard)
            if (ship.isAtPeace)
                return

            // Make sure the player has high enough level sensors to see the bar
            val game = ship.sys
            if (
                game.player.sensorsFor(ship)?.showsEnemyWeaponCharge != true
                && !passiveHacking
                && !game.debugFlags.showEverything.set
            )
                return

            val enemyChargeBox = game.getImg("img/combatUI/box_hostiles_weapon_charge.png")
            val enemyChargeBar = game.getImg("img/combatUI/box_hostiles_weapon_chargebar.png")

            // Draw the charge bar on enemy ships when the player has
            // improved sensors.
            // These are level with the top of the weapon, and 10px out.
            val barX = weapon.animation.sheet.frameWidth + 10
            val barColour = when (activeHacking) {
                true -> Constants.SYSTEM_HACKED
                false -> Constants.SYS_ENERGY_REPAIR
            }
            enemyChargeBox.draw(barX, 0, barColour)

            val height = (enemyChargeBar.height * weapon.chargeProgress).roundToInt()
            val barY = 2 + enemyChargeBar.height - height
            enemyChargeBar.draw(barX + 2f, barY.f, enemyChargeBar.width.f, height.f, barColour)
        }
    }
}

private object WeaponsInfo : SystemInfo("weapons") {
    override val canBeManned: Boolean get() = true

    override fun create(blueprint: SystemBlueprint) = Weapons(blueprint)

    override fun getLevelName(level: Int, translator: Translator): String {
        return translator["system_power"]
    }
}

sealed class SelectedTarget(val weapon: AbstractWeaponInstance, val weaponNumber: Int) {
    abstract val targetShip: Ship

    /**
     * If true, the weapon keeps firing at this target each time it's
     * charged, until the player aims it somewhere else or turns it off.
     * Otherwise, it fires once and forgets the target.
     */
    var autofire: Boolean = false

    class RoomAim(val room: Room, weapon: IRoomTargetingWeapon, weaponNumber: Int) :
        SelectedTarget(weapon.asWeaponInstance(), weaponNumber) {

        val roomTargetingWeapon get() = weapon as IRoomTargetingWeapon
        override val targetShip: Ship get() = room.ship

        /**
         * For a combined weapon, the rooms its other copies fire at, in the
         * other ships (see [ShadowTargets]), copy 1 first.
         */
        val shadowRooms = ArrayList<Room>()

        /**
         * The room a combined weapon's [copy] fires at: its shadow target,
         * or the main target if it hasn't one.
         */
        fun roomForCopy(copy: Int): Room = if (copy == 0) room else shadowRooms.getOrNull(copy - 1) ?: room
    }

    class BeamAim(
        weapon: BeamBlueprint.BeamInstance,
        weaponId: Int,
        override val targetShip: Ship,
        val startShipPoint: IPoint
    ) : SelectedTarget(weapon, weaponId) {
        val hitRooms = ArrayList<Room>()
        var angle: Float = 0f

        /**
         * True if the beam is valid. This is only ever false for the beam
         * the player is currently aiming.
         */
        var visible: Boolean = false

        /**
         * The length of this beam swipe.
         *
         * This is lowered by AI-controlled attacks, which end before
         * the beam goes off the side of the screen.
         */
        var length = (weapon.type as BeamBlueprint).length

        val beamWeapon get() = weapon as BeamBlueprint.BeamInstance

        /**
         * Update the rooms the beam will hit, based on [angle].
         */
        fun updateHitRooms() {
            hitRooms.clear()
            visible = true

            // Loop over the pixels, marking whenever we cross one of the lines of the
            // grid all the enemy rooms are placed on.
            val lastPoint = Point(-100, -100)
            var lastRoom: Room? = null
            val tmpPoint = Point(ConstPoint.ZERO)
            for (i in 0 until length) {
                tmpPoint.x = startShipPoint.x + (i * cos(angle)).roundToInt()
                tmpPoint.y = startShipPoint.y + (i * sin(angle)).roundToInt()

                // This (among other things) divides the position by the size
                // of a room, so whenever the result changes we might
                // be in a new room.
                targetShip.screenPosToShipPos(tmpPoint)

                if (tmpPoint == lastPoint)
                    continue
                lastPoint.set(tmpPoint)

                val roomPoint = targetShip.shipToRoomPos(tmpPoint) ?: continue

                // We might still be inside the same room, however.
                if (roomPoint.room == lastRoom)
                    continue
                lastRoom = roomPoint.room

                hitRooms.add(roomPoint.room)
            }
        }
    }

    fun saveToXML(elem: Element, refs: ObjectRefs) {
        SaveUtil.addAttrInt(elem, "hardpoint", weaponNumber)
        SaveUtil.addAttr(elem, "targetShip", refs[targetShip])
        if (autofire)
            SaveUtil.addAttrBool(elem, "autofire", true)

        when (this) {
            is RoomAim -> {
                SaveUtil.addAttr(elem, "type", "room")
                SaveUtil.addAttrInt(elem, "roomId", room.id)
                for ((i, shadow) in shadowRooms.withIndex()) {
                    if (refs.contains(shadow.ship))
                        SaveUtil.addRoomRef(elem, "shadow$i", refs, shadow)
                }
            }

            is BeamAim -> {
                require(visible)
                SaveUtil.addAttr(elem, "type", "beam")
                SaveUtil.addPoint(elem, "startPos", startShipPoint)
                SaveUtil.addAttrFloat(elem, "angle", angle)
                SaveUtil.addAttrInt(elem, "length", length)
            }
        }
    }


    companion object {
        fun loadFromXML(
            elem: Element, refs: RefLoader,
            getWeapon: (Int) -> AbstractWeaponInstance,
            onLoaded: (SelectedTarget) -> Unit
        ) {
            val hardpointIndex = SaveUtil.getAttrInt(elem, "hardpoint")
            val targetShipRef = SaveUtil.getAttr(elem, "targetShip")
            val autofire = elem.getAttributeValue("autofire")?.toBoolean() ?: false

            val type = SaveUtil.getAttr(elem, "type")
            when (type) {
                "room" -> {
                    val roomId = SaveUtil.getAttrInt(elem, "roomId")

                    refs.asyncResolve(Ship::class.java, targetShipRef) { targetShip ->
                        val weapon = getWeapon(hardpointIndex)
                        require(weapon is IRoomTargetingWeapon)

                        val room = targetShip!!.rooms[roomId]
                        val target = RoomAim(room, weapon, hardpointIndex)
                        target.autofire = autofire
                        var i = 0
                        while (elem.getChild("shadow$i") != null) {
                            SaveUtil.getRoomRef(elem, "shadow$i", refs) { target.shadowRooms += it }
                            i++
                        }
                        onLoaded(target)
                    }
                }

                "beam" -> {
                    val startPos = SaveUtil.getPoint(elem, "startPos")
                    val angle = SaveUtil.getAttrFloat(elem, "angle")
                    val length = SaveUtil.getAttrInt(elem, "length")

                    refs.asyncResolve(Ship::class.java, targetShipRef) { targetShip ->
                        val weapon = getWeapon(hardpointIndex)
                        require(weapon is BeamBlueprint.BeamInstance)

                        val target = BeamAim(weapon, hardpointIndex, targetShip!!, startPos)
                        target.angle = angle
                        target.length = length
                        target.updateHitRooms()
                        target.autofire = autofire
                        onLoaded(target)
                    }
                }

                else -> {
                    error("Unknown weapon target type '$type'")
                }
            }
        }
    }
}
