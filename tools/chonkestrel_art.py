#!/usr/bin/env python3
"""
Make the Chonkestrel's images (the mod's player ship, see
mods/chonkestrel/data/chonkestrel.xml) from the vanilla Kestrel A's in
ftl.dat: its hull, cloak, floor, gibs and shield, its bigger rooms'
interiors, and its miniature for the hangar.

    python3 tools/chonkestrel_art.py ftl.dat                # make them
    python3 tools/chonkestrel_art.py ftl.dat --preview out/ # draw them, and the mod's, side by side

The Chonkestrel is the Kestrel made four rows of rooms (140 pixels)
taller, by stretching the middle of its hull (see ship_variants.insert):
its rows from SPLIT on move down, and the gap is filled with copies of
row SOURCE. Then two things are moved to suit the taller ship:

- the cockpit bump on the nose (its rows TIP_ROWS, right of NOSE_X) is
  moved to the middle of the nose, and the nose where it was is made flat,
  as it is either side of it;
- the airlock bracket at the top of the rear (its rows REAR_ROWS, left of
  REAR_X) is mirrored at the bottom, about the middle of the ship, so it's
  lit from below there, as the rest of the hull's bottom is;
- the weapons blocks at the top and bottom (BLOCK) are made wider, towards
  the nose (by repeating their column BLOCK_AT), so the weapons on them are
  spread out, and the slope of the hull past them (SLOPE) is cut off level
  with them.

The floor is cut to match the pilot's room, which loses the top and
bottom cells the stretch gave it (PILOT_CUT): the floor's left 6 pixels
past it, the outer 2 of them its black outline, as vanilla's is.

The cloak and the floor are stretched the same way (at the same place on
the ship: they're drawn offset from the hull, see OFFSETS), the cloak
changed as the hull is, and the gibs too, each where it is on the hull (its
place in the layout's explosion, which doesn't change). The shield is the
Kestrel's, scaled to the Chonkestrel's ellipse, and the miniature is the
new hull scaled down, with crisp outlines as vanilla's have. The rooms'
interiors are the vanilla ones', their corners moved apart to fit the
Chonkestrel's bigger rooms. The images go in the mod's img/.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

from ship_variants import MOD, Dat, insert

# How much taller it is, where the hull's stretched, and the row its copies
# are of (in the hull's pixels)
ADD = 140
SPLIT = 243
SOURCE = 239

# The cockpit bump on the nose, and the airlock bracket at the rear: the rows
# (in the vanilla hull) moved or mirrored, and how far into the ship
TIP_ROWS = (178, 242)
NOSE_X = 566
REAR_ROWS = (168, 244)
REAR_X = 118

# The weapons block at the top (the bottom one's its mirror image): its box
# (left, top, right, bottom), the column repeated and how many more; and the
# slope of the hull past it that's cut off level with its bottom
BLOCK = (262, 70, 346, 137)
BLOCK_AT = 300
BLOCK_ADD = 68
SLOPE = (414, 90, 472, 137)

# The floor cut from around the pilot's room: from FLOOR_CUT_X, above
# FLOOR_CUT_ROWS[0] and below FLOOR_CUT_ROWS[1] (in the floor's pixels), and
# the width of its outline
PILOT_CUT_X = 510
PILOT_CUT_ROWS = (109, 261)
OUTLINE = 2

# Where the cloak and the floor are drawn, relative to the hull (the layout's
# offsets): the same stretch is at the same place on the ship
OFFSETS = {"cloak": (19, 19), "floor": (45, 93)}

# The gibs' places on the hull (the layout's explosion, the same as the
# Kestrel's)
GIBS = {1: (-5, 114), 2: (25, 23), 3: (35, -7), 4: (38, 193), 5: (174, 44), 6: (375, 107)}

# The shield's size (the layout's ellipse, twice its radii) and the
# miniature's (as vanilla's)
SHIELD_SIZE = (700, 580)
MINIATURE_SIZE = (191, 121)

# The bigger rooms' interiors: the vanilla image, the new one's size, and
# how it's put together (each copy of the vanilla image, flipped or not, at
# a place, keeping only the part of it inside a box of the new image)
ROOMS = {
    "room_medbay_big": ("room_medbay", (140, 140), [
        ("flip_h", (0, 0), (0, 0, 70, 38)), ("none", (70, 0), (70, 0, 140, 38)),
        ("flip_hv", (0, 70), (0, 102, 70, 140)), ("flip_v", (70, 70), (70, 102, 140, 140)),
    ]),
    "room_pilot_tall": ("room_pilot", (35, 140), [
        ("flip_v", (0, 0), (0, 0, 35, 35)), ("none", (0, 35), (0, 35, 35, 105)),
    ]),
    "room_cloaking_wide": ("room_cloaking", (140, 70), [
        ("flip_h", (0, 0), (0, 0, 45, 35)), ("none", (70, 0), (95, 0, 140, 35)),
        ("flip_hv", (0, 0), (0, 35, 45, 70)), ("flip_v", (70, 0), (95, 35, 140, 70)),
    ]),
}


def stretched(image, dy):
    """The image (dy pixels below the hull's top) stretched as the hull is."""
    return insert(image, "y", SPLIT - dy, ADD, SOURCE - dy)


def reshape(image, dx, dy):
    """
    Move the cockpit bump to the middle of the nose, and mirror the airlock
    bracket at the bottom of the rear, in a stretched image of the hull
    (or of something drawn on it, dx and dy from its top left corner).
    """
    pixels = np.array(image)
    height = pixels.shape[0]
    centre = (ADD + 418) // 2 - dy  # The middle of the stretched hull (418 tall before)

    # The bump, moved to the middle; the nose where it was is filled with the
    # flat nose below it (the stretched rows)
    top, bottom = TIP_ROWS[0] - dy, TIP_ROWS[1] - dy
    x = max(0, NOSE_X - dx)
    if 0 <= top and bottom <= height and x < pixels.shape[1]:
        bump = pixels[top:bottom, x:].copy()
        flat = pixels[SPLIT - dy, x:].copy()
        pixels[top:bottom, x:] = flat
        at = centre - (bottom - top) // 2
        pixels[at:at + (bottom - top), x:] = bump

    # The bracket, mirrored about the middle
    top, bottom = REAR_ROWS[0] - dy, REAR_ROWS[1] - dy
    x = REAR_X - dx
    if 0 <= top and bottom <= height and x > 0:
        bracket = pixels[top:bottom, :x][::-1].copy()
        at = 2 * centre - bottom
        pixels[at:at + (bottom - top), :x] = bracket

    # The weapons blocks, wider: the top one, and the bottom one by doing the
    # same upside down (the hull's the same shape, mirrored about the middle)
    widen_block(pixels, dx, dy)
    flipped = pixels[::-1].copy()
    widen_block(flipped, dx, (ADD + 418) - height - dy)
    pixels = flipped[::-1].copy()
    return Image.fromarray(pixels, "RGBA")


def widen_block(pixels, dx, dy):
    """Make the weapons block at the top wider (see BLOCK), in place."""
    left, top, right, bottom = BLOCK[0] - dx, BLOCK[1] - dy, BLOCK[2] - dx, BLOCK[3] - dy
    if top < 0 or bottom > pixels.shape[0] or right <= 0 or left >= pixels.shape[1]:
        return
    left = max(0, left)
    block = Image.fromarray(np.ascontiguousarray(pixels[top:bottom, left:right]), "RGBA")
    wide = np.array(insert(block, "x", BLOCK_AT - dx - left, BLOCK_ADD, BLOCK_AT - dx - left - 1))
    end = min(pixels.shape[1], left + wide.shape[1])
    pixels[top:bottom, left:end] = wide[:, :end - left]
    s_left, s_top, s_right, s_bottom = SLOPE[0] - dx, SLOPE[1] - dy, SLOPE[2] - dx, SLOPE[3] - dy
    pixels[max(0, s_top):s_bottom, max(0, s_left):max(0, s_right)] = 0


def hull(dat):
    return reshape(stretched(dat.image("img/ship/kestral_base.png").convert("RGBA"), 0), 0, 0)


def cloak(dat):
    dx, dy = OFFSETS["cloak"]
    return reshape(stretched(dat.image("img/ship/kestral_cloak.png").convert("RGBA"), dy), dx, dy)


def floor(dat):
    """The floor, stretched, and cut from around the pilot's room (see PILOT_CUT_X)."""
    image = stretched(dat.image("img/ship/kestral_floor.png").convert("RGBA"), OFFSETS["floor"][1])
    pixels = np.array(image)
    x, (top, bottom) = PILOT_CUT_X, PILOT_CUT_ROWS
    pixels[:top, x:] = 0
    pixels[bottom:, x:] = 0
    # The new edges' outline, where there's floor
    black = np.array([0, 0, 0, 255], dtype=np.uint8)
    for rows, cols in [(slice(top, top + OUTLINE), slice(x, None)), (slice(bottom - OUTLINE, bottom), slice(x, None)),
                       (slice(None, top + OUTLINE), slice(x - OUTLINE, x)),
                       (slice(bottom - OUTLINE, None), slice(x - OUTLINE, x))]:
        region = pixels[rows, cols]
        region[region[..., 3] > 0] = black
    return Image.fromarray(pixels, "RGBA")


def gib(dat, i):
    """A gib, made on the hull where it is (so it's changed as the hull is), then cut back out."""
    piece = dat.image("img/ship/kestral_gib%d.png" % i).convert("RGBA")
    gx, gy = GIBS[i]
    pad = 20  # (some sit a little off the hull's top left)
    canvas = Image.new("RGBA", (652 + 2 * pad, 418 + 2 * pad))
    canvas.paste(piece, (gx + pad, gy + pad))
    canvas = reshape(stretched(canvas, -pad), -pad, -pad)
    height = piece.height + (ADD if gy < SPLIT < gy + piece.height else 0)
    top = gy + pad + (ADD if gy >= SPLIT else 0)
    return canvas.crop((gx + pad, top, gx + pad + piece.width, top + height))


def shield(dat):
    return dat.image("img/ship/kestral_shields1.png").convert("RGBA").resize(SHIELD_SIZE, Image.BICUBIC)


def miniature(new_hull):
    """
    The hull, scaled to fit the miniature, with crisp outlines as vanilla's
    have: the hull's edge, and its black lines where they cover enough of a
    pixel.
    """
    w, h = new_hull.size
    scale = min(MINIATURE_SIZE[0] / w, MINIATURE_SIZE[1] / h)
    size = (round(w * scale), round(h * scale))
    small = np.array(new_hull.resize(size, Image.BOX)).astype(np.float32)

    src = np.array(new_hull).astype(np.float32)
    dark = ((src[..., :3].max(axis=2) < 60) & (src[..., 3] > 200)).astype(np.float32)
    dark_share = np.array(Image.fromarray((dark * 255).astype(np.uint8)).resize(size, Image.BOX)) / 255

    solid = small[..., 3] >= 128
    out = small.copy()
    out[..., 3] = np.where(solid, 255, 0)
    out[dark_share >= 0.35, :3] = 0
    # The edge: solid pixels next to a clear one
    edge = solid & ~(np.roll(solid, 1, 0) & np.roll(solid, -1, 0) & np.roll(solid, 1, 1) & np.roll(solid, -1, 1))
    out[edge, :3] = out[edge, :3] * 0.25
    image = Image.fromarray(out.clip(0, 255).astype(np.uint8), "RGBA")
    canvas = Image.new("RGBA", MINIATURE_SIZE)
    canvas.paste(image, ((MINIATURE_SIZE[0] - size[0]) // 2, (MINIATURE_SIZE[1] - size[1]) // 2))
    return canvas


def room(dat, name):
    source, size, copies = ROOMS[name]
    vanilla = dat.image("img/ship/interior/%s.png" % source).convert("RGBA")
    flips = {
        "none": vanilla,
        "flip_h": vanilla.transpose(Image.FLIP_LEFT_RIGHT),
        "flip_v": vanilla.transpose(Image.FLIP_TOP_BOTTOM),
        "flip_hv": vanilla.transpose(Image.ROTATE_180),
    }
    out = Image.new("RGBA", size)
    for flip, (x, y), box in copies:
        copy = flips[flip]
        out.paste(copy.crop((box[0] - x, box[1] - y, box[2] - x, box[3] - y)), box[:2])
    return out


def images(dat):
    """Every image, by its path in the mod's img/."""
    new_hull = hull(dat)
    result = {
        "ship/chonkestrel_base.png": new_hull,
        "ship/chonkestrel_cloak.png": cloak(dat),
        "ship/chonkestrel_floor.png": floor(dat),
        "ship/chonkestrel_shields1.png": shield(dat),
        "customizeUI/miniship_chonkestrel.png": miniature(new_hull),
    }
    for i in GIBS:
        result["ship/chonkestrel_gib%d.png" % i] = gib(dat, i)
    for name in ROOMS:
        result["ship/interior/%s.png" % name] = room(dat, name)
    return result


def preview(made, out):
    """Each new image beside the one in the mod now (if there is one)."""
    out.mkdir(parents=True, exist_ok=True)
    for path, image in made.items():
        old_file = MOD / "img" / path
        panels = [image] + ([Image.open(old_file).convert("RGBA")] if old_file.exists() else [])
        width = sum(p.width for p in panels) + 10 * (len(panels) - 1)
        sheet = Image.new("RGBA", (width, max(p.height for p in panels)), (0, 0, 0, 255))
        x = 0
        for p in panels:
            back = Image.new("RGBA", p.size, (40, 40, 60, 255))
            back.alpha_composite(p)
            sheet.paste(back, (x, 0))
            x += p.width + 10
        scale = 2 if sheet.width < 800 else 1
        sheet.resize((sheet.width * scale, sheet.height * scale), Image.NEAREST).save(
            out / path.replace("/", "_"))
    print("Previews in", out)


def main():
    dat = Dat(sys.argv[1])
    made = images(dat)
    if len(sys.argv) > 3 and sys.argv[2] == "--preview":
        preview(made, Path(sys.argv[3]))
        return
    for path, image in made.items():
        target = MOD / "img" / path
        target.parent.mkdir(parents=True, exist_ok=True)
        image.save(target)
    print("Made the Chonkestrel's %d images" % len(made))


if __name__ == "__main__":
    main()
