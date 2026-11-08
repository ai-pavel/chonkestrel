package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.SystemInfo
import xyz.znix.xftl.Translator
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader

class Sensors(blueprint: SystemBlueprint) : SubSystem(blueprint) {
    override val sortingType = SortingType.SENSORS

    /**
     * True if the sensors are in a nebula (of any kind), which stops them
     * working.
     */
    val isInNebula: Boolean get() = ship.sys.currentBeacon.isNebula

    private val blocked: Boolean get() = isInNebula || isHackActive

    override val blockedByEnvironment: Boolean get() = isInNebula

    // The icon is blue in a nebula, as in vanilla, unless it's broken
    override val iconColourName: String
        get() = when {
            isInNebula && damagedEnergyLevels < energyLevels -> "blue"
            else -> super.iconColourName
        }
    val providesPlayerVision: Boolean get() = !blocked && effectivePower >= 1
    val providesEnemyVision: Boolean get() = !blocked && effectivePower >= 2
    val showsEnemyWeaponCharge: Boolean get() = !blocked && effectivePower >= 3
    val showsEnemyPowerLevels: Boolean get() = !blocked && effectivePower >= 4

    // Nothing to serialise
    override fun saveSystem(elem: Element, refs: ObjectRefs) = Unit
    override fun loadSystem(elem: Element, refs: RefLoader) = Unit

    companion object {
        val INFO: SystemInfo = SensorInfo
    }
}

private object SensorInfo : SystemInfo("sensors") {
    override val canBeManned: Boolean get() = true
    override val isSubSystem: Boolean get() = true

    override fun create(blueprint: SystemBlueprint) = Sensors(blueprint)

    override fun getLevelName(level: Int, translator: Translator): String {
        return when (level) {
            0 -> translator["sensor_1"]
            1 -> translator["sensor_2"]
            2 -> translator["sensor_3"]
            3 -> translator["sensor_4"]
            else -> "INVALID LEVEL ${level + 1}"
        }
    }
}
