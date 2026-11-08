# Sectors

The sectors the Chonkestrel mod (`mods/chonkestrel`) adds, and what
changes about the vanilla ones. For the story they're part of, see
[lore.md](lore.md); for how many events each has, see [events.md](events.md);
for the new kinds of nebula, see [nebulas.md](nebulas.md).

The sectors are defined in `sector_data.xml.append`, their names in
`text_sectorname.xml.append`, and their events in `events.xml.append` (and
`newEvents.xml.append` for the events only for big ships).

## Overview

| Sector               | Class     | Where                | Unique | Music                        |
|----------------------|-----------|----------------------|--------|------------------------------|
| The Blockade Line    | hostile   | sector 8, every game | yes    | colonial, wasteland, hacking |
| Crystal Homeworlds   | hostile   | act 2                | yes    | vanilla's                    |
| The Free Colonies    | civilian  | act 2                | no     | civilian, colonial, milkyway |
| The Anvil Yards      | civilian  | act 2                | yes    | engi, cosmos, milkyway       |
| The Quiet            | civilian  | act 2                | yes    | zoltan, cosmos, milkyway     |
| 28 hybrid sectors    | hostile   | act 2                | no     | both races' vanilla music    |
| The Radiation Fields | hazardous | either act, from 5   | no     | deepspace, void, rock        |
| The Mirage Expanse   | hazardous | either act, from 5   | no     | deepspace, void, slug        |
| The Dark Reach       | hazardous | either act, from 5   | no     | deepspace, void, shrike      |
| The Maw              | hazardous | sectors 5-8          | yes    | void, deepspace, rock        |
| The Gullet           | hazardous | sectors 9-12         | yes    | void, deepspace, debris      |
| The Belly            | hazardous | sectors 11-15        | yes    | void, debris, deepspace      |

The class sets the sector's colour on the sector map, and which sector types
it's picked from. The hazardous sectors (the Radiation Fields, the Mirage
Expanse, the Dark Reach, the Maw, the Gullet and the Belly, where
every beacon has hazards) are a class of their own to the player: they're
dark purple on the map, with their own entry in the legend ("Hazardous"),
and an unknown one is an "Unidentified Hazardous Sector". Behind the scenes,
they're still picked as one of vanilla's classes (`sectorType` in
`sector_data.xml.append`): the new nebula sectors as nebula sectors, and the
others as hostile ones. The act 2 sectors are twice as likely as each vanilla
sector (`GameMap.ACT_ONLY_WEIGHT`), as is the Maw (`xftlWeight`), and
the map avoids putting the same type twice in a row on the player's path.
In act 2, the factions' own vanilla sectors are gone (`xftlAct="1"` in
`sector_data.xml.append`): the Federation's space is behind the Rebel lines
by then, and CONCORD's hybrids hold the rest. The civilian sectors are the
Free Colonies, the Anvil Yards and the Quiet; the hostile ones are the
hybrid sectors (see below) and the hazardous ones; and the nebulas are the
vanilla Nebula sectors and the hazardous ones (the Slugs' nebulas are only
in act 1, but for their homeworld). A homeworld that didn't turn up in
act 1 can still turn up in act 2, as likely as any other sector.
Some are limited to some sectors (`xftlSectors`, numbered as in a
sixteen-sector game), as they get more dangerous further in. The three
hazardous sectors with bosses (the Maw, the Gullet and the Belly) are on
every map (`xftlAlways`): if one didn't turn up, it takes the place of an
ordinary sector in a column it can go in (`GameMap.placeRequiredSectors`).

In a thousand random maps:

| Sector             | On the map | On a random path  | Where                                                         |
|--------------------|------------|-------------------|---------------------------------------------------------------|
| The Maw            | always     | 0.36 a game       | sectors 5-7, mostly 5 (once a game)                           |
| The Gullet         | always     | 0.20 a game       | sectors 9-12, mostly 11 (once a game)                         |
| The Belly          | always     | 0.16 a game       | sectors 11-15, a little more often earlier (once a game)      |
| Each nebula sector | about 90%  | about 0.45 a game | from sector 5, more often in act 2, which has more sectors    |

