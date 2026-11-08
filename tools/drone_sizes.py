#!/usr/bin/env python3
"""
Draw how the twin and triple drones are meant to look (see
doc/game/drone-sizes.md), from the vanilla drones' images in ftl.dat: each
drone's single, twin and triple versions side by side, in a little scene
showing how its copies share the work out, and the enemies' bulwarks (their
anti-personnel drone, scaled up) in the rooms they fit in.

Without --preview, it makes the bulwarks' own images (see
doc/game/drone-sizes.md), which the game uses: the anti-personnel drone's
sprite sheets (its base and its glowing layer1) scaled up two and three
times with the Scale2x and Scale3x pixel-art scalers, which keep the edges
sharp and round off the stair-steps, rather than drawing each pixel as a
square block, into the mod's img/people.

    python3 tools/drone_sizes.py ftl.dat                  # the bulwarks' images
    python3 tools/drone_sizes.py ftl.dat --preview out/   # the preview

The preview writes one image for each drone into out/, and all.png with
them all; it's a sketch of the design, not the game's own drawing.
"""

import argparse
import math
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from weapon_sizes import Dat  # noqa: E402

# How much bigger than the game's own pixels the images are drawn
SCALE = 2

# A panel for each size, and the gap round them
PANEL = (300, 230)
GAP = 10
TITLE_HEIGHT = 26

ROOM = 35  # A room cell, as in the game

BACKGROUND = (22, 26, 30, 255)
PANEL_COLOUR = (34, 40, 46, 255)
TEXT = (230, 234, 236, 255)
MUTED = (150, 160, 166, 255)
LINE = (90, 104, 112, 255)
ENEMY = (200, 70, 60, 255)
FRIEND = (90, 190, 120, 255)
SHIELD = (90, 170, 255, 255)
ROOM_FLOOR = (190, 190, 182, 255)
ROOM_WALL = (60, 60, 60, 255)


def font(size):
    for path in ("/System/Library/Fonts/Supplemental/Arial.ttf", "/Library/Fonts/Arial.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


FONT = font(12)
SMALL = font(10)
BIG = font(15)


class Drones:
    """The vanilla drones' blueprints and images."""

    def __init__(self, dat):
        self.dat = dat
        text = dat.read("data/blueprints.xml").decode("utf-8", "replace")
        text += dat.read("data/dlcBlueprints.xml").decode("utf-8", "replace")
        self.blueprints = {}
        for m in re.finditer(r'<droneBlueprint name="([^"]+)">(.*?)</droneBlueprint>', text, re.S):
            body = m.group(2)

            def tag(name):
                found = re.search(r"<%s>([^<]*)</%s>" % (name, name), body)
                return found.group(1) if found else None

            title_id = re.search(r'<title id="([^"]+)"', body)
            short_id = re.search(r'<short id="([^"]+)"', body)
            self.blueprints[m.group(1)] = {
                "image": tag("droneImage"), "power": int(tag("power") or 0), "cost": int(tag("cost") or 0),
                "type": tag("type"),
                "title": title_id.group(1) if title_id else None,
                "short": short_id.group(1) if short_id else None,
            }
        self.titles = self._titles()

    def _titles(self):
        text = self.dat.read("data/text_blueprints.xml").decode("utf-8", "replace")
        return dict(re.findall(r'<text name="([^"]+)">([^<]*)</text>', text))

    def title(self, name, default):
        title_id = self.blueprints.get(name, {}).get("title")
        return self.titles.get(title_id, default) if title_id else default

    def short(self, name):
        short_id = self.blueprints[name]["short"]
        return self.titles.get(short_id, name) if short_id else self.title(name, name)

    def image(self, path):
        return self.dat.image(path)

    def icon(self, name):
        """The drone's icon in the interface, as DroneBlueprint.drawIconUI
        draws it: an indoors drone's portrait, or else the outside drone
        charged, with a defence drone's gun on it."""
        blueprint = self.blueprints[name]
        kind = blueprint["type"]
        if kind in ("REPAIR", "BATTLE", "BOARDER"):
            look = "boarder_ion" if name == "BOARDER_ION" else ("repair" if kind == "REPAIR" else "battle")
            return self.pawn(look, row=0)
        look = blueprint["image"]
        base = self.image("img/ship/drones/%s_charged.png" % look)
        if kind != "DEFENSE":
            return base
        # (shifted down 4 for the gun, which sticks out of the top)
        icon = Image.new("RGBA", (base.width, base.height + 4), (0, 0, 0, 0))
        icon.alpha_composite(base, (0, 4))
        for layer in ("gun_on", "gun_charged"):
            path = "img/ship/drones/%s_%s.png" % (look, layer)
            if path in self.dat.files:
                gun = self.image(path)
                if gun.size == base.size:
                    icon.alpha_composite(gun, (0, 4))
        return icon.crop(icon.getbbox())

    def external(self, name, charged=True):
        """An external drone as it flies: its base, and its lights on top."""
        look = self.blueprints[name]["image"]
        # (the shield overcharger has no base image: its charged one is the whole drone)
        base_path = "img/ship/drones/%s_base.png" % look
        if base_path not in self.dat.files:
            return self.image("img/ship/drones/%s_charged.png" % look)
        base = self.image(base_path)
        for layer in ("on", "charged") if charged else ("on",):
            path = "img/ship/drones/%s_%s.png" % (look, layer)
            if path in self.dat.files:
                lights = self.image(path)
                if lights.size == base.size:
                    base = Image.alpha_composite(base, lights)
                break
        gun = "img/ship/drones/%s_gun.png" % look
        if gun in self.dat.files:
            gun_image = self.image(gun)
            if gun_image.size == base.size:
                base = Image.alpha_composite(base, gun_image)
        return base

    def pawn(self, look, row=5):
        """An indoors drone (or boarder) facing down, one 35px frame of its sheet."""
        sheet = self.image("img/people/%s_base.png" % look)
        return sheet.crop((0, row * ROOM, ROOM, (row + 1) * ROOM))


# The bulwarks' images: the anti-personnel drone's sheets, scaled up
BULWARK_SHEETS = ["battle_base", "battle_layer1"]
MOD_PEOPLE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "mods", "chonkestrel", "img", "people")


