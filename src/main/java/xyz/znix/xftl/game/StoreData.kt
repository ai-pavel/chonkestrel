package xyz.znix.xftl.game

import org.jdom2.Element
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrewInfo
import xyz.znix.xftl.crew.Skill
import xyz.znix.xftl.crew.SkillLevel
import xyz.znix.xftl.rollChance
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.sector.SectorType
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import java.util.*
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Carries persistent information about a store, like its available items and prices.
 */
class StoreData {
    val availableResources = ResourceSet()

    val sections = ArrayList<Section>()

    /**
     * If this store has a systems section, this contains the systems, or null if they're sold out.
     */
    val systems = ArrayList<SystemBlueprint?>()

    /**
     * The drone that comes free with the drones system, if this store sells
     * it, as in vanilla: one of the common ones, at random. Null once it's
     * been given.
     */
    var freeDrone: DroneBlueprint? = null

    /**
     * If this store has a weapons section, this contains the weapons, or null if they're sold out.
     */
    val weapons = ArrayList<AbstractWeaponBlueprint?>()

    /**
     * If this store has a drones section, this contains the drones, or null if they're sold out.
     */
    val drones = ArrayList<DroneBlueprint?>()

    /**
     * If this store has an augments section, this contains the augments, or null if they're sold out.
     */
    val augments = ArrayList<AugmentBlueprint?>()

    /**
     * If this store has a crew section, this is the crew, or null if they're sold out.
     */
    val crew = ArrayList<LivingCrewInfo?>()

    // How many things are on each section's shelf, when it's filled: up to
    // ITEMS_PER_SECTION (the rest are shown empty), see StoreProfile
    private var itemsPerSection = ITEMS_PER_SECTION

    /**
     * Fill the store, as a store in a sector with [profile] is (see
     * doc/game/stores.md), [progress] through the game (0 to 1), in a
     * [sector] that may have its own chances of double and triple weapons.
     */
    fun generateRandomContents(
        game: InGameState, rand: Random,
        profile: StoreProfile = StoreProfile.CIVILIAN, progress: Float = 0f,
        sector: SectorType? = null
    ) {
        // See the original XFTL's doc/stores for generation details.
        // Stores are bigger than in vanilla, so they have more resources too
        // (and the more dangerous sectors' have less). Later on, the double
        // and triple weapons use up more missiles, so there are more of
        // them: twice as many by the end of the game. A bigger ship, which
        // takes more fuel a jump, finds as many jumps' worth of fuel.
        val scale = ITEMS_PER_SECTION / VANILLA_ITEMS_PER_SECTION * profile.resources
        fun resource(range: IntRange, factor: Float = 1f) = (range.random(rand) * scale * factor).roundToInt()
        availableResources[Resource.FUEL] = resource(3..7, game.player.type.shipSize.toFloat())
        availableResources[Resource.MISSILES] = resource(2..6, 1 + progress)
        availableResources[Resource.DRONES] = resource(2..4)

        // Generate the section types: the systems one (if there is one)
        // counts towards the store's sections
        val numSections = profile.sections.random(rand)
        itemsPerSection = profile.itemsPerSection.random(rand)

        val ship = game.player
        val numSystems = ship.systems.size

        val possibleSections = ArrayList<Section>()
        possibleSections += Section.AUGMENTS
        possibleSections += Section.CREW
        possibleSections += Section.DRONES
        possibleSections += Section.WEAPONS

        // On non-AE, systems don't spawn once you've got them all.
        if (game.content.enableAdvancedEdition || numSystems < 11) {
            possibleSections += Section.SYSTEMS
        }

        // If you have <11 systems (including subsystems), there's
        // a 50% chance the first section will be a systems section.
        // A bigger ship has more than that from the start, so for it, it's
        // while it has an empty system room left.
        val wantsSystems = when {
            ship.type.shipSize > 1 -> ship.systemSlots.any { it.system.rarity != 0 && it.room.system == null }
            else -> numSystems < 11
        }
        if (wantsSystems && rand.rollChance(50)) {
            possibleSections.remove(Section.SYSTEMS)
            sections += Section.SYSTEMS
        }

        // Randomly spawn the rest of the sections
        while (sections.size < numSections && possibleSections.isNotEmpty()) {
            sections += possibleSections.removeAt(possibleSections.indices.random(rand))
        }

        systems += generateSystems(game, rand)
        if (systems.any { it?.info == Drones.INFO })
            freeDrone = pickFreeDrone(game, rand)

        // The weapons, double and triple ones more often in the more
        // dangerous sectors, and further into the game (see WeaponSizes)
        if (sections.contains(Section.WEAPONS)) {
            weapons += game.lootPool.getManyRandom(
                AbstractWeaponBlueprint::class.java, rand, itemsPerSection,
                sector?.storeDoubleChance ?: profile.doubleChance(progress),
                sector?.storeTripleChance ?: profile.tripleChance, profile.rareBias
            )
        }
        // The drones, double and triple as often as the weapons (see
        // DroneSizes)
        if (sections.contains(Section.DRONES)) {
            drones += game.lootPool.getManyRandom(
                DroneBlueprint::class.java, rand, itemsPerSection,
                sector?.storeDoubleChance ?: profile.doubleChance(progress),
                sector?.storeTripleChance ?: profile.tripleChance, profile.rareBias
            )
        }
        augments += getForSection(game, rand, Section.AUGMENTS, AugmentBlueprint::class.java, profile)
        addZoltanShield(game, rand, progress)

        // The crew for hire, with more of their skills learnt further into
        // the game, and in the more dangerous sectors
        if (sections.contains(Section.CREW)) {
            crew += game.lootPool.getManyRandom(CrewBlueprint::class.java, rand, itemsPerSection, rareBias = profile.rareBias)
                .map { LivingCrewInfo.generateRandom(it, game).also { info -> trainCrew(info, profile, progress, rand) } }
        }
    }

