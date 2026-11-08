#!/usr/bin/env python3
"""
Package the game for players, with its own Java, so they don't need to
install anything:

- macOS: a .dmg with Chonkestrel.app in it, for this Mac's architecture;
- Windows: a .zip with a Chonkestrel folder, holding Chonkestrel.exe, for
  x64 PCs (and, if asked for, another for ARM64 ones, such as Windows in
  Parallels on an Apple Silicon Mac, where the x64 one is slow, as Java's
  emulated);
- Linux: a .tar.gz with a Chonkestrel folder, holding a Chonkestrel script
  that starts the game, for x64 PCs.

Usage: python3 tools/release/package.py VERSION [mac|windows|linux|windows-arm64 ...]

Without a target, it makes the Mac, Windows (x64) and Linux packages.

VERSION is the version players see, such as 0.1-alpha: in the packages'
names, and the game's log. The OSes get a build number made from the
date, such as 2026.10.21, as macOS wants three numbers, the first not 0.

Build the game's jar first (`./gradlew fatJar`, which `make release`
does). The packages go in dist/release/, named like
Chonkestrel-0.1.0-macos-arm64.dmg.

The Mac app is made with the JDK's jlink (a Java runtime with only the
modules the game uses) and jpackage, so it has to be made on a Mac. The
Windows and Linux ones can be made anywhere: their runtime is made by jlink
from a Windows or Linux JDK of the same version (downloaded into
dist/release/cache). On Windows, the executable is the launcher Windows'
jpackage uses, from that JDK, and on Linux, it's a shell script. See
doc/tools/release.md.

None are signed, so macOS and Windows warn about them the first time
they're opened.
"""

import datetime
import json
import platform
import shutil
import subprocess
import sys
import tarfile
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "dist" / "release"
CACHE = OUT / "cache"
JAR = ROOT / "build" / "libs" / "XFTL-complete.jar"
MOD = ROOT / "mods" / "chonkestrel"
ICONS = ROOT / "tools" / "release" / "icon"
# The game's licence, and those of what comes with it (see its README.txt)
LICENCE = ROOT / "LICENCE.txt"
LICENCES = ROOT / "tools" / "release" / "licences"

NAME = "Chonkestrel"
MAIN_CLASS = "xyz.znix.xftl.App"
MAC_IDENTIFIER = "io.gitlab.chonkestrel"

# The Java modules the game uses: XML for the game's data, the desktop
# module for images, Unsafe for LWJGL, and a few the libraries use
MODULES = ["java.base", "java.desktop", "java.xml", "java.logging", "java.sql", "jdk.unsupported"]

# What the packaged game is told when it starts (see PackagedGame).
# $APPDIR is the app's directory, which the launcher fills in. It uses every
# crew name preset at once (see doc/game/names.md), which XFTL_NAMES can still
# override.
GAME_OPTIONS = ["-Dxftl.packaged=true", "-Dxftl.bundled-mods=$APPDIR/mods", "-Dxftl.names=all"]


def run(*args, **kwargs):
    print("+", " ".join(str(a) for a in args))
    subprocess.run([str(a) for a in args], check=True, **kwargs)


def build_number():
    """Today's date, such as 2026.10.21: the build number the OSes see."""
    today = datetime.date.today()
    return f"{today.year}.{today.month}.{today.day}"


def game_options(version):
    """What the packaged game is told when it starts, with its version."""
    return GAME_OPTIONS + [f"-Dxftl.version={version}"]


def java_home():
    """The JDK to package with: JAVA_HOME, or Java 17 on a Mac."""
    import os
    home = os.environ.get("JAVA_HOME")
    if not home and platform.system() == "Darwin":
        home = subprocess.run(["/usr/libexec/java_home", "-v", "17"], capture_output=True, text=True).stdout.strip()
    if not home:
        sys.exit("Set JAVA_HOME to a JDK 17")
    return Path(home)


def java_version(jdk):
    """The JDK's full version, such as 17.0.18+8."""
    release = (jdk / "release").read_text()
    for line in release.splitlines():
        if line.startswith("JAVA_RUNTIME_VERSION="):
            # Such as 17.0.18+8 (Temurin may add -LTS)
            return line.split("=", 1)[1].strip('"').removesuffix("-LTS")
    sys.exit(f"Can't find the Java version in {jdk / 'release'}")


