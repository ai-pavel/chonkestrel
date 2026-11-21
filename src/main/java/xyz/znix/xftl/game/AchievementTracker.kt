package xyz.znix.xftl.game

import org.jdom2.Element
import xyz.znix.xftl.Constants
import xyz.znix.xftl.Ship
import xyz.znix.xftl.XftlDebug
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.AbstractCrewDamage
import xyz.znix.xftl.crew.CombatDamage
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.SuffocationDamage
import xyz.znix.xftl.f
import xyz.znix.xftl.game.AchievementGoal.Kind
import xyz.znix.xftl.game.AchievementGoal.Progress
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.sector.Sector
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import xyz.znix.xftl.weapons.Damage

/**
 * Tracks this game's progress towards the player ship's achievements (the
 * ones with an XFTL goal, see [AchievementGoal]: the mod's six for each of
 * its ships), and unlocks them in the profile as their goals are met, with
 * a short notice at the top of the screen. See doc/game/achievements.md.
 *
 * The game tells it what happens through small hooks (an enemy ship
 * beaten, an enemy crewmember dying, a missile fired and so on), and it
 * keeps a count for each kind of goal, saved with the game. Everything's
 * counted whatever the ship, but only the ship's own achievements are
 * checked. A fight's own state (the ships in it, the hull at its start,
 * the weapons fired) isn't saved: after loading a game in the middle of a
 * fight, it starts again from there.
 *
 * Nothing goes in the profile in the tests (with no main game), for the
 * screenshots, the videos, the automatic playtest, or in debug mode, as
 * with the statistics (see InGameState.recordGame); the debug console's
 * ach-set does it by hand.
 */
class AchievementTracker(private val game: InGameState) {
    // The counts and bests for each kind of goal (see AchievementGoal.Progress)
    private val values = HashMap<Kind, Int>()

    // The achievements unlocked in this game, so each is only announced once
    private val completed = HashSet<String>()

    /** Whether the player ship's ever had an oxygen system in this game. */
    var hadOxygen: Boolean = false
        private set

    /** Whether the player ship's ever had a hacking or mind control system in this game. */
    var hadMindTricks: Boolean = false
        private set

    /** Whether the player ship's ever had a shields system in this game. */
    var hadShields: Boolean = false
        private set

    // The player ship's achievements, found once the player ship's there
    // (or set by the tests)
    private var shipAchievements: List<Achievement>? = null

    /**
     * The profile achievements are unlocked in, for the tests, instead of
     * the main game's.
     */
    var profileOverride: SaveProfile? = null

    /** The player ship's achievements that XFTL tracks, in order (none in the main menu, or the tests). */
    val achievements: List<Achievement>
        get() {
            shipAchievements?.let { return it }
            val player = game.player ?: return emptyList()
            val table: Achievement.AchievementTable? = game.content.achievements
            val found = table?.trackedFor(player.type.name) ?: emptyList()
            shipAchievements = found
            return found
        }

    /** Track [list] in place of the ship's own achievements, for the tests. */
    fun useAchievementsForTests(list: List<Achievement>) {
        shipAchievements = list
    }

    /**
     * The profile achievements are unlocked and kept in, or null if this
     * game doesn't count (see the class's description).
     */
    private val profile: SaveProfile?
        get() {
            profileOverride?.let { return it }
            if (game.isRunningAutomatedTest || game.isMenuScene || XftlDebug.enabled ||
                XftlDebug.recording || XftlDebug.autoplay
            )
                return null
            return game.mainGame.profile
        }

    /** This game's count or best for [kind] (see [AchievementGoal.Progress]). */
    fun value(kind: Kind): Int = values[kind] ?: 0

    /** This game's progress towards [ach], or null if it has no number to show. */
    fun progress(ach: Achievement): Int? {
        val goal = ach.goal ?: return null
        if (!goal.kind.isCounted)
            return null
        return value(goal.kind)
    }