    /**
     * Give a crew member for hire their skills: every skill a level or so
     * [progress] through the game (0 to 1), up to [CREW_LEVELS_BY_THE_END]
     * by the end, and on top of that, [profile]'s stores' crew have some
     * skills [PARTIAL_SKILL_LEVELS] levels further on, and some fully learnt,
     * picked at random.
     */
    private fun trainCrew(info: LivingCrewInfo, profile: StoreProfile, progress: Float, rand: Random) {
        val base = progress * CREW_LEVELS_BY_THE_END
        val skills = Skill.entries.shuffled(rand)
        for ((i, skill) in skills.withIndex()) {
            // (each a level more, or not, at random)
            val level = (base + rand.nextFloat()).toInt()
            info.skills[skill] = SkillLevel.progressAt(
                when {
                    i < profile.crewMaxSkills -> SkillLevel.MAX_LEVEL
                    i < profile.crewMaxSkills + profile.crewPartialSkills -> level + PARTIAL_SKILL_LEVELS
                    else -> level
                }.coerceAtMost(SkillLevel.MAX_LEVEL)
            )
        }
    }

    private fun <T> getForSection(
        game: InGameState, rand: Random, section: Section, type: Class<T>, profile: StoreProfile
    ): List<T?> {
        // Return an empty list to reduce the savefile size
        if (!sections.contains(section)) {
            return emptyList()
        }

        return game.lootPool.getManyRandom(type, rand, itemsPerSection, rareBias = profile.rareBias)
    }

    /**
     * Now and then, the augments section has a Zoltan Shield (which stacks,
     * see AugZoltanShield) in place of one of its augments: more often the
     * further through the game it is ([progress], 0 to 1), from
     * [ZOLTAN_SHIELD_CHANCE_START] to [ZOLTAN_SHIELD_CHANCE_END]. Not once
     * the ship has as many as stack.
     */
    private fun addZoltanShield(game: InGameState, rand: Random, progress: Float) {
        if (!sections.contains(Section.AUGMENTS) || augments.isEmpty())
            return
        val shield = game.blueprintManager.getOrNull(AugZoltanShield.NAME) as? AugmentBlueprint ?: return
        if (shield in augments || game.player.augments.count { it == shield } >= AugZoltanShield.MAX_STACKED)
            return
        val chance = ZOLTAN_SHIELD_CHANCE_START + (ZOLTAN_SHIELD_CHANCE_END - ZOLTAN_SHIELD_CHANCE_START) * progress
        if (rand.nextFloat() < chance)
            augments[augments.indices.random(rand)] = shield
    }

    // A common drone (rarity 1 or 2, such as Combat I or Defense I) to come
    // free with the drones system: one the player hasn't got yet (as a
    // single, twin or triple), if there is one, so a ship with several
    // drones systems doesn't end up with the same drone each time.
    private fun pickFreeDrone(game: InGameState, rand: Random): DroneBlueprint? {
        val ship = game.player
        val owned = (ship.droneSystems.flatMap { system -> system.drones.mapNotNull { it?.type } } +
                ship.cargoBlueprints.filterIsInstance<DroneBlueprint>()).map { it.baseName }.toSet()

        val common = game.blueprintManager.blueprints.values.filterIsInstance<DroneBlueprint>()
            .filter { (it.rarity ?: 0) in FREE_DRONE_RARITIES }
            .sortedBy { it.name }
        return common.filter { it.name !in owned }.ifEmpty { common }.randomOrNull(rand)
    }

