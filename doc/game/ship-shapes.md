# Ship shapes: bigger enemy ships

Big player ships like the Chonkestrel (size 2) fight several vanilla ships
at once (see `Wingman`, and `opponentShips` in [lore.md](lore.md)). They can
also fight bigger versions of the vanilla ships: twice the size, either
**long** or **wide**. Size 3 is for the bosses (**colossal**, see
[bosses.md](bosses.md)).

## Shapes

A hostile ship is drawn nose up in its window on the right of the screen,
so its shape sets the size of its window (`ShipShape`, set with
`<xftlShape>` in its blueprint):

| Shape   | Size | Window                                     | Places in the row |
|---------|------|--------------------------------------------|-------------------|
| normal  | 1    | as in vanilla                              | 1                 |
| long    | 2    | 260px taller (`LONG_WINDOW_EXTRA_HEIGHT`)  | 1                 |
| wide    | 2    | twice as wide                              | 2                 |
| colossal| 3    | both: 260px taller and twice as wide       | 2                 |

The windows are stretched by repeating a plain part of the middle of the
window image (`HostileShipUI`), and laid out from the right, each taking up
as many places as its shape.

A ship with several shields systems shows a group of shield bubbles for
each, side by side, and one with several Zoltan shields a bar for each.
If a ship has too many systems to fit along the bottom of its window, their
icons are put closer together.

## Which fights there can be

The ships in a fight have to fit on the screen (`FightPlan`): their windows
take up at most 3 places, and a wide ship's takes up two. So a fight is one
of:

- 1, 1+1, 1+1+1 (as before);
- 2 (long or wide);
- 2+1 (long or wide);
- 2+2 (two long ones, or a long and a wide one: two wide ones take up four
  places);
- with three ships, only long 2s: 2+1+1, 2+2+1 or 2+2+2 (a wide one and two
  others take up four places).

## How many ships, and how big

For player ships of size 2 or more, each fight is planned when an event
loads its enemy ship (`InGameState.planFight`, `FightPlan.forSector`). A
fight has a strength: the sum of its ships' `ShipShape.strength`, 1 for a
normal ship, 2 for a long one (Д below) and 3 for a wide one (Ш). The
strength depends on the sector (numbered as in a sixteen-sector game, see
`GameMap.nominalSectorNumber`) and on which third of it the beacon is in,
from the start beacon to the exit (`FightPlan.STRENGTH_BY_SECTOR`). The
fight is one of the fights of that strength that fit on the screen, at
random:

| Strength | Fights                    |
|---------:|---------------------------|
|        1 | 1                         |
|        2 | 1+1, 1Д                   |
|        3 | 1+1+1, 1Д+1, 1Ш           |
|        4 | 1Д+1Д, 1Д+1+1, 1Ш+1       |
|        5 | 1Д+1Д+1, 1Ш+1Д            |
|        6 | 1Д+1Д+1Д                  |

Long ships only turn up from the last third of sector 2
(`FightPlan.LONG_FROM_SECTOR`), and wide ones from sector 5
(`WIDE_FROM_SECTOR`), so a strength's fights without them are used until
then:

| Sector       | Start | Middle | End | So, by the end              |
|--------------|------:|-------:|----:|-----------------------------|
| 1            |     1 |      1 |   1 | one ship at a time          |
| 2            |     1 |      2 |   2 | 1+1, then 1Д or 1+1         |
| 3            |     2 |      3 |   3 | 1Д+1 or 1+1+1               |
| 4            |     3 |      3 |   4 | 1Д+1Д or 1Д+1+1             |
| 5            |     3 |      4 |   4 | wide ones: 1Ш, 1Ш+1         |
| 6            |     4 |      4 |   5 | 1Д+1Д+1 or 1Ш+1Д            |
| 7            |     4 |      5 |   5 |                             |
| 8 (blockade) |     5 |      5 |   5 |                             |
| 9-12         |     5 |      5 |   6 | 1Д+1Д+1Д                    |
| 13-15        |     6 |      6 |   6 |                             |

