package xyz.znix.xftl.sector

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest

class EventsTest {
    @Test
    fun testAddCrewName() {
        val kit = InGameTest(
            extraEvents = listOf(CREW_NAME_XML),
        )
        val events = kit.state.eventManager

        assertEquals("test_localised", events["crew_localised"].resolve().addedCrew[0].name?.getKeyForTesting())
        assertEquals("Test literal", events["crew_literal"].resolve().addedCrew[0].name?.getLiteralForTesting())
        assertEquals(null, events["crew_random"].resolve().addedCrew[0].name)
    }
}

@Language("XML")
private val CREW_NAME_XML = """
    <?xml version="1.0" encoding="UTF-8" ?>
    <FTL>
        <event name="crew_localised">
            <crewMember amount="1" id="test_localised"/>
        </event>
        <event name="crew_literal">
            <crewMember amount="1">Test literal</crewMember>
        </event>
        <event name="crew_random">
            <crewMember amount="1"/>
        </event>
    </FTL>
""".trimIndent()