Act 2 is twice as big as act 1 on the sector map (`GameMap.columnSizeRange`):
act 1's columns have two to four sectors each, as in vanilla, and act 2's
four to eight (opening up from two sectors just after the blockade, to
four). That's about 17 sectors in act 1 and 36 in act 2, so there's more
choice of route once the Chonkestrel is through the blockade.

Each sector also gets the events for big ships (`XFTL_BIG_*`) when the player
flies a size 2 ship like the Chonkestrel.

## The Blockade Line

**XFTL_BLOCKADE.** The sector between the two acts: every game at least 12
sectors long goes through it, at sector `N / 2` (8 of 16), as the only sector
in its column (`xftlMidpoint`).

- **Start:** the blockade, with some of its ships gone quiet, and refugees
  running from a hum coming out of the Rebel Core.
- **Exit:** three exit beacons at the end of the sector (`xftlExits`), all
  held by the blockade's command ship, the RSV Vigilance, a size 3 boss
  (see [bosses.md](bosses.md)), through `xftlFinishEvent`: the same ship,
  whichever of them the player comes to. The player has to beat it to
  leave: fight it, ambush it under cloak, or, in a big
  ship, answer that it was built to break blockades (and pick up some
  supplies, and start with its Zoltan shields down). The Rebel fleet advances 1.15
  times as fast here (`xftlFleetSpeed`).
- **The reveal:** beating the Vigilance or killing its crew leads to its log (`XFTL_BLOCKADE_REVEAL`), and then Dr. Revic's message
  in the data the player carries: CONCORD has taken over the Rebel fleet, and
  its core, the Mainframe, is at these coordinates. See
  [lore.md](lore.md).
- **Events:** blockade patrols, a smuggler selling the patrol routes, a
  defector (who might be a trap), refugees stuck at the line, a customs
  "toll station", debris from earlier breakouts (an asteroid field), agent
  Kestrel-Nine (a quest to pick up the patrol codes at another beacon), and
  a minelayer closing the gap, and a Rebel cruiser that only hums (a blue
  option with hacking). Plus vanilla Rebel events.

## The hybrid sectors

**XFTL_HYBRID_<first>_<second>**, such as `XFTL_HYBRID_ROCK_ZOLTAN`, the
Rock-Zoltan Controlled Sector: act 2's hostile sectors, one for every pair
of the eight races with hybrid ships (all but the Federation), 28 in all,
made by `tools/hybrid_sectors.py` (run it again after changing it). The
first race is the more hostile one: Rebel, Mantis, Rock, Slug, Lanius,
Crystal, Engi, then Zoltan.

- **Its ships:** of the enemy ships that can be hybrids (the scouts and
  bombers of each race, not the pirates', the bosses or the events' own),
  three in four are the pair's hybrids, a scout's one of their scouts and a
  bomber's one of their bombers (`xftlHybrids`, see
  `ShipGenerator.hybridFor`). The rest are other hybrids, or the ship
  itself, less and less through act 2, as CONCORD takes the rest: one in
  five at its first sector, down to one in fifty at its last
  (`ShipGenerator.hybridSectorShip`, by `GameMap.actTwoProgress`).

  | Act 2 sector | The pair's hybrids | Other hybrids | Vanilla ships |
  |--------------|--------------------|---------------|---------------|
  | First        | 75%                | 5%            | 20%           |
  | Middle       | 75%                | 14%           | 11%           |
  | Last         | 75%                | 23%           | 2%            |
- **Its events:** the hybrid sectors' own (`XFTL_HYBRIDSEC_*`, in
  `events.xml.append`): its own start beacon (`XFTL_HYBRIDSEC_START_<race>_<race>`,
  made by the script: what each race's space looks like under the hum, and
  the joined ships between them), fights with CONCORD's joined ships
  (`XFTL_HYBRIDSEC_HOSTILE`, 5-7), life under the hum (`_COMMON`, 3-4: a
  clinic, a relay, refugees, a market, a graveyard, a census...), empty
  beacons (`_NOTHING`), and a pool for each of its two races
  (`XFTL_HYBRIDSEC_<race>`, 2-3 each, four events a race about what
  CONCORD did to it), plus stores, the events for big ships, and one of
  CONCORD's. The races' vanilla events are rare: their sectors' first
  hostile and neutral events, and their new events (`XFTL_MORE_*`), 0-1
  each, about one beacon in twenty-five. The events' joined ships are from
  `XFTL_HYBRID_SECTOR_SHIPS`, so they're mostly the pair's hybrids; the
  ships CONCORD hasn't joined are marked `xftlNoHybrid`.
