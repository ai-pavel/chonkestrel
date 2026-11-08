package xyz.znix.xftl.game

import xyz.znix.xftl.*
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.sys.GameContainer

/**
 * The window showing an enemy ship. [slot] is 0 for the main enemy ship,
 * and counts up for any [Opponent] ships, whose windows go to its left.
 *
 * The images and fonts are loaded lazily, so this can be created in tests.
 */
class HostileShipUI @JvmOverloads constructor(
    private val game: InGameState,
    private val enemy: Ship,
    slot: Int = 0
) {
    /** Which window it is (see [Opponent.slot]). */
    var slot: Int = slot

    private val mutableShipPos = Point(0, 0)
    val shipPos: IPoint get() = mutableShipPos

    private val font by lazy { game.getFont("HL2") }
    private val titleFont by lazy { game.getFont("HL2", 2f) }
    private val statusFont by lazy { game.getFont("JustinFont8") }
    private val jumpWarningFont by lazy { game.getFont("HL1", 2f) }

    private val shieldIconStandard by lazy { game.getImg("img/combatUI/box_hostiles_shield1.png") }
    private val shieldIconBroken by lazy { game.getImg("img/combatUI/box_hostiles_shield2.png") }
    private val shieldIconStandardHacked by lazy { game.getImg("img/combatUI/box_hostiles_shield2_hacked_charged.png") }
    private val shieldIconBrokenHacked by lazy { game.getImg("img/combatUI/box_hostiles_shield2_hacked.png") }

    private val superShieldBar by lazy { game.getImg("img/combatUI/box_hostiles_shield_super5.png") }
    private val superShieldBarBoss by lazy { game.getImg("img/combatUI/box_hostiles_shield_super12.png") }
    private val shieldChargeBar by lazy { game.getImg("img/combatUI/box_hostiles_shield_charge.png") }

    private val boxNormal by lazy { game.getImg("img/combatUI/box_hostiles2.png") }
    private val boxBoss by lazy { game.getImg("img/combatUI/box_hostiles_boss.png") }

    private val maskNormal by lazy { game.getImg("img/combatUI/box_hostiles_mask.png") }
    private val maskBoss by lazy { game.getImg("img/combatUI/box_hostiles_boss_mask.png") }

    // This ship's shape (bosses are always drawn as normal ships), and how
    // many places in the row the windows to its right take up
    private val shape: ShipShape get() = if (enemy.isUsingBossUI) ShipShape.NORMAL else enemy.type.shape
    private val slotsBefore: Int
        get() {
            return game.opponents.take(slot).sumOf { if (it.ship.isUsingBossUI) 1 else it.ship.type.shape.slots }
        }

    fun render(gc: GameContainer, g: Graphics, hoveredRoom: Room?, interiorVisible: Boolean, isHostile: Boolean) {
        val box = when (enemy.isUsingBossUI) {
            true -> boxBoss
            false -> boxNormal
        }
        val mask = when (enemy.isUsingBossUI) {
            true -> maskBoss
            false -> maskNormal
        }

        val filter = when (isHostile) {
            true -> Constants.SHIP_BOX_HOSTILE
            false -> Constants.SHIP_BOX_NEUTRAL
        }
        val textColour = when (isHostile) {
            true -> Constants.SHIP_BOX_TEXT_HOSTILE
            false -> Constants.SHIP_BOX_TEXT_NEUTRAL
        }

        // The position of the box, when you remove the glow padding, and how
        // much bigger it is than usual, for a bigger ship
        val boxX: Int
        val boxY: Int
        var extraWidth = 0
        var extraHeight = 0

        val leftGlow: Int
        val rightGlow: Int
        val topGlow: Int
        val bottomGlow: Int
        if (enemy.isUsingBossUI) {
            leftGlow = 4
            rightGlow = 4
            topGlow = 4
            bottomGlow = 4
            boxX = gc.width - (box.width - rightGlow) - 10 + leftGlow
            boxY = 11
        } else {
            leftGlow = 10
            rightGlow = 20
            topGlow = 9
            bottomGlow = 17
            // With three enemy windows, they're squeezed to the right and moved
            // down a bit, so the leftmost doesn't cover the settings button.
            val threeWindows = Opponent.maxEnemyShipCount(game.player.type) >= 3
            val rightMargin = if (threeWindows) THREE_WINDOWS_RIGHT_MARGIN else 18
            val spacing = if (threeWindows) THREE_WINDOWS_SPACING else SLOT_SPACING
            val slotWidth = box.width - leftGlow - rightGlow + spacing

            // A wide ship's window takes up two places, and pushes the
            // windows to its left over (see ShipShape)
            extraWidth = (shape.slots - 1) * slotWidth
            boxX = gc.width - (box.width - rightGlow) - rightMargin + leftGlow - (slotsBefore + shape.slots - 1) * slotWidth
            boxY = if (threeWindows) THREE_WINDOWS_Y else 54
            // A long ship's window (or a boss's) stops above the player's
            // weapons boxes, which are at the bottom of the screen, so it's
            // shorter in a window less than 1080 high (such as one with a
            // title bar)
            extraHeight = if (shape.isTall) {
                val bottom = gc.height - LONG_WINDOW_BOTTOM_MARGIN
                (bottom - (boxY - topGlow + box.height)).coerceIn(0, LONG_WINDOW_EXTRA_HEIGHT)
            } else 0
        }

        // The position of the image
        val rawBoxX = boxX - leftGlow
        val rawBoxY = boxY - topGlow

        val boxRightX = rawBoxX + box.width + extraWidth - rightGlow
        val boxBottomY = rawBoxY + box.height + extraHeight - bottomGlow

        // It's not quite the same as FTL, but works well enough for now
        mutableShipPos.x = boxX + (box.width + extraWidth - enemy.hullImage.width) / 2
        mutableShipPos.y = boxY + (box.height + extraHeight - enemy.hullImage.height) / 2
        mutableShipPos -= enemy.hullOffset

        // Bigger ships' windows are stretched, repeating a plain part of the
        // middle of the window
        val columns = listOf(Image.Insertion(STRETCH_SPLIT_X, STRETCH_CHUNK, extraWidth))
        val rows = listOf(Image.Insertion(STRETCH_SPLIT_Y, STRETCH_CHUNK, extraHeight))
        box.drawWithInsertions(rawBoxX, rawBoxY, columns, rows, filter)

        Utils.drawStenciled(Utils.StencilMode.MASKING, {
            mask.drawWithInsertions(rawBoxX, rawBoxY, columns, rows)
        }) {
            g.pushTransform()
            g.translate(shipPos.x.f, shipPos.y.f)
            // (the beam being aimed may belong to any of the player's
            // weapons systems)
            enemy.updateInboundBeamAim(game.shipUI.ship.weaponSystems.firstNotNullOfOrNull { it.selectedTargets.beamAiming })
            enemy.render(g, interiorVisible, hoveredRoom)
            // A boss's own marks over its rooms, such as the Mainframe's
            // build bars (see ColossalBoss)
            enemy.colossal?.renderOverlay(g)

            for (playerWeapons in game.shipUI.ship.weaponSystems) {
                enemy.renderTargeting(g, playerWeapons.selectedTargets)
            }
            enemy.renderPendingTargets(game.player)

            g.popTransform()
        }


        val textX = boxX + 2

        // Draw the title
        val titleX = textX + when (enemy.isUsingBossUI) {
            true -> 7
            else -> 1
        }
        titleFont.drawString(titleX.f, boxY + 16f, game.translator["target_window"], textColour)

        // Draw the class and relationship text
        val statusTextX = boxRightX - 11
        val classY = boxY + 38
        drawStatus(statusTextX, classY, isHostile)

        // Draw the FTL charging warning, if relevant.
        val centreX = rawBoxX + (box.width + extraWidth) / 2
        drawEscapeWarning(centreX, boxY)
        drawCrewKilled(centreX, boxY)

        // Draw the hull level
        val hullY = boxY + 20 + 4
        renderSmallbar(textX, hullY, "status_hull", filter, textColour)
        // Bigger ships have more hull. A wide ship's window is twice as wide,
        // so its bar has room for three times the cells, of a point each;
        // a long ship's isn't, so each of its cells shows two points. (If
        // a ship has more than its bar holds, each cell shows more.)
        val wide = shape.slots > 1
        val barCells = if (wide) WIDE_HULL_BAR_CELLS else Ship.HULL_BAR_CELLS
        val pointsPerCell = if (wide) 1 else enemy.hullScale
        val perCell = maxOf(pointsPerCell, (enemy.maxHealth + barCells - 1) / barCells)
        val hpWidth = 11 * enemy.health / perCell
        val hpX = textX + 5
        val hpY = hullY + 12
        val hull = game.getImg("img/combatUI/box_hostiles_hull2.png")
        drawHullBar(hull, hpX, hpY, hpWidth, barCells)
        // A long ship's cells are split in two, to show they're two points
        // each; a wide ship's (three or more points each) aren't split, as
        // their parts would be too thin to make out
        if (perCell <= MAX_SPLIT_HULL_CELL)
            game.shipUI.drawHullCellDividers(hull, hpX.f, hpY.f, hpWidth.f, 11, 10, perCell)

        if (enemy.shieldSystems.isNotEmpty()) {
            val shieldsY = hullY + 27
            renderSmallbar(textX, shieldsY, "status_shields", filter, textColour)

            // Draw the shield bubbles: a group for each shields system, side
            // by side, as most ships only have one.
            var bubbleX = textX + 7
            val groupXs = ArrayList<Int>()
            for ((index, shields) in enemy.shieldSystems.withIndex()) {
                if (index != 0)
                    bubbleX += SHIELD_GROUP_GAP
                groupXs += bubbleX

                for (i in 0 until shields.selectedShieldBars) {
                    val intact = i < shields.activeShields
                    val hacked = shields.isHackActive
                    val img = when {
                        hacked && intact -> shieldIconStandardHacked
                        hacked && !intact -> shieldIconBrokenHacked
                        intact -> shieldIconStandard
                        else -> shieldIconBroken
                    }
                    img.draw(bubbleX, shieldsY + 15)
                    bubbleX += 23
                }
            }

            if (enemy.superShield != 0) {
                // TODO support ships with a super-shield but no shields system
                // Each Zoltan shield gets a row of its own, one under the
                // other below the bubbles, as in the player's stacked panel
                // (each row a plain bar, filling up as it recharges).
                val perRow = AugZoltanShield.SHIELD_PER_AUGMENT
                val rows = (enemy.maxSuperShield + perRow - 1) / perRow
                // Each row is vanilla's super-shield bar, as big as it is
                // there, with its white frame
                val barX = textX + 5
                for (row in 0 until rows) {
                    val rowY = shieldsY + 39 + row * (superShieldBar.height + SUPER_SHIELD_ROW_GAP)
                    superShieldBar.draw(barX, rowY)

                    val points = (enemy.superShield - row * perRow).coerceIn(0, perRow)
                    g.colour = Constants.SYS_ENERGY_ACTIVE
                    g.fillRect(barX + 3f, rowY + 3f, 50f * points / perRow, 7f)
                }
            } else {
                // Draw each system's charge bar, under its bubbles. With
                // several systems, each bar is only as wide as there's room
                // for before the next system's bubbles (a one-bubble system's
                // bar is shorter than vanilla's), so they don't overlap.
                for ((index, shields) in enemy.shieldSystems.withIndex()) {
                    if (shields.rechargeTimer == 0f)
                        continue
                    val barX = groupXs[index] - 2
                    val barY = shieldsY + 39
                    val nextX = groupXs.getOrNull(index + 1)
                    val barWidth = when (nextX) {
                        null -> shieldChargeBar.width
                        else -> (nextX - 2 - SHIELD_BAR_GAP - barX).coerceIn(MIN_SHIELD_BAR_WIDTH, shieldChargeBar.width)
                    }
                    drawShieldChargeBar(barX, barY, barWidth)

                    val progress = shields.rechargeTimer / shields.rechargeDelay
                    val colour = when {
                        shields.isHackActive -> Constants.SHIELD_BAR_HACKED
                        else -> Constants.SHIELD_BAR_NORMAL
                    }

                    val width = ((barWidth - 6) * progress)
                    g.colour = colour
                    g.fillRect(barX.f + 3, barY.f + 3, width, 6f)
                }
            }
        }

        val powerVisible = game.player.sensorsFor(enemy)?.showsEnemyPowerLevels == true
                || game.debugFlags.showEverything.set

        // Draw the enemy's systems, closer together if a bigger ship has too
        // many to fit in its window, in the same order as the player's: the
        // main systems, then the subsystems
        val systems = enemy.mainSystems + enemy.subSystems
        val systemsX = boxX + 49
        val systemsWidth = boxRightX - SYSTEMS_RIGHT_MARGIN - systemsX
        val systemSpacing = (systemsWidth / systems.size.coerceAtLeast(1)).coerceAtMost(SYSTEM_SPACING)
        for ((i, sys) in systems.withIndex()) {
            val y = boxBottomY - 49
            val x = systemsX + i * systemSpacing

            val roomPowerVisible = powerVisible || sys.hackedBy?.isPoweredUp == true

            sys.drawIconAndPower(game, g, false, roomPowerVisible, false, x, y)
        }
    }

    /**
     * Draw the player's combined beams aimed at this window's ship (see
     * [Ship.renderLongBeamTargeting]). Their long lines go on through the
     * other enemy windows, so this is done once they've all been drawn, so
     * they go over them, rather than being cut off at this one's edge.
     */
    fun renderLongBeamTargeting(g: Graphics) {
        g.pushTransform()
        g.translate(shipPos.x.f, shipPos.y.f)
        for (playerWeapons in game.shipUI.ship.weaponSystems) {
            enemy.renderLongBeamTargeting(g, playerWeapons.selectedTargets)
        }
        g.popTransform()
    }

    /**
     * Draw [width] pixels of the hull bar's [image] at ([x], [y]), for a bar
     * with room for [barCells] cells. The image's first half of cells are
     * short and the second half tall, with a bevelled corner on the first
     * of each. A bar with more cells than the image (a wide ship's) keeps
     * that shape: short cells for its first half and tall ones after, with
     * the bevel only on the first cell and the first tall one.
     */
    private fun drawHullBar(image: Image, x: Int, y: Int, width: Int, barCells: Int) {
        val imageCells = (image.width + 1) / HULL_CELL_PITCH
        val imageFirstTall = imageCells / 2
        val firstTall = barCells / 2
        var drawn = 0
        var cell = 0
        while (drawn < width) {
            val source = when {
                barCells <= imageCells -> cell
                cell == 0 -> 0
                cell < firstTall -> 1
                cell == firstTall -> imageFirstTall
                else -> imageFirstTall + 1
            }
            val sourceX = source * HULL_CELL_PITCH
            val part = minOf(HULL_CELL_PITCH, width - drawn, image.width - sourceX)
            image.draw(
                (x + drawn).f, y.f, (x + drawn + part).f, (y + image.height).f,
                sourceX.f, 0f, (sourceX + part).f, image.height.f,
                1f, Constants.SHIP_HEALTH_HIGH
            )
            drawn += HULL_CELL_PITCH
            cell++
        }
    }

    /**
     * Draw a shields system's charge bar's frame, [width] wide: vanilla's
     * image, with its middle squashed if it's narrower.
     */
    private fun drawShieldChargeBar(x: Int, y: Int, width: Int) {
        val img = shieldChargeBar
        if (width >= img.width) {
            img.draw(x, y)
            return
        }
        val cap = SHIELD_BAR_CAP.f
        val h = img.height.f
        img.draw(x.f, y.f, x + cap, y + h, 0f, 0f, cap, h)
        img.draw(x + cap, y.f, x + width - cap, y + h, cap, 0f, img.width - cap, h)
        img.draw(x + width - cap, y.f, x + width.f, y + h, img.width - cap, 0f, img.width.f, h)
    }

    private fun drawStatus(x: Int, classY: Int, isHostile: Boolean) {
        val relationY = classY + 15

        if (enemy.type.shipClass != null) {
            val shipClassName = game.translator[enemy.type.shipClass]
            // Just the class's name, without "Class:" before it
            val classStr = shipClassName

            statusFont.drawStringLeftAligned(x.f, classY.f, classStr, Constants.SHIP_STATUS_PLAIN)
        }

        val relationText = when (isHostile) {
            true -> game.translator["hostile"]
            false -> game.translator["neutral"]
        }
        val relationColour = when (isHostile) {
            true -> Constants.SHIP_STATUS_HOSTILE
            false -> Constants.SHIP_STATUS_PLAIN
        }
        val relationStr = game.translator["combat_relationship"].replaceArg(relationText)
        statusFont.drawStringLeftAligned(x.f, relationY.f, relationStr, relationColour)
    }

    /**
     * Say so above the window when the ship's crew are all dead, while the
     * player's boarders are still aboard (the window goes once they're
     * taken home). In a fight with several ships, the rewards wait until
     * the end, so otherwise there'd be no sign of it.
     */
    private fun drawCrewKilled(centreX: Int, boxY: Int) {
        if (enemy.isAutoScout)
            return
        if (game.isEnemyOf(game.player, enemy) || enemy.hasCrewOwnedByShip(enemy))
            return
        if (!enemy.hasCrewOwnedByShip(game.player))
            return

        val message = game.translator["xftl_crew_killed"]
        val leftX = centreX - jumpWarningFont.getWidth(message) / 2
        UIUtils.drawStringWithGlow(game, jumpWarningFont, message, leftX, boxY - 10, GlowColour.WHITE, 1f)
    }

    private fun drawEscapeWarning(centreX: Int, boxY: Int) {
        val timeRemaining = enemy.escapeTimer ?: return

        // A ship that's given up or lost its crew isn't going anywhere
        if (!game.isEnemyOf(game.player, enemy))
            return

        val warningKey = when {
            !enemy.canChargeFTL -> "warning_ftl_delayed"
            timeRemaining < 5f -> "warning_ftl_imminent"
            else -> "warning_ftl_charging"
        }

        var alpha = 1f

        // Flash the alpha if a jump is imminent.
        if (warningKey == "warning_ftl_imminent") {
            // Run the flashing animation while paused
            var timeNS = System.nanoTime()
            if (timeNS < 0) {
                timeNS += Long.MAX_VALUE
            }

            val period = 700_000_000
            val progress = (timeNS % period).toFloat() / period

            // Fade up then down
            alpha = progress * 2
            if (alpha > 1) {
                alpha = 2 - alpha
            }
        }

        val message = game.translator[warningKey]

        val leftX = centreX - jumpWarningFont.getWidth(message) / 2
        UIUtils.drawStringWithGlow(game, jumpWarningFont, message, leftX, boxY - 10, GlowColour.RED, alpha)
    }

    private fun renderSmallbar(x: Int, y: Int, key: String, filter: Colour, textColour: Colour) {
        val text = game.translator[key]

        val textX = 2
        val textWidth = font.getWidth(text)

        val left = game.getImg("img/combatUI/box_hostiles_smallbar_left.png")
        left.draw(x, y, filter)
        val middle = game.getImg("img/combatUI/box_hostiles_smallbar_middle.png")
        val midWidth = textWidth + textX - left.width
        middle.draw(
            x.f + left.width, y.f,
            x.f + left.width + midWidth, y.f + middle.height,
            0f, 0f, middle.width.f, middle.height.f,
            1f, filter
        )

        val rightImg = game.getImg("img/combatUI/box_hostiles_smallbar_right.png")
        rightImg.draw(x + left.width + midWidth, y, filter)

        font.drawString(x + 2f, y + 9f, text, textColour)
    }

    companion object {
        // The gap between the windows of several enemy ships
        private const val SLOT_SPACING = 12

        // How far apart the enemy's systems are, and the space left right of
        // them, for a ship with too many to fit
        private const val SYSTEM_SPACING = 30
        private const val SYSTEMS_RIGHT_MARGIN = 12

        // The gap between the rows of a ship's Zoltan shields
        private const val SUPER_SHIELD_ROW_GAP = 2

        // The most cells in the hull bar, so it doesn't run into the ship's
        // class: a ship with more hull than that shows more in each cell

        // The most points a cell of the hull bar is split into
        private const val MAX_SPLIT_HULL_CELL = 2

        // The gap between the shield bubbles of a ship's shields systems
        private const val SHIELD_GROUP_GAP = 10

        // How many cells a wide ship's hull bar has room for, a point each
        // (see doc/game/hull.md), and the width of each cell of the hull bar
        // image, with the gap after it
        const val WIDE_HULL_BAR_CELLS = Ship.HULL_BAR_CELLS * 3
        private const val HULL_CELL_PITCH = 11

        // The space between one shields system's charge bar and the next,
        // the narrowest a bar's drawn, and the width of the ends of its frame
        // that aren't squashed
        private const val SHIELD_BAR_GAP = 2
        private const val MIN_SHIELD_BAR_WIDTH = 16
        private const val SHIELD_BAR_CAP = 4

        // The windows' layout when there's room for three of them: close to
        // each other, and below the top bar's settings button (which ends at
        // y=70). There's a margin on the right, so the weapon charge bars of
        // the rightmost ship don't go off the screen.
        private const val THREE_WINDOWS_RIGHT_MARGIN = 24
        private const val THREE_WINDOWS_SPACING = 6
        private const val THREE_WINDOWS_Y = 78

        // How much taller a long ship's window is (any more, and it would
        // cover the top row of the player's weapons boxes, on a ship with
        // two), and where the windows are stretched (see ShipShape): a plain
        // part in the middle
        private const val LONG_WINDOW_EXTRA_HEIGHT = 260

        // How far above the bottom of the screen a long ship's window has to
        // stop, to clear the player's weapons boxes (the most it's made
        // taller by fits a 1080-high window exactly)
        private const val LONG_WINDOW_BOTTOM_MARGIN = 1080 - (THREE_WINDOWS_Y - 9 + 555 + LONG_WINDOW_EXTRA_HEIGHT)
        private const val STRETCH_SPLIT_X = 220
        private const val STRETCH_SPLIT_Y = 300
        private const val STRETCH_CHUNK = 40
    }
}
