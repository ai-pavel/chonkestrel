package xyz.znix.xftl.devutil

import xyz.znix.xftl.Ship
import xyz.znix.xftl.ShipShape
import xyz.znix.xftl.XftlDebug
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.LivingCrewInfo
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.systems.Cloaking
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.systems.Engines
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.WeaponSizes
import kotlin.random.Random

/**
 * The release trailer, which the video recorder records (XFTL_VIDEO,
 * see [VideoDirector] and doc/tools/trailer.md): the first few seconds of sixteen fights, a
 * game's worth of them, with a screenshot of each, as the hazards build up
 * from a nebula to all four at once (see [FIGHTS]). It starts with a
 * Chonkestrel that's had its first upgrades, against two vanilla ships, and
 * with each fight the Chonkestrel gets more crew, systems and weapons, and
 * its enemies get bigger and more of them, until it fights three hybrid
 * ships, all of them (and it) armed with double and triple weapons (see
 * doc/game/weapon-sizes.md). The enemies' rooms and crew are shown, even in a
 * nebula. Every enemy has the Zoltan Shield Bypass, and those with
 * teleporters board the Chonkestrel.
 *
 * Its Chonkestrel doesn't get debug mode's loadout (see
 * [XftlDebug.plainPlayerShip]): it's built up here, a fight at a time.
 */
object Trailer {
    const val NAME = "trailer"

    /**
     * How long each fight is shown for, and when in it its screenshot's
     * taken.
     */
    private const val FIGHT_SECONDS = 6f
    private const val SCREENSHOT_AT = 4f

    /**
     * When in each fight the enemies with teleporters board the Chonkestrel,
     * and when its boarding party boards them (before the enemies' cloaks
     * are ready, see [CLOAK_DELAY]).
     */
    private const val BOARDING_AT = 1.5f
    private const val PLAYER_BOARDING_AT = 1f

    /**
     * How long the enemies' cloaking takes to get ready as each fight
     * starts, so the Chonkestrel's boarders have time to get aboard.
     */
    private const val CLOAK_DELAY = 2.5f

    /** The most crew in the Chonkestrel's boarding party, and who's best at it, best first. */
    private const val BOARDING_PARTY = 4
    private val BOARDERS = listOf("mantis", "rock", "crystal", "human", "engi", "slug", "energy")

    /**
     * The Lanius (who take the air out of the room they're in) never wait
     * in the medbay, where they'd suffocate the others: they man the
     * engines, and wait there.
     */
    private const val LANIUS = "anaerobic"

    /**
     * How charged everyone's weapons are as a fight starts (as a fraction,
     * at random), so they fire in the few seconds it's shown for, but not
     * all at once.
     */
    private val START_CHARGE = 0.7f..0.95f

    /** An enemy ship: its blueprint (or a random hybrid, if null) and its shape. */
    private class Enemy(val blueprint: String?, val shape: ShipShape)

    /**
     * One fight: where it is (a sector, how far through the game, and any
     * hazards), who it's against (and the biggest combined weapons they're
     * given, 1 for none), and what the Chonkestrel has by then.
     */
    private class Fight(
        val sector: String,
        val sectorClass: GameMap.SectorClass,
        val progress: Float,
        val enemies: List<Enemy>,
        val enemyWeaponSize: Int = 1,
        val hazards: List<Beacon.EnvironmentType> = emptyList(),
        val player: Loadout? = null,
    )

    /**
     * What the Chonkestrel gets for a fight, on top of what it had: more
     * crew (by race), more systems (one of each listed that isn't
     * installed yet), each system's level (by name, or "all", but for the
     * cloaking, which grows a level at a time, see [CLOAK]), its weapons
     * (by slot: a blueprint and its size), drones, Zoltan shields and
     * other augments.
     */
    private class Loadout(
        val crew: List<String> = emptyList(),
        val systems: List<String> = emptyList(),
        val levels: Map<String, Int> = emptyMap(),
        val weapons: List<Pair<String, Int>?>? = null,
        val drones: List<String> = emptyList(),
        val zoltanShields: Int = 0,
        val augments: List<String> = emptyList(),
    )

