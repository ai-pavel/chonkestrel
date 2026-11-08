package xyz.znix.xftl.mods

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.systems.BackupBattery
import xyz.znix.xftl.systems.Clonebay
import xyz.znix.xftl.systems.Medbay
import xyz.znix.xftl.systems.Oxygen
import xyz.znix.xftl.systems.Teleporter
import xyz.znix.xftl.systems.Cloaking
import xyz.znix.xftl.systems.Hacking
import xyz.znix.xftl.systems.MindControl
import xyz.znix.xftl.systems.SystemBlueprint
import java.io.File

/**
 * With the Chonkestrel mod, the cloaking, hacking and mind control systems
 * go to six bars, and the backup battery to four, each bar past vanilla's
 * adding less and less.
 */
class SystemLevelsTest {
    private val modded = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(File("mods/chonkestrel").toPath())))
    private val blueprints = BlueprintManager(modded, true)

    private fun system(name: String) = blueprints[name] as SystemBlueprint

    @Test
    fun testMaxPower() {
        assertEquals(6, system("cloaking").maxPower)
        assertEquals(6, system("hacking").maxPower)
        assertEquals(6, system("mind").maxPower)
        assertEquals(4, system("battery").maxPower)
        for (name in listOf("medbay", "clonebay", "oxygen", "teleporter"))
            assertEquals(name, 6, system(name).maxPower)
    }

    @Test
    fun testEachBarPastVanillaAddsLess() {
        assertEquals(listOf(5f, 10f, 15f, 17f, 19f, 20f), (1..6).map { Cloaking.durationFor(it) })
        assertEquals(listOf(4f, 7f, 10f, 12f, 14f, 15f), (1..6).map { Hacking.durationFor(it) })
        assertEquals(listOf(14f, 20f, 28f, 32f, 35f, 37f), (1..6).map { MindControl.durationFor(it) })
        assertEquals(listOf(2, 4, 6, 7), (1..4).map { BackupBattery.powerFor(it) })
        assertEquals(listOf(1f, 1.5f, 3f, 3.75f, 4.25f, 4.5f), (0..5).map { Medbay.speedForLevel(it) })
        assertEquals(listOf(12f, 9f, 7f, 6f, 5f, 4.5f), Clonebay.CLONE_DURATIONS)
        assertEquals(listOf(8, 16, 25, 30, 34, 37), Clonebay.PASSIVE_HEALING)
        // The refill rates, less the drain: vanilla's 1, 4 and 7, then on
        assertEquals(listOf(1f, 4f, 7f, 8.5f, 9.5f, 10f), (1..6).map { Oxygen.refillMultiplier(it) - 1f })
        assertEquals(listOf(20, 15, 10, 8, 7, 6), (1..6).map { Teleporter.cooldownTime(it) })
    }
}
