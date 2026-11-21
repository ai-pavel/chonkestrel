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
and race_ships.py, so this runs after them), its colours toned down a little (and a dark one brightened),
with a dark badge in its top right corner, with an emblem for the achievement's goal (its
xftlGoal kind, see EMBLEMS): a wall for the Blockade, a crown for the win,
an H on vanilla's red rays for the win on Hard, an artillery beam for
the Federation's artillery goals, a dotted cloak outline for the Stealth
ships' cloaking goals, and so on. The emblems are
pixel art, drawn at twice their size. The game dims a locked achievement
itself (with the padlock), so there's no locked copy.

ftl.dat's only used for vanilla's own icon, to match its background. It
needs Pillow (pip3 install pillow).
"""
import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance

from ship_variants import MOD, Dat

ACHIEVEMENTS = MOD / "data" / "achievements.xml.append"
BLUEPRINT_FILES = [MOD / "data" / "blueprints.xml.append", MOD / "data" / "autoBlueprints.xml.append"]

SIZE = 64
# Vanilla's background (see kestral_1.png), and how the emblem's badge looks
VANILLA_ICON = "img/achievements/kestral_1.png"
BADGE_BOX = (37, 1, 63, 27)
BADGE_FILL = (28, 30, 32, 235)
BADGE_EDGE = (215, 220, 210, 255)
# Where the ship goes: as big as fits in this box, the badge over its corner
SHIP_BOX = (2, 6, 62, 62)
# How much colour the ship keeps (1 is all of it), as vanilla's are greyer
SHIP_COLOUR = 0.6
# A ship darker than this on average (0 to 255) is brightened, its blacks
# lifted by as much as it's darker, but at most MAX_LIFT, and given a pale
# edge
MIN_BRIGHTNESS = 100
MAX_LIFT = 35
# The edge around a dark ship
DARK_SHIP_EDGE = (200, 205, 195, 255)

WHITE = (250, 250, 245, 255)
GOLD = (255, 214, 80, 255)
RED = (235, 88, 84, 255)
ORANGE = (255, 150, 50, 255)
BLUE = (110, 190, 255, 255)
GREEN = (120, 240, 120, 255)
CYAN = (120, 245, 235, 255)
PURPLE = (215, 140, 255, 255)

# The emblems, 11x11 (drawn at twice the size): each kind's picture, and
# the colours of its marks ('#' the first, '+' the second)
EMBLEMS = {
    "blockade": ([
        "###########",
        "#...#...#..",
        "###########",
        "..#...#...#",
        "###########",
        "#...#...#..",
        "###########",
        "..#...#...#",
        "###########",
        "...........",
        "...........",
    ], WHITE, WHITE),
    "win": ([
        "...........",
        "+....+....+",
        "##..###..##",
        "###.###.###",
        "###########",
        "###########",
        "###########",
        "###########",
        "...........",
        "###########",
        "...........",
    ], GOLD, RED),
    "winHard": ([
        "##.......##",
        "##.......##",
        "##.......##",
        "##.......##",
        "###########",
        "###########",
        "##.......##",
        "##.......##",
        "##.......##",
        "##.......##",
        "...........",
    ], WHITE, WHITE),
    "fleetFight": ([
        "#..........",
        "##.........",
        "###.....#..",
        "##......##.",
        "#.......###",
        "........##.",
        "#.......#..",
        "##.........",
        "###........",
        "##.........",
        "#..........",
    ], RED, RED),
    "bossHull": ([
        "###########",
        "#.........#",
        "#.........#",
        "#.........#",
        "#.........#",
        "#+++++++++#",
        ".#+++++++#.",
        ".#+++++++#.",
        "..#+++++#..",
        "...#+++#...",
        "....###....",
    ], WHITE, GREEN),
    "crewAtWin": ([
        ".#...#...#.",
        "###.###.###",
        ".#...#...#.",
        "###.###.###",
        "###.###.###",
        "#.#.#.#.#.#",
        "...........",
        "+.+.+.+.+.+",
        "...........",
        "+++++++++++",
        "...........",
    ], WHITE, GOLD),
    "droneKills": ([
        "....#.#....",
        ".....#.....",
        "...#####...",
        "..#.....#..",
        "###.+++.###",
        "###.+++.###",
        "###.+++.###",
        "..#.....#..",
        "...#####...",
        "...........",
        "...........",
    ], WHITE, RED),
    "dronesOut": ([
        "..#.....#..",
        ".###...###.",
        "##+##.##+##",
        ".###...###.",
        "..#.....#..",
        "...........",
        ".....#.....",
        "....###....",
        "...##+##...",
        "....###....",
        ".....#.....",
    ], WHITE, RED),
    "winNoLosses": ([
        "...........",
        ".###...###.",
        "#####.#####",
        "###########",
        "###########",
        ".#########.",
        "..#######..",
        "...#####...",
        "....###....",
        ".....#.....",
        "...........",
    ], RED, RED),
    "shieldAbsorbed": ([
        "....###....",
        "..##...##..",
        ".#.......#.",
        "#....+....#",
        "#...+++...#",
        "#..+++++..#",
        "....+++....",
        "....+++....",
        "....+++....",
        "...........",
        "...........",
    ], BLUE, RED),
    "superShield": ([
        "....###....",
        "..#######..",
        ".####+####.",
        "#####+#####",
        "###+++++###",
        "#####+#####",
        "#####+#####",
        ".#########.",
        "..#######..",
        "....###....",
        "...........",
    ], GREEN, WHITE),
    "bossUnharmed": ([
        "###########",
        "#.........#",
        "#........+#",
        "#.......++#",
        "#+.....++.#",
        "#++...++..#",
        ".#++.++..#.",
        ".#.+++...#.",
        "..#.+...#..",
        "...#...#...",
        "....###....",
    ], WHITE, GREEN),
    "boarderKills": ([
        "..#######..",
        ".#########.",
        "###########",
        "##..###..##",
        "##..###..##",
        "#####.#####",
        ".####.####.",
        "..#######..",
        "..#.#.#.#..",
        "...........",
        "...........",
    ], WHITE, WHITE),
    "crewKills": ([
        "+...###...+",
        ".+.#####.+.",
        "..#######..",
        "..#..#..#..",
        "..#######..",
        "...##.##...",
        "...#####...",
        "..+.....+..",
        ".+.......+.",
        "+.........+",
        "...........",
    ], WHITE, RED),
    "bossCrew": ([
        "+...+.+...+",
        "++.+++++.++",
        "+++++++++++",
        "...........",
        "..#######..",
        ".##..#..##.",
        ".##..#..##.",
        ".#########.",
        "..##.#.##..",
        "..#######..",
        "...........",
    ], WHITE, GOLD),
    "mindControls": ([
        "...........",
        "...#####...",
        "..#.....#..",
        ".#..###..#.",
        "#..#+++#..#",
        "#..#+++#..#",
        "#..#+++#..#",
        ".#..###..#.",
        "..#.....#..",
        "...#####...",
        "...........",
    ], PURPLE, WHITE),
    "hacks": ([
        ".....#.....",
        "....###....",
        "...#####...",
        "..##+++##..",
        ".##+++++##.",
        "###+++++###",
        ".##+++++##.",
        "..##+++##..",
        "...#####...",
        "....###....",
        ".....#.....",
    ], GREEN, WHITE),
    "nebulaSectors": ([
        "...........",
        "....###....",
        "..#######..",
        ".#########.",
        "####+######",
        "###+++#####",
        ".####+#####",
        "..#########",
        "...#######.",
        "...........",
        "...........",
    ], PURPLE, WHITE),
    "missiles": ([
        "...........",
        "...........",
        "+..........",
        "++.#####...",
        "+++#######.",
        "+++########",
        "+++#######.",
        "++.#####...",
        "+..........",
        "...........",
        "...........",
    ], WHITE, RED),
    "enemyFires": ([
        ".....#.....",
        "....##.....",
        "....###..#.",
        "...####.##.",
        "..#######..",
        ".####+####.",
        ".###+++###.",
        "###+++++###",
        "###+++++###",
        ".##+++++##.",
        "..#######..",
    ], ORANGE, GOLD),
    "allWeapons": ([
        "...........",
        "##########.",
        "##########+",
        "...........",
        "##########.",
        "##########+",
        "...........",
        "##########.",
        "##########+",
        "...........",
        "...........",
    ], WHITE, RED),
    "suffocated": ([
        ".....#.....",
        ".....#.....",
        "..##.#.##..",
        ".###.#.###.",
        "####...####",
        "####...####",
        "####...####",
        "####...####",
        ".###...###.",
        "..#.....#..",
        "...........",
    ], BLUE, WHITE),
    "winNoOxygen": ([
        "+.........+",
        ".+###.###+.",
        "..+.#.#.+..",
        ".#.+#.#+...",
        ".#..+.+..#.",
        ".#...+..#..",
        ".#..+.+#...",
        ".#.+#.+....",
        "..+.#.#+##.",
        ".+###.###+.",
        "+.........+",
    ], BLUE, RED),
    "boardedWins": ([
        ".....#.....",
        "....###....",
        "....#+#....",
        "....###....",
        "...#####...",
        "..#.###.#..",
        ".....#.....",
        "....#.#....",
        "+++++++++++",
        "+.+.+.+.+.+",
        "...........",
    ], WHITE, BLUE),
    "cloakMisses": ([
        "...........",
        "...#####...",
        "..#.....#..",
        ".#...+...#.",
        "#...+++...#",
        "#..+++++..#",
        "#...+++...#",
        ".#...+...#.",
        "..#.....#..",
        "...#####...",
        "...........",
    ], CYAN, WHITE),
    "lockdowns": ([
        "...#####...",
        "..#.....#..",
        "..#.....#..",
        ".#########.",
        ".####+####.",
        ".###+++###.",
        ".####+####.",
        ".####+####.",
        ".#########.",
        "...........",
        "...........",
    ], CYAN, WHITE),
    "cloakedKills": ([
        ".........#.",
        "........##.",
        ".......##..",
        "......##...",
        ".....##....",
        "....##.....",
        ".+.##......",
        "..++.......",
        "..++.......",
        ".+..+......",
        "...........",
    ], CYAN, WHITE),
    "artilleryDamage": ([
        "#..........",
        ".#.........",
        "..#........",
        "...#.......",
        "....#......",
        ".....#.....",
        "......#.+..",
        ".......+++.",
        "......+++++",
        ".......+++.",
        "........+..",
    ], GOLD, ORANGE),
    "artilleryKills": ([
        "#..........",
        ".#.........",
        "..#........",
        "...#.......",
        "....#+++...",
        "....+++++..",
        "....+.+.+..",
        "....+++++..",
        ".....+.+...",
        "...........",
        "...........",
    ], GOLD, WHITE),
    "winNoMindTricks": ([
        "+.........+",
        ".+.#####.+.",
        "..+.....+..",
        ".#.+###+.#.",
        "#..#+.+#..#",
        "#..#.+.#..#",
        "#..#+.+#..#",
        ".#.+###+.#.",
        "..+.....+..",
        ".+.#####.+.",
        "+.........+",
    ], PURPLE, RED),
    "winAllHuman": ([
        "....###....",
        "....###....",
        "....###....",
        "...........",
        "..#######..",
        ".#.#####.#.",
        ".#.#####.#.",
        "...#####...",
        "...##.##...",
        "...##.##...",
        "...##.##...",
    ], WHITE, WHITE),
    "surrenders": ([
        "#+++++.....",
        "#++++++++..",
        "#+++++++++.",
        "#++++++++..",
        "#+++++.....",
        "#..........",
        "#..........",
        "#..........",
        "#..........",
        "#..........",
        "###........",
    ], GOLD, WHITE),
    "bossArtillery": ([
        "#....+.+.+.",
        ".#...+++++.",
        "..#..+++++.",
        "...#.......",
        "....#......",
        ".....#.....",
        "......#.#..",
        ".......###.",
        "......#####",
        ".......###.",
        "........#..",
    ], GOLD, GOLD),
    "dodges": ([
        "...........",
        "..........#",
        "+.+.+...###",
        ".......####",
        "+.+.+.#####",
        ".......####",
        "+.+.+...###",
        "..........#",
        "...........",
        "...........",
        "...........",
    ], WHITE, BLUE),
    "flawlessFights": ([
        "###########",
        "#.........#",
        "#....+....#",
        "#...+++...#",
        "#..+++++..#",
        "#.+++++++.#",
        ".#.......#.",
        ".#.......#.",
        "..#.....#..",
        "...#...#...",
        "....###....",
    ], WHITE, GOLD),
    "noShieldsSector": ([
        "+...###...+",
        ".+#.....#+.",
        ".#+.....+#.",
        "#..+...+..#",
        "#...+.+...#",
        "#....+....#",
        "#...+.+...#",
        ".#.+...+.#.",
        ".#+.....+#.",
        ".+.......+.",
        "+.........+",
    ], BLUE, RED),
    "cloakedVolleys": ([
        "...........",
        "#.#.#......",
        "...........",
        "#.#.#.+++..",
        "......++++.",
        "#.#.#.+++++",
        "......++++.",
        "#.#.#.+++..",
        "...........",
        "#.#.#......",
        "...........",
    ], CYAN, RED),
    "cloakChains": ([
        "...........",
        "###....+++.",
        "#.#....+.+.",
        "#.#....+.+.",
        "#.######.+.",
        "#.#....+.+.",
        "#.#....+.+.",
        "###....+++.",
        "...........",
        "...........",
        "...........",
    ], CYAN, WHITE),
    "bossCloaked": ([
        "+...+.+...+",
        "++.+++++.++",
        "+++++++++++",
        "...........",
        ".#.#.#.#.#.",
        "...........",
        "#.#######.#",
        "..#.....#..",
        "#.#######.#",
        "...........",
        ".#.#.#.#.#.",
    ], CYAN, GOLD),
}

# The backgrounds: vanilla's grey, with gold rays for the win, and red ones
# (as vanilla's third achievements have) for the win on Hard
RAYS = {"win": (205, 175, 80, 255), "winHard": (225, 90, 86, 255)}


def ship_images():
    """Each player ship's blueprint name, and its img (for its miniature)."""
    ships = {}
    for path in BLUEPRINT_FILES:
        for name, img in re.findall(r'<shipBlueprint\s+name="([^"]+)"[^>]*\simg="([^"]+)"', path.read_text()):
            ships[name] = img
    return ships


