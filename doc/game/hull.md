# Hull

How much hull each ship has, and how its hull bar shows it.

## The rule

A normal ship (any vanilla one, the player's ships and the bosses) has its
blueprint's hull times its size (`Ship.maxHealth`, `Ship.hullScale`): 1 for
a normal enemy ship, and the blueprint's `shipSize` for others (2 for the
Chonkestrel and the rebel flagship).

The long and wide versions of enemy ships, and the hybrids, work it out from
the hull of their normal ship (`Ship.baseHull`: for a version, the ship it's
a version of, from `xftlVariantOf`, rather than what its own blueprint says):

| | Long | Wide | Hybrid |
|-|-|-|-|
| Times the normal ship's hull | 2.5 (`HULL_FACTOR_LONG`) | 4 (`HULL_FACTOR_WIDE`) | another 1.25 (`HULL_FACTOR_HYBRID`) |
| Points per cell of the bar | 2 | 1 | as its shape |
| Most hull (15 cells) | 30 | 45 | 15 normal, 30 long, 45 wide |

A long ship is worth two normal ones and a wide one three, but a long
ship's bar has less room (16 cells of two points, against 48 of a point for
a wide one), so a long ship is weaker than a wide one. Both are a bit tougher than
the two or three normal ships they're worth, as they're one target. Hybrids
are tougher than the other ships their size. It's rounded up, then capped
at what the bar shows evenly, with a cell kept for bosses (see below).

| Normal ship's hull | Normal | Long | Wide | Hybrid | Long hybrid | Wide hybrid |
|-|-|-|-|-|-|-|
| 7 (scouts) | 7 | 18 | 28 | 9 | 22 | 35 |
| 8 (Rock scout) | 8 | 20 | 32 | 10 | 25 | 40 |
| 9 (fighters, bombers) | 9 | 23 | 36 | 12 | 29 | 45 |
| 10 (Rebel and Rock fighters) | 10 | 25 | 40 | 13 | 30 | 45 |
| 11 (Mantis bomber) | 11 | 28 | 44 | 14 | 30 | 45 |
| 12 (Rock assault) | 12 | 30 | 45 | 15 | 30 | 45 |
| 14 (Rebel elite) | 14 | 30 | 45 | 15 | 30 | 45 |

## Rock ships and hybrids

Rock ships are tougher in vanilla (a Rock scout has 8 where the others have
7, a Rock fighter 10, a Rock assault ship 12 where the other bombers have 9),
and that carries through to their long and wide versions, as it's their
normal ship's hull that's multiplied.

A hybrid has the more of its two races' ships' hull (`tools/hybrid_ships.py`),
so a hybrid with a Rock half starts from the Rock ship's, and its bigger
versions from that.

## The hull bar

A normal or long ship's window has room for 16 cells of hull
(`Ship.HULL_BAR_CELLS`), each showing as many points as the ship's size: a
normal ship's are a point each, a long ship's two (drawn split in two). A
wide ship's window is twice as wide, so its bar has room for 48 cells
(`HostileShipUI.WIDE_HULL_BAR_CELLS`), of a point each. The bar's image
has 22 cells, short for the first half and tall for the second, with a
bevelled corner on the first of each; the longer bar keeps that shape, with
short cells for its first half and tall ones after, bevelled only at the
start and where they get taller. If a ship has more hull than its bar holds, each
cell shows more points.

The player's hull bar works the same way, with each cell showing as many
points as the player ship's size.

## Room for bosses

Versions and hybrids have at most 15 × their size in hull
(`Ship.MAX_ORDINARY_HULL_CELLS`): 30 for a long ship and 45 for a wide one,
short of their bars' 32 and 48. That keeps the end of the bar for bosses: a
boss in a wide ship's window can have the whole bar, **48 hull**, more than
any wide ship.

The bosses there are now (the rebel flagship) are size 2, with 20 or 22 hull
in each stage's blueprint, so 40 or 44: more than a size-2 bar's 32, so each
cell of theirs shows three points.

Vanilla's special normal-sized ships with lots of hull (the ghost ship, the
elite Rebel cruisers) keep theirs; their bar's cells just show more points
each.
