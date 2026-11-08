package xyz.znix.xftl.game

import xyz.znix.xftl.SILFontLoader

/**
 * Fitting items' short names into the boxes they're shown in, such as a
 * combined weapon's (see xyz.znix.xftl.weapons.WeaponSizes), whose "(x2)" or
 * "(x3)" can make it too long.
 */
object ShortNames {
    /**
     * [name], fitted into [maxLines] lines of [maxWidth] pixels in [font]:
     * as it is if it fits, or else with its long words cut down to their
     * first three letters and a full stop ("Halberd" to "Hal."), from left
     * to right, until it does. The "(x2)" stays as it is.
     */
    fun fit(name: String, font: SILFontLoader, maxWidth: Int, maxLines: Int): String {
        fun fits(text: String) = font.wrapString(text, maxWidth).size <= maxLines

        if (fits(name))
            return name

        val words = name.split(" ").toMutableList()
        for (i in words.indices) {
            val word = words[i]
            if (word.length <= ABBREVIATED_LENGTH + 1 || word.startsWith("("))
                continue
            words[i] = word.take(ABBREVIATED_LENGTH) + "."
            val shorter = words.joinToString(" ")
            if (fits(shorter))
                return shorter
        }
        return words.joinToString(" ")
    }

    // How many letters a long word is cut down to
    private const val ABBREVIATED_LENGTH = 3
}
