# Race ships

Besides the Chonkestrel, the mod has a bigger ship for each of seven races:
that race's vanilla Type A ship, made twice the size as the Chonkestrel is
from the Kestrel, and laid out for what the race is good at, and the
Federation's (`PLAYER_SHIP_XFTL_FED`), for its artillery, and the Stealth
cruisers' (`PLAYER_SHIP_XFTL_STEALTH`), for their cloaking. They're all in the hangar (`PLAYER_SHIP_XFTL_<RACE>`), after
the Chonkestrel, which is still the one picked to start with.

Each also has a Type B (`PLAYER_SHIP_XFTL_<RACE>_2`), made the same way
from the race's vanilla Type B ship: the same race's strengths, built on
what that ship has and plays like. The Chonkestrel has one too, the
Chonk-Tail (`PLAYER_SHIP_CHONKESTREL_2`), from the Kestrel B, the Red-Tail;
the Chonkestrel itself is hand-made (see `tools/chonkestrel_art.py`), but
the Chonk-Tail is made by the same script as the rest. The hangar treats a
ship and the ones with `_2` and `_3` on the end as Type A, B and C of one
family.

The races with a vanilla Type C (in the Advanced Edition's
`dlcBlueprintsOverwrite.xml`) have a big Type C too
(`PLAYER_SHIP_XFTL_<RACE>_3`), made from it, and so does the Chonkestrel:
the Chonkswallow (`PLAYER_SHIP_CHONKESTREL_3`), from the Kestrel C, the
Swallow. The Lanius and the Crystals have no vanilla Type C, so they have
no big one.

## In the hangar

The ship list has the big ships first, in a row of their own (the
Chonkestrel first, the Lanius and then the Crystal ships last, each Type A
then its B), and vanilla's ships below them, in vanilla's order, each
family's Type A, B and C together, a little apart from the big ships
(`ShipList.BIG_SHIPS_GAP`). The big ships' pictures are half as big again
as the others', made from their hulls by `tools/hangar_art.py` (as their
miniatures are, so they're as crisp). It scrolls with the mouse wheel, with
a bar on the right showing where it's at, when it's taller than the
screen. The arrows either side of the list button go through both, in
that order, and back round. Every ship can be picked, as it always could in
XFTL: the hangar has never locked ships.

Above the layout's A, B and C buttons, in a section of their own (TYPE),
are **Normal** and **Chonky**, one above the other, which switch between
a vanilla ship and its big version, keeping the race and the layout
letter: the Engi Type B (`PLAYER_SHIP_CIRCLE_2`) goes to the
Chonkvortex (`PLAYER_SHIP_XFTL_ENGI_2`) and back. The one the ship is
shows as picked, as the layout's does. A vanilla Type C goes to its big
Type C the same way (the Swallow to the Chonkswallow). Only a race without
a big ship of that layout would fall back to its big Type A, whose layout
buttons then show A; none does now, as the Lanius and Crystals have no
vanilla Type C either. A ship with nothing to switch to has the other
button disabled.

Which vanilla ship each big one was made from is in its blueprint, an
`<xftlVanillaShip>` element (an XFTL extension, see
[modding](../tools/modding.md)): `race_ships.py` writes the one each was made
from, and the Chonkestrel's (`PLAYER_SHIP_HARD`) is in its hand-made
blueprint. The hangar maps both ways from them.

Random Ship picks a ship as big as the current one: a big ship (of either
layout) on a big ship, a vanilla one (of any layout) on a vanilla ship. So
it keeps to what Normal and Chonky picked.

A vanilla ship is size 1, so it fights one vanilla-sized ship at a time,
as in vanilla.

They're made by `tools/race_ships.py` (`make art`, see
[art](../tools/art.md)), from the vanilla ships in `ftl.dat`: their layouts
(`mods/chonkestrel/data/<ship>.txt` and `.xml`), their images (made, not
kept in git), and their blueprints, in their own block of
`autoBlueprints.xml.append`. `python3 tools/race_ships.py ftl.dat --preview out/`
draws each one beside the vanilla ship, with its rooms, doors, systems and
weapon mounts.

## What they all have

As the Chonkestrel:

- **Size 2** (`shipSize`), so they fight two vanilla ships at once, up to
  three (`opponentShips`), starting with one in the first sector
  (`opponentShipsAtStart`). The game doubles their blueprint's hull, as it
  does the Chonkestrel's (see [hull](hull.md)).
