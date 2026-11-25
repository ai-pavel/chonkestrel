package xyz.znix.xftl.web;

import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.newdawn.slick.KeyListener;
import org.newdawn.slick.MouseListener;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.webgl.WebGL2RenderingContext;
import xyz.znix.xftl.rendering.Cursor;
import xyz.znix.xftl.rendering.Graphics;
import xyz.znix.xftl.sys.Game;
import xyz.znix.xftl.sys.GameContainer;
import xyz.znix.xftl.sys.Input;

import java.nio.file.Path;
import java.util.List;

/**
 * The browser version of the game's window (in place of LWJGLGameContainer):
 * a canvas the game's drawn on with WebGL 2, the game's own size
 * (1920x1080) and scaled by the page to fit the browser window, updated and
 * drawn each animation frame.
 */
public class WebGameContainer implements GameContainer {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    private final Game game;
    private final HTMLCanvasElement canvas;
    private final WebInput input;
    private Graphics g;
    private double lastTime = -1;
    // When the saves were last sent to IndexedDB (see WebSaves), in ms
    private double lastSaveSync = 0;
    private static final double SAVE_SYNC_INTERVAL = 5000;
    private boolean running = true;

    @Nullable
    private Function1<? super List<? extends Path>, kotlin.Unit> onFilesDropped;

    public WebGameContainer(Game game) {
        this.game = game;
        canvas = (HTMLCanvasElement) Window.current().getDocument().getElementById("game");
        canvas.setWidth(WIDTH);
        canvas.setHeight(HEIGHT);
        input = new WebInput(canvas, WIDTH, HEIGHT);
    }

    public void start() {
        WebGL.gl = (WebGL2RenderingContext) canvas.getContext("webgl2", contextAttributes());
        if (WebGL.gl == null)
            throw new IllegalStateException("This browser has no WebGL 2");

        // As the desktop version sets OpenGL up (see LWJGLGameContainer.initOpenGL)
        WebGL.gl.disable(WebGL2RenderingContext.DEPTH_TEST);
        WebGL.gl.clearColor(0, 0, 0, 0);
        WebGL.gl.enable(WebGL2RenderingContext.BLEND);
        WebGL.gl.blendFunc(WebGL2RenderingContext.SRC_ALPHA, WebGL2RenderingContext.ONE_MINUS_SRC_ALPHA);
        WebGL.gl.viewport(0, 0, WIDTH, HEIGHT);

        g = new Graphics();
        g.markCurrentImageTransformSource();

        if (game instanceof KeyListener)
            input.keyListeners.add((KeyListener) game);
        if (game instanceof MouseListener)
            input.mouseListeners.add((MouseListener) game);

        try {
            game.init(this);
        } catch (Throwable e) {
            System.out.println("The game failed to start:");
            printChain(e);
            throw new RuntimeException("The game failed to start", e);
        }
        Window.requestAnimationFrame(this::frame);

        // Keep the saves when the page is hidden (another tab, minimised)
        // or left, as well as every few seconds (see frame)
        Window.current().getDocument().addEventListener("visibilitychange", evt -> WebSaves.sync());
        Window.current().addEventListener("pagehide", evt -> WebSaves.sync());
    }

    private void frame(double time) {
        if (!running)
            return;
        float dt = lastTime < 0 ? 0f : (float) ((time - lastTime) / 1000.0);
        lastTime = time;
        // (a long pause, such as a background tab, isn't one big step)
        dt = Math.min(dt, 0.1f);

        input.update();
        try {
            game.update(this, dt);
        } catch (Exception ex) {
            System.out.println("Exception during game update");
            printChain(ex);
        }
        try {
            WebGL.gl.viewport(0, 0, WIDTH, HEIGHT);
            game.render(this, g);
        } catch (Exception ex) {
            System.out.println("Exception during rendering");
            printChain(ex);
        }
        if (time - lastSaveSync > SAVE_SYNC_INTERVAL) {
            lastSaveSync = time;
            WebSaves.sync();
        }
        Window.requestAnimationFrame(this::frame);
    }

    // Print an exception and its causes, each with its stack
    static void printChain(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            System.out.println((t == e ? "" : "Caused by: ") + t);
            for (StackTraceElement frame : t.getStackTrace())
                System.out.println("    at " + frame);
            // (a JavaScript error, such as running out of stack, has its
            // own stack)
            org.teavm.jso.JSObject js = org.teavm.jso.JSExceptions.getJSException(t);
            if (js != null)
                logJsStack(js);
        }
    }

    @JSBody(params = "e", script = "var s = e && e.stack ? e.stack.split('\\n') : [];"
            + "console.log('JavaScript stack (' + s.length + ' lines):\\n' + s.slice(0, 60).join('\\n'));")
    private static native void logJsStack(org.teavm.jso.JSObject e);

    @JSBody(script = "return { alpha: false, stencil: true, antialias: false, preserveDrawingBuffer: false };")
    private static native org.teavm.jso.JSObject contextAttributes();

    @NotNull
    @Override
    public Input getInput() {
        return input;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void exit() {
        running = false;
        game.shutdown();
        // There's no window to close: back to the page's start, to pick
        // ftl.dat again, once the saves are kept
        WebSaves.sync();
        reloadWhenSaved();
    }

    @JSBody(script = "window.xftlSaves.whenSaved().then(() => window.location.reload());")
    private static native void reloadWhenSaved();

    @Override
    public void setCursor(@Nullable Cursor cursor) {
    }

    @Override
    public float getPixelScale() {
        return 1f;
    }

    @Override
    public boolean isDefaultSize() {
        return false;
    }

    @NotNull
    @Override
    public List<xyz.znix.xftl.sys.Screen> getScreens() {
        return List.of();
    }

    @Nullable
    @Override
    public Function1<List<? extends Path>, kotlin.Unit> getOnFilesDropped() {
        //noinspection unchecked
        return (Function1<List<? extends Path>, kotlin.Unit>) (Function1<?, ?>) onFilesDropped;
    }

    @Override
    public void setOnFilesDropped(@Nullable Function1<? super List<? extends Path>, kotlin.Unit> listener) {
        onFilesDropped = listener;
    }

    @NotNull
    @Override
    public List<xyz.znix.xftl.sys.DisplayMode> getDisplayModes() {
        return List.of();
    }

    @Nullable
    @Override
    public xyz.znix.xftl.sys.DisplayMode getCurrentDisplayMode() {
        return null;
    }

    @Override
    public void setDisplay(@NotNull xyz.znix.xftl.sys.DisplayMode mode) {
    }
}
