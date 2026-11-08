# 🚀 XFTL

A reimplementation of FTL: Faster Than Light, which runs on the game's
original `ftl.dat`. This fork adds support for bigger ships, longer games
and bigger stores, plus a mod that adds a big ship, the Chonkestrel.

- **What's new, downloads and screenshots:** <https://chonkestrel.gitlab.io>
- **What changed in each release:** [CHANGELOG.md](CHANGELOG.md)

## 🛠️ Building and running

It needs JDK 17 and FTL's `ftl.dat` (the tests need it too), and Python 3
for the mod's art, which isn't in git: `make art` makes it from `ftl.dat`.
The `Makefile` looks for `ftl.dat` in this directory (git ignores it), or
wherever `XFTL_DATAFILE` says.
See [BUILD.md](BUILD.md) for every step.

```sh
XFTL_DATAFILE=/path/to/ftl.dat ./gradlew build   # Build and run the tests
XFTL_DATAFILE=/path/to/ftl.dat ./gradlew run     # Play
```

The `Makefile` has shortcuts: `make run` to play, `make debug` for debug
mode, and more in [doc/tools/parameters.md](doc/tools/parameters.md). `make release` packages
the game for players, with its own Java: a .dmg for macOS and a .zip for
Windows. See [doc/tools/release.md](doc/tools/release.md).

Without `XFTL_DATAFILE`, the game finds `ftl.dat` itself: next to the game,
in the path it saved last time (`ftl-path.txt` in the saves directory), or
in Steam, GOG, Epic Games or an FTL app on macOS. If it finds more than one,
or none, it asks, and the player can browse for `ftl.dat` (or the game's
executable or app), or drop it onto the window.

## 🛸 The Chonkestrel mod

`mods/chonkestrel` is a Slipstream mod that adds the Chonkestrel, a big,
symmetric take on the Kestrel A, and the second act's story and sectors.
To enable it, add the path to this repo's `mods/chonkestrel` to
`mods/order.txt` in the saves directory: `cache` in the directory the game
is run from on macOS, `Saved Games\Project Wormhole` on Windows and
`~/.local/share/ProjectWormhole` on Linux. For example:

```
/path/to/xftl/mods/chonkestrel
```

A relative path is relative to that `mods` directory. On macOS, `make run`
(and every other target that runs the game) makes `cache/mods/order.txt`
with the mod in it, if there isn't one yet.

## 📚 Documentation

- [doc/tools/parameters.md](doc/tools/parameters.md): environment variables, debug mode and
  the automatic playtest.
- [doc/tools/modding.md](doc/tools/modding.md): XFTL's XML extensions for ships,
  sectors and events.
- [doc/tools/tests.md](doc/tools/tests.md): what the automated tests cover.
- [doc/tools/art.md](doc/tools/art.md): where the new art is, and the scripts
  that make it from vanilla's.
- How things work: [evasion](doc/game/evasion.md), [hull](doc/game/hull.md),
  [oxygen](doc/game/oxygen.md), [weapon sizes](doc/game/weapon-sizes.md),
  [stores](doc/game/stores.md), [nebulas](doc/game/nebulas.md),
  [extreme hazards](doc/game/extreme-hazards.md),
  [ship shapes](doc/game/ship-shapes.md), [hybrid ships](doc/game/hybrid-ships.md)
  and [crew names](doc/game/names.md).
- The campaign: [the campaign](doc/game/lore.md), [the story](doc/game/story.md),
  [sectors](doc/game/sectors.md) and [event statistics](doc/game/events.md).
- Internal tools, in [doc/tools](doc/tools): the
  [automatic playtest](doc/tools/playtest.md),
  [screenshots](doc/tools/screenshots.md), the
  [trailer](doc/tools/trailer.md) and [packaging](doc/tools/release.md).

## 📜 Credits and licence

- **XFTL** is a fork of [XFTL](https://gitlab.com/znixian/xftl) by Campbell
  Suter (ZNix), the clean-sheet reimplementation of FTL's engine it's built
  on, with contributions from Kommandant_Julk and The Dumb Dino. This fork
  adds the bigger ships, the longer game, the Chonkestrel mod and the rest
  in the [changelog](CHANGELOG.md).
- **Slipstream:** the Slipstream mods are applied with part of the
  [Slipstream Mod Manager](https://github.com/Vhati/Slipstream-Mod-Manager)
  by Vhati, in [slipstream/](slipstream/README.md).
- **Reverse engineering notes:** the code's comments refer to the original
  XFTL's notes on how FTL works (its `doc/crew-ai`, `doc/sector-map` and so
  on), which are in [its repo](https://gitlab.com/znixian/xftl/-/tree/master/doc).
- **Code from other projects, in this repo:** parts of
  [JOrbis](http://www.jcraft.com/jorbis/) by ymnk, JCraft, Inc. (LGPL v2,
  in `src/main/java/com/jcraft/jorbis`), and of
  [Slick2D](https://slick.ninjacave.com/) by Kevin Glass, with Matthias
  Mann's PNG decoder (BSD, in `src/main/java/org/newdawn/slick`), changed
  for XFTL, with their notices in the files.
- **Libraries:** [LWJGL](https://www.lwjgl.org/) (with GLFW, OpenAL Soft,
  stb and tinyfiledialogs), Slick2D, JOrbis, JDOM, the Kotlin standard
  library, and Slipstream's (Saxon-HE, XML Resolver, Jackson and others),
  and the [Roboto](https://fonts.google.com/specimen/Roboto) font, each
  under its own licence. The release packages have them all in their
  `licences` folder, from [tools/release/licences](tools/release/licences/README.txt).
- **Licence:** the GNU General Public License, version 2, as XFTL's
  ([LICENCE.txt](LICENCE.txt)).
- **FTL: Faster Than Light** and its art, music and data belong to
  [Subset Games](https://subsetgames.com/). This project isn't made or
  endorsed by them, and holds none of FTL's files: it runs on the
  `ftl.dat` of your own copy of FTL, and makes its mod's new ships and
  weapons from the art in it (see [BUILD.md](BUILD.md)).
