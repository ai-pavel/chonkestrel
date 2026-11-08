package xyz.znix.xftl.testkit

import xyz.znix.xftl.game.*

class NoOpSoundManager : SoundManager {
    override fun getSample(name: String): FTLSound {
        return NoOpSound()
    }

    override fun getSampleOrWarn(name: String): FTLSound {
        return NoOpSound()
    }

    // A loop that plays nothing, so the fires, the clonebay, hacking and so
    // on can be tested
    override fun getLoop(name: String): LoopHandle =
        LoopHandle(SoundSpec(org.jdom2.Element(name).setAttribute("volume", "0")), null)

    override fun getTrack(name: String): MusicSpec {
        TODO("Not yet implemented")
    }

    override fun switchMusicList(tracks: List<MusicSpec>) {
    }
}

private class NoOpSound : FTLSound {
    override fun play(): SoundInstance {
        return NoOpSoundInstance()
    }
}

private class NoOpSoundInstance : SoundInstance {
    override var isPaused: Boolean = false
    override var volume: Float = 1f

    override fun stop() {
    }
}
