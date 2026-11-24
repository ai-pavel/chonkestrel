package xyz.znix.xftl.mods

import org.jdom2.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.Language
import xyz.znix.xftl.Translator
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.modding.FileSource
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import java.io.File

/**
 * The Chonkestrel mod's text is all looked up by key, so it can be
 * translated (see doc/tools/translations.md and tools/extract_mod_text.py):
 * its data has no text written into it, every key it refers to has English,
 * and every language has every text (in English, if it hasn't got its own).
 */
class ModTextTest {
    private val vanilla = VanillaDatafile.createWithDefaultPath()
    private val modded = Datafile(vanilla, listOf(SlipstreamDirectoryMod(MOD_DIR.toPath())))
    private val english = Translator(modded, "en")

    // The mod's data files (not its text files), parsed as they're written
    private val dataFiles: Map<String, Element> = File(MOD_DIR, "data").listFiles()!!
        .filter { it.name.endsWith(".xml.append") && !it.name.startsWith("text_") && !it.name.startsWith("text-") }
        .sortedBy { it.name }
        .associate { file -> file.name to file.inputStream().use { FileSource.parseModdedXML(it, file.name, true) }.rootElement }

    /** The elements in the mod's data whose text is shown: (file, element) for each. */
    private fun shownText(): List<Pair<String, Element>> {
        val result = ArrayList<Pair<String, Element>>()
        for ((name, root) in dataFiles) {
            for (elem in root.descendants.filterIsInstance<Element>()) {
                val parent = elem.parentElement?.name ?: continue
                val shown = when {
                    elem.name == "text" -> true
                    parent.endsWith("Blueprint") -> elem.name in BLUEPRINT_FIELDS
                    parent == "achievement" -> elem.name == "name" || elem.name == "desc"
                    parent == "nameList" -> elem.name == "name"
                    else -> false
                }
                if (shown)
                    result += name to elem
            }
        }
        return result
    }

    @Test
    fun noTextIsWrittenIntoTheData() {
        val inline = shownText().filter { (_, elem) ->
            elem.textTrim.isNotEmpty() && elem.getAttribute("id") == null && elem.getAttribute("load") == null
        }
        assertEquals(
            "Text written into the mod's data (run tools/extract_mod_text.py)",
            emptyList<String>(), inline.map { (file, elem) -> "$file: <${elem.name}>${elem.textTrim.take(40)}" }
        )
    }

    @Test
    fun everyKeyHasEnglish() {
        val keys = shownText().flatMap { (file, elem) ->
            listOfNotNull(elem.getAttributeValue("id"), elem.getAttributeValue("short")).map { file to it }
        }
        assertTrue(keys.size > 2000)
        val missing = keys.filter { (_, key) -> english.getOrNull(key) == null }
        assertEquals(emptyList<String>(), missing.map { (file, key) -> "$file: $key" })
    }

    /**
     * The mod's own keys (as plain <text>s) aren't vanilla's: it changes
     * vanilla's text only on purpose, with mod:findName, which the
     * translations must then cover too.
     */
    @Test
    fun theModsKeysAreNotVanillas() {
        val vanillaText = Translator(Datafile(vanilla, emptyList()), "en").translations.keys
        val clashes = File(MOD_DIR, "data").listFiles()!!.filter { it.name.startsWith("text_") }.flatMap { file ->
            val root = file.inputStream().use { FileSource.parseModdedXML(it, file.name, true) }.rootElement
            root.getChildren("text").map { it.getAttributeValue("name") }.filter { it in vanillaText }
        }
        assertEquals(emptyList<String>(), clashes)
    }

    @Test
    fun everyLanguageHasEveryText() {
        for (language in Language.entries) {
            val translator = Translator(modded, language.code)
            val missing = english.translations.keys - translator.translations.keys
            assertEquals(language.code, emptySet<String>(), missing)
        }
    }

    companion object {
        private val MOD_DIR = File("mods/chonkestrel")

        // The blueprints' fields that are shown (see tools/mod_text.py)
        private val BLUEPRINT_FIELDS = setOf("title", "short", "desc", "tooltip", "flavorType", "class", "name")
    }
}
