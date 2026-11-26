package xyz.znix.xftl.game

import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.JDOMException
import org.jdom2.input.SAXBuilder
import xyz.znix.xftl.XftlDebug
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents the user's profile: achievements/ships/high scores/settings/etc.
 *
 * Unlike the game state saves, this is supposed to be durable, as it'd be
 * much, much worse to lose it than a savegame.
 */
class SaveProfile private constructor() {
    /**
     * Store the unlocked achievements by ID, rather than using [Achievement]
     * objects, so that profiles don't get broken if you add or remove a mod.
     */
    private val unlockedAchievements = HashMap<String, AchievementUnlockInfo>()

    /**
     * The most progress made in a single game towards each achievement
     * XFTL tracks (see AchievementTracker), by ID, for the achievements
     * window. Only those with some progress are kept.
     */
    private val achievementProgress = HashMap<String, Int>()

    /**
     * Stores the ships (indexed by their unlockShip id) which the player
     * has completed the unlock quest for.
     *
     * This re-uses the AchievementUnlockInfo class, since we need to track
     * the difficulty the quests were completed on to show in the UI.
     */
    private val unlockedShips = HashMap<String, AchievementUnlockInfo>()

    /**
     * Stores all the user-modified hotkeys. This maps from the hotkey ID
     * to that of the key. It's stored here as a string mapping, as mods
     * will be able to add their own keybinds and we don't want to delete
     * those if the user loads up without said mod.
     *
     * Keys that are mapped to their default values aren't stored here,
     * which means that adding a mod won't immediately fill your profile
     * with all it's keybinds unless you modify them.
     *
     * Mapping to a null string means the key is unbound.
     */
    private val keybinds = HashMap<String, String?>()

    // These must be manually marked as dirty, since they can change quite
    // a lot when the slider is being moved.
    var musicVolume: Float = 1f
    var soundVolume: Float = 1f

    /**
     * The size of the game's window the player picked in the display
     * options (in the screen's units), or null for the size it starts at.
     */
    var displayWindowSize: Pair<Int, Int>? = null
        set(value) {
            field = value
            dirty = true
        }

    /**
     * Whether the jump map shows every connection between the sector's
     * beacons, not just those from the player's beacon and the hovered one.
     */
    var mapShowAllConnections: Boolean = false
        set(value) {
            field = value
            dirty = true
        }

    /**
     * Whether the jump map previews where the Rebel fleet's front will be
     * after each of the next jumps.
     */
    var mapShowFleetForecast: Boolean = false
        set(value) {
            field = value
            dirty = true
        }

    /**
     * The code of the language the player picked in the options (see
     * [xyz.znix.xftl.Language]), or null if there's none yet (a new
     * profile, or an older one), for the computer's language.
     */
    var language: String? = null
        set(value) {
            field = value
            dirty = true
        }

    /**
     * Set to true if this profile needs saving.
     */
    var dirty = false
        private set

    /**
     * The best finished games, by score, best first, at most
     * [GameRecord.TOP_GAMES] of them, for the statistics screen.
     */
    var topGames: List<GameRecord> = emptyList()
        private set

    /** Add a finished game to [topGames], if it's among the best. */
    fun addGameRecord(record: GameRecord) {
        topGames = GameRecord.topGames(topGames, record)
        dirty = true
    }

    /**
     * Forget every game in [topGames] (and any made-up ones), for the
     * statistics screen's Reset. The achievements stay.
     */
    fun clearGameRecords() {
        topGames = emptyList()
        randomTopGames = null
        dirty = true
    }

    /**
     * Made-up games the statistics screen shows instead of [topGames], to
     * see how it looks (see XftlDebug.randomScores), or null. They're never
     * saved.
     */
    var randomTopGames: List<GameRecord>? = null
        set(value) {
            field = value?.sortedByDescending { it.score }?.take(GameRecord.TOP_GAMES)
        }

    /** The games the statistics screen shows: the made-up ones, if there are any, or the real ones. */
    val shownTopGames: List<GameRecord> get() = randomTopGames ?: topGames

