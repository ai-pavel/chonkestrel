package xyz.znix.xftl.shipgen

import org.jdom2.Element
import xyz.znix.xftl.*
import xyz.znix.xftl.ai.FriendlyCrewAI
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.LivingCrewInfo
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.ShipBlueprint
import xyz.znix.xftl.sector.EventManager
import xyz.znix.xftl.sector.IEvent
import xyz.znix.xftl.sector.SectorType
import xyz.znix.xftl.drones.AntiPersonnelDrone
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.LaserBlueprint
import xyz.znix.xftl.weapons.MissileBlueprint
import xyz.znix.xftl.weapons.WeaponSizes
import xyz.znix.xftl.weapons.DroneSizes
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class ShipGenerator(val df: Datafile, val bp: BlueprintManager) {
    /**
     * Build an enemy ship from [spec]. With a [shape] other than
     * [ShipShape.NORMAL], it's a bigger version of the ship (see
     * [xyz.znix.xftl.game.FightPlan]), if there is one: check the ship's
     * [ShipBlueprint.shape] to see.
     */
    @JvmOverloads
    fun buildShip(
        sys: InGameState, spec: EnemyShipSpec, sector: Int, difficulty: Difficulty, seed: Int? = null,
        shape: ShipShape = ShipShape.NORMAL
    ): Ship {
        // Shift everything back a sector on easy.
        // This is thus -1 for the first sector on easy.
        val effectiveSector = sector + difficulty.sectorOffset

        // Use a specific random instance to generate this ship, and
        // print out it's seed. This will let us diagnose ships where
        // something went wrong with the generation.
        val effectiveSeed = seed ?: Random.nextInt()
        val rand = Random(effectiveSeed)

        if (seed == null) {
            val seedStr = seedToString(sector, difficulty, effectiveSeed)
            println("Generating ship from spec '${spec.name}' with seed $seedStr")
        }

        val base = spec.blueprintFor(sys.playerBlueprint).resolve(rand) as LazyShipBlueprint
        // The size 3 bosses are built exactly as their blueprints say
        if (base.real.bossKind != null)
            return buildBoss(sys, spec, base.real, rand)
        // A hybrid in its place (see hybridFor), in the shape asked for
        val manager = sys.blueprintManager
        val hybrid = if (spec.noHybrid) null else hybridFor(sys, base, shape, rand)
        val shipBlueprint = (hybrid?.let { if (shape == ShipShape.NORMAL) it else manager.shipVariant(it.name, shape, rand) }
            ?: manager.shipVariant(base.name, shape, rand) ?: base).real
        val elem = shipBlueprint.loadElem()

        val ship = Ship(shipBlueprint, sys, null, spec, null)
        ship.loadDefaultContents()

        ship.escapeHealth = spec.escapeHealth?.pick(rand) ?: 0
        ship.surrenderHealth = spec.surrenderHealth?.pick(rand) ?: 0

        // If surrender and escape come to the same health, offset them by 1.
        if (ship.surrenderHealth == ship.escapeHealth) {
            ship.escapeHealth = ship.surrenderHealth - 1
        }

        for (crewSpec in ship.type.initialCrew) {
            // See the link to the guide below, calculated same as system power
            val min = crewSpec.amount
            var max = crewSpec.max

            if (max == null) {
                println("Missing max tag in crewCount tag for generated ship '${ship.name}'")
                max = min
            }

            val baseAmount = min + ((max - min) / 8f * effectiveSector).toInt()

            val crewOverride = spec.crewOverride?.specifiers ?: emptyList()

            if (crewOverride.isEmpty()) {
                for (i in 1..baseAmount) {
                    val crewBP = sys.lootPool.getCrewOrRandom(rand, crewSpec.race)
                    val crewInfo = LivingCrewInfo.generateRandom(crewBP, sys)
                    ship.addCrewMember(crewInfo, true)
                }
            } else {
                for (crew in crewOverride) {
                    val count = when {
                        crew.prop > 0 -> (crew.prop * baseAmount).toInt().coerceAtLeast(1)
                        else -> crew.amount
                    }

                    for (i in 1..count) {
                        val crewBP = sys.lootPool.getCrewOrRandom(rand, crew.race)
                        val crewInfo = LivingCrewInfo.generateRandom(crewBP, sys)
                        ship.addCrewMember(crewInfo, true)
                    }
                }
            }

            // This is used to send the right number of boarders
            // Note we count the crew we added, since for overrides we can
            // get some other number if the prop values don't sum to 1.
            ship.crewAI.initialCrewCount = ship.crew.size
        }

        // Not 100% sure about this, but it looks like we remove zoltan shields
        // on the first sector on easy.
        if (sector == 0 && difficulty == Difficulty.EASY) {
            ship.augments.remove(sys.blueprintManager["ENERGY_SHIELD"])
        }

        // If the ship included any weapons or drones, clear them out because
        // we'll pick them ourselves. This happens if a weapon/drone is set
        // explicitly in weaponList/droneList, rather than via a list.
        for (hp in ship.hardpoints) {
            hp.weapon = null
        }
        ship.drones?.let { drones ->
            for (i in 0 until drones.drones.size) {
                drones.drones[i] = null
            }
        }

        // Calculate the power levels for each system
        // There's a useful Reddit post by someone else who's also been
        // reverse-engineering FTL, which saves us the effort of a lot
        // of the reverse-engineering for shipgen:
        // https://old.reddit.com/r/ftlgame/comments/qu8kz7/details_on_enemy_ship_generation/

        // Pick the power for each category. A bigger version of a ship (see
        // ShipShape) gets as many times more as it's worth, as it has more
        // systems.
        val sizeScale = if (ship.type.isVariant) ship.type.shape.strength else 1
        var offensivePower = (effectiveSector + 1).coerceAtLeast(1) * sizeScale
        var defensivePower = (effectiveSector + 2).coerceAtLeast(1) * sizeScale
        var generalPower = ((effectiveSector + 1) / 2).coerceAtLeast(1) * sizeScale

        fun subtractPower(category: SystemCategory, power: Int) = when (category) {
            SystemCategory.OFFENSIVE -> offensivePower -= power
            SystemCategory.DEFENSIVE -> defensivePower -= power
            SystemCategory.GENERAL -> generalPower -= power
        }

        if (difficulty == Difficulty.HARD)
            generalPower++

        // Place the optional systems
        val optionalSystemChance = (sector * 10 + when (difficulty) {
            Difficulty.EASY -> 10
            Difficulty.NORMAL -> 20
            Difficulty.HARD -> 30
        }).coerceAtLeast(10)

        for (system in ship.systemSlots) {
            // Don't install one system by uninstalling another.
            // This also checks if this system is already installed.
            if (system.room.system != null)
                continue

            // A long version of a ship has its cloaking half the time (a wide
            // one always has it, see ShipShape)
            val chance = when {
                ship.type.isVariant && system.spec.systemName == "cloaking" -> VARIANT_CLOAKING_CHANCE
                else -> optionalSystemChance
            }
            if (!rand.rollChance(chance))
                continue

            // Install the system
            system.room.setSystem(system)

            // Add a power cost for adding this system.
            // Don't check the current power here, it's allowed to go negative.
            val systemInstance = system.room.system!!
            val category = getSystemCategory(systemInstance)
            val basePowerUse = when {
                category == SystemCategory.OFFENSIVE && systemInstance !is Artillery -> 1
                difficulty == Difficulty.HARD -> 1
                else -> 2
            }
            subtractPower(category, basePowerUse)
        }

        // Later in the game, ships that can have a doors system always do,
        // locked against boarders (and boarding drones), stronger further in
        lateDoors(ship, effectiveSector)

        // Make sure each system has at least one power
        // This is a fix for the auto-assault ship which has 0 minimum weapons power
        for (system in ship.systems) {
            if (system.energyLevels == 0) {
                system.energyLevels = 1
            }
        }

        // Find a soft cap for how much power each system can have
        // This can be exceeded by scripted weapons and drones
        val softCaps = HashMap<AbstractSystem, Int>()
        for (system in ship.systems) {
            softCaps[system] = pickSystemPowerLimit(effectiveSector, difficulty, system, rand)
        }

        // Spend the power upgrading the systems

        fun getSystem(category: SystemCategory?): AbstractSystem? {
            val suitable = ship.systems.filter {
                val systemCategory = getSystemCategory(it)
                if (category != null && category != systemCategory)
                    return@filter false

                return@filter it.energyLevels < softCaps.getValue(it)
            }

            if (suitable.isEmpty())
                return null
            return suitable.random(rand)
        }

        while (offensivePower > 0) {
            // Get the system, or stop if we fully upgrade all of them.
            val system = getSystem(SystemCategory.OFFENSIVE) ?: break

            system.energyLevels++
            offensivePower--

            // Note we track the reactor power separately to everything else.
            // It's not necessarily just the sum of all the system powers,
            // and in a plasma storm it would matter.
            ship.purchasedReactorPower++
        }

        while (defensivePower > 0) {
            val system = getSystem(SystemCategory.DEFENSIVE) ?: break

            system.energyLevels++
            defensivePower--
            ship.purchasedReactorPower++
        }

        // Add (or subtract, in the case of negative power) the leftover
        // power from the offensive and defensive pools, and use that
        // to upgrade everything else.
        generalPower += offensivePower
        generalPower += defensivePower

        while (generalPower > 0) {
            val system = getSystem(null) ?: break
            system.energyLevels++
            generalPower--

            if (system is MainSystem) {
                ship.purchasedReactorPower++
            }
        }

        // Select the scripted weapons/drones
        // Note there's no scripted drones in vanilla, but implement it
        // since mods likely use it.
        val weaponOverrides = spec.weaponOverride?.select(rand)?.map { it as AbstractWeaponBlueprint }
        val droneOverrides = spec.droneOverride?.select(rand)?.map { it as DroneBlueprint }

        @Suppress("DuplicatedCode")
        if (weaponOverrides != null) {
            // Check if we need to upgrade the weapon power to fit
            // the scripted weapons.
            val weapons = ship.weapons!!
            val maxWeaponPower = weapons.configuration.spec.aiMaxPower!!
            val scriptedWeaponPower = weaponOverrides.sumBy { it.power }.coerceAtMost(maxWeaponPower)
            val missingPower = scriptedWeaponPower - weapons.energyLevels
            if (missingPower > 0) {
                weapons.energyLevels += missingPower
                ship.purchasedReactorPower += missingPower
            }
            for (weapon in weaponOverrides) {
                ship.addBlueprint(weapon, false)
            }
        }

        @Suppress("DuplicatedCode")
        if (droneOverrides != null) {
            // Same goes for drones.
            val drones = ship.drones!!
            val maxDronePower = drones.configuration.spec.aiMaxPower!!
            val scriptedDronePower = droneOverrides.sumBy { it.power }.coerceAtMost(maxDronePower)
            val missingPower = scriptedDronePower - drones.energyLevels
            if (missingPower > 0) {
                drones.energyLevels += missingPower
                ship.purchasedReactorPower += missingPower
            }
            for (drone in droneOverrides) {
                ship.addBlueprint(drone, false)
            }
        }

        // Spawn the weapons in. Again, see the linked Reddit post near
        // the top of the function, as it explains what this is doing.
        val weaponList = parseWeaponsList(elem)
        var hasHullDamageWeapon = false
        var hasLaserLikeWeapon = false

        // The weapons the blueprint says the ship always has go in its first
        // weapons system first, with more power for them if they need it.
        ship.weapons?.let { weapons ->
            for (name in ship.type.requiredWeapons) {
                val weapon = sys.blueprintManager[name] as AbstractWeaponBlueprint
                val slot = weapons.hardpointIndices.firstOrNull { ship.hardpoints[it].weapon == null } ?: break
                val usedPower = weapons.hardpointIndices.sumOf { ship.hardpoints[it].weapon?.type?.power ?: 0 }
                val missingPower = usedPower + weapon.power - weapons.energyLevels
                if (missingPower > 0) {
                    weapons.energyLevels += missingPower
                    ship.purchasedReactorPower += missingPower
                }
                ship.hardpoints[slot].weapon = weapon.buildInstance(ship)
                val (doesHullDamage, isLaserStyle) = getWeaponFlags(weapon)
                hasHullDamageWeapon = hasHullDamageWeapon || doesHullDamage
                hasLaserLikeWeapon = hasLaserLikeWeapon || isLaserStyle
            }
        }

        // Some weapons are double or triple ones (see WeaponSizes), more
        // further in and on bigger ships; triple ones only in the most
        // dangerous sectors, at most a couple on each ship
        val epicSector = sys.currentBeacon.sector.type.hazards.let {
            it == SectorType.HazardLevel.CATASTROPHIC || it == SectorType.HazardLevel.APOCALYPTIC
        }
        var triplesLeft = if (epicSector) WeaponSizes.EPIC_MAX_TRIPLES else 0

        // Ships with several weapons systems (such as the bigger versions of
        // the vanilla ships) fill each one's own slots, with its own power.
        // The under-construction flagship and zoltan peace envoy don't have any weapons.
        val weaponSlots = ship.weaponSlots ?: ship.hardpoints.size
        for (weaponSystem in ship.weaponSystems) while (true) {
            val slots = weaponSystem.hardpointIndices.filter { it < ship.hardpoints.size && it < weaponSlots }
            val usedPower = slots.sumOf { ship.hardpoints[it].weapon?.type?.power ?: 0 }
            val remainingPower = weaponSystem.energyLevels - usedPower

            // Ran out of power?
            if (remainingPower <= 0)
                break

            // Filled all our hardpoints?
            val freeSlot = slots.firstOrNull { ship.hardpoints[it].weapon == null } ?: break

            val suitable = weaponList.filter {
                if (it.power > remainingPower)
                    return@filter false

                // We can't use all the remaining power, except for
                // with a one-power weapon.
                if (it.power == remainingPower && it.power != 1)
                    return@filter false

                // Don't start with one-power weapons
                if (usedPower == 0 && remainingPower >= 3 && it.power < 2)
                    return@filter false

                // This weapon must use >25% of the remaining power
                if (it.power < remainingPower / 4f)
                    return@filter false

                // Pick weapons such that we can:
                // * Do hull damage
                // * Have a laser-style (laser or non-shield-piercing
                //   missile, which is to say a crystal weapon) weapon.

                val (doesHullDamage, isLaserStyle) = getWeaponFlags(it)

                var satisfiesFlags = false

                // If our weapon satisfies either of the unsatisfied
                // flags, allow it.
                if (!hasHullDamageWeapon && doesHullDamage) {
                    satisfiesFlags = true
                }
                if (!hasLaserLikeWeapon && isLaserStyle) {
                    satisfiesFlags = true
                }

                // Do we already have weapons that can do what's required?
                if (hasHullDamageWeapon && hasLaserLikeWeapon) {
                    satisfiesFlags = true
                }

                return@filter satisfiesFlags
            }

            // We should never find ourselves with no suitable weapons,
            // so print a warning if that occurs (along with hopefully
            // enough information to diagnose it).
            if (suitable.isEmpty()) {
                println("Warning: wasn't able to find any more suitable weapons for ship '${ship.name}'")
                println("  Current weapons: " + ship.hardpoints.mapNotNull { it.weapon })
                println("  Weapon system power: ${weaponSystem.energyLevels}")
                break
            }

            // A double or triple weapon (see WeaponSizes) may need more
            // power than's left, so the weapons system's upgraded for it, as
            // far as it can be
            val picked = suitable.random(rand)
            val headroom = weaponSystem.blueprint.maxPower - weaponSystem.energyLevels
            val weapon = WeaponSizes.enemyWeapon(
                sys.blueprintManager, picked, remainingPower + headroom, ship.type.shape, effectiveSector,
                triplesLeft > 0, rand
            )
            if (weapon.combinedCount >= 3)
                triplesLeft--
            addWeaponPower(ship, weaponSystem, weapon.power - remainingPower)

            val (doesHullDamage, isLaserStyle) = getWeaponFlags(weapon)
            if (doesHullDamage)
                hasHullDamageWeapon = true
            if (isLaserStyle)
                hasLaserLikeWeapon = true

            ship.hardpoints[freeSlot].weapon = weapon.buildInstance(ship)
        }

        // A long or wide ship, from a quarter of the way through the game,
        // always has at least one double weapon: if it hasn't, the first of
        // its single ones that can be (with the power for it) is made double
        if (ship.type.shape != ShipShape.NORMAL &&
            WeaponSizes.progress(effectiveSector) >= WeaponSizes.BIG_SHIP_DOUBLE_FROM &&
            ship.hardpoints.none { it.weapon?.type?.isCombined == true }
        ) {
            makeOneWeaponDouble(sys, ship)
        }

        // The drones the blueprint says the ship always has, spread over its
        // drones systems, with more power for them if they need it (up to
        // each system's most: any that don't fit are left out)
        for ((index, name) in withBulwarks(ship, ship.type.requiredDrones).withIndex()) {
            val systems = ship.droneSystems
            if (systems.isEmpty())
                break
            val drone = sys.blueprintManager[name] as DroneBlueprint
            fun used(system: Drones) = system.drones.sumOf { it?.type?.power ?: 0 }
            val drones = systems.drop(index % systems.size).plus(systems).firstOrNull {
                it.drones.any { d -> d == null } && used(it) + drone.power <= it.blueprint.maxPower
            } ?: continue
            val usedPower = used(drones)
            val missingPower = usedPower + drone.power - drones.energyLevels
            if (missingPower > 0) {
                drones.energyLevels += missingPower
                ship.purchasedReactorPower += missingPower
            }
            drones.drones[drones.drones.indexOfFirst { it == null }] = Drones.DroneInfo(drone, null)
            ship.cargoUpdated()
        }

        // Spawn the drones in. This works along similar lines to the weapons.
        // Now and then, a boarding drone or an Ion Intruder, before the rest
        addBoardingDrone(sys, ship, sector, rand)

        // Ships with several drones systems fill each one, with different
        // drones where they can. Some are double or triple ones, as the
        // weapons are (see DroneSizes).
        val dronesList = parseDronesList(elem)
        var droneTriplesLeft = if (epicSector) WeaponSizes.EPIC_MAX_TRIPLES else 0
        for (drones in ship.droneSystems) while (true) {

            val dronePower = drones.drones.sumBy { it?.type?.power ?: 0 }
            val remainingPower = drones.energyLevels - dronePower

            if (remainingPower <= 0)
                break

            // Filled all the drone slots?
            if (drones.drones.all { it != null })
                break

            fun findSuitable(fallback: Boolean): List<DroneBlueprint> = dronesList.filter {
                // Must have enough power left
                if (it.power > remainingPower)
                    return@filter false

                // Can't use all the power with a single drone
                if (drones.energyLevels >= 4 && it.power >= drones.energyLevels)
                    return@filter false

                // If we couldn't find any drones with the last two requirements,
                // try again without them.
                if (fallback)
                    return@filter true

                // Avoid duplicates
                if (ship.droneSystems.any { system -> system.drones.any { info -> info?.type == it } })
                    return@filter false

                // Require combat drones if we don't have proper offensive weapons
                if (!hasHullDamageWeapon || !hasLaserLikeWeapon) {
                    if (it.type != DroneBlueprint.DroneType.COMBAT)
                        return@filter false
                }

                return@filter true
            }

            val suitable = findSuitable(false)
            val drone: DroneBlueprint

            if (suitable.isNotEmpty()) {
                drone = suitable.random(rand)
            } else {
                // If we can't satisfy the last few requirements, try again without them
                val fallback = findSuitable(true)

                if (fallback.isEmpty()) {
                    println("Warning: wasn't able to find any more suitable drones for ship '${ship.name}'")
                    println("  Current drones: " + drones.drones.mapNotNull { it?.type?.name })
                    println("  Drone system power: ${drones.energyLevels}")
                    break
                }

                drone = fallback.random(rand)
            }

            // A double or triple one may need more power than's left, so
            // the drones system's upgraded for it, as far as it can be
            val headroom = drones.blueprint.maxPower - drones.energyLevels
            var sized = DroneSizes.enemyDrone(
                sys.blueprintManager, drone, remainingPower + headroom, ship.type.shape, effectiveSector,
                droneTriplesLeft > 0, rand
            )
            // (an anti-personnel one's a bulwark, which needs a room big
            // enough for it, see AntiPersonnelDrone)
            val fits = sized.type != DroneBlueprint.DroneType.BATTLE ||
                    AntiPersonnelDrone.bulwarkFits(ship, sized.combinedCount)
            if (!fits)
                sized = drone
            if (sized.combinedCount >= 3)
                droneTriplesLeft--
            addDronePower(ship, drones, sized.power - remainingPower)

            drones.drones[drones.drones.indexOfFirst { it == null }] = Drones.DroneInfo(sized, null)
            ship.cargoUpdated()
        }

        ensureDroneParts(ship)

        if (ship.dronesCount == 0 && ship.hacking != null) {
            ship.dronesCount = 5
        }

        // Ships that board a bigger player ship (with more crew) get more
        // crew of their own, so their boarding parties are bigger too.
        // This is done last, as the teleporter may be an optional system.
        val boarding = ShipScaling.boardingMultiplier(sys, ship, sector)
        if (boarding > 1 && ship.teleporter != null && ship.type.boardingStrategy != FriendlyCrewAI.BoardingStrategy.NONE) {
            addBoardingCrew(sys, ship, boarding, rand)
        }

        // The reactor powers all its systems at once: the reactor added with
        // each upgrade doesn't cover the optional systems' starting power
        ship.purchasedReactorPower = max(ship.purchasedReactorPower, ship.mainSystems.sumOf { it.energyLevels })

        return ship
    }

    /**
     * Build one of the size 3 bosses (see ColossalBoss): unlike the other
     * enemy ships, it has the weapons, drones, systems and reactor its
     * blueprint lists, and the most crew it allows, rather than what the
     * sector would give it. It never surrenders or escapes.
     */
    private fun buildBoss(sys: InGameState, spec: EnemyShipSpec, blueprint: ShipBlueprint, rand: Random): Ship {
        val ship = Ship(blueprint, sys, null, spec, null)
        ship.loadDefaultContents()
        for (crewSpec in ship.type.initialCrew) {
            repeat(crewSpec.max ?: crewSpec.amount) {
                val crewBP = sys.lootPool.getCrewOrRandom(rand, crewSpec.race)
                ship.addCrewMember(LivingCrewInfo.generateRandom(crewBP, sys), true)
            }
        }
        ship.crewAI.initialCrewCount = ship.crew.size
        ship.escapeHealth = 0
        ship.surrenderHealth = 0
        // (the Mainframe's drones are already the sizes it wants, singles
        // among them, see tools/boss_ships.py)
        ship.colossal?.takeIf { it.kind.mainframePhase == 0 }?.let { combineBossDrones(sys, ship, it.kind.tier) }
        ship.colossal?.lineUpBoarders()
        ship.colossal?.fightStarted()
        return ship
    }

    /**
     * Give a ship [multiplier] times as many crew as it has, for boarding a
     * bigger player ship. The extra crew are the same races as the existing
     * ones, and they stop once the ship's out of room for them.
     */
    /**
     * Give an enemy [ship] a boarding drone (or, with Advanced Edition, half
     * the time an Ion Intruder), now and then: more often further into the
     * game, from [BOARDING_DRONE_CHANCE_START] to [BOARDING_DRONE_CHANCE_END],
     * if one of its drones systems has the power for it. Vanilla's drone
     * lists have them, but among so many others they hardly ever turn up.
     */
    private fun addBoardingDrone(sys: InGameState, ship: Ship, sector: Int, rand: Random) {
        val progress = WeaponSizes.progress(sector)
        val chance = BOARDING_DRONE_CHANCE_START + (BOARDING_DRONE_CHANCE_END - BOARDING_DRONE_CHANCE_START) * progress
        if (rand.nextFloat() >= chance)
            return

        val names = listOfNotNull("BOARDER", ION_INTRUDER_NAME.takeIf { sys.content.enableAdvancedEdition })
        val drone = sys.blueprintManager.getOrNull(names.random(rand)) as? DroneBlueprint ?: return
        val system = ship.droneSystems.firstOrNull { system ->
            system.drones.any { it == null } &&
                    system.energyLevels - system.drones.sumOf { it?.type?.power ?: 0 } >= drone.power
        } ?: return
        system.drones[system.drones.indexOfFirst { it == null }] = Drones.DroneInfo(drone, null)
        ship.cargoUpdated()
    }

    private fun addBoardingCrew(sys: InGameState, ship: Ship, multiplier: Int, rand: Random) {
        val existing = ship.crew.filterIsInstance<LivingCrew>()
        if (existing.isEmpty())
            return

        // Leave a little room, so crew can still move around
        val room = ship.rooms.sumOf { it.cellCount } - ship.crew.size - 2
        val extra = (existing.size * (multiplier - 1)).coerceAtMost(room)

        for (i in 1..extra) {
            val race = existing.random(rand).info.race
            ship.addCrewMember(LivingCrewInfo.generateRandom(race, sys), true)
        }

        ship.crewAI.initialCrewCount = ship.crew.size
    }

    /**
     * Pick the upper limit for the amount of power given a system can start with.
     *
     * The [effectiveSector] is the sector number with -1 applied for easy (this
     * means that on the first sector of easy, the effective sector number is -1).
     */
    private fun pickSystemPowerLimit(
        effectiveSector: Int, difficulty: Difficulty, system: AbstractSystem, rand: Random
    ): Int {
        // See ShipGenerator::GenerateSystemMaxes
        // Note that the map the data comes from is a libstdc++ red-blue tree,
        // whose datatype is std::pair<int_const,_ShipBlueprint::SystemTemplate>.
        // It may help to define a type putting that after _Rb_tree_node_base.

        val startPower = system.energyLevels

        // In vanilla it's only the flagship and under-construction flagship
        // that lack a maximum power, and the former is not handled by the ship generator.
        // If this is missing, use the system's maximum power.
        var maxPower = system.configuration.spec.aiMaxPower ?: system.blueprint.maxPower
        // A cloaking system that could go as far as vanilla's (three bars)
        // can go as far as the mod lets it (see Cloaking.durationFor), so
        // the enemies' cloaks get longer further in, as the player's can;
        // a ship whose cloaking is held to less stays as it is
        // (and the same for hacking, mind control and the backup battery)
        val vanillaMax = when (system) {
            is Cloaking -> Cloaking.VANILLA_MAX_POWER
            is Hacking -> Hacking.VANILLA_MAX_POWER
            is MindControl -> MindControl.VANILLA_MAX_POWER
            is BackupBattery -> BackupBattery.VANILLA_MAX_POWER
            is Medbay -> Medbay.VANILLA_MAX_POWER
            is Clonebay -> Clonebay.VANILLA_MAX_POWER
            is Oxygen -> Oxygen.VANILLA_MAX_POWER
            is Teleporter -> Teleporter.VANILLA_MAX_POWER
            else -> null
        }
        if (vanillaMax != null && maxPower >= vanillaMax)
            maxPower = system.blueprint.maxPower

        // Lerp between the min and max power
        val numSectors = 8
        val powerRange = maxPower - startPower
        val baseLimit = startPower + (1f * effectiveSector / numSectors * powerRange).toInt()

        // Add the random offset
        val offsetMax = when {
            // First sector on easy is 0, then 1 for all subsequent sectors
            effectiveSector == -1 -> 0
            difficulty == Difficulty.EASY -> 1

            // Normal/hard is 1, rising to 2 on sector 3 (id=2 when zero-indexed)
            effectiveSector < 3 - 1 -> 2
            else -> 2
        }

        val offset = (0..offsetMax).random(rand)

        return min(baseLimit + offset, maxPower)
    }

    private fun getSystemCategory(system: AbstractSystem): SystemCategory {
        return when (system) {
            is Shields, is Engines, is Cloaking -> SystemCategory.DEFENSIVE
            is Weapons, is Drones, is Teleporter, is Artillery -> SystemCategory.OFFENSIVE
            else -> SystemCategory.GENERAL
        }
    }

    private fun parseWeaponsList(shipElem: Element): List<AbstractWeaponBlueprint> {
        val weaponBlueprints = ArrayList<AbstractWeaponBlueprint>()
        val weaponList = shipElem.getChild("weaponList") ?: return emptyList()

        weaponList.getAttributeValue("load")?.let { listName ->
            weaponBlueprints += bp[listName].list().map { it as AbstractWeaponBlueprint }
        }

        for (node in weaponList.children) {
            val name = node.getAttributeValue("name")
            weaponBlueprints += bp[name] as AbstractWeaponBlueprint
        }

        return weaponBlueprints
    }

    private fun parseDronesList(shipElem: Element): List<DroneBlueprint> {
        val droneBlueprints = ArrayList<DroneBlueprint>()
        val droneList = shipElem.getChild("droneList") ?: return emptyList()

        droneList.getAttributeValue("load")?.let { listName ->
            droneBlueprints += bp[listName].list().map { it as DroneBlueprint }
        }

        for (node in droneList.children) {
            val name = node.getAttributeValue("name")
            droneBlueprints += bp[name] as DroneBlueprint
        }

        return droneBlueprints
    }

    /**
     * Give [weapons] (and the ship's reactor) [amount] more power, if it's
     * more than none, for a weapon that needs it (up to the system's most).
     */
    /**
     * A hybrid's control drones (see doc/game/hybrid-ships.md), [names],
     * with some of its anti-personnel drones made into bulwarks (see
     * doc/game/drone-sizes.md): one twin on a long hybrid, a triple and a
     * twin on a wide one, and another twin on an Engi one, each in place of
     * as many single ones as it's worth, if it has a room big enough. The
     * bulwarks come first, so they get the drone slots.
     */
    private fun withBulwarks(ship: Ship, names: List<String>): List<String> {
        val sizes = when (ship.type.shape) {
            ShipShape.LONG -> listOf(2)
            ShipShape.WIDE -> listOf(3, 2)
            else -> emptyList()
        } + if ("_ENGI" in ship.type.name) listOf(2) else emptyList()

        val result = names.toMutableList()
        var bulwarks = 0
        for (size in sizes) {
            if (result.count { it == CONTROL_DRONE } < size || !AntiPersonnelDrone.bulwarkFits(ship, size))
                continue
            repeat(size) { result.remove(CONTROL_DRONE) }
            result.add(bulwarks++, DroneSizes.nameFor(CONTROL_DRONE, size))
        }
        return result
    }

    /**
     * From [LATE_DOORS_FROM] of the way through the game (see
     * WeaponSizes.progress), a ship with room for a doors system always has
     * one, at least at level 2, which locks out boarders and their drones
     * (they have to break the doors down, see Door.isLockedFor), and at
     * level 3 from [LATE_DOORS_LEVEL_3_FROM], as far as the system goes.
     * It's a subsystem, so it takes no reactor power.
     */
    private fun lateDoors(ship: Ship, effectiveSector: Int) {
        val progress = WeaponSizes.progress(effectiveSector)
        if (progress < LATE_DOORS_FROM)
            return
        if (ship.doorsSystem == null) {
            val slot = ship.systemSlots.firstOrNull { it.system.info == Doors.INFO && it.room.system == null } ?: return
            slot.room.setSystem(slot)
        }
        val doors = ship.doorsSystem ?: return
        val level = if (progress >= LATE_DOORS_LEVEL_3_FROM) 3 else 2
        doors.energyLevels = maxOf(doors.energyLevels, level.coerceAtMost(doors.blueprint.maxPower))
    }

    /**
     * Give [drones] (and the ship's reactor) [amount] more power, if it's
     * more than none, for a drone that needs it (up to the system's most).
     */
    private fun addDronePower(ship: Ship, drones: Drones, amount: Int) {
        val added = amount.coerceAtMost(drones.blueprint.maxPower - drones.energyLevels)
        if (added <= 0)
            return
        drones.energyLevels += added
        ship.purchasedReactorPower += added
    }

    /**
     * Make sure [ship] has the drone parts to launch its drones a couple of
     * times: one for each copy of each (see DroneSizes).
     */
    private fun ensureDroneParts(ship: Ship) {
        val copies = ship.droneSystems.sumOf { system -> system.drones.sumOf { it?.type?.combinedCount ?: 0 } }
        if (ship.dronesCount < copies * 2)
            ship.dronesCount = copies * 2
    }

    /**
     * Make a size 3 boss's drones twins and triples, as its weapons are (see
     * DroneSizes.bossTriples): its first ones triples, as many as its [tier]
     * has, and the rest twins, each with the power for it, as far as its
     * drones system can be upgraded (or else the next size down).
     */
    private fun combineBossDrones(sys: InGameState, ship: Ship, tier: Int) {
        var triples = DroneSizes.bossTriples(tier)
        for (drones in ship.droneSystems) {
            for ((index, info) in drones.drones.withIndex()) {
                val single = info?.type ?: continue
                if (single.isCombined)
                    continue
                for (size in if (triples > 0) listOf(3, 2) else listOf(2)) {
                    val sized = DroneSizes.combined(sys.blueprintManager, single, size) ?: continue
                    val used = drones.drones.sumOf { it?.type?.power ?: 0 }
                    val needed = used - single.power + sized.power - drones.energyLevels
                    if (needed > drones.blueprint.maxPower - drones.energyLevels)
                        continue
                    addDronePower(ship, drones, needed)
                    drones.drones[index] = Drones.DroneInfo(sized, null)
                    if (size == 3)
                        triples--
                    break
                }
            }
        }
        ship.cargoUpdated()
        ensureDroneParts(ship)
    }

    private fun addWeaponPower(ship: Ship, weapons: Weapons, amount: Int) {
        val added = amount.coerceAtMost(weapons.blueprint.maxPower - weapons.energyLevels)
        if (added <= 0)
            return
        weapons.energyLevels += added
        ship.purchasedReactorPower += added
    }

    /**
     * Swap the first of [ship]'s single weapons that has a double version
     * (see WeaponSizes), whose weapons system can have the power for it, for
     * that double one.
     */
    private fun makeOneWeaponDouble(sys: InGameState, ship: Ship) {
        for (weapons in ship.weaponSystems) {
            for (index in weapons.hardpointIndices) {
                val single = ship.hardpoints.getOrNull(index)?.weapon?.type ?: continue
                if (single.isCombined)
                    continue
                val double = WeaponSizes.combined(sys.blueprintManager, single, 2) ?: continue
                val used = weapons.hardpointIndices.sumOf { ship.hardpoints.getOrNull(it)?.weapon?.type?.power ?: 0 }
                val needed = used - single.power + double.power - weapons.energyLevels
                if (needed > weapons.blueprint.maxPower - weapons.energyLevels)
                    continue
                addWeaponPower(ship, weapons, needed)
                ship.hardpoints[index].weapon = double.buildInstance(ship)
                return
            }
        }
    }

    private fun getWeaponFlags(weapon: AbstractWeaponBlueprint): Pair<Boolean, Boolean> {
        val doesHullDamage = weapon.damage != 0
        val isLaserStyle = when (weapon) {
            is LaserBlueprint -> true
            is MissileBlueprint -> weapon.shieldPiercing <= 3
            else -> false
        }

        return Pair(doesHullDamage, isLaserStyle)
    }

    // The categories that power is allocated from
    private enum class SystemCategory {
        OFFENSIVE, DEFENSIVE, GENERAL;
    }

    /**
     * A hybrid ship (see doc/game/hybrid-ships.md) to fight in place of [base]:
     * one made from it and another race's ship, never in the first act
     * ([HYBRID_CHANCE_ACT_1], so they only turn up after the blockade, but
     * for the omens' scripted ones) and nearly always in the second
     * ([HYBRID_CHANCE_ACT_2]). A bigger ship (long or wide) is a bigger
     * hybrid even more often in the second act ([BIG_HYBRID_CHANCE_ACT_2]).
     * Only ships that hybrids are made from can be one, so special ships
     * from events, bosses and pirates stay as they are.
     *
     * In a hybrid sector (see [SectorType.hybridRaces]), most of those are
     * one of that sector's pair of races' hybrids, a scout's one of their
     * scouts and a bomber's one of their bombers, and the rest are other
     * hybrids, or now and then (less and less through act 2) the ship
     * itself (see [hybridSectorShip]).
     */
    private fun hybridFor(sys: InGameState, base: LazyShipBlueprint, shape: ShipShape, rand: Random): LazyShipBlueprint? {
        // (the screenshots and videos want the ships they asked for)
        if (XftlDebug.recording)
            return null
        val hybrids = sys.blueprintManager.getOrNull(HYBRIDS_OF + base.name.removeSuffix("_DLC")) ?: return null
        val sectorInfo = sys.currentBeacon?.sector?.info
        sectorInfo?.type?.hybridRaces?.let { (first, second) ->
            val progress = sys.gameMap.actTwoProgress(sectorInfo.columnNumber)
            when (hybridSectorShip(progress, rand.nextFloat())) {
                HybridSectorShip.VANILLA -> return null
                HybridSectorShip.OTHER_HYBRID -> return hybrids.resolve(rand) as? LazyShipBlueprint
                HybridSectorShip.PAIR -> {
                    val bomber = hybrids.list().any { it.name.startsWith(HYBRID_BOMBER_PREFIX) }
                    val prefix = if (bomber) HYBRID_BOMBER_PREFIX else HYBRID_PREFIX
                    val pair = listOf("$prefix${first}_$second", "$prefix${second}_$first")
                        .mapNotNull { sys.blueprintManager.getOrNull(it) as? LazyShipBlueprint }
                    if (pair.isNotEmpty())
                        return pair.random(rand)
                }
            }
        }
        val column = sys.currentBeacon?.sector?.info?.columnNumber ?: return null
        val chance = when {
            sys.gameMap.actOf(column) == 1 -> HYBRID_CHANCE_ACT_1
            shape != ShipShape.NORMAL -> BIG_HYBRID_CHANCE_ACT_2
            else -> HYBRID_CHANCE_ACT_2
        }
        if (rand.nextFloat() >= chance)
            return null
        return hybrids.resolve(rand) as? LazyShipBlueprint
    }

    /**
     * What an enemy ship that can be a hybrid is in a hybrid sector (see
     * [hybridSectorShip]): one of the sector's pair of races' hybrids,
     * another hybrid (made from the ship and any other race's), or the
     * ship itself.
     */
    enum class HybridSectorShip { PAIR, OTHER_HYBRID, VANILLA }

    companion object {
        /**
         * The chance of an enemy ship having a boarding drone or an Ion
         * Intruder (see addBoardingDrone), at the start and end of the game.
         */
        const val BOARDING_DRONE_CHANCE_START = 0.1f
        const val BOARDING_DRONE_CHANCE_END = 0.3f
        private const val ION_INTRUDER_NAME = "BOARDER_ION"

        // The hybrids' control anti-personnel drone, some of which become
        // bulwarks (see withBulwarks)
        private const val CONTROL_DRONE = "BATTLE"

        /**
         * In a hybrid sector, how likely an enemy ship that can be a hybrid
         * is to be one of the sector's pair's hybrids, and to be the ship
         * itself (not a hybrid), at the start of act 2 and at its end: less
         * and less through act 2, as CONCORD takes the rest. The rest are
         * other hybrids. See [hybridSectorShip].
         */
        const val PAIR_HYBRID_CHANCE = 0.75f
        const val VANILLA_CHANCE_START = 0.2f
        const val VANILLA_CHANCE_END = 0.02f

        /**
         * In a hybrid sector [progress] through act 2 (0 to 1, see
         * GameMap.actTwoProgress), the chance of an enemy ship that can be a
         * hybrid being the ship itself.
         */
        fun hybridSectorVanillaChance(progress: Float): Float =
            VANILLA_CHANCE_START + (VANILLA_CHANCE_END - VANILLA_CHANCE_START) * progress.coerceIn(0f, 1f)

        /**
         * What an enemy ship that can be a hybrid is in a hybrid sector
         * [progress] through act 2, for a random [roll] (0 to 1): one of the
         * pair's hybrids ([PAIR_HYBRID_CHANCE]), the ship itself
         * ([hybridSectorVanillaChance]), or another hybrid.
         */
        fun hybridSectorShip(progress: Float, roll: Float): HybridSectorShip = when {
            roll < PAIR_HYBRID_CHANCE -> HybridSectorShip.PAIR
            roll < PAIR_HYBRID_CHANCE + hybridSectorVanillaChance(progress) -> HybridSectorShip.VANILLA
            else -> HybridSectorShip.OTHER_HYBRID
        }

        /**
         * How likely an enemy ship is to be a hybrid made from it, in each
         * act (see [hybridFor]), and the start of the name of each ship's
         * list of hybrids (made by tools/hybrid_ships.py).
         */
        const val HYBRID_CHANCE_ACT_1 = 0f
        const val HYBRID_CHANCE_ACT_2 = 0.85f
        const val BIG_HYBRID_CHANCE_ACT_2 = 0.95f
        const val HYBRIDS_OF = "XFTL_HYBRIDS_OF_"

        /**
         * The start of the hybrid scouts' and bombers' names, which go on with
         * their two races, such as XFTL_HYBRID_ROCK_ZOLTAN (made by
         * tools/hybrid_ships.py).
         */
        const val HYBRID_PREFIX = "XFTL_HYBRID_"
        const val HYBRID_BOMBER_PREFIX = "XFTL_HYBRID_BOMBER_"

        /**
         * The chance (in percent) that a bigger version of a ship gets its
         * cloaking, if it isn't started with: a long one's half the time.
         */
        const val VARIANT_CLOAKING_CHANCE = 50

        /**
         * How far through the game (see WeaponSizes.progress) the ships
         * always have a doors system, locked against boarders (see
         * lateDoors), and from when it's at level 3.
         */
        const val LATE_DOORS_FROM = 0.35f
        const val LATE_DOORS_LEVEL_3_FROM = 0.65f

        fun seedToString(sector: Int, difficulty: Difficulty, seed: Int): String {
            // Base64-encode the seed to make it less painful to type in
            val bytes = ByteBuffer.allocate(6)
            bytes.put(sector.toByte())
            bytes.put(difficulty.ordinal.toByte())
            bytes.putInt(seed)
            return Base64.getEncoder().encodeToString(bytes.array()).trim('=')
        }
    }
}