- **Its music and stores:** both races' music, and the crew of both for
  hire.
- **How often:** together, about as often as the sectors they replaced, as
  each is half as likely as a vanilla sector (`xftlWeight="0.5"`, which can
  be a fraction).

Each one, with its hybrid ships (and their long and wide versions, where
there are any) and its music:

<!-- BEGIN generated by tools/hybrid_sectors.py -->
| Sector | Scouts | Bombers | Music |
|--------|--------|---------|-------|
| Rebel-Mantis Controlled Sector | Rebel-Mantis Hybrid Fighter (long, wide), Mantis-Rebel Hybrid Fighter (long, wide) | Rebel-Mantis Hybrid Rigger (long, wide), Mantis-Rebel Hybrid Bomber (long, wide) | colonial, wasteland, lostship, hacking, mantis, debris, void |
| Rebel-Rock Controlled Sector | Rebel-Rock Hybrid Fighter (long, wide), Rock-Rebel Hybrid Scout (long, wide) | Rebel-Rock Hybrid Rigger (long, wide), Rock-Rebel Hybrid Fighter (long, wide) | colonial, wasteland, lostship, hacking, rock |
| Rebel-Slug Controlled Sector | Rebel-Slug Hybrid Fighter (long, wide), Slug-Rebel Hybrid Interceptor (long, wide) | Rebel-Slug Hybrid Rigger (long, wide), Slug-Rebel Hybrid Light-Cruiser (long, wide) | colonial, wasteland, lostship, hacking, slug, debris, deepspace |
| Rebel-Lanius Controlled Sector | Rebel-Lanius Hybrid Fighter (long, wide), Lanius-Rebel Hybrid Scout (long, wide) | Rebel-Lanius Hybrid Rigger (long, wide), Lanius-Rebel Hybrid Bomber (long, wide) | colonial, wasteland, lostship, hacking, shrike, deepspace |
| Rebel-Crystal Controlled Sector | Rebel-Crystal Hybrid Fighter (long, wide), Crystal-Rebel Hybrid Scout (long, wide) | Rebel-Crystal Hybrid Rigger (long, wide), Crystal-Rebel Hybrid Bomber (long, wide) | colonial, wasteland, lostship, hacking, debris, deepspace |
| Rebel-Engi Controlled Sector | Rebel-Engi Hybrid Fighter (long, wide), Engi-Rebel Hybrid Scout (long, wide) | Rebel-Engi Hybrid Rigger (long, wide), Engi-Rebel Hybrid Bomber (long, wide) | colonial, wasteland, lostship, hacking, engi, cosmos |
| Rebel-Zoltan Controlled Sector | Rebel-Zoltan Hybrid Fighter (long, wide), Zoltan-Rebel Hybrid Fighter (long, wide) | Rebel-Zoltan Hybrid Rigger (wide), Zoltan-Rebel Hybrid Bomber (long, wide) | colonial, wasteland, lostship, hacking, zoltan, cosmos |
| Mantis-Rock Controlled Sector | Mantis-Rock Hybrid Fighter (long, wide), Rock-Mantis Hybrid Scout (long, wide) | Mantis-Rock Hybrid Bomber (long, wide), Rock-Mantis Hybrid Fighter (long, wide) | mantis, debris, void, rock, wasteland |
| Mantis-Slug Controlled Sector | Mantis-Slug Hybrid Fighter (long, wide), Slug-Mantis Hybrid Interceptor (long, wide) | Mantis-Slug Hybrid Bomber (long, wide), Slug-Mantis Hybrid Light-Cruiser (long, wide) | mantis, debris, void, slug, wasteland, deepspace |
| Mantis-Lanius Controlled Sector | Mantis-Lanius Hybrid Fighter (long, wide), Lanius-Mantis Hybrid Scout (long, wide) | Mantis-Lanius Hybrid Bomber (long, wide), Lanius-Mantis Hybrid Bomber (long, wide) | mantis, debris, void, shrike, deepspace, wasteland |
| Mantis-Crystal Controlled Sector | Mantis-Crystal Hybrid Fighter (long, wide), Crystal-Mantis Hybrid Scout (long, wide) | Mantis-Crystal Hybrid Bomber (long, wide), Crystal-Mantis Hybrid Bomber (wide) | mantis, debris, void, wasteland, deepspace |
| Mantis-Engi Controlled Sector | Mantis-Engi Hybrid Fighter (long, wide), Engi-Mantis Hybrid Scout (long, wide) | Mantis-Engi Hybrid Bomber (long, wide), Engi-Mantis Hybrid Bomber (long, wide) | mantis, debris, void, engi, cosmos, hacking |
| Mantis-Zoltan Controlled Sector | Mantis-Zoltan Hybrid Fighter (long, wide), Zoltan-Mantis Hybrid Fighter (long, wide) | Mantis-Zoltan Hybrid Bomber (long, wide), Zoltan-Mantis Hybrid Bomber (long, wide) | mantis, debris, void, zoltan, cosmos |
| Rock-Slug Controlled Sector | Rock-Slug Hybrid Scout (long, wide), Slug-Rock Hybrid Interceptor (long, wide) | Rock-Slug Hybrid Fighter (long, wide), Slug-Rock Hybrid Light-Cruiser (long, wide) | rock, wasteland, slug, debris, deepspace |
| Rock-Lanius Controlled Sector | Rock-Lanius Hybrid Scout (long, wide), Lanius-Rock Hybrid Scout (long, wide) | Rock-Lanius Hybrid Fighter (long, wide), Lanius-Rock Hybrid Bomber (long, wide) | rock, wasteland, shrike, deepspace |
| Rock-Crystal Controlled Sector | Rock-Crystal Hybrid Scout (long, wide), Crystal-Rock Hybrid Scout (long, wide) | Rock-Crystal Hybrid Fighter (long, wide), Crystal-Rock Hybrid Bomber (long, wide) | rock, wasteland, debris, deepspace |
| Rock-Engi Controlled Sector | Rock-Engi Hybrid Scout (long, wide), Engi-Rock Hybrid Scout (long, wide) | Rock-Engi Hybrid Fighter (long, wide), Engi-Rock Hybrid Bomber (long, wide) | rock, wasteland, engi, cosmos, hacking |
| Rock-Zoltan Controlled Sector | Rock-Zoltan Hybrid Scout (long, wide), Zoltan-Rock Hybrid Fighter (wide) | Rock-Zoltan Hybrid Fighter (long, wide), Zoltan-Rock Hybrid Bomber (long, wide) | rock, wasteland, zoltan, cosmos |
| Slug-Lanius Controlled Sector | Slug-Lanius Hybrid Interceptor (long, wide), Lanius-Slug Hybrid Scout (long, wide) | Slug-Lanius Hybrid Light-Cruiser (long, wide), Lanius-Slug Hybrid Bomber (long, wide) | slug, debris, wasteland, deepspace, shrike |
| Slug-Crystal Controlled Sector | Slug-Crystal Hybrid Interceptor (long, wide), Crystal-Slug Hybrid Scout (long, wide) | Slug-Crystal Hybrid Light-Cruiser (long, wide), Crystal-Slug Hybrid Bomber (long, wide) | slug, debris, wasteland, deepspace |
| Slug-Engi Controlled Sector | Slug-Engi Hybrid Interceptor (long, wide), Engi-Slug Hybrid Scout (long, wide) | Slug-Engi Hybrid Light-Cruiser (long, wide), Engi-Slug Hybrid Bomber (long, wide) | slug, debris, wasteland, deepspace, engi, cosmos, hacking |
| Slug-Zoltan Controlled Sector | Slug-Zoltan Hybrid Interceptor (long, wide), Zoltan-Slug Hybrid Fighter (long, wide) | Slug-Zoltan Hybrid Light-Cruiser (long, wide), Zoltan-Slug Hybrid Bomber (long, wide) | slug, debris, wasteland, deepspace, zoltan, cosmos |
| Lanius-Crystal Controlled Sector | Lanius-Crystal Hybrid Scout (long, wide), Crystal-Lanius Hybrid Scout (long, wide) | Lanius-Crystal Hybrid Bomber (long, wide), Crystal-Lanius Hybrid Bomber (long, wide) | shrike, deepspace, wasteland, debris |
| Lanius-Engi Controlled Sector | Lanius-Engi Hybrid Scout (long, wide), Engi-Lanius Hybrid Scout (long, wide) | Lanius-Engi Hybrid Bomber (long, wide), Engi-Lanius Hybrid Bomber (long, wide) | shrike, deepspace, wasteland, engi, cosmos, hacking |
| Lanius-Zoltan Controlled Sector | Lanius-Zoltan Hybrid Scout (long, wide), Zoltan-Lanius Hybrid Fighter (long, wide) | Lanius-Zoltan Hybrid Bomber (long, wide), Zoltan-Lanius Hybrid Bomber (long, wide) | shrike, deepspace, wasteland, zoltan, cosmos |
| Crystal-Engi Controlled Sector | Crystal-Engi Hybrid Scout (long, wide), Engi-Crystal Hybrid Scout (long, wide) | Crystal-Engi Hybrid Bomber (long, wide), Engi-Crystal Hybrid Bomber (long, wide) | debris, wasteland, deepspace, engi, cosmos, hacking |
| Crystal-Zoltan Controlled Sector | Crystal-Zoltan Hybrid Scout (long, wide), Zoltan-Crystal Hybrid Fighter (long, wide) | Crystal-Zoltan Hybrid Bomber (wide), Zoltan-Crystal Hybrid Bomber (long, wide) | debris, wasteland, deepspace, zoltan, cosmos |
| Engi-Zoltan Controlled Sector | Engi-Zoltan Hybrid Scout (long, wide), Zoltan-Engi Hybrid Fighter (long, wide) | Engi-Zoltan Hybrid Bomber (wide), Zoltan-Engi Hybrid Bomber (long, wide) | engi, cosmos, hacking, zoltan |
<!-- END generated by tools/hybrid_sectors.py -->

