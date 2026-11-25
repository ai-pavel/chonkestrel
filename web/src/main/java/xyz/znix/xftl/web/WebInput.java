package xyz.znix.xftl.web;

import org.jetbrains.annotations.NotNull;
import org.newdawn.slick.InputListener;
import org.newdawn.slick.KeyListener;
import org.newdawn.slick.MouseListener;
import org.teavm.jso.JSBody;
import org.teavm.jso.dom.events.KeyboardEvent;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.events.WheelEvent;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.browser.Window;
import xyz.znix.xftl.sys.Input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The browser version of the game's input (in place of LWJGLInput): the
 * page's keyboard and mouse events, with the keys numbered as GLFW numbers
 * them, as the game expects (see Input).
 */
public class WebInput implements Input {
    // The furthest the mouse can move between pressing and releasing a
    // button for it to count as a click (as LWJGLInput's)
    private static final int CLICK_DISTANCE = 4;
    // How much one notch of the scroll wheel is worth, as LWJGLInput's
    private static final int SCROLL_SCALE = 120;

    private static final int GLFW_KEY_LAST = 348;

    final List<KeyListener> keyListeners = new ArrayList<>();
    final List<MouseListener> mouseListeners = new ArrayList<>();

    private final HTMLCanvasElement canvas;
    private final int gameWidth;
    private final int gameHeight;

    private int mouseX;
    private int mouseY;
    // Where the mouse is now, from the last mouse event, and where the
    // game last saw it (see update)
    private int rawX;
    private int rawY;
    private int clickX;
    private int clickY;
    private double scrollLeftover;

