package xyz.znix.xftl.game

import org.jdom2.Element

/**
 * The sounds when there's no sound device (and in the tests): every sound
 * and loop plays nothing, and the music's silent.
 */
class SilentSoundManager : SoundManager {
    override fun getSample(name: String): FTLSound = SilentSound

    override fun getSampleOrWarn(name: String): FTLSound = SilentSound

    // A loop that plays nothing, so the fires, the clonebay, hacking and so
    // on still run
    override fun getLoop(name: String): LoopHandle =
        LoopHandle(SoundSpec(Element(name).setAttribute("volume", "0")), null)

    override fun getTrack(name: String): MusicSpec =
        MusicSpec(Element("track").addContent(Element("name").setText(name)).addContent(Element("explore").setText(name)))

    override fun switchMusicList(tracks: List<MusicSpec>) {
    }
}

private object SilentSound : FTLSound {
    override fun play(): SoundInstance = SilentSoundInstance()
}

private class SilentSoundInstance : SoundInstance {
    override var isPaused: Boolean = false
    override var volume: Float = 1f

    override fun stop() {
    }
}
