package xyz.znix.xftl.devutil

import org.jdom2.Element
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import xyz.znix.xftl.Ship
import xyz.znix.xftl.XftlDebug
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.shipgen.EnemyShipSpec
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.LWJGLGameContainer
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.BombBlueprint
import xyz.znix.xftl.weapons.FlakBlueprint
import xyz.znix.xftl.systems.SelectedTarget
import xyz.znix.xftl.weapons.IRoomTargetingWeapon
import xyz.znix.xftl.weapons.ShadowTargets
import xyz.znix.xftl.weapons.WeaponSizes
import java.io.File
import kotlin.math.atan2
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The video recorder, for the project's site: it plays the release trailer
 * (see [Trailer]), a [timeline] of scripted fights, recording every frame
 * into a video with ffmpeg, then quits. Turn it on with XFTL_VIDEO (see
 * [XftlDebug]), which also starts a game with the default ship (the
 * Chonkestrel). The video's named after the time it was recorded, such as
 * trailer-20261024-173718.mp4. The fights are fought by [BattleDriver],
 * which the screenshot generator uses too.
 *
 * While recording, every frame advances the game by exactly one frame of the
 * video ([LWJGLGameContainer.fixedDeltaSec]), however long it really took, so
 * the video plays smoothly.
 */
class VideoDirector(
    private val main: MainGame,
    private val outputFile: File,
    private val fps: Int = FPS,
    private val timeline: List<Step>,
    private val length: Float,
) {
    /**
     * Something to do at a given time in the video, in seconds. With a
     * [screenshot] name, the frame's also saved as a PNG next to the video,
     * named after it and the [screenshot], such as
     * trailer-20261024-003544-fight-01.png.
     */
    class Step(
        val at: Float, val name: String, val screenshot: String? = null,
        val action: (InGameState, BattleDriver) -> Unit,
    )

    private val clock = FixedClock(fps)
    private var nextStep = 0
    private var encoder: Encoder? = null
    private var driver: BattleDriver? = null
    private var finished = false

    fun afterRender(framebufferWidth: Int, framebufferHeight: Int) {
        if (finished)
            return
        val game = main.currentState as? InGameState ?: return

        val encoder = encoder ?: Encoder.start(outputFile, framebufferWidth, framebufferHeight, fps).also {
            encoder = it
            println("[video] Recording ${framebufferWidth}x$framebufferHeight at $fps fps to ${outputFile.absolutePath}")
        }
        val driver = driver ?: BattleDriver(game).also { driver = it }

        val time = clock.time

        // Run the steps that are due
        while (nextStep < timeline.size && timeline[nextStep].at <= time) {
            val step = timeline[nextStep++]
            println("[video] %.1fs: %s".format(time, step.name))
            step.screenshot?.let { name ->
                val file = File(outputFile.absoluteFile.parentFile, outputFile.nameWithoutExtension + "-$name.png")
                ScreenshotDirector.capture(framebufferWidth, framebufferHeight, file)
                println("[video] Screenshot: ${file.absolutePath}")
            }
            try {
                step.action(game, driver)
            } catch (ex: Exception) {
                println("[video] Step '${step.name}' failed:")
                ex.printStackTrace()
            }
        }

        try {
            driver.update(time)
        } catch (ex: Exception) {
            println("[video] Driving the battle failed:")
            ex.printStackTrace()
        }

        try {
            encoder.writeFrame(framebufferWidth, framebufferHeight)
        } catch (ex: Exception) {
            println("[video] Couldn't record a frame, stopping:")
            ex.printStackTrace()
            finish()
            return
        }
        clock.advance()

        if (time >= length)
            finish()
    }

    private fun finish() {
        finished = true
        encoder?.finish()
        println("[video] Done: ${clock.frame} frames, saved to ${outputFile.absolutePath}")
        main.quitGame()
    }

    /**
     * The video's clock: each frame is exactly 1/[fps] seconds long.
     */
    class FixedClock(val fps: Int) {
        var frame: Int = 0
            private set

        val frameLength: Float get() = 1f / fps

        val time: Float get() = frame.toFloat() / fps

        fun advance() {
            frame++
        }
    }

    /**
     * Pipes raw frames into ffmpeg, which encodes them into an H.264 MP4
     * (also used by the [ScreenshotDirector], to record its whole run).
     */
    class Encoder(private val process: Process, private val output: OutputStream, width: Int, height: Int) {
        private val pixels = BufferUtils.createByteBuffer(width * height * 4)
        private val bytes = ByteArray(width * height * 4)

        fun writeFrame(width: Int, height: Int) {
            pixels.clear()
            GL11.glReadBuffer(GL11.GL_BACK)
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
            pixels.get(bytes)
            output.write(bytes)
        }

        fun finish() {
            output.close()
            val code = process.waitFor()
            if (code != 0)
                println("[video] ffmpeg exited with code $code, see its log next to the video")
        }

        companion object {
            fun start(file: File, width: Int, height: Int, fps: Int): Encoder {
                file.absoluteFile.parentFile?.mkdirs()
                val process = ProcessBuilder(ffmpegCommand(file, width, height, fps))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(File(file.absolutePath + ".log"))
                    .start()
                return Encoder(process, process.outputStream.buffered(1 shl 20), width, height)
            }
        }
    }

    /**
     * Fights the battle for the player: spreading the weapons' fire across
     * the enemy ships, keeping the drones out, and using the hacking, mind
     * control and teleporter systems when the timeline says to. When an enemy
     * ship is destroyed, its reward screen is left up for a moment (which
     * pauses the game, as usual), then closed.
     */
    class BattleDriver(private val game: InGameState) {
        private val player: Ship get() = game.player

        var battleStarted = false
            private set

        /**
         * True once every enemy ship has been destroyed (or its crew killed).
         */
        val battleOver: Boolean get() = enemies().isEmpty()

        var dronesOn = false
        var hackingOn = false
        var mindControlOn = false

        /**
         * Fight as a player would (the playtest, see AutoplayDirector),
         * rather than for show: focus every weapon on one ship (see
         * [focusTarget]), its shields first, then its weapons, in volleys,
         * saving missiles and drone parts against weak ships, and cloak
         * when the enemy's about to fire. The trailer and screenshots leave
         * it off.
         */
        var playerTactics = false

        /**
         * True once it's judged the fight one it can't win without losing
         * too much of its hull ([playerTactics], see [assess]): it powers its
         * engines up, so the FTL drive charges faster, shoots at the most
         * dangerous ship's weapons, and the playtest jumps away as soon as
         * it can (see AutoplayDirector). It stays that way for the fight.
         */
        var fleeing = false
            private set

        /** Why it's [fleeing], for the playtest's log. */
        var fleeReason: String? = null
            private set

        // When it last weighed the fight up (see assess)
        private var lastAssessed = -1f

        // The boarding party: sent, and brought back before their ship's destroyed
        private var boardingTarget: Ship? = null
        private var recalling = false

        // How many enemy ships were left last frame, to spot one being
        // destroyed, and how long its reward screen stays up
        private var lastEnemyCount = 0
        private var showRewardUntil: Float? = null

        /**
         * True while a reward screen is being shown.
         */
        fun showingReward(time: Float): Boolean = showRewardUntil?.let { time < it } == true

        fun startBattle() {
            battleStarted = true
        }

        /**
         * Get ready for the next fight, the last one's ships having been
         * taken away (rather than destroyed), as the trailer does.
         */
        fun nextFight() {
            restorePower()
            lastEnemyCount = 0
            showRewardUntil = null
            boardingTarget = null
            recalling = false
        }

        /**
         * The enemy ships still fighting, the main one first. A destroyed
         * ship stays in this for a moment, until the game removes it and
         * shows its reward screen.
         */
        fun enemies(): List<Ship> = game.getOpponentShips(player)

        fun update(time: Float) {
            val allEnemies = enemies()

            // When an enemy ship goes, leave its reward screen up for a bit
            if (battleStarted && allEnemies.size < lastEnemyCount) {
                if (game.shipUI.isWindowOpen) {
                    showRewardUntil = time + REWARD_SCREEN_TIME
                    println("[video] %.1fs: an enemy ship is gone, showing its rewards".format(time))
                } else {
                    println("[video] %.1fs: an enemy ship is gone, without a reward screen".format(time))
                }
            }
            lastEnemyCount = allEnemies.size
            if (showingReward(time))
                return

            // Only fight the ones that haven't been destroyed yet
            val enemies = allEnemies.filter { !it.isDead }

            // Otherwise, keep the event dialogues from stopping the battle
            game.shipUI.debugCloseWindow()

            if (!battleStarted || enemies.isEmpty())
                return

            if (playerTactics) {
                if (time - lastAssessed >= ASSESS_EVERY) {
                    lastAssessed = time
                    assess(enemies)
                }
                cloakAgainst(enemies)
                if (crewHurt())
                    powerMedbays()
                if (fleeing)
                    powerEngines()
            }
            if (dronesOn)
                launchDrones(enemies)
            if (hackingOn)
                hack(enemies)
            if (mindControlOn)
                mindControl(enemies)
            updateBoarding(enemies)

            fireWeapons(enemies)
        }

        private fun launchDrones(enemies: List<Ship>) {
            for (system in player.droneSystems) {
                for (slot in system.drones.indices) {
                    val drone = system.drones[slot] ?: continue
                    if (drone.instance?.isPowered == true)
                        continue
                    // Against weak ships, only the drones that don't attack
                    // them, to save drone parts
                    if (playerTactics && drone.type.type.needsHostileShip && enemies.all(::isWeak))
                        continue
                    system.setDronePower(slot, true)
                }
            }
        }

        private fun hack(enemies: List<Ship>) {
            // Each hacking system hacks a different ship's shields
            for ((i, hacking) in player.hackingSystems.withIndex()) {
                if (!hacking.droneLaunched) {
                    val target = enemies[i % enemies.size]
                    (target.shieldSystems.firstOrNull()?.room ?: target.rooms.random()).let { hacking.selectTarget(it) }
                } else {
                    hacking.startHackingPulse()
                }
            }
        }

        private fun mindControl(enemies: List<Ship>) {
            // Set the mind control up directly, as the nebula blocks the
            // sensors, so the player couldn't pick a room to target. Each
            // system takes a crew member from a different ship, if it can.
            for ((i, mc) in player.mindControlSystems.withIndex()) {
                if (!mc.ready || mc.active)
                    continue

                val taken = player.mindControlSystems.mapNotNull { it.controlledCrew }
                val ships = enemies.drop(i % enemies.size) + enemies.take(i % enemies.size)
                val crew = ships.flatMap { ship ->
                    ship.crew.filterIsInstance<LivingCrew>()
                        .filter { it.ownerShip == ship && it.mindControlledBy == null && !it.isMindControlResistant }
                }.firstOrNull { it !in taken } ?: continue

                mc.debugControl(crew)
            }
        }

        /**
         * Each enemy ship teleports a couple of its crew aboard, each into a
         * different room of the Chonkestrel, or with [randomRooms], each
         * boarder into a room at random. With [onlyWithTeleporters], only
         * the ships with a teleporter do.
         */
        fun enemiesBoard(randomRooms: Boolean = false, onlyWithTeleporters: Boolean = false) {
            val rooms = listOfNotNull(
                player.shieldSystems.firstOrNull()?.room,
                player.weaponSystems.firstOrNull()?.room,
                player.engineSystems.firstOrNull()?.room,
                player.oxygen?.room,
            ).distinct()

            for ((i, ship) in enemies().withIndex()) {
                if (onlyWithTeleporters && ship.teleporter == null)
                    continue
                val room = rooms.getOrNull(i) ?: player.rooms.random()
                val crew = ship.crew.filterIsInstance<LivingCrew>()
                    .filter { it.ownerShip == ship && it.room.ship == ship && it.mindControlledBy == null }
                    .take(ENEMY_BOARDERS)
                for (boarder in crew) {
                    boarder.teleportAnimatedTo(if (randomRooms) player.rooms.random() else room)
                }
            }
        }

        /**
         * Send the crew in the teleporter to the [target]'s weapons room.
         */
        fun sendBoarders(target: Ship? = enemies().getOrNull(1) ?: enemies().firstOrNull()) {
            val teleporter = player.teleporter ?: return
            target ?: return
            val room = target.weaponSystems.firstOrNull()?.room ?: target.rooms.random()
            boardingTarget = target
            teleporter.selectTeleportAction(true, room)
        }

        private fun boarders(ship: Ship): List<LivingCrew> =
            ship.crew.filterIsInstance<LivingCrew>().filter { it.ownerShip == player }

        private fun updateBoarding(enemies: List<Ship>) {
            val target = boardingTarget ?: return
            val boarders = boarders(target)

            // Bring them home before their ship's destroyed: once it's badly
            // damaged, or it's the last one
            if (!recalling && boarders.isNotEmpty()
                && (target.health <= target.maxHealth / 2 || enemies == listOf(target))
            ) {
                recalling = true
            }

            if (recalling && boarders.isNotEmpty()) {
                val teleporter = player.teleporter ?: return
                if (teleporter.isReceiveAvailable)
                    teleporter.selectTeleportAction(false, boarders.first().room)
            }
        }

        /**
         * Fire every charged weapon, each at a different enemy ship in turn,
         * so they all take a beating at once, at random rooms, as the enemy AI
         * does.
         */
        private fun fireWeapons(allEnemies: List<Ship>) {
            // Not at the ships that are cloaked, which can't be fired at
            val enemies = allEnemies.filter { !it.isCloakActive }
            if (enemies.isEmpty())
                return
            if (playerTactics) {
                fireVolleys(enemies)
                return
            }
            for ((slot, hardpoint) in player.hardpoints.withIndex()) {
                val weapon = hardpoint.weapon ?: continue
                val weapons = player.weaponsFor(slot) ?: continue

                if (!weapon.isPowered) {
                    if (weapon.hasEnoughMissiles && !weapons.setWeaponPower(weapon, true))
                        powerFromOtherSystems(weapon.type.power) { weapons.setWeaponPower(weapon, true) }
                    continue
                }
                if (!weapon.isCharged)
                    continue

                val target = enemies[slot % enemies.size]
                when (weapon) {
                    is IRoomTargetingWeapon -> {
                        // A combined weapon's other copies fire at the other
                        // ships, as the player's shadow targets do (see
                        // doc/game/weapon-sizes.md)
                        val shadows = ShadowTargets.pick(game, pickRoom(target), weapon.type.combinedCount)
                        weapon.fireCombined { copy -> shadows.getOrNull(copy - 1) ?: pickRoom(target) }
                    }
                    is BeamBlueprint.BeamInstance -> {
                        weapon.fire(beamAim(weapon, target))
                        weapon.type.launchSounds?.get()?.play()
                    }
                }
            }
        }

        /**
         * A ship that's no match for the player: at most one layer of
         * shields, so its lasers get through without the missiles.
         */
        private fun isWeak(ship: Ship): Boolean = ship.shieldSystems.sumOf { it.energyLevels / 2 } <= 1

        /**
         * The ship every weapon fires at, with [playerTactics]: the first in
         * the order it kills them in (see [killOrder]), or while [fleeing],
         * the one that hurts most, to knock its weapons out.
         */
        private fun focusTarget(enemies: List<Ship>): Ship =
            if (fleeing) enemies.maxBy { threat(it) } else killOrder(enemies).first()

        /**
         * The order to kill the [enemies] in: the most damage they do to it a
         * second, for each second it takes to kill them, first (dangerous
         * and quick to kill), then the least hull.
         */
        private fun killOrder(enemies: List<Ship>): List<Ship> =
            enemies.sortedWith(compareByDescending<Ship> { worth(it) }.thenBy { it.health })

        private fun worth(enemy: Ship): Float {
            val kill = timeToKill(enemy)
            return if (kill.isInfinite()) 0f else (threat(enemy) + THREAT_FLOOR) / kill
        }

        /**
         * How much damage a second [weapons] get through [layers] layers of
         * shields, a [superShield] and [evasion] (in percent), roughly, as
         * one volley each time the slowest is charged: missiles and bombs
         * aren't stopped by shields, beams lose a point of damage for each
         * layer (on [BEAM_ROOMS] rooms), and each layer stops one of the
         * other shots.
         */
        private fun damagePerSecond(
            weapons: List<AbstractWeaponInstance>, layers: Int, superShield: Int, evasion: Int
        ): Float {
            var hits = 0
            var hitDamage = 0f
            var through = 0f
            var period = 0f
            for (weapon in weapons) {
                val type = weapon.type
                val shots = (if (type is FlakBlueprint) type.projectileCount else type.shots) * type.combinedCount
                when {
                    type.missilesUsed > 0 || type is BombBlueprint -> through += type.damage * shots
                    type is BeamBlueprint -> through += maxOf(0, type.damage - layers) * BEAM_ROOMS * type.combinedCount
                    else -> {
                        hits += shots
                        hitDamage += type.damage * shots
                    }
                }
                period = maxOf(period, weapon.chargeTime)
            }
            if (period <= 0f)
                return 0f
            val perHit = if (hits > 0) hitDamage / hits else 0f
            val volley = (through + maxOf(0, hits - layers) * perHit - superShield).coerceAtLeast(0f)
            return volley * (1f - evasion / 100f) / period
        }

        // The shield layers a ship has when they're all up
        private fun fullLayers(ship: Ship): Int =
            maxOf(ship.activeShieldLayers, ship.shieldSystems.sumOf { it.energyLevels / 2 })

        // The weapons a ship fights with (not those that only help the enemy)
        private fun armed(ship: Ship): List<AbstractWeaponInstance> = ship.hardpoints.mapNotNull { it.weapon }.filter {
            it.type.name !in Ship.RANDOMIZE_EXCLUDED && it.type.combinedOf.none { name -> name in Ship.RANDOMIZE_EXCLUDED } &&
                    (it.type.missilesUsed == 0 || it.ship.missilesCount > 0 || !it.ship.isPlayerShip)
        }

        /** How much damage a second an [enemy] does to the player's ship, roughly. */
        private fun threat(enemy: Ship): Float =
            damagePerSecond(armed(enemy), fullLayers(player), player.superShield, player.evasion)

        /** How long the player's weapons take to destroy an [enemy], roughly (infinite if they can't). */
        private fun timeToKill(enemy: Ship): Float {
            val dps = damagePerSecond(armed(player), fullLayers(enemy), enemy.superShield, enemy.evasion)
            return if (dps <= 0f) Float.POSITIVE_INFINITY else enemy.health / dps
        }

        /**
         * Weigh the fight up ([playerTactics]): how much of its hull it would
         * lose, killing the [enemies] in order (see [killOrder]), each doing
         * its damage until it's dead. More than [FLEE_LOSS] of what it has
         * left, and it flees (see [fleeing]).
         */
        private fun assess(enemies: List<Ship>) {
            if (fleeing || enemies.isEmpty())
                return
            var loss = 0f
            var alive = killOrder(enemies)
            for (enemy in killOrder(enemies)) {
                val kill = timeToKill(enemy)
                val threats = alive.sumOf { threat(it).toDouble() }.toFloat()
                if (kill.isInfinite()) {
                    if (threats > 0f)
                        loss = Float.POSITIVE_INFINITY
                    break
                }
                loss += threats * kill
                alive = alive - enemy
            }
            if (loss > player.health * FLEE_LOSS) {
                fleeing = true
                fleeReason = if (loss.isInfinite()) "it can't hurt them" else "it would lose about ${loss.toInt()} hull"
                return
            }

            // Or their boarders would beat its crew
            val boarders = boardingStrength(enemies)
            val defenders = crewStrength(player.crew.filterIsInstance<LivingCrew>()
                .filter { it.ownerShip == player && it.room.ship == player })
            if (boarders > defenders * FLEE_BOARDERS) {
                fleeing = true
                fleeReason = "their boarders (%.0f) would beat its crew (%.0f)".format(boarders, defenders)
            }
        }

        // How much a [crew] can do in a fist fight: their health, times how
        // hard they hit (the Mantis half as hard again, the Engi half)
        private fun crewStrength(crew: List<LivingCrew>): Float =
            crew.sumOf { (it.health * it.attackDamageMult).toDouble() }.toFloat() / 100f

        /**
         * How strong the [enemies]' boarding parties are: those aboard the
         * player's ship already, and those that could come, two teleporter
         * loads from each ship with a working teleporter (its strongest
         * crew), unless the player's super shield keeps them out.
         */
        private fun boardingStrength(enemies: List<Ship>): Float {
            var strength = crewStrength(player.crew.filterIsInstance<LivingCrew>().filter { it.ownerShip in enemies })
            for (enemy in enemies) {
                val teleporter = enemy.teleporter ?: continue
                val room = teleporter.room ?: continue
                if (teleporter.broken)
                    continue
                if (player.superShield > 0 && !enemy.hasAugment(AugmentBlueprint.ZOLTAN_BYPASS))
                    continue
                val aboard = enemy.crew.filterIsInstance<LivingCrew>().filter { it.ownerShip == enemy && it.room.ship == enemy }
                strength += crewStrength(aboard.sortedByDescending { it.health * it.attackDamageMult }
                    .take(room.cellCount * BOARDING_WAVES))
            }
            return strength
        }

        /**
         * While [fleeing], power the engines all the way up, so the FTL
         * drive charges faster, with power from the hacking, mind control,
         * teleporter and drones, then the weapons.
         */
        private fun powerEngines() {
            for (engines in player.engineSystems) {
                while (engines.powerSelected < engines.energyLevels) {
                    val before = engines.powerSelected
                    engines.increasePower()
                    if (engines.powerSelected != before)
                        continue
                    val donor = (player.hackingSystems + player.mindControlSystems +
                            listOfNotNull(player.teleporter) + player.droneSystems).firstOrNull { it.powerSelected > 0 }
                    if (donor != null) {
                        val had = donor.powerSelected
                        donor.decreasePower()
                        if (donor.powerSelected == had)
                            return
                        tookPower = true
                        continue
                    }
                    val slot = player.hardpoints.indices.lastOrNull { player.hardpoints[it].weapon?.isPowered == true }
                        ?: return
                    val weapon = player.hardpoints[slot].weapon!!
                    player.weaponsFor(slot)?.setWeaponPower(weapon, false)
                    if (weapon.isPowered)
                        return
                }
            }
        }

        /**
         * Fire as a player would ([playerTactics]): power every weapon (but
         * the missiles against weak ships, to save them), wait until the
         * projectile weapons are all charged, then fire them together at
         * the [focusTarget], so its shields can't come back up in between:
         * at its shields while they're up (the bombs, which go through
         * them, at its weapons), then at its weapons. The beams, which
         * shields stop, fire once its shields are down.
         */
        private fun fireVolleys(enemies: List<Ship>) {
            val target = focusTarget(enemies)
            val weak = enemies.all(::isWeak)

            val powered = ArrayList<Pair<Int, xyz.znix.xftl.weapons.AbstractWeaponInstance>>()
            for ((slot, hardpoint) in player.hardpoints.withIndex()) {
                val weapon = hardpoint.weapon ?: continue
                val weapons = player.weaponsFor(slot) ?: continue
                val isMissile = weapon.type.missilesUsed > 0
                // Not the weapons that only help the enemy, such as the
                // healing bombs (see Ship.RANDOMIZE_EXCLUDED), or missiles
                // against weak ships
                val useless = weapon.type.name in Ship.RANDOMIZE_EXCLUDED ||
                        weapon.type.combinedOf.any { it in Ship.RANDOMIZE_EXCLUDED }
                if (useless || (weak && isMissile)) {
                    if (weapon.isPowered)
                        weapons.setWeaponPower(weapon, false)
                    continue
                }
                if (!weapon.isPowered) {
                    // (not while fleeing, when its power's gone to the engines)
                    if (!fleeing && weapon.hasEnoughMissiles && !weapons.setWeaponPower(weapon, true))
                        powerFromOtherSystems(weapon.type.power) { weapons.setWeaponPower(weapon, true) }
                    continue
                }
                powered += slot to weapon
            }

            // (while fleeing, it goes for the weapons, to stop the damage)
            val shieldsUp = target.activeShieldLayers > 0 && !fleeing
            val shieldRoom = target.shieldSystems.firstOrNull { it.room != null }?.room
            val weaponRoom = target.weaponSystems.firstOrNull { it.room != null }?.room
            fun roomFor(weapon: xyz.znix.xftl.weapons.AbstractWeaponInstance): Room {
                val wanted = when {
                    weapon.type is xyz.znix.xftl.weapons.BombBlueprint -> weaponRoom
                    shieldsUp -> shieldRoom
                    else -> weaponRoom
                }
                return wanted ?: pickRoom(target)
            }

            // The projectile weapons fire together: once they're all
            // charged, or those charged have the shots to get through the
            // target's shields (one more than it has layers up), or its
            // shields are down
            val volley = powered.filter { it.second !is BeamBlueprint.BeamInstance }
            val charged = volley.filter { it.second.isCharged }
            val shots = charged.sumOf { (_, weapon) ->
                val type = weapon.type
                val count = (if (type is FlakBlueprint) type.projectileCount else type.shots) * type.combinedCount
                if (type.missilesUsed > 0 || type is BombBlueprint) count * SHOTS_PAST_SHIELDS else count
            }
            val enough = charged.size == volley.size || target.activeShieldLayers == 0 ||
                    shots >= target.activeShieldLayers + 1
            if (charged.isNotEmpty() && enough) {
                for ((_, weapon) in charged) {
                    if (weapon is IRoomTargetingWeapon) {
                        val room = roomFor(weapon)
                        weapon.fireCombined { room }
                    }
                }
            }

            // The beams, once the shields are down (or if they're all it has)
            for ((_, weapon) in powered) {
                if (weapon !is BeamBlueprint.BeamInstance || !weapon.isCharged)
                    continue
                if (shieldsUp && volley.isNotEmpty())
                    continue
                val start = weaponRoom ?: pickRoom(target)
                val aim = weapon.buildAim(start, target.systems.mapNotNull { it.room }.filter { it != start }
                    .randomOrNull() ?: pickRoom(target), false)
                weapon.fire(aim)
                weapon.type.launchSounds?.get()?.play()
            }
        }

        /**
         * True if any of the player's crew aboard are below half health, so
         * they need the medbay, even in a fight ([playerTactics]).
         */
        private fun crewHurt(): Boolean = player.crew.any {
            it is LivingCrew && it.ownerShip == player && it.room.ship == player &&
                    it.health > 0f && it.health < it.maxHealth * HURT_CREW
        }

        /**
         * Keep the medbays powered while the crew are hurt ([playerTactics]),
         * taking the power from the drones, hacking, mind control,
         * teleporter or cloaking, or else a weapon, if the reactor's short.
         */
        private fun powerMedbays() {
            for (medbay in player.medbaySystems) {
                if (medbay.damaged)
                    continue
                while (medbay.powerSelected < medbay.energyLevels) {
                    val before = medbay.powerSelected
                    medbay.increasePower()
                    if (medbay.powerSelected != before)
                        continue
                    val donor = (player.droneSystems + player.hackingSystems + player.mindControlSystems +
                            listOfNotNull(player.teleporter, player.cloaking)).firstOrNull { it.powerSelected > 0 }
                    if (donor != null) {
                        val had = donor.powerSelected
                        donor.decreasePower()
                        if (donor.powerSelected == had)
                            break
                        continue
                    }
                    val slot = player.hardpoints.indices.lastOrNull { player.hardpoints[it].weapon?.isPowered == true }
                        ?: break
                    val weapon = player.hardpoints[slot].weapon!!
                    player.weaponsFor(slot)?.setWeaponPower(weapon, false)
                    if (weapon.isPowered)
                        break
                }
            }
        }

        /**
         * Cloak ([playerTactics]) when an enemy's about to fire a missile or
         * a bomb, or several weapons at once, so they miss.
         */
        private fun cloakAgainst(enemies: List<Ship>) {
            val cloaking = player.cloaking ?: return
            if (cloaking.active)
                return
            val aboutToFire = enemies.flatMap { enemy ->
                enemy.hardpoints.mapNotNull { it.weapon }.filter { it.isPowered && it.chargeProgress >= CLOAK_AT_CHARGE }
            }
            if (aboutToFire.size >= 2 || aboutToFire.any { it.type.missilesUsed > 0 })
                cloaking.activateCloak()
        }

        /**
         * Without the reactor power for a weapon that takes [power] (such as
         * in an ion storm, which halves the reactor), take it from the other
         * systems a power at a time until [tryPower] works: the mind
         * control, hacking, teleporter and cloaking systems, the medbay, then
         * the shields (the second ones first), as a big ship's hull can take
         * a few hits while its weapons charge, but a fight it can't hurt the
         * enemy in never ends. Never the oxygen, as the crew would suffocate.
         * If there's the power for it and it still can't be powered (its
         * weapons system is damaged, or limited by an event), the last power
         * taken is put back. [restorePower] puts the rest back after the
         * fight.
         */
        private fun powerFromOtherSystems(power: Int, tryPower: () -> Boolean) {
            // (with [playerTactics], not the medbay while the crew are hurt)
            val medical = if (playerTactics && crewHurt()) emptyList()
            else player.medbaySystems + listOfNotNull(player.clonebay)
            val donors = player.mindControlSystems + player.hackingSystems +
                    listOfNotNull(player.teleporter, player.cloaking) +
                    medical + player.shieldSystems.reversed()
            for (system in donors) {
                while (system.powerSelected > 0) {
                    val before = system.powerSelected
                    system.decreasePower()
                    if (system.powerSelected == before)
                        break
                    tookPower = true
                    if (tryPower())
                        return
                    if (player.powerAvailable >= power) {
                        // It isn't the reactor that's stopping it
                        while (system.powerSelected < before) {
                            val now = system.powerSelected
                            system.increasePower()
                            if (system.powerSelected == now)
                                break
                        }
                        return
                    }
                }
            }
        }

        // True if powerFromOtherSystems took power, to put it back
        private var tookPower = false

        /**
         * Power the systems [powerFromOtherSystems] took power from back up,
         * as far as the reactor allows (the shields first), once the fight's
         * over.
         */
        fun restorePower() {
            if (!tookPower)
                return
            tookPower = false
            val systems = player.shieldSystems + player.oxygenSystems + player.medbaySystems +
                    listOfNotNull(player.clonebay) + player.hackingSystems + player.mindControlSystems +
                    listOfNotNull(player.teleporter, player.cloaking)
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
         * A room to shoot at, mostly the ones with systems in.
         */
        /**
         * Where to sweep [weapon] across [target]: between two of its rooms,
         * or for a combined beam (see doc/game/weapon-sizes.md), across the screen
         * towards a room in the furthest of the other ships its copies go
         * into, long enough to reach it, so it goes on through their
         * windows, as the player can aim it. With no other ship, it's just
         * longer, as for the player.
         */
        private fun beamAim(weapon: BeamBlueprint.BeamInstance, target: Ship): SelectedTarget.BeamAim {
            val start = pickRoom(target)
            val size = weapon.type.combinedCount
            // The ships the copies go into, as BeamInstance.fireCopies picks them
            val others = game.hostileShipsLeftToRight.filter { it != target }.take(size - 1)
            val furthest = others.maxByOrNull { game.getEnemyPosition(it).distToSq(game.getEnemyPosition(target)) }
            if (furthest == null) {
                val aim = weapon.buildAim(start, pickRoom(target), false)
                if (size > 1) {
                    aim.length = WeaponSizes.beamLength(aim.length, size)
                    aim.updateHitRooms()
                }
                return aim
            }

            // The other ship's room, in the target's space
            val end = pickRoom(furthest).pixelCentre + game.getEnemyPosition(furthest) - game.getEnemyPosition(target)
            val aim = SelectedTarget.BeamAim(weapon, -1, target, start.pixelCentre)
            aim.angle = atan2((end.y - start.pixelCentre.y).toFloat(), (end.x - start.pixelCentre.x).toFloat())
            aim.length = maxOf(WeaponSizes.beamLength(aim.length, size),
                start.pixelCentre.distTo(end) + BEAM_OVERSHOOT)
            aim.updateHitRooms()
            return aim
        }

        private fun pickRoom(target: Ship): Room {
            val systemRooms = target.systems.mapNotNull { it.room }
            return if (systemRooms.isNotEmpty() && Math.random() < 0.8) systemRooms.random() else target.rooms.random()
        }
    }

    companion object {
        const val FPS = 30

        /** How far past the room it's aimed at a combined beam's line goes, in the furthest ship. */
        private const val BEAM_OVERSHOOT = 60

        /**
         * How long each enemy ship's reward screen stays up.
         */
        const val REWARD_SCREEN_TIME = 2f

        /**
         * How many crew each enemy ship sends aboard.
         */
        const val ENEMY_BOARDERS = 2

        /**
         * How far an enemy weapon has charged when the playtest cloaks
         * against it (see BattleDriver.playerTactics).
         */
        const val CLOAK_AT_CHARGE = 0.95f

        /**
         * Weighing a fight up (see BattleDriver.assess): how often, in
         * seconds; how much of its hull it can expect to lose before it
         * flees; how many rooms a beam's taken to hit; how many shields' worth
         * a missile or bomb counts for in a volley (they go through them);
         * and the threat a ship's counted as having at least, so the ones
         * that can't hurt it are still killed in order.
         */
        const val ASSESS_EVERY = 1f
        const val FLEE_LOSS = 0.6f
        const val BEAM_ROOMS = 2
        const val SHOTS_PAST_SHIELDS = 3
        const val THREAT_FLOOR = 0.01f

        /**
         * How much stronger than its crew the enemies' boarders can be
         * before it flees (see BattleDriver.boardingStrength), and how many
         * teleporter loads each ship's counted as sending.
         */
        const val FLEE_BOARDERS = 0.8f
        const val BOARDING_WAVES = 2

        /**
         * Below how much of their health the player's crew need the medbay,
         * even in a fight (see BattleDriver.playerTactics), as the crew's AI
         * sends them in the playtest (see FriendlyCrewAI).
         */
        const val HURT_CREW = 0.5f

        /**
         * A ship spec for an enemy [blueprint], with its own reward screens
         * for when it's destroyed or its crew are killed.
         */
        fun enemySpec(game: InGameState, blueprint: String, description: String): EnemyShipSpec {
            fun outcome(name: String, text: String) = Element(name).apply {
                addContent(Element("text").setText(text))
                addContent(Element("autoReward").setAttribute("level", "HIGH").setText("standard"))
            }

            val elem = Element("ship")
                .setAttribute("name", "XFTL_VIDEO_$blueprint")
                .setAttribute("auto_blueprint", blueprint)
            elem.addContent(outcome("destroyed", "$description breaks apart under the Chonkestrel's guns."))
            elem.addContent(outcome("deadCrew", "$description drifts silently, its crew dead."))
            return EnemyShipSpec(elem, game.blueprintManager, game.eventManager)
        }

        /**
         * The ffmpeg command that encodes raw RGBA frames (bottom row first,
         * as OpenGL reads them, hence the vflip) from stdin into [file].
         */
        fun ffmpegCommand(file: File, width: Int, height: Int, fps: Int): List<String> = listOf(
            System.getenv("XFTL_FFMPEG") ?: "ffmpeg", "-y", "-loglevel", "warning",
            "-f", "rawvideo", "-pix_fmt", "rgba", "-s", "${width}x$height", "-r", "$fps", "-i", "-",
            "-vf", "vflip", "-c:v", "libx264", "-preset", "medium", "-crf", "24",
            "-pix_fmt", "yuv420p", "-movflags", "+faststart",
            file.absolutePath
        )

        /**
         * Start the video recorder, if XFTL_VIDEO is set. This is called
         * once the game has started.
         */
        @JvmStatic
        fun install(main: MainGame, container: GameContainer) {
            val directory = XftlDebug.videoDirectory ?: return
            val lwjgl = container as? LWJGLGameContainer ?: return

            val director = VideoDirector(
                main, videoFile(File(directory), Trailer.NAME), timeline = Trailer.timeline(), length = Trailer.LENGTH
            )
            lwjgl.fixedDeltaSec = 1f / director.fps
            lwjgl.afterRender = director::afterRender
        }

        /**
         * Where to save a video: in the [directory], named [name] and the
         * time, such as trailer-20261024-173718.mp4.
         */
        fun videoFile(directory: File, name: String, time: LocalDateTime = LocalDateTime.now()): File {
            val timestamp = time.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            return File(directory, "$name-$timestamp.mp4")
        }

        /**
         * The strongest ship of each race (the ones with crew), for the
         * screenshots' fights, which pick them at random.
         */
        val STRONG_SHIPS = listOf(
            "REBEL_FAT" to "The Rebel cruiser",
            "MANTIS_BOMBER" to "The Mantis bomber",
            "ROCK_ASSAULT" to "The Rockman assault ship",
            "ANAEROBIC_BOMBER" to "The Lanius bomber",
            "CIRCLE_BOMBER" to "The Engi bomber",
            "ZOLTAN_BOMBER" to "The Zoltan bomber",
            "JELLY_TRUFFLE" to "The Slug cruiser",
            "CRYSTAL_BOMBER" to "The Crystal bomber",
        )
    }
}
