package xyz.znix.xftl.crew

import org.jdom2.Element
import xyz.znix.xftl.*
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.PlayerShipUI
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.systems.*
import java.util.*
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Represents a crew member that can be hired by the player.
 */
abstract class LivingCrew(blueprint: CrewBlueprint, anims: Animations, room: Room, mode: SlotType) :
    AbstractCrew(blueprint, anims, room) {

    override val repairSpeed: Float get() = 1f + REPAIR_SKILL_BONUS[linkedSkillLevel(Skill.REPAIRS)] / 100f

    override val attackDamageMult: Float get() = 1f + COMBAT_SKILL_BONUS[linkedSkillLevel(Skill.COMBAT)] / 100f

    /**
     * The crew of a boss linked by the Chorus (see ColossalBoss.isLinked)
     * repair and fight as if fully skilled.
     */
    private fun linkedSkillLevel(skill: Skill): Int {
        if (ownerShip?.colossal?.isLinked == true)
            return COMBAT_SKILL_BONUS.size - 1
        return getSkillLevel(skill).level
    }

    open val isMindControlResistant: Boolean get() = false

    var info: LivingCrewInfo = LivingCrewInfo.generateRandom(blueprint, room.ship.sys)

    /**
     * Where this crewmember goes in the player's crew list: they're listed
     * in the order they joined, wherever they are (see
     * InGameState.updatePlayerCrew). Zero until they're first listed.
     */
    var listOrder: Int = 0

    /**
     * The ship that 'owns' this crewmember, or null if they were
     * spawned in by an intruder event.
     */
    var ownerShip: Ship? = if (mode == SlotType.CREW) room.ship else null

    /**
     * The mind control system that is actively controlling this crewmember.
     */
    var mindControlledBy: MindControl? = null

    /**
     * The position the player saved this crewmember to, for the
     * return-to-stations button.
     *
     * This is only set for player crew, and is in the ship room (not
     * ship pixel) coordinate systetm.
     */
    var savedPosition: ConstPoint? = null

    private var mindControlAnimation: FTLAnimation? = null

    override val playerControllable: Boolean
        get() = mindControlledBy == null && ownerShip?.isPlayerShip == true

    override val showRedOutline: Boolean get() = ownerShip?.isPlayerShip != true

    // If a crewmember is mind controlled, you have vision of it whether
    // it belongs to the player or not.
    override val providesPlayerVision: Boolean
        get() = ownerShip?.isPlayerShip == true || mindControlledBy != null

    @Suppress("SENSELESS_COMPARISON")
    override val backImg: Image?
        get() = when {
            // This seems unnecessary since info is non-nullable, but it's read
            // in the AbstractCrew constructor.
            info == null -> super.backImg

            info.isFemale -> game.getImg("img/people/female_color.png")
            else -> super.backImg
        }

    override val mode: SlotType
        get() {
            var side = when (room.ship) {
                ownerShip -> SlotType.CREW
                else -> SlotType.INTRUDER
            }

            if (mindControlledBy != null) {
                side = side.other
            }

            return side
        }

    // Their ship's Mantis Pheromones make them walk 25% faster
    override val speedMultiplier: Float
        get() = 1f + (getAugmentValue(AugmentBlueprint.MANTIS_PHEROMONES) ?: 0f)

    override val suffocationMultiplier: Float
        get() {
            getAugmentValue(AugmentBlueprint.OXYGEN_MASKS)?.let { return it }
            return 1f
        }

    // Whether this crewmember's health was low last update, for the
    // warning sound (see update)
    private var wasLowHealth = false

    private val lowHealthSound by lazy { game.sounds.getSample("lowCrewHealth") }

    // The medbay's healing (or, hacked, its hurting), shown over them for a
    // moment after each bit of it (see showHealing)
    private var healingShownFor = 0f
    private var healingHurts = false
    private var healingAnimation: FTLAnimation? = null

    /**
     * Show the medbay healing them (or, with [hurts], a hacked one hurting
     * them), as vanilla does: green crosses rising over them (or red ones).
     * Called each update they're healed.
     */
    fun showHealing(hurts: Boolean) {
        if (hurts != healingHurts)
            healingAnimation = null
        healingHurts = hurts
        healingShownFor = HEALING_SHOWN_FOR
    }

    override fun update(dt: Float) {
        skillUpTime = (skillUpTime - dt).coerceAtLeast(0f)

        // Update the mind-control status now, so it applies for
        // the rest of the update cycle.
        if (mindControlledBy?.isControlling(this) != true) {
            mindControlledBy = null
            mindControlAnimation = null
        } else {
            mindControlAnimation?.update(dt)
        }

        super.update(dt)

        if (healingShownFor > 0f) {
            healingShownFor -= dt
            healingAnimation?.update(dt)
        } else {
            healingAnimation = null
        }

        // Warn the player when one of their crew's health gets low, as
        // vanilla does, once each time it drops below that
        val lowHealth = health > 0f && health < maxHealth * LOW_HEALTH_FRACTION
        if (lowHealth && !wasLowHealth && ownerShip?.isPlayerShip == true && !game.isCurrentlyLoadingSave)
            lowHealthSound.play()
        wasLowHealth = lowHealth
    }

    override fun drawForeground(g: Graphics) {
        super.drawForeground(g)

        // The medbay's crosses, over them
        if (healingShownFor > 0f) {
            val animation = healingAnimation
                ?: (if (healingHurts) HURTING_ANIMATION else HEALING_ANIMATION).startLooping(game)
                    .also { healingAnimation = it }
            animation.draw(screenX.f + HEALING_OFFSET_X, screenY.f + HEALING_OFFSET_Y)
        }

        if (mindControlledBy == null)
            return

        // Draw the mind-control animation
        if (mindControlAnimation == null) {
            mindControlAnimation = game.animations["mindcontrol"].startLooping(game)
        }

        val filter = when (showRedOutline) {
            true -> Colour.red
            false -> Colour.green
        }

        if (showHealthBar) {
            // Draw to the left of the health bar
            mindControlAnimation!!.draw(screenX.f - 11, screenY.f - 3, filter)
        } else {
            // Draw in the centre, above the crew
            mindControlAnimation!!.draw(screenX.f + 9, screenY.f, filter)
        }
    }

    override fun drawImage(x0: Float, y0: Float, x1: Float, y1: Float, baseFrame: Image, alpha: Float) {
        info.drawImage(x0, y0, x1, y1, baseFrame, alpha, true)
    }

    override fun onMidTeleport() {
        super.onMidTeleport()

        // Apply the reconstructive teleport effect now, since this
        // is both the mid-point of our animation, and we can't take
        // any more damage on the enemy ship.
        if (currentAction != Action.DYING && getAugmentValue(AugmentBlueprint.RECONSTRUCTIVE_TELEPORT) != null) {
            health = maxHealth
        }
    }

    override fun onFinishedDying() {
        super.onFinishedDying()

        val clonebay = ownerShip?.clonebay ?: return
        clonebay.addDeadCrew(this)
    }

    override fun onCloned() {
        super.onCloned()

        // Deduct 20% (of a single level) from all skills. This deduction
        // is then rounded down to an integer number of actions.
        for (skill in Skill.values()) {
            val actionsToDeduct = (skill.actionsPerStep * 0.20f).toInt()
            val oldLevel = info.skills.getValue(skill)
            val newLevel = (oldLevel - actionsToDeduct * skill.amountPerAction).coerceIn(0f..1f)
            info.skills[skill] = newLevel
        }
    }

    override fun onFinishedRepair(sys: AbstractSystem) {
        super.onFinishedRepair(sys)
        addSkillPoint(Skill.REPAIRS)
    }

    override fun onFinishedExtinguishing() {
        super.onFinishedExtinguishing()

        // Putting out fires doesn't give repair skill. The code for it is
        // there in vanilla, but Repairable::PartialRepair compares the final
        // negative health value to zero to check if it's done, which is
        // thus always false.
    }

    override fun onKilledCrew(enemy: AbstractCrew) {
        super.onKilledCrew(enemy)
        addSkillPoint(Skill.COMBAT)
    }

    override fun onSabotagedSystem(system: AbstractSystem) {
        super.onSabotagedSystem(system)

        // Damaging systems isn't any faster with the combat skill, but
        // it still awards experience.
        addSkillPoint(Skill.COMBAT)
    }

    /**
     * A quick way for systems to credit the crewmember with performing a single action.
     */
    fun addSkillPoint(skill: Skill) {
        val oldLevel = getSkillLevel(skill)
        val oldProgress = info.skills.getValue(skill)
        val newProgress = (oldProgress + skill.amountPerAction).coerceIn(0f..1f)
        info.skills[skill] = newProgress

        // Levelled up: the crew box shows it (see PlayerShipUI.drawCrewBox),
        // with vanilla's sound for the player's crew
        if (getSkillLevel(skill) != oldLevel) {
            skillUp = skill
            skillUpTime = SKILL_UP_TIME
            if (ownerShip?.isPlayerShip == true)
                game.sounds.getSampleOrWarn("levelup")?.play()
        }
    }

    /** The skill that last levelled up, shown for [skillUpTime] more seconds. */
    var skillUp: Skill? = null
        private set
    var skillUpTime: Float = 0f
        private set

    // Helper function
    fun getSkillLevel(skill: Skill): SkillLevel = info.getSkillLevel(skill)

    protected fun getAugmentValue(name: String): Float? {
        return ownerShip?.getAugmentValueOrNull(name)
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        // A boarder whose ship has gone (such as a wingman that was
        // destroyed) isn't saved with its ship, which isn't in the save
        val owner = ownerShip?.takeIf { refs.contains(it) }
        SaveUtil.addAttrRef(elem, "ownerShip", refs, owner)
        info.saveToXML(elem, false)

        savedPosition?.let { SaveUtil.addPoint(elem, "savedPos", it) }
        if (listOrder != 0)
            SaveUtil.addAttrInt(elem, "listOrder", listOrder)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        super.loadFromXML(elem, refs)

        SaveUtil.getAttrRef(elem, "ownerShip", refs, Ship::class.java) { ownerShip = it }
        info = LivingCrewInfo.loadFromXMLWithRace(elem, blueprint, game)

        if (elem.getChild("savedPos") != null) {
            savedPosition = SaveUtil.getPoint(elem, "savedPos")
        }
        // (missing in older saves, and for crew not listed yet)
        listOrder = SaveUtil.getOptionalAttrInt(elem, "listOrder") ?: 0
    }

    companion object {
        /** How long a skill that's levelled up is shown on the crew box. */
        const val SKILL_UP_TIME = 2f

        // For each skill level (vanilla's three were 0, 10 and 20)
        val REPAIR_SKILL_BONUS = listOf(0, 8, 16, 24, 32, 40)
        val COMBAT_SKILL_BONUS = listOf(0, 8, 16, 24, 32, 40)

        // Below this much of their health, the player's crew are warned about
        const val LOW_HEALTH_FRACTION = 0.25f

        // The medbay's healing and hurting (see showHealing), from vanilla's
        // images (which its animations.xml doesn't list): eleven frames, a
        // second round. They're shown a little while after the last bit of
        // healing, and a little above the crew's sprite.
        private fun healingAnimation(path: String, name: String): AnimationSpec {
            val sheet = Animations.SpriteSheetSpec(path, 32, 32, 352, 32)
            return AnimationSpec(sheet, name, 0, 0, sheet.horizontalCount, 1f / sheet.horizontalCount)
        }
        val HEALING_ANIMATION = healingAnimation("img/people/healing_strip11.png", "xftl_hardcoded_healing")
        val HURTING_ANIMATION = healingAnimation("img/people/healing_hurt_strip11.png", "xftl_hardcoded_healing_hurt")
        const val HEALING_SHOWN_FOR = 0.2f
        const val HEALING_OFFSET_X = 2f
        const val HEALING_OFFSET_Y = -6f
    }
}

