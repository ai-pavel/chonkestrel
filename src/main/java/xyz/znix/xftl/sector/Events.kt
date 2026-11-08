package xyz.znix.xftl.sector

import org.jdom2.Element
import xyz.znix.xftl.GameText
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.LivingCrewInfo
import xyz.znix.xftl.crew.Skill
import xyz.znix.xftl.environment.AntiShipBattery
import xyz.znix.xftl.game.*
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.requireAttributeValue
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.random.Random

// Many of the comments in this file came from ftlwiki.com (now unfortunately
// offline, but accessable via the wayback machine):
// https://ftlwiki.com/wiki/Events_file_structure

/**
 * Represents something that can be used as an event. This is either an Event or
 * an EventList.
 *
 * When this is to be used, [resolve] is called which provides a concrete event.
 */
interface IEvent {
    fun resolve(): Event

    val debugId: String
}

class Event(
    val text: IEventText?, val choices: List<Choice>, elem: Element, override val debugId: String,

    /**
     * A unique ID that can be used to index all the game's events,
     * and load this exact event.
     *
     * This has to handle any event, even unnamed ones that can
     * only be accessed via choices, as it's how the game remembers
     * what event the player had open when they closed the game.
     */
    val deserialisationId: String,

    imageFinder: (String) -> ImageList, loadText: (Element) -> IEventText
) : IEvent {
    val isDistressBeacon: Boolean = elem.getChild("distressBeacon") != null
    val isStore: Boolean = elem.getChild("store") != null

    val itemsModifySteal: Boolean
    val itemsModify: Map<Resource, IntRange>
    val addedCrew: List<AddCrew>
    val removedCrew: List<RemoveCrew>
    val autoRewards: Pair<RewardType, RewardTier>?
    val blueprintRewards: List<String>
    val hullDamage: List<EventHullDamage>
    val systemUpgrades: List<EventSystemUpgrade>

    val modifyPursuit: Int = elem.getChild("modifyPursuit")?.getAttributeValue("amount")?.toInt() ?: 0

    /**
     * A size 3 boss's rewards, an XFTL extension (`<xftlBossReward>`, see
     * [BossRewards]).
     */
    val bossRewards: BossRewards? = elem.getChild("xftlBossReward")?.let { BossRewards(it) }

    /**
     * How a size 3 boss's fight starts, an XFTL extension
     * (`<xftlBossOpening>`, see [ColossalBoss.opening]).
     */
    val bossOpening: String? = elem.getChildTextTrim("xftlBossOpening")
    val unlockShip: String? = elem.getChild("unlockShip")?.getAttributeValue("id")

    val boarderRace: String?
    val boarderCount: IntRange

    /**
     * Which of the event's effects are multiplied by the size of the player's
     * ship, an XFTL extension: `xftlScale="size"` on `<item_modify>`,
     * `<autoReward>` (its fuel, missiles, drone parts and scrap, but not its
     * items) or `<boarders>`. `<damage>` has its own, see [EventHullDamage].
     *
     * This lets events be written once for ships of every size: a big ship
     * needs more scrap to upgrade its systems, has more hull to repair, and
     * so on.
     */
    val scaleItemsBySize: Boolean
    val scaleRewardBySize: Boolean
    val scaleBoardersBySize: Boolean

    val statuses: List<EventStatus>
    val removedStuff: List<String>

    /**
     * If this event triggers a quest, this is its event name.
     */
    val questName: String? = elem.getChild("quest")?.getAttributeValue("event")

    /**
     * Whether the quest's beacon must be ahead of the player, towards the
     * sector's exit and a few jumps on, or else in the next sector, an XFTL
     * extension (`<quest event="..." xftlAhead="true"/>`). See Sector.addQuest.
     */
    val questAhead: Boolean = elem.getChild("quest")?.getAttributeValue("xftlAhead")?.toBoolean() ?: false

    /**
     * Whether this event can start a quest: itself, or through its choices
     * and the lists they lead to. The debug mode's map marks such beacons
     * (see JumpWindow).
     */
    val givesQuest: Boolean by lazy { givesQuest(this, HashSet()) }

    val revealMap: Boolean = elem.getChild("reveal_map") != null
    val isLastStandRepair: Boolean = elem.getChild("repair") != null

    val fleetBackground: FleetBackground? = elem.getChildTextTrim("fleet")
        ?.let { name -> FleetBackground.values().first { it.xmlName == name } }

    init {
        // Initialise these in the constructor so we can mutate them,
        // which isn't otherwise possible as they're declared as List.
        addedCrew = ArrayList()
        removedCrew = ArrayList()

        fun Element?.scaledBySize(): Boolean = this?.getAttributeValue("xftlScale") == "size"
        scaleItemsBySize = elem.getChild("item_modify").scaledBySize()
        scaleRewardBySize = elem.getChild("autoReward").scaledBySize()
        scaleBoardersBySize = elem.getChild("boarders").scaledBySize()

        val modElem = elem.getChild("item_modify")
        if (modElem != null) {
            itemsModifySteal = modElem.getAttributeValue("steal")?.toBoolean() ?: false
            val items = HashMap<Resource, IntRange>()
            itemsModify = items

            for (child in modElem.children) {
                check(child.name == "item")
                val type = Resource.byName(child.getAttributeValue("type"))
                val min = child.getAttributeValue("min").toInt()
                val max = child.getAttributeValue("max").toInt()
                items[type] = min..max
            }
        } else {
            itemsModifySteal = false
            itemsModify = emptyMap()
        }

        val crewElem = elem.getChild("crewMember")
        if (crewElem != null) {
            val count = crewElem.getAttributeValue("amount").toInt()

            val race = crewElem.getAttributeValue("class")

            // Although it's not used by vanilla, a literal name
            // can be inserted as the element's text.
            // This is used by Insurrection+, you can check the
            // DISTRESS_STATION_FIRE_RESCUE event to test it.
            val nameId = crewElem.getAttributeValue("id")
            val nameLiteral = crewElem.textTrim
            val name: GameText? = when {
                nameId != null -> GameText.localised(nameId)
                nameLiteral.isNotEmpty() -> GameText.literal(nameLiteral)
                else -> null
            }

            val skills = HashMap<Skill, Float>()
            crewElem.getAttributeValue("all_skills")?.toInt()?.let { level ->
                for (skill in Skill.values()) {
                    // Use 0.5 times so that 1 is halfway (vanilla's green), and 2 is fully
                    // learnt (yellow).
                    skills[skill] = 0.5f * level
                }
            }
            for (skill in Skill.values()) {
                crewElem.getAttributeValue(skill.xmlName)?.toInt()?.let { level ->
                    skills[skill] = 0.5f * level
                }
            }

            for (i in 0 until count) {
                addedCrew.add(AddCrew(race, name, skills))
            }

            // Special-case the crew-removing event used in STATION_SICK
            if (count < 0) {
                for (i in 0 until -count) {
                    // Using an empty clone message hides the dialogue window
                    val emptyText = EventText("")

                    removedCrew.add(RemoveCrew(false, emptyText, null, race == "traitor", this))
                }
            }
        }

        val killCrew = elem.getChild("removeCrew")
        if (killCrew != null) {
            val race = killCrew.getChildText("class")
            val clone = killCrew.getChildText("clone")!!.toBoolean()
            val cloneText = loadText(killCrew.getChild("text"))
            removedCrew.add(RemoveCrew(clone, cloneText, race, false, this))
        }

        val boardersElem = elem.getChild("boarders")
        if (boardersElem != null) {
            // TODO what's the default race?
            boarderRace = boardersElem.getAttributeValue("class") ?: "random"
            val min = boardersElem.getAttributeValue("min").toInt()
            val max = boardersElem.getAttributeValue("max").toInt()
            boarderCount = min..max
        } else {
            boarderRace = null
            boarderCount = 0..0
        }

        val auto = elem.getChild("autoReward")
        autoRewards = if (auto != null) {
            val type = RewardType.valueOf(auto.textTrim.toUpperCase())
            val tier = RewardTier.fromName(auto.getAttributeValue("level"))
            Pair(type, tier)
        } else {
            null
        }

        blueprintRewards = ArrayList()
        for (e in listOf("weapon", "drone", "augment").flatMap(elem::getChildren)) {
            val name = e.requireAttributeValue("name")
            check(e.children.size == 0)
            // A random weapon or drone with xftlCombined="true" is always a
            // double or triple one (see LootPool.getCombinedWeapon), an XFTL
            // extension
            val combined = e.getAttributeValue("xftlCombined")?.toBoolean() ?: false
            check(!combined || (e.name in listOf("weapon", "drone") && name == "RANDOM")) {
                "xftlCombined is only for a random weapon or drone, in event $debugId"
            }
            blueprintRewards += when {
                combined && e.name == "drone" -> COMBINED_DRONE_REWARD
                combined -> COMBINED_WEAPON_REWARD
                name == "RANDOM" -> "xftl_rand_${e.name.toLowerCase()}"
                else -> name
            }
        }
        blueprintRewards.trimToSize()

        hullDamage = ArrayList()
        for (damageElem in elem.getChildren("damage")) {
            val amount = damageElem.getAttributeValue("amount")!!.toInt()
            val system: String? = damageElem.getAttributeValue("system")

            val effect: String? = damageElem.getAttributeValue("effect")
            val isFire = effect == "fire" || effect == "all"
            val isBreach = effect == "breach" || effect == "all"

            val scaled = damageElem.getAttributeValue("xftlScale") == "size"
            hullDamage.add(EventHullDamage(amount, system, isFire, isBreach, scaled))
        }

        systemUpgrades = ArrayList()
        for (upgradeElem in elem.getChildren("upgrade")) {
            val amount = upgradeElem.getAttributeValue("amount")!!.toInt()
            val system = upgradeElem.getAttributeValue("system")!!
            systemUpgrades.add(EventSystemUpgrade(amount, system))
        }

        statuses = ArrayList()
        statusLoop@ for (statusElem in elem.getChildren("status")) {
            val op = when (val type = statusElem.getAttributeValue("type")) {
                "limit" -> EventStatus.Operation.LIMIT
                "clear" -> EventStatus.Operation.CLEAR
                "divide" -> EventStatus.Operation.DIVIDE
                "loss" -> EventStatus.Operation.LOSS
                else -> {
                    println("Warning: unimplemented status type '$type' in event '$debugId'")
                    continue@statusLoop
                }
            }

            val target = when (val tgt = statusElem.getAttributeValue("target")) {
                "player" -> EventStatus.Target.PLAYER
                "enemy" -> EventStatus.Target.ENEMY
                else -> {
                    println("Warning: unimplemented target '$tgt' in event '$debugId'")
                    continue@statusLoop
                }
            }

            val system = statusElem.getAttributeValue("system")

            // In the case of type=clear, no amount is needed.
            val amount = statusElem.getAttributeValue("amount")?.toInt() ?: 0

            statuses.add(EventStatus(system, op, target, amount))
        }

        removedStuff = ArrayList()
        for (e in elem.getChildren("remove")) {
            removedStuff += e.requireAttributeValue("name")
        }
    }

    val loadShipName: String?
    val loadShipHostile: Boolean?

    init {
        val ship = elem.getChild("ship")
        if (ship != null) {
            check(ship.children.isEmpty()) { "Inline ship specs not yet supported" }
            loadShipName = ship.getAttributeValue("load")
            loadShipHostile = ship.getAttributeValue("hostile")?.toBoolean()
        } else {
            loadShipName = null
            loadShipHostile = null
        }
    }

    val backImg: ImageList?
    val planetImg: ImageList?

    init {
        val img = elem.getChild("img")
        backImg = img?.getAttributeValue("back")?.let(imageFinder)
        planetImg = img?.getAttributeValue("planet")?.let(imageFinder)
    }

    val environment: Beacon.EnvironmentType?

    /**
     * The hazards the event's beacon has on top of its [environment], an XFTL
     * extension (`<environment type="nebula" xftlHazards="asteroid,sun"/>`),
     * for the bosses' beacons (see doc/game/bosses.md and GameMap.addHazards).
     */
    val extraHazards: List<Beacon.EnvironmentType> =
        elem.getChild("environment")?.getAttributeValue("xftlHazards")
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.map { parseEnvironment(it) ?: error("Unknown hazard $it in event $debugId") }
            ?: emptyList()

    /**
     * Who the beacon's Anti-Ship Battery fires at, or null if it hasn't got
     * one: `<environment type="PDS" target="player"/>` (see AntiShipBattery).
     */
    val antiShipBattery: AntiShipBattery.Target? = elem.getChild("environment")
        ?.takeIf { it.getAttributeValue("type") == "PDS" }
        ?.let { AntiShipBattery.Target.parse(it.getAttributeValue("target")) }

    init {
        environment = parseEnvironment(elem.getChild("environment")?.requireAttributeValue("type"))
    }

    private fun parseEnvironment(env: String?): Beacon.EnvironmentType? {
        return when (env) {
            "asteroid" -> Beacon.EnvironmentType.ASTEROID
            "nebula" -> Beacon.EnvironmentType.NEBULA
            "pulsar" -> Beacon.EnvironmentType.PULSAR
            "storm" -> Beacon.EnvironmentType.ION_STORM
            // XFTL's own nebulas
            "radiation" -> Beacon.EnvironmentType.RADIATION
            "mirage" -> Beacon.EnvironmentType.MIRAGE
            "dark" -> Beacon.EnvironmentType.DARK
            "sun" -> Beacon.EnvironmentType.SUN
            // An Anti-Ship Battery isn't an environment of its own, but
            // something on top of the beacon's (see antiShipBattery)
            "PDS" -> null
            null -> null
            else -> error("Unknown environment $env")
        }
    }

    override fun resolve() = this

    /**
     * Evaluate all the random values in this event's rewards, and pack them into a ResourceSet
     */
    fun resolveResources(game: InGameState, rand: Random): ResourceSet {
        // The plain resources (fuel, missiles, etc)
        val size = game.player.type.shipSize
        val itemsScale = if (scaleItemsBySize) size else 1
        val resourcesGained = ResourceSet(itemsModify.mapValues { it.value.random(rand) * itemsScale })

        // Add all the blueprints
        resourcesGained.items += blueprintRewards.map { name ->
            when (name) {
                "xftl_rand_weapon" -> game.lootPool.getWeapon(rand)
                COMBINED_WEAPON_REWARD -> game.lootPool.getCombinedWeapon(rand)
                COMBINED_DRONE_REWARD -> game.lootPool.getCombinedDrone(rand)
                "xftl_rand_drone" -> game.lootPool.getDrone(rand)
                "xftl_rand_augment" -> game.lootPool.getAugment(rand)
                else -> game.blueprintManager[name].resolve()
            }
        }

        // Pick names for the new crewmembers
        for (crew in addedCrew) {
            val race = if (crew.race != null) {
                game.blueprintManager[crew.race] as CrewBlueprint
            } else {
                game.lootPool.getRandom(rand) { it is CrewBlueprint } as CrewBlueprint
            }

            val info = if (crew.name != null) {
                // Specifically named by the event (eg Slocknog)
                val name = game.translator[crew.name]
                LivingCrewInfo.generateWithName(race, game, name)
            } else {
                LivingCrewInfo.generateRandom(race, game)
            }

            info.skills.putAll(crew.skills)

            resourcesGained.crew.add(info)
        }

        // Select crewmembers to kill
        for (info in removedCrew) {
            val crew: LivingCrew

            val allCrew = game.player.crew
                .filterIsInstance(LivingCrew::class.java)
                .filter { it.ownerShip == game.player }

            if (allCrew.isEmpty()) {
                println("Warning: Cannot remove crewmember via event, no crew left.")
                continue
            }

            if (info.race == null) {
                crew = allCrew.random()
            } else {
                val ofRace = allCrew.filter { it.blueprint.name == info.race }
                if (ofRace.isEmpty()) {
                    println("Warning: Cannot remove crewmember of race '${info.race}', no crew of that race.")
                    continue
                }
                crew = ofRace.random()
            }

            resourcesGained.lostCrew.add(RemoveCrewEval(crew, info))
        }

        // Spawn boarders
        if (boarderRace != null) {
            var count = boarderCount.random() * (if (scaleBoardersBySize) size else 1)
            // At most a couple of them in the first sectors, as the start of
            // the game (see GameMap.effectiveSectorNumber)
            val map = game.gameMap
            val column = game.currentBeacon.sector.info.columnNumber
            if (map.effectiveSectorNumber(column, game.difficulty) <= EARLY_BOARDER_SECTORS)
                count = count.coerceAtMost(EARLY_BOARDERS)
            for (i in 0 until count) {
                val race = game.lootPool.getCrewOrRandom(rand, boarderRace)
                resourcesGained.intruders.add(LivingCrewInfo.generateRandom(race, game))
            }
        }

        // For hull damage, we don't need to resolve it - the rooms and systems
        // that are damaged aren't displayed, we only have to evaluate that
        // when the damage is actually applied.
        resourcesGained.damage += hullDamage.map {
            if (it.scaleBySize) EventHullDamage(it.amount * size, it.system, it.effectFire, it.effectBreach) else it
        }

        // System upgrades aren't randomised, so we can just copy them over as-is.
        resourcesGained.upgrades += systemUpgrades

        resourcesGained.modifyPursuit = modifyPursuit

        // Add the standard type/tier rewards - these are the standard results and most commonly used
        // eg destroying a ship usually gives STANDARD/MEDIUM rewards.
        if (autoRewards != null) {
            // (not rounded down, so they grow with every sector, see
            // GameMap.SectorInfo.rewardSectorNumber)
            val sector = game.currentBeacon.sector.rewardSectorNumber + 1
            val rewards = LootDropGenerator.generateRewards(game, rand, autoRewards.second, autoRewards.first, sector)
            if (scaleRewardBySize) {
                for (res in Resource.values()) {
                    rewards[res] = (rewards[res] ?: 0) * size
                }
            }
            resourcesGained += rewards
        }

        bossRewards?.addTo(resourcesGained, game, rand, size)

        // The Scrap Recovery Arm adds 10% to any scrap gained (each one, as
        // they stack). It's added here, so the reward shown is what's given.
        val scrapBonus = game.player.getAugmentValue(AugmentBlueprint.SCRAP_RECOVERY_ARM)
        if (resourcesGained.scrap > 0 && scrapBonus > 0f) {
            resourcesGained.scrap = (resourcesGained.scrap * (1f + scrapBonus)).roundToInt()
        }

        // The Repair Arm repairs two hull (for a bigger ship, as much more as
        // it is bigger) every time scrap's collected, for 15% of the scrap
        // (rounded down), unless the hull's already whole
        val player = game.player
        val repairCut = player.getAugmentValue(AugmentBlueprint.REPAIR_ARM)
        if (resourcesGained.scrap > 0 && repairCut > 0f && player.health < player.maxHealth) {
            resourcesGained.scrap = (resourcesGained.scrap * (1f - repairCut)).toInt()
            resourcesGained.damage += EventHullDamage(-REPAIR_ARM_HULL * player.type.shipSize, null, false, false)
        }

        return resourcesGained
    }

    init {
        // Check for and print out warnings for unknown elements
        for (child in elem.children) {
            if (child.name in KNOWN_TAGS)
                continue

            println("Warning: Unknown element '${child.name}' in event '$debugId'")
        }
    }

    companion object {
        private fun givesQuest(event: IEvent, visited: MutableSet<IEvent>): Boolean {
            if (!visited.add(event))
                return false
            return when (event) {
                is Event -> event.questName != null || event.choices.any { givesQuest(it.event, visited) }
                is EventList -> event.events.any { givesQuest(it, visited) }
                else -> false
            }
        }

        // The reward name for a random weapon with xftlCombined="true"
        private const val COMBINED_WEAPON_REWARD = "xftl_rand_combined_weapon"

        // And for a random drone with xftlCombined="true"
        private const val COMBINED_DRONE_REWARD = "xftl_rand_combined_drone"

        // How much hull the Repair Arm repairs each time scrap's collected
        const val REPAIR_ARM_HULL = 2

        // The first sectors (as the difficulty plays them, see
        // GameMap.effectiveSectorNumber), and the most boarders an event
        // sends there
        const val EARLY_BOARDER_SECTORS = 2
        const val EARLY_BOARDERS = 2

        private val KNOWN_TAGS = setOf(
            "distressBeacon", "store", "item_modify",
            "crewMember", "removeCrew", "boarders",
            "autoReward", "weapon", "drone", "augment",
            "ship", "img", "environment", "damage",
            "upgrade", "modifyPursuit", "status",
            "quest", "reveal_map", "remove", "fleet",
            "unlockShip", "repair", "xftlBossReward", "xftlBossOpening",

            // Used by the code loading the event
            "text", "choice",

            // Vanilla's two ways into the secret Crystal sector, which XFTL
            // doesn't have yet (its homeworlds are an act 2 sector in the
            // Chonkestrel mod instead), so it's ignored
            "secretSector",
            // Vanilla's GHOST_DOCK has an event straight inside another, not
            // in a choice, which vanilla ignores too
            "event",
        )
    }
}

