package xyz.znix.xftl.hangar

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

/**
 * The hangar shows the starting crew where the game puts them (see
 * StartingCrew and Ship.addCrewMember).
 */
class StartingCrewTest {
    @Test
    fun testTheHangarPutsTheCrewWhereTheGameDoes() {
        val kit = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML), playerShip = "TEST_STARTING_CREW_SHIP")
        val blueprint = kit.state.blueprintManager.getShip("TEST_STARTING_CREW_SHIP")
        val spots = StartingCrew.spots(blueprint, EditableShip.fromBlueprint(blueprint))

        val inGame = kit.player.crew.map { crew ->
            val pos = crew.standingPosition!!
            Triple(pos.room.id, pos.x, pos.y)
        }
        assertEquals(inGame, spots.map { Triple(it.room, it.cell.x, it.cell.y) })

        // The one at the oxygen's console (which the blueprint doesn't place,
        // so it goes on an edge without a door), the shields' and the
        // pilot's man them; the second in the shields' room stands by
        assertEquals(listOf(true, true, false, true, false), spots.map { it.facing != null })
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <systemBlueprint name="shields">
        <type>shields</type>
        <startPower>2</startPower>
        <maxPower>8</maxPower>
        <cost>1</cost>
        <upgradeCost/>
    </systemBlueprint>

    <shipBlueprint name="TEST_STARTING_CREW_SHIP" layout="test_big_ship" img="test_ship">
        <health amount="30"/>
        <maxPower amount="30"/>
        <systemList>
            <pilot power="1" max="1" room="0"/>
            <oxygen power="1" max="1" room="1"/>
            <shields power="2" max="2" room="2"/>
        </systemList>
        <crewCount amount="1" class="human" room="1"/>
        <crewCount amount="2" class="human" room="2"/>
        <crewCount amount="2" class="human"/>
    </shipBlueprint>
</FTL>
"""
    }
}
