package xyz.znix.xftl.devutil

import org.jdom2.Element
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Ship
import xyz.znix.xftl.XftlDebug
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.LivingCrewInfo
import xyz.znix.xftl.devutil.ScreenshotDirector.Scenario
import xyz.znix.xftl.drones.ShieldDrone
import xyz.znix.xftl.game.BossManager
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.StoreData
import xyz.znix.xftl.game.StoreWindow
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.sector.Mirage
import xyz.znix.xftl.systems.Clonebay
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.systems.SelectedTarget
import xyz.znix.xftl.systems.SystemBlueprint
import xyz.znix.xftl.weapons.AbstractProjectile
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.Damage
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.IRoomTargetingWeapon
import xyz.znix.xftl.weapons.MissileBlueprint
import xyz.znix.xftl.weapons.ShadowTargets
import xyz.znix.xftl.weapons.WeaponSizes
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Screenshots that show the reported bugs marked as fixed in the bug tracker
 * (and the changelog's fixes) are fixed (XFTL_SCREENSHOTS_SET=bugs),
 * one or two for each, with a caption saying what to look at, and the
 * game's numbers that show it as it's taken. The same bugs are also
 * proved by tests (see src/test/java/xyz/znix/xftl/proofs/).
 *
 * The first ones are taken in the debug game the screenshots start with (the
 * Chonkestrel, with everything), each putting the ship right first; the
 * last ones in new games without debug mode, as a player has them.
 */
object BugProofs {
    fun scenarios(): List<Scenario> {
        // XFTL_BUG_PROOFS_ONLY, a comma-separated list of parts of names,
        // takes only those, to try them out
        val only = System.getenv("XFTL_BUG_PROOFS_ONLY")?.split(',')?.filter { it.isNotBlank() }
        return allScenarios()
            .filter { scenario -> only == null || only.any { it in scenario.name } }
            .onEach { it.closeWindows = it.name !in WINDOWED }
    }

    // The scenarios whose windows are what they show: the maps, the stores,
    // and the event for escaping a hazard
    private val WINDOWED = setOf(
        "nebulas-gathered-into-clouds-on-the-map", "exit-in-a-nebula-cloud-is-a-nebula",
        "fleet-advance-under-the-beacons-labels", "last-stand-fleet-takes-two-beacons-a-jump",
        "mirage-scouted-shows-the-beacons-outside-it", "waiting-out-of-fuel-in-asteroids-gets-away",
        "drones-system-free-drone-text-fits-the-store-box", "drones-systems-free-drone-isnt-one-the-ship-has",
    )

    private fun allScenarios(): List<Scenario> = listOf(
        // "shields critical warns you if any shields are fully down": with
        // one of the two shields systems broken, there's no warning...
        Scenario("shields-critical-not-shown-with-one-of-two-shields-systems-broken", caption = { game ->
            "Shields critical: only one of the two shields systems is broken, " +
                    "${game.player.activeShieldLayers} layers still up, so no warning (under the scrap)"
        }) { game ->
            calm(game)
            val shields = game.player.shieldSystems.first()
            shields.damagedEnergyLevels = shields.energyLevels
        },
        // ...and with both broken, there is
        Scenario("shields-critical-shown-with-every-shields-system-broken", caption = { game ->
            "Shields critical: every shields system is broken (${game.player.activeShieldLayers} layers), " +
                    "so the warning's shown, under the scrap, clear of the Zoltan shield's panel"
        }) { game ->
            calm(game)
            for (shields in game.player.shieldSystems)
                shields.damagedEnergyLevels = shields.energyLevels
        },

        // "Иконки систем мигают синим (активные, ионизированные)": ionised
        // systems' icons (in their rooms and the power bar), and the active
        // cloak's
        Scenario("ionised-and-active-system-icons-flash-blue", settleSeconds = 1.2f, caption = {
            "System icons: the ionised weapons, engines and oxygen, and the active cloaking, " +
                    "flash blue in their rooms and in the power bar"
        }) { game ->
            calm(game)
            fightRandomShip(game)
            for (system in listOfNotNull(game.player.weaponSystems.firstOrNull(), game.player.engines, game.player.oxygen))
                system.dealDamage(0, 3)
            game.player.cloaking?.activateCloak()
        },

        // "Замок вокруг энергии при активной резервной батарее"
        Scenario("backup-battery-on-with-its-padlock", caption = {
            "Backup battery on: its power is locked, with the padlock round it in the reactor bar"
        }) { game ->
            calm(game)
            game.player.backupBattery?.startBattery() ?: println("[bugs] No backup battery to turn on")
        },

        // "Частицы огнетушителя": the crew in a room on fire putting it out
        Scenario("crew-putting-out-a-fire-with-extinguisher-spray", settleSeconds = 3f, caption = {
            "Extinguishers: the crew in the burning room put the fire out, spraying"
        }) { game ->
            calm(game)
            val room = game.player.rooms.firstOrNull { room ->
                room.system != null && room.crew.any { it is LivingCrew && it.ownerShip == game.player }
            } ?: error("No crewed room to set on fire")
            repeat(room.cellCount) { room.spawnFire() }
        },

        // "Свечение включённых дронов (абордажная, анти-персонал, ремонтная)":
        // the drones' lights on, outside and inside, in a fight
        Scenario("drones-lights-on-in-a-fight", settleSeconds = 3f, fight = true, caption = {
            "Drone lights: the anti-personnel and repair drones inside, and the drones outside, " +
                    "have their lights on while they're powered"
        }) { game ->
            calm(game)
            val system = game.player.droneSystems.first()
            for ((slot, name) in listOf("BATTLE", "REPAIR").withIndex()) {
                if (slot < system.drones.size)
                    system.drones[slot] = Drones.DroneInfo(game.blueprintManager[name] as DroneBlueprint, null)
            }
            game.player.dronesCount = 50
            game.player.cargoUpdated()
            fightRandomShip(game)
        },

        // "x3 halberd beam sometimes changes sizes [IF THERE IS ONE SHIP IT
        // HAS A SMALLER RANGE]": a triple beam, at its full length, against
        // one ship
        Scenario(
            "triple-beam-at-full-length-against-one-ship", settleSeconds = 1.1f, fight = true,
            prepare = { game ->
                for (hardpoint in game.player.hardpoints) {
                    val weapon = hardpoint.weapon ?: continue
                    weapon.timeCharged = weapon.chargeTime
                }
            },
            caption = {
                "x3 beam against one ship: the full length of three beams " +
                        "(${WeaponSizes.beamLength(BEAM_LENGTH, 3)}px for a ${BEAM_LENGTH}px beam), " +
                        "not cut short for the missing ships"
            }
        ) { game ->
            calm(game)
            val beam = game.blueprintManager["XFTL_X3_BEAM_1"] as AbstractWeaponBlueprint
            for (hardpoint in game.player.hardpoints)
                hardpoint.weapon = null
            game.player.hardpoints[0].weapon = beam.buildInstance(game.player)
            game.player.cargoUpdated()
            fightRandomShip(game)
        },

        // "2x beam did not do multi ship if there was 3 ship total": a twin
        // beam swept from the right-hand ship through the middle one
        twinBeamThroughThreeShips(),

        // "nebula spawns are random and not clustered": a nebula sector's
        // map, its nebulas gathered into clouds
        Scenario("nebulas-gathered-into-clouds-on-the-map", caption = { game ->
            val beacons = game.currentBeacon.sector.beacons
            val nebulas = beacons.filter { it.isOwnNebula }
            val inClouds = nebulas.count { nebula -> nebula.neighbours.any { it.isOwnNebula } }
            "Nebulas in clouds: $inClouds of the ${nebulas.size} nebula beacons are next to another nebula"
        }) { game ->
            calm(game)
            ScreenshotDirector.enterSector(game, "NEBULA_SECTOR", GameMap.SectorClass.NEBULA)
            game.shipUI.debugShowJumpMap()
        },

        // "Выход из сектора в туманности (FINISH_BEACON_NEBULA)"
        Scenario("exit-in-a-nebula-cloud-is-a-nebula", caption = { game ->
            val exit = game.currentBeacon.sector.finishBeacon
            "Exit in a nebula cloud: ${exit.neighbours.count { it.isOwnNebula }} of its ${exit.neighbours.size} " +
                    "neighbours are nebulas, so the exit's one too (its event: ${exit.event.debugId})"
        }) { game ->
            calm(game)
            enterNebulaExitSector(game)
            game.shipUI.debugShowJumpMap()
        },

        // "Зона флота под подписями маяков": the fleet's advance, halfway
        // across the map, under the beacons' labels
        Scenario("fleet-advance-under-the-beacons-labels", caption = {
            "Fleet's advance: the red zone covers the whole map up to it, " +
                    "and is drawn under the beacons and their labels"
        }) { game ->
            calm(game)
            ScreenshotDirector.enterSector(game, "CIVILIAN_SECTOR", GameMap.SectorClass.CIVILIAN)
            val sector = game.currentBeacon.sector
            for (beacon in sector.beacons.filter { !it.isExit }.shuffled().take(6))
                beacon.hasStore = true
            sector.dangerZoneCentre.x = FLEET_HALFWAY
            game.shipUI.debugShowJumpMap()
        },

        // "Последний сектор: флот захватывает 2 маяка за прыжок"
        lastStandFleet(),

        // "Mercenary to "scout the sector" event - should reduce the mirage?"
        mirageScouted(),

        // "Can upgrade? (опасность поверх миража не блокировала окно корабля)"
        Scenario(
            "mirage-with-asteroids-closes-the-ship-window",
            prepare = { XftlDebug.recordingMouse = SHIP_BUTTON },
            caption = { game ->
                "A mirage with asteroids: in danger (${game.isInDanger}), " +
                        "so the ship button's off, and upgrading can't be opened"
            }
        ) { game ->
            calm(game)
            ScreenshotDirector.setEnvironments(game, listOf(Beacon.EnvironmentType.MIRAGE, Beacon.EnvironmentType.ASTEROID))
        },

        // "Корабли флота на фоне при захвате маяка во время ожидания": out
        // of fuel, waiting at a beacon the fleet takes
        Scenario("fleet-in-the-background-waiting-out-of-fuel", settleSeconds = 2f, caption = {
            "Waiting out of fuel at a beacon the fleet's taken: its ships are in the background"
        }) { game ->
            calm(game)
            game.player.fuelCount = 0
            game.currentBeacon.isOvertaken = true
            game.shipUI.autoWaitOutOfFuel()
        },

        // "События побега из опасной среды при ожидании без топлива"
        Scenario("waiting-out-of-fuel-in-asteroids-gets-away", caption = { game ->
            "Waiting out of fuel in an asteroid field: the escape event (${game.currentBeacon.event.debugId}), " +
                    "and the asteroids are gone (in danger: ${game.isInDanger})"
        }) { game ->
            calm(game)
            ScreenshotDirector.setEnvironments(game, listOf(Beacon.EnvironmentType.NORMAL, Beacon.EnvironmentType.ASTEROID))
            game.currentBeacon.sector.dangerZoneCentre.x = -100_000
            game.player.fuelCount = 0
            game.shipUI.autoWaitOutOfFuel()
        },

        // "when out of fuel you have a makeshift weapon pre igniter"
        waitingLosesTheWeaponsCharge(),

        // "when out of fuel drone recovery arm will not work on defence
        // drones", and the Drone Recovery Arm
        waitingRecoversTheDefenceDrone(),

        // "Shield overcharger of a beaten enemy keeps charging, so the
        // teleported crew can't be recalled after the battle"
        boardersComeHomeFromABeatenShip(),

        // "Repair Drone - стоял в комнате пока там закончилась драка"
        repairDroneKeepsOutOfTheFight(),

        // "Enemy having healing burst with no teleporter"
        healingBurstHeldForTheirOwnCrew(),

        // "mantis uses laser projectile in crew fight"
        mantisPunches(),

        // "missiles do not give visual feedback when piercing shields", and
        // "missiles evently hit shield barriers (pierce too low)"
        missileThroughEveryLayer(),

        // "when ion runs out on shields the power will not round down"
        Scenario("ionised-shields-keep-whole-layers", caption = { game ->
            val shields = game.player.shieldSystems.first()
            "Ionised shields: ${shields.powerSelected} bars in the shields (whole layers only, ionised: ${shields.isIonised}), " +
                    "the odd bar back in the reactor"
        }) { game ->
            calm(game)
            val shields = game.player.shieldSystems.first()
            shields.dealDamage(0, 1)
        },

        // "enemy ships do not have enough power to power all of there systems"
        Scenario("enemies-power-all-their-systems", fight = true, settleSeconds = 2f, caption = { game ->
            game.allEnemyShips.joinToString("\n") { enemy ->
                val needed = enemy.mainSystems.sumOf { it.energyLevels }
                "${enemy.name}: reactor ${enemy.purchasedReactorPower}, its systems need $needed, " +
                        "powered ${enemy.mainSystems.sumOf { it.powerSelected }}"
            }
        }) { game ->
            calm(game)
            ScreenshotDirector.fightRandomShips(game, 2)
        },

        // "when event has someone try to escape its only first ship that
        // attempt to"
        Scenario("every-hurt-enemy-ship-runs", settleSeconds = 4f, caption = { game ->
            "Ships running: " + game.allEnemyShips.joinToString("; ") { enemy ->
                "${enemy.name} ${enemy.health}/${enemy.maxHealth} hull, " +
                        (enemy.escapeTimer?.let { "FTL in %.1fs".format(it) } ?: "not running")
            }
        }) { game ->
            calm(game)
            game.debugFlags.noEnemyFire.set = true
            val specs = VideoDirector.STRONG_SHIPS.shuffled().take(3).map { (blueprint, _) -> runningSpec(game, blueprint) }
            game.debugRemoveEnemy()
            game.debugWingmanSpecs = specs.drop(1)
            game.debugSpawnShip(specs.first(), Difficulty.NORMAL, game.currentBeacon.sector.difficultySectorNumber, 1)
            game.updateGameState(0.01f)
            for (enemy in game.allEnemyShips) {
                enemy.escapeHealth = enemy.maxHealth
                enemy.health = enemy.maxHealth / 3
            }
        },

        // "in ion storms AI will not prioritize weapons"
        Scenario("ion-storm-enemy-powers-weapons-first", fight = true, settleSeconds = 2f, caption = { game ->
            game.allEnemyShips.joinToString("\n") { enemy ->
                "Ion storm, ${enemy.name}: reactor ${enemy.reactorPower}; " +
                        "weapons ${enemy.weaponSystems.sumOf { it.powerSelected }}/${enemy.weaponSystems.sumOf { it.energyLevels }}, " +
                        "engines ${enemy.engineSystems.sumOf { it.powerSelected }}/${enemy.engineSystems.sumOf { it.energyLevels }}"
            }
        }) { game ->
            calm(game)
            ScreenshotDirector.setEnvironments(game, listOf(Beacon.EnvironmentType.ION_STORM))
            fightRandomShip(game)
        },

        // "overcharger drone is always glowing when on"
        overchargerIdleWhenFull(),

        // "hacking engines or pilot does not revive dodge chance"
        hackingTakesTheEvasion("engines") { it.engineSystems.lastOrNull()?.room },
        hackingTakesTheEvasion("piloting") { it.piloting?.room },

        // "Хакинг снимает майнд-контроль, Zoltan Shield Bypass для майнд-контроля"
        hackingBreaksMindControl(),
        bypassLetsMindControlThrough(),

        // "all ships will try to extract the same unit with teleporter"
        eachShipTakesItsOwnBoarders(),

        // "Оглушение от оружия: Stun Bomb, Ion Stun Blast, шанс оглушения"
        Scenario("weapons-stun-the-crew", caption = { game ->
            val stunned = game.player.crew.filterIsInstance<LivingCrew>().filter { it.stunTime > 0f }
            "Stun: a Stun Bomb's hit; ${stunned.size} crew stunned " +
                    "(${stunned.joinToString { "%s %.0fs".format(it.info.name, it.stunTime) }})"
        }) { game ->
            calm(game)
            val room = crewedRoom(game)
            val stun = game.blueprintManager["BOMB_STUN"] as AbstractWeaponBlueprint
            game.player.damage(room, Damage().apply { stunDuration = stun.stun.toFloat() })
        },

        // "Reverse Ion Field защищает суперщит"
        Scenario("reverse-ion-field-keeps-the-super-shield", caption = { game ->
            "Reverse Ion Field: an ion shot at the Zoltan shield took nothing, " +
                    "super shield ${game.player.superShield}/${game.player.maxSuperShield}"
        }) { game ->
            calm(game)
            addAugment(game, "ION_ARMOR")
            game.player.superShield = game.player.maxSuperShield
            game.player.attackShields(Damage().apply { ionDamage = 1 }, null)
        },

        // "Флагман без экипажа переходит на автоматику (BOSS_AUTOMATED)"
        flagshipOnAutomated(),

        // "Лимит абордажей сохраняется в сейв"
        boardingLimitSaved(),

        // The augments: "12 аугментаций не работают"
        titaniumCasing(),
        rockPlating(),
        crystalVengeance(),
        ftlBooster(),
        ftlJammer(),
        repairArm(),
        droneReactorBooster(),
        explosiveReplicator(),

        // "clone bay does not save crew on fleeing or destroyed ships"
        clonebaySavesBoarders(),

        // "Купил Zoltan Shield он появился только после прыжка": a new game
        // without debug mode, a Zoltan shield bought (added as the store
        // does), its super shield up straight away
        Scenario("zoltan-shield-up-as-soon-as-its-bought", settleSeconds = 1.5f, caption = { game ->
            "Zoltan Shield bought, no jump since: super shield ${game.player.superShield} up straight away"
        }) { game ->
            val newGame = newGame(game, game.blueprintManager.defaultPlayerShip)
            val shield = newGame.blueprintManager[AugZoltanShield.NAME] as AugmentBlueprint
            check(newGame.player.addBlueprint(shield, false)) { "No room for the Zoltan shield" }
        },

        // "Drone Control purchased drone text overflow": a store selling the
        // drones system to a ship without one (the Kestrel), with the free
        // drone with the longest name, hovered
        Scenario(
            "drones-system-free-drone-text-fits-the-store-box", settleSeconds = 1.5f,
            prepare = ::hoverDronesSystem,
            caption = { game ->
                "Free drone with the drones system: the longest name " +
                        "(${game.currentBeacon.getStore(game)?.freeDrone?.name}) wraps inside the box"
            }
        ) { game ->
            val newGame = newGame(game, "PLAYER_SHIP_HARD")
            val store = dronesStore(newGame)
            store.freeDrone = newGame.blueprintManager.blueprints.values.filterIsInstance<DroneBlueprint>()
                .filter { (it.rarity ?: 0) in 1..2 }
                .maxBy { newGame.translator[it.title!!].length }
            newGame.shipUI.updateButtons()
            newGame.shipUI.debugShowStore()
        },

        // "Drone Control with Anti-Personnel 3 times in a row": a ship with
        // an Anti-Personnel Drone, whose stores' drones systems come with
        // something else
        freeDroneIsNotOneTheShipHas(),
    )

    /**
     * A ship that, as in some events, runs once it's lost half its hull:
     * each of them, not just the first.
     */
    private fun runningSpec(game: InGameState, blueprint: String): xyz.znix.xftl.shipgen.EnemyShipSpec {
        val elem = Element("ship")
            .setAttribute("name", "XFTL_PROOF_RUNNING_$blueprint")
            .setAttribute("auto_blueprint", blueprint)
        elem.addContent(
            Element("escape").setAttribute("min", "100").setAttribute("max", "100")
                .setAttribute("timer", "20")
                .addContent(Element("text").setText("The ships are powering their FTL drives!"))
        )
        return xyz.znix.xftl.shipgen.EnemyShipSpec(elem, game.blueprintManager, game.eventManager)
    }

    // How many hits or shots the augments that work by chance are tried with
    private const val TRIES = 100

    private fun chance(game: InGameState, augment: String): String =
        "${(100 * game.player.getAugmentValue(augment)).roundToInt()}%"

    // The beam the x3 beam's made of's length (see WeaponSizes.beamLength)
    private const val BEAM_LENGTH = 60

    // The fleet's position, halfway across the map (see Sector.dangerZoneCentre)
    private const val FLEET_HALFWAY = 150

    // The middle of the ship button, at the top (see PlayerShipUI.updateButtons)
    private val SHIP_BUTTON = ConstPoint(531 + 101 + 30, 29 + 20)

    private fun twinBeamThroughThreeShips(): Scenario {
        var copies = ""
        var crossed = ""
        return Scenario(
            "twin-beam-copy-goes-into-the-ship-its-swept-through", settleSeconds = 1.6f,
            prepare = { game ->
                val (left, middle, right) = game.hostileShipsLeftToRight
                val beam = game.player.hardpoints[0].weapon as BeamBlueprint.BeamInstance
                // From the right-hand ship's right edge, sweeping left, at a
                // height where the line crosses rooms in it and the middle
                // one (the copy goes into the ship the line crosses)
                val startX = right.rooms.maxOf { it.x + it.width } * 35 - 5
                val aim = (0 until right.rooms.maxOf { it.y + it.height } * 35 step 5).firstNotNullOfOrNull { y ->
                    val aim = SelectedTarget.BeamAim(beam, 0, right, ConstPoint(startX, y))
                    aim.angle = PI.toFloat()
                    aim.length = WeaponSizes.beamLength(aim.length, 2)
                    aim.updateHitRooms()
                    aim.takeIf {
                        it.hitRooms.isNotEmpty() && ShadowTargets.beamAimIn(game, it, middle).hitRooms.isNotEmpty()
                    }
                } ?: error("No line through the right-hand and middle ships")
                crossed = listOf("left" to left, "middle" to middle, "right" to right)
                    .filter { (_, ship) -> ship == right || ShadowTargets.beamAimIn(game, aim, ship).hitRooms.isNotEmpty() }
                    .joinToString { it.first }
                beam.timeCharged = beam.chargeTime
                beam.fire(aim)
                copies = "left ${left.inboundBeams.size}, middle ${middle.inboundBeams.size}, right ${right.inboundBeams.size}"
            },
            caption = {
                "Twin beam against three ships, swept left from the right-hand one, its line crossing rooms in: " +
                        "$crossed. Beams in each: $copies - the copy's in the first ship the line crosses, not just the leftmost"
            }
        ) { game ->
            calm(game)
            game.debugFlags.noEnemyFire.set = true
            ScreenshotDirector.fightRandomShips(game, 3)
            val beam = game.blueprintManager[WeaponSizes.nameFor("BEAM_1", 2)] as AbstractWeaponBlueprint
            for (hardpoint in game.player.hardpoints)
                hardpoint.weapon = null
            game.player.hardpoints[0].weapon = beam.buildInstance(game.player)
            game.player.cargoUpdated()
        }
    }

    private fun lastStandFleet(): Scenario {
        var taken = ""
        return Scenario("last-stand-fleet-takes-two-beacons-a-jump", caption = {
            "The last stand: the fleet took $taken beacons over two jumps (two a jump)"
        }) { game ->
            calm(game)
            val info = game.gameMap.sectors.last().single()
            val sector = game.gameMap.generateSector(info, game)
            game.currentBeacon = sector.startBeacon
            game.shipUI.debugCloseWindow()
            val counts = ArrayList<Int>()
            counts += sector.beacons.count { it.isOvertaken }
            repeat(2) {
                game.advanceFleet()
                counts += sector.beacons.count { it.isOvertaken }
            }
            taken = counts.joinToString(" -> ")
            game.shipUI.debugShowJumpMap()
        }
    }

    private fun mirageScouted(): Scenario {
        var before = 0
        return Scenario("mirage-scouted-shows-the-beacons-outside-it", caption = { game ->
            val current = game.currentBeacon
            val lying = current.sector.beacons.count { it != current && Mirage.isDeceptive(it, current) }
            "In a mirage, after the mercenaries scouted the sector: $lying beacons lie, " +
                    "$before before (only those in a mirage themselves still do)"
        }) { game ->
            calm(game)
            ScreenshotDirector.enterSector(game, "XFTL_MIRAGE_EXPANSE", GameMap.SectorClass.NEBULA)
            ScreenshotDirector.setEnvironments(game, listOf(Beacon.EnvironmentType.MIRAGE))
            val current = game.currentBeacon
            before = current.sector.beacons.count { it != current && Mirage.isDeceptive(it, current) }
            current.sector.mirageScouted = true
            game.shipUI.debugShowJumpMap()
        }
    }

    private fun waitingLosesTheWeaponsCharge(): Scenario {
        var before = ""
        var after = ""
        return Scenario(
            "waiting-out-of-fuel-loses-the-weapons-charge", settleSeconds = 0.3f, prepareAt = 0.05f,
            caption = {
                "Waiting out of fuel, without a Weapon Pre-Igniter: no free one, " +
                        "the weapons' charge was $before, after waiting $after"
            }
        ) { game ->
            calm(game)
            game.player.augments.removeAll { it.name == "WEAPON_PREIGNITE" }
            game.player.cargoUpdated()
            game.currentBeacon.sector.dangerZoneCentre.x = -100_000
            val weapons = game.player.hardpoints.mapNotNull { it.weapon }
            for (weapon in weapons)
                weapon.timeCharged = weapon.chargeTime * 0.6f
            before = weapons.joinToString { "${(100 * it.timeCharged / it.chargeTime).roundToInt()}%" }
            game.player.fuelCount = 0
            game.shipUI.autoWaitOutOfFuel()
            after = weapons.joinToString { "${(100 * it.timeCharged / it.chargeTime).roundToInt()}%" }
        }
    }

    private fun waitingRecoversTheDefenceDrone(): Scenario {
        var before = 0
        return Scenario("waiting-out-of-fuel-recovers-the-defence-drone", caption = { game ->
            "Waiting out of fuel with the Drone Recovery Arm: the Defense Drone left outside is recovered, " +
                    "drone parts $before -> ${game.player.dronesCount}"
        }) { game ->
            calm(game)
            game.currentBeacon.sector.dangerZoneCentre.x = -100_000
            addAugment(game, "DRONE_RECOVERY")
            val system = game.player.droneSystems.first()
            for (slot in system.drones.indices)
                system.setDronePower(slot, false)
            system.drones[0] = Drones.DroneInfo(game.blueprintManager["DEFENSE_1"] as DroneBlueprint, null)
            game.player.dronesCount = 10
            check(system.setDronePower(0, true)) { "The defence drone didn't launch" }
            before = game.player.dronesCount
            game.player.fuelCount = 0
            game.shipUI.autoWaitOutOfFuel()
        }
    }

    private fun boardersComeHomeFromABeatenShip(): Scenario {
        var boarders: List<LivingCrew> = emptyList()
        var enemy: Ship? = null
        return Scenario(
            "beaten-enemys-super-shield-lets-the-boarders-home", settleSeconds = 6f, prepareAt = 3f,
            prepare = { game ->
                val ship = enemy!!
                ship.superShield = ship.maxSuperShield
                game.debugBeatEnemy()
                val room = boarders.first().room
                game.player.teleporter!!.selectTeleportAction(false, room)
            },
            caption = { game ->
                val ship = enemy!!
                "Beaten enemy, its super shield still up (${ship.superShield}): " +
                        "our boarders came home through it, ${boarders.count { it.room.ship == game.player }} " +
                        "of ${boarders.size} back aboard"
            }
        ) { game ->
            calm(game)
            fight(game, "ZOLTAN_BOMBER")
            val ship = game.enemy!!
            enemy = ship
            val room = ship.rooms.first { it.system == null }
            boarders = idleCrew(game).take(2)
            for (crew in boarders)
                crew.teleportAnimatedTo(room)
            game.debugFlags.noEnemyFire.set = true
        }
    }

    private fun repairDroneKeepsOutOfTheFight(): Scenario {
        var fightRoom: Room? = null
        var otherRoom: Room? = null
        var drone: xyz.znix.xftl.drones.AbstractIndoorsDrone? = null
        return Scenario("repair-drone-keeps-out-of-the-boarders-room", settleSeconds = 10f, caption = {
            val boarded = fightRoom!!.system!!
            val other = otherRoom!!.system!!
            "Repair drone: a boarder's in the broken ${boarded.info?.name} (damaged ${boarded.damagedEnergyLevels}), " +
                    "so it fixed the ${other.info?.name} instead (damaged ${other.damagedEnergyLevels}); " +
                    "the drone's in the ${drone?.pawn?.room?.system?.info?.name ?: "corridor"}"
        }) { game ->
            calm(game)
            // (the crew stay where they are, rather than fighting the boarder)
            game.debugFlags.noCrewAI.set = true
            val system = game.player.droneSystems.first()
            for (slot in system.drones.indices)
                system.setDronePower(slot, false)
            system.drones[0] = Drones.DroneInfo(game.blueprintManager["REPAIR"] as DroneBlueprint, null)
            game.player.dronesCount = 10
            check(system.setDronePower(0, true)) { "The repair drone didn't start" }
            drone = system.drones[0]!!.instance as xyz.znix.xftl.drones.AbstractIndoorsDrone

            val empty = game.player.rooms.filter { room ->
                room.system != null && room.system !is Drones && room.crew.none { it is LivingCrew }
            }
            fightRoom = empty[0]
            otherRoom = empty.last()
            for (room in listOf(fightRoom!!, otherRoom!!))
                room.system!!.damagedEnergyLevels = 1
            addCrew(game, game.player, fightRoom!!, "human", intruder = true)
        }
    }

    private fun healingBurstHeldForTheirOwnCrew(): Scenario {
        var ours: LivingCrew? = null
        return Scenario("enemy-holds-its-healing-burst-for-its-own-crew", settleSeconds = 8f, caption = { game ->
            val bomb = game.enemy!!.hardpoints.firstNotNullOf { it.weapon }
            "Enemy with only a Healing Burst, charged (${bomb.isCharged}), and only our crew hurt: " +
                    "it holds it, our ${ours!!.info.name} still ${ours!!.health.roundToInt()}/${ours!!.maxHealth.roundToInt()}"
        }) { game ->
            calm(game)
            fight(game, "REBEL_FAT")
            val enemy = game.enemy!!
            enemy.hardpoints.forEach { it.weapon = null }
            enemy.addBlueprint(game.blueprintManager["BOMB_HEAL"] as Blueprint, true)
            enemy.cargoUpdated()
            enemy.hardpoints.firstNotNullOf { it.weapon }.let { it.timeCharged = it.chargeTime * 0.9f }
            val crew = idleCrew(game).first()
            crew.health = crew.maxHealth / 2
            ours = crew
        }
    }

    private fun mantisPunches(): Scenario {
        var mantis: LivingCrew? = null
        var human: LivingCrew? = null
        var mantisShot = false
        var humanShot = false
        val shotField = AbstractCrew::class.java.getDeclaredField("shot").also { it.isAccessible = true }
        return Scenario("mantis-punch-rather-than-shoot", settleSeconds = 2.5f, caption = {
            mantisShot = mantisShot || shotField.get(mantis) != null
            humanShot = humanShot || shotField.get(human) != null
            "Crew fight: the human shoots the boarder (shot: $humanShot), " +
                    "the Mantis punches it (shot: $mantisShot)"
        }) { game ->
            calm(game)
            mantisShot = false
            humanShot = false
            val room = game.player.rooms.filter { it.system == null }.maxBy { it.cellCount }
            // (two of the crew make way for them, so it's not over capacity)
            for (crew in idleCrew(game).take(2))
                game.player.crew.remove(crew)
            game.updatePlayerCrew()
            mantis = addCrew(game, game.player, room, "mantis")
            human = addCrew(game, game.player, room, "human")
            addCrew(game, game.player, room, "rock", intruder = true)
        }
    }

    private fun missileThroughEveryLayer(): Scenario {
        var hull = 0
        var flashed = false
        val flashField = Ship::class.java.getDeclaredField("shieldFlash").also { it.isAccessible = true }
        return Scenario("missile-through-every-shield-layer-with-a-flash", settleSeconds = 4f, caption = { game ->
            flashed = flashed || flashField.getFloat(game.player) > 0f
            "A missile (piercing 5) against our ${game.player.activeShieldLayers} layers in two shields systems: " +
                    "it went through, the shields flashed ($flashed), hull $hull -> ${game.player.health}"
        }) { game ->
            calm(game)
            flashed = false
            game.player.superShield = 0
            // (the defence drones would shoot it down)
            for (system in game.player.droneSystems)
                for (slot in system.drones.indices)
                    system.setDronePower(slot, false)
            fight(game, "REBEL_FAT")
            val enemy = game.enemy!!
            enemy.hardpoints.forEach { it.weapon = null }
            enemy.addBlueprint(game.blueprintManager["MISSILES_2_PLAYER"] as Blueprint, true)
            enemy.cargoUpdated()
            val weapon = enemy.hardpoints.firstNotNullOf { it.weapon }
            enemy.missilesCount = 10
            weapon.timeCharged = weapon.chargeTime
            for (engines in game.player.engineSystems)
                engines.damagedEnergyLevels = engines.energyLevels
            hull = game.player.health
            val room = game.player.rooms.first { it.system == null }
            (weapon as IRoomTargetingWeapon).fire { room }
        }
    }

    private fun overchargerIdleWhenFull(): Scenario {
        return Scenario("full-overcharger-doesnt-glow", settleSeconds = 4f, caption = { game ->
            "Shield Overcharger, the super shield full (${game.player.superShield}): " +
                    "it's waiting to put back a point, so it doesn't glow"
        }) { game ->
            calm(game)
            fightRandomShip(game)
            game.debugFlags.noEnemyFire.set = true
            val system = game.player.droneSystems.first()
            for (slot in system.drones.indices)
                system.setDronePower(slot, false)
            system.drones[0] = Drones.DroneInfo(game.blueprintManager["DRONE_SHIELD_PLAYER"] as DroneBlueprint, null)
            game.player.dronesCount = 10
            check(system.setDronePower(0, true)) { "The overcharger didn't launch" }
            game.player.superShield = game.player.maxSuperShield
        }
    }

    private fun hackingTakesTheEvasion(what: String, target: (Ship) -> Room?): Scenario {
        var before = 0
        var pulsed = false
        return Scenario(
            "hacking-$what-takes-all-the-evasion", settleSeconds = 6.4f, prepareAt = 6f,
            prepare = { game ->
                before = maxOf(before, game.enemy!!.evasion)
                game.player.hacking!!.startHackingPulse()
                pulsed = true
            },
            caption = { game ->
                val enemy = game.enemy!!
                if (!pulsed)
                    before = maxOf(before, enemy.evasion)
                "Hacking the enemy's $what (pulse on: ${target(enemy)?.system?.isHackActive}): " +
                        "its evasion $before% -> ${enemy.evasion}%"
            }
        ) { game ->
            calm(game)
            before = 0
            pulsed = false
            game.player.superShield = 0
            game.debugFlags.noEnemyFire.set = true
            fight(game, "ANAEROBIC_BOMBER")
            val enemy = game.enemy!!
            for (engines in enemy.engineSystems)
                repeat(engines.energyLevels) { engines.increasePower() }
            game.player.dronesCount = 50
            powerUp(game.player.hacking!!)
            game.player.hacking!!.selectTarget(target(enemy) ?: error("No $what to hack"))
        }
    }

    private fun hackingBreaksMindControl(): Scenario {
        var controlled: LivingCrew? = null
        var wasControlled = false
        return Scenario(
            "hacking-breaks-the-enemys-mind-control", settleSeconds = 6.6f, prepareAt = 6f,
            prepare = { game ->
                val mind = game.enemy!!.mindControl!!
                powerUp(mind)
                mind.controlledCrew?.let { controlled = it }
                mind.debugControl(controlled!!)
                wasControlled = controlled!!.mindControlledBy == mind
                game.player.hacking!!.startHackingPulse()
            },
            caption = { game ->
                "The enemy mind controlled our ${controlled!!.info.name} ($wasControlled), then a hacking pulse " +
                        "on its mind control (on: ${game.enemy!!.mindControl!!.isHackActive}): they're " +
                        "${if (controlled!!.mindControlledBy == null) "free" else "still controlled"} " +
                        "(the enemy controls ${game.enemy!!.mindControl!!.controlledCrew?.info?.name ?: "no one"})"
            }
        ) { game ->
            calm(game)
            // (our super shield would keep the mind control out)
            game.player.superShield = 0
            game.debugFlags.noEnemyFire.set = true
            fight(game, "ANAEROBIC_BOMBER")
            val enemy = game.enemy!!
            if (enemy.mindControl == null)
                install(game, enemy, "mind")
            check(enemy.mindControl != null) { "The enemy has no mind control" }
            enemy.mindControl!!.energyLevels = enemy.mindControl!!.blueprint.maxPower
            game.player.dronesCount = 50
            powerUp(game.player.hacking!!)
            game.player.hacking!!.selectTarget(enemy.mindControl!!.room!!)
            controlled = idleCrew(game).first()
        }
    }

    private fun bypassLetsMindControlThrough(): Scenario {
        var target: LivingCrew? = null
        return Scenario(
            "zoltan-bypass-lets-mind-control-through", settleSeconds = 1.5f,
            prepare = { game ->
                game.enemy!!.superShield = game.enemy!!.maxSuperShield
                game.player.mindControl!!.selectRoom(target!!.room)
            },
            caption = { game ->
                "Zoltan Shield Bypass: mind control through the enemy's super shield (${game.enemy!!.superShield}), " +
                        "controlling their ${game.player.mindControl!!.controlledCrew?.info?.name ?: "no one"}"
            }
        ) { game ->
            calm(game)
            game.debugFlags.noEnemyFire.set = true
            fight(game, "ZOLTAN_BOMBER")
            addAugment(game, "ZOLTAN_BYPASS")
            target = game.enemy!!.crew.filterIsInstance<LivingCrew>().first { it.ownerShip == game.enemy }
        }
    }

    private fun eachShipTakesItsOwnBoarders(): Scenario {
        var mains: LivingCrew? = null
        var wingmans: LivingCrew? = null
        return Scenario("each-ship-takes-home-its-own-hurt-boarder", settleSeconds = 12f, caption = { game ->
            val main = mains!!.ownerShip!!
            val wingman = wingmans!!.ownerShip!!
            "Two ships' hurt boarders: the ${main.name}'s teleporter is out, so its boarder's still on " +
                    "${if (mains!!.room.ship == game.player) "our ship" else "theirs"}; the ${wingman.name} " +
                    "took its own home (on ${if (wingmans!!.room.ship == game.player) "our ship" else "theirs"})"
        }) { game ->
            calm(game)
            game.debugFlags.noEnemyFire.set = true
            // Two ships with teleporters (which they don't always get), that
            // take their boarders home (not the invaders, who never do)
            for (attempt in 0 until 40) {
                val specs = VideoDirector.STRONG_SHIPS.shuffled().take(2)
                    .map { (blueprint, description) -> VideoDirector.enemySpec(game, blueprint, description) }
                game.debugRemoveEnemy()
                game.debugWingmanSpecs = specs.drop(1)
                game.debugSpawnShip(specs.first(), Difficulty.NORMAL, game.currentBeacon.sector.difficultySectorNumber, attempt)
                // (the wingmen are spawned as the game's updated)
                repeat(3) { game.updateGameState(0.01f) }
                if (game.allEnemyShips.size == 2 && game.allEnemyShips.all {
                        it.teleporter != null && it.type.boardingStrategy != xyz.znix.xftl.ai.FriendlyCrewAI.BoardingStrategy.INVASION
                    })
                    break
            }
            val main = game.enemy!!
            val wingman = game.wingmen.first().ship
            check(main.teleporter != null && wingman.teleporter != null) { "No two ships with teleporters" }
            // (our super shield would keep their teleporters out)
            game.player.superShield = 0
            main.teleporter?.ionTimer = 1000f
            val rooms = game.player.rooms.filter { it.system == null }
            mains = addCrew(game, game.player, rooms[0], "human", intruder = true, owner = main)
            wingmans = addCrew(game, game.player, rooms[1], "human", intruder = true, owner = wingman)
            for (crew in listOf(mains!!, wingmans!!))
                crew.health = crew.maxHealth / 5
            game.debugFlags.noCrewAI.set = false
        }
    }

    private fun flagshipOnAutomated(): Scenario = Scenario("flagship-without-its-crew-fights-on-automated", settleSeconds = 2f, caption = { game ->
        val enemy = game.enemy!!
        "The flagship with no crew left (${enemy.crew.count { it is LivingCrew && it.ownerShip == enemy }}): " +
                "automated ${enemy.automated}, still there ${game.isShipPresent(enemy)}, still fighting ${game.isInDanger}"
    }) { game ->
        calm(game)
        fight(game, "REBEL_FAT")
        val enemy = game.enemy!!
        Ship::class.java.getDeclaredField("boss").also { it.isAccessible = true }.set(enemy, StubBoss)
        enemy.crew.removeAll { it is LivingCrew }
    }

    private fun boardingLimitSaved(): Scenario {
        var loaded = 0
        return Scenario("boarding-limit-kept-in-the-save", caption = {
            "Boarding limit: the enemy's sent 3 boarding parties; saved and loaded, its AI has still sent $loaded"
        }) { game ->
            calm(game)
            game.debugFlags.noEnemyFire.set = true
            fight(game, "MANTIS_BOMBER")
            val enemy = game.enemy!!
            repeat(3) { enemy.crewAI.onCrewTeleportedOut() }
            val elem = Element("ai")
            enemy.crewAI.saveToXML(elem)
            val reloaded = xyz.znix.xftl.ai.FriendlyCrewAI(enemy)
            reloaded.loadFromXML(elem)
            val again = Element("ai")
            reloaded.saveToXML(again)
            loaded = xyz.znix.xftl.savegame.SaveUtil.getOptionalTagInt(again, "aiTeleportCount") ?: 0
        }
    }

    private fun titaniumCasing(): Scenario {
        var kept = 0
        return Scenario("titanium-casing-keeps-hits-off-the-systems", caption = { game ->
            "Titanium System Casing: $kept of $TRIES 1-damage hits on the engines left them undamaged " +
                    "(its chance: ${chance(game, "SYSTEM_CASING")}); the hull took every one"
        }) { game ->
            calm(game)
            addAugment(game, "SYSTEM_CASING")
            val engines = game.player.engines!!
            kept = (0 until TRIES).count {
                engines.damagedEnergyLevels = 0
                game.player.damage(engines.room!!, Damage.hullAndSys(1))
                engines.damagedEnergyLevels == 0
            }
            game.player.health = game.player.maxHealth
        }
    }

    private fun rockPlating(): Scenario {
        var kept = 0
        return Scenario("rock-plating-keeps-hits-off-the-hull", caption = { game ->
            "Rock Plating: $kept of $TRIES 1-damage hits on the engines left the hull whole " +
                    "(its chance: ${chance(game, "ROCK_ARMOR")}); the engines took every one"
        }) { game ->
            calm(game)
            addAugment(game, "ROCK_ARMOR")
            val engines = game.player.engines!!
            kept = (0 until TRIES).count {
                game.player.health = game.player.maxHealth
                game.player.damage(engines.room!!, Damage.hullAndSys(1))
                game.player.health == game.player.maxHealth
            }
            engines.damagedEnergyLevels = 0
        }
    }

    private fun crystalVengeance(): Scenario {
        var shards = 0
        return Scenario("crystal-vengeance-fires-shards-back", settleSeconds = 0.5f, caption = { game ->
            "Crystal Vengeance: $TRIES hits on our hull broke off $shards crystal shards at the enemy " +
                    "(its chance: ${chance(game, "CRYSTAL_SHARDS")}), flying at it"
        }) { game ->
            calm(game)
            game.debugFlags.noEnemyFire.set = true
            fight(game, "REBEL_FAT")
            addAugment(game, "CRYSTAL_SHARDS")
            val room = game.player.rooms.first { it.system == null }
            val before = game.player.projectiles.size
            repeat(TRIES) {
                game.player.health = game.player.maxHealth
                game.player.damage(room, Damage.hullOnly(1))
            }
            shards = game.player.projectiles.count { it is MissileBlueprint.MissileProjectile } - before
            game.player.health = game.player.maxHealth
        }
    }

    private fun ftlBooster(): Scenario {
        var plain = 0f
        var boosted = 0f
        return Scenario("ftl-booster-charges-faster", settleSeconds = 2f, caption = { game ->
            "FTL Recharge Booster: in a second of the fight, the FTL drive charged %.2f%% without it, %.2f%% with it (x%.2f)"
                .format(100 * plain, 100 * boosted, boosted / plain)
        }) { game ->
            calm(game)
            game.debugFlags.noEnemyFire.set = true
            fight(game, "REBEL_FAT")
            game.player.augments.removeAll { it.name == "FTL_BOOSTER" }
            // (someone has to fly the ship, which an earlier scenario's crew
            // may have left)
            val helm = game.player.piloting!!.room!!
            if (game.player.friendlyCrew.none { it.room == helm }) {
                val pilot = idleCrew(game).first()
                pilot.jumpTo(xyz.znix.xftl.math.RoomPoint(helm, game.player.piloting!!.configuration.computerPoint ?: ConstPoint(0, 0)))
            }
            game.player.update(0.01f)
            fun charged(): Float {
                game.player.ftlChargeProgress = 0f
                game.player.update(1f)
                return game.player.ftlChargeProgress
            }
            plain = charged()
            addAugment(game, "FTL_BOOSTER")
            boosted = charged()
            check(plain > 0f) {
                val player = game.player
                "The FTL drive isn't charging: engines running ${player.areEnginesRunning}, " +
                        "piloting ${player.piloting?.undamagedEnergy}, pilot ${player.friendlyCrew.any { it.room == player.piloting?.room }}, " +
                        "hacked ${player.piloting?.isHackActive}"
            }
            game.player.ftlChargeProgress = 0.3f
        }
    }

    private var startTime = 0L

    private fun ftlJammer(): Scenario = Scenario("ftl-jammer-slows-the-enemys-escape", settleSeconds = 4f, caption = { game ->
        val elapsed = (System.nanoTime() - startTime) / 1e9f
        "FTL Jammer: the enemy's FTL went from 20s to %.1fs in %.1fs (about half speed)".format(game.enemy!!.escapeTimer ?: 0f, elapsed)
    }) { game ->
        calm(game)
        game.debugFlags.noEnemyFire.set = true
        fight(game, "REBEL_FAT")
        addAugment(game, "FTL_JAMMER")
        game.enemy!!.escapeTimer = 20f
        startTime = System.nanoTime()
    }

    private fun repairArm(): Scenario {
        var plain = ""
        var armed = ""
        return Scenario("repair-arm-turns-scrap-into-hull", caption = {
            "Repair Arm: the same scrap reward, without it: $plain; with it: $armed (less scrap, hull mended)"
        }) { game ->
            calm(game)
            game.player.health = game.player.maxHealth - 10
            val event = game.eventManager.eventNames.mapNotNull { game.eventManager[it] as? Event }.first { event ->
                event.resolveResources(game, Random(0)).let { it.scrap in 15..40 && it.damage.isEmpty() }
            }
            fun describe(): String {
                val resources = event.resolveResources(game, Random(0))
                return "${resources.scrap} scrap" + resources.damage.joinToString("") { ", hull +${-it.amount}" }
            }
            plain = describe()
            addAugment(game, "REPAIR_ARM")
            armed = describe()
        }
    }

    private fun droneReactorBooster(): Scenario = Scenario("drone-reactor-booster-speeds-up-the-drones", settleSeconds = 2f, caption = { game ->
        val drone = game.player.droneSystems.first().drones[0]?.instance as? xyz.znix.xftl.drones.AbstractIndoorsDrone
        val pawn = drone?.pawn
        "Drone Reactor Booster: the repair drone walks at %.0f, rather than %.0f".format(pawn?.movementSpeed, pawn?.droneSpeed)
    }) { game ->
        calm(game)
        addAugment(game, "DRONE_SPEED")
        val system = game.player.droneSystems.first()
        for (slot in system.drones.indices)
            system.setDronePower(slot, false)
        system.drones[0] = Drones.DroneInfo(game.blueprintManager["REPAIR"] as DroneBlueprint, null)
        game.player.dronesCount = 10
        system.setDronePower(0, true)
        game.player.engines!!.damagedEnergyLevels = 1
    }

    private fun explosiveReplicator(): Scenario {
        var used = 0
        return Scenario("explosive-replicator-saves-missiles", caption = { game ->
            "Explosive Replicator: $TRIES missile launches used $used missiles " +
                    "(its chance of using none: ${chance(game, "EXPLOSIVE_REPLICATOR")})"
        }) { game ->
            calm(game)
            game.debugFlags.noEnemyFire.set = true
            fight(game, "REBEL_FAT")
            addAugment(game, "EXPLOSIVE_REPLICATOR")
            val missile = game.blueprintManager["MISSILES_2_PLAYER"] as AbstractWeaponBlueprint
            val room = game.enemy!!.rooms.first()
            game.debugFlags.infiniteMissiles.set = false
            game.player.missilesCount = 1000
            repeat(TRIES) {
                val weapon = missile.buildInstance(game.player)
                weapon.timeCharged = weapon.chargeTime
                (weapon as IRoomTargetingWeapon).fire { room }
            }
            used = 1000 - game.player.missilesCount
            game.player.missilesCount = 50
        }
    }

    private fun clonebaySavesBoarders(): Scenario {
        var boarders: List<LivingCrew> = emptyList()
        return Scenario(
            "clonebay-saves-the-boarders-on-a-destroyed-ship", settleSeconds = 5f, prepareAt = 2.5f,
            prepare = { game -> game.enemy?.health = 0 },
            caption = { game ->
                val clonebay = game.player.clonebay
                "Clonebay: our boarders' ship was destroyed under them; " +
                        "${boarders.count { clonebay != null && it in clonebay.queue }} of ${boarders.size} waiting to be cloned"
            }
        ) { game ->
            calm(game)
            game.debugFlags.noEnemyFire.set = true
            install(game, game.player, "clonebay")
            check(game.player.clonebay != null) { "No clonebay" }
            game.player.clonebay!!.energyLevels = game.player.clonebay!!.blueprint.maxPower
            fight(game, "REBEL_FAT")
            val room = game.enemy!!.rooms.first { it.system == null }
            boarders = idleCrew(game).take(2)
            for (crew in boarders)
                crew.teleportAnimatedTo(room)
        }
    }

    private fun freeDroneIsNotOneTheShipHas(): Scenario {
        var offered = emptySet<String>()
        return Scenario(
            "drones-systems-free-drone-isnt-one-the-ship-has", settleSeconds = 1.5f,
            prepare = ::hoverDronesSystem,
            caption = { game ->
                "Free drone with the drones system, for a ship with an Anti-Personnel Drone: " +
                        "${game.currentBeacon.getStore(game)?.freeDrone?.name} here; " +
                        "in 100 stores: ${offered.joinToString()} (never BATTLE)"
            }
        ) { game ->
            val newGame = newGame(game, "PLAYER_SHIP_HARD")
            newGame.player.addBlueprint(newGame.blueprintManager["BATTLE"] as Blueprint, true)
            val free = HashSet<String>()
            for (seed in 0 until 100) {
                val store = StoreData()
                store.generateRandomContents(newGame, Random(seed))
                if (store.systems.any { it?.info == Drones.INFO })
                    store.freeDrone?.let { free += it.name }
            }
            offered = free
            val store = dronesStore(newGame)
            store.freeDrone = null
            for (seed in 0 until 100) {
                val generated = StoreData()
                generated.generateRandomContents(newGame, Random(seed))
                if (generated.freeDrone != null && generated.systems.any { it?.info == Drones.INFO }) {
                    store.freeDrone = generated.freeDrone
                    break
                }
            }
            newGame.shipUI.updateButtons()
            newGame.shipUI.debugShowStore()
        }
    }

    // A store at the current beacon, selling the drones system first, with
    // scrap to spare
    private fun dronesStore(game: InGameState): StoreData {
        val beacon = game.currentBeacon
        beacon.hasStore = true
        val store = beacon.getStore(game) ?: error("No store")
        val drones = game.blueprintManager.blueprints.values.filterIsInstance<SystemBlueprint>()
            .first { it.info == Drones.INFO }
        store.sections.remove(StoreData.Section.SYSTEMS)
        store.sections.add(0, StoreData.Section.SYSTEMS)
        store.systems.clear()
        store.systems += drones
        game.player.scrap = 300
        return store
    }

    // Hover the drones system's buy button, to show its info panel
    private fun hoverDronesSystem(game: InGameState) {
        val window = game.shipUI.openWindow as? StoreWindow ?: error("The store isn't open")
        // (the repair buttons have no blueprint, and say so)
        val button = window.buttons.filterIsInstance<StoreWindow.BuyButton>().firstOrNull {
            (runCatching { it.blueprint }.getOrNull() as? SystemBlueprint)?.info == Drones.INFO
        } ?: error("No drones system for sale")
        XftlDebug.recordingMouse = button.pos + ConstPoint(button.size.x / 2, button.size.y / 2)
    }

    /**
     * Move to a nebula sector whose exit is in a nebula cloud (most of its
     * neighbours nebulas), trying new ones until one is.
     */
    private fun enterNebulaExitSector(game: InGameState) {
        val map = game.gameMap
        val type = map.sectorType("NEBULA_SECTOR") ?: error("No nebula sector")
        repeat(50) {
            val info = map.SectorInfo(GameMap.SECTOR_COUNT / 2 + 1, 0, type, GameMap.SectorClass.NEBULA)
            val sector = map.generateSector(info, game)
            if (sector.finishBeacon.event.debugId == "FINISH_BEACON_NEBULA") {
                game.currentBeacon = sector.startBeacon
                game.shipUI.debugCloseWindow()
                return
            }
        }
        error("No nebula sector with its exit in a cloud")
    }

    // A boss that does nothing, to make the enemy ship a flagship
    private object StubBoss : BossManager {
        override val serialisationType: String get() = "proof"
        override val beacon: Beacon? get() = null
        override val nextBeacon: Beacon? get() = null
        override val jumping: Boolean get() = false
        override val useBossShipUI: Boolean get() = false
        override fun createShip(): Ship = error("not used")
        override fun drawMapIcon(g: Graphics, centre: IPoint) {}
        override fun drawJumpArc(g: Graphics, from: IPoint, to: IPoint) {}
        override fun advanceFleet() {}
        override fun bossShipKilled(enemy: Ship) {}
        override fun saveToXML(elem: Element, refs: ObjectRefs) {}
        override fun loadFromXML(elem: Element, refs: RefLoader) {}
    }

    /**
     * Put the ship right for the next scenario: no enemy, no windows, its
     * systems repaired, no fires or breaches, no boarders, and the debug
     * flags the scenarios use off.
     */
    private fun calm(game: InGameState) {
        game.debugRemoveEnemy()
        game.shipUI.debugCloseWindow()
        game.debugFlags.noEnemyFire.set = false
        game.debugFlags.noCrewAI.set = false
        game.currentBeacon.isOvertaken = false
        // (an overcharger from an earlier scenario would keep the super
        // shield up, which keeps out missiles, mind control and teleporters)
        for (system in game.player.droneSystems) {
            for (slot in system.drones.indices) {
                if (system.drones[slot]?.type?.name == "DRONE_SHIELD_PLAYER") {
                    system.setDronePower(slot, false)
                    system.drones[slot] = null
                }
            }
        }
        game.player.crew.removeAll { it is LivingCrew && it.ownerShip != game.player }
        for (system in game.player.systems) {
            system.damagedEnergyLevels = 0
            system.ionTimer = 0f
        }
        for (room in game.player.rooms) {
            room.fires.indices.forEach { room.fires[it] = null }
            room.breaches.indices.forEach { room.breaches[it] = null }
            room.oxygen = 1f
        }
        for (crew in game.player.crew.filterIsInstance<LivingCrew>())
            crew.health = crew.maxHealth
    }

    private fun fightRandomShip(game: InGameState) = ScreenshotDirector.fightRandomShips(game, 1)

    // Fight one ship, of the given blueprint
    private fun fight(game: InGameState, blueprint: String) {
        val spec = VideoDirector.enemySpec(game, blueprint, "The enemy")
        game.debugRemoveEnemy()
        game.debugWingmanSpecs = emptyList()
        game.debugSpawnShip(spec, Difficulty.NORMAL, game.currentBeacon.sector.difficultySectorNumber, 1)
    }

    // Install a system on [ship], as a store does (in place of the medbay,
    // for the clonebay)
    private fun install(game: InGameState, ship: Ship, name: String) {
        val blueprint = game.blueprintManager[name] as SystemBlueprint
        val slot = ship.findFreeSystemSlot(blueprint) ?: error("No place for $name on ${ship.name}")
        slot.room.setSystem(slot)
    }

    // Power [system] up as far as it goes
    private fun powerUp(system: xyz.znix.xftl.systems.MainSystem) {
        repeat(system.energyLevels) { system.increasePower() }
    }

    private fun addAugment(game: InGameState, name: String) {
        val augment = game.blueprintManager[name] as AugmentBlueprint
        if (augment !in game.player.augments)
            game.player.augments += augment
        game.player.cargoUpdated()
    }

    // The player's crew not at a station, to send places
    private fun idleCrew(game: InGameState): List<LivingCrew> =
        game.player.crew.filterIsInstance<LivingCrew>()
            .filter { it.ownerShip == game.player && it.room.ship == game.player }
            .sortedBy { it.room.system != null }

    // A room of the player's with crew in it
    private fun crewedRoom(game: InGameState): Room = game.player.rooms.first { room ->
        room.crew.count { it is LivingCrew && it.ownerShip == game.player } >= 2
    }

    // Add a crew member of [race] to [ship], in [room], as its crew or a
    // boarder (owned by [owner])
    private fun addCrew(
        game: InGameState, ship: Ship, room: Room, race: String,
        intruder: Boolean = false, owner: Ship? = null
    ): LivingCrew {
        val info = LivingCrewInfo.generateRandom(game.blueprintManager[race] as CrewBlueprint, game)
        val crew = ship.addCrewMember(info, false, intruder, room)
        if (intruder)
            crew.ownerShip = owner
        ship.updateCrewReservedSlots()
        game.updatePlayerCrew()
        return crew
    }

    /**
     * Start a new game without debug mode, with [ship], as a player would,
     * and close its start event.
     */
    private fun newGame(game: InGameState, ship: String): InGameState {
        XftlDebug.enabled = false
        XftlDebug.randomize = false
        XftlDebug.zoltan = false
        game.mainGame.startNewGame(ship, Difficulty.NORMAL, null)
        val newGame = game.mainGame.currentState as InGameState
        newGame.debugRemoveEnemy()
        newGame.shipUI.debugCloseWindow()
        return newGame
    }
}
