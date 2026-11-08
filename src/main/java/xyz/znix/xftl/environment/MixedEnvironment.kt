package xyz.znix.xftl.environment

import org.jdom2.Element
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.EnergySource
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sys.GameContainer

/**
 * Two, three or four environments at once, such as a nebula with an
 * asteroid field, or a pulsar near a sun: an extreme hazard (see
 * [Beacon.isExtremeHazard]), or with three, a catastrophic one, and with all
 * four kinds, an apocalyptic one. They all work
 * as they do on their own, and this passes everything on to them.
 *
 * The first of the [parts] is the beacon's main environment, which gives
 * this its [type].
 */
class MixedEnvironment(
    game: InGameState, beacon: Beacon,
    override val parts: List<AbstractEnvironment>
) : AbstractEnvironment(game, beacon) {
    init {
        require(parts.size in 2..Beacon.MAX_HAZARDS) { "A mixed environment needs two to four parts, not ${parts.size}" }
    }

    override val type: Beacon.EnvironmentType get() = parts[0].type

    // The order they're drawn in: nebula clouds at the back, then stars,
    // then asteroids in front of everything
    private val drawOrder = parts.sortedBy {
        when (it.type) {
            Beacon.EnvironmentType.ASTEROID -> 2
            Beacon.EnvironmentType.SUN, Beacon.EnvironmentType.PULSAR -> 1
            else -> 0
        }
    }

    // Only one of them can have its background picture: one with its own
    // picture, as a nebula's is just black
    override fun renderBackdrop(gc: GameContainer, g: Graphics) {
        val withPicture = drawOrder.firstOrNull { !it.type.isNebula } ?: parts[0]
        withPicture.renderBackdrop(gc, g)
    }

    override fun renderScenery(gc: GameContainer, g: Graphics) {
        for (part in drawOrder) {
            part.renderScenery(gc, g)
        }
    }

    override fun renderOverlay(gc: GameContainer, g: Graphics) {
        for (part in drawOrder) {
            part.renderOverlay(gc, g)
        }
    }

    override fun update(dt: Float) {
        for (part in parts) {
            part.update(dt)
        }
    }

    override fun adjustShipPower(ship: Ship, powerAvailableTypes: HashMap<EnergySource, Int>) {
        for (part in parts) {
            part.adjustShipPower(ship, powerAvailableTypes)
        }
    }

    override fun systemPowerLimit(system: AbstractSystem): Int? {
        return parts.mapNotNull { it.systemPowerLimit(system) }.minOrNull()
    }

    override val ftlChargeMultiplier: Float
        get() = parts.fold(1f) { multiplier, part -> multiplier * part.ftlChargeMultiplier }

    // The main environment saves into the beacon's element as usual, and the
    // others into their own
    override fun saveToXML(elem: Element) {
        parts[0].saveToXML(elem)

        for ((index, part) in parts.withIndex().drop(1)) {
            val partElem = Element(PART_ELEMENTS[index - 1])
            part.saveToXML(partElem)
            elem.addContent(partElem)
        }
    }

    override fun loadFromXML(elem: Element) {
        parts[0].loadFromXML(elem)
        for ((index, part) in parts.withIndex().drop(1)) {
            elem.getChild(PART_ELEMENTS[index - 1])?.let { part.loadFromXML(it) }
        }
    }

    companion object {
        private val PART_ELEMENTS = listOf("secondEnvironment", "thirdEnvironment", "fourthEnvironment")
    }
}
