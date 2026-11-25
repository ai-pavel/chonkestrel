package xyz.znix.xftl.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.webaudio.AudioBuffer;
import org.teavm.jso.webaudio.AudioBufferSourceNode;
import org.teavm.jso.webaudio.AudioContext;
import org.teavm.jso.webaudio.GainNode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The browser version's OpenAL (see org.lwjgl.openal.AL10 in this module),
 * on the browser's WebAudio: the bits of it the game uses.
 * <p>
 * An OpenAL buffer is an AudioBuffer of its samples. An OpenAL source has
 * a queue of buffers (one, for a sound effect; a few, refilled as they're
 * played, for the music, see OpenALStreamPlayer), played one after the
 * other through a gain node, each by an AudioBufferSourceNode scheduled for
 * when the one before ends. Where a source has got to (which buffers it's
 * finished, whether it's still playing) is worked out from the audio
 * clock whenever the game asks, rather than from the nodes' events, so it
 * doesn't change in the middle of a frame.
 */
public final class WebAudio {
    private WebAudio() {
    }

    // OpenAL's numbers for the things the game asks about and sets
    public static final int AL_NONE = 0;
    public static final int AL_PITCH = 0x1003;
    public static final int AL_LOOPING = 0x1007;
    public static final int AL_BUFFER = 0x1009;
    public static final int AL_GAIN = 0x100A;
    public static final int AL_SOURCE_STATE = 0x1010;
    public static final int AL_INITIAL = 0x1011;
    public static final int AL_PLAYING = 0x1012;
    public static final int AL_PAUSED = 0x1013;
    public static final int AL_STOPPED = 0x1014;
    public static final int AL_BUFFERS_QUEUED = 0x1015;
    public static final int AL_BUFFERS_PROCESSED = 0x1016;
    public static final int AL_SEC_OFFSET = 0x1024;
    public static final int AL_SIZE = 0x2004;
    public static final int AL_FORMAT_MONO8 = 0x1100;
    public static final int AL_FORMAT_MONO16 = 0x1101;
    public static final int AL_FORMAT_STEREO8 = 0x1102;
    public static final int AL_FORMAT_STEREO16 = 0x1103;

    private static AudioContext context;

    private static final class Buffer {
        AudioBuffer audio;
        int size;

        double duration() {
            return audio == null ? 0 : audio.getDuration();
        }
    }

    private static final class Source {
        final GainNode gain;
        final List<Integer> queue = new ArrayList<>();
        // The buffer in the queue it's playing, and how far into it (in
        // seconds of the buffer) it was at [anchor] on the audio clock
        int current;
        double offset;
        int state = AL_INITIAL;
        boolean looping;
        float pitch = 1f;
        // The nodes playing the queue from [current], and when each of
        // their buffers starts and ends on the audio clock
        final List<AudioBufferSourceNode> nodes = new ArrayList<>();
        final List<Double> starts = new ArrayList<>();
        final List<Double> ends = new ArrayList<>();

        Source(AudioContext context) {
            gain = context.createGain();
            gain.connect(context.getDestination());
        }
    }

    private static final Map<Integer, Buffer> buffers = new HashMap<>();
    private static final Map<Integer, Source> sources = new HashMap<>();
    private static int nextId = 1;

    /** Open the browser's audio (made by the page while picking ftl.dat counts as the player asking for it). */
    public static boolean open() {
        if (context != null)
            return true;
        context = pageContext();
        if (context == null)
            context = AudioContext.create();
        return context != null;
    }

    @JSBody(script = "return window.xftlAudio || null;")
    private static native AudioContext pageContext();

    private static double now() {
        return context.getCurrentTime();
    }

    // Buffers

    public static int genBuffer() {
        int id = nextId++;
        buffers.put(id, new Buffer());
        return id;
    }

    public static void genBuffers(IntBuffer ids) {
        while (ids.hasRemaining())
            ids.put(genBuffer());
        ids.flip();
    }

    public static void deleteBuffer(int id) {
        buffers.remove(id);
    }

    public static void deleteBuffers(IntBuffer ids) {
        for (int i = ids.position(); i < ids.limit(); i++)
            deleteBuffer(ids.get(i));
    }

    /** OpenAL's alBufferData: [data]'s samples (8 or 16 bit, mono or stereo) into the buffer. */
    public static void bufferData(int id, int format, ByteBuffer data, int frequency) {
        Buffer buffer = buffers.get(id);
        if (buffer == null || data == null)
            return;
        boolean stereo = format == AL_FORMAT_STEREO8 || format == AL_FORMAT_STEREO16;
        boolean sixteen = format == AL_FORMAT_MONO16 || format == AL_FORMAT_STEREO16;
        int channels = stereo ? 2 : 1;
        int bytes = data.remaining();
        int frames = bytes / (channels * (sixteen ? 2 : 1));
        buffer.size = bytes;
        if (frames == 0) {
            buffer.audio = null;
            return;
        }
        ByteBuffer in = data.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        float[][] samples = new float[channels][frames];
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < channels; c++) {
                samples[c][f] = sixteen ? in.getShort() / 32768f : ((in.get() & 0xFF) - 128) / 128f;
            }
        }
        AudioBuffer audio = context.createBuffer(channels, frames, frequency);
        for (int c = 0; c < channels; c++)
            audio.copyToChannel(samples[c], c);
        buffer.audio = audio;
    }

    public static int getBufferi(int id, int name) {
        Buffer buffer = buffers.get(id);
        if (buffer == null)
            return 0;
        return name == AL_SIZE ? buffer.size : 0;
    }

    // Sources

    public static void genSources(IntBuffer ids) {
        while (ids.hasRemaining()) {
            int id = nextId++;
            sources.put(id, new Source(context));
            ids.put(id);
        }
        ids.flip();
    }

    public static void setSourcei(int id, int name, int value) {
        Source source = sources.get(id);
        if (source == null)
            return;
        if (name == AL_BUFFER) {
            // A source's one buffer (or none), in place of its queue
            stopNodes(source);
            source.queue.clear();
            if (value != AL_NONE)
                source.queue.add(value);
            source.current = 0;
            source.offset = 0;
            source.state = AL_INITIAL;
        } else if (name == AL_LOOPING) {
            source.looping = value != 0;
        }
    }

    public static void setSourcef(int id, int name, float value) {
        Source source = sources.get(id);
        if (source == null)
            return;
        if (name == AL_GAIN) {
            source.gain.getGain().setValue(value);
        } else if (name == AL_PITCH) {
            // (only at the start, which is where the game sets it)
            source.pitch = value;
        } else if (name == AL_SEC_OFFSET) {
            advance(source);
            source.offset = value;
            if (source.state == AL_PLAYING)
                schedule(source);
        }
    }

    public static int getSourcei(int id, int name) {
        Source source = sources.get(id);
        if (source == null)
            return 0;
        advance(source);
        switch (name) {
            case AL_SOURCE_STATE:
                return source.state;
            case AL_BUFFERS_QUEUED:
                return source.queue.size();
            case AL_BUFFERS_PROCESSED:
                return source.looping ? 0 : source.current;
            default:
                return 0;
        }
    }

    public static float getSourcef(int id, int name) {
        Source source = sources.get(id);
        if (source == null || name != AL_SEC_OFFSET)
            return 0;
        advance(source);
        return (float) position(source);
    }

    public static void play(int id) {
        Source source = sources.get(id);
        if (source == null)
            return;
        advance(source);
        if (source.state != AL_PAUSED) {
            // From the start of what's queued
            source.current = 0;
            source.offset = 0;
        }
        source.state = AL_PLAYING;
        schedule(source);
    }

    public static void pause(int id) {
        Source source = sources.get(id);
        if (source == null || source.state != AL_PLAYING)
            return;
        advance(source);
        source.offset = position(source);
        stopNodes(source);
        source.state = AL_PAUSED;
    }

    public static void stop(int id) {
        Source source = sources.get(id);
        if (source == null)
            return;
        stopNodes(source);
        // (a stopped source has finished all its buffers)
        source.current = source.queue.size();
        source.offset = 0;
        source.state = AL_STOPPED;
    }

    public static void queueBuffers(int id, IntBuffer ids) {
        Source source = sources.get(id);
        if (source == null)
            return;
        advance(source);
        for (int i = ids.position(); i < ids.limit(); i++) {
            int buffer = ids.get(i);
            source.queue.add(buffer);
            // Straight after the rest, if it's still playing them
            if (source.state == AL_PLAYING && !source.ends.isEmpty())
                scheduleOne(source, source.queue.size() - 1, last(source.ends), 0);
        }
    }

    public static void unqueueBuffers(int id, IntBuffer ids) {
        Source source = sources.get(id);
        if (source == null)
            return;
        advance(source);
        while (ids.hasRemaining() && source.current > 0 && !source.queue.isEmpty()) {
            // (its node went when it finished, see advance: the nodes are
            // the queue's from [current] on)
            ids.put(source.queue.remove(0));
            source.current--;
        }
        ids.flip();
    }

    // Playing

    // Start the source's queue playing from [current] (at [offset] into it),
    // now
    private static void schedule(Source source) {
        stopNodes(source);
        double at = now();
        for (int i = source.current; i < source.queue.size(); i++) {
            at = scheduleOne(source, i, at, i == source.current ? source.offset : 0);
        }
        if (source.current >= source.queue.size())
            source.state = AL_STOPPED;
    }

    // Play buffer [index] of the queue at [at] (from [offset] into it),
    // returning when it ends
    private static double scheduleOne(Source source, int index, double at, double offset) {
        Buffer buffer = buffers.get(source.queue.get(index));
        AudioBufferSourceNode node = context.createBufferSource();
        double length = 0;
        if (buffer != null && buffer.audio != null) {
            node.setBuffer(buffer.audio);
            length = Math.max(0, buffer.duration() - offset);
        }
        node.getPlaybackRate().setValue(source.pitch);
        node.setLoop(source.looping);
        node.connect(source.gain);
        if (length > 0 || source.looping)
            node.start(at, Math.max(0, offset));
        source.nodes.add(node);
        source.starts.add(at - offset / source.pitch);
        double end = source.looping ? Double.MAX_VALUE : at + length / source.pitch;
        source.ends.add(end);
        return end;
    }

    // Move [current] on past the buffers that have finished playing, and
    // stop the source once they all have, as OpenAL would have by now
    private static void advance(Source source) {
        if (source.state != AL_PLAYING || source.looping)
            return;
        double time = now();
        while (!source.ends.isEmpty() && time >= source.ends.get(0) && source.current < source.queue.size()) {
            source.current++;
            source.offset = 0;
            source.nodes.remove(0);
            source.starts.remove(0);
            source.ends.remove(0);
        }
        if (source.current >= source.queue.size())
            source.state = AL_STOPPED;
    }

    // How far into its current buffer the source is (in its seconds)
    private static double position(Source source) {
        if (source.state != AL_PLAYING || source.starts.isEmpty())
            return source.offset;
        double into = (now() - source.starts.get(0)) * source.pitch;
        if (source.looping && source.current < source.queue.size()) {
            Buffer buffer = buffers.get(source.queue.get(source.current));
            double length = buffer == null ? 0 : buffer.duration();
            if (length > 0)
                into %= length;
        }
        return Math.max(0, into);
    }

    private static void stopNodes(Source source) {
        for (AudioBufferSourceNode node : source.nodes) {
            stopNode(node);
        }
        source.nodes.clear();
        source.starts.clear();
        source.ends.clear();
    }

    // (a node that never started, for an empty buffer, can't be stopped)
    @JSBody(params = "node", script = "try { node.stop(); } catch (e) {} node.disconnect();")
    private static native void stopNode(AudioBufferSourceNode node);

    private static double last(List<Double> list) {
        return list.get(list.size() - 1);
    }

    /** OpenAL's listener settings: the game only ever puts it in the middle, facing forwards, which WebAudio's is. */
    public static void listener(int name, FloatBuffer values) {
    }
}
