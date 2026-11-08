# The bosses (a design)

The Chonkestrel campaign's bosses: one at the end of act 1, one for each of
the three hazardous sectors, and CONCORD's Mainframe at the end of the game.
The four sector bosses are in the game (`ColossalBoss`, see
[How it's made](#how-its-made)); the Mainframe is [put off](#the-mainframe).

They're all **size 3**: bigger than the Chonkestrel (size 2), so they're the
only fights where the player is the smaller ship. They're thick, packed with
weapons and crew, and each one belongs to its place in the story (see
[story.md](story.md) and [lore.md](lore.md)).

The four sector bosses come in four tiers. The Maw comes before the Blockade
Line (sectors 5-8, mostly 5), so its boss is the first and easiest:

| Tier | Boss | Where | Who | The idea |
|---|---|---|---|---|
| I | [The Devourer](#the-devourer) | The Maw's exit (sectors 5-8) | A Lanius hive-ship grown from the Maw's wrecks | It eats asteroids, there's no air aboard, and it boards and mind controls |
| II | [RSV Vigilance](#the-rsv-vigilance) | The Blockade Line's exit (sector 8) | Rebel command dreadnought, its crew the first to join the Chorus | A wall: two shields systems and two Zoltan shields, and a crew that acts as one |
| III | [The Stormbreaker](#the-stormbreaker) | The Gullet's exit (sectors 9-12) | CONCORD's Mantis hunter flagship, built to follow its prey into the storm | A swarm: two teleporters, forty crew, and a relay that links them |
| IV | [The Ossuary](#the-ossuary) | The Belly's exit (sectors 11-15) | CONCORD's Engi salvage ark, rebuilding the graveyard's dead ships | Drones: three drones systems, inside and out, that it never runs short of |
| - | [The Mainframe](#the-mainframe) | Sector 16, instead of the Last Stand | CONCORD's core, a station the size of a moon | Size 4, swarming with drones, crewed by every race; three phases: a wall of shields that comes back, bays that build drones, and a mind that turns the crew |

## What every boss shares

**Size 3.** A boss is both long and wide: its window is twice as wide and
260px taller (`ShipShape.COLOSSAL`, see [ship-shapes.md](ship-shapes.md)).

**Always one on one.** A boss fights the player alone: no wingmen, no
escorts, and the Rebel fleet doesn't join in either (later, it'll fire on
the player with an anti-ship battery, as vanilla's fleet does).

**Thick.** Each has a lot of hull for its sector (three times what a vanilla
ship of its role would have, as its size says), two or three shields systems,
and systems upgraded past vanilla's (six-bar cloaking, hacking, mind control
and so on, see the six-bar systems in [stores.md](stores.md)).

**Packed.** As many weapons as the Chonkestrel can carry (eight) or more,
every one of them a twin or a triple (see [weapon-sizes.md](weapon-sizes.md)),
more of them triples the stronger the boss (2, 4, 8 and 10 of them), in
three to five weapons systems, each with no more weapons than its eight
power runs,
two or three drones systems, and twenty to forty crew, so boarding them is
a battle of its own. Its rooms are big (3x3, 3x4, 4x4 and so on, as well
as vanilla's own), so it has room for them all without dozens of small
rooms.

**Zoltan shields.** Each has as many Zoltan shields (super shield rows) as
its tier: one for the Devourer, up to four for the Ossuary. The last two
(the Stormbreaker and the Ossuary) have the Zoltan Shield Bypass too, so
the player's own Zoltan shield doesn't stop their boarders or their mind
control.

**Boarders ready.** Its first boarding party (as many as fit in its
teleporter, up to half its crew) is already standing in its teleporter
when the fight starts, and goes as soon as the teleporter has power
(`ColossalBoss.lineUpBoarders`); after that, its crew board as any ship's
do. (Ordinary ships gather their boarders once the fight's started, and
send them once they're all in the teleporter.)

**Can't run, won't surrender.** As the flagship. Killing its crew wins too,
but with that many it's a long boarding fight.

**Art.** The bosses are made from vanilla's art, as the long, wide and hybrid
ships are: a vanilla hull stretched both ways (by `tools/ship_variants.py`,
taught a size 3 shape), or spliced from two (`tools/hybrid_ships.py`), with
their rooms laid out to match.

### Where they are

The Vigilance holds the Blockade Line's exits; the other three hunt the
player across their sectors.

**The Vigilance holds the exits.** The Blockade Line has three exit
beacons (`xftlExits`), at the end of the sector, all with the same event:
the blockade's command ship, the only way on to the next sector. They're
labelled EXIT on the map, as any exit, and their tooltips say the boss is
there until it's beaten. It's one
ship, whichever exit the player tries: met at one, and left behind, it's
waiting at the next one they come to, as they left it.

- **It holds the exits.** The FTL drive can still jump to the sector's other
  beacons during the fight (to get away and repair), but not out of the
  sector until the boss is beaten. The jump window says why.
- **It mends while the player's away.** Every jump the player makes away
  from it, it repairs 10% of its hull (never past full) and its systems.
  Its dead crew stay dead.
- **The fleet doesn't take it.** If the Rebel fleet overtakes an exit, the
  boss is still there (even if the player hadn't got there yet), alone.
- **Once it's beaten, the only way is on.** The Rebel fleet surges forward,
  right up behind the player (at least four jumps' worth at once), and takes
  the Blockade Line's other exits, which have the fleet's ships like any
  beacon it's overtaken: the player leaves from the exit they beat it at
  (`InGameState.exitHoldBroken`).
- **No way round.** Its cloak choice is an ambush instead (its shields
  start ionised, so the player's first volleys get through), and the big
  ships' "built to break blockades" choice starts the fight with its Zoltan
  shields down (`<xftlBossOpening>`: `ambush` and `noSuperShield`).

**The others hunt the player** (`xftlHunter`, `HunterBoss`). In the Maw,
the Gullet and the Belly, there's no Rebel fleet: the boss is what comes
after the player. The exit is an ordinary one, and the player can leave
through it without fighting (the hazardous sectors are optional anyway),
giving up the boss's rewards.

- **It sets out from the exit,** and jumps a beacon at a time towards the
  beacon the player was at, as the flagship does in the last stand, and
  drawn the same way on the map: circling its beacon, with the line it'll
  jump along, wide when it's jumping with the player's next jump. The
  Devourer and the Ossuary jump every second turn, the Stormbreaker (built
  to follow its prey into the storm) every turn.
- **When it ends up where the player does, they fight.** The first time,
  its event is shown, in place of the beacon's own; after that, it's the
  same ship, as the player left it, which travels with it.
- **It brings its environment with it:** the beacon it's at has the boss's
  own hazards (see the table below), whatever it had before, and gets them
  back once it's gone.
- **It mends while it waits:** on each of the player's jumps it doesn't
  jump itself, it repairs 10% of its hull and its systems. The Stormbreaker,
  always on the move, never does.
- **Once beaten, it's gone,** and the rest of the sector is the player's
  to explore at leisure.

**The beacon suits it.** A boss's beacon has the boss's own environment,
whatever the sector would roll for it (its event's `<environment>`, with
`xftlHazards` for the hazards on top, see `GameMap.addHazards`, and for a
hunter, `HunterBoss.environment`). The Vigilance's is an ordinary beacon;
the others' are a plain nebula (never a random kind: a dark one would stop
the drones) and one, two or three more hazards, the harder the boss:

| Boss | Beacon |
|---|---|
| The Devourer | a nebula and asteroids (which it eats) |
| RSV Vigilance | ordinary |
| The Stormbreaker | an ion storm (which charges its ion weapons), asteroids and a pulsar |
| The Ossuary | a nebula, asteroids, a sun and a pulsar |

The hazardous sectors themselves stay optional, as they are now: a player
who doesn't want to fight the Devourer goes round the Maw. Every game goes
through the Blockade Line, so every game fights the Vigilance.

### Faster pursuit

In the Blockade Line, the Rebel fleet advances 1.15 times as fast
(`xftlFleetSpeed`, a multiplier on `Sector.DANGER_ZONE_ADVANCE`). Nebulas
still slow it as they do now, and `modifyPursuit` still doubles or stops
it. The sector's check that the player can reach the exit before the fleet
(`Sector`'s placement of the exit) uses the faster speed, so the exit is
never cut off from the start. The hunters' sectors have no fleet at all.

### Rewards

A boss's reward is the best of its sector, scaled with its tier. Every
boss gives all of this at once, in the reward window (which grows to fit
it; anything that doesn't fit the ship goes to the overflow, see
`Ship.overflowItems`):

| | I: Devourer | II: Vigilance | III: Stormbreaker | IV: Ossuary |
|---|---|---|---|---|
| Scrap | 150 | 200 | 300 | 400 |
| Fuel | 6 | 8 | 10 | 12 |
| Missiles | 6 | 8 | 10 | 12 |
| Drone parts | 4 | 6 | 8 | 10 |
| Weapons | 1 twin | 2 twin | 1 triple, 1 twin | 2 triple |
| Drones | 1 | 1 | 1 | 2 |
| Rare augments | 1 | 1 | 1 | 2 |
| Its own augment | yes | yes | yes | yes |

- **Scrap, fuel, missiles and drone parts** are for a size 2 ship, and
  scale with the player's ship size as the events' rewards do
  (`xftlScale="size"`, which multiplies by the size): half of it for a
  size 1 ship.
- **Weapons:** the first is always the boss's signature weapon (listed
  with each boss), and the rest are random twin or triple versions of the
  stronger vanilla weapons (the top half by price, see
  [weapon-sizes.md](weapon-sizes.md)), the higher the tier, the stronger.
- **Drones:** a random drone blueprint, from the rarer ones (rarity 3 and up:
  Combat II, Beam, Defence II, Anti-personnel, Hull Repair, Shield).
- **Rare augments:** a random augment of rarity 3 and up (vanilla has
  only a few of 4 and 5), never one the player already has (unless it
  stacks), and never a race's augment.
- **Its own augment:** unique to the boss, found nowhere else (listed with
  each boss).
- **A Zoltan Shield,** a third of the time, on top of the rest (they stack;
  not once the ship has six).

## The Devourer

**Tier I: the Maw's exit (sectors 5-8, act 1).** The Maw is a dying star, a
pulsar and a wrecked nebula churning through an asteroid belt, full of
wrecks. The Lanius found it before anyone: a hive-ship that has grown by
eating the wrecks, welding their hulls onto its own until nobody can say
what it was. It's an omen: among the hulls it's eaten is a Rebel long ship
whose comms still hum, which the Lanius won't go near.

| | |
|---|---|
| Hull | 90, and it heals (see below) |
| Shields | one system, 3 layers, and a Zoltan shield |
| Weapons | 8 (2 triples): ion weapons, breach and ion bombs, salvaged lasers and missiles |
| Drones | anti-ship and defence drones, salvaged from wrecks |
| Crew | 20 Lanius |
| Systems | no oxygen system (the Lanius don't breathe), 4-bar hacking, a teleporter, mind control |
| Art | the Lanius bomber, stretched wide and long |

**Feeding.** Its beacon is an asteroid field, and every asteroid that hits
it repairs a point of its hull rather than hurting it (it eats them).

**The hive's reach.** As the Lanius ships do, it boards (one big
teleporter) and mind controls, and its boarders drain the air from the
player's rooms as they go.

**No air.** It has no oxygen at all, so boarders suffocate (as in a vacuum) unless
they don't breathe: the Lanius' own crew don't, Rockmen hold out longer,
and the Oxygen Masks augment helps. Its fires never burn.

**Signature weapon:** a Twin Ion Blast II.

**Its own augment:** the hive-core: one asteroid in four that hits the
player's ship repairs a point of hull instead. And a Lanius crew member
asks to leave with the player.

## The RSV Vigilance

**Tier II: the Blockade Line's exit (sector 8), the end of act 1.** The Rebel
blockade's command ship (it's there now as an ordinary elite Rebel ship,
`XFTL_BLOCKADE_COMMAND`). Its crew were the first anyone saw join the Chorus:
they don't answer hails, and they answer a boarding party all at once, in
the same voice. Beating it is the reveal (`XFTL_BLOCKADE_REVEAL`).

| | |
|---|---|
| Hull | 120 |
| Shields | two systems, 3 layers each |
| Weapons | 10 (4 triples): heavy and burst lasers, a twin Breach missile, ion bombs |
| Drones | two systems: defence drones, and a pair of combat drones |
| Crew | 24, all human (Rebel officers), linked: they act as one |
| Systems | 3-bar cloaking, 3-bar hacking, 2-bar teleporter, two engines |
| Augments | two Zoltan shields |
| Art | the Rebel bomber, stretched wide and long |

**The wall.** Two shields systems behind two Zoltan shields: the player has
to strip its super shield before anything else gets through. A heavy
cruiser's answer stalls its Zoltan shield generators, so the fight starts
with them down.

**One voice.** While its crew are linked, they fight better (their combat
skill counts as full) and repair a little faster. Kill a third of them, and
the link breaks: the rest are stunned for a few seconds, and fight as
ordinary crew.

**Signature weapon:** a Twin Heavy Laser II.

**Its own augment:** the Vigilance's command codes: Rebel automated ships in
act 2 hesitate (their weapons charge 20% slower). And the reveal.

## The Stormbreaker

**Tier III: the Gullet's exit (sectors 9-12, act 2).** The storm-torn edge of
the galaxy's arm is too loud for the Chorus, so CONCORD can't follow anyone
into it, except with this: a hunter flagship that carries its own relay, a
dish the size of a station, so its crews stay linked inside the storm. It's
what leads the hunter group the Gullet's pursuers belong to, and it waits
at the way out.

| | |
|---|---|
| Hull | 160 |
| Shields | two systems, 4 layers each, and Reverse Ion Field |
| Weapons | 12 (8 triples): ion weapons (it hunts by stripping shields), triple burst and heavy lasers, a twin hull laser, bombs |
| Drones | two systems: ion intruders and defence drones |
| Crew | 40: 30 Mantis and 10 Rockmen, linked |
| Systems | 4-bar mind control (the Chorus Relay), 4-bar cloaking, two teleporters, two medbays |
| Augments | three Zoltan shields, Zoltan Shield Bypass, Reverse Ion Field |
| Art | the Mantis bomber, stretched wide and long |

**The Chorus Relay.** Its mind control system, the dish in its middle. While
it works, the crew are linked (as the Vigilance's, and more: its mind
control waits half as long between uses). Break it, and the crew fall
apart: stunned for ten seconds, then ordinary, until it's repaired.
Boarding the relay room is the fastest way to win.

**Storm-rider.** Its beacon is an ion storm, and it charges its ion weapons
from it: they charge half as fast again, while the player's ships lose
power to it as usual.

**The swarm.** Two teleporters, in big rooms. Its crew's AI boards with the
first, as any ship does, and the second sends a wave of its crew (up to
six) straight onto the player's ship as the fight starts, and every 25
seconds after while it works,
keeping twelve aboard to crew it. With forty of them, the player's crew
fight wave after wave, until the second teleporter's broken.

**Signature weapon:** a Triple Burst Laser II.

**Its own augment:** a shard of its relay: the player's crew can't be mind
controlled for the first 10 seconds of a fight.

## The Ossuary

**Tier IV: the Belly's exit (sectors 11-15, act 2).** At the Belly's far edge,
where the wrecks it has swallowed end up, CONCORD's salvage ark is
rebuilding the graveyard's dead ships into swarms of drones, and new
hybrids, to send out into the galaxy. The flagship's scout that followed the player
in (`XFTL_BELLY_FLAGSHIP_SCOUT`) was one of them. The Belly's eye
(`XFTL_BELLY_EYE`) stays what it is now: a calm beacon to repair at before
the fight.

| | |
|---|---|
| Hull | 200 |
| Shields | three systems, 3 layers each, and four Zoltan shields |
| Weapons | 12 (10 triples): beams (the Halberd, fire, anti-bio and hull), heavy lasers, bombs |
| Drones | three systems, eight drones: combat (Mark II and beam), shield drones (they refill its super shield) and defence, two boarding drones, and inside, anti-personnel and repair drones |
| Crew | 30, of every race, spliced |
| Systems | 4-bar teleporter, 4-bar hacking, three drones systems (the first is the slipway) |
| Art | the Engi bomber, stretched wide and long |

**The slipway.** Its first drones system rebuilds its drones from the
wrecks: while it works, the Ossuary never has fewer than ten drone parts,
so it relaunches every drone the player shoots down. Breaking it stops
that until it's repaired.

**Signature weapon:** a Triple Halberd Beam.

**Its own augment:** its schematics: a destroyed enemy ship's drones are
salvaged as drone parts (one for each drone it had).

## The Mainframe

It's in the game, in place of the flagship, with its three phases. Its
design, with sector 16 around it, is in [mainframe.md](mainframe.md); in
short:

**Sector 16, instead of vanilla's Last Stand.** CONCORD's core: a station the
size of a moon, at the Rebel capital (see [story.md](story.md#sector-16-the-mainframe)).
Signal relays at some beacons feed it power, and each one destroyed takes
away one of its defences. It doesn't move, so unlike vanilla's flagship, it
doesn't jump away between phases: each phase is another layer of it.

| Phase | Hull | What it is |
|---|---|---|
| 1. The Firewall | 150 | The outer shell: three shields systems, a super shield, defence drones, hacking |
| 2. The Splicing Bays | 150 | The yards inside: it launches hybrids, two at a time |
| 3. The Mind | 200 | The core: mind control pulses, spliced boarders, mirages (and sanity, once there is any) |

## Tuning

The numbers here are a start, to be tuned in playtests (the automatic
playtest can fight them with `XFTL_DEBUG`'s fight commands). A boss
should be the hardest fight of its sector, but not a wall:

- At the Devourer (sector 5-7), the Chonkestrel has about 3-5 weapons and
  one or two shields systems. It should take about a minute and a half, and
  15-25 hull off the player.
- At the Vigilance, the Chonkestrel has about 4-6 weapons, two shields
  systems at 2-3 layers, and 60-70 hull. It should take about a minute and
  a half to two minutes, and 20-30 hull off the player.
- The Stormbreaker and the Ossuary are for players who chose the harder
  road, each a step up from the one before; about two to three minutes.

## How it's made

- **Size 3 ships** (`ShipShape.COLOSSAL`): long and wide, so their window
  (`HostileShipUI`) is as tall as a long ship's and two places wide, with
  a hull bar of a point a cell, or more. A size 3 ship is never in a
  random fight (`FightPlan`), and always fights alone.
- **Their blueprints** (`<xftlBoss>`, `ShipBlueprint.bossKind`) are built
  exactly as they say (`ShipGenerator.buildBoss`): their weapons, drones,
  systems' power, reactor and the most crew they allow. They're made by
  `tools/boss_ships.py` (see below), into the mod's `autoBlueprints.xml.append`.
- **Their mechanics** are `ColossalBoss`, kept with the ship (`Ship.colossal`)
  and saved with it: the linked crew (`LivingCrew`'s skills), the
  Stormbreaker's swarm (its second teleporter, `Ship.teleporterSystems`),
  the Ossuary's slipway, the repairs, and the openings.
- **At the exit:** a sector with a boss names its event
  (`xftlFinishEvent`). Where it holds the exits (`SectorType.bossHoldsExit`,
  with `xftlExits` of them), each exit has that event (which sets the
  beacon's environment), gets no hazards of its own (`GameMap.addHazards`),
  has the boss in its map tooltip until the boss is beaten
  (`InGameState.isExitBossBeaten`), and the next sector button isn't there
  while it's fighting (`InGameState.isHeldByBoss`). Its ship moves to
  whichever exit the player comes to (`InGameState`'s arrival). Each jump
  away, it mends (`InGameState.advanceFleet`).
- **Hunting:** a sector's `xftlHunter` (`SectorType.hunterPeriod`) makes its
  boss a `HunterBoss` (a `BossManager`, as the flagship is, drawn with the
  same `BossMapIcon`), set up in `GameMap.generateSector`. Its sector has
  no fleet (`SectorType.hasFleet`): `InGameState.advanceFleet` moves it
  instead, and the arrival shows its event. Its ship stays at its beacon
  (`Beacon.ship`), and moves with it; its environment is
  `Beacon.hunterEnvironment`. `InGameState.colossalBeaten` tells it when
  it's beaten.
- **Faster pursuit:** a sector's `xftlFleetSpeed`, in `Sector.getFleetAdvanceFor`.
- **Rewards:** `<xftlBossReward tier= weapon= augment=>` (`BossRewards`).
- **Their own augments:** `XFTL_HIVE_CORE` (`Ship.eatsAsteroid`),
  `XFTL_COMMAND_CODES` (`Ship.weaponChargeFactor`), `XFTL_RELAY_SHARD`
  (`MindControl.selectRoom`, from `Ship.hostileTime`) and
  `XFTL_OSSUARY_SCHEMATICS` (`InGameState.countDrones`).

### The art

`tools/boss_ships.py` makes each boss from a vanilla ship's hull, stretched
twice with `tools/ship_variants.py`'s functions: wide (columns put in),
then long (rows put in). The vanilla rooms stay as they are; the new part
of the hull is split into big rooms (4x4, 4x3, 3x4, 3x3, 3x2, 2x3, and
2x2 only where nothing bigger fits), as few as cover it, its teleporters
in the biggest. The boss's systems go in the vanilla rooms where the ship
had them, and the biggest of the rest, and it has a weapon mount for each
of its weapons. Its window holds a ship up to about 720x580 pixels.

```sh
python3 tools/boss_ships.py ftl.dat
python3 tools/boss_ships.py ftl.dat --preview out/
```

| Boss | From | Image | Rooms (with systems) | Cells | Crew |
|---|---|---|---|---|---|
| The Devourer | the Lanius bomber | 653x564 | 24 (12) | 129 | 20 |
| RSV Vigilance | the Rebel bomber | 642x573 | 17 (17) | 128 | 24 |
| The Stormbreaker | the Mantis bomber | 694x580 | 24 (19) | 174 | 40 |
| The Ossuary | the Engi bomber | 604x546 | 24 (19) | 158 | 30 |
| The Mainframe (the Firewall), size 4, four variants | the Rebel flagship and two races' bomber halves, glued | about 1080x511 | 32-41 (20) | 134-185 | 48 |
| The Mainframe (the Splicing Bays), size 4 | the automated basic ship | 1014x507 | 27 (21) | 244 | 48 |
| The Mainframe (the Mind), size 4 | the Slug cruiser | 1013x541 | 25 (17) | 242 | 48 |

The middle of each hull is stretched into a big plain plate, which its
rooms cover in the game; better art can replace it later.

### Testing

`ColossalBossTest` checks the mechanics on a small test boss, and
`BossesTest` the mod's four (their sectors, blueprints and rewards).
`XFTL_SCREENSHOTS_SET=boss` takes a screenshot of a fight with each (see
[screenshots.md](../tools/screenshots.md)).

### Still to do

- Tuning, in playtests: the numbers are a start.
- The Mainframe.
