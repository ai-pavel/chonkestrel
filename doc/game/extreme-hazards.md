# Extreme, catastrophic and apocalyptic hazards

An extreme hazard is a beacon with two hazards at once
(`Beacon.isExtremeHazard`), a catastrophic hazard one with three
(`Beacon.isCatastrophicHazard`), and an apocalyptic hazard one with all four
(`Beacon.isApocalypticHazard`), all run as a `MixedEnvironment`. They can
turn up anywhere, more and more often further into the game, and most of all
in the hazardous sectors: see [How often each turns up](#how-often-each-turns-up).

## The rules

- There are four kinds of hazard: a **nebula** (any of the five kinds), an
  **asteroid field**, a **sun** and a **pulsar**.
- A beacon has at most four (`Beacon.MAX_HAZARDS`), never two of a kind:
  no nebula with another nebula (such as an ion storm in a radiation
  nebula), no two suns. So there's at most one nebula.
- They all work at once, as they do on their own, with all their warnings.
  If a flare and a pulse are both about to hit, the pulse's warning goes
  under the flare's, and a nebula's warning moves over to make room.
- They're stronger than on their own (`AbstractEnvironment.intensity` and
  `hazardRate`): at least intensity 2 in either act, as in act 2 (see
  [lore.md](lore.md)), and suns flare and pulsars pulse twice as often.
- Power limits take the lower one, and effects on the FTL drive multiply.
- On the beacon map, they're marked with a red hazard icon (purple for three
  at once, cyan for four), and described as "EXTREME HAZARD" (or
  "CATASTROPHIC HAZARD", or "APOCALYPTIC HAZARD"), even before they're
  scanned.
- Three or four at once are as strong as two: what makes them worse is that
  there's more going wrong at a time.

So, in an extreme or catastrophic hazard:

| Hazard   | On its own (act 1)                                   | In an extreme hazard                                                              |
|----------|------------------------------------------------------|-----------------------------------------------------------------------------------|
| Nebula   | Sensors blocked, fleet slowed                        | The same, and thick: FTL drives charge at 75% (radiation does 1.2 health a second) |
| Asteroids| Asteroids at the vanilla rate                        | Asteroids twice as often                                                          |
| Sun      | A flare every 28-34s: 1-2 fires (3-6 without shields) | A flare every 14-17s: 2-4 fires (6-12 without shields)                            |
| Pulsar   | A pulse every 11-18s, ioning the shields and 1 system | A pulse every 5.5-9s, ioning the shields and 2 systems                            |

## Every pair

There are 18: each of the five nebulas with each of the other three hazards
(15), and the three pairs of the others.

### Nebula and asteroids

| # | Combination                    | What it's like                                                                                          |
|---|--------------------------------|---------------------------------------------------------------------------------------------------------|
| 1 | Nebula + asteroids             | Asteroids hit you without sensors to see the enemy, and the thick nebula slows your escape.              |
| 2 | Ion storm + asteroids          | Half the reactor's power, so the shields may not stay up against the asteroids.                          |
| 3 | Radiation nebula + asteroids   | Crew outside the medbay lose health while they repair the asteroid damage.                               |
| 4 | Mirage nebula + asteroids      | The beacon map lies about the beacons around you, on top of the asteroids.                               |
| 5 | Dark nebula + asteroids        | No drones, so no defence drones to shoot the asteroids down.                                             |

### Nebula and a sun

| # | Combination                    | What it's like                                                                                          |
|---|--------------------------------|---------------------------------------------------------------------------------------------------------|
| 6 | Nebula + sun                   | Fires every 14-17s, without sensors to see them spread on the enemy ship.                                |
| 7 | Ion storm + sun                | Half the reactor's power and frequent flares: hard to keep the shields up, and fires start without them. |
| 8 | Radiation nebula + sun         | Crew take radiation damage as they fight the fires.                                                      |
| 9 | Mirage nebula + sun            | Frequent fires, and a beacon map you can't trust.                                                        |
| 10| Dark nebula + sun              | No drones, so no repair or fire-fighting drones.                                                         |

### Nebula and a pulsar

| # | Combination                    | What it's like                                                                                          |
|---|--------------------------------|---------------------------------------------------------------------------------------------------------|
| 11| Nebula + pulsar                | Systems ioned every 5.5-9s, and no sensors.                                                              |
| 12| Ion storm + pulsar             | The worst for power: half the reactor, and pulses ioning three systems at a time.                        |
| 13| Radiation nebula + pulsar      | Ioned systems and radiation damage: the medbay may be ioned when the crew need it.                       |
| 14| Mirage nebula + pulsar         | Frequent ion pulses, and a beacon map you can't trust.                                                   |
| 15| Dark nebula + pulsar           | No drones, and the pulses ion what's left.                                                               |

