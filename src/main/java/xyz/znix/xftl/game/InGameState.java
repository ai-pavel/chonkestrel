package xyz.znix.xftl.game;

import kotlin.random.Random;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.util.InputAdapter;
import xyz.znix.xftl.*;
import xyz.znix.xftl.ai.ShipAI;
import xyz.znix.xftl.augments.AugZoltanShield;
import xyz.znix.xftl.augments.AugmentBlueprint;
import xyz.znix.xftl.crew.*;
import xyz.znix.xftl.devutil.AutoplayDirector;
import xyz.znix.xftl.devutil.DebugConsole;
import xyz.znix.xftl.devutil.DebugFlagManager;
import xyz.znix.xftl.drones.AbstractExternalDrone;
import xyz.znix.xftl.environment.AntiShipBattery;
import xyz.znix.xftl.hangar.EditableShip;
import xyz.znix.xftl.layout.Room;
import xyz.znix.xftl.math.ConstPoint;
import xyz.znix.xftl.math.IPoint;
import xyz.znix.xftl.math.Point;
import xyz.znix.xftl.math.RoomPoint;
import xyz.znix.xftl.rendering.*;
import xyz.znix.xftl.savegame.ObjectRefs;
import xyz.znix.xftl.savegame.RefLoader;
import xyz.znix.xftl.savegame.SaveUtil;
import xyz.znix.xftl.sector.*;
import xyz.znix.xftl.shipgen.EnemyShipSpec;
import xyz.znix.xftl.shipgen.ShipGenerator;
import xyz.znix.xftl.shipgen.ShipScaling;
import xyz.znix.xftl.sys.GameContainer;
import xyz.znix.xftl.sys.Input;
import xyz.znix.xftl.sys.ResourceContext;
import xyz.znix.xftl.systems.*;
import xyz.znix.xftl.ui.SpecDeserialiser;
import xyz.znix.xftl.ui.UIProvider;
import xyz.znix.xftl.ui.Widget;
import xyz.znix.xftl.weapons.Damage;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class InGameState extends MainGame.GameState {
    @Nullable // Null only when running tests
    private final MainGame mainGame;

    // Save the game for continuing (see ContinueSave) on the next update,
    // after a jump, once the beacon's event has started
    private boolean autosaveRequested;

    // Set once the game's lost or won, so it isn't saved any more
    private boolean finished;

    // True for the main menu's scene (see forMainMenu)
    private boolean menuScene;

    // The crew names preset picked for this game in the main menu (see
    // CrewNameManager), or null for the one XFTL_NAMES picks
    @Nullable
    private String namePreset;

    private final GameContent content;

    private Difficulty difficulty = Difficulty.NORMAL;

    // These are copied from the GameContent object for convenience
    private final Datafile df;
    private final boolean enableAdvancedEdition;
    private final BlueprintManager blueprintManager;
    private final EventManager eventManager;

    private final SpecDeserialiser uiLoader = new SpecDeserialiser(new GameUIProvider());

    private GameMap gameMap;
    private Ship player;

    // Where the player's ship goes, for ships bigger than vanilla, which can fight several enemy ships
    private static final int SEVERAL_ENEMIES_PLAYER_X = 215;
    private static final int THREE_ENEMIES_PLAYER_X = 125;

    /**
     * The ships the player's facing at this beacon: the main enemy ship
     * first (slot 0), then any extra ships fighting alongside it, its
     * wingmen, for player ships that fight several at once (see
     * {@link Opponent}).
     */
    private final List<Opponent> opponents = new ArrayList<>();

    /**
     * The enemy ship we last spawned wingmen for, so they're only spawned
     * once per enemy ship.
     */
    private Ship wingmenSpawnedFor;

    // The rewards for ships already dealt with in a fight with several, kept
    // until the rest are
    private final FleetRewards fleetRewards = new FleetRewards(this);

    // How many ships with a Zoltan Shield the player's boarders have killed
    // the crew of in this fight (see zoltanShieldBoarded)
    private int zoltanShieldsBoarded;

    private LootPool lootPool;

    private SILFontLoader pauseFont;

    private Image missingImage;

    private final Point tempPoint = new Point(0, 0);

    private Room hoveredRoom;
    private RoomClickListener clickEvent;
    private final boolean[] mouseDownPrev = new boolean[3];

    private ITooltipProvider lastFameTooltip;

    private PlayerShipUI shipUI;

    private Beacon currentBeacon;
    private boolean paused;

    // The list of all the sectors the ship has visited, including
    // the current one.
    private final ArrayList<GameMap.SectorInfo> visitedSectors = new ArrayList<>();

    private DebugConsole debugConsole;
    private boolean debugConsoleVisible;

    private DebugFlagManager debugFlags = new DebugFlagManager();

    private boolean isCurrentlyLoadingSave;

    // Used for animation rendering only
    private float renderingDeltaTime;

    // The enemy ships seen jumping away, for show (see JumpAwayEffect)
    private final List<JumpAwayEffect> jumpAwayEffects = new ArrayList<>();

    private final Point playerShipOffset = new Point(0, 0);

    // The size of the screen, set while rendering, for the projectiles
    // flying across it (see getPlayerSpaceScreen)
    private int screenWidth = 1920;
    private int screenHeight = 1080;

    /**
     * The quest events that couldn't be fit in the current sector,
     * and were delayed to the next one.
     */
    private final ArrayList<Event> delayedQuests = new ArrayList<>();

    /**
     * This is the list of all the player-owned crew, as used by the main UI
     * and the ship upgrade window.
     */
    private final ArrayList<LivingCrew> playerCrew = new ArrayList<>();

    /**
     * The buttons each hotkey is bound to.
     * <p>
     * This is calculated from the profile and hotkey manager, so it shouldn't
     * be modified externally.
     */
    private final HashMap<Hotkey, HotkeyButton> reverseHotkeyBindings = new HashMap<>();
    private final HashMap<HotkeyButton, Hotkey> forwardHotkeyBindings = new HashMap<>();

    /**
     * The hotkeys that have been pressed and queued up to be used by the game.
     * <p>
     * This is done in case a key is somehow dispatched while update() is running.
     */
    private final List<Hotkey> queuedHotkeys = Collections.synchronizedList(new ArrayList<>());

    /**
     * The player ship's blueprint in a new game, see {@link #getPlayerBlueprint()}.
     */
    private ShipBlueprint startingShipBlueprint;

    public InGameState(MainGame mainGame, GameContent content, String playerShipName, Difficulty difficulty, EditableShip customised) {
        this(mainGame, content, playerShipName, difficulty, customised, null, false);
    }

    /**
     * A new game, with the crew names from {@code namePreset} (see CrewNameManager),
     * or from the one XFTL_NAMES picks if it's null.
     */
    public InGameState(MainGame mainGame, GameContent content, String playerShipName, Difficulty difficulty,
                       EditableShip customised, @Nullable String namePreset) {
        this(mainGame, content, playerShipName, difficulty, customised, namePreset, false);
    }

    /**
     * The main menu's scene (see MainMenuState): a new game's player ship,
     * for the menu to draw, and its windows (the options and so on) to use.
     * It has no starting event or music, and it's never saved.
     */
    public static InGameState forMainMenu(MainGame mainGame, GameContent content, String playerShipName) {
        return new InGameState(mainGame, content, playerShipName, Difficulty.NORMAL, null, null, true);
    }

    private InGameState(MainGame mainGame, GameContent content, String playerShipName, Difficulty difficulty,
                        EditableShip customised, @Nullable String namePreset, boolean menuScene) {
        this(mainGame, content);
        this.menuScene = menuScene;
        this.difficulty = difficulty;
        // Before the crew are made, as they're named straight away
        this.namePreset = namePreset;
        startingShipBlueprint = blueprintManager.getShip(playerShipName);

        gameMap = new GameMap(df, eventManager, enableAdvancedEdition, Random.Default);

        // Start at the first beacon of the first sector
        // Be sure we do this after creating the player ship, it's used by the enemy AI
        // Note that we don't store the current sector anywhere - it's determined via
        // the current beacon.
        Sector firstSector = gameMap.generateSector(gameMap.getSectors().get(0).get(0), this);
        setCurrentBeacon(firstSector.getStartBeacon());

        // Do this after setting the initial beacon, since the ship reads the current
        // beacon when calculating its power values.
        createNewPlayerShip(playerShipName, customised);

        // Don't create PlayerShipUI in automated tests
        if (mainGame != null) {
            shipUI = new PlayerShipUI(player, this);
        }

        // Show the starting beacon dialogue, since it wasn't displayed earlier
        // due to shipUI not existing yet.
        if (!menuScene) {
            showEventDialogue(currentBeacon.getEvent(), Random.Default.nextInt());
        }
    }

    /**
     * Load a previously-saved game.
     */
    public InGameState(MainGame mainGame, GameContent content, Document saveGame) {
        this(mainGame, content);

        Element root = saveGame.getRootElement();
        if (!root.getName().equals("xftlSaveGame")) {
            throw new IllegalArgumentException("Bad save-game root element name: " + root.getName());
        }

        // If AE was enabled on the save, make sure our content also has it.
        boolean saveEnabledAE = Boolean.parseBoolean(saveGame.getRootElement().getAttributeValue("enableAE"));
        if (saveEnabledAE != content.enableAdvancedEdition) {
            throw new IllegalArgumentException("Cannot load a game with content that doesn't match it's AE state!");
        }

        difficulty = Difficulty.valueOf(root.getAttributeValue("difficulty"));
        // Older saves don't have one, and use the one XFTL_NAMES picks
        namePreset = root.getAttributeValue("namePreset");

        isCurrentlyLoadingSave = true;
        loadGameState(root);
        isCurrentlyLoadingSave = false;

        // This just loads stuff from XML, we don't need to serialise it
        lootPool = new LootPool(blueprintManager, currentBeacon.getSector().getType());

        // Give all the ships an update, for stuff like the
        // doors automatically opening. This must only be done once all
        // the ships are loaded, otherwise it'd break hacking since the
        // hacked system would think it wasn't being hacked any more.
        for (Beacon beacon : currentBeacon.getSector().getBeacons()) {
            if (beacon.getShip() != null)
                beacon.getShip().update(0f);
        }
        player.update(0f);

        // Start the music up again.
        // We need a special exception here for the continuous-save-load mode,
        // as otherwise it'll be restarting the music every frame. As the music
        // isn't saved, this would use a lot of CPU for no benefit.
        // To do this, just check if it's already playing something. Since
        // the sound manager is part of the game content, it's not cleared
        // across a reload.
        if (getSounds() instanceof RealSoundManager real && real.getCurrentMusic() == null) {
            loadSectorMusic(currentBeacon.getSector().getType());
        }

        updatePlayerCrew();
    }

    private InGameState(MainGame mainGame, GameContent content) {
        this.mainGame = mainGame;
        this.content = content;

        // Load up the convenience variables
        df = content.datafile;
        enableAdvancedEdition = content.enableAdvancedEdition;
        blueprintManager = content.blueprintManager;
        eventManager = content.eventManager;

        // Don't load any assets while running automated tests
        if (isRunningAutomatedTest())
            return;

        pauseFont = getFont("c&c");

        // Load a bunch of images that we'll need, to make them available for later.
        List<SystemBlueprint> systems = blueprintManager.getBlueprints().values().stream()
                .filter(bp -> bp instanceof SystemBlueprint)
                .map(bp -> (SystemBlueprint) bp)
                .collect(Collectors.toList());
        for (SystemBlueprint system : systems) {
            getImg(system.getRoomIconPath());
        }

        for (SystemBlueprint system : systems) {
            getImg("img/icons/s_" + system.getType() + "_red1.png");
            getImg("img/icons/s_" + system.getType() + "_orange1.png");
            getImg("img/icons/s_" + system.getType() + "_grey1.png");
            getImg("img/icons/s_" + system.getType() + "_green1.png");
        }

        updateHotkeyBindings();
    }

    @Override
    public void init(@NotNull GameContainer container) {
        super.init(container);

        // A mouse button that's still down from the last state (such as the
        // click on the main menu's Continue) isn't a click in the game
        for (int i = 0; i < mouseDownPrev.length; i++) {
            mouseDownPrev[i] = container.getInput().isMouseButtonDown(i);
        }

        // Feed inputs to the debug console.
        container.getInput().addListener(new InputAdapter() {
            @Override
            public void keyPressed(int key, char c) {
                // The debug console blocks all hotkeys
                if (debugConsoleVisible) {
                    getDebugConsole().keyPressed(key, c);
                    return;
                }

                // If the window uses the key for typing, then prevent it
                // from being used as a hotkey too.
                if (shipUI.onTextInput(key, c)) {
                    container.getInput().clearInputPressedRecord();
                    return;
                }

                // Check if there's a hotkey associated with this button
                HotkeyButton button = HotkeyButton.BY_KEY_ID.get(key);
                if (button == null) {
                    return;
                }
                Hotkey matching = forwardHotkeyBindings.get(button);
                if (matching == null) {
                    return;
                }

                // And if so, queue it for the next update
                queuedHotkeys.add(matching);
            }

            @Override
            public void mouseWheelMoved(int change) {
                if (debugConsoleVisible) {
                    getDebugConsole().mouseWheelMoved(change);
                    return;
                }

                shipUI.mouseScroll(change);
            }

            @Override
            public void mousePressed(int button, int x, int y) {
                if (debugConsoleVisible)
                    debugConsole.mousePressed(button, x, y);
            }

            @Override
            public void mouseReleased(int button, int x, int y) {
                if (debugConsoleVisible)
                    debugConsole.mouseReleased(button, x, y);
            }

            @Override
            public void mouseDragged(int oldX, int oldY, int newX, int newY) {
                if (debugConsoleVisible)
                    debugConsole.mouseDragged(oldX, oldY, newX, newY);
            }
        });
    }

    @Override
    public void shutdown() {
        // Our resource context is owned by the game data, so we don't need to free it here.
    }

    /**
     * The blueprint of the player's ship, which is available while the game's
     * being set up (such as when generating the first sector), before the
     * player's ship itself exists.
     */
    public ShipBlueprint getPlayerBlueprint() {
        return player != null ? player.getType() : startingShipBlueprint;
    }

    private void createNewPlayerShip(String shipName, EditableShip customised) {
        ShipBlueprint blueprint = blueprintManager.getShip(shipName);
        player = new Ship(blueprint, this, customised, null, null);
        player.loadDefaultContents();

        for (ShipBlueprint.InitialCrewSpec spec : blueprint.getInitialCrew()) {
            CrewBlueprint race = (CrewBlueprint) blueprintManager.get(spec.getRace());
            Room room = spec.getRoom() == null ? null : player.getRooms().get(spec.getRoom());
            for (int i = 0; i < spec.getAmount(); i++) {
                LivingCrewInfo info = LivingCrewInfo.generateRandom(race, this);
                player.addCrewMember(info, true, false, room);
            }
        }

        // Save the default crew positions, so the player can immediately
        // load them again without first having to save them.
        player.saveCrewPositions();

        // Set the default power levels. Don't turn on weapons or drones, both
        // to avoid running out of power for other stuff, and to avoid wasting
        // drone parts. In debug mode the reactor's big enough to run
        // nearly everything, and there's plenty of drone parts, so turn it
        // all on, except that the oxygen and medbay only get one power each,
        // leaving more for the rest.
        // (Each increasePower call turns on one weapon or drone.)
        player.updateAvailablePower();
        boolean powerEverything = XftlDebug.INSTANCE.getEnabled();
        for (MainSystem system : player.getMainSystems()) {
            if (!powerEverything && (system instanceof Weapons || system instanceof Drones))
                continue;

            // As a hack, turn artillery off so what's below doesn't charge it.
            if (system instanceof Artillery)
                continue;

            // Some ships start with a system off (see ISystemConfiguration.startsOff)
            if (system.getConfiguration().getSpec().getStartsOff())
                continue;

            boolean minimum = system instanceof Oxygen || system instanceof Medbay || system instanceof Clonebay;
            int power = minimum ? 1 : system.getBlueprint().getMaxPower();
            for (int i = 0; i < power; i++) {
                system.increasePower();
            }
        }

        // Properly calculate the evasion chance, so it shows properly while
        // paused in the start-of-game event, and to charge up the shield in
        // that same time.
        // Call it repeatedly to charge up the level-2 starting shields
        // on Mantis B. This is a bit hacky, but it's fine for now.
        for (int i = 0; i < 5; i++) {
            player.update(10f);
        }
    }

    /**
     * True once the game over screen has been shown, so it's only shown once.
     */
    private boolean gameOverShown = false;

    /**
     * Show the game over screen if the player's ship has been destroyed, or
     * all its crew have died (and none are waiting in a powered clonebay to
     * be brought back). Its restart button starts a new game.
     */
    private void checkGameOver() {
        if (gameOverShown || player == null)
            return;
        GameOverWindow.Outcome outcome = null;
        if (player.isGone()) {
            outcome = GameOverWindow.Outcome.LOOSE_HULL;
        } else if (!playerHasCrewLeft()) {
            outcome = GameOverWindow.Outcome.LOOSE_CREW;
        }
        if (outcome != null) {
            System.out.println("Game over: " + outcome + ", the player's ship has " + player.getHealth()
                    + " hull and " + player.getCrew().size() + " aboard");
            gameOverShown = true;
            shipUI.showGameOverScreen(outcome);
        }
    }

    /**
     * True if any of the player's crew is still alive, wherever they are
     * (on the player's ship, or boarding an enemy), or waiting to be cloned.
     */
    public boolean playerHasCrewLeft() {
        List<Ship> ships = new ArrayList<>(getOpponentShips(player));
        ships.add(player);
        if (getEnemy() != null && !ships.contains(getEnemy()))
            ships.add(getEnemy());
        for (Ship ship : ships) {
            for (AbstractCrew crew : ship.getCrew()) {
                if (crew instanceof LivingCrew living && living.getOwnerShip() == player && living.getHealth() > 0f)
                    return true;
            }
        }
        Clonebay clonebay = player.getClonebay();
        return clonebay != null && !clonebay.getQueue().isEmpty() && clonebay.getPowerSelected() > 0;
    }

    @Override
    public void update(@NotNull GameContainer container, float delta) throws SlickException {
        renderingDeltaTime = delta;

        if (autosaveRequested) {
            autosaveRequested = false;
            ContinueSave.save(this);
        }

        if (!isPaused()) {
            updateGameState(delta);
            checkGameOver();
        }

        Input in = container.getInput();

        // For debugging, this lets you either step or fast-forward through time.
        // Don't do this in response to typing on the debug console, though.
        if (!debugConsoleVisible) {
            HotkeyButton fastForwardButton = reverseHotkeyBindings.get(getHotkeyManager().getKeyFastForward());
            HotkeyButton stepButton = reverseHotkeyBindings.get(getHotkeyManager().getKeyFrameStep());

            if (fastForwardButton != null && in.isKeyDown(fastForwardButton.getKeyID())) {
                updateGameState(delta * 4);
            }
            if (stepButton != null && in.isKeyPressed(stepButton.getKeyID())) {
                updateGameState(0.01f);
            }
        }

        // Holding this while aiming a weapon does the opposite of the autofire button
        shipUI.setAutofireModifierHeld(isHotkeyHeld(VanillaHotkeys.WEAPON_AUTOFIRE_TARGET, in));

        shipUI.updateAlways(delta);

        hoveredRoom = null;

        // Don't touch sounds in automated tests
        if (content.sounds instanceof RealSoundManager sounds) {
            sounds.updateLoopedSounds(isPaused());
            sounds.setCombatMusic(isInDanger());
            sounds.updateMusic(delta);
            // The automatic playtest is always silent, without changing the
            // profile's volumes
            boolean silent = XftlDebug.INSTANCE.getAutoplay();
            sounds.setSoundEffectVolume(silent ? 0f : mainGame.getProfile().getSoundVolume());
            sounds.setMusicVolume(silent ? 0f : mainGame.getProfile().getMusicVolume());
        }

        HotkeyButton consoleButton = reverseHotkeyBindings.get(getHotkeyManager().getKeyDevConsole());
        if (consoleButton != null && in.isKeyPressed(consoleButton.getKeyID())) {
            debugConsoleVisible = !debugConsoleVisible;

            // Clear out any pending key presses, so keys pressed while
            // the console is open don't have an effect once it's closed.
            in.clearInputPressedRecord();
        }

        if (debugConsoleVisible) {
            getDebugConsole().update(container, delta);
        }

        while (!queuedHotkeys.isEmpty()) {
            dispatchHotkey(queuedHotkeys.remove(0), in);
        }

        // Escape is special, it's not a hotkey and can't be rebound.
        if (in.isKeyPressed(Input.KEY_ESCAPE)) {
            shipUI.escapePressed();
        }

        boolean rightClicked = false;

        for (int i = 0; i < 3; i++) {
            boolean prev = mouseDownPrev[i];
            boolean now = in.isMouseButtonDown(i);

            // Block mouse input while the debug console is open, to avoid
            // accidentally clicking on something under the console.
            if (debugConsoleVisible) {
                now = false;
            }

            if (now && !prev) {
                if (i == Input.MOUSE_RIGHT_BUTTON) {
                    clickEvent = null;
                    rightClicked = true;
                }

                shipUI.mouseClick(i, in.getMouseX(), in.getMouseY(), playerShipOffset);
            } else if (prev && !now) {
                shipUI.mouseUp(i, in.getMouseX(), in.getMouseY(), playerShipOffset);
            }
            mouseDownPrev[i] = now;
        }

        shipUI.updateUI(in.getMouseX(), in.getMouseY(), playerShipOffset);

        // Figure out when the player right-clicks a room on one of the
        // ships they're facing - this is used for controlling boarders.
        if (rightClicked) {
            for (Opponent opponent : opponents) {
                Room room = roomUnderMouse(container, opponent.getShip(), opponent.getUi().getShipPos());
                if (room != null)
                    shipUI.enemyRoomRightClicked(room, opponent.getShip());
            }
        }

        updateClickEvent(container);

        // Hammer away at the serialisation logic, if that flag is set.
        // Only do it while un-paused, since we can't properly use
        // the console if we're constantly reloading.
        if (debugFlags.getContinuousSaveLoad().getSet() && !isPaused()) {
            mainGame.doSaveLoadGame();
        }
    }

    @Nullable
    @Override
    public Cursor getCurrentCursor() {
        return shipUI.getCurrentCursor();
    }

    /**
     * The room of [ship], drawn at [shipPos] on the screen, that the mouse
     * is over, if any.
     */
    @Nullable
    private Room roomUnderMouse(GameContainer container, Ship ship, IPoint shipPos) {
        tempPoint.setX(container.getInput().getMouseX());
        tempPoint.setY(container.getInput().getMouseY());
        tempPoint.minusAssign(shipPos);
        ship.screenPosToShipPos(tempPoint);

        RoomPoint rp = ship.shipToRoomPos(tempPoint);
        return rp == null ? null : rp.getRoom();
    }

    private void updateClickEvent(GameContainer container) {
        // Handle stuff like weapon targeting where the player
        // highlights a room on the enemy (or their) ship.
        if (clickEvent == null)
            return;

        // Hovering over a room of a ship the player's facing, if they can see inside it
        for (Opponent opponent : opponents) {
            if (!opponent.isInteriorVisible(player))
                continue;
            Room room = roomUnderMouse(container, opponent.getShip(), opponent.getUi().getShipPos());
            if (room != null)
                hoveredRoom = room;
        }

        // Hovering over the player
        Room playerRoom = roomUnderMouse(container, player, playerShipOffset);
        if (playerRoom != null)
            hoveredRoom = playerRoom;

        if (hoveredRoom != null && !clickEvent.canTargetRoom(hoveredRoom)) {
            hoveredRoom = null;
        }

        if (hoveredRoom != null && clickEvent != null && mouseDownPrev[Input.MOUSE_LEFT_BUTTON]) {
            var prev = clickEvent;
            clickEvent = null;
            prev.roomClicked(hoveredRoom, container);
        }
    }

    private void dispatchHotkey(Hotkey key, Input in) {
        if (key.getId().equals(VanillaHotkeys.PAUSE))
            paused = !paused;

        boolean shiftPressed = in.isKeyDown(Input.KEY_LSHIFT);
        for (int i = 0; i < VanillaHotkeys.WEAPON_SLOTS.size(); i++) {
            if (key.getId().equals(VanillaHotkeys.WEAPON_SLOTS.get(i))) {
                shipUI.weaponHotkeyPressed(i, shiftPressed);
            }
        }
        // TODO drone hotkeys

        shipUI.hotkeyPressed(key, in);
    }

    @Override
    public void render(@NotNull GameContainer container, @NotNull Graphics g) throws SlickException {
        g.clear(Colour.black);
        screenWidth = container.getWidth();
        screenHeight = container.getHeight();

        currentBeacon.getEnvironment(this).renderBackground(container, g);

        // Set the position of the player's ship, based on whether or not
        // there's also an enemy ship. With no other ship around, it's in the
        // middle of the screen.
        if (getEnemy() == null && getWingmen().isEmpty()) {
            int hullCentre = player.getHullOffset().getX() + player.getHullImage().getWidth() / 2;
            playerShipOffset.setX(container.getWidth() / 2 - hullCentre);
        } else if (player.getType().getShipSize() > 1) {
            // Ships bigger than vanilla can fight several enemy ships at
            // once, so they stay in the same place to leave room for their
            // windows, and so they don't jump around between fights.
            // Further left if there's three enemy windows to fit in
            boolean threeEnemies = Opponent.maxEnemyShipCount(player.getType()) >= 3;
            playerShipOffset.setX(threeEnemies ? THREE_ENEMIES_PLAYER_X : SEVERAL_ENEMIES_PLAYER_X);
        } else if (getEnemy() != null && getEnemy().isUsingBossUI()) {
            playerShipOffset.setX(150);
        } else {
            playerShipOffset.setX(200);
        }
        playerShipOffset.setY(170);

        // Get the player's ship away from the top UI
        g.pushTransform();
        g.translate(playerShipOffset.getX(), playerShipOffset.getY());
        player.render(g, true, hoveredRoom);
        // Some modded ships don't have a weapons system, or have several
        for (Weapons weapons : player.getWeaponSystems()) {
            player.renderTargeting(g, weapons.getSelectedTargets());
        }
        g.popTransform();

        shipUI.render(container, g);

        // The windows of the ships the player's facing. If one's neutral but
        // the player still has crew inside, they can see inside, to control
        // and recover them.
        for (Opponent opponent : opponents) {
            opponent.getUi().render(container, g, hoveredRoom, opponent.isInteriorVisible(player), opponent.getHostile());
        }

        // The ships jumping away (see JumpAwayEffect)
        for (JumpAwayEffect effect : jumpAwayEffects) {
            effect.update(renderingDeltaTime);
            effect.render();
        }
        jumpAwayEffects.removeIf(JumpAwayEffect::isFinished);

        // The player's combined beams' long aiming lines, over all the enemy
        // windows they go through (see HostileShipUI.renderLongBeamTargeting)
        for (Opponent opponent : opponents) {
            opponent.getUi().renderLongBeamTargeting(g);
        }

        shipUI.renderCrewSelection(g);

        // Draw the paused text before the UI, so the UI goes on top.
        // It goes above the weapons boxes, however many rows of them there are.
        if (paused) {
            Image pauseImg = getImg("img/Text_pause2.png");
            int imgY = container.getHeight() - 193 - shipUI.getWeaponBoxStackHeight();
            int imgX = container.getWidth() / 2 - pauseImg.getWidth() / 2;
            pauseImg.draw(imgX, imgY);

            String pauseKey = getButtonNameForHotkey(VanillaHotkeys.PAUSE, "<unbound>");
            String pauseText = getTranslator().get("paused_text").replace("\\1", pauseKey);
            pauseFont.drawStringCentred(
                    container.getWidth() / 2f, imgY + 11 + 53,
                    0,
                    pauseText, Constants.PAUSE_TEXT_COLOUR
            );
        }

        // Draw solar flares and pulsar pulses
        currentBeacon.getEnvironment(this).renderOverlay(container, g);

        shipUI.renderMenus(container, g);

        if (debugConsoleVisible) {
            g.setTooltip(null);
            getDebugConsole().render(container, g);
        }

        // Render the tooltip, except in screenshots, where it would just be
        // wherever the mouse happens to be
        ITooltipProvider tooltip = g.getTooltip();
        boolean mouseShown = !XftlDebug.INSTANCE.getRecording() || XftlDebug.INSTANCE.getRecordingMouse() != null;
        if (tooltip != null && mouseShown) {
            Input in = container.getInput();
            tooltip.drawTooltip(g,
                    in.getMouseX(), in.getMouseY(),
                    tooltip != lastFameTooltip,
                    container.getWidth(), container.getHeight()
            );
        }
        lastFameTooltip = tooltip;

        String caption = XftlDebug.INSTANCE.getRecordingCaption();
        if (caption != null) {
            drawRecordingCaption(g, caption);
        }

        // The automatic playtest's progress, in the top right corner
        // (in the same small font as the build's version, in the bottom right),
        // or in debug mode, where the player is
        String autoplayStatus = AutoplayDirector.getStatus();
        if (autoplayStatus == null && XftlDebug.INSTANCE.getEnabled() && !XftlDebug.INSTANCE.getRecording()) {
            autoplayStatus = debugStatus();
        }
        if (autoplayStatus != null) {
            getFont("JustinFont8").drawStringLeftAligned(container.getWidth() - 4f, 10f, autoplayStatus,
                    new Colour(1f, 1f, 1f, 0.5f));
        }
    }

    /**
     * A screenshot's caption (see XftlDebug.recordingCaption), in a dark box
     * at the top, between the ship's buttons and the enemy's window: each
     * line of it wrapped to fit.
     */
    private void drawRecordingCaption(Graphics g, String caption) {
        SILFontLoader font = getFont("JustinFont10");
        List<String> lines = new ArrayList<>();
        for (String line : caption.split("\n")) {
            lines.addAll(font.wrapString(line, CAPTION_WIDTH - 2 * CAPTION_PADDING));
        }
        int height = lines.size() * font.getLineSpacing() + 2 * CAPTION_PADDING;

        g.setColour(new Colour(0f, 0f, 0f, 0.8f));
        g.fillRect(CAPTION_X, CAPTION_Y, CAPTION_WIDTH, height);
        g.setColour(new Colour(1f, 0.85f, 0.3f, 1f));
        g.drawRect(CAPTION_X, CAPTION_Y, CAPTION_WIDTH - 1, height - 1);

        int y = CAPTION_Y + CAPTION_PADDING + font.getBaselineToTop();
        for (String line : lines) {
            font.drawString(CAPTION_X + CAPTION_PADDING, y, line, Colour.white);
            y += font.getLineSpacing();
        }
    }

    private static final int CAPTION_X = 780;
    private static final int CAPTION_Y = 4;
    private static final int CAPTION_WIDTH = 700;
    private static final int CAPTION_PADDING = 6;

    /**
     * What debug mode shows in the top right corner, like the playtest's
     * progress: the sector the player's in, and how many of its beacons
     * they've visited.
     */
    private String debugStatus() {
        Sector sector = currentBeacon.getSector();
        int visited = 0;
        for (Beacon beacon : sector.getBeacons()) {
            if (beacon.getVisited())
                visited++;
        }
        return "Sector " + (sector.getInfo().getColumnNumber() + 1)
                + " (" + getTranslator().get(sector.getType().getShortTextId()) + "), "
                + visited + " beacons visited";
    }

    /**
     * Update the in-game state.
     * <p>
     * This is only public so that automated tests can call it - it should
     * NOT be called elsewhere from the main code.
     */
    public void updateGameState(float dt) {
        currentBeacon.getEnvironment(this).update(dt);
        AntiShipBattery battery = currentBeacon.getAntiShipBattery(this);
        if (battery != null)
            battery.update(dt);

        if (shipUI != null)
            shipUI.update();

        player.update(dt);

        updatePlayerCrew();

        updateOpponents(dt);

        updateMainframeBreak(dt);

        // Show any rewards kept for the end of the fight, once it's over
        // some other way (such as the last ship escaping, or the player
        // jumping away)
        if (!fleetRewards.isEmpty() && !hasHostileShipsOtherThan(null) && shipUI != null) {
            kotlin.Pair<Ship, Event> special = fleetRewards.takeSpecial();
            if (special != null)
                showFullRewards(special.getFirst(), special.getSecond());
            fleetRewards.show();
        }

        // The player's weapons only stop when every ship they're fighting is
        // cloaked, since they can still fire at the others.
        List<Ship> opponents = getOpponentShips(player);
        boolean allCloaked = !opponents.isEmpty();
        for (Ship opponent : opponents) {
            if (!opponent.isCloakActive())
                allCloaked = false;
        }
        player.setOpponentCloakActive(allCloaked);

        if (!isInDanger()) {
            // If the player isn't fighting a ship, there are no borders (not yet implemented), and they're not
            // in a dangerous environment (eg, an asteroid field) then let them jump instantly.
            player.setFtlChargeProgress(1);
        }
    }

    public void setCurrentBeacon(Beacon currentBeacon) {
        if (this.currentBeacon == null || this.currentBeacon.getSector() != currentBeacon.getSector()) {
            SectorType sectorType = currentBeacon.getSector().getType();
            lootPool = new LootPool(blueprintManager, sectorType);

            // Add any quests we didn't have time for last time
            for (Event quest : delayedQuests) {
                currentBeacon.getSector().addQuest(currentBeacon, quest, true);
            }
            delayedQuests.clear();

            // Switch to the new sector's music (the main menu is silent)
            if (!menuScene) {
                loadSectorMusic(sectorType);
            }
        }

        this.currentBeacon = currentBeacon;
        autosaveRequested = true;

        // Keep track of which sectors we visit, to show on the sector map UI.
        GameMap.SectorInfo sectorInfo = currentBeacon.getSector().getInfo();
        if (!visitedSectors.contains(sectorInfo)) {
            visitedSectors.add(sectorInfo);
        }

        // The boss holding a sector's exits meets the player at whichever
        // of them they come to (see SectorType.exitCount), as they left it
        boolean bossBeacon = isBossBeacon(currentBeacon);
        if (bossBeacon && currentBeacon.getShip() == null) {
            Beacon held = exitBossBeacon(currentBeacon.getSector());
            if (held != null && held != currentBeacon) {
                currentBeacon.setShip(held.getShip());
                held.setShip(null);
            }
        }

        setEnemy(currentBeacon.getShip(), true);

        // A hunting boss waiting where the player's arrived (see HunterBoss)
        HunterBoss hunter = hunterIn(currentBeacon.getSector());

        // This is called for the first beacon before the player is created.
        if (player != null) {
            // Make the store button appear and disappear.
            shipUI.updateButtons();

            player.resetAfterJump();
            if (hunter != null && hunter.isWaitingAt(currentBeacon)) {
                // It meets the player in place of the beacon's own event
                showEventDialogue(hunter.startEncounter(), currentBeacon.getEnvironmentSeed());
            } else if (bossBeacon) {
                // A boss's beacon (its sector's exit, see ColossalBoss)
                // always has its boss, even once the fleet's overtaken it,
                // until it's beaten (at this exit or another)
                if (currentBeacon.getShip() == null && !isExitBossBeaten(currentBeacon.getSector()))
                    showEventDialogue(currentBeacon.getEvent(), currentBeacon.getEnvironmentSeed());
            } else if (isMainframeWaiting(currentBeacon)) {
                // Coming back to the Mainframe, after beating one of its
                // phases: the next one's there
                startMainframePhase();
            } else if (currentBeacon.getState() == Beacon.State.UNVISITED) {
                showEventDialogue(currentBeacon.getEvent(), currentBeacon.getEnvironmentSeed());
            }
        }

        // Spawn in a new rebel elite every jump for overtaken beacons. A
        // boss's beacon keeps its boss, which fights the player alone.
        // (Once it's beaten, the fleet's taken its other exits, see
        // exitHoldBroken, and they're the fleet's like any other.)
        boolean bossStillHolds = bossBeacon && !isExitBossBeaten(currentBeacon.getSector());
        if (currentBeacon.getState() == Beacon.State.OVERTAKEN && !(bossStillHolds && (!currentBeacon.getVisited() || isHeldByBoss(currentBeacon)))) {
            // See the original XFTL's doc/sector-map for information on these events

            String eventName;

            if (currentBeacon.isExit()) {
                eventName = "FLEET_EASY_BEACON";
                if (difficulty != Difficulty.EASY) {
                    // This is correct - use _DLC for non-easy difficulties!
                    eventName += "_DLC";
                }
            } else {
                eventName = "FLEET_EASY";

                if (currentBeacon.getEnvironmentType().isNebula()) {
                    eventName += "_NEBULA";
                } else if (enableAdvancedEdition) {
                    eventName += "_DLC";
                }
            }

            Event event = eventManager.get(eventName).resolve();
            // The fleet's Anti-Ship Battery (see Beacon.fleetBatteryTarget)
            currentBeacon.setFleetBatteryTarget(event.getAntiShipBattery());
            showEventDialogue(event, Random.Default.nextInt());
        }

        // If the flagship is here, spawn it in. Doing this last overwrites
        // the rebel elite that's already been spawned.
        trySpawnBoss();

        currentBeacon.setVisited(true);

        // The hunter heads for where the player is now
        if (hunter != null)
            hunter.playerArrived(currentBeacon);

        // Note: the new ship is loaded by loadShipEvent, which is called by the event dialogue window.
    }

    private void trySpawnBoss() {
        // (a hunting boss's ship comes from its event, see HunterBoss)
        BossManager boss = currentBeacon.getSector().getBosses().stream()
                .filter(thisBoss -> !(thisBoss instanceof HunterBoss) && thisBoss.getBeacon() == currentBeacon)
                .findFirst().orElse(null);

        // No boss at this beacon?
        if (boss == null) {
            return;
        }

        // If there's a ship at the current beacon, get rid of it. This avoids
        // it sticking around after the flagship leaves, and saves space
        // in the serialised game.
        // Note the flagship isn't marked as belonging to a beacon, since it's
        // generated whenever the player jumps there.
        currentBeacon.setShip(null);

        setEnemy(boss.createShip(), true);
    }

    /**
     * Switch to using a new sector's music.
     * <p>
     * This switches to the start of the first track in this sector, so it
     * shouldn't be called unless there's a new sector.
     */
    private void loadSectorMusic(SectorType type) {
        List<MusicSpec> tracks = type.getSoundtracks().stream()
                .map(name -> getSounds().getTrack(name))
                .toList();
        getSounds().switchMusicList(tracks);
    }

    public void loadEventShip(Event event) {
        if (event.getLoadShipName() != null) {
            EnemyShipSpec spec = eventManager.getShip(event.getLoadShipName());
            int sector = currentBeacon.getSector().getDifficultySectorNumber();

            // Plan the fight: how many ships, and which are bigger ones. The
            // first is this ship, and the rest its wingmen (see spawnWingmen).
            // (A ship marked xftlAlone, such as a quest's, fights alone.)
            FightPlan plan = spec.getAlone() ? new FightPlan(List.of(ShipShape.NORMAL)) : planFight();
            Ship ship = content.generator.buildShip(this, spec, sector, difficulty, null, plan.getShapes().get(0));
            // How a boss's fight starts, such as with the player's ambush
            if (ship.getColossal() != null && event.getBossOpening() != null)
                ship.getColossal().opening(event.getBossOpening());
            // Ships aren't hostile by default
            setEnemy(ship, false);
            if (ship.getType().getShipSize() > 1 && !ship.getType().isVariant()) {
                // A ship as big as the player's (like the flagship) is alone
                fightPlan = null;
            } else {
                // If there isn't a bigger version of this ship, it's a normal one
                List<ShipShape> shapes = new ArrayList<>(plan.getShapes());
                shapes.set(0, ship.getType().getShape());
                fightPlan = new FightPlan(shapes);
            }
            fightPlanFor = ship;
        }
        Boolean hostileState = event.getLoadShipHostile();

        // A surrender being accepted: the ship that offered it stops fighting
        // (a wingman's, not the main one's), or with its leader speaking for
        // them all, every ship does (see surrenderOffered)
        Opponent surrendering = surrenderingOpponent;
        if (hostileState != null && !hostileState && surrendering != null && opponents.contains(surrendering)) {
            boolean everyone = fleetSurrender;
            surrenderingOpponent = null;
            fleetSurrender = false;
            if (everyone) {
                for (Opponent opponent : new ArrayList<>(opponents)) {
                    setHostile(opponent, false);
                    sendBoardersHome(opponent.getShip());
                }
                currentBeacon.setShip(null);
                return;
            }
            if (surrendering != getMainOpponent()) {
                setHostile(surrendering, false);
                sendBoardersHome(surrendering.getShip());
                return;
            }
        }

        if (hostileState != null) {
            this.currentBeacon.setShip(hostileState ? getEnemy() : null);
            setEnemyIsHostile(hostileState);
            if (!hostileState && getEnemy() != null)
                sendBoardersHome(getEnemy());

            // Bring in the rest of the ships straight away, so they're on
            // screen with the event's window, rather than once it's closed
            // and the game's running again
            if (hostileState)
                spawnWingmen();
        }
    }

    /**
     * Once [ship] stops fighting (it surrenders, or an event makes peace),
     * the boarders go home, as in vanilla: the player's on it teleport back
     * to the player's ship, and its own on the player's ship back to it.
     * They arrive in the teleporter's room, or wherever there's space.
     */
    void sendBoardersHome(Ship ship) {
        for (AbstractCrew crew : new ArrayList<>(ship.getCrew())) {
            if (crew instanceof LivingCrew living && living.getOwnerShip() == player)
                sendHome(living, player);
        }
        for (AbstractCrew crew : new ArrayList<>(player.getCrew())) {
            if (crew instanceof LivingCrew living && living.getOwnerShip() == ship)
                sendHome(living, ship);
        }
    }

    private static void sendHome(LivingCrew crew, Ship home) {
        if (crew.isTeleporting() || crew.getCurrentAction() == AbstractCrew.Action.DYING || crew.getHealth() <= 0f)
            return;
        Room room = home.getTeleporter() != null ? home.getTeleporter().getRoom() : null;
        crew.teleportAnimatedTo(room != null ? room : home.findSpaceForCrew(null, crew.getMode()).getRoom());
    }

    // For use by the debug console
    public void debugSpawnShip(EnemyShipSpec spec, Difficulty difficulty, int sector, int seed) {
        setEnemy(content.generator.buildShip(this, spec, sector, difficulty, seed), true);
        currentBeacon.setShip(getEnemy());
    }

    /**
     * Start a fight with ships of the given shapes (see FightPlan), for
     * testing the bigger ships: the first is the main enemy ship, and the
     * rest its wingmen.
     */
    public void debugFightShapes(EnemyShipSpec spec, List<ShipShape> shapes) {
        int sector = currentBeacon.getSector().getDifficultySectorNumber();
        debugRemoveEnemy();
        debugWingmanSpecs = null;
        setEnemy(content.generator.buildShip(this, spec, sector, difficulty, null, shapes.get(0)), true);
        fightPlan = new FightPlan(shapes);
        fightPlanFor = getEnemy();
        currentBeacon.setShip(getEnemy());
    }

    /**
     * Beat the main enemy ship as if its crew were all killed, without its
     * rewards (for tests).
     */
    public void debugBeatEnemy() {
        Opponent main = getMainOpponent();
        if (main == null)
            return;
        setHostile(main, false);
        currentBeacon.setShip(null);
    }

    /**
     * Remove the enemy ship without it being destroyed or escaping, for
     * the debug option that skips events. Bosses are left alone, as
     * removing them would break the boss fight.
     */
    public void debugRemoveEnemy() {
        if (getEnemy() == null || getEnemy().getBoss() != null)
            return;

        setEnemy(null, false);
        currentBeacon.setShip(null);
    }

    /**
     * Make [enemy] the ship the player's facing, alone (any wingmen belong
     * to the old enemy ship), [hostile] or not, or with null, no ship.
     */
    private void setEnemy(@Nullable Ship enemy, boolean hostile) {
        opponents.clear();
        zoltanShieldsBoarded = 0;
        if (enemy != null) {
            Opponent main = new Opponent(enemy, Objects.requireNonNull(player), 0, this);
            main.setHostile(hostile);
            opponents.add(main);
        }

        opponentsChanged();
    }

    /**
     * Let everything that cares know the ships the player's facing have
     * changed.
     */
    private void opponentsChanged() {
        // player=null when we're starting a new game, in which case there's no enemy either.
        if (player == null)
            return;

        player.enemyShipUpdated();
        for (Opponent opponent : opponents)
            opponent.getShip().enemyShipUpdated();

        // Update the drones system, as drones may need to disappear
        // when the enemy ship is changed.
        for (Drones drones : player.getDroneSystems()) {
            drones.enemyShipUpdated();
        }

        // Turn off any drones flying around the player's ship if they belong
        // to a now-gone ship.
        for (AbstractExternalDrone drone : player.getExternalDrones()) {
            if (isShipPresent(drone.getOwnerShip()))
                continue;

            drone.setPowered(false);
        }
    }

    /**
     * Remove [opponent], after it's destroyed or escapes. The rest move
     * over to fill the gap in the windows, keeping their AI and windows. If
     * it was the main enemy ship, the first wingman still fighting (or, with
     * none, the first crew-killed one, which stays on the screen rather than
     * disappearing) becomes the main one.
     */
    private void removeOpponent(Opponent opponent) {
        boolean wasMain = opponent == getMainOpponent();

        // The player's boarders still aboard die with it: the clonebay, if
        // the player has one, brings them back, as in vanilla
        Clonebay clonebay = player.getClonebay();
        for (AbstractCrew crew : new ArrayList<>(opponent.getShip().getCrew())) {
            if (!(crew instanceof LivingCrew living) || living.getOwnerShip() != player)
                continue;
            living.removeFromShip();
            if (clonebay != null)
                clonebay.addDeadCrew(living);
        }

        opponents.remove(opponent);

        if (wasMain) {
            Opponent next = null;
            for (Opponent wingman : opponents) {
                if (wingman.getHostile()) {
                    next = wingman;
                    break;
                }
            }
            if (next == null && !opponents.isEmpty())
                next = opponents.get(0);

            if (next != null) {
                opponents.remove(next);
                opponents.add(0, next);
                // It doesn't get wingmen of its own
                wingmenSpawnedFor = next.getShip();
            }

            // (a crew-killed one isn't there to come back to, as with the enemy's)
            currentBeacon.setShip(next != null && next.getHostile() ? next.getShip() : null);
        }

        for (int i = 0; i < opponents.size(); i++)
            opponents.get(i).setSlot(i);

        opponentsChanged();
    }

    /**
     * Add a wingman, in the next free window to the left.
     */
    private void addWingman(Ship ship, boolean hostile) {
        Opponent wingman = new Opponent(ship, player, opponents.size(), this);
        wingman.setHostile(hostile);
        // Wingmen hold out longer than the ship they fly with before they
        // surrender (see Opponent)
        ship.setSurrenderHealth(ship.getSurrenderHealth() / 2);
        opponents.add(wingman);
    }

    // The ship whose surrender's being offered (see surrenderOffered), and
    // whether its leader's speaking for every ship
    @Nullable
    private Opponent surrenderingOpponent;
    private boolean fleetSurrender;

    /**
     * The chance that the main enemy ship's surrender, with wingmen still
     * fighting alongside it, is for them all: its captain speaks for the
     * whole group.
     */
    public static final float FLEET_SURRENDER_CHANCE = 0.4f;

    /**
     * [ship] offers to surrender (see ShipAI): its surrender event, which,
     * accepted, stops that ship fighting, whichever of the opponents it is
     * (see loadEventShip). The main ship's sometimes speaks for its wingmen
     * too ([FLEET_SURRENDER_CHANCE]), which its event says.
     */
    public void surrenderOffered(Ship ship) {
        surrenderOffered(ship, Random.Default.nextFloat() < FLEET_SURRENDER_CHANCE);
    }

    /**
     * [surrenderOffered], the main ship's [speaksForAll] its wingmen if it
     * has any (for tests).
     */
    void surrenderOffered(Ship ship, boolean speaksForAll) {
        Opponent opponent = null;
        for (Opponent o : opponents) {
            if (o.getShip() == ship)
                opponent = o;
        }
        if (opponent == null || ship.getSpec() == null || ship.getSpec().getSurrender() == null)
            return;

        boolean hasWingmen = false;
        for (Opponent o : opponents) {
            if (o != opponent && o.getHostile())
                hasWingmen = true;
        }
        surrenderingOpponent = opponent;
        fleetSurrender = opponent == getMainOpponent() && hasWingmen && speaksForAll;

        showEventDialogue(ship.getSpec().getSurrender().resolve(), Random.Default.nextInt());
        if (fleetSurrender && shipUI != null)
            shipUI.appendToEventDialogue(getTranslator().get("xftl_fleet_surrender"));
    }

    /**
     * The event window's closed: a surrender that wasn't accepted is
     * forgotten (see surrenderOffered).
     */
    void eventDialogueClosed() {
        surrenderingOpponent = null;
        fleetSurrender = false;
    }

    // How many drones a ship had, for the Ossuary Schematics
    private static int countDrones(Ship ship) {
        int count = 0;
        for (Drones system : ship.getDroneSystems()) {
            for (Drones.DroneInfo drone : system.getDrones()) {
                if (drone != null)
                    count++;
            }
        }
        return count;
    }

    /**
     * True if [beacon] is a boss's: the exit of a sector with a boss (one
     * with an xftlFinishEvent, see SectorType.finishEventName).
     */
    public boolean isBossBeacon(Beacon beacon) {
        return beacon.isExit() && beacon.getSector().getType().getBossHoldsExit();
    }

    /**
     * The exit of [sector] that its boss's ship is at, having met the player
     * there, while it's still fighting, or null (see isBossBeacon).
     */
    @Nullable
    private Beacon exitBossBeacon(Sector sector) {
        for (Beacon beacon : sector.getBeacons()) {
            Ship ship = beacon.getShip();
            if (beacon.isExit() && ship != null && ship.getColossal() != null && !ship.isGone())
                return beacon;
        }
        return null;
    }

    /**
     * True once the boss holding [sector]'s exits has been beaten: the player's
     * been to one of them, and it's not there, or at any of the others.
     */
    public boolean isExitBossBeaten(Sector sector) {
        if (!sector.getType().getBossHoldsExit() || exitBossBeacon(sector) != null)
            return false;
        for (Beacon beacon : sector.getBeacons()) {
            if (beacon.isExit() && beacon.getVisited())
                return true;
        }
        return false;
    }

    /** The boss hunting the player in [sector] (see HunterBoss), or null. */
    @Nullable
    public HunterBoss hunterIn(Sector sector) {
        for (BossManager boss : sector.getBosses()) {
            if (boss instanceof HunterBoss hunter)
                return hunter;
        }
        return null;
    }

    // A size 3 boss has been beaten: if it was the one hunting the player,
    // it's gone from the map
    private void colossalBeaten(Ship ship) {
        if (ship.getColossal() == null)
            return;
        // One of the Mainframe's phases (see doc/game/mainframe.md)
        int phase = ship.getColossal().getKind().getMainframePhase();
        if (phase > 0) {
            mainframePhaseBeaten(ship, phase);
            return;
        }
        HunterBoss hunter = hunterIn(currentBeacon.getSector());
        if (hunter != null)
            hunter.bossBeaten();
        if (currentBeacon.getSector().getType().getBossHoldsExit() && currentBeacon.isExit())
            exitHoldBroken(currentBeacon.getSector(), currentBeacon);
    }

    /**
     * One of the Mainframe's phases, [phase], has been beaten (destroyed, or
     * its crew killed): beating the last wins the game. Otherwise, the next
     * starts after a break of [MAINFRAME_BREAK] seconds (see
     * updateMainframeBreak), and the player's boarders on it come home.
     */
    private void mainframePhaseBeaten(Ship ship, int phase) {
        if (phase >= MAINFRAME_PHASES) {
            if (shipUI != null)
                shipUI.showGameOverScreen(GameOverWindow.Outcome.WIN);
            return;
        }

        Sector sector = currentBeacon.getSector();
        if (sector.getMainframePhase() > phase)
            return;
        sector.setMainframePhase(phase + 1);
        sector.setMainframeBreak(MAINFRAME_BREAK);

        sendBoardersHome(ship);
    }

    /**
     * The break between two of the Mainframe's phases: it counts down while
     * the player's at its beacon, with no one else fighting them, and then
     * the next phase starts.
     */
    private void updateMainframeBreak(float dt) {
        Sector sector = currentBeacon.getSector();
        if (sector.getMainframeBreak() <= 0f || !currentBeacon.isBase() || hasHostileShipsOtherThan(null))
            return;
        float left = sector.getMainframeBreak() - dt;
        sector.setMainframeBreak(Math.max(left, 0f));
        if (left <= 0f)
            startMainframePhase();
    }

    /**
     * True if the Mainframe's next phase is waiting at [beacon] for the
     * player to come back to it: one of its phases has been beaten, and
     * there's no ship there.
     */
    private boolean isMainframeWaiting(Beacon beacon) {
        return beacon.isBase() && beacon.getSector().getMainframePhase() > 1 && beacon.getShip() == null;
    }

    // The Mainframe's next phase starts: its event, which brings its ship
    private void startMainframePhase() {
        Sector sector = currentBeacon.getSector();
        sector.setMainframeBreak(0f);
        String name = MAINFRAME_PHASE_EVENT + sector.getMainframePhase();
        showEventDialogue(eventManager.get(name).resolve(), Random.Default.nextInt());
    }

    /**
     * The boss holding [sector]'s exits (the Vigilance, at the Blockade Line)
     * has been beaten at the player's exit, [at]: the Rebel fleet surges forward
     * (right up behind the player, and at least [EXIT_HOLD_FLEET_LEAP] jumps'
     * worth at once), and takes the sector's
     * other exits, so the only way left is on, out of the sector.
     */
    public void exitHoldBroken(@NotNull Sector sector, @NotNull Beacon at) {
        if (!sector.getType().getHasFleet())
            return;

        Point dangerZone = sector.getDangerZoneCentre();
        // At least that far, and right up behind the player: its edge a
        // cell short of the player's exit
        int advance = sector.getFleetAdvanceFor(at, player.getType().getShipSize());
        int upBehind = at.getPos().getX() - Sector.DANGER_ZONE_RADIUS - Sector.Companion.getCELL_SIZE().getX();
        dangerZone.setX(Math.max(dangerZone.getX() + advance * EXIT_HOLD_FLEET_LEAP, upBehind));

        for (Beacon beacon : sector.getBeacons()) {
            if (beacon == at)
                continue;
            boolean reached = beacon.getPos().distToSq(dangerZone) <= Sector.DANGER_ZONE_RADIUS_SQUARED;
            if (reached || beacon.isExit())
                beacon.setOvertaken(true);
        }

        if (shipUI != null) {
            shipUI.showSyntheticDialogue(new DialogueWindow.SyntheticEvent(getTranslator().get("xftl_exit_hold_broken")));
        }
    }

    /**
     * True in the second act (after the Blockade Line), in a game with acts.
     */
    public boolean isActTwo() {
        if (currentBeacon == null)
            return false;
        return gameMap.actOf(currentBeacon.getSector().getInfo().getColumnNumber()) == 2;
    }

    /**
     * True if the ship at [beacon] is a size 3 boss that's still fighting:
     * its sector's exit is held until it's beaten (see ColossalBoss), so the
     * player can't leave the sector from there.
     */
    public boolean isHeldByBoss(Beacon beacon) {
        Ship ship = beacon.getShip();
        if (ship == null || ship.getColossal() == null || ship.isGone())
            return false;
        if (beacon != currentBeacon)
            return true;
        Opponent opponent = opponentFor(ship);
        return opponent != null && opponent.getHostile();
    }

    /**
     * For the video recorder and screenshots (see VideoDirector): the specs
     * to build the wingmen from, in order, instead of more copies of the
     * enemy ship, and exactly that many of them. Null (as always outside
     * them) to use the enemy ship's spec, as many times as usual.
     */
    @Nullable
    public List<EnemyShipSpec> debugWingmanSpecs = null;

    // The plan for the fight with the current enemy ship (see FightPlan and
    // loadEventShip), and the ship it's for
    private FightPlan fightPlan = null;
    private Ship fightPlanFor = null;

    /**
     * Show the rewards for destroying (or killing the crew of) an enemy ship:
     * its event, and for a bigger ship, the rest of its salvage (see
     * BigShipRewards), which is shown first.
     */
    private void showShipRewards(Ship ship, IEvent event) {
        if (event == null)
            return;
        Event resolved = event.resolve();

        // In a fight with several ships, the rewards wait until they've
        // all been dealt with, and are shown together (see FleetRewards):
        // the plain ones in one window, and the first event that does
        // anything more (offers a choice, say) once, after it
        boolean othersLeft = hasHostileShipsOtherThan(ship);
        if (othersLeft || !fleetRewards.isEmpty()) {
            if (!fleetRewards.add(ship, resolved)) {
                if (othersLeft) {
                    fleetRewards.deferSpecial(ship, resolved);
                    return;
                }
                // The last ship's: its event is shown, and any kept
                // earlier only gives its resources
                kotlin.Pair<Ship, Event> earlier = fleetRewards.takeSpecial();
                if (earlier != null)
                    fleetRewards.addResourcesOnly(earlier.getFirst(), earlier.getSecond());
                showFullRewards(ship, resolved);
            } else if (!othersLeft) {
                kotlin.Pair<Ship, Event> special = fleetRewards.takeSpecial();
                if (special != null)
                    showFullRewards(special.getFirst(), special.getSecond());
            }
            // (shown before the special event, if there is one)
            if (!othersLeft)
                fleetRewards.show();
            return;
        }

        showFullRewards(ship, resolved);
    }

    /**
     * Show [ship]'s [event], and for a bigger ship, the rest of its salvage
     * (see BigShipRewards), which is shown first.
     */
    private void showFullRewards(@Nullable Ship ship, Event resolved) {
        showEventDialogue(resolved, Random.Default.nextInt());

        DialogueWindow.SyntheticEvent salvage = ship == null ? null : BigShipRewards.salvage(this, ship, resolved, Random.Default);
        if (salvage != null && shipUI != null) {
            shipUI.showSyntheticDialogue(salvage);
        }
    }

    /**
     * Plan a fight in the current sector: how many ships there are, and which
     * are bigger ones (see FightPlan).
     */
    private FightPlan planFight() {
        Sector sector = currentBeacon.getSector();
        int column = sector.getInfo().getColumnNumber();
        int count = Opponent.rampedEnemyShipCount(player.getType(), 1, column, gameMap.getSectorCount(), Random.Default);
        // Bigger player ships fight more, and bigger, ships through the game,
        // and through each sector
        if (player.getType().getShipSize() >= 2) {
            int startX = sector.getStartBeacon().getPos().getX();
            int exitX = sector.getFinishBeacon().getPos().getX();
            float progress = exitX == startX ? 1f
                    : (float) (currentBeacon.getPos().getX() - startX) / (exitX - startX);
            return FightPlan.forSector(Opponent.maxEnemyShipCount(player.getType()),
                    player.getType().getOpponentShipsAtStart(), gameMap.nominalSectorNumber(column), progress,
                    Random.Default);
        }
        return FightPlan.plan(count, 0f, Random.Default);
    }

    /**
     * Bring in the enemy ship's wingmen now, rather than on the next update,
     * so a scripted fight (the trailer's) can set them up straight away.
     */
    public void debugSpawnWingmen() {
        spawnWingmen();
    }

    /**
     * Spawn wingmen for the enemy ship, if the player's ship is big enough
     * to fight several of them at once and the enemy ship has just become hostile.
     */
    private void spawnWingmen() {
        if (getEnemy() == null || !isEnemyHostile() || wingmenSpawnedFor == getEnemy())
            return;
        wingmenSpawnedFor = getEnemy();

        // Bosses and scripted ships without a spec are always alone
        if (getEnemy().getBoss() != null || getEnemy().getSpec() == null)
            return;

        // Use the plan made when the ship was loaded, if there is one, and
        // otherwise (such as after loading a saved game) the usual number
        // of normal ships
        GameMap.SectorInfo sectorInfo = currentBeacon.getSector().getInfo();
        FightPlan plan = fightPlanFor == getEnemy() ? fightPlan : null;
        int count = plan != null ? plan.getSize() : Opponent.rampedEnemyShipCount(player.getType(), getEnemy().getType(),
                sectorInfo.getColumnNumber(), gameMap.getSectorCount(), Random.Default);
        // The screenshots and videos want exactly the ships they asked for
        if (debugWingmanSpecs != null) {
            count = 1 + debugWingmanSpecs.size();
            // Keep the plan's shapes for them, if it has one for each (see
            // debugFightShapes)
            if (plan != null && plan.getSize() != count)
                plan = null;
        }
        int sector = currentBeacon.getSector().getDifficultySectorNumber();
        for (int i = 1; i < count; i++) {
            EnemyShipSpec spec = getEnemy().getSpec();
            if (debugWingmanSpecs != null && i - 1 < debugWingmanSpecs.size())
                spec = debugWingmanSpecs.get(i - 1);
            ShipShape shape = plan != null ? plan.getShapes().get(i) : ShipShape.NORMAL;
            Ship ship = content.generator.buildShip(this, spec, sector, difficulty, null, shape);
            addWingman(ship, true);
        }

        if (!getWingmen().isEmpty())
            opponentsChanged();
    }

    /**
     * Update the ships the player's facing: each one's ship and AI, and
     * when it's destroyed, escapes or loses its crew, its rewards (each
     * gives its own). Only the main enemy ship escapes, and only it can be a
     * boss.
     */
    private void updateOpponents(float dt) {
        spawnWingmen();

        // (a copy, as ships are removed along the way)
        for (Opponent opponent : new ArrayList<>(opponents)) {
            Ship ship = opponent.getShip();
            boolean isMain = opponent == getMainOpponent();

            ship.update(dt);
            ship.setOpponentCloakActive(player.isCloakActive());

            if (opponent.getHostile()) {
                opponent.getAi().update(dt);
                ship.setHostileTime(ship.getHostileTime() + dt);

                // A boss's mechanics: its linked crew and so on
                if (ship.getColossal() != null && !ship.isGone())
                    ship.getColossal().update(dt);
            }

            if (ship.isGone()) {
                // Be sure to check it's still hostile: if its crew were
                // already killed, it can't give a second lot of rewards
                // when it later blows up.
                if (opponent.getHostile() && ship.getSpec() != null)
                    showShipRewards(ship, ship.getSpec().getDestroyed());
                if (opponent.getHostile())
                    colossalBeaten(ship);
                // The Ossuary Schematics salvage its drones as drone parts
                if (opponent.getHostile() && player.hasAugment(AugmentBlueprint.OSSUARY_SCHEMATICS))
                    player.setDronesCount(player.getDronesCount() + countDrones(ship));
                if (ship.getBoss() != null)
                    ship.getBoss().bossShipKilled(ship);
                removeOpponent(opponent);
                continue;
            }

            Float escapeTimer = ship.getEscapeTimer();
            if (escapeTimer != null && escapeTimer <= 0f) {
                // It's seen jumping away, with the FTL drive's sound
                // (The got-away event's only for the main ship, as it's
                // about "the enemy": a wingman just goes)
                IEvent jumpEvent = !isMain || ship.getSpec() == null ? null : ship.getSpec().getGotaway();
                IPoint shipPos = opponent.getUi().getShipPos();
                jumpAwayEffects.add(new JumpAwayEffect(ship.getHullImage(), new ConstPoint(
                        shipPos.getX() + ship.getHullOffset().getX(), shipPos.getY() + ship.getHullOffset().getY())));
                getSounds().getSample("jumpLeave").play();
                removeOpponent(opponent);
                if (jumpEvent != null)
                    showEventDialogue(jumpEvent.resolve(), Random.Default.nextInt());
                continue;
            }

            if (opponent.getHostile() && opponent.isCrewKilled(player)) {
                // As in vanilla, a boss's AI takes over once its crew are
                // dead, and it fights on, automated
                if (ship.getBoss() != null) {
                    ship.setAutomated(true);
                    showEventDialogue(eventManager.get("BOSS_AUTOMATED").resolve(), Random.Default.nextInt());
                    continue;
                }

                if (ship.getSpec() != null)
                    showShipRewards(ship, ship.getSpec().getDeadCrew());
                colossalBeaten(ship);
                zoltanShieldBoarded(ship);

                setHostile(opponent, false);
                // With no one to fly it, it's not escaping any more
                ship.setEscapeTimer(null);

                // If we jump back, the enemy ship shouldn't re-appear.
                // This also means there won't be a danger mark on the map.
                if (isMain)
                    currentBeacon.setShip(null);
            }

            // A crew-killed ship stays, with its hull closed once the
            // player's boarders are taken home, until the player jumps away
        }
    }

    /**
     * Count [ship], whose crew the player's just killed, if it has a Zoltan
     * Shield: once the boarders have taken [ZOLTAN_SHIELD_BOARDINGS] such
     * ships in a fight, the player gets one of their shields (which stack,
     * see AugZoltanShield), once a fight. Returns the shield when it's
     * given, and shows it.
     */
    @Nullable
    public AugmentBlueprint zoltanShieldBoarded(@NotNull Ship ship) {
        if (!(blueprintManager.getOrNull(AugZoltanShield.NAME) instanceof AugmentBlueprint shield)
                || !ship.getAugments().contains(shield))
            return null;
        zoltanShieldsBoarded++;
        if (zoltanShieldsBoarded != ZOLTAN_SHIELD_BOARDINGS)
            return null;
        long owned = player.getAugments().stream().filter(shield::equals).count();
        if (owned >= AugZoltanShield.MAX_STACKED)
            return null;

        if (shipUI != null) {
            DialogueWindow.SyntheticEvent reward = new DialogueWindow.SyntheticEvent(getTranslator().get("xftl_zoltan_shield_boarded"));
            reward.getResources().getItems().add(shield);
            shipUI.showSyntheticDialogue(reward);
        }
        return shield;
    }

    /**
     * Whether there's any ship still fighting the player, other than [except].
     */
    private boolean hasHostileShipsOtherThan(@Nullable Ship except) {
        for (Opponent opponent : opponents) {
            if (opponent.getShip() != except && opponent.getHostile() && !opponent.getShip().isGone())
                return true;
        }
        return false;
    }



    // This is, somewhat ironically, only used by the debug console itself.
    public void reloadDebugConsole() {
        debugConsole = null;
        debugConsoleVisible = false;
    }

    public void reloadDebugFlags() {
        debugFlags = new DebugFlagManager();
    }

    /**
     * Whether the player's ship can jump to [beacon] from the current one:
     * one next to it, or with Adv. FTL Navigation, any it's been to.
     */
    public boolean canJumpTo(Beacon beacon) {
        if (currentBeacon.getNeighbours().contains(beacon))
            return true;
        return beacon.getVisited() && player.hasAugment(AugmentBlueprint.ADV_FTL_NAVIGATION);
    }

    /**
     * Called as the player's ship enters a new [sector], before it arrives
     * at its start beacon: Distraction Buoys hold the rebels back for a jump.
     */
    public void sectorEntered(Sector sector) {
        if (player.hasAugment(AugmentBlueprint.DISTRACTION_BUOYS))
            sector.setFleetAdvanceModifier(Math.min(sector.getFleetAdvanceModifier(), 0) - 1);
    }

    public boolean isInDanger() {
        for (Opponent opponent : opponents) {
            if (opponent.getHostile())
                return true;
        }

        if (!player.getIntruders().isEmpty())
            return true;

        // Any hazard counts, including those added on top of a harmless
        // environment, such as asteroids in a mirage nebula
        return currentBeacon.isDangerous();
    }

    /**
     * Add a quest event marker, as required by an event.
     */
    @NotNull
    public QuestAddResult addQuest(Event questEvent) {
        return addQuest(questEvent, false);
    }

    /**
     * Add a quest event marker, as required by an event: if [ahead] (see
     * Event.questAhead), on the way to the sector's exit, a few jumps on, or
     * else in the next sector.
     */
    @NotNull
    public QuestAddResult addQuest(Event questEvent, boolean ahead) {
        Sector sector = currentBeacon.getSector();
        boolean wasAdded = sector.addQuest(currentBeacon, questEvent, false, ahead);

        if (wasAdded) {
            return QuestAddResult.CURRENT_SECTOR;
        } else if (sector.getSectorNumber() >= gameMap.getSectorCount() - 2) {
            // If we're on the sector before the last stand (sector 7, or 6
            // when zero-indexed, in vanilla) or later, the quest is skipped
            // as we shouldn't put quests into the last stand.
            return QuestAddResult.TOO_LATE;
        } else {
            delayedQuests.add(questEvent);
            return QuestAddResult.NEXT_SECTOR;
        }
    }

    /**
     * Save the entire game state to a new XML document.
     * <p>
     * This can then be trivially serialised, and loaded back into
     * a new {@link InGameState} instance.
     */
    public Document saveGameState() {
        Element root = new Element("xftlSaveGame");
        root.setAttribute("enableAE", Boolean.toString(enableAdvancedEdition));
        root.setAttribute("difficulty", difficulty.name());
        if (namePreset != null) {
            root.setAttribute("namePreset", namePreset);
        }

        ObjectRefs refs = new ObjectRefs();
        Sector sector = currentBeacon.getSector();

        // Generate IDs for all known ships
        refs.register(player, "player");
        for (Beacon beacon : sector.getBeacons()) {
            refs.register(beacon.getShip(), "ship");
        }
        if (getEnemy() != null && getEnemy().getBoss() != null) {
            refs.register(getEnemy(), "bossShip");
        }
        // A main enemy ship that isn't fighting isn't at the beacon any more
        // (see updateOpponents), so it's saved separately, to stay on the
        // screen (it might still have the player's crew aboard, and the
        // wingmen's windows stay where they are)
        boolean saveDefeatedEnemy = getEnemy() != null && !isEnemyHostile() && getEnemy().getBoss() == null
                && currentBeacon.getShip() != getEnemy();
        if (saveDefeatedEnemy) {
            refs.register(getEnemy(), "defeatedEnemy");
        }
        for (Opponent wingman : getWingmen()) {
            refs.register(wingman.getShip(), "wingman");
        }

        // Add the ID for the boss managers. We have to do this now, so the IDs
        // are available when we're writing the boss ship if there is one.
        for (BossManager boss : sector.getBosses()) {
            refs.register(boss, "boss");
        }

        // If the player ship has a custom layout, save that.
        if (player.getCustomised() != null) {
            Element customLayout = new Element("customLayout");
            player.getCustomised().saveToXML(customLayout);
            root.addContent(customLayout);
        }

        // Serialise the player ship
        Element playerShip = new Element("playerShip");
        player.saveToXML(playerShip, refs);
        root.addContent(playerShip);

        // Serialise the flagship, as it's not stored at a beacon like normal ships.
        if (getEnemy() != null && getEnemy().getBoss() != null) {
            Element flagshipElem = new Element("bossShip");
            getEnemy().saveToXML(flagshipElem, refs);
            root.addContent(flagshipElem);
        }

        // If there's a neutral ship (such as one that's crew-killed, perhaps with
        // some player-owned crew in it), serialise it. Once you crew-kill a ship
        // it's not saved by the beacon, so without this the ship would disappear
        // if you save-loaded, taking your crew with it.
        if (saveDefeatedEnemy) {
            Element flagshipElem = new Element("defeatedEnemy");
            getEnemy().saveToXML(flagshipElem, refs);
            root.addContent(flagshipElem);
        }

        // Serialise any wingmen, which like the flagship aren't stored at the beacon
        for (Opponent wingman : getWingmen()) {
            Element wingmanElem = new Element("wingman");
            SaveUtil.INSTANCE.addAttrBool(wingmanElem, "hostile", wingman.getHostile());
            wingman.getShip().saveToXML(wingmanElem, refs);
            root.addContent(wingmanElem);
        }

        // The rewards waiting for the rest of a fight to be over
        fleetRewards.saveToXML(root, refs);

        // Remember if the enemy ship already had wingmen, so they aren't
        // spawned again after they've been destroyed.
        if (getEnemy() != null && wingmenSpawnedFor == getEnemy()) {
            SaveUtil.INSTANCE.addAttrBool(root, "wingmenSpawned", true);
        }

        // Serialise the sector map (the one you access via exit beacons)
        Element mapXML = new Element("gameMap");
        gameMap.saveToXML(mapXML, refs);
        root.addContent(mapXML);

        // Serialise the sector
        Element sectorXML = new Element("currentSector");
        ObjectRefs sectorRefs = sector.saveToXML(sectorXML, refs);
        root.addContent(sectorXML);

        // Save the beacon the player is currently at - this is how
        // we'll know what enemy ship to load, we'll just use whatever
        // is at the same beacon.
        SaveUtil.INSTANCE.addRef(root, "currentBeacon", sectorRefs, currentBeacon);

        // Save the list of visited sectors, which is used by the sector map.
        Element visitedSectorsXML = new Element("visitedSectors");
        for (GameMap.SectorInfo visited : visitedSectors) {
            String ref = refs.get(visited);
            Element visitedElem = new Element("sectorInfo");
            visitedElem.setAttribute("idr", ref);
            visitedSectorsXML.addContent(visitedElem);
        }
        root.addContent(visitedSectorsXML);

        // If the player saves while they're currently in the event UI, save that.
        // (There's no UI in automated tests.)
        if (shipUI != null) {
            Element shipUiXML = new Element("shipUI");
            shipUI.saveToXML(shipUiXML, refs);
            root.addContent(shipUiXML);
        }

        return new Document(root);
    }

    private void loadGameState(Element root) {
        RefLoader refs = new RefLoader();

        // If the player ship has a custom layout, load that.
        Element customLayout = root.getChild("customLayout");
        EditableShip customised = null;
        if (customLayout != null) {
            customised = EditableShip.loadFromXML(customLayout);
        }

        // If there's another ship at this beacon, it's hostile, unless it's
        // a crew-killed one (see below)
        boolean loadedEnemyHostile = true;

        // Load the player ship
        Element playerShip = root.getChild("playerShip");
        player = deserialiseSingleShip(playerShip, refs, customised);

        // Load the flagship, if we're fighting it.
        Element flagshipElem = root.getChild("bossShip");
        Ship overrideCurrentBeacon = null;
        if (flagshipElem != null) {
            overrideCurrentBeacon = deserialiseSingleShip(flagshipElem, refs, null);
        }

        // Load a ship we crew-killed but still has our crew
        Element defeatedElem = root.getChild("defeatedEnemy");
        if (defeatedElem != null) {
            overrideCurrentBeacon = deserialiseSingleShip(defeatedElem, refs, null);
            loadedEnemyHostile = false;
        }

        // Load any wingmen. They're added once the enemy ship is set.
        List<Ship> loadedWingmen = new ArrayList<>();
        List<Boolean> loadedWingmenHostile = new ArrayList<>();
        for (Element wingmanElem : root.getChildren("wingman")) {
            loadedWingmen.add(deserialiseSingleShip(wingmanElem, refs, null));
            loadedWingmenHostile.add(SaveUtil.INSTANCE.getAttrBool(wingmanElem, "hostile"));
        }
        fleetRewards.loadFromXML(root, refs);
        boolean wingmenSpawned = root.getAttributeValue("wingmenSpawned") != null
                && SaveUtil.INSTANCE.getAttrBool(root, "wingmenSpawned");

        // Load the game map. The sector needs to reference it's SectorInfo
        // objects, so we need a separate reference loader we can switch
        // to resolve mode before loading the sector.
        RefLoader mapRefLoader = new RefLoader();
        Element mapXML = root.getChild("gameMap");
        gameMap = new GameMap(content, mapXML, mapRefLoader);
        mapRefLoader.switchToResolveMode();

        // Load the current sector - it looks odd to have a floating constructor,
        // but we only need it to create the current beacon and register it with
        // the reference loader. We then use our normal trick of only referring
        // to the current beacon, and letting that imply the current sector.
        Element sectorXML = root.getChild("currentSector");
        new Sector(sectorXML, refs, this, mapRefLoader);

        // (not in automated tests, which have no UI, as for a new game)
        Element shipUiXML = root.getChild("shipUI");
        if (mainGame != null) {
            shipUI = new PlayerShipUI(player, this);
            shipUI.loadFromXML(shipUiXML, refs);
        }

        // This resolves any async-resolved object references.
        refs.switchToResolveMode();

        // Load the list of visited sectors, which is used by the sector map.
        for (Element visitedElem : root.getChild("visitedSectors").getChildren("sectorInfo")) {
            String ref = visitedElem.getAttributeValue("idr");
            GameMap.SectorInfo visited = mapRefLoader.resolve(GameMap.SectorInfo.class, ref);
            visitedSectors.add(visited);
        }

        // We can finally load out our current beacon.
        currentBeacon = SaveUtil.INSTANCE.getRefImmediate(root, "currentBeacon", refs, Beacon.class);
        Objects.requireNonNull(currentBeacon);

        // Load in the previous enemy
        if (overrideCurrentBeacon != null) {
            setEnemy(overrideCurrentBeacon, loadedEnemyHostile);
        } else {
            setEnemy(currentBeacon.getShip(), loadedEnemyHostile);
        }

        // If the enemy ship's gone but a wingman is still fighting, it
        // becomes the enemy ship, like it would have during the fight.
        if (getEnemy() == null && !loadedWingmen.isEmpty()) {
            Ship next = loadedWingmen.remove(0);
            boolean hostile = loadedWingmenHostile.remove(0);
            setEnemy(next, hostile);
            wingmenSpawned = true;
        }

        // Add the wingmen back (setEnemy clears them)
        for (int i = 0; i < loadedWingmen.size(); i++) {
            addWingman(loadedWingmen.get(i), loadedWingmenHostile.get(i));
        }
        if (getEnemy() != null && (wingmenSpawned || !getWingmen().isEmpty())) {
            wingmenSpawnedFor = getEnemy();
        }

        // We might have loaded a beacon with a store
        if (shipUI != null)
            shipUI.updateButtons();
    }

    /**
     * Deserialise a player or enemy ship.
     * <p>
     * This should only be used by deserialisation code, specifically
     * the player ship deserialisation in this class and the enemy ship
     * deserialisation in beacons.
     */
    public Ship deserialiseSingleShip(Element shipElement, RefLoader refs, EditableShip customised) {
        // Load the appropriate ship definition XML element from the blueprints
        String shipId = shipElement.getAttributeValue("shipId");

        String specId = shipElement.getAttributeValue("specId");
        EnemyShipSpec spec;
        if (specId.equals("null")) {
            spec = null;
        } else {
            spec = content.eventManager.getShip(specId);
        }

        ShipBlueprint blueprint = blueprintManager.getShip(shipId);

        // And use that to build and deserialise the ship
        // Note we pass in null as the boss - that'll be updated if this turns out to be a boss.
        Ship ship = new Ship(blueprint, this, customised, spec, null);
        ship.loadFromXml(shipElement, refs);

        return ship;
    }

    @NotNull
    public Image getImg(String name) {
        Image img = content.images.get(name);
        if (img != null)
            return img;

        // This is just a way to access the image by a path, since we need
        // that for the missing animation.
        if (name.equals(Constants.MISSING_FILE_PATH))
            return getMissingImage();

        FTLFile file = df.getOrNull(name);
        if (file != null) {
            img = df.readImage(content.resourceContext, file);
        } else {
            System.out.printf("[WARN] Missing image: '%s'%n", name);
            img = getMissingImage();
        }
        content.images.put(name, img);
        return img;
    }

    /**
     * Loads an image if it's file exists, or returns null.
     * <p>
     * Try to avoid this if you can - normally image paths are specified in
     * some predictable way, so you shouldn't be guessing their names.
     */
    @Nullable
    public Image getImgIfExists(String name) {
        Image img = content.images.get(name);
        if (img != null)
            return img;

        FTLFile file = df.getOrNull(name);
        if (file == null)
            return null;

        img = df.readImage(content.resourceContext, file);
        content.images.put(name, img);
        return img;
    }

    /**
     * Creates a new {@link SILFontLoader} for a given font. This caches the bitmap and character
     * data, but the scale of each returned fontloader is completely independent.
     *
     * @param name The name of the font - the font at 'fonts/$name.font' is loaded
     * @return The new {@link SILFontLoader} instance
     */
    @NotNull
    public SILFontLoader getFont(String name) {
        SILFontLoader font = content.fonts.get(name);
        if (font != null) {
            // Make a new instance sharing the font data
            return new SILFontLoader(font);
        }

        font = new SILFontLoader(content.resourceContext, df, df.get("fonts/" + name + ".font"));
        content.fonts.put(name, font);
        return new SILFontLoader(font);
    }

    /**
     * Create a new {@link SILFontLoader} (see {@link #getFont(String)}), and also set the font's scale
     */
    @NotNull
    public SILFontLoader getFont(String name, float scale) {
        SILFontLoader font = getFont(name);
        font.setScale(scale);
        return font;
    }

    /**
     * Get or create a cursor for a given image path.
     */
    @NotNull
    public Cursor getCursor(String path) {
        Cursor cursor = content.cursors.get(path);
        if (cursor != null) {
            return cursor;
        }

        // For overlaying cursors
        String[] parts = path.split(",");

        // And for a frame of a strip of them, "strip.png@frame"
        String[] framed = parts[0].split("@");
        Integer frame = framed.length == 2 ? Integer.parseInt(framed[1]) : null;
        FTLFile main = df.get(framed[0]);
        FTLFile overlay = parts.length == 2 ? df.get(parts[1]) : null;

        cursor = new Cursor(content.resourceContext, df, main, overlay, frame);
        content.cursors.put(path, cursor);
        return cursor;
    }

    public Animations getAnimations() {
        return content.animations;
    }

    public SoundManager getSounds() {
        return content.sounds;
    }

    public SpecDeserialiser getUiLoader() {
        return uiLoader;
    }

    public RoomClickListener getClickEvent() {
        return clickEvent;
    }

    public void setClickEvent(RoomClickListener clickEvent) {
        this.clickEvent = clickEvent;
    }

    public Translator getTranslator() {
        return content.translator;
    }

    public GameMap getGameMap() {
        return gameMap;
    }

    public Beacon getCurrentBeacon() {
        return currentBeacon;
    }

    public BlueprintManager getBlueprintManager() {
        return blueprintManager;
    }

    public boolean isPaused() {
        return paused || shipUI.isWindowOpen();
    }

    /**
     * True if the game's been paused (with the pause key), rather than by a
     * window being open.
     */
    public boolean isPauseToggled() {
        return paused;
    }

    public void setPauseToggled(boolean paused) {
        this.paused = paused;
    }

    /**
     * Try to avoid using, this is generally indicative of little hacks.
     * <p>
     * It's generally better to go through SlickGame or something else to
     * keep the various moving parts separate.
     */
    public PlayerShipUI getShipUI() {
        return shipUI;
    }

    /**
     * Try to avoid using, this is generally indicative of little hacks.
     */
    /**
     * The screen's edges in the player's ship's space, where the projectiles
     * flying at it and away from it are: left, top, right, bottom.
     */
    public int[] getPlayerSpaceScreen() {
        int x = playerShipOffset.getX();
        int y = playerShipOffset.getY();
        return new int[]{-x, -y, screenWidth - x, screenHeight - y};
    }

    public Ship getPlayer() {
        return player;
    }

    /**
     * The main enemy ship (the first of the ships the player's facing), or
     * null if there isn't one.
     */
    @Nullable
    public Ship getEnemy() {
        Opponent main = getMainOpponent();
        return main == null ? null : main.getShip();
    }

    /**
     * The main enemy ship, with its AI and window (see {@link Opponent}), or
     * null if there isn't one.
     */
    @Nullable
    public Opponent getMainOpponent() {
        return opponents.isEmpty() ? null : opponents.get(0);
    }

    /**
     * Every ship the player's facing: the main enemy ship first, then its
     * wingmen.
     */
    @NotNull
    public List<Opponent> getOpponents() {
        return Collections.unmodifiableList(opponents);
    }

    /**
     * Whether the main enemy ship is fighting the player.
     */
    public boolean isEnemyHostile() {
        Opponent main = getMainOpponent();
        return main != null && main.getHostile();
    }


    /**
     * Find the ship that's the enemy of the specified one.
     * <p>
     * This is used for things like drones and weapon targeting,
     * which need to access the enemy ship if installed on the player,
     * or the player ship if installed on the enemey.
     */
    @Nullable
    public Ship getEnemyOf(Ship source) {
        if (source == player) {
            // With several enemy ships, this is the main one if it's still
            // fighting, otherwise the first wingman that is.
            for (Opponent opponent : opponents) {
                if (opponent.getHostile())
                    return opponent.getShip();
            }
            return null;
        }

        // If there isn't a fight going on, no-one should be shooting at anyone
        // else, and a ship that isn't present has no enemy.
        Opponent opponent = opponentFor(source);
        return opponent != null && opponent.getHostile() ? player : null;
    }

    /**
     * The ship the player's facing that's [ship], with its AI and window, or
     * null if it isn't one of them.
     */
    @Nullable
    public Opponent opponentFor(@Nullable Ship ship) {
        for (Opponent opponent : opponents) {
            if (opponent.getShip() == ship)
                return opponent;
        }
        return null;
    }

    /**
     * Check if [other] is a ship that [source] is fighting. Unlike
     * {@link #getEnemyOf}, this handles the player fighting several ships.
     */
    public boolean isEnemyOf(Ship source, @Nullable Ship other) {
        if (other == null)
            return false;
        if (source == player) {
            Opponent opponent = opponentFor(other);
            return opponent != null && opponent.getHostile();
        }
        return other == player && getEnemyOf(source) == player;
    }

    /**
     * All the ships [source] is fighting: for the player, those it's facing
     * that are hostile, and for them, the player.
     */
    @NotNull
    public List<Ship> getOpponentShips(Ship source) {
        List<Ship> ships = new ArrayList<>();
        if (source == player) {
            for (Opponent opponent : opponents) {
                if (opponent.getHostile())
                    ships.add(opponent.getShip());
            }
        } else {
            Ship opponent = getEnemyOf(source);
            if (opponent != null)
                ships.add(opponent);
        }
        return ships;
    }

    /**
     * All the other ships at this beacon, whether or not they're hostile:
     * the enemy ship, and any wingmen.
     */
    @NotNull
    public List<Ship> getAllEnemyShips() {
        List<Ship> ships = new ArrayList<>();
        for (Opponent opponent : opponents)
            ships.add(opponent.getShip());
        return ships;
    }

    /**
     * Pick which of the ships [source] is fighting to send a drone to.
     * This spreads them out, by picking the one with the fewest of its
     * drones around it.
     */
    @Nullable
    public Ship pickDroneTarget(Ship source) {
        Ship best = null;
        long bestCount = Long.MAX_VALUE;
        for (Ship ship : getOpponentShips(source)) {
            long count = ship.getExternalDrones().stream().filter(d -> d.getOwnerShip() == source).count();
            if (count < bestCount) {
                best = ship;
                bestCount = count;
            }
        }
        return best;
    }

    /**
     * Find an enemy ship's place: its window's slot, 0 for the enemy ship.
     * This is -1 for any other ship.
     */
    public int getEnemySlot(Ship ship) {
        Opponent opponent = opponentFor(ship);
        return opponent == null ? -1 : opponent.getSlot();
    }

    /**
     * The wingmen fighting alongside the enemy ship: the ships the player's
     * facing after the main one.
     */
    @NotNull
    public List<Opponent> getWingmen() {
        return opponents.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(opponents.subList(1, opponents.size()));
    }

    /**
     * The ships the player's sensors systems look at (see Ship.sensorsFor):
     * those still fighting, and those that have given up, or lost their
     * crew, while the player's boarders are still aboard, so their rooms
     * stay visible until the boarders are taken home. Those fighting come
     * first, so they're the ones to keep the sensors if there aren't enough.
     */
    @NotNull
    public List<Ship> getWatchedShipsLeftToRight() {
        List<Ship> ships = getHostileShipsLeftToRight();
        for (Opponent opponent : opponentsLeftToRight()) {
            Ship ship = opponent.getShip();
            // After those still fighting, so they keep their sensors first
            if (!opponent.getHostile() && !ship.isGone() && ship.hasCrewOwnedByShip(player))
                ships.add(ship);
        }
        return ships;
    }

    // The windows go to the left of the main enemy ship's, so the higher
    // the slot, the further left
    private List<Opponent> opponentsLeftToRight() {
        List<Opponent> ordered = new ArrayList<>(opponents);
        Collections.reverse(ordered);
        return ordered;
    }

    /**
     * The ships still fighting the player, in the order their windows are
     * across the screen, left to right. The player's sensors systems look
     * at them in this order (see Ship.sensorsFor): the first at the
     * leftmost one, and so on, moving on to the next as each is beaten.
     */
    @NotNull
    public List<Ship> getHostileShipsLeftToRight() {
        List<Ship> ships = new ArrayList<>();
        for (Opponent opponent : opponentsLeftToRight()) {
            if (opponent.getHostile() && !opponent.getShip().isGone())
                ships.add(opponent.getShip());
        }
        return ships;
    }

    /**
     * Find where an enemy ship (the main one or a wingman) is drawn on the screen.
     */
    public IPoint getEnemyPosition(@NotNull Ship ship) {
        Opponent opponent = opponentFor(ship);
        return (opponent != null ? opponent : getMainOpponent()).getUi().getShipPos();
    }

    /**
     * Check if the given ship is either the player or one it's facing.
     * <p>
     * This is intended for drones, which need to self-destruct
     * when the ship powering them is gone (regardless of which
     * ship had the system installed, or who jumped away).
     */
    public boolean isShipPresent(@NotNull Ship ship) {
        Objects.requireNonNull(ship);
        return ship == player || opponentFor(ship) != null;
    }

    /**
     * Try to avoid using, this is generally indicative of little hacks.
     */
    public IPoint getEnemyPosition() {
        return getMainOpponent().getUi().getShipPos();
    }

    public LootPool getLootPool() {
        return lootPool;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public HotkeyManager getHotkeyManager() {
        return content.hotkeyManager;
    }

    public List<GameMap.SectorInfo> getVisitedSectors() {
        return Collections.unmodifiableList(visitedSectors);
    }

    public CrewNameManager getNameManager() {
        return content.nameManagerFor(namePreset);
    }

    /**
     * The crew names preset picked for this game in the main menu, or null
     * for the one XFTL_NAMES picks.
     */
    @Nullable
    public String getNamePreset() {
        return namePreset;
    }

    public GameContent getContent() {
        return content;
    }

    public DebugFlagManager getDebugFlags() {
        return debugFlags;
    }

    public DebugConsole getDebugConsole() {
        // Create the debug console if it doesn't already exist
        if (debugConsole == null) {
            debugConsole = new DebugConsole(this);
        }

        return debugConsole;
    }

    /**
     * Stuff that happens in-game should be isolated to this specific game.
     * Think carefully about why you need to use this!
     */
    /**
     * Whether the game's been lost or won.
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * True for the main menu's scene (see {@link #forMainMenu}).
     */
    public boolean isMenuScene() {
        return menuScene;
    }

    /**
     * Update the main menu's scene: the player's ship and its crew, and the
     * windows' animations. The rest of the game (the environment, enemies
     * and so on) stands still.
     */
    public void updateMenuScene(float delta) {
        renderingDeltaTime = delta;
        player.update(delta);
        updatePlayerCrew();
        shipUI.updateAlways(delta);
    }

    /**
     * Mark the game as lost or won, so it isn't saved for continuing any
     * more, and delete its save.
     */
    public void markFinished() {
        finished = true;
        ContinueSave.delete();
    }

    public MainGame getMainGame() {
        // This is only used for stuff like switching out of the main state,
        // which we don't currently support testing. Since very few things
        // do that, automated testing for that is overkill.
        if (mainGame == null) {
            throw new UnsupportedOperationException("Cannot get main state while running tests");
        }

        return mainGame;
    }

    /**
     * True if stuff is currently being deserialised.
     * <p>
     * It's generally a bit ugly to use this, but it's an easy
     * way to stop stuff from happening while the game is being loaded.
     */
    public boolean isCurrentlyLoadingSave() {
        return isCurrentlyLoadingSave;
    }

    @NotNull
    public Difficulty getDifficulty() {
        return difficulty;
    }

    /**
     * Get the delta-time (of wall-clock time) that has elapsed this frame.
     * <p>
     * This is for use in animations that play regardless of whether the game
     * is paused or not. It must NOT be used for anything related to gameplay,
     * as that would break if we change the game speed.
     */
    public float getRenderingDeltaTime() {
        return renderingDeltaTime;
    }

    /**
     * If there's currently a room click listener, this returns the room that is
     * currently being hovered.
     *
     * @see #setClickEvent(RoomClickListener)
     */
    @Nullable
    public Room getHoveredRoom() {
        return hoveredRoom;
    }

    /**
     * True if this 'game' is actually an automated test.
     * <p>
     * Try to avoid using this where possible to keep the tests accurate,
     * but it's useful for things that relate to sound or rendering.
     */
    public boolean isRunningAutomatedTest() {
        return mainGame == null;
    }

    @NotNull
    public Image getMissingImage() {
        if (missingImage != null)
            return missingImage;

        missingImage = df.readImage(content.resourceContext, df.get("img/nullResource.png"));
        return missingImage;
    }

    public WindowRenderer getWindowRenderer() {
        if (content.windowRenderer != null)
            return content.windowRenderer;

        Image background = getImg("img/window_base.png");
        Image outline = getImg("img/window_outline.png");
        Image mask = getImg("img/window_mask.png");
        content.windowRenderer = new WindowRenderer(background, outline, mask);

        return content.windowRenderer;
    }

    public void givePlayerResources(@NotNull ResourceSet resources) {
        player.setFuelCount(player.getFuelCount() + resources.getFuel());
        player.setDronesCount(player.getDronesCount() + resources.getDroneParts());
        player.setMissilesCount(player.getMissilesCount() + resources.getMissiles());
        player.setScrap(player.getScrap() + resources.getScrap());

        for (Blueprint item : resources.getItems()) {
            player.addBlueprint(item, true);
        }

        for (LivingCrewInfo info : resources.getCrew()) {
            player.addCrewMember(info, false, false);
        }

        for (RemoveCrewEval removed : resources.getLostCrew()) {
            LivingCrew crew = removed.getCrew();
            RemoveCrew info = removed.getInfo();

            if (player.getClonebay() != null) {
                String cloneMessage = info.getCloneText().resolve(Random.Default);
                if (!cloneMessage.isBlank()) {
                    shipUI.showSyntheticDialogue(new DialogueWindow.SyntheticEvent(cloneMessage));
                }

                // Never actually remove the crewmember if they're cloned
                if (info.getClone()) {
                    crew.jumpTo(player.findSpaceForCrew(player.getClonebay().getRoom(), crew.getMode()));
                    crew.onCloned(); // Deducts skills
                    continue;
                }
            }

            if (info.getTurnHostile()) {
                crew.setOwnerShip(null);
            } else {
                crew.removeFromShip();
            }
        }

        // Put all the intruders in the same room, as far as possible.
        int intruderRoomId = Random.Default.nextInt(player.getRooms().size());
        Room intruderRoom = player.getRooms().get(intruderRoomId);

        for (LivingCrewInfo info : resources.getIntruders()) {
            LivingCrew intruder = player.addCrewMember(info, false, true);

            RoomPoint slot = player.findSpaceForCrew(intruderRoom, AbstractCrew.SlotType.INTRUDER);
            intruder.jumpTo(slot);
        }

        // Apply the hull damage, with the sound of a hit on the hull
        if (resources.getDamage().stream().anyMatch(d -> d.getAmount() > 0))
            getSounds().getSample("hitHull" + (1 + Random.Default.nextInt(3))).play();
        for (EventHullDamage damage : resources.getDamage()) {
            String system = damage.getSystem();
            if (system == null) {
                // A null system means hull damage only
                player.setHealth(player.getHealth() - damage.getAmount());
                continue;
            }

            Room targetRoom;

            if (system.equals("random")) {
                AbstractSystem randomSystem = player.getSystems().get(Random.Default.nextInt(player.getSystems().size()));
                targetRoom = randomSystem.getRoom();
            } else if (system.equals("room")) {
                targetRoom = player.getRooms().get(Random.Default.nextInt(player.getRooms().size()));
            } else {
                Optional<AbstractSystem> foundSystem = player.getSystems().stream()
                        .filter(s -> s.getCodename().equals(system))
                        .findFirst();
                if (foundSystem.isPresent()) {
                    targetRoom = foundSystem.get().getRoom();
                } else {
                    // Just pick a random room if this system isn't installed.
                    // This might not be what FTL does, but it should be close
                    // enough - in vanilla, this is only used to damage your
                    // engines so this case shouldn't come up without mods.
                    targetRoom = player.getRooms().get(Random.Default.nextInt(player.getRooms().size()));
                }
            }

            Objects.requireNonNull(targetRoom);

            player.damage(targetRoom, Damage.hullAndSys(damage.getAmount()), null);

            if (damage.getEffectFire()) {
                targetRoom.spawnFire();
                targetRoom.spawnFire();
            }
            if (damage.getEffectBreach()) {
                targetRoom.spawnBreach();
            }

            // TEST_EVENT is a good way to test this, as it spawns both of them.
        }

        // Apply system/reactor upgrades
        for (EventSystemUpgrade upgrade : resources.getUpgrades()) {
            if (upgrade.getSystem().equals("reactor")) {
                int newPower = player.getPurchasedReactorPower() + upgrade.getAmount();
                player.setPurchasedReactorPower(Math.min(newPower, player.getMaxReactorPower()));
                continue;
            }

            AbstractSystem system = player.getSystems().stream()
                    .filter(s -> s.getCodename().equals(upgrade.getSystem()))
                    .findFirst().orElse(null);

            if (system == null)
                continue;

            int newPower = system.getEnergyLevels() + upgrade.getAmount();
            system.setEnergyLevels(Math.min(system.getBlueprint().getMaxPower(), newPower));
        }

        // Apply the fleet pursuit modifier
        Sector currentSector = currentBeacon.getSector();
        currentSector.setFleetAdvanceModifier(currentSector.getFleetAdvanceModifier() + resources.getModifyPursuit());
    }

    /**
     * Advance the fleet pursuit by the amount determined by the current beacon.
     */
    public void advanceFleet() {
        Sector sector = currentBeacon.getSector();

        // The last stand works differently, capturing two beacons per jump.
        if (sector.isLastStand()) {
            advanceFleetLastStand();
            return;
        }

        // Where a boss hunts the player, it's that, and not the fleet, that
        // comes after them (and it repairs itself, see HunterBoss)
        if (!sector.getType().getHasFleet()) {
            for (BossManager boss : sector.getBosses())
                boss.advanceFleet();
            return;
        }

        // A boss holding its sector's exit mends while the player's away
        // from it (see ColossalBoss)
        for (Beacon beacon : sector.getBeacons()) {
            Ship bossShip = beacon.getShip();
            if (beacon != currentBeacon && bossShip != null && bossShip.getColossal() != null)
                bossShip.getColossal().repairWhileAway();
        }

        Point dangerZone = sector.getDangerZoneCentre();

        int advance = sector.getFleetAdvanceFor(currentBeacon, player.getType().getShipSize());

        dangerZone.setX(dangerZone.getX() + advance);

        for (Beacon beacon : sector.getBeacons()) {
            int distSq = beacon.getPos().distToSq(dangerZone);
            if (distSq > Sector.DANGER_ZONE_RADIUS_SQUARED)
                continue;

            beacon.setOvertaken(true);
        }

        // The modifier is a counter of how long we apply no or double pursuit,
        // so we have to bring it back towards zero.
        int modifier = sector.getFleetAdvanceModifier();
        if (modifier < 0)
            modifier++;
        if (modifier > 0)
            modifier--;
        sector.setFleetAdvanceModifier(modifier);
    }

    // How many beacons the fleet takes each jump in the last stand
    private static final int LAST_STAND_BEACONS_TAKEN = 2;

    /** How many phases the Mainframe has, and the break between them, in seconds. */
    public static final int MAINFRAME_PHASES = 3;
    public static final float MAINFRAME_BREAK = 20f;

    /** The events that start each of the Mainframe's phases after the first, with its number on the end. */
    public static final String MAINFRAME_PHASE_EVENT = "XFTL_MAINFRAME_PHASE_";

    /**
     * How many ships with a Zoltan Shield the player's boarders must take in
     * one fight to get one of their shields (see zoltanShieldBoarded).
     */
    public static final int ZOLTAN_SHIELD_BOARDINGS = 2;

    /**
     * How many jumps' worth the Rebel fleet surges forward when the boss
     * holding a sector's exits is beaten (see exitHoldBroken).
     */
    public static final int EXIT_HOLD_FLEET_LEAP = 4;

    private void advanceFleetLastStand() {
        Sector sector = currentBeacon.getSector();

        // The fleet takes two beacons a jump, wherever they are: not the
        // player's, the base, or where the flagship is (or is going)
        Set<Beacon> spared = new HashSet<>();
        spared.add(currentBeacon);
        for (BossManager boss : sector.getBosses()) {
            spared.add(boss.getBeacon());
            spared.add(boss.getNextBeacon());
        }
        List<Beacon> free = new ArrayList<>();
        for (Beacon beacon : sector.getBeacons()) {
            if (!beacon.isOvertaken() && !beacon.isBase() && !spared.contains(beacon))
                free.add(beacon);
        }
        Collections.shuffle(free);
        for (Beacon beacon : free.subList(0, Math.min(LAST_STAND_BEACONS_TAKEN, free.size())))
            beacon.setOvertaken(true);

        // Make the flagship jump
        for (BossManager boss : sector.getBosses()) {
            boss.advanceFleet();
        }

        // The Mainframe, at the base, mends while the player's away from it
        // (see ColossalBoss)
        for (Beacon beacon : sector.getBeacons()) {
            Ship bossShip = beacon.getShip();
            if (beacon != currentBeacon && bossShip != null && bossShip.getColossal() != null && !bossShip.isGone())
                bossShip.getColossal().repairWhileAway();
        }
    }

    /**
     * FOR DEBUG USE ONLY!
     * <p>
     * When reloading the game every frame, some UI stuff (like the selection
     * rectangle) breaks. It'd be a bit silly to serialise this, but it's
     * also very hard to meaningfully play with it being constantly cleared.
     * <p>
     * Thus, this copies over basic UI stuff that's very unlikely to cover
     * up any serialisation bugs.
     */
    void debugContinuousSaveRestore(InGameState prev) {
        // Swap over the entire debug console, since it has no state.
        // Without this, the debug console would close across a reload.
        // Note we copy it whether or not it's open, so we don't loose
        // it's command history when you close it.
        if (prev.debugConsole != null) {
            debugConsole = prev.debugConsole;
            debugConsole.setGame(this);
        }
        debugConsoleVisible = prev.debugConsoleVisible;

        shipUI.debugContinuousSaveRestore(prev.shipUI);
        System.arraycopy(prev.mouseDownPrev, 0, mouseDownPrev, 0, mouseDownPrev.length);

        paused = prev.paused;

        // Copy over the debug flags.
        // It'd be annoying to lose those, since they're not supposed
        // to be saved.
        List<DebugFlagManager.DebugFlag> oldFlags = prev.debugFlags.getAll();
        List<DebugFlagManager.DebugFlag> newFlags = debugFlags.getAll();
        for (int i = 0; i < newFlags.size(); i++) {
            newFlags.get(i).setSet(oldFlags.get(i).getSet());
        }
    }

    /**
     * FOR DEBUGGING USE ONLY!
     * <p>
     * This is called by {@link MainGame} when we're in continuous save-load mode,
     * and there's an exception during the save-load process.
     */
    public void debugFailedSaveRestore() {
        // Pause the game so we don't get an ongoing stream of these errors.
        paused = true;

        // Print a message in the debug console, so the player knows what happened.
        debugConsoleVisible = true;
        getDebugConsole().onFailedSaveRestore();
    }

    /**
     * Update the list of player-owned crew, to account for crew being added or removed.
     */
    public void updatePlayerCrew() {
        // Ignore calls during deserialisation
        if (isCurrentlyLoadingSave)
            return;

        playerCrew.clear();

        addPlayerCrew(player.getCrew());
        if (player.getClonebay() != null)
            addPlayerCrew(player.getClonebay().getQueue());
        // (and boarders on the ships the player's facing, so they're listed and can be picked)
        for (Opponent opponent : opponents)
            addPlayerCrew(opponent.getShip().getCrew());

        // List them in the order they joined, wherever they are, so a new
        // crewmember goes at the end, and boarders don't move about as
        // they leave the ship and come back. Those not listed before (new
        // ones, or all of them in an older save) go after the rest, in the
        // order they were found.
        int next = 0;
        for (LivingCrew crew : playerCrew)
            next = Math.max(next, crew.getListOrder());
        for (LivingCrew crew : playerCrew) {
            if (crew.getListOrder() == 0)
                crew.setListOrder(++next);
        }
        playerCrew.sort(Comparator.comparingInt(LivingCrew::getListOrder));
    }

    private <T extends AbstractCrew> void addPlayerCrew(List<T> crewList) {
        for (AbstractCrew crew : crewList) {
            // Filter out drones
            if (!(crew instanceof LivingCrew living))
                continue;

            // Filter out intruders, not including mind-controlled crew.
            if (living.getOwnerShip() != player)
                continue;

            playerCrew.add(living);
        }
    }

    public List<LivingCrew> getPlayerCrew() {
        return playerCrew;
    }

    public Map<Hotkey, HotkeyButton> getReverseHotkeyBindings() {
        return Collections.unmodifiableMap(reverseHotkeyBindings);
    }

    /**
     * Whether the key the hotkey's bound to is held down.
     */
    public boolean isHotkeyHeld(String hotkeyID, Input in) {
        Hotkey key = getHotkeyManager().getByID().get(hotkeyID);
        HotkeyButton button = key == null ? null : reverseHotkeyBindings.get(key);
        return button != null && in.isKeyDown(button.getKeyID());
    }

    public String getButtonNameForHotkey(String hotkeyID, String defaultName) {
        Hotkey key = getHotkeyManager().getByID().get(hotkeyID);
        if (key == null) {
            return defaultName;
        }

        HotkeyButton button = reverseHotkeyBindings.get(key);
        if (button == null) {
            return defaultName;
        }

        return getTranslator().get(button.getText());
    }

    /**
     * Update our cache of which key each hotkey is bound to.
     * <p>
     * This should be called if you modify the keybinds inside the [SaveProfile].
     */
    public void updateHotkeyBindings() {
        assert mainGame != null;
        getHotkeyManager().calculateBindings(mainGame.getProfile(), forwardHotkeyBindings, reverseHotkeyBindings);
    }

    private void setEnemyIsHostile(boolean hostile) {
        Opponent main = getMainOpponent();
        if (main != null)
            setHostile(main, hostile);
    }

    /**
     * Make [opponent] start or stop fighting the player.
     */
    private void setHostile(Opponent opponent, boolean hostile) {
        if (opponent.getHostile() == hostile)
            return;

        opponent.setHostile(hostile);

        player.enemyShipUpdated();
        opponent.getShip().enemyShipUpdated();
        turnWeaponsOff(opponent.getShip());
    }

    /**
     * Turn [ship]'s weapons off, as it stops fighting - this is only a
     * visual thing at this point, since the AI isn't being updated to fire
     * them.
     */
    private static void turnWeaponsOff(Ship ship) {
        for (Ship.Hardpoint hardpoint : ship.getHardpoints()) {
            if (hardpoint.getWeapon() == null)
                continue;

            Weapons weapons = ship.weaponsFor(hardpoint.getWeapon());
            if (weapons != null)
                weapons.setWeaponPower(hardpoint.getWeapon(), false);
        }
    }

    void showEventDialogue(Event event, int seed) {
        // The UI is null for automated tests
        if (shipUI != null) {
            shipUI.showEventDialogue(event, seed);
        }
    }

    public boolean isPlayerCrewFull() {
        return playerCrew.size() >= player.getMaxCrew();
    }

    public boolean playerHasTooManyCrew() {
        return playerCrew.size() > player.getMaxCrew();
    }

    public interface RoomClickListener {
        void roomClicked(Room room, GameContainer gc);

        default boolean canTargetRoom(@NotNull Room room) {
            return true;
        }
    }

    public enum QuestAddResult {
        CURRENT_SECTOR,
        NEXT_SECTOR,
        TOO_LATE,
    }

    /**
     * This represents all the parsed resources that can be pulled from a {@link Datafile}.
     * <p>
     * It's kept separate so that stuff like restarting the game (or starting a new game
     * where the mods and Advanced Edition mode are the same) is fast.
     */
    public static class GameContent {
        public final Datafile datafile;
        public final boolean enableAdvancedEdition;

        public final BlueprintManager blueprintManager;
        public final EventManager eventManager;
        public final CrewNameManager nameManager;
        public final Animations animations;
        public final SoundManager sounds;
        public final Translator translator;
        public final ShipGenerator generator;
        public final Achievement.AchievementTable achievements;
        public final ShipFamily.FamilyTable shipFamilies;
        public final HotkeyManager hotkeyManager;

        // These are loaded on-demand
        private final Map<String, Image> images = new HashMap<>();
        private final Map<String, SILFontLoader> fonts = new HashMap<>();
        private final Map<String, Cursor> cursors = new HashMap<>();

        // The context that owns the images and fonts
        private final ResourceContext resourceContext = new ResourceContext();

        // This is only here to avoid wasting heaps of resources in cont-save-load mode.
        private WindowRenderer windowRenderer;

        public GameContent(Datafile datafile, boolean enableAdvancedEdition) {
            this.datafile = datafile;
            this.enableAdvancedEdition = enableAdvancedEdition;

            blueprintManager = new BlueprintManager(datafile, enableAdvancedEdition);
            animations = new Animations(datafile);
            sounds = new RealSoundManager(datafile, resourceContext);
            generator = new ShipGenerator(datafile, blueprintManager);
            translator = new Translator(datafile, "en");
            eventManager = new EventManager(datafile, translator, blueprintManager);
            nameManager = new CrewNameManager(datafile, "en");
            achievements = new Achievement.AchievementTable(datafile);
            hotkeyManager = new HotkeyManager();

            blueprintManager.finishLoading(this);

            // TODO load this via the usual asset system, so mods can override it
            SAXBuilder builder = new SAXBuilder();
            builder.setExpandEntities(false);
            try {
                Document doc = builder.build(getClass().getResourceAsStream("/assets/data/xftl_ships.xml"));
                shipFamilies = new ShipFamily.FamilyTable(doc);
            } catch (JDOMException | IOException e) {
                throw new RuntimeException("Failed to load ship data", e);
            }
        }

        /**
         * Wrap some pre-loaded content.
         * <p>
         * This is used by automated tests.
         */
        public GameContent(
                Datafile datafile, boolean enableAdvancedEdition,
                BlueprintManager blueprintManager, EventManager eventManager,
                CrewNameManager nameManager, Animations animations,
                SoundManager sounds, Translator translator,
                ShipGenerator generator, Achievement.AchievementTable achievements,
                ShipFamily.FamilyTable shipFamilies, HotkeyManager hotkeyManager
        ) {
            this.datafile = datafile;
            this.enableAdvancedEdition = enableAdvancedEdition;
            this.blueprintManager = blueprintManager;
            this.eventManager = eventManager;
            this.nameManager = nameManager;
            this.animations = animations;
            this.sounds = sounds;
            this.translator = translator;
            this.generator = generator;
            this.achievements = achievements;
            this.shipFamilies = shipFamilies;
            this.hotkeyManager = hotkeyManager;
        }

        // The crew names for each preset picked in the main menu, loaded
        // when they're first needed
        private final Map<String, CrewNameManager> presetNameManagers = new HashMap<>();

        /**
         * The crew names from a preset (see CrewNameManager), or the ones
         * XFTL_NAMES picks ({@link #nameManager}) for null.
         */
        public CrewNameManager nameManagerFor(@Nullable String preset) {
            if (preset == null) {
                return nameManager;
            }
            return presetNameManagers.computeIfAbsent(preset, p -> new CrewNameManager(datafile, "en", p));
        }

        public void freeResources() {
            resourceContext.freeAll();
        }
    }

    private class GameUIProvider implements UIProvider {
        @NotNull
        @Override
        public SILFontLoader getFont(@NotNull String name) {
            return InGameState.this.getFont(name);
        }

        @NotNull
        @Override
        public Image getImg(@NotNull String path) {
            return InGameState.this.getImg(path);
        }

        @Nullable
        @Override
        public String translate(@NotNull String key) {
            return getTranslator().getTranslations().get(key);
        }

        @Nullable
        @Override
        public Colour getDebugOutlineColour(@NotNull Widget widget) {
            return null;
        }

        @NotNull
        @Override
        public WindowRenderer getWindowRenderer() {
            // Not the field: it's only made once something draws a window
            return InGameState.this.getWindowRenderer();
        }
    }
}