## The Free Colonies

**XFTL_FEDERATION_REMNANT.** Colonies where Federation colonists and Rebel
deserters hide from CONCORD together, behind jammers that keep the hum out.
The friendliest sector in the game, with the Quiet: lots of stores and
help, and few fights.

- **Events:** a drydock that repairs the hull, volunteers who join the crew,
  patrols sharing supplies, an armory selling a weapon, colonies under attack
  (fight off a raider for a high reward), an election where both candidates
  want the player's endorsement, a quarantined colony with a plague (a blue
  option with a level 2 medbay; the crew can catch it otherwise), the last
  flight academy, and a courier job (a quest to deliver a data chip to
  another beacon).
- **For big ships:** the militia hail the player as their fleet's flagship,
  and offer crew, repairs or a weapon.
- Repairs and supplies here scale with the ship's size (`xftlScale="size"`).

## The Anvil Yards

**XFTL_ANVIL_YARDS.** Unique. The hidden shipyard where the Federation
built the cruisers of Project Chonkestrel, to a design with no network:
the one shipyard CONCORD can't see into, though its ships are searching for
it. The sector with the best rewards.

- **Start:** half-built cruiser hulls in the slipways, and Rebel raiders on
  the way.
- **Events:** the chief engineer's free refit (a choice of 2 reactor power,
  a shields upgrade, a weapon, or 15 hull per size of reinforcement),
  engineers who join the crew, a supply depot being evacuated, CONCORD's raids
  on the slipways, a saboteur (a blue option with a teleporter), the weapons
  test range (a blue option with level 3 weapons), the evacuation convoy
  under attack, the drone lab's experimental drone (a blue option with Engi
  crew), and the canteen full of shipwrights.