    private const val ALL = "all"
    private const val MAX = 99

    // The Chonkestrel's cloaking, which it gets in the third fight, and
    // upgrades every other fight to its sixth and last level (see
    // Cloaking.durationFor). It's only shown, never used in the fights.
    private const val CLOAK = "cloaking"

    // The hazards, for short
    private val RADIATION = Beacon.EnvironmentType.RADIATION
    private val MIRAGE = Beacon.EnvironmentType.MIRAGE
    private val DARK = Beacon.EnvironmentType.DARK
    private val NEBULA = Beacon.EnvironmentType.NEBULA
    private val ION_STORM = Beacon.EnvironmentType.ION_STORM
    private val ASTEROID = Beacon.EnvironmentType.ASTEROID
    private val SUN = Beacon.EnvironmentType.SUN
    private val PULSAR = Beacon.EnvironmentType.PULSAR

    private fun vanilla(name: String, shape: ShipShape = ShipShape.NORMAL) = Enemy(name, shape)
    private fun hybrid(shape: ShipShape = ShipShape.NORMAL) = Enemy(null, shape)

    private val LONG = ShipShape.LONG
    private val WIDE = ShipShape.WIDE

    // The Chonkestrel's weapons, as it gets more and bigger ones
    private val TRIPLES_EVERYWHERE = listOf(
        "LASER_HEAVY_1" to 3, "MISSILES_2_PLAYER" to 3, "BEAM_1" to 3, "BOMB_1" to 3,
        "LASER_BURST_2" to 3, "ION_1" to 3, "MISSILES_1" to 3, "BOMB_ION" to 3,
    )