def achievements():
    """The achievements: each one's image, ship and goal kind."""
    text = ACHIEVEMENTS.read_text()
    found = []
    for block in re.findall(r"<achievement\b.*?</achievement>", text, re.S):
        img = re.search(r"<img>(.*?)</img>", block).group(1)
        ship = re.search(r"<ship>(.*?)</ship>", block).group(1)
        kind = re.search(r'<xftlGoal\s+kind="([^"]+)"', block).group(1)
        found.append((img, ship, kind))
    return found


def background(dat, kind):
    """Vanilla's grey square, with rays from the middle of the top for the wins."""
    base = dat.image(VANILLA_ICON).convert("RGBA").getpixel((0, 0))
    icon = Image.new("RGBA", (SIZE, SIZE), base)
    rays = RAYS.get(kind)
    if rays:
        draw = ImageDraw.Draw(icon)
        x0, y0, x1, y1 = BADGE_BOX
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
        for i in range(12):
            a0 = math.radians(i * 30)
            a1 = math.radians(i * 30 + 15)
            r = SIZE * 2
            draw.polygon([(cx, cy), (cx + r * math.cos(a0), cy + r * math.sin(a0)),
                          (cx + r * math.cos(a1), cy + r * math.sin(a1))], fill=rays)
    return icon


