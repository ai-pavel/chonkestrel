package xyz.znix.xftl.game

import org.jdom2.Element
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.CrewNameManager
import xyz.znix.xftl.crew.Skill
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.DroneSizes
import xyz.znix.xftl.weapons.WeaponSizes
import kotlin.random.Random

/**
 * What's counted through a game, for its score and its record on the
 * statistics screen (see [GameRecord]), saved with the game.
 */
class GameStats {
    /** When the game started, in milliseconds since the epoch. */
    var startTime: Long = System.currentTimeMillis()

    /** How long it's been played, in seconds of real time, not counting the pause. */
    var playedSeconds: Float = 0f

    /** The beacons visited, each counted once (the start beacons among them). */
    var beaconsExplored: Int = 0

    /** The enemy ships destroyed, and those whose crew were all killed. */
    var shipsDestroyed: Int = 0
    var shipsCrewKilled: Int = 0

    /** The scrap the player's been given, by events, rewards and sales. */
    var scrapCollected: Int = 0

    /** The player's crew who died and weren't cloned, or who left for good. */
    var crewLost: Int = 0

    val shipsDefeated: Int get() = shipsDestroyed + shipsCrewKilled

    fun saveToXML(parent: Element) {
        val elem = Element("stats")
        SaveUtil.addAttr(elem, "start", startTime.toString())
        SaveUtil.addAttrFloat(elem, "played", playedSeconds)
        SaveUtil.addAttrInt(elem, "beacons", beaconsExplored)
        SaveUtil.addAttrInt(elem, "destroyed", shipsDestroyed)
        SaveUtil.addAttrInt(elem, "crewKilled", shipsCrewKilled)
        SaveUtil.addAttrInt(elem, "scrap", scrapCollected)
        SaveUtil.addAttrInt(elem, "crewLost", crewLost)
        parent.addContent(elem)
    }

    companion object {
        /** The stats saved in [parent], or new ones for an older save without them. */
        @JvmStatic
        fun loadFromXML(parent: Element): GameStats {
            val stats = GameStats()
            val elem = parent.getChild("stats") ?: return stats
            fun int(name: String) = elem.getAttributeValue(name)?.toInt() ?: 0
            stats.startTime = elem.getAttributeValue("start")?.toLong() ?: stats.startTime
            stats.playedSeconds = elem.getAttributeValue("played")?.toFloat() ?: 0f
            stats.beaconsExplored = int("beacons")
            stats.shipsDestroyed = int("destroyed")
            stats.shipsCrewKilled = int("crewKilled")
            stats.scrapCollected = int("scrap")
            stats.crewLost = int("crewLost")
            return stats
        }
    }
}

/**
 * A finished game, as the statistics screen shows it (see
 * StatisticsWindow): its score, how it went, and the ship as it was at the
 * end. Its crew's races, weapons, drones and augments are blueprint names,
 * so they can be drawn as the game draws them; one the game no longer has
 * (after the mods change, say) is shown by its name. The best are kept in
 * the player's profile (see SaveProfile.topGames).
 */
