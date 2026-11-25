package org.lwjgl.openal;

/** The browser version's OpenAL (see WebAudio): nothing to set up. */
public class ALC {
    public static ALCCapabilities createCapabilities(long device) {
        return new ALCCapabilities();
    }
}