    /**
     * The fights: the hazards build up from one of XFTL's own nebulas on its
     * own, through every pair of hazards (extreme), every three (catastrophic),
     * to all four at once (apocalyptic, see doc/game/extreme-hazards.md), with
     * every kind of nebula along the way, and the enemies from two vanilla
     * ships to three hybrids armed with triple weapons. Each fight's ships
     * have to fit on the screen (see FightPlan): a wide one takes two
     * places, so it only comes with one other ship, and never a wide one.
     */
    private val FIGHTS = listOf(
        // XFTL's own nebulas, each on its own
        Fight(
            "XFTL_RADIATION_FIELDS", GameMap.SectorClass.NEBULA, 0f,
            listOf(vanilla("CIRCLE_SCOUT"), vanilla("REBEL_SKINNY")), hazards = listOf(RADIATION),
            player = Loadout(
                crew = listOf("engi", "mantis", "rock"),
                systems = listOf("drones"),
                levels = mapOf("shields" to 4, "engines" to 3, "weapons" to 6, "drones" to 3, "medbay" to 1),
                weapons = listOf(
                    "LASER_BURST_3" to 2, "MISSILES_2_PLAYER" to 1, "LASER_HEAVY_1" to 1, "BOMB_1" to 1,
                    "LASER_BURST_3" to 2, "MISSILES_2_PLAYER" to 1, "ION_1" to 1, "BEAM_1" to 1,
                ),
                // (a twin combat drone, one at each enemy ship, see
                // doc/game/drone-sizes.md)
                drones = listOf("XFTL_X2_COMBAT_1"),
            )
        ),
        Fight(
            "XFTL_MIRAGE_EXPANSE", GameMap.SectorClass.NEBULA, 0.07f,
            listOf(vanilla("MANTIS_FIGHTER"), vanilla("ZOLTAN_FIGHTER")), hazards = listOf(MIRAGE),
            player = Loadout(
                crew = listOf("rock"),
                systems = listOf("hacking"),
                levels = mapOf("shields" to 5, "engines" to 4, "weapons" to 7, "hacking" to 2),
            )
        ),
        Fight(
            "XFTL_DARK_REACH", GameMap.SectorClass.NEBULA, 0.13f,
            listOf(vanilla("JELLY_BUTTON", LONG), vanilla("ROCK_SCOUT")), hazards = listOf(DARK),
            player = Loadout(
                crew = listOf("engi"),
                systems = listOf(CLOAK),
                levels = mapOf(CLOAK to 1),
                weapons = listOf(
                    "LASER_BURST_3" to 2, "MISSILES_2_PLAYER" to 2, "LASER_HEAVY_1" to 1, "BOMB_1" to 1,
                    "LASER_BURST_3" to 2, "MISSILES_2_PLAYER" to 1, "ION_1" to 2, "BEAM_1" to 1,
                ),
            )
        ),

        // Extreme hazards: every pair
        Fight(
            "ROCK_SECTOR", GameMap.SectorClass.HOSTILE, 0.2f,
            listOf(vanilla("CIRCLE_BOMBER", WIDE), vanilla("CRYSTAL_SCOUT")), hazards = listOf(ASTEROID, SUN),
            player = Loadout(
                crew = listOf("slug"),
                systems = listOf("sensors"),
                levels = mapOf("shields" to 8, "engines" to 5, "weapons" to 8, "drones" to 6),
                weapons = listOf(
                    "LASER_BURST_3" to 2, "MISSILES_2_PLAYER" to 2, "BEAM_1" to 2, "LASER_HEAVY_1" to 1,
                    "LASER_BURST_2" to 2, "ION_1" to 2, "MISSILES_1" to 2, "BOMB_ION" to 2,
                ),
                drones = listOf("XFTL_X2_COMBAT_1", "XFTL_X2_DEFENSE_1"),
            )
        ),
        Fight(
            "MANTIS_SECTOR", GameMap.SectorClass.HOSTILE, 0.27f,
            listOf(vanilla("MANTIS_BOMBER", LONG), vanilla("REBEL_FAT", LONG)), hazards = listOf(NEBULA, ASTEROID),
            player = Loadout(
                crew = listOf("energy", "mantis"),
                systems = listOf("mind", "teleporter"),
                levels = mapOf("engines" to 6, "mind" to 2, "hacking" to 3, "oxygen" to 2, "teleporter" to 2, CLOAK to 2),
                zoltanShields = 1,
                augments = listOf(AugmentBlueprint.RECONSTRUCTIVE_TELEPORT),
            )
        ),
        Fight(
            "ENGI_SECTOR", GameMap.SectorClass.HOSTILE, 0.33f,
            listOf(vanilla("ROCK_ASSAULT", WIDE), vanilla("ANAEROBIC_SCOUT")), hazards = listOf(ASTEROID, PULSAR),
            player = Loadout(
                crew = listOf("crystal"),
                systems = listOf("drones"),
                levels = mapOf(ALL to 6, "weapons" to 8, "shields" to 8, "drones" to 8),
                // (a triple wing and a twin defence screen, then in the
                // second drones system a twin raid and a bank of
                // overchargers: each system's 8 power runs its drones, see
                // DroneSizes; "" leaves a slot as it is)
                drones = listOf(
                    "XFTL_X3_COMBAT_1", "XFTL_X2_DEFENSE_1", "",
                    "XFTL_X2_BOARDER", "XFTL_X2_DRONE_SHIELD_PLAYER",
                ),
            )
        ),
        Fight(
            "NEBULA_SECTOR", GameMap.SectorClass.NEBULA, 0.4f,
            listOf(vanilla("JELLY_CROISSANT", LONG), vanilla("FED_SCOUT"), vanilla("AUTO_ASSAULT")),
            hazards = listOf(ION_STORM, SUN),
            player = Loadout(
                crew = listOf("crystal"),
                levels = mapOf(CLOAK to 3),
                weapons = listOf(
                    "LASER_HEAVY_1" to 3, "MISSILES_2_PLAYER" to 3, "BEAM_1" to 3, "BOMB_1" to 2,
                    "LASER_BURST_3" to 2, "ION_1" to 2, "LASER_HEAVY_1" to 2, "LASER_BURST_2" to 1,
                ),
            )
        ),
        Fight(
            "ZOLTAN_SECTOR", GameMap.SectorClass.HOSTILE, 0.47f,
            listOf(vanilla("ZOLTAN_BOMBER", WIDE), vanilla("ROCK_FIGHT", LONG)),
            hazards = listOf(SUN, PULSAR),
            player = Loadout(crew = listOf("anaerobic"), zoltanShields = 2)
        ),
        Fight(
            "SLUG_SECTOR", GameMap.SectorClass.NEBULA, 0.53f,
            listOf(hybrid(WIDE), vanilla("MANTIS_SCOUT", LONG)), hazards = listOf(MIRAGE, PULSAR),
            player = Loadout(crew = listOf("anaerobic"), systems = listOf("hacking"), levels = mapOf(CLOAK to 4))
        ),

        // Catastrophic hazards: every three
        Fight(
            "XFTL_HYBRID_REBEL_ROCK", GameMap.SectorClass.HOSTILE, 0.6f,
            listOf(hybrid(LONG), hybrid(LONG)), hazards = listOf(ASTEROID, SUN, PULSAR),
            player = Loadout(levels = mapOf(ALL to MAX))
        ),
        Fight(
            "XFTL_HYBRID_SLUG_CRYSTAL", GameMap.SectorClass.HOSTILE, 0.67f,
            listOf(hybrid(WIDE), hybrid()), hazards = listOf(RADIATION, ASTEROID, SUN),
            player = Loadout(
                levels = mapOf(CLOAK to 5),
                weapons = listOf(
                    "LASER_BURST_3" to 2, "MISSILES_2_PLAYER" to 3, "BEAM_1" to 3, "LASER_BURST_2" to 1,
                    "BEAM_LONG" to 2, "ION_1" to 3, "LASER_HEAVY_1" to 3, "BOMB_ION" to 1,
                ),
            )
        ),
        Fight(
            "XFTL_DARK_REACH", GameMap.SectorClass.NEBULA, 0.73f,
            listOf(hybrid(WIDE), hybrid(LONG)), enemyWeaponSize = 2, hazards = listOf(DARK, ASTEROID, PULSAR),
            player = Loadout(
                drones = listOf(
                    "XFTL_X2_COMBAT_2", "XFTL_X2_COMBAT_2", "XFTL_X2_COMBAT_BEAM",
                    "XFTL_X2_COMBAT_2", "XFTL_X2_COMBAT_2", "XFTL_X2_COMBAT_BEAM",
                ),
                zoltanShields = 3,
            )
        ),
        Fight(
            "XFTL_ANVIL_YARDS", GameMap.SectorClass.HOSTILE, 0.8f,
            listOf(hybrid(LONG), hybrid(LONG), hybrid()), enemyWeaponSize = 2, hazards = listOf(NEBULA, SUN, PULSAR),
            player = Loadout(
                levels = mapOf(CLOAK to 6),
                weapons = listOf(
                    "LASER_HEAVY_1" to 3, "MISSILES_2_PLAYER" to 3, "BEAM_1" to 3, "BOMB_1" to 3,
                    "LASER_BURST_3" to 2, "ION_1" to 3, "MISSILES_1" to 3, "LASER_BURST_2" to 1,
                ),
                // (a triple defence screen and a twin anti-combat volley,
                // then a triple wing and a triple bank of overchargers)
                drones = listOf(
                    "XFTL_X3_DEFENSE_2", "XFTL_X2_ANTI_DRONE", "",
                    "XFTL_X3_COMBAT_1", "XFTL_X3_DRONE_SHIELD_PLAYER", "",
                ),
            )
        ),

        // Apocalyptic hazards: all four, each with a different nebula
        Fight(
            "XFTL_GULLET", GameMap.SectorClass.HOSTILE, 0.87f,
            listOf(hybrid(LONG), hybrid(LONG), hybrid(LONG)), enemyWeaponSize = 2,
            hazards = listOf(ION_STORM, ASTEROID, SUN, PULSAR),
            player = Loadout(weapons = TRIPLES_EVERYWHERE)
        ),
        Fight(
            "XFTL_BELLY", GameMap.SectorClass.HOSTILE, 0.93f,
            listOf(hybrid(WIDE), hybrid(LONG)), enemyWeaponSize = 3,
            hazards = listOf(DARK, ASTEROID, SUN, PULSAR),
        ),
        Fight(
            "XFTL_MAW", GameMap.SectorClass.HOSTILE, 1f,
            listOf(hybrid(LONG), hybrid(LONG), hybrid(LONG)), enemyWeaponSize = 3,
            hazards = listOf(RADIATION, ASTEROID, SUN, PULSAR),
            // (a triple raid of Ion Intruders and a twin repair crew, then
            // a triple wing of beam drones and another of combat drones)
            player = Loadout(
                drones = listOf(
                    "XFTL_X3_BOARDER_ION", "XFTL_X2_REPAIR", "",
                    "XFTL_X3_COMBAT_BEAM", "XFTL_X3_COMBAT_1", "",
                ),
            ),
        ),
    )