    /**
     * How [ach] was unlocked, or null if it's not. In debug mode
     * (XFTL_DEBUG) every achievement shows as unlocked, on Normal if it's
     * not really; that isn't saved in the profile.
     */
    fun getAchievement(ach: Achievement): AchievementUnlockInfo? {
        return unlockedAchievements[ach.id] ?: DEBUG_UNLOCK.takeIf { XftlDebug.enabled }
    }

    /**
     * Remove an achievement, if it's unlocked. This is intended for debugging.
     */
    fun deleteAchievement(ach: Achievement) {
        unlockedAchievements.remove(ach.id)
        dirty = true
    }

    fun unlockAchievement(ach: Achievement, difficulty: Difficulty) {
        val current = unlockedAchievements[ach.id]

        // If we've already unlocked the achievement on a harder difficulty,
        // don't overwrite it with the easier unlock.
        if (current != null && current.difficulty.ordinal <= difficulty.ordinal) {
            return
        }

        unlockedAchievements[ach.id] = AchievementUnlockInfo(difficulty)
        dirty = true
    }

    /** The most progress made in a single game towards [ach] (see AchievementTracker), or 0. */
    fun getAchievementProgress(ach: Achievement): Int = achievementProgress[ach.id] ?: 0

    /** Keep [value] as [ach]'s best progress in a game, if it's more than before. */
    fun recordAchievementProgress(ach: Achievement, value: Int) {
        if (value <= getAchievementProgress(ach))
            return
        achievementProgress[ach.id] = value
        dirty = true
    }

    /** Forget [ach]'s best progress. This is intended for debugging. */
    fun deleteAchievementProgress(ach: Achievement) {
        if (achievementProgress.remove(ach.id) != null)
            dirty = true
    }

    fun getShipUnlock(shipFamily: ShipFamily): AchievementUnlockInfo? {
        requireNotNull(shipFamily.unlockId)
        return unlockedShips[shipFamily.unlockId]
    }

    fun deleteShipUnlock(shipFamily: ShipFamily) {
        requireNotNull(shipFamily.unlockId)
        unlockedShips.remove(shipFamily.unlockId)
        dirty = true
    }

    fun unlockShip(shipFamily: ShipFamily, difficulty: Difficulty) {
        requireNotNull(shipFamily.unlockId)

        val current = unlockedShips[shipFamily.unlockId]

        // If we've already unlocked the achievement on a harder difficulty,
        // don't overwrite it with the easier unlock.
        if (current != null && current.difficulty.ordinal <= difficulty.ordinal) {
            return
        }

        unlockedShips[shipFamily.unlockId] = AchievementUnlockInfo(difficulty)
        dirty = true
    }

    fun getKeybinds(): Map<String, String?> {
        return keybinds
    }

    fun setKeybind(action: Hotkey, button: HotkeyButton) {
        keybinds[action.id] = button.id
        dirty = true
    }

    fun unbindKey(action: Hotkey) {
        keybinds[action.id] = null
        dirty = true
    }

    fun resetHotkeyToDefault(action: Hotkey) {
        keybinds.remove(action.id)
        dirty = true
    }

    fun save(): Document {
        val doc = Document(Element("xftl-profile"))
        val root = doc.rootElement

        for ((id, info) in unlockedAchievements) {
            val elem = Element("ach")
            root.addContent(elem)

            elem.setAttribute("id", id)
            elem.setAttribute("diff", info.difficulty.name)
        }

        for ((id, value) in achievementProgress) {
            val elem = Element("achProgress")
            root.addContent(elem)

            elem.setAttribute("id", id)
            elem.setAttribute("value", value.toString())
        }

        for ((id, info) in unlockedShips) {
            val elem = Element("ship")
            root.addContent(elem)

            elem.setAttribute("unlockId", id)
            elem.setAttribute("diff", info.difficulty.name)
        }

        for ((hotkey, button) in keybinds) {
            val elem = Element("hotkey")
            root.addContent(elem)

            elem.setAttribute("action", hotkey)
            if (button != null) {
                elem.setAttribute("key", button)
            }
        }

        val volumes = Element("soundVolume")
        root.addContent(volumes)
        volumes.setAttribute("sfx", soundVolume.toString())
        volumes.setAttribute("music", musicVolume.toString())

        displayWindowSize?.let { (width, height) ->
            val display = Element("display")
            root.addContent(display)
            display.setAttribute("windowWidth", width.toString())
            display.setAttribute("windowHeight", height.toString())
        }

        val map = Element("map")
        root.addContent(map)
        map.setAttribute("showAllConnections", mapShowAllConnections.toString())
        map.setAttribute("showFleetForecast", mapShowFleetForecast.toString())

        language?.let { code ->
            val elem = Element("language")
            root.addContent(elem)
            elem.setAttribute("code", code)
        }

        if (topGames.isNotEmpty()) {
            val games = Element("topGames")
            root.addContent(games)
            for (record in topGames)
                games.addContent(record.saveToXML())
        }

        return doc
    }

