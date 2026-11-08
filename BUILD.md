# 🛠️ Building XFTL

The repo keeps the code and the mod's XML, but no images made from FTL's
own: all of the Chonkestrel mod's images, and the app's icons, are made
from `ftl.dat` (see [doc/tools/art.md](doc/tools/art.md)).
FTL's art isn't ours to share, and the images can always be made again,
exactly as they were.

## What it needs

- **JDK 17** (the build doesn't work with newer ones yet).
- **FTL's `ftl.dat`**, from the game (Steam, GOG, Epic Games or the macOS
  app). The `Makefile` uses `XFTL_DATAFILE` if it's set, or else `ftl.dat`
  in the repo's own directory: copy it there, or link it
  (`ln -s /path/to/ftl.dat ftl.dat`); git ignores it. `make
  DATAFILE=/path/to/ftl.dat` works too.
- **Python 3** with Pillow, NumPy and SciPy, for the art:

  ```sh
  python3 -m pip install pillow numpy scipy
  ```

## 1. The art

```sh
make art
```

It runs the scripts in `tools/` in turn, each making part of the mod's art
in `mods/chonkestrel/img/` (and their ships' layouts and blueprints in
`mods/chonkestrel/data/`) from the images in `ftl.dat`:

| Script | Makes |
|---|---|
| `tools/ship_variants.py` | the long and wide versions of the vanilla enemy ships |
| `tools/hybrid_ships.py` | the hybrid enemy ships |
| `tools/boss_ships.py` | the size 3 bosses |
| `tools/weapon_sizes.py` | the twin and triple weapons |
| `tools/system_buttons.py` | the systems' buttons for more than three power bars |
| `tools/drone_sizes.py` | the twin and triple Bulwarks |
| `tools/chonkestrel_art.py` | the Chonkestrel itself: its hull, cloak, floor, gibs, shield, rooms and miniature, from the Kestrel's |

The order matters: it's the order of their blocks in the mod's
`autoBlueprints.xml.append`, which they rewrite (in another order, the
file comes out the same but shuffled, and shows up as changed in git).
(Only the first three write it; the rest can go in any order.) It takes
about half a minute, and leaves `mods/chonkestrel/img/.generated`
to say it's done.

Every `make` target that runs the game (`make run`, `make debug` and so
on) runs `make art` first if the art's missing or one of the scripts has
changed since. Run `make art` by hand after pulling changes to the
scripts, or to make it all again.

## 2. Build and run

```sh
make run       # Play, with the Chonkestrel mod
make debug     # Debug mode (see doc/tools/parameters.md)
make test      # Run the tests
```

or with Gradle itself:

```sh
XFTL_DATAFILE=/path/to/ftl.dat ./gradlew build   # Build and run the tests
XFTL_DATAFILE=/path/to/ftl.dat ./gradlew run     # Play
```

`make run` also adds the mod to the game's mod list (`cache/mods/order.txt`
on macOS) if there isn't one yet; see the [README](README.md) for other
systems.

## 3. The icons

```sh
make icon
```

It renders the Chonkestrel as it looks in the hangar with the game, then
makes the app's icons from it: `tools/release/icon/chonkestrel.png`,
`.icns` (macOS) and `.ico` (Windows), and the window icon,
`src/main/resources/assets/img/xftl_icon.png`. Without them the game still
runs, with the default window icon. Run it again if the ship changes.

## 4. A release

```sh
make release
```

It makes the art and the icons first if they're missing, then packages the
game for players with its own Java: a .dmg for macOS and a .zip for
Windows. See [doc/tools/release.md](doc/tools/release.md).

## What's left out of git

`.gitignore` leaves out `ftl.dat`, if it's in the repo's directory, and
everything `make art` and `make icon` make:

- `mods/chonkestrel/img/**/*.png`, every image of the mod;
- `mods/chonkestrel/img/.generated`;
- `tools/release/chonkestrel-hangar.png` and `tools/release/icon/`;
- `src/main/resources/assets/img/xftl_icon.png`.

The XML the scripts write (the generated blocks in
`mods/chonkestrel/data/`) is kept, as it's text, and shows what changed
when a script does.