- **Four rows of rooms more** than the vanilla ship (two on either side of
  its middle, for the Chonkila Monster), put in through its middle (or,
  for the Chonkelian, across its lower half, where its hull carries on
  cleanly). The
  rooms the new rows go through get taller, with their interiors (and
  consoles) moved apart to fit; the new rows have the new rooms. Ships too
  tall for the screen (the Chonkruos and Chonkrike, with their long fins) have their hull
  made a little shorter above and below their rooms.
- **Two weapons systems** (three on the Chonkwark and Chonkivan), each with four of the
  eight weapon slots (three of nine), and a weapon mount for each slot: the
  vanilla ship's four, and the rest along the top and bottom of the hull.
  The second weapons system starts with about what the vanilla ship has.
- Room for 18 crew (16 on the ships with three shields systems, so the
  shields and the crew list both fit on the screen), 8 cargo and 6 augments, and for every system it has
  rooms for (`maxSystems`). Its reactor is enough to power everything it
  starts with at once.
- A crew of its own race, about twice the vanilla ship's, each at a station
  (fewer on the Chonkvortex, whose drones do the work, and a mixed one on
  the Chonk-Tail, as on the Red-Tail): every one of them starts at the
  console of a system the ship starts with, one to each, but for the
  Chonkila Monster's and the Chonkeseus' boarders that there are no more
  consoles for, who wait on a teleporter pad. `tools/race_ships.py` checks
  it, and the hangar shows them there (see `StartingCrew`).

## The ships

| Ship | Race | Made from | Size in rooms | Its race's systems |
|---|---|---|---|---|
| The Chonktorus | Engi | The Torus | 9 x 11 | three drones systems |
| The Chonkudicator | Zoltan | The Adjudicator | 13 x 11 | three shields systems |
| The Chonkila Monster | Mantis | The Gila Monster | 12 x 14 | two teleporters, two medbays |
| The Man of Chonk | Slug | The Man of War | 8 x 12 | two sensors, two hacking, two mind control |
| The Chonkwark | Rock | The Bulwark | 9 x 10 | three weapons systems |
| The Chonkruos | Lanius | The Kruos | 8 x 12 | no oxygen; two backup batteries, two hacking |
| The Chonkravais | Crystal | The Bravais | 15 x 12 | two cloaking systems |
| The Chonksprey | Federation | The Osprey | 15 x 12 | two Artillery Beams |
| The Chonkasio | Stealth | The Nesasio | 13 x 10 | two cloaking systems, no shields to start with |

And the Type B versions:

| Ship | Race | Made from | Size in rooms | Its race's systems |
|---|---|---|---|---|
| The Chonk-Tail | Human | The Red-Tail (Kestrel B) | 14 x 11 | two each of weapons, shields, engines, oxygen and sensors; eight Basic Lasers |
| The Chonkvortex | Engi | The Vortex | 9 x 11 | three drones systems, anti-personnel and repair drones |
| The Chonkoether | Zoltan | Noether | 12 x 10 | three shields systems, ions |
| The Chonkilisk | Mantis | The Basilisk | 11 x 14 | two teleporters, two medbays, two drones systems |
| The Chonkwalker | Slug | The Stormwalker | 12 x 14 | two sensors, two hacking, two mind control |
| The Chonkivan | Rock | Shivan | 9 x 11 | three weapons systems, fire bombs |
| The Chonkrike | Lanius | The Shrike | 9 x 14 | no oxygen; two backup batteries, two hacking |
| The Chonkelian | Crystal | Carnelian | 14 x 12 | two cloaking systems, a teleporter |
| The Chonknisos | Federation | The Nisos | 13 x 13 | two Artillery Beams |
| DA-CHONK 24 | Stealth | DA-SR 12 | 12 x 11 | two cloaking systems, no shields to start with |

And the Type C versions:

| Ship | Race | Made from | Size in rooms | Its race's systems |
|---|---|---|---|---|
| The Chonkswallow | Human | The Swallow (Kestrel C) | 15 x 10 | two each of weapons, shields, engines, oxygen and sensors |
| The Chonkagon | Engi | The Tetragon | 9 x 11 | three drones systems, beam drones |
| The Chonkerenkov | Zoltan | Cerenkov | 13 x 11 | three shields systems, two backup batteries |
| The Chonkatidae | Federation | The Fregatidae | 15 x 12 | two Artillery Beams |
| The Chonkolimax | Slug | Ariolimax | 8 x 12 | two sensors, two hacking, two mind control |
| The Chonkeseus | Mantis | The Theseus | 12 x 14 | two teleporters (one for eight), two medbays |
| The Chonktite | Rock | Tektite | 9 x 10 | three weapons systems |
| Simo-CHONK | Stealth | Simo-H | 13 x 10 | two cloaking systems, shield drones, no shields to start with |