    /**
     * Request that the profile should be saved.
     */
    fun markDirty() {
        dirty = true
    }

    fun markSaveComplete() {
        dirty = false
    }

    private fun load(doc: Document) {
        val root = doc.rootElement
        check(root.name == "xftl-profile")

        for (elem in root.getChildren("ach")) {
            val id = elem.getAttributeValue("id")
            val difficultyName = elem.getAttributeValue("diff")

            unlockedAchievements[id] = AchievementUnlockInfo(
                Difficulty.valueOf(difficultyName)
            )
        }

        // (an older profile has none)
        for (elem in root.getChildren("achProgress")) {
            val id = elem.getAttributeValue("id") ?: continue
            val value = elem.getAttributeValue("value")?.toIntOrNull() ?: continue
            achievementProgress[id] = value
        }

        for (elem in root.getChildren("ship")) {
            val id = elem.getAttributeValue("unlockId")
            val difficultyName = elem.getAttributeValue("diff")

            unlockedShips[id] = AchievementUnlockInfo(
                Difficulty.valueOf(difficultyName)
            )
        }

        for (elem in root.getChildren("hotkey")) {
            val action: String = elem.getAttributeValue("action")
            val key: String? = elem.getAttributeValue("key")
            keybinds[action] = key
        }

        val volumes = root.getChild("soundVolume")
        if (volumes != null) {
            soundVolume = volumes.getAttributeValue("sfx").toFloat()
            musicVolume = volumes.getAttributeValue("music").toFloat()
        }

        val display = root.getChild("display")
        val displayWidth = display?.getAttributeValue("windowWidth")?.toIntOrNull()
        val displayHeight = display?.getAttributeValue("windowHeight")?.toIntOrNull()
        if (displayWidth != null && displayHeight != null) {
            displayWindowSize = displayWidth to displayHeight
        }

        // (an older profile has none)
        val map = root.getChild("map")
        mapShowAllConnections = map?.getAttributeValue("showAllConnections")?.toBoolean() ?: false
        mapShowFleetForecast = map?.getAttributeValue("showFleetForecast")?.toBoolean() ?: false

        language = root.getChild("language")?.getAttributeValue("code")

        topGames = root.getChild("topGames")?.getChildren("game")?.mapNotNull { GameRecord.loadFromXML(it) }
            ?.sortedByDescending { it.score }?.take(GameRecord.TOP_GAMES) ?: emptyList()
    }

    class AchievementUnlockInfo(val difficulty: Difficulty)

    companion object {
        @JvmField
        val PROFILE_NAME = "save-profile.xml"

        // How every achievement shows in debug mode (see getAchievement)
        private val DEBUG_UNLOCK = AchievementUnlockInfo(Difficulty.NORMAL)

        // Make it a bit more explicit what this is doing
        @JvmStatic
        fun createBlank(): SaveProfile {
            val profile = SaveProfile()

            // Immediately save the empty profile, so there's at least something there.
            profile.dirty = true

            return profile
        }

        /** A profile from a document [save] wrote (for the tests). */
        @JvmStatic
        fun fromDocument(doc: Document): SaveProfile = SaveProfile().also { it.load(doc) }

        @JvmStatic
        fun load(path: Path): SaveProfile? {
            val profile = SaveProfile()

            val doc: Document = try {
                Files.newBufferedReader(path).use { reader ->
                    val builder = SAXBuilder()
                    builder.setExpandEntities(true)
                    builder.build(reader)
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
                return null
            } catch (ex: JDOMException) {
                ex.printStackTrace()
                return null
            }

            profile.load(doc)
            return profile
        }
    }
}