def stage_input():
    """The app's files: the game's jar, its mods, turned on, and the licences."""
    if not JAR.is_file():
        sys.exit(f"Build the game first: {JAR} is missing (./gradlew fatJar)")
    staging = OUT / "input"
    shutil.rmtree(staging, ignore_errors=True)
    staging.mkdir(parents=True)
    shutil.copy2(JAR, staging / JAR.name)
    mods = staging / "mods"
    shutil.copytree(MOD, mods / MOD.name)
    (mods / "order.txt").write_text(MOD.name + "\n")
    shutil.copytree(LICENCES, staging / "licences")
    shutil.copy2(LICENCE, staging / "licences" / LICENCE.name)
    return staging


def jlink(jdk, module_path, output):
    shutil.rmtree(output, ignore_errors=True)
    run(jdk / "bin" / "jlink",
        "--module-path", module_path,
        "--add-modules", ",".join(MODULES),
        "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=2",
        "--output", output)


def package_mac(jdk, version, staging):
    if platform.system() != "Darwin":
        sys.exit("The Mac app can only be made on a Mac")
    arch = "arm64" if platform.machine() == "arm64" else "x64"
    runtime = OUT / "runtime-mac"
    jlink(jdk, jdk / "jmods", runtime)

    dest = OUT / "mac"
    shutil.rmtree(dest, ignore_errors=True)
    options = ["-XstartOnFirstThread"] + game_options(version)
    run(jdk / "bin" / "jpackage",
        "--type", "dmg",
        "--name", NAME,
        "--app-version", build_number(),
        "--input", staging,
        "--main-jar", JAR.name,
        "--main-class", MAIN_CLASS,
        "--runtime-image", runtime,
        "--icon", ICONS / "chonkestrel.icns",
        "--java-options", " ".join(options),
        "--mac-package-identifier", MAC_IDENTIFIER,
        "--mac-package-name", NAME,
        "--dest", dest)

    made = dest / f"{NAME}-{build_number()}.dmg"
    result = OUT / f"{NAME}-{version}-macos-{arch}.dmg"
    result.unlink(missing_ok=True)
    made.rename(result)
    shutil.rmtree(dest)
    return result


def jdk_url(version, os_name, arch):
    """
    Where to download a Windows or Linux JDK of the version: Temurin's, but
    Microsoft's build for ARM64 Windows, as there's no Temurin 17 for it.
    """
    if os_name == "windows" and arch == "arm64":
        # Microsoft's are named by the version without the build, such as 17.0.18
        return f"https://aka.ms/download-jdk/microsoft-jdk-{version.split('+')[0]}-windows-aarch64.zip"
    release = urllib.parse.quote(f"jdk-{version}")
    return (f"https://api.adoptium.net/v3/binary/version/{release}/{os_name}/{arch}/jdk/hotspot/normal/eclipse"
            "?project=jdk")


def other_jdk(version, os_name, arch):
    """
    A Windows or Linux JDK of the same version, downloaded once: a zip for
    Windows, and a .tar.gz for Linux.
    """
    jdk_dir = CACHE / f"jdk-{version}-{os_name}-{arch}"
    if (jdk_dir / "jmods").is_dir():
        return jdk_dir

    CACHE.mkdir(parents=True, exist_ok=True)
    extension = "zip" if os_name == "windows" else "tar.gz"
    archive = CACHE / f"jdk-{version}-{os_name}-{arch}.{extension}"
    if not archive.is_file():
        url = jdk_url(version, os_name, arch)
        print(f"Downloading the {os_name} {arch} JDK {version}")
        partial = archive.with_name(archive.name + ".part")
        # Adoptium turns away Python's default user agent
        request = urllib.request.Request(url, headers={"User-Agent": "chonkestrel-release-packager"})
        with urllib.request.urlopen(request) as response, open(partial, "wb") as file:
            shutil.copyfileobj(response, file)
        partial.rename(archive)

    unpacked = CACHE / "unpacking"
    shutil.rmtree(unpacked, ignore_errors=True)
    if extension == "zip":
        with zipfile.ZipFile(archive) as zip_file:
            zip_file.extractall(unpacked)
    else:
        with tarfile.open(archive) as tar_file:
            tar_file.extractall(unpacked, filter="tar")
    # The archive has one directory, such as jdk-17.0.18+8
    (top,) = list(unpacked.iterdir())
    shutil.rmtree(jdk_dir, ignore_errors=True)
    top.rename(jdk_dir)
    shutil.rmtree(unpacked)
    return jdk_dir


def windows_launcher(win_jdk, console):
    """
    The executable Windows' jpackage uses for apps, from its JDK: with a
    console window (for seeing what goes wrong), or without. It's in the
    jdk.jpackage module, a JMOD file, which is a zip with a short header
    (Python's zipfile skips it).
    """
    wanted = "jpackageapplauncher.exe" if console else "jpackageapplauncherw.exe"
    with zipfile.ZipFile(win_jdk / "jmods" / "jdk.jpackage.jmod") as jmod:
        for name in jmod.namelist():
            if name.endswith("/" + wanted):
                return jmod.read(name)
    sys.exit(f"Can't find {wanted} in the Windows JDK's jdk.jpackage module")


