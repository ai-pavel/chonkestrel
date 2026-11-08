package xyz.znix.xftl.environment

import org.jdom2.Element
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.EnergySource
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.EnvironmentImage
import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.sector.FleetBackground
import xyz.znix.xftl.sector.ImageList
import xyz.znix.xftl.sys.GameContainer
import kotlin.math.roundToInt
import kotlin.random.Random

abstract class AbstractEnvironment(val game: InGameState, val beacon: Beacon) {
    abstract val type: Beacon.EnvironmentType

    /**
     * True if this is one of the two environments of an extreme hazard (see
     * [MixedEnvironment]), which are stronger. This comes from the beacon,
     * as it's needed while the environments are being made.
     */
    private val isPartOfMix: Boolean get() = beacon.isExtremeHazard

    /**
     * How strong this environment's hazards are: 1 (as in vanilla) in the
     * first act, and 2 in the second, or in an extreme hazard. See
     * [GameMap.environmentIntensity].
     */
    val intensity: Int
        get() {
            val forAct = game.gameMap.environmentIntensity(beacon.sector.info)
            return if (isPartOfMix) maxOf(forAct, EXTREME_HAZARD_INTENSITY) else forAct
        }

    /**
     * How many times as often a sun's flares and a pulsar's pulses come: twice
     * as often in an extreme hazard.
     */
    val hazardRate: Float get() = if (isPartOfMix) EXTREME_HAZARD_RATE else 1f

    private var backgroundImage: EnvironmentImage? = null
    private var planetImage: EnvironmentImage? = null
    private val backgroundShips = ArrayList<BackgroundShip>()

    init {
        var backgroundList: ImageList = game.eventManager.getImageList("BACKGROUND")
        var planetList: ImageList = game.eventManager.getImageList("PLANET")

        beacon.event.backImg?.let { backgroundList = it }
        beacon.event.planetImg?.let { planetList = it }

        val rand = Random(beacon.environmentSeed)

        backgroundImage = backgroundList.getRandom(rand.nextInt())
        planetImage = planetList.getRandom(rand.nextInt())

        backgroundShips.clear()
        val fleet = beacon.event.fleetBackground
        if (fleet != null) {
            initFleet(fleet, rand)
        }
    }

    private fun initFleet(fleet: FleetBackground, rand: Random) {
        val rebelShips = listOf(
            game.getImg("img/ship/fleet/fleet_1_small.png"),
            game.getImg("img/ship/fleet/fleet_2_small.png"),
            game.getImg("img/ship/fleet/fleet_1_med.png"),
            game.getImg("img/ship/fleet/fleet_2_med.png")
        )
        val federationShips = listOf(
            game.getImg("img/ship/fleet/fleetfed_1_small.png"),
            game.getImg("img/ship/fleet/fleetfed_2_small.png"),
            game.getImg("img/ship/fleet/fleetfed_1_med.png"),
            game.getImg("img/ship/fleet/fleetfed_2_med.png")
        )

        fun addShip(row: Int, column: Int, fed: Boolean) {
            val image = when (fed) {
                true -> federationShips.random(rand)
                else -> rebelShips.random(rand)
            }

            backgroundShips.add(BackgroundShip(image, column, row, rand.nextFloat(), rand.nextFloat()))
        }

        for (x in 0 until 3) {
            for (y in 0 until 3) {
                val isFed = when (fleet) {
                    FleetBackground.REBEL -> false
                    FleetBackground.FEDERATION -> true
                    FleetBackground.BOTH -> when (x) {
                        0 -> false
                        2 -> true
                        else -> rand.nextBoolean()
                    }
                }
                addShip(y, x, isFed)
            }
        }
    }

    /**
     * Draw an image (normally a 1280x720 background) scaled up to cover the
     * whole screen, keeping its aspect ratio. If the screen is a different
     * shape to the image, the image is centred and its edges are cut off.
     */
    protected fun drawCoveringScreen(gc: GameContainer, image: Image, filter: Colour) {
        val scale = maxOf(gc.width.toFloat() / image.width, gc.height.toFloat() / image.height)
        val width = image.width * scale
        val height = image.height * scale
        image.draw((gc.width - width) / 2, (gc.height - height) / 2, width, height, filter)
    }

