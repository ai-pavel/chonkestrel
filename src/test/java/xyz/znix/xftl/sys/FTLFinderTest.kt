package xyz.znix.xftl.sys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Check the player can pick ftl.dat, or something that leads to it, in the
 * datafile selection screen.
 */
class FTLFinderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun datafile(relative: String): Path {
        val path = temp.root.toPath().resolve(relative)
        Files.createDirectories(path.parent)
        Files.write(path, "PKG\n".toByteArray() + ByteArray(16))
        return path
    }

    private fun file(relative: String, contents: String = "not a datafile"): Path {
        val path = temp.root.toPath().resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, contents)
        return path
    }

    @Test
    fun recognisesDatafiles() {
        assertTrue(FTLFinder.isFtlDat(datafile("ftl.dat")))
        assertFalse(FTLFinder.isFtlDat(file("other.dat")))
        assertFalse(FTLFinder.isFtlDat(file("short.dat", "PKG\n")))
        assertFalse(FTLFinder.isFtlDat(temp.root.toPath().resolve("missing.dat")))
    }

    @Test
    fun resolvesTheDatafileItself() {
        val dat = datafile("anywhere/ftl.dat")
        assertEquals(dat, FTLFinder.resolveDatafile(dat))
    }

    @Test
    fun resolvesGameDirectories() {
        // Windows, Linux (Steam and GOG) and macOS layouts
        val windows = datafile("windows/ftl.dat")
        val linux = datafile("linux/data/ftl.dat")
        val gog = datafile("gog/game/data/ftl.dat")
        val mac = datafile("mac/FTL.app/Contents/Resources/ftl.dat")
        val root = temp.root.toPath()

        assertEquals(windows, FTLFinder.resolveDatafile(root.resolve("windows")))
        assertEquals(linux, FTLFinder.resolveDatafile(root.resolve("linux")))
        assertEquals(gog, FTLFinder.resolveDatafile(root.resolve("gog")))
        assertEquals(mac, FTLFinder.resolveDatafile(root.resolve("mac/FTL.app")))
        assertEquals(mac, FTLFinder.resolveDatafile(root.resolve("mac")))
    }

    @Test
    fun resolvesTheExecutable() {
        val dat = datafile("game/ftl.dat")
        val exe = file("game/FTLGame.exe")
        assertEquals(dat, FTLFinder.resolveDatafile(exe))
    }

    @Test
    fun findsTheDatafileNextToTheGame() {
        val dat = datafile("installed/ftl.dat")
        val oldDir = System.getProperty("user.dir")
        try {
            // Run from the directory ftl.dat is in
            System.setProperty("user.dir", dat.parent.toString())
            assertEquals(dat, FTLFinder.findNextToGame())
            assertEquals("This folder", FTLFinder.findInstallations().first().source)
        } finally {
            System.setProperty("user.dir", oldDir)
        }
    }

    @Test
    fun rejectsOtherFiles() {
        assertNull(FTLFinder.resolveDatafile(file("elsewhere/readme.txt")))
        assertNull(FTLFinder.resolveDatafile(temp.newFolder("empty").toPath()))
        // Something called ftl.dat which isn't one
        assertNull(FTLFinder.resolveDatafile(file("fake/ftl.dat")))
    }
}
