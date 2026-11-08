package xyz.znix.xftl.crew

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.Animations
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.testkit.InGameTest

class CrewLimitTest {
    @Test
    fun testDefaultLimit() {
        val kit = InGameTest()
        assertEquals(8, kit.player.maxCrew)

        addCrew(kit, 8)
        assertEquals(true, kit.state.isPlayerCrewFull)
        assertEquals(false, kit.state.playerHasTooManyCrew())

        addCrew(kit, 1)
        assertEquals(true, kit.state.playerHasTooManyCrew())
    }

    @Test
    fun testRaisedLimit() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_SHIP_BIG_CREW")
        assertEquals(16, kit.player.maxCrew)

        addCrew(kit, 15)
        assertEquals(false, kit.state.isPlayerCrewFull)

        addCrew(kit, 1)
        assertEquals(true, kit.state.isPlayerCrewFull)
        assertEquals(false, kit.state.playerHasTooManyCrew())

        addCrew(kit, 1)
        assertEquals(true, kit.state.playerHasTooManyCrew())
    }

    private fun addCrew(kit: InGameTest, count: Int) {
        val room = kit.player.rooms.first { it.width == 5 && it.height == 5 }
        val type = kit.state.blueprintManager["test_crew"] as CrewBlueprint

        for (i in 0 until count) {
            val slot = kit.player.findSpaceForCrew(room, AbstractCrew.SlotType.CREW)
            val crewMember = DummyCrew(type, kit.state.animations, slot.room, AbstractCrew.SlotType.CREW)
            kit.player.crew.add(crewMember)
            crewMember.jumpTo(slot)
        }

        kit.state.updatePlayerCrew()
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <shipBlueprint name="TEST_SHIP_BIG_CREW" layout="test_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <maxCrew>16</maxCrew>

        <systemList>
            <oxygen power="1" max="1" room="0"/>
            <pilot power="1" max="1" room="1"/>
            <engines power="1" max="1" room="2"/>
        </systemList>
    </shipBlueprint>
</FTL>
"""
    }
}

private class DummyCrew(
    blueprint: CrewBlueprint,
    anims: Animations,
    initialRoom: Room,
    mode: AbstractCrew.SlotType,
) : LivingCrew(blueprint, anims, initialRoom, mode)
