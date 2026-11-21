#!/usr/bin/env python3
"""
Make the hangar's background (see hangar/SelectShipState) from vanilla's,
img/customizeUI/custom_background.png in ftl.dat, which is 1280x720: made
1920x1080, the game's window, by stretching the middle of its landing pad,
so the mod's bigger ships fit on it.

    python3 tools/hangar_art.py ftl.dat                # make it
    python3 tools/hangar_art.py ftl.dat --preview out/ # and save a copy there

It's stretched as the ships' hulls are (see ship_variants.insert): COLUMNS
copies of the pad's column COLUMN are put in at it, and ROWS copies of its
row ROW. Those lines cross the pad's plain floor and the corridors, where
copies of them don't show. The left strip (left of LEFT_STRIP), where the
docked ships are, gets its rows at LEFT_ROW instead, between two of them,
so they aren't stretched.
"""
import sys
from pathlib import Path

from PIL import Image

from ship_variants import MOD, Dat, insert

SOURCE = "img/customizeUI/custom_background.png"
OUT = MOD / "img" / "customizeUI" / "xftl_hangar_background.png"

# Where it's stretched, and by how much, to 1920x1080
COLUMN = 923
COLUMNS = 640
ROW = 249
LEFT_ROW = 175
ROWS = 360
LEFT_STRIP = 262


def background(dat):
    vanilla = dat.image(SOURCE).convert("RGBA")
    wide = insert(vanilla, "x", COLUMN, COLUMNS, COLUMN - 1)
    w, h = wide.size
    left = insert(wide.crop((0, 0, LEFT_STRIP, h)), "y", LEFT_ROW, ROWS, LEFT_ROW - 1)
    rest = insert(wide.crop((LEFT_STRIP, 0, w, h)), "y", ROW, ROWS, ROW - 1)
    out = Image.new("RGBA", (w, h + ROWS))
    out.paste(left, (0, 0))
    out.paste(rest, (LEFT_STRIP, 0))
    return out


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    dat = Dat(sys.argv[1])
    image = background(dat)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)
    print("Made the hangar's background, %dx%d" % image.size)
    if len(sys.argv) >= 4 and sys.argv[2] == "--preview":
        out = Path(sys.argv[3])
        out.mkdir(parents=True, exist_ok=True)
        image.save(out / "hangar_background.png")


if __name__ == "__main__":
    main()
