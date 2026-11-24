package xyz.znix.xftl

import java.util.Locale

/**
 * The languages the game can be played in, picked in the options window
 * (see [xyz.znix.xftl.game.LanguageWindow]) and kept in the profile. Each
 * one's [code] picks vanilla's `data/text-<code>.xml`, XFTL's own
 * `assets/lang/<code>.xml` and the mod's `text-<code>.xml.append` (see
 * [Translator], and doc/tools/translations.md). The [nativeName] is how the
 * options window shows it, in its own language.
 */
enum class Language(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    SPANISH("es", "Español"),
    ITALIAN("it", "Italiano"),
    RUSSIAN("ru", "Русский");

    companion object {
        /** The language with [code] (such as `fr`), or null if there's none. */
        @JvmStatic
        fun fromCode(code: String?): Language? {
            val wanted = code?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.code == wanted }
        }

        /**
         * The language XFTL_LANG (or `-Dxftl.lang`) picks for this run, such
         * as `XFTL_LANG=ru`, instead of the profile's. It isn't saved. The
         * screenshots and videos (the site's, the trailer) are in English,
         * whatever the profile's language, unless XFTL_LANG says otherwise.
         */
        @JvmStatic
        val override: Language? = (System.getenv("XFTL_LANG") ?: System.getProperty("xftl.lang"))
            ?.takeIf { it.isNotBlank() }
            ?.let { code ->
                fromCode(code).also { if (it == null) println("[WARN] Unknown XFTL_LANG '$code', ignoring it") }
            }
            ?: ENGLISH.takeIf { XftlDebug.recording }

        /**
         * The language of the computer's locale, if it's one of these, or
         * English: the language a new profile starts with.
         */
        @JvmStatic
        fun fromLocale(): Language = fromCode(Locale.getDefault().language) ?: ENGLISH
    }
}
