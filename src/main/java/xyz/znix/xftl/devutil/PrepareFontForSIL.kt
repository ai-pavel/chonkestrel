package xyz.znix.xftl.devutil

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * This converts a TrueType font into a bitmap and a text file, which can then
 * be passed to SIL's makefont utility.
 *
 * This is used for building the Roboto font we use in the select ftl.dat screen,
 * since we don't have any of FTL's fonts available at that point.
 *
 * To convert the result of this script to the .font file, use pngtotex and makefont
 * from SIL's tools directory:
 *
 * pngtotex -alpha /tmp/raw-image.png
 * makefont -texture=/tmp/raw-image.tex -charlist=/tmp/font-meta.txt roboto.font
 */
object PrepareFontForSIL {
    @JvmStatic
    fun main(args: Array<String>) {
        val fontSize = 15

        val ttfPath = "src/main/resources/roboto-font/Roboto-Regular.ttf"
        val rootFont = Font.createFont(Font.TRUETYPE_FONT, File(ttfPath))

        // This is largely copied from Slick's TrueTypeFont
        val image = BufferedImage(
            256, 128,
            BufferedImage.TYPE_INT_ARGB
        )
        val g = image.graphics as Graphics2D
        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )

        // Find the font size to get the ascent we want
        g.font = rootFont
        val font = rootFont.deriveFont(fontSize.toFloat()) // / g.fontMetrics.ascent)
        g.font = font

        val chars = listOf(
            'a'..'z',
            'A'..'Z',
            '0'..'9',
            listOf('ä', 'ö', 'ü', 'Ä', 'Ö', 'Ü'),
            // Note we must include ? as it's the fallback character,
            // used if an unknown one is found.
            listOf(' ', '-', '_', ':', '\\', '/', '.', ',', '\'', '"', '?')
        ).flatMap { it.toList() }

        Files.newBufferedWriter(Path.of("/tmp/font-meta.txt")).use { writer ->
            var x = 0
            var y = 0
            for (c in chars) {
                val w = drawChar(writer, g, font, x, y, c)
                x += w

                // Add 1px of spacing for anti-aliasing bleedover
                x += 1

                // Hardcoded numbers for wrap-around
                if (x > image.width - 20) {
                    x = 0
                    y += 20
                }
            }
        }

        // Linux-specific paths, just change these if you need to run it on Windows.
        ImageIO.write(image, "png", File("/tmp/raw-image.png"))
    }

    private fun drawChar(output: Writer, g: Graphics2D, font: Font, x: Int, y: Int, ch: Char): Int {
        val fontMetrics = g.fontMetrics
        val bounds = fontMetrics.getStringBounds(ch.toString(), g)

        // bounds.y is negative, this moves the top of the font to the y
        val ascent = -bounds.y.toInt()
        val baselineY = y + ascent

        // Test stuff
        // g.color = Color.red
        // g.drawRect(
        //     x + bounds.x.toInt(),
        //     baselineY + bounds.y.toInt(),
        //     bounds.width.toInt() - 1,
        //     bounds.height.toInt() - 1
        // )
        // g.color = Color.blue
        // g.drawRect(x, baselineY - fontMetrics.ascent, 5, 0)

        g.color = Color.BLACK
        g.drawString(ch.toString(), x, baselineY)

        output.append("char '$ch' $x $y ${bounds.width.toInt()} ${bounds.height.toInt()} $ascent 0 0\n")

        return bounds.width.toInt()
    }
}
