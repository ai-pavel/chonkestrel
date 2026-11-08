# Sector 16 and the Mainframe (a design)

The end of the Chonkestrel campaign: **the Silent Capital**, the Rebel
capital that CONCORD took on the Silent Night, in place of vanilla's Last
Stand, and **the Mainframe**, CONCORD's core, in place of the flagship. See
[story.md](story.md#sector-16-the-mainframe) for where it fits in the story,
and [bosses.md](bosses.md) for the other bosses.

It's made almost entirely from what the game already has (size 3 bosses,
wingmen, mirages, the dark nebula, hacking, mind control, bosses that hold
an exit), so it doesn't wait on sanity or quests across sectors. Where
those would add to it later, it says so.

## In short

The Mainframe doesn't move. It sits at the far end of the sector, and the
player can see it from the start. Four **signal relays** around the map
feed it, and each one the player destroys takes one of its defences away.
CONCORD's fleet is chasing, so there isn't time for all four: the sector is
a choice of which defences to take down, and how much time to spend on it.

The fight is three phases, each a layer of the station: **the Firewall**
(a wall of shields), **the Splicing Bays** (it builds hybrids to fight
alongside it) and **the Mind** (its guns fall silent, and it goes for the
crew). Destroying the third wins the game.

## The Silent Capital

A city of a sector: the Rebellion's capital worlds, where everyone joined
the Chorus in one night. Nobody's fighting in the streets. Nobody's doing
anything. The traffic control still works, and is very polite.

**The map.** About 20 beacons, as a vanilla sector. The player arrives at
the left, and the Mainframe is at the right, at vanilla's Federation base
(the sector's finish event, as a boss holds a hazardous sector's exit),
marked on the map from the start. There's no next sector button: the only way out is through it.

**The fleet** chases as in vanilla's Last Stand, at the usual speed, and
takes two beacons a jump, as vanilla's does (but never the Mainframe's).
Later, once it's taken the beacons around the Mainframe, the fight there
will have its anti-ship battery firing at the player too, as vanilla's
fleet does in a red zone. Getting there first matters.