    /**
     * Draw a full-screen effect, given its position and size on the
     * 1280x720 screen FTL was designed for. This stretches it to fit
     * the actual screen size.
     */
    protected fun drawStretchedToScreen(
        gc: GameContainer, image: Image,
        x: Float, y: Float, width: Float, height: Float,
        filter: Colour
    ) {
        val scaleX = gc.width / BASE_SCREEN_WIDTH
        val scaleY = gc.height / BASE_SCREEN_HEIGHT
        image.draw(x * scaleX, y * scaleY, width * scaleX, height * scaleY, filter)
    }

    open fun pauseShade(game: InGameState): Colour {
        val s = game.player // s for ship
        val paused = game.isPaused
        val shade = if (paused) (1.0f - s.health.toFloat() / s.maxHealth.toFloat()) * 0.4f + 0.4f else 1.0f

        return Colour(shade, shade, shade)
    }

    /**
     * The environments this is made of: just this one, or two for a
     * [MixedEnvironment].
     */
    open val parts: List<AbstractEnvironment> get() = listOf(this)

    open fun renderBackground(gc: GameContainer, g: Graphics) {
        renderBackdrop(gc, g)
        renderScenery(gc, g)
    }

    /**
     * Draw what's in front of the backdrop, such as asteroids or a star.
     */
    open fun renderScenery(gc: GameContainer, g: Graphics) {
    }

    /**
     * Draw the picture behind everything else: the beacon's background,
     * planet and fleet, or an environment's own background.
     */
    open fun renderBackdrop(gc: GameContainer, g: Graphics) {
        // Here the background scenery is rendered with a
        // pause-darkness that depends on the hull health.
        val ps = pauseShade(game)
        backgroundImage?.getImg(game)?.let { drawCoveringScreen(gc, it, ps) }
        planetImage?.getImg(game)?.draw(0f, 0f, ps)

        // Background rebel/federation ships live on a 3x3 grid
        // We can't pick a random position within that grid when we populate
        // this list, as we don't know the screen size, so instead we store
        // a float of it's position within that cell in each axis.
        val bgShipColumnWidth = gc.width / 3
        val bgShipRowHeight = gc.height / 3
        for (ship in backgroundShips) {
            val x = ((ship.gridX + ship.offsetX) * bgShipColumnWidth).roundToInt()
            val y = ((ship.gridY + ship.offsetY) * bgShipRowHeight).roundToInt()

            ship.image.drawAlignedCentred(x, y, ps)
        }

        // TODO: Get the big fleet ships working (those are unaffected by the pause darkness)
        // Mods like Multiverse use quite a lot of them with hyperspace,
        // so this will sooner or later need attention. Example image here:
        // https://cdn.discordapp.com/attachments/859493631215534110/1256333357986480220/Bildschirmfoto_2024-06-28_um_21.40.07.png
    }

    /**
     * Render something on top of everything else, including the UI.
     *
     * Used for solar flares and pulsars.
     */
    open fun renderOverlay(gc: GameContainer, g: Graphics) {
    }

    open fun update(dt: Float) {
    }

    /**
     * Adjust a ship's available power, before systems use it.
     *
     * This is how plasma storms reduce a ship's power.
     */
    open fun adjustShipPower(ship: Ship, powerAvailableTypes: HashMap<EnergySource, Int>) {
    }

    /**
     * The most power a system can have here, or null for no limit. This
     * applies to every ship at the beacon.
     */
    open fun systemPowerLimit(system: AbstractSystem): Int? = null

    /**
     * How fast ships' FTL drives charge here, as a multiple of normal.
     */
    open val ftlChargeMultiplier: Float get() = 1f

    open fun saveToXML(elem: Element) {
    }

    open fun loadFromXML(elem: Element) {
    }

    private class BackgroundShip(
        val image: Image,
        val gridX: Int,
        val gridY: Int,
        val offsetX: Float,
        val offsetY: Float
    )

    companion object {
        /**
         * The screen size FTL's graphics were designed for.
         */
        const val BASE_SCREEN_WIDTH = 1280f
        const val BASE_SCREEN_HEIGHT = 720f

        // How much stronger the environments of an extreme hazard are
        const val EXTREME_HAZARD_INTENSITY = 2
        const val EXTREME_HAZARD_RATE = 2f
    }
}
