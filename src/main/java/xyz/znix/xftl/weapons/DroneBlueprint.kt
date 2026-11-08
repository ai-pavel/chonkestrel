package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Constants
import xyz.znix.xftl.drones.*
import xyz.znix.xftl.f
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.GameText
import kotlin.math.roundToInt

class DroneBlueprint(xml: Element) : Blueprint(xml) {
    val type: DroneType = DroneType.valueOf(xml.getChildTextTrim("type"))
    override val cost: Int? = xml.getChildTextTrim("cost")?.toInt()
    val power: Int = xml.getChildTextTrim("power").toInt()
    val speed: Int? = xml.getChildTextTrim("speed")?.toInt()
    val droneImage: String? = xml.getChildTextTrim("droneImage")
    val iconImage: String? = xml.getChildTextTrim("iconImage")

    // For defence drones (which includes anti-drones)
    val cooldown: Int? = xml.getChildTextTrim("cooldown")?.toInt() // In milliseconds
    val defenceTarget: String? = xml.getChildTextTrim("target") // Either DRONES or LASERS

    // For external drones, a 0-10 chance that this drone will dodge projectiles that hit it.
    // Nothing in vanilla uses it, but it's easy to implement.
    val dodge: Int? = xml.getChildTextTrim("dodge")?.toInt()

    /**
     * The drones this one's made of, if it's a combined drone (an XFTL
     * extension, see [DroneSizes]), or empty for a normal one.
     */
    val combinedOf: List<String> =
        xml.getChild("xftlCombined")?.getChildren("drone")?.map { it.getAttributeValue("name") } ?: emptyList()

    /** How many drones this launches from its slot: its size (see doc/game/drone-sizes.md). */
    val combinedCount: Int get() = combinedOf.size.coerceAtLeast(1)

    val isCombined: Boolean get() = combinedOf.size > 1

    /**
     * The name of the drone this is: its own, or for a combined one, the
     * single drone it's made of (so a twin Ion Intruder is still an Ion
     * Intruder, see BoardingDrone.isIon).
     */
    val baseName: String get() = combinedOf.firstOrNull() ?: name

    // A combined drone is called "Twin" or "Triple", as a combined weapon is
    override val title: GameText? = super.title?.let { if (isCombined) combinedName(it, "title") else it }
    override val short: GameText? = super.short?.let { if (isCombined) combinedName(it, "short") else it }

    private fun combinedName(base: GameText, kind: String): GameText =
        GameText.localisedWith("xftl_weapon_${kind}_x$combinedCount", base)

    private val weaponBlueprintName: String? = xml.getChildTextTrim("weaponBlueprint")
    var weaponBlueprint: AbstractWeaponBlueprint? = null
        private set

    // The size of the icon, as it should appear in the UI. This is for roughly
    // positioning them, and obviously different drones are different sizes.
    // These are mostly guessed, I couldn't find anywhere to measure them and
    // disassembling the UI code to look for this seems a bit excessive.
    val iconSize: ConstPoint get() = combinedIconSize(singleIconSize)

    // A combined drone's icon is its single one's, two side by side or
    // three in a triangle (see drawIconUI)
    private fun combinedIconSize(single: ConstPoint): ConstPoint = when (combinedCount) {
        1 -> single
        2 -> ConstPoint(2 * single.x + TWIN_GAP, single.y)
        else -> ConstPoint(single.x + 2 * tripleSpread(single.x), single.y + TRIPLE_RISE)
    }

    private val singleIconSize: ConstPoint = when (type) {
        // TODO find some logic behind this - is it the laser arm
        //  in defence drones that make them taller?
        // DEFENSE_1 drones use 46 pixels of Y in the event option UI, while COMBAT_1 uses 38.
        DroneType.DEFENSE -> ConstPoint(29, 37)
        DroneType.COMBAT -> ConstPoint(29, 29)

        // TODO fill in all the drones properly
        else -> ConstPoint(25, 25)
    }

    fun drawIconUI(game: InGameState, pos: IPoint) {
        drawIconUI(game.animations, pos, game::getImg)
    }

    // This version is used by the ship editor, since it doesn't have an InGameState instance.
    fun drawIconUI(animations: Animations, pos: IPoint, getImg: (String) -> Image) {
        // A combined drone's is N of the single one's, as the combined
        // weapons are drawn: a twin's two side by side, a triple's three in
        // a triangle, two behind (darker, raised) and one in front
        when (combinedCount) {
            1 -> drawSingleIcon(animations, pos, getImg, Colour.white)
            2 -> {
                val offset = (singleIconSize.x + TWIN_GAP) / 2
                drawSingleIcon(animations, ConstPoint(pos.x - offset, pos.y), getImg, Colour.white)
                drawSingleIcon(animations, ConstPoint(pos.x + offset, pos.y), getImg, Colour.white)
            }

            else -> {
                val spread = tripleSpread(singleIconSize.x)
                val back = ConstPoint(0, -TRIPLE_RISE / 2)
                drawSingleIcon(animations, ConstPoint(pos.x - spread, pos.y + back.y), getImg, TRIPLE_BACK)
                drawSingleIcon(animations, ConstPoint(pos.x + spread, pos.y + back.y), getImg, TRIPLE_BACK)
                drawSingleIcon(animations, ConstPoint(pos.x, pos.y + TRIPLE_RISE / 2), getImg, Colour.white)
            }
        }
    }

