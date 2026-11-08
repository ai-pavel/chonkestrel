# Event statistics

How many events the game has: the vanilla ones (from `ftl.dat`, including
Advanced Edition's), and the ones the Chonkestrel mod
(`mods/chonkestrel`) adds. These were counted from the data files
on 2026-10-20, so they'll go out of date as events are added. Since then,
six act 2 sectors (the Assimilated Core, the Graveyard of Giants, the Ion
Storm Belt, the Splice, the Datavaults and the Choir) and their 89 events
have gone, replaced by the hybrid sectors, which reuse the vanilla
sectors' events: the totals below still count those events.

The mod's events come in three groups:

- **New events for the vanilla sectors** (the `XFTL_MORE_*` lists, in the
  `events_*.xml.append` and `dlcEvents_anaerobic.xml.append` files), for
  ships of every size.
- **Campaign events** (`events.xml.append`), for the Blockade Line and the
  five act 2 sectors, for ships of every size. See [lore.md](lore.md).
- **Events for size 2 ships** (`newEvents.xml.append`), which only appear
  for big player ships like the Chonkestrel.

## Totals

|                                 | Vanilla | New, vanilla sectors | New, campaign | New, size 2 | All new |
|---------------------------------|--------:|---------------------:|--------------:|------------:|--------:|
| Beacon events                   |     258 |                   46 |            57 |          25 | **128** |
| Named events                    |     442 |                   46 |            63 |          25 |     134 |
| Event lists                     |     286 |                   35 |            14 |          10 |      59 |
| Text lists (alternative texts)  |     125 |                    1 |            12 |           5 |      18 |
| Ship specs                      |     136 |                   10 |            13 |           6 |      29 |
| Choices                         |   1,171 |                  117 |           119 |          47 |     283 |
| Texts                           |   3,945 |                  303 |           357 |         146 |     806 |

- **Beacon events** are the events that can be what's at a beacon: the
  events that sectors list, directly or through event lists. This is the
  best measure of variety. The rest are the outcomes of choices and fights,
  and parts of quests.
- **Named events** are all the top-level `<event name="...">` elements,
  including outcomes and quest steps.
- Choices and texts include the ones inside every event, outcome and ship.

So the mod adds about 50% more beacon events than vanilla has (128 to 258),
and about 40% more for ships of every size (103).

## Per sector

The expected number of events placed in each sector type (from the sectors'
`min` and `max` counts, which XFTL doubles when `max` is more than one, for
its bigger maps), and how many of them are new. A sector has about 45
beacons, so not all of the events get used, and leftover beacons get filler
events.

| Sector                  | Act 1, size 1 | Act 1, size 2 | Act 2, size 1 | Act 2, size 2 |
|-------------------------|---------------|---------------|---------------|---------------|
| CIVILIAN_SECTOR         | 46 (0.5 new)  | 47 (1 new)    | —             | —             |
| ENGI_SECTOR             | 46 (0.5 new)  | 46 (1 new)    | —             | —             |
| ENGI_HOME               | 48 (0.5 new)  | 49 (1 new)    | 51 (3 new)    | 56 (8 new)    |
| ZOLTAN_SECTOR           | 51 (0.5 new)  | 52 (1 new)    | —             | —             |
| ZOLTAN_HOME             | 52 (0.5 new)  | 52 (1 new)    | 54 (3 new)    | 60 (8 new)    |
| PIRATE_SECTOR           | 44 (0.5 new)  | 44 (1 new)    | —             | —             |
| REBEL_SECTOR            | 46 (0.5 new)  | 46 (1 new)    | —             | —             |
| REBEL_SECTOR_MINIBOSS   | 46 (0.5 new)  | 47 (1 new)    | —             | —             |
| MANTIS_SECTOR           | 44 (0.5 new)  | 45 (1 new)    | —             | —             |
| MANTIS_HOME             | 46 (0.5 new)  | 46 (1 new)    | 48 (3 new)    | 53 (8 new)    |
| ROCK_SECTOR             | 48 (0.5 new)  | 48 (1 new)    | —             | —             |
| ROCK_HOME               | 50 (0.5 new)  | 50 (1 new)    | 52 (3 new)    | 58 (8 new)    |
| LANIUS_SECTOR           | 45 (0.5 new)  | 46 (1 new)    | —             | —             |
| NEBULA_SECTOR           | 44 (0.5 new)  | 44 (1 new)    | 46 (3 new)    | 50 (6 new)    |
| SLUG_SECTOR             | 52 (0.5 new)  | 52 (1 new)    | 54 (3 new)    | 58 (6 new)    |
| SLUG_HOME               | 53 (0.5 new)  | 54 (1 new)    | 56 (3 new)    | 58 (6 new)    |
| CRYSTAL_HOME            | 56 (0.5 new)  | 56 (0.5 new)  | 59 (3 new)    | 59 (3 new)    |
| XFTL_BLOCKADE           | 40 (16 new)   | 43 (19 new)   | —             | —             |
| XFTL_FEDERATION_REMNANT | —             | —             | 36 (17 new)   | 42 (23 new)   |
| XFTL_ANVIL_YARDS        | —             | —             | 38 (19 new)   | 42 (23 new)   |
| XFTL_HYBRID_* (28)      | —             | —             | 42 (36 new)   | 45 (39 new)   |

The Blockade Line is the last sector of act 1, and the campaign's sectors
only appear in act 2. The factions' own vanilla sectors are only in act 1,
and the 28 hybrid sectors take their place in act 2 (see
[sectors.md](sectors.md#the-hybrid-sectors)), with their own events
(`XFTL_HYBRIDSEC_*`, about 50, in pools every one of them has and one for
each race), and only a few of their two races' vanilla ones. The game starts out mostly vanilla, with more of the
new events further in (`xftlSectors` on the sectors' events, see
[lore.md](lore.md)). Of a vanilla sector's beacons (on the generated maps,
counted on 2026-10-23), these are new:

| Sectors      | Vanilla ship | Big ship |
|--------------|-------------:|---------:|
| 1-5          |           8% |       9% |
| 6-7          |          12% |      12% |
| 9-15 (act 2) |          13% |      19% |

The Blockade Line is about 40% new, and the campaign sectors 37-67% for a
vanilla ship and 49-71% for a big one.

## What's not counted

- The flagship's and the last stand's events (`events_boss.xml`), and the
  sectors the game never uses (`STANDARD_SPACE`, `ABANDONED_SECTOR` and
  `DEEP_SPACE_SECTOR`).
- Vanilla's quest events placed by other events, which aren't in any
  sector's list, aren't beacon events either.
- Events with the same name in several vanilla files (Advanced Edition
  overrides some) are counted once.