### Without a nebula

| # | Combination                    | What it's like                                                                                          |
|---|--------------------------------|---------------------------------------------------------------------------------------------------------|
| 16| Asteroids + sun                | Asteroids twice as often wear down the shields, so the flares' fires start without them.                |
| 17| Asteroids + pulsar             | The pulses ion the shields, and the asteroids get through.                                               |
| 18| Sun + pulsar                   | Both warnings at once: pulses ion the shields, and flares start fires (6-12 without shields).            |

Every effect applies to the enemy ship too, as hazards do on their own: the
radiation, the dark nebula's drones, the ion storm's power, the asteroids,
the flares and the pulses all hit both ships (and any others at the beacon).

## Every triple

There are 16: each of the five nebulas with each pair of the other three
hazards (15), and all three of the others together. With the pairs, that's
34 combinations in all.

### Without a nebula

| # | Combination                    | What it's like                                                                                          |
|---|--------------------------------|---------------------------------------------------------------------------------------------------------|
| 1 | Asteroids + sun + pulsar       | The pulses ion the shields, and then both the asteroids and the flares get through: fires and breaches at once. |

### A nebula, asteroids and a sun

| # | Combination                    | What it's like                                                                                          |
|---|--------------------------------|---------------------------------------------------------------------------------------------------------|
| 2 | Nebula                         | Fires and asteroids, without sensors to see the enemy.                                                   |
| 3 | Ion storm                      | Half the reactor's power: the shields barely hold, and the flares start fires without them.             |
| 4 | Radiation nebula               | The crew lose health while they fight the fires and repair the breaches.                                 |
| 5 | Mirage nebula                  | The beacon map lies, and the enemy's weapons aren't what they look like.                                 |
| 6 | Dark nebula                    | No drones: no defence drones against the asteroids, and no repair drones.                                |

### A nebula, asteroids and a pulsar

| # | Combination                    | What it's like                                                                                          |
|---|--------------------------------|---------------------------------------------------------------------------------------------------------|
| 7 | Nebula                         | Systems ioned, without sensors.                                                                          |
| 8 | Ion storm                      | Half the reactor, and the pulses ion three systems at a time.                                            |
| 9 | Radiation nebula               | A pulse can ion the medbay just when the crew need it.                                                   |
| 10| Mirage nebula                  | Ioned systems, and an enemy firing something other than what you see.                                    |
| 11| Dark nebula                    | No drones, the shields ioned, and the asteroids getting through.                                         |

### A nebula, a sun and a pulsar

| # | Combination                    | What it's like                                                                                          |
|---|--------------------------------|---------------------------------------------------------------------------------------------------------|
| 12| Nebula                         | A pulse takes the shields down, and the next flare starts 6-12 fires.                                    |
| 13| Ion storm                      | The worst for power: half the reactor, ion pulses and fires.                                             |
| 14| Radiation nebula               | Fires and radiation together, and the medbay may be ioned.                                               |
| 15| Mirage nebula                  | Fires, ion pulses and a beacon map you can't trust.                                                      |
| 16| Dark nebula                    | No repair or fire-fighting drones.                                                                       |

Every effect applies to every ship at the beacon, as with two.

## All four

There are 5: each of the five nebulas, with asteroids, a sun and a pulsar.
With the pairs and triples, that's 39 combinations in all.

| # | Nebula                         | What it's like                                                                                          |
|---|--------------------------------|---------------------------------------------------------------------------------------------------------|
| 1 | Nebula                         | Pulses take the shields down, then asteroids breach the hull and flares set it on fire, without sensors. |
| 2 | Ion storm                      | All of that on half the reactor's power: the shields barely come back between pulses.                    |
| 3 | Radiation nebula               | Fires, breaches and ioned systems to fix, while the radiation wears the crew down.                       |
| 4 | Mirage nebula                  | All of it, and the enemy's weapons and the beacon map can't be trusted.                                  |
| 5 | Dark nebula                    | All of it, without a single drone to help.                                                               |

## How often each turns up

### Through the game

The game is split into quarters for the hazards, not counting the last
stand (`GameMap.hazardStage`): sectors 1-4, 5-8, 9-12 and 13-15 of sixteen.
The story explains why it gets worse (see [lore.md](lore.md)): with the
whole Rebel fleet behind it, the Chonkestrel takes the more dangerous routes
through the galaxy's arm.