    private fun generateSystems(game: InGameState, rand: Random): List<SystemBlueprint?> {
        val ship = game.player
        val systems = ArrayList<SystemBlueprint?>()

        // Return an empty list to reduce the savefile size
        if (!sections.contains(Section.SYSTEMS)) {
            return systems
        }

        // (not those with a rarity of 0, which are never sold, such as the
        // mod's piloting)
        val possibleSystems = ArrayList(
            game.blueprintManager.blueprints.values.filterIsInstance<SystemBlueprint>().filter { it.rarity != 0 }
        )
        val dronesSystem = possibleSystems.first { it.info == Drones.INFO }
        val shieldsSystem = possibleSystems.first { it.info == Shields.INFO }
        val medbaySystem = possibleSystems.first { it.info == Medbay.INFO }
        val clonebaySystem = possibleSystems.firstOrNull { it.info == Clonebay.INFO } // Null on non-AE

        val playerHasDrones = ship.systems.any { it is Drones }
        val playerHasShields = ship.systems.any { it is Shields }
        val playerHasMedical = ship.systems.any { it is Medbay || it is Clonebay }

        val forcedSystems = ArrayList<SystemBlueprint>()

        if (sections.contains(Section.DRONES) && !playerHasDrones) {
            forcedSystems += dronesSystem
            possibleSystems.remove(dronesSystem)
        }

        if (!playerHasShields) {
            forcedSystems += shieldsSystem
            possibleSystems.remove(shieldsSystem)
        }

        if (!playerHasMedical) {
            val medicalSystem = when {
                game.content.enableAdvancedEdition -> listOf(medbaySystem, clonebaySystem!!).random(rand)
                else -> medbaySystem
            }

            forcedSystems += medicalSystem

            // A forced medical system hides both the clonebay and medbay.
            possibleSystems.remove(medbaySystem)
            clonebaySystem?.let { possibleSystems.remove(it) }
        }

        // Only the systems the ship can still buy are offered: not those it
        // has (unless they replace one it has, as a clonebay does the
        // medbay), nor those it can't take at all (some modded ships can't
        // take every system)
        forcedSystems.removeIf { !canBuy(ship, it) }
        possibleSystems.removeIf { !canBuy(ship, it) }

        // The forced systems first (as in vanilla, they're always offered),
        // then the others at random, three at most in all (or however many
        // the store has a section). With fewer to offer, the rest of the
        // section's empty.
        // See the original XFTL's doc/stores for more information.
        val numSystems = min(itemsPerSection, forcedSystems.size + possibleSystems.size)
        systems += (forcedSystems + possibleSystems.shuffled(rand)).take(numSystems)

        return systems
    }

    /**
     * Whether [ship] can buy [system]: it has a free place for it (or one
     * it'd replace, as a clonebay does the medbay), and room for another
     * system.
     */
    private fun canBuy(ship: Ship, system: SystemBlueprint): Boolean {
        val slot = ship.findFreeSystemSlot(system) ?: return false
        val replaces = slot.room.system != null
        val isSubSystem = system.info?.isSubSystem == true
        if (isSubSystem && !replaces && ship.subSystems.size >= ship.maxSubSystems)
            return false
        if (!isSubSystem && !replaces && ship.mainSystems.size >= ship.maxMainSystems)
            return false
        return true
    }

    fun saveToXML(elem: Element) {
        for (section in sections) {
            val sectionElem = Element("section")
            SaveUtil.addAttr(sectionElem, "id", section.name)
            elem.addContent(sectionElem)
        }

        for ((name, count) in availableResources.entries) {
            elem.setAttribute(name.name, count.toString())
        }

        saveBlueprints(elem, "drone", drones)
        saveBlueprints(elem, "weapon", weapons)
        saveBlueprints(elem, "system", systems)
        saveBlueprints(elem, "augment", augments)
        freeDrone?.let { SaveUtil.addTag(elem, "freeDrone", it.name) }

        for ((i, crew) in crew.withIndex()) {
            if (crew == null)
                continue

            val crewElem = Element("crew")
            SaveUtil.addAttrInt(crewElem, "idx", i)
            crew.saveToXML(crewElem)
            elem.addContent(crewElem)
        }
    }

