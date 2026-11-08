package xyz.znix.xftl.devutil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.XftlDebug
import java.nio.ByteBuffer

class ScreenshotDirectorTest {
    @Test
    fun testFileNames() {
        // Numbered from one, padded so they sort in order
        assertEquals("01-new-game.png", ScreenshotDirector.fileName(0, "new-game"))
        assertEquals("12-store.png", ScreenshotDirector.fileName(11, "store"))
    }

    @Test
    fun testScenarios() {
        val scenarios = ScreenshotDirector.defaultScenarios()
        val names = scenarios.map { it.name }
        assertTrue(names.size >= 8)

        // Each one gets its own file, with a name that's safe for a URL
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.toString(), names.all { it.matches(Regex("[a-z0-9-]+")) })
        // The first is the main menu's new game window, the only one taken
        // in the main menu
        assertEquals("new-game", names.first())
        assertEquals(listOf("new-game"), scenarios.filter { it.menu != null }.map { it.name })
    }

    @Test
    fun testShipScenarios() {
        // One for each ship with bigger versions, each with its own file
        val names = ScreenshotDirector.shipScenarios().map { it.name }
        assertEquals(ScreenshotDirector.BIGGER_SHIPS.size, names.size)
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.toString(), names.all { it.matches(Regex("[a-z0-9-]+")) })
    }

    @Test
    fun testFramesAreFlipped() {
        // A 2x2 frame, as OpenGL returns it: the bottom row first
        val pixels = ByteBuffer.allocate(2 * 2 * 4)
        val bottomLeft = byteArrayOf(10, 20, 30, -1)
        val bottomRight = byteArrayOf(40, 50, 60, -1)
        val topLeft = byteArrayOf(-1, 0, 0, -1) // 255, 0, 0
        val topRight = byteArrayOf(0, -1, 0, -1)
        for (pixel in listOf(bottomLeft, bottomRight, topLeft, topRight)) {
            pixels.put(pixel)
        }

        val image = ScreenshotDirector.toImage(pixels, 2, 2)
        assertEquals(0xff0000, image.getRGB(0, 0) and 0xffffff)
        assertEquals(0x00ff00, image.getRGB(1, 0) and 0xffffff)
        assertEquals(0x0a141e, image.getRGB(0, 1) and 0xffffff)
        assertEquals(0x28323c, image.getRGB(1, 1) and 0xffffff)
    }

    @Test
    fun testParseSize() {
        assertEquals(Pair(1920, 1080), XftlDebug.parseSize("1920x1080"))
        assertEquals(Pair(1280, 720), XftlDebug.parseSize(" 1280X720 "))
        assertNull(XftlDebug.parseSize("1920"))
        assertNull(XftlDebug.parseSize("0x100"))
        assertNull(XftlDebug.parseSize("axb"))
    }
}