    private final boolean[] keysDown = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] pendingKeyPresses = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] buttonsDown = new boolean[8];

    public WebInput(HTMLCanvasElement canvas, int gameWidth, int gameHeight) {
        this.canvas = canvas;
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;

        Window window = Window.current();
        window.addEventListener("keydown", evt -> keyDown((KeyboardEvent) evt));
        window.addEventListener("keyup", evt -> keyUp((KeyboardEvent) evt));
        canvas.addEventListener("mousemove", evt -> mouseMoved((MouseEvent) evt));
        canvas.addEventListener("mousedown", evt -> mouseDown((MouseEvent) evt));
        window.addEventListener("mouseup", evt -> mouseUp((MouseEvent) evt));
        canvas.addEventListener("wheel", evt -> wheel((WheelEvent) evt));
        canvas.addEventListener("contextmenu", evt -> evt.preventDefault());
    }

    /** Pass on any mouse movement since the last frame (as LWJGLInput.update). */
    void update() {
        int lastX = mouseX;
        int lastY = mouseY;
        mouseX = rawX;
        mouseY = rawY;
        if (lastX == mouseX && lastY == mouseY)
            return;
        boolean dragging = buttonsDown[MOUSE_LEFT_BUTTON] || buttonsDown[MOUSE_RIGHT_BUTTON];
        for (MouseListener listener : new ArrayList<>(mouseListeners)) {
            if (dragging)
                listener.mouseDragged(lastX, lastY, mouseX, mouseY);
            else
                listener.mouseMoved(lastX, lastY, mouseX, mouseY);
        }
    }

    private void keyDown(KeyboardEvent event) {
        int key = glfwKey(event.getCode());
        boolean shortcut = ctrlOrMeta(event);
        if (!shortcut)
            event.preventDefault();
        if (key >= 0 && !isRepeat(event)) {
            keysDown[key] = true;
            pendingKeyPresses[key] = true;
            for (KeyListener listener : new ArrayList<>(keyListeners))
                listener.keyPressed(key, (char) 0);
        }
        // Typed text, as GLFW's character callback
        String text = event.getKey();
        if (text != null && text.length() == 1 && !shortcut) {
            for (KeyListener listener : new ArrayList<>(keyListeners))
                listener.keyPressed(-1, text.charAt(0));
        }
    }

    private void keyUp(KeyboardEvent event) {
        int key = glfwKey(event.getCode());
        if (key < 0)
            return;
        keysDown[key] = false;
        for (KeyListener listener : new ArrayList<>(keyListeners))
            listener.keyReleased(key, (char) 0);
    }

    private void mouseMoved(MouseEvent event) {
        setRaw(event);
    }

    private void mouseDown(MouseEvent event) {
        setRaw(event);
        update();
        int button = glfwButton(event.getButton());
        if (button < 0)
            return;
        buttonsDown[button] = true;
        clickX = mouseX;
        clickY = mouseY;
        for (MouseListener listener : new ArrayList<>(mouseListeners))
            listener.mousePressed(button, mouseX, mouseY);
    }

    private void mouseUp(MouseEvent event) {
        int button = glfwButton(event.getButton());
        if (button < 0 || !buttonsDown[button])
            return;
        setRaw(event);
        update();
        buttonsDown[button] = false;
        for (MouseListener listener : new ArrayList<>(mouseListeners))
            listener.mouseReleased(button, mouseX, mouseY);
        int dx = mouseX - clickX;
        int dy = mouseY - clickY;
        if (dx * dx + dy * dy <= CLICK_DISTANCE * CLICK_DISTANCE) {
            for (MouseListener listener : new ArrayList<>(mouseListeners))
                listener.mouseClicked(button, mouseX, mouseY, 1);
        }
    }

    private void wheel(WheelEvent event) {
        event.preventDefault();
        // GLFW's scroll is up positive, in notches; the browser's is down
        // positive, in pixels (about 100 a notch)
        double input = -event.getDeltaY() / 100.0 + scrollLeftover;
        int change = (int) (input * SCROLL_SCALE);
        scrollLeftover = input - change / (double) SCROLL_SCALE;
        for (MouseListener listener : new ArrayList<>(mouseListeners))
            listener.mouseWheelMoved(change);
    }

    // The mouse's position in the game, from where it is on the canvas
    // (which the page scales to fit the window)
    private void setRaw(MouseEvent event) {
        double left = rectLeft(canvas);
        double top = rectTop(canvas);
        double width = rectWidth(canvas);
        double height = rectHeight(canvas);
        rawX = (int) ((event.getClientX() - left) * gameWidth / width);
        rawY = (int) ((event.getClientY() - top) * gameHeight / height);
    }

    @JSBody(params = "e", script = "return e.getBoundingClientRect().left;")
    private static native double rectLeft(HTMLCanvasElement e);

    @JSBody(params = "e", script = "return e.getBoundingClientRect().top;")
    private static native double rectTop(HTMLCanvasElement e);

    @JSBody(params = "e", script = "return e.getBoundingClientRect().width;")
    private static native double rectWidth(HTMLCanvasElement e);

    @JSBody(params = "e", script = "return e.getBoundingClientRect().height;")
    private static native double rectHeight(HTMLCanvasElement e);

    @JSBody(params = "e", script = "return e.repeat === true;")
    private static native boolean isRepeat(KeyboardEvent e);

    @JSBody(params = "e", script = "return e.ctrlKey || e.metaKey;")
    private static native boolean ctrlOrMeta(KeyboardEvent e);

    // The browser's buttons are left, middle and right; GLFW's left, right and middle
    private static int glfwButton(int button) {
        switch (button) {
            case 0: return 0;
            case 1: return 2;
            case 2: return 1;
            default: return -1;
        }
    }

    private static final Map<String, Integer> KEYS = new HashMap<>();

    static {
        for (char c = 'A'; c <= 'Z'; c++)
            KEYS.put("Key" + c, (int) c);
        for (char c = '0'; c <= '9'; c++) {
            KEYS.put("Digit" + c, (int) c);
            KEYS.put("Numpad" + c, 320 + (c - '0'));
        }
        for (int i = 1; i <= 25; i++)
            KEYS.put("F" + i, 289 + i);
        KEYS.put("Space", 32);
        KEYS.put("Quote", 39);
        KEYS.put("Comma", 44);
        KEYS.put("Minus", 45);
        KEYS.put("Period", 46);
        KEYS.put("Slash", 47);
        KEYS.put("Semicolon", 59);
        KEYS.put("Equal", 61);
        KEYS.put("BracketLeft", 91);
        KEYS.put("Backslash", 92);
        KEYS.put("BracketRight", 93);
        KEYS.put("Backquote", 96);
        KEYS.put("IntlBackslash", 161);
        KEYS.put("Escape", 256);
        KEYS.put("Enter", 257);
        KEYS.put("NumpadEnter", 335);
        KEYS.put("Tab", 258);
        KEYS.put("Backspace", 259);
        KEYS.put("Insert", 260);
        KEYS.put("Delete", 261);
        KEYS.put("ArrowRight", 262);
        KEYS.put("ArrowLeft", 263);
        KEYS.put("ArrowDown", 264);
        KEYS.put("ArrowUp", 265);
        KEYS.put("PageUp", 266);
        KEYS.put("PageDown", 267);
        KEYS.put("Home", 268);
        KEYS.put("End", 269);
        KEYS.put("ShiftLeft", 340);
        KEYS.put("ControlLeft", 341);
        KEYS.put("AltLeft", 342);
        KEYS.put("MetaLeft", 343);
        KEYS.put("ShiftRight", 344);
        KEYS.put("ControlRight", 345);
        KEYS.put("AltRight", 346);
        KEYS.put("MetaRight", 347);
        KEYS.put("ContextMenu", 348);
    }

    private static int glfwKey(String code) {
        Integer key = code == null ? null : KEYS.get(code);
        return key == null ? -1 : key;
    }

    @Override
    public int getMouseX() {
        return mouseX;
    }

    @Override
    public int getMouseY() {
        return mouseY;
    }

    @Override
    public boolean isMouseButtonDown(int button) {
        return button >= 0 && button < buttonsDown.length && buttonsDown[button];
    }

    @Override
    public boolean isKeyPressed(int key) {
        if (key < 0 || key > GLFW_KEY_LAST)
            return false;
        boolean old = pendingKeyPresses[key];
        pendingKeyPresses[key] = false;
        return old;
    }

    @Override
    public boolean isKeyDown(int key) {
        return key >= 0 && key <= GLFW_KEY_LAST && keysDown[key];
    }

    @Override
    public void addListener(@NotNull InputListener listener) {
        keyListeners.add(listener);
        mouseListeners.add(listener);
    }

    @Override
    public void removeAllListeners() {
        keyListeners.clear();
        mouseListeners.clear();
    }

    @Override
    public void clearInputPressedRecord() {
        java.util.Arrays.fill(pendingKeyPresses, false);
    }
}