Each sector starts out a little easier than the last one ended, and builds
up to its exit. A player ship fights at most `<opponentShips>` ships at
once, and at least `<opponentShipsAtStart>` (the Chonkestrel's is 1). Only
ships with a bigger version can be one (see below); the rest stay normal,
and a ship as big as the player's (such as the flagship) is still alone.
Hybrids only turn up after the blockade, see
[hybrid-ships.md](hybrid-ships.md).

Vanilla-sized player ships fight one ship at a time, as in vanilla
(`Wingman.rampedEnemyShipCount`, which also gives the number of ships
after loading a saved game in the middle of a fight).

## Bigger versions

A bigger version of a ship is a ship blueprint of its own, with:

```xml
<xftlVariantOf>FED_SCOUT</xftlVariantOf>
<shipSize>2</shipSize>
<xftlShape>long</xftlShape>
```

Every bigger version (the vanilla ships', the hybrids' and the bosses')
has an empty hold, a room with no system kept for its bulwarks (the
enemies' twin and triple anti-personnel drones, see
[drone-sizes.md](drone-sizes.md#bulwarks)): 2x2 on a long ship, 3x3 on a
wide one (or 2x2 if there's no run of new rooms that wide), in rows (or
columns) put in after its new rooms, with the rest of those rows made
into small empty rooms (`ship_variants.with_hold`, which the hybrids'
tool uses too, and `boss_ships.place_systems`, which keeps one of a
boss's new rooms empty). A long ship is no taller than its window allows
(`MAX_LONG_HEIGHT`, 580 pixels): its tail's made shorter first, taking out
the rows below the rooms one at a time, each the one most like the row
under it (so where it's straight), and once that's as short as it goes,
its nose, the same way above the rooms (`ship_variants.tail_rows`). A long
ship still too tall with its hold goes without one: 9 long hybrids, the
Zoltan-Rock, Rock-Rebel and Zoltan-Mantis ones, and the Engi-Crystal,
Rock-Mantis, Crystal-Zoltan, Crystal-Mantis, Rock-Zoltan and
Lanius-Zoltan bombers. Only one has no long version at all, as it's too
tall even without one: the Engi-Zoltan bomber.

Whenever a fight would have that ship, a bigger one can be its version of
that shape (`BlueprintManager.shipVariant`). A wide ship takes up as much
of the screen as two, so it's the strongest (`ShipShape.strength`): a long
version is worth two of the ship, and a wide one three. When it's built
(`ShipGenerator`), it gets that many times more power for its systems, and
each of its weapons systems gets weapons for its own slots, with its own
power.

Each faction's bigger ships play up what it's good at. Their new systems:

| Faction    | Ship             | Long version                                       | Wide version                                                         |
|------------|------------------|----------------------------------------------------|----------------------------------------------------------------------|
| Federation | Federation Scout | weapons, shields, engines                          | medbay, drones, oxygen, two weapons, shields, engines                |
| Rebel      | Rebel Fighter    | longer nose: weapons                               | wider nose: two weapons, drones, oxygen, shields, engines            |
| Engi       | Engi Scout       | drones, shields                                    | drones, shields, oxygen                                              |
| Zoltan     | Zoltan Fighter   | shields, a Zoltan shield                           | two weapons, shields, drones, medbay, oxygen, engines, two Zoltan shields |
| Mantis     | Mantis Fighter   | a 2x4 teleporter (for its own), weapons, engines   | a 4x2 teleporter (for its own), two weapons, shields, engines, oxygen |
| Slug       | Slug Interceptor | two mind control systems, shields                  | widened on both sides of its tail: teleporter, mind control, shields, oxygen |
| Rock       | Rock Scout       | weapons, shields                                   | two weapons, two shields, medbay, oxygen, engines                    |
| Crystal    | Crystal Scout    | shields, weapons                                   | two shields, weapons, medbay, oxygen                                 |
| Lanius     | Lanius Scout     | two hacking systems, shields, weapons              | mind control, hacking, shields (no oxygen: they don't breathe)       |
| Automated  | Auto Scout       | weapons, shields                                   | two weapons, drones, shields                                         |

The rest of the enemy ships (apart from bosses and the special ships from
events) have their versions made automatically (`auto` in `SHIPS`, see
`auto_config`): the cut is the line in the middle half of the ship that
leaves no bits of hull hanging off, has room for new rooms, and whose pixels
change least along it; the new rows (or columns) get rooms wherever the hull
is solid, and the faction's systems (`FACTION_SYSTEMS`) go in the ones
nearest the middle. That covers the bombers, fighters and assault ships, and
the pirates' versions of the ships, which have layouts of their own. The
Mantis Bombers and the pirate Mantis Fighter are widened down their middle (the vanilla one's
grid is cut a room to the left of its image, where no rooms are in the way),
and the Slug Interceptors, whose tail is down their middle, on both sides of
it (`second`: a second cut, with its own rooms).

A long ship has at most ten systems (`MAX_LONG_SYSTEMS`), as many icons as
fit along the bottom of its window: past that, it leaves out the new systems
it has the most of already, but always keeps its cloaking and teleporter.

Every bigger version's hull is worked out by the game from the vanilla
ship's: two and a half times it for a long one and four times for a wide one
(see doc/game/hull.md); the hull in its own blueprint (`HULL` and `MIN_HULL` in
the tool) isn't used. The
automatic versions of each faction's ships get what it's known for
(`FACTION_EXTRAS`):

- Mantis: a big teleporter (up to 4x4, as big as the hull leaves room for),
  more crew (8 to 12 on a long ship, 12 to 16 on a wide one) and extra
  medbays to heal them. A ship can have several medbays, and its crew go to
  whichever one they can (`Ship.medbaySystems`).
- Rock: a wide teleporter, and a third of the crew are Crystals (a second
  `crewCount`).
- Crystal: a smaller teleporter than the Mantis.
- Slug: a teleporter, and an anti-bio beam, which the ship always has
  (`<xftlWeapon name="BEAM_BIO"/>`, see `ShipBlueprint.requiredWeapons`).
- Engi: hacking, a second drones system, and twice the drone parts. Every
  drones system gets its own drones, different ones where it can.
- Zoltan: two (long) or three (wide) Zoltan shields, as do the hand-made
  Zoltan ships; the Federation, Engi and Crystal bombers get one.

New rooms are never split into single cells. A hostile ship with several
Zoltan shields shows each one's bar, as in vanilla, one under the other,
below its shield bubbles.

Every wide version has cloaking too, and a long one has a room for it,
which it gets half the time (`ShipGenerator.VARIANT_CLOAKING_CHANCE`; the
Auto Scout has one already). The Mantis ships' bigger teleporters send more
of their boarders over at once, and they have more crew (6 to 10 on a long
one, 10 to 14 on a wide one). As vanilla ships can only have eight main
systems, the blueprints set `<maxSystems>` to fit all of theirs.

A long version has five weapon mounts, and a wide one six; bigger ships
(bombers, the Rigger, the Light-Cruiser and the Rock Fighter) have six and
seven, and the biggest (the Mantis Bomber, Slug Assault, Rock Assault and
Auto-Assault) seven (eight are left for future bosses of size 3): the vanilla ship's first four (the only ones
vanilla uses), and new ones on the part of the hull that was put in, down
its sides on a long ship and along its top on a wide one. Each new one is
as far in from the edge of the hull as a vanilla mount on that side, so it
sits on the hull the same way. With several weapons systems, the mounts are
shared out between them in order (`Ship.assignWeaponHardpoints`).

The pirate and elite versions of these ships that share their layout (such
as the pirate Federation Scout and the elite Rebel Fighter) get their own
bigger versions too, with their own colours. A long version has twice the
reactor, missiles and drone parts, and twice the crew (up to eight); a wide
one has four columns of rooms put in, three times the reactor, missiles and
drone parts, and three times the crew (up to twelve). The game gives them
more hull than the vanilla ship (see doc/game/hull.md), and each cell of their
hull bar is two or three points. A new
drones system gets the standard drones if the ship doesn't have its own.

Their images, layouts and blueprints are made from the vanilla ships' by
`tools/ship_variants.py` (it needs Pillow), and the blueprints go between
the markers in the mod's `autoBlueprints.xml.append`:

```sh
python3 tools/ship_variants.py ftl.dat                    # make them all
python3 tools/ship_variants.py ftl.dat --analyse rock_scout
python3 tools/ship_variants.py ftl.dat --explore rock_scout out/
python3 tools/ship_variants.py ftl.dat --preview out/
```

Each ship's version is set up in its `SHIPS` table: the line of the room
grid it's cut along, how many rows or columns are put in, and the new rooms
and their systems (and `drop`, for the vanilla systems a new room takes
over, like the Mantis teleporter, and `crew`). The hull is stretched by repeating the row (or column)
of pixels just before the cut, so its edges carry straight on, and the
rooms cover the inside of the hull, so only the edges show. Where a ship's
middle isn't on a line of the grid, the image is cut down its middle
(`shift`) so it stays symmetric. `--explore` draws the hull stretched at
every line (and half way between), to pick a cut without artefacts;
`--preview` draws every version with its rooms. The doors across the cut
are taken out, and the new rooms get a door to each room next to them; the
script checks every room can still be reached. The gibs are the new hull
cut into quarters.

Ships whose hulls don't stretch cleanly don't get that version. The rest of each faction's ships can be
added to the table the same way.

## Rewards

A long ship is worth two of the ship it's a version of, and a wide one
three. When it's destroyed (or its crew are killed), its event's rewards
are given as usual, and then the rest of its wreckage has as much again
for each extra ship it's worth: the same event's resources and items,
rolled again (`BigShipRewards`). 15% of the time for a long ship, and 30%
for a wide one (`BigShipRewards.ITEM_CHANCE` for each extra ship), there's
a weapon or an augment in it too.
This is shown before the event, as a message of its own.

## Testing

`InGameState.debugFightShapes` starts a fight with ships of the given
shapes, for the screenshot or video directors.

To see every bigger ship in the game, take screenshots of them:

```sh
XFTL_SCREENSHOTS=$PWD/dist/ship-screenshots-shape XFTL_SCREENSHOTS_SET=shape XFTL_RANDOMIZE=1 make run
```

This runs the screenshot generator (see [screenshots.md](../tools/screenshots.md))
with `XFTL_SCREENSHOTS_SET=shape`: a fight with the long and wide version of
each ship (or two long ones, if it doesn't have a wide one), in the second
act, saved in `dist/ship-screenshots-shape`. The player fights each pair (firing,
with its drones, hacking and mind control, see `VideoDirector.BattleDriver`)
and they board it (into random rooms), for 5 seconds before each screenshot; it's patched up
between fights. The whole run is recorded into `shape.mp4` there too, with
every frame a thirtieth of a second of the game, as the video recorder
does. They aren't for the site, so they
don't go in the site's screenshots. The ships are in
`ScreenshotDirector.BIGGER_SHIPS`, which needs a new ship added when the
script makes versions of it.