class EnemyShipSpec(elem: Element, bp: BlueprintManager, ev: EventManager) {
    val name = elem.requireAttributeValue("name")

    val escape: IEvent? by loadEvent(elem, ev, name, "escape")
    val surrender: IEvent? by loadEvent(elem, ev, name, "surrender")
    val destroyed: IEvent? by loadEvent(elem, ev, name, "destroyed")
    val deadCrew: IEvent? by loadEvent(elem, ev, name, "deadCrew")
    val gotaway: IEvent? by loadEvent(elem, ev, name, "gotaway")

    // Two ships (TUTORIAL_PIRATE and IMPOSSIBLE_PIRATE) have 'blueprint' attributes instead. While it's
    // unlikely we'll care about them for a long time (if ever), it's nice to load all the ships without
    // having to carry around a list of exceptions.
    val autoBlueprint = bp[elem.getAttributeValue("blueprint") ?: elem.requireAttributeValue("auto_blueprint")]

    /**
     * The ships to use against player ships bigger than vanilla, instead of
     * several copies of the [autoBlueprint] ships, an XFTL extension
     * (`xftlBigBlueprint`). This is usually a list, which can be empty (or
     * missing) until there are big ships to put in it, so events can already
     * be written for them.
     */
    val bigBlueprint: IBlueprint? = elem.getAttributeValue("xftlBigBlueprint")?.let { bp.getOrNull(it) }