In most sectors, as in vanilla, only the beacons whose events have a hazard
of their own (a nebula, asteroids, a sun or a pulsar) have hazards. Each of
them has one to four at once, counting its own, with these chances
(`GameMap.HAZARD_COUNT_WEIGHTS`):

| Sectors | One | Two (extreme) | Three (catastrophic) | Four (apocalyptic) |
|---------|-----|---------------|----------------------|--------------------|
| 1-4     | 85% | 12%           | 3%                   | never              |
| 5-8     | 60% | 27%           | 11%                  | 2%                 |
| 9-12    | 35% | 35%           | 23%                  | 7%                 |
| 13-15   | 15% | 30%           | 37%                  | 18%                |

So early on, three at once is a rare surprise and four never happens, and
by the end of the game, most hazardous beacons have three or four.

### Hazardous sectors

In the hazardous sectors (`xftlHazards="true"`), every beacon has hazards:
the Radiation Fields, the Mirage Expanse, the Dark Reach and the Maw.
The other nebula sectors (vanilla's) are left as
they are, like any other sector. A beacon whose event has no hazard of its
own gets the sector's own kind 75% of the time (`SECTOR_THEME_CHANCE`): its
nebula, or whatever's most common there, and a random one otherwise. Then,
more of them are at once (`HAZARD_SECTOR_COUNT_WEIGHTS`):

| Sectors | One | Two (extreme) | Three (catastrophic) | Four (apocalyptic) |
|---------|-----|---------------|----------------------|--------------------|
| 1-4     | 60% | 30%           | 10%                  | never              |
| 5-8     | 35% | 40%           | 22%                  | 3%                 |
| 9-12    | 15% | 35%           | 40%                  | 10%                |
| 13-15   | 5%  | 25%           | 45%                  | 25%                |

The Maw only turns up in sectors 5-8, so it uses the second row, and
the nebula sectors from sector 5, so they never use the first.

### The Gullet and the Belly

The two most dangerous sectors have their own chances, wherever they are:

| Sector                                    | Where | Two (extreme) | Three (catastrophic) | Four (apocalyptic) |
|-------------------------------------------|-------|---------------|----------------------|--------------------|
| The Gullet (`xftlHazards="catastrophic"`) | 9-12  | 20%           | 70%                  | 10%                |
| The Belly (`xftlHazards="apocalyptic"`)   | 11-15 | never         | 25%                  | 75%                |

(`CATASTROPHIC_COUNT_WEIGHTS` and `APOCALYPTIC_COUNT_WEIGHTS`.) See
[sectors.md](sectors.md).

Whatever the sector, four at once never happen in the first quarter. And the first
sectors have fewer at once, whatever the sector's weights
(`GameMap.maxHazards`): one at most in the first sector, two in the
second, three from the third and four from the fifth. On easy, that's a
sector later, and on hard a sector sooner. Events' boarders are kept to two
at most in the first two sectors, likewise (`Event.EARLY_BOARDERS`).

### Fuel

In the Maw, the Gullet and the Belly, every jump takes a unit more
fuel than usual, as the engines fight the hazards (`xftlExtraFuel`, see
`Ship.fuelPerJump`): three for the Chonkestrel rather than two, the jump on
to the next sector included (which takes fuel, as in vanilla). Their start
events say so.

### What's left out

The start beacon never has a hazard, in any sector (the last stand
included): at most a plain nebula (see `GameMap.calmStart`), which any
other kind of nebula, or an ion storm, becomes. Its event is the sector's
own, and only describes it (`StartEventsTest`): the vanilla sectors that
shared one have their own (`XFTL_START_*`), each hybrid sector has one
about its two races (see [sectors.md](sectors.md#the-hybrid-sectors)), and
the Radiation Fields, the Mirage Expanse and the Dark Reach start at their
edge, in a plain nebula. The last stand keeps vanilla's welcome from the
Federation.

Stores, quests, the events marked `xftlNoHazards` (such as
the Maw's survey station, the Belly's eye, or the nebula sectors'
peaceful events, which keep only their nebula) and the last stand don't get
any more hazards. So even the most dangerous sectors have calm beacons: the
Maw always has a store (and sometimes a pirate one too), the Gullet has one half the time and the Belly a pirate one half the time, and
they all have a calm beacon with a special event.

Quests can go to any beacon but a nebula, as in vanilla, but only the
beacon's own nebula counts: one that came from the sector doesn't, as a
quest beacon loses the sector's hazards (`Beacon.isOwnNebula`).

For a beacon with fewer hazards than it rolled, each extra one is a kind it
doesn't have yet, picked at random, and a nebula is any of the five kinds
equally.
