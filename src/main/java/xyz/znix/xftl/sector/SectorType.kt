package xyz.znix.xftl.sector

import org.jdom2.Element
import xyz.znix.xftl.GameText
import xyz.znix.xftl.game.StoreProfile
import xyz.znix.xftl.mapChildrenText
import xyz.znix.xftl.requireAttributeValue
import xyz.znix.xftl.requireAttributeValueInt

class SectorType(private val eventManager: EventManager, elem: Element) {
    val name: String = elem.requireAttributeValue("name")
    val soundtracks: List<String> = elem.getChild("trackList").mapChildrenText("track")
    val startEventName: String = let {
        // The final sector seems to have the wrong thing set here,
        // starting you at a random event rather than the initial dialogue.
        if (name == "FINAL")
            return@let "LAST_STAND_START"

        elem.getChildTextTrim("startEvent") ?: "START_BEACON"
    }
    val startEvent: IEvent get() = startEventName.let { eventManager[it] }

    /**
     * The (zero-indexed) earliest sector this is allowed to occur at.
     */
    val minSector: Int = elem.getAttributeValue("minSector")?.toInt() ?: -1

    val unique: Boolean = elem.getAttributeValue("unique")!!.toBoolean()

    /**
     * The act (1 or 2) this sector is limited to, or null if it can appear
     * in either. This is an XFTL extension (the `xftlAct` attribute): longer
     * games are split into two acts by the [isMidpoint] sector, see
     * [GameMap.actOf].
     */
    val act: Int? = elem.getAttributeValue("xftlAct")?.toInt()

    /**
     * The sectors this one can appear in, numbered from 1 as in a game of
     * [GameMap.NOMINAL_SECTOR_COUNT] sectors (and scaled to match in games
     * of other lengths), or null for anywhere. This is an XFTL extension (the
     * `xftlSectors` attribute): `6-10` for sectors 6 to 10, or `5-` for
     * sector 5 onwards.
     */
    val sectors: IntRange? = elem.getAttributeValue("xftlSectors")?.let { parseSectors(it, "sector $name") }

    /**
     * How much more likely this sector is to be picked than each vanilla
     * sector, an XFTL extension (`xftlWeight`, which can be a fraction). By
     * default, the sectors only for act 2 are [GameMap.ACT_ONLY_WEIGHT] times
     * as likely in act 2, and the others are as likely as vanilla's.
     */
    val weight: Float? = elem.getAttributeValue("xftlWeight")?.toFloat()

    /**
     * How much more fuel each jump takes in this sector, on top of the
     * ship's own (see Ship.fuelPerJump): an XFTL extension (`xftlExtraFuel`),
     * for the most hazardous sectors, where the engines fight the hazards.
     */
    val extraFuel: Int = elem.getAttributeValue("xftlExtraFuel")?.toInt() ?: 0

    /**
     * How much faster than usual the Rebel fleet advances in this sector:
     * an XFTL extension (`xftlFleetSpeed`), for the sectors whose exit a
     * boss holds (see doc/game/bosses.md), so the player has to hurry.
     */
    val fleetSpeed: Float = elem.getAttributeValue("xftlFleetSpeed")?.toFloat() ?: 1f

    /**
     * True for a sector every map has somewhere (in the sectors it can go
     * in, see [sectors]): an XFTL extension (`xftlAlways`), for the
     * hazardous sectors with bosses (see doc/game/bosses.md).
     */
    val always: Boolean = elem.getAttributeValue("xftlAlways")?.toBoolean() ?: false

    /**
     * The two races whose hybrids hold this sector, such as `ROCK` and
     * `ZOLTAN` (as in the hybrids' names), or null. This is an XFTL extension
     * (the `xftlHybrids` attribute), for the hybrid sectors made by
     * tools/hybrid_sectors.py: the enemy ships that can be hybrids are that
     * pair's (see ShipGenerator.hybridFor).
     */
    val hybridRaces: List<String>? = elem.getAttributeValue("xftlHybrids")
        ?.split(" ")?.filter { it.isNotEmpty() }
        ?.also { require(it.size == 2) { "xftlHybrids on sector $name isn't two races: $it" } }

