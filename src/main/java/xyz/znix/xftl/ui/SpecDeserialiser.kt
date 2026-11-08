package xyz.znix.xftl.ui

import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import xyz.znix.xftl.Constants
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.requireAttributeValue
import java.io.InputStream

/**
 * Loads an XML-based widget tree, and deserialises it into a working UI.
 */
class SpecDeserialiser(private val provider: UIProvider) {
    /**
     * Load the UI with the given name. The widgets with the IDs in [without]
     * (and their children) are left out, see [removeWidgets].
     */
    fun load(name: String, without: Set<String> = emptySet()): LoadedUI {
        val path = "assets/ui/$name.xml"
        return javaClass.classLoader.getResourceAsStream(path).use { load(it, without) }
    }

    fun load(reader: InputStream, without: Set<String> = emptySet()): LoadedUI {
        val builder = SAXBuilder()
        builder.expandEntities = false
        return load(builder.build(reader), without)
    }

    fun load(doc: Document, without: Set<String> = emptySet()): LoadedUI {
        val root = doc.rootElement
        if (root.name != "ui")
            throw IllegalUISpecException("Invalid root element name")

        val styleElem = root.getChild("style")
        val styleSheet = styleElem?.let { StyleSheet(styleElem) }
        styleSheet?.injectAttributes(root)

        removeWidgets(root, without)

        val widgets = loadWidgets(root)
        if (widgets.isEmpty())
            throw IllegalUISpecException("No root widget!")

        for (widget in widgets) {
            widget.init(null)
        }

        val containers = widgets.map { WidgetContainer(it) }

        for (container in containers) {
            container.updateLayout()
        }

        return LoadedUI(containers.first(), emptyMap())
    }

    /**
     * Remove the widgets with the given IDs, before the UI's laid out, so
     * boxes close up the space they leave.
     *
     * Widgets positioned from the top of a free container (with pTop) don't
     * move up by themselves, so the ones below a removed widget are moved
     * up to close the gap: by the distance from it to the next one down.
     */
    private fun removeWidgets(root: Element, ids: Set<String>) {
        if (ids.isEmpty())
            return

        val toRemove = root.descendants.filterIsInstance<Element>()
            .filter { it.name == "widget" && it.getAttributeValue("id") in ids }
            .toList()

        for (elem in toRemove) {
            val parent = elem.parentElement
            val top = elem.getAttributeValue("pTop")?.toIntOrNull()

            if (top != null && parent.getAttributeValue("type") == "free") {
                val below = parent.getChildren("widget")
                    .filter { it !== elem }
                    .filter { (it.getAttributeValue("pTop")?.toIntOrNull() ?: Int.MIN_VALUE) > top }
                val gap = below.minOfOrNull { it.getAttributeValue("pTop").toInt() }?.minus(top) ?: 0

                for (sibling in below) {
                    sibling.setAttribute("pTop", (sibling.getAttributeValue("pTop").toInt() - gap).toString())
                }
            }

            parent.removeContent(elem)
        }
    }

    private fun loadWidgets(parent: Element): List<Widget> {
        return parent.getChildren("widget").map { loadWidget(it) }
    }

    private fun loadWidget(elem: Element): Widget {
        val widget = when (val type = elem.requireAttributeValue("type")) {
            "label" -> Label.fromXML(provider, elem)
            "vbox" -> BoxContainer.fromXML(provider, elem, true)
            "hbox" -> BoxContainer.fromXML(provider, elem, false)
            "free" -> FreeContainer.fromXML(provider, elem)
            "image" -> ImageView.fromXML(provider, elem)
            "button" -> UIKitButton.fromXML(provider, elem)
            "image-button" -> ImageButton.fromXML(provider, elem)
            "window" -> StyledWindowView.fromXML(provider, elem)
            "slider" -> Slider.fromXML(provider, elem)
            else -> throw IllegalUISpecException("Unknown widget type '$type'")
        }

        widget.children += loadWidgets(elem)

        return widget
    }

    class LoadedUI(val mainWidget: WidgetContainer, val extraRoots: Map<String, WidgetContainer>)

    companion object {
        fun parseColour(elem: Element, attrName: String): Colour {
            return parseColour(elem.requireAttributeValue(attrName))
        }

        fun parseColour(elem: Element, attrName: String, defaultColour: Colour): Colour {
            val value = elem.getAttributeValue(attrName) ?: return defaultColour
            return parseColour(value)
        }

        fun parseColour(value: String): Colour {
            // TODO parse CSS hex codes

            return when (value) {
                "text-dark" -> Constants.JUMP_DISABLED_TEXT
                "glow-white" -> Constants.SECTOR_CUTOUT_TEXT
                else -> throw IllegalUISpecException("Unknown colour name '$value'")
            }
        }
    }
}

class IllegalUISpecException(message: String) : Exception(message)
