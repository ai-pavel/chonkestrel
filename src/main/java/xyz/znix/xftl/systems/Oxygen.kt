package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.SystemInfo
import xyz.znix.xftl.Translator
import xyz.znix.xftl.game.UIUtils
import xyz.znix.xftl.replaceArg
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import kotlin.math.max
import kotlin.math.roundToInt

class Oxygen(blueprint: SystemBlueprint) : MainSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.OXYGEN

    val refillRate: Float
        get() {
            // Drains 6% per second, including the constant drain rate.
            if (isHackActive)
                return -(0.06f - ROOM_DRAIN_RATE)

            if (powerSelected == 0)
                return 0f

            return refillMultiplier(powerSelected) * ROOM_DRAIN_RATE
        }

    // Nothing to serialise
    override fun saveSystem(elem: Element, refs: ObjectRefs) = Unit
    override fun loadSystem(elem: Element, refs: RefLoader) = Unit

    companion object {
        /**
         * 1.2% oxygen drain per second, with the oxygen system off.
         */
        const val ROOM_DRAIN_RATE = 0.012f

        /**
         * The amount of oxygen below which crew take damage and the warning stripes appear.
         */
        const val OXYGEN_CRITICAL_LEVEL = 0.05f

        const val REFILL_SCALING = 3f

        /** Vanilla's most bars; the Chonkestrel mod lets it go to six. */
        const val VANILLA_MAX_POWER = 3

        // How much more each bar past vanilla's three refills (in drain
        // rates), less and less, as with the cloak (see Cloaking.durationFor)
        private val EXTRA_REFILL = listOf(1.5f, 1f, 0.5f)

        /**
         * How fast the oxygen system refills with [power] bars, in drain
         * rates. The UI is wrong, the refill rates are 1,4,7: we add 2,
         * since for level 1 we have to offset the drain rate, then also
         * refill at a rate equal to the drain rate. Past three bars, each
         * adds less (see EXTRA_REFILL).
         */
        fun refillMultiplier(power: Int): Float {
            val vanilla = 2 + (power.coerceAtMost(VANILLA_MAX_POWER) - 1) * REFILL_SCALING
            return vanilla + EXTRA_REFILL.take((power - VANILLA_MAX_POWER).coerceAtLeast(0)).sum()
        }

        /** The refill speed the UI shows for [level] (zero-indexed): vanilla's 1, 3 and 6, then on from there. */
        fun shownSpeed(level: Int): Float =
            max(1f, REFILL_SCALING * level.coerceAtMost(VANILLA_MAX_POWER - 1)) +
                    EXTRA_REFILL.take((level + 1 - VANILLA_MAX_POWER).coerceAtLeast(0)).sum()

        val INFO: SystemInfo = OxygenInfo
    }
}

private object OxygenInfo : SystemInfo("oxygen") {
    override val canBeManned: Boolean get() = false

    override fun create(blueprint: SystemBlueprint) = Oxygen(blueprint)

    override fun getLevelName(level: Int, translator: Translator): String {
        val fixedPoint = (Oxygen.shownSpeed(level) * 100).roundToInt()
        val speedStr = UIUtils.formatStringFTL(fixedPoint)
        return translator["oxygen_on"].replaceArg(speedStr)
    }
}
