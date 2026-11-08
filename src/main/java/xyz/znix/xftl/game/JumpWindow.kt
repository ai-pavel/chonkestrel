package xyz.znix.xftl.game

import xyz.znix.xftl.*
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.environment.NebulaEnvironment
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.rendering.StandardTooltip
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.sector.Mirage
import xyz.znix.xftl.sector.Sector
import xyz.znix.xftl.sys.Input
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// Note that the actual window appears at 340, if we want to be resizable we'll have to fix
// that (and the height). Currently we run much smaller than FTL so their size doesn't fit
// for us atm.
class JumpWindow(
    val game: InGameState,
    showSectorMap: () -> Unit,
    val jump: (Beacon?) -> Unit
) : Window() {
    // Sectors are bigger than in vanilla, so the window is too
    override val size = ConstPoint(752, 534) + Sector.EXTRA_MAP_SIZE

    private val sectorInfoTab = game.getImg("img/map/side_sector.png")
    private val titleTab = game.getImg("img/map/side_beaconmap.png")
    private val nextSectorTab = game.getImg("img/map/side_nextsector.png")
    private val font = game.getFont("HL2", 3f)
    private val cancelButtonOutline = game.getImg("img/main_menus/button_cancel_base.png")
    private val sectorInfoFont = game.getFont("c&cnew", 2f)
    private val beaconLabelFont = game.getFont("HL1")
    private val distressBeaconFont = game.getFont("HL1", 2f) // Out-of-fuel UI

    private val outlineImage = game.getImg("img/window_outline.png")

    private val lineImg = game.getImg("img/map/dotted_line.png")
    private val targetBox = game.getImg("img/map/map_targetbox.png")

    private val fleetAdvanceImg = game.getImg("img/map/map_warningcircle_point.png")
    private val fleetAdvanceColour = Colour(246, 128, 125, 21)
    private val fleetControlImg = game.getImg("img/map/map_warningcircle.png")
    private val fleetControlTile = game.getImg("img/map/map_warningcircle_tile.png")

    private val beaconShadow = game.getImg("img/map/map_icon_diamond_shadow.png")
    private val beaconEnvironmentHazard = game.getImg("img/map/map_icon_hazard.png")
    private val beaconYellow = game.getImg("img/map/map_icon_diamond_yellow.png")
    private val beaconBlue = game.getImg("img/map/map_icon_diamond_blue.png")
    private val beaconDanger = game.getImg("img/map/map_icon_triangle_red.png")
    private val beaconShipPresent = game.getImg("img/map/map_icon_triangle_yellow.png")
    private val beaconWillOvertakeCircle = game.getImg("img/tutorial/player_circle.png") // This is the right image!
    private val beaconOvertaken = game.getImg("img/map/map_icon_warning.png")

    private val playerShip = game.getImg("img/map/map_icon_ship.png")
    private val playerShipNoFuel = game.getImg("img/map/map_icon_ship_fuel.png")

    private val noFuelImage = game.getImg("img/map/map_fuel_text_nofuel.png")
    private val waitDistressFrame = game.getImg("img/map/side_wait_distress.png")
    private val fuelDistressFlashLight = game.getImg("img/map/side_distressbeacon_redlight.png")

    private val beaconOffset = Point(-beaconYellow.width / 2, -beaconYellow.height / 2)

    // The offsets to the coordinate system the beacons are positioned on
    private val mapBase = Point(0, 0)

    private val sector = game.currentBeacon.sector

    private val flashTimerBase = System.nanoTime()

    // The set of all the beacons we've visited a neighbour of - this
    // is used to figure out which beacons to display information about.
    private val neighbourVisSet = sector.beacons.filter { it.visited }.flatMap { it.neighbours }.toSet()

    private val labelWhite = (1..3).map { game.getImg("img/map/map_box_white_$it.png") }
    private val labelGreen = (1..3).map { game.getImg("img/map/map_box_green_$it.png") }
    private val labelPurple = (1..3).map { game.getImg("img/map/map_box_purple_$it.png") }

    val cancelButton: Button
    val nextSectorButton: Button

    // In debug mode, a button that jumps straight to the sector's exit beacon

    val noFuelButtons: List<Button>

    val background = game.getImg("img/map/zone_1.png")

    /**
     * True if the player's out-of-fuel distress beacon is enabled.
     */
    var fuelDistressOn = false

    private var fuelDistressFlashTimer: Float = 0f
    private val fuelDistressFlashOn: Boolean get() = fuelDistressOn && fuelDistressFlashTimer < 0.5f

    var hovered: Beacon? = null

    private var playerRotation: Float = (0f..10f).random(VisualRandom)

    // Bigger ships need more than one fuel to jump
    private val outOfFuel: Boolean get() = game.player.fuelCount < game.player.fuelPerJump

    // The next sector button is only available from the exit beacon, and
    // only if there's fuel to jump with.
    // In debug mode, the sector map can be opened from any beacon, and any
    // sector can be jumped to from it (see SectorMapWindow).
    // A boss holding the exit keeps the player in the sector until it's
    // beaten (see ColossalBoss)
    private val showNextSectorButton: Boolean
        get() = (game.currentBeacon.isExit && !game.isHeldByBoss(game.currentBeacon) || XftlDebug.enabled) && !outOfFuel

    init {
        // The buttons are fiddly to set up, as they move depending on
        // the length of the text inside them.

        // Cancel button
        val cancelText = game.translator["button_cancel"]
        val cancelTextWidth = font.getWidth(cancelText)
        val cancelButtonWidth = cancelTextWidth + 3 * 2
        cancelButton = Buttons.BasicButton(
            game, size + ConstPoint(-31 - cancelButtonWidth, 7),
            ConstPoint(cancelButtonWidth, 30), cancelText,
            3, font, 24,
            ::cancelClicked
        )
        buttons += cancelButton

        // Next sector button
        val nsText = game.translator["button_nextsector"]
        val nsTextWidth = font.getWidth(nsText)
        val nsButtonWidth = nsTextWidth + 4 * 2
        nextSectorButton = Buttons.BasicButton(
            game, ConstPoint(size.x - 12 - nsButtonWidth, 12),
            ConstPoint(nsButtonWidth, 36), nsText,
            3, font, 27, showSectorMap
        )

        if (showNextSectorButton)
            buttons += nextSectorButton


        // Hacky, the rendering code is also what populates the buttons
        noFuelButtons = ArrayList()
        drawNoFuelUI(noFuelButtons)
        if (outOfFuel)
            buttons += noFuelButtons
    }

    /**
     * Draw a cloud round each beacon in a nebula, as vanilla does, so the
     * nebulas can be seen on the map at a glance. Each beacon gets one of the
     * clouds, picked by where it is (so it's always the same one), tinted
     * for XFTL's own kinds of nebula.
     */
    private fun drawNebulaClouds() {
        for (beacon in sector.beacons) {
            val nebula = beacon.nebulaType ?: continue
            val index = Math.floorMod(beacon.pos.x * 31 + beacon.pos.y * 17, NEBULA_CLOUDS)
            val cloud = game.getImg("img/map/nebula_$index.png")
            val x = mapBase.x + beacon.pos.x - cloud.width / 2f
            val y = mapBase.y + beacon.pos.y - cloud.height / 2f
            val tint = NebulaEnvironment.mapCloudColour(nebula)
            cloud.draw(x, y, tint)
        }
    }

    /**
     * Draw the insides of the window. This must be done with a stencil.
     */
    private fun drawMapContent(g: Graphics) {
        // Draw the background image, offset 4px to account for the line wall of the window.
        // It's the size of a vanilla window, so scale it up evenly to cover
        // our bigger window (and the stencil cuts off the excess).
        val backgroundScale = maxOf(size.x.f / background.width, size.y.f / background.height)
        background.draw(
            position.x + 4f, position.y + 4f,
            background.width * backgroundScale, background.height * backgroundScale
        )

        mapBase.x = position.x + Sector.OFFSET.x
        mapBase.y = position.y + Sector.OFFSET.y

        // In the last stand, the danger stripes sit behind everything else.
        // TODO draw the player and boss ships below this.
        if (sector.isLastStand) {
            drawLastStandDangerZone(g)
        }

        // The nebulas' clouds, behind the beacons and the lines between them
        drawNebulaClouds()

        // Draw the connections to the adjacent beacons.
        drawBeaconLinesTo(g, game.currentBeacon, Constants.BEACON_LINE_PLAYER) { true }

        // Draw the line showing where the flagship will next jump
        for (boss in sector.bosses) {
            val currentBeacon = boss.beacon ?: continue
            val nextBeacon = boss.nextBeacon ?: continue

            if (!boss.jumping) {
                // Draw a dotted line if the flagship isn't jumping this turn
                drawBeaconLine(
                    g,
                    currentBeacon,
                    nextBeacon,
                    Constants.BEACON_LINE_FLAGSHIP
                )
            } else {
                // Draw a wide, continuous line if the flagship is jumping this turn.
                val a = mapBase + currentBeacon.pos
                val b = mapBase + nextBeacon.pos

                boss.drawJumpArc(g, a, b)
            }
        }

        // Make a local immutable copy to avoid nullability errors
        val hovered = hovered

        if (hovered != null && hovered != game.currentBeacon) {
            // Draw the lines between the hovered beacon and it's neighbours
            drawBeaconLinesTo(g, hovered, Constants.BEACON_LINE_HOVER) { it != game.currentBeacon }
        }

        val nextFleetPos = Point(sector.dangerZoneCentre)
        nextFleetPos.x += sector.getFleetAdvanceFor(game.currentBeacon, game.player.type.shipSize)

        // Whether or not the ship is equipped with long-range scanners
        val hasLRS = game.player.hasAugment(AugmentBlueprint.LONG_RANGE_SCANNERS)

        // Whether the player can tell what's at the Silent Capital's
        // Chorus-held beacons (see Mirage.seesThroughChorus)
        val seesThroughChorus = Mirage.seesThroughChorus(game.player)

        // Draw the beacons
        pendingLabels.clear()
        for (beacon in sector.beacons) {
            val centrePos = mapBase + beacon.pos
            val pos = centrePos + beaconOffset

            // In debug mode, every beacon is scanned, as if the map was
            // revealed and the long-range scanners reached everywhere.
            val isNeighbour = neighbourVisSet.contains(beacon)
            val scanned = beacon.visited || sector.mapRevealed || XftlDebug.enabled
            val showBasicInfo = scanned || isNeighbour
            // The long-range scanners only reach the beacons next to the
            // ship, not every one it's ever been next to
            val showAdvInfo = scanned || (hasLRS && beacon in game.currentBeacon.neighbours)

            // Mirage nebulas show false ships and hazards (see Mirage), as
            // do the Silent Capital's Chorus-held beacons. The beacons in
            // them show them even if they haven't been scanned.
            val deceptive = Mirage.isDeceptive(beacon, game.currentBeacon, seesThroughChorus)
            val showFakes = deceptive && (showBasicInfo || beacon.nebulaType == Beacon.EnvironmentType.MIRAGE ||
                    beacon in sector.chorusHeld)
            val showsHazard = when {
                deceptive -> showFakes && Mirage.showsFakeHazard(beacon)
                else -> showAdvInfo && beacon.isDangerous
            }
            val showsShip = when {
                deceptive -> showFakes && Mirage.showsFakeShip(beacon)
                else -> showAdvInfo && beacon.event.loadShipName != null
            }

            // Draw the little transparent circle below nebulas, coloured by
            // the kind of nebula
            beacon.nebulaType?.let { nebula ->
                g.colour = NebulaEnvironment.mapCircleColour(nebula)
                g.fillOval(centrePos.x - 9f, centrePos.y - 9f, 20f, 20f)
            }

            // Extreme hazards (two at once) can be seen from afar, like
            // nebulas, so they're always marked, in red, or purple for a
            // catastrophic one (three at once), or cyan for an apocalyptic
            // one (all four)
            if (beacon.isExtremeHazard && !deceptive) {
                val colour = when {
                    beacon.isApocalypticHazard -> APOCALYPTIC_HAZARD_COLOUR
                    beacon.isCatastrophicHazard -> CATASTROPHIC_HAZARD_COLOUR
                    else -> EXTREME_HAZARD_COLOUR
                }
                beaconEnvironmentHazard.draw(pos.x - 5, pos.y - 5, colour)
            } else if (showsHazard) {
                beaconEnvironmentHazard.draw(pos.x - 5, pos.y - 5)
            }

            // Draw the flashing background if this beacon
            // will be overtaken after this jump.
            val willBeOvertaken = beacon.pos.distToSq(nextFleetPos) < Sector.DANGER_ZONE_RADIUS_SQUARED
            if (willBeOvertaken && beacon.state != Beacon.State.OVERTAKEN) {
                val baseTimer = (System.nanoTime() - flashTimerBase) / 1000000000f
                val flashTimer = (baseTimer + beacon.overtakeFlashAnimationOffset).rem(2)

                val opacity = if (flashTimer < 1f) flashTimer else 2f - flashTimer

                beaconWillOvertakeCircle.draw(
                    pos.x + 1f, pos.y + 2f, pos.x + 31f, pos.y + 32f,
                    0f, 0f, 40f, 40f,
                    opacity, Colour.white
                )
            }

            beaconShadow.draw(pos)

            val beaconImg = when (beacon.state) {
                Beacon.State.UNVISITED -> when {
                    showsShip -> beaconShipPresent
                    else -> beaconYellow
                }

                Beacon.State.VISITED_CLEAR -> beaconBlue
                Beacon.State.VISITED_DANGER -> beaconDanger
                Beacon.State.OVERTAKEN -> beaconOvertaken
            }
            beaconImg.draw(pos)

            // In debug mode, the beacons whose events can start a quest are
            // marked: with a star on the beacon's label, if it has one, so
            // the two don't overlap, or else "QUEST*"
            var questStar = XftlDebug.enabled && !beacon.hasQuest && !beacon.visited && beacon.event.givesQuest
            fun label(images: List<Image>, colour: Colour, text: String) {
                drawBeaconLabel(images, colour, pos, if (questStar) "$text*" else text)
                questStar = false
            }

            if (beacon.event.isDistressBeacon && !beacon.visited && showBasicInfo && !willBeOvertaken && !deceptive)
                label(labelWhite, Constants.SECTOR_CUTOUT_TEXT, game.translator["map_icon_distress"])

            if (beacon.event.isLastStandRepair && !beacon.visited && !willBeOvertaken)
                label(labelWhite, Constants.SECTOR_CUTOUT_TEXT, game.translator["map_icon_repair"])

            // Note that quests and stores are cleared when the beacon is overrun,
            // so we don't have to check if willBeOvertaken is set.
            if (beacon.hasQuest && !beacon.visited)
                label(labelPurple, Constants.SECTOR_CUTOUT_TEXT_PURPLE, game.translator["map_icon_quest"])

            if (beacon.hasStore && showBasicInfo && !deceptive)
                label(labelWhite, Constants.SECTOR_CUTOUT_TEXT, game.translator["map_icon_store"])

            // (an exit a boss holds is still labelled EXIT: its tooltip
            // says the boss is there, see bossAt)
            if (beacon.isExit)
                label(labelGreen, Constants.SECTOR_CUTOUT_TEXT_GREEN, game.translator["map_icon_exit"])

            if (beacon.isBase)
                label(labelPurple, Constants.SECTOR_CUTOUT_TEXT_PURPLE, game.translator["map_icon_base"])

            if (questStar)
                label(labelPurple, Constants.SECTOR_CUTOUT_TEXT_PURPLE, game.translator["map_icon_quest"])

            if (beacon == hovered) {
                val text = beaconDescription(beacon, deceptive, showBasicInfo, showAdvInfo, showsShip, willBeOvertaken)
                g.tooltip = object : StandardTooltip(game) {
                    override fun getText(): String = text
                }
            }

            if (beacon == hovered && beacon != game.currentBeacon && canJumpTo(beacon)) {
                drawTargetBox(g, pos)
            }

            // Draw the player ship rotating around the beacon.
            val rotationSpeed = TWO_PI / 20f // Go around every 20 seconds
            if (beacon == game.currentBeacon) {
                val icon = when (fuelDistressFlashOn) {
                    true -> playerShipNoFuel
                    false -> playerShip
                }

                // The player's ship doesn't rotate when they're out of fuel
                if (!outOfFuel) {
                    playerRotation += game.renderingDeltaTime * rotationSpeed
                }

                // These offsets are approximate
                g.pushTransform()
                g.rotate(centrePos.x.f, centrePos.y.f, -playerRotation)
                icon.draw(centrePos.x - 8, centrePos.y - 32)
                g.popTransform()
            }

            // Draw the flagship rotating around the beacon.
            for (boss in sector.bosses) {
                if (boss.beacon != beacon || boss.jumping)
                    continue

                boss.drawMapIcon(g, centrePos)
            }
        }

        // The fleet advance goes over the beacons and the circling ship, but
        // under the beacons' labels, which are drawn after it
        // (there's none where a boss hunts the player instead, see HunterBoss)
        if (!sector.isLastStand && sector.type.hasFleet) {
            drawDangerZone(g)
        }
        for (label in pendingLabels)
            label()
        pendingLabels.clear()

        // For debugging, this can draw the grid the sectors fit in
        if (game.debugFlags.showMapGrid.set) {
            for (x in 0 until Sector.GRID_SIZE.x) {
                for (y in 0 until Sector.GRID_SIZE.y) {
                    g.colour = Colour.red
                    g.drawRect(mapBase.x + x * 110f, mapBase.y + y * 110f, 110f, 110f)
                }
            }
        }

        // If we're out of fuel, tint the map black and draw the text on top
        if (outOfFuel) {
            g.colour = Constants.JUMP_NO_FUEL_TINT
            g.fillRect(position.x, position.y, size.x, size.y)

            noFuelImage.drawAlignedCentred(
                position.x + size.x / 2, position.y + size.y / 2,
                noFuelImage.width * 3f, noFuelImage.height * 3f
            )
        }
    }

    private fun drawDangerZone(g: Graphics) {
        val dangerZoneRHS = sector.dangerZoneCentre.x + Sector.DANGER_ZONE_RADIUS
        val nextDangerZoneRHS = dangerZoneRHS + sector.getFleetAdvanceFor(game.currentBeacon, game.player.type.shipSize)
        fleetAdvanceImg.draw(
            mapBase.x + nextDangerZoneRHS - 181f,
            mapBase.y + sector.dangerZoneCentre.y - 498f
        )
        g.colour = fleetAdvanceColour
        g.fillRect(
            position.x.f,
            position.y.f,
            (mapBase.x + nextDangerZoneRHS - 181f) - position.x,
            size.y.f
        )

        var fleetControlX = mapBase.x + dangerZoneRHS - 181f
        val fleetControlY = mapBase.y + sector.dangerZoneCentre.y - 498f
        fleetControlImg.draw(fleetControlX, fleetControlY)

        // Draw a bunch of tiles filling in the area controlled by
        // the fleet but not covered by the curved front image: all of the
        // window left of it, top to bottom (XFTL's maps are bigger than
        // the front image, and reach further left and up than vanilla's),
        // lined up with the front image
        val tileHeight = fleetControlTile.height.f
        val firstTileY = fleetControlY - ((fleetControlY - position.y) % tileHeight + tileHeight) % tileHeight
        while (fleetControlX > position.x) {
            fleetControlX -= fleetControlTile.width

            var tileY = firstTileY
            while (tileY < position.y + size.y) {
                fleetControlTile.draw(fleetControlX, tileY)
                tileY += tileHeight
            }
        }
    }

    private fun drawLastStandDangerZone(g: Graphics) {
        // The last stand is entirely covered in stripes.
        g.colour = fleetAdvanceColour
        g.fillRect(position.x.f, position.y.f, size.x.f, size.y.f)

        for (tileX in position.x..position.x + size.x step fleetControlTile.width) {
            for (tileY in position.y..position.y + size.y step fleetControlTile.height) {
                fleetControlTile.draw(tileX, tileY)
            }
        }
    }

    override fun draw(g: Graphics) {
        Utils.drawStenciled(Utils.StencilMode.MASKING, {
            // Draw the stencil
            g.colour = Colour.red // Any non-transparent colour will work

            // The window edge width means the inside of the window has a four pixel boundary
            // This does leave a tiny area in the bevelled right-hand corner unstenciled,
            // but it seems unlikely anything will actually draw there.
            // FIXME this is visible - the background clips out
            g.fillRect(position.x + 4, position.y + 4, size.x - 8, size.y - 8)
        }, {
            // Draw the contents of the map
            drawMapContent(g)
        })

        // Draw the top-left map label tab
        val tab = game.translator["map_title"]
        val tabWidth = UIUtils.drawTab(font, tab, titleTab, position.x.f - GLOW, position.y.f - GLOW, 20f, 38f)
        font.drawString(position.x + 14f, position.y + 25f, tab, Constants.JUMP_DISABLED_TEXT)

        // Draw the rest of the top, going clockwise to the right side
        drawSide(Direction.UP, tabWidth.toInt())
        drawCorner(Direction.UP_RIGHT)
        drawSide(Direction.RIGHT)

        // Draw the bottom line connecting
        // Note these have to be drawn before the cancel button - see below
        val sectorName = game.translator["sectorname_" + sector.type.name]
        val nameWidth = sectorNameWidth(sectorName)
        val tabExtra = nameWidth - VANILLA_SECTOR_NAME_WIDTH
        drawSide(Direction.DOWN, sectorInfoTab.width + tabExtra)
        drawCorner(Direction.RIGHT_DOWN)

        // Cancel button
        // This is done in a slightly weird way - the bottom line is drawn exactly how it usually is, but
        // the cancel button is drawn just below it, cutting off part of its glow. The glow at the edges
        // of the cancel button image are modified specially to fit the glow of the line, so it looks
        // seamless. Note that this must be done after the lines are drawn, otherwise their glow would overlap
        // the cancel button frame.
        // Note we also have to stretch it to fit around the localised cancel button.
        val cancelY = position.y + size.y - 1
        val cancelH = cancelButtonOutline.height
        cancelButtonOutline.drawSection(cancelButton.pos.x - 24, cancelY, 34, cancelH)
        cancelButtonOutline.drawSection(cancelButton.pos.x + 10, cancelY, 20, cancelH, 34, 0, cancelButton.size.x - 20)
        cancelButtonOutline.drawSection(cancelButton.pos.x + cancelButton.size.x - 10, cancelY, 35, cancelH, 138)
        cancelButton.draw(g)

        // Draw the 'next sector' button frame, which has a similar glow trick to the cancel button
        if (showNextSectorButton) {
            val nsY = position.y + 4
            val nsH = nextSectorTab.height
            nextSectorTab.drawSection(nextSectorButton.pos.x - 19, nsY, 29, nsH)
            nextSectorTab.drawSection(nextSectorButton.pos.x + 10, nsY, 20, nsH, 24, 0, nextSectorButton.size.x - 20)
            nextSectorTab.drawSection(nextSectorButton.pos.x + nextSectorButton.size.x - 10, nsY, 18, nsH, 235)
            nextSectorButton.draw(g)
        }

        // Draw the sector info, stretched to fit the sector's name: the
        // middle of the tab (where the name's cutout is) is all the same, so
        // a column of it is drawn wider
        val infoX = position.x - GLOW
        val infoY = position.y + size.y - 20
        val infoH = sectorInfoTab.height
        sectorInfoTab.drawSection(infoX, infoY, SECTOR_INFO_STRETCH_X, infoH)
        sectorInfoTab.drawSection(infoX + SECTOR_INFO_STRETCH_X, infoY, 1, infoH, SECTOR_INFO_STRETCH_X, 0, tabExtra)
        sectorInfoTab.drawSection(
            infoX + SECTOR_INFO_STRETCH_X + tabExtra, infoY,
            sectorInfoTab.width - SECTOR_INFO_STRETCH_X, infoH, SECTOR_INFO_STRETCH_X
        )

        // Cutouts for text
        fun drawCutout(x: Int, y: Int, width: Int, text: String) {
            g.colour = Constants.SECTOR_CUTOUT
            g.fillRect(x + 3f, y.f, width - 6f, 2f)
            g.fillRect(x + 2f, y + 1f, width - 4f, 2f)
            g.fillRect(x + 1f, y + 2f, width - 2f, 2f)
            g.fillRect(x.f, y + 3f, width.f, 20f)
            g.fillRect(x + 1f, y + 22f + 0f, width - 2f, 2f)
            g.fillRect(x + 2f, y + 22f + 1f, width - 4f, 2f)
            g.fillRect(x + 3f, y + 22f + 2f, width - 6f, 2f)

            val txtFont = sectorInfoFont
            val textWidth = txtFont.getWidth(text)
            val tx = width / 2 - textWidth / 2
            txtFont.drawString(x + tx.f, y + 20f, text, Constants.SECTOR_CUTOUT_TEXT)
        }

        val sectorText = game.translator["map_sector"]
        val sectorTextY = position.y + size.y + 22f
        font.drawString(position.x + 6f, sectorTextY, sectorText, Constants.JUMP_DISABLED_TEXT)

        drawCutout(position.x + 134, position.y + size.y - 1, 38, (sector.sectorNumber + 1).toString())
        drawCutout(position.x + 183, position.y + size.y - 1, nameWidth, sectorName)

        // The top and bottom tabs are slightly different sizes, this compensates for them
        drawSide(Direction.LEFT, 45, size.y + GLOW * 2 - 27)

        // Draw the out-of-fuel UI
        if (outOfFuel) {
            drawNoFuelUI(null)

            for (button in noFuelButtons) {
                button.draw(g)
            }
        }
    }

    // It's a bit horrible, but this function can both draw and create buttons, since
    // most of the work is calculating the positions of everything.
    private fun drawNoFuelUI(buttons: MutableList<Button>?) {
        val rightX = position.x + size.x
        val frameY = position.y + size.y - 57

        // Calculate all the text-based positions
        val offText = game.translator["button_distress_off"]
        val onText = game.translator["button_distress_on"]
        val toggleTextWidth = max(font.getWidth(offText), font.getWidth(onText))
        val toggleButtonWidth = toggleTextWidth + 4 * 2
        val toggleX = rightX - 12 - toggleButtonWidth

        val distressText = game.translator["map_distress_beacon"]
        val distressLabelWidth = distressBeaconFont.getWidth(distressText)
        val labelAreaWidth = 7 + distressLabelWidth + 8
        val labelAreaX = toggleX - 7 - labelAreaWidth

        val waitText = game.translator["button_wait"]
        val waitButtonWidth = font.getWidth(waitText) + 4 * 2
        val waitButtonX = labelAreaX - 5 - 4 - waitButtonWidth

        val leftX = waitButtonX - 4 - 5

        if (buttons == null) {
            // Draw the background box
            val imgY = frameY - 10
            val imgH = waitDistressFrame.height
            waitDistressFrame.drawSection(leftX - 10, imgY, 22, imgH)
            waitDistressFrame.drawSection(waitButtonX + 3, imgY, 1, imgH, 22, 0, waitButtonWidth - 6)
            waitDistressFrame.drawSection(labelAreaX - 12, imgY, 22, imgH, 27)
            waitDistressFrame.drawSection(labelAreaX + 10, imgY, 1, imgH, 49, 0, labelAreaWidth - 10 - 5)
            waitDistressFrame.drawSection(toggleX - 7 - 5, imgY, 12, imgH, 48)
            waitDistressFrame.drawSection(toggleX, imgY, 1, imgH, 60, 0, toggleButtonWidth - 3)
            waitDistressFrame.drawSection(rightX - 15, imgY, 15, imgH, 62)

            // Draw the beacon toggle button's label text
            // Note the Y value comes from the DE localisation.
            distressBeaconFont.drawStringCentredVertical(
                labelAreaX, frameY + 33,
                labelAreaWidth,
                18,
                distressText,
                Constants.SECTOR_CUTOUT_TEXT
            )

            if (fuelDistressFlashOn) {
                fuelDistressFlashLight.draw(rightX - 2 - 33, frameY + 45 - 23)
            }

            if (fuelDistressOn) {
                fuelDistressFlashTimer = (fuelDistressFlashTimer + game.renderingDeltaTime).mod(1f)

                // This sound is special-cased
                (game.sounds as? RealSoundManager)?.playingFuelDistressSound = true
            } else {
                // Reset, so it immediately lights up when turned back on
                fuelDistressFlashTimer = 0f
            }
        } else {
            // Wait button
            buttons += Buttons.BasicButton(
                game,
                ConstPoint(waitButtonX, frameY + 9), ConstPoint(waitButtonWidth, 36),
                waitText, 4, font, 27
            ) {
                waitOutOfFuel()
            }

            // Toggle distress beacon
            buttons += object : Button(game, ConstPoint(toggleX, frameY + 9), ConstPoint(toggleButtonWidth, 36)) {
                override fun draw(g: Graphics) {
                    g.colour = when {
                        disabled -> Constants.JUMP_DISABLED
                        hovered -> Constants.UI_BUTTON_HOVER
                        else -> Constants.SECTOR_CUTOUT_TEXT
                    }
                    Buttons.drawRounded(g, pos.x, pos.y, size.x, size.y, 4)

                    // Draw the background, which is rounded on the right only
                    // Draw a bunch of overlapping rectangles, making a diagonal corner.
                    for (i in 0..4) {
                        g.fillRect(pos.x, pos.y + 4 - i, size.x - i, size.y - (4 - i) * 2)
                    }

                    val label = when (fuelDistressOn) {
                        true -> onText
                        false -> offText
                    }

                    font.drawStringCentred(pos.x.f, pos.y + 27f, size.x.f, label, Constants.JUMP_DISABLED_TEXT)
                }

                override fun click(button: Int) {
                    fuelDistressOn = !fuelDistressOn
                }
            }
        }
    }

    override fun updateUI(x: Int, y: Int) {
        super.updateUI(x, y)

        hovered = null

        // Can't hover over beacons while out of fuel
        if (outOfFuel) {
            return
        }

        val closest = sector.beacons.map {
            val bp = it.pos + mapBase
            val dist = bp.distToSq(ConstPoint(x, y))
            Pair(it, dist)
        }.minByOrNull { it.second } ?: return

        val hoverDist = 12
        if (closest.second > hoverDist * hoverDist)
            return

        hovered = closest.first
    }

    override fun mouseClick(button: Int, x: Int, y: Int) {
        super.mouseClick(button, x, y)

        if (button != Input.MOUSE_LEFT_BUTTON)
            return

        // Don't jump to a beacon behind a button that was just clicked
        if (buttons.any { it.hovered })
            return

        val hovered = hovered ?: return

        if (hovered == game.currentBeacon || !canJumpTo(hovered))
            return

        // As in vanilla, check before leaving boarders behind
        if (game.shipUI.crewLeftOnOtherShips)
            game.shipUI.confirm("confirm_leave_crew") { jumpTo(hovered) }
        else
            jumpTo(hovered)
    }

    /**
     * The text of a beacon's tooltip on the map, as in vanilla: what's there,
     * and what kind of environment it's in, if the player knows.
     */
    private fun beaconDescription(
        beacon: Beacon, deceptive: Boolean,
        showBasicInfo: Boolean, showAdvInfo: Boolean, showsShip: Boolean,
        willBeOvertaken: Boolean
    ): String {
        val tl = game.translator
        val lines = ArrayList<String>()

        lines += tl[
            when {
                beacon == game.currentBeacon -> "map_current_loc"
                beacon.isBase -> "map_base_loc"
                beacon.isExit -> "map_exit_loc"
                beacon.state == Beacon.State.OVERTAKEN -> "map_fleet_loc"
                willBeOvertaken -> "map_rebels_loc"
                beacon.event.isLastStandRepair && !beacon.visited -> "map_repair_loc"
                beacon.hasQuest && !beacon.visited -> "map_quest_loc"
                beacon.visited && beacon.hasStore -> "map_store_loc"
                beacon.visited && beacon.ship != null -> "map_hostile_loc"
                beacon.visited -> "map_nothing_loc"
                !deceptive && showBasicInfo && beacon.hasStore -> "map_merchant_loc"
                !deceptive && showBasicInfo && beacon.event.isDistressBeacon -> "map_distress_loc"
                showsShip -> "map_ship_loc"
                else -> "map_unvisited_loc"
            }
        ]

        if (bossAt(beacon))
            lines += tl["xftl_map_boss_loc"]

        // The nebulas are always visible on the map, but the other hazards
        // have to be scanned first, unless there's two at once
        val extreme = beacon.isExtremeHazard && !deceptive
        if (extreme)
            lines += tl[
                when {
                    beacon.isApocalypticHazard -> "xftl_map_apocalyptic_loc"
                    beacon.isCatastrophicHazard -> "xftl_map_catastrophic_loc"
                    else -> "xftl_map_extreme_loc"
                }
            ]
        for (environment in beacon.environmentTypes) {
            environmentKey(environment, showAdvInfo || extreme, deceptive)?.let { lines += tl[it] }
        }

        // An Anti-Ship Battery, once scanned: the fleet's, at a beacon it's
        // taken, or a planet's
        if (beacon.antiShipBatteryTarget != null && showAdvInfo && !deceptive) {
            lines += tl[if (beacon.state == Beacon.State.OVERTAKEN) "map_pds_fleet" else "map_pds_loc"]
        }

        // The Silent Capital's Chorus-held beacons say so, whether or not
        // the player can see through it
        if (beacon in sector.chorusHeld && !beacon.visited) {
            lines += tl["xftl_map_chorus_loc"]
        } else if (deceptive && beacon.nebulaType != Beacon.EnvironmentType.MIRAGE) {
            lines += tl["xftl_map_mirage_confused"]
        }

        return lines.joinToString("\n\n")
    }

    /**
     * The text describing an environment on the map, or null if there's
     * none or the player doesn't know about it.
     */
    private fun environmentKey(environment: Beacon.EnvironmentType, showAdvInfo: Boolean, deceptive: Boolean): String? {
        return when (environment) {
            Beacon.EnvironmentType.RADIATION -> "xftl_map_radiation_loc"
            Beacon.EnvironmentType.MIRAGE -> "xftl_map_mirage_loc"
            Beacon.EnvironmentType.DARK -> "xftl_map_dark_loc"
            Beacon.EnvironmentType.ION_STORM -> if (showAdvInfo) "map_ion_loc" else "map_nebula_loc"
            Beacon.EnvironmentType.NEBULA -> when (sector.info.sectorClass) {
                GameMap.SectorClass.NEBULA -> "map_nebula_fleet_loc"
                else -> "map_nebula_loc"
            }

            Beacon.EnvironmentType.ASTEROID -> if (showAdvInfo && !deceptive) "map_asteroid_loc" else null
            Beacon.EnvironmentType.SUN -> if (showAdvInfo && !deceptive) "map_sun_loc" else null
            Beacon.EnvironmentType.PULSAR -> if (showAdvInfo && !deceptive) "map_pulsar_loc" else null
            Beacon.EnvironmentType.NORMAL -> null
        }
    }

    /**
     * Whether the player can jump to a beacon: one next to theirs, or any
     * beacon in debug mode (like vanilla's Advanced FTL Navigation, but for
     * every beacon) or with the 'aj' (anyjump) flag set, which is obviously
     * convenient for testing.
     */
    /**
     * True if a boss is at [beacon], or will be when the player gets there:
     * a boss sector's exit (any of them, if it has several), before its
     * boss is beaten (see ColossalBoss).
     */
    private fun bossAt(beacon: Beacon): Boolean =
        game.isBossBeacon(beacon) && !game.isExitBossBeaten(beacon.sector)

    private fun canJumpTo(beacon: Beacon): Boolean {
        return game.canJumpTo(beacon) || XftlDebug.enabled || game.debugFlags.anyJump.set
    }

    private fun jumpTo(target: Beacon) {
        jump(target)

        game.player.fuelCount -= game.player.fuelPerJump

        // Advance the fleet pursuit *before* changing the beacon, since
        // if we're in a nebula that slows down the fleet pursuit.
        game.advanceFleet()

        // Make sure we set the beacon after we've called the jump callback, so that the event
        // dialogue window doesn't get closed by the callback.
        game.currentBeacon = target
    }

    private fun waitOutOfFuel() {
        // Close the window, and wait for help (see PlayerShipUI.waitOutOfFuel)
        jump(game.currentBeacon)
        game.shipUI.waitOutOfFuel(fuelDistressOn)
    }

    override fun escapePressed() {
        cancelClicked()
    }

    private fun drawTargetBox(g: Graphics, pos: IPoint) {
        drawTargetMarkers(g, targetBox, pos.x + targetBox.width / 2, pos.y + targetBox.height / 2)
    }

    private fun drawBeaconLinesTo(g: Graphics, beacon: Beacon, colour: Colour, predicate: (Beacon) -> Boolean) {
        for (neighbour in beacon.neighbours) {
            if (!predicate(neighbour))
                continue

            drawBeaconLine(g, beacon, neighbour, colour)
        }
    }

    private fun drawBeaconLine(g: Graphics, from: Beacon, to: Beacon, colour: Colour) {
        g.pushTransform()

        val fromPos = from.pos + mapBase + beaconOffset
        val toPos = to.pos + mapBase + beaconOffset

        // Find the delta vector between the two points we're drawing between, and the length of said vector
        val delta = toPos - fromPos
        val dist = delta.distTo(ConstPoint.ZERO)
        val angle = atan2(delta.y.f, delta.x.f) * 180 / PIf

        // Translate to the start position, and rotate so that positive x runs along our line.
        // The beacons are drawn at their image origins, and they're 32px² - so +16 for x,y.
        g.translate(fromPos.x + 16f, fromPos.y + 16f)
        g.rotate(0f, 1.5f, angle)

        val segmentWidth = 10

        // Step along the path of the vector, drawing images in each place
        for (i in 6..(dist - 5) step segmentWidth) {
            // Draw the line itself
            lineImg.drawSection(i, 0, segmentWidth, 4, 1, 0, colour = colour)
        }

        g.popTransform()
    }

    // The beacons' labels, drawn once the fleet advance is (see drawBeaconLabel)
    private val pendingLabels = ArrayList<() -> Unit>()

    private fun drawBeaconLabel(images: List<Image>, colour: Colour, pos: IPoint, text: String) {
        val at = ConstPoint(pos)
        pendingLabels += { drawBeaconLabelNow(images, colour, at, text) }
    }

    private fun drawBeaconLabelNow(images: List<Image>, colour: Colour, pos: IPoint, text: String) {
        // AFAIK the labels in FTL are drawn with alpha=204, TODO use that

        val strWidth = beaconLabelFont.getWidth(text)

        val boxTop = pos.y - 11
        val boxTopWithGlow = boxTop - 6

        images[0].draw(pos.x + 15, boxTopWithGlow)
        images[1].draw(pos.x + 34f, boxTopWithGlow.f, strWidth - 8f, 32f)
        images[2].draw(pos.x + 26 + strWidth, boxTopWithGlow)

        beaconLabelFont.drawString(pos.x + 30f, boxTop + 10f, text, colour)
    }

    private fun drawCorner(edge: Direction) {
        val x = position.x - GLOW + edge.x.coerceAtLeast(0) * (size.x + GLOW * 2 - 33)
        val y = position.y - GLOW + edge.y.coerceAtLeast(0) * (size.y + GLOW * 2 - 36)
        val tx = edge.x.coerceAtLeast(0) * outlineImage.width / 2
        val ty = edge.y.coerceAtLeast(0) * outlineImage.height / 2

        outlineImage.drawSection(x, y, 33, 36, tx, ty)
    }

    /**
     * The width of the sector name's cutout under the map: wider than
     * vanilla's, as the map is, by half as much, and wider still for a long
     * name (such as a hybrid sector's), up to as much wider as the map is.
     */
    private fun sectorNameWidth(name: String): Int {
        val extraMapWidth = Sector.EXTRA_MAP_SIZE.x
        return (sectorInfoFont.getWidth(name) + 2 * SECTOR_NAME_MARGIN).coerceIn(
            VANILLA_SECTOR_NAME_WIDTH + extraMapWidth / 2,
            VANILLA_SECTOR_NAME_WIDTH + extraMapWidth
        )
    }

    private fun drawSide(edge: Direction, start: Int? = null, stop: Int? = null) {
        val paddedSizeX = size.x + GLOW * 2
        val paddedSizeY = size.y + GLOW * 2

        val xb = position.x - GLOW + edge.x.coerceAtLeast(0) * (paddedSizeX - 33)
        val yb = position.y - GLOW + edge.y.coerceAtLeast(0) * (paddedSizeY - 36)

        val texPos = when (edge) {
            Direction.UP -> ConstPoint(33, 0)
            Direction.LEFT -> ConstPoint(0, 36)
            Direction.DOWN, Direction.RIGHT -> ConstPoint(33, 36)
            else -> error("Invalid side edge $edge")
        }

        val texSize = when (edge) {
            Direction.UP, Direction.DOWN -> ConstPoint(1, 36)
            Direction.LEFT, Direction.RIGHT -> ConstPoint(33, 1)
            else -> error("Invalid side edge $edge")
        }

        val x: Int
        val y: Int

        val drawSize: ConstPoint
        when (edge) {
            Direction.UP, Direction.DOWN -> {
                x = xb + (start ?: 33)
                y = yb
                drawSize = ConstPoint((stop ?: (paddedSizeX - 33)) - (start ?: 33), 36)
            }

            Direction.LEFT, Direction.RIGHT -> {
                x = xb
                y = yb + (start ?: 36)
                drawSize = ConstPoint(33, (stop ?: (paddedSizeY - 36)) - (start ?: 36))
            }

            else -> error("Invalid edge $edge")
        }

        outlineImage.draw(
            x.f,
            y.f,
            x.f + drawSize.x,
            y.f + drawSize.y,
            texPos.x.f,
            texPos.y.f,
            texPos.x.f + texSize.x,
            texPos.y.f + texSize.y
        )
    }

    private fun cancelClicked() {
        jump(null)
    }

    companion object {
        // How many of vanilla's clouds there are for nebulas on the map
        // (img/map/nebula_N.png)
        private const val NEBULA_CLOUDS = 4

        // Not translated, as it's only for debugging

        // The width of the glow around the edge of the window
        private const val GLOW = 7

        // The sector name's cutout in vanilla, the room either side of the
        // name in it, and a column of the sector info tab in the middle of
        // the cutout, which is stretched to make it wider (see sectorNameWidth)
        private const val VANILLA_SECTOR_NAME_WIDTH = 276
        private const val SECTOR_NAME_MARGIN = 16
        private const val SECTOR_INFO_STRETCH_X = 240

        // The colour of the hazard icon for an extreme hazard (two at once)
        private val EXTREME_HAZARD_COLOUR = Colour(255, 70, 70)

        // And for a catastrophic hazard (three at once)
        private val CATASTROPHIC_HAZARD_COLOUR = Colour(200, 90, 255)

        // And for an apocalyptic hazard (all four at once)
        private val APOCALYPTIC_HAZARD_COLOUR = Colour(60, 225, 255)

        /**
         * This draws the little rounded corners that move in and out, which is used
         * both on the beacon and sector map.
         */
        fun drawTargetMarkers(g: Graphics, targetBox: Image, centreX: Int, centreY: Int) {
            val period = 1_000_000_000
            val timePoint = (System.nanoTime() % period) / period.toFloat()
            val distFactor = (1 + sin(timePoint * (Math.PI * 2))) / 2
            val spacing = (distFactor * 4).roundToInt()

            g.pushTransform()
            g.translate(centreX.f, centreY.f)

            val boxX = -spacing - targetBox.width / 2
            val boxY = -spacing - targetBox.height / 2

            targetBox.draw(boxX, boxY)
            g.rotate(0f, 0f, 90f)
            targetBox.draw(boxX, boxY)
            g.rotate(0f, 0f, 90f)
            targetBox.draw(boxX, boxY)
            g.rotate(0f, 0f, 90f)
            targetBox.draw(boxX, boxY)

            g.popTransform()
        }
    }
}
