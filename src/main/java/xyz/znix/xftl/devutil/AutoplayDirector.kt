package xyz.znix.xftl.devutil

import xyz.znix.xftl.Ship
import xyz.znix.xftl.XftlDebug
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewAnaerobic
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.game.ContinueSave
import xyz.znix.xftl.game.DialogueWindow
import xyz.znix.xftl.game.GameOverWindow
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.game.Resource
import xyz.znix.xftl.game.StoreData
import xyz.znix.xftl.game.StoreProfile
import xyz.znix.xftl.layout.Door
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.sector.SectorType
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.EventManager
import xyz.znix.xftl.sector.Sector
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.LWJGLGameContainer
import xyz.znix.xftl.systems.Clonebay
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.systems.Engines
import xyz.znix.xftl.systems.MainSystem
import xyz.znix.xftl.systems.Medbay
import xyz.znix.xftl.systems.Oxygen
import xyz.znix.xftl.systems.Piloting
import xyz.znix.xftl.systems.Shields
import xyz.znix.xftl.systems.Weapons
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * The automatic playtest: it plays the game by itself, faster than real
 * time, to find crashes and see how hard the game is. Turn it on with
 * XFTL_AUTOPLAY (see [XftlDebug]); with XFTL_DEBUG too, the player's ship
 * can beat anything. Its strategy is written up in doc/tools/playtest.md.
 *
 * In short: in every event it picks the first blue option, or else the
 * first that doesn't take on crew or open a store. It explores each sector
 * (see exploreGoal) on its way to the exit beacon, steering round the
 * hazards it can see (see seemsDangerous), then picks the next sector by
 * how risky it is for its ship (see pickSector), until it reaches the last
 * stand (or its time or sector limit), or a game over (its ship destroyed,
 * or all its crew dead). It fights every hostile ship with
 * [VideoDirector.BattleDriver]'s player tactics: focused volleys, cloaking,
 * drones, hacking, mind control. At stores (see shop) it sells its cargo,
 * buys Long-Range Scanners, repairs, missiles, weapons, crew, drones and a
 * few augments, then upgrades. Out of a fight it keeps its systems powered
 * (see powerUp), and waits at a safe beacon for repairs and for its crew to
 * heal. It vents rooms on fire or with boarders (see ventRooms), and the AI
 * runs its crew. With XFTL_RANDOMIZE, it gets new random weapons for each
 * fight (see Ship.rerollDebugWeapons).
 *
 * Every exception thrown while the game's updating is counted, and a report
 * (where it went, and each different exception with its stack trace) is
 * written into the output directory when it stops, then the game quits.
 */
