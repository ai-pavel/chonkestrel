package xyz.znix.xftl.crew

import org.jdom2.Element
import xyz.znix.xftl.Animations
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.game.AchievementGoal
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import kotlin.math.max

class CrewCrystal(blueprint: CrewBlueprint, animations: Animations, room: Room, mode: SlotType) :
    LivingCrew(blueprint, animations, room, mode) {

    override val suffocationMultiplier: Float
        get() {
            val oxygenMasks = getAugmentValue(AugmentBlueprint.OXYGEN_MASKS) ?: 1f
            return 0.5f * oxygenMasks
        }

    override val movementSpeed: Float get() = BASE_MOVEMENT_SPEED * 0.8f
    override val maxHealth: Float get() = 125f
    override val fireFightingSpeed: Float get() = 0.83f

    /** How long until its lockdown can be used again, or 0 once it's ready (see [lockDown]). */
    var lockdownCooldown: Float = 0f
        private set

    /** Whether it can lock its room down now. */
    val canLockDown: Boolean
        get() = lockdownCooldown == 0f && health > 0f && currentAction != Action.DYING &&
                !isTeleporting && stunTime == 0f

    /**
     * Its ability: lock the room it's in down (see [Room.lockDown]), so no
     * one gets in or out for [LOCKDOWN_TIME] seconds, then wait
     * [LOCKDOWN_COOLDOWN] seconds to do it again. Returns false if it
     * can't yet.
     */
    fun lockDown(): Boolean {
        if (!canLockDown)
            return false

        room.lockDown(LOCKDOWN_TIME)
        lockdownCooldown = LOCKDOWN_COOLDOWN
        // One of the player's own, for the achievements
        if (ownerShip?.isPlayerShip == true && mindControlledBy == null)
            room.ship.sys.achievementTracker.add(AchievementGoal.Kind.LOCKDOWNS)
        room.ship.sys.sounds.getSampleOrWarn("lockdown${(1..2).random()}")?.play()
        return true
    }

    /** Called as its ship jumps: the lockdown's ready again at the next beacon. */
    fun onJump() {
        lockdownCooldown = 0f
    }

    override fun update(dt: Float) {
        super.update(dt)

        lockdownCooldown = max(0f, lockdownCooldown - dt)

        // The AI's crystals (the enemies', and the player's while they're
        // mind controlled) lock in whoever they're fighting, the boarders
        // in with the defenders and the defenders in with the boarders: not
        // straight away, but once the rest of its side on the way into the
        // room (a boarding party coming in with it) are in, so the lockdown
        // doesn't shut them out, or after [AI_LOCKDOWN_MAX_WAIT] at the most
        if (!playerControllable && isFightingHere()) {
            lockdownWait += dt
            val ready = lockdownWait >= AI_LOCKDOWN_DELAY &&
                    (!isSideStillComing() || lockdownWait >= AI_LOCKDOWN_MAX_WAIT)
            if (ready && lockDown())
                lockdownWait = 0f
        } else {
            lockdownWait = 0f
        }
    }

    // How long the AI's been fighting in its room, waiting to lock it down
    private var lockdownWait = 0f

    // Whether there's anyone in its room fighting its side
    private fun isFightingHere(): Boolean =
        !room.ship.isAtPeace && room.crew.any { it.mode != mode && it.currentAction != Action.DYING }

    // Whether any of its side (on its ship, boarders or defenders as it is)
    // are on their way into its room, and not there yet
    private fun isSideStillComing(): Boolean = room.ship.rooms.any { other ->
        other != room && other.crew.any {
            it !== this && it.mode == mode && it.currentAction != Action.DYING && it.pathingTarget?.room == room
        }
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)
        if (lockdownCooldown > 0f)
            SaveUtil.addAttrFloat(elem, "lockdownCooldown", lockdownCooldown)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        super.loadFromXML(elem, refs)
        lockdownCooldown = SaveUtil.getOptionalAttrFloat(elem, "lockdownCooldown") ?: 0f
    }

    companion object {
        /** How long a lockdown lasts, a Crystal's or a Lockdown Bomb's. */
        const val LOCKDOWN_TIME = 15f

        /** How long a Crystal waits before it can lock a room down again. */
        const val LOCKDOWN_COOLDOWN = 50f

        /**
         * How long the AI's crystals wait, fighting in a room, before
         * locking it down: a moment, so the rest of a boarding party that
         * came in together are in too, and longer while any of its side are
         * still on their way in, up to [AI_LOCKDOWN_MAX_WAIT].
         */
        const val AI_LOCKDOWN_DELAY = 0.5f
        const val AI_LOCKDOWN_MAX_WAIT = 3f
    }
}
