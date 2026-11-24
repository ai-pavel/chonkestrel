#!/usr/bin/env python3
"""
Make the images of the cloaking, hacking and mind control systems' buttons
(button_cloaking<level>_*.png, see SystemPowerButton) for more power bars
than vanilla's systems have (three), for the Chonkestrel mod, whose cloaking
can be upgraded further (see Cloaking.durationFor).

    python3 tools/system_buttons.py ftl.dat

Vanilla's buttons grow by 12 pixels for each bar, from one to four, while
the power bars beside them are 8 pixels apart, so past vanilla's three bars
they'd tower over the bars. These grow by 8 pixels for each bar past three
instead, as the bars do, upwards, their bottom staying against the power
bars: from four bars (replacing vanilla's four-bar images, which vanilla
never uses) to eight.

Each is vanilla's four-bar one, made shorter (for four bars) or taller. The
outline and the charging stripes (which are four pixels apart) have rows
below the top removed or repeated; the button with the power icon has plain
rows removed or repeated as much above the icon as below it, so the icon
stays in the middle. The images go in the mod's img/systemUI.

It also makes the Artillery Beam's charge bar frames for five and six bars
(button_artillery_<level>.png, see Artillery.COOLDOWN_FACTORS), which the
mod's artillery goes up to: vanilla's are a frame as tall as the charge
takes (a pixel a second, 50 for one bar down to 20 for four), so these are
its four-bar one with rows of the plain middle of the frame taken out, as
many as the charge is shorter, and as many clear rows added at the top, so
the image is the same size and sits in the same place.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

from ship_variants import MOD, Dat

KINDS = ("base", "on", "off", "select2", "charging_on", "charging_off")
LEVELS = range(4, 9)
VANILLA_LEVEL = 4
# How much taller vanilla's buttons get for each bar, and how much ours do
# past vanilla's three bars (as the power bars do), and the rows of
# vanilla's four-bar one removed or repeated to make it so (below the top's
# rounded corners)
VANILLA_PER_LEVEL = 12
PER_LEVEL = 8
VANILLA_MAX_LEVEL = 3
CHUNK = (19, 31)
# The kinds with the power icon in the middle, and the plain rows above and
# below it, each repeated for half of the extra height
ICON_KINDS = {"on", "off", "select2"}
ABOVE_ICON = 25
BELOW_ICON = 50
OUT = MOD / "img" / "systemUI"

# The artillery frames: how many pixels shorter each is than the four-bar
# one (its charge of 20 seconds at four bars, 17 at five and 14 at six), and
# the plain rows in the middle of the four-bar one's frame they come out of
ARTILLERY_SHORTER = {5: 3, 6: 6}
ARTILLERY_PLAIN = (44, 54)


def extra_rows(level):
    """How many more rows a [level]-bar button has than vanilla's four-bar one
    (fewer, for four bars)."""
    vanilla_max = VANILLA_MAX_LEVEL * VANILLA_PER_LEVEL
    return vanilla_max + (level - VANILLA_MAX_LEVEL) * PER_LEVEL - VANILLA_LEVEL * VANILLA_PER_LEVEL


def resized(image, extra, kind):
    """The image, with [extra] rows repeated (or removed, if it's negative)."""
    if kind in ICON_KINDS:
        half = extra // 2
        return np.concatenate([
            stretch(image[:ABOVE_ICON + 1], half),
            stretch(image[ABOVE_ICON + 1:BELOW_ICON + 1], extra - half),
            image[BELOW_ICON + 1:],
        ], axis=0)
    top, split = CHUNK
    if extra < 0:
        return np.concatenate([image[:split + extra], image[split:]], axis=0)
    chunk = image[top:split]
    repeats = [chunk[i % len(chunk)] for i in range(extra)]
    return np.concatenate([image[:split], np.array(repeats).reshape(-1, *image.shape[1:]), image[split:]], axis=0)


def stretch(part, extra):
    """[part] with its last (plain) row repeated [extra] more times, or with
    that many of its last rows removed if [extra] is negative."""
    if extra < 0:
        return part[:len(part) + extra]
    return np.concatenate([part, np.repeat(part[-1:], extra, axis=0)], axis=0)


def main():
    dat = Dat(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    for kind in KINDS:
        vanilla = np.asarray(dat.image("img/systemUI/button_cloaking%d_%s.png" % (VANILLA_LEVEL, kind)))
        for level in LEVELS:
            image = resized(vanilla, extra_rows(level), kind)
            Image.fromarray(image).save(OUT / ("button_cloaking%d_%s.png" % (level, kind)))

    four = np.asarray(dat.image("img/systemUI/button_artillery_4.png").convert("RGBA"))
    start, _ = ARTILLERY_PLAIN
    for level, shorter in ARTILLERY_SHORTER.items():
        clear = np.zeros((shorter, *four.shape[1:]), dtype=four.dtype)
        image = np.concatenate([clear, four[:start], four[start + shorter:]], axis=0)
        Image.fromarray(image).save(OUT / ("button_artillery_%d.png" % level))
    print("Made %d button images" % (len(KINDS) * len(LEVELS) + len(ARTILLERY_SHORTER)))


if __name__ == "__main__":
    main()
