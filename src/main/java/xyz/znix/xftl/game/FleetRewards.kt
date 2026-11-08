package xyz.znix.xftl.game

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.replaceArg
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.sector.Event
import kotlin.random.Random

/**
 * The rewards for the ships in a fight with several of them, which are kept
 * until they've all been dealt with, and then shown together: a line for
 * each ship, saying what it gave, and the total.
 *
 * Plain rewards (resources and items) wait. A ship whose event does
 * anything else, such as adding crew or offering a choice (to contact the
 * civilian ship it was attacking, say), waits too ([deferSpecial]): the
 * first such event is shown once, with the rest, when they're all dealt
 * with (see InGameState.showShipRewards), and the other ships' copies of it
 * (the wingmen have the main ship's events) only give their resources.
 */
class FleetRewards(private val game: InGameState) {
    private class Line(val label: String, val resources: ResourceSet)

    private val lines = ArrayList<Line>()

    /**
     * The first ship's event that isn't a plain reward, to show once they've
     * all been dealt with (see [deferSpecial]).
     */
    // (the ship's for a bigger ship's salvage, which a saved game might not
    // have any more, if it's gone)
    var special: Pair<Ship?, Event>? = null
        private set

    val isEmpty: Boolean get() = lines.isEmpty() && special == null

    /**
     * Keep [ship]'s reward from its [event] (and the extra salvage from a
     * bigger ship) for later. Returns false if it's not a plain reward, and
     * should be shown now instead.
     */
    fun add(ship: Ship, event: Event): Boolean {
        if (!isPlainReward(event))
            return false

        val resources = event.resolveResources(game, Random.Default)
        if (!isPlainResources(resources))
            return false
        BigShipRewards.salvage(game, ship, event, Random.Default)?.let { resources += it.resources }

        lines += Line(labelOf(ship), resources)
        return true
    }

    /**
     * Keep [ship]'s [event], which isn't a plain reward, to show once all
     * the ships have been dealt with, if it's the first such one; for the
     * others, keep just their resources and items, as for a plain reward.
     */
    fun deferSpecial(ship: Ship, event: Event) {
        if (special == null)
            special = ship to event
        else
            addResourcesOnly(ship, event)
    }

    /**
     * Take the event kept by [deferSpecial] (if any) to show it, and forget it.
     */
    fun takeSpecial(): Pair<Ship?, Event>? = special.also { special = null }

    /**
     * Keep just the resources and items of [ship]'s [event] (and the extra
     * salvage from a bigger ship), leaving out everything else it does.
     */
    fun addResourcesOnly(ship: Ship?, event: Event) {
        val all = event.resolveResources(game, Random.Default)
        val resources = ResourceSet()
        resources.scrap = all.scrap
        resources.fuel = all.fuel
        resources.missiles = all.missiles
        resources.droneParts = all.droneParts
        resources.items += all.items
        if (ship != null)
            BigShipRewards.salvage(game, ship, event, Random.Default)?.let { resources += it.resources }
        lines += Line(labelOf(ship), resources)
    }

    private fun labelOf(ship: Ship?): String {
        val shipClass = ship?.type?.shipClass
        return if (shipClass != null) game.translator[shipClass] else ship?.name ?: "?"
    }

    /**
     * Show the rewards kept so far, in one window, and forget them (the
     * player gets them on pressing continue).
     */
    fun show() {
        if (lines.isEmpty())
            return

        // A line for each ship, but with only one there's no need for a list
        val total = ResourceSet()
        for (line in lines)
            total += line.resources
        val text = StringBuilder()
        if (lines.size == 1) {
            text.append(game.translator["xftl_fleet_reward_single"])
        } else {
            text.append(game.translator["xftl_fleet_rewards"])
            for (line in lines)
                text.append("\n - ").append(line.label).append(": ").append(describe(line.resources))
        }
        lines.clear()

        val event = DialogueWindow.SyntheticEvent(text.toString())
        event.resources += total
        game.shipUI.showSyntheticDialogue(event)
    }

    private fun describe(resources: ResourceSet): String {
        val parts = ArrayList<String>()
        fun add(amount: Int, key: String) {
            if (amount != 0)
                parts += game.translator[key].replaceArg(amount)
        }
        add(resources.scrap, "xftl_reward_scrap")
        add(resources.fuel, "xftl_reward_fuel")
        add(resources.missiles, "xftl_reward_missiles")
        add(resources.droneParts, "xftl_reward_drones")
        for (item in resources.items)
            parts += item.translateTitle(game)
        return if (parts.isEmpty()) game.translator["xftl_reward_nothing"] else parts.joinToString(", ")
    }

    fun saveToXML(parent: Element, refs: ObjectRefs) {
        special?.let { (ship, event) ->
            val elem = Element("fleetSpecial")
            ship?.takeIf { refs.contains(it) }?.let { SaveUtil.addAttrRef(elem, "ship", refs, it) }
            SaveUtil.addAttr(elem, "event", event.deserialisationId)
            parent.addContent(elem)
        }
        for (line in lines) {
            val elem = Element("fleetReward")
            SaveUtil.addAttr(elem, "label", line.label)
            val resourcesElem = Element("resources")
            line.resources.saveToXML(resourcesElem, refs)
            elem.addContent(resourcesElem)
            parent.addContent(elem)
        }
    }

    fun loadFromXML(parent: Element, refs: RefLoader) {
        lines.clear()
        special = null
        parent.getChild("fleetSpecial")?.let { elem ->
            val event = game.eventManager.getByDeserialisationId(SaveUtil.getAttr(elem, "event"))
            special = null to event
            if (elem.getAttribute("ship") != null)
                SaveUtil.getAttrRef(elem, "ship", refs, Ship::class.java) { special = it to event }
        }
        for (elem in parent.getChildren("fleetReward")) {
            lines += Line(SaveUtil.getAttr(elem, "label"), ResourceSet(elem.getChild("resources"), refs, game))
        }
    }

    private companion object {
        // An event that just gives resources or items, with nothing else to
        // show or do
        fun isPlainReward(event: Event): Boolean = event.choices.isEmpty() &&
                event.addedCrew.isEmpty() && event.removedCrew.isEmpty() &&
                event.hullDamage.isEmpty() && event.systemUpgrades.isEmpty() &&
                event.modifyPursuit == 0 && event.unlockShip == null &&
                event.boarderRace == null && event.statuses.isEmpty() &&
                event.removedStuff.isEmpty() && event.questName == null &&
                !event.revealMap && !event.isStore && !event.isDistressBeacon &&
                !event.isLastStandRepair && event.fleetBackground == null

        fun isPlainResources(resources: ResourceSet): Boolean = resources.crew.isEmpty() &&
                resources.lostCrew.isEmpty() && resources.intruders.isEmpty() &&
                resources.damage.isEmpty() && resources.upgrades.isEmpty() && resources.modifyPursuit == 0
    }
}
