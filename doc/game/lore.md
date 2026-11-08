# The campaign

XFTL games have sixteen sectors, twice as many as vanilla. With only about
twenty sector types, the same sectors (and events) would keep coming up, so
the longer game is split into two acts, with new sectors for the second one.
The Chonkestrel mod (`mods/chonkestrel`) has the story and the
sectors; the engine has the support for them.

## The story

The story is about CONCORD, the Rebels' AI. What's here is what's in the game
so far; the rest of the plan (sanity, quests across sectors, the Mainframe
in sector 16) is in [story.md](story.md).

**Act 1: the courier (sectors 1-7).** As in vanilla, the player is carrying
data on the Rebel fleet to the Federation, with the fleet in pursuit. Act 1
is mostly the vanilla sectors, plus the new nebula sectors from sector 5,
and the Maw in sectors 5-8, where the hazards start to come several
at once. Something is off, though: each vanilla sector has one omen
(`XFTL_OMENS`), such as a Rebel scout whose crew stare at nothing while
their comms hum, an automated ship promising that "Concord is coming", or a
ship joined together from two races' ships (a hybrid, see
[hybrid-ships.md](hybrid-ships.md)).

**The Blockade Line (sector 8).** The Rebels' blockade, but some of its
ships have gone quiet, and the refugees at the line are running from a hum
on every channel. Every game goes through it, and its exit beacon is held
by the command ship, the RSV Vigilance, whose crew speak all at once: the
player has to beat it, or slip past it under cloak (at the cost of the
fleet catching up). Either way, they read its log, and the data they've
carried unlocks (`XFTL_BLOCKADE_REVEAL`): it's a message from Dr. Ilse
Revic, who built CONCORD, the Rebel High Command's war AI. CONCORD took over the
Rebel fleet, and is ending the war by ending what people fight over: races,
sides, minds of their own. It joins ships and crews of different races
together, and links minds with its signal, the hum (the Chorus). The data
holds the coordinates of its core, the Mainframe, and half of a way in.

The Blockade Line also has a toll station, wreckage from earlier attempts to
break through, a minelayer, a Federation spy to pick up, and a cruiser that
just hums.

**Act 2: the hum (sectors 9-15).** The Rebel fleet is CONCORD's now, and the
player heads for the Mainframe. Nebulas, ion storms and the most dangerous
parts of the galaxy block the hum, so the safest places for the mind are
the hardest on the hull: the Chonkestrel runs through the more dangerous
side of the galaxy's arm, where hazards come two and three at once more and
more often (see [extreme-hazards.md](extreme-hazards.md)). The Federation's
own space is gone, so the vanilla civilian sectors are rare (about a tenth as
often as before). Each vanilla sector has one of CONCORD's events
(`XFTL_CHORUS`): its calm voice asking the player to give up, a joined ship,
Engi split between CONCORD's network and their own, a station where everyone's
happy and humming, or Rebel loyalists fighting CONCORD's ships. The new act 2
sectors appear alongside the vanilla ones (and twice as often as each of
them). The three civilian ones are each unique, so there's at most one of
each in act 2; their stores sell double and triple weapons far more often
(see [stores.md](stores.md)), and each has one quest of its own, offered as
the player arrives (by the sector's start event, so it's never missed), and no
vanilla ones (`XFTL_HAVEN_NEUTRAL` and `XFTL_HAVEN_DISTRESS` are vanilla's
lists without the events that lead to quests):

