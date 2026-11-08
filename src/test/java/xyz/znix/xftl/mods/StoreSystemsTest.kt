package xyz.znix.xftl.mods

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.systems.SystemBlueprint
import java.io.File

/**
 * With the Chonkestrel mod, the stores never sell piloting, the oxygen or
 * the doors (their rarity is 0, see StoreData.generateSystems), and the
 * engines cost 60 scrap.
 */
class StoreSystemsTest {
    private val modded = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(File("mods/chonkestrel").toPath())))
    private val blueprints = BlueprintManager(modded, true)

    private fun system(name: String) = blueprints[name] as SystemBlueprint

    @Test
    fun pilotingOxygenAndDoorsArentSold() {
        assertEquals(0, system("pilot").rarity)
        assertEquals(0, system("oxygen").rarity)
        assertEquals(0, system("doors").rarity)
        assertEquals(1, system("shields").rarity)
        assertEquals(60, system("engines").cost)
    }
}
