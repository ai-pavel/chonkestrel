package org.lwjgl.opengl;

import org.teavm.jso.webgl.WebGLTexture;
import xyz.znix.xftl.web.WebGL;

import java.nio.ByteBuffer;

import static xyz.znix.xftl.web.WebGL.gl;

/**
 * The browser version of LWJGL's OpenGL 1.1 functions the game uses, drawn
 * with WebGL 2 (see WebGL). Their constants are the same numbers in both.
 */
public class GL11 {
    public static final int GL_RGB = 0x1907;
    public static final int GL_RGBA = 0x1908;
    public static final int GL_RGB8 = 0x8051;
    public static final int GL_RGBA8 = 0x8058;

    public static void glClearColor(float r, float g, float b, float a) {
        gl.clearColor(r, g, b, a);
    }

    public static void glClear(int mask) {
        gl.clear(mask);
    }

    public static void glEnable(int cap) {
        gl.enable(cap);
    }

    public static void glDisable(int cap) {
        gl.disable(cap);
    }

    public static void glViewport(int x, int y, int width, int height) {
        gl.viewport(x, y, width, height);
    }

    public static void glBlendFunc(int src, int dst) {
        gl.blendFunc(src, dst);
    }

    public static int glGenTextures() {
        return WebGL.add(gl.createTexture());
    }

    public static void glDeleteTextures(int texture) {
        gl.deleteTexture(WebGL.<WebGLTexture>get(texture));
        WebGL.remove(texture);
    }

    public static void glBindTexture(int target, int texture) {
        gl.bindTexture(target, WebGL.<WebGLTexture>get(texture));
    }

    public static void glTexParameteri(int target, int name, int value) {
        gl.texParameteri(target, name, value);
    }

    public static void glTexImage2D(int target, int level, int internalFormat, int width, int height, int border,
                                    int format, int type, ByteBuffer pixels) {
        // WebGL wants the internal format to match the data's: RGB data
        // goes in an RGB texture, not an RGBA one
        if (format == GL_RGB && internalFormat == GL_RGBA8)
            internalFormat = GL_RGB8;
        gl.pixelStorei(WebGLConstants.UNPACK_ALIGNMENT, 1);
        gl.texImage2D(target, level, internalFormat, width, height, border, format, type,
                pixels == null ? (org.teavm.jso.typedarrays.ArrayBufferView) null : WebGL.bytes(pixels));
    }

    public static int glGetInteger(int name) {
        return WebGL.toInt(gl.getParameter(name));
    }

    public static void glStencilMask(int mask) {
        gl.stencilMask(mask);
    }

    public static void glStencilFunc(int func, int ref, int mask) {
        gl.stencilFunc(func, ref, mask);
    }

    public static void glStencilOp(int fail, int zfail, int zpass) {
        gl.stencilOp(fail, zfail, zpass);
    }

    public static String glGetString(int name) {
        return "WebGL 2";
    }

    public static int glGetError() {
        return gl.getError();
    }

    static final class WebGLConstants {
        static final int UNPACK_ALIGNMENT = 0x0CF5;
    }
}
