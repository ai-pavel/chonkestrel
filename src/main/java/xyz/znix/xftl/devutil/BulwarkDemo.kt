package xyz.znix.xftl.devutil

import xyz.znix.xftl.Ship
import xyz.znix.xftl.XftlDebug
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.drones.AntiPersonnelDrone
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.LWJGLGameContainer
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.systems.Teleporter
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.DroneSizes

/**
 * The bulwarks' demo (XFTL_BULWARK_DEMO, or `make bulwarks`, see
 * doc/game/drone-sizes.md): a new game, against the Rock-Zoltan Mainframe,
 * the boss with the most big empty rooms joined by wide passages (see
 * tools/wide_passages.py), with a bulwark in the first slot of four of its
 * drones systems, two triples and two twins ([SIZES]), and no other
 * drones, so they've rooms to walk through (one bulwark to a room). It
 * doesn't fire, and the player's ship takes no damage, so the fight goes
 * on; the enemy's crew are shown, and the rooms' numbers. Every
 * [BOARD_SECONDS], two of the player's own crew are teleported into one of
 * its big empty rooms, for the bulwarks to go after, and after
 * [ABOARD_SECONDS] back home again; in between, the bulwarks are on their
 * rounds. (They're the ship's own crew, so none are added.)
 */
class BulwarkDemo(private val main: MainGame) {
    private var started = false
    private var lastBoarding = 0L
    private var boarders: List<LivingCrew> = emptyList()
    private var lastLaunch = 0L

    fun afterRender(width: Int, height: Int) {
        val game = main.currentState as? InGameState ?: return
        val now = System.nanoTime()
        if (!started) {
            started = true
            start(game)
            lastBoarding = now
            lastLaunch = now
            return
        }
        val enemy = game.enemy ?: return

        // The boss's drones system might turn one off: on again
        if (now - lastLaunch > LAUNCH_SECONDS * 1e9) {
            lastLaunch = now
            launch(game, enemy, quiet = true)
        }
        if (boarders.isNotEmpty() && now - lastBoarding > ABOARD_SECONDS * 1e9) {
            bringHome(game)
        }
        if (now - lastBoarding > BOARD_SECONDS * 1e9) {
            lastBoarding = now
            board(game, enemy)
        }
    }

    private fun start(game: InGameState) {
        game.debugFlags.noEnemyFire.set = true
        game.debugFlags.noDmg.set = true
        game.debugFlags.showRoomNumbers.set = true
        XftlDebug.showEnemyCrew = true

        game.debugRemoveEnemy()
        game.debugWingmanSpecs = emptyList()
        val spec = VideoDirector.enemySpec(game, BOSS, "The Mainframe")
        game.debugSpawnShip(spec, Difficulty.NORMAL, game.currentBeacon.sector.difficultySectorNumber, 1)
        val enemy = game.enemy ?: error("No $BOSS")
        game.shipUI.debugCloseWindow()
        launch(game, enemy, quiet = false)
        board(game, enemy)
    }

    // A bulwark in the first slot of each of the first drones systems (one
    // for each of [SIZES]), powered up
    private fun launch(game: InGameState, enemy: Ship, quiet: Boolean) {
        enemy.dronesCount = 99
        for ((index, system) in enemy.droneSystems.withIndex()) {
            // (the rest without drones, so they have room to walk)
            if (index >= SIZES.size) {
                system.drones.indices.forEach { system.drones[it] = null }
                continue
            }
            val size = SIZES[index]
            val name = DroneSizes.nameFor("BATTLE", size)
            if (system.drones[0]?.type?.name != name) {
                system.drones.indices.forEach { system.drones[it] = null }
                system.drones[0] = Drones.DroneInfo(game.blueprintManager[name] as DroneBlueprint, null)
            }
            val info = system.drones[0]!!
            if (info.liveCopies.isNotEmpty() && info.liveCopies.all { it.isPowered })
                continue
            enemy.purchasedReactorPower = 99
            enemy.updateAvailablePower()
            repeat(system.energyLevels) { system.increasePower() }
            system.setDronePower(0, true)
            if (!quiet) {
                val drone = info.liveCopies.firstOrNull() as? AntiPersonnelDrone
                println("[bulwarks] Drones system in room ${system.room?.id}: $name, " +
                        if (drone == null) "not launched" else "standing ${drone.scale}x${drone.scale} in room ${drone.pawn?.room?.id}")
            }
        }
    }

    // Two of the player's crew, aboard at home and not at the piloting,
    // teleported into one of the boss's big empty rooms
    private fun board(game: InGameState, enemy: Ship) {
        val player = game.player
        val pilot = player.piloting?.room
        val pair = player.crew.filterIsInstance<LivingCrew>()
            .filter { it.room.ship == player && it.mode == AbstractCrew.SlotType.CREW && it.health > 0f && it.room != pilot }
            .sortedByDescending { it.health }.take(2)
        val room = AntiPersonnelDrone.bulwarkRooms(enemy, 2).randomOrNull() ?: return
        if (pair.isEmpty())
            return
        pair.forEach { it.teleportAnimatedTo(room) }
        boarders = pair
        println("[bulwarks] ${pair.joinToString { it.info.name }} boarding, in room ${room.id}")
    }

    // The boarders back home, to the teleporter (or wherever there's room)
    private fun bringHome(game: InGameState) {
        val player = game.player
        val home = player.rooms.firstOrNull { it.system is Teleporter } ?: player.rooms.first()
        for (crew in boarders) {
            if (crew.health > 0f && crew.room.ship != player)
                crew.teleportAnimatedTo(home)
        }
        boarders = emptyList()
    }

    companion object {
        const val BOSS = "XFTL_BOSS_MAINFRAME_ROCK_ZOLTAN"

        // The bulwarks' sizes, a drones system each
        private val SIZES = listOf(3, 2, 3, 2)

        // How often the boarders go, and how long they stay
        private const val BOARD_SECONDS = 30f
        private const val ABOARD_SECONDS = 15f
        private const val LAUNCH_SECONDS = 5f

        @JvmStatic
        fun install(main: MainGame, container: GameContainer) {
            if (!XftlDebug.bulwarkDemo)
                return
            val lwjgl = container as? LWJGLGameContainer ?: return
            val demo = BulwarkDemo(main)
            println("[bulwarks] Fighting the $BOSS's bulwarks")
            lwjgl.afterRender = demo::afterRender
        }
    }
}