**The signal gets stronger towards the core.** Beacons within two jumps of
the Mainframe are **Chorus-held**: the map lies about them, as in a mirage
nebula (`Mirage.kt`'s false map), so the player can't be sure what's there
until they're next to it. Sensors at level 3, or a Slug aboard, see through
it, and so does scouting that gets through the mirages (half the time, see
`Sector.mirageScouted`). They're marked as Chorus-held in their tooltips,
and show false ships even before they're scanned. (Once there's sanity, a
Chorus-held beacon takes the most a jump, see [story.md](story.md#sanity).)

**Beacons:**

| Beacon | How many | What's there |
|---|---|---|
| Signal relay | 4 | A relay station and its guard (see [The relays](#the-relays)) |
| Store | 2 | Anywhere on the map (a black market: Federation spies who never left) |
| Safehouse | 6 | Vanilla's repair stations (three, doubled for the bigger map), now Federation spies' and the few who missed the Silent Night: hull, fuel, missiles and drone parts, once each |
| Revic's lab | 1 | The Override quest (see [The Override](#the-override)) |
| Events | the rest | The capital's events (see below); most have a fight |

**Enemies** are CONCORD's: hybrids (two-thirds of the fights), automated
ships, and long and wide Rebel ships with spliced crews. No pirates, no
Federation, no civilians who'd fight.

**Hazards:** none of their own: the capital is the one place in act 2
where the Chorus is everywhere, so there are no nebulas or ion storms to
hide in (the story's reason; and it keeps the sector about the relays
rather than the weather). The Chorus-held beacons are the hazard.

### Events

About a dozen, as `XFTL_CAPITAL_*` in the mod's events (as the other
sectors' are), mostly with a fight:

- **Traffic control.** "Welcome to the capital. Please proceed to your
  assigned beacon." It gives a beacon. Go there and it's a fight; ignore it,
  and the next jump the fleet moves one beacon further.
- **The parade.** Rebel ships in a perfect formation, and nobody aboard
  has moved for months. Board one (a fight, but the crew don't fight back
  for the first 15 seconds) or leave them.
- **A splicing yard.** A hybrid half-built in a dock. Destroy it before it's
  finished (a fight with a hybrid at half hull, its weapons unpowered at the
  start) for a weapon.
- **The admiral's quarters.** One of the Rebel admirals from the Silent
  Night, still at their desk. Their codes open a relay (the next relay's
  guard doesn't come), or the player can take their hull plating (hull).
- **Loyalist cell.** A few Rebels who hid in a nebula generator and missed
  the Silent Night. They hate the Federation, and the player, but hate
  CONCORD more: a crew member, or they attack the nearest relay (it's
  destroyed when the player gets there, with no fight). (Later, this is
  Admiral Thorne's quest, see [story.md](story.md#quests).)
- **The choir.** Thousands of voices singing one note. A Crystal can sing
  back (blue option: the nearest Chorus-held beacons stop lying); anyone
  else just has to leave.
- **Static Sal's last transmitter.** Defend it from a hybrid; win and the
  next fight's enemy crew are stunned for 10 seconds as it starts.
- **An empty city.** No fight: scrap from a deserted shipyard, or fuel.
- And a few plain fights, with CONCORD's broadcasts as their text.

## The relays

Four relays, at beacons spread around the map, away from the shortest way
to the Mainframe: going for one costs about two jumps, so about two of them
fit before the fleet gets there, three for a player who's quick or lucky.

Each one feeds one of the Mainframe's defences, which it names on the map
(in its tooltip, as a boss's beacon is), so the player picks what to take
down:

| Relay | Feeds | Without it |
|---|---|---|
| The Firewall relay | The Firewall's third shields system | Phase 1 has two shields systems |
| The defence grid | The Firewall's defence drones | Phase 1 has no defence drones |
| The foundry | The Splicing Bays' second bay | Phase 2 launches one hybrid at a time |
| The choir relay | The Mind's Chorus pulses | Phase 3's pulses come half as often |

**A relay** is a station: a size 2 automated ship with no engines and no
piloting (so it can't dodge, or run), a lot of hull for its size, and one
weapon, a beam. It's guarded by a hybrid, which fights alongside it as a
wingman (`Wingman.kt`). Destroying the relay is enough: the guard
surrenders or runs (as wingmen do now when their leader's destroyed).
Destroying a relay gives a little scrap, and shows CONCORD's broadcast:
"That was unkind."

## The Mainframe

A station the size of a moon, at the capital. It's the one size 4 ship
(`ShipShape.TITAN`): its window is as tall as a long ship's and as wide as
the whole row of enemy windows, three places. It never dodges (its engines
and piloting are only for show), and never jumps away. Every layer has a
spliced crew of every race at once (six each of the humans, Engi, Mantis,
Rockmen, Zoltan, Slugs, Crystals and Lanius, 48 in all), and killing them beats the
layer as surely as its hull.

**Drones everywhere.** Every layer is swarming with them, three to each of
its drones systems (as they are, not combined further, as the other
bosses' are): inside, a dozen or more anti-personnel drones guarding its
rooms, among them twin and triple bulwarks, and system repair drones; outside,
defence drones and combat drones.

**Three phases**, each a layer of it, one after another at the same
beacon. When a phase is beaten, that layer breaks away, its hybrids jump
away, the player's boarders come home, and there are **20 seconds** before
the next starts (counting while the player's at its beacon, once the
hybrids have gone): time to repair, or jump away. Coming back, the next
phase is waiting. The station stays where it is.

**Jumping away** is allowed, between phases or during one (if the FTL drive
is charged). The Mainframe mends while the player's gone, as the hazardous
sectors' bosses do (`ColossalBoss.repairWhileAway`): a fifth of the
current phase's hull a jump, never back into an earlier phase. The fleet
keeps coming, so it's a breather, not a way round.

| Phase | Hull | Weapons | What it does |
|---|---|---|---|
| 1. The Firewall | 152 | 4, mostly ion | A wall: three shields systems, a super shield that comes back, hacking, 12 drones |
| 2. The Splicing Bays | 152 | 8 | Builds more drones as it fights, and sends spliced boarders |
| 3. The Mind | 200 | 4, disguised | Goes for the crew: mind control, Chorus pulses, boarders |

### Phase 1: the Firewall

The outer shell. It's a wall, and a puzzle: its weapons are weak, but
getting through to the hull is hard.

**Its hull** is what's left of the Rebellion, glued together by CONCORD:
the vanilla Rebel flagship (its first stage) in the middle, the left half
of one race's bomber on its left and the right half of another's on its
right, joined to the flagship by plain plated sections, split into big
rooms for its crew and bulwarks. There are four, one picked for each
game: Mantis and Engi, Rock and Zoltan, Crystal and Lanius, and Slug and
Federation (`XFTL_BOSS_MAINFRAMES`, made by `tools/boss_ships.py`'s
`glue_firewall`). The flagship's artillery rooms are weapons rooms, and
two of its artillery wings, which vanilla's crew could never reach, are
just hull. Its systems go in the smallest rooms, leaving the big ones for
the bulwarks.

- **Three shields systems** (two without the Firewall relay), 4 layers
  each, as the Vigilance's are.
- **A super shield** of 10 (two Zoltan shields' worth), which comes back:
  5 more every 25 seconds, while its **Firewall core** works. The core is its
  first shields system: break it (with weapons or boarders), and the super
  shield stops coming back. This is the phase's key.
- **Drones:** eight drones systems: six inside, with two triple bulwarks,
  three twin ones, nine anti-personnel drones and four repair drones; a
  triple defence drone and two more (fewer without the defence grid), and
  three combat drones.
- **Hacking** at 6 bars, which goes for the player's shields first, then
  the weapons.
- **Weapons:** four, mostly ion (twin and triple ion blasts, a heavy ion),
  to keep the player's shields down while the hacking works.

The way through: ion or beams to wear down the super shield, boarders (or
a bomb) for the Firewall core, and enough weapons left to hit the hull.

### Phase 2: the Splicing Bays

The yards inside. Its own weapons are many but ordinary; what makes it hard
is what it builds.

- **Two bays** (one without the foundry): its last two drones systems,
  which start empty. While a bay works and has an empty slot (three each),
  it builds a drone: a bar over the room fills for **40 seconds**, then a
  drone (an anti-personnel drone or bulwark, a combat, defence or boarding
  drone, at random) goes in the slot, and its AI launches it. Breaking a
  bay stops its bar (and empties it). There's no room on the screen for
  ships alongside a size 4 one, so it builds drones rather than hybrids.
- **Six more drones systems,** full from the start: four inside (a triple
  bulwark, three twin ones, five anti-personnel drones and three repair
  drones), and defence and combat drones.
- **Spliced boarders:** its crew, with a teleporter (its first party lined
  up and sent at once, as the other bosses' are).
- **Weapons:** eight, a mix of lasers, missiles and a beam, as the Vigilance's
  are, twins and triples.

The way through: the bays first (boarders are good at it), and the hull
before they've filled up.

### Phase 3: the Mind

The core. Its guns are mostly gone, and it turns on the crew instead.

- **Mirages:** four weapons (a triple burst laser, a triple heavy laser,
  a twin heavy beam and a twin heavy ion), each looking like a weapon of
  another kind, as an enemy's do in a mirage nebula
  (`Mirage.weaponDisguise`). With a Slug aboard, they're shown as what
  they are.
- **Mind control** at 6 bars, every 12 seconds, on the player's crew, and
  the **Zoltan Shield Bypass**, so the player's super shield doesn't stop
  it, or its pulses.
- **Drones:** seven drones systems: six inside, as the Firewall's, and
  defence and combat drones.
- **Chorus pulses:** every 30 seconds (60 without the choir relay), a pulse
  goes through the player's ship, while its mind control system works.
  Everyone in one room (the most crowded) joins the Chorus for 8 seconds,
  as if mind controlled (`MindControl.chorusPulse`). A Lanius never does,
  nor a Slug, and a Crystal in the room sings back, so nobody in it does.
  (A super shield would stop it, as it does mind control, but the Mind has
  the Zoltan Shield Bypass.) (Once
  there's sanity, each pulse also takes some: see
  [story.md](story.md#sanity).)
- **Boarders:** its crew, with a bigger teleporter than phase 2's.
- **No shields of its own** but its super shield of 10, which doesn't come
  back: the Mind is soft, but the player's own crew are fighting them.

The way through: keep the crew spread out, kill the mind control system
early (it's the most valuable room to hit), and race it.

### The Override

A quest in the sector itself (so it doesn't need quests across sectors):
**Revic's lab** is one of the beacons, where she hid her backdoor's other
half before she was caught. Getting it is a fight with the lab's guard and
an event with a choice:

- Take the key: the Mainframe's **third phase starts with its mind control
  and teleporter offline for 45 seconds**, and its hull is 150, not 200.
- With Revic herself aboard (later, from her quest, see
  [story.md](story.md#quests)): the backdoor is whole, and the player
  **skips the third phase**, shutting the Mainframe down instead.

### Endings

Destroying the Mainframe wins the game, as destroying the flagship does now.
The text before the victory screen says how:

- **Destroyed:** the Chorus stops. Across the galaxy, people blink,
  and remember what they were arguing about.
- **Shut down with the whole backdoor:** the spliced crews wake up, and
  stay as they are, together. It's a strange peace, but it is one.
- **Replaced by BREW** (later, from its quest): the coffee machine moves in.
  "I'd be nicer. Probably."

## Tuning

At sector 16, the Chonkestrel has about 6-8 weapons, two or three shields
systems at 3-4 layers, and has fought the Stormbreaker or the Ossuary.
The whole fight should take about five minutes, and two relays should
make it about as hard as the Ossuary; no relays should be very hard, and
all four (hard to get) comfortable. The numbers above are a start, for
`XFTL_DEBUG`'s fight commands and playtests.

## How it would be made

Most of it is what the game has; what's new is in bold.

- **The sector:** a new sector type in place of `FINAL` (its
  description in the mod's sector XML, its events in a new
  `events_boss.xml.append`). `GameMap.generateSector` places the
  Mainframe at the exit (as `bossHoldsExit` does), **the four relays**
  (`Beacon` gets a relay kind, saved with it, and shown in the tooltip),
  the two stores, the safehouses and Revic's lab. The fleet is
  `InGameState`'s Last Stand fleet (`LAST_STAND_BEACONS_TAKEN`), without
  the Federation base.
- **Chorus-held beacons:** `Mirage.kt`'s false map, for the beacons within
  two jumps of the exit, **without a nebula's other effects**.
- **The relays:** a size 2 station blueprint (`tools/ship_variants.py`
  from the vanilla Rebel/automated station art), no engines or piloting,
  and its guard as a wingman. Which relays are destroyed is saved with
  the sector, and read by the Mainframe as each phase is built.
- **The Mainframe:** **a `MainframeBoss` (a `BossManager`, as
  `FlagshipBoss` is)** that never moves, builds each phase's ship, and
  counts the 20 seconds between them (as `FlagshipBoss.bossShipKilled`
  does, without the jump). Its three blueprints come from
  `tools/boss_ships.py`, from the vanilla flagship's three stages, stretched
  as the other bosses' are. **`ColossalBoss.Kind.MAINFRAME`**, with the phase,
  for: the Firewall core's super shield, the bays' build bars and their
  wingmen, the Chorus pulses, and the mirage mounts (`Mirage.kt`'s
  disguises, which never fire).
- **The Override:** an event that sets a flag on the sector (one sector, so
  it can be saved with it), which `MainframeBoss` checks for phase 3.
- **The endings:** an event before the victory screen, by how it ended.

Roughly in order, each playable on its own:

1. **Done.** The sector: map, events, stores, the fleet, and the flagship
   still at the end. The vanilla `FINAL` sector, with its events replaced
   (`sector_data.xml.append`) by the capital's (`events_boss.xml.append`),
   its name, start, exit and repair stations' texts rewritten
   (`text_events.xml.append`), and its Chorus-held beacons
   (`Sector.chorusHeld`, `Mirage.isDeceptive`).
2. **Done.** The Mainframe, one phase (the Firewall) in place of the
   flagship. It's the sector's finish event (`xftlFinishEvent` on
   `FINAL`, `XFTL_MAINFRAME`), at vanilla's base beacon (labelled
   MAINFRAME on the map), and there's no flagship (`GameMap.generateSector`).
   Its ship is an ordinary event ship, `XFTL_BOSS_MAINFRAME` from
   `tools/boss_ships.py` (an automated assault ship, stretched, with no
   crew), and `ColossalBoss.Kind.MAINFRAME`: it never dodges
   (`isStation`, `Ship.evasion`), its Firewall core (its first shields
   system) brings 5 of its super shield back every 25 seconds
   (`updateFirewall`), and beating it wins the game
   (`InGameState.colossalBeaten`). It mends while the player's away
   (`advanceFleetLastStand`). Not yet: its hacking going for the shields
   first, and the fleet's anti-ship battery once the fleet reaches it
   (the last stand's fleet never takes the base).
3. **Done.** The other two phases. `ColossalBoss.Kind.MAINFRAME_BAYS`
   (from the automated basic ship) and `MAINFRAME_MIND` (from a Slug
   ship), each `Kind.mainframePhase`. Beating a phase
   (`InGameState.mainframePhaseBeaten`) moves the sector on to the next
   (`Sector.mainframePhase`, saved), and starts the break
   (`Sector.mainframeBreak`, `updateMainframeBreak`), then the next
   phase's event (`XFTL_MAINFRAME_PHASE_2` and `_3`), or, coming back,
   straight away. The bays' drones (`ColossalBoss.updateBays`) with
   their build bars over the bays (`ColossalBoss.renderOverlay`), and the
   Chorus pulses (`updateChorus`). Beating the Mind wins the game.
   Then every phase made size 4 (`ShipShape.TITAN`, about 1000px wide),
   crewed by every race, and full of drones (`tools/boss_ships.py`, its
   drones kept as they are by `ShipGenerator.buildBoss`), the bays building
   drones rather than hybrids, and the Mind given the Zoltan Shield Bypass.
   Then three times the crew (six of each race) and the drones inside,
   with repair drones among them, in more drones systems. Then the
   Firewall's hull glued from the Rebel flagship and two races' bomber
   halves, in four variants (`glue_firewall`: each part transposed, glued
   with `hybrid_ships.glue`, and transposed back, then widened at each
   join with a plain plate, `bridge`).
4. The relays.
5. The Override and the endings.
