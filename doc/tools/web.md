# The browser version

The game also runs in a browser, compiled to JavaScript by
[TeaVM](https://teavm.org) (0.17), from the same JVM bytecode as the
desktop version. It's at https://chonkestrel.gitlab.io/play/, and

```sh
make web    # Builds it (./gradlew :web:site) and serves it on http://localhost:8000
```

The page asks for the player's `ftl.dat`, which it reads in the browser
(it's never uploaded), loads the Chonkestrel mod, and starts the game full
screen. It needs WebGL 2, and about 0.4 GB of memory.

## How it works

`web/` is a Gradle module with the TeaVM plugin. It compiles against a
copy of the game's jar (and JDOM's) without the classes it has its own
versions of (`replacedClasses` in `web/build.gradle.kts`), so the game's
own code is compiled as it is:

- **Graphics:** `org.lwjgl.opengl.GL11` and `GL30`, the OpenGL functions
  the game uses, on WebGL 2 (`WebGL` keeps OpenGL's numbered objects).
  The shaders' `#version 330 core` becomes `#version 300 es`, so they
  have to keep to what GLSL ES allows (no comparing a float with an int).
- **Sound:** `org.lwjgl.openal.*` on WebAudio (`WebAudio`): buffers are
  `AudioBuffer`s, and sources queues of them, each played by a node
  scheduled for when the one before ends; a source's state is worked out
  from the audio clock when the game asks.
- **The window and input:** `WebGameContainer` (a 1920x1080 canvas,
  scaled to fit) and `WebInput` (the page's keys, numbered as GLFW's).
- **Files:** TeaVM's in-memory file system. `WebMain` writes `ftl.dat`
  into it, and unzips the mod (`chonkestrel.zip`, made by the build) into
  `/saves/mods`. `WebSaves` keeps `/saves` in IndexedDB.
- **Replaced classes:** `PlatformSpecific` (the saves in `/saves`),
  `Cursor` (the browser's own), and JDOM's `SAXBuilder` (Slipstream's
  parser, as JDOM's needs the JDK's).
- **`DesktopOnlyStubs`:** a TeaVM compiler plugin that empties the
  methods the browser version never runs and that use parts of Java TeaVM
  hasn't got: the mods' XML cache and XSLT patches, zipped mods, the
  `ftl.dat` picker, the debug console's reflection, WAV sounds and so on.
  A method added to the game that uses one of them (TeaVM says which when
  it fails to compile) needs adding there, or doing another way.
- **`GameResources`:** the game's resources TeaVM embeds, for
  `getResourceAsStream` (`ClassLoader.getResource`, a URL, doesn't work).

TeaVM's class library has a few differences from the JDK the game's had
to allow for: no reflection on fields, regexes that recurse for each
character a group with a lookahead matches (Slipstream's parser), an
inflater that won't inflate before it's given its input (`PNGDecoder`),
and `Files.createDirectories` throwing for a directory that's there
(`Utils.createDirectories`).

## Publishing it

The site's `build.py` copies `web/build/site` to `/play`, without its
source maps and the sources they point to, if it's been built: run
`./gradlew :web:site` before deploying the site.
