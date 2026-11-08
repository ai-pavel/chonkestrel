package xyz.znix.xftl.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.Animations
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.testkit.InGameTest
import java.lang.reflect.Field

class TestFriendlyCrewAI {
    private val assignmentsField: Field = FriendlyCrewAI::class.java
        .getDeclaredField("assignments")
        .apply { isAccessible = true }

    @Test
    fun testRunToMedbay() {
        // Run to the medbay when on low health, but only when the medbay is working.
        val kit = InGameTest()
        val ai = FriendlyCrewAI(kit.player)

        val crew = addCrewMember(kit, AbstractCrew.SlotType.CREW)
        addCrewMember(kit, AbstractCrew.SlotType.INTRUDER)

        // Update the per-room crew lists
        kit.updateSingle(0f)

        ai.update()
        assertEquals(CombatTask::class.java, getAssignments(ai)[crew]?.javaClass)

        // Dropping below full health doesn't run to medbay unless
        // the ship is calm.
        crew.health = 50f
        ai.update()
        assertEquals(CombatTask::class.java, getAssignments(ai)[crew]?.javaClass)

        crew.health = 0.01f
        ai.update()
        assertEquals(HealingTask::class.java, getAssignments(ai)[crew]?.javaClass)

        // Breaking the medbay cancels healing
        kit.player.medbay!!.dealDamage(100, 0)
        kit.player.updateAvailablePower()
        ai.update()
        assertEquals(CombatTask::class.java, getAssignments(ai)[crew]?.javaClass)

        // Shouldn't oscillate between combat and healing
        ai.update()
        assertEquals(CombatTask::class.java, getAssignments(ai)[crew]?.javaClass)
    }

    private fun getAssignments(ai: FriendlyCrewAI): Map<AbstractCrew, AITask> {
        @Suppress("UNCHECKED_CAST")
        return assignmentsField.get(ai) as Map<AbstractCrew, AITask>
    }

    private fun addCrewMember(kit: InGameTest, mode: AbstractCrew.SlotType): DummyCrew {
        val room = kit.player.rooms.first { it.width == 5 && it.height == 5 }
        val slot = kit.player.findSpaceForCrew(room, mode)
        check(slot.room == room)
        val type = kit.state.blueprintManager["test_crew"] as CrewBlueprint
        val crewMember = DummyCrew(type, kit.state.animations, room, mode)

        kit.player.crew.add(crewMember)
        crewMember.jumpTo(slot)

        return crewMember
    }
}

private class DummyCrew(
    blueprint: CrewBlueprint,
    anims: Animations,
    initialRoom: Room,
    mode: SlotType,
) : LivingCrew(blueprint, anims, initialRoom, mode)