def ship(img):
    """The ship's hangar miniature, cut to its hull, toned down and scaled to fit SHIP_BOX."""
    mini = Image.open(MOD / "img" / "customizeUI" / ("miniship_%s.png" % img)).convert("RGBA")
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
    x0, y0, x1, y1 = SHIP_BOX
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


def badge(icon, kind):
    """The dark badge in the top right corner, with the kind's emblem in it."""
    draw = ImageDraw.Draw(icon)
    x0, y0, x1, y1 = BADGE_BOX
    draw.rounded_rectangle((x0 - 1, y0 - 1, x1, y1), radius=5, fill=BADGE_EDGE)
    draw.rounded_rectangle((x0, y0, x1 - 1, y1 - 1), radius=4, fill=BADGE_FILL)
    rows, first, second = EMBLEMS[kind]
    assert len(rows) == 11 and all(len(row) == 11 for row in rows), kind
    left = (x0 + x1) // 2 - 11
    top = (y0 + y1) // 2 - 11
    for y, row in enumerate(rows):
        for x, mark in enumerate(row):
            colour = {"#": first, "+": second}.get(mark)
            if colour:
                draw.rectangle((left + 2 * x, top + 2 * y, left + 2 * x + 1, top + 2 * y + 1), fill=colour)


def make(dat, img, kind):
    icon = background(dat, kind)
    hull = ship(img)
    x0, y0, x1, y1 = SHIP_BOX
    icon.alpha_composite(hull, ((x0 + x1 - hull.width) // 2, (y0 + y1 - hull.height) // 2))
    badge(icon, kind)
    return icon


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    dat = Dat(sys.argv[1])
    images = ship_images()
    made = []
    for path, ship_name, kind in achievements():
        if kind not in EMBLEMS:
            sys.exit("No emblem for the goal kind %s" % kind)
        icon = make(dat, images[ship_name], kind)
        out = MOD / "img" / path
        out.parent.mkdir(parents=True, exist_ok=True)
        icon.save(out)
        made.append(icon)
    print("Made %d achievement icons" % len(made))

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
