Chonkestrel (XFTL) and the software that comes with it
=======================================================

The game is XFTL, by Campbell Suter and its contributors, with the
Chonkestrel's changes and mod, and is free software under the GNU General
Public License, version 2: see LICENCE.txt. Its source code is at
https://github.com/ai-pavel/chonkestrel (and the original XFTL's at
https://gitlab.com/znixian/xftl).

FTL: Faster Than Light, its art, music and data belong to Subset Games.
None of it comes with the game, which runs on the ftl.dat of your own copy.

The game includes these, under their own licences, whose texts are in
this folder:

  Slipstream Mod Manager (part of it), by Vhati      GPL v2: LICENCE.txt
  JOrbis, by ymnk, JCraft, Inc. (modified)           LGPL v2: LGPL-2.0.txt
  Slick2D, by Kevin Glass (parts of it modified)      BSD: Slick2D.txt
  PNGDecoder, by Matthias Mann (modified)             BSD: PNGDecoder.txt
  LWJGL 3                                             BSD: LWJGL.txt
    with GLFW                                         zlib: GLFW.txt
    with OpenAL Soft                                  LGPL v2: OpenAL-Soft.txt
    with stb, by Sean Barrett                         MIT / public domain: stb.txt
    with tinyfiledialogs                              zlib: tinyfiledialogs.txt
    with libffi                                       MIT: libffi.txt
    with liburing (Linux)                             MIT: liburing.txt
    with the Khronos OpenGL headers                   Khronos.txt
  Kotlin standard library, by JetBrains               Apache 2.0: Apache-2.0.txt
  JetBrains annotations                               Apache 2.0: Apache-2.0.txt
  JDOM 2, by Jason Hunter and Brett McLaughlin        JDOM.txt
  Saxon-HE, by Saxonica                               MPL 2.0: MPL-2.0.txt
  XML Resolver, by Norman Walsh                       Apache 2.0: Apache-2.0.txt
  Jackson, by FasterXML                               Apache 2.0: Apache-2.0.txt
  Apache HttpComponents and Commons Codec             Apache 2.0: Apache-2.0.txt
  SLF4J, by QOS.ch                                    MIT: SLF4J.txt
  XML Commons External (xml-apis), by Apache          Apache 2.0: Apache-2.0.txt,
                                                      W3C-DOM.txt, SAX.txt
  Roboto font, by Google                              Apache 2.0: Apache-2.0.txt

OpenAL Soft is a library of its own in the game (OpenAL.dll, libopenal.dylib
or libopenal.so, inside its jar), which can be replaced with another build of
it; its source code is at https://github.com/kcat/openal-soft.

The Java runtime that comes with the game is Eclipse Temurin's build of
OpenJDK 17 (or Microsoft's, for ARM64 Windows), under the GNU GPL v2 with
the Classpath Exception: its licences are in the runtime's legal folder.
