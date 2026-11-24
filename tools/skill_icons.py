#!/usr/bin/env python3
"""
Make the icons of XFTL's skills (see doc/game/skills.md), which vanilla
hasn't: the Drones skill's and the Life Support skill's.

The Drones skill's (Skill.DRONES) is vanilla's drones system icon from the
top bar, img/ui_icons/icon_drones.png (a white drone, 14x16, in a 32x32
image), put in a 26x26 image as vanilla's
skill icons are (img/people/skill_*_white.png): pure white, its edges
either solid or clear, where the hacking skill's icon (as big) is, so it
lines up with the rest in the skills panel.

The Life Support skill's (Skill.LIFE_SUPPORT, manning the mind control and
oxygen systems) is drawn here: a medical monitor's screen with a heartbeat
across it, on its stand, as wide and tall as the rest.

    python3 tools/skill_icons.py ftl.dat                # make them
    python3 tools/skill_icons.py ftl.dat --preview out/ # and a preview there

The preview is the two among vanilla's skill icons, on the skills panel's
dark blue, eight times as big.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

from ship_variants import MOD, Dat

SOURCE = "img/ui_icons/icon_drones.png"
OUT = MOD / "img" / "people" / "skill_drones_white.png"
LIFE_OUT = MOD / "img" / "people" / "skill_life_white.png"

# The Life Support icon: its screen (a frame two pixels thick), the pulse across it
# (two pixels tall), and its stand's neck and foot
LIFE_SCREEN = (4, 4, 21, 17)
LIFE_PULSE = [(7, 10), (9, 10), (11, 7), (14, 13), (16, 10), (18, 10)]
LIFE_NECK = (11, 18, 14, 19)
LIFE_FOOT = (8, 20, 17, 21)

SIZE = 26  # Vanilla's skill icons' size

# Where the drone's top left corner goes: as the hacking icon's, which is
# the same size
POSITION = (6, 6)

PREVIEW_SCALE = 8
PREVIEW_BACKGROUND = (32, 40, 56, 255)
VANILLA = ["pilot", "engines", "shields", "weapons", "repair", "combat"]


def icon(dat):
    source = dat.image(SOURCE)
    drone = source.crop(source.getbbox())
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y in range(drone.height):
        for x in range(drone.width):
            if drone.getpixel((x, y))[3] >= 128:
                out.putpixel((POSITION[0] + x, POSITION[1] + y), (255, 255, 255, 255))
    return out


def life_icon():
    mask = Image.new("1", (SIZE, SIZE), 0)
    draw = ImageDraw.Draw(mask)
    x0, y0, x1, y1 = LIFE_SCREEN
    draw.rectangle((x0, y0, x1, y1), fill=1)
    draw.rectangle((x0 + 2, y0 + 2, x1 - 2, y1 - 2), fill=0)
    draw.line(LIFE_PULSE, fill=1)
    draw.line([(x, y + 1) for x, y in LIFE_PULSE], fill=1)
    draw.rectangle(LIFE_NECK, fill=1)
    draw.rectangle(LIFE_FOOT, fill=1)
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    out.paste((255, 255, 255, 255), mask=mask)
    return out


def preview(dat, ours):
    icons = [dat.image("img/people/skill_%s_white.png" % name) for name in VANILLA] + ours
    gap = 4
    sheet = Image.new("RGBA", (len(icons) * (SIZE + gap) + gap, SIZE + 2 * gap), PREVIEW_BACKGROUND)
    for i, image in enumerate(icons):
        sheet.alpha_composite(image, (gap + i * (SIZE + gap), gap))
    return sheet.resize((sheet.width * PREVIEW_SCALE, sheet.height * PREVIEW_SCALE), Image.NEAREST)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    dat = Dat(sys.argv[1])
    image = icon(dat)
    life = life_icon()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)
    life.save(LIFE_OUT)
    print("Made the Drones and Life Support skills' icons")
    if len(sys.argv) >= 4 and sys.argv[2] == "--preview":
        out = Path(sys.argv[3])
        out.mkdir(parents=True, exist_ok=True)
        preview(dat, [image, life]).save(out / "skill_icons.png")


if __name__ == "__main__":
    main()