    /** How long the trailer is. */
    val LENGTH: Float = FIGHTS.size * FIGHT_SECONDS

    /**
     * The trailer's timeline: each fight set up as the last one ends, then,
     * once everyone's powered their weapons, everyone's weapons charged,
     * the enemies with teleporters boarding the Chonkestrel, its own boarding
     * party boarding them, and a screenshot of it all later on (sorted by
     * time, as the recorder runs them in order).
     */
    fun timeline(): List<VideoDirector.Step> = FIGHTS.withIndex().flatMap { (index, fight) ->
        val at = index * FIGHT_SECONDS
        listOf(
            VideoDirector.Step(at, "fight ${index + 1}: ${fight.sector} (${fight.hazards.joinToString(" + ")})") { game, driver ->
                XftlDebug.showEnemyCrew = true
                setUp(game, fight)
                driver.nextFight()
                driver.dronesOn = game.player.droneSystems.isNotEmpty()
                driver.hackingOn = game.player.hackingSystems.isNotEmpty()
                driver.mindControlOn = game.player.mindControlSystems.isNotEmpty()
                driver.startBattle()
            },
            VideoDirector.Step(at + BOARDING_AT, "the enemies with teleporters board") { _, driver ->
                driver.enemiesBoard(onlyWithTeleporters = true)
            },
            VideoDirector.Step(at + PLAYER_BOARDING_AT, "board") { game, driver ->
                if (game.player.teleporter != null)
                    driver.sendBoarders()
            },
            VideoDirector.Step(at + 0.5f, "charge the weapons") { game, _ ->
                val enemies = game.getOpponentShips(game.player)
                for (ship in enemies + game.player)
                    charge(ship)
                println("[video] Weapons: you: ${weaponsOf(game.player)}")
                for (enemy in enemies)
                    println("[video]   ${enemy.type.name}: ${weaponsOf(enemy)}")
            },
            VideoDirector.Step(at + SCREENSHOT_AT, "screenshot", screenshot = "fight-%02d".format(index + 1)) { _, _ -> },
        )
    }.sortedBy { it.at }