/**
 * Attributes about a crewmember that isn't spawned into the world.
 *
 * This is used for things like events and shops, to show you a player
 * without spawning them.
 */
class LivingCrewInfo(
    private val game: InGameState,

    val race: CrewBlueprint,

    name: String,

    /**
     * The index of this crew's colour selection. This is used to select
     * what colours are applied to the layer filter.
     *
     * This is how different crewmembers can have different colours.
     */
    var colour: Int
) {
    /**
     * The player facing name of this crew, eg 'Slocknog' or some
     * other name that's selected by the player, by an event or
     * from the default list of names.
     */
    var name: String = name
        set(value) {
            if (field == value)
                return

            field = value
            shortName = findShortName()
        }

    var shortName: String = findShortName()
        private set

    /**
     * The crew's skills. 0 means no skill, 1 means fully yellow (max level),
     * with the levels evenly between (see [SkillLevel]).
     */
    val skills: EnumMap<Skill, Float> = EnumMap(Skill.entries.associateWith { 0f })

    /**
     * For humans, this determines whether they're male or female.
     */
    @Suppress("ConvertTwoComparisonsToRangeCheck") // Intention is clearer like this
    val isFemale: Boolean get() = colour >= race.baseNumberOfColours && race.baseNumberOfColours > 0

    private val portraitImage: Image = game.animations["${race.name}_portrait"].spriteAt(game, 0)

    /**
     * Draw this crew's portrait, without having to construct the crewmember instance.
     *
     * Useful for events and shops.
     */
    fun drawPortrait(x: Int, y: Int, scale: Float = 1f) {
        drawImage(
            x.f, y.f,
            x.f + portraitImage.width * scale, y.f + portraitImage.height * scale,
            portraitImage,
            1f,
            false
        )
    }

    // This is moved out of LivingCrew so it can be used in drawPortrait.
    fun drawImage(
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        baseFrame: Image,
        alpha: Float,
        skipColours: Boolean
    ) {
        val texX0 = baseFrame.textureOffsetX
        val texY0 = baseFrame.textureOffsetY
        val texX1 = texX0 + baseFrame.width
        val texY1 = texY0 + baseFrame.height

        // Find the sprite sheet for this race. In the case of humans, there's
        // separate male/female sprite sheets, with the unfortunate naming
        // of 'human' for the male version and 'female' for the female version.
        val effectiveColour = if (isFemale) colour - race.baseNumberOfColours else colour
        val baseAnimationName = if (isFemale) "female" else race.name
        val imageNames = if (isFemale) race.femaleLayerImageNames else race.layerImageNames

        val baseImagePath = when {
            isFemale -> "img/people/female_base.png"
            else -> game.animations["${baseAnimationName}_portrait"].sheet.sheetPath
        }
        val fullBaseImage = game.getImg(baseImagePath)

        // Draw the base image, without any tinting
        fullBaseImage.draw(x0, y0, x1, y1, texX0.f, texY0.f, texX1.f, texY1.f, alpha, Colour.white)

        if (skipColours)
            return

        // Draw all the layers
        for ((index, path) in imageNames.withIndex()) {
            val layer = game.getImg(path)

            val colours = race.colourFilters[index]
            val filter = colours[effectiveColour % colours.size]

            layer.draw(x0, y0, x1, y1, texX0.f, texY0.f, texX1.f, texY1.f, alpha, filter)
        }
    }

    fun getSkillLevel(skill: Skill): SkillLevel = SkillLevel.of(skills.getValue(skill))

    /**
     * Draw a skill's progress: green filling up through its levels (marked
     * off by white lines), and all yellow once it's learnt fully. Each
     * level's cell is the same width, so the bar's cut down to the widest
     * that fits in [width] (see [skillBarWidth]).
     */
    fun drawSkillProgressBar(g: Graphics, x: Int, y: Int, width: Int, height: Int, skill: Skill) {
        val level = getSkillLevel(skill)
        val progress = skills.getValue(skill)
        val cells = SkillLevel.MAX_LEVEL
        val cell = skillBarCellWidth(width)
        val barWidth = skillBarWidth(cell)

        // Draw the outer box around the bar.
        // Note that drawRect draws its lower and right lines outside
        // the specified region, so -1 from width and height.
        g.colour = Colour.white
        g.drawRect(x.f, y.f, barWidth - 1f, height - 1f)

        if (level.isMax) {
            g.colour = Constants.SYS_ENERGY_REPAIR
            g.fillRect(x + 1f, y + 1f, barWidth - 2f, height - 2f)
            return
        }

        for (i in 0 until cells) {
            val cellX = x + 1 + i * (cell + 1)

            // How far through this level they are
            val filled = ((progress - i * SkillLevel.STEP) / SkillLevel.STEP).coerceIn(0f, 1f)
            g.colour = Constants.SYS_ENERGY_ACTIVE
            g.fillRect(cellX.f, y + 1f, (cell * filled).roundToInt().f, height - 2f)

            // The mark before the next level
            if (i < cells - 1) {
                g.colour = Colour.white
                g.fillRect((cellX + cell).f, y + 1f, 1f, height - 2f)
            }
        }
    }

    private fun findShortName(): String {
        // Pick a short name.
        // This does a few things:
        // * Use the name as-is if it's short enough
        // * Check if there's a pre-defined short version of the name
        // * Pick the last (space-separated) part of the name
        // * Pick the shortest-by-pixel-length part of the name, and truncate it

        // If we're running in an automated test, we can't use fonts.
        if (game.isRunningAutomatedTest) {
            return name
        }

        val font = game.getFont(PlayerShipUI.CREW_NAME_FONT)

        // Already short enough?
        if (font.getWidth(name) < PlayerShipUI.MAX_NAME_WIDTH) {
            return name
        }

        // Pre-defined short version?
        game.nameManager.findShort(name)?.let { return it }

        // Last name short enough?
        val parts = name.split(" ")
        val lastName = parts.last()
        if (font.getWidth(lastName) < PlayerShipUI.MAX_NAME_WIDTH) {
            return lastName
        }

        // Find the shortest part, and truncate it
        var shortened = parts.minBy { font.getWidth(it) }

        while (font.getWidth("$shortened.") >= PlayerShipUI.MAX_NAME_WIDTH) {
            shortened = shortened.substring(0, shortened.length - 1)
        }

        return "$shortened."
    }

    fun saveToXML(elem: Element, includeRace: Boolean = true) {
        if (includeRace) {
            SaveUtil.addAttr(elem, "race", race.name)
        }
        SaveUtil.addAttr(elem, "selectedName", name)
        SaveUtil.addAttrInt(elem, "colour", colour)

        for ((skill, value) in skills) {
            if (value == 0f)
                continue

            val skillElem = Element("skill")
            SaveUtil.addAttr(skillElem, "id", skill.name)
            SaveUtil.addAttrFloat(skillElem, "value", value)
            elem.addContent(skillElem)
        }
    }

    companion object {
        @JvmStatic
        fun generateRandom(race: CrewBlueprint, game: InGameState): LivingCrewInfo {
            val isMale = when {
                race.name != "human" -> null // Aliens are genderless
                else -> Random.nextBoolean()
            }
            val colour: Int = when {
                race.numberOfColours == 0 -> 0

                // For humans, make sure we set the crew's visual gender to match their name.
                isMale == true -> Random.nextInt(race.baseNumberOfColours)
                isMale == false -> Random.nextInt(race.baseNumberOfColours) + race.baseNumberOfColours

                else -> Random.nextInt(race.numberOfColours) // Aliens
            }

            val name = game.nameManager.getForGender(isMale, Random.Default)
            return LivingCrewInfo(game, race, name, colour)
        }

        fun generateWithName(race: CrewBlueprint, game: InGameState, name: String): LivingCrewInfo {
            val colour: Int = when (val max = race.numberOfColours) {
                0 -> 0
                else -> Random.nextInt(max)
            }
            return LivingCrewInfo(game, race, name, colour)
        }

        fun loadFromXML(elem: Element, game: InGameState): LivingCrewInfo {
            val raceName = SaveUtil.getAttr(elem, "race")
            val race = game.blueprintManager[raceName] as CrewBlueprint
            return loadFromXMLWithRace(elem, race, game)
        }

        fun loadFromXMLWithRace(elem: Element, race: CrewBlueprint, game: InGameState): LivingCrewInfo {
            val name = SaveUtil.getAttr(elem, "selectedName")
            val colour = SaveUtil.getAttrInt(elem, "colour")
            val info = LivingCrewInfo(game, race, name, colour)

            // Skills are omitted if they're set to zero, which is what the default is anyway.
            for (skillElem in elem.getChildren("skill")) {
                val skillName = SaveUtil.getAttr(skillElem, "id")
                val value = SaveUtil.getAttrFloat(skillElem, "value")

                val skill = Skill.valueOf(skillName)
                info.skills[skill] = value
            }

            return info
        }
    }
}