    fun loadFromXML(game: InGameState, elem: Element) {
        for (sectionElem in elem.getChildren("section")) {
            val sectionName = SaveUtil.getAttr(sectionElem, "id")
            sections += Section.valueOf(sectionName)
        }

        for (resource in Resource.entries) {
            val value = elem.getAttributeValue(resource.name)?.toInt() ?: continue
            availableResources[resource] = value
        }

        loadBlueprints(game, elem, "drone", drones, DroneBlueprint::class.java)
        loadBlueprints(game, elem, "weapon", weapons, AbstractWeaponBlueprint::class.java)
        loadBlueprints(game, elem, "system", systems, SystemBlueprint::class.java)
        loadBlueprints(game, elem, "augment", augments, AugmentBlueprint::class.java)
        freeDrone = elem.getChild("freeDrone")?.let { game.blueprintManager[SaveUtil.getAttr(it, "v")] as? DroneBlueprint }

        for (crewElem in elem.getChildren("crew")) {
            val idx = SaveUtil.getAttrInt(crewElem, "idx")
            while (crew.size <= idx) {
                crew.add(null)
            }

            crew[idx] = LivingCrewInfo.loadFromXML(crewElem, game)
        }
    }

    private fun saveBlueprints(elem: Element, name: String, items: List<Blueprint?>) {
        for ((i, item) in items.withIndex()) {
            if (item == null)
                continue

            val itemElem = Element(name)
            SaveUtil.addAttrInt(itemElem, "idx", i)
            SaveUtil.addAttr(itemElem, "type", item.name)
            elem.addContent(itemElem)
        }
    }

    private fun <T> loadBlueprints(
        game: InGameState,
        elem: Element, name: String,
        items: MutableList<T?>,
        type: Class<T>
    ) {
        for (itemElem in elem.getChildren(name)) {
            val idx = SaveUtil.getAttrInt(itemElem, "idx")
            val itemName = SaveUtil.getAttr(itemElem, "type")

            val item = game.blueprintManager[itemName]
            require(type.isInstance(item))
            @Suppress("UNCHECKED_CAST")
            val casted: T = run { item as T } // Run block needed to suppress the warning

            // Grow the items list until it can fit the item in the correct index
            while (items.size <= idx) {
                items.add(null)
            }

            items[idx] = casted
        }
    }

    companion object {
        /**
         * The chance of a store's augments section having a Zoltan Shield (see
         * addZoltanShield), at the start of the game and at the end.
         */
        const val ZOLTAN_SHIELD_CHANCE_START = 0.02f
        const val ZOLTAN_SHIELD_CHANCE_END = 0.25f

        /**
         * The skill level every skill of a crew member for hire has by the
         * end of the game (they start with none), see trainCrew.
         */
        private const val CREW_LEVELS_BY_THE_END = 3f

        /** How many levels further on a store's better-trained skills are. */
        private const val PARTIAL_SKILL_LEVELS = 2

        /** The rarities of the drones that can come free with the drones system. */
        private val FREE_DRONE_RARITIES = 1..2

        /**
         * The number of items in each section of a store. This is twice
         * as many as in vanilla, and [StoreWindow] makes room for them.
         */
        const val ITEMS_PER_SECTION = 6

        /**
         * How many missiles a store takes for a drone part: big ships go
         * through drone parts fast, so every store trades them, at about
         * what they're worth (a missile costs 6 scrap, and a drone part 8,
         * so a little less, as the store keeps the difference).
         */
        const val MISSILES_PER_DRONE_PART = 2

        /**
         * Whether stores show the trade (it's hidden for now, and only the
         * automatic playtest uses it).
         */
        const val SHOW_MISSILE_TRADE = false

        /**
         * Trade the [ship]'s missiles for drone parts, [times] times at most
         * (as long as it has the missiles): [missiles] missiles for [parts]
         * drone parts each time. Returns how many drone parts it got.
         */
        fun tradeMissilesForDrones(
            ship: Ship, times: Int = 1, missiles: Int = MISSILES_PER_DRONE_PART, parts: Int = 1
        ): Int {
            var got = 0
            var traded = 0
            while (traded < times && ship.missilesCount >= missiles) {
                ship.missilesCount -= missiles
                ship.dronesCount += parts
                got += parts
                traded++
            }
            return got
        }

        const val VANILLA_ITEMS_PER_SECTION = 3
    }

    enum class Section {
        AUGMENTS,
        CREW,
        DRONES,
        SYSTEMS,
        WEAPONS;

        val localisationKey get() = "store_title_" + name.toLowerCase(Locale.UK)
    }
}
