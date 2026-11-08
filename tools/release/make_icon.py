#!/usr/bin/env python3
"""
Make the game's icons from a render of the Chonkestrel as it looks in the
hangar (made by `make icon`, with the game's XFTL_ICON mode): the ship,
centred on a transparent square.

Usage: python3 tools/release/make_icon.py SHIP.png

It writes, in tools/release/icon/:
- chonkestrel.png: 1024x1024;
- chonkestrel.icns: for the macOS app (needs macOS's iconutil);
- chonkestrel.ico: for Windows;
and the window icon, src/main/resources/assets/img/xftl_icon.png (64x64).
"""

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "tools" / "release" / "icon"
WINDOW_ICON = ROOT / "src/main/resources/assets/img/xftl_icon.png"

# The empty space around the ship, as a fraction of the icon's size
MARGIN = 0.03

# The sizes macOS's iconset wants: (points, scale)
ICONSET = [(16, 1), (16, 2), (32, 1), (32, 2), (128, 1), (128, 2), (256, 1), (256, 2), (512, 1), (512, 2)]
ICO_SIZES = [16, 24, 32, 48, 64, 128, 256]


def square(ship, size):
    """The ship, scaled to fit a square of the given size, keeping its shape."""
    room = round(size * (1 - 2 * MARGIN))
    scale = room / max(ship.size)
    resized = ship.resize((max(1, round(ship.width * scale)), max(1, round(ship.height * scale))), Image.LANCZOS)
    icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    icon.alpha_composite(resized, ((size - resized.width) // 2, (size - resized.height) // 2))
    return icon


def main():
    ship = Image.open(sys.argv[1]).convert("RGBA")
    ship = ship.crop(ship.getbbox())
    OUT.mkdir(parents=True, exist_ok=True)

    square(ship, 1024).save(OUT / "chonkestrel.png")
    square(ship, 64).save(WINDOW_ICON)

    # Each size is scaled from the ship itself, so the small ones stay sharp
    square(ship, 256).save(OUT / "chonkestrel.ico", sizes=[(s, s) for s in ICO_SIZES],
                           append_images=[square(ship, s) for s in ICO_SIZES])

    if shutil.which("iconutil"):
        with tempfile.TemporaryDirectory() as tmp:
            iconset = Path(tmp) / "chonkestrel.iconset"
            iconset.mkdir()
            for points, scale in ICONSET:
                name = f"icon_{points}x{points}{'@2x' if scale == 2 else ''}.png"
                square(ship, points * scale).save(iconset / name)
            subprocess.run(["iconutil", "-c", "icns", str(iconset), "-o", str(OUT / "chonkestrel.icns")], check=True)
    else:
        print("No iconutil (it's only on macOS), so the .icns wasn't made")

    print(f"Made the icons in {OUT}, and the window icon")


if __name__ == "__main__":
    main()
