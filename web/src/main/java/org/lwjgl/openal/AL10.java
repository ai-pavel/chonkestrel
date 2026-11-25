package org.lwjgl.openal;

import xyz.znix.xftl.web.WebAudio;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** The browser version of the OpenAL functions the game uses, on WebAudio (see WebAudio). */
public class AL10 {
    public static int alGetError() { return 0; }
    public static int alGenBuffers() { return WebAudio.genBuffer(); }
    public static void alGenBuffers(IntBuffer buffers) { WebAudio.genBuffers(buffers); }
    public static void alGenSources(IntBuffer sources) { WebAudio.genSources(sources); }
    public static void alDeleteBuffers(int buffer) { WebAudio.deleteBuffer(buffer); }
    public static void alDeleteBuffers(IntBuffer buffers) { WebAudio.deleteBuffers(buffers); }
    public static void alBufferData(int buffer, int format, ByteBuffer data, int frequency) {
        WebAudio.bufferData(buffer, format, data, frequency);
    }
    public static int alGetBufferi(int buffer, int name) { return WebAudio.getBufferi(buffer, name); }
    public static int alGetSourcei(int source, int name) { return WebAudio.getSourcei(source, name); }
    public static float alGetSourcef(int source, int name) { return WebAudio.getSourcef(source, name); }
    public static void alSourcei(int source, int name, int value) { WebAudio.setSourcei(source, name, value); }
    public static void alSourcef(int source, int name, float value) { WebAudio.setSourcef(source, name, value); }
    // (where a sound is: the game plays them all in the middle)
    public static void alSource3f(int source, int name, float x, float y, float z) {}
    public static void alListener3f(int name, float x, float y, float z) {}
    public static void alListenerfv(int name, FloatBuffer values) { WebAudio.listener(name, values); }
    public static void alSourcePlay(int source) { WebAudio.play(source); }
    public static void alSourcePause(int source) { WebAudio.pause(source); }
    public static void alSourceStop(int source) { WebAudio.stop(source); }
    public static void alSourceQueueBuffers(int source, IntBuffer buffers) { WebAudio.queueBuffers(source, buffers); }
    public static void alSourceUnqueueBuffers(int source, IntBuffer buffers) { WebAudio.unqueueBuffers(source, buffers); }
}
