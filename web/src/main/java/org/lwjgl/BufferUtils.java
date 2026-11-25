package org.lwjgl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** The browser version of LWJGL's buffers: plain ones, as there's no native memory. */
public class BufferUtils {
    public static ByteBuffer createByteBuffer(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.nativeOrder());
    }

    public static FloatBuffer createFloatBuffer(int size) {
        return createByteBuffer(size * 4).asFloatBuffer();
    }

    public static IntBuffer createIntBuffer(int size) {
        return createByteBuffer(size * 4).asIntBuffer();
    }
}
