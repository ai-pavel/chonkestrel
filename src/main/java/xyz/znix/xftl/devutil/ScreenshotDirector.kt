package xyz.znix.xftl.devutil

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import xyz.znix.xftl.Ship
import xyz.znix.xftl.ShipShape
import xyz.znix.xftl.XftlDebug
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.systems.Cloaking
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.game.MainMenuState
import xyz.znix.xftl.game.ShipWindow
import xyz.znix.xftl.game.StoreData
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.LWJGLGameContainer
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO

/**
 * The screenshot generator, for the project's site: it plays through a list
 * of [Scenario]s, setting each one up, waiting for it to settle, and saving a
 * screenshot of it, then quits. Turn it on with XFTL_SCREENSHOTS (see
 * [XftlDebug]), which also starts a game in debug mode with the default ship.
 *
 * It runs after each frame's rendered (see [LWJGLGameContainer.afterRender]),
 * so it can read the frame back before it's shown. A scenario that fails is
 * logged and skipped, so the rest still get taken.
 */
class ScreenshotDirector(
    private val main: MainGame,
    private val outputDir: File,
    val scenarios: List<Scenario> = defaultScenarios(),
    private val videoFile: File? = null,
) {
    /**
     * One screenshot: [setup] puts the game in the state to show, and the
     * screenshot is taken [settleSeconds] later (and at least
     * [MIN_SETTLE_FRAMES] frames later), so the animations and UI can settle.
     * With [fight], the player fights the enemy ships meanwhile (see
     * [VideoDirector.BattleDriver]), and the enemies board it. [prepare],
     * if there is one, runs [prepareAt] seconds after the setup (by default
     * [PREPARE_TIME], once the weapons have been powered). [caption], if
     * there is one, is drawn over it, saying what it shows (worked out
     * again each frame, so it has the game's numbers as it's taken). With
     * [closeWindows], any window that pops up after [prepare] (an event's,
     * or the crew over capacity) is closed, so it doesn't hide what's shown.
     *
     * A scenario with [menu] is taken in the main menu instead, which it
     * goes to first, set up by [menu] rather than [setup]. The next scenario
     * without one starts a new game with the default ship, as the
     * screenshots started with.
     */
    class Scenario(
        val name: String,
        val settleSeconds: Float = 1.5f,
        val fight: Boolean = false,
        val prepare: ((InGameState) -> Unit)? = null,
        val menu: ((MainMenuState) -> Unit)? = null,
        val prepareAt: Float = PREPARE_TIME,
        val caption: ((InGameState) -> String)? = null,
        val setup: (InGameState) -> Unit = {}
    ) {
        var closeWindows: Boolean = false
    }

    private var index = 0
    private var started = false
    private var setupTime = 0L
    private var framesSinceSetup = 0
    private var finished = false

    // Recording the whole run into a video, if there's a videoFile: every
    // frame is then exactly 1/FPS seconds of game time (see install)
    private var encoder: VideoDirector.Encoder? = null
    private var driver: VideoDirector.BattleDriver? = null
    private var boarded = false
    private var prepared = false

    fun afterRender(framebufferWidth: Int, framebufferHeight: Int) {
        if (finished)
            return
        val state = main.currentState
        if (state !is InGameState && state !is MainMenuState)
            return
        val game = state as? InGameState

        if (videoFile != null) {
            val encoder = encoder ?: VideoDirector.Encoder.start(
                videoFile, framebufferWidth, framebufferHeight, VideoDirector.FPS
            ).also {
                encoder = it
                println("[screenshots] Recording a video to ${videoFile.absolutePath}")
            }
            try {
                encoder.writeFrame(framebufferWidth, framebufferHeight)
            } catch (ex: Exception) {
                println("[screenshots] Couldn't record a frame:")
                ex.printStackTrace()
            }
        }

        if (index >= scenarios.size) {
            finished = true
            encoder?.finish()
            println("[screenshots] Done, saved to ${outputDir.absolutePath}")
            main.quitGame()
            return
        }

        val scenario = scenarios[index]
        if (!started) {
            // Go to the main menu for a scenario taken there, or back to a
            // game for the rest, and set it up on the next frame
            if (scenario.menu != null && state !is MainMenuState) {
                main.switchToMainMenu()
                return
            }
            if (scenario.menu == null && game == null) {
                main.startDefaultNewGame()
                return
            }

            started = true
            framesSinceSetup = 0
            XftlDebug.recordingMouse = null
            XftlDebug.recordingCaption = null
            setupTime = System.nanoTime()
            driver = null
            boarded = false
            prepared = false
            try {
                if (scenario.menu != null)
                    scenario.menu.invoke(state as MainMenuState)
                else
                    scenario.setup(game!!)
                if (scenario.fight && game != null) {
                    driver = VideoDirector.BattleDriver(game).apply {
                        startBattle()
                        dronesOn = true
                        hackingOn = true
                        mindControlOn = true
                    }
                }
            } catch (ex: Exception) {
                println("[screenshots] Skipping '${scenario.name}', its setup failed:")
                ex.printStackTrace()
                next()
            }
            return
        }

        framesSinceSetup++
        val elapsed = when (videoFile) {
            null -> (System.nanoTime() - setupTime) / 1_000_000_000f
            else -> framesSinceSetup.toFloat() / VideoDirector.FPS
        }

        driver?.let { driver ->
            try {
                if (!boarded && elapsed >= BOARDING_TIME) {
                    boarded = true
                    driver.enemiesBoard(randomRooms = true)
                }
                // Keep the enemies from hiding behind their cloaks, so
                // they're seen: their cloaking's always cooling down
                for (enemy in driver.enemies()) {
                    for (cloaking in enemy.systems.filterIsInstance<Cloaking>()) {
                        cloaking.timeRemaining = null
                        cloaking.ionTimer = maxOf(cloaking.ionTimer, Cloaking.COOLDOWN)
                    }
                }
                driver.update(elapsed)
            } catch (ex: Exception) {
                println("[screenshots] Fighting in '${scenario.name}' failed:")
                ex.printStackTrace()
                this.driver = null
            }
        }

        if (!prepared && elapsed >= scenario.prepareAt) {
            prepared = true
            try {
                game?.let { scenario.prepare?.invoke(it) }
            } catch (ex: Exception) {
                println("[screenshots] Preparing '${scenario.name}' failed:")
                ex.printStackTrace()
            }
        }

        if (game != null && scenario.closeWindows && prepared)
            game.shipUI.debugCloseWindow()

        // (drawn on the next frame, so it's a frame behind the game)
        if (game != null && scenario.caption != null) {
            XftlDebug.recordingCaption = try {
                scenario.caption.invoke(game)
            } catch (ex: Exception) {
                "Caption failed: $ex"
            }
        }

        if (framesSinceSetup < MIN_SETTLE_FRAMES || elapsed < scenario.settleSeconds)
            return

        val file = File(outputDir, fileName(index, scenario.name))
        try {
            capture(framebufferWidth, framebufferHeight, file)
            println("[screenshots] Saved $file")
        } catch (ex: Exception) {
            println("[screenshots] Couldn't save '${scenario.name}':")
            ex.printStackTrace()
        }
        next()
    }

    private fun next() {
        index++
        started = false
    }

    companion object {
        /**
         * Read the frame that's just been rendered (which hasn't been shown
         * yet, so it's in the back buffer), and save it as a PNG (or a JPEG,
         * if the file's name ends in .jpg).
         */
        fun capture(width: Int, height: Int, file: File) {
            val pixels = BufferUtils.createByteBuffer(width * height * 4)
            GL11.glReadBuffer(GL11.GL_BACK)
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)

            file.parentFile?.mkdirs()
            ImageIO.write(toImage(pixels, width, height), if (file.name.endsWith(".jpg")) "jpg" else "png", file)
        }

        /**
         * When a scenario's [Scenario.prepare] runs, in seconds after it's
         * set up.
         */
        const val PREPARE_TIME = 0.5f

        /**
         * When the site's fights are taken, in seconds after they're set
         * up: with the weapons charged as they start (see [armedFight]),
         * they're firing by then.
         */
        private const val FIGHT_SECONDS = 2.5f

        /**
         * The hostile act 2 sector the screenshots of act 2 are taken in: one
         * of the hybrid sectors (see doc/game/sectors.md).
         */
        private const val ACT_TWO_SECTOR = "XFTL_HYBRID_REBEL_MANTIS"

        /**
         * The fewest frames to wait after setting up a scenario.
         */
        const val MIN_SETTLE_FRAMES = 10

        /**
         * When the enemy ships board the player in a scenario with a fight,
         * in seconds after it's set up.
         */
        const val BOARDING_TIME = 1f

        // The scrap the default ship gets for the store's screenshot, so it
        // can afford most of what's for sale
        private const val STORE_SCRAP = 300

        /**
         * Start the screenshot generator, if XFTL_SCREENSHOTS is set. This is
         * called once the game has started.
         */
        @JvmStatic
        fun install(main: MainGame, container: GameContainer) {
            val dir = XftlDebug.screenshotDir ?: return
            val lwjgl = container as? LWJGLGameContainer ?: return

            val outputDir = File(dir)
            outputDir.mkdirs()
            println("[screenshots] Saving screenshots to ${outputDir.absolutePath}")

            val scenarios = when (XftlDebug.screenshotSet) {
                "shape" -> shipScenarios()
                "boss" -> bossScenarios()
                "hybrid" -> hybridScenarios()
                "mixed" -> mixedScenarios()
                "bugs" -> BugProofs.scenarios()
                else -> defaultScenarios()
            }
            // The bigger ships' screenshots are recorded into a video too,
            // the whole run from the first to the last
            // with the enemy crew always in sight, and every environment's
            // warnings lit up
            val video = when (XftlDebug.screenshotSet) {
                "shape", "hybrid", "mixed" -> File(outputDir, "${XftlDebug.screenshotSet}.mp4")
                else -> null
            }
            if (XftlDebug.screenshotSet == "boss" || XftlDebug.screenshotSet == "bugs")
                XftlDebug.showEnemyCrew = true
            if (video != null) {
                XftlDebug.showEnemyCrew = true
                XftlDebug.showAllHazardWarnings = true
            }
            val director = ScreenshotDirector(main, outputDir, scenarios, video)
            if (video != null)
                lwjgl.fixedDeltaSec = 1f / VideoDirector.FPS
            lwjgl.afterRender = director::afterRender
        }

        /**
         * The file name for a screenshot: its number (from one, so they sort
         * in order) and its name, such as `01-new-game.png`.
         */
        fun fileName(index: Int, name: String): String = "%02d-%s.png".format(index + 1, name)

        /**
         * Turn RGBA pixels read from OpenGL, which go from the bottom row up,
         * into an image, which goes from the top down. The alpha is dropped,
         * as the frame's always opaque.
         */
        fun toImage(pixels: ByteBuffer, width: Int, height: Int): BufferedImage {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until height) {
                val row = (height - 1 - y) * width * 4
                for (x in 0 until width) {
                    val i = row + x * 4
                    val r = pixels.get(i).toInt() and 0xff
                    val g = pixels.get(i + 1).toInt() and 0xff
                    val b = pixels.get(i + 2).toInt() and 0xff
                    image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
                }
            }
            return image
        }

        /**
         * The column the act 2 scenarios are set in, where the Chonkestrel
         * fights its full three ships: just after the Blockade Line in a
         * sixteen-sector game.
         */
        private val ACT_TWO_COLUMN: Int get() = GameMap.SECTOR_COUNT / 2 + 1

        /**
         * Move the player to a new sector of the given type, as if they'd
         * just jumped there, and close its start event.
         */
        internal fun enterSector(game: InGameState, typeName: String, sectorClass: GameMap.SectorClass) {
            val map = game.gameMap
            val type = map.sectorType(typeName) ?: error("Missing sector type $typeName (is the mod enabled?)")
            val info = map.SectorInfo(ACT_TWO_COLUMN, 0, type, sectorClass)
            val sector = map.generateSector(info, game)
            game.currentBeacon = sector.startBeacon
            game.shipUI.debugCloseWindow()
        }

        /**
         * Make the current beacon one or more environments at once (an
         * extreme hazard and worse, see MixedEnvironment).
         */
        internal fun setEnvironments(game: InGameState, types: List<Beacon.EnvironmentType>) {
            val beacon = game.currentBeacon
            beacon.hasStore = false
            beacon.hasQuest = false
            beacon.nebulaOverride = types.first()
            beacon.sectorHazards = types.drop(1)
            beacon.clearEnvironment()
            game.player.updateScriptedPowerLimits()
        }

        /**
         * Start a fight with [count] strong ships of different races, at
         * random (see [VideoDirector.STRONG_SHIPS]).
         */
        internal fun fightRandomShips(game: InGameState, count: Int) {
            val ships = VideoDirector.STRONG_SHIPS.shuffled().take(count)
            println("[screenshots] Fighting ${ships.joinToString { it.first }}")
            val specs = ships.map { (blueprint, description) -> VideoDirector.enemySpec(game, blueprint, description) }
            game.debugRemoveEnemy()
            game.debugWingmanSpecs = specs.drop(1)
            game.debugSpawnShip(specs.first(), Difficulty.NORMAL, game.currentBeacon.sector.difficultySectorNumber, 1)
        }

        /**
         * A beacon with a store, in a hostile sector near the end of the
         * game, that sells at least [STORE_COMBINED_WEAPONS] double or triple
         * weapons (see doc/game/stores.md), to show them off, and systems: it tries the
         * beacons of such sectors in turn, until one has.
         */
        private fun storeWithCombinedWeapons(game: InGameState): Beacon {
            val map = game.gameMap
            val type = map.sectorType(ACT_TWO_SECTOR) ?: error("Missing the sector $ACT_TWO_SECTOR (is the mod enabled?)")
            var fallback: Beacon? = null
            repeat(STORE_SECTOR_TRIES) {
                val info = map.SectorInfo(GameMap.SECTOR_COUNT - 2, 0, type, GameMap.SectorClass.HOSTILE)
                val sector = map.generateSector(info, game)
                for (beacon in sector.beacons) {
                    if (beacon.isExit || beacon == sector.startBeacon || beacon.isDangerous)
                        continue
                    beacon.hasStore = true
                    val store = beacon.getStore(game) ?: continue
                    // (not counting the ones that are no use in a fight, see Ship.RANDOMIZE_EXCLUDED)
                    val combined = store.weapons.count {
                        it != null && it.isCombined && it.combinedOf.none { part -> part in Ship.RANDOMIZE_EXCLUDED }
                    }
                    if (combined >= STORE_COMBINED_WEAPONS && StoreData.Section.SYSTEMS in store.sections) {
                        println("[screenshots] A store with $combined double or triple weapons and systems")
                        return beacon
                    }
                    if (fallback == null && combined > 0)
                        fallback = beacon
                    beacon.hasStore = false
                }
            }
            println("[screenshots] No store with $STORE_COMBINED_WEAPONS double or triple weapons and systems, using another")
            return fallback ?: error("No store with double or triple weapons")
        }

        // How many double or triple weapons the store's screenshot wants,
        // and how many sectors to look through for one
        private const val STORE_COMBINED_WEAPONS = 3
        private const val STORE_SECTOR_TRIES = 10

        /**
         * A fight for the site's screenshots, set up by [setUp], against
         * [count] random strong ships, all of them (and the player) armed
         * with double and triple weapons (as big as [enemyWeaponSize], see
         * [Trailer.arm]), fought by the [VideoDirector.BattleDriver], with
         * the enemies' crew shown even in a nebula. Everyone's weapons are
         * charged as it starts, so they're firing when it's taken.
         */
        private fun armedFight(name: String, count: Int, enemyWeaponSize: Int, setUp: (InGameState) -> Unit) =
            Scenario(name, settleSeconds = FIGHT_SECONDS, fight = true, prepare = { game ->
                for (ship in game.getOpponentShips(game.player) + game.player)
                    Trailer.charge(ship)
            }) { game ->
                XftlDebug.showEnemyCrew = true
                game.shipUI.debugCloseWindow()
                patchUp(game.player)
                Trailer.setWeapons(game, game.player, SITE_WEAPONS)
                setUp(game)
                fightRandomShips(game, count)
                game.debugSpawnWingmen()
                for (enemy in game.getOpponentShips(game.player))
                    Trailer.arm(game, enemy, enemyWeaponSize)
            }

        /**
         * The Chonkestrel's weapons in the site's fights: double and triple
         * ones of every kind, as many as each weapons system can power at
         * once, so they all fire (XFTL_RANDOMIZE's fill every slot, whether
         * or not there's the power for them).
         */
        private val SITE_WEAPONS = listOf(
            "BEAM_LONG" to 2, "MISSILES_2_PLAYER" to 3, "LASER_HEAVY_1" to 3, "BEAM_1" to 1,
            "LASER_BURST_3" to 2, "ION_1" to 3, "BOMB_1" to 3, "LASER_BURST_2" to 1,
        )

        /**
         * How long the player fights each pair of bigger ships before their
         * screenshot is taken.
         */
        private const val SHIP_FIGHT_SECONDS = 5f

        /**
         * Get the [ship] ready for its next fight: repair it, put its fires
         * out, heal its crew, and take any boarders left from the last fight
         * off it.
         */
        internal fun patchUp(ship: Ship) {
            for (system in ship.systems) {
                system.damagedEnergyLevels = 0
                system.ionTimer = 0f
                system.ionPowerLimit = null
                system.powerLimitChanged()
            }
            for (room in ship.rooms) {
                for (i in room.fires.indices) {
                    room.fires[i] = null
                    room.breaches[i] = null
                }
            }
            ship.health = ship.maxHealth
            ship.superShield = ship.maxSuperShield
            for (crew in ship.crew.toList()) {
                if (crew !is LivingCrew)
                    continue
                if (crew.ownerShip != ship)
                    crew.removeFromShip()
                else
                    crew.health = crew.maxHealth
            }
        }

        /**
         * How many Zoltan shields the player has in the bigger ships'
         * screenshots.
         */
        private const val PLAYER_ZOLTAN_SHIELDS = 3

        /**
         * Give the player [count] Zoltan shields, in place of other augments
         * if its augment slots are full.
         */
        internal fun giveZoltanShields(game: InGameState, count: Int) {
            val player = game.player
            val shield = game.blueprintManager[AugZoltanShield.NAME] as AugmentBlueprint
            while (player.augments.count { it is AugZoltanShield } < count) {
                if (player.augments.size >= player.maxAugments) {
                    val other = player.augments.firstOrNull { it !is AugZoltanShield } ?: break
                    player.augments.remove(other)
                }
                player.augments.add(shield)
            }
            player.superShield = player.maxSuperShield
        }

        /**
         * Send the player's crew standing in the teleporter to the medbays,
         * so they're not in the way.
         */
        internal fun leaveTeleporter(ship: Ship) {
            val teleporterRoom = ship.teleporter?.room ?: return
            for (crew in ship.crew.filterIsInstance<LivingCrew>()) {
                if (crew.ownerShip != ship || crew.room != teleporterRoom)
                    continue
                ship.medbaySystems.firstOrNull { medbay -> crew.setTargetRoom(medbay.room!!) }
            }
        }

        /**
         * Put the player somewhere different for each fight: one of XFTL's
         * own nebulas, or a mix of two to four environments (at most one of
         * each family, see MixedEnvironment).
         */
        private fun setRandomEnvironment(game: InGameState) {
            val types = if (Math.random() < 0.5) {
                listOf(NEW_NEBULAS.random())
            } else {
                val nebula = (NEW_NEBULAS + Beacon.EnvironmentType.NEBULA + Beacon.EnvironmentType.ION_STORM).random()
                val families = listOf(nebula, Beacon.EnvironmentType.ASTEROID, Beacon.EnvironmentType.SUN,
                    Beacon.EnvironmentType.PULSAR)
                families.shuffled().take((2..4).random())
            }
            println("[screenshots] Environment: ${types.joinToString(" + ")}")
            setEnvironments(game, types)
        }

        private val NEW_NEBULAS = listOf(
            Beacon.EnvironmentType.RADIATION, Beacon.EnvironmentType.MIRAGE, Beacon.EnvironmentType.DARK
        )

        /**
         * The ships with bigger versions (see doc/game/ship-shapes.md), for
         * [shipScenarios]: the first of each layout's ships, and the pirate
         * and elite ones, which have colours of their own.
         */
        val BIGGER_SHIPS = listOf(
            "FED_SCOUT", "FED_SCOUT_P", "REBEL_SKINNY", "REBEL_SKINNY_ELITE", "REBEL_SKINNY_P",
            "CIRCLE_SCOUT", "ZOLTAN_FIGHTER", "ZOLTAN_FIGHTER_P", "MANTIS_FIGHTER", "MANTIS_FIGHTER_P",
            "MANTIS_SCOUT", "MANTIS_SCOUT_P", "JELLY_BUTTON", "JELLY_BUTTON_P", "ROCK_SCOUT",
            "ROCK_SCOUT_P", "CRYSTAL_SCOUT", "ANAEROBIC_SCOUT", "AUTO_BASIC", "REBEL_FAT",
            "REBEL_FAT_P", "FED_BOMBER", "FED_BOMBER_P", "CIRCLE_BOMBER", "ZOLTAN_BOMBER",
            "ZOLTAN_BOMBER_P", "JELLY_CROISSANT", "JELLY_CROISSANT_P", "ROCK_FIGHT", "ROCK_FIGHT_P",
            "CRYSTAL_BOMBER", "ANAEROBIC_BOMBER", "MANTIS_BOMBER", "MANTIS_BOMBER_P", "JELLY_TRUFFLE",
            "JELLY_TRUFFLE_P", "ROCK_ASSAULT", "ROCK_ASSAULT_ELITE", "AUTO_ASSAULT"
        )

        /**
         * Screenshots of the bigger enemy ships, rather than the site's
         * (XFTL_SCREENSHOTS_SET=ships): a fight with the long and wide
         * versions of each ship (or two long ones, if it doesn't have a wide
         * one), in the second act, so they have the power for all their
         * weapons.
         */
        fun shipScenarios(): List<Scenario> = BIGGER_SHIPS.mapIndexed { index, name ->
            Scenario(name.lowercase().replace('_', '-'), settleSeconds = SHIP_FIGHT_SECONDS, fight = true) { game ->
                if (index == 0) {
                    enterSector(game, ACT_TWO_SECTOR, GameMap.SectorClass.HOSTILE)
                    giveZoltanShields(game, PLAYER_ZOLTAN_SHIELDS)
                }
                setRandomEnvironment(game)
                game.shipUI.debugCloseWindow()
                patchUp(game.player)
                leaveTeleporter(game.player)
                val shapes = game.blueprintManager.shipVariants[name].orEmpty().map { it.shape }.toSet()
                val second = if (ShipShape.WIDE in shapes) ShipShape.WIDE else ShipShape.LONG
                val spec = VideoDirector.enemySpec(game, name, "A bigger version of $name")
                game.debugFightShapes(spec, listOf(ShipShape.LONG, second))
                armBiggerShips(game)
            }
        }

        /**
         * The size 3 bosses (see doc/game/bosses.md), for [bossScenarios]:
         * each one's sector, its ship's spec, and its beacon's environments
         * (as its exit event gives them).
         */
        private val BOSSES = listOf(
            Triple("XFTL_MAW", "XFTL_MAW_DEVOURER", listOf(Beacon.EnvironmentType.NEBULA, Beacon.EnvironmentType.ASTEROID)),
            Triple("XFTL_BLOCKADE", "XFTL_BLOCKADE_COMMAND", emptyList()),
            Triple(
                "XFTL_GULLET", "XFTL_GULLET_STORMBREAKER",
                listOf(Beacon.EnvironmentType.ION_STORM, Beacon.EnvironmentType.ASTEROID, Beacon.EnvironmentType.PULSAR)
            ),
            Triple(
                "XFTL_BELLY", "XFTL_BELLY_OSSUARY",
                listOf(
                    Beacon.EnvironmentType.NEBULA, Beacon.EnvironmentType.ASTEROID,
                    Beacon.EnvironmentType.SUN, Beacon.EnvironmentType.PULSAR
                )
            ),
        )

        /**
         * Screenshots of the four sector bosses (XFTL_SCREENSHOTS_SET=boss):
         * a fight with each, alone, at its sector's exit. The enemy crew are
         * shown, even in a nebula.
         */
        fun bossScenarios(): List<Scenario> = BOSSES.map { (sector, specName, environments) ->
            bossFight(sector, specName, environments)
        } + Scenario("xftl-maw-devourer-rewards") { game ->
            // The Devourer's rewards, with every kind of reward there is:
            // resources, weapons, a drone, augments and a crew member
            game.debugRemoveEnemy()
            val destroyed = game.eventManager.getShip("XFTL_MAW_DEVOURER").destroyed!!
            game.shipUI.debugCloseWindow()
            game.shipUI.showEventDialogue(destroyed.resolve(), 1)
        }

        private fun bossFight(sector: String, specName: String, environments: List<Beacon.EnvironmentType>) =
            Scenario(specName.lowercase().replace('_', '-'), settleSeconds = SHIP_FIGHT_SECONDS, fight = true) { game ->
                enterSector(game, sector, GameMap.SectorClass.HOSTILE)
                if (environments.isNotEmpty())
                    setEnvironments(game, environments)
                game.shipUI.debugCloseWindow()
                patchUp(game.player)
                leaveTeleporter(game.player)
                println("[screenshots] Fighting $specName")
                game.debugRemoveEnemy()
                game.debugWingmanSpecs = emptyList()
                val spec = game.eventManager.getShip(specName)
                game.debugSpawnShip(spec, Difficulty.NORMAL, game.currentBeacon.sector.difficultySectorNumber, 1)
            }

        /**
         * Screenshots of the hybrid ships (XFTL_SCREENSHOTS_SET=hybrids, see
         * doc/game/hybrid-ships.md): fights with two of them at a time, in the
         * second act, going through the list of them all.
         */
        fun hybridScenarios(): List<Scenario> =
            (0 until HYBRID_FIGHTS).map { index ->
                Scenario("hybrids-${index + 1}", settleSeconds = SHIP_FIGHT_SECONDS, fight = true) { game ->
                    val all = game.blueprintManager[XftlDebug.SCOUT_SHIP].list().map { it.name }
                    val name = all[(index * 2) % all.size]
                    val second = all[(index * 2 + 1) % all.size]
                    if (index == 0) {
                        enterSector(game, ACT_TWO_SECTOR, GameMap.SectorClass.HOSTILE)
                        giveZoltanShields(game, PLAYER_ZOLTAN_SHIELDS)
                    }
                    setRandomEnvironment(game)
                    game.shipUI.debugCloseWindow()
                    patchUp(game.player)
                    leaveTeleporter(game.player)
                    println("[screenshots] Fighting $name and $second")
                    val spec = VideoDirector.enemySpec(game, name, "The hybrid $name")
                    game.debugFightShapes(spec, listOf(ShipShape.NORMAL, ShipShape.NORMAL))
                    // (after the fight's set up, which clears the wingmen's specs)
                    game.debugWingmanSpecs = listOf(VideoDirector.enemySpec(game, second, "The hybrid $second"))
                    armBiggerShips(game)
                }
            }

        /**
         * Arm the enemy ships in the bigger ships' screenshots (and their
         * wingmen, spawned now for it) with triple weapons, or double ones
         * where their weapons systems can't power triples (see
         * [Trailer.arm]), to check how they look on them.
         */
        private fun armBiggerShips(game: InGameState) {
            game.debugSpawnWingmen()
            for (enemy in game.getOpponentShips(game.player))
                Trailer.arm(game, enemy, 3)
        }

        /**
         * How many fights [hybridScenarios] has: enough for every hybrid.
         */
        const val HYBRID_FIGHTS = 70

        /**
         * Screenshots of the long and wide versions of the hybrid ships
         * (XFTL_SCREENSHOTS_SET=mixed, see doc/game/hybrid-ships.md): fights with
         * a hybrid's long and wide versions, for every [MIXED_STRIDE]th
         * hybrid, so the scouts and bombers of every race come up.
         */
        fun mixedScenarios(): List<Scenario> =
            (0 until MIXED_FIGHTS).map { index ->
                Scenario("mixed-${index + 1}", settleSeconds = SHIP_FIGHT_SECONDS, fight = true) { game ->
                    val all = game.blueprintManager[XftlDebug.SCOUT_SHIP].list().map { it.name }
                    val name = all[(index * MIXED_STRIDE) % all.size]
                    if (index == 0) {
                        enterSector(game, ACT_TWO_SECTOR, GameMap.SectorClass.HOSTILE)
                        giveZoltanShields(game, PLAYER_ZOLTAN_SHIELDS)
                    }
                    setRandomEnvironment(game)
                    game.shipUI.debugCloseWindow()
                    patchUp(game.player)
                    leaveTeleporter(game.player)
                    println("[screenshots] Fighting the long and wide $name")
                    val spec = VideoDirector.enemySpec(game, name, "A bigger version of the hybrid $name")
                    game.debugFightShapes(spec, listOf(ShipShape.LONG, ShipShape.WIDE))
                    armBiggerShips(game)
                }
            }

        /**
         * How many fights [mixedScenarios] has, and how far apart in the list
         * of hybrids its ships are: every 4th, so it goes through both the
         * scouts and the bombers.
         */
        const val MIXED_FIGHTS = 35
        const val MIXED_STRIDE = 4

        /**
         * The screenshots to take, showing off the big new features.
         */
        fun defaultScenarios(): List<Scenario> = listOf(
            // The main menu's new game window, as New Game opens it: the
            // difficulty and the crew names, over the Chonkestrel
            Scenario("new-game", menu = MainMenuState::showNewGameWindow),

            // Sixteen sectors, and the new ones
            Scenario("sector-map") { game ->
                game.shipUI.debugCloseWindow()
                game.shipUI.openSectorMap()
            },

            // A sector of mirage nebulas, with every beacon scanned (in
            // debug mode), and the false ships and hazards
            Scenario("beacon-map-mirage-expanse") { game ->
                enterSector(game, "XFTL_MIRAGE_EXPANSE", GameMap.SectorClass.NEBULA)
                game.shipUI.debugShowJumpMap()
            },

            // Several hazards at once: two (an extreme hazard), fighting two
            // random ships in an asteroid field near a sun, and three (a
            // catastrophic one), fighting a random ship in an ion storm with
            // asteroids and a pulsar. Everyone's armed with double and triple
            // weapons (see doc/game/weapon-sizes.md), and fires them.
            armedFight("twin-and-triple-weapons-near-a-sun-in-asteroids", 2, 2) { game ->
                setEnvironments(game, listOf(Beacon.EnvironmentType.ASTEROID, Beacon.EnvironmentType.SUN))
            },
            armedFight("ion-storm-with-asteroids-and-a-pulsar", 1, 3) { game ->
                setEnvironments(
                    game,
                    listOf(Beacon.EnvironmentType.ION_STORM, Beacon.EnvironmentType.ASTEROID, Beacon.EnvironmentType.PULSAR)
                )
            },

            // Fighting three random ships at once, in the second act, at an
            // extreme hazard (two at once): a mirage nebula near a sun
            armedFight("three-enemies-in-a-mirage-near-a-sun", 3, 3) { game ->
                enterSector(game, ACT_TWO_SECTOR, GameMap.SectorClass.HOSTILE)
                setEnvironments(game, listOf(Beacon.EnvironmentType.MIRAGE, Beacon.EnvironmentType.SUN))
            },

            // The equipment tab, as full as it gets: the weapons and drones,
            // the bigger cargo hold (full of random weapons) and the augments
            Scenario("cargo") { game ->
                game.debugRemoveEnemy()
                game.shipUI.debugShowShipWindow(ShipWindow.Tab.EQUIPMENT)
            },

            // The bigger reactor and the doubled systems
            Scenario("upgrades") { game ->
                game.debugRemoveEnemy()
                game.shipUI.debugShowShipWindow(ShipWindow.Tab.UPGRADES)
            },

            // A store with twice as many items, as the Chonkestrel starts out
            // without debug mode, with enough scrap to buy things, near the
            // end of the game, selling double and triple weapons
            Scenario("store") { game ->
                val main = game.mainGame
                XftlDebug.enabled = false
                XftlDebug.randomize = false
                XftlDebug.zoltan = false
                main.startNewGame(game.blueprintManager.defaultPlayerShip, Difficulty.NORMAL, null)

                val newGame = main.currentState as InGameState
                newGame.player.scrap = STORE_SCRAP
                newGame.currentBeacon = storeWithCombinedWeapons(newGame)
                newGame.debugRemoveEnemy()
                newGame.shipUI.debugCloseWindow()
                newGame.shipUI.updateButtons()
                newGame.shipUI.debugShowStore()
            },
        )
    }
}