def package_windows(jdk, version, staging, arch):
    win_jdk = other_jdk(java_version(jdk), "windows", arch)

    # The same layout jpackage makes on Windows: the executable, with its
    # settings in app/<name>.cfg, the game in app/, and Java in runtime/
    image = OUT / "windows" / NAME
    shutil.rmtree(image.parent, ignore_errors=True)
    shutil.copytree(staging, image / "app")
    jlink(jdk, win_jdk / "jmods", image / "runtime")
    shutil.copy2(ICONS / "chonkestrel.ico", image / f"{NAME}.ico")

    # The game, and the same with a console window, which shows what it
    # prints, for when it won't start. Each launcher reads the settings
    # file named after it.
    launchers = {NAME: False, f"{NAME}-console": True}
    for launcher, console in launchers.items():
        (image / f"{launcher}.exe").write_bytes(windows_launcher(win_jdk, console))

    options = [f"-Djpackage.app-version={build_number()}"] + [o.replace("/", "\\") for o in game_options(version)]
    config = "\n".join([
        "[Application]",
        f"app.classpath=$APPDIR\\{JAR.name}",
        f"app.mainclass={MAIN_CLASS}",
        "",
        "[JavaOptions]",
        *[f"java-options={option}" for option in options],
        "",
    ])
    for launcher in launchers:
        (image / "app" / f"{launcher}.cfg").write_text(config, newline="\r\n")

    result = OUT / f"{NAME}-{version}-windows-{arch}.zip"
    result.unlink(missing_ok=True)
    with zipfile.ZipFile(result, "w", zipfile.ZIP_DEFLATED) as zip_file:
        for path in sorted(image.rglob("*")):
            zip_file.write(path, path.relative_to(image.parent))
    shutil.rmtree(image.parent)
    return result


def package_linux(jdk, version, staging):
    linux_jdk = other_jdk(java_version(jdk), "linux", "x64")

    # Laid out like the Windows one: the script that starts the game, the
    # game in app/, and Java in runtime/
    image = OUT / "linux" / NAME
    shutil.rmtree(image.parent, ignore_errors=True)
    shutil.copytree(staging, image / "app")
    jlink(jdk, linux_jdk / "jmods", image / "runtime")
    shutil.copy2(ICONS / "chonkestrel.png", image / f"{NAME}.png")

    options = [f"-Djpackage.app-version={build_number()}"] + game_options(version)
    options = " ".join(f'"{o}"' for o in options)
    script = image / NAME
    script.write_text("\n".join([
        "#!/bin/sh",
        f"# Starts {NAME}, with the Java that comes with it",
        'DIR="$(dirname "$(readlink -f "$0")")"',
        'APPDIR="$DIR/app"',
        f'exec "$DIR/runtime/bin/java" {options} -cp "$APPDIR/{JAR.name}" {MAIN_CLASS} "$@"',
        "",
    ]))

    script.chmod(0o755)

    # The files keep their permissions (the script's and Java's executables),
    # but not who owns them here
    def anyone(info):
        info.uid = info.gid = 0
        info.uname = info.gname = ""
        return info

    result = OUT / f"{NAME}-{version}-linux-x64.tar.gz"
    result.unlink(missing_ok=True)
    with tarfile.open(result, "w:gz") as tar_file:
        tar_file.add(image, arcname=NAME, filter=anyone)
    shutil.rmtree(image.parent)
    return result


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    version = sys.argv[1]
    targets = sys.argv[2:] or ["mac", "windows", "linux"]

    jdk = java_home()
    OUT.mkdir(parents=True, exist_ok=True)
    staging = stage_input()

    made = []
    for target in targets:
        if target == "mac":
            made.append(package_mac(jdk, version, staging))
        elif target == "windows":
            made.append(package_windows(jdk, version, staging, "x64"))
        elif target == "windows-arm64":
            made.append(package_windows(jdk, version, staging, "arm64"))
        elif target == "linux":
            made.append(package_linux(jdk, version, staging))
        else:
            sys.exit(f"Unknown target '{target}': it should be mac, windows, linux or windows-arm64")

    print()
    for path in made:
        print(f"Made {path.relative_to(ROOT)} ({path.stat().st_size / 1e6:.0f} MB)")


if __name__ == "__main__":
    main()
