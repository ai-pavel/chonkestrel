package xyz.znix.xftl.testkit

import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.LivingCrewInfo
import xyz.znix.xftl.layout.Room

private var crewMade = 0

/**
 * Add a human (the test kit has their animations, as stand-ins), or one of
 * [race] (whose blueprint and animations the test supplies), to [ship],
 * the way the game does, in [room] (or the nearest free place): as its own
 * crew, named "Crew 1", "Crew 2" and so on, or ([mode] INTRUDER) a boarder owned by [owner] (by default no ship,
 * as an event's boarders are).
 */
fun InGameTest.addCrew(
    ship: Ship, room: Room,
    mode: AbstractCrew.SlotType = AbstractCrew.SlotType.CREW,
    owner: Ship? = if (mode == AbstractCrew.SlotType.CREW) ship else null,
    race: String = "human",
): LivingCrew {
    val blueprint = state.blueprintManager[race] as CrewBlueprint
    val info = LivingCrewInfo.generateRandom(blueprint, state)
    // A name of their own, to find them again (after loading a game, say)
    info.name = "Crew ${++crewMade}"
    val crew = ship.addCrewMember(info, false, mode == AbstractCrew.SlotType.INTRUDER, room)
    crew.ownerShip = owner
    ship.updateCrewReservedSlots()
    state.updatePlayerCrew()
    return crew
}
