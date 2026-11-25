package org.lwjgl.opengl;

import org.teavm.jso.webgl.WebGLBuffer;
import org.teavm.jso.webgl.WebGLProgram;
import org.teavm.jso.webgl.WebGLShader;
import org.teavm.jso.webgl.WebGLUniformLocation;
import org.teavm.jso.webgl.WebGLVertexArrayObject;
import xyz.znix.xftl.web.WebGL;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static xyz.znix.xftl.web.WebGL.gl;

/**
 * The browser version of LWJGL's OpenGL 1.5 to 3.0 functions the game uses
 * (buffers, shaders and vertex arrays), drawn with WebGL 2 (see WebGL).
 */
public class GL30 extends GL11 {
    private static final int GL_COMPILE_STATUS = 0x8B81;
    private static final int GL_LINK_STATUS = 0x8B82;
    private static final int GL_INFO_LOG_LENGTH = 0x8B84;

    public static int glGenBuffers() {
        return WebGL.add(gl.createBuffer());
    }

    public static void glDeleteBuffers(int buffer) {
        gl.deleteBuffer(WebGL.<WebGLBuffer>get(buffer));
        WebGL.remove(buffer);
    }

    public static void glBindBuffer(int target, int buffer) {
        gl.bindBuffer(target, WebGL.<WebGLBuffer>get(buffer));
    }

    public static void glBufferData(int target, ByteBuffer data, int usage) {
        gl.bufferData(target, WebGL.bytes(data), usage);
    }

    public static int glGenVertexArrays() {
        return WebGL.add(gl.createVertexArray());
    }

    public static void glDeleteVertexArrays(int array) {
        gl.deleteVertexArray(WebGL.<WebGLVertexArrayObject>get(array));
        WebGL.remove(array);
    }

    public static void glBindVertexArray(int array) {
        gl.bindVertexArray(WebGL.<WebGLVertexArrayObject>get(array));
    }

    public static void glEnableVertexAttribArray(int index) {
        gl.enableVertexAttribArray(index);
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
        gl.vertexAttribPointer(index, size, type, normalized, stride, (int) offset);
    }

    public static void glDrawArrays(int mode, int first, int count) {
        gl.drawArrays(mode, first, count);
    }

    public static void glDrawElements(int mode, int count, int type, long offset) {
        gl.drawElements(mode, count, type, (int) offset);
    }

    public static int glCreateShader(int type) {
        return WebGL.add(gl.createShader(type));
    }

    public static void glShaderSource(int shader, CharSequence source) {
        // The shaders are written for desktop OpenGL 3.3: WebGL 2 runs
        // OpenGL ES 3.0's, which say so, and how precise floats are
        String text = source.toString().replace("#version 330 core",
                "#version 300 es\nprecision highp float;\nprecision highp int;");
        gl.shaderSource(WebGL.<WebGLShader>get(shader), text);
    }

    public static void glCompileShader(int shader) {
        gl.compileShader(WebGL.<WebGLShader>get(shader));
    }

    public static int glGetShaderi(int shader, int name) {
        WebGLShader s = WebGL.get(shader);
        if (name == GL_INFO_LOG_LENGTH) {
            String log = gl.getShaderInfoLog(s);
            return log == null ? 0 : log.length();
        }
        if (name == GL_COMPILE_STATUS)
            return WebGL.toBoolean(gl.getShaderParameter(s, name)) ? 1 : 0;
        return WebGL.toInt(gl.getShaderParameter(s, name));
    }

    public static String glGetShaderInfoLog(int shader, int maxLength) {
        String log = gl.getShaderInfoLog(WebGL.<WebGLShader>get(shader));
        return log == null ? "" : log;
    }

    public static void glDeleteShader(int shader) {
        gl.deleteShader(WebGL.<WebGLShader>get(shader));
        WebGL.remove(shader);
    }

    public static int glCreateProgram() {
        return WebGL.add(gl.createProgram());
    }

    public static void glAttachShader(int program, int shader) {
        gl.attachShader(WebGL.<WebGLProgram>get(program), WebGL.<WebGLShader>get(shader));
    }

    public static void glLinkProgram(int program) {
        gl.linkProgram(WebGL.<WebGLProgram>get(program));
    }

    public static int glGetProgrami(int program, int name) {
        WebGLProgram p = WebGL.get(program);
        if (name == GL_INFO_LOG_LENGTH) {
            String log = gl.getProgramInfoLog(p);
            return log == null ? 0 : log.length();
        }
        if (name == GL_LINK_STATUS)
            return WebGL.toBoolean(gl.getProgramParameter(p, name)) ? 1 : 0;
        return WebGL.toInt(gl.getProgramParameter(p, name));
    }

    public static String glGetProgramInfoLog(int program, int maxLength) {
        String log = gl.getProgramInfoLog(WebGL.<WebGLProgram>get(program));
        return log == null ? "" : log;
    }

    public static void glDeleteProgram(int program) {
        gl.deleteProgram(WebGL.<WebGLProgram>get(program));
        WebGL.remove(program);
    }

    public static void glUseProgram(int program) {
        gl.useProgram(WebGL.<WebGLProgram>get(program));
    }

    public static int glGetAttribLocation(int program, CharSequence name) {
        return gl.getAttribLocation(WebGL.<WebGLProgram>get(program), name.toString());
    }

    public static int glGetUniformLocation(int program, CharSequence name) {
        WebGLUniformLocation location = gl.getUniformLocation(WebGL.<WebGLProgram>get(program), name.toString());
        return location == null ? -1 : WebGL.add(location);
    }

    public static void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) {
        gl.uniformMatrix3fv(WebGL.<WebGLUniformLocation>get(location), transpose, WebGL.floats(value));
    }
}