### The Chonktorus (Engi)

A drone carrier. It has three drones systems, the Torus's own and two new
2x2 rooms at the rear, each with three drone slots, all installed, and 40
drone parts. It starts with a Combat Drone, a Defence Drone and a Repair
Drone in the first, and a twin Combat Drone in the second. Its new rows
also have a second shields system and its second weapons system (an Ion
Blast II in the first, a twin Ion Blast in the second), and the sensors and
teleporter rooms the new rows go through are 1x6 (a pad for six). Six Engi,
the Engi Med-bot Dispersal and the Drone Reactor Booster.

### The Chonkudicator (Zoltan)

A fortress. Two new shields systems stack with the Adjudicator's, three in
all, and it has two Zoltan shields. Its Zoltan crew power the rooms they're
in, which makes up for the reactor being only what its systems need. Its
second weapons system has the Adjudicator's Halberd Beam and Leto Missiles, as the
first does; a second backup battery can be bought, and there are two big
empty rooms for the crew. Six Zoltan, at the piloting, the three shields,
the weapons and the engines.

### The Chonkila Monster (Mantis)

A boarding ship. Its rows go in on either side of its middle, so it stays
symmetric, and the medbay, mind control, oxygen and hacking rooms they go
through become 2x4. The Gila Monster's teleporter is moved into two new
2x2 teleporters, each sending four crew at once, and there's a second
medbay (3x2) to patch them up, as well as the first, now 2x4. Its second
weapons system has a twin Basic Laser and a Small Bomb. Seven Mantis (two
at the doors and the oxygen, and two more, with no consoles left for them,
on a teleporter) and two Engi, at the piloting and the engines, and
Mantis Pheromones.

### The Man of Chonk (Slug)

