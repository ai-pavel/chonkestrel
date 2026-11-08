package xyz.znix.xftl.environment

import xyz.znix.xftl.*
import xyz.znix.xftl.game.EnergySource
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.ReactorEnergySource
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.RadiationDamage
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.systems.Clonebay
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.systems.Hacking
import xyz.znix.xftl.systems.Medbay
import xyz.znix.xftl.sys.GameContainer
import kotlin.random.nextInt

/**
 * A nebula, and the kinds of nebula based on it: ion storms from vanilla,
 * and XFTL's own (see doc/game/nebulas.md):
 *
 * - Radiation nebulas slowly hurt the crew of every ship, outside their medbay
 *   or clonebay.
 * - Mirage nebulas show false ships and hazards on the beacon map.
 * - Dark nebulas cut off drones, as well as sensors.
 *
 * XFTL's nebulas use the normal nebula's clouds, tinted.
 */
class NebulaEnvironment(game: InGameState, beacon: Beacon, override val type: Beacon.EnvironmentType) :
    AbstractEnvironment(game, beacon) {

    val ionStorm: Boolean get() = type == Beacon.EnvironmentType.ION_STORM

    private val tint: Colour = cloudTint(type)

    private val clouds = ArrayList<Cloud>()

    // These are loaded lazily, so the environment can be created in tests
    private val normalImage by lazy { game.getImg("img/stars/nebula_large_c.png") }
    private val stormImage by lazy { game.getImg("img/stars/nebula_large_b.png") }
    private val stormFlashImage by lazy { game.getImg("img/stars/nebula_large_a.png") }
    private val lightningImage by lazy { game.getImg("img/stars/lightning.png") }

    init {
        for (xIdx in 0 until 5) {
            for (yIdx in 0 until 4) {
                val pos = ConstPoint(
                    xIdx * 350 + VisualRandom.nextInt(-60..60),
                    yIdx * 230 + VisualRandom.nextInt(-40..40)
                )
                clouds += Cloud(pos)
            }
        }

        // Don't use a left-to-right order, as this determines which clouds
        // are drawn on over each other.
        clouds.shuffle()

        // Do a few initial updates so the clouds start in a steady state
        for (i in 0 until 50) {
            updateClouds(0.5f)
        }
    }

    // A nebula has no background picture, only its clouds
    override fun renderBackdrop(gc: GameContainer, g: Graphics) {
    }

    override fun renderScenery(gc: GameContainer, g: Graphics) {
        // The cloud positions are laid out for a 1280x720 screen, so
        // spread them out to cover the real screen. Scale the clouds up
        // by the same amount, so they don't leave gaps between them.
        val spreadX = gc.width / BASE_SCREEN_WIDTH
        val spreadY = gc.height / BASE_SCREEN_HEIGHT
        val sizeScale = maxOf(spreadX, spreadY)

        for (cloud in clouds) {
            g.pushTransform()
            g.translate(cloud.centre.x * spreadX, cloud.centre.y * spreadY)
            g.scale(cloud.scale * sizeScale, cloud.scale * sizeScale)

            val lightning = cloud.lightingTimer > 0f && ionStorm
            val cloudImage = when {
                lightning -> stormFlashImage
                ionStorm -> stormImage
                else -> normalImage
            }

            val filter = pauseShade(game)
            filter.r *= tint.r
            filter.g *= tint.g
            filter.b *= tint.b
            filter.a = cloud.alpha * tint.a
            cloudImage.draw(-cloudImage.width / 2, -cloudImage.height / 2, filter)

            // Draw the lightning image on top
            if (lightning) {
                g.rotateRadians(0f, 0f, cloud.lightningRotation)
                lightningImage.draw(-lightningImage.width / 2, -lightningImage.height / 2, filter)
            }

            g.popTransform()
        }
    }

    override fun update(dt: Float) {
        super.update(dt)
        updateClouds(dt)

        if (type == Beacon.EnvironmentType.RADIATION) {
            irradiateCrew(dt)
        }
    }

    /**
     * Hurt the crew of every ship at the beacon who aren't in a medbay or a
     * clonebay, which are shielded against it, more in the second act.
     */
    private fun irradiateCrew(dt: Float) {
        val damage = RADIATION_DAMAGE * intensity * dt
        for (ship in listOf(game.player) + game.allEnemyShips) {
            for (crew in ship.crew.toList()) {
                if (crew !is LivingCrew || crew.room.system is Medbay || crew.room.system is Clonebay)
                    continue
                crew.dealDamage(RadiationDamage(damage))
            }
        }
    }

    /**
     * In the second act, nebulas are thicker, and the FTL drive charges slower.
     */
    override val ftlChargeMultiplier: Float
        get() = if (intensity > 1) THICK_NEBULA_FTL_CHARGE else 1f

    override fun systemPowerLimit(system: AbstractSystem): Int? {
        // Dark nebulas cut drones off from their ships, the hacking
        // system's probe too
        if (type == Beacon.EnvironmentType.DARK && (system is Drones || system is Hacking))
            return 0
        return null
    }

    private fun updateClouds(dt: Float) {
        // Use indices so we can add new clouds
        var offset = 0
        for (idx in clouds.indices) {
            val cloud = clouds[idx + offset]
            cloud.update(dt)

            // Does this cloud need to be replaced?
            if (!cloud.isFadingIn && !cloud.isReplaced && cloud.alpha < cloud.replaceThreshold) {
                cloud.isReplaced = true

                clouds.add(0, Cloud(cloud.centre))
                offset++
            }
        }

        clouds.removeIf { it.alpha == 0f && !it.isFadingIn }
    }

    override fun adjustShipPower(ship: Ship, powerAvailableTypes: HashMap<EnergySource, Int>) {
        if (!ionStorm)
            return

        var reactorPower = powerAvailableTypes[ReactorEnergySource] ?: 0
        val toDeduct = reactorPower / 2 // Rounds down
        reactorPower -= toDeduct
        powerAvailableTypes[ReactorEnergySource] = reactorPower
    }

    private class Cloud(
        val centre: IPoint
    ) {
        var alpha = 0f
        var scale = (3f..4f).random(VisualRandom)

        var isFadingIn: Boolean = true
        var isReplaced: Boolean = false

        var nextLightning = 0f
        var lightingTimer = 0f
        var lightningRotation = 0f

        val replaceThreshold = (0.60f..0.90f).random(VisualRandom)

        fun update(dt: Float) {
            scale += 0.048f * dt

            if (isFadingIn) {
                alpha += 0.80f * dt
                if (alpha >= 1f) {
                    isFadingIn = false
                }
            } else if (isReplaced) {
                alpha -= 0.16f * dt
            } else {
                alpha -= 0.032f * dt
            }

            // Always run the lightning code, and just don't render it if
            // we're not in a plasma storm.
            nextLightning -= dt
            if (nextLightning <= 0) {
                lightingTimer = 0.25f
                nextLightning = (6.25f..18.75f).random(VisualRandom)
                lightningRotation = (0..3).random() * (PIf / 2f)
            }
            lightingTimer = (lightingTimer - dt).coerceAtLeast(0f)

            alpha = alpha.coerceIn(0f..1f)
        }
    }

    // Don't bother serialising the clouds, since they're only visual.

    companion object {
        /**
         * How much health crew lose per second in a radiation nebula, in the
         * first act (it's multiplied by the [intensity]). Crew have 100.
         */
        const val RADIATION_DAMAGE = 0.6f

        /**
         * How fast the FTL drive charges in a nebula in the second act.
         */
        const val THICK_NEBULA_FTL_CHARGE = 0.75f

        /**
         * The colour to tint a nebula's clouds.
         */
        fun cloudTint(type: Beacon.EnvironmentType): Colour = when (type) {
            Beacon.EnvironmentType.RADIATION -> Colour(0.55f, 1f, 0.4f)
            Beacon.EnvironmentType.MIRAGE -> Colour(0.85f, 0.45f, 1f)
            Beacon.EnvironmentType.DARK -> Colour(0.3f, 0.3f, 0.35f)
            else -> Colour(1f, 1f, 1f)
        }

        /**
         * The colour of the circle under a nebula beacon on the beacon map.
         */
        /**
         * The tint of a nebula's cloud on the map: vanilla's own colours for
         * plain nebulas, and XFTL's kinds' colours for theirs.
         */
        fun mapCloudColour(type: Beacon.EnvironmentType): Colour = when (type) {
            Beacon.EnvironmentType.RADIATION -> Colour(0.5f, 1f, 0.4f, 1f)
            Beacon.EnvironmentType.MIRAGE -> Colour(0.9f, 0.5f, 1f, 1f)
            Beacon.EnvironmentType.DARK -> Colour(0.35f, 0.35f, 0.4f, 1f)
            else -> Colour.white
        }

        fun mapCircleColour(type: Beacon.EnvironmentType): Colour = when (type) {
            Beacon.EnvironmentType.RADIATION -> Colour(0.2f, 0.9f, 0.1f, 0.3f)
            Beacon.EnvironmentType.MIRAGE -> Colour(0.7f, 0.1f, 1f, 0.3f)
            Beacon.EnvironmentType.DARK -> Colour(0.05f, 0.05f, 0.08f, 0.6f)
            else -> Constants.BEACON_NEBULA_CIRCLE
        }
    }
}
