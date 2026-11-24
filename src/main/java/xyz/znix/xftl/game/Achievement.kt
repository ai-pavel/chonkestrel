package xyz.znix.xftl.game

import org.jdom2.Element
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.GameText
import xyz.znix.xftl.Translator
import xyz.znix.xftl.getGameTextChild
import xyz.znix.xftl.requireAttributeValue

class Achievement(elem: Element) {
    val id: String = elem.requireAttributeValue("id")
    val name: GameText = elem.getGameTextChild("name")!!
    val desc: GameText = elem.getGameTextChild("desc")!!
    val img: String = "img/" + elem.getChildTextTrim("img")
    val ship: String? = elem.getChildTextTrim("ship")
    val isMultiDifficulty: Boolean = elem.getChildTextTrim("multiDifficulty") == "1"

    /**
     * What XFTL tracks to unlock it (see [AchievementTracker]), an XFTL
     * extension: `<xftlGoal kind="..." target="N"/>`. It's null for
     * vanilla's achievements, and for a kind this version doesn't know,
     * which are then never unlocked by playing (as before).
     */
    val goal: AchievementGoal? = elem.getChild("xftlGoal")?.let { AchievementGoal.parse(id, it) }

    /**
     * Its description, with its goal's target put in place of `\1`, so
     * the number's only in the goal (see [AchievementGoal.target]).
     */
    fun description(translator: Translator): String {
        val text = translator[desc]
        val goal = goal ?: return text
        return text.replace("\\1", goal.target.toString())
    }

    class AchievementTable(df: Datafile) {
        /** The achievements by ID, in the order they're in the file (the mods' after vanilla's). */
        val achievements: Map<String, Achievement>

        init {
            val doc = df.parseXML(df["data/achievements.xml"])
            check(doc.rootElement.name == "FTL")

            val loaded = LinkedHashMap<String, Achievement>()
            for (elem in doc.rootElement.getChildren("achievement")) {
                val ach = Achievement(elem)
                loaded[ach.id] = ach
            }
            achievements = loaded
        }

        operator fun get(id: String): Achievement {
            return achievements[id] ?: error("No such achievement with ID '$id'")
        }

        /** The achievements XFTL tracks for the player ship [shipName] (see [AchievementTracker]), in order. */
        fun trackedFor(shipName: String): List<Achievement> =
            achievements.values.filter { it.ship == shipName && it.goal != null }

        /**
         * Every ship with achievements XFTL tracks, with them, in the
         * order the file has them.
         */
        fun trackedByShip(): Map<String, List<Achievement>> =
            achievements.values.filter { it.ship != null && it.goal != null }.groupBy { it.ship!! }
    }
}

/**
 * An achievement's goal, which XFTL tracks through a game (see
 * [AchievementTracker]): its [kind], and the number to reach, [target]
 * (such as 100 enemy crew killed). Each kind has its own default target.
 */
data class AchievementGoal(val kind: Kind, val target: Int) {
    /** How a kind's progress is kept, through a game. */
    enum class Progress {
        /** Something that happens (such as winning), checked when it does: no progress to show. */
        EVENT,

        /** A count that goes up through the game, such as missiles fired. */
        COUNT,

        /** The most of something seen at once, such as drones out. */
        BEST,
    }

    /**
     * The kinds of goal, by the name they have in the XML. [atMost] is for
     * an event whose value must be at most the target (such as crew lost),
     * rather than at least.
     */
    enum class Kind(val xmlName: String, val progress: Progress, val defaultTarget: Int = 1, val atMost: Boolean = false) {
        /** Beat the RSV Vigilance, the Blockade Line's boss. */
        BLOCKADE("blockade", Progress.EVENT),

        /** Win the game (beat the Mainframe's last phase). */
        WIN("win", Progress.EVENT),

        /** Win the game on Hard. */
        WIN_HARD("winHard", Progress.EVENT),

        /** Win a fight against this many ships at once (each destroyed or its crew killed). */
        FLEET_FIGHT("fleetFight", Progress.BEST, 3),

        /** Beat a boss without the hull going below this percent of its most. */
        BOSS_HULL("bossHull", Progress.EVENT, 50),

        /** Win the game with this many crew alive. */
        CREW_AT_WIN("crewAtWin", Progress.EVENT, 18),

        /** Finish off this many enemy ships with drones (their last hull hit a drone's). */
        DRONE_KILLS("droneKills", Progress.COUNT, 25),

        /** Have this many drones out at once. */
        DRONES_OUT("dronesOut", Progress.BEST, 9),

        /** Win the game with at most this many crew lost. */
        WIN_NO_LOSSES("winNoLosses", Progress.EVENT, 0, atMost = true),

        /** The player's shields stop this much damage. */
        SHIELD_ABSORBED("shieldAbsorbed", Progress.COUNT, 1000),

