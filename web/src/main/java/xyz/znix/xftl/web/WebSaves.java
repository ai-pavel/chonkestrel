package xyz.znix.xftl.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSString;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the browser version's saves (the profile, the saved game and so
 * on, in /saves in TeaVM's in-memory file system, see PlatformSpecific)
 * in the browser's IndexedDB, so they're there next time. The page
 * (index.html, window.xftlSaves) does the IndexedDB side: it reads them
 * all before the game starts, for [restore], and writes what [sync] sends
 * it.
 * <p>
 * The game writes its files as it does on the desktop; [sync] looks for
 * the ones that changed (or were deleted) since it last looked, every few
 * seconds and when the page is hidden or left.
 */
public final class WebSaves {
    private WebSaves() {
    }

    static final String ROOT = "/saves";

    // Not saved: the mod, unzipped there each time (see WebMain), and the
    // patched XML's cache
    private static final Set<String> SKIPPED = Set.of(ROOT + "/mods", ROOT + "/mod-xml-cache");

    // What each saved file had in it when last saved (its hash), by path
    private static final Map<String, Integer> saved = new HashMap<>();

    /** Write the saves the page read from IndexedDB into /saves. */
    static void restore() throws IOException {
        JSObject files = savedFiles();
        int count = length(files);
        for (int i = 0; i < count; i++) {
            String path = pathAt(files, i);
            byte[] data = Int8Array.create(dataAt(files, i)).copyToJavaArray();
            File file = new File(path);
            file.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(data);
            }
            saved.put(path, hash(data));
        }
        System.out.println("Restored " + count + " saved files");
        forgetSavedFiles();
    }

    /** Send IndexedDB the saves that changed since last time, and delete the ones that are gone. */
    static void sync() {
        Set<String> seen = new HashSet<>();
        try {
            walk(new File(ROOT), seen);
        } catch (IOException e) {
            System.out.println("Couldn't read the saves: " + e);
            return;
        }
        for (String path : new HashSet<>(saved.keySet())) {
            if (!seen.contains(path)) {
                delete(path);
                saved.remove(path);
            }
        }
    }

    private static void walk(File dir, Set<String> seen) throws IOException {
        File[] children = dir.listFiles();
        if (children == null)
            return;
        for (File child : children) {
            String path = child.getPath();
            if (SKIPPED.contains(path))
                continue;
            if (child.isDirectory()) {
                walk(child, seen);
                continue;
            }
            seen.add(path);
            byte[] data;
            try (FileInputStream in = new FileInputStream(child)) {
                data = in.readAllBytes();
            }
            int hash = hash(data);
            Integer old = saved.get(path);
            if (old == null || old != hash) {
                put(path, Int8Array.copyFromJavaArray(data));
                saved.put(path, hash);
            }
        }
    }

    private static int hash(byte[] data) {
        return 31 * Arrays.hashCode(data) + data.length;
    }

    @JSBody(script = "return window.savedFiles || [];")
    private static native JSObject savedFiles();

    @JSBody(script = "window.savedFiles = null;")
    private static native void forgetSavedFiles();

    @JSBody(params = "files", script = "return files.length;")
    private static native int length(JSObject files);

    @JSBody(params = {"files", "i"}, script = "return files[i].path;")
    private static native String pathAt(JSObject files, int i);

    @JSBody(params = {"files", "i"}, script = "return files[i].data;")
    private static native ArrayBuffer dataAt(JSObject files, int i);

    @JSBody(params = {"path", "data"}, script = "window.xftlSaves.put(path, new Uint8Array(data.buffer, data.byteOffset, data.length).slice());")
    private static native void put(String path, Int8Array data);

    @JSBody(params = "path", script = "window.xftlSaves.remove(path);")
    private static native void delete(String path);
}
