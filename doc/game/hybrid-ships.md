# Hybrid ships

A hybrid ship is glued together from two races' ships: the front of one and
the back of the other, crewed half by each race, with what both races are
known for. There's one for every pair of the nine races with crew, both
ways round, apart from the Federation and the Rebels (both human), made from
their scouts (or fighters), and from their bombers (or the like: the Rebel
Rigger, Slug Light-Cruiser and Rock Fighter, see `CLASSES`): 140 ships of
size 1. The scouts are named `XFTL_HYBRID_<front>_<back>` (listed in
`XFTL_HYBRID_SCOUTS`), and the bombers `XFTL_HYBRID_BOMBER_<front>_<back>`
(in `XFTL_HYBRID_BOMBERS`), with a list of both lists, `XFTL_HYBRIDS`. For
example, `XFTL_HYBRID_FED_SLUG` is the front of a Federation Scout on the
back of a Slug Interceptor (a "Federation-Slug Hybrid Scout"). Each half's
weapons and drones are its own ship's (or its race's).

A hybrid has:

- the front ship's rooms (and hull) in front of a line of its grid, and the
  back ship's behind a line of its, lined up on the same grid (a room the
  line goes through keeps its part on that side), with doors
  between the rooms either side of the join (and to any of the rooms that
  had no doors in the vanilla ship);
- each half's systems in its own rooms. A system the front half has
  already becomes the back race's own instead (`SIGNATURE`: mind control
  for the Slugs, a teleporter for the Mantis and Crystals, hacking for the
  Lanius, drones for the Engi, cloaking for the Federation...), and the
  systems every ship needs go in rooms without systems if neither half has
  them (or in place of the least needed ones, `LEAST_NEEDED`);
- each half's weapon mounts;
- no rooms of one cell (`merge_small_rooms`): one (from a vanilla ship, or
  cut down at the join) is joined to a room next to it that's one cell
  wide along that side, or else grows into the hull next to it;
- its crew half one race and half the other (two `crewCount`s), as many as
  the bigger of the two ships has;
- a class saying what it's made from and what kind of ship its front is,
  such as "Rock-Zoltan Hybrid Scout";
- both races' weapons to pick from, both races' drones if it has a drones
  system, both races' augments (such as Slug Gel), and the bigger of the
  two ships' hull and reactor.

The halves are lined up by whole rooms (so the rooms are on one grid), so
their sides only meet at the join by chance: most miss each other by a few
pixels, leaving a gap or a little step. Where they miss by up to 20 pixels
(`FIT_MAX`), the narrower half's rows nearest the join are stretched out from
their middle, more the nearer the join, so its side bends out to meet the
other's (`fit_join`); the cloak the same way. A bigger difference is left as
a step, which reads as one half being wider.