    /** Whether [ach] has been unlocked in this game. */
    fun isCompleted(ach: Achievement): Boolean = ach.id in completed

    /** Add [amount] to the count for [kind] (a [Progress.COUNT] one). */
    fun add(kind: Kind, amount: Int = 1) {
        if (amount <= 0)
            return
        values[kind] = value(kind) + amount
        check(kind)
    }

    /** Keep [value] as [kind]'s best (a [Progress.BEST] one), if it's more than before. */
    fun best(kind: Kind, value: Int) {
        if (value <= value(kind))
            return
        values[kind] = value
        check(kind)
    }

    /**
     * Something's happened that [kind]'s goal (a [Progress.EVENT] one) is
     * about, with [value] (such as the crew alive at the win): the ship's
     * achievements of that kind are unlocked if it meets them.
     */
    fun event(kind: Kind, value: Int = 1) {
        for (ach in achievements) {
            val goal = ach.goal ?: continue
            if (goal.kind == kind && goal.isMetBy(value))
                unlock(ach)
        }
    }

    /**
     * Set this game's count or best for [kind] (from the debug console),
     * unlocking anything it reaches.
     */
    fun setValue(kind: Kind, value: Int) {
        values[kind] = value
        check(kind)
    }

    /** Forget that [ach] was unlocked in this game (from the debug console, after deleting it). */
    fun forget(ach: Achievement) {
        completed.remove(ach.id)
    }

    // Unlock the ship's achievements of [kind] its count or best now reaches
    private fun check(kind: Kind) {
        for (ach in achievements) {
            val goal = ach.goal ?: continue
            if (goal.kind == kind && goal.isMetBy(value(kind)))
                unlock(ach)
        }
    }

    private fun unlock(ach: Achievement) {
        if (!completed.add(ach.id))
            return
        val profile = profile
        if (profile != null) {
            profile.unlockAchievement(ach, game.difficulty)
            recordBest(profile)
        }
        // (announced even when it isn't kept, so it can be seen working)
        if (!game.isRunningAutomatedTest && !game.isMenuScene)
            toasts.addLast(ach)
    }

    /**
     * Keep this game's progress towards the ship's achievements as their
     * best in the profile, where it's more. This is done now and then (as
     * the ship jumps, and so on) rather than every time it changes, so the
     * profile isn't saved over and over.
     */
    fun recordBest() {
        profile?.let { recordBest(it) }
    }

    private fun recordBest(profile: SaveProfile) {
        for (ach in achievements) {
            val progress = progress(ach) ?: continue
            profile.recordAchievementProgress(ach, progress)
        }
    }

    // The fight going on, if there is one: the most ships fighting at
    // once, the ships beaten, the player's hull at its start and its
    // lowest, and the player's weapons fired (by their slot)
    private var inFight = false
    private var fightShips = 0
    private var fightBeaten = 0
    private var fightStartHull = 0
    private var fightLowestHull = 0
    private val fightWeapons = HashSet<Int>()

    /**
     * Called every update of the game: keeps an eye on the things that
     * aren't events (the drones out, the fires on the enemy ships, the
     * player's hull through a fight), and when fights start and end.
     */
    fun update() {
        val player = game.player ?: return
        if (game.isMenuScene)
            return

        if (player.oxygenSystems.isNotEmpty())
            hadOxygen = true
        if (player.hackingSystems.isNotEmpty() || player.mindControlSystems.isNotEmpty())
            hadMindTricks = true
        if (player.shieldSystems.isNotEmpty())
            hadShields = true

        val dronesOut = player.droneSystems.sumOf { system ->
            system.drones.sumOf { info -> info?.liveCopies?.count { it.isPowered } ?: 0 }
        }
        best(Kind.DRONES_OUT, dronesOut)

        val hostile = game.opponents.filter { it.hostile && !it.ship.isGone }
        if (!inFight && hostile.isNotEmpty())
            startFight(player, hostile.size)
        if (!inFight)
            return

        fightShips = maxOf(fightShips, hostile.size)
        fightLowestHull = minOf(fightLowestHull, player.health)
        for (opponent in hostile) {
            val fires = opponent.ship.rooms.sumOf { room -> room.fires.count { it != null } }
            best(Kind.ENEMY_FIRES, fires)
        }

        if (hostile.isEmpty())
            endFight(player)
    }