    private fun setUp(game: InGameState, fight: Fight) {
        val player = game.player
        bringBoardersHome(game)
        game.debugRemoveEnemy()
        ScreenshotDirector.patchUp(player)
        fight.player?.let { grow(game, it) }

        // A new sector, at the start
        val map = game.gameMap
        val type = map.sectorType(fight.sector)
            ?: map.sectorType("CIVILIAN_SECTOR")?.also { println("[video] No sector ${fight.sector}, using a civilian one") }
            ?: error("Missing the sector ${fight.sector}")
        val column = (fight.progress * (GameMap.SECTOR_COUNT - 2)).toInt()
        val sector = map.generateSector(map.SectorInfo(column, 0, type, fight.sectorClass), game)
        game.currentBeacon = sector.startBeacon
        if (fight.hazards.isNotEmpty())
            ScreenshotDirector.setEnvironments(game, fight.hazards)
        game.shipUI.debugCloseWindow()
        stationCrew(player)

        // The enemies, the first one first, then the rest as its wingmen
        val hybrids = game.blueprintManager[XftlDebug.SCOUT_SHIP].list().map { it.name }.shuffled()
        val names = fight.enemies.mapIndexed { i, enemy -> enemy.blueprint ?: hybrids[i % hybrids.size] }
        println("[video] Fighting " + names.zip(fight.enemies).joinToString { (name, enemy) -> "$name (${enemy.shape})" })
        val specs = names.map { VideoDirector.enemySpec(game, it, "The $it") }
        game.debugFightShapes(specs.first(), fight.enemies.map { it.shape })
        // (after the fight's set up, which clears the wingmen's specs)
        game.debugWingmanSpecs = specs.drop(1)
        game.debugSpawnWingmen()

        val bypass = game.blueprintManager.getOrNull(AugmentBlueprint.ZOLTAN_BYPASS) as? AugmentBlueprint
        for (enemy in game.getOpponentShips(player)) {
            if (fight.enemyWeaponSize > 1)
                arm(game, enemy, fight.enemyWeaponSize)
            // Every enemy can board through the Chonkestrel's Zoltan shields
            if (bypass != null && !enemy.hasAugment(bypass.name))
                enemy.augments.add(bypass)
            // and their cloaks take a moment to get ready
            enemy.cloaking?.let { it.ionTimer = maxOf(it.ionTimer, CLOAK_DELAY) }
        }
    }