The join is finished off (`blend`), so where one half is wider than the
other, the part sticking out doesn't look cut off in midair: its cut edge
gets a dark outline (as every vanilla hull's edges have), the hull's glow
is made again round the join (each half's glow otherwise stops in a
straight line there), and bits of hull left on their own near the join,
with no rooms on them, are taken out. On the cloak (`close_cloak_lines`),
the lines that stop at the join are joined to the nearest ones on the
other side, along it (or taken along it to the nearest line), and near the
join, the faint fill inside the hull goes smoothly from the front's to the
back's, and the glow round the lines is made again, as each half's fade out
where they were cut.

A hybrid is no taller than the tallest vanilla ship, the Rebel Fighter
(`MAX_HEIGHT`, 509 pixels), so it fits in the enemy's window. The Rebel
Fighter's long tail makes some of the ones with its back taller than that
(such as the Rock-Rebel one): their tails are made shorter, taking out the
rows below the rooms one at a time, each the one most like the row under
it, so it's where the tail's straight and doesn't leave a join.

They're made by `tools/hybrid_ships.py` from the vanilla ships in ftl.dat,
into the mod's images and layouts, and their own part of its
`autoBlueprints.xml.append`, with its lists:

```sh
python3 tools/hybrid_ships.py ftl.dat                  # make them
python3 tools/hybrid_ships.py ftl.dat --search          # list them
python3 tools/hybrid_ships.py ftl.dat --preview out/   # draw them
```

For each pair of races, it tries every pair of lines to cut their ships
along, and every way of lining their grids up, and picks the one whose
hulls meet best (`best_hybrids`): with the edges of the hulls closest
together at the join, in pixels, and worse for each room a line goes
through, or for leaving one ship only one row (`CROSSING_PENALTY`), as long
as every room can be reached and there's room for the systems every ship
needs. `--search` lists what it picked, with how well each one's hulls meet.

## Control drones

Something has taken over the races these ships come from (see lore.md), and
it keeps watch from inside: every hybrid always has anti-personnel and
system repair drones, taking turns (`CONTROL_DRONES`): one of each on a
normal hybrid, two on a long one and three on a wide one, and more if one of
its races is the Engi (`CONTROL_DRONE_RACES`: one more on a normal hybrid,
two on a bigger one), up to five of each kind and eight in all. A ship with
more than one drones system's worth (8 power) gets more drones systems
(`ensure_drones_systems`); any that still don't fit are left out. They're `<xftlDrone>`
elements in its blueprint (see `ShipBlueprint.requiredDrones`), which the
ship generator puts in its drones systems before the random drones, with
more power for them if they need it. Every hybrid has a drones system for
them: one without gets one, in a room without a system or in place of its
least needed one (`LEAST_NEEDED`), and a long one keeps it when it's cut
down to 10 systems. It gets enough drone slots and parts for them too.
Some of a bigger or Engi hybrid's anti-personnel drones are bulwarks:
big ones standing guard in its empty hold (`ShipGenerator.withBulwarks`,
see [drone-sizes.md](drone-sizes.md#bulwarks) and
[ship-shapes.md](ship-shapes.md#bigger-versions)).

A hybrid without a doors system gets a place for one, in the smallest room
without a system (never the hold), if it has one to spare
(`ensure_doors_system`): it's optional, and the later enemies always get
it, to keep the player's boarders out (`ShipGenerator.lateDoors`). Doors
are no longer the first system dropped to make room for others
(`LEAST_NEEDED`).

## Bigger hybrids

Every hybrid has a long and a wide version too (280 more ships, named like
`XFTL_HYBRID_FED_SLUG_LONG`), made by `bigger_hybrid` the way
`tools/ship_variants.py` makes the automatic versions of the vanilla ships
(see doc/game/ship-shapes.md): the hull stretched where it changes least, new
rooms wherever it's solid, with both races' systems in them (taking turns,
`FACTION_SYSTEMS`), cloaking, a second oxygen system on a wide one, and the
front race's extras (`FACTION_EXTRAS`, such as a Mantis teleporter), 5 or 6
weapon mounts for the scouts and 6 or 7 for the bombers. The repeated glow
beside the stretched hull is made again (`clean_band`). The line it's cut
along, and the row (or column) of pixels repeated there, keep away from the
join and from dark parts of the hull (`bad_line`, `clean_source` in
ship_variants.py), which would otherwise leave dark stripes across it. A long one keeps to
10 systems, leaving out its least needed ones.

## In the game

An enemy ship that hybrids are made from (one of the scouts or bombers in
`CLASSES`, of its normal size) is sometimes one of its hybrids instead: one
with its front or its back, picked at random (`ShipGenerator.hybridFor`,
from the list `XFTL_HYBRIDS_OF_<ship>`). That's never in the first act,
and 85% of the time in the second (`HYBRID_CHANCE_ACT_1` and `_2`), so
they're the usual enemies after the blockade. In act 1, the only hybrids
are the omens' (`XFTL_JOINED_SHIP`, see [lore.md](lore.md)). A long or wide
ship in a fight is a bigger hybrid even more often in the second act: 95%
of the time (`BIG_HYBRID_CHANCE_ACT_2`). Special ships from
events, bosses and pirates stay as they are, as do the
screenshots' and videos' ships.

Act 2's hostile sectors are the hybrid sectors, one for each pair of races
(see [sectors.md](sectors.md#the-hybrid-sectors)). In those, three in four
of the ships that can be hybrids are the sector's pair's (a scout one of
their scouts, a bomber one of their bombers); the rest are other hybrids,
or vanilla ships, fewer and fewer through act 2 (one in five at its start,
one in fifty at its end).

## Trying them

`XFTL_SCREENSHOTS_SET=hybrid` takes screenshots of fights with two hybrids at
a time, going through them all (as `XFTL_SCREENSHOTS_SET=shape` does for the
bigger ships; see [screenshots.md](../tools/screenshots.md)), recording it
into `hybrid.mp4` next to them too. `XFTL_SCREENSHOTS_SET=mixed` does the
same for the long and wide hybrids: a fight with each of every 4th hybrid's
long and wide versions.
