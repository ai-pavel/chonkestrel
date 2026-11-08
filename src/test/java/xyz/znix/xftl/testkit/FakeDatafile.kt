package xyz.znix.xftl.testkit

import org.newdawn.slick.opengl.ImageDataFactory
import xyz.znix.xftl.*
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.rendering.Texture
import xyz.znix.xftl.sys.ResourceContext

class FakeDatafile(files: List<String>, stringFiles: Map<String, String>) :
    Datafile(FakeVanillaDatafile(files, stringFiles), emptyList()) {

    override fun readImage(context: ResourceContext, file: FTLFile): Image {
        val imageData = ImageDataFactory.getImageDataFor(file.name)

        open(file).use { imageData.loadImage(it, false, null) }

        val tex = Texture(
            imageData.texWidth,
            imageData.texHeight,

            imageData.width,
            imageData.height,

            0 // Invalid OpenGL ID
        )

        return Image(0, 0, tex.imageWidth, tex.imageHeight, tex)
    }
}

private class FakeVanillaDatafile(
    files: List<String>,
    private val stringFiles: Map<String, String>
) : IVanillaDatafile {
    private val fileSet: Set<String> = files.toSet()
    private val fontNames: Set<String> = HashSet(FontOverrideData.fonts.keys)

    override val xmlCacheSupported: Boolean get() = true

    override fun getAllFiles(): List<VanillaDatafile.Entry> {
        // Using the wrong length is fine, it's only used for caching etc.
        return (fileSet + fontNames + stringFiles.keys).map { VanillaDatafile.Entry(it, 0, 0) }
    }

    override fun containsFile(name: String): Boolean {
        return fileSet.contains(name) || fontNames.contains(name) || stringFiles.containsKey(name)
    }

    override fun read(file: VanillaDatafile.Entry): ByteArray {
        // Map all the fonts to the one we use for the datafile selection.
        if (fontNames.contains(file.name)) {
            return readFromClasspath("baked/roboto.font")
        }

        // String files are strings passed in by the test that pretend to be files.
        stringFiles[file.name]?.let { return it.toByteArray(Charsets.UTF_8) }

        return readFromClasspath("test_assets/" + file.name)
    }

    private fun readFromClasspath(name: String): ByteArray {
        val stream = javaClass.classLoader
            .getResourceAsStream(name)
            ?: error("Missing test asset: '$name'")
        return stream.readAllBytes()
    }
}
