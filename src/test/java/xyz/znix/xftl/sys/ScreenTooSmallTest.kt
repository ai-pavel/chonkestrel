package xyz.znix.xftl.sys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.znix.xftl.rendering.Cursor
import java.nio.file.Path

/**
 * Check the game only refuses to start, with its own window size, on a
 * screen too small for the big ships' interface.
 */
class ScreenTooSmallTest {
    private class FakeContainer(override val screens: List<Screen>, override val isDefaultSize: Boolean = true) :
        GameContainer {
        override val input: Input get() = error("Not used")
        override val width: Int get() = 1280
        override val height: Int get() = 720
        override var onFilesDropped: ((List<Path>) -> Unit)? = null
        override fun exit() {}
        override fun setCursor(cursor: Cursor?) {}
    }

    private val laptop = Screen("Built-in Retina Display", 1710, 1107)
    private val monitor = Screen("VA27DCP", 1920, 1080)

    @Test
    fun startsOnABigEnoughScreen() {
        assertNull(ScreenTooSmallState.screensIfTooSmall(FakeContainer(listOf(monitor, laptop))))
        assertNull(ScreenTooSmallState.screensIfTooSmall(FakeContainer(listOf(Screen("4K", 3840, 2160)))))
    }

    @Test
    fun refusesASmallMainScreen() {
        assertEquals(listOf(laptop), ScreenTooSmallState.screensIfTooSmall(FakeContainer(listOf(laptop))))
        // Even if another screen is big enough, as the game uses the main one
        val both = listOf(laptop, monitor)
        assertEquals(both, ScreenTooSmallState.screensIfTooSmall(FakeContainer(both)))
        // Too short, though wide enough
        val short = listOf(Screen("Wide", 2560, 1000))
        assertEquals(short, ScreenTooSmallState.screensIfTooSmall(FakeContainer(short)))
    }

    @Test
    fun ignoresTheScreenForAChosenWindowSize() {
        assertNull(ScreenTooSmallState.screensIfTooSmall(FakeContainer(listOf(laptop), isDefaultSize = false)))
    }
}