- **For big ships:** an unfinished sister ship to strip for its systems,
  armour or reactor.

## The Quiet

**XFTL_QUIET.** Unique. Zoltan monks have kept the sector in meditation for
a thousand years, and their calm drowns out the hum, so ships of every race
hide here. With the Free Colonies, the friendliest sector in the game.

- **Events:** empty, peaceful beacons, the monastery (a Zoltan monk joins
  the crew, or a blue option with Zoltan crew for an augment), refugees of
  every race (a volunteer, or their market), a refugee to bring back from
  the hum (she joins the crew, or runs and the fleet catches up; blue
  options with Lanius or Slug crew), a drydock run by hand (cheaper repairs,
  but a full overhaul takes long enough for the fleet to catch up), and
  CONCORD's ships trying to get in (a high reward for stopping them).
- **For big ships:** the usual civilian events.

## The Maw

**XFTL_MAW.** Where a dying star, a pulsar and the wreck of a nebula
churn through an old asteroid belt. Every beacon has a hazard, and many have
two or three at once (`xftlHazards="true"`, see below). It's in the second
quarter of the game, sectors 5-8 (`xftlSectors="5-8"`), before the
blockade, so it's the first really dangerous sector.

- **Start:** a navigation buoy warning of what's ahead (the start beacon has
  no hazards).