enum class Skill(
    val xmlName: String,

    /**
     * The number of actions a non-human (no 10% boost) has to perform
     * to make a single colour change in vanilla (0->50% or 50->100%). XFTL's
     * six levels take [SkillLevel.TIME_FACTOR] times as long in all, see [actionsPerStep].
     */
    val actionsPerLevel: Int
) {
    PILOTING("pilot", 15),
    ENGINES("engines", 15),
    SHIELDS("shields", 55),
    WEAPONS("weapons", 65),
    REPAIRS("repair", 18),
    COMBAT("combat", 8),
    ;

    val iconPath: String = "img/people/skill_${xmlName}_white.png"

    // Vanilla takes actionsPerLevel actions for each of its two colour
    // changes (the 0-1 progress going 0.5 at a time); XFTL takes TIME_FACTOR
    // times that, over its five levels
    val amountPerAction = 1f / (actionsPerLevel * 2f * SkillLevel.TIME_FACTOR)

    /** How many actions each of XFTL's levels takes. */
    val actionsPerStep: Int get() = (actionsPerLevel * 2f * SkillLevel.TIME_FACTOR / SkillLevel.MAX_LEVEL).roundToInt()

    fun getDescription(game: InGameState, level: SkillLevel): String {
        val levelNum = level.level

        return when (this) {
            PILOTING -> game.translator["pilot_skill"].replaceArg(Piloting.SKILL_BONUSES[levelNum])
            ENGINES -> game.translator["engine_skill"].replaceArg(Engines.SKILL_BONUSES[levelNum])
            SHIELDS -> game.translator["shield_skill"].replaceArg(Shields.SKILL_BONUSES[levelNum])
            WEAPONS -> game.translator["weapon_skill"].replaceArg(Weapons.SKILL_BONUSES[levelNum])

            REPAIRS -> when {
                level.isBase -> game.translator["repair_0"]
                else -> game.translator["repair_skilled"].replaceArg(LivingCrew.REPAIR_SKILL_BONUS[levelNum])
            }

            COMBAT -> when {
                level.isBase -> game.translator["combat_0"]
                else -> game.translator["combat_skilled"].replaceArg(LivingCrew.COMBAT_SKILL_BONUS[levelNum])
            }
        }
    }
}