    private fun startFight(player: Ship, ships: Int) {
        inFight = true
        fightShips = ships
        fightBeaten = 0
        fightStartHull = player.health
        fightLowestHull = player.health
        fightWeapons.clear()
        best(Kind.SUPER_SHIELD, player.superShield)
    }

    // The fight's over: it's won if every ship in it was beaten (none
    // escaped, or was left behind by the player jumping away)
    private fun endFight(player: Ship) {
        inFight = false
        if (player.isDead || fightBeaten == 0 || fightBeaten < fightShips)
            return
        best(Kind.FLEET_FIGHT, fightBeaten)
        if (fightLowestHull >= fightStartHull)
            add(Kind.FLAWLESS_FIGHTS)
        // (the weapons still in the slots they fired from)
        best(Kind.ALL_WEAPONS, fightWeapons.count { player.hardpoints.getOrNull(it)?.weapon != null })
    }

    /**
     * An enemy ship that was fighting the player has been beaten: [destroyed],
     * or its crew killed. Called before the player's boarders come off it.
     */
    fun enemyBeaten(ship: Ship, destroyed: Boolean) {
        val player = game.player ?: return
        fightBeaten++

        if (destroyed && ship.lastHullHitByDrone)
            add(Kind.DRONE_KILLS)
        if (destroyed && ship.lastHullHitByArtillery) {
            add(Kind.ARTILLERY_KILLS)
            if (isBoss(ship))
                event(Kind.BOSS_ARTILLERY)
        }
        if (destroyed && player.isCloakActive) {
            add(Kind.CLOAKED_KILLS)
            if (isBoss(ship))
                event(Kind.BOSS_CLOAKED)
        }
        if (!destroyed)
            add(Kind.CREW_KILLS)
        if (ship.crew.any { it is LivingCrew && it.ownerShip == player })
            add(Kind.BOARDED_WINS)

        // A boss: how the player's hull held up in the fight
        if (isBoss(ship) && inFight) {
            fightLowestHull = minOf(fightLowestHull, player.health)
            event(Kind.BOSS_HULL, fightLowestHull * 100 / player.maxHealth.coerceAtLeast(1))
            event(Kind.BOSS_UNHARMED, if (fightLowestHull >= fightStartHull) 1 else 0)
        }
    }

    /**
     * All of [ship]'s crew have been killed. For a boss, that's a win of its
     * own, even if it then fights on, automated.
     */
    fun enemyCrewKilled(ship: Ship) {
        if (isBoss(ship))
            event(Kind.BOSS_CREW)
    }

    private fun isBoss(ship: Ship): Boolean = ship.colossal != null || ship.boss != null

    /** One of the size 3 bosses has been beaten (see ColossalBoss). */
    fun colossalBeaten(kind: ColossalBoss.Kind) {
        if (kind == ColossalBoss.Kind.VIGILANCE)
            event(Kind.BLOCKADE)
    }

    /** The game's over, with [outcome]: the win's achievements, and the best progress kept. */
    fun gameOver(outcome: GameOverWindow.Outcome) {
        if (outcome == GameOverWindow.Outcome.WIN) {
            event(Kind.WIN)
            if (game.difficulty == Difficulty.HARD)
                event(Kind.WIN_HARD)
            event(Kind.CREW_AT_WIN, livingCrew())
            event(Kind.WIN_NO_LOSSES, game.stats.crewLost)
            if (!hadOxygen && game.player?.oxygenSystems?.isEmpty() == true)
                event(Kind.WIN_NO_OXYGEN)
            val player = game.player
            if (!hadMindTricks && player != null && player.hackingSystems.isEmpty() && player.mindControlSystems.isEmpty())
                event(Kind.WIN_NO_MIND_TRICKS)
            val crew = game.playerCrew.filter { it.health > 0f }
            if (crew.isNotEmpty() && crew.all { it.info.race.name == HUMAN })
                event(Kind.WIN_ALL_HUMAN)
        }
        recordBest()
    }

