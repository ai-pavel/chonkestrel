package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.*
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.game.*
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.sys.Input

class Teleporter(blueprint: SystemBlueprint) : MainSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.TELEPORTER
    override val insertButtonSpace: Boolean get() = true

    private val teleportSound by onInit { it.sounds.getSample("teleport") }

    private val padOffImage by onInit { it.getImg("img/ship/interior/teleporter_off.png") }
    private val padOnImage by onInit { it.getImg("img/ship/interior/teleporter_selected.png") }
    private val padActiveImage by onInit { it.getImg("img/ship/interior/teleporter_on.png") }

    private var onCooldown = false

    private var sendButton: TeleporterButton? = null
    private var recvButton: TeleporterButton? = null

    val isSendAvailable: Boolean
        get() {
            if (isPowerLocked || powerSelected == 0)
                return false

            // There must be an enemy ship
            if (ship.sys.getEnemyOf(ship) == null)
                return false

            // There must be at least one crew member standing in the room
            return room!!.crew.any { it.standingPosition != null && it.mode == AbstractCrew.SlotType.CREW && it is LivingCrew }
        }

    val isReceiveAvailable: Boolean
        get() {
            if (isPowerLocked || powerSelected == 0)
                return false

            // There must be another ship present, whether or not it's
            // currently hostile - if not, we can still teleport crew home
            // from it. The player may be fighting several ships.
            val others = when {
                ship.isPlayerShip -> ship.sys.allEnemyShips
                else -> listOf(ship.sys.player)
            }

            // If this is somehow run from a ship that's at a different beacon
            // (which it really shouldn't be!) then block the teleport.
            // There must be at least one of our crew in the other ship.
            return others.any { other ->
                other != ship && ship.sys.isShipPresent(other) && other.crew.any { isOurs(it) }
            }
        }

    private var commandedTeleport: TeleportAction? = null

    /**
     * The room a command is waiting to teleport to or from, such as one on
     * a cloaked ship, for its icon (see Ship.renderPendingTargets).
     */
    val pendingRoom: Room? get() = commandedTeleport?.room

    /**
     * Why the last command to send crew didn't, or null if it did. The
     * enemy's crew AI logs this, for boarders that never leave (see
     * FriendlyCrewAI).
     */
    var sendProblem: String? = null
        private set

    // Whether it was being hacked last update, to bring the boarders home
    // as a hacking pulse starts
    private var wasHacked = false

    override fun update(dt: Float) {
        super.update(dt)
        endCooldownOutOfDanger()

        if (ionTimer == 0f) {
            onCooldown = false
        }

        // As in vanilla, hacking a teleporter brings its ship's boarders
        // home, to its room
        if (isHackActive && !wasHacked)
            recallBoarders()
        wasHacked = isHackActive

        // If there's a command ready, grab and action it
        val command = commandedTeleport ?: return

        // If the enemy ship is cloaked, stop now without removing the command.
        // Thus the command will stay there until the enemy comes out of cloak.
        if (command.room.ship.isCloakActive) {
            if (command.send)
                sendProblem = "the ship they'd board is cloaked"
            return
        }

        commandedTeleport = null

        if (command.send) {
            if (!isSendAvailable) {
                sendProblem = sendUnavailableReason()
                return
            }

            // A ship that's given up or lost its crew can't be boarded again,
            // only have the boarders still on it taken home
            if (!ship.sys.isEnemyOf(ship, command.room.ship)) {
                sendProblem = "the ship they'd board has already been beaten"
                return
            }

            if (blockedBySuperShield(command.room.ship)) {
                sendProblem = "the ship they'd board has a super shield up"
                return
            }

            // Crew are only teleported if they're assigned a slot on the teleporter.
            val ourCrew = room!!.crew.filter {
                it.mode == AbstractCrew.SlotType.CREW && it is LivingCrew && room!!.isCrewAssigned(it)
            }

            // Don't waste a cooldown if we're not teleporting anyone
            if (ourCrew.isEmpty()) {
                val standing = room!!.crew.filter { it.mode == AbstractCrew.SlotType.CREW }
                sendProblem = "none of the crew in the teleporter (${standing.joinToString { it.logName }}) " +
                        "have a place on it"
                return
            }

            sendProblem = null
            for (crew in ourCrew) {
                crew.teleportAnimatedTo(command.room)
            }

            ship.crewAI.onCrewTeleportedOut()
        } else {
            if (!isReceiveAvailable)
                return

            if (blockedBySuperShield(command.room.ship))
                return

            // Only our own crew are brought home: never anyone else's that
            // are fighting them, or that are mind controlled
            var ourCrew = command.room.crew.filter { isOurs(it) }.map { it as LivingCrew }

            // Limit the crew we can teleport to four (at least on a two-person
            // teleporter, you can't teleport more than four crew, even if
            // they're all inside the same room by walking thorough).
            // Modded teleporters with more than four cells can bring back
            // as many crew as they can send.
            val maxCrew = maxOf(4, room!!.cellCount)
            if (ourCrew.size > maxCrew) {
                ourCrew = ourCrew.shuffled().subList(0, maxCrew)
            }

            // Don't waste a cooldown if we're not teleporting anyone
            if (ourCrew.isEmpty())
                return

            for (crew in ourCrew) {
                crew.teleportAnimatedTo(room!!)
            }
        }

        teleportSound.play()

        // Ion-stun for 20s at 1 power, 15s at 2, and 10s at 3.
        ionTimer += cooldownTime(powerSelected).f

        // Render the teleport pads blue until the ion timer runs out.
        // This prevents an ion weapon making the pads go blue.
        onCooldown = true
    }

    override fun onJump() {
        super.onJump()
        endCooldown()
        onCooldown = false
    }

    /**
     * Whether [crew] is one of this ship's own crew, on another ship, that
     * the teleporter can bring home.
     */
    private fun isOurs(crew: AbstractCrew): Boolean =
        crew is LivingCrew && crew.ownerShip == ship && crew.room.ship != ship && !crew.isTeleporting

    private fun recallBoarders() {
        for (other in ship.sys.getOpponentShips(ship)) {
            for (crew in other.crew.filter { isOurs(it) })
                crew.teleportAnimatedTo(room!!)
        }
    }

    /**
     * Whether [target]'s super shield stops the teleporter: only while
     * they're fighting (once a ship's surrendered or lost its crew, its
     * shield overcharger drones keep its super shield up, and that mustn't
     * strand the boarders on it), and not with the Zoltan Shield Bypass.
     */
    private fun blockedBySuperShield(target: Ship): Boolean =
        target.superShield > 0 && !ship.hasAugment(AugmentBlueprint.ZOLTAN_BYPASS) &&
                ship.sys.isEnemyOf(ship, target)

    // Why isSendAvailable is false, for sendProblem
    private fun sendUnavailableReason(): String = when {
        isHackActive -> "the teleporter is hacked"
        isIonised -> "the teleporter is ionised or on cooldown (${ionTimer.toInt()}s left)"
        powerSelected == 0 -> "the teleporter has no power " +
                "(${damagedEnergyLevels} of $energyLevels bars damaged, ${ship.powerAvailable} reactor power free)"
        ship.sys.getEnemyOf(ship) == null -> "there's no ship to board"
        else -> "no crew are standing in the teleporter"
    }

    override fun makeExtraButtons(powerPos: IPoint): List<Button> {
        val bottom = ButtonImageSet.select2(ship.sys, "img/systemUI/button_teleport_bottom")
        val top = ButtonImageSet.select2(ship.sys, "img/systemUI/button_teleport_top")

        val buttonBase = powerPos + ConstPoint(22, -49)

        val bgButton = TeleporterButtonBackground(buttonBase, powerPos)

        sendButton = TeleporterButton(buttonBase, ConstPoint(4, 4), true, top, bgButton.superShieldWarning)
        recvButton = TeleporterButton(buttonBase, ConstPoint(4, 27), false, bottom, bgButton.superShieldWarning)

        return listOf(
            // The background comes first so it doesn't draw on top of the others
            bgButton,

            sendButton!!,
            recvButton!!
        )
    }

    override fun drawRoom(g: Graphics) {
        if (!room!!.playerHasVision) {
            super.drawRoom(g)
            return
        }

        val padImage = when {
            powerSelected == 0 -> padOffImage
            onCooldown -> padActiveImage
            else -> padOnImage
        }

        for (x in 0 until room!!.width) {
            for (y in 0 until room!!.height) {
                padImage.drawAlignedCentred(
                    room!!.offsetX + x * Constants.ROOM_SIZE + Constants.ROOM_SIZE / 2,
                    room!!.offsetY + y * Constants.ROOM_SIZE + Constants.ROOM_SIZE / 2,
                )
            }
        }

        // Draw the icon on top
        super.drawRoom(g)
    }

    override fun hotkeyPressed(key: Hotkey) {
        super.hotkeyPressed(key)

        // Calling Button.click bit hacky, but it properly shows the super-shield warning
        if (key.id == VanillaHotkeys.SYS_ACTION_TELEPORT_SEND) {
            sendButton?.click(Input.MOUSE_LEFT_BUTTON)
        }
        if (key.id == VanillaHotkeys.SYS_ACTION_TELEPORT_RECV) {
            recvButton?.click(Input.MOUSE_LEFT_BUTTON)
        }
    }

    fun selectTeleportAction(send: Boolean, room: Room) {
        commandedTeleport = TeleportAction(room, send)
    }

    // Surprisingly, we have very little to serialise - the crew
    // handle their teleportation state themselves, and our cooldown
    // is handled via ion damage.
    override fun saveSystem(elem: Element, refs: ObjectRefs) {
        // If the player has selected a destination to teleport their
        // crew to, remember that across saves.
        val command = commandedTeleport ?: return

        val commandElem = Element("teleportCommand")
        commandElem.setAttribute("roomId", command.room.id.toString())
        commandElem.setAttribute("roomShip", refs[command.room.ship])
        commandElem.setAttribute("isSending", command.send.toString())
        elem.addContent(commandElem)
    }

    override fun loadSystem(elem: Element, refs: RefLoader) {
        val commandElem = elem.getChild("teleportCommand") ?: return

        val roomId = commandElem.getAttributeValue("roomId")!!.toInt()
        val shipRef = commandElem.getAttributeValue("roomShip")!!
        val send = commandElem.getAttributeValue("isSending")!!.toBoolean()

        refs.asyncResolve(Ship::class.java, shipRef) {
            val room = it!!.rooms[roomId]
            commandedTeleport = TeleportAction(room, send)
        }
    }

    private inner class TeleporterButton(
        val base: IPoint,
        offset: IPoint,
        val isSend: Boolean,
        val images: ButtonImageSet,
        val superShieldWarning: WarningFlasher
    ) :
        Button(ship.sys, base + offset, ConstPoint(20, 20)) {

        override val disabled: Boolean
            get() = when {
                isSend -> !isSendAvailable
                else -> !isReceiveAvailable
            }

        override fun draw(g: Graphics) {
            val img = when {
                disabled -> images.off
                hovered -> images.hover

                // If we're selecting, or have selected, a teleport of this type,
                // then keep the button highlighted.
                ship.sys.shipUI.teleportMode == isSend -> images.hover
                commandedTeleport?.send == isSend -> images.hover

                else -> images.normal
            }
            img.draw(base.x.f - BASE_GLOW, base.y.f - BASE_GLOW)
        }

        public override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            if (disabled)
                return

            // There may be several enemy ships, and the player can teleport
            // crew off a non-hostile ship.
            val enemyShips = when {
                !isSend && ship == game.player -> game.allEnemyShips
                else -> game.getOpponentShips(ship)
            }

            if (enemyShips.isEmpty())
                return

            // The room picked is checked again when teleporting, so only stop
            // here if there's nowhere to go.
            if (enemyShips.all { blockedBySuperShield(it) }) {
                superShieldWarning.startFor(3.5f)
                return
            }

            // If there's already an order queued up, clear it out.
            commandedTeleport = null

            ship.sys.shipUI.teleportSelected(isSend)
        }
    }

    private inner class TeleporterButtonBackground(pos: IPoint, powerPos: IPoint) :
        Button(ship.sys, pos, ConstPoint.ZERO) {

        override val makesHoverNoise: Boolean get() = false

        // Put this here so there's only one copy of it.
        val superShieldWarning = WarningFlasher(
            game, powerPos + ConstPoint(17, -59),
            "warning_super_shield_teleporter",
            false, colour = GlowColour.WHITE
        )

        override fun draw(g: Graphics) {
            val img = ship.sys.getImg("img/systemUI/button_teleport_base.png")
            img.draw(pos.x.f - BASE_GLOW, pos.y.f - BASE_GLOW)

            superShieldWarning.draw(g)
        }

        override fun click(button: Int) = Unit
    }

    private class TeleportAction(val room: Room, val send: Boolean)

    companion object {
        private const val BASE_GLOW: Int = 6

        val INFO: SystemInfo = TeleporterInfo

        /** Vanilla's most bars; the Chonkestrel mod lets it go to six. */
        const val VANILLA_MAX_POWER = 3

        // The cooldown with four bars, five and six: less and less off it
        // for each, as with the cloak (see Cloaking.durationFor)
        private val EXTRA_COOLDOWNS = listOf(8, 7, 6)

        /** How long the teleporter takes to recharge with [power] bars: 20, 15 or 10 seconds, then as EXTRA_COOLDOWNS. */
        fun cooldownTime(power: Int): Int = when {
            power <= VANILLA_MAX_POWER -> 5 * (5 - power)
            else -> EXTRA_COOLDOWNS[minOf(power - VANILLA_MAX_POWER, EXTRA_COOLDOWNS.size) - 1]
        }
    }
}

private object TeleporterInfo : SystemInfo("teleporter") {
    override val canBeManned: Boolean get() = false

    override fun create(blueprint: SystemBlueprint) = Teleporter(blueprint)

    override fun getLevelName(level: Int, translator: Translator): String {
        val time = Teleporter.cooldownTime(level + 1)
        return translator["teleporter_on"].replaceArg(time)
    }
}
