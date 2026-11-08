package xyz.znix.xftl.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Check how a sector's stores depend on what sort of sector it is (see
 * [StoreProfile] and doc/game/stores.md).
 */
class StoreProfileTest {
    @Test
    fun dangerousSectorsHaveFewerStores() {
        assertEquals(4, StoreProfile.CIVILIAN.keptStores(4))
        assertEquals(3, StoreProfile.HOSTILE.keptStores(4))
        assertEquals(2, StoreProfile.NEBULA.keptStores(4))
        // At most one in a whole hazardous sector
        assertEquals(1, StoreProfile.HAZARD.keptStores(6))
        assertEquals(0, StoreProfile.HAZARD.keptStores(0))
    }

    @Test
    fun dangerousSectorsStoresHaveLessButRarer() {
        val profiles = StoreProfile.entries
        // Fewer things, and less fuel and so on, the more dangerous it is
        assertEquals(profiles.map { it.sections.last * it.itemsPerSection.last }.sortedDescending(),
            profiles.map { it.sections.last * it.itemsPerSection.last })
        assertEquals(profiles.map { it.resources }.sortedDescending(), profiles.map { it.resources })
        // But more double weapons (though the odd one even in a civilian
        // store), and better crew
        assertTrue(StoreProfile.CIVILIAN.doubleChance(0f) > 0f)
        assertTrue(profiles.zipWithNext().all { (a, b) -> a.doubleChance(0.5f) < b.doubleChance(0.5f) })
        assertTrue(profiles.zipWithNext().all { (a, b) ->
            a.crewMaxSkills * 2 + a.crewPartialSkills < b.crewMaxSkills * 2 + b.crewPartialSkills
        })
        // and more triple weapons, rare outside the most dangerous ones
        assertTrue(profiles.zipWithNext().all { (a, b) -> a.tripleChance < b.tripleChance })
        assertTrue(StoreProfile.HOSTILE.tripleChance < 0.05f)
    }
}
