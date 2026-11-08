# Releasing the game

## Packaging

The game is packaged for players with its own Java, so they don't need to
install anything, and with the Chonkestrel mod turned on:

```sh
make release                  # The version in gradle.properties, like 0.1-alpha
make release VERSION=0.2-alpha
```

That builds the game's jar and runs `tools/release/package.py`, which makes, in
`dist/release/`:

The version is what players see, in the packages' names, the game's log,
and the bottom-right corner of the screen, with the date the jar was built
(written into the jar as `xftl-build.properties`, see `BuildInfo`). The
OSes see a build number made from the date, like 2026.10.21, as macOS
wants three numbers, the first not 0.

- `Chonkestrel-<version>-macos-arm64.dmg`: `Chonkestrel.app`, to drag into
  Applications. It's for the architecture of the Mac it's made on (Apple
  Silicon here); an Intel Mac needs one made on an Intel Mac.
- `Chonkestrel-<version>-windows-x64.zip`: a `Chonkestrel` folder with
  `Chonkestrel.exe` in it, to unzip anywhere and run.
- `Chonkestrel-<version>-linux-x64.tar.gz`: a `Chonkestrel` folder with a
  `Chonkestrel` script in it, which starts the game with the Java beside
  it, to extract anywhere and run.

Each is about 125 MB. `python3 tools/release/package.py <version> mac` (or
`windows`, or `linux`) makes just one. `windows-arm64` makes a zip for ARM64
Windows, such as Windows in Parallels on an Apple Silicon Mac (the x64 one
runs there too, but slowly, as Windows emulates Java and its JIT compiler
for it). It isn't made by default, or offered on the site.

## How they're made

They all use the JDK's tools, with the JDK 17 in `JAVA_HOME`:

- **jlink** makes a Java runtime with only the modules the game uses
  (`MODULES` in `package.py`).
- On macOS, **jpackage** makes the app and the .dmg, so the Mac one has to be
  made on a Mac. The app starts Java with `-XstartOnFirstThread`, which
  GLFW needs on macOS.
- The Windows one is put together the way jpackage would on Windows, so it
  can be made on a Mac too: the runtime is made by jlink from a Windows JDK
  of the same version (downloaded once into `dist/release/cache/`, about
  190 MB: Temurin's for x64, and Microsoft's for ARM64, as there's no
  Temurin 17 for it), and `Chonkestrel.exe` is the launcher Windows' jpackage uses, from
  that JDK's `jdk.jpackage` module, with its settings in
  `app/Chonkestrel.cfg`. The executable has the launcher's own icon, as
  setting it needs Windows' tools, but the window has the Chonkestrel's.
- The Linux one is put together the same way, with its runtime made by
  jlink from Temurin's Linux x64 JDK of the same version (also downloaded
  into `dist/release/cache/`). It's started by a shell script, with the
  same settings as the others, rather than a launcher, and keeps the
  files' permissions in the .tar.gz.

The app is told it's packaged with system properties (see `PackagedGame`):

- `xftl.packaged`: it keeps its saves in the usual place for the OS:
  `~/Library/Application Support/Chonkestrel` on macOS, and
  `Saved Games\Chonkestrel` on Windows, and `~/.local/share/Chonkestrel`
  on Linux (or under `XDG_DATA_HOME`).
- `xftl.bundled-mods`: the mods it comes with, in the app's `mods/` folder,
  with their `order.txt`. They're used unless the player has their own
  `mods/order.txt` in the saves folder.

It finds `ftl.dat` like the game run from the repo (see the README), and also
next to the app: next to `Chonkestrel.app` on macOS, or in the `Chonkestrel`
folder on Windows and Linux. It starts in the main menu, where Continue loads
the saved game (`continue.xml` in the saves folder, see `ContinueSave`) and
New Game starts with the Chonkestrel, and it checks the screen is big
enough (1920x1080), the size of its window.

## Licences

Every package has a `licences` folder next to the game's jar (in the app's
`app/` folder): the game's `LICENCE.txt`, and the licences of the libraries
in the jar and the native libraries in them, listed in its `README.txt`
(from `tools/release/licences/`). The Java runtime has its own, in its
`legal` folder. When a library's added, or one brings others with it, its
licence goes in there too.

## When it won't start

The packaged game copies what it prints, including any crash, into a log,
replaced each time it starts (see `PackagedLog`):

- macOS: `~/Library/Logs/Chonkestrel/log.txt`;
- Windows: `%LOCALAPPDATA%\Chonkestrel\log.txt`;
- Linux: `~/.local/state/Chonkestrel/log.txt`.

On Windows, `Chonkestrel-console.exe` starts the game with a console window
showing the same, for when it fails before it can write the log (the
launcher only says "Failed to launch JVM" when the game stops with an
error).

## Not signed

Neither package is signed, so the first time they're opened:

- **macOS** says the app can't be checked for malware, or is damaged. Open
  it with right-click, **Open**, or run
  `xattr -cr /Applications/Chonkestrel.app`.
- **Windows** SmartScreen says it protected the PC: click **More info**, then
  **Run anyway**.

Signing needs an Apple Developer account (and notarizing the app), and a
Windows code signing certificate.

## The icon

The icon is the Chonkestrel as it looks in the hangar. `make icon` renders it
with the game (`XFTL_ICON`, see `IconDirector`: the ship's drawn on black and
on white, which gives each pixel's transparency) into
`tools/release/chonkestrel-hangar.png`, then `tools/release/make_icon.py` makes
`tools/release/icon/chonkestrel.png` (1024x1024), `.icns` (macOS), `.ico`
(Windows), and the window icon, `src/main/resources/assets/img/xftl_icon.png`.
Run it again if the ship changes.
