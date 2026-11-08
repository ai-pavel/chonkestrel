package xyz.znix.xftl.crew

import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import xyz.znix.xftl.Datafile
import kotlin.random.Random

/**
 * Stores all the available crew names, from names.xml.
 *
 * The XFTL_NAMES environment variable (or the xftl.names system property) can
 * pick a preset of names to use instead, from XFTL's assets/data/names folder:
 * see [PRESETS], or [ALL_PRESET] for all of them at once. The default is the
 * game's names (including any added by mods).
 */
class CrewNameManager @JvmOverloads constructor(
    df: Datafile,
    private val language: String,
    preset: String = selectedPreset()
) {
    val names: List<CrewNameList>

    init {
        val presetRoots = when (preset) {
            DEFAULT_PRESET -> null
            ALL_PRESET -> PRESETS.map { loadPreset(it) }
            in PRESETS -> listOf(loadPreset(preset))
            else -> {
                println("[WARN] Unknown crew names preset '$preset', using the game's names. The presets are: $PRESETS and $ALL_PRESET")
                null
            }
        }

        if (presetRoots != null) {
            // Presets don't have different languages
            names = presetRoots.flatMap { parseLists(it) }
        } else {
            val allNames = parseLists(df.parseXML(df["data/names.xml"]).rootElement)

            val matchingLanguage = allNames.filter { it.language == language }
            if (matchingLanguage.isNotEmpty()) {
                names = matchingLanguage
            } else {
                names = allNames.filter { it.language == "en" }
            }
        }
    }

    private fun parseLists(root: Element): List<CrewNameList> {
        val allNames = ArrayList<CrewNameList>()

        for (child in root.children) {
            require(child.name == "nameList") { "Found non-nameList element in names.xml root" }

            val listNames = ArrayList<String>()
            val shortVersions = HashMap<String, String>()

            for (nameElem in child.children) {
                require(nameElem.name == "name") { "Found non-name element in nameList (in names.xml)" }
                val name = nameElem.textTrim
                listNames.add(name)

                val shortName = nameElem.getAttributeValue("short")
                if (shortName != null) {
                    shortVersions[name] = shortName
                }
            }

            val isMale = child.getAttributeValue("sex") == "male"
            val listLanguage = child.getAttributeValue("language") ?: "en"

            allNames.add(CrewNameList(listNames, shortVersions, isMale, listLanguage))
        }

        return allNames
    }

    fun getForGender(male: Boolean?, rand: Random): String {
        val lists = names.filter {
            // If a gender is specified, filter on that
            if (male != null) {
                if (male != it.isMale)
                    return@filter false
            }

            return@filter true
        }

        return lists.random(rand).names.random(rand)
    }

    fun findShort(name: String): String? {
        for (list in names) {
            list.shortVersions[name]?.let { return it }
        }
        return null
    }

    companion object {
        /**
         * The name of the preset that uses the game's own names.
         */
        const val DEFAULT_PRESET = "default"

        /**
         * The name of the preset that uses every preset's names at once. As
         * a name list is picked before a name from it, each preset is about
         * as likely as the others.
         */
        const val ALL_PRESET = "all"

        /**
         * The presets of names from different countries and languages.
         */
        val ETHNIC_PRESETS = listOf(
            "american", "english", "irish", "scottish", "french", "german",
            "spanish", "italian", "russian", "turkish", "arabic", "indian",
            "chinese", "korean", "japanese"
        )

        /**
         * The presets of characters' names, from films, series and games.
         */
        val THEMED_PRESETS = listOf("starwars", "startrek", "masseffect")

        /**
         * The presets of names in XFTL's assets/data/names folder.
         */
        val PRESETS = ETHNIC_PRESETS + THEMED_PRESETS

        /**
         * The preset picked with XFTL_NAMES or xftl.names, or the default.
         */
        fun selectedPreset(): String {
            val value = System.getenv("XFTL_NAMES")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("xftl.names")?.takeIf { it.isNotBlank() }
                ?: return DEFAULT_PRESET
            return value.trim().lowercase()
        }

        fun loadPreset(preset: String): Element {
            val stream = CrewNameManager::class.java.getResourceAsStream("/assets/data/names/$preset.xml")
                ?: error("Missing crew names preset '$preset'")
            return stream.use { SAXBuilder().build(it).rootElement }
        }
    }
}

class CrewNameList(
    val names: List<String>,

    /**
     * This list's mapping between full and shortened names.
     *
     * This is used for names that have custom shortened versions.
     */
    val shortVersions: Map<String, String>,

    val isMale: Boolean,
    val language: String
)