    /**
     * If true, this ship is never a hybrid in its place (see
     * ShipGenerator.hybridFor), even in act 2 or a hybrid sector: for the
     * events' ships that CONCORD hasn't joined. An XFTL extension
     * (`xftlNoHybrid`).
     */
    val noHybrid: Boolean = elem.getAttributeValue("xftlNoHybrid")?.toBoolean() ?: false

    /**
     * If true, this ship fights alone, with no wingmen, and is never a
     * bigger version (see FightPlan), so its rewards come once: for the
     * quests' ships, whose rewards are set by hand. An XFTL extension
     * (`xftlAlone`).
     */
    val alone: Boolean = elem.getAttributeValue("xftlAlone")?.toBoolean() ?: false

    /**
     * The blueprint (or list) to build this ship from, against the [player]'s
     * ship (which is null while the game's still being set up).
     */
    fun blueprintFor(player: ShipBlueprint?): IBlueprint {
        val big = bigBlueprint
        if (player != null && player.shipSize > 1 && big != null && big.list().isNotEmpty())
            return big
        return autoBlueprint
    }

    val escapeTimer: Float = elem.getChild("escape")?.getAttributeValue("timer")?.toFloat() ?: 15f
    val escapeHealth: RandomWithChance? = elem.getChild("escape")?.let(::RandomWithChance)
    val surrenderHealth: RandomWithChance? = elem.getChild("surrender")?.let(::RandomWithChance)