    /**
     * If true, this is the sector between the two acts, which every game long
     * enough to have them goes through. This is an XFTL extension (the
     * `xftlMidpoint` attribute).
     */
    val isMidpoint: Boolean = elem.getAttributeValue("xftlMidpoint")?.toBoolean() ?: false

    /**
     * The event to use for the exit beacon instead of the usual one, or null.
     * This is an XFTL extension (the `xftlFinishEvent` element), used to guard
     * the exit of the [isMidpoint] sector.
     */
    val finishEventName: String? = elem.getChildTextTrim("xftlFinishEvent")

    /**
     * For a sector whose boss hunts the player across the map rather than
     * holding its exit (see HunterBoss), how many of the player's jumps it
     * takes to make one of its own: an XFTL extension (`xftlHunter`). Its
     * event is [finishEventName], its exit is an ordinary one, and there's
     * no Rebel fleet behind it. Null for every other sector.
     */
    val hunterPeriod: Int? = elem.getAttributeValue("xftlHunter")?.toInt()
        ?.also { require(it >= 1) { "xftlHunter on sector $name must be at least 1: $it" } }
        ?.also { require(finishEventName != null) { "xftlHunter on sector $name needs an xftlFinishEvent" } }

    /**
     * True if this sector's boss ([finishEventName]) holds its exit, so the
     * player has to beat it to leave (see ColossalBoss): every sector with
     * a boss that isn't a hunter ([hunterPeriod]).
     */
    val bossHoldsExit: Boolean get() = finishEventName != null && hunterPeriod == null

    /** True if the Rebel fleet's behind the player in this sector: not where a hunter is ([hunterPeriod]). */
    val hasFleet: Boolean get() = hunterPeriod == null

    /**
     * How many exit beacons this sector has: an XFTL extension (`xftlExits`),
     * for the Blockade Line, where the blockade's ships wait at each of them
     * (the same boss, wherever the player tries to get through). One for
     * every other sector.
     */
    val exitCount: Int = elem.getAttributeValue("xftlExits")?.toInt() ?: 1

    /**
     * The kind of nebula most of this sector's beacons are, an XFTL extension
     * (the `xftlNebula` attribute, with an environment type such as
     * `RADIATION`), or null. See [GameMap.addStrangeNebulas].
     */
    val nebula: Beacon.EnvironmentType? = elem.getAttributeValue("xftlNebula")
        ?.let { Beacon.EnvironmentType.valueOf(it) }
        ?.also { require(it.isNebula) { "xftlNebula on sector $name isn't a nebula: $it" } }

    /**
     * The chance of each beacon being the [nebula], set with `xftlNebulaChance`.
     */
    val nebulaChance: Float = elem.getAttributeValue("xftlNebulaChance")?.toFloat() ?: DEFAULT_NEBULA_CHANCE

    /**
     * The chances of a weapon in this sector's stores being a double or a
     * triple one, an XFTL extension (`xftlStoreDoubleChance` and
     * `xftlStoreTripleChance`), in place of its [StoreProfile]'s, or null to
     * use the profile's. See doc/game/stores.md.
     */
    val storeDoubleChance: Float? = elem.getAttributeValue("xftlStoreDoubleChance")?.toFloat()
    val storeTripleChance: Float? = elem.getAttributeValue("xftlStoreTripleChance")?.toFloat()

    /**
     * How many hazards this sector's beacons get, an XFTL extension (the
     * `xftlHazards` attribute): `true` for a hazard at every beacon, besides
     * stores, quests and the events marked `xftlNoHazards`, `catastrophic`
     * for mostly three at once, or `apocalyptic` for mostly all four at once.
     * See [GameMap.addHazards].
     */
    val hazards: HazardLevel = when (val value = elem.getAttributeValue("xftlHazards")) {
        null, "false" -> HazardLevel.NORMAL
        "true" -> HazardLevel.EVERY_BEACON
        "catastrophic" -> HazardLevel.CATASTROPHIC
        "apocalyptic" -> HazardLevel.APOCALYPTIC
        else -> error("Invalid xftlHazards on sector $name: $value")
    }

