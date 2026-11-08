package xyz.znix.xftl.crew

import xyz.znix.xftl.Animations
import xyz.znix.xftl.layout.Room

class CrewSlug(blueprint: CrewBlueprint, animations: Animations, room: Room, mode: SlotType) :
    LivingCrew(blueprint, animations, room, mode) {

    override val isMindControlResistant: Boolean get() = true

    /**
     * Slugs are telepathic: the player can see into the rooms next to one of
     * their slugs, even without sensors (such as in a nebula), and on an
     * enemy ship. See [Room.adjacentRooms].
     */
    val givesPlayerTelepathy: Boolean get() = playerControllable
}