    val crewOverride: ShipCrewOverride? = elem.getChild("crew")?.let { ShipCrewOverride(it, bp) }
    val weaponOverride: ShipBlueprintOverride? = elem.getChild("weaponOverride")?.let { ShipBlueprintOverride(it, bp) }
    val droneOverride: ShipBlueprintOverride? = elem.getChild("droneOverride")?.let { ShipBlueprintOverride(it, bp) }

    companion object {
        private fun loadEvent(root: Element, ev: EventManager, name: String, evName: String): Lazy<IEvent?> {
            // TODO is there a default destroyed/deadCrew/gotaway event we should use if one hasn't been set?
            val elem = root.getChild(evName) ?: return lazyOf(null)
            return ev.loadEmbeddedEvent(elem, name)
        }
    }
}

class ShipCrewOverride(elem: Element, bp: BlueprintManager) {
    val specifiers: List<Spec> = elem.getChildren("crewMember").map { Spec(it) }

    class Spec(elem: Element) {
        // GHOST_SHIP doesn't set this, so we can't require it
        val race: String = elem.getAttributeValue("type") ?: "human"

        val prop: Float = elem.getAttributeValue("prop")?.toFloat() ?: 0f
        val amount: Int = elem.getAttributeValue("amount")?.toInt() ?: 0
    }
}

/**
 * For <weaponOverride> and <droneOverride> in <ship>.
 */
class ShipBlueprintOverride(elem: Element, bp: BlueprintManager) {
    val blueprints: List<IBlueprint>
    val count: Int

    init {
        blueprints = ArrayList()

        count = elem.getAttributeValue("count").toInt()
        for (nameElem in elem.children) {
            require(nameElem.name == "name")
            blueprints += bp[nameElem.textTrim]
        }
    }

    fun select(rand: Random): List<Blueprint> {
        // Pick the first (count) random blueprints.
        // While we try to pick distinct blueprints, if the same blueprint
        // is resolved twice through separate lists we won't bother to stop that.
        return blueprints.shuffled(rand).subList(0, count).map { it.resolve(rand) }
    }
}