        /** Start a fight with a super shield this strong. */
        SUPER_SHIELD("superShield", Progress.BEST, 15),

        /** Beat a boss without losing any hull in that fight. */
        BOSS_UNHARMED("bossUnharmed", Progress.EVENT),

        /** The player's boarders kill this many enemy crew. */
        BOARDER_KILLS("boarderKills", Progress.COUNT, 100),

        /** Kill the crew of this many enemy ships. */
        CREW_KILLS("crewKills", Progress.COUNT, 15),

        /** Kill a boss's whole crew. */
        BOSS_CREW("bossCrew", Progress.EVENT),

        /** Mind control this many enemy crew. */
        MIND_CONTROLS("mindControls", Progress.COUNT, 20),

        /** This many hacks: each hacking probe landing, and each pulse. */
        HACKS("hacks", Progress.COUNT, 40),

        /** Enter this many nebula sectors. */
        NEBULA_SECTORS("nebulaSectors", Progress.COUNT, 3),

        /** Fire this many missiles. */
        MISSILES("missiles", Progress.COUNT, 400),

        /** Have this many fires burning on one enemy ship at once. */
        ENEMY_FIRES("enemyFires", Progress.BEST, 8),

        /** Win a fight with this many different weapons fired in it. */
        ALL_WEAPONS("allWeapons", Progress.BEST, 9),

        /** Suffocate this many enemy crew. */
        SUFFOCATED("suffocated", Progress.COUNT, 50),

        /** Win the game without ever having an oxygen system. */
        WIN_NO_OXYGEN("winNoOxygen", Progress.EVENT),

        /** Beat this many enemy ships with the player's boarders aboard. */
        BOARDED_WINS("boardedWins", Progress.COUNT, 10),

        /** This many enemy shots miss while the player's cloaked. */
        CLOAK_MISSES("cloakMisses", Progress.COUNT, 100),

        /** The player's Crystal crew lock this many rooms down. */
        LOCKDOWNS("lockdowns", Progress.COUNT, 25),

        /** Destroy this many enemy ships while cloaked. */
        CLOAKED_KILLS("cloakedKills", Progress.COUNT, 10),

        /** The player's artillery beams do this much hull damage to enemy ships. */
        ARTILLERY_DAMAGE("artilleryDamage", Progress.COUNT, 300),

        /** Finish off this many enemy ships with artillery (their last hull hit an artillery beam's). */
        ARTILLERY_KILLS("artilleryKills", Progress.COUNT, 10),

        /** Win the game without ever having a hacking or mind control system. */
        WIN_NO_MIND_TRICKS("winNoMindTricks", Progress.EVENT),

        /** Win the game with only humans in the crew. */
        WIN_ALL_HUMAN("winAllHuman", Progress.EVENT),

        /** Accept this many enemy ships' surrenders. */
        SURRENDERS("surrenders", Progress.COUNT, 5),

        /** Destroy a boss with an artillery beam's hit. */
        BOSS_ARTILLERY("bossArtillery", Progress.EVENT),

        /** This many enemy shots miss the player, cloaked or not. */
        DODGES("dodges", Progress.COUNT, 150),

        /** Win this many fights without losing any hull in them. */
        FLAWLESS_FIGHTS("flawlessFights", Progress.COUNT, 10),

        /** Reach this sector without ever having had a shields system. */
        NO_SHIELDS_SECTOR("noShieldsSector", Progress.BEST, 8),

        /** Fire this many volleys (a weapon firing) while cloaked. */
        CLOAKED_VOLLEYS("cloakedVolleys", Progress.COUNT, 50),

        /** Cloak this many times just as the last cloak ended (see AchievementTracker.CLOAK_CHAIN_TIME). */
        CLOAK_CHAINS("cloakChains", Progress.COUNT, 10),

        /** Destroy a boss while cloaked. */
        BOSS_CLOAKED("bossCloaked", Progress.EVENT);

        /** Whether it has a number to show as progress (see [AchievementTracker.progress]). */
        val isCounted: Boolean get() = progress != Progress.EVENT

        companion object {
            @JvmStatic
            fun byXmlName(name: String): Kind? = entries.firstOrNull { it.xmlName == name }
        }
    }

    /** Whether [value] reaches the goal. */
    fun isMetBy(value: Int): Boolean = if (kind.atMost) value <= target else value >= target

    companion object {
        /** The goal in [elem], or null (with a warning) if its kind isn't known. */
        fun parse(id: String, elem: Element): AchievementGoal? {
            val kindName = elem.getAttributeValue("kind")
            val kind = kindName?.let { Kind.byXmlName(it) }
            if (kind == null) {
                println("[WARN] Achievement '$id' has an unknown goal kind '$kindName'")
                return null
            }
            val target = elem.getAttributeValue("target")?.toIntOrNull() ?: kind.defaultTarget
            return AchievementGoal(kind, target)
        }
    }
}