def bulwark_sheet_name(sheet, size):
    """The name of a bulwark's [sheet] at [size], as AntiPersonnelDrone loads it."""
    return "xftl_bulwark_x%d_%s" % (size, sheet.split("_", 1)[1])


def scale2x(image):
    """The Scale2x (EPX) pixel-art scaler: each pixel becomes four, which
    take a neighbour's colour where two neighbours that meet at that
    corner agree, so diagonal edges are rounded off rather than stepped."""
    w, h = image.size
    src = image.load()
    out = Image.new("RGBA", (w * 2, h * 2))
    dst = out.load()

    def at(x, y):
        return src[min(max(x, 0), w - 1), min(max(y, 0), h - 1)]

    for y in range(h):
        for x in range(w):
            p = src[x, y]
            a, b, c, d = at(x, y - 1), at(x + 1, y), at(x - 1, y), at(x, y + 1)
            dst[2 * x, 2 * y] = a if c == a and c != d and a != b else p
            dst[2 * x + 1, 2 * y] = b if a == b and a != c and b != d else p
            dst[2 * x, 2 * y + 1] = c if d == c and d != b and c != a else p
            dst[2 * x + 1, 2 * y + 1] = d if b == d and b != a and d != c else p
    return out


def scale3x(image):
    """The Scale3x pixel-art scaler: as [scale2x], each pixel becoming nine."""
    w, h = image.size
    src = image.load()
    out = Image.new("RGBA", (w * 3, h * 3))
    dst = out.load()

    def at(x, y):
        return src[min(max(x, 0), w - 1), min(max(y, 0), h - 1)]

    for y in range(h):
        for x in range(w):
            a, b, c = at(x - 1, y - 1), at(x, y - 1), at(x + 1, y - 1)
            d, e, f = at(x - 1, y), at(x, y), at(x + 1, y)
            g, hh, i = at(x - 1, y + 1), at(x, y + 1), at(x + 1, y + 1)
            if b != hh and d != f:
                e0 = d if d == b else e
                e1 = b if (d == b and e != c) or (b == f and e != a) else e
                e2 = f if b == f else e
                e3 = d if (d == b and e != g) or (d == hh and e != a) else e
                e5 = f if (b == f and e != i) or (hh == f and e != c) else e
                e6 = d if d == hh else e
                e7 = hh if (d == hh and e != i) or (hh == f and e != g) else e
                e8 = f if hh == f else e
                block = [e0, e1, e2, e3, e, e5, e6, e7, e8]
            else:
                block = [e] * 9
            for k, colour in enumerate(block):
                dst[3 * x + k % 3, 3 * y + k // 3] = colour
    return out


def make_bulwark_sheets(dat, out_dir=MOD_PEOPLE):
    """Write the bulwarks' sheets (see [bulwark_sheet_name]) into [out_dir]."""
    os.makedirs(out_dir, exist_ok=True)
    made = []
    for sheet in BULWARK_SHEETS:
        image = dat.image("img/people/%s.png" % sheet)
        for size, scaler in ((2, scale2x), (3, scale3x)):
            path = os.path.join(out_dir, bulwark_sheet_name(sheet, size) + ".png")
            scaler(image).save(path)
            made.append(path)
    return made


def sized_power(power, size):
    """As the weapons' (see WeaponSizes): half again, rounded up, for a twin; twice for a triple."""
    if size == 1:
        return power
    if size == 2:
        return power + (power + 1) // 2
    return power * 2


def sized_cost(cost, size):
    return {1: cost, 2: round(cost * 2.2), 3: round(cost * 3.3)}[size]


def paste_centred(target, image, centre, scale=1):
    if scale != 1:
        image = image.resize((image.width * scale, image.height * scale), Image.NEAREST)
    # (kept inside the target, which alpha_composite needs)
    x = min(max(int(centre[0] - image.width / 2), 0), max(target.width - image.width, 0))
    y = min(max(int(centre[1] - image.height / 2), 0), max(target.height - image.height, 0))
    target.alpha_composite(image, (x, y))


def draw_label(draw, xy, text, fill=TEXT, f=None):
    draw.text(xy, text, fill=fill, font=f or FONT)


def darker(image, amount=0.65):
    r, g, b, a = image.split()
    r, g, b = (band.point(lambda v: int(v * amount)) for band in (r, g, b))
    return Image.merge("RGBA", (r, g, b, a))


def combined_icon(icon, size):
    """A combined drone's icon, as the combined weapons are drawn: a twin's
    two side by side, a triple's three in a triangle, two behind (darker,
    raised) and one in front."""
    icon = icon.crop(icon.getbbox())
    if size == 1:
        return icon
    w, h = icon.size
    if size == 2:
        gap = 2
        canvas = Image.new("RGBA", (2 * w + gap, h), (0, 0, 0, 0))
        canvas.alpha_composite(icon, (0, 0))
        canvas.alpha_composite(icon, (w + gap, 0))
        return canvas
    rise = 8
    spread = round(w * 0.55)
    canvas = Image.new("RGBA", (w + 2 * spread, h + rise), (0, 0, 0, 0))
    back = darker(icon)
    canvas.alpha_composite(back, (0, 0))
    canvas.alpha_composite(back, (2 * spread, 0))
    canvas.alpha_composite(icon, (spread, rise))
    return canvas


def text_width(text, f):
    return f.getbbox(text)[2] if text else 0


def fit_name(name, f, width, lines):
    """Cut a name down to fit in [lines] lines of [width], as ShortNames
    does: its long words cut to three letters and a full stop, left to
    right, until it fits."""
    def wrap(words):
        out, line = [], ""
        for word in words:
            candidate = (line + " " + word).strip()
            if line and text_width(candidate, f) > width:
                out.append(line)
                line = word
            else:
                line = candidate
        out.append(line)
        return out

    words = name.split(" ")
    for i in range(len(words) + 1):
        wrapped = wrap(words)
        if len(wrapped) <= lines and all(text_width(l, f) <= width for l in wrapped):
            return wrapped
        if i < len(words) and len(words[i]) > 4 and not words[i].startswith("("):
            words[i] = words[i][:3] + "."
    return wrap(words)[:lines]


def sized_short(drones, name, size):
    short = drones.short(name)
    return short if size == 1 else "%s (x%d)" % (short, size)


UI_HEIGHT = 138
NAME_FONT = font(9)


def ui_row(drones, name, size):
    """How a drone looks in the interface: its store box (with its price),
    its equipment box, and its drones bar button."""
    blueprint = drones.blueprints[name]
    panel = Image.new("RGBA", (PANEL[0], UI_HEIGHT), PANEL_COLOUR)
    icon = combined_icon(drones.icon(name), size)
    short = sized_short(drones, name, size)

    def box(image_path, origin, price=None):
        frame = drones.image(image_path)
        panel.alpha_composite(frame, origin)
        # As BlueprintButton draws it: the icon centred at (60, 35), and
        # the name under it, in the 96 pixels from 11
        paste_centred(panel, icon, (origin[0] + 60, origin[1] + 35))
        line = fit_name(short, NAME_FONT, 96, 1)[0]
        d = ImageDraw.Draw(panel)
        d.text((origin[0] + 11 + (96 - text_width(line, NAME_FONT)) // 2, origin[1] + 62), line,
               fill=TEXT, font=NAME_FONT)
        if price is not None:
            label = "%d" % price
            d.text((origin[0] + 11 + (96 - text_width(label, FONT)) // 2, origin[1] + 84), label,
                   fill=(255, 220, 120, 255), font=FONT)

    box("img/storeUI/store_buy_drones_on.png", (6, 8), sized_cost(blueprint["cost"], size))
    box("img/upgradeUI/Equipment/box_drones_on.png", (124, 8))

    # The drones bar's button: its name in two lines of 58 pixels, and its power
    d = ImageDraw.Draw(panel)
    bx, by = 124, 86
    d.rectangle((bx, by, bx + 94, by + 38), outline=(240, 240, 240, 255), width=2)
    for i, line in enumerate(fit_name(short, NAME_FONT, 58, 2)):
        d.text((bx + 6, by + 6 + i * 12), line, fill=TEXT, font=NAME_FONT)
    for i in range(sized_power(blueprint["power"], size)):
        x = bx + 70 + (i % 4) * 5
        y = by + 30 - (i // 4) * 9
        d.rectangle((x, y - 6, x + 3, y), fill=(120, 230, 90, 255))
    draw_label(d, (230, 98), "drones bar", MUTED, SMALL)
    draw_label(d, (44, 122), "store", MUTED, SMALL)
    draw_label(d, (236, 40), "equipment", MUTED, SMALL)
    return panel


# The scenes: each draws one size of a drone into a panel

def enemy_windows(draw, count=3):
    """Three enemy windows side by side, as the player sees them: the main
    one on the right. Returns their centres, left to right."""
    centres = []
    width = (PANEL[0] - 20 - (count - 1) * 8) // count
    for i in range(count):
        x = 10 + i * (width + 8)
        box = (x, 40, x + width, 150)
        draw.rectangle(box, outline=ENEMY, width=2)
        label = "main" if i == count - 1 else "ship %d" % (i + 1)
        draw_label(draw, (x + 4, 42), label, MUTED, SMALL)
        centres.append(((box[0] + box[2]) // 2, (box[1] + box[3]) // 2))
    return centres


def wing_targets(size, ships):
    """The ships a wing's copies go to: one each, left to right, starting
    with the main one, and the rest to the main one."""
    main = ships - 1
    order = [main] + [i for i in range(ships) if i != main]
    return [order[i] if i < ships else main for i in range(size)]


def scene_wing(panel, drones, name, size):
    draw = ImageDraw.Draw(panel)
    centres = enemy_windows(draw)
    sprite = drones.external(name)
    counts = {}
    for target in wing_targets(size, len(centres)):
        n = counts.get(target, 0)
        counts[target] = n + 1
        cx, cy = centres[target]
        paste_centred(panel, sprite, (cx + 10 * n, cy - 12 + 22 * n))
    draw_label(draw, (10, 160), "one copy at each ship, left to right" if size > 1 else "at the main ship",
               MUTED, SMALL)


def orbit_points(centre, radius, count, start=-math.pi / 2):
    return [(centre[0] + radius[0] * math.cos(start + i * 2 * math.pi / count),
             centre[1] + radius[1] * math.sin(start + i * 2 * math.pi / count)) for i in range(count)]


def player_ship(drones, width=150):
    hull = drones.image("img/ship/kestral_base.png")
    height = round(hull.height * width / hull.width)
    return hull.resize((width, height), Image.LANCZOS)


def scene_screen(panel, drones, name, size):
    draw = ImageDraw.Draw(panel)
    centre = (PANEL[0] // 2, 100)
    paste_centred(panel, player_ship(drones), centre)
    radius = (115, 62)
    draw.ellipse((centre[0] - radius[0], centre[1] - radius[1], centre[0] + radius[0], centre[1] + radius[1]),
                 outline=SHIELD, width=1)
    sprite = drones.external(name)
    for point in orbit_points(centre, radius, size):
        paste_centred(panel, sprite, point)
    draw_label(draw, (10, 175), "spread round the orbit, never at the same shot" if size > 1 else "", MUTED, SMALL)


def scene_volley(panel, drones, name, size):
    """The anti-combat drone's copies, each firing at its own enemy drone."""
    draw = ImageDraw.Draw(panel)
    targets = [(230, 50), (240, 100), (230, 150)]
    origins = [(60, 50), (70, 100), (60, 150)][:size] if size > 1 else [(60, 100)]
    enemy = drones.external("COMBAT_1", charged=False)
    for target in targets:
        paste_centred(panel, enemy, target)
    sprite = drones.external(name)
    for origin, target in zip(origins, targets if size > 1 else [targets[1]]):
        draw.line((origin, target), fill=SHIELD, width=2)
        paste_centred(panel, sprite, origin)
    draw_label(draw, (10, 175), "each copy at its own drone" if size > 1 else "", MUTED, SMALL)


def flying_boarder(drones, look):
    engine = drones.image("img/ship/drones/boarder_engine.png")
    pawn = drones.pawn(look, row=0)
    # As BoardingDrone draws it: the thruster over the robot's portrait, both
    # from the same corner
    canvas = Image.new("RGBA", (max(engine.width, pawn.width), max(engine.height, pawn.height)), (0, 0, 0, 0))
    canvas.alpha_composite(pawn, (0, 0))
    canvas.alpha_composite(engine, (0, 0))
    return canvas


def scene_raid(panel, drones, name, size):
    draw = ImageDraw.Draw(panel)
    centres = enemy_windows(draw)
    look = "boarder_ion" if name == "BOARDER_ION" else "battle"
    sprite = flying_boarder(drones, look)
    counts = {}
    for target in wing_targets(size, len(centres)):
        n = counts.get(target, 0)
        counts[target] = n + 1
        cx, cy = centres[target]
        paste_centred(panel, sprite, (cx - 12 + 24 * n, cy + 10))
    draw_label(draw, (10, 160), "one boards each ship, in its own room" if size > 1 else "boards the main ship",
               MUTED, SMALL)


def draw_rooms(panel, rooms, origin):
    """Rooms as (x, y, w, h) in cells, drawn as the game's floor and walls."""
    draw = ImageDraw.Draw(panel)
    for (x, y, w, h) in rooms:
        box = (origin[0] + x * ROOM, origin[1] + y * ROOM,
               origin[0] + (x + w) * ROOM, origin[1] + (y + h) * ROOM)
        draw.rectangle(box, fill=ROOM_FLOOR, outline=ROOM_WALL, width=2)
        for cx in range(1, w):
            draw.line((box[0] + cx * ROOM, box[1], box[0] + cx * ROOM, box[3]), fill=(170, 170, 162, 255))
        for cy in range(1, h):
            draw.line((box[0], box[1] + cy * ROOM, box[2], box[1] + cy * ROOM), fill=(170, 170, 162, 255))
    return draw


def cell_centre(origin, cell):
    return (origin[0] + cell[0] * ROOM + ROOM // 2, origin[1] + cell[1] * ROOM + ROOM // 2)


SQUAD_ROOMS = [(0, 0, 2, 2), (2, 0, 2, 2), (4, 0, 2, 2), (1, 2, 2, 1), (3, 2, 2, 1)]
SQUAD_ORIGIN = (45, 35)


def scene_squad(panel, drones, look, size, marker, marker_rooms, caption):
    draw = draw_rooms(panel, SQUAD_ROOMS, SQUAD_ORIGIN)
    # What they're after: intruders, or damaged systems
    for room in marker_rooms:
        x, y, w, h = SQUAD_ROOMS[room]
        c = cell_centre(SQUAD_ORIGIN, (x + w - 1, y))
        draw.ellipse((c[0] - 6, c[1] - 6, c[0] + 6, c[1] + 6), fill=marker)
    sprite = drones.pawn(look)
    for i in range(size):
        x, y, w, h = SQUAD_ROOMS[marker_rooms[i % len(marker_rooms)]]
        paste_centred(panel, sprite, cell_centre(SQUAD_ORIGIN, (x, y + h - 1)))
    draw_label(draw, (10, 175), caption if size > 1 else "", MUTED, SMALL)


def scene_team(panel, drones, name, size):
    draw = ImageDraw.Draw(panel)
    centre = (PANEL[0] // 2, 100)
    paste_centred(panel, player_ship(drones), centre)
    sprite = drones.external(name)
    for i in range(size):
        paste_centred(panel, sprite, (centre[0] - 40 + 40 * i, centre[1] - 45 + (i % 2) * 90))
    draw_label(draw, (10, 175), "N times the hull mended a trip" if size > 1 else "", MUTED, SMALL)


def scene_bank(panel, drones, name, size):
    draw = ImageDraw.Draw(panel)
    centre = (PANEL[0] // 2, 95)
    paste_centred(panel, player_ship(drones), centre)
    radius = (115, 62)
    draw.ellipse((centre[0] - radius[0], centre[1] - radius[1], centre[0] + radius[0], centre[1] + radius[1]),
                 outline=SHIELD, width=1)
    sprite = drones.external(name)
    for point in orbit_points(centre, radius, size, start=-0.4):
        paste_centred(panel, sprite, point)
    # The rows of super shield they look after, one each
    for row in range(size):
        for point in range(5):
            x = 70 + point * 16
            y = 168 + row * 10
            draw.rectangle((x, y, x + 12, y + 6), fill=(80, 220, 120, 255))
    draw_label(draw, (160, 166), "%d row%s of 5, one each" % (size, "s" if size > 1 else ""), MUTED, SMALL)


# The drones, each with its scene, and how it shares the work out

def drone_kinds(drones):
    def title(name, default):
        return drones.title(name, default)

    kinds = []
    for name in ("COMBAT_1", "COMBAT_2", "COMBAT_BEAM", "COMBAT_BEAM_2", "COMBAT_FIRE"):
        kinds.append((name, title(name, name), "A wing", lambda p, s, n=name: scene_wing(p, drones, n, s)))
    for name in ("DEFENSE_1", "DEFENSE_2"):
        kinds.append((name, title(name, name), "A screen", lambda p, s, n=name: scene_screen(p, drones, n, s)))
    kinds.append(("ANTI_DRONE", title("ANTI_DRONE", "Anti-Combat Drone"), "A volley",
                  lambda p, s: scene_volley(p, drones, "ANTI_DRONE", s)))
    for name in ("BOARDER", "BOARDER_ION"):
        kinds.append((name, title(name, name), "A raid", lambda p, s, n=name: scene_raid(p, drones, n, s)))
    kinds.append(("BATTLE", title("BATTLE", "Anti-Personnel Drone"), "A squad (the player's)",
                  lambda p, s: scene_squad(p, drones, "battle", s, ENEMY, [0, 2, 4],
                                           "each to its own room with intruders")))
    kinds.append(("REPAIR", title("REPAIR", "System Repair Drone"), "A crew",
                  lambda p, s: scene_squad(p, drones, "repair", s, (255, 170, 40, 255), [1, 3, 0],
                                           "each to its own damaged system")))
    kinds.append(("SHIP_REPAIR", title("SHIP_REPAIR", "Hull Repair Drone"), "A team",
                  lambda p, s: scene_team(p, drones, "SHIP_REPAIR", s)))
    kinds.append(("DRONE_SHIELD_PLAYER", title("DRONE_SHIELD_PLAYER", "Shield Overcharger"), "A bank (one row each)",
                  lambda p, s: scene_bank(p, drones, "DRONE_SHIELD_PLAYER", s)))
    return kinds


def sheet(drones, name, title, how, scene):
    blueprint = drones.blueprints[name]
    width = 3 * PANEL[0] + 4 * GAP
    height = TITLE_HEIGHT + PANEL[1] + UI_HEIGHT + 3 * GAP
    image = Image.new("RGBA", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(image)
    draw_label(draw, (GAP, 6), "%s  -  %s" % (title, how), TEXT, BIG)
    for i, size in enumerate((1, 2, 3)):
        panel = Image.new("RGBA", PANEL, PANEL_COLOUR)
        scene(panel, size)
        pd = ImageDraw.Draw(panel)
        label = {1: "Single", 2: "Twin (x2)", 3: "Triple (x3)"}[size]
        draw_label(pd, (8, 6), label, TEXT)
        draw_label(pd, (8, 205), "power %d, %d scrap, %d drone part%s" % (
            sized_power(blueprint["power"], size), sized_cost(blueprint["cost"], size), size,
            "s" if size > 1 else ""), MUTED, SMALL)
        image.alpha_composite(panel, (GAP + i * (PANEL[0] + GAP), TITLE_HEIGHT + GAP))
        image.alpha_composite(ui_row(drones, name, size),
                              (GAP + i * (PANEL[0] + GAP), TITLE_HEIGHT + PANEL[1] + 2 * GAP))
    return image


def bulwark_sheet(drones):
    """The enemies' bulwarks: their anti-personnel drone scaled up, on a
    square of cells, in the rooms it fits in, and one it doesn't."""
    width = 3 * PANEL[0] + 4 * GAP
    height = TITLE_HEIGHT + PANEL[1] + 2 * GAP
    image = Image.new("RGBA", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(image)
    draw_label(draw, (GAP, 6), "Bulwarks - the enemies' twin and triple anti-personnel drones", TEXT, BIG)
    sprite = drones.pawn("battle")
    # A boarder of the player's, come into the room
    boarder = drones.pawn("human", row=0) if "img/people/human_base.png" in drones.dat.files else None

    # Each in a room with a cell or so to spare, for the boarder
    cases = [
        ("Single (moves)", (0, 0, 2, 2), 1, (1, 1)),
        ("Twin bulwark: 2x2, 4x health", (0, 0, 3, 2), 2, (2, 1)),
        ("Triple bulwark: 3x3, 9x health", (0, 0, 4, 3), 3, (3, 2)),
    ]
    for i, (label, room, size, boarder_cell) in enumerate(cases):
        panel = Image.new("RGBA", PANEL, PANEL_COLOUR)
        origin = ((PANEL[0] - room[2] * ROOM) // 2, 40)
        draw_rooms(panel, [room], origin)
        # (its own image, made by Scale2x or Scale3x, see make_bulwark_sheets)
        if size == 1:
            scaled = sprite
        else:
            scaler = scale2x if size == 2 else scale3x
            scaled = scaler(sprite)
        panel.alpha_composite(scaled, origin)
        if boarder is not None:
            panel.alpha_composite(boarder, (origin[0] + boarder_cell[0] * ROOM, origin[1] + boarder_cell[1] * ROOM))
        pd = ImageDraw.Draw(panel)
        draw_label(pd, (8, 6), label, TEXT)
        draw_label(pd, (8, 205), "stays in its room, fights whoever comes in" if size > 1 else "hunts boarders",
                   MUTED, SMALL)
        image.alpha_composite(panel, (GAP + i * (PANEL[0] + GAP), TITLE_HEIGHT + GAP))
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("datafile")
    parser.add_argument("--preview", help="draw the preview into this directory, rather than making the bulwarks' images")
    args = parser.parse_args()

    dat = Dat(os.path.expanduser(args.datafile))
    if not args.preview:
        for path in make_bulwark_sheets(dat):
            print("Wrote", os.path.relpath(path))
        return
    drones = Drones(dat)
    os.makedirs(args.preview, exist_ok=True)

    sheets = []
    for name, title, how, scene in drone_kinds(drones):
        image = sheet(drones, name, title, how, scene)
        sheets.append(image)
        image.resize((image.width * SCALE, image.height * SCALE), Image.NEAREST) \
            .save(os.path.join(args.preview, "%s.png" % name.lower()))
    bulwarks = bulwark_sheet(drones)
    sheets.append(bulwarks)
    bulwarks.resize((bulwarks.width * SCALE, bulwarks.height * SCALE), Image.NEAREST) \
        .save(os.path.join(args.preview, "bulwarks.png"))

    total = Image.new("RGBA", (sheets[0].width, sum(s.height for s in sheets)), BACKGROUND)
    y = 0
    for s in sheets:
        total.alpha_composite(s, (0, y))
        y += s.height
    total.save(os.path.join(args.preview, "all.png"))
    print("Drew %d drones and the bulwarks into %s" % (len(sheets) - 1, args.preview))


if __name__ == "__main__":
    main()
