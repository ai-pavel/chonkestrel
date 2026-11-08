package xyz.znix.xftl.ui

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.rendering.WindowRenderer

/**
 * Check SpecDeserialiser can leave widgets out, closing up the space they leave.
 */
class RemoveWidgetsTest {
    private val provider = object : UIProvider {
        override fun getFont(name: String): SILFontLoader = error("Not used")
        override fun getImg(path: String): Image = error("Not used")
        override fun translate(key: String): String? = null
        override fun getDebugOutlineColour(widget: Widget): Colour? = null
        override fun getWindowRenderer(): WindowRenderer = error("Not used")
    }

    private fun load(without: Set<String>) =
        SpecDeserialiser(provider).load(XML.byteInputStream(), without).mainWidget

    @Test
    fun testFreeContainer() {
        val all = load(emptySet())
        assertEquals(74, all.byId["c"]!!.position.y)

        // The widgets below a removed one move up into its place
        val ui = load(setOf("b"))
        assertFalse(ui.byId.containsKey("b"))
        assertEquals(0, ui.byId["a"]!!.position.y)
        assertEquals(37, ui.byId["c"]!!.position.y)
        assertEquals(74, ui.byId["d"]!!.position.y)
    }

    @Test
    fun testRemoveTwo() {
        // Removing two in a row moves the rest up by both
        val ui = load(setOf("a", "b"))
        assertEquals(0, ui.byId["c"]!!.position.y)
        assertEquals(37, ui.byId["d"]!!.position.y)
    }

    @Test
    fun testBox() {
        assertEquals(30, load(emptySet()).byId["z"]!!.position.x)

        // Boxes close up the space by themselves
        val ui = load(setOf("y"))
        assertFalse(ui.byId.containsKey("y"))
        assertEquals(10, ui.byId["z"]!!.position.x)
    }

    companion object {
        @Language("XML")
        private val XML = """
<ui>
    <widget type="free" w="200" h="200">
        <widget type="free" pTop="0" w="10" h="10" id="a"/>
        <widget type="free" pTop="37" w="10" h="10" id="b"/>
        <widget type="free" pTop="74" w="10" h="10" id="c"/>
        <widget type="free" pTop="111" w="10" h="10" id="d"/>

        <widget type="hbox" pTop="150" pLeft="0">
            <widget type="free" w="10" h="10" id="x"/>
            <widget type="free" w="20" h="10" id="y"/>
            <widget type="free" w="30" h="10" id="z"/>
        </widget>
    </widget>
</ui>
"""
    }
}