data class GameRecord(
    val score: Int,
    val outcome: GameOverWindow.Outcome,
    val difficulty: Difficulty,
    val shipName: String,
    val startTime: Long,
    val durationSeconds: Int,
    val sector: Int,
    val beacons: Int,
    val shipsDestroyed: Int,
    val shipsCrewKilled: Int,
    val scrap: Int,
    val crewLost: Int,
    val crew: List<CrewRecord>,
    val systems: List<SystemRecord>,
    val weapons: List<String>,
    val drones: List<String>,
    val augments: List<String>,
    /** The crew names preset the game was played with (see CrewNameManager). */
    val namePreset: String = CrewNameManager.DEFAULT_PRESET,
) {
    /**
     * A crewmember at the end of the game: their name, their race's
     * blueprint name and colour (as LivingCrewInfo has them, to draw them),
     * and each skill's level, in [Skill]'s order (records from before the
     * Drones skill, the last, have just the six before it; their crew
     * hadn't learnt it).
     */
    data class CrewRecord(val name: String, val race: String, val colour: Int, val skills: List<Int>)

    /** A system at the end of the game: its codename (such as shields), and its level. */
    data class SystemRecord(val name: String, val level: Int)

    val isWin: Boolean get() = outcome == GameOverWindow.Outcome.WIN

    fun saveToXML(): Element {
        val elem = Element("game")
        SaveUtil.addAttrInt(elem, "score", score)
        SaveUtil.addAttr(elem, "outcome", outcome.name)
        SaveUtil.addAttr(elem, "difficulty", difficulty.name)
        SaveUtil.addAttr(elem, "ship", shipName)
        SaveUtil.addAttr(elem, "start", startTime.toString())
        SaveUtil.addAttrInt(elem, "duration", durationSeconds)
        SaveUtil.addAttrInt(elem, "sector", sector)
        SaveUtil.addAttrInt(elem, "beacons", beacons)
        SaveUtil.addAttrInt(elem, "destroyed", shipsDestroyed)
        SaveUtil.addAttrInt(elem, "crewKilled", shipsCrewKilled)
        SaveUtil.addAttrInt(elem, "scrap", scrap)
        SaveUtil.addAttrInt(elem, "crewLost", crewLost)
        SaveUtil.addAttr(elem, "names", namePreset)
        for (member in crew) {
            val crewElem = Element("crew")
            SaveUtil.addAttr(crewElem, "name", member.name)
            SaveUtil.addAttr(crewElem, "race", member.race)
            SaveUtil.addAttrInt(crewElem, "colour", member.colour)
            SaveUtil.addAttr(crewElem, "skills", member.skills.joinToString(","))
            elem.addContent(crewElem)
        }
        for (system in systems) {
            val systemElem = Element("system")
            SaveUtil.addAttr(systemElem, "name", system.name)
            SaveUtil.addAttrInt(systemElem, "level", system.level)
            elem.addContent(systemElem)
        }
        for ((tag, names) in listOf("weapon" to weapons, "drone" to drones, "augment" to augments)) {
            for (name in names) {
                val itemElem = Element(tag)
                SaveUtil.addAttr(itemElem, "name", name)
                elem.addContent(itemElem)
            }
        }
        return elem
    }

    companion object {
        /** How many games the profile keeps, the best by score. */
        const val TOP_GAMES = 20

        /**
         * The score for a game: 1 for each scrap collected, 10 for each beacon
         * explored, 20 for each ship defeated and 50 for each sector reached,
         * less 10 for each crewmember lost, then times the difficulty's
         * multiplier (half on easy, half again on hard, double on insane), and
         * doubled for a win.
         */
        @JvmStatic
        fun score(stats: GameStats, sector: Int, win: Boolean, difficulty: Difficulty): Int {
            val base = stats.scrapCollected + 10 * stats.beaconsExplored + 20 * stats.shipsDefeated +
                    50 * sector - 10 * stats.crewLost
            val multiplier = difficulty.scoreMultiplier
            return (base.coerceAtLeast(0) * multiplier * (if (win) 2 else 1)).toInt()
        }

        /** The record of [game], just finished with [outcome]. */
        @JvmStatic
        fun of(game: InGameState, outcome: GameOverWindow.Outcome): GameRecord {
            val player = game.player
            val translator = game.translator
            val stats = game.stats
            val sector = game.currentBeacon.sector.sectorNumber + 1
            val win = outcome == GameOverWindow.Outcome.WIN
            return GameRecord(
                score = score(stats, sector, win, game.difficulty),
                outcome = outcome,
                difficulty = game.difficulty,
                shipName = shipName(player.type.shipTitle?.let { translator[it] } ?: player.name),
                startTime = stats.startTime,
                durationSeconds = stats.playedSeconds.toInt(),
                sector = sector,
                beacons = stats.beaconsExplored,
                shipsDestroyed = stats.shipsDestroyed,
                shipsCrewKilled = stats.shipsCrewKilled,
                scrap = stats.scrapCollected,
                crewLost = stats.crewLost,
                crew = game.playerCrew.map { crew ->
                    CrewRecord(crew.info.name, crew.info.race.name, crew.info.colour,
                        Skill.entries.map { crew.getSkillLevel(it).level })
                },
                systems = player.systems.map { system ->
                    SystemRecord(system.codename, system.energyLevels)
                },
                weapons = player.hardpoints.mapNotNull { it.weapon?.type?.name },
                drones = player.droneSystems.flatMap { it.drones }.mapNotNull { it?.type?.name },
                augments = player.augments.map { it.name },
                namePreset = game.namePreset ?: CrewNameManager.DEFAULT_PRESET,
            )
        }

        /**
         * A made-up game, to fill the statistics screen for testing it (see
         * XftlDebug.randomScores), with the weapons, drones, augments and
         * races the game has, and its crew named from a random preset of
         * names (see CrewNameManager), from [names], which gives the names for
         * a preset. The further it got (so the higher it scores),
         * the more it has, as a real game would: more crew, more skilled,
         * more and better weapons and drones (more of them twins and
         * triples), more augments (the mod's own among them), and higher
         * system levels, up to the Chonkestrel's limits.
         */
        @JvmStatic
        fun random(blueprints: BlueprintManager, names: (String) -> CrewNameManager, rand: Random): GameRecord {
            val outcome = GameOverWindow.Outcome.entries.filter { it != GameOverWindow.Outcome.LOOSE_BASE_DESTROYED }.random(rand)
            val win = outcome == GameOverWindow.Outcome.WIN
            val sector = if (win) 16 else rand.nextInt(1, 17)
            // How far through the game it got, from 0 to 1, with a little luck
            val progress = (sector / 16f + rand.nextFloat() * 0.2f - 0.1f).coerceIn(0f, 1f)
            fun scaled(least: Int, most: Int): Int =
                (least + (most - least) * progress + rand.nextInt(-1, 2)).toInt().coerceIn(least, most)

            val all = blueprints.blueprints.values.filterIsInstance<Blueprint>()
            val races = all.filterIsInstance<CrewBlueprint>().filter { (it.rarity ?: 0) > 0 }
                .ifEmpty { all.filterIsInstance<CrewBlueprint>() }

            // A weapon or drone: a single one, or its twin or triple (if the
            // game has them), more often the further it got
            fun <T : Blueprint> item(pool: List<T>, nameFor: (String, Int) -> String): String {
                val name = pool.random(rand).name
                val size = when {
                    rand.nextFloat() >= progress -> 1
                    rand.nextFloat() < progress * 0.6f -> 3
                    else -> 2
                }
                val combined = nameFor(name, size)
                return if (size > 1 && blueprints.getOrNull(combined) != null) combined else name
            }
            val weapons = all.filterIsInstance<AbstractWeaponBlueprint>()
                .filter { (it.rarity ?: 0) > 0 && !it.isCombined }
            val drones = all.filterIsInstance<DroneBlueprint>()
                .filter { (it.rarity ?: 0) > 0 && !it.isCombined }
            // Any augment with a name, the mod's own (found nowhere but
            // their quests and bosses) among them
            val augments = all.filterIsInstance<AugmentBlueprint>().filter { it.title != null }

            // The crew's names, all from one preset, as a game's are
            val namePreset = (CrewNameManager.PRESETS + CrewNameManager.DEFAULT_PRESET).random(rand)
            val nameManager = names(namePreset)

            val stats = GameStats().apply {
                startTime = System.currentTimeMillis() - rand.nextLong(0, 90L * 24 * 3600 * 1000)
                playedSeconds = sector * rand.nextInt(300, 900).toFloat()
                beaconsExplored = sector * rand.nextInt(4, 9)
                shipsDestroyed = sector * rand.nextInt(1, 4)
                shipsCrewKilled = rand.nextInt(0, sector + 1)
                scrapCollected = sector * rand.nextInt(60, 160)
                crewLost = rand.nextInt(0, 8)
            }
            val difficulty = Difficulty.entries.random(rand)
            return GameRecord(
                score = score(stats, sector, win, difficulty),
                outcome = outcome,
                difficulty = difficulty,
                shipName = "Chonkestrel",
                startTime = stats.startTime,
                durationSeconds = stats.playedSeconds.toInt(),
                sector = sector,
                beacons = stats.beaconsExplored,
                shipsDestroyed = stats.shipsDestroyed,
                shipsCrewKilled = stats.shipsCrewKilled,
                scrap = stats.scrapCollected,
                crewLost = stats.crewLost,
                crew = List(scaled(4, 18)) {
                    val race = races.random(rand)
                    val colour = rand.nextInt(0, race.numberOfColours.coerceAtLeast(1))
                    // (a human's sex goes with their look, as LivingCrewInfo.isFemale has it)
                    val male = when {
                        race.name != "human" -> null
                        else -> !(race.baseNumberOfColours > 0 && colour >= race.baseNumberOfColours)
                    }
                    CrewRecord(nameManager.getForGender(male, rand), race.name, colour, Skill.entries.map { scaled(0, 5) })
                },
                systems = randomSystems(progress, rand),
                weapons = if (weapons.isEmpty()) emptyList() else List(scaled(2, 8)) { item(weapons, WeaponSizes::nameFor) },
                drones = if (drones.isEmpty()) emptyList() else List(scaled(0, 6)) { item(drones, DroneSizes::nameFor) },
                augments = augments.shuffled(rand).take(scaled(0, 6)).map { it.name },
                namePreset = namePreset,
            )
        }

        /**
         * The systems of a made-up game (see [random]), as the Chonkestrel
         * might have them [progress] of the way through the game: the
         * further on, the more of them (several shields, weapons, drones,
         * engines and sensors systems, and the rarer systems), and the
         * higher their levels.
         */
        private fun randomSystems(progress: Float, rand: Random): List<SystemRecord> {
            fun count(least: Int, most: Int): Int =
                (least + (most - least) * progress + rand.nextFloat() - 0.5f).toInt().coerceIn(least, most)
            fun level(most: Int): Int =
                (1 + (most - 1) * progress + rand.nextInt(-1, 2)).toInt().coerceIn(1, most)

            val systems = ArrayList<SystemRecord>()
            fun add(name: String, copies: Int, most: Int) = repeat(copies) { systems += SystemRecord(name, level(most)) }
            add("shields", count(1, 2), 8)
            add("engines", count(1, 2), 8)
            add("oxygen", 1, 3)
            add("weapons", count(1, 3), 8)
            add("drones", count(0, 2), 8)
            add("medbay", 1, 3)
            add("pilot", 1, 3)
            add("sensors", count(1, 2), 3)
            add("doors", 1, 3)
            // The rarer systems, more of them the further it got
            for (name in listOf("teleporter", "cloaking", "hacking", "mind", "clonebay", "battery").shuffled(rand).take(count(0, 6)))
                add(name, 1, if (name == "battery") 2 else 8)
            return systems
        }

        @JvmStatic
        fun loadFromXML(elem: Element): GameRecord? = try {
            fun int(name: String) = elem.getAttributeValue(name)?.toInt() ?: 0
            GameRecord(
                score = int("score"),
                outcome = GameOverWindow.Outcome.valueOf(elem.getAttributeValue("outcome")),
                difficulty = Difficulty.valueOf(elem.getAttributeValue("difficulty")),
                shipName = shipName(elem.getAttributeValue("ship") ?: ""),
                startTime = elem.getAttributeValue("start")?.toLong() ?: 0L,
                durationSeconds = int("duration"),
                sector = int("sector"),
                beacons = int("beacons"),
                shipsDestroyed = int("destroyed"),
                shipsCrewKilled = int("crewKilled"),
                scrap = int("scrap"),
                crewLost = int("crewLost"),
                crew = elem.getChildren("crew").map { crewElem ->
                    CrewRecord(
                        crewElem.getAttributeValue("name") ?: "",
                        crewElem.getAttributeValue("race") ?: "",
                        crewElem.getAttributeValue("colour")?.toInt() ?: 0,
                        crewElem.getAttributeValue("skills")?.split(",")?.filter { it.isNotBlank() }?.map { it.toInt() }
                            ?: emptyList()
                    )
                },
                systems = elem.getChildren("system").map {
                    SystemRecord(it.getAttributeValue("name") ?: "", it.getAttributeValue("level")?.toInt() ?: 0)
                },
                weapons = elem.getChildren("weapon").map { it.getAttributeValue("name") ?: "" },
                drones = elem.getChildren("drone").map { it.getAttributeValue("name") ?: "" },
                augments = elem.getChildren("augment").map { it.getAttributeValue("name") ?: "" },
                namePreset = elem.getAttributeValue("names") ?: CrewNameManager.DEFAULT_PRESET,
            )
        } catch (e: IllegalArgumentException) {
            // (one from a later version, say, with an outcome this one doesn't know)
            null
        }

        /**
         * The ship's name as the statistics screen shows it: its title in
         * the hangar, without the "The" (the Chonkestrel, not The
         * Chonkestrel). A record from before took its class instead, the
         * Chonkestrel Cruiser, which is the Chonkestrel too.
         */
        private fun shipName(title: String): String = when (title) {
            "Chonkestrel Cruiser" -> "Chonkestrel"
            else -> title.removePrefix("The ")
        }

        /** [games] and [record], the best [TOP_GAMES] of them by score, best first. */
        @JvmStatic
        fun topGames(games: List<GameRecord>, record: GameRecord): List<GameRecord> =
            (games + record).sortedByDescending { it.score }.take(TOP_GAMES)
    }
}
