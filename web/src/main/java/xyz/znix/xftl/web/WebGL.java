package xyz.znix.xftl.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.typedarrays.Uint8Array;
import org.teavm.jso.webgl.WebGL2RenderingContext;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * The browser's WebGL 2 context, which the web versions of LWJGL's OpenGL
 * classes (see org.lwjgl.opengl in this module) draw with.
 * <p>
 * OpenGL names its objects (textures, buffers, shaders and so on) by
 * numbers, and WebGL by objects, so they're kept in a table here: an
 * object's number is its index in it.
 */
public final class WebGL {
    private WebGL() {
    }

    public static WebGL2RenderingContext gl;

    private static final ArrayList<JSObject> objects = new ArrayList<>();

    static {
        // Number 0 is no object, as in OpenGL
        objects.add(null);
    }

    public static int add(JSObject object) {
        if (object == null)
            return 0;
        objects.add(object);
        return objects.size() - 1;
    }

    @SuppressWarnings("unchecked")
    public static <T extends JSObject> T get(int id) {
        if (id <= 0 || id >= objects.size())
            return null;
        return (T) objects.get(id);
    }

    public static void remove(int id) {
        if (id > 0 && id < objects.size())
            objects.set(id, null);
    }

    /** A view of what's left in [buffer] (from its position to its limit), as bytes. */
    public static Uint8Array bytes(Buffer buffer) {
        ByteBuffer bytes = (ByteBuffer) buffer;
        byte[] copy = new byte[bytes.remaining()];
        bytes.duplicate().get(copy);
        Int8Array array = Int8Array.fromJavaArray(copy);
        return Uint8Array.create(array.getBuffer(), array.getByteOffset(), array.getLength());
    }

    /** What's left in [buffer], as floats. */
    public static Float32Array floats(FloatBuffer buffer) {
        float[] copy = new float[buffer.remaining()];
        buffer.duplicate().get(copy);
        return Float32Array.fromJavaArray(copy);
    }

    @JSBody(params = "value", script = "return value === null || value === undefined ? 0 : (typeof value === 'boolean' ? (value ? 1 : 0) : value);")
    public static native int toInt(JSObject value);

    @JSBody(params = "value", script = "return value === true;")
    public static native boolean toBoolean(JSObject value);
}
