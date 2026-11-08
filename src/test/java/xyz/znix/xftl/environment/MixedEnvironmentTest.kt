package xyz.znix.xftl.environment

import org.jdom2.Element
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.testkit.InGameTest

/**
 * Check a beacon with two environments at once has both, and keeps them
 * (and their state) through saving and loading.
 */
class MixedEnvironmentTest {
    @Test
    fun mixedEnvironmentSurvivesSaving() {
        val kit = InGameTest()
        val beacon = kit.state.currentBeacon
        beacon.sectorHazards = listOf(Beacon.EnvironmentType.PULSAR, Beacon.EnvironmentType.SUN)
        beacon.clearEnvironment()

        assertTrue(beacon.isExtremeHazard)
        val env = beacon.getEnvironment(kit.state)
        assertTrue(env is MixedEnvironment)
        assertEquals(listOf(Beacon.EnvironmentType.PULSAR, Beacon.EnvironmentType.SUN), env.parts.map { it.type })

        // Run their timers a while, so there's state to save
        env.update(3f)
        val saved = save(beacon)

        val refs = RefLoader()
        val loaded = Beacon.loadFromXML(saved, refs, kit.state)
        refs.switchToResolveMode()
        val loadedEnv = loaded.getEnvironment(kit.state)
        assertTrue(loadedEnv is MixedEnvironment)
        assertEquals(listOf(Beacon.EnvironmentType.PULSAR, Beacon.EnvironmentType.SUN), loadedEnv.parts.map { it.type })

        // Both environments' state comes back as it was
        val output = XMLOutputter(Format.getCompactFormat())
        assertEquals(output.outputString(saved), output.outputString(save(loaded)))
    }

    @Test
    fun threeEnvironmentsSurviveSaving() {
        val kit = InGameTest()
        val beacon = kit.state.currentBeacon
        val types = listOf(Beacon.EnvironmentType.RADIATION, Beacon.EnvironmentType.SUN, Beacon.EnvironmentType.PULSAR)
        beacon.sectorHazards = types
        beacon.clearEnvironment()

        assertTrue(beacon.isCatastrophicHazard)
        val env = beacon.getEnvironment(kit.state)
        assertEquals(types, env.parts.map { it.type })
        for (part in env.parts) {
            assertEquals(2, part.intensity)
            assertEquals(2f, part.hazardRate)
        }

        env.update(3f)
        val saved = save(beacon)
        val refs = RefLoader()
        val loaded = Beacon.loadFromXML(saved, refs, kit.state)
        refs.switchToResolveMode()
        assertEquals(types, loaded.getEnvironment(kit.state).parts.map { it.type })

        val output = XMLOutputter(Format.getCompactFormat())
        assertEquals(output.outputString(saved), output.outputString(save(loaded)))
    }

    @Test
    fun allFourSurviveSaving() {
        val kit = InGameTest()
        val beacon = kit.state.currentBeacon
        val types = listOf(
            Beacon.EnvironmentType.ION_STORM, Beacon.EnvironmentType.ASTEROID,
            Beacon.EnvironmentType.SUN, Beacon.EnvironmentType.PULSAR
        )
        beacon.sectorHazards = types
        beacon.clearEnvironment()

        assertTrue(beacon.isApocalypticHazard)
        val env = beacon.getEnvironment(kit.state)
        assertEquals(types, env.parts.map { it.type })

        env.update(3f)
        val saved = save(beacon)
        val refs = RefLoader()
        val loaded = Beacon.loadFromXML(saved, refs, kit.state)
        refs.switchToResolveMode()
        assertEquals(types, loaded.getEnvironment(kit.state).parts.map { it.type })

        val output = XMLOutputter(Format.getCompactFormat())
        assertEquals(output.outputString(saved), output.outputString(save(loaded)))
    }

    @Test
    fun mixedEnvironmentsAreStronger() {
        val kit = InGameTest()
        val beacon = kit.state.currentBeacon

        // A single hazard in the first act is as in vanilla
        beacon.sectorHazards = listOf(Beacon.EnvironmentType.SUN)
        beacon.clearEnvironment()
        val single = beacon.getEnvironment(kit.state)
        assertEquals(1, single.intensity)
        assertEquals(1f, single.hazardRate)

        // Two at once are as strong as in the second act, and twice as quick
        beacon.sectorHazards = listOf(Beacon.EnvironmentType.SUN, Beacon.EnvironmentType.PULSAR)
        beacon.clearEnvironment()
        for (part in beacon.getEnvironment(kit.state).parts) {
            assertEquals(2, part.intensity)
            assertEquals(2f, part.hazardRate)
        }
    }

    private fun save(beacon: Beacon): Element {
        val elem = Element("beacon")
        val refs = ObjectRefs()
        refs.register(beacon, "beacon")
        beacon.saveToXML(elem, refs)
        return elem
    }
}