/**
 * How far a crewmember's learnt a skill: XFTL has six levels, from 0 (no
 * advantage, white, as vanilla's first) to [MAX_LEVEL] (yellow, as vanilla's
 * last), with the four between green, each giving a bigger bonus than the
 * last (vanilla has just one green level). See doc/game/skills.md.
 */
data class SkillLevel(val level: Int) {
    val isBase: Boolean get() = level == 0
    val isMax: Boolean get() = level >= MAX_LEVEL

    /** One of the green levels, between none and the most. */
    val isPartial: Boolean get() = !isBase && !isMax

    companion object {
        const val MAX_LEVEL = 5

        /** How many levels there are, counting none. */
        const val COUNT = MAX_LEVEL + 1

        val BASE = SkillLevel(0)
        val MAX = SkillLevel(MAX_LEVEL)

        /** How many times as long as vanilla's it takes to learn a skill fully. */
        const val TIME_FACTOR = 2f

        /** How much of a skill's 0-1 progress each level takes. */
        const val STEP = 1f / MAX_LEVEL

        /** The level for a skill's 0-1 progress. */
        fun of(progress: Float): SkillLevel =
            SkillLevel((progress * MAX_LEVEL + 0.0001f).toInt().coerceIn(0, MAX_LEVEL))

        /** The 0-1 progress at the start of [level]. */
        fun progressAt(level: Int): Float = (level * STEP).coerceIn(0f, 1f)
    }
}

/**
 * The width of each level's cell in a skill's progress bar (see
 * [LivingCrewInfo.drawSkillProgressBar]) up to [width] pixels wide, with its
 * outline and the one-pixel marks between the cells.
 */
fun skillBarCellWidth(width: Int): Int =
    ((width - 2 - (SkillLevel.MAX_LEVEL - 1)) / SkillLevel.MAX_LEVEL).coerceAtLeast(1)

/** How wide a skill's progress bar is, with [cell] pixel wide cells. */
fun skillBarWidth(cell: Int): Int = SkillLevel.MAX_LEVEL * cell + (SkillLevel.MAX_LEVEL - 1) + 2
