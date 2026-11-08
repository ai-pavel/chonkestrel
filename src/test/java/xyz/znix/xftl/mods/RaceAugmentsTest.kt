package xyz.znix.xftl.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.Translator
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.game.LootPool
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.sector.EventManager
import xyz.znix.xftl.sector.GameMap
import java.io.File
import kotlin.random.Random

/**
 * With the Chonkestrel mod, the race augments and the Titanium System
 * Casing, which vanilla never sells, can turn up anywhere, as the rarest
 * things, and are much commoner in their race's sectors (and the hybrid
 * sectors of that race).
 */
class RaceAugmentsTest {
    private val modded = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(File("mods/chonkestrel").toPath())))
    private val blueprints = BlueprintManager(modded, true)
    private val events = EventManager(modded, Translator(modded, "en"), blueprints)
    private val map = GameMap(modded, events, true, Random(0))

    // How many times the augment is in the sector's loot pool: 6 - rarity
    private fun copies(sector: String, augment: String): Int {
        val type = map.sectorType(sector) ?: error("No sector $sector")
        return LootPool(blueprints, type).pool.count { it.name == augment }
    }

    @Test
    fun testRaceAugmentsAreSoldEverywhereButMostlyInTheirSectors() {
        val augments = listOf(
            "CREW_STIMS", "CRYSTAL_SHARDS", "DRONE_SPEED", "ENERGY_SHIELD",
            "NANO_MEDBAY", "ROCK_ARMOR", "SLUG_GEL", "SYSTEM_CASING"
        )
        // Rarity 5 elsewhere
        for (augment in augments)
            assertEquals(augment, 1, copies("STANDARD_SPACE", augment))

        assertEquals(4, copies("ROCK_SECTOR", "ROCK_ARMOR"))
        assertEquals(2, copies("ROCK_SECTOR", "CRYSTAL_SHARDS"))
        assertEquals(4, copies("CRYSTAL_HOME", "CRYSTAL_SHARDS"))
        assertEquals(4, copies("MANTIS_HOME", "CREW_STIMS"))
        assertEquals(4, copies("SLUG_SECTOR", "SLUG_GEL"))
        assertEquals(4, copies("ENGI_SECTOR", "NANO_MEDBAY"))
        assertEquals(3, copies("ZOLTAN_SECTOR", "ENERGY_SHIELD"))
        assertEquals(3, copies("CIVILIAN_SECTOR", "SYSTEM_CASING"))

        // A hybrid sector has both races'
        assertEquals(4, copies("XFTL_HYBRID_ROCK_ZOLTAN", "ROCK_ARMOR"))
        assertEquals(3, copies("XFTL_HYBRID_ROCK_ZOLTAN", "ENERGY_SHIELD"))

        // The quest's stasis pod still isn't sold
        assertTrue(copies("STANDARD_SPACE", "STASIS_POD") == 0)
    }
}
