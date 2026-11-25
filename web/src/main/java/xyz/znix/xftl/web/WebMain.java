package xyz.znix.xftl.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;
import xyz.znix.xftl.game.MainGame;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The browser version's entry point (see web/build.gradle.kts). The page
 * (index.html) has the player pick their ftl.dat, reads it, and only then
 * starts this, with it in window.ftlDat, and the Chonkestrel mod (zipped,
 * see web/build.gradle.kts) in window.modZip.
 */
public final class WebMain {
    private WebMain() {
    }

    // Where ftl.dat goes in TeaVM's in-memory file system
    private static final String FTL_DAT = "/ftl.dat";

    public static void main(String[] args) throws IOException {
        System.out.println("XFTL in the browser");

        // Put the player's ftl.dat where the game looks for it (see
        // MainGame.findFtlDat), in TeaVM's in-memory file system
        byte[] data = Int8Array.create(ftlDat()).toJavaArray();
        System.out.println("ftl.dat is " + data.length + " bytes");
        try (FileOutputStream out = new FileOutputStream(new File(FTL_DAT))) {
            out.write(data);
        }
        System.setProperty("xftl.datafile-path", FTL_DAT);
        // The game reads its own copy from now on: let the browser free the page's
        data = null;
        forget("ftlDat");

        // The saves from last time, from IndexedDB (see WebSaves)
        WebSaves.restore();

        // The mod goes in the saves' mods folder, as a folder of its own,
        // with an order.txt naming it (see Datafile.loadWithMods)
        ArrayBuffer modZip = modZip();
        if (modZip != null) {
            int files = unzip(Int8Array.create(modZip).toJavaArray(), new File(MODS, MOD_NAME));
            System.out.println("The " + MOD_NAME + " mod: " + files + " files");
            try (FileOutputStream out = new FileOutputStream(new File(MODS, "order.txt"))) {
                out.write((MOD_NAME + "\n").getBytes(StandardCharsets.UTF_8));
            }
            modZip = null;
            forget("modZip");
        }

        MainGame game = new MainGame(new MainGame.CommandLineArgs());
        new WebGameContainer(game).start();
    }

    // The saves' mods folder (see the web version of PlatformSpecific)
    private static final String MODS = "/saves/mods";
    private static final String MOD_NAME = "chonkestrel";

    // Write the files in a zip into [dir], returning how many there were
    private static int unzip(byte[] zip, File dir) throws IOException {
        int count = 0;
        byte[] buffer = new byte[65536];
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory())
                    continue;
                File file = new File(dir, entry.getName());
                file.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(file)) {
                    int read;
                    while ((read = in.read(buffer)) > 0)
                        out.write(buffer, 0, read);
                }
                count++;
            }
        }
        return count;
    }

    @JSBody(script = "return window.ftlDat;")
    private static native ArrayBuffer ftlDat();

    @JSBody(script = "return window.modZip || null;")
    private static native ArrayBuffer modZip();

    // Drop the page's reference to something it loaded for the game (the
    // game having copied it), so its memory can be freed
    @JSBody(params = "name", script = "window[name] = null;")
    private static native void forget(String name);
}
