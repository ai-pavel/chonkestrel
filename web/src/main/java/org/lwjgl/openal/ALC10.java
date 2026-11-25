package org.lwjgl.openal;

import xyz.znix.xftl.web.WebAudio;

import java.nio.ByteBuffer;

/** The browser version's OpenAL device and context: the page's WebAudio (see WebAudio). */
public class ALC10 {
    public static long alcOpenDevice(ByteBuffer name) {
        return WebAudio.open() ? 1 : 0;
    }

    public static long alcCreateContext(long device, int[] attributes) {
        return 1;
    }

    public static boolean alcMakeContextCurrent(long context) {
        return true;
    }

    public static String alcGetString(long device, int name) {
        return "WebAudio";
    }
}
