#!/usr/bin/env python3
"""
Make the icons of the mod's ship achievements (see doc/game/achievements.md):
one for each achievement in the mod's data/achievements.xml.append, at the
image it names (img/achievements/xftl_<ship>_<n>.png in the mod), 64x64 like
vanilla's (such as img/achievements/kestral_1.png in ftl.dat).

    python3 tools/achievement_art.py ftl.dat                 # make them all
    python3 tools/achievement_art.py ftl.dat --preview out/  # and a sheet of them all there

Each is vanilla's flat grey square with the ship's hangar miniature in it
(img/customizeUI/miniship_<img>.png in the mod, made by chonkestrel_art.py
and race_ships.py, so this runs after them), its colours toned down a little
(and a dark one brightened), marked as vanilla marks its ships' three, with
vanilla's star (from kestral_2.png) by the achievement's number among its
ship's six:

- 1, the Blockade: the ship alone, as vanilla's first;
- 2, the win: the ship under the star, as vanilla's second;
- 3, the win on Hard: and red rays behind it, as vanilla's third;
- 4 to 6, the race's own three: one, two and three gold stars, the last
  (its hardest) on red rays too.

The game dims a locked achievement itself (with the padlock), so there's no
locked copy.

For the achievements window (see game/AchievementsWindow), which shows them
smaller, it also makes each icon at its size there, SMALL_SIZE
(<image>_48.png, scaled down smoothly rather than by the game, which skips
rows), and each ship's picture there, half its miniature's size
(img/customizeUI/xftl_ach_ship_<img>.png, made from its hull as the
miniature is, so it's as crisp). ftl.dat's used for vanilla's icons, for their background and
the star. It needs Pillow (pip3 install pillow).
"""
import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance

from chonkestrel_art import MINIATURE_SIZE, miniature
from ship_variants import MOD, Dat

ACHIEVEMENTS = MOD / "data" / "achievements.xml.append"
BLUEPRINT_FILES = [MOD / "data" / "blueprints.xml.append", MOD / "data" / "autoBlueprints.xml.append"]

SIZE = 64
# The achievements window's icons, and its ships' pictures (half the
# miniatures' size)
SMALL_SIZE = 48
SHIP_PICTURE_SIZE = (round(MINIATURE_SIZE[0] / 2), round(MINIATURE_SIZE[1] / 2))
SHIP_PICTURE_OUT = MOD / "img" / "customizeUI" / "xftl_ach_ship_%s.png"
# Vanilla's background (see kestral_1.png)
VANILLA_ICON = "img/achievements/kestral_1.png"
# Where the ship goes: as big as fits in this box, alone, or under the
# stars (as vanilla's second and third have it lower)
SHIP_BOX = (2, 6, 62, 62)
SHIP_BOX_STARRED = (2, 22, 62, 62)
# How much colour the ship keeps (1 is all of it), as vanilla's are greyer
SHIP_COLOUR = 0.6
# A ship darker than this on average (0 to 255) is brightened, its blacks
# lifted by as much as it's darker, but at most MAX_LIFT, and given a pale
# edge
MIN_BRIGHTNESS = 100
MAX_LIFT = 35
# The edge around a dark ship
DARK_SHIP_EDGE = (200, 205, 195, 255)

# The red rays behind the win on Hard's star, as vanilla's third
# achievements have (see kestral_3.png)
RAYS = (225, 90, 86, 255)
# Vanilla's star, from its second ship achievements (kestral_2.png): where
# it is there, which is where it goes in ours (the top middle)
STAR_ICON = "img/achievements/kestral_2.png"
STAR_BOX = (19, 0, 46, 22)
# The race goals' stars: gold, one, two or three of them across the top,
# their middles this far apart
GOLD = (255, 214, 80)
STAR_SPACING = 20


def ship_images():
    """Each player ship's blueprint name, and its img (for its miniature)."""
    ships = {}
    for path in BLUEPRINT_FILES:
        for name, img in re.findall(r'<shipBlueprint\s+name="([^"]+)"[^>]*\simg="([^"]+)"', path.read_text()):
            ships[name] = img
    return ships


def achievements():
    """The achievements: each one's image, ship, and number (1 to 6) among its ship's."""
    text = ACHIEVEMENTS.read_text()
    found = []
    for block in re.findall(r"<achievement\b.*?</achievement>", text, re.S):
        img = re.search(r"<img>(.*?)</img>", block).group(1)
        ship = re.search(r"<ship>(.*?)</ship>", block).group(1)
        number = int(re.search(r"_(\d+)\.png$", img).group(1))
        found.append((img, ship, number))
    return found


def background(dat, rays):
    """Vanilla's grey square, with red rays from the star for the win on Hard."""
    base = dat.image(VANILLA_ICON).convert("RGBA").getpixel((0, 0))
    icon = Image.new("RGBA", (SIZE, SIZE), base)
    if rays:
        draw = ImageDraw.Draw(icon)
        x0, y0, x1, y1 = STAR_BOX
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
        for i in range(12):
            a0 = math.radians(i * 30)
            a1 = math.radians(i * 30 + 15)
            r = SIZE * 2
            draw.polygon([(cx, cy), (cx + r * math.cos(a0), cy + r * math.sin(a0)),
                          (cx + r * math.cos(a1), cy + r * math.sin(a1))], fill=RAYS)
    return icon


