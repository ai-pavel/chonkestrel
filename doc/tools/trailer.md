# The trailer

The trailer recorder plays the release trailer, records it into a video,
and quits. It's for the project's site.

## Running it

```sh
rm -rf dist/trailer/save
XFTL_VIDEO=$PWD/dist/trailer XFTL_VIDEO_SIZE=1920x1080 make run SAVE_DIR=dist/trailer/save
```

This records into `dist/trailer/`, in a 1920x1080 window. It needs the
Chonkestrel mod enabled, and `ffmpeg` (`brew install ffmpeg`).

- `XFTL_VIDEO=<directory>` (or `-Dxftl.video=<directory>`): the directory to
  record into. The video's named after the time it was recorded, such as
  `dist/trailer/trailer-20261024-173718.mp4`. It skips the main menu to
  start with the default ship (the Chonkestrel), as a new game has it.
- `XFTL_VIDEO_SIZE=<width>x<height>` (or `-Dxftl.video-size`): the window
  size.

The trailer goes in `dist/trailer/`, to watch before putting it on the
site, with a poster (below) of the same name.

Every frame advances the game by exactly a thirtieth of a second, however
long it really took to draw and record, so the video plays smoothly at 30
frames a second. The frames are piped straight into `ffmpeg`, which encodes
an H.264 MP4. Its log goes next to the video while it's recording.

To make a poster for the site (the `.jpg` next to the video), take a frame
from the middle of it:

```sh
ffmpeg -ss 10 -i dist/trailer/trailer-20261024-173718.mp4 -frames:v 1 -q:v 3 dist/trailer/trailer-20261024-173718.jpg
```

## What it records

The trailer recorder records the release trailer, 96 seconds long, into
`dist/trailer/trailer-<time>.mp4` (see `devutil/Trailer.kt`), with a screenshot of each fight next to it
(`trailer-<time>-fight-01.png` and so on, four seconds into it). Rather than
one battle, it's the first six seconds of sixteen fights, each in a new
sector. The hazards build up from each of XFTL's own nebulas on its own,
through every pair of hazards (extreme), every three (catastrophic), to all
four at once (apocalyptic, each with a different nebula), with every kind of
nebula along the way (see [extreme-hazards.md](../game/extreme-hazards.md)):

| # | Hazards | Against | The Chonkestrel |
|---|---------|---------|-----------------|
| 1 | Radiation nebula | Engi scout, Rebel fighter | Its first upgrades, three more crew, a drones system with a twin combat drone, its first doubles |
| 2 | Mirage nebula | Mantis fighter, Zoltan fighter | Hacking |
| 3 | Dark nebula | Long Slug ship, Rockman scout | Doubles in half its slots, cloaking |
| 4 | Asteroids + sun | Wide Engi bomber, Crystal scout | Doubles in every slot, a twin combat and a twin defence drone |
| 5 | Nebula + asteroids | Long Mantis bomber, long Rebel cruiser | Mind control, a teleporter with the Reconstructive Teleport, a Zoltan shield, cloaking 2 |
| 6 | Asteroids + pulsar | Wide Rockman assault ship, Lanius scout | A second drones system: a triple combat drone, a twin defence drone, a triple boarding drone and a twin overcharger |
| 7 | Ion storm + sun | Long Slug cruiser, Federation scout, auto-ship | The first triples, cloaking 3 |
| 8 | Sun + pulsar | Wide Zoltan bomber, long Rockman fighter | Two Zoltan shields |
| 9 | Mirage nebula + pulsar | A wide hybrid, a long Mantis scout | A second hacking system, cloaking 4 |
| 10 | Asteroids + sun + pulsar | Two long hybrids | Every system maxed out (but the cloaking) |
| 11 | Radiation nebula + asteroids + sun | A wide hybrid and another | Triples in half its slots, cloaking 5 |
| 12 | Dark nebula + asteroids + pulsar | A wide and a long hybrid, with doubles | Three Zoltan shields |
| 13 | Nebula + sun + pulsar | Two long hybrids and another, with doubles | Triples in most slots, cloaking 6 (the most), triple drones of every kind (Combat II and Beam, Defence II, Ion Intruder, overcharger) and a twin anti-combat drone |
| 14 | Ion storm + all three | Three long hybrids, with doubles | Triples in every slot |
| 15 | Dark nebula + all three | A wide and a long hybrid, with triples | |
| 16 | Radiation nebula + all three | Three long hybrids, with triples | |

The drones are twins and triples (see
[drone-sizes.md](../game/drone-sizes.md)), launched as each fight starts,
so their copies spread out over the enemy ships and round the orbit; the
long and wide hybrids' bulwarks stand guard in their holds, seen with
their rooms. Its Chonkestrel starts as a new game has it (not with debug mode's
loadout), and gets the rest before each fight. Its cloaking grows a level
every other fight, from the third to the thirteenth, to its sixth and last,
but it never cloaks. The hybrids are random, and
printed in the log with everyone's weapons. The ships in each fight fit on
the screen as in the game (see `FightPlan`): a wide one only comes with one
other ship. The enemy ships' rooms and crew are shown even in a nebula, they
all have the Zoltan Shield Bypass, and those with teleporters board the
Chonkestrel a second and a half into the fight. Their cloaks take two and a
half seconds to get ready, so boarders can get aboard first.

The Chonkestrel's crew are put at their stations as each fight starts: one
at each system that can be manned (the Lanius at the engines, as they'd
suffocate everyone in the medbay), from the fifth fight a boarding party of
up to four in the teleporter (the Mantis, Rockmen and Crystals first), who
board an enemy a second in, and the rest in the medbay, to meet the
enemies' boarders. With the Reconstructive Teleport, the boarding party is
brought home, healed, before the next fight. As each fight starts, everyone's weapons are
most of the way charged, so they fire in the few seconds it's shown. Where
the reactor's short (in an ion storm), the weapons take the power from the
mind control, hacking, teleporter and cloaking systems first, then the
medbay and shields. The dark nebula's drones stay down, as they would. It
plays in a save directory of its own (`dist/trailer/save`), emptied first,
so it always starts a new game.