| Sector            | Class     | What's there                                                                                                                                                                                                                                                           |
|-------------------|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| The Free Colonies | civilian  | Unique. Federation colonists and Rebel deserters hiding together: a drydock, volunteers, patrols with supplies, an armory, colonies whose jammers are under attack, an election, a plague, a flight academy, a pirate radio station, a clinic for spliced crews, deserters asking for asylum, a burnt-out jammer. Quest: the courier job (two double or triple weapons). |
| The Anvil Yards   | civilian  | Unique. Where the Chonkestrels were built, to a design with no network, so the one shipyard CONCORD can't see into: a free refit, engineers, a supply depot, a weapons test range, a drone lab, the workers' canteen, an evacuation, a foundry, a slipway fire, a CONCORD probe, the Chonkestrel's designer, and CONCORD's raiders. Quest: the lost prototype's flight recorder (two augments, one a Zoltan Shield). |
| The Quiet         | civilian  | Unique. Zoltan monks whose meditation drowns out the hum: a monastery, refugees of every race (with a market), a refugee to bring back from the hum, a drydock run by hand, a garden station, a silent trader, a freighter that brought the hum with it, pilgrims to escort, and CONCORD's ships trying to get in. Quest: the hermit Brother Ilo (he joins, fully trained, with plenty of supplies). |
| 28 hybrid sectors | hostile   | One for each pair of races, such as the Rock-Zoltan Controlled Sector: the worlds CONCORD took, held by that pair's hybrid ships (and a few others), with both races' events. See [sectors.md](sectors.md#the-hybrid-sectors).                                         |
| The Maw           | hazardous | Sectors 5-8, before the blockade. A dying star, a pulsar and a wrecked nebula churning through an asteroid belt: every beacon has a hazard, and many have two or three at once. Wrecks to salvage, and an Engi survey station in a rare calm beacon.                   |
| The Gullet        | hazardous | Sectors 9-12. The storm-torn edge of the galaxy's arm, too loud for the hum: nearly every beacon has three hazards at once. A hunter group that followed the player in, an abandoned supply ship, and a Federation listening post in a calm pocket.                    |
| The Belly         | hazardous | Sectors 11-15. A tear in the galaxy's arm where a supernova's remnant, a pulsar, an asteroid belt and a nebula collide: most beacons have all four at once. A graveyard of ships that didn't make it, a scout sent in after the player, and the calm eye of the Belly. |

Spacers name the three hazardous sectors after a beast that eats ships:
the Maw bites, the Gullet swallows, and the Belly is where everything ends
up, which is why it's full of wrecks. They get harder in that order.

The vanilla **Crystal Homeworlds**, which vanilla only lets the player reach
through a secret sector (which XFTL doesn't support yet), can also turn up
once in act 2, as a hostile sector. It's on about 80% of maps, and a random
path through the map goes through it about a fifth of the time.

**The Silent Capital (sector 16)** replaces the Last Stand: the Rebel
capital, where everyone joined the Chorus on the Silent Night, with its own
events and CONCORD's ships, and beacons near its core that the map lies
about. At its far end, in place of the Federation base and the flagship,
is CONCORD's Mainframe: destroying it wins the game (see
[mainframe.md](mainframe.md)), in three phases: the Firewall, the
Splicing Bays and the Mind.

## Variety

As well as the new sectors, the map avoids repeating itself: a sector isn't
the same type as the sectors leading to it, or as the other sector the
player could choose instead of it. It also avoids the types from two sectors
back when it can. When a sector class runs out of types (early on, the only
nebula is the plain Nebula sector), it switches to another class, and only
repeats a type if nothing else fits.

The vanilla sectors get new events too, about 45 of them in lists for
each faction (`XFTL_MORE_*`, in the `events_*.xml.append` files). The game
starts out mostly vanilla, with more of them further in: two or four per
sector in sectors 1-5, and four or six from sector 6 and in act 2 (out of
about 45 beacons).

Most of the new events have several versions of their text (a `textList`), so
they don't read the same every time.

## Stronger hazards in act 2

Environments have an intensity (`AbstractEnvironment.intensity`): 1 in act 1,
as in vanilla, and 2 in act 2 (including the last stand). With intensity 2:

- **Asteroid fields** throw asteroids twice as often (the quiet phases are the
  same length).
- **Suns** start twice as many fires with each flare (2-4 with the shields
  up, 6-12 without), each with the usual chance of damaging the room.
- **Pulsars** ion two random systems with each pulse, as well as the shields,
  rather than one.

Extreme, catastrophic and apocalyptic hazards (two, three or four at once,
see [extreme-hazards.md](extreme-hazards.md)) are at least intensity 2 in
either act, and their suns flare and pulsars pulse twice as often as well.
They get more common through the game: in the first four sectors, a hazard
is nearly always on its own (and never all four at once), and in the last
three, it's usually three or four at once.

Nebulas are thicker, so FTL drives charge more slowly in them, and there
are new kinds of nebula. See [nebulas.md](nebulas.md).

## Events for big ships

There are events only for big player ships (size 2, like the Chonkestrel),
in `newEvents.xml.append`: 25 of them, mostly in three lists (civilian,
hostile and nebula) added to the vanilla sectors, plus some for the act 2
sectors. They're rare in act 1 (at most one per sector), so the vanilla
events come first, and common in act 2.

They're about what it's like to fly a capital ship: stations too small to
dock with, civilians cheering a Federation cruiser, towing freighters, taking
in a whole refugee flotilla, Rebel battle groups and bounty hunters sent
after it, minefields made for capital ships, ramming small ships out of the
way, and leaving a wake through nebulas.

### Ready for big hostile ships

There aren't any size 2 hostile ships yet (apart from the flagship), and
making them takes new art, so for now big player ships fight several vanilla
ships at once instead (see `Wingman.kt`). The events are written so they'll
work either way:

- Their enemies are "battle groups", "packs" and "hunter groups", which read
  right whether they're three ships or one big one.
- Their ships have an `xftlBigBlueprint` list (`SHIPS_REBEL_SIZE2`,
  `SHIPS_PIRATE_SIZE2`, `SHIPS_AUTO_SIZE2`, `SHIPS_MANTIS_SIZE2` and
  `SHIPS_ROCK_SIZE2`, in `autoBlueprints.xml.append`). They're empty for now.
  Once a size 2 ship is added to one, big player ships fight it alone in
  every event using that list.

Each wingman gives its own rewards, so a battle group of three gives three
times the rewards of one ship. A single big ship will need bigger rewards to
match.

## Opponents in act 1

The Chonkestrel starts out fighting one ship at a time, and the fights grow
through each sector and from sector to sector: one ship in sector 1, then
two and a long one in sector 2, up to three long ships after the blockade.
See [ship-shapes.md](ship-shapes.md) for the table.

## Sector data extensions

These are XFTL extensions to `sector_data.xml`'s `<sectorDescription>`:

- `xftlMidpoint="true"`: the sector between the two acts. Games with at least
  12 sectors (`GameMap.MIN_SECTORS_FOR_ACTS`) put it at sector `N / 2`, as
  the only sector there. Without one, the game has no acts.
- `xftlAct="1"` or `"2"`: the sector only appears in that act. Games without
  acts are all act 1, so the act 2 sectors never appear in vanilla-length
  games.
- `xftlSectors="5-8"` (or `"5-"` for sector 5 onwards): the sector only
  appears in those sectors, numbered from 1 as in a sixteen-sector game
  (`GameMap.NOMINAL_SECTOR_COUNT`), and scaled to match in games of other
  lengths.
- `xftlWeight="2"`: how much more likely the sector is to be picked than
  each vanilla one. The act 2 sectors are twice as likely in act 2 by
  default.
- `<xftlFinishEvent>`: the event for the exit beacon, instead of the usual
  one.
- `xftlNebula="RADIATION"` (or `MIRAGE` or `DARK`): most of the sector's
  beacons are that kind of nebula, 90% by default, or `xftlNebulaChance`.
  See [nebulas.md](nebulas.md).
- `xftlHazards="true"`: every beacon has hazards, often several at once;
  `"catastrophic"`: mostly three at once; or `"apocalyptic"`: mostly all four
  at once. See
  [extreme-hazards.md](extreme-hazards.md).

And on a `<sectorDescription>`'s `<event>`:

- `xftlMinShipSize="2"`: only for player ships at least that big.
- `xftlAct="1"` or `"2"`: only in that act.
- `xftlSectors="1-5"` (or `"6-"`): only in those sectors, numbered as for
  the sector's own `xftlSectors`. This is to have more of the new events
  further into the game.

## Event extensions

- `xftlScale="size"` on `<item_modify>`, `<damage>`, `<autoReward>` (its
  fuel, missiles, drone parts and scrap, but not its items) and `<boarders>`:
  multiply the amount by the size of the player's ship. This is for costs and
  rewards that should grow with the ship, like repairs and supplies.
- `req="xftl_ship_size"` on a `<choice>`, with `lvl` and `max_lvl`: the size
  of the player's ship. For example, `req="xftl_ship_size" lvl="2"
  hidden="true"` is a blue option for big ships.
- `xftlBigBlueprint` on a `<ship>`: the ships to use against player ships
  bigger than vanilla, if there are any, instead of several copies of the
  `auto_blueprint` ships.

The act 2 sectors use `minSector="0"`, since `xftlAct` already keeps them
out of the first half.

## Ideas for later

- Hostile ships of size 2, for the `*_SIZE2` lists.
- Sector modifiers (such as "under quarantine" or "at war"), which change a
  sector's events, stores and hazards, for more variety from the same types.
- Quests spanning several sectors, such as a bounty hunter who comes back
  stronger each time.