    /**
     * Bring the Chonkestrel's crew home from the last fight's enemy ships,
     * and stop any teleport that's on its way, as the reconstructive
     * teleport does when it jumps away: they're all back, healed.
     */
    private fun bringBoardersHome(game: InGameState) {
        val player = game.player
        val home = player.teleporter?.room ?: player.rooms.first()
        for (ship in game.getOpponentShips(player) + player) {
            for (member in ship.crew.filterIsInstance<LivingCrew>().toList()) {
                if (member.ownerShip == player)
                    member.teleportInstantlyTo(if (ship == player) member.room else home)
            }
        }
    }

    /**
     * Put the Chonkestrel's crew at their stations as the fight starts, so
     * none of them stand about: one at each system that can be manned (the
     * piloting, engines, shields, weapons, sensors and doors, the engines
     * by the Lanius first), a boarding party in the teleporter, if it has
     * one, and the rest in the medbay, ready for the enemies' boarders (but
     * the Lanius, who wait by the engines, see [LANIUS]). They're put
     * straight there, as there isn't time to walk in a fight this short, and
     * those are saved as their stations, to go back to after repairs.
     */
    private fun stationCrew(player: Ship) {
        val crew = player.crew.filterIsInstance<LivingCrew>().filter {
            it.ownerShip == player && it.room.ship == player && it.mode == AbstractCrew.SlotType.CREW && it.health > 0f
        }.toMutableList()
        fun isLanius(member: LivingCrew) = member.info.race.name == LANIUS

        // Put [member] in a free place in [room], the [point] if it's given
        fun place(member: LivingCrew, room: Room, point: IPoint? = null): Boolean {
            val at = point ?: room.firstFreeSlot(AbstractCrew.SlotType.CREW) ?: return false
            if (!room.isSlotFree(at, AbstractCrew.SlotType.CREW, member))
                return false
            if (member.room != room || member.standingPosition?.posEq(at) != true)
                member.jumpTo(room, at)
            crew.remove(member)
            return true
        }

        // The piloting first, so the ship can dodge, then the engines (for
        // the Lanius), then the rest
        val manned = player.systems.filter { it.room != null && it.configuration.computerPoint != null }
            .sortedBy { if (it == player.piloting) 0 else if (it is Engines) 1 else 2 }
        for (system in manned) {
            val room = system.room!!
            val computer = system.configuration.computerPoint!!
            val candidates = if (system is Engines) crew.sortedBy { if (isLanius(it)) 0 else 1 }
            else crew.sortedBy { if (isLanius(it)) 1 else 0 }
            candidates.firstOrNull { place(it, room, computer) }
        }

        // The Lanius left over wait by the engines
        val engineRooms = player.engineSystems.mapNotNull { it.room }
        for (member in crew.filter(::isLanius)) {
            engineRooms.firstOrNull { place(member, it) }
        }

        // A boarding party in the teleporter, the best fighters first
        val teleporterRoom = player.teleporter?.room
        if (teleporterRoom != null) {
            val party = crew.sortedBy { BOARDERS.indexOf(it.info.race.name).let { i -> if (i < 0) BOARDERS.size else i } }
                .take(BOARDING_PARTY)
            for (member in party)
                place(member, teleporterRoom)
        }

        // Everyone else waits in the medbays
        val medbays = player.medbaySystems.mapNotNull { it.room }
        for (member in crew.toList()) {
            medbays.firstOrNull { place(member, it) }
        }

        player.saveCrewPositions()
    }