class EventList(val name: String, events: List<Lazy<IEvent>>) : IEvent {
    val events by lazy { events.map { it.value } }

    override fun resolve() = events.random().resolve()

    override val debugId: String get() = name
}

class AddCrew(val race: String?, val name: GameText?, val skills: Map<Skill, Float>)

class RemoveCrew(
    /**
     * If true, this crew member will be saved by a clone bay.
     */
    val clone: Boolean,

    /**
     * If the ship has a clone bay, this is the message that either
     * says your crew has been recovered or explains why they haven't.
     *
     * Note: this is empty for the CRYSTAL_HELP_DIG events, we
     * shouldn't display a popup for those.
     */
    val cloneText: IEventText,

    /**
     * If this event removes a crewmember of a specific race, for
     * example the Engi Virus, this specifies it.
     */
    val race: String?,

    /**
     * If true, this crew member is left on the ship and converted
     * to an enemy, which the infectious space station event uses.
     *
     * This is actually set by <crewMember> not <removeCrew>.
     */
    val turnHostile: Boolean,

    /**
     * The event this object belongs to.
     *
     * This is used to uniquely identify a RemoveCrew object during deserialisation.
     */
    val event: Event
)

class EventHullDamage(
    val amount: Int,

    /**
     * The system this damage should be inflicted to.
     *
     * Null means hull damage only, "random" means a random system, and
     * "room" means a random room (regardless of whether it has a system
     * in it or not).
     */
    val system: String?,

    val effectFire: Boolean,
    val effectBreach: Boolean,

    /**
     * If true, the amount is multiplied by the size of the player's ship when
     * the event is resolved (`xftlScale="size"`, an XFTL extension).
     */
    val scaleBySize: Boolean = false
)

