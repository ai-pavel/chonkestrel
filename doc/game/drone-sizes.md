# Drone sizes

As with the weapons (see [weapon-sizes.md](weapon-sizes.md)), a drone slot
can hold two or three of the same drone combined into one: a "Twin" or
"Triple" drone. The code is `weapons/DroneSizes.kt`, and each slot's
copies are `Drones.DroneInfo.copies`.

A combined drone is **N of the vanilla drone, launched from one slot, that
work together**. They're N separate drones (no new art, and it reads at a
glance), and what makes one worth more than N single drones in N slots is
how its copies share the work out: across the enemy ships (the reason the
sizes exist at all, as the bigger player ships fight several), or across
targets, rooms and systems. How they share it is each drone's own, so every
kind of drone gets its own rules below.

The one exception is the enemies' anti-personnel drone, which grows instead
(see [Bulwarks](#bulwarks)).

## The drones

When the blueprints are loaded, every vanilla drone gets a size 2 and a
size 3 version, as blueprints of their own: `XFTL_X2_<name>` and
`XFTL_X3_<name>`, called "Twin …" and "Triple …", with "(x2)" or "(x3)"
after the short name in the drones bar and the stores' boxes, as the
weapons have (`ShortNames`).

Left out, as with the weapons: the hacking system's drone (it's the
system's, not a drone part's), and the guns inside the drones.

## Balance

As the weapons', compared with the single drone:

| | Size 2 | Size 3 |
|-|-|-|
| Price | 2.2x | 3.3x |
| Power | half again, rounded up (1→2, 2→3, 3→5, 4→6) | twice (1→2, 2→4, 3→6, 4→8) |
| Drone parts a launch | 2 | 3 |
| Slots | 1 | 1 |

So it saves slots and power, not drone parts.

**Losing copies.** The slot counts as launched while any of its copies is
still flying (or standing). Launching it again sends only the copies it's
lost, for a drone part each. Turning it off, or the drones system losing
the power for it, or being ionised or hacked, takes all of them at once, as
with a single drone.

## How each kind works

### Combat drones (Combat I and II, Beam I and II, Fire Beam)

**A wing.** One copy goes to each of the ships fighting the player: each
copy, as it's launched, goes to the ship with the fewest of the player's
drones round it (`InGameState.pickDroneTarget`), the main ship first, so a
wing spreads over the ships, and round the ones the player's other drones
aren't at. With fewer ships than copies, they double up. When a ship's
destroyed or gives up, its drones move on to the next ship still fighting
(they don't need launching again; single drones too). Each copy flies and
fires as the single drone does.

The enemies' wings all fly round the player's ship, as their weapons all
fire at it.

### Defence drones (Defence I and II)

**A screen.** The copies fly the same orbit, spread evenly round it (half
way round each other for a twin, a third for a triple), and share their
targets: they never fire at the same shot, so a twin stops two missiles at
once where two single drones would both fire at the first. Each still turns
and fires as fast as the single drone.

### Anti-combat drone

**A volley.** It's a defence drone (that shoots at drones), so it works as
the screen does: N copies spread round the orbit, each firing at a
different enemy drone where there are enough of them, so each time they
fire it's a volley of N shots at N drones.

### Boarding drones (Boarding and Ion Intruder)

**A raid.** One copy boards each enemy ship, left to right, the rest the
main one. Copies boarding the same ship go to different rooms, the
single drone's choice first, then the next best. The enemies' raids all
board the player's ship, in different rooms.

They never go for a ship with a Zoltan super shield up, which would
destroy them as they crossed it (Zoltan Shield Bypass or not): the copies
share out the ships without one. While every ship has one, the slot can't
be turned on at all, and no drone parts are used, until a shield's down
(`BoardingDrone.boardableShips`). The same goes for single boarding drones
and Ion Intruders, the enemies' too.

### Anti-personnel drone (the player's)

**A squad.** N bodies, each taking a place in a room as a crew member
does. They share the intruders out: the first goes to the room the single
drone would, the next to the next room with intruders, and so on; with
only one room, they all go there. When it's clear, each moves on to the
next room with intruders.

### System repair drone

**A crew.** N bodies, each mending a different damaged system (the worst
damaged first), so they don't all crowd into one room.

### Hull repair drone

**A team.** N copies fly out together, so the hull's mended N times as fast
while it's out, for the N drone parts it took.

### Shield overcharger

**A bank.** N overchargers on the orbit, spread evenly round it, as the
defence screen's are. Each looks after its own row of five points of super
shield (see `Ship.maxSuperShield`), as a single one does, and stops on its
way round for the last of its charging. They charge one after another
rather than all at once (a third of the way through each other's charge,
for a triple), so the shield's topped up more often, and they don't all
stop at the same moment.

## Drawing

The drones themselves are the vanilla ones, N of them (and the bulwarks
scaled up). What's new is how a combined drone looks in the interface:

- **Its icon** (in the stores' and equipment's boxes, an event's rewards,
  and while it's being dragged; `DroneBlueprint.drawIconUI`) is N of the
  single drone's icon, as the combined weapons are drawn: a twin's two side
  by side, and a triple's three in a triangle, two behind (darker, raised)
  and one in front, in the middle. It fits the boxes' 96x45 icon space for
  every drone (the defence drones' gun included), and an event's reward row
  is as wide as it is (`iconSize`).
- **Its name** is the single drone's short name with "(x2)" or "(x3)" after
  it, cut down to fit (`ShortNames`) in the drones bar's two lines and the
  boxes' one, as a combined weapon's is.

`tools/drone_sizes.py --preview` draws each drone's boxes too (see below).

## Bulwarks

The enemies' twin and triple anti-personnel drones aren't squads, but one
big drone that doesn't move: a **bulwark**, standing guard in a big room.

- **Size.** It's the anti-personnel drone's image scaled up, two or three
  times as wide and tall (its own sprite sheets, every frame of the
  anti-personnel drone's and its glow, scaled with the Scale2x and Scale3x
  pixel-art scalers, which keep its edges sharp and round off the
  stair-steps: `xftl_bulwark_x2_base.png` and so on in the mod's
  img/people, made by `python3 tools/drone_sizes.py ftl.dat`), and it stands on a square of 2x2 or 3x3 cells,
  so it only goes in a room at least that big (a twin in a 2x2 room or
  bigger, a triple in a 3x3 or bigger), and only one without a system: it
  never stands in a system's way, or on its console. A ship with no empty
  room that big doesn't get one (it gets the single drone instead).
- **Thick.** As much health as it covers cells (four times the single
  drone's for a twin, nine for a triple), and N times its damage, against
  everyone in its room.
- **Still.** It's launched in its room, and stays there: it fights whoever
  comes in, and doesn't go after boarders elsewhere. It holds all the
  cells it stands on, so no one else, of either side, stands on any of
  them: boarders teleported into its room take its other cells, or go
  to the next rooms (`Room.holdCells`), and it's only launched onto a
  square no one's standing on. Its health bar is over its middle.

Its one launch takes as many drone parts as it's worth (two or three).
If there's no free square for it when it's launched (crew standing in
it, say), it goes as an ordinary single anti-personnel drone.

Estimates, to be playtested: the health and damage.

**The hybrids' control drones.** The hybrids (see
[hybrid-ships.md](hybrid-ships.md)) carry a lot of anti-personnel drones,
for whatever's controlling them to keep watch. Some of those become
bulwarks, where the ship has the room: on a long hybrid, one of its
anti-personnel drones becomes a twin bulwark; on a wide one, one becomes a
triple and one a twin; and an Engi hybrid gets one more twin. Each takes
the place of as many of the single drones as it's worth (a twin two, a
triple three), so the ship's no stronger, but it's drones of two kinds:
small ones that hunt the boarders, and big ones holding the empty rooms
(the biggest first). A bulwark that won't fit goes back to being its
single drones.

## Where they turn up

The same as the combined weapons (see
[weapon-sizes.md](weapon-sizes.md#where-they-turn-up)):

- **Stores:** none in civilian sectors, more and more through the game in
  hostile and nebula ones, and often in hazardous ones, where triple ones
  turn up too.
- **Rewards:** a drone is a double one 10% of the time, in place of the
  single one. An event's random drone with `xftlCombined="true"` is always
  a double one, or a third of the time a triple one: the Free Colonies'
  courier quest gives one, alongside its two weapons.
- **Bosses' rewards:** every drone a boss gives is combined, as its
  weapons are: a twin from the Devourer and the Vigilance, a triple from
  the Stormbreaker, and a triple and a twin from the Ossuary.
- **Enemy ships:** each drone has the same chance of being a double one as
  a weapon (5% to 30% through the game for normal ships, 25% to 60% for long
  and wide ones), and in the Gullet and the Belly, a 35% chance of being a
  triple one, at most two on a ship. One that needs more power than the
  drones system has left gets it, as far as the system goes; if even that
  isn't enough, it stays single. A ship gets the drone parts for its
  drones' copies.
- **Bosses:** every boss drone is a twin or a triple, as their weapons
  are, more of them triples the stronger the boss. The Ossuary's slipway
  rebuilds the copies too.

## Previewing it

`python3 tools/drone_sizes.py ftl.dat --preview out/` draws how they're meant
to look, from the vanilla drones' images, into `out/`: an
image for each drone, with its single, twin and triple versions side by
side, each in a little scene of how its copies share the work out (and its
power, price and drone parts), and under them how each looks in the
interface: its equipment box, its store box (with its price) and its
drones bar button; one of the bulwarks in their rooms; and all.png with
them all. It's a sketch of the design, not the game's own
drawing.

## How it's made

- **The blueprints:** `DroneSizes.combinedElement`, made in
  `BlueprintManager.addCombinedDrones`; a combined drone's blueprint has
  `xftlCombined` (`DroneBlueprint.combinedOf`), its names "Twin" and
  "Triple" (as the weapons'), and its icon (`DroneBlueprint.drawIconUI`,
  `iconSize`).
- **The copies:** `Drones.DroneInfo.copies`, each an instance of the
  combined blueprint with its `AbstractDrone.copyIndex` (saved with it);
  `Drones.setDronePowerInternal` launches the missing ones, a part each.
  `AbstractDrone.siblingCopies` are the others, which they share the work
  with: `BoardingDrone.launchDroneProjectile`, `AbstractIndoorsDrone.claimedBySibling`
  (the squads and repair crews), `DefenceDrone`'s shared targets,
  `OrbitFlightController.spreadAngle`, and `ShieldDrone.onLaunched`.
- **The bulwarks:** `AntiPersonnelDrone` (`bulwarkSpot`, `scale`), holding
  its cells with `Room.holdCells`; `Drones.bodiesFor` launches the one.
  The hybrids' are `ShipGenerator.withBulwarks`.
- **Where they turn up:** `LootPool.getDrone` and `getCombinedDrone`,
  `StoreData`, events' `<drone name="RANDOM" xftlCombined="true"/>`,
  `BossRewards.droneSizes`, `DroneSizes.enemyDrone` and
  `ShipGenerator.combineBossDrones` (`DroneSizes.bossTriples`).
