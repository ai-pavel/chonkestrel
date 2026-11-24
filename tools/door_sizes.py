#!/usr/bin/env python3
"""
Make the wide doors' sprites (see doc/game/drone-sizes.md): a door two or
three cells long, one door rather than two or three side by side, where
the bulwarks walk between the bigger enemies' big empty rooms (see
tools/wide_passages.py, which marks them in the layouts). Each is
vanilla's door, as long as its cells:

- img/effects/xftl_door_sheet_x<N>.png: vanilla's door_sheet.png, five
  frames of opening (across) for each of its five levels (down), each
  frame a cell wide and N long. Each half of the door keeps its ends as
  vanilla's, its middle repeated to its length, and opening goes as far,
  for its length, as vanilla's does: each half shrinks into its end of
  the doorway.
- img/effects/xftl_door_highlight_x<N>.png: vanilla's door_highlight.png
  round it, a frame for each frame of opening (as Door.drawHighlight cuts
  vanilla's), across.

They're drawn upright, as vanilla's are (a door on a vertical wall); the
game turns them for the others.

    python3 tools/door_sizes.py ftl.dat

It needs Pillow (pip3 install pillow).
"""
import sys

from PIL import Image

from ship_variants import MOD, Dat

CELL = 35
FRAMES = 5
LEVELS = 5
SIZES = (2, 3)
OUT = MOD / "img" / "effects"

# Vanilla's door (see Door's HALVES_CLOSED_AT): its halves run from this
# row, and the mirror of it at the bottom, to the middle, where they meet
CLOSED_AT = 17
# Its highlight, a couple of rows round the door
HIGHLIGHT_MARGIN = 1


def opaque(image, x, y):
    return image.getpixel((x, y))[3] > 0


def panel_ends(frame):
    """
    The rows of vanilla's door in [frame] (a cell's): where its top half
    starts and ends, and its bottom half starts and ends.
    """
    column = [y for y in range(CELL) if any(opaque(frame, x, y) for x in range(CELL))]
    top_start, bottom_end = column[0], column[-1]
    top_end = top_start
    while top_end + 1 <= CLOSED_AT and opaque_row(frame, top_end + 1):
        top_end += 1
    bottom_start = bottom_end
    while bottom_start - 1 >= CLOSED_AT and opaque_row(frame, bottom_start - 1):
        bottom_start -= 1
    return top_start, top_end, bottom_start, bottom_end


def opaque_row(frame, y):
    return any(opaque(frame, x, y) for x in range(CELL))


def stretch_rows(rows, length):
    """
    [rows] (a half of the door, its first and last rows its ends) made
    [length] rows long: its ends as they are, its middle repeated.
    """
    middle = rows[1:-1] or rows[:1]
    return [rows[0]] + [middle[i % len(middle)] for i in range(length - 2)] + [rows[-1]]


def wide_frame(frame, size):
    """Vanilla's door [frame] (a cell's) as a door [size] cells long."""
    height = CELL * size
    out = Image.new("RGBA", (CELL, height), (0, 0, 0, 0))
    top_start, top_end, bottom_start, bottom_end = panel_ends(frame)
    rows = [[frame.getpixel((x, y)) for x in range(CELL)] for y in range(CELL)]

    # How far along each half is from its end: as far, for its length, as
    # vanilla's
    middle = (height - 1) / 2
    scale = (middle - top_start) / (CLOSED_AT - top_start)
    top_length = round((top_end - top_start + 1 - 1) * scale) + 1
    bottom_length = round((bottom_end - bottom_start + 1 - 1) * scale) + 1
    # (closed, they meet in the middle, as vanilla's do)
    if top_end >= CLOSED_AT:
        top_length = int(middle) - top_start + 1
        bottom_length = (height - 1 - (CELL - 1 - bottom_end)) - (int(middle) + 1) + 1

    top = stretch_rows(rows[top_start:top_end + 1], top_length)
    bottom = stretch_rows(rows[bottom_start:bottom_end + 1], bottom_length)
    bottom_at = height - (CELL - 1 - bottom_end) - len(bottom)
    for i, row in enumerate(top):
        for x, pixel in enumerate(row):
            out.putpixel((x, top_start + i), pixel)
    for i, row in enumerate(bottom):
        for x, pixel in enumerate(row):
            out.putpixel((x, bottom_at + i), pixel)
    return out


def wide_highlight(highlight, door):
    """
    Vanilla's highlight round the wide [door] frame: its rounded ends at
    the door's ends, its full width along the halves, and cut a row past
    their inner ends (as Door.drawHighlight cuts vanilla's).
    """
    out = Image.new("RGBA", door.size, (0, 0, 0, 0))
    rows = [[highlight.getpixel((x, y)) for x in range(CELL)] for y in range(CELL)]
    filled = [y for y in range(CELL) if any(p[3] > 0 for p in rows[y])]
    first, last = filled[0], filled[-1]
    full = rows[CLOSED_AT]
    height = door.height
    on = [any(opaque(door, x, y) for x in range(CELL)) for y in range(height)]
    for y in range(height):
        near = any(on[max(0, y - (2 + HIGHLIGHT_MARGIN - 1)):y + 2 + HIGHLIGHT_MARGIN])
        if not near:
            continue
        # (the rounded ends, as vanilla's, at the door's ends)
        if y < CLOSED_AT:
            row = rows[y] if y < first + 3 else full
        elif y > height - 1 - CLOSED_AT:
            row = rows[CELL - (height - y)] if height - y <= CELL - last + 2 else full
        else:
            row = full
        for x, pixel in enumerate(row):
            if pixel[3] > 0:
                out.putpixel((x, y), pixel)
    return out


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    dat = Dat(sys.argv[1])
    sheet = dat.image("img/effects/door_sheet.png").convert("RGBA")
    highlight = dat.image("img/effects/door_highlight.png").convert("RGBA")
    OUT.mkdir(parents=True, exist_ok=True)
    for size in SIZES:
        doors = Image.new("RGBA", (CELL * FRAMES, CELL * size * LEVELS), (0, 0, 0, 0))
        highlights = Image.new("RGBA", (CELL * FRAMES, CELL * size), (0, 0, 0, 0))
        for level in range(LEVELS):
            for f in range(FRAMES):
                frame = sheet.crop((f * CELL, level * CELL, (f + 1) * CELL, (level + 1) * CELL))
                wide = wide_frame(frame, size)
                doors.paste(wide, (f * CELL, level * CELL * size))
                if level == 0:
                    highlights.paste(wide_highlight(highlight, wide), (f * CELL, 0))
        doors.save(OUT / ("xftl_door_sheet_x%d.png" % size))
        highlights.save(OUT / ("xftl_door_highlight_x%d.png" % size))
    print("Made the doors %s cells long" % " and ".join(map(str, SIZES)))


if __name__ == "__main__":
    main()
