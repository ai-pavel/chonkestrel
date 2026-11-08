# The automatic playtest

The automatic playtest plays a game by itself, from a new game with the
Chonkestrel, faster than real time, and writes a report (where it went,
what it fought and bought, every exception) with screenshots of every
fight and event:

```sh
XFTL_AUTOPLAY=1 XFTL_AUTOPLAY_DIR=$PWD/dist/playtest XFTL_AUTOPLAY_SPEED=8 make run SAVE_DIR=dist/playtest-save
```

It plays without debug mode, so it shows how hard the game is, as well as
finding crashes. With `XFTL_DEBUG` set too, its ship can beat anything, to
get through the whole game quickly. Give it a save directory of its own
(`SAVE_DIR`, emptied first), so it starts a new game and leaves yours alone.

`XFTL_AUTOPLAY_MINUTES` and `XFTL_AUTOPLAY_SECTORS` set when it stops, and
`XFTL_AUTOPLAY_SPEED` how fast it plays. The code is in
`src/main/java/xyz/znix/xftl/devutil/AutoplayDirector.kt`, and its fighting
in `VideoDirector.BattleDriver` (with `playerTactics` on).

## Its strategy

### The map

- In each sector it heads for the exit, exploring on the way: the nearest
  beacons it hasn't been to, stores first, then those without known
  hazards, and of the nearest, those furthest from the exit, so it sweeps
  the sector from the start to the exit.
- It only explores while it can still get to the exit with three jumps to
  spare before the rebel fleet catches it (`EXPLORE_MARGIN`).
- With less than half its hull, it only goes to stores on the way.
- **Repairs:** with less than 70% of its hull (`REPAIR_TRIP_HULL`), and the
  scrap to mend at least 15 points of it, it goes to the nearest store it
  can get to and on to the exit before the fleet (`repairGoal`), one it's
  been to too, before exploring; at the exit, it goes back for them first.
- It steers round the hazards (asteroids, suns, pulsars, nebulas with
  hazards, extreme hazards) it can see, as a player could: those at the
  beacons it's been to, all of them on a revealed map, and with Long-Range
  Scanners, which it buys, those next to it. It steers round the beacons
  the fleet has overtaken too.
- The next sector is picked by how risky it is for its ship (`pickSector`):
  - while it's weak (fewer than two layers of shields, or less than half
    its hull), a civilian sector, then a hostile one;
  - once it's stronger, a hostile sector first, then a civilian one;
  - nebula and hazardous sectors (and XFTL's nebula sectors, such as the
    Radiation Fields, are counted as hazardous) only when it's strong (three
    layers of shields, and at least 70% of its hull), and last otherwise.
- **Fuel:** it buys it at stores, before anything else (see below). With
  too little to explore (fewer jumps' worth than it takes to the exit,
  plus six), it only goes to stores on the way. Out of fuel, it waits with
  the distress beacon on, as a player would, till someone brings some (the
  report counts the waits).

### Events

- It picks the first blue option, or else the first that doesn't take on
  or give up crew (to slavers, say) or open a store, or else the first.
- When it's in no state to fight (`cautious`: less than half its hull, its
  crew failing, or not the fuel for a jump), it leaves out the options that
  start a fight (a ship turning hostile) or damage the hull, if there are
  others.

### Stores

At each store, once, it buys fuel first, up to sixteen jumps' worth
(`FUEL_JUMPS`), out of the reserve if it has to: that's what the reserve
is for. Then, in this order (`shop`), keeping a tenth of its scrap in
reserve (`STORE_RESERVE`), but none at the last store it can get to in the
sector:

1. It fits the weapons and drones in the cargo hold that it has the slots
   and power for (as for buying them, below), keeps those it has an empty
   slot for but not yet the power, and sells the rest, for half their
   price.
2. The augments it wants first, one of each, in this order, whatever its
   shields (`FIRST_AUGMENTS`): Long-Range Scanners, the Scrap Recovery Arm
   and the Weapon Pre-Igniter.