def star(dat, gold):
    """Vanilla's star, cut from its grey: white, or gold for a race goal."""
    vanilla = dat.image(STAR_ICON).convert("RGBA").crop(STAR_BOX)
    grey = dat.image(VANILLA_ICON).convert("RGBA").getpixel((0, 0))
    out = Image.new("RGBA", vanilla.size, (0, 0, 0, 0))
    for y in range(vanilla.height):
        for x in range(vanilla.width):
            r, g, b, _ = vanilla.getpixel((x, y))
            if abs(r - grey[0]) + abs(g - grey[1]) + abs(b - grey[2]) <= 6:
                continue
            if gold:
                # (its whites gold, its dark middle as dark)
                level = (r + g + b) / 765
                r, g, b = (round(c * level) for c in GOLD)
            out.putpixel((x, y), (r, g, b, 255))
    return out


def ship(img, box):
    """The ship's hangar miniature, cut to its hull, toned down and scaled to fit [box]."""
    mini = Image.open(MOD / "img" / "customizeUI" / ("miniship_%s.png" % img)).convert("RGBA")
    # (without the glow round it, which is for the hangar's dark list)
    mini.putalpha(mini.getchannel("A").point(lambda a: 255 if a >= 128 else 0))
    mini = mini.crop(mini.getchannel("A").getbbox())
    alpha = mini.getchannel("A")
    rgb = ImageEnhance.Color(mini.convert("RGB")).enhance(SHIP_COLOUR)
    # A dark hull (such as the Chonkivan's) is brightened, to stand out
    # from the background
    grey = rgb.convert("L")
    seen = [g for g, a in zip(grey.getdata(), alpha.getdata()) if a > 128]
    mean = sum(seen) / max(1, len(seen))
    if mean < MIN_BRIGHTNESS:
        # (its blacks lifted, as black can't be brightened)
        lift = min(MAX_LIFT, MIN_BRIGHTNESS - mean)
        rgb = rgb.point(lambda v: int(lift + v * (255 - lift) / 255))
    mini = rgb.convert("RGBA")
    mini.putalpha(alpha)
    x0, y0, x1, y1 = box
    scale = min((x1 - x0) / mini.width, (y1 - y0) / mini.height)
    mini = mini.resize((max(1, round(mini.width * scale)), max(1, round(mini.height * scale))), Image.LANCZOS)
    if mean >= MIN_BRIGHTNESS:
        return mini
    # A dark one gets a pale edge, to stand out from the background
    edged = Image.new("RGBA", (mini.width + 2, mini.height + 2), (0, 0, 0, 0))
    solid = mini.getchannel("A").point(lambda a: 255 if a > 96 else 0)
    edge = Image.new("RGBA", mini.size, DARK_SHIP_EDGE)
    for dx, dy in ((0, 1), (2, 1), (1, 0), (1, 2)):
        edged.paste(edge, (dx, dy), solid)
    edged.alpha_composite(mini, (1, 1))
    return edged


def make(dat, img, number):
    stars = {1: 0, 2: 1, 3: 1}.get(number, number - 3)
    # (red rays behind the win on Hard's star, and the race's last three
    # gold stars, its hardest goal)
    icon = background(dat, number in (3, 6))
    box = SHIP_BOX_STARRED if stars else SHIP_BOX
    hull = ship(img, box)
    x0, y0, x1, y1 = box
    icon.alpha_composite(hull, ((x0 + x1 - hull.width) // 2, (y0 + y1 - hull.height) // 2))
    if stars:
        mark = star(dat, number > 3)
        middle = (STAR_BOX[0] + STAR_BOX[2]) // 2
        for i in range(stars):
            cx = middle + round((i - (stars - 1) / 2) * STAR_SPACING)
            icon.alpha_composite(mark, (cx - mark.width // 2, STAR_BOX[1]))
    return icon


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    dat = Dat(sys.argv[1])
    images = ship_images()
    made = []
    for path, ship_name, number in achievements():
        icon = make(dat, images[ship_name], number)
        out = MOD / "img" / path
        out.parent.mkdir(parents=True, exist_ok=True)
        icon.save(out)
        icon.resize((SMALL_SIZE, SMALL_SIZE), Image.LANCZOS).save(out.with_name(out.stem + "_%d.png" % SMALL_SIZE))
        made.append(icon)
    print("Made %d achievement icons" % len(made))

    # The ships' pictures for the achievements window
    ships = sorted({images[ship_name] for _, ship_name, _ in achievements()})
    for img in ships:
        hull = MOD / "img" / "ship" / ("%s_base.png" % img)
        miniature(Image.open(hull).convert("RGBA"), SHIP_PICTURE_SIZE).save(str(SHIP_PICTURE_OUT) % img)
    print("Made the achievements window's pictures of %d ships" % len(ships))

    if len(sys.argv) >= 4 and sys.argv[2] == "--preview":
        # All of them, six to a row (a ship's), at twice their size
        out = Path(sys.argv[3])
        out.mkdir(parents=True, exist_ok=True)
        rows = (len(made) + 5) // 6
        sheet = Image.new("RGBA", (6 * (SIZE + 4), rows * (SIZE + 4)), (20, 20, 20, 255))
        for i, icon in enumerate(made):
            sheet.paste(icon, ((i % 6) * (SIZE + 4), (i // 6) * (SIZE + 4)))
        sheet = sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST)
        sheet.save(out / "achievements.png")


if __name__ == "__main__":
    main()