    /**
     * [ship]'s hull has been hit for [hullDamage] by [damage]: the
     * player's artillery beams' damage to the enemy ships.
     */
    fun hullHit(ship: Ship, damage: Damage, hullDamage: Int) {
        if (damage.fromArtillery && !ship.isPlayerShip)
            add(Kind.ARTILLERY_DAMAGE, hullDamage)
    }

    /** The player's accepted the surrender of [ships] enemy ships. */
    fun surrendersAccepted(ships: Int) {
        add(Kind.SURRENDERS, ships)
    }

    // The player's crew alive, wherever they are, not counting those
    // waiting to be cloned
    private fun livingCrew(): Int {
        val queue = game.player?.clonebay?.queue ?: emptyList<LivingCrew>()
        return game.playerCrew.count { it.health > 0f && it !in queue }
    }

    /** The player ship's entering [sector]. */
    fun sectorEntered(sector: Sector) {
        val type = sector.type
        if (type.nebula != null || type.name.contains("NEBULA"))
            add(Kind.NEBULA_SECTORS)
        // (counting from 1, as the game shows them)
        if (!hadShields && game.player?.shieldSystems?.isNotEmpty() != true)
            best(Kind.NO_SHIELDS_SECTOR, sector.sectorNumber + 1)
    }

    /** An enemy shot has missed the player. */
    fun playerDodged() {
        add(Kind.DODGES)
        if (game.player?.isCloakActive == true)
            add(Kind.CLOAK_MISSES)
    }

    // When the player's last cloak ended, in seconds of play (see
    // GameStats.playedSeconds), or null
    private var lastCloakEnd: Float? = null

    /** One of the player's cloaks has ended. */
    fun cloakEnded() {
        lastCloakEnd = game.stats.playedSeconds
    }

    /** The player's cloaked: one cloak straight after another counts (see [CLOAK_CHAIN_TIME]). */
    fun cloakStarted() {
        val ended = lastCloakEnd ?: return
        if (game.stats.playedSeconds - ended <= CLOAK_CHAIN_TIME)
            add(Kind.CLOAK_CHAINS)
        lastCloakEnd = null
    }

    /** [crew] has just been killed by [damage]. */
    fun crewDied(crew: AbstractCrew, damage: AbstractCrewDamage) {
        val player = game.player ?: return
        // Only the enemy's own crew count, not drones or anyone of the player's
        if (crew !is LivingCrew || crew.ownerShip == null || crew.ownerShip == player)
            return

        if (damage is SuffocationDamage)
            add(Kind.SUFFOCATED)

        // Killed aboard an enemy ship by the player's side: their boarders
        // (or a boarding drone, or someone mind controlled), who are the
        // intruders there
        val attacker = damage as? CombatDamage ?: return
        val shipThere = attacker.attacker.room.ship
        if (shipThere != player && attacker.attacker.mode == AbstractCrew.SlotType.INTRUDER)
            add(Kind.BOARDER_KILLS)
    }

    /** One of the player's weapons, [weapon], has fired. */
    fun weaponFired(weapon: AbstractWeaponInstance) {
        if (weapon.type.missilesUsed > 0)
            add(Kind.MISSILES, weapon.type.missilesUsed)
        val slot = weapon.ship.hardpoints.indexOfFirst { it.weapon === weapon }
        if (slot >= 0 && weapon.ship.isCloakActive)
            add(Kind.CLOAKED_VOLLEYS)
        if (inFight && slot >= 0)
            fightWeapons += slot
    }