All eyes and minds: two sensors systems (each seeing into one enemy ship,
as the Chonkestrel's do), two hacking systems and two mind control
systems, all installed from the start. The backup battery and teleporter
rooms the new rows go through are 1x6 (a pad for six). Its second weapons
system has a Dual Shot Laser and a Breach Bomb. Six Slugs and Slug Repair Gel.

### The Chonkwark (Rock)

Firepower to match its armour: three weapons systems, with nine weapon
slots between them, the first two with the Bulwark's Artemis and Hull
Missile and the third with a twin Artemis, and 64 missiles. Its blueprint's
hull is 40 rather than 30 (80 in the game), the most of any of the ships,
and it has Rock Plating and a second shields system. Seven Rocks.

### The Chonkruos (Lanius)

For a crew that doesn't breathe: it has no oxygen system at all, not even
to buy, so its air runs out, which doesn't matter to its seven Lanius (at
the doors and a hacking system too, ready to board and drain the air from
enemy ships). It has two backup
batteries (one in the Kruos's empty room by the teleporter), two hacking
systems and its teleporter, all installed. The engines and piloting rooms
the new rows go through are 2x6 and 1x6. Its second weapons system has a
Chain Burst Laser and an Ion Stunner, as the first does.

### The Chonkravais (Crystal)

Made to vanish. It has two cloaking systems, both installed: only one can
cloak the ship at once, but while one is cooling down, the other can cloak
it again (see `Ship.cloakingSystems`). Its second weapons system has a twin
Crystal Burst and a Heavy Crystal. The oxygen room and the empty room by
the nose that the new rows go through are 1x6, and there are big empty
rooms for its six Crystals, who have Crystal Vengeance.

### The Chonksprey (Federation)

The Osprey, with the Federation's artillery doubled: two Artillery Beams,
each an artillery system in a 2x1 room of its own, the Osprey's at the rear
and a new one in the middle, firing on their own as they charge: against
several enemy ships, each volley (from either) at the next of them in turn
(`Artillery.nextTargets`), rather than all at the first, and at five bars
at two of them, at six at three (`Artillery.SHIPS_PER_VOLLEY`), one after
another. Their guns
are on the nose, one above and one below where the Osprey's is, on mounts
of their own after the eight weapon slots' (the game takes an artillery
system's mount from there, see `Artillery.hardpoint`). Its rows go in two
on either side of its middle, so it stays symmetric, and the doors, oxygen,
weapons and drones rooms they go through get taller. A Burst Laser II in
each weapons system, a second shields system and a second sensors system.
Five humans, a Mantis, a Rock and an Engi, as the Osprey has one of each.

### The Chonkasio (Stealth)

The Nesasio, twice as hard to see: two cloaking systems, its own and a new
one, so one can cloak it while the other cools down. As the Nesasio, it
starts with no shields (its shields system is there to buy), and its
engines start at four power: it dodges and hides rather than taking hits.
That's playable for a ship that fights up to three at once, as it meets one
ship at a time at first (`opponentShipsAtStart`), and has time to buy
shields before the fights get bigger. Its rows go through its spine, whose
rooms get taller, as the wings above and below it carry on cleanly; the
new rows have its second weapons system (a twin Dual Shot Laser, beside the
Nesasio's Mini Beam and Dual Shot Laser) and the second cloaking system.
Six humans, with the Nesasio's reinforced system casing and advanced
scanners.

## The Type B versions

### The Chonk-Tail (Kestrel B)

The Red-Tail, doubled as the Chonkestrel is: two weapons systems, each with
four Basic Lasers, as the Red-Tail has, and two shields, engines, oxygen
and sensors systems (each sensors system seeing into one enemy ship). Its
four new rows go across the middle, where no rooms are in the way, so the
Red-Tail's long body and nose carry straight on; they hold the new
systems, a 4x4 second medbay (with no bed in the way, as the
Chonkestrel's), and empty rooms by the nose. Six humans, a Zoltan in the
engines and a Mantis at the doors, as the Red-Tail has a Zoltan and a
Mantis.

### The Chonkvortex (Engi B)

The Vortex leans on its drones rather than its crew, and so does this: three
drones systems with three slots each, the Vortex's anti-personnel drone and
two repair drones in the first and a twin anti-personnel drone and a twin
repair drone in the second, and only three Engi. Its second weapons system
has a twin Heavy Laser, beside the Vortex's Heavy Laser and Heavy Ion.
The medbay the new rows go through is 1x6.

### The Chonkoether (Zoltan B)

Noether's ions behind three shields systems: two new ones stack with its
own (which starts at two power, not one), with two Zoltan shields. Its
second weapons system has a twin Ion Blast and a Pike Beam (beside two Ion
Blasts and a Pike Beam in the first), to strip the enemies' shields. The
cloaking and weapons rooms the new rows go through are 2x6. Six Zoltan.

### The Chonkilisk (Mantis B)

The Basilisk boards with drones as well as crew. It has its own 2x2
teleporter and a new one, with a new medbay between them and its own medbay
above, two drones systems (a Boarding Drone and a Defense Drone in the
first, a twin Boarding Drone in the second, and 30 drone parts), and a
Basic Laser in each weapons system. The piloting room the new rows go
through is 1x6. Eight Mantis (at the doors, the sensors, the oxygen and a
drones system too, ready to board), and Mantis Pheromones.

### The Chonkwalker (Slug B)

The Stormwalker's Healing Bursts and missiles, and two of each of the
Slugs' systems: sensors, hacking and mind control, its own and new ones,
all installed. Its second weapons system has an Artemis and a Small Bomb,
with 50 missiles. The empty room at its rear, the hacking room and the
piloting room the new rows go through are 1x6, 2x6 and 1x6. Six Slugs and
Slug Repair Gel.

### The Chonkivan (Rock B)

Shivan's fire bombs, in three weapons systems with nine slots between them:
its own Heavy Pierce Laser, Fire Bomb and Small Bomb, a twin Fire Bomb, and an
Artemis and a twin Small Bomb, with 40 missiles. Like the Chonkwark, its
blueprint's hull is 40, and it has Rock Plating and a second shields
system; its new rows go across the middle, where no rooms are in the way,
with a big empty room. Seven Rocks.

### The Chonkrike (Lanius B)

The Shrike, with no oxygen system at all, its flak (an Advanced Flak in each
weapons system), two backup batteries (one in the empty room by the
shields), two hacking systems, its mind control and its teleporter, all
installed. The engines and piloting rooms the new rows go through are 2x6
and 1x6, and, as the Chonkruos, its fins are a little shorter, to fit the
screen. Seven Lanius (at the doors and a hacking system too).

### The Chonkelian (Crystal B)

Carnelian's Crystal boarders, who seal the rooms they land in, sent over
while it's cloaked: it has its own cloaking system and a new one (see the
Chonkravais), its 2x2 teleporter, and a Heavy Crystal and a twin Heavy
Crystal. Its new rows go across its lower half, where the hull carries on
cleanly. Six Crystals (at the doors and the sensors too), and Crystal
Vengeance.

### The Chonknisos (Federation B)

The Nisos, with two Artillery Beams as the Chonksprey has (each at two
power, as the Nisos's is), the Nisos's at the bottom and a new one in the
middle, their guns on the nose. Its rows go in on either side of the
piloting row, where no rooms are in the way. A Dual Shot Laser and Leto
Missiles in each weapons system, a second shields system and a second
sensors system. Five humans, a Zoltan and a Slug, as the Nisos has.

### DA-CHONK 24 (Stealth B)

The DA-SR 12, doubled: two cloaking systems (at two power each, as the
DA-SR 12's is), a Glaive Beam to fire from the dark and a twin Mini Beam,
and no shields to start with (two shields systems to buy), for the same
reasons as the Chonkasio. Its rows go across its middle, where no rooms are
in the way. Four humans and two Zoltan, one powering the second cloak.

## The Type C versions

As the vanilla Type C ships do, they start with their clonebay rather than
their medbay (the Chonkswallow and Chonkeseus have a second, new medbay
installed as well).

### The Chonkswallow (Kestrel C)

The Swallow, doubled as the Chonkestrel is: two each of weapons, shields,
engines, oxygen and sensors, with a Dual Shot Laser and an Ion Stunner in
each weapons system, and a new 2x4 medbay. Its rows go in through the
weapons room, which gets taller, so its body and nose carry on cleanly.
Six humans and two Lanius (at the engines), as the Swallow has a Lanius.

### The Chonkagon (Engi C)

The Tetragon's beam drones, three drones systems' worth: two Anti-Ship
Beam Drones and a Defense Drone in the first, a twin Beam Drone in the second, and 40 drone
parts, with the Tetragon's defense scrambler. A Dual Shot Laser and a twin
one. The shields and the empty room by the nose that the new rows go
through are 2x6 and 1x6. Five Engi and a Lanius at the hacking.

### The Chonkerenkov (Zoltan C)

Cerenkov runs on its crew and its batteries: three shields systems, its
own backup battery and a new one, both installed, two Zoltan shields, an
Ion Charger in each weapons system and its Anti-Ship Beam Drone. Its rows go in below
its middle, where no rooms are in the way. Seven Zoltan.

### The Chonkatidae (Federation C)

The Fregatidae with two Artillery Beams, as the Chonksprey has: its own,
in its 2x2 room (2x6 now, as the new rows go through it), and a new one, in
a 2x1 room, their guns on the nose. A Dual Shot Laser in each weapons
system and a second shields system. Five humans, two Zoltan (one in the
artillery room) and a Mantis.

### The Chonkolimax (Slug C)

Ariolimax with two of each of the Slugs' systems: sensors, hacking and
mind control, its own and new ones, all installed. A Chain Burst Laser in
each weapons system. Its rows go in below its middle, where no rooms are
in the way. Six Slugs and Slug Repair Gel.

### The Chonkeseus (Mantis C)

The Theseus, built to board: its rows go in two on either side of its
middle, so its own teleporter, which they go through, becomes a 2x4 pad
for eight, and there's a new 2x2 one for four, and a second medbay (as
well as its clonebay). A Crystal Lockdown Bomb and a Stun Bomb in each
weapons system.
Seven Mantis (at the doors, the sensors and the oxygen too, and one, with
no console left, on a teleporter), an Engi and a Lanius. The row of
pixels repeated is eight above each cut, as the rows just above have a
pale highlight that would make pale blocks.

### The Chonktite (Rock C)

Tektite's Rock and Crystal guns in three weapons systems: Swarm Missiles
and a Heavy Crystal in the first, Swarm Missiles in the second and a
Heavy Crystal and a twin one in the third, nine slots between them. Its
blueprint's hull is 40, as the other Rock ships', with Rock Plating and a
second shields system. Five Rocks and two Crystals.

### Simo-CHONK (Stealth C)

The Simo-H, doubled: two cloaking systems (its own, installed now, and a
new one), and, as the Simo-H, no shields but Shield Overchargers (two, and
an Anti-Combat Drone, in three drone slots). A Laser Charger and a Mini
Beam in the first weapons system and a Laser Charger in the second. The engines,
shields and piloting rooms the new rows go through are 2x6 and 1x6. Four
humans, a Rock and a Slug.