    /**
     * Give the Chonkestrel what it has by the next fight (see [Loadout]).
     */
    private fun grow(game: InGameState, loadout: Loadout) {
        val player = game.player
        val blueprints = game.blueprintManager

        for (race in loadout.crew) {
            if (player.crew.size >= player.type.maxCrew)
                break
            val blueprint = blueprints.getOrNull(race) as? CrewBlueprint ?: continue
            player.addCrewMember(LivingCrewInfo.generateRandom(blueprint, game), false)
        }

        for (name in loadout.systems) {
            val slot = player.systemSlots.firstOrNull { it.spec.systemName == name && it.room.system == null } ?: continue
            val isSubSystem = slot.system.info?.isSubSystem == true
            if (isSubSystem && player.subSystems.size >= player.maxSubSystems ||
                !isSubSystem && player.mainSystems.size >= player.maxMainSystems
            ) continue
            slot.room.setSystem(slot)
        }
        for (name in loadout.systems) {
            if (player.systems.none { it.blueprint.name == name })
                println("[video] No room for the $name system")
        }

        for (system in player.systems) {
            val level = loadout.levels[system.blueprint.name]
                ?: loadout.levels[ALL]?.takeIf { system !is Cloaking } ?: continue
            system.energyLevels = maxOf(system.energyLevels, level.coerceAtMost(system.blueprint.maxPower))
        }
        // The reactor powers everything at once
        player.purchasedReactorPower = maxOf(player.purchasedReactorPower, player.mainSystems.sumOf { it.energyLevels })

        loadout.weapons?.let { setWeapons(game, player, it) }

        if (loadout.drones.isNotEmpty()) {
            val slots = player.droneSystems.flatMap { system -> system.drones.indices.map { system to it } }
            for ((i, name) in loadout.drones.withIndex()) {
                val (system, slot) = slots.getOrNull(i) ?: break
                val drone = blueprints.getOrNull(name) as? DroneBlueprint ?: continue
                if (system.drones[slot]?.type != drone)
                    system.drones[slot] = Drones.DroneInfo(drone, null)
            }
        }

        if (loadout.zoltanShields > 0)
            ScreenshotDirector.giveZoltanShields(game, loadout.zoltanShields)
        for (name in loadout.augments) {
            val augment = blueprints.getOrNull(name) as? AugmentBlueprint ?: continue
            if (!player.hasAugment(name) && player.augments.size < player.maxAugments)
                player.augments.add(augment)
        }

        player.missilesCount = maxOf(player.missilesCount, 40)
        // (enough for every copy of every drone, see DroneSizes)
        player.dronesCount = maxOf(player.dronesCount, 60)

        // Power the systems up (the weapons and drones are powered by the
        // battle driver)
        for (system in player.mainSystems) {
            while (true) {
                val before = system.powerSelected
                system.increasePower()
                if (system.powerSelected == before)
                    break
            }
        }
        player.cargoUpdated()
    }

