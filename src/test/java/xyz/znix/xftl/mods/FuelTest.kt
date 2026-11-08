package xyz.znix.xftl.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.Translator
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.game.Resource
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.EventManager
import java.io.File

/**
 * With the Chonkestrel mod, the events that sell fuel sell as many jumps'
 * worth to a bigger ship (twice as much fuel, for twice the scrap, for the
 * Chonkestrel), and say so.
 */
class FuelTest {
    private val modded = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(MOD_DIR.toPath())))
    private val blueprints = BlueprintManager(modded, true)
    private val translator = Translator(modded, "en")
    private val events = EventManager(modded, translator, blueprints)

    @Test
    fun theFuelStationsScaleWithTheShip() {
        for ((name, amounts) in mapOf("REFUEL_STATION" to listOf(6, 3, 1), "FUEL_SELLER_DISTRESS" to listOf(5, 2))) {
            val event = events[name] as Event
            val sales = event.choices.map { it.event }.filterIsInstance<Event>()
                .filter { (it.itemsModify[Resource.FUEL]?.first ?: 0) > 0 }
            assertEquals(name, amounts, sales.map { it.itemsModify.getValue(Resource.FUEL).first })
            assertTrue(name, sales.all { it.scaleItemsBySize })
        }
        assertEquals("Buy 12 fuel for 24 scrap.", translator["event_REFUEL_STATION_c1_choice"])
        assertEquals("Buy 10 fuel for 40 scrap.", translator["event_FUEL_SELLER_DISTRESS_c2_choice"])
    }

    companion object {
        private val MOD_DIR = File("mods/chonkestrel")
    }
}