- **Events:** empty beacons, a wreck to salvage before the hazards break it
  up (some cargo and hull damage, or more cargo), and an Engi survey station
  in a rare calm beacon (`xftlNoHazards`), which reveals the map or repairs
  the hull. Plus Rebel and pirate fights, a store (and sometimes a pirate
  one), and the big ship fights.
- **The hunter:** no Rebel fleet here; the Devourer, a size 3 Lanius
  hive-ship, hunts the player instead, a beacon every two jumps, bringing
  its nebula and asteroid field with it (`xftlHunter`, see
  [bosses.md](bosses.md)). The exit is an ordinary one.

### Hazardous sectors, and several hazards at once

Any beacon with a hazard can have two, three or four at once, more often
further into the game (`GameMap.addHazards`). In a hazardous sector, every
beacon has hazards: the Radiation Fields, the Mirage Expanse, the Dark
Reach, the Maw (`xftlHazards="true"`), the Gullet
(`xftlHazards="catastrophic"`) and the Belly (`xftlHazards="apocalyptic"`). Vanilla's nebula sectors and the Ion Storm
Belt are left as they are. The hazards are any kind of
nebula, asteroids, a sun and a pulsar, with no two of a kind (such as two
nebulas). The start beacon, stores, quests and events marked
`xftlNoHazards="true"` get none.

Two hazards at once are an extreme hazard (`Beacon.isExtremeHazard`), three
a catastrophic one (`Beacon.isCatastrophicHazard`), and all four an
apocalyptic one (`Beacon.isApocalypticHazard`), run as a
`MixedEnvironment`: they all work as they do on their own (a nebula's sensor
blackout and its own effects, asteroids, solar flares, ion pulses), with all
their warnings. They're stronger, though: their intensity is at least 2, as
in act 2 (see [lore.md](lore.md)), and suns flare and pulsars pulse twice as
often (`AbstractEnvironment.hazardRate`). Their power limits take the lowest
one, and their effects on the FTL drive multiply. The nebula's clouds are
drawn behind a sun or pulsar, and asteroids in front of everything.

How often each turns up, every combination, and what it's like, are in
[extreme-hazards.md](extreme-hazards.md).

