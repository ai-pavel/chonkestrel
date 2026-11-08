package xyz.znix.xftl.game;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.util.InputAdapter;
import xyz.znix.xftl.BuildInfo;
import xyz.znix.xftl.Datafile;
import xyz.znix.xftl.SILFontLoader;
import xyz.znix.xftl.VanillaDatafile;
import xyz.znix.xftl.Utils;
import xyz.znix.xftl.XftlDebug;
import xyz.znix.xftl.devutil.DebugConsole;
import xyz.znix.xftl.devutil.IconDirector;
import xyz.znix.xftl.devutil.ScreenshotDirector;
import xyz.znix.xftl.devutil.AutoplayDirector;
import xyz.znix.xftl.devutil.VideoDirector;
import xyz.znix.xftl.hangar.EditableShip;
import xyz.znix.xftl.hangar.SelectShipState;
import xyz.znix.xftl.rendering.Colour;
import xyz.znix.xftl.rendering.Cursor;
import xyz.znix.xftl.rendering.Graphics;
import xyz.znix.xftl.rendering.ShaderProgramme;
import xyz.znix.xftl.sys.DatafileSelectState;
import xyz.znix.xftl.sys.FTLFinder;
import xyz.znix.xftl.sys.Game;
import xyz.znix.xftl.sys.DisplayMode;
import xyz.znix.xftl.sys.GameContainer;
import xyz.znix.xftl.sys.PlatformSpecific;
import xyz.znix.xftl.sys.ResourceContext;
import xyz.znix.xftl.sys.Screen;
import xyz.znix.xftl.sys.ScreenTooSmallState;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class MainGame implements Game {
    private VanillaDatafile vanillaDatafile;
    private Datafile datafile;

    // The version and build date label in the corner, see renderBuildLabel
    private final ResourceContext buildLabelResources = new ResourceContext();
    private SILFontLoader buildLabelFont;
    private static final int BUILD_LABEL_MARGIN = 4;

    // The FPS counter in debug mode (see renderFps): the frames drawn since
    // it was last worked out, when that was, and what it came to
    private static final long FPS_PERIOD_NANOS = 500_000_000L;
    private int fpsFrames = 0;
    private long fpsSince = 0;
    private int fps = 0;
    private static final Colour BUILD_LABEL_COLOUR = new Colour(1f, 1f, 1f, 0.5f);
    private final CommandLineArgs commandLineArgs;

    private GameContainer gameContainer;

    private GameState currentState;

    private InGameState.GameContent content;

    private SaveProfile profile;

    private Cursor currentCursor;

    public MainGame(CommandLineArgs args) {
        this.commandLineArgs = args;
    }

    /**
     * The window the game's in, for the display options.
     */
    public GameContainer getGameContainer() {
        return gameContainer;
    }

    @Override
    public void init(@NotNull GameContainer gc) throws SlickException {
        gameContainer = gc;

        // Make sure the saves directory exists, as a fresh install has none
        try {
            Files.createDirectories(PlatformSpecific.INSTANCE.getSaveGamePath());
        } catch (IOException e) {
            System.err.println("Failed to create the saves directory: " + e);
        }

        profile = loadProfile();

        // Use the size the player picked in the display options, if this
        // screen still has it. Otherwise (on a smaller screen, say) it stays
        // at the recommended size, and that's what's remembered instead.
        if (gc.isDefaultSize() && profile.getDisplayWindowSize() != null) {
            kotlin.Pair<Integer, Integer> size = profile.getDisplayWindowSize();
            DisplayMode saved = null;
            for (DisplayMode mode : gc.getDisplayModes()) {
                if (mode.getWindowWidth() == size.getFirst() && mode.getWindowHeight() == size.getSecond()) {
                    saved = mode;
                    break;
                }
            }
            if (saved != null) {
                gc.setDisplay(saved);
            } else {
                System.out.println("The saved display size (a " + size.getFirst() + "x" + size.getSecond()
                        + " window) doesn't fit this screen, using the recommended size");
                profile.setDisplayWindowSize(null);
            }
        }

        // If the screen is too small for the big ships' interface, say so
        // instead of starting the game
        List<Screen> screens = ScreenTooSmallState.screensIfTooSmall(gc);
        if (screens != null) {
            setCurrentState(new ScreenTooSmallState(this, screens));
            return;
        }

        // If we don't have a valid ftl.dat file to use, open the selection
        // screen, which calls datafileSelected once the player picks one
        if (!loadDatafile()) {
            return;
        }

        startFirstState();
    }

    /**
     * Start the game once the player has picked an ftl.dat file, in the
     * datafile selection screen.
     */
    public void datafileSelected() {
        if (loadDatafile()) {
            startFirstState();
        }
    }

    private void startFirstState() {
        GameContainer gc = gameContainer;

        if (XftlDebug.INSTANCE.getIconFile() != null) {
            // XFTL_ICON renders the default ship, as it looks in the
            // hangar, for the game's icon, then quits
            switchToShipSelect();
            IconDirector.install(this, gc);
        } else if (commandLineArgs.newGameShip != null) {
            // Switch right into a new game
            startNewGame(commandLineArgs.newGameShip, Difficulty.NORMAL, null);
        } else if (XftlDebug.INSTANCE.getSkipMenu()) {
            // Skip the main menu: continue the saved game if there is one, or
            // start with the ship the hangar would pick by default
            if (!continueSavedGame()) {
                startDefaultNewGame();
            }

            // XFTL_SCREENSHOTS takes screenshots of this game, then quits,
            // and XFTL_VIDEO records a battle
            ScreenshotDirector.install(this, gc);
            VideoDirector.install(this, gc);
            // and XFTL_AUTOPLAY plays it by itself, to find crashes
            AutoplayDirector.install(this, gc);
        } else if (commandLineArgs.debugLoad != null) {
            Path path = DebugConsole.DEBUG_SAVE_DIR.resolve(commandLineArgs.debugLoad + ".xml");
            Document doc;
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                SAXBuilder builder = new SAXBuilder();
                builder.setExpandEntities(true);
                doc = builder.build(reader);
            } catch (IOException | JDOMException ex) {
                throw new RuntimeException("Failed to load save", ex);
            }

            loadSavedGame(doc);
        } else {
            switchToMainMenu();
        }
    }

    @Override
    public void shutdown() {
        // The state and content might be null if there was an exception during
        // initialisation, as this will be run in a finally block.
        // If we throw an exception here, it'll hide the original one that
        // caused the crash in the first place.
        // Save the game, to continue it next time
        if (currentState instanceof InGameState) {
            ContinueSave.save((InGameState) currentState);
        }

        if (currentState != null) {
            currentState.shutdown();
        }

        if (content != null) {
            content.freeResources();
        }
    }

    public void switchToShipSelect() {
        // This may have been called by DatafileSelectState
        if (vanillaDatafile == null) {
            loadDatafile();
        }

        SelectShipState state = new SelectShipState(datafile, this);
        setCurrentState(state);
    }

    /**
     * Go to the main menu. A game in progress is saved first, to continue
     * it from the menu.
     */
    public void switchToMainMenu() {
        if (currentState instanceof InGameState) {
            ContinueSave.save((InGameState) currentState);
        }

        // The menu is silent
        if (content.sounds instanceof RealSoundManager sounds) {
            sounds.stopMusic();
        }

        setCurrentState(new MainMenuState(this, content));
    }

    /**
     * Continue the saved game, from the main menu. Returns false if it
     * couldn't be loaded (and so has been moved out of the way).
     */
    public boolean continueFromMainMenu() {
        return continueSavedGame();
    }

    /**
     * Start a new game with the ship the hangar would pick by default (the
     * Chonkestrel, with the mod), on normal.
     */
    public void startDefaultNewGame() {
        startNewGame(content.blueprintManager.getDefaultPlayerShip(), Difficulty.NORMAL, null);
    }

    /**
     * Start a new game from the main menu's new game window, with the ship
     * the hangar would pick by default, on {@code difficulty}, with the crew names
     * from {@code namePreset}.
     */
    public void startNewGameFromMenu(Difficulty difficulty, String namePreset) {
        startNewGame(content.blueprintManager.getDefaultPlayerShip(), difficulty, null, namePreset);
    }

    public void switchToDatafileSelect() {
        setCurrentState(new DatafileSelectState(this));
    }

    /**
     * Load the game saved for continuing (see ContinueSave), if there is
     * one. Returns false if there isn't, or it can't be loaded.
     */
    private boolean continueSavedGame() {
        Document saved = ContinueSave.load();
        if (saved == null) {
            return false;
        }

        try {
            loadSavedGame(saved);
            return true;
        } catch (Exception ex) {
            System.out.println("Failed to load the saved game, starting a new one");
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
            ContinueSave.discardBroken();
            return false;
        }
    }

    public void startNewGame(@NotNull String shipName, Difficulty difficulty, EditableShip customised) {
        startNewGame(shipName, difficulty, customised, null);
    }

    /**
     * Start a new game, with the crew names from {@code namePreset} (see
     * CrewNameManager), or from the one XFTL_NAMES picks if it's null.
     */
    public void startNewGame(@NotNull String shipName, Difficulty difficulty, EditableShip customised,
                             @Nullable String namePreset) {
        // A new game replaces the saved one
        ContinueSave.delete();
        InGameState inGameState = new InGameState(this, content, shipName, difficulty, customised, namePreset);
        setCurrentState(inGameState);
    }

    public void restartGame() {
        InGameState state = (InGameState) currentState;
        startNewGame(state.getPlayer().getName(), state.getDifficulty(), state.getPlayer().getCustomised(),
                state.getNamePreset());
    }

    public void loadSavedGame(Document savedGame) {
        InGameState inGameState = new InGameState(this, content, savedGame);
        setCurrentState(inGameState);
    }

    @Override
    public void update(@NotNull GameContainer gc, float dt) throws SlickException {
        currentState.update(gc, dt);

        // If the profile needs saving, do that now. This means that if there's
        // a bunch of changes in the same update, we won't save multiple times.
        if (profile.getDirty()) {
            saveProfile();
        }
    }

    @Override
    public void render(@NotNull GameContainer gc, Graphics g) throws SlickException {
        // When we use shaders, we have to transform from pixels to NDC
        // If this is set wrong, all the text etc will be transformed wrong.
        ShaderProgramme.getSHADER_SCREEN_SIZE().set(gameContainer.getWidth(), gameContainer.getHeight());

        // Reset the transform from last frame, in case there was a transform
        // call that wasn't inside a pushTransform block.
        g.loadIdentityMatrix();

        currentState.render(gc, g);

        // Check there aren't any mismatched pushTransform/popTransform calls.
        g.checkNoPushedTransforms();

        renderBuildLabel(gc);
        renderFps(gc);

        // Update the mouse cursor, if required.
        Cursor newCursor = currentState.getCurrentCursor();
        if (newCursor != currentCursor) {
            gc.setCursor(newCursor);
            currentCursor = newCursor;
        }
    }

    /**
     * Show the version and build date in small text, in the bottom-right
     * corner. This uses one of the game's fonts, so it waits until there's
     * a datafile to load it from. It's left out of the screenshots and
     * videos for the site.
     */
    private void renderBuildLabel(GameContainer gc) {
        if (datafile == null || XftlDebug.INSTANCE.getRecording())
            return;

        if (buildLabelFont == null) {
            buildLabelFont = new SILFontLoader(buildLabelResources, datafile, datafile.get("fonts/JustinFont8.font"));
        }

        buildLabelFont.drawStringLeftAligned(
                gc.getWidth() - BUILD_LABEL_MARGIN, gc.getHeight() - BUILD_LABEL_MARGIN,
                BuildInfo.INSTANCE.getLabel(), BUILD_LABEL_COLOUR
        );
    }

    /**
     * In debug mode (and so in the playtest), show how many frames a second
     * the game's drawing, in the bottom-left corner, in the same small font
     * as the build label. It's counted over half a second at a time.
     */
    private void renderFps(GameContainer gc) {
        if (buildLabelFont == null || !XftlDebug.INSTANCE.getEnabled() || XftlDebug.INSTANCE.getRecording())
            return;

        long now = System.nanoTime();
        if (fpsSince == 0)
            fpsSince = now;
        fpsFrames++;
        if (now - fpsSince >= FPS_PERIOD_NANOS) {
            fps = Math.round(fpsFrames * 1e9f / (now - fpsSince));
            fpsFrames = 0;
            fpsSince = now;
        }

        buildLabelFont.drawString(BUILD_LABEL_MARGIN, gc.getHeight() - BUILD_LABEL_MARGIN, fps + " FPS",
                BUILD_LABEL_COLOUR);
    }

    @Override
    public String getTitle() {
        return "Project Chonkestrel";
    }

    public SaveProfile getProfile() {
        return profile;
    }

    /**
     * Save the current game to XML, and re-load it.
     * <p>
     * This is only for development use, to find bugs in the serialisation logic!
     */
    public boolean doSaveLoadGame() {
        InGameState oldGame = (InGameState) currentState;
        InGameState newGame;

        Document savedGame;
        try {
            savedGame = oldGame.saveGameState();
            newGame = new InGameState(this, content, savedGame);
        } catch (Exception ex) {
            oldGame.debugFailedSaveRestore();
            ex.printStackTrace();
            return false;
        }

        // Only change the state once we've done the save-load, so if
        // there's an exception while loading the saved state we stay
        // on the old state for ease of debugging.
        setCurrentState(newGame);

        // Copy over some basic UI stuff
        newGame.debugContinuousSaveRestore(oldGame);

        return true;
    }

    public GameState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(GameState currentState) {
        if (this.currentState != null) {
            this.currentState.shutdown();
        }

        this.currentState = currentState;

        gameContainer.getInput().removeAllListeners();
        gameContainer.getInput().addListener(currentState);

        // Forget the keys pressed in the last state, which might not have
        // checked for them (such as escape in the main menu), so they
        // don't carry over, and open the pause menu in a game, say
        gameContainer.getInput().clearInputPressedRecord();

        currentState.init(gameContainer);
    }

    public void quitGame() {
        // Do we need to do anything else?
        gameContainer.exit();
    }

    private boolean loadDatafile() {
        Path ftlDat = findFtlDat();
        if (ftlDat == null) {
            switchToDatafileSelect();
            return false;
        }
        vanillaDatafile = new VanillaDatafile(ftlDat.toFile());

        // TODO make mods adjustable in the ship select screen
        datafile = Datafile.Companion.loadWithMods(vanillaDatafile);

        // Load the game content immediately - this will work until
        // we support mods or turning Advanced Edition on or off.
        content = new InGameState.GameContent(datafile, true);

        return true;
    }

    public static Path findFtlDat() {
        // Let the user override the path with system properties
        String override = System.getProperty("xftl.datafile-path");
        if (override != null) {
            return Path.of(override);
        }
        override = System.getenv("XFTL_DATAFILE");
        if (override != null) {
            return Path.of(override);
        }

        // If the player put ftl.dat (or the game) next to this game, use that
        Path local = FTLFinder.INSTANCE.findNextToGame();
        if (local != null) {
            return local;
        }

        // There's a text file which contains the path to the ftl.dat file
        Path ftlPathFile = PlatformSpecific.INSTANCE.getFtlDatPathFile();
        Path saved = readSavedFtlDat(ftlPathFile);
        if (saved != null) {
            return saved;
        }

        // If there's exactly one FTL installation, use it (and remember
        // it). If there are several, the player picks one in the datafile
        // selection screen.
        List<FTLFinder.Installation> installations = FTLFinder.INSTANCE.findInstallations();
        if (installations.size() == 1) {
            Path found = installations.get(0).getPath();
            System.out.println("Found FTL: " + installations.get(0));
            saveFtlDatPath(found);
            return found;
        }

        if (!Files.isRegularFile(ftlPathFile)) {
            // Create the file, so the user can find and edit it
            try {
                Files.writeString(ftlPathFile, "Put the path to ftl.dat here", StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("Failed to write placeholder to: " + ftlPathFile);
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * Remember the ftl.dat file to use, in the file the game looks for it
     * in next time.
     */
    public static void saveFtlDatPath(@NotNull Path ftlDat) {
        Path ftlPathFile = PlatformSpecific.INSTANCE.getFtlDatPathFile();
        try {
            Files.createDirectories(ftlPathFile.getParent());
            Files.writeString(ftlPathFile, ftlDat.toAbsolutePath().toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to save the ftl.dat path to: " + ftlPathFile);
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    private static @Nullable Path readSavedFtlDat(Path ftlPathFile) {
        if (!Files.isRegularFile(ftlPathFile)) {
            return null;
        }

        String rawPath;
        try {
            rawPath = Files.readString(ftlPathFile).strip();
        } catch (IOException e) {
            // Abort in this case, since there's probably something valid there.
            throw new RuntimeException(e);
        }

        Path path;
        try {
            path = Path.of(rawPath);
        } catch (InvalidPathException ignored) {
            System.err.printf("Invalid ftl.dat path (could not parse): '%s'%n", rawPath);
            return null;
        }

        // This can be the game's directory (or app) too
        Path ftlDat = FTLFinder.INSTANCE.resolveDatafile(path);
        if (ftlDat == null) {
            System.err.printf("Invalid ftl.dat path: '%s'%n", rawPath);
            return null;
        }

        return ftlDat;
    }

    private SaveProfile loadProfile() {
        Path profilePath = PlatformSpecific.INSTANCE.getSaveProfilePath();

        if (!Files.exists(profilePath)) {
            return SaveProfile.createBlank();
        }

        // If the profile file does exist, either load it or fail.
        // We *really* don't want to save over the top of it.
        SaveProfile profile = SaveProfile.load(profilePath);

        if (profile == null) {
            // TODO some better behaviour for if/when users encounter this
            JOptionPane.showMessageDialog(
                    null,
                    "Failed to load profile at: " + profilePath,
                    "Failed to load profile",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(0);
        }

        return profile;
    }

    private void saveProfile() {
        // First, save to a temporary file
        Path tempFile = PlatformSpecific.INSTANCE.getSaveGamePath().resolve("profile-save-temp.xml");
        Document doc = profile.save();

        // If we fail to save, don't try again every frame.
        profile.markSaveComplete();

        // Make sure the directory exists
        if (!Files.exists(tempFile.getParent())) {
            try {
                Files.createDirectories(tempFile.getParent());
            } catch (IOException e) {
                // Fine to crash, should happen on startup when we try
                // and save the new, blank profile.
                // If we don't crash, the player might try and play without
                // being able to save their game.
                throw new RuntimeException("Failed to create savegame directory", e);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            XMLOutputter xmlOutput = new XMLOutputter(Format.getPrettyFormat());
            xmlOutput.output(doc, writer);

            // Overwrite the main save file with the newly-written one
            // On most OSes this should be atomic, so we shouldn't be able
            // to get a corrupted profile with this.
            Files.move(tempFile, PlatformSpecific.INSTANCE.getSaveProfilePath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            // Hopefully the profile will be saved again, and succeed.
            System.err.println("Failed to save profile:");
            ex.printStackTrace(System.err);
        }
    }

    public static abstract class GameState extends InputAdapter {
        public void init(@NotNull GameContainer container) {
        }

        public abstract void shutdown();

        public abstract void update(@NotNull GameContainer container, float delta) throws SlickException;

        public abstract void render(@NotNull GameContainer container, @NotNull Graphics g) throws SlickException;

        @Nullable
        public Cursor getCurrentCursor() {
            return null;
        }
    }

    public static class CommandLineArgs {
        // If a new game should be started, this is the name of the ship to use.
        public String newGameShip;

        // If a save created with the debug 'save' command should be loaded, this is it's name.
        public String debugLoad;
    }
}