class AutoplayDirector(
    private val main: MainGame,
    private val outputDir: File,
    private val sectorLimit: Int,
    private val timeLimitMinutes: Float,
) {
    private val random = Random.Default
    private val startNanos = System.nanoTime()
    private var finished = false

    // Game time, in seconds, as the battle driver wants it
    private var time = 0f
    private var lastFrameNanos = System.nanoTime()

    private var driver: VideoDirector.BattleDriver? = null
    private var driverBeacon: Beacon? = null
    private var arrivedAt = 0f
    private var nextWindowAction = 0f

    // What happened, for the report
    private var sectors = 1
    private var beacons = 0
    private var fights = 0
    private var events = 0
    private val errors = LinkedHashMap<String, Pair<Int, String>>()

    // For the summary: the sectors it went through, the enemy ships it
    // fought and destroyed (or killed the crew of), the events it saw, its
    // scrap, and its blue options and quests
    private val sectorNames = ArrayList<String>()
    private val shipsFought = HashMap<String, Int>()
    private val shipsKilled = HashMap<String, Int>()
    private var shipsLeft = 0
    private val eventsSeen = HashMap<String, Int>()
    // The events from the vanilla game and the new ones (from the mods), by
    // the name of the event, event list or ship they're part of
    private val vanillaEventsSeen = HashMap<String, Int>()
    private val newEventsSeen = HashMap<String, Int>()
    private var vanillaEventNames: Set<String>? = null
    private var lastSectorCounted: Sector? = null
    private var lastWaitReason: String? = null
    // How many times it's waited for fuel, out of it (see jumpOn)
    private var outOfFuelWaits = 0
    // The sector it last bought a crew member in (see buyGear)
    private var crewBoughtIn: Sector? = null
    // The doors it opened to vent rooms (see ventRooms)
    private val ventedDoors = HashSet<Door>()

    // How many times it's started venting (see ventRooms)
    private var ventings = 0
    private var blueChoices = 0
    private val questsStarted = ArrayList<String>()
    private var questBeacons = 0
    private var storesShopped = 0
    private var scrapEarned = 0
    private var scrapSpent = 0
    private var lastScrap: Int? = null
    private var lastPlayerWeapons: String? = null
    private val fightingShips = LinkedHashSet<xyz.znix.xftl.Ship>()
    private val log = ArrayList<String>()

    // For the summary: the enemies' weapons, by the shape of ship (how many,
    // and how many double and triple ones), and the stores' contents, by
    // the sort of sector they're in (see StoreProfile and doc/game/weapon-sizes.md)
    private class WeaponCount(var ships: Int = 0, var weapons: Int = 0, var doubles: Int = 0, var triples: Int = 0,
                              var shipsWithCombined: Int = 0)
    private val enemyWeapons = LinkedHashMap<String, WeaponCount>()
    private val storeWeapons = LinkedHashMap<String, WeaponCount>()
    private val storesBySector = LinkedHashMap<String, Int>()
    private val shipsCounted: MutableSet<xyz.znix.xftl.Ship> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())

    // A ship's weapons, for the log: "Burst Laser II (x2), Artemis"
    private fun weaponsOf(ship: xyz.znix.xftl.Ship): String =
        ship.hardpoints.mapNotNull { it.weapon?.type }.joinToString { weaponName(it) }

    private fun weaponName(weapon: AbstractWeaponBlueprint): String {
        val base = weapon.combinedOf.firstOrNull() ?: weapon.name
        return if (weapon.isCombined) "$base (x${weapon.combinedCount})" else base
    }

    // Count a ship's weapons into counts, under key
    private fun countWeapons(counts: LinkedHashMap<String, WeaponCount>, key: String, weapons: List<AbstractWeaponBlueprint>) {
        val count = counts.getOrPut(key) { WeaponCount() }
        count.ships++
        count.weapons += weapons.size
        count.doubles += weapons.count { it.combinedCount == 2 }
        count.triples += weapons.count { it.combinedCount >= 3 }
        if (weapons.any { it.isCombined })
            count.shipsWithCombined++
    }

    fun updateError(ex: Exception) {
        val trace = StringWriter().also { ex.printStackTrace(PrintWriter(it)) }.toString()
        // Exceptions from the same place are the same bug
        val key = ex.javaClass.name + " @ " + (ex.stackTrace.firstOrNull()?.toString() ?: "?")
        val (count, _) = errors[key] ?: Pair(0, trace)
        errors[key] = Pair(count + 1, trace)
        if (count == 0)
            note("Exception: $key")
    }

    // When the current fight started, and whether its screenshot's taken
    private var fightStartedAt = 0f

    // Its hull as the fight started, to tell how fast it's losing (see step)
    private var fightStartHull = 0

    // Whether this fight's flight's been noted, and how many fights it fled
    private var notedFleeing = false
    private var fleeingFights = 0
    private var fightShot = true
    private var shots = 0
    private var shopped = false
    private val storesVisited = HashSet<Beacon>()
    private var pendingEventShot = true

    fun afterRender(width: Int, height: Int) {
        if (finished)
            return
        val game = main.currentState as? InGameState ?: return
        time += (FRAME * UPDATES_PER_FRAME)

        // A screenshot of each fight, a moment after it starts, and of each
        // event, to look for things overlapping and the ships' artefacts
        val window = game.shipUI.openWindow
        val shotName = when {
            window is DialogueWindow && pendingEventShot -> "event"
            !fightShot && time - fightStartedAt >= FIGHT_SHOT_DELAY -> "fight"
            else -> null
        }
        if (shotName != null) {
            if (shotName == "event") pendingEventShot = false else fightShot = true
            shots++
            val names = game.getOpponentShips(game.player).joinToString("-") { it.type.name.lowercase() }
            val file = File(outputDir, "shots/%04d-sector%02d-%s%s.jpg".format(shots, sectors, shotName,
                if (shotName == "fight") "-$names" else ""))
            try {
                ScreenshotDirector.capture(width, height, file)
            } catch (ex: Exception) {
                updateError(ex)
            }
        }

        try {
            step(game)
        } catch (ex: Exception) {
            updateError(ex)
        }

        val minutes = (System.nanoTime() - startNanos) / 60e9f
        if (minutes >= timeLimitMinutes) {
            note("Reached the time limit of $timeLimitMinutes minutes")
            finish()
        }
    }

    private fun step(game: InGameState) {
        val player = game.player
        val ui = game.shipUI

        // The player's weapons, at the start and the end
        if (lastPlayerWeapons == null)
            note("Player's weapons: ${weaponsOf(player)}")
        lastPlayerWeapons = weaponsOf(player)

        status = "Playtest: sector $sectors (${game.translator[game.currentBeacon.sector.type.shortTextId]}), " +
                "$beacons beacons"

        // How many of each sector's beacons have the new events, to compare
        // with the ones it went to
        val currentSector = game.currentBeacon.sector
        if (currentSector !== lastSectorCounted) {
            lastSectorCounted = currentSector
            val vanilla = vanillaEventNames ?: readVanillaEventNames(game).also { vanillaEventNames = it }
            val new = currentSector.beacons.filter { eventRoot(it.event.deserialisationId) !in vanilla }
            val stores = currentSector.beacons.count { it.hasStore }
            val profile = StoreProfile.of(currentSector.info).name.lowercase()
            storesBySector["$profile x${storesBySector.size + 1}"] = stores
            note("Sector map: $stores stores ($profile sector)")
            note("Sector map: ${new.size} of ${currentSector.beacons.size} beacons have new events" +
                    new.joinToString(prefix = " (", postfix = ")") { eventRoot(it.event.deserialisationId) }
                        .takeIf { new.isNotEmpty() }.orEmpty())
        }

        // With more crew than the ship has room for (from an event), the
        // game makes the player dismiss one: dismiss the newest
        while (game.playerHasTooManyCrew()) {
            val crew = game.playerCrew.last()
            player.clonebay?.queue?.remove(crew)
            crew.removeFromShip()
            game.updatePlayerCrew()
            note("Dismissed a crew member, as there were too many")
            ui.debugCloseWindow()
        }

        // It never pauses: if it's been paused (by the pause key, say), go on
        if (game.isPauseToggled) {
            game.isPauseToggled = false
            note("Unpaused the game")
        }

        // Scrap earned and spent
        lastScrap?.let { last ->
            val delta = player.scrap - last
            if (delta > 0) scrapEarned += delta else scrapSpent -= delta
        }
        lastScrap = player.scrap

        if (sectorNames.isEmpty())
            sectorNames += game.translator[game.currentBeacon.sector.type.shortTextId]

        // A new beacon
        if (game.currentBeacon != driverBeacon) {
            driverBeacon = game.currentBeacon
            driver = null
            arrivedAt = time
            beacons++
            shopped = false
            val here = game.currentBeacon
            val what = listOfNotNull(
                "exit".takeIf { here.isExit }, "store".takeIf { here.hasStore }, "quest".takeIf { here.hasQuest },
                here.getEnvironment(game).parts.joinToString("+") { it.type.name }.takeIf { it != "NORMAL" }
            )
            if (here.hasQuest) questBeacons++
            val crew = player.crew.count { it is LivingCrew && it.ownerShip == player }
            note("Beacon $beacons" + (if (what.isEmpty()) "" else " (${what.joinToString()})") +
                    ", hull ${player.health}/${player.maxHealth}, crew $crew, fuel ${player.fuelCount}, " +
                    "missiles ${player.missilesCount}, drone parts ${player.dronesCount}, scrap ${player.scrap}")
        }

        // Enemy ships that are gone since last time: destroyed (or their
        // crew killed), or they got away
        val opponents = game.getOpponentShips(player)
        for (ship in fightingShips.toList()) {
            if (ship in opponents)
                continue
            fightingShips -= ship
            val name = ship.type.name
            if (ship.isDead || ship.crew.none { it is LivingCrew && it.ownerShip == ship && it.health > 0f }) {
                shipsKilled[name] = (shipsKilled[name] ?: 0) + 1
                note("Destroyed $name")
            } else {
                shipsLeft++
                note("$name is gone (it got away, or the fight ended)")
            }
        }
        for (ship in opponents) {
            if (fightingShips.add(ship))
                shipsFought[ship.type.name] = (shipsFought[ship.type.name] ?: 0) + 1
        }

        // Shop once at each store, when nothing's attacking
        if (!shopped && game.getOpponentShips(player).isEmpty()) {
            game.currentBeacon.getStore(game)?.let { store ->
                shopped = true
                storesShopped++
                storesVisited += game.currentBeacon
                logStore(game, store)
                shop(game, store)
            }
        }

        // A game over ends the playtest
        if (ui.openWindow is GameOverWindow) {
            note("Game over: ${(ui.openWindow as GameOverWindow).outcome}")
            finish()
            return
        }

        // Answer the events, a little while after they come up, and close
        // any other window (stores, the ship screen...)
        val window = ui.openWindow
        if (window != null) {
            if (time < nextWindowAction)
                return
            nextWindowAction = time + WINDOW_DELAY
            // (the next one gets a screenshot before it's answered)
            if (window is DialogueWindow && pendingEventShot)
                return
            if (window is DialogueWindow) {
                events++
                val choice = window.autoChoose(cautious(player))
                if (choice == null) {
                    ui.debugCloseWindow()
                } else {
                    eventsSeen[choice.eventId] = (eventsSeen[choice.eventId] ?: 0) + 1
                    choice.serialId?.let { countEvent(game, it) }
                    if (choice.blue) blueChoices++
                    choice.quest?.let { questsStarted += it }
                    note("Event ${choice.eventId}: \"${choice.text.replace('\n', ' ').take(EVENT_TEXT_LENGTH)}\" " +
                            "-> \"${choice.option}\"" + (if (choice.blue) " [blue]" else "") +
                            (if (cautious(player)) " [cautious]" else "") +
                            (choice.quest?.let { " [quest $it]" } ?: ""))
                }
                pendingEventShot = true
            } else {
                ui.debugCloseWindow()
            }
            return
        }

        ventRooms(player)

        // Fight whatever's hostile
        val enemies = game.getOpponentShips(player)
        if (enemies.isNotEmpty()) {
            val driver = driver ?: VideoDirector.BattleDriver(game).apply {
                startBattle()
                dronesOn = true
                hackingOn = true
                mindControlOn = true
                playerTactics = true
                fights++
                fightStartedAt = time
                fightStartHull = player.health
                notedFleeing = false
                fightShot = false
                note("Fighting ${enemies.joinToString { it.type.name }}")
                // With XFTL_RANDOMIZE, new random weapons for each fight, to
                // try out more of them
                if (XftlDebug.randomize) {
                    player.rerollDebugWeapons()
                    note("  Player's weapons: ${weaponsOf(player)}")
                }
                // Each enemy's weapons, to see how many are double or triple
                // (see doc/game/weapon-sizes.md)
                for (enemy in enemies) {
                    if (!shipsCounted.add(enemy))
                        continue
                    note("  ${enemy.type.name} (${enemy.type.shape.name.lowercase()}, sector " +
                            "${game.currentBeacon.sector.difficultySectorNumber}/7): ${weaponsOf(enemy)}")
                    countWeapons(enemyWeapons, enemy.type.shape.name.lowercase(), enemy.hardpoints.mapNotNull { it.weapon?.type })
                }
            }.also { driver = it }
            // A fight that drags on (one neither side can win, say), or that
            // it's losing, ends with a jump away, once the FTL drive's charged
            // (or that's already cost it [RETREAT_LOSS] of its hull, from
            // however much it started with: a fight that bad won't end well)
            val losing = player.health < player.maxHealth * RETREAT_HULL || crewFailing(player) ||
                    fightStartHull - player.health >= player.maxHealth * RETREAT_LOSS || driver.fleeing
            if (driver.fleeing && !notedFleeing) {
                notedFleeing = true
                fleeingFights++
                note("Fleeing: ${driver.fleeReason}, with ${player.health}/${player.maxHealth} hull")
            }
            if ((time - fightStartedAt > MAX_FIGHT_TIME || losing) && player.isFtlReady &&
                !game.currentBeacon.sector.isLastStand
            ) {
                note(if (losing) "Retreating with ${player.health}/${player.maxHealth} hull, crew at ${crewHealthPercent(player)}%"
                else "Jumping away after ${MAX_FIGHT_TIME.toInt()}s of fighting")
                driver.restorePower()
                this.driver = null
                arrivedAt = time
                jumpOn(game)
                return
            }
            driver.update(time)
            return
        }
        // (the wait for repairs and so on starts when the fight's over, with
        // any power it took from the shields and so on put back)
        if (driver != null) {
            arrivedAt = time
            driver?.restorePower()
        }
        driver = null
        val boarded = player.crew.any { it is LivingCrew && it.mode == AbstractCrew.SlotType.INTRUDER && it.health > 0f }
        if (boarded || hurtCrew(player).isNotEmpty())
            powerMedbays(player)
        powerUp(player)

        // Then move on, once the ship's in order (see waitingFor)
        if (time - arrivedAt < BEACON_STAY)
            return
        val waitReason = waitingFor(game)
        if (waitReason != null && time - arrivedAt < MAX_BEACON_WAIT) {
            if (waitReason != lastWaitReason)
                note("Waiting: $waitReason")
            lastWaitReason = waitReason
            return
        }
        if (waitReason != null) {
            note("Moving on after ${MAX_BEACON_WAIT.toInt()}s, still $waitReason")
            if (waitReason == HEALING)
                note("  Still hurt: " + hurtCrew(player).joinToString { crew ->
                    "${crew.info.name} ${crew.health.toInt()}/${crew.maxHealth.toInt()} in room ${crew.room.id}" +
                            " (oxygen ${(crew.room.oxygen * 100).toInt()}%)" +
                            (if (crew.room.system is Medbay) " (medbay)" else "")
                } + "; medbays: " + player.medbaySystems.joinToString {
                    "room ${it.room?.id} power ${it.powerSelected}/${it.energyLevels}"
                })
        }
        lastWaitReason = null
        jumpOn(game)
    }

    /**
     * Jump on from this beacon: to the next sector from the exit, and
     * otherwise towards the next beacon to explore (see exploreGoal) or the
     * exit.
     */
    private fun jumpOn(game: InGameState) {
        val ui = game.shipUI
        val beacon = game.currentBeacon
        val sector = beacon.sector

        // Without the fuel for a jump, it waits with the distress beacon on,
        // as a player would, till someone brings some (or the fleet comes)
        if (game.player.fuelCount < game.player.fuelPerJump) {
            outOfFuelWaits++
            note("Out of fuel (${game.player.fuelCount}): waiting with the distress beacon on")
            ui.autoWaitOutOfFuel()
            return
        }
        // (at the exit, it goes back for repairs first, if it needs them and
        // can get there and back, see repairGoal)
        val repairFromExit = if (beacon.isExit && !sector.isLastStand) repairGoal(game, beacon, beacon) else null
        if (repairFromExit != null) {
            val step = nextStepTowards(beacon, repairFromExit, avoid = { it.isOvertaken || seemsDangerous(game, it) })
                ?: nextStepTowards(beacon, repairFromExit)
            if (step != null) {
                ui.autoJump(step)
                return
            }
        }
        if (beacon.isExit) {
            val next = sector.info.nextSectors
            if (next.isEmpty() || sectors >= sectorLimit) {
                note("Done after $sectors sectors")
                finish()
                return
            }
            val info = pickSector(game, next)
            sectors++
            ui.autoEnterSector(info)
            val name = game.translator[info.type.shortTextId]
            sectorNames += name
            note("Sector $sectors: $name (${info.type.name})")
            if (game.currentBeacon.sector.isLastStand)
                note("Reached the last stand: on to the flagship")
            return
        }

        // Towards the exit, along the shortest way there, by way of each of
        // the sector's stores
        // (in the last stand, the flagship: there's no exit to go to)
        // In debug mode it can jump to any beacon, so it jumps straight to
        // the flagship's. The flagship only moves when the player jumps, so
        // after beating a stage, if it's still at the same beacon, it jumps
        // away and back to let the flagship move on to its next one.
        val flagship = sector.bosses.firstOrNull()?.beacon
        if (sector.isLastStand && flagship != null) {
            if (flagship != beacon) {
                note("Jumping to the flagship")
                ui.autoJump(flagship)
            } else {
                beacon.neighbours.firstOrNull()?.let { ui.autoJump(it) }
            }
            return
        }
        val exit = sector.beacons.firstOrNull { it.isExit } ?: return
        val goal = repairGoal(game, beacon, exit) ?: exploreGoal(game, beacon, exit) ?: exit
        val target = if (random.nextFloat() < WANDER_CHANCE) {
            beacon.neighbours.random(random)
        } else {
            nextStepTowards(beacon, goal, avoid = { it.isOvertaken || seemsDangerous(game, it) })
                ?: nextStepTowards(beacon, goal) ?: return
        }
        ui.autoJump(target)
    }

    /**
     * True if its ship's in no state to take on a fight an event offers (see
     * DialogueWindow.autoChoose): it's lost half its hull, its crew are
     * failing, or it hasn't the fuel to run.
     */
    private fun cautious(player: Ship): Boolean =
        player.health < player.maxHealth * RETREAT_HULL || crewFailing(player) || player.fuelCount < player.fuelPerJump

    /**
     * Its crew's health, all together, as a percentage of what it could be.
     */
    private fun crewHealthPercent(player: Ship): Int {
        val crew = player.crew.filterIsInstance<LivingCrew>().filter { it.ownerShip == player && it.health > 0f }
        val max = crew.sumOf { it.maxHealth.toDouble() }
        return if (max <= 0.0) 0 else (crew.sumOf { it.health.toDouble() } * 100 / max).toInt()
    }

    /**
     * True if its crew are losing the fight: their health, all together, is
     * under [RETREAT_CREW] of what it could be.
     */
    private fun crewFailing(player: Ship): Boolean = crewHealthPercent(player) < RETREAT_CREW * 100

    /**
     * The next sector, by how risky it is for the ship as it is now (ties
     * at random): while it's weak (fewer than two layers of shields, or
     * half its hull gone) a civilian sector, then a hostile one, once it's
     * stronger a hostile one first, and nebulas and hazardous sectors only
     * when it's strong (three layers of shields, and most of its hull).
     */
    private fun pickSector(game: InGameState, next: List<GameMap.SectorInfo>): GameMap.SectorInfo {
        val player = game.player
        val layers = player.shieldSystems.sumOf { it.energyLevels / 2 }
        val hull = player.health.toFloat() / player.maxHealth
        val weak = layers < 2 || hull < 0.5f
        val strong = layers >= 3 && hull >= 0.7f
        fun risk(info: GameMap.SectorInfo): Int = when {
            // (XFTL's nebula sectors, of one kind of nebula, are as bad)
            info.type.hazards != SectorType.HazardLevel.NORMAL || info.type.nebula != null -> if (strong) 2 else 4
            info.sectorClass == GameMap.SectorClass.NEBULA -> if (strong) 1 else 3
            info.sectorClass == GameMap.SectorClass.HOSTILE -> if (weak) 1 else 0
            else -> if (weak) 0 else 1
        }
        val best = next.minOf(::risk)
        return next.filter { risk(it) == best }.random(random)
    }

    /**
     * Vent rooms, as a player would: open the doors from each to the
     * nearest airlock, so the air goes, and the fire or boarders with it,
     * and keep its crew out of them, and the rooms the air goes through
     * (see FriendlyCrewAI.keepOut). Once there's no more need, the doors it
     * opened are closed again, and the crew go back. It vents:
     *
     * - a room on fire that none of its crew are in, or with a big fire (on
     *   half its cells, and at least [FIRE_VENT_CELLS]), which isn't worth
     *   them getting burnt for;
     * - a room with boarders that none of its crew are in, or all the rooms
     *   with boarders, while they're stronger than [BOARDERS_VENT] of its
     *   crew: but never the Lanius, who don't need the air.
     *
     * It never vents the medbays, nor a room with the shields, weapons,
     * engines, piloting or oxygen that its crew are in (they're needed
     * there), nor lets the air out through one, and vents at most
     * [MAX_VENTED_ROOMS] at once, the biggest fires and the most boarders
     * first.
     */
    private fun ventRooms(player: Ship) {
        fun ownCrewIn(room: Room) = room.crew.any {
            it is LivingCrew && it.ownerShip == player && it.mode == AbstractCrew.SlotType.CREW
        }
        fun boardersIn(room: Room) = room.crew.filter {
            it is LivingCrew && it.mode == AbstractCrew.SlotType.INTRUDER && it.health > 0f
        }
        fun strength(crew: List<AbstractCrew>) = crew.sumOf { (it.health * it.attackDamageMult).toDouble() }
        val boarders = player.rooms.flatMap(::boardersIn)
        val defenders = player.crew.filter {
            it is LivingCrew && it.ownerShip == player && it.mode == AbstractCrew.SlotType.CREW && it.health > 0f
        }
        val strongBoarders = boarders.isNotEmpty() &&
                strength(boarders.filter { it !is CrewAnaerobic }) >= strength(defenders) * BOARDERS_VENT
        val kept = player.crewAI.keepOut

        fun fires(room: Room) = room.fires.count { it != null }
        fun bigFire(room: Room) = fires(room) >= maxOf(FIRE_VENT_CELLS, (room.cellCount + 1) / 2)
        // (the rooms the crew have to man: the air isn't let out through
        // them, and they're only vented with none of the crew in them)
        fun needed(room: Room) = when (room.system) {
            is Medbay, is Shields, is Weapons, is Engines, is Piloting, is Oxygen -> true
            else -> false
        }
        fun needsVenting(room: Room): Boolean {
            if (room.system is Medbay)
                return false
            // (a room it's venting already counts as having no crew in, as
            // they're on their way out)
            val crewed = ownCrewIn(room) && room !in kept
            if (crewed && needed(room))
                return false
            if (fires(room) > 0 && (!crewed || bigFire(room)))
                return true
            val inRoom = boardersIn(room)
            return inRoom.isNotEmpty() && inRoom.none { it is CrewAnaerobic } && (!crewed || strongBoarders)
        }

        val wanted = HashSet<Door>()
        val keepOut = HashSet<Room>()
        val rooms = player.rooms.filter(::needsVenting)
            .sortedByDescending { fires(it) * 10 + boardersIn(it).size }
            .take(MAX_VENTED_ROOMS)
        for (room in rooms) {
            // The shortest way out, not through the rooms the crew are needed in
            val previous = HashMap<Room, Door>()
            val queue = ArrayDeque(listOf(room))
            val seen = hashSetOf(room)
            var airlock: Door? = null
            var last: Room? = null
            while (queue.isNotEmpty() && airlock == null) {
                val here = queue.removeFirst()
                for (door in here.doors) {
                    if (door.isAirlock) {
                        airlock = door
                        last = here
                        break
                    }
                    val next = door.other(here) ?: continue
                    if (next in seen || needed(next))
                        continue
                    seen += next
                    previous[next] = door
                    queue.addLast(next)
                }
            }
            if (airlock == null)
                continue
            wanted += airlock
            keepOut += room
            var step = last
            while (step != null && step != room) {
                keepOut += step
                val door = previous[step] ?: break
                wanted += door
                step = door.other(step)
            }
        }
        if (keepOut.isNotEmpty() && player.crewAI.keepOut.isEmpty()) {
            ventings++
            val why = rooms.joinToString { room ->
                (room.system?.blueprint?.name?.lowercase() ?: "room ${room.id}") +
                        (if (fires(room) > 0) " (fire)" else " (boarders)")
            }
            note("Venting $why${if (strongBoarders) ", boarders too strong to fight" else ""}")
        }
        player.crewAI.keepOut = keepOut

        for (door in wanted) {
            if (!door.open && door.isControllable) {
                door.open = true
                ventedDoors += door
            }
        }
        val close = ventedDoors.filter { it !in wanted && it.isControllable }
        for (door in close) {
            door.open = false
            ventedDoors -= door
        }
    }

    /**
     * Why it should stay at this beacon before jumping on, as a player
     * would, or null if it shouldn't: unless the beacon is dangerous,
     * boarders still aboard (from an event, or a ship that's gone), fires,
     * breaches, damaged systems or hurt crew (the AI puts them out and
     * repairs while it waits, and the hurt crew go to the medbay to heal,
     * see powerMedbays).
     */
    private fun waitingFor(game: InGameState): String? {
        val player = game.player
        // Not at a dangerous beacon, even for boarders: the hazards (such
        // as a radiation nebula) wear the crew down while they fight
        if (game.currentBeacon.isDangerous)
            return null
        val intruders = player.crew.count {
            it is LivingCrew && it.mode == AbstractCrew.SlotType.INTRUDER &&
                    it.health > 0f
        }
        if (intruders > 0)
            return "$intruders boarders aboard"
        if (player.rooms.any { room -> room.fires.any { it != null } })
            return "fires"
        if (player.rooms.any { room -> room.breaches.any { it != null } })
            return "breaches"
        if (player.systems.any { it.damaged })
            return "repairs"
        if (hurtCrew(player).isNotEmpty() && workingMedbays(player).isNotEmpty())
            return HEALING
        return null
    }

    // Its crew aboard that aren't healed (as far as the crew's AI heals
    // them when the ship's calm, see FriendlyCrewAI), and the medbays that
    // can heal them
    private fun hurtCrew(player: Ship): List<LivingCrew> = player.crew.filterIsInstance<LivingCrew>().filter {
        it.ownerShip == player && it.room.ship == player && it.mode == AbstractCrew.SlotType.CREW &&
                it.health > 0f && it.health < it.maxHealth * HEALED
    }

    private fun workingMedbays(player: Ship) = player.medbaySystems.filter { it.energyLevels > 0 && !it.damaged }

    /**
     * Out of a fight, power every system but the weapons and drones up, as
     * far as the reactor allows, the shields and engines first, as a
     * player would. The battle driver takes power from some of them for
     * the weapons, and puts it back after the fight, but not what the
     * reactor couldn't power then (such as in an ion storm, which halves
     * it).
     */
    private fun powerUp(player: Ship) {
        val systems = player.systems.filterIsInstance<MainSystem>()
            .filter { it !is Weapons && it !is Drones && !(it is Medbay && hurtCrew(player).isEmpty()) }
            .sortedBy {
                when (it) {
                    is Shields -> 0
                    is Engines -> 1
                    is Oxygen -> 2
                    else -> 3
                }
            }
        for (system in systems) {
            while (system.powerSelected < system.energyLevels) {
                val before = system.powerSelected
                system.increasePower()
                if (system.powerSelected == before)
                    break
            }
        }
    }

    /**
     * Out of a fight, with crew hurt or boarders aboard, power the medbays
     * up first (the Chonkestrel starts with its medbay off), taking the
     * power from the weapons, or else the drones, hacking, mind control,
     * teleporter or cloaking, if it has to (the battle driver powers the
     * weapons again in the next fight). The crew's AI sends the hurt to
     * them, and they stay till they're healed, when the ship's calm (see
     * FriendlyCrewAI).
     */
    private fun powerMedbays(player: Ship) {
        for (medbay in workingMedbays(player)) {
            while (medbay.powerSelected < medbay.energyLevels) {
                val before = medbay.powerSelected
                medbay.increasePower()
                if (medbay.powerSelected != before)
                    continue
                // Not enough power left in the reactor: turn a weapon off,
                // or else take a bar from a system it can do without for
                // now
                val slot = player.hardpoints.indices.firstOrNull { player.hardpoints[it].weapon?.isPowered == true }
                if (slot != null) {
                    val weapon = player.hardpoints[slot].weapon!!
                    player.weaponsFor(slot)?.setWeaponPower(weapon, false)
                    if (weapon.isPowered)
                        break
                    continue
                }
                val donor = (player.droneSystems + player.hackingSystems + player.mindControlSystems +
                        listOfNotNull(player.teleporter, player.cloaking)).firstOrNull { it.powerSelected > 0 }
                    ?: break
                val had = donor.powerSelected
                donor.decreasePower()
                if (donor.powerSelected == had)
                    break
            }
        }
    }

    /**
     * The next beacon to explore on the way to the exit, to see as much of
     * the sector as it can, or null to head for the exit: the nearest beacon
     * it hasn't been to (a store first, then one without a dangerous
     * environment), if it can still get there and on to the exit, with
     * [EXPLORE_MARGIN] jumps to spare, before the rebel fleet overtakes them.
     * With less than [EXPLORE_MIN_HULL] of its hull left, it only goes to
     * stores (to repair) on the way.
     */
    private fun exploreGoal(game: InGameState, from: Beacon, exit: Beacon): Beacon? {
        val sector = from.sector
        val player = game.player
        // With little fuel, it only goes to stores (for more) on the way,
        // and nowhere it couldn't then get on to the exit from
        val fuelJumps = player.fuelCount / player.fuelPerJump
        val lowFuel = fuelJumps < (jumpsBetween(from, exit) ?: 0) + EXPLORE_FUEL_MARGIN
        val storesOnly = player.health < player.maxHealth * EXPLORE_MIN_HULL || lowFuel
        val advance = sector.getFleetAdvanceFor(from, game.playerBlueprint.shipSize).coerceAtLeast(1)
        // How many jumps until the fleet overtakes a beacon (it moves about
        // the same distance with each jump)
        fun jumpsLeft(beacon: Beacon) = (beacon.pos.distTo(sector.dangerZoneCentre) - Sector.DANGER_ZONE_RADIUS) / advance
        val exitJumpsLeft = jumpsLeft(exit)

        return sector.beacons.asSequence()
            .filter { !it.visited && !it.isOvertaken && !it.isExit && it != from }
            .filter { !storesOnly || (it.hasStore && it !in storesVisited) }
            .mapNotNull { beacon ->
                val there = jumpsBetween(from, beacon) ?: return@mapNotNull null
                val onToExit = jumpsBetween(beacon, exit) ?: return@mapNotNull null
                if (there >= jumpsLeft(beacon) || there + onToExit + EXPLORE_MARGIN > exitJumpsLeft)
                    return@mapNotNull null
                if (there + onToExit > fuelJumps)
                    return@mapNotNull null
                Triple(beacon, there, onToExit)
            }
            .minWithOrNull(compareBy<Triple<Beacon, Int, Int>>(
                { if (it.first.hasStore && it.first !in storesVisited) 0 else 1 },
                { if (seemsDangerous(game, it.first)) 1 else 0 },
                { it.second },
                // Of the nearest, the one furthest from the exit, so it
                // sweeps the sector from the start to the exit
                { -it.third }
            ))?.first
    }

    /**
     * Where to go to get the hull mended: the nearest store (one it's been to
     * already too) it can get to and on to the exit before the fleet, while
     * it's lost more than [REPAIR_TRIP_HULL] of its hull and has the scrap to
     * mend at least [REPAIR_TRIP_POINTS] of it (or all that's missing). Null
     * if it doesn't need to, or can't.
     */
    private fun repairGoal(game: InGameState, from: Beacon, exit: Beacon): Beacon? {
        val sector = from.sector
        val player = game.player
        val missing = player.maxHealth - player.health
        if (player.health >= player.maxHealth * REPAIR_TRIP_HULL)
            return null
        if (player.scrap < minOf(missing, REPAIR_TRIP_POINTS) * REPAIR_PRICE_HIGHEST)
            return null
        val advance = sector.getFleetAdvanceFor(from, game.playerBlueprint.shipSize).coerceAtLeast(1)
        fun jumpsLeft(beacon: Beacon) = (beacon.pos.distTo(sector.dangerZoneCentre) - Sector.DANGER_ZONE_RADIUS) / advance
        val exitJumpsLeft = jumpsLeft(exit)
        val fuelJumps = player.fuelCount / player.fuelPerJump
        val goal = sector.beacons.asSequence()
            .filter { it.hasStore && it != from && !it.isOvertaken }
            .mapNotNull { beacon ->
                val there = jumpsBetween(from, beacon) ?: return@mapNotNull null
                val onToExit = jumpsBetween(beacon, exit) ?: return@mapNotNull null
                if (there >= jumpsLeft(beacon) || there + onToExit + EXPLORE_MARGIN > exitJumpsLeft)
                    return@mapNotNull null
                if (there + onToExit > fuelJumps)
                    return@mapNotNull null
                beacon to there
            }
            .minByOrNull { it.second }?.first
        if (goal != null && goal != lastRepairGoal) {
            note("Heading for a store to repair: ${player.health}/${player.maxHealth} hull, scrap ${player.scrap}")
            lastRepairGoal = goal
        }
        return goal
    }

    // The store it last set out for to repair, so it's only noted once
    private var lastRepairGoal: Beacon? = null

    /**
     * Whether a [beacon] has a hazard, as far as a player could tell from the
     * map: where it's been, a revealed map, and with Long-Range Scanners
     * (which it buys, see shop), the beacons next to it. Without them it
     * can't tell, and doesn't steer round them.
     */
    private fun seemsDangerous(game: InGameState, beacon: Beacon): Boolean {
        if (!beacon.isDangerous)
            return false
        return beacon.visited || beacon.sector.mapRevealed ||
                (game.player.hasAugment(AugmentBlueprint.LONG_RANGE_SCANNERS) && beacon in game.currentBeacon.neighbours)
    }

    /**
     * How many jumps it is from [from] to [to], or null if it can't get there.
     */
    private fun jumpsBetween(from: Beacon, to: Beacon): Int? {
        val distance = hashMapOf(from to 0)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val beacon = queue.removeFirst()
            if (beacon == to)
                return distance[beacon]
            for (next in beacon.neighbours) {
                if (next !in distance) {
                    distance[next] = distance.getValue(beacon) + 1
                    queue.addLast(next)
                }
            }
        }
        return null
    }

    /**
     * The beacon next to [from] on the shortest way to [to] (by jumps),
     * going through none of the beacons [avoid] picks out (but [to] itself).
     */
    private fun nextStepTowards(from: Beacon, to: Beacon, avoid: (Beacon) -> Boolean = { false }): Beacon? {
        val previous = HashMap<Beacon, Beacon>()
        val queue = ArrayDeque(listOf(from))
        val seen = hashSetOf(from)
        while (queue.isNotEmpty()) {
            val beacon = queue.removeFirst()
            if (beacon == to)
                break
            for (next in beacon.neighbours) {
                if (next != to && avoid(next))
                    continue
                if (seen.add(next)) {
                    previous[next] = beacon
                    queue.addLast(next)
                }
            }
        }
        var step = to
        while (previous[step] != from) {
            step = previous[step] ?: return null
        }
        return step
    }

    /**
     * Note what a store has, and what sort of sector it's in (see
     * StoreProfile and doc/game/stores.md), for the report.
     */
    private fun logStore(game: InGameState, store: StoreData) {
        val profile = StoreProfile.of(game.currentBeacon.sector.info).name.lowercase()
        val weapons = store.weapons.filterNotNull()
        note("Store ($profile sector): sections ${store.sections.joinToString { it.name.lowercase() }}; " +
                "weapons: ${weapons.joinToString { weaponName(it) }.ifEmpty { "none" }}; " +
                "drones ${store.drones.count { it != null }}, augments ${store.augments.count { it != null }}, " +
                "crew ${store.crew.filterNotNull().joinToString { c -> c.race.name + c.skills.values.joinToString("", "[", "]") { v -> if (v >= 1f) "2" else if (v >= 0.5f) "1" else "0" } }}; " +
                "fuel ${store.availableResources[Resource.FUEL]}, missiles ${store.availableResources[Resource.MISSILES]}, " +
                "drone parts ${store.availableResources[Resource.DRONES]}")
        countWeapons(storeWeapons, profile, weapons)
    }

    /**
     * Spend the scrap at a store, keeping a tenth of it (STORE_RESERVE),
     * but none at the last store it can get to in the sector, in the order
     * doc/tools/playtest.md gives: the cargo, the first augments, two thirds of the hull,
     * missiles (STORE_WANTS), weapons, crew and drones (see buyGear),
     * upgrades (see upgrade), augments (see buyAugments), then the rest of
     * the hull.
     */
    private fun shop(game: InGameState, store: StoreData) {
        val player = game.player
        val start = player.scrap
        // At the last store it can still get to in the sector, it spends
        // everything, as the scrap's no use till the next one
        val here = game.currentBeacon
        val lastStore = here.sector.beacons.none {
            it.hasStore && it != here && it !in storesVisited && !it.isOvertaken
        }
        val floor = if (lastStore) 0 else (start * STORE_RESERVE).toInt()
        fun canSpend(price: Int) = price > 0 && player.scrap - price >= floor

        // Fit what's in the cargo hold that it has the slots and power for,
        // as it would buy it (see buyGear), and sell the rest first, for half
        // its price
        val sold = ArrayList<String>()
        val fitted = ArrayList<String>()
        for ((index, item) in player.cargoBlueprints.withIndex()) {
            item ?: continue
            if (item is AbstractWeaponBlueprint && item.name !in Ship.RANDOMIZE_EXCLUDED &&
                item.combinedOf.none { it in Ship.RANDOMIZE_EXCLUDED }
            ) {
                val slot = weaponSlotFor(player, item)
                if (slot != null) {
                    player.hardpoints[slot].weapon = item.buildInstance(player)
                    player.cargoBlueprints[index] = null
                    fitted += item.name
                    continue
                }
            }
            if (item is DroneBlueprint) {
                val system = droneSystemFor(player, item)
                if (system != null) {
                    system.drones[system.drones.indexOf(null)] = Drones.DroneInfo(item, null)
                    player.cargoBlueprints[index] = null
                    fitted += item.name
                    continue
                }
            }
            // Keep a weapon or drone there's an empty slot for, but not yet
            // the power (see upgrade), to fit later
            val hasSlot = when (item) {
                is AbstractWeaponBlueprint -> item.name !in Ship.RANDOMIZE_EXCLUDED &&
                        item.combinedOf.none { it in Ship.RANDOMIZE_EXCLUDED } &&
                        player.hardpoints.indices.any { player.weaponsFor(it) != null && player.hardpoints[it].weapon == null }
                is DroneBlueprint -> player.droneSystems.any { null in it.drones }
                else -> false
            }
            if (hasSlot)
                continue
            player.scrap += (item.cost ?: 0) / 2
            player.cargoBlueprints[index] = null
            sold += item.name
        }
        // And the weapons fitted (by an event's reward) that only help the
        // enemy, such as the healing bombs
        for (hardpoint in player.hardpoints) {
            val type = hardpoint.weapon?.type ?: continue
            if (type.name !in Ship.RANDOMIZE_EXCLUDED && type.combinedOf.none { it in Ship.RANDOMIZE_EXCLUDED })
                continue
            hardpoint.weapon?.let { player.weaponsFor(it)?.setWeaponPower(it, false) }
            hardpoint.weapon = null
            player.scrap += type.cost / 2
            sold += type.name
        }
        if (sold.isNotEmpty() || fitted.isNotEmpty())
            player.cargoUpdated()

        // The hull, a point at a time, at the store's price
        val repairPrice = when (game.currentBeacon.sector.difficultySectorNumber) {
            0, 1, 2 -> 2
            3, 4, 5 -> 3
            else -> 4
        }
        // Most of its hull first ([REPAIR_FIRST]), and the rest once it's
        // bought what it wants and upgraded: it's the hull that keeps it going
        var repaired = 0
        fun repair(upTo: Int) {
            while (player.health < upTo && canSpend(repairPrice)) {
                player.scrap -= repairPrice
                player.health += 1
                repaired++
            }
        }
        repair((player.maxHealth * REPAIR_FIRST).toInt())

        // Supplies, up to [wanted] (in jumps for the fuel), bought a unit at
        // a time with the scrap [afford] allows
        val bought = HashMap<Resource, Int>()
        fun buySupply(resource: Resource, wanted: Int, afford: (Int) -> Boolean) {
            val price = resource.getBlueprint(game)?.cost ?: return
            while ((store.availableResources[resource] ?: 0) > 0 && afford(price)) {
                val have = when (resource) {
                    Resource.FUEL -> player.fuelCount / player.fuelPerJump
                    Resource.DRONES -> player.dronesCount
                    Resource.MISSILES -> player.missilesCount
                    else -> return
                }
                if (have >= wanted)
                    return
                player.scrap -= price
                store.availableResources[resource] = store.availableResources.getValue(resource) - 1
                when (resource) {
                    Resource.FUEL -> player.fuelCount++
                    Resource.DRONES -> player.dronesCount++
                    Resource.MISSILES -> player.missilesCount++
                    else -> {}
                }
                bought[resource] = (bought[resource] ?: 0) + 1
            }
        }

        // Fuel before anything else, out of the reserve if it has to: the
        // reserve is for it (FUEL_JUMPS)
        buySupply(Resource.FUEL, FUEL_JUMPS) { price -> price > 0 && player.scrap >= price }

        // The augments it wants first, one of each, in order (see
        // FIRST_AUGMENTS): the Long-Range Scanners, to steer round the
        // hazards (see seemsDangerous), the Scrap Recovery Arm and the
        // Weapon Pre-Igniter
        val firstAugments = ArrayList<String>()
        for (name in FIRST_AUGMENTS) {
            val slot = store.augments.indexOfFirst { it?.name == name }
            if (slot < 0 || player.hasAugment(name) || player.augments.size >= player.maxAugments)
                continue
            val augment = store.augments[slot]!!
            if (canSpend(augment.cost) && player.addBlueprint(augment, false)) {
                player.scrap -= augment.cost
                store.augments[slot] = null
                firstAugments += name
            }
        }

        for ((resource, wanted) in STORE_WANTS)
            buySupply(resource, wanted, ::canSpend)
        val gear = buyGear(game, store, ::canSpend)
        val upgrades = upgrade(player, ::canSpend)
        gear += buyAugments(player, store, ::canSpend)
        repair(player.maxHealth)

        note("Shopped: " + (if (fitted.isNotEmpty()) "fitted ${fitted.joinToString()}, " else "") +
                (if (sold.isNotEmpty()) "sold ${sold.joinToString()}, " else "") +
                "repaired $repaired hull, " +
                (if (firstAugments.isNotEmpty()) "bought ${firstAugments.joinToString()}, " else "") +
                (if (gear.isNotEmpty()) "bought ${gear.joinToString()}, " else "") +
                "supplies ${bought.entries.joinToString { "${it.value} ${it.key}" }.ifEmpty { "none" }}, " +
                "upgraded ${upgrades.ifEmpty { "nothing" }}, scrap $start -> ${player.scrap}")
    }

    /**
     * The reactor power left over once every system, weapon and drone it
     * has is powered.
     */
    private fun spareReactor(player: Ship): Int {
        val systems = player.systems.filterIsInstance<MainSystem>()
            .filter { it !is Weapons && it !is Drones }.sumOf { it.energyLevels }
        val weapons = player.hardpoints.sumOf { it.weapon?.type?.power ?: 0 }
        val drones = player.droneSystems.sumOf { system -> system.drones.sumOf { it?.type?.power ?: 0 } }
        return player.purchasedReactorPower - systems - weapons - drones
    }

    /**
     * An empty slot of a weapons system with the power left for [weapon],
     * if the reactor can power it too, or null.
     */
    private fun weaponSlotFor(player: Ship, weapon: AbstractWeaponBlueprint): Int? {
        if (spareReactor(player) < weapon.power)
            return null
        return player.hardpoints.indices.firstOrNull { slot ->
            val system = player.weaponsFor(slot) ?: return@firstOrNull false
            if (player.hardpoints[slot].weapon != null)
                return@firstOrNull false
            val used = system.hardpointIndices.sumOf { player.hardpoints.getOrNull(it)?.weapon?.type?.power ?: 0 }
            system.energyLevels - used >= weapon.power
        }
    }

    /**
     * Upgrade a weapons system with an empty slot, and the reactor, so they
     * can power [weapon] too, if it can afford that and the weapon with the
     * scrap [canSpend] allows. Returns the slot, or null if it can't.
     */
    private fun makeRoomFor(player: Ship, weapon: AbstractWeaponBlueprint, canSpend: (Int) -> Boolean): Int? {
        val weaponCost = weapon.cost ?: 0
        for (slot in player.hardpoints.indices) {
            val system = player.weaponsFor(slot) ?: continue
            if (player.hardpoints[slot].weapon != null)
                continue
            val used = system.hardpointIndices.sumOf { player.hardpoints.getOrNull(it)?.weapon?.type?.power ?: 0 }
            val levels = used + weapon.power - system.energyLevels
            if (levels <= 0 || system.energyLevels + levels > system.blueprint.maxPower)
                continue
            val upgrades = (0 until levels).sumOf {
                system.blueprint.upgradeCost.getOrNull(system.energyLevels - 1 + it) ?: return@sumOf 10000
            }
            // The reactor for the weapon, with the price of each bar going
            // up as it does in upgrade
            val bars = maxOf(0, weapon.power - spareReactor(player))
            val reactor = (0 until bars).sumOf { ((player.purchasedReactorPower + it) / 5) * 5 + 15 }
            if (player.purchasedReactorPower + bars > player.maxReactorPower ||
                !canSpend(upgrades + reactor + weaponCost)
            ) continue
            player.scrap -= upgrades + reactor
            system.energyLevels += levels
            player.purchasedReactorPower += bars
            return slot
        }
        return null
    }

    /**
     * A drones system with an empty slot and the power left for [drone], if
     * the reactor can power it too, or null.
     */
    private fun droneSystemFor(player: Ship, drone: DroneBlueprint): Drones? {
        if (spareReactor(player) < drone.power)
            return null
        return player.droneSystems.firstOrNull { system ->
            null in system.drones && system.energyLevels - system.drones.sumOf { it?.type?.power ?: 0 } >= drone.power
        }
    }

    /**
     * Buy weapons, crew and drones, in that order, with the scrap
     * [canSpend] allows. A weapon
     * goes in an empty slot of a weapons system with the power left for
     * it, or that it upgrades for it (see makeRoomFor), if it can afford
     * both (the best against shields first: missiles and ion weapons, then
     * those with the most shots), and a drone likewise (a defense drone
     * first, then combat ones), as long as the reactor can power them too.
     * (The augments come after the upgrades, see buyAugments.)
     * Crew, as long as there's room, one a sector at most, the best fighters
     * first ([CREW_PREFERENCE]). Returns what it bought.
     */
    private fun buyGear(game: InGameState, store: StoreData, canSpend: (Int) -> Boolean): MutableList<String> {
        val player = game.player
        val bought = ArrayList<String>()

        fun weaponScore(weapon: AbstractWeaponBlueprint): Int = when {
            weapon.missilesUsed > 0 && weapon !is xyz.znix.xftl.weapons.BombBlueprint -> 3
            weapon.ionDamage > 0 -> 3
            else -> minOf(weapon.shots, 2)
        }
        val weapons = store.weapons.withIndex()
            .filter { (_, w) -> w != null && w.name !in Ship.RANDOMIZE_EXCLUDED && w.combinedOf.none { it in Ship.RANDOMIZE_EXCLUDED } }
            .sortedByDescending { (_, w) -> weaponScore(w!!) * 1000 + (w.cost ?: 0) }
        for ((index, weapon) in weapons) {
            weapon!!
            if (!canSpend(weapon.cost ?: 0))
                continue
            val slot = weaponSlotFor(player, weapon) ?: makeRoomFor(player, weapon, canSpend) ?: continue
            player.scrap -= weapon.cost ?: 0
            player.hardpoints[slot].weapon = weapon.buildInstance(player)
            store.weapons[index] = null
            bought += weapon.name
        }

        // One crew member a sector at most, as they take the scrap it needs
        // for upgrades
        // The best fighters first, as boarders are what kill its crew
        val sector = game.currentBeacon.sector
        val crewForSale = store.crew.withIndex().filter { it.value != null }
            .sortedBy { CREW_PREFERENCE.indexOf(it.value!!.race.name).let { i -> if (i < 0) CREW_PREFERENCE.size else i } }
        for ((index, crew) in crewForSale) {
            crew ?: continue
            if (crewBoughtIn == sector || game.isPlayerCrewFull || !canSpend(crew.race.cost ?: 0))
                continue
            player.scrap -= crew.race.cost ?: 0
            player.addCrewMember(crew, false)
            store.crew[index] = null
            bought += crew.race.name
            crewBoughtIn = sector
        }

        fun droneScore(drone: DroneBlueprint): Int = when (drone.type) {
            DroneBlueprint.DroneType.DEFENSE -> if (player.droneSystems.any { s -> s.drones.any { it?.type?.type == DroneBlueprint.DroneType.DEFENSE } }) 1 else 3
            DroneBlueprint.DroneType.COMBAT -> 2
            // Anti-personnel drones, against boarders
            DroneBlueprint.DroneType.BATTLE -> 2
            else -> 0
        }
        val drones = store.drones.withIndex()
            .filter { (_, d) -> d != null && droneScore(d) > 0 }
            .sortedByDescending { (_, d) -> droneScore(d!!) }
        for ((index, drone) in drones) {
            drone!!
            if (!canSpend(drone.cost ?: 0) || spareReactor(player) < drone.power)
                continue
            val system = droneSystemFor(player, drone) ?: continue
            player.scrap -= drone.cost ?: 0
            system.drones[system.drones.indexOf(null)] = Drones.DroneInfo(drone, null)
            store.drones[index] = null
            bought += drone.name
        }
        player.cargoUpdated()

        return bought
    }

    /**
     * Buy the augments that help in a fight ([WANTED_AUGMENTS]), with the
     * scrap [canSpend] allows once it's upgraded, and only once its shields
     * systems have two layers each. Returns what it bought.
     */
    private fun buyAugments(player: Ship, store: StoreData, canSpend: (Int) -> Boolean): List<String> {
        val bought = ArrayList<String>()
        // Only once its shields are up to two layers each (the first ones
        // it wants come before, see FIRST_AUGMENTS)
        if (player.shieldSystems.any { it.energyLevels < 4 })
            return bought
        for ((index, augment) in store.augments.withIndex()) {
            augment ?: continue
            if (augment.name !in WANTED_AUGMENTS || player.hasAugment(augment.name) ||
                player.augments.size >= player.maxAugments || !canSpend(augment.cost ?: 0)
            ) continue
            if (player.addBlueprint(augment, false)) {
                player.scrap -= augment.cost ?: 0
                store.augments[index] = null
                bought += augment.name
            }
        }
        return bought
    }

    /**
     * Upgrade the ship's systems with the scrap [canSpend] allows, a power
     * at a time: the first two layers of each shields system, then the
     * weakest engines, and weapons systems with an empty slot or a weapon
     * they can't power, then the rest, the oxygen and medbay last, and the
     * weapons systems that are full and powered after that, with a bar of
     * reactor after each (and first, when it couldn't power them all).
     * Returns what it bought, like "2 shields, 1 reactor".
     */
    private fun upgrade(player: xyz.znix.xftl.Ship, canSpend: (Int) -> Boolean): String {
        val bought = LinkedHashMap<String, Int>()
        // A weapons system wants more power when it has an empty slot, or a
        // weapon it can't power, for a weapon to buy or fit
        fun wantsPower(system: Weapons): Boolean {
            val slots = system.hardpointIndices.filter { it < player.hardpoints.size }
            val needed = slots.sumOf { player.hardpoints[it].weapon?.type?.power ?: 0 }
            return slots.any { player.hardpoints[it].weapon == null } || needed > system.energyLevels
        }
        // A drones system wants more power when there's a drone in the cargo
        // hold it has an empty slot for, but not the power (see shop)
        fun wantsPower(system: Drones): Boolean {
            val spare = system.energyLevels - system.drones.sumOf { it?.type?.power ?: 0 }
            return null in system.drones &&
                    player.cargoBlueprints.any { it is DroneBlueprint && it.power > spare }
        }
        fun priority(system: MainSystem): Int = when (system) {
            // The first two layers of each shields system, then the rest
            is Shields -> if (system.energyLevels < 4) 0 else 2
            is Weapons -> if (wantsPower(system)) 1 else 4
            is Drones -> if (wantsPower(system)) 1 else 2
            is Engines -> 1
            is Oxygen, is Medbay, is Clonebay -> 3
            else -> 2
        }
        while (true) {
            val main = player.systems.filterIsInstance<MainSystem>()
            val system = main.filter { it.energyLevels < it.blueprint.maxPower }
                .minWithOrNull(compareBy<MainSystem>({ priority(it) }, { it.energyLevels }))
                ?: break
            val price = system.blueprint.upgradeCost.getOrNull(system.energyLevels - 1) ?: break

            // The reactor first, if it couldn't power the upgrade too
            val needed = main.sumOf { it.energyLevels } + 1
            if (needed > player.purchasedReactorPower && player.purchasedReactorPower < player.maxReactorPower) {
                // (only if it can afford the upgrade after it too)
                val reactorPrice = (player.purchasedReactorPower / 5) * 5 + 15
                if (!canSpend(reactorPrice + price))
                    break
                player.scrap -= reactorPrice
                player.purchasedReactorPower++
                bought["reactor"] = (bought["reactor"] ?: 0) + 1
                continue
            }

            if (!canSpend(price))
                break
            player.scrap -= price
            system.energyLevels++
            val name = system.blueprint.name.lowercase()
            bought[name] = (bought[name] ?: 0) + 1

            // And a bar of reactor after each one, to power more weapons
            // and drones too
            if (player.purchasedReactorPower < player.maxReactorPower) {
                val reactorPrice = (player.purchasedReactorPower / 5) * 5 + 15
                if (canSpend(reactorPrice)) {
                    player.scrap -= reactorPrice
                    player.purchasedReactorPower++
                    bought["reactor"] = (bought["reactor"] ?: 0) + 1
                }
            }
        }
        return bought.entries.joinToString { "${it.value} ${it.key}" }
    }

    private fun note(text: String) {
        val minutes = (System.nanoTime() - startNanos) / 60e9f
        val line = "[autoplay] %5.1f min: %s".format(minutes, text)
        println(line)
        log += line
    }

    private fun countEvent(game: InGameState, serialId: String) {
        val root = eventRoot(serialId)
        val vanilla = vanillaEventNames ?: readVanillaEventNames(game).also { vanillaEventNames = it }
        val counts = if (root in vanilla) vanillaEventsSeen else newEventsSeen
        counts[root] = (counts[root] ?: 0) + 1
    }

    /**
     * The names of the events, event lists and ships in the vanilla game's
     * event files, without any mods. A mod's copy of a vanilla event (which
     * replaces it) counts as vanilla.
     */
    private fun readVanillaEventNames(game: InGameState): Set<String> {
        val vanilla = game.content.datafile.vanilla
        val files = EventManager.FILE_NAMES.map { "data/$it.xml" }.toSet()
        return vanilla.getAllFiles().filter { it.name in files }.flatMapTo(HashSet()) { entry ->
            VANILLA_NAME.findAll(String(vanilla.read(entry), Charsets.UTF_8)).map { it.groupValues[1] }
        }
    }

    private fun finish() {
        if (finished)
            return
        finished = true
        val report = File(outputDir, "report-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.txt")
        outputDir.mkdirs()
        report.writeText(buildString {
            appendLine("Automatic playtest")
            appendLine("Sectors: $sectors, beacons: $beacons, events: $events, fights: $fights, screenshots: $shots")
            appendLine("Different exceptions: ${errors.size}, in all: ${errors.values.sumOf { it.first }}")
            appendLine()
            appendLine("== Summary")
            appendLine("Sectors (${sectorNames.size}): ${sectorNames.joinToString(" -> ")}")
            appendLine("Enemy ships: fought ${shipsFought.values.sum()}, destroyed (or crew killed) " +
                    "${shipsKilled.values.sum()}, got away or left $shipsLeft")
            appendLine("Scrap: earned $scrapEarned, spent $scrapSpent, ended with ${lastScrap ?: 0}")
            appendLine("Stores shopped at: $storesShopped")
            appendLine("Waits for fuel, out of it: $outOfFuelWaits")
            appendLine("Fights fled, judged not worth it: $fleeingFights")
            appendLine("Times it vented rooms: $ventings")
            appendLine("Stores in each sector: " + storesBySector.entries.joinToString { "${it.key.substringBefore(" x")} ${it.value}" })
            fun weaponLine(title: String, counts: Map<String, WeaponCount>) {
                appendLine("$title: " + counts.entries.joinToString("; ") { (key, c) ->
                    "$key: ${c.ships} (${c.shipsWithCombined} with combined), ${c.weapons} weapons, " +
                            "${c.doubles} double, ${c.triples} triple"
                }.ifEmpty { "none" })
            }
            weaponLine("Enemy weapons, by shape", enemyWeapons)
            weaponLine("Store weapons, by sector", storeWeapons)
            lastPlayerWeapons?.let { appendLine("Player's weapons at the end: $it") }
            appendLine("Blue options picked: $blueChoices")
            appendLine("Quests started: ${questsStarted.size}${questsStarted.joinToString(prefix = " (", postfix = ")").takeIf { questsStarted.isNotEmpty() } ?: ""}, quest beacons reached: $questBeacons")
            fun repeats(title: String, counts: Map<String, Int>) {
                val repeated = counts.filterValues { it > 1 }.entries.sortedByDescending { it.value }
                appendLine("$title: ${counts.size} different, ${repeated.size} more than once" +
                        repeated.joinToString(prefix = ": ") { "${it.key} x${it.value}" }.takeIf { repeated.isNotEmpty() }.orEmpty())
            }
            repeats("Sectors", sectorNames.groupingBy { it }.eachCount())
            repeats("Enemy ships", shipsFought)
            repeats("Events", eventsSeen.filterKeys { !it.startsWith("(") })
            val vanillaCount = vanillaEventsSeen.values.sum()
            val newCount = newEventsSeen.values.sum()
            val percent = if (vanillaCount + newCount == 0) 0 else newCount * 100 / (vanillaCount + newCount)
            appendLine("Vanilla vs new events: $vanillaCount vanilla (${vanillaEventsSeen.size} different), " +
                    "$newCount new ($percent%, ${newEventsSeen.size} different)")
            repeats("New events", newEventsSeen)
            appendLine()
            for ((key, value) in errors) {
                appendLine("== ${value.first}x $key")
                appendLine(value.second)
            }
            appendLine("== Log")
            log.forEach { appendLine(it) }
        })
        println("[autoplay] ${errors.size} different exceptions, report saved to ${report.absolutePath}")
        main.quitGame()
    }

    companion object {
        // A top-level event, event list or ship in an event file
        private val VANILLA_NAME = Regex("""<(?:event|eventList|ship)\s[^>]*?\bname\s*=\s*"([^"]+)"""")

        /**
         * The name of the event, event list or ship the event with this
         * deserialisation ID is part of (see EventManager): rootEvent::NAME,
         * NAME::eventList3 or embeddedEvent::NAME::destroyed, then any
         * choices, with a -N suffix where a mod replaced the event.
         */
        fun eventRoot(serialId: String): String {
            val id = serialId.removePrefix("rootEvent::").removePrefix("embeddedEvent::")
            return id.substringBefore("::").replace(Regex("-\\d+$"), "")
        }

        /**
         * What the playtest shows in the top right corner (see
         * InGameState.render): the sector it's in and how many beacons it's
         * been to, or null when it isn't running.
         */
        @JvmStatic
        var status: String? = null
            private set

        // The length of each update, how many there are each frame (how much
        // faster than real time it plays), how long it waits on each window
        // and beacon, and how often it wanders off its way to the exit
        const val FRAME = 1f / 30
        val UPDATES_PER_FRAME: Int = (System.getenv("XFTL_AUTOPLAY_SPEED") ?: "8").toIntOrNull()?.coerceIn(1, 32) ?: 8
        const val WINDOW_DELAY = 0.5f
        const val FIGHT_SHOT_DELAY = 3f
        const val EVENT_TEXT_LENGTH = 80

        // How much of its scrap it keeps when it shops, and how much of each
        // supply it buys up to, before spending the rest on upgrades. It
        // doesn't buy fuel, as it never runs out anyway (see step).
        const val STORE_RESERVE = 0.1f
        val STORE_WANTS = listOf(Resource.MISSILES to 14)
        // How many jumps' worth of fuel it buys at a store, first: enough to
        // get to the next store, usually (more takes the scrap it needs for
        // upgrades)
        const val FUEL_JUMPS = 16
        const val BEACON_STAY = 1f
        const val WANDER_CHANCE = 0f
        // How many jumps it keeps to spare, exploring, before the fleet
        // would overtake the exit
        const val EXPLORE_MARGIN = 3
        // How much of its hull it needs to explore, rather than heading for
        // stores and the exit
        const val EXPLORE_MIN_HULL = 0.5f
        // How many jumps' worth of fuel it keeps to spare, exploring, beyond
        // what it takes to get to the exit
        const val EXPLORE_FUEL_MARGIN = 6
        // The longest it waits at a beacon (in game seconds) for the ship to
        // be in order (see waitingFor): waiting costs nothing, as the fleet
        // only advances when it jumps, but boarders, repairs and healing
        // one after another can take a while
        const val MAX_BEACON_WAIT = 180f
        // The longest a fight goes on (in game seconds) before it jumps away
        const val MAX_FIGHT_TIME = 240f
        // How much of its hull it has left when it runs from a fight
        const val RETREAT_HULL = 0.5f
        // How much of its hull it can lose in one fight before it runs
        const val RETREAT_LOSS = 0.3f
        // How much of its hull it mends at a store before buying anything
        // else (see shop)
        const val REPAIR_FIRST = 0.9f
        // How much of its hull it can lose before it goes out of its way to
        // a store to mend it, if it has the scrap for at least that many
        // points of it (at the dearest price, see shop), and that price
        const val REPAIR_TRIP_HULL = 0.7f
        const val REPAIR_TRIP_POINTS = 15
        const val REPAIR_PRICE_HIGHEST = 4
        // How much of its crew's health is left when it runs from a fight
        const val RETREAT_CREW = 0.6f

        // Venting (see ventRooms): the fewest burning cells for a fire to be
        // worth venting with the crew in the room, how strong the boarders
        // are against its crew before it vents them rather than fighting,
        // and the most rooms it vents at once
        const val FIRE_VENT_CELLS = 2
        const val BOARDERS_VENT = 0.6
        const val MAX_VENTED_ROOMS = 3

        // The reason it waits while the crew heal (see waitingFor), and how
        // healed they get: as far as the crew's AI heals them when the
        // ship's calm (see FriendlyCrewAI)
        private const val HEALING = "healing"
        const val HEALED = 0.99f

        // The crew races it hires, the best fighters first
        val CREW_PREFERENCE = listOf("mantis", "rock", "crystal", "anaerobic", "human", "slug", "energy", "engi")

        // The augments it buys first, one of each, in this order, whatever
        // its shields (see shop), and those it buys once each shields system
        // has two layers (see buyAugments)
        val FIRST_AUGMENTS = listOf(
            AugmentBlueprint.LONG_RANGE_SCANNERS, AugmentBlueprint.SCRAP_RECOVERY_ARM, "WEAPON_PREIGNITE"
        )
        val WANTED_AUGMENTS = setOf(AugmentBlueprint.AUTOMATED_RELOADERS)

        /**
         * Start the automatic playtest, if XFTL_AUTOPLAY is set. This is
         * called once the game has started.
         */
        @JvmStatic
        fun install(main: MainGame, container: GameContainer) {
            if (!XftlDebug.autoplay)
                return
            val lwjgl = container as? LWJGLGameContainer ?: return
            val sectors = System.getenv("XFTL_AUTOPLAY_SECTORS")?.toIntOrNull() ?: Int.MAX_VALUE
            val minutes = System.getenv("XFTL_AUTOPLAY_MINUTES")?.toFloatOrNull() ?: 30f
            val dir = File(System.getenv("XFTL_AUTOPLAY_DIR") ?: "dist/playtest")
            val director = AutoplayDirector(main, dir, sectors, minutes)
            println("[autoplay] Playing ${UPDATES_PER_FRAME}x as fast, for up to $minutes minutes")
            lwjgl.fixedDeltaSec = FRAME
            lwjgl.updatesPerFrame = UPDATES_PER_FRAME
            lwjgl.renderWhenUnfocused = true
            lwjgl.onUpdateError = director::updateError
            // The saves are caught and printed, so the report counts them too
            ContinueSave.onSaveError = director::updateError
            lwjgl.afterRender = director::afterRender
        }
    }
}
