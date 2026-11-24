package xyz.znix.xftl.systems

import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.crew.Skill

/**
 * Manning the systems that vanilla's crew can't (an XFTL extension, see
 * doc/game/skills.md): someone at a system's console speeds it up by their
 * skill, as much as the Weapons skill speeds up a weapons system's weapons,
 * 10% at the least and 25% at the most ([SKILL_BONUSES]).
 *
 * - The Life Support skill (an XFTL one too) for the mind control
 *   system, which recharges sooner, and the oxygen system, which refills
 *   the air faster.
 * - The Drones skill for the hacking system (its probe's a drone), whose
 *   pulse recharges sooner, as for the drones system (see
 *   [Drones.manningSpeed]).
 *
 * The mind control's and the hacking's cooldowns run on the ion timer (see [AbstractSystem.cooldownSpeed]),
 * and a system on its cooldown can still be manned (see
 * [AbstractSystem.isMannableBy]), so the bonus does something.
 */
object Manning {
    // As the weapons' (Weapons.SKILL_BONUSES), and the drones'
    val SKILL_BONUSES = listOf(10, 13, 16, 19, 22, 25)

    /**
     * How much faster [system] goes, manned with [skill]: 1 if no-one's at
     * its console, and as much faster as a weapon charges (its time cut by
     * the bonus) if someone is.
     */
    fun speed(system: AbstractSystem, skill: Skill): Float {
        val level = system.getSkillLevel(skill) ?: return 1f
        return 1f / (1f - SKILL_BONUSES[level.level] / 100f)
    }

    /**
     * How many of the Drones skill's actions a hacking pulse is: as rare
     * as a mind control, where a drone fires all fight, so it's worth as
     * much of a level as one of those is of the Life Support skill's.
     */
    val HACKING_PULSE_ACTIONS: Int
        get() = (Skill.DRONES.actionsPerLevel.toFloat() / Skill.LIFE_SUPPORT.actionsPerLevel).toInt()

    /**
     * How long the oxygen system's manned refilling the air (any of the
     * ship's rooms short of it) for each of the Life Support skill's actions.
     */
    const val OXYGEN_SECONDS_PER_ACTION = 15f
}