    /**
     * Put [weapons] in the Chonkestrel's slots, each in the biggest size
     * up to the one asked for that its weapons system has the power for.
     */
    internal fun setWeapons(game: InGameState, player: Ship, weapons: List<Pair<String, Int>?>) {
        for ((slot, hardpoint) in player.hardpoints.withIndex()) {
            val weapon = hardpoint.weapon ?: continue
            if (weapon.isPowered)
                player.weaponsFor(slot)?.setWeaponPower(weapon, false)
            hardpoint.weapon = null
        }
        for (system in player.weaponSystems) {
            var power = 0
            for (index in system.hardpointIndices) {
                if (index >= player.hardpoints.size)
                    break
                val (name, size) = weapons.getOrNull(index) ?: continue
                val single = game.blueprintManager.getOrNull(name) as? AbstractWeaponBlueprint
                if (single == null) {
                    println("[video] No weapon $name")
                    continue
                }
                val weapon = (size downTo 1).asSequence()
                    .mapNotNull { if (it == 1) single else WeaponSizes.combined(game.blueprintManager, single, it) }
                    .firstOrNull { power + it.power <= system.energyLevels }
                if (weapon == null) {
                    println("[video] No power left for $name in slot ${index + 1}")
                    continue
                }
                power += weapon.power
                player.hardpoints[index].weapon = weapon.buildInstance(player)
            }
        }
    }

    /**
     * Swap each of an enemy [ship]'s weapons for its combined version, as
     * big as [size] (or smaller, if its weapons system can't have the power
     * for that), upgrading its weapons system and reactor for them.
     */
    internal fun arm(game: InGameState, ship: Ship, size: Int) {
        for (system in ship.weaponSystems) {
            val indices = system.hardpointIndices.filter { it < ship.hardpoints.size }
            for (index in indices) {
                val single = ship.hardpoints[index].weapon?.type ?: continue
                if (single.isCombined)
                    continue
                val othersPower = indices.filter { it != index }.sumOf { ship.hardpoints[it].weapon?.type?.power ?: 0 }
                val combined = (size downTo 2).asSequence()
                    .mapNotNull { WeaponSizes.combined(game.blueprintManager, single, it) }
                    .firstOrNull { othersPower + it.power <= system.blueprint.maxPower } ?: continue

                for (other in indices) {
                    val weapon = ship.hardpoints[other].weapon ?: continue
                    if (weapon.isPowered)
                        system.setWeaponPower(weapon, false)
                }
                val needed = othersPower + combined.power
                if (needed > system.energyLevels) {
                    ship.purchasedReactorPower += needed - system.energyLevels
                    system.energyLevels = needed
                }
                ship.hardpoints[index].weapon = combined.buildInstance(ship)
            }
        }
        ship.cargoUpdated()
    }

    // Charge the [ship]'s powered weapons most of the way (see START_CHARGE)
    internal fun charge(ship: Ship) {
        for (hardpoint in ship.hardpoints) {
            val weapon = hardpoint.weapon ?: continue
            if (!weapon.isPowered)
                continue
            val fraction = START_CHARGE.start + Random.nextFloat() * (START_CHARGE.endInclusive - START_CHARGE.start)
            weapon.timeCharged = weapon.type.chargeTime * fraction
        }
    }

    private fun weaponsOf(ship: Ship): String = ship.hardpoints.mapNotNull { it.weapon?.type }.joinToString {
        val base = it.combinedOf.firstOrNull() ?: it.name
        if (it.isCombined) "$base (x${it.combinedCount})" else base
    }
}
