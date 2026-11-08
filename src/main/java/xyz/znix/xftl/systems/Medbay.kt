package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.SystemInfo
import xyz.znix.xftl.Translator
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.MedbayHealing
import xyz.znix.xftl.game.RealSoundManager
import xyz.znix.xftl.game.UIUtils
import xyz.znix.xftl.replaceArg
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import kotlin.math.roundToInt

class Medbay(blueprint: SystemBlueprint) : MainSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.MEDBAY

    // Heard while it's healing the player's crew (with the game's sounds,
    // not the tests' silent ones)
    private val healingSound by onInit { (it.sounds as? RealSoundManager)?.getLoop("healing") }

    override fun update(dt: Float) {
        super.update(dt)

        var healthPerSec = when (powerSelected) {
            0 -> 0f
            else -> 6.4f * speedForLevel(powerSelected - 1)
        }

        // Hacking hurts the friendly crew, and rather quickly.
        if (isHackActive) {
            healthPerSec = -13f
        }

        val healing = healthPerSec * dt

        for (crew in room!!.crew) {
            // Don't heal drones
            if (crew !is LivingCrew)
                continue

            // Only heal friendly crew
            if (crew.mode == AbstractCrew.SlotType.INTRUDER)
                continue

            // Don't revive dying crewmembers
            if (crew.currentAction == AbstractCrew.Action.DYING)
                continue

            // Show it (and hear it), as long as there's healing to do, or
            // it's hurting them
            if (healing < 0f || (healing > 0f && crew.health < crew.maxHealth)) {
                crew.showHealing(healing < 0f)
                if (healing > 0f)
                    healingSound?.continueLoopPlayerOnly(ship)
            }

            crew.dealDamage(MedbayHealing(-healing))
        }
    }

    // Nothing to serialise
    override fun saveSystem(elem: Element, refs: ObjectRefs) = Unit
    override fun loadSystem(elem: Element, refs: RefLoader) = Unit

    companion object {
        /**
         * How fast the medbay heals at [level] (zero-indexed): vanilla's 1,
         * 1.5 and 3 times, then, past its three bars (the Chonkestrel mod
         * lets it go to six), less and less for each, as with the cloak
         * (see Cloaking.durationFor).
         */
        fun speedForLevel(level: Int): Float = SPEEDS[level.coerceIn(0, SPEEDS.size - 1)]

        private val SPEEDS = listOf(1f, 1.5f, 3f, 3.75f, 4.25f, 4.5f)

        /** Vanilla's most bars; the Chonkestrel mod lets it go to six. */
        const val VANILLA_MAX_POWER = 3

        val INFO: SystemInfo = MedbayInfo
    }
}

private object MedbayInfo : SystemInfo("medbay") {
    override val canBeManned: Boolean get() = false
    override val isComputerObstruction: Boolean get() = true

    override fun create(blueprint: SystemBlueprint) = Medbay(blueprint)

    override fun getLevelName(level: Int, translator: Translator): String {
        val fixedPoint = (Medbay.speedForLevel(level) * 100).roundToInt()
        val speedStr = UIUtils.formatStringFTL(fixedPoint)
        return translator["medbay_healing"].replaceArg(speedStr)
    }
}