    private fun drawSingleIcon(animations: Animations, pos: IPoint, getImg: (String) -> Image, filter: Colour) {
        var base: Image? = null

        // For indoor drones, use their portrait.
        // For some reason, the ion intruder drone (BOARDER_ION) doesn't set
        // it's iconImage such that we can access it - likely due to it being
        // hardcoded - so leave it null to use missingImage.
        // (the Ion Intruder has its own look, if its animations are there)
        val ionLook = type == DroneType.BOARDER && baseName == BoardingDrone.ION_INTRUDER &&
                animations.animations.containsKey("${BoardingDrone.ION_LOOK}_portrait")
        val indoorIcon = when {
            type == DroneType.REPAIR -> "repair"
            ionLook -> BoardingDrone.ION_LOOK
            type == DroneType.BATTLE || type == DroneType.BOARDER -> "battle"
            else -> null
        }
        if (indoorIcon != null) {
            val portraitAnim = animations.animations["${indoorIcon}_portrait"]
            if (portraitAnim != null) {
                val img = getImg(portraitAnim.sheet.sheetPath)
                base = portraitAnim.sheet.getSprite(img, portraitAnim.x, portraitAnim.y)
            }
        }

        // Special-case the hacking drone, though obviously it's probably
        // not great if the player is seeing this outside a debug menu.
        if (name == "DRONE_HACKING") {
            base = getImg("img/ship/drones/drone_hack_base.png")
        }

        // Otherwise use the outside charged image - this won't exist
        // for indoor drones, so only use it if there isn't a portrait.
        if (base == null && droneImage != null) {
            base = getImg("img/ship/drones/${droneImage}_charged.png")
        }

        if (base == null)
            base = getImg(Constants.MISSING_FILE_PATH)

        // Be sure to pixel-align the image, to make it as sharp as possible.
        val imgX = pos.x - base.width / 2
        var imgY = pos.y - base.height / 2

        if (type == DroneType.DEFENSE) {
            // Shift everything down to account for the gun sticking out.
            imgY += 4
        }

        base.draw(imgX.f, imgY.f, filter)

        // Draw the laser for defence drones.
        // Note that while the normal defence drones have a fully-charged
        // gun sprite, the anti-drone doesn't and uses a separate gun and
        // charge glow sprite.
        if (type == DroneType.DEFENSE) {
            val gunBase = getImg("img/ship/drones/${droneImage}_gun_on.png")
            gunBase.draw(imgX.f, imgY.f, filter)

            val gunGlow = getImg("img/ship/drones/${droneImage}_gun_charged.png")
            gunGlow.draw(imgX.f, imgY.f, filter)
        }
    }

    fun makeInstance(): AbstractDrone {
        return when (type) {
            DroneType.REPAIR -> RepairDrone(this)
            DroneType.BATTLE -> AntiPersonnelDrone(this)
            DroneType.BOARDER -> BoardingDrone(this)
            DroneType.COMBAT -> CombatDrone(this)
            DroneType.DEFENSE -> DefenceDrone(this)
            DroneType.SHIP_REPAIR -> HullRepairDrone(this)
            DroneType.SHIELD -> ShieldDrone(this)
            else -> DummyDrone(this)
        }
    }

    override fun finishSetup(content: InGameState.GameContent) {
        super.finishSetup(content)

        if (weaponBlueprintName != null) {
            weaponBlueprint = content.blueprintManager[weaponBlueprintName] as AbstractWeaponBlueprint
        }
    }

    enum class DroneType(val needsHostileShip: Boolean) {
        COMBAT(true),
        SHIP_REPAIR(false),
        DEFENSE(false),
        REPAIR(false),
        BATTLE(false),
        BOARDER(true),
        HACKING(true),
        SHIELD(false),
    }

    // A fake drone that does nothing, to avoid the game crashing
    // if an unsupported drone is deployed.
    // (This is more useful with enemy ships, as otherwise
    // unsupported drones being deployed by the enemy ship would trigger
    // a for-the-player unavoidable crash, with no apparent reason).
    private class DummyDrone(type: DroneBlueprint) : AbstractDrone(type) {
        init {
            println("WARNING: Initialising dummy drone ${type.name}, actual drone isn't implemented.")
        }
    }

    companion object {
        // The combined drones' icons (see drawIconUI): the gap between a
        // twin's two, how far a triple's back two are raised above its
        // front one, how far out to the sides they are (as a share of an
        // icon's width), and how much darker they're drawn
        private const val TWIN_GAP = 2
        private const val TRIPLE_RISE = 8
        private fun tripleSpread(iconWidth: Int): Int = (iconWidth * 0.55f).roundToInt()
        private val TRIPLE_BACK = Colour(0.65f, 0.65f, 0.65f, 1f)
    }

}
