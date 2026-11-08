package xyz.znix.xftl

import org.jdom2.Element
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.ai.FriendlyCrewAI
import xyz.znix.xftl.ai.IntruderCrewAI
import xyz.znix.xftl.augments.AugPreigniter
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.*
import xyz.znix.xftl.drones.AbstractDrone
import xyz.znix.xftl.drones.AbstractExternalDrone
import xyz.znix.xftl.drones.ShieldDrone
import xyz.znix.xftl.drones.AbstractIndoorsDrone
import xyz.znix.xftl.game.*
import xyz.znix.xftl.hangar.EditableShip
import xyz.znix.xftl.hangar.FinalisedEditableSystem
import xyz.znix.xftl.layout.*
import xyz.znix.xftl.math.*
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.CrosshairNumbers
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.savegame.ISerialReferencable
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.shipgen.EnemyShipSpec
import xyz.znix.xftl.sector.Sector
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.*
import java.util.*
import java.util.stream.Collectors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class Ship(
    val type: ShipBlueprint,

    val sys: InGameState,

    /**
     * If this ship was customised by the player, this is it.
     *
     * Note this must ABSOLUTELY NOT be modified once the game has started!
     * Serialisation in particular would be completely wrecked if the room
     * indices change.
     *
     * While it would be possible to always create an EditableShip instance,
     * even for unmodified ships, it constraints what simplifications we can
     * do with EditableShip while still being able to properly load all the
     * vanilla ships, with all their details.
     */
    val customised: EditableShip?,

    val spec: EnemyShipSpec?,

    boss: BossManager?,
) :
    ISerialReferencable {

    // Helper methods, providing quick access to important stuff from the blueprint
    val name: String get() = type.name
    /**
     * The ship's maximum hull (see doc/game/hull.md). For a normal ship, it's its
     * blueprint's hull times its size (see [hullScale]). An enemy ship's
     * bigger versions, and the hybrids, have their [baseHull] times
     * [HULL_FACTOR_LONG] or [HULL_FACTOR_WIDE] (and [HULL_FACTOR_HYBRID] for
     * a hybrid), up to as much as their hull bar shows evenly with a cell
     * kept for bosses ([MAX_ORDINARY_HULL_CELLS] cells of [hullScale] points).
     */
    val maxHealth: Int
        get() {
            if (!type.isVariant && !isHybrid)
                return type.maxHealth * hullScale

            var factor = when {
                !type.isVariant -> 1f
                type.shape == ShipShape.WIDE -> HULL_FACTOR_WIDE
                else -> HULL_FACTOR_LONG
            }
            if (isHybrid)
                factor *= HULL_FACTOR_HYBRID
            val hull = kotlin.math.ceil(baseHull * factor).toInt()
            return hull.coerceAtMost(MAX_ORDINARY_HULL_CELLS * hullScale)
        }

    /**
     * The hull of the normal-sized ship this is: for a bigger version of a
     * ship (see [ShipBlueprint.variantOf]), that ship's, rather than what its
     * own blueprint says, so the versions are always in proportion. (Rock
     * ships have more to start with, and a hybrid has the more of its two
     * races' ships', so that carries through to their bigger versions.)
     */
    val baseHull: Int
        get() = type.variantOf.firstOrNull()
            ?.let { (sys.blueprintManager.getOrNull(it) as? LazyShipBlueprint)?.real?.maxHealth }
            ?: type.maxHealth

    /**
     * True for a hybrid ship (see doc/game/hybrid-ships.md), or a bigger version
     * of one.
     */
    val isHybrid: Boolean
        get() = (type.variantOf.firstOrNull() ?: name).startsWith(HYBRID_PREFIX)

    /**
     * How many ships this one's worth: its size, or for a bigger version of
     * a vanilla ship, its [ShipShape.strength]. A normal-sized or long
     * enemy ship's hull bar cells show this many points each (a wide one's
     * window has room for cells of a point each, see doc/game/hull.md).
     */
    val hullScale: Int get() = if (type.isVariant) type.shape.strength else type.shipSize
    val isPlayerShip: Boolean get() = type.isPlayerShip
    val isUsingBossUI: Boolean get() = boss?.useBossShipUI ?: false

    /**
     * For one of the size 3 bosses (see doc/game/bosses.md), its mechanics.
     */
    val colossal: ColossalBoss? = type.bossKind?.let { ColossalBoss(it, this) }

    /**
     * How long this ship's been fighting the player's, in seconds, for the
     * Chorus Relay Shard (see MindControl). Not saved: a loaded fight
     * starts again from zero.
     */
    var hostileTime: Float = 0f
    val isAutoScout: Boolean get() = type.isAutoScout || automated

    /**
     * Set once a boss's crew are all killed, as in vanilla: its AI takes
     * over, and it fights on as an automated ship (see [isAutoScout]).
     */
    var automated: Boolean = false
    val weaponSlots: Int? get() = customised?.weaponSlots ?: type.weaponSlots

    /**
     * The maximum number of crew the player can have on this ship.
     */
    val maxCrew: Int get() = type.maxCrew

    /**
     * The maximum number of main systems that can be installed on this ship.
     */
    val maxMainSystems: Int get() = type.maxSystems

    /**
     * The maximum number of subsystems (piloting, sensors, doors and backup
     * batteries) that can be installed on this ship.
     */
    val maxSubSystems: Int get() = MAX_SUB_SYSTEMS

    /**
     * The maximum number of augments this ship can have.
     */
    val maxAugments: Int get() = type.maxAugments
    val droneSlots: Int? get() = customised?.droneSlots ?: type.droneSlots

    /**
     * If this ship is a boss, this points to its manager. Null otherwise.
     */
    var boss: BossManager? = boss
        private set // Mutable only for deserialisation

    val rooms: List<Room>
    val doors: List<Door>

    val offset: ConstPoint = type.roomOffset
    val floorOffset: ConstPoint = type.floorOffset
    val cloakOffset: ConstPoint = type.cloakOffset
    val hullOffset: ConstPoint

    val weaponFireDirection: Direction = when (type.isPlayerShip) {
        true -> Direction.RIGHT // Weapons fly right
        false -> Direction.UP // Weapons fly upwards
    }

    // Played when any two projectiles collide.
    private val projectileCollisionSound: FTLSound = sys.sounds.getSample("hitHull1")
    val breachSound: FTLSound = sys.sounds.getSample("hullBreach")
    val airLossSound: FTLSound = sys.sounds.getSample("airLoss")

    val floorImage: Image? = type.floorImage?.let { sys.getImg(it) }
    val hullImage: Image = type.hullImage.firstNotNullOf { sys.getImgIfExists(it) }
    val cloakImage: Image? = type.cloakImage?.let { sys.getImg(it) }
    val gibs: List<ShipGib.Instance>

    val shieldImage: Image = sys.getImg(type.shieldImage)

    val selectedShieldHalfSize: ConstPoint
    val shieldHalfSize: ConstPoint

    val shieldOrigin: ConstPoint

    // All the crew currently standing on this ship. This includes drone pawns.
    val crew: MutableList<AbstractCrew> = ArrayList()

    // The subset of the crew that are or aren't intruders. This also includes drone pawns.
    val intruders: List<AbstractCrew> = ArrayList()
    val friendlyCrew: List<AbstractCrew> = ArrayList()

    val cargoBlueprints = ArrayList<Blueprint?>(arrayOfNulls<Blueprint>(type.maxCargo).toList())

    /**
     * The items, and augments, the ship got with no room for them: they wait
     * in the equipment window's over capacity boxes, to be swapped with
     * something the player doesn't want, and are left behind when the ship
     * jumps (see [addBlueprint]).
     */
    val overflowItems = ArrayList<Blueprint>()
    val overflowAugments = ArrayList<AugmentBlueprint>()

    /**
     * The ship's augment slots. This should only be used for UI relating
     * to the augment objects as seen on the ship screen - in the future,
     * mods will be able to apply blueprint effects to the ship without
     * using an augment slot, and those show up in [augmentValues], so it
     * should be used for mod compatibility whenever possible.
     *
     * One must always call [cargoUpdated] after modifying this, so the
     * changes show up in [augmentValues].
     */
    val augments = ArrayList<AugmentBlueprint>()

    /**
     * This contains the sum of the values for each type of augment. Mods
     * will be able to apply augment effects without a regular augments,
     * and those effects will then be stored here.
     */
    private val privateAugmentValues = HashMap<AugmentBlueprint, Float>()
    val augmentValues: Map<AugmentBlueprint, Float> = Collections.unmodifiableMap(privateAugmentValues)

    val pathFinder: PathFinder

    val hardpoints: List<Hardpoint>

    // This contains all the projectiles, both incoming and outgoing.
    val projectiles: MutableList<IProjectile> = ArrayList()

    val inboundBeams: MutableList<BeamBlueprint.BeamInstance> = ArrayList()
    val animations: MutableList<FloatingAnimation> = ArrayList()

    private val damageNumbers = ArrayList<DamageNumber>()

    /**
     * A list of all the drones (friendly or otherwise) that are deployed
     * around this ship.
     */
    val externalDrones = ArrayList<AbstractExternalDrone>()

    /**
     * The AI that controls friendly (non-intruder) crew on this ship.
     *
     * It's present on all ships, including the player ship. This is because
     * it's used for mind-controlled intruders, as well as regular enemies
     * on their ships.
     */
    val crewAI = FriendlyCrewAI(this)

    val intruderAI = IntruderCrewAI(this)

    // This really is a bit horrible - if this is the enemy ship, we store the beam the
    // player is currently aiming at us to highlight the affected rooms.
    private var inboundBeamAim: SelectedTarget.BeamAim? = null

    // The number of fuel, missiles and drones this ship has. The missiles and drones
    // are set during ship loading. Player ships always seem to start with 16 fuel.
    var fuelCount: Int = STARTING_FUEL
    var missilesCount: Int = 0
    var dronesCount: Int = 0

    // How much scrap the player has (see startingScrap for player ships)
    var scrap: Int = startingScrap(sys.difficulty, 1)

    // How far through charging the FTL drive, 1=fully charged.
    // These only apply to the player, enemies use a fixed timer.
    var ftlChargeProgress: Float = 0f
    val isFtlCharged get() = ftlChargeProgress >= 1f
    val isFtlReady get() = isFtlCharged && engineSystems.any { it.powerSelected > 0 }

    // This is for both the player and enemies
    val canChargeFTL: Boolean
        get() {
            // For the FTL to charge, the engines and piloting must
            // be working, and a pilot must be present.
            val hasPilot = isAutoScout || friendlyCrew.any { it.room == piloting!!.room }
            return areEnginesRunning && piloting!!.undamagedEnergy > 0 && hasPilot
                    && !piloting!!.isHackActive
        }

    val maxReactorPower: Int get() = 100


    // The raw amount of reactor power purchased by the player
    var purchasedReactorPower: Int = 5

    // The amount of reactor power available for use by the player, taking ion storms, events and so on into account
    val reactorPower: Int get() = purchasedReactorPower

    // The unused reactor/battery power
    val powerAvailableTypes = HashMap<EnergySource, Int>()

    val powerAvailable: Int get() = powerAvailableTypes.values.sum()

    /**
     * The amount of power that's unusable due to events or the environment (in
     * a plasma storm).
     */
    var blockedReactorPower: Int = 0
        private set

    var health = maxHealth
        set(value) {
            field = value.coerceAtLeast(0).coerceAtMost(maxHealth)
        }

    // The amount of health at which this ship will surrender and attempt to escape
    var escapeHealth: Int = 0
    var surrenderHealth: Int = 0

    /**
     * If this ship is attempting to escape, this is the time remaining until it jumps.
     */
    var escapeTimer: Float? = null

    /**
     * The ship's own maximum number of super-shield levels, not counting
     * any extra from Zoltan shield augments - see [maxSuperShield].
     *
     * This is always 5, except for the flagship since it has a super-super-shield.
     */
    var baseMaxSuperShield: Int = 5

    /**
     * The maximum number of super-shield levels this ship can have.
     *
     * Zoltan shield augments stack, each one adding five levels, so this is
     * worked out from them. It goes down again if one is sold or lost.
     *
     * Shield overcharger drones each add a row of five too, which they
     * recharge (see [ShieldDrone]), as long as they're powered. They don't
     * add to the Zoltan shields' rows: the bigger of the two counts. So
     * turning one off loses its row straight away.
     */
    val maxSuperShield: Int
        get() {
            val rows = maxOf(zoltanShieldCount, shieldDroneCount)
            return maxOf(baseMaxSuperShield, rows * AugZoltanShield.SHIELD_PER_AUGMENT)
        }

    /**
     * The number of powered shield overcharger drones around this ship,
     * capped like [zoltanShieldCount]. One that's ioned or hacked still
     * counts, as it's only out for a while.
     */
    val shieldDroneCount: Int
        get() = externalDrones.count { it is ShieldDrone && it.isPowered }.coerceAtMost(AugZoltanShield.MAX_STACKED)

    /**
     * The number of Zoltan shield augments whose super-shields stack up,
     * which is capped at [AugZoltanShield.MAX_STACKED].
     */
    val zoltanShieldCount: Int
        get() = augments.count { it is AugZoltanShield }.coerceAtMost(AugZoltanShield.MAX_STACKED)

    /**
     * The value of [zoltanShieldCount] the last time [cargoUpdated] ran,
     * to notice when a Zoltan shield is sold or lost.
     */
    private var lastZoltanShieldCount: Int = 0

    /**
     * The number of points of super-shield this ship currently has.
     */
    var superShield: Int = 0
        set(value) {
            field = value.coerceIn(0..maxSuperShield)
        }

    /**
     * Returns true if this ship has ran out of health
     */
    val isDead: Boolean get() = health == 0

    /**
     * Returns true if this ship is dead, and either it doesn't have any gibs or their animation has finished.
     */
    val isGone: Boolean get() = isDead && gibs.firstOrNull()?.isFinished != false

    val isCloakActive: Boolean get() = cloaking?.active ?: false

    /**
     * How opaque the ship's interior is drawn, its crew and drones among
     * them: half see-through once it's cloaked, fading as the cloak does.
     */
    val interiorAlpha: Float get() = 1f - (cloaking?.cloakFade ?: 0f) / 2f

    // The amount of power currently used by the ship's systems
    val powerConsumed: Int
        get() {
            var used = 0
            for (room in rooms) {
                used += (room.system as? MainSystem ?: continue).powerSelected
            }
            return used
        }

    /**
     * Set by SlickGame, this is true if the opponent's cloak is active.
     *
     * This prevents the weapons from charging.
     */
    var opponentCloakActive: Boolean = false

    /**
     * This is the list of all the possible systems that can be installed on this ship.
     *
     * It's simply the sum of all the [Room.systemSlots] lists.
     */
    val systemSlots: List<SystemInstallConfiguration>

    var systems: List<AbstractSystem> = emptyList()
        private set

    /**
     * The list of all the [MainSystem]s in the ship, sorted into the order
     * they appear left-to-right in the power UI.
     */
    var mainSystems: List<MainSystem> = emptyList()
        private set

    /**
     * The list of all the [SubSystem]s in the ship, sorted into the order
     * they appear in the subsystem tray.
     */
    var subSystems: List<SubSystem> = emptyList()
        private set

    /**
     * The ship's first weapons system.
     *
     * Most ships only have one, but a ship can have several - see
     * [weaponSystems] and [weaponsFor].
     */
    var weapons: Weapons? = null
        private set

    /**
     * All the ship's weapons systems, in the order they're listed in the
     * ship's blueprint. Each one controls a separate set of hardpoints.
     */
    var weaponSystems: List<Weapons> = emptyList()
        private set

    /**
     * The ship's first drones system.
     *
     * Most ships only have one, but a ship can have several - see [droneSystems].
     */
    var drones: Drones? = null
        private set

    /**
     * All the ship's drones systems, in the order they're listed in the
     * ship's blueprint. Each one has its own drone slots.
     */
    var droneSystems: List<Drones> = emptyList()
        private set

    /**
     * Find the drones system that a drone was launched from.
     */
    fun dronesSystemFor(drone: AbstractDrone): Drones? {
        return droneSystems.firstOrNull { system -> system.drones.any { it?.holds(drone) == true } }
    }

    var engines: Engines? = null
        private set

    /**
     * All the ship's engines systems. Ships bigger than vanilla can have
     * several, see [evasion]. [engines] is the first.
     */
    var engineSystems: List<Engines> = emptyList()
        private set

    /**
     * The ship's first shields system.
     *
     * Most ships only have one, but a ship can have several - see [shieldSystems].
     */
    var shields: Shields? = null
        private set

    /**
     * All the ship's shields systems, in the order they're listed in the
     * ship's blueprint. They each charge their own shield layers, which
     * stack together.
     */
    var shieldSystems: List<Shields> = emptyList()
        private set

    /**
     * The total number of shield layers that are currently up, across all
     * the shields systems.
     */
    val activeShieldLayers: Int get() = shieldSystems.sumOf { it.activeShields }

    /**
     * The shields system to take a hit: the one with the most layers up.
     */
    private val shieldsToHit: Shields? get() = shieldSystems.maxByOrNull { it.activeShields }

    var medbay: Medbay? = null
        private set

    /**
     * All the ship's medbays, in the order they're listed in the blueprint:
     * bigger ships with lots of crew can have several (see [medbay]).
     */
    var medbaySystems: List<Medbay> = emptyList()
        private set

    var clonebay: Clonebay? = null
        private set

    var cloaking: Cloaking? = null
        private set

    var teleporter: Teleporter? = null

    /**
     * All the ship's teleporters. Ships have one at most, apart from the
     * Stormbreaker (see ColossalBoss), whose second one sends its swarm.
     */
    var teleporterSystems: List<Teleporter> = emptyList()
        private set

    /**
     * The ship's first hacking system. Ships can have several - see [hackingSystems].
     */
    var hacking: Hacking? = null
        private set

    /**
     * All the ship's hacking systems, in the order they're listed in the
     * ship's blueprint. They can't hack the same room at once.
     */
    var hackingSystems: List<Hacking> = emptyList()
        private set

    /**
     * The ship's first mind control system. Ships can have several - see [mindControlSystems].
     */
    var mindControl: MindControl? = null
        private set

    /**
     * All the ship's mind control systems, in the order they're listed in
     * the ship's blueprint. They can't target the same room at once.
     */
    var mindControlSystems: List<MindControl> = emptyList()
        private set

    var piloting: Piloting? = null
        private set

    var sensors: Sensors? = null
        private set

    /**
     * All the ship's sensors systems. Ships that fight several enemy ships
     * can have several, one for each enemy ship (see [sensorsFor]).
     * [sensors] is the first.
     */
    var sensorSystems: List<Sensors> = emptyList()
        private set

    /**
     * Find which of this (the player's) ship's sensors systems shows [target].
     *
     * For the player's own ship that's any working one. For enemy ships it's
     * the one for the enemy ship's place: the first is for the main enemy
     * ship, the second for the first wingman, and so on. A ship without
     * enough sensors systems can't see into the extra enemy ships, and one
     * with more than enough uses its best ones (see [sensorsWatching]).
     */
    fun sensorsFor(target: Ship): Sensors? {
        if (target == this)
            return sensorSystems.firstOrNull { it.providesPlayerVision } ?: sensors

        // The player's sensors look at the ships fighting it left to right:
        // the first at the leftmost, and so on. When one's destroyed or
        // gives up, the rest move on to the next ones.
        // A ship that's given up while the others fight on isn't looked at,
        // unless the player's boarders are still aboard it.
        // With no fight on, they're looked at in their window's order.
        if (this == sys.player) {
            val watched = sys.watchedShipsLeftToRight
            if (watched.isNotEmpty()) {
                val index = watched.indexOf(target)
                return if (index >= 0) sensorsWatching(watched.size).getOrNull(index) else null
            }
        }
        return sensorSystems.getOrNull(sys.getEnemySlot(target))
    }

    /**
     * The sensors systems that look at [count] enemy ships, in order: all of
     * them, if there aren't more than that, or else the best ones (the most
     * power, with manning, and not hacked), so a manned or upgraded sensors
     * system isn't left looking at nothing while a worse one looks at a
     * ship. They stay in their order, so the leftmost ship's is the first.
     */
    private fun sensorsWatching(count: Int): List<Sensors> =
        bestInOrder(sensorSystems, count) { if (it.isHackActive) -1 else it.effectivePower }

    /**
     * How much fuel a jump takes. Bigger ships (see [ShipBlueprint.shipSize])
     * use more, and start with more to make up for it. Some sectors take
     * more for every ship (see [xyz.znix.xftl.sector.SectorType.extraFuel]).
     */
    val fuelPerJump: Int get() = type.shipSize + (sys.currentBeacon?.sector?.type?.extraFuel ?: 0)

    /**
     * The ship's first backup battery. Ships can have several - see [batterySystems].
     */
    var backupBattery: BackupBattery? = null
        private set

    /**
     * All the ship's backup batteries, in the order they're listed in the
     * ship's blueprint. Their power adds together.
     */
    var batterySystems: List<BackupBattery> = emptyList()
        private set

    // Can't just call this 'doors' since that's the list of all
    // the doors in the ship.
    var doorsSystem: Doors? = null
        private set

    var oxygen: Oxygen? = null
        private set

    /**
     * All the ship's oxygen systems. Most ships have one, which is [oxygen].
     */
    var oxygenSystems: List<Oxygen> = emptyList()
        private set

    /**
     * How fast the oxygen systems refill each room, per second (before the
     * rooms' own drain). With several, it's the average of their rates:
     * a bigger ship needs them all to refill as fast as a small one does
     * with one, and losing one slows it down.
     */
    val oxygenRefillRate: Float
        get() = if (oxygenSystems.isEmpty()) 0f else oxygenSystems.sumOf { it.refillRate.toDouble() }.toFloat() / oxygenSystems.size

    // The flagship has multiple artillery systems.
    var artillery: List<Artillery> = emptyList()
        private set

    /**
     * True if any of the ship's engines are powered and not hacked.
     */
    val areEnginesRunning: Boolean
        get() = engineSystems.any { it.powerSelected > 0 && !it.isHackActive }

    /**
     * The engine level used for the ship's evasion.
     *
     * Bigger ships (see [ShipBlueprint.shipSize]) need more engines to dodge
     * as well as small ones: a ship twice the size of the vanilla ships needs
     * two engines systems at a level to get that level's evasion. Every bar
     * of power in any of them counts, so one being off, broken or hacked
     * only loses its own share. For vanilla ships, this is the engine's power.
     */
    private val engineEvasionLevel: Float
        get() = engineSystems.sumOf { if (it.isHackActive) 0 else it.powerSelected }.f / type.shipSize

    /**
     * The engine level used for the FTL charge rate. Unlike evasion, this
     * is the total power of the engines (scaled by the ship's size), so a
     * ship can still jump away if one of its engines goes down.
     */
    private val engineChargeLevel: Float
        get() = engineSystems.sumOf { if (it.isHackActive) 0 else it.powerSelected }.f / type.shipSize

    // The ship's evasion, in percent
    val evasion: Int
        get() {
            // The Mainframe's a station, and never dodges
            if (colossal?.isStation == true)
                return 0

            val engineLevel = engineEvasionLevel
            var engineEvasion = Engines.evasionAt(engineLevel)

            // Each manned engine gives its share of the manning bonus, as long
            // as the engines are working: a ship with two needs both manned
            // for the whole of it
            if (engineLevel > 0f) {
                engineEvasion += engineSystems.sumOf { engines ->
                    if (engines.powerSelected == 0 || engines.isHackActive) 0
                    else engines.getSkillLevel(Skill.ENGINES)?.let { Engines.SKILL_BONUSES[it.level] } ?: 0
                }.f / engineSystems.size
            }

            var evasion: Float = piloting!!.evasion + engineEvasion
            evasion *= piloting!!.evasionMultiplier

            // Bigger ships are easier to hit, which makes up for their thicker
            // hull. This is 2/3 for a ship twice the size of vanilla's.
            evasion *= 2f / (type.shipSize + 1)

            // With no engines running, the ship can't dodge at all, nor while
            // a hacking pulse is on any of its engines or its piloting
            if (!areEnginesRunning || engineSystems.any { it.isHackActive } || piloting!!.isHackActive)
                evasion = 0f

            // Cloaking doesn't hide bigger ships as well
            if (isCloakActive)
                evasion += 60f / type.shipSize

            return evasion.toInt()
        }

    /**
     * From 0-1.
     */
    var averageOxygen: Float = 1f
        private set

    init {
        if (customised != null) {
            val roomIds = customised.rooms.withIndex().associate { Pair(it.value, it.index) }
            rooms = customised.rooms.withIndex().map { (idx, r) -> Room(this, idx, r.x, r.y, r.w, r.h) }

            doors = customised.doors.mapNotNull { door ->
                val firstRoom = door.findNeighbourRoom(customised, null)
                val secondRoom = door.findNeighbourRoom(customised, firstRoom)

                // Skip disconnected doors.
                if (firstRoom == null && secondRoom == null)
                    return@mapNotNull null

                val firstRoomReal = firstRoom?.let { rooms[roomIds.getValue(it)] }
                val secondRoomReal = secondRoom?.let { rooms[roomIds.getValue(it)] }

                Door(ConstPoint(door.x, door.y), firstRoomReal, secondRoomReal, door.isVertical)
            }
        } else {
            rooms = type.rooms.map { Room(this, it.id, it.pos.x, it.pos.y, it.size.x, it.size.y) }
            doors = type.doors.mapNotNull { it ->
                // Some modded ships have doors that don't connect to any rooms.
                // (eg, the circle destroyer from Dino's Hangar)
                // We should probably guess what rooms these connect to and link
                // them up, for now ignore those doors.
                if (it.left == null && it.right == null) {
                    println("[WARN] Ignoring disconnected door at ${it.pos.x},${it.pos.y}.")
                    return@mapNotNull null
                }

                Door(
                    it.pos,
                    it.left?.let { rooms[it.id] },
                    it.right?.let { rooms[it.id] },
                    it.isVertical
                )
            }
        }

        for (room in rooms) {
            val roomDoors =
                doors.stream().filter { d -> d.left == room || d.right == room }.collect(Collectors.toList())
            room.initialise(roomDoors)
        }

        val shieldOffset = type.shieldEllipse.pos.const
        selectedShieldHalfSize = type.shieldEllipse.size.const

        // The player ship uses the exact size of the shield image,
        // while enemies scale it to fit a custom size.
        shieldHalfSize = when {
            isPlayerShip -> ConstPoint(shieldImage.width / 2, shieldImage.height / 2)
            else -> selectedShieldHalfSize
        }

        if (customised != null) {
            var systemIdx = 0
            for ((idx, room) in rooms.withIndex()) {
                val editable = customised.rooms[idx]
                val system = editable.system ?: continue
                val finalised = FinalisedEditableSystem(system, systemIdx++, sys, room)
                room.systemSlots.add(SystemInstallConfiguration(finalised, sys, room))
            }
        } else {
            for (node in type.systems) {
                val room = rooms[node.room.id]

                // Load the information about the available system into the room.
                // This will be used when loading the system from a save, spawning
                // a new ship, or buying a system at a store.
                room.systemSlots.add(SystemInstallConfiguration(node, sys, room))
            }
        }

        systemSlots = rooms.flatMap { it.systemSlots }

        // The shield origin is found by drawing a box that contains all
        // the rooms, then taking its centre.
        val minX = offset.x + rooms.minOf { it.x }
        val minY = offset.y + rooms.minOf { it.y }
        val maxX = offset.x + rooms.maxOf { it.x + it.width }
        val maxY = offset.y + rooms.maxOf { it.y + it.height }
        val centreRoomX = (minX + maxX) / 2f
        val centreRoomY = (minY + maxY) / 2f
        val origin = Point(
            (ROOM_SIZE * centreRoomX).toInt(),
            (ROOM_SIZE * centreRoomY).toInt(),
        )
        origin += shieldOffset
        if (!isPlayerShip) {
            // Yes this looks silly, but it's what vanilla does
            origin.y += 110
        }
        shieldOrigin = origin.const

        hullOffset = ConstPoint(minX, minY) * ROOM_SIZE + type.hullOffset

        hardpoints = type.hardpoints.map { Hardpoint(it) }
        gibs = type.gibs.map { it.createInstance(sys) }

        // Set up the pathfinder after the layout is loaded
        pathFinder = PathFinder(this)
    }

    /**
     * Loads the default version of the ship's stuff (system, blueprints, etc) - this
     * is all stuff that the player can upgrade and needs to be saved.
     */
    fun loadDefaultContents() {
        // Load the starting reactor power
        purchasedReactorPower = type.startingReactorPower

        // Load the starting systems. In debug mode, the player gets every
        // system there's room for, fully upgraded, up to the ship's limits:
        // they're installed in the order the blueprint lists them, so the
        // last ones are left out.
        // (The trailer starts with the ship as a new game has it.)
        val debug = XftlDebug.enabled && isPlayerShip && !XftlDebug.plainPlayerShip
        var mainCount = 0
        var subCount = 0
        for (config in systemSlots.sortedBy { it.spec.systemIndex }) {
            if (!config.spec.availableByDefault && !debug)
                continue

            // Some rooms can hold several systems (eg the medbay and
            // clonebay), so only install the first in debug mode.
            if (config.room.system != null)
                continue

            val isSubSystem = config.system.info?.isSubSystem == true
            if (isSubSystem && subCount >= maxSubSystems || !isSubSystem && mainCount >= maxMainSystems) {
                println("[WARN] Not installing ${config.spec.systemName} in room ${config.room.id} of ship '$name', as it has as many as it can")
                continue
            }
            if (isSubSystem) subCount++ else mainCount++

            config.room.setSystem(config)
        }
        // (With XFTL_DEBUG=2, they keep their starting levels, as does
        // the reactor.)
        if (debug && !XftlDebug.starterLevels) {
            for (system in systems) {
                system.energyLevels = system.blueprint.maxPower
            }
            purchasedReactorPower = XftlDebug.REACTOR_POWER
        }

        // Load the starting resources. Bigger ships use more fuel and cost
        // more to upgrade, so they start with more (see startingFuel and
        // startingScrap).
        if (isPlayerShip) {
            fuelCount = startingFuel(type.shipSize)
            scrap = startingScrap(sys.difficulty, type.shipSize)
        }
        missilesCount = type.initialMissiles
        dronesCount = type.initialDroneParts
        if (debug) {
            fuelCount = XftlDebug.RESOURCE_COUNT
            missilesCount = XftlDebug.RESOURCE_COUNT
            dronesCount = XftlDebug.RESOURCE_COUNT
            scrap = XftlDebug.SCRAP
        }

        // Load all the weapon blueprints
        for ((index, name) in type.initialWeapons.withIndex()) {
            // Slots can be left empty
            if (name == null)
                continue

            if (index >= hardpoints.size) {
                println("[WARN] Weapon '$name' in ship '${this.name}' is in slot ${index + 1}, but there's only ${hardpoints.size}")
                continue
            }

            // If there's a ship with invalid weapons, vanilla loads the weapon
            // with an empty name.
            // This empty-name weapon isn't actually set in the vanilla XML, but
            // that's fine since only mods have ships with invalid weapon names.
            var weapon = sys.blueprintManager.getOrNull(name)
            if (weapon == null) {
                println("[WARN] Invalid weapon '$name' in ship '${this.name}' initial weapons")
                weapon = sys.blueprintManager[""]
            }
            check(weapon is AbstractWeaponBlueprint)

            hardpoints[index].weapon = weapon.buildInstance(this)
        }

        // Load all the drone blueprints
        for ((idx, name) in type.initialDrones.withIndex()) {
            val drone = sys.blueprintManager.getOrNull(name) as DroneBlueprint?

            // If a ship has an invalid drone, just ignore it.
            if (drone == null) {
                println("[WARN] Invalid drone '$name' in ship '${this.name}' initial drones")
                continue
            }

            // Fill up the drones systems in order. If the ship doesn't have
            // enough drone slots, just give the last one some more.
            // This is required for the flagship at least, but it makes sense
            // that if there is some fixed list of included drones then there ought
            // to be enough space for them.
            var slot = idx
            var system = droneSystems.first()
            for (candidate in droneSystems) {
                system = candidate
                if (slot < candidate.drones.size || candidate == droneSystems.last())
                    break
                slot -= candidate.drones.size
            }
            while (system.drones.size <= slot) {
                system.drones.add(null)
            }

            system.drones[slot] = Drones.DroneInfo(drone, null)
        }

        // Load any augments. With XFTL_ZOLTAN, the player gets nothing but
        // Zoltan shields, and with XFTL_RANDOMIZE, random ones.
        val initialAugments = when {
            XftlDebug.zoltan && isPlayerShip -> List(maxAugments) { AugZoltanShield.NAME }
            debug && XftlDebug.randomize -> randomDebugAugments()
            else -> type.initialAugments
        }
        for (name in initialAugments) {
            val augment = sys.blueprintManager.getOrNull(name) as AugmentBlueprint?

            // Also ignore invalid blueprints.
            if (augment == null) {
                println("[WARN] Invalid augment '$name' in ship '${this.name}' initial augments")
                continue
            }

            augments.add(augment)
        }

        // Do this after adding all the augments, so stacking ones (like
        // multiple Zoltan shields) can see each other.
        for (augment in augments) {
            augment.onShipSpawn(this)
        }
        lastZoltanShieldCount = zoltanShieldCount

        // Load anything that starts in the cargo hold
        for ((index, name) in type.initialCargo.withIndex()) {
            val item = sys.blueprintManager.getOrNull(name) as Blueprint?
            if (item == null) {
                println("[WARN] Invalid cargo '$name' in ship '${this.name}' initial cargo")
                continue
            }
            cargoBlueprints[index] = item
        }

        // In debug mode, the drones systems and the cargo hold are full of
        // twin and triple drones (and with XFTL_RANDOMIZE, the weapons are
        // random too)
        if (debug && XftlDebug.randomize) {
            randomizeDebugWeapons()
        } else if (debug) {
            fillDebugDrones()
        }

        // If we don't have an oxygen system, the rooms start with no oxygen by default
        if (oxygen == null) {
            for (room in rooms) {
                room.oxygen = 0f
            }
        }

        // Calculate the number of each type of augment
        cargoUpdated()
    }

    fun render(g: Graphics, interiorVisible: Boolean, selected: Room?) {
        // Draw the shield
        renderShields()

        // Draw departing shots. We'll draw the rest of them later.
        for (proj in projectiles) {
            if (proj.drawUnderShip) {
                proj.render(g, this)
            }
        }

        for (system in systems)
            system.drawBackground(g)

        // If the ship is exploding, draw no further
        if (isDead) {
            if (isGone) {
                //health++ // testing
                //gibs.forEach { it.reset() }
                return
            }
            for (gib in gibs.asReversed()) {
                gib.draw(g, hullOffset)
            }
        } else {
            // Animate between cloaked and uncloaked
            val cloakFade = cloaking?.cloakFade ?: 0f

            val hullAlpha = when (cloakFade) {
                0f -> 1f
                else -> interiorAlpha * 0.75f
            }
            hullImage.draw(hullOffset.x, hullOffset.y, Colour(1f, 1f, 1f, hullAlpha))

            if (cloakFade != 0f) {
                requireNotNull(cloakImage) { "Ship '$name' cloaked, but it doesn't have a cloak image!" }

                cloakImage.draw(
                    hullOffset.x + cloakOffset.x, hullOffset.y + cloakOffset.y,
                    Colour(1f, 1f, 1f, cloakFade)
                )
            }

            if (interiorVisible) {
                drawInterior(g, selected, interiorAlpha)
            }
        }

        // Draw the debug hardpoint visuals
        if (sys.debugFlags.showHardpoints.set) {
            for ((index, hp) in hardpoints.withIndex()) {
                val pos = hp.spec.position
                g.colour = Colour.red
                g.drawLine(pos.x - 5f, pos.y - 5f, pos.x + 5f, pos.y + 5f)
                g.drawLine(pos.x + 5f, pos.y - 5f, pos.x - 5f, pos.y + 5f)

                sys.getFont("JustinFont8").drawString(
                    pos.x.f + 5f,
                    pos.y + 8f,
                    index.toString(), Colour.red
                )
            }
        }

        // Draw the projectiles, except for those we already
        // drew underneath the ship earlier.
        for (proj in projectiles) {
            if (!proj.drawUnderShip) {
                proj.render(g, this)
            }
        }

        for (beam in inboundBeams) {
            beam.renderInbound(g)
        }

        // Draw any drones flying around the ship
        for (drone in externalDrones) {
            drone.renderExternal(g)
        }

        // Draw the floating animations (eg, from projectile explosions)
        for (a in animations)
            a.render(g)

        animations.removeIf { a -> a.isFinished }

        // Draw the damage numbers that appear when your ship is hit
        for (num in damageNumbers) {
            num.draw()
        }
    }

    /**
     * How much longer the shield bubble flashes for, after something hit it
     * or passed through it (see [flashShields]), in seconds.
     */
    private var shieldFlash: Float = 0f

    /**
     * Make the shield bubble flash, as something hits it, or (like a
     * missile) passes through it, so it's clear the shot got past.
     */
    fun flashShields() {
        shieldFlash = SHIELD_FLASH_TIME
    }

    private fun renderShields() {
        val level = activeShieldLayers

        // A flash, fading out, after a hit
        if (shieldFlash > 0f && (level > 0 || superShield > 0))
            renderSingleShield(SHIELD_FLASH_ALPHA * shieldFlash / SHIELD_FLASH_TIME, Colour.white)

        // Draw the standard (non-Zoltan) shield
        val shieldMax = if (isPlayerShip) 3.5f else 4f // This is odd, but correct
        val levelFraction = level / shieldMax
        val alpha = SHIELD_OPACITY_BASE + SHIELD_OPACITY_SCALING * levelFraction

        if (level == 0) {
            // Do nothing, shields disabled
        } else {
            renderSingleShield(alpha, Colour.white)
        }

        // Draw the super shields
        if (superShield != 0) {
            var superShieldAlpha = superShield.f / maxSuperShield

            if (level == 0) {
                superShieldAlpha = SHIELD_OPACITY_BASE + SHIELD_OPACITY_SCALING * superShieldAlpha
            }

            renderSingleShield(superShieldAlpha, SYS_ENERGY_ACTIVE)

            // TODO fix the ugly darkened line around the edge of enemy super-shields
            // I suspect this is caused by the scaling - Slick has had problems
            // rendering drones while rotating with white aliasing artefacts
            // along their edges, so maybe it's the same thing?
            // Or it could possible be some kind of SRGB-related issue.
        }
    }

    private fun renderSingleShield(alpha: Float, filter: Colour) {
        val basePosX = shieldOrigin.x - shieldHalfSize.x
        val basePosY = shieldOrigin.y - shieldHalfSize.y

        // Draw the image scaled to fit the
        shieldImage.draw(
            basePosX.f, basePosY.f,
            basePosX.f + shieldHalfSize.x * 2, basePosY.f + shieldHalfSize.y * 2,
            0f, 0f,
            shieldImage.width.f, shieldImage.height.f,
            alpha, filter
        )
    }

    /**
     * Render the crosshairs, beam marker, etc that appear when a weapon
     * is targeted to a specific room. This is in Ship rather than the
     * hostile ship UI, as the player can teleport bombs to their own ship
     * and for debug purposes we may want to show where the AI is targeting
     * their shots.
     */
    fun renderTargeting(g: Graphics, targets: Weapons.TargetList) {
        // A combined weapon's shadow targets on this ship (see
        // ShadowTargets): its crosshairs, faintly, unless it's cloaked, when
        // those copies fire at the main target
        for (target in targets) {
            if (isCloakActive)
                break
            if (target !is SelectedTarget.RoomAim)
                continue
            for (shadow in target.shadowRooms) {
                if (shadow.ship != this)
                    continue
                val number = target.weaponNumber + 1
                val suffix = if (target.autofire) "_yellow" else ""
                val img = when {
                    number <= 4 -> sys.getImg("img/misc/crosshairs_placed$number$suffix.png")
                    else -> sys.getImg("img/misc/crosshairs_placed$suffix.png")
                }
                val centre = shadow.pixelCentre
                img.draw(centre.x - img.width / 2, centre.y - img.height / 2, SHADOW_CROSSHAIR_ALPHA)
            }
        }

        for (target in targets) {
            if (target.targetShip != this)
                continue

            // (a combined beam's long line is drawn over the window, see
            // renderLongBeamTargeting)
            if (target is SelectedTarget.BeamAim && !target.weapon.type.isCombined)
                renderTargetingBeam(g, target)
            if (target !is SelectedTarget.RoomAim)
                continue

            val room = target.room

            val number = target.weaponNumber + 1

            // Weapons on autofire have yellow crosshairs
            val suffix = if (target.autofire) "_yellow" else ""

            // There's only numbered crosshairs images for the first four
            // weapons, so for the others (on ships with more weapon slots)
            // draw the number over the unnumbered image.
            if (number > 4 && CrosshairNumbers.hasNumber(number)) {
                val img = sys.getImg("img/misc/crosshairs_placed$suffix.png")
                val centre = room.pixelCentre
                img.draw(centre.x - img.width / 2, centre.y - img.height / 2)

                val frameSize = CrosshairNumbers.FRAME_SIZE
                CrosshairNumbers.drawNumber(g, centre.x - frameSize / 2, centre.y - frameSize / 2, number, target.autofire)
                continue
            }

            val img = when {
                number <= 4 -> sys.getImg("img/misc/crosshairs_placed$number$suffix.png")
                else -> sys.getImg("img/misc/crosshairs_placed$suffix.png")
            }

            val pos = Point(room.pixelCentre)

            pos.x -= img.width / 2
            pos.y -= img.height / 2

            img.draw(pos)
        }

        val aiming = targets.beamAiming
        if (aiming?.targetShip == this && !aiming.weapon.type.isCombined)
            renderTargetingBeam(g, aiming)
    }

    /**
     * Set which beam the player's aiming at this ship, if any, from the
     * one being aimed ([aiming], of whichever of the player's weapons
     * systems it belongs to), for the rooms it would hit to be highlighted
     * (see drawInterior). Called before the ship's drawn.
     */
    fun updateInboundBeamAim(aiming: SelectedTarget.BeamAim?) {
        inboundBeamAim = when {
            aiming == null -> null
            aiming.targetShip == this -> aiming

            // A combined beam's long line, aimed in another window, may go
            // on through this one: show the rooms it would hit here too
            aiming.visible && aiming.weapon.type.isCombined && sys.isEnemyOf(sys.player, this) ->
                ShadowTargets.beamAimIn(sys, aiming, this)

            else -> null
        }
    }

    /**
     * Draw the player's combined beams aimed at this ship (see
     * [WeaponSizes]), being aimed or placed: their long lines go on through
     * the other enemy windows, so this is drawn over the window, rather than
     * inside it with [renderTargeting], where it'd be cut off at its edge.
     */
    fun renderLongBeamTargeting(g: Graphics, targets: Weapons.TargetList) {
        for (target in targets) {
            if (target is SelectedTarget.BeamAim && target.targetShip == this && target.weapon.type.isCombined)
                renderTargetingBeam(g, target)
        }
        val aiming = targets.beamAiming
        if (aiming != null && aiming.targetShip == this && aiming.weapon.type.isCombined)
            renderTargetingBeam(g, aiming)
    }

    /**
     * Draw [source]'s teleporters' and mind control systems' icons on the
     * rooms of this ship they're waiting to reach, such as while this ship
     * is cloaked, as the weapons' targets are.
     */
    fun renderPendingTargets(source: Ship) {
        // Just the systems' symbols (vanilla's white ones, without the round
        // frame), red with a thin black outline, so they don't look like
        // any of the systems' own states (blue is ionised, purple hacked)
        val teleportIcon = sys.getImg("img/icons/s_teleporter.png")
        val mindIcon = sys.getImg("img/icons/s_mind.png")
        val hackIcon = sys.getImg("img/icons/s_hacking.png")
        val rooms = source.systems.filterIsInstance<Teleporter>().mapNotNull { it.pendingRoom }
            .map { it to teleportIcon } +
                source.mindControlSystems.mapNotNull { it.pendingRoom }.map { it to mindIcon } +
                source.hackingSystems.mapNotNull { it.pendingRoom }.map { it to hackIcon }
        for ((room, icon) in rooms) {
            if (room.ship != this)
                continue
            val centre = room.pixelCentre
            val x = centre.x - icon.width / 2f
            val y = centre.y - icon.height / 2f
            for ((dx, dy) in PENDING_OUTLINE_OFFSETS)
                icon.draw(x + dx, y + dy, Colour.black)
            icon.draw(x, y, PENDING_TARGET_COLOUR)
        }
    }

    fun renderTargetingBeam(g: Graphics, beam: SelectedTarget.BeamAim) {
        // This is false for beams the player is currently aiming, where their
        // mouse is too close to the start of the beam.
        if (!beam.visible)
            return

        // FTL has a nice 'cap' image that squares off the ends of a beam. However
        // this seemed to cause flickering at the transition between the cap and centre
        // image when rotated, so don't bother with it. Notably the game itself
        // doesn't seem to use this asset.
        // TODO use the cap without a flicker, it really does look nicer.
        // Yellow for a beam on autofire
        val img = sys.getImg(if (beam.autofire) "img/misc/beam_center_yellow.png" else "img/misc/beam_center.png")

        g.pushTransform()
        g.translate(beam.startShipPoint.x.f, beam.startShipPoint.y.f)
        g.rotate(0f, 0f, Math.toDegrees(beam.angle.toDouble()).toFloat())

        // Draw the stretched-out middle image, as long as the beam (a
        // combined beam's is longer, see WeaponSizes)
        val length = beam.length
        img.draw(
            0f, -img.height / 2f, length.f, img.height.f / 2f,
            // Stretch out the centre of the beam - if we don't do this, then
            // the ends appear faded (windows and nvidia, if that's important).
            0.5f, 0f, 0.5f, img.height.f
        )

        g.popTransform()
    }

    // The player's Lifeform Scanner (or Slugs) show where everyone is, even
    // without sensors (though not what's going on in the rooms)
    private val lifeformScanned: Boolean
        get() = sys.player?.sensesLifeforms == true

    /**
     * True if this ship can sense the crew in every room, as with the Lifeform
     * Scanner: it has one, or a Slug of its own crew (on any ship of the
     * fight, and not mind controlled), as Slugs are telepathic, as in vanilla.
     */
    val sensesLifeforms: Boolean
        get() {
            if (hasAugment(AugmentBlueprint.LIFEFORM_SCANNER))
                return true
            return (listOf(this) + sys.getOpponentShips(this)).any { other ->
                other.crew.any {
                    it is CrewSlug && it.ownerShip == this && it.mindControlledBy == null &&
                            it.currentAction != AbstractCrew.Action.DYING
                }
            }
        }

    /**
     * Whether this ship can see the crew in [room], to mind control them:
     * its own rooms always; any with the Lifeform Scanner or a Slug (see
     * [sensesLifeforms]); otherwise, for the player, the rooms they can see
     * (with their sensors and so on, see Room.playerHasVision), and for an
     * enemy, the player's ship with its sensors at level 2 (as the player's
     * need to be), and the rooms its own boarders are in.
     */
    fun canSeeInto(room: Room): Boolean {
        if (room.ship == this || sensesLifeforms)
            return true
        if (isPlayerShip)
            return room.playerHasVision
        if (sensorSystems.any { it.providesEnemyVision })
            return true
        return room.crew.any { it is LivingCrew && it.ownerShip == this }
    }

    private fun drawInterior(g: Graphics, selected: Room?, alpha: Float) {
        floorImage?.draw(
            floorOffset.x + hullOffset.x,
            floorOffset.y + hullOffset.y,
            Colour(1f, 1f, 1f, alpha)
        )

        // Draw the rooms
        for (room in rooms) {
            var roomSelected = selected == room

            // If the user is currently aiming a beam, highlight
            // the rooms it would hit.
            if (inboundBeamAim?.hitRooms?.contains(room) == true) {
                roomSelected = true
            }

            room.render(g, roomSelected, alpha)
        }

        // Draw the doors
        for (door in doors) {
            door.render(g, alpha)
        }

        // A locked-down room's crystal covers its walls and doors
        for (room in rooms) {
            room.renderLockdown(g, alpha)
        }

        // Draw the crew
        for (crew in crew) {
            if (isCrewVisible(crew)) {
                crew.draw(g)
            }
        }

        // Draw the health bars on top of all the other crew, so two
        // fighting crewmembers can't block one of their health bars.
        for (crew in crew) {
            if (isCrewVisible(crew)) {
                crew.drawForeground(g)
            }
        }

        // Draw the system foregrounds
        for (system in systems)
            system.drawForeground(g)
    }

    /**
     * Whether the player can see [crew]: in a room they can see into, or
     * one that gives vision itself (which works even if playerHasVision
     * isn't set, so crew walking between rooms without sensors don't
     * flicker). The Lifeform Scanner (or a Slug's telepathy) senses the
     * living crew everywhere, but not drones, which aren't alive.
     */
    private fun isCrewVisible(crew: AbstractCrew): Boolean =
        crew.room.playerHasVision || crew.providesPlayerVision || (lifeformScanned && crew is LivingCrew)

    fun screenPosToShipPos(point: Point) {
        point.x = Math.floorDiv(point.x, ROOM_SIZE)
        point.y = Math.floorDiv(point.y, ROOM_SIZE)
        point.sub(offset.x, offset.y)
    }

    fun shipToRoomPos(pos: IPoint): RoomPoint? {
        if (pos is RoomPoint)
            return pos

        for (r in rooms) {
            if (r.containsAbsolute(pos))
                return RoomPoint(r, pos - r.position)
        }

        return null
    }

    fun update(dt: Float) {
        // Update the power, in case a Zoltan has moved between rooms.
        updateAvailablePower()

        shieldFlash = (shieldFlash - dt).coerceAtLeast(0f)

        // A shield overcharger's row goes when the drone is turned off or lost
        if (superShield > maxSuperShield) {
            superShield = maxSuperShield
        }

        updateExterior(dt)

        // A beam stops itself when it's done, as its ship updates it, so one
        // from a ship that's been destroyed or left (mid-shot, or as either
        // ship jumped away) would otherwise stay here for good.
        inboundBeams.removeIf { it.ship.isDead || !sys.isShipPresent(it.ship) }

        if (isDead) {
            for (gib in gibs)
                gib.update(dt)
            return
        }

        for (room in rooms)
            room.update(dt)

        // Update the door open/close animations
        for (door in doors) {
            door.update(dt)
        }

        // Move oxygen around the ship
        OxygenTransfer.update(this, dt)

        averageOxygen = rooms.map { it.oxygen }.average().toFloat()

        if (!sys.debugFlags.noCrewAI.set) {
            crewAI.update(dt)
            intruderAI.update()
        }

        updateCrew(dt)

        // This FTL charge logic is for the player; enemies have a completely
        // separate charge time stored in ShipAI.
        if (canChargeFTL) {
            var rate = Engines.chargeRateAt(engineChargeLevel)

            // When you're in a dangerous environment without an enemy,
            // the charge speed is boosted.
            if (sys.getEnemyOf(this) == null) {
                rate *= 3
            }

            // Thick nebulas slow it down
            rate *= sys.currentBeacon.getEnvironment(sys).ftlChargeMultiplier

            // The FTL Recharge Booster speeds it up (25% each, as they stack)
            rate *= 1f + getAugmentValue(AugmentBlueprint.FTL_RECHARGE_BOOSTER)

            ftlChargeProgress += rate * dt / 68f
        }
        if (ftlChargeProgress > 1f) {
            ftlChargeProgress = 1f
        }

        for ((augment, totalValue) in augmentValues) {
            augment.update(this, dt, totalValue)
        }
    }

    private fun updateCrew(dt: Float) {
        // Duplicate the crew and drone lists, in case some are removed
        // by dying or (in the case of drones) the enemy ship blowing up.
        for (crew in crew.toTypedArray())
            crew.update(dt)

        // Update the list of intruders and friendly (non-intruder) crewmembers
        require(intruders is ArrayList)
        require(friendlyCrew is ArrayList)
        intruders.clear()
        friendlyCrew.clear()
        for (crew in crew) {
            when (crew.mode) {
                AbstractCrew.SlotType.CREW -> friendlyCrew.add(crew)
                AbstractCrew.SlotType.INTRUDER -> intruders.add(crew)
            }
        }
    }

    private fun updateExterior(dt: Float) {
        // Walk backwards, since projectiles may remove themselves
        for (i in projectiles.size - 1 downTo 0) {
            projectiles[i].update(dt, this)
        }

        // Check for collisions between any two projectiles. To avoid
        // checking a pair twice (but in a swapped order), only check
        // a projectile against those that come after it in the list.
        // By iterating down, we can again ignore the problem of changing
        // indices when projectiles are removed.
        for (lowerI in projectiles.size - 1 downTo 0) {
            val first = projectiles[lowerI]
            if (!first.collisionsEnabled)
                continue

            for (upperI in projectiles.size - 1 downTo lowerI + 1) {
                val second = projectiles[upperI]

                if (!second.collisionsEnabled)
                    continue

                // Make sure the projectiles are targeting different ships - otherwise
                // you could shoot down your own projectiles, and in particular flak
                // projectiles would collide with each other.
                if (first !is AbstractProjectile || second !is AbstractProjectile) {
                    continue
                }

                // A projectile that's not fired at a ship is assumed to be friendly,
                // like a defence drone laser.
                val isFirstFriendly = first.targetShip != this
                val isSecondFriendly = second.targetShip != this
                if (isFirstFriendly == isSecondFriendly) {
                    continue
                }

                // Run a CCD check to see if the projectiles have collided
                // at any point between the last frame and this frame.
                if (!IProjectile.checkCollision(
                        dt,
                        first.position, second.position,
                        first.velocity, second.velocity,
                        first.hitboxRadius.f, second.hitboxRadius.f
                    )
                ) {
                    continue
                }

                first.hitOtherProjectile(this)
                second.hitOtherProjectile(this)

                // Play a single hit sound. It seems that FTL doesn't
                // play this missile explode sound when a drone shoots
                // down a missile, so always using one is probably
                // close to correct.
                // It also means we don't play two sounds for the same
                // thing, which would be the case if hitOtherProjectile
                // played a sound effect.
                projectileCollisionSound.play()

                projectiles.removeAt(upperI)
                projectiles.removeAt(lowerI)

                // Don't process any more projectiles in relation to the
                // one from the outer loop, as we removed it.
                break
            }
        }

        // Check for collisions between projectiles and drones
        for (droneI in externalDrones.size - 1 downTo 0) {
            val drone = externalDrones[droneI]

            for (projectileI in projectiles.size - 1 downTo 0) {
                val proj = projectiles[projectileI]

                if (!proj.collisionsEnabled)
                    continue

                if (!IProjectile.checkCollision(
                        dt,
                        drone.flightController.position, proj.position,
                        drone.flightController.velocity, proj.velocity,
                        drone.hitboxRadius.f, proj.hitboxRadius.f
                    )
                ) {
                    continue
                }

                val distSq = drone.flightController.position.distToSq(proj.position)
                val maxDist = drone.hitboxRadius + proj.hitboxRadius

                // No overlap?
                if (distSq > maxDist * maxDist)
                    continue

                if (!drone.canCollideWith(proj))
                    continue

                proj.hitOtherProjectile(this)
                projectiles.removeAt(projectileI)

                // The drone is expected to play a sound.
                drone.hitProjectile(proj)

                break
            }
        }


        // Update the animations
        for (a in animations) {
            a.update(dt)
        }

        // update returns false if the number should be removed
        damageNumbers.removeIf { !it.update(dt) }
    }

    fun updateAvailablePower() {
        // Don't update our power during saving/loading, since different types
        // of power that systems can use (notably batteries, but other mods
        // might add similar power types) need all the systems to be loaded
        // first, and this would thus cause a system to pull different types
        // of power as a replacement during deserialisation.
        if (sys.isCurrentlyLoadingSave)
            return

        powerAvailableTypes.clear()

        for (type in EnergySource.GLOBAL_TYPES) {
            type.adjustShipPower(this, powerAvailableTypes)
        }

        val preEnvPower = powerAvailableTypes.values.sum()

        // Let the environment (for ion storms) deduct power
        sys.currentBeacon.getEnvironment(sys).adjustShipPower(this, powerAvailableTypes)

        // Remember how much power is unavailable, as it's shown in the UI.
        val postEnvPower = powerAvailableTypes.values.sum()
        blockedReactorPower = max(0, preEnvPower - postEnvPower)

        // First make the systems use the power they're already using
        for (system in systems) {
            if (system !is MainSystem)
                continue

            system.consumePowerFirst(powerAvailableTypes)
        }

        // Then grab any extra for increasing power
        for (system in systems) {
            if (system !is MainSystem)
                continue

            system.consumePowerSecond(powerAvailableTypes)
        }
    }

    /**
     * Attack the shields, either damaging the super-shield or popping
     * a regular shield bubble.
     */
    fun attackShields(damage: Damage, damagePos: IPoint?) {
        // Break super shields first
        // The reason this function is in Ship and not Shields is to support
        // ships with a super shield, but no shield system.
        if (superShield != 0) {
            // Clamp damages to at least zero, to avoid negative values
            // repairing the super shield.
            val ionDamage = if (resistsIon()) 0 else max(damage.ionDamage, 0)
            val damageValue = ionDamage * 2 + max(damage.hullDamage, 0)
            superShield -= damageValue

            if (damagePos != null && damageValue > 0) {
                showDamageTextAt(damagePos, damageValue, DAMAGE_COLOUR_ZOLTAN)
            }

            return
        }

        // Pick the shields system to hit before popping a layer, so the ion
        // damage goes to the same one.
        val target = shieldsToHit

        // Only projectiles with hull damage pop shields!
        if (damage.hullDamage > 0) {
            target?.popShieldLayer()
        }

        attackShieldsIon(damage, target)
    }

    /**
     * Apply the ion and system damage components of a [Damage] object to the shields.
     *
     * This is the standard behaviour that should be used when attacking shields with
     * a weapon, if it can do ion damage to the ship's shield.
     */
    @JvmOverloads
    fun attackShieldsIon(damage: Damage, target: Shields? = shieldsToHit) {
        if (damage.ionDamage <= 0 || target == null)
            return

        damage(target.room!!, damage.copyIonAndSys())
    }

    /**
     * Roll the Reverse Ion Field: true if this hit's ion damage does nothing,
     * to the systems or the super shield.
     */
    fun resistsIon(): Boolean {
        val ionField = getAugmentValue(AugmentBlueprint.REVERSE_ION_FIELD)
        return ionField > 0f && Random.nextFloat() < ionField
    }

    /**
     * True for an enemy ship that's no longer fighting the player (it
     * surrendered, say, or its crew are dead): as in vanilla, the shots
     * already on their way still hit it, but harmlessly (see [damage]).
     */
    val isAtPeace: Boolean
        get() = this != sys.player && sys.isShipPresent(this) && !sys.isEnemyOf(sys.player, this)

    fun damage(target: Room, damage: Damage, textPos: IPoint? = null) {
        if (isAtPeace)
            return

        var hullMult = 1
        if (target.system == null) {
            // TODO does this properly apply hullBust values other than 1 (vanilla doesn't do that)
            hullMult += damage.emptyRoomBonus
        }

        var hullDamage = damage.hullDamage * hullMult

        // Rock Plating gives a chance of the hull taking none of the damage
        // (the system still does)
        val plating = getAugmentValue(AugmentBlueprint.ROCK_PLATING)
        if (hullDamage > 0 && plating > 0f && Random.nextFloat() < plating)
            hullDamage = 0

        // Reverse Ion Field gives a chance of the ion damage doing nothing
        // (the shields' too, as that comes here, see attackShieldsIon)
        val ionDamage = if (damage.ionDamage > 0 && resistsIon()) 0 else damage.ionDamage

        showDamageText(target, hullDamage, damage.effectiveSysDamage, ionDamage, textPos)
        crewWeaponDamage(target, damage.effectiveCrewDamage.f, damage)

        // The Lockdown Bomb seals the room for as long as a Crystal's lockdown
        if (damage.lockdown)
            target.lockDown(CrewCrystal.LOCKDOWN_TIME)

        if (sys.debugFlags.noDmg.set)
            return

        health -= hullDamage

        // Crystal Vengeance gives a chance of a shard breaking off at the
        // enemy each time the ship's damaged
        val vengeance = getAugmentValue(AugmentBlueprint.CRYSTAL_VENGEANCE)
        if (hullDamage > 0 && vengeance > 0f && Random.nextFloat() < vengeance)
            launchCrystalShard()

        // Titanium System Casing gives a chance of the system taking none
        // of the damage (the hull still does)
        val casing = getAugmentValue(AugmentBlueprint.TITANIUM_SYSTEM_CASING)
        val sysDamage = if (casing > 0f && Random.nextFloat() < casing) 0 else damage.effectiveSysDamage
        target.system?.dealDamage(sysDamage, ionDamage)

        // Fire and breach are mutually exclusive, if a fire spawns then a breach cannot.
        if (Random.rollChance(damage.fireChance)) {
            // Spawns two fires (or possibly only one, if they both roll on the same cell).
            target.spawnFire()
            target.spawnFire()
        } else if (Random.rollChance(damage.breachChance)) {
            // Spawns two fires (or possibly only one, if they both roll on the same cell).
            target.spawnBreach()
        }
    }

    /**
     * True if an asteroid hitting this ship should repair a point of its
     * hull rather than damage it: the Devourer eats them, and the Devourer
     * Hive-Core gives the player's ship a chance of it.
     */
    fun eatsAsteroid(): Boolean {
        // Not once it's been destroyed: it would come back to life
        if (isDead)
            return false
        if (colossal?.eatsAsteroids == true)
            return true
        val core = getAugmentValue(AugmentBlueprint.HIVE_CORE)
        return core > 0f && Random.nextFloat() < core
    }

    /**
     * How much faster than usual [weapon] charges, on this ship: the
     * Stormbreaker's ion weapons in an ion storm, and the Rebels' automated
     * ships in act 2, against a player with the Vigilance Command Codes.
     */
    fun weaponChargeFactor(weapon: AbstractWeaponInstance): Float {
        var factor = colossal?.chargeFactor(weapon, sys.currentBeacon) ?: 1f
        val player = sys.player
        if (player != null && this != player && isAutoScout && sys.isActTwo) {
            val codes = player.getAugmentValue(AugmentBlueprint.COMMAND_CODES)
            if (codes > 0f)
                factor *= 1f - codes
        }
        return factor
    }

    fun playDamageEffect(type: AbstractWeaponBlueprint, position: IPoint): FloatingAnimation {
        val animation = sys.animations[type.explosion ?: error("Default explosion not set")]
        return playCentredAnimation(animation, position)
    }

    /**
     * Fire a crystal shard (vanilla's CRYSTAL_1, a weapon only used for
     * this) from the middle of the ship at a random room of the ship it's
     * fighting, for Crystal Vengeance.
     */
    private fun launchCrystalShard() {
        val enemy = sys.getEnemyOf(this) ?: return
        val shard = sys.blueprintManager.getOrNull("CRYSTAL_1") as? MissileBlueprint ?: return
        val projectile = shard.MissileProjectile(enemy.rooms.random())
        projectile.entryAngle = Random.nextFloat() * 2f * Math.PI.toFloat()
        val start = ConstPoint(hullOffset.x + hullImage.width / 2, hullOffset.y + hullImage.height / 2)
        projectile.setInitialPath(start, start + weaponFireDirection * 5000)
        projectiles += projectile
        shard.launchSounds?.get()?.play()
    }

    private fun showDamageText(target: Room, damage: Int, systemDamage: Int, ionDamage: Int, pos: IPoint?) {
        // Don't show the popup for system damage in a system-less room.
        if (target.system == null && damage == 0)
            return

        // Damage numbers split based on their type, also huge thanks to Gabriel Cooper
        // for helpfully providing data on the order of damage numbers!
        if (ionDamage > 0 && target.system != null) {
            target.showDamageText(ionDamage, DAMAGE_COLOUR_ION, pos)
        }
        val extraSystemDamage = systemDamage - damage.coerceAtLeast(0)
        if (extraSystemDamage > 0 && target.system != null) {
            // because the specific sys damage shown is that that exceeds the hull damage
            target.showDamageText(extraSystemDamage, DAMAGE_COLOUR_SYSTEM, pos)
        }
        if (damage > 0) {
            target.showDamageText(damage, Colour.white, pos)
        }
    }

    /**
     * Show the damage text, as it relates to a projectile or impact at a given position.
     *
     * This doesn't draw the text exactly at said position - it's shown a bit above.
     */
    fun showDamageTextAt(damagePos: IPoint, imageName: String, colour: Colour) {
        val image = sys.getImg("img/numbers/$imageName.png")
        val floatingNum = DamageNumber(damagePos.x.f, damagePos.y.f, image, colour)
        floatingNum.y -= 30f
        floatingNum.x += VisualRandom.nextInt(-10, 10).f
        damageNumbers.add(floatingNum)
    }

    fun showDamageTextAt(damagePos: IPoint, number: Int, colour: Colour) {
        val clamped = number.coerceIn(0..9)
        val imageName = "Text_${clamped}_L"
        showDamageTextAt(damagePos, imageName, colour)
    }

    fun crewWeaponDamage(target: Room, damage: Float, dmg: Damage) {
        // A stun (the Stun Bomb's, or a weapon's stun chance coming up) hits
        // everyone in the room, whoever's side they're on
        val stun = when {
            dmg.stunDuration > 0f -> dmg.stunDuration
            Random.rollChance(dmg.stunChance) -> Damage.CHANCE_STUN_TIME
            else -> 0f
        }

        for (crew in target.crew.toList()) {
            crew.dealDamage(ShipDamage(damage, dmg))
            if (stun > 0f && crew.currentAction != AbstractCrew.Action.DYING && !crew.isTeleporting)
                crew.stunTime = max(crew.stunTime, stun)
        }
    }

    fun resetAfterJump() {
        // Recharge the shields
        for (system in shieldSystems) {
            system.activeShields = system.selectedShieldBars
        }

        // And reset the FTL drive
        ftlChargeProgress = 0f

        // Jumping clears the super shield (if the augment is
        // present, it'll add it back in afterwards).
        superShield = 0

        // Remove all incoming and outgoing projectiles
        projectiles.clear()

        // Get rid of any drones orbiting us
        externalDrones.clear()

        // Reset the weapon charge times
        for (hp in hardpoints) {
            hp.weapon?.timeCharged = 0f

            // Reset the chain/charge count
            hp.weapon?.chainCount = 0
            hp.weapon?.extraCharges = 0
        }

        // Clear any previously-set scripted power limits, and re-apply
        // any that were previously set at this beacon.
        updateScriptedPowerLimits()

        for (augment in augmentValues.keys) {
            augment.onJump(this)
        }

        for (system in systems) {
            system.onJump()
        }

        // The Crystals' lockdowns are ready again
        for (crystal in crew.filterIsInstance<CrewCrystal>())
            crystal.onJump()

        // What didn't fit is left behind
        overflowItems.clear()
        overflowAugments.clear()

        // Jumping resets the door health. I haven't checked that FTL does
        // this, but the wiki says it does and it's also convenient as it
        // reduces the size of the savefile.
        for (door in doors) {
            door.resetHealth()
        }
    }

    /**
     * This updates the power limits imposed on systems by scripted events.
     *
     * This must only be called on the player ship - on the enemy ship, systems
     * should be limited directly. This is because the player can jump between
     * beacons, and the effects need to be cleared and re-applied as appropriate.
     */
    fun updateScriptedPowerLimits() {
        for (system in systems) {
            val limit = sys.currentBeacon.powerLimitEffects[system.codename]
            system.scriptedPowerLimit = limit
        }
    }

    /**
     * Add a new crewmember to the ship.
     *
     * If [startingRoom] is set, they're put in that room (or as close to it
     * as possible, if it's full), manning its system if it has one.
     */
    @JvmOverloads
    fun addCrewMember(
        info: LivingCrewInfo,
        initial: Boolean,
        isIntruder: Boolean = false,
        startingRoom: Room? = null
    ): LivingCrew {
        var freeSpace: RoomPoint? = null
        val mode = if (isIntruder) AbstractCrew.SlotType.INTRUDER else AbstractCrew.SlotType.CREW

        if (startingRoom != null) {
            // Man the room's system if possible, otherwise stand anywhere nearby
            val computer = startingRoom.system?.configuration?.computerPoint
            freeSpace = when {
                computer != null && startingRoom.isSlotFree(computer, mode) -> RoomPoint(startingRoom, computer)
                else -> findSpaceForCrew(startingRoom, mode)
            }
        }

        // If this crewmember is being created with the ship, put them
        // in a system that makes sense.
        if (freeSpace == null && initial && !isIntruder) {
            val systems = listOf(piloting, engines, weapons, shields)
            for (system in systems) {
                if (system == null)
                    continue

                val room = system.room!!

                // If someone's already at the computer, don't put another person there.
                val firstSlot = system.configuration.computerPoint ?: ConstPoint.ZERO
                if (!room.isSlotFree(firstSlot, mode))
                    continue

                // Stand by the computer, if possible
                freeSpace = RoomPoint(room, firstSlot)
                break
            }
        }

        // Otherwise just put them wherever they'll fit.
        if (freeSpace == null) {
            freeSpace = findSpaceForCrew(null, mode)
        }

        val crewMember = info.race.spawn(freeSpace.room, mode)
        crewMember.info = info
        crew.add(crewMember)
        crewMember.jumpTo(freeSpace)

        // If this is an intruder, make sure they don't count as being
        // 'owned' by the player. This means they can't use the player's
        // augments, for example.
        if (isIntruder) {
            crewMember.ownerShip = null
        }

        sys.updatePlayerCrew()

        return crewMember
    }

    /**
     * Find a slot somewhere in the ship (accessible from [startingRoom],
     * if this ship has some disconnected rooms) that's empty.
     *
     * This works with the pathfinding slots - the points that a crewmember
     * can be sent to by right-clicking. Thus a room that has no crew in
     * it would be considered full if crew are walking towards every spot.
     *
     * Similarly, a room full of crew would be considered empty if they're
     * all walking elsewhere.
     */
    fun findSpaceForCrew(startingRoom: Room?, type: AbstractCrew.SlotType): RoomPoint {
        val checked = HashSet<Room>()

        // Checks a single room for free space
        fun checkRoom(room: Room): RoomPoint? {
            if (checked.contains(room))
                return null
            checked.add(room)

            val freeSpot = room.firstFreeSlot(type)
            if (freeSpot != null) {
                return RoomPoint(room, freeSpot)
            }

            for (door in room.doors) {
                val neighbour = door.other(room) ?: continue
                checkRoom(neighbour)?.let { return it }
            }

            return null
        }

        // If no room has been specified, start at the piloting room.
        // This is the behaviour used for newly-spawned crew.
        // This might be called very early on before the systems
        // are added, so don't require that piloting exists.
        val firstRoom = startingRoom ?: piloting?.room ?: rooms.first()

        // Check every room via recursion. This will prefer the first
        // room if it's empty, and will only return a room if every room
        // in a path from it to piloting is connected.
        checkRoom(firstRoom)?.let { return it }

        // No free space!
        error("Couldn't find any free space in ship $name, starting at room $firstRoom")
    }

    fun updateAvailableSystems() {
        systems = rooms.mapNotNull { it.system }
        // If there's several of the same system (eg, weapons), put them in
        // the order they're listed in the blueprint.
        mainSystems = systems.mapNotNull { it as? MainSystem }
            .sortedWith(compareBy({ it.sortingType }, { it.configuration.spec.systemIndex }))
        subSystems = systems.mapNotNull { it as? SubSystem }.sortedBy { it.sortingType }

        weaponSystems = systems.mapNotNull { it as? Weapons }.sortedBy { it.configuration.spec.systemIndex }
        weapons = weaponSystems.firstOrNull()
        assignWeaponHardpoints()
        droneSystems = systems.mapNotNull { it as? Drones }.sortedBy { it.configuration.spec.systemIndex }
        drones = droneSystems.firstOrNull()
        engineSystems = systems.mapNotNull { it as? Engines }.sortedBy { it.configuration.spec.systemIndex }
        engines = engineSystems.firstOrNull()
        shieldSystems = systems.mapNotNull { it as? Shields }.sortedBy { it.configuration.spec.systemIndex }
        shields = shieldSystems.firstOrNull()
        medbaySystems = systems.mapNotNull { it as? Medbay }.sortedBy { it.configuration.spec.systemIndex }
        medbay = medbaySystems.firstOrNull()
        clonebay = systems.mapNotNull { it as? Clonebay }.firstOrNull()
        cloaking = systems.mapNotNull { it as? Cloaking }.firstOrNull()
        teleporterSystems = systems.mapNotNull { it as? Teleporter }.sortedBy { it.configuration.spec.systemIndex }
        teleporter = teleporterSystems.firstOrNull()
        hackingSystems = systems.mapNotNull { it as? Hacking }.sortedBy { it.configuration.spec.systemIndex }
        hacking = hackingSystems.firstOrNull()
        mindControlSystems = systems.mapNotNull { it as? MindControl }.sortedBy { it.configuration.spec.systemIndex }
        mindControl = mindControlSystems.firstOrNull()
        piloting = systems.mapNotNull { it as? Piloting }.firstOrNull()
        sensorSystems = systems.mapNotNull { it as? Sensors }.sortedBy { it.configuration.spec.systemIndex }
        sensors = sensorSystems.firstOrNull()
        batterySystems = systems.mapNotNull { it as? BackupBattery }.sortedBy { it.configuration.spec.systemIndex }
        backupBattery = batterySystems.firstOrNull()
        doorsSystem = systems.mapNotNull { it as? Doors }.firstOrNull()
        oxygenSystems = systems.mapNotNull { it as? Oxygen }.sortedBy { it.configuration.spec.systemIndex }
        oxygen = oxygenSystems.firstOrNull()

        // If we have multiple artillery systems, sort them by the order
        // they appear in the XML. This probably isn't necessary, but if
        // we're using this order to display them in the UI or anything
        // like that it's nice to have this order.
        artillery = systems.mapNotNull { it as? Artillery }.sortedBy { it.configuration.spec.systemIndex }

        // The UI will need to change to reflect this
        // Note that this function is called very early on, before shipUI
        // is initialised, right after ship initialisation.
        sys.shipUI?.shipModified()
    }

    /**
     * Split the hardpoints between the weapons systems.
     *
     * With a single weapons system, it controls all the hardpoints. With
     * several, the weapon slots are split evenly between them, in order.
     */
    private fun assignWeaponHardpoints() {
        if (weaponSystems.size <= 1) {
            weapons?.hardpointIndices = hardpoints.indices
            return
        }

        val slotCount = weaponSlots ?: hardpoints.size
        val slotsPerSystem = slotCount / weaponSystems.size
        for ((i, system) in weaponSystems.withIndex()) {
            val start = i * slotsPerSystem
            val end = when (i) {
                weaponSystems.size - 1 -> slotCount // Give any spare slots to the last system
                else -> start + slotsPerSystem
            }
            system.hardpointIndices = start until end
        }
    }

    /**
     * Find a slot to install [system] in, or null if there isn't one.
     *
     * This is one that doesn't already have the system installed. Some ships
     * have several slots for a system (for example two weapons systems), in
     * which case the ones in empty rooms are used first, before any which
     * would replace another system (like a clonebay replacing a medbay).
     */
    fun findFreeSystemSlot(system: SystemBlueprint): SystemInstallConfiguration? {
        val slots = systemSlots.filter { it.system == system && !it.isInstalled }
        return slots.firstOrNull { it.room.system == null } ?: slots.firstOrNull()
    }

    /**
     * Find the weapons system that controls a given hardpoint.
     */
    fun weaponsFor(hardpointIndex: Int): Weapons? {
        return weaponSystems.firstOrNull { it.ownsHardpoint(hardpointIndex) }
    }

    fun weaponsFor(weapon: AbstractWeaponInstance): Weapons? {
        return weaponsFor(hardpoints.indexOfFirst { it.weapon == weapon })
    }

    /**
     * For XFTL_RANDOMIZE: give each weapons system different random weapons,
     * as many as it has the power for, and fill the cargo hold with more.
     */
    /**
     * For the playtest with XFTL_RANDOMIZE (see AutoplayDirector): new
     * random weapons for the next fight, as at the start (but the drones
     * stay), so it tries out more of them.
     */
    fun rerollDebugWeapons() {
        for ((slot, hardpoint) in hardpoints.withIndex()) {
            val weapon = hardpoint.weapon ?: continue
            if (weapon.isPowered)
                weaponsFor(slot)?.setWeaponPower(weapon, false)
        }
        randomizeDebugWeapons(drones = false)
        cargoUpdated()
    }

    private fun randomizeDebugWeapons(drones: Boolean = true) {
        // Only use weapons that can turn up in the game, rather than
        // enemy-only or broken ones, and that are any use in a fight.
        val pool = sys.blueprintManager.blueprints.values
            .filterIsInstance<AbstractWeaponBlueprint>()
            .filter { (it.rarity ?: 0) > 0 && it.name !in RANDOMIZE_EXCLUDED }
            .shuffled()
            .toMutableList()

        for (hardpoint in hardpoints) {
            hardpoint.weapon = null
        }

        // One of each kind of weapon (beam, bomb, laser or ion, and missile
        // or crystal) as a triple one, and another of each as a double one
        // (see WeaponSizes), each kind's two together: that's eight, to fill
        // the slots. (The combined ones aren't in the pool themselves, as
        // they have no rarity.)
        val manager = sys.blueprintManager
        val kinds = listOf(BeamBlueprint::class.java, BombBlueprint::class.java,
            LaserBlueprint::class.java, MissileBlueprint::class.java)
        val picks = ArrayList<AbstractWeaponBlueprint>()
        for (kind in kinds) {
            for (size in listOf(3, 2)) {
                val single = pool.filter { kind.isInstance(it) && WeaponSizes.combined(manager, it, size) != null }
                    .randomOrNull() ?: continue
                pool.remove(single)
                picks += WeaponSizes.combined(manager, single, size)!!
            }
        }

        // Any other weapon picked may be its single, double or triple version
        fun anySize(weapon: AbstractWeaponBlueprint): AbstractWeaponBlueprint =
            (listOf(weapon) + WeaponSizes.SIZES.mapNotNull { WeaponSizes.combined(manager, weapon, it) }).random()

        // Every slot's filled, whether or not there's the power for them all
        for (system in weaponSystems) {
            for (index in system.hardpointIndices) {
                if (index >= hardpoints.size)
                    break
                val weapon = picks.removeFirstOrNull() ?: pool.removeFirstOrNull()?.let(::anySize) ?: break
                hardpoints[index].weapon = weapon.buildInstance(this)
            }
        }

        for (index in cargoBlueprints.indices) {
            cargoBlueprints[index] = picks.removeFirstOrNull() ?: pool.removeFirstOrNull()?.let(::anySize) ?: break
        }

        if (drones)
            fillDebugDrones()
    }

    /**
     * For debug mode: fill every drone slot, and the whole cargo hold, with
     * random twin and triple drones (see DroneSizes), whether or not there's
     * the power for them all, each of a different drone while there are
     * enough of them (only those sold, with a rarity).
     */
    private fun fillDebugDrones() {
        val manager = sys.blueprintManager
        val singles = manager.blueprints.values
            .filterIsInstance<DroneBlueprint>()
            .filter { (it.rarity ?: 0) > 0 && !it.isCombined && it.name !in RANDOMIZE_EXCLUDED }
            .filter { single -> DroneSizes.SIZES.all { DroneSizes.combined(manager, single, it) != null } }
        if (singles.isEmpty())
            return
        val pool = ArrayList<DroneBlueprint>()
        fun next(): DroneBlueprint {
            if (pool.isEmpty())
                pool += singles.shuffled()
            return DroneSizes.combined(manager, pool.removeFirst(), DroneSizes.SIZES.random())!!
        }

        for (system in droneSystems) {
            for (slot in system.drones.indices)
                system.drones[slot] = Drones.DroneInfo(next(), null)
        }
        for (index in cargoBlueprints.indices)
            cargoBlueprints[index] = next()
    }

    /**
     * For XFTL_RANDOMIZE: a random mix of different augments, filling the
     * augment slots, always with the Weapon Pre-Igniter, so the weapons are
     * ready to fire straight away, and a Zoltan shield.
     */
    private fun randomDebugAugments(): List<String> {
        val always = listOf(AugPreigniter.NAME, AugZoltanShield.NAME).filter { sys.blueprintManager.getOrNull(it) != null }
        val others = sys.blueprintManager.blueprints.values
            .filterIsInstance<AugmentBlueprint>()
            .filter { (it.rarity ?: 0) > 0 && it.name !in RANDOMIZE_EXCLUDED && it.name !in always }
            .shuffled()
            .take((maxAugments - always.size).coerceAtLeast(0))
            .map { it.name }
        return (always + others).take(maxAugments)
    }

    fun cargoUpdated() {
        // If a Zoltan shield was removed, its part of the super-shield goes
        // with it. This has to be done separately from clamping it to the
        // maximum, since that never goes below the base maximum, and
        // otherwise selling every Zoltan shield would leave a super-shield.
        val zoltanShields = zoltanShieldCount
        if (zoltanShields < lastZoltanShieldCount) {
            val rows = maxOf(zoltanShields, shieldDroneCount)
            superShield = superShield.coerceAtMost(rows * AugZoltanShield.SHIELD_PER_AUGMENT)
        }
        // And a new one (bought, or from an event) comes charged, rather
        // than only after the next jump.
        if (zoltanShields > lastZoltanShieldCount) {
            superShield += (zoltanShields - lastZoltanShieldCount) * AugZoltanShield.SHIELD_PER_AUGMENT
        }
        lastZoltanShieldCount = zoltanShields
        superShield = superShield

        // Pack the weapons and drones so all the empty slots are at the end.
        // If there's multiple weapons systems, pack the weapons within
        // each one's slots, so weapons don't move between them.
        val weaponRanges = when (weaponSystems.size) {
            0, 1 -> listOf(hardpoints.indices)
            else -> weaponSystems.map { it.hardpointIndices }
        }
        for (range in weaponRanges) {
            var weaponDest = range.first
            for (i in range) {
                val weapon = hardpoints[i].weapon ?: continue
                hardpoints[weaponDest++].weapon = weapon
            }
            for (i in weaponDest..range.last) {
                hardpoints[i].weapon = null
            }
        }

        // Avoid power artefacts from Zoltan power interactions, as how that
        // power is distributed needs to be updated when the weapons change.
        for (system in weaponSystems) {
            system.powerStateChanged()
        }

        for (droneList in droneSystems.map { it.drones }) {
            var droneDest = 0
            for (drone in droneList) {
                if (drone == null)
                    continue
                droneList[droneDest++] = drone
            }
            for (i in droneDest until droneList.size) {
                droneList[i] = null
            }
        }

        // Update all the buttons that have to change to reflect
        // the cargo being modified.
        // This is called very early, before the ship UI is created.
        sys.shipUI?.shipModified()

        // Update the number of each type of augment, for fast lookups.
        privateAugmentValues.clear()
        for (aug in augments) {
            privateAugmentValues[aug] = aug.value + (augmentValues[aug] ?: 0f)
        }
    }

    /**
     * Whether a weapon or drone [item] would fit in the ship (see
     * [addBlueprint]): in an empty weapon slot or drone slot, as it is, or
     * else in the cargo.
     */
    fun hasRoomFor(item: Blueprint): Boolean {
        if (item is AbstractWeaponBlueprint) {
            val weaponSlotCount = weaponSlots ?: hardpoints.size
            if ((0 until weaponSlotCount).any { hardpoints[it].weapon == null })
                return true
        }
        if (item is DroneBlueprint && droneSystems.any { drones -> drones.drones.any { it == null } })
            return true
        return cargoBlueprints.any { it == null }
    }

    /**
     * Place an item in this ship's inventory.
     *
     * If [forced] is true and the ship's inventory is full, the item is placed
     * into the left-behind-when-you-jump window. Otherwise, nothing happens.
     *
     * @return True if the item fits in the ship, false otherwise.
     */
    fun addBlueprint(item: Blueprint, forced: Boolean): Boolean {
        // Augments can only go in their special area
        if (item is AugmentBlueprint) {
            // Break down non-stackable duplicated augments
            // to scrap, using the AUGMENT_FULL event.
            // Note this goes before the max augments check, so the player
            // gets paid for duplicate augments when they're full.
            if (!item.stackable && augments.any { it == item }) {
                val realEvent = sys.eventManager["AUGMENT_FULL"].resolve()
                val synthetic = DialogueWindow.SyntheticEvent(realEvent.text!!.resolve(Random))
                synthetic.resources.scrap = 25
                sys.shipUI.showSyntheticDialogue(synthetic)
                return true
            }

            if (augments.size < maxAugments) {
                augments.add(item)
                cargoUpdated()
                return true
            }

            if (!forced)
                return false
            overflowAugments += item
            overCapacity()
            return true
        }

        if (item is AbstractWeaponBlueprint) {
            val weaponSlotCount = weaponSlots ?: hardpoints.size

            for (slot in 0 until weaponSlotCount) {
                if (hardpoints[slot].weapon != null)
                    continue

                hardpoints[slot].weapon = item.buildInstance(this)
                cargoUpdated()
                return true
            }
        }

        if (item is DroneBlueprint) {
            for (drones in droneSystems) {
                for (slot in 0 until drones.drones.size) {
                    if (drones.drones[slot] != null)
                        continue

                    drones.drones[slot] = Drones.DroneInfo(item, null)
                    cargoUpdated()
                    return true
                }
            }
        }

        for ((slot, current) in cargoBlueprints.withIndex()) {
            if (current != null)
                continue

            cargoBlueprints[slot] = item
            cargoUpdated()
            return true
        }

        if (!forced)
            return false
        overflowItems += item
        overCapacity()
        return true
    }

    // Show the player the equipment window, to make room for the overflow
    private fun overCapacity() {
        cargoUpdated()
        if (this == sys.player)
            sys.shipUI?.showOverCapacity()
    }

    /**
     * Remove an item from the ship's inventory, including it's weapons and
     * drones areas.
     *
     * Returns true if the item was removed, false otherwise.
     */
    fun removeBlueprint(item: Blueprint): Boolean {
        // Check the cargo storage first, so the player doesn't have to swap
        // the item in again if they have multiple.
        for ((index, cargo) in cargoBlueprints.withIndex()) {
            if (cargo != item)
                continue
            cargoBlueprints[index] = null

            cargoUpdated()
            return true
        }

        if (augments.remove(item)) {
            cargoUpdated()
            return true
        }

        for (drones in droneSystems) {
            for ((index, drone) in drones.drones.withIndex()) {
                if (drone?.type != item)
                    continue

                drone.removeAll()
                drones.drones[index] = null

                cargoUpdated()
                return true
            }
        }

        for (hp in hardpoints) {
            if (hp.weapon?.type != item)
                continue

            hp.weapon = null

            cargoUpdated()
            return true
        }

        return false
    }

    /**
     * Check if the ship has an augment by name.
     *
     * This throws an exception if the named blueprint doesn't exist.
     */
    fun hasAugment(name: String): Boolean {
        // Multiverse removes the O2_MASKS blueprint, so we need to account
        // for augments not existing.
        val blueprint = sys.blueprintManager.getOrNull(name) as AugmentBlueprint? ?: return false
        return augmentValues.containsKey(blueprint)
    }

    /**
     * Get the value for the given augment. If multiple of an augment
     * are installed, their values are summed together.
     *
     * Returns 0 if the ship has no such augments.
     */
    fun getAugmentValue(name: String): Float {
        return getAugmentValueOrNull(name) ?: 0f
    }

    /**
     * Get the value for the given augment. If multiple of an augment
     * are installed, their values are summed together.
     *
     * Returns null if the ship has no such augments, or if the specified
     * augment doesn't exist.
     */
    fun getAugmentValueOrNull(name: String): Float? {
        val blueprint = sys.blueprintManager.getOrNull(name) as AugmentBlueprint? ?: return null
        return augmentValues[blueprint]
    }

    /**
     * Update [Room.reservedPlayerSlots] and [Room.reservedEnemySlots]
     * for every room on the ship.
     *
     * This should mainly be called by [AbstractCrew], whenever a change
     * to their pathfinding target is made. It can also be called whenever
     * the crew on the ship are changed, such as from deaths or new crew.
     */
    fun updateCrewReservedSlots() {
        val conflicts = ArrayList<AbstractCrew>()

        for (room in rooms) {
            room.updateCrewReservedSlots(conflicts)
        }

        // If two crewmembers are in, or set to be in, the same location then
        // one of them will be in the conflicts list. Make them find a new
        // free slot.
        for (conflict in conflicts) {
            val freeSpot = findSpaceForCrew(conflict.room, conflict.mode)
            require(conflict.setTargetRoom(freeSpot.room)) { "Failed to set target of empty room!" }
        }
    }

    /**
     * Perform the projectile missed test.
     *
     * This credits piloting/engine skills if it succeeds.
     *
     * [deltaEvasion] is added to the ship's evasion for this calculation. Note
     * this is the opposite of [AbstractWeaponBlueprint.accuracyModifier], which
     * when positive reduces the effective evasion.
     *
     * If [missDeadEnemies] is true, this will always return true if this ship
     * is an enemy ship that's been crew-killed. This should be set so that you
     * can't kill an enemy ship with your in-flight volley after they surrender,
     * however environmental stuff like asteroids should set this to false.
     */
    fun pickMissed(deltaEvasion: Int = 0, missDeadEnemies: Boolean = true): Boolean {
        // Weapons fired at enemy ships before they surrender always
        // miss after a surrender.
        if (!isPlayerShip && sys.getEnemyOf(this) == null && missDeadEnemies)
            return true

        val missed = Random.rollChance(evasion + deltaEvasion)
        if (missed) {
            piloting?.addSkillPoint(Skill.PILOTING)
            for (engines in engineSystems) {
                engines.addSkillPoint(Skill.ENGINES)
            }
        }
        return missed
    }

    fun playCentredAnimation(animation: AnimationSpec, centre: IPoint): FloatingAnimation {
        val firstFrame = animation.spriteAt(sys, 0)
        val offsetPos = ConstPoint(centre.x - firstFrame.width / 2, centre.y - firstFrame.height / 2)
        val floating = FloatingAnimation(sys, animation, offsetPos)
        animations += floating
        return floating
    }

    fun enemyShipUpdated() {
        for (system in droneSystems) {
            system.enemyShipUpdated()
        }

        // If our weapons are currently targeted on the now-neutral enemy,
        // un-target them. There may be several enemy ships.
        for (targets in weaponSystems.map { it.selectedTargets }) {
            val toUnTarget = ArrayList<Int>()
            for (target in targets.iterator()) {
                if (target.targetShip == this || sys.isEnemyOf(this, target.targetShip))
                    continue

                toUnTarget.add(target.weaponNumber)
            }

            for (id in toUnTarget) {
                targets.unTarget(id)
            }
        }
    }

    fun saveCrewPositions() {
        for (member in crew) {
            if (member.mode != AbstractCrew.SlotType.CREW)
                continue

            if (member !is LivingCrew)
                continue

            if (member.ownerShip != this)
                continue

            val currentPos = member.pathingTarget ?: member.standingPosition ?: continue
            member.savedPosition = currentPos.shipPoint
        }
    }

    fun loadCrewPositions() {
        // Note that since we change the crew target positions one at a time,
        // if crew need to swap positions then we can end up not being able
        // to do that, even if it should be valid.
        for (member in crew) {
            if (member.mode != AbstractCrew.SlotType.CREW)
                continue

            if (member !is LivingCrew)
                continue

            if (member.ownerShip != this)
                continue

            val saved = member.savedPosition ?: continue
            val roomPos = shipToRoomPos(saved) ?: continue

            if (!member.setTargetRoom(roomPos.room, roomPos)) {
                // If our spot is taken, go somewhere else in the room.
                member.setTargetRoom(roomPos.room)
            }
        }
    }


    /**
     * Serialise this ship and everything related to it (projectiles,
     * crew, etc) into an XML element.
     */
    fun saveToXML(elem: Element, globalRefs: ObjectRefs) {
        // Create IDs for all the objects that might reference each other
        // Note that events can reference crewmembers, so register them
        // into the global refs.
        val refs = ObjectRefs(globalRefs)
        for (crew in this.crew) {
            globalRefs.register(crew, crew.codename)
        }

        // Build the XML
        SaveUtil.addObjectId(elem, refs, this)

        // These two are read by InGameState, as they're needed for constructor arguments.
        SaveUtil.addAttr(elem, "shipId", name)
        SaveUtil.addAttr(elem, "specId", spec?.name ?: "null")

        val resources = ResourceSet()
        resources.fuel = fuelCount
        resources.missiles = missilesCount
        resources.droneParts = dronesCount
        resources.scrap = scrap

        val resourcesElem = Element("resources")
        resources.saveToXML(resourcesElem, refs)
        elem.addContent(resourcesElem)

        SaveUtil.addAttrFloat(elem, "ftlProgress", ftlChargeProgress)
        SaveUtil.addAttrInt(elem, "reactorPower", purchasedReactorPower)
        SaveUtil.addAttrInt(elem, "health", health)

        SaveUtil.addTagInt(elem, "escapeHealth", escapeHealth, 0)
        SaveUtil.addTagInt(elem, "surrenderHealth", surrenderHealth, 0)
        SaveUtil.addTagFloat(elem, "escapeTimer", escapeTimer, null)
        SaveUtil.addTagInt(elem, "maxSuperShield", baseMaxSuperShield, 5)
        SaveUtil.addTagInt(elem, "superShield", superShield, 0)
        SaveUtil.addTagBoolIfTrue(elem, "automated", automated)
        crewAI.saveToXML(elem)

        SaveUtil.addAttrRef(elem, "bossManager", refs, boss)

        val crewListElem = Element("crew")
        for (crew in this.crew) {
            // We save drones separately
            if (crew is AbstractIndoorsDrone.Pawn)
                continue

            val crewElem = Element("crewMember")
            crew.saveToXML(crewElem, refs)
            crewListElem.addContent(crewElem)
        }
        elem.addContent(crewListElem)

        val systemListElem = Element("systems")
        for (system in systems) {
            val systemElem = Element("system")
            system.saveToXML(systemElem, refs)

            // Save the room this system is in, as we'll need it to match up
            // artillery weapons to the correct room on the flagship.
            SaveUtil.addAttrInt(systemElem, "room", system.room!!.id)

            systemListElem.addContent(systemElem)
        }
        elem.addContent(systemListElem)

        // Serialise the weapons
        val weaponsElem = Element("weapons")
        for ((index, hardpoint) in hardpoints.withIndex()) {
            val weapon = hardpoint.weapon ?: continue

            val weaponElem = Element("weapon")
            SaveUtil.addAttrInt(weaponElem, "hardpoint", index)
            weapon.saveToXML(weaponElem, refs)
            weaponsElem.addContent(weaponElem)
        }
        elem.addContent(weaponsElem)

        // Serialise the drones. If a drone is deployed to another ship, it's
        // serialised on that ship instead of this one.
        val dronesElem = Element("drones")
        for (drone in externalDrones) {
            // Skip drones that are left over from a now-destroyed fight
            if (!sys.isShipPresent(drone.ownerShip))
                continue

            val droneElem = Element("drone")
            drone.saveToXML(droneElem, refs)
            dronesElem.addContent(droneElem)
        }
        for (pawn in crew.mapNotNull { it as? AbstractIndoorsDrone.Pawn }) {
            // Skip the boarding drones of a ship that's gone (it got away,
            // or was destroyed), which can't be saved without it
            if (!sys.isShipPresent(pawn.drone.ownerShip))
                continue

            val droneElem = Element("drone")
            pawn.drone.saveToXML(droneElem, refs)
            dronesElem.addContent(droneElem)
        }
        for (flyingDrone in projectiles.mapNotNull { it as? FlyingDroneProjectile }) {
            val droneElem = Element("drone")
            flyingDrone.drone.saveToXML(droneElem, refs)
            dronesElem.addContent(droneElem)
        }
        elem.addContent(dronesElem)

        // Serialise the augments. Since these are only the blueprint name,
        // they don't need a new parent element line drones/weapons/etc do.
        for (aug in augments) {
            val augElem = Element("augment")
            SaveUtil.addAttr(augElem, "name", aug.name)
            elem.addContent(augElem)
        }

        for (item in overflowItems)
            SaveUtil.addTag(elem, "overflowItem", item.name)
        for (augment in overflowAugments)
            SaveUtil.addTag(elem, "overflowAugment", augment.name)

        colossal?.let { boss ->
            val bossElem = Element("colossalBoss")
            boss.saveToXML(bossElem)
            elem.addContent(bossElem)
        }

        // Serialise loose cargo
        for ((index, item) in cargoBlueprints.withIndex()) {
            if (item == null)
                continue

            val cargoElem = Element("cargoBlueprint")
            SaveUtil.addAttr(cargoElem, "name", item.name)
            SaveUtil.addAttrInt(cargoElem, "index", index)
            elem.addContent(cargoElem)
        }

        // Serialise the doors. Most of the time there's nothing interesting
        // about them other than whether they're open or closed, but occasionally
        // they have more information (like the damage they've taken from
        // being attacked).
        val doorsElem = Element("doors")
        val doorStateString = StringBuilder()
        for ((index, door) in doors.withIndex()) {
            val thisDoorElem = door.saveToXML()
            thisDoorElem?.setAttribute("doorIndex", index.toString())
            thisDoorElem?.let { doorsElem.addContent(it) }

            doorStateString.append(if (door.isSetOpen) 'Y' else 'n')
        }
        val doorStateElem = Element("isOpen")
        doorStateElem.addContent(doorStateString.toString())
        doorsElem.addContent(doorStateElem)
        elem.addContent(doorsElem)

        // Do the same thing for rooms, they won't emit an element unless
        // there's a fire or breach or something like that.
        val roomsElem = Element("rooms")
        for (room in rooms) {
            val thisRoomElem = room.saveToXML()
            thisRoomElem?.setAttribute("id", room.id.toString())
            thisRoomElem?.let { roomsElem.addContent(it) }
        }
        elem.addContent(roomsElem)

        // Serialise the room oxygen levels. Since there's a lot of rooms,
        // don't serialise them all individually.
        val oxygenLevelString = StringBuilder()
        for (room in rooms) {
            oxygenLevelString.append(room.oxygen)
            oxygenLevelString.append(' ')
        }
        val oxygenLevelElem = Element("oxygenLevels")
        oxygenLevelElem.addContent(oxygenLevelString.toString().trim())
        elem.addContent(oxygenLevelElem)

        // Serialise any floating animations, like the animations from a weapon
        // hitting a room or the shields.
        val animationsElem = Element("animations")
        for (animation in animations) {
            val animElem = Element("animation")
            animation.saveToXML(animElem)
            animationsElem.addContent(animElem)
        }
        elem.addContent(animationsElem)

        // Serialise in-flight projectile
        val projectilesElem = Element("projectiles")
        for (projectile in projectiles) {
            // A flying drone is saved as a drone rather than a projectile,
            // though really it's kinda both.
            if (projectile is FlyingDroneProjectile)
                continue

            val projectileElem = Element("projectile")
            SaveUtil.addAttr(projectileElem, "loadType", projectile.serialisationType)
            projectile.saveToXML(projectileElem, refs)
            projectilesElem.addContent(projectileElem)
        }
        elem.addContent(projectilesElem)
    }

    fun loadFromXml(rootElem: Element, refs: RefLoader) {
        SaveUtil.registerObjectId(rootElem, refs, this)

        val resources = ResourceSet(rootElem.getChild("resources"), refs, sys)
        fuelCount = resources.fuel
        missilesCount = resources.missiles
        dronesCount = resources.droneParts
        scrap = resources.scrap

        ftlChargeProgress = SaveUtil.getAttrFloat(rootElem, "ftlProgress")
        purchasedReactorPower = SaveUtil.getAttrInt(rootElem, "reactorPower")
        health = SaveUtil.getAttrInt(rootElem, "health")

        escapeHealth = SaveUtil.getOptionalTagInt(rootElem, "escapeHealth") ?: 0
        surrenderHealth = SaveUtil.getOptionalTagInt(rootElem, "surrenderHealth") ?: 0
        escapeTimer = SaveUtil.getOptionalTagFloat(rootElem, "escapeTimer")
        baseMaxSuperShield = SaveUtil.getOptionalTagInt(rootElem, "maxSuperShield") ?: 5
        crewAI.loadFromXML(rootElem)
        automated = SaveUtil.getOptionalTagBool(rootElem, "automated") ?: false

        SaveUtil.getAttrRef(rootElem, "bossManager", refs, BossManager::class.java) { boss = it }

        // Load the systems
        for (elem in rootElem.getChild("systems").getChildren("system")) {
            // Spawn in the system
            val name: String = elem.getAttributeValue("name")
            val blueprint = sys.blueprintManager[name] as SystemBlueprint

            // Find the slot this system is in.
            // This isn't as simple as it might sound, since for artillery
            // we can have multiple systems installed. Thus we need to use
            // the serialised room ID to make sure we load this into the
            // correct instance of the system.
            val roomId = SaveUtil.getAttrInt(elem, "room")
            val room = rooms[roomId]

            val slot = room.systemSlots.first { it.system == blueprint }
            room.setSystem(slot)

            // Re-load the system's properties
            room.system!!.loadFromXML(elem, refs)
        }

        // Load the crew
        for (crewElem in rootElem.getChild("crew").getChildren("crewMember")) {
            val raceName = crewElem.getAttributeValue("type")
            val race = sys.blueprintManager[raceName] as CrewBlueprint
            val info = LivingCrewInfo.generateRandom(race, sys)
            val crewMember = addCrewMember(info, false)
            crewMember.loadFromXML(crewElem, refs)
        }

        // Deserialise the doors. (A ship whose layout's changed since the game
        // was saved, as the mod's generated ships' can, loses what it had
        // for the doors and rooms that aren't there any more, and the new
        // ones start as they would.)
        val doorsElem = rootElem.getChild("doors")
        val doorsWithXML = HashSet<Door>()
        for (doorElem in doorsElem.getChildren("door")) {
            val index = SaveUtil.getAttrInt(doorElem, "doorIndex")
            val door = doors.getOrNull(index) ?: continue
            door.loadFromXML(doorElem)
            doorsWithXML.add(door)
        }
        for (door in doors) {
            if (door !in doorsWithXML) {
                door.loadWithoutXML()
            }
        }
        val doorStateString = doorsElem.getChildTextTrim("isOpen")
        for ((index, door) in doors.withIndex()) {
            val open = doorStateString.getOrNull(index) == 'Y'
            door.loadSavedOpen(open)
        }

        // Deserialise rooms, without their oxygen levels which are stored separately below.
        val roomsElem = rootElem.getChild("rooms")
        for (elem in roomsElem.getChildren("room")) {
            val id = elem.getAttributeValue("id").toInt()
            rooms.getOrNull(id)?.loadFromXML(elem)
        }

        // Deserialise the oxygen levels, which are just numbers for each room.
        val oxygenLevels = rootElem.getChildTextTrim("oxygenLevels").split(' ', '\t')
        for ((index, levelStr) in oxygenLevels.withIndex()) {
            val room = rooms.getOrNull(index) ?: continue
            room.oxygen = levelStr.toFloat()
        }

        // Deserialise the weapons
        for (weaponElem in rootElem.getChild("weapons").getChildren("weapon")) {
            val hardpointIndex = SaveUtil.getAttrInt(weaponElem, "hardpoint")
            val type = SaveUtil.getAttr(weaponElem, "type")
            val blueprint = sys.blueprintManager[type] as AbstractWeaponBlueprint

            val weapon = blueprint.buildInstance(this)
            weapon.loadFromXML(weaponElem, refs)
            hardpoints[hardpointIndex].weapon = weapon
        }

        // Deserialise the drones, both friendly and not.
        for (droneElem in rootElem.getChild("drones").getChildren("drone")) {
            val type = SaveUtil.getAttr(droneElem, "type")
            val blueprint = sys.blueprintManager[type] as DroneBlueprint

            val drone = blueprint.makeInstance()
            drone.loadFromXML(droneElem, refs, this)

            // The drone will add itself to the externalDrones or crew list.
        }

        // Deserialise the augments.
        for (augElem in rootElem.getChildren("augment")) {
            val name = SaveUtil.getAttr(augElem, "name")
            augments.add(sys.blueprintManager[name] as AugmentBlueprint)
        }

        // This must be set after the max super-shield (which depends on the
        // ship's Zoltan shields) is loaded, to avoid it being wrongly clamped.
        superShield = SaveUtil.getOptionalTagInt(rootElem, "superShield") ?: 0
        lastZoltanShieldCount = zoltanShieldCount

        overflowItems.clear()
        for (itemElem in rootElem.getChildren("overflowItem"))
            overflowItems += sys.blueprintManager[SaveUtil.getAttr(itemElem, "v")] as Blueprint
        overflowAugments.clear()
        for (augElem in rootElem.getChildren("overflowAugment"))
            overflowAugments += sys.blueprintManager[SaveUtil.getAttr(augElem, "v")] as AugmentBlueprint

        rootElem.getChild("colossalBoss")?.let { colossal?.loadFromXML(it) }

        // Deserialise loose cargo
        for (cargoElem in rootElem.getChildren("cargoBlueprint")) {
            val name = SaveUtil.getAttr(cargoElem, "name")
            val index = SaveUtil.getAttrInt(cargoElem, "index")
            cargoBlueprints[index] = sys.blueprintManager[name] as Blueprint
        }

        // Deserialise explosion (and similar) animations
        for (animElem in rootElem.getChild("animations").getChildren("animation")) {
            animations += FloatingAnimation.loadFromXML(animElem, sys)
        }

        // Deserialise the in-flight projectiles
        for (projectileElem in rootElem.getChild("projectiles").getChildren("projectile")) {
            val serialisationType = SaveUtil.getAttr(projectileElem, "loadType")
            IProjectile.loadFromXML(sys, projectileElem, refs, serialisationType) { projectiles += it }
        }

        updateCrewReservedSlots()
        updateAvailableSystems()
        cargoUpdated()
    }

    // (called several times a frame, so it doesn't make a list)
    fun hasCrewOwnedByShip(otherShip: Ship): Boolean =
        crew.any { it is LivingCrew && it.ownerShip == otherShip }

    /**
     * Like [hasCrewOwnedByShip], but not counting the crew who've been
     * killed and are still playing their dying animation: a ship's crew are
     * dead as soon as the last one's killed, so the shots still on their way
     * to it miss (see pickMissed), rather than wrecking it during the
     * animation.
     */
    fun hasLivingCrewOwnedBy(otherShip: Ship): Boolean =
        crew.any { it is LivingCrew && it.ownerShip == otherShip && it.health > 0f }

    companion object {
        /** How long the shield bubble flashes for after a hit, and how bright. */
        const val SHIELD_FLASH_TIME = 0.35f
        const val SHIELD_FLASH_ALPHA = 0.6f

        // The colour of the teleporter's and mind control's icons on a room
        // they're waiting to reach (see renderPendingTargets)
        private val PENDING_TARGET_COLOUR = Colour(235, 45, 45)

        // Where the outline's drawn round the icons, a pixel each way
        private val PENDING_OUTLINE_OFFSETS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to -1, -1 to 1, 1 to 1)

        /**
         * How many cells a hostile ship's hull bar has room for in a
         * normal-sized or long ship's window, each showing [hullScale]
         * points (a wide ship's has room for three times as many, see
         * doc/game/hull.md).
         */
        const val HULL_BAR_CELLS = 16

        // How strongly a combined weapon's shadow targets' crosshairs show
        const val SHADOW_CROSSHAIR_ALPHA = 0.45f

        /**
         * The most cells of hull a bigger version of a ship (or a hybrid)
         * has, one short of the whole bar, which is kept for bosses as big
         * as them.
         */
        const val MAX_ORDINARY_HULL_CELLS = HULL_BAR_CELLS - 1

        /**
         * How many times its normal ship's hull a long or wide version has
         * (see doc/game/hull.md): a long one less than a wide one, as it's worth
         * fewer ships and its bar has fewer points.
         */
        const val HULL_FACTOR_LONG = 2.5f
        const val HULL_FACTOR_WIDE = 4f

        /**
         * How much tougher a hybrid is than other ships of its size.
         */
        const val HULL_FACTOR_HYBRID = 1.25f

        // The start of a hybrid ship's blueprint's name
        const val HYBRID_PREFIX = "XFTL_HYBRID_"

        /**
         * The number of augments and cargo slots a ship has, unless its blueprint says otherwise.
         */
        const val DEFAULT_MAX_AUGMENTS: Int = 3
        const val DEFAULT_MAX_CARGO: Int = 4

        /**
         * The maximum number of crew a ship has, unless its blueprint says otherwise.
         */
        const val DEFAULT_MAX_CREW: Int = 8

        // The fuel a vanilla-sized player ship starts with in vanilla
        const val STARTING_FUEL: Int = 16

        /**
         * The fuel a player ship of this size starts with: vanilla's, times
         * the fuel it uses per jump (its size), times how much wider XFTL's
         * sector maps are than vanilla's (as the rebel fleet advances that
         * much less per jump, there are that many more jumps across a
         * sector). That's 21 for a vanilla-sized ship and 43 for a size 2
         * one, against vanilla's 16.
         */
        @JvmStatic
        fun startingFuel(shipSize: Int): Int =
            (STARTING_FUEL * shipSize * Sector.GRID_SIZE.x.toFloat() / Sector.VANILLA_GRID_SIZE.x).roundToInt()

        /**
         * The scrap a player ship of this size starts with: vanilla's (30 on
         * easy, 10 otherwise), times its size.
         */
        @JvmStatic
        fun startingScrap(difficulty: Difficulty, shipSize: Int): Int =
            (if (difficulty == Difficulty.EASY) 30 else 10) * shipSize

        /**
         * The maximum number of main systems a ship has, unless its blueprint says otherwise.
         */
        const val DEFAULT_MAX_SYSTEMS: Int = 8

        /**
         * The most subsystems (piloting, sensors, doors and backup
         * batteries) a ship can have, as there's only room for so many in
         * the subsystems box.
         */
        const val MAX_SUB_SYSTEMS: Int = 8

        /**
         * The blueprints XFTL_RANDOMIZE never picks: the bombs that only
         * target your own ship (the Healing Burst and the Repair Burst), which
         * are no use against the enemy, the anti-bio beam, which can't hurt
         * the enemy's ship, and the fire beam, which barely can.
         */
        /**
         * The [count] best of [items] by [score] (the first ones, of those as
         * good), in their order in [items]: all of them, if there aren't
         * more than [count].
         */
        fun <T> bestInOrder(items: List<T>, count: Int, score: (T) -> Int): List<T> {
            if (items.size <= count)
                return items
            val best = items.indices.sortedWith(compareByDescending<Int> { score(items[it]) }.thenBy { it })
                .take(count).sorted()
            return best.map { items[it] }
        }

        val RANDOMIZE_EXCLUDED = setOf("BOMB_HEAL", "BOMB_HEAL_SYSTEM", "BEAM_BIO", "BEAM_FIRE")
    }

    class Hardpoint(val spec: ShipBlueprint.ParsedHardpoint) {
        var weapon: AbstractWeaponInstance? = null
    }

    class FloatingAnimation(game: InGameState, val spec: AnimationSpec, val pos: ConstPoint, val scaling: Float = 1f) {
        private val animation = spec.startSingle(game)

        val isFinished get() = animation.isStopped

        fun render(g: Graphics) {
            val frame = animation.currentFrame

            if (scaling == 1f) {
                frame.draw(pos.x.f, pos.y.f)
                return
            }

            // This is used for flak
            g.pushTransform()
            g.translate(pos.x.f, pos.y.f)
            g.scale(scaling, scaling)
            frame.draw()
            g.popTransform()
        }

        fun update(dt: Float) {
            animation.update(dt)
        }

        fun saveToXML(elem: Element) {
            SaveUtil.addAttr(elem, "name", spec.name)
            SaveUtil.addAttrInt(elem, "x", pos.x)
            SaveUtil.addAttrInt(elem, "y", pos.y)
            SaveUtil.addAttrFloat(elem, "timer", animation.timer)
            SaveUtil.addAttrFloat(elem, "scaling", scaling)
        }

        companion object {
            fun loadFromXML(elem: Element, game: InGameState): FloatingAnimation {
                val spec = when (val name = SaveUtil.getAttr(elem, "name")) {
                    // Fires don't have an animation set in the XML,
                    // so we have to grab the proper animation here.
                    FireInstance.SMOKE_ANIMATION.name -> FireInstance.SMOKE_ANIMATION

                    else -> game.animations[name]
                }

                val x = SaveUtil.getAttrInt(elem, "x")
                val y = SaveUtil.getAttrInt(elem, "y")
                val scaling = SaveUtil.getAttrFloat(elem, "scaling")

                val anim = FloatingAnimation(game, spec, ConstPoint(x, y), scaling)
                anim.animation.timer = SaveUtil.getAttrFloat(elem, "timer")

                return anim
            }
        }
    }

    inner class DamageNumber(var x: Float, var y: Float, val image: Image, val colour: Colour) {
        var timer: Float = 0f

        private val LIFETIME = 1.2f

        fun draw() {
            image.drawAlignedCentred(x.roundToInt(), y.roundToInt(), colour)
        }

        fun update(dt: Float): Boolean {
            timer += dt
            y -= dt / LIFETIME * 10

            return timer < LIFETIME
        }
    }
}
