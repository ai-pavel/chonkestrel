package xyz.znix.xftl.web.teavm;

import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts the game's own resources (its shaders, translations, UI layouts and
 * so on, from src/main/resources) into the browser version, for
 * ClassLoader.getResourceAsStream to find: TeaVM only includes the ones it's
 * told about. Their names are listed in web-resources.txt, which the build
 * writes (see web/build.gradle.kts).
 */
public class GameResources implements ResourceSupplier {
    @Override
    public String[] supplyResources(ResourceSupplierContext context) {
        List<String> names = new ArrayList<>();
        try (InputStream in = context.getClassLoader().getResourceAsStream("web-resources.txt")) {
            if (in == null)
                throw new IllegalStateException("No web-resources.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank())
                    names.add(line.trim());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names.toArray(new String[0]);
    }
}
