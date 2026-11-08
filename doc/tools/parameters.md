# Options and debug settings

Environment variables (and a few Java properties) that change how the game
runs. Most have a `Makefile` target too.

## Window

- `XFTL_WINDOW_SIZE=1600x1000` (or `make window WIDTH=1600 HEIGHT=1000`):
  a window of a given size, rather than 1920x1080 (less the title bar and
  taskbar). Without it, the main screen needs to be at least 1920x1080 (as
  it looks, on a Retina screen) to fit the big ships' interface: on a
  smaller one, the game says so instead of starting.
- `-Dxftl.small-window=true`: a 1440x900 window.

## Saves and menus

- `XFTL_DATAFILE=/path/to/ftl.dat`: the game's data file, rather than the
  one the game finds itself (see the README).
- `XFTL_SAVE_DIR=<dir>` (or `-Dxftl.save-dir`, or `make run SAVE_DIR=<dir>`):
  keep the saved game (`continue.xml`) in another directory, to play a
  separate game without touching the usual one. The profile, mods and the
  path to `ftl.dat` stay in the usual saves directory.
- `XFTL_SKIP_MENU=1`: skip the main menu, and continue the saved game, or
  start a new game with the ship the hangar picks by default (the
  Chonkestrel, with the mod). The pause and game over menus don't have the
  hangar and main menu buttons.
- `XFTL_SHOW_HANGAR=1`: show the hangar buttons in the pause and game over
  menus, to pick another ship. They're hidden otherwise (and always with
  `XFTL_SKIP_MENU`).
- `XFTL_NAMES=<preset>` (or `-Dxftl.names`): use a different set of crew
  names (in games not started from the main menu's new game window, which
  has its own choice of them), one of `american`, `english`, `irish`,
  `scottish`, `french`, `spanish`, `italian`, `german`, `russian`, `arabic`,
  `turkish`, `indian`, `chinese`, `japanese`, `korean`, `starwars`,
  `startrek` or `masseffect`, with 100 male and 100 female names each, or
  `all` for all of them at once (the `Makefile`'s default). The default is
  the game's own names (plus the Chonkestrel mod's). See [names.md](../game/names.md).
- `-Dxftl.sectors=N`: change the number of sectors.

## Debugging

- `XFTL_DEBUG=1` (`make debug`):
  - every system installed, maxed out and turned on (the oxygen and medbay
    at one power, to leave more for the rest), and a reactor of 80, short of
    the limit so the backup batteries can be tested;
  - 99 fuel, missiles and drone parts, and 1000 scrap;
  - the ship's debug loadout, with every drone slot and the whole cargo
    hold full of random twin and triple drones (see
    [drone-sizes.md](../game/drone-sizes.md)), whether or not there's the
    power for them all;
  - every beacon in a sector is scanned, as if the map was revealed and
    the long-range scanners reached everywhere;
  - the beacons whose events can start a quest (such as the act 2
    civilian sectors' own quests) are marked on the map: their label gets
    a star (such as "DISTRESS*"), or with no label they're "QUEST*"
    (`Event.givesQuest`);
  - jumps to any beacon in the sector, not just the next ones (like an
    Advanced FTL Navigation for every beacon);
  - the sector map can be opened from any beacon, to jump to any sector
    (including the last stand);
  - a red "Skip Event" option.

  `make rich` is debug mode with the systems and reactor at the ship's
  starting levels.
- `XFTL_RANDOMIZE=1` (with `XFTL_DEBUG`, `make randomize`): different random
  weapons in every weapons system (a double and a triple one of each kind,
  see [weapon-sizes.md](../game/weapon-sizes.md)), the drones and cargo as
  in debug mode, and random augments. It never picks the Healing Burst or the Repair
  Burst, which only target your own ship.
- `XFTL_ZOLTAN=1`: every augment slot is filled with a Zoltan shield.
- `XFTL_AUTOPLAY=1`: the game plays itself, faster than real time, to find
  crashes, and writes a report of the exceptions it hit, into
  `XFTL_AUTOPLAY_DIR` (`dist/playtest` by default), until the last stand, a
  game over, `XFTL_AUTOPLAY_MINUTES` or `XFTL_AUTOPLAY_SECTORS`, at
  `XFTL_AUTOPLAY_SPEED` times real time. See [tools/playtest.md](playtest.md).

## Making media for the site

- `XFTL_SCREENSHOTS=<dir>`: take screenshots of the main features into a
  directory, for the site, then quit. See
  [tools/screenshots.md](screenshots.md).
- `XFTL_VIDEO=<dir>`: record the release trailer into a video in that
  directory, named after the time, then quit. See
  [tools/trailer.md](trailer.md).
