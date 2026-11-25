package xyz.znix.xftl.web.teavm;

import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.MethodHolder;
import org.teavm.model.PrimitiveType;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A TeaVM compiler plugin for the browser version (registered in
 * META-INF/services): it empties the methods of the game (and the
 * libraries it uses) that only work on the desktop, and that the browser
 * version never runs, as they use parts of Java TeaVM hasn't got. Each
 * returns nothing (null, 0 or false) instead.
 * <p>
 * For a class here, the methods named are emptied (all of their overloads),
 * or every method in it, for "*".
 */
public class DesktopOnlyStubs implements TeaVMPlugin, ClassHolderTransformer {
    private static final Map<String, Set<String>> STUBS = Map.ofEntries(
            // Mods: the cache of patched XML (MD5 hashes), XSLT patches
            // (Saxon, which brings an HTTP client), zipped mods and their
            // raw appends. The prototype runs vanilla FTL.
            Map.entry("xyz.znix.xftl.modding.PatchedXMLCache", Set.of("<clinit>", "lookup", "save")),
            Map.entry("xyz.znix.xftl.modding.PatchedXMLCache$Companion", Set.of("createDigest")),
            Map.entry("xyz.znix.xftl.modding.ModFileSource", Set.of("addCacheContribution")),
            Map.entry("xyz.znix.xftl.modding.ReParseXMLFileSource", Set.of("addCacheContribution")),
            Map.entry("xyz.znix.xftl.modding.VanillaFileSource", Set.of("addCacheContribution")),
            Map.entry("xyz.znix.xftl.modding.XMLPatchFileSource", Set.of("addCacheContribution")),
            Map.entry("xyz.znix.xftl.modding.XMLRawAppendFileSource", Set.of("addCacheContribution", "open")),
            Map.entry("xyz.znix.xftl.modding.XSLTransformFileSource", Set.of("addCacheContribution", "open", "openXML")),
            Map.entry("xyz.znix.xftl.modding.SlipstreamZipMod", Set.of("<init>")),
            // The debug console's commands, found by reflection
            Map.entry("xyz.znix.xftl.devutil.ConsoleCommandProvider", Set.of("<init>")),
            // Finding and picking ftl.dat on the computer: the page has the
            // player pick it before the game starts
            Map.entry("xyz.znix.xftl.game.MainGame",
                    Set.of("showProfileError", "switchToDatafileSelect", "readSavedFtlDat")),
            Map.entry("xyz.znix.xftl.sys.FTLFinder", Set.of("findNextToGame", "findInstallations", "findRunningInstance")),
            Map.entry("xyz.znix.xftl.sys.PackagedGame", Set.of("isPackaged")),
            // TrueType text (with STB), only on the screen-too-small screen
            Map.entry("xyz.znix.xftl.sys.TrueTypeText", Set.of("<init>", "render")),
            // WAV sounds (with Java Sound): there's no sound yet
            Map.entry("xyz.znix.xftl.game.RealSoundManager", Set.of("getWAV")),
            Map.entry("org.newdawn.slick.openal.WaveData", Set.of("create")),
            // Slick's ImageIO image loader, which the PNGs don't use
            Map.entry("org.newdawn.slick.opengl.ImageIOImageData", Set.of("*")),
            // SLF4J setting itself up (see getLogger below)
            Map.entry("org.slf4j.LoggerFactory", Set.of("<clinit>"))
    );

    private static final String IMAGE_DATA_FACTORY = "org.newdawn.slick.opengl.ImageDataFactory";

    // Methods with bodies of their own, rather than returning nothing
    private static final Map<String, Consumer<ProgramEmitter>> REPLACED = Map.of(
            // Slick picks its PNG loader (not ImageIO) from a system
            // property, read with the security manager's help
            "org.newdawn.slick.opengl.ImageDataFactory.checkProperty", pe -> {
                pe.setField(IMAGE_DATA_FACTORY, "usePngLoader", pe.constant(1));
                pe.setField(IMAGE_DATA_FACTORY, "pngLoaderPropertyChecked", pe.constant(1));
                pe.exit();
            },
            // Slipstream's logger: one that logs nothing, without SLF4J
            // looking for a logger on the classpath
            "org.slf4j.LoggerFactory.getLogger", pe -> pe.getField("org.slf4j.helpers.NOPLogger", "NOP_LOGGER",
                    ValueType.object("org.slf4j.helpers.NOPLogger")).returnValue()
    );

    @Override
    public void install(TeaVMHost host) {
        host.add(this);
    }

    @Override
    public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        for (MethodHolder method : cls.getMethods()) {
            Consumer<ProgramEmitter> body = REPLACED.get(cls.getName() + "." + method.getName());
            if (body != null && method.getProgram() != null)
                body.accept(ProgramEmitter.create(method, context.getHierarchy()));
        }

        Set<String> names = STUBS.get(cls.getName());
        if (names == null)
            return;
        for (MethodHolder method : cls.getMethods()) {
            if (method.getProgram() == null)
                continue;
            if (!names.contains("*") && !names.contains(method.getName()))
                continue;
            ProgramEmitter pe = ProgramEmitter.create(method, context.getHierarchy());
            ValueType type = method.getResultType();
            if (type == ValueType.VOID) {
                pe.exit();
            } else if (type instanceof ValueType.Primitive) {
                PrimitiveType kind = ((ValueType.Primitive) type).getKind();
                switch (kind) {
                    case LONG:
                        pe.constant(0L).returnValue();
                        break;
                    case FLOAT:
                        pe.constant(0f).returnValue();
                        break;
                    case DOUBLE:
                        pe.constant(0.0).returnValue();
                        break;
                    default:
                        pe.constant(0).returnValue();
                        break;
                }
            } else {
                pe.constantNull(type).returnValue();
            }
        }
    }
}