    // The achievements unlocked, waiting to be announced, and how long the
    // first's been shown, in seconds of real time
    private val toasts = ArrayDeque<Achievement>()
    private var toastTime = 0f

    /**
     * Draw the notice of the latest achievement unlocked, at the top of the
     * screen, [screenWidth] wide: its icon, and its name under vanilla's
     * "Achievement Unlocked!". Each stays for [TOAST_TIME] seconds, then
     * the next is shown.
     */
    fun drawToast(g: Graphics, screenWidth: Int) {
        val ach = toasts.firstOrNull() ?: return
        toastTime += game.renderingDeltaTime
        if (toastTime >= TOAST_TIME) {
            toasts.removeFirst()
            toastTime = 0f
            return
        }

        val x = (screenWidth - TOAST_WIDTH) / 2
        val y = TOAST_TOP
        game.windowRenderer.render(x, y, TOAST_WIDTH, TOAST_HEIGHT)
        val icon = game.getImg(ach.img)
        icon.draw(x + 16f, y + (TOAST_HEIGHT - ICON_SIZE) / 2f, ICON_SIZE.f, ICON_SIZE.f)
        val textX = x + 16f + ICON_SIZE + 14
        game.getFont("JustinFont12Bold").drawString(textX, y + 36f, game.translator["ach_unlocked"], Constants.UI_BUTTON_HOVER)
        game.getFont("HL2", 2f).drawString(textX, y + 66f, game.translator[ach.name], Constants.SECTOR_CUTOUT_TEXT)
    }

    fun saveToXML(parent: Element) {
        val elem = Element("achievements")
        if (hadOxygen)
            SaveUtil.addAttrBool(elem, "hadOxygen", true)
        if (hadMindTricks)
            SaveUtil.addAttrBool(elem, "hadMindTricks", true)
        if (hadShields)
            SaveUtil.addAttrBool(elem, "hadShields", true)
        for ((kind, value) in values) {
            val valueElem = Element("progress")
            SaveUtil.addAttr(valueElem, "kind", kind.xmlName)
            SaveUtil.addAttrInt(valueElem, "value", value)
            elem.addContent(valueElem)
        }
        for (id in completed) {
            val doneElem = Element("unlocked")
            SaveUtil.addAttr(doneElem, "id", id)
            elem.addContent(doneElem)
        }
        parent.addContent(elem)
    }

    /** Load what [saveToXML] saved in [parent]; an older save has nothing, and starts from nothing. */
    fun loadFromXML(parent: Element) {
        values.clear()
        completed.clear()
        val elem = parent.getChild("achievements") ?: return
        hadOxygen = elem.getAttributeValue("hadOxygen")?.toBoolean() ?: false
        hadMindTricks = elem.getAttributeValue("hadMindTricks")?.toBoolean() ?: false
        hadShields = elem.getAttributeValue("hadShields")?.toBoolean() ?: false
        for (valueElem in elem.getChildren("progress")) {
            val kind = valueElem.getAttributeValue("kind")?.let { Kind.byXmlName(it) } ?: continue
            values[kind] = valueElem.getAttributeValue("value")?.toIntOrNull() ?: continue
        }
        for (doneElem in elem.getChildren("unlocked"))
            doneElem.getAttributeValue("id")?.let { completed += it }
    }

    companion object {
        // The human race's blueprint name, for winning with only humans
        private const val HUMAN = "human"

        /** How soon after a cloak ends the next must start, in seconds, to count as straight after it. */
        const val CLOAK_CHAIN_TIME = 3f

        // The unlock notice: how long each is shown, and its box
        const val TOAST_TIME = 5f
        private const val TOAST_WIDTH = 460
        private const val TOAST_HEIGHT = 96
        private const val TOAST_TOP = 110
        private const val ICON_SIZE = 64
    }
}
