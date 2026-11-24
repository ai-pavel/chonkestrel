package xyz.znix.xftl

import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.input.SAXBuilder

class Translator {
    val translations: Map<String, String>

    /** The code of the language this is in, such as `en` or `fr` (see [Language]). */
    val language: String

    /**
     * The game's text in [lang]: each layer over the last, so a text a
     * language hasn't got (yet) is in English rather than missing:
     *
     * - vanilla's English (`data/text_*.xml`), with the mods' English;
     * - vanilla's [lang] (`data/text-<lang>.xml`), with the mods' own
     *   translations, which they append to it (such as the Chonkestrel
     *   mod's `text-fr.xml.append`), as [df] has them patched in;
     * - XFTL's own English (`assets/lang/en.xml`);
     * - XFTL's own [lang] (`assets/lang/<lang>.xml`), if there is one.
     *
     * See doc/tools/translations.md.
     */
    constructor(df: Datafile, lang: String) {
        translations = HashMap()
        language = lang
        fun load(name: String) = parseFile(name, df, translations)

        load("data/text_achievements.xml")
        load("data/text_blueprints.xml")
        load("data/text_events.xml")
        load("data/text_misc.xml")
        load("data/text_sectorname.xml")
        load("data/text_tooltips.xml")
        load("data/text_tutorial.xml")

        when (lang) {
            "en" -> Unit
            "zh" -> load("data/text-zh-Hans.xml")
            else -> load("data/text-$lang.xml")
        }

        parseEmbedded("assets/lang/en.xml", translations)
        if (lang != "en")
            parseEmbeddedIfPresent("assets/lang/$lang.xml", translations)
    }

    /**
     * Used for the select-a-datafile screen, as we don't have a main
     * datafile to use at that point.
     */
    constructor(embeddedPath: String) {
        translations = HashMap()
        language = "en"
        parseEmbedded(embeddedPath, translations)
    }

    private fun parseFile(filename: String, df: Datafile, tl: HashMap<String, String>) {
        val doc = df.parseXML(df[filename])
        parseDoc(filename, doc, tl)
    }

    private fun parseEmbedded(path: String, tl: HashMap<String, String>) {
        check(parseEmbeddedIfPresent(path, tl)) { "Missing translation file $path" }
    }

    // Returns false if there's no such file
    private fun parseEmbeddedIfPresent(path: String, tl: HashMap<String, String>): Boolean {
        val stream = javaClass.classLoader.getResourceAsStream(path) ?: return false

        val builder = SAXBuilder()
        builder.expandEntities = false
        val doc = stream.use { builder.build(it) }

        parseDoc(path, doc, tl)
        return true
    }

    private fun parseDoc(fileName: String, doc: Document, tl: HashMap<String, String>) {
        for (elem in doc.rootElement.children) {
            if (elem.name != "text") {
                println("[WARN] Found invalid element '${elem.name}' in translation file '$fileName'")
                continue
            }

            tl[elem.getAttributeValue("name")] = elem.textTrim.replace("\\n", "\n")
        }
    }

    operator fun get(key: String): String {
        // Make it really obvious if a translation is missing
        return translations[key] ?: "could not find: $key"
    }

    /** The text for [key], or null if there isn't one (for optional texts). */
    fun getOrNull(key: String): String? = translations[key]

    /**
     * Convenience helper for [GameText.get].
     */
    operator fun get(text: GameText): String {
        return text.get(this)
    }
}

/**
 * Represents a string parsed from the XML files that may or may not be localised.
 */
class GameText private constructor(
    private val literal: String?, private val key: String?,
    private val arg: GameText? = null
) {

    fun get(translator: Translator): String {
        if (literal != null) {
            return literal
        }

        // A localised string with another text put in it, as \1
        if (arg != null) {
            return translator[key!!].replace("\\1", arg.get(translator))
        }

        return translator[key!!]
    }

    // These are for use in automated tests only!
    fun getLiteralForTesting(): String? = literal
    fun getKeyForTesting(): String? = key

    companion object {
        fun parse(elem: Element): GameText {
            val id: String? = elem.getAttributeValue("id")
            if (id != null) {
                return GameText(null, id)
            }

            return GameText(elem.textTrim, null)
        }

        fun localised(key: String): GameText {
            return GameText(null, key)
        }

        fun literal(string: String): GameText {
            return GameText(string, null)
        }

        /**
         * The localised string [key], with [arg] put in it in place of \1.
         */
        fun localisedWith(key: String, arg: GameText): GameText {
            return GameText(null, key, arg)
        }
    }
}

fun Element.getGameTextChild(name: String): GameText? {
    val elem = this.getChild(name) ?: return null
    return GameText.parse(elem)
}
