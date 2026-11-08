# Evasion and ship size

XFTL lets ships be bigger than vanilla (`<shipSize>`, which is 1 for all the
vanilla ships), and have several engines systems. Bigger ships dodge worse:
they have to put more power into their engines, and they're easier to hit,
which makes up for them having more hull (their size times their
blueprint's). This is implemented in `Ship.evasion`.

## Formula

- **Engines:** the evasion comes from an engine level, which is
  `weakest engine's power × number of engines systems / ship size`. The
  evasion for that level comes from vanilla's table (5, 10, 15, 20, 25, 28,
  31, 35%), interpolating between levels for fractional ones. An engine that's
  off, broken or hacked counts as zero power, so it loses all the engines'
  evasion.
- **Manning:** the pilot gives 5, 7 or 10% depending on their skill, as in
  vanilla. The best-manned engine does too, as long as the engine level is
  above zero.
- **Autopilot:** without a pilot, the piloting system's evasion multiplier
  applies (0, 50% or 80% depending on its level), as in vanilla.
- **Size:** all of the above is multiplied by `2 / (ship size + 1)`: 1 for
  vanilla ships, 2/3 for size 2, 1/2 for size 3 and 2/5 for size 4.
- **No engines running:** the evasion is zero, as in vanilla.
- **Cloaking:** adds `60% / ship size`, on top of that.

The FTL drive's charge rate uses the total power of the engines divided by
the ship's size instead, so a ship with one engine down still jumps (just
more slowly) rather than being stuck.

For vanilla ships (size 1, one engines system) this is all the same as vanilla.

## Maximum evasion

With everything at level 8, and a pilot and engine crew with the best skills:

| Ship                                 | Engines | Without cloaking | Cloaked              |
|--------------------------------------|---------|------------------|----------------------|
| Size 1 (vanilla)                     | 1 × 8   | **55%**          | 115% (always misses) |
| Size 2 (the Chonkestrel)             | 2 × 8   | **36%**          | **66%**              |
| Size 2, only one engines system      | 1 × 8   | 26%              | 56%                  |
| Size 2, with its second engine off   | 8 + 0   | 6% (pilot only)  | 36%                  |
| Size 3                               | 3 × 8   | 27%              | 47%                  |
| Size 3, with two engines systems     | 2 × 8   | 23%              | 43%                  |
| Size 4                               | 4 × 8   | 22%              | 37%                  |

## What this means

- A size 2 ship needs 16 power in its engines to get its best evasion,
  rather than 8, and that's still only 36%, rather than 55%.
- It's hit by 64% of shots at best, rather than 45%: about 1.4 times as
  often as a vanilla ship. With twice the hull, it can take about 1.4 times
  as much fire before being destroyed.
- Cloaking is the big difference: a cloaked vanilla ship can't be hit, but a
  cloaked size 2 ship still takes a third of the shots aimed at it.
- The Chonkestrel starts with both engines at level 1, which gives up to
  10% with a pilot and someone at the engines, rather than the vanilla
  Kestrel's 20% (with its engines at level 2).
- Losing either engine leaves a size 2 ship with only its pilot's evasion,
  which is intentionally a big penalty.
- Nothing else in the formula adds evasion (such as augments), so 100%
  isn't possible without cloaking.