    enum class HazardLevel {
        /**
         * As in vanilla, only beacons whose events have an environment have
         * hazards, though they may get more of them.
         */
        NORMAL,

        /**
         * Every beacon has hazards (as in a nebula sector).
         */
        EVERY_BEACON,

        /**
         * Every beacon has hazards, and most have three at once.
         */
        CATASTROPHIC,

        /**
         * Every beacon has hazards, and most have all four at once.
         */
        APOCALYPTIC,
    }
    val finishEvent: IEvent? get() = finishEventName?.let { eventManager[it] }

    val events: List<EventInfo> = elem.getChildren("event").map {
        val name = it.requireAttributeValue("name")
        val count = it.requireAttributeValueInt("min")..it.requireAttributeValueInt("max")
        val minShipSize = it.getAttributeValue("xftlMinShipSize")?.toInt() ?: 1
        val act = it.getAttributeValue("xftlAct")?.toInt()
        val noHazards = it.getAttributeValue("xftlNoHazards")?.toBoolean() ?: false
        val sectors = it.getAttributeValue("xftlSectors")
            ?.let { value -> parseSectors(value, "event $name in sector ${this@SectorType.name}") }
        EventInfo(name, count, eventManager[name], minShipSize, act, noHazards, sectors)
    }

    val nameTextId: GameText
    val shortTextId: GameText

    val rarityOverrides: Map<String, Int>

    init {
        val nameList = elem.getChild("nameList")
        check(nameList.children.size == 1)
        val nameElem = nameList.getChild("name")

        // In vanilla, both of these are specified as localisation keys for
        // all sector types. However, mods don't have to.
        // I haven't checked if the short text behaviour is correct, this is
        // just a guess.
        // This is reproduced with the Insurrection mod.
        nameTextId = GameText.parse(nameElem)
        shortTextId = nameElem.getAttributeValue("short")?.let { GameText.localised(it) } ?: nameTextId

        // Load the rarity overrides
        check(elem.getChildren("rarityList").size <= 1)
        rarityOverrides = elem.getChild("rarityList")?.children?.map { e ->
            check(e.name == "blueprint")
            check(e.children.size == 0)
            Pair(e.requireAttributeValue("name"), e.requireAttributeValueInt("rarity"))
        }?.toMap() ?: emptyMap()
    }

    companion object {
        const val DEFAULT_NEBULA_CHANCE = 0.9f

        /**
         * An `xftlSectors` attribute's sectors: `6-10` for sectors 6 to 10,
         * or `5-` for sector 5 onwards.
         */
        private fun parseSectors(value: String, where: String): IntRange {
            val parts = value.split("-").map { it.trim() }
            require(parts.size == 2 && parts[0].isNotEmpty()) { "Invalid xftlSectors on $where: $value" }
            return parts[0].toInt()..(parts[1].toIntOrNull() ?: Int.MAX_VALUE)
        }
    }

    class EventInfo(
        val name: String, val count: IntRange, val event: IEvent,

        /**
         * The smallest player ship this event appears for, an XFTL extension
         * (`xftlMinShipSize`). This is for events about big ships.
         */
        val minShipSize: Int = 1,

        /**
         * The act (1 or 2) this event is limited to, or null for either, an
         * XFTL extension (`xftlAct`). See [GameMap.actOf].
         */
        val act: Int? = null,

        /**
         * If true, this event's beacons don't get a [hazards] sector's
         * hazards, an XFTL extension (`xftlNoHazards`) for special events.
         */
        val noHazards: Boolean = false,

        /**
         * The sectors this event is limited to, numbered as in
         * [SectorType.sectors], or null for any, an XFTL extension
         * (`xftlSectors`). This is to have more of the new events further
         * into the game. See [GameMap.nominalSectorNumber].
         */
        val sectors: IntRange? = null
    )
}