class EventSystemUpgrade(val amount: Int, val system: String)

/**
 * Defines some status effect (or removal thereof) for a ship at the current beacon.
 *
 * This is what's used to limit systems during events, for example
 * the slugs that hack your medbay or oxygen.
 */
class EventStatus(
    /**
     * The name of the system to affect.
     */
    val system: String,

    /**
     * The type of effect to place on the system.
     */
    val op: Operation,

    /**
     * Who is this status applying to?
     */
    val target: Target,

    val amount: Int
) {
    enum class Operation {
        CLEAR, // Remove any prior status effect
        DIVIDE, // Divide the system power by a set amount
        LIMIT, // Limit the system power to at most a set amount
        LOSS, // Subtract the given amount from the system power
    }

    enum class Target { PLAYER, ENEMY }
}

class Choice(val text: IEventText, lazyEvent: Lazy<IEvent>, elem: Element, val deserialisationId: String) {
    /**
     * Event to be triggered when this choice is taken. This element is required. One of:
     * A complete event, as detailed above (which can again contain choices).
     * Empty with just the load attribute given: loads the event with the given id.
     * Completely empty (<element />), if you want nothing to happen at all.
     */
    val event by lazyEvent

    /**
     * true/false, false if omitted. When set to false or omitted, it causes rewards (fuel, drone parts, scrap, etc)
     * in the choice's event (specified by <item_modify> or <autoReward>) to appear next to the choice's text.
     * See req for behavior of hidden when used alongside req.
     */
    val hidden: Boolean = elem.getAttributeValue("hidden")?.toBoolean() ?: false