On the beacon map, an extreme hazard is marked with a red hazard icon (a
catastrophic one with a purple one, and an apocalyptic one with a cyan
one), and its description starts with "EXTREME HAZARD" (or "CATASTROPHIC
HAZARD", or "APOCALYPTIC HAZARD"), whether or not it's been scanned, as
several hazards can be seen from afar, like a nebula. It's saved with the
beacon (`sectorHazards`, and `env2` to `env4` for the other environments).

## The Gullet

**XFTL_GULLET.** The storm-torn edge of the galaxy's arm, where stars
flare, pulsars howl and nebulas tear through the asteroid fields. After the
blockade, with the whole Rebel fleet behind it, the Chonkestrel can take it
to stay ahead: no sane captain follows it in. Every beacon has two to four
hazards at once, mostly three (`xftlHazards="catastrophic"`). It's in the
third quarter of the game, sectors 9-12 (`xftlSectors="9-12"`), just after
the blockade.

- **Start:** the fleet closing in behind, and the storm ahead (the start
  beacon has no hazards).
- **Events:** empty beacons, a Rebel hunter group that followed the player
  in (fight it while it's weak, or try to lose it in the hazards), an
  abandoned Rebel supply ship being torn apart (a high reward, or a smaller
  one and hull damage), and a Federation listening post in a calm pocket
  behind a dead star (`xftlNoHazards`), which reveals the map, repairs the
  hull or slows the fleet down. Plus Rebel and pirate fights, a store half
  the time, and the big ship fights.
- **The hunter:** no Rebel fleet here; the Stormbreaker, CONCORD's size 3
  hunter flagship, hunts the player instead, a beacon every jump, bringing
  its ion storm with it (`xftlHunter`, see [bosses.md](bosses.md)). The
  exit is an ordinary one.

## The Belly

**XFTL_BELLY.** The most impassable sector in the game: a tear in the
galaxy's arm where a supernova's remnant, a pulsar, an asteroid belt and a
nebula all grind against each other. Most beacons have all four at once,
and the rest three (`xftlHazards="apocalyptic"`). It's only in sectors 11-15
(`xftlSectors="11-15"`), on the way to the last stand, and the Rebel fleet
waits at its edge to see whether the player comes out.

- **Start:** the end of the charts (the start beacon has no hazards).
- **Events:** empty beacons, a graveyard of ships that didn't make it
  through (a high reward, or a smaller one and hull damage, or a reactor
  going critical), a Rebel scout sent in after the player (fight it, or let
  it go and the fleet catches up), and the eye of the Belly, a calm beacon
  (`xftlNoHazards`) where the crew can repair the hull or chart the whole
  sector. Plus Rebel and pirate fights, a pirate store half the time, and
  the big ship fights.
- **The hunter:** no Rebel fleet here; the Ossuary, CONCORD's size 3
  salvage ark, hunts the player instead, a beacon every two jumps
  (`xftlHunter`, see [bosses.md](bosses.md)). The exit is an ordinary one.

## Crystal Homeworlds

**CRYSTAL_HOME.** The vanilla sector, which vanilla only lets the player
reach through a secret sector (which XFTL doesn't support yet). It can now
turn up once in act 2, as a hostile sector (`xftlAct="2"`): it's on about
80% of maps, and a random path goes through it about a fifth of the time.
Its events are vanilla's, plus the new crystal events.

## The nebula sectors

**XFTL_RADIATION_FIELDS, XFTL_MIRAGE_EXPANSE and XFTL_DARK_REACH.** Nebula
sectors where 90% of the beacons are one of XFTL's own kinds of nebula
(`xftlNebula`, see [nebulas.md](nebulas.md)), whatever their events. Every
beacon has hazards (`xftlHazards="true"`), some of them two or three at
once (see [extreme-hazards.md](extreme-hazards.md)), except the start,
stores, quests, and each sector's peaceful events (`xftlNoHazards`), which
only have their nebula. Unlike
the other new sectors, they can turn up in either act, from sector 5
(`xftlSectors="5-"`). A random path through the map goes through about 1.3
of them a game. Each has a start event about the sector, the three events of its
kind of nebula, three events of its own, and vanilla nebula events.

- **The Radiation Fields** (radiation): a supernova seeded every nebula with
  radioactive gas, so the medbay will be busy. A mining colony out of
  anti-radiation drugs (a blue option with a level 2 medbay), a pirate base
  hidden in the radiation, and Rockman pilgrims bathing in it (a blue option
  with Rockman crew).
- **The Mirage Expanse** (mirage): nebulas full of sensor ghosts, so the map
  can't be trusted. A distress signal that may not be real (a blue option with
  level 2 sensors), Slug illusionists who project a phantom fleet to slow the
  Rebels down (a blue option with Slug crew), and a reflection of the
  player's own ship.
- **The Dark Reach** (dark): nebulas too thick for light, so drones don't
  work anywhere in it. A lighthouse keeper with charts of the sector, Lanius
  feeding on the hull (a blue option with Lanius crew), and a blind trader
  who can be cheated (or not).

## Changes to the vanilla sectors

- **The story:** each sector can have one omen of CONCORD in act 1 (at most;
  `XFTL_OMENS`), and has one of CONCORD's events in act 2 (`XFTL_CHORUS`), in
  `events.xml.append`. See [lore.md](lore.md).
- **More events:** each faction's sectors get a list of new events
  (`XFTL_MORE_*`, in the `events_*.xml.append` files), at most one per sector
  in act 1, and two to four per sector in act 2.
- **Events for big ships:** civilian, hostile and nebula lists
  (`XFTL_BIG_*`), at most one per sector in act 1, and four to six in act 2.
- **Nebulas:** the Nebula sectors get radiation and dark nebulas (and mirages
  in act 2), and the Slug sectors get mirages.
- **Hazards:** asteroid fields, suns and pulsars are stronger in act 2, and
  nebulas are thicker. See [lore.md](lore.md) and [nebulas.md](nebulas.md).
