package xyz.znix.xftl.augments

import org.jdom2.Element
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.MedbayHealing

/**
 * The base augment class, which is suitable for augments which are
 * implemented as part of another system (for example, reconstructive
 * teleport is implemented by the teleporter).
 *
 * In general, it's probably a good idea to implement augments here
 * to show likely pain points for modding.
 */
open class AugmentBlueprint(elem: Element) : Blueprint(elem) {
    override val cost: Int = elem.getChildTextTrim("cost").toInt()
    val stackable: Boolean = elem.getChildTextTrim("stackable")!!.toBoolean()
    val value: Float = elem.getChildTextTrim("value")?.toFloat() ?: 1f

    open fun update(ship: Ship, dt: Float, totalValue: Float) {}

    open fun onJump(ship: Ship) {}

    /**
     * Called when a ship which comes with this augment is created.
     */
    open fun onShipSpawn(ship: Ship) {}

    companion object {
        const val AUTOMATED_RELOADERS: String = "AUTO_COOLDOWN"
        const val LONG_RANGE_SCANNERS: String = "ADV_SCANNERS"
        const val RECONSTRUCTIVE_TELEPORT: String = "TELEPORT_HEAL"
        const val OXYGEN_MASKS: String = "O2_MASKS"
        const val BACKUP_DNA: String = "BACKUP_DNA"
        const val BATTERY_CHARGER: String = "BATTERY_BOOSTER"
        const val SHIELD_CHARGE_BOOSTER: String = "SHIELD_RECHARGE"
        const val STEALTH_WEAPONS: String = "CLOAK_FIRE"

        /**
         * The Zoltan Shield Bypass: the ship's teleporter and mind control
         * work through an enemy's super shield (see Teleporter and MindControl).
         */
        const val ZOLTAN_BYPASS: String = "ZOLTAN_BYPASS"
        const val DEFENSE_SCRAMBLER: String = "DEFENSE_SCRAMBLER"
        const val FIRE_SUPPRESSION: String = "FIRE_EXTINGUISHERS"
        const val SCRAP_RECOVERY_ARM: String = "SCRAP_COLLECTOR"
        const val REPAIR_ARM: String = "REPAIR_ARM"
        const val ADV_FTL_NAVIGATION: String = "FTL_JUMPER"
        const val FTL_RECHARGE_BOOSTER: String = "FTL_BOOSTER"
        const val FTL_JAMMER: String = "FTL_JAMMER"
        const val DISTRACTION_BUOYS: String = "FLEET_DISTRACTION"
        const val EXPLOSIVE_REPLICATOR: String = "EXPLOSIVE_REPLICATOR"
        const val TITANIUM_SYSTEM_CASING: String = "SYSTEM_CASING"
        const val DRONE_RECOVERY_ARM: String = "DRONE_RECOVERY"
        const val DRONE_REACTOR_BOOSTER: String = "DRONE_SPEED"
        const val HACKING_STUN: String = "HACKING_STUN"
        const val LIFEFORM_SCANNER: String = "LIFE_SCANNER"
        const val MANTIS_PHEROMONES: String = "CREW_STIMS"
        const val CRYSTAL_VENGEANCE: String = "CRYSTAL_SHARDS"
        const val ROCK_PLATING: String = "ROCK_ARMOR"
        const val SLUG_REPAIR_GEL: String = "SLUG_GEL"
        const val REVERSE_ION_FIELD: String = "ION_ARMOR"
        const val DAMAGED_STASIS_POD: String = "STASIS_POD"

        // The bosses' own augments (see doc/game/bosses.md)
        /** A share of the asteroids that hit the ship repair it instead. */
        const val HIVE_CORE: String = "XFTL_HIVE_CORE"

        /** Rebel automated ships in act 2 charge their weapons slower. */
        const val COMMAND_CODES: String = "XFTL_COMMAND_CODES"

        /** The crew can't be mind controlled for the start of a fight. */
        const val RELAY_SHARD: String = "XFTL_RELAY_SHARD"

        /** A destroyed enemy ship's drones are salvaged as drone parts. */
        const val OSSUARY_SCHEMATICS: String = "XFTL_OSSUARY_SCHEMATICS"
    }
}

/**
 * Engi Med-bot Dispersal: while the medbay's powered, the crew outside it
 * are healed too, at a quarter of its speed.
 */
class AugEngiMedbots(elem: Element) : AugmentBlueprint(elem) {
    override fun update(ship: Ship, dt: Float, totalValue: Float) {
        super.update(ship, dt, totalValue)

        val medbay = ship.medbay ?: return
        if (medbay.powerSelected == 0)
            return

        val healing = dt * 6.4f * totalValue

        for (crew in ship.friendlyCrew) {
            if (crew !is LivingCrew)
                continue

            // Medbots don't add extra healing in the medbay
            if (crew.room == medbay.room)
                continue

            // Nor revive dying crewmembers
            if (crew.currentAction == AbstractCrew.Action.DYING)
                continue

            // Show it, as the medbay does, while there's healing to do
            if (crew.health < crew.maxHealth)
                crew.showHealing(false)

            crew.dealDamage(MedbayHealing(-healing))
        }
    }

    companion object {
        const val NAME = "NANO_MEDBAY"
    }
}

class AugPreigniter(elem: Element) : AugmentBlueprint(elem) {
    override fun onJump(ship: Ship) {
        super.onJump(ship)

        for (hp in ship.hardpoints) {
            val weapon = hp.weapon ?: continue

            // We don't charge up unpowered weapons
            if (!weapon.isPowered)
                continue

            weapon.timeCharged = weapon.type.chargeTime
        }
    }

    companion object {
        const val NAME: String = "WEAPON_PREIGNITE"
    }
}

class AugZoltanShield(elem: Element) : AugmentBlueprint(elem) {
    override fun onJump(ship: Ship) {
        super.onJump(ship)
        chargeShield(ship)
    }

    // This is required so that enemy ships start with a super shield.
    override fun onShipSpawn(ship: Ship) {
        super.onShipSpawn(ship)
        chargeShield(ship)
    }

    /**
     * Fully charge the ship's super-shield. Ships can have several Zoltan
     * shields (mods can make it stackable), in which case they add together,
     * which [Ship.maxSuperShield] accounts for.
     */
    private fun chargeShield(ship: Ship) {
        val count = ship.zoltanShieldCount.coerceAtLeast(1)
        ship.superShield = SHIELD_PER_AUGMENT * count
    }

    companion object {
        const val NAME: String = "ENERGY_SHIELD"
        const val SHIELD_PER_AUGMENT = 5

        /**
         * The most Zoltan shields whose super-shields stack, any more
         * don't add anything (and wouldn't fit in the UI).
         */
        const val MAX_STACKED = 6
    }
}