3. Its hull, repaired up to 90% (`REPAIR_FIRST`; the rest once it's
   upgraded, last): it's the hull that keeps it going.
4. Missiles, up to 14.
5. Weapons (`buyGear`): into an empty slot of a weapons system with the
   power left for them, as long as the reactor can power them too, or else
   upgrading the weapons system and the reactor for them (`makeRoomFor`),
   if it can afford both. The best
   against shields first: missiles and ion weapons, then those with more
   shots, then the dearest. Never the anti-bio beam, the fire beam or the
   healing bombs.
6. Crew, while there's room, one a sector at most, the best fighters first
   (Mantis, Rock, Crystal, Lanius, Human, Slug, Zoltan, then Engi).
7. Drones: a defense drone first (if it hasn't one), then combat and
   anti-personnel drones,
   into a drone slot with the power left, as long as the reactor can power
   them.
8. Upgrades (`upgrade`): the first two layers of each shields system, then
   the weakest engines, the weapons systems with an empty slot or a weapon
   they can't power (for the weapons it wants to buy), and a drones system
   when a drone in the cargo hold needs its power, then the rest, the
   oxygen and medbay last, and the weapons systems that are full and powered
   after that, with a bar of reactor after each upgrade (and before one, if
   it couldn't power it and can afford both).
9. The other augments it wants, the Automated Reloader (`buyAugments`),
   once each shields system has two layers.
10. The rest of its hull.

### Fights

With `playerTactics`, the battle driver fights as a player would:

- **Focus:** every weapon fires at one ship, the one with the fewest
  shields up, then the least hull, so they go down one at a time.
- **Volleys:** the projectile weapons wait until they're all charged, then
  fire together, so the shields can't come back up between the shots.
- **Targets:** while the target's shields are up, everything fires at its
  shields room, but the bombs (which go through shields) at its weapons;
  once they're down, everything fires at its weapons. A twin or triple
  weapon's copies all fire at the same room.
- **Beams** fire once the target's shields are down (or straight away, if
  it has nothing else), across its weapons room.
- **Saving supplies:** against ships with at most one layer of shields,
  the missile weapons are turned off, and the drones that attack them aren't
  launched, as lasers will do.
- **Cloaking:** when an enemy's about to fire a missile, or several weapons
  at once (95% charged).
- Drones, hacking (each system at a different ship's shields) and mind
  control are always used.
- Power for the weapons comes from the mind control, hacking, teleporter,
  cloaking and medbay, then the shields, if the reactor's short (such as in
  an ion storm).
- **Weighing the fight up** (`assess`, every second): how much damage a
  second each ship gets through its shields (missiles and bombs through
  them, beams a point less for each layer, each layer stopping a shot,
  less its evasion and super shield), and how long its own weapons take
  to kill each, so how much hull it would lose killing them in turn.
- **The order it kills them in** (`killOrder`): the most damage they do
  to it for each second they take to kill first, then the least hull.
- **Volleys:** the charged projectile weapons fire together once they have
  the shots to get through the target's shields (a layer and one more),
  or its shields are down, or they're all charged.
- **Fleeing:** if it would lose more than 60% of the hull it has left
  (`FLEE_LOSS`), or can't hurt them, or the boarders the ships could send
  (two teleporter loads each, their health times how hard they hit) are
  stronger than 80% of its crew (`FLEE_BOARDERS`), it flees the fight: it
  powers its engines up (from the hacking, mind control, teleporter,
  drones, then the weapons), so the FTL drive charges faster, shoots at
  the worst ship's weapons, and jumps as soon as it can.
- **Retreat:** with less than half its hull left (`RETREAT_HULL`), 30% of
  it lost in the fight (`RETREAT_LOSS`), its crew down to 60% of their
  health (`RETREAT_CREW`), or after 240 seconds of fighting, it jumps away
  once the FTL drive's charged (but not from the flagship).
- **Boarders:** two of its crew go after each one (`FriendlyCrewAI`), so
  they outnumber them.

### Between fights

- With crew hurt or boarders aboard, it powers the medbay first
  (`powerMedbays`), taking the power from the weapons, or else the drones,
  hacking, mind control, teleporter or cloaking.
- It keeps every system powered, as far as the reactor allows, the shields
  and engines first (`powerUp`).
- At a safe beacon, it waits (up to 180 seconds, as the fleet only moves
  when it jumps) for the boarders to be
  dealt with, the fires to be put out, the breaches mended, the systems
  repaired and the crew healed (at a dangerous one, it jumps on). The
  medbay is turned on for them (the Chonkestrel starts with it off), taking
  the power from the weapons if it has to, and the crew's AI sends the hurt
  to it, healing them fully when the ship's calm.
- It vents the rooms on fire or with boarders in that none of its crew are
  in (`ventRooms`): it opens the doors from each to the nearest airlock,
  through rooms without its crew (and never the medbay, so the hurt can
  still heal there), and closes them again once the fire's out or the
  boarders are gone.
- The crew's AI runs the crew: to their stations, repairs, fires and the
  medbay.
- It never pauses: if the game's been paused (with the pause key), it
  unpauses it.

Each beacon in the report's log shows the hull, crew, fuel, missiles,
drone parts and scrap, and when it gives up waiting for the crew to heal,
who's still hurt, where, and the medbays' power.

## To do

- **Retreat to a safe beacon:** it retreats towards its goal, and can run
  into the next fight.
- **Weighing events:** risk against reward, beyond staying out of fights
  when it's weak.
- **Boarding:** sending boarders when its crew are strong at it (Mantis,
  Rock), not onto ships with anti-personnel drones, and bringing them back
  in time.
- **Upgrades for the threat:** more engines and piloting against missiles,
  sensors, rather than a fixed order.
- **The flagship:** tactics for each of its stages: its weapons first, a
  defense drone against its missiles.
- **Better weapons:** swap a weapon for a better one, rather than only
  filling empty slots, and compare them by damage per power.
- **Upgrading the weapons and drones systems** for the weapons and drones
  it wants to buy, and buying systems it doesn't have.
- **Doors against fires it can reach:** close the doors round a fire to
  starve it, and keep the crew out of rooms with no air.
- **Hacking and mind control targets** by the threat: the weapons of the
  ship that hurts most, a medbay mid-boarding.
- **Defense drones and the cloak** timed to each projectile, rather than
  to the charge of the weapons.
- **Running out of hull:** every game so far has ended with the hull worn
  down, by the third to sixth sector: each fight against two or three ships
  costs about ten points of hull, and the repairs (2-4 scrap a point) and
  upgrades take more scrap than the fights give. Either the playtest needs
  to fight better, or the Chonkestrel's early game is too hard without
  debug mode.
- **Hazards without the scanners:** until it can afford Long-Range
  Scanners it can't see hazards, and jumps into suns and asteroid fields.
- **Boarders:** games now end as often with the crew dead as with the hull
  gone. Big ships' boarding parties grow through the game, and several
  Lanius ships (whom venting doesn't stop) can send more boarders at once
  than it has crew. It could keep its crew together, fight boarders in the
  medbay, and buy anti-personnel drones and crew sooner.
- **The FTL drive in a fight:** when it retreats, the drive is often still
  charging, and it can lose most of its hull first, in nebulas especially.
- **Fights it can't run from:** with the fights weighed up and fled
  (as of 2026-10-30), it gets to the fourth or fifth
  sector, and seventh once. It still loses those it can't get away from in
  time: the Rebel fleet's, a boss's, and when it's out of fuel. It could
  keep ahead of the fleet better, and judge a boss before it gets there.
- **Suppressing the other ships:** bombs, ion and hacking at the worst
  ship's weapons while the volleys kill the first one.