    /**
     * The name of any race, weapon, drone, augmentation or system/subsystem.
     *
     * When hidden is set to true, the choice will be visible, able to be selected, and will be a blue choice
     *   (unless blue is set to false), if and only if the player has whatever is specified by the req, otherwise
     *   it will not be shown (hidden).
     * When hidden is omitted or set to false and the player does not have the listed req, the player will be able
     *   to see the choice but will be unable to select it (it will be grayed out).
     */
    val req: String? = elem.getAttributeValue("req")

    /**
     * The minimum level of a system required by [req].
     */
    val minLevel: Int? = elem.getAttributeValue("lvl")?.toInt()

    /**
     * The maximum level of a system required by [req].
     *
     * Note this is usually used with (or replaced by) [maxGroup].
     */
    val maxLevel: Int? = elem.getAttributeValue("max_lvl")?.toInt()

    /**
     * This defines a group of choices, only one of which can be displayed.
     *
     * If multiple choices with the same max_group attribute are available, all
     * except the last one (in the order defined in the XML) will be hidden.
     *
     * This behaviour doesn't seem to be documented anywhere, and is largely
     * guessed from the XML.
     */
    val maxGroup: Int? = elem.getAttributeValue("max_group")?.toInt()

    /**
     * true/false, true if omitted. Determines whether the choice will appear as (literally) a blue choice.
     * Only has meaning when used alongside req, as only req can make the choice blue in the first place.
     */
    val blue: Boolean = elem.getAttributeValue("blue")?.toBoolean() ?: (req != null)
}

interface IEventText {
    fun resolve(rand: Random): String
}

class TextList(val name: String, val items: List<IEventText>) : IEventText {
    override fun resolve(rand: Random): String = items.random(rand).resolve(rand)
}

class EventText(val localised: String) : IEventText {
    override fun resolve(rand: Random): String = localised

    // TODO handle the 'planet' and 'back' image names
}

class ImageList(val name: String, val images: List<EnvironmentImage>) {
    // This makes sense in the context of Beacon.getEnvironmentImages
    fun getRandom(seed: Int): EnvironmentImage? {
        if (this == NONE)
            return null

        if (images.isEmpty())
            return null

        return images[seed.absoluteValue % images.size]
    }

    companion object {
        // The special image list 'NONE' can be specified to remove an inherited image
        val NONE = ImageList("NONE", emptyList())
    }
}

/**
 * Represents an image specified in an <imageList>, used for a background or planet.
 */
class EnvironmentImage(val path: String) {
    fun getImg(game: InGameState): Image {
        return game.getImg(path)
    }

    // TODO do the width and height matter?
}

enum class FleetBackground(val xmlName: String) {
    REBEL("rebel"),
    FEDERATION("fed"),
    BOTH("battle")
}
