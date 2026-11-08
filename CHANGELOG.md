# Changelog

## 2026-11-08: version 0.7.1-alpha

Thanks to u/ActivityFew2621 on Reddit for reporting many of the bugs fixed
here.

### Features

- **Weapons and drones can be swapped in their bars mid-fight:** a weapon
  keeps its charge, stored shots and chain count, and a drone the copies
  it's launched (so moving one costs no drone parts); each stays powered
  if its new place has the power.
- **More long ships:** four hybrids that were too tall for their window
  get long versions (the Zoltan-Rock one, and the Crystal-Zoltan,
  Crystal-Mantis and Rebel-Zoltan bombers), and seventeen long ships
  get the hold their bulwarks stand in, as their noses are shortened too.

### Changes

- **The hybrids' halves fit together better:** where the front's and
  back's sides missed each other by a few pixels at the join, the
  narrower one is stretched out to meet the other, and their cloaks' lines
  join up across it.

### Fixes

- **Killing the last of a ship's crew ends the fight at once:** the shots
  still on their way miss, and the crew-killed event shows, rather than
  the ship being wrecked by them.
- **A weapon's last missile is fired,** rather than the weapon turning off
  for want of missiles as it launches.
- **The Lifeform Scanner and Slugs' telepathy sense living crew,** not
  drones.
- **An enemy ship jumping away streaks off upwards,** the way it points,
  not to the right.
- **The shields critical warning is under the scrap,** clear of the Zoltan
  shield's panel.
- **The fleet's danger zone covers the whole beacon map,** without gaps at
  the left and top.
- **A cloaked ship's crew and drones fade with it,** so its bulwarks no
  longer stand out.
- **Asteroids leave alone an enemy ship the player avoided** or made peace
  with.

## 2026-10-30: version 0.7-alpha

Thanks to u/ActivityFew2621 on Reddit for reporting many of the bugs fixed
here.

### Features

- **A new end to the campaign:** sector 16 is no longer the Last Stand.
  It's somewhere new, with its own events, and the map isn't to be
  trusted near the end of it (sensors at level 3, or a Slug aboard,
  help). What waits there, in place of the Rebel flagship, is for you to
  find out: bring everything you've got, and a crew of every race.
- **Six skill levels,** rather than vanilla's three: none, four green
  levels and fully learnt, each giving a bigger bonus than the last (up to
  half again vanilla's), and taking twice as long to learn in all. See
  doc/game/skills.md.
- **Twin and triple drones:** as the weapons have, every vanilla drone
  (except the hacking one) has a twin and a triple version, launched from
  one slot, for a drone part each copy, which share the work out: combat
  drones spread over the enemy ships (and move on to the next when one's
  beaten), defence and anti-combat drones spread round the orbit and never
  fire at the same thing, boarding drones board different ships and rooms,
  anti-personnel and repair drones go to different rooms, and overchargers
  look after a row each, charging in turn. Lost copies are sent again (only
  them) when the drone's turned off and on. They turn up as the combined
  weapons do: in stores, as rewards, with the enemies (the bosses' are all
  twins and triples), in the bosses' rewards and the Free Colonies'
  courier quest. Their icons are two or three of the drone's, and their
  names say "(x2)" or "(x3)". See doc/game/drone-sizes.md.
- **The bigger ships have an empty hold for their bulwarks:** every long
  and wide version (the vanilla ships', the hybrids' and the bosses') has
  an empty room with no system, 2x2 on a long ship and 3x3 on a wide one,
  put in after its new rooms, so the enemies' twin and triple
  anti-personnel drones have somewhere to stand. And most of the hybrids
  now have a place for a doors system (274 of 415, from 182), which the
  later ones always get.
- **Hovering over an enemy you can see** (their crew and drones aboard
  their ship, or their boarders aboard yours) shows its name and health.
- **Bulwarks:** the enemies' twin and triple anti-personnel drones are one
  big drone instead, two or three times the size, standing guard in their
  ship's biggest empty room (never one with a system) on a square of cells, with four or nine times the
  health, and the hybrids have some among their control drones.
- **The hazardous sectors' bosses hunt the player:** in the Maw, the
  Gullet and the Belly, there's no Rebel fleet any more. The Devourer, the
  Stormbreaker and the Ossuary set out from the exit instead, and jump a
  beacon at a time towards the player (the Stormbreaker every jump, the
  others every second one), shown on the map as the flagship is. Caught,
  the player fights it; got away from, it follows, as it was left, and
  mends while it waits. It brings its own hazards to the beacon it's at.
  The exit is an ordinary one, so the player can leave without the fight
  (and its rewards); once it's beaten, the sector's safe.
- **The Blockade Line has three exits,** at the end of the sector, all
  held by the Vigilance: the same ship, at whichever of them the player
  comes to. They're labelled EXIT on the map, with the boss in their
  tooltips.
- **The act 2 civilian sectors have more to them:** the Free Colonies, the
  Anvil Yards and the Quiet each have four or five new events of their own
  (a pirate radio station, a clinic for spliced crews, a foundry, the
  Chonkestrel's designer, a silent trader, pilgrims to escort and more),
  and one quest each, with no vanilla ones: the courier, the lost
  prototype's flight recorder, and the hermit Brother Ilo. They're each
  unique, so the Free Colonies no longer turn up more than once, and their
  stores sell double weapons 35% of the time and triple ones 12%. The
  quests' rewards are few but good: the courier's two double or triple
  weapons, the prototype's two augments (one a Zoltan Shield), and the
  hermit, a fully trained crewmember with plenty of scrap, missiles and
  drone parts. Their ships fight alone (`xftlAlone`), so the rewards come
  once.
- **Beating the Vigilance breaks the blockade:** the Rebel fleet surges up
  behind the player and takes the Blockade Line's other two exits, so the
  only way left is on, out of the sector.
- **Anti-Ship Batteries:** a planet's (or the rebel fleet's) battery, from
  vanilla's `<environment type="PDS"/>` events, now fires at the ships it's
  told to, the player's, the enemy's or both, with "ASB TARGET LOCKED!"
  before each shot at the player, and a line on the map once scanned. Its
  timings and damage are estimates, as vanilla's aren't in its data. The
  beacons the Rebel fleet has overtaken have the fleet's battery, firing
  at the player, as vanilla's fleet events say (they didn't, as the fleet's
  event wasn't the beacon's own).
- **An Anti-Ship Battery shows vanilla's icon** at the top between the
  ships (red when it fires at the player, green when it's on their side),
  with vanilla's description under the mouse, its "ASB TARGET LOCKED!"
  just under it, and the hazards' other warnings below that.
- **Weapons stun the crew:** the Stun Bomb and the Ion Stun Blast stun
  everyone in the room they hit, and weapons with a stun chance (the heavy
  laser, missiles, ions) do for a few seconds when it comes up.
- **Wingmen escape and surrender too:** a badly hurt wingman starts its FTL
  drive and goes, leaving the rest fighting, and surrenders (later than the
  ship it flies with, at half its hull), its surrender only for itself.
  The main ship's surrender is sometimes for them all: its captain speaks
  for the whole group, and they all stand down if the player accepts.
- **Crystals' lockdown:** a Crystal crew member can lock down the room it's
  in, sealing its doors in crystal (vanilla's crystal shards, growing all
  round its walls and over its doors) for 15 seconds, so no one gets in or
  out (the doors stay shut and let no air through, and crew sent through
  them, its own or boarders, stand and shoot at the crystal in vain),
  then recharges for 50. The player's use it with the button under their
  skills, or the "Secret Alien Ability" hotkey (P) for the selected
  Crystals; the enemies' use it as soon as there's a fight in their room.
- **The Lockdown Bomb (and its twin and triple versions) locks down** the
  room it hits, as a Crystal does.
- **The drones system comes with a free drone** in a store, as in vanilla:
  one of the common ones (such as Combat I or Defense I), at random, which
  its description names.

### Changes

- **Crew for hire are better trained further into the game:** every skill
  a level or so more, up to about level 3 by the end, on top of what the
  more dangerous sectors' stores give them.
- **A wider skills panel,** with room for the six levels: every skill's bar
  is split into level cells all the same width (in the crew window too).
- **The Shield Overcharger stops dead for the last second of charging**
  each point of the shields, then flies on round.
- **Boarding drones shoot at the systems they break,** with their little
  laser and its sound, as vanilla's do, rather than punching them (and a
  bulwark's shots come from the middle of it).
- **Boarding drones and Ion Intruders don't fly at Zoltan shields:** they
  go for the ships without a super shield up (a twin's or triple's copies
  share those out), and while every ship has one, the slot won't turn on,
  and no drone parts are spent, until a shield's down.
- **The later enemies always have locked doors:** from a third of the way
  through the game, a ship that has room for a doors system always has
  one, at level 2 (level 3 from two thirds of the way), which the player's
  boarders, boarding drones and Ion Intruders have to break down, shooting
  at each door, rather than walking through.
- **The bulwarks have their own images,** the anti-personnel drone's scaled
  up two and three times by a pixel-art scaler (Scale2x and Scale3x), with
  sharp edges and smooth diagonals rather than blocky pixels.
- **More Zoltan Shields to stack:** a store's augments sell one now and
  then, more often later on (2% at the start, 25% by the end); a boss gives
  one a third of the time; the Anvil Yards' quest gives one; and taking two ships
  with Zoltan Shields by boarding in one fight gives one of theirs.
- **The act 2 civilian sectors offer their quests as the player arrives,**
  so they're never missed, and no other quest can be put over a beacon
  that gives one.
- **The act 2 civilian sectors' quests send the player onwards:** the
  courier's station, the lost prototype and the hermit are a few jumps
  ahead, near the sector's exit, never behind the player or next door, or
  else in the next sector.
- **The Anti-Ship Battery's shots go through shields, a Zoltan super shield
  too,** and always breach the hull where they hit, but come far less
  often: a minute or so apart (the first after half a minute).
- **An ionised system's icon in its room flashes ion blue,** as in vanilla.
- **The Ion Intruder looks like itself,** with vanilla's own animations
  rather than the boarding drone's, and walks slower. It ionises each
  system once, hard: it charges for four seconds, wings down, raises them
  slowly once, and discharges three points of ion (fifteen seconds) with
  an ion flash, stunning the crew in the room, then lowers its wings as
  slowly and goes on to a system that isn't ionised (or waits, if they all
  are), rather than zapping the same one over and over.
- **Enemies send boarding drones and Ion Intruders:** an enemy ship with
  the drone power for one has one 10% of the time at the start of the game,
  rising to 30% by the end (an Ion Intruder half the time, with Advanced
  Edition), rather than almost never.
- **The last stand's fleet takes two beacons a jump,** as in vanilla.
- **The flagship fights on automated** once its crew are dead, as in
  vanilla, rather than giving up.
- **An exit beacon inside a nebula cloud is a nebula,** with vanilla's
  nebula exit text.
- **The Reverse Ion Field protects the super shield** from ion damage too.
- **A hacking pulse breaks mind control,** and the Zoltan Shield Bypass
  lets mind control be picked against a ship with a super shield.
- **The Engi and other weak fighters are the last picked to fight or
  board,** and an enemy's limit on boarding parties isn't reset by a reload.
- **Enemy ships are seen jumping away:** an escaping ship streaks off
  ahead in a white flash, with the FTL drive's sound, rather than vanishing.
- **Waiting out of fuel in a hazard gets the ship away from it,** as in
  vanilla: a sun, pulsar, asteroid field or ion storm is left behind, with
  vanilla's escape event (a storm leaves a plain nebula). Overtaken by the
  fleet while waiting, its ships show in the background.
- **More of vanilla's little touches:** fire extinguishers spray, with their
  sound; drones' lights glow while they're on; the backup battery's power
  bars have the white padlock while it's on; an active system's icon
  (the cloak, hacking, mind control, the battery) flashes blue, and an
  ionised one is blue in the enemy's window; a crew box flashes white with
  the skill's icon when a skill levels up (with vanilla's sound), yellow
  while they're stunned, and red when they're badly hurt; the cursor
  over a door is vanilla's door cursor; an event's hull damage makes the
  sound of a hit; and looped sounds (fires, fist fights) fade away rather
  than cutting off.
- **Boarders go home after a surrender,** as in vanilla: once the player
  accepts a ship's surrender (or its leader's, for them all), the player's
  crew on it teleport back, and its boarders on the player's ship go back to it.
- **The clonebay shields its crew from radiation,** as the medbay does:
  crew standing in it don't lose health in a radiation nebula.
- **Boarders sabotaging a system punch it,** as in vanilla, with the
  sound of a fist fight, rather than standing still: the Lanius and Rockmen, who only punch up and down,
  punch up or down whichever way they face.
- **The Crystals' LOCKDOWN button** is half as tall, white with rounded
  corners.
- **Double and triple beams are always their full length,** as long again
  as the beam (or as far as the next enemy window) for each extra copy,
  however many enemy ships there are and whether they're cloaked, to be
  worth what they cost.
- **Waiting out of fuel counts as a jump to the same beacon:** the weapons
  lose their charge (rather than staying charged for the next fight), the
  drones outside are left behind (or recovered by the Drone Recovery Arm),
  and the shields are refilled.
- **The Drone Recovery Arm gets back combat and boarding drones** left
  behind when the enemy's beaten, as well as those orbitting the ship.
- **The shields keep whole layers:** when an ion or damage takes a bar, the
  other bar of that layer goes back to the reactor, rather than sitting in
  the shields doing nothing, and comes back with it.
- **Enemy ships have the reactor for all their systems,** their optional
  systems (a medbay, a teleporter and so on) too.
- **Short of power (in an ion storm, say), the enemy powers its weapons**
  right after its shields, taking power from its other systems if it has
  to.
- **Dark nebulas cut off the hacking system** as well as the drones, as its
  probe is a drone.
- **Defence drones' guns turn** (a turn a second), and only fire once
  they're pointing at their target.
- **The Shield Overcharger only glows while it's charging** a point, not
  while the shield's full.
- **The crew repair the medbay sooner,** and sooner still while hurt crew
  are waiting for it, as in vanilla.
- **A crew member's skills panel stays open** while the mouse is over its
  skills, rather than closing as soon as it leaves the crew box, and each
  skill has its tooltip, as in vanilla: what it gives them at their level,
  and how far they are from the next.
- **A hacking probe waiting for a cloak to drop shows where it's going,**
  with a red hacking icon on its room, as the teleporter and mind control
  do.
- **A mind control waiting on a cloaked ship can be cancelled,** by
  clicking its button again, as with the teleporter.
- **The augments' names are centred in their boxes** on the equipment
  screen, a pixel higher than they were.
- **Mind control only reaches the crew a ship can see,** for the player and
  enemies alike: through its sensors (an enemy needs them at level 2 to
  see the player's ship, as the player does), or the rooms its own boarders
  are in, or anywhere with the Lifeform Scanner or a Slug of its own crew.
  Enemies could control anyone, and the player's Slugs didn't help.
- **Slugs sense everyone,** as in vanilla: with a Slug among the player's
  crew, the crew in every room of both ships can be seen, as with the
  Lifeform Scanner (as well as the rooms next to the Slug, as before).
- **The teleporter's and mind control's symbols on a cloaked ship's room**
  are red with a thin black outline, rather than blue and purple, which
  looked like an ionised and a hacked system.
- **The Distraction Buoys say they've been deployed** at the end of a new
  sector's start event, as in vanilla.
- **Nebulas come in clouds:** a sector's nebula beacons are gathered into
  one or a few clouds of neighbouring beacons, as in vanilla, rather than
  scattered across the map.
- **Every engine's power counts for evasion:** a ship with two engines
  dodges by their power added up (over its size), rather than by the
  weaker one, so one off or broken only loses its share; and each manned
  engine gives its share of the manning bonus.
- **Sounds:** the doors open and close with vanilla's sound, for the player
  and the crew walking through them; and the engines' sound is for them
  all, as the first starts and the last stops.
- **A shot passing through the shields makes them flash,** as does one
  they stop, so a missile getting through can be seen.
- **A badly hurt crewmember's box blinks red** in the crew list, below a
  quarter of their health.
- **The Mantis slash rather than shoot** across the room, as in vanilla:
  no little lasers from them.
- **The repair drone keeps out of rooms with boarders in them,** where its
  repairs would only be undone, and goes to fix the other systems instead.
- **The ship behind the main menu is fully crewed:** every system that can
  be manned is in, with someone at each station, and a boarding party of
  four waits in the teleporter, with nobody standing about. Its one Lanius
  has the cloaking room (put in too) to themselves, draining the air from
  that room only.
- **Crew already at a station keep it:** the crew AI gives a station to
  whoever's standing at it, rather than sending someone else over to find
  it taken.
- **Scouting the sector in a mirage nebula** (such as hiring a mercenary to)
  has an even chance of getting through the mirages: the other beacons stop
  lying while you're in one, though those in a mirage still do. Before, it
  didn't help at all.

### Fixes

- **A twin or triple drone in an event's rewards stays inside the box:**
  its wider icon stuck out past the box's left edge.
- **A Zoltan shield's super-shield is up as soon as it's bought** (or
  got from an event), rather than after the next jump.
- **The free drone with the drones system fits in the store's info box:**
  a long name that wraps onto a second line makes the box taller, and
  moves the system's levels down below it, rather than running into them.
- **The free drone with the drones system is one the ship hasn't got,**
  where there's one left, so a ship with several drones systems doesn't
  get the same Anti-Personnel Drone with each.
- **A Crystal's skills panel has no line above its LOCKDOWN button.**
- **The skills panel stays open on the way to it:** moving the mouse
  slowly from a crew box across to the skills (or a Crystal's LOCKDOWN
  button) no longer closes it in the gap between them.
- **A twin beam against three ships hits the one it's swept through:** its
  copy went to the leftmost of the other ships, even when the beam went
  through the middle one.
- **Each enemy ship takes home its own hurt boarders:** with several ships
  boarding, they all went for the same boarder, which only one of them
  could take home.
- **The door cursor's animation is drawn right:** its frames came out
  skewed, in pixel triangles.
- **A Crystal's LOCKDOWN button is as wide as the wider skills panel.**
- **A Crystal's lockdown is ready again after a jump,** however long it
  had left to recharge.
- **Twin and triple Ion Intruders are Ion Intruders,** with their look and
  their discharge, rather than ordinary boarding drones; their icon in the
  stores and the equipment is the Ion Intruder's own.
- **A game saved in a fight with a ship whose layout's changed since
  loads:** the doors and rooms that aren't there any more are left out
  (the new ones start as they would), and anyone saved manning a system
  that's gone from their room stands there instead. It crashed after the
  generated ships got their holds.
- **No one stands on a bulwark's cells:** boarders sent into its room
  (or already there when it's launched) go to the room's other cells, or
  the next rooms if there are none, and its health bar is over its middle.
- **Fixed a crash buying a drone with no room for it:** the store only
  checked for a free weapon slot, so with one free, but the drone slots and
  the cargo full, it sold the drone and then crashed.
- **A door's highlight follows its halves** as it opens and closes, rather
  than showing through the gap between them.
- **Drones go where they're put:** with several drones systems, their boxes
  in the drones bar are in the same order as in the ship window (the first
  at the top), so a drone put in the first one's slot is there in the fight.
- **The beacons the fleet has taken stay taken through a save:** they were
  free again after loading until the fleet reached them again, and the
  last stand's (and the Blockade Line's other exits) never were.
- **The Ion Intruder drone ionises systems:** it goes from system to
  system, ionising each, and leaves the crew alone, rather than acting as
  an ordinary boarding drone.
- **The fleet's advance is drawn under the beacons' labels** on the map,
  as in vanilla, so they stay readable.
- **Stores are in plain nebulas:** a store's beacon in a radiation, mirage,
  dark or ion storm nebula is an ordinary nebula instead, so its crew
  aren't hurt and the store window opens.
- **The clonebay brings back boarders left on an enemy ship** that's
  destroyed or gets away: they die with it, rather than vanishing.
- **Buying a clonebay in place of the medbay keeps its upgrades** (and the
  other way round).
- **A hacking pulse on the engines or the piloting takes all the ship's
  evasion,** on ships with several engines systems too.
- **The Devourer can't come back to life** by eating an asteroid as it
  breaks up.
- **Aiming a beam highlights the rooms it'll hit** with whichever weapons
  system it's in.
- **Missiles go through any amount of shields,** as on a ship with two
  shields systems (their piercing of 5 used to only beat five layers).
- **Anti-drone drones go after every enemy drone,** switching to one they
  can shoot when theirs is out of reach or pausing, rather than waiting on
  it and leaving the rest alone.
- **Defence drones defend all of a big ship,** its front too: vanilla's
  area they defend is widened to take in a bigger ship's shields.
- **The system repair drone puts out fires and patches hull breaches** in
  any room, going to them as it goes to damaged systems: before, it only
  dealt with those it came across in a room with a damaged system.
- **A system gets its power back once it's repaired,** or the ion wears
  off, as far as the reactor allows: even one the player never set the
  power of (such as at its starting level), and the rest comes back with
  the next repair if the reactor was short. The weapons and drones systems
  turn back on the weapons and drones they had on, which they didn't at all.
- **Defence drones launched together spread out** round the shields,
  each starting at its own point, rather than all on the same pixel.
- **No "Ready" under the jump button while the jump's unavailable,** such as
  with the piloting or engines down to a pulsar.
- **The long-range scanners only reach the beacons next to the ship,** not
  every beacon it's ever been next to.
- **The shields critical warning** only comes on when none of the shields
  systems can raise a layer (one of two broken isn't critical), with
  vanilla's warning sound.
- **Enemies aim their Healing Burst at their own side:** their hurt crew, or
  their boarders on the player's ship, and hold it otherwise, rather than
  firing it at the player's crew (and a repair bomb at their own damaged
  systems).
- **Fixed beacons out of reach on the sector map,** such as a store whose
  neighbours were all just too far away to be linked to: a beacon the
  player can't get to from the start is linked to the nearest one they can.
- **Fixed upgrading at a hazard in a nebula,** such as asteroids in a mirage
  nebula: the ship window couldn't be opened at a hazard, unless the beacon
  was in a harmless nebula as well.

## 2026-10-25: version 0.6-alpha

Thanks to u/ActivityFew2621 on Reddit for reporting many of the bugs fixed
here.

### Features

- **Four bosses, size 3:** the Devourer (a Lanius hive-ship at the Maw's
  exit), the RSV Vigilance (the Blockade Line's command dreadnought), the
  Stormbreaker (CONCORD's Mantis hunter flagship at the Gullet's exit) and
  the Ossuary (CONCORD's Engi salvage ark at the Belly's exit). Each is
  twice as wide and as tall as a long ship, fights the player alone, and
  holds its sector's exit: the player can't leave the sector until it's
  beaten, and each jump away it mends a tenth of its hull and its systems.
  They have 8 to 12 twin and triple weapons (more triples the stronger
  they are), 20 to 40 crew in big rooms, one to four Zoltan shields, and
  their own mechanics: the Devourer eats asteroids and has no air, the
  Vigilance's crew act as one until a third of them die, the
  Stormbreaker's swarm boards from two teleporters and its relay links
  them, and the Ossuary's slipway rebuilds its drones. Their boarders are
  waiting in their teleporters, and go as the fight starts. See
  doc/game/bosses.md.
- **Boss rewards:** 150 to 400 scrap, fuel, missiles and drone parts, the
  boss's own twin or triple weapon and more of them, rare drones and
  augments, and an augment found nowhere else: the Devourer Hive-Core
  (asteroids repair the ship), the Vigilance Command Codes (automated
  Rebel ships charge slower in act 2), the Chorus Relay Shard (no mind
  control for the first 10 seconds of a fight) and the Ossuary Schematics
  (a destroyed ship's drones are salvaged as drone parts).
- **The medbay, clonebay, oxygen and teleporter up to six bars** too, each
  bar past vanilla's costing 150, 300, then 600 scrap and adding less: the
  medbay healing 3.75, 4.25, then 4.5 times as fast; the clonebay cloning
  in 6, 5, then 4.5 seconds and healing 30, 34, then 37 a jump; the oxygen
  refilling 7.5, 8.5, then 9 times; and the teleporter recharging in 8, 7,
  then 6 seconds. The enemies' go as far, further in.
- **Hacking and mind control up to six bars, and the backup battery up to
  four,** as the cloaking: past vanilla's, each bar costs far more (150,
  300, then 600 scrap) and adds less (hacking 12, 14, then 15 seconds; mind
  control 32, 35, then 37 seconds; the battery 6, then 7 power). The
  enemies' go as far, further in.
- **The race augments are sold:** those vanilla only gives in events
  (Rock Plating, Mantis Pheromones, Titanium System Casing and so on) can
  turn up in any store, rarely, and much more often in their race's
  sectors (Rock Plating in the Rock ones, say).
- **Over capacity:** the items and augments the ship gets with no room
  for them wait in vanilla's over capacity boxes, together in a frame by
  the equipment window, which opens for them. Drag one onto something the
  player doesn't want to swap them, or drop something onto a box to leave
  it behind; what's left in them is left behind when jumping.

### Changes

- **The Maw, the Gullet and the Belly:** the hazardous sectors are renamed
  (from the Maelstrom, the Tempest Arm and the Rift), as the parts of a
  beast that eats ships, and they're on every map. The Rebel fleet advances
  faster in a boss's sector: 1.1 times as fast in the Maw, up to 1.35 in
  the Belly. A boss's beacon is a nebula with one to three more hazards
  (the Vigilance's is an ordinary one), and the BOSS label shows it on the
  map.
- **Every ship boards:** every enemy ship with a teleporter sends boarders
  from the first sector, rather than hardly any early on.
- **The Zoltan Shield Bypass lets an enemy board** through the player's
  Zoltan shield, as it lets it mind control through it.
- **A calm start to every sector:** its start beacon has no hazards, at
  most a plain nebula, and only describes the sector, with its own start
  event: the vanilla sectors that shared one have their own, each hybrid
  sector has one about its two races under the hum, and the Radiation
  Fields, the Mirage Expanse and the Dark Reach start at their edge.
- **Fuel for a big ship:** stores, refuelling stations and fuel sellers
  have twice as much fuel for the Chonkestrel (at the same price a unit),
  which takes two a jump. The jump to the next sector takes fuel too, as
  in vanilla, and in the Maw, the Gullet and the Belly every
  jump takes a unit more, as their start beacons warn.
- **A gentler first few sectors:** a beacon has one hazard at most in the
  first sector and two in the second (a sector later on easy), and an
  event's boarders are two at most there, rather than an ion storm with
  asteroids, a pulsar and four boarders at the third beacon of the game.
- **The Chonkestrel's Burst Lasers come first** in each weapons system,
  ahead of the Artemis.
- **The oxygen and doors systems aren't sold any more** (the oxygen for 0
  scrap), as piloting isn't: the Chonkestrel has them from the start.
- **The medbay shows its healing,** as vanilla does: green crosses rising
  over the crew it's healing (red ones in a hacked medbay), with its sound.
- **Boarders on another ship show where they're going,** with the same
  green marker as on the player's ship.
- **"Crew killed" over an enemy window** once its crew are all dead, while
  the player's boarders are still aboard, and its rooms stay visible to
  the sensors until they're taken home (in a fight with several ships,
  they went dark as soon as the crew died).
- **Are you sure?** As in vanilla, before jumping away with crew still
  aboard another ship, and before buying a clonebay that replaces the
  medbay (or the other way round).
- **The event window grows to fit a lot of rewards,** such as a fight's
  with several ships, staying in the middle of the screen.
- **Hacking a teleporter brings its ship's boarders home,** as in vanilla.
- **Mind control waits for a cloaked ship to uncloak,** as the teleporter
  does, rather than reaching through the cloak, and the rooms both are
  waiting for show the teleporter's and mind control's symbols (blue and
  purple, without the round frame).
- **The crew list keeps the order the crew joined in:** a new crewmember
  (such as a quest one) goes at the end, and boarders keep their place
  when they leave the ship and come back, rather than moving to the end.
- **Engi Med-bot Dispersal shows its healing** on the crew it heals
  outside the medbay (while the medbay's powered, as it needs).
- **Crew shoot little lasers** at enemies across the room, with vanilla's
  small arms sounds.
- **The teleporter's and the cloak's cooldowns end** once the player's out
  of danger, and when jumping.
- **Doors flash red when boarders hit them,** so it's clear which one
  they're breaking down.
- **Anti-personnel and repair drones go back to the drones room** when
  there's nothing for them to do, rather than standing wherever the last
  job was.
- **The anti-personnel drone walks as slowly as a Rockman,** rather than
  as fast as a human.
- **Sounds for crew fighting:** vanilla's brawling loop while there's a
  fist fight (one sound, louder the more fights, as in vanilla), the small
  arms sound for each shot, and for boarders breaking down a door.
- **The odd double or triple weapon in civilian and hostile stores:** a
  double one 3% to 15% of the time in a civilian store (none before) and a
  triple one 1%, and a triple one 2% in a hostile store (none before).
- **A crew-killed ship stays on the screen** after its boarders are taken
  home, with its hull closed, until the player jumps away, rather than
  disappearing.
- **No firing at a cloaked ship,** by the player or the enemy: a weapon
  aimed at one stops charging, as in vanilla (in a fight with several
  ships too), and holds its fire till the cloak's gone (on autofire, too).
  What's already on its way when a ship cloaks still flies on. A twin or
  triple weapon's copy aimed at another ship that's cloaked fires at the
  main target instead.
- **Rewards grow with every sector,** rather than every other one, so the
  first sectors give a little more scrap.

### Fixes

- **The rewards window lines up:** the new crew come last, centred like
  the rest, and every row has the same gap after it (a drone's row ran
  into the next).
- **A dark nebula's warning stays up** while the ship's in it, as its
  drones stay off.
- **Fixed rewards shown ship by ship** in a fight with several ships whose
  events do more than give scrap (such as rescuing a civilian ship from
  automated ships): the event is shown once, after them all, with the
  others' salvage in one window, rather than each ship's in turn.
- **Fixed the game drawing nothing** while its window isn't focused: it's
  still drawn, at 20 frames a second.
- **Fixed boarders on the other ships of a fight** (not the main enemy
  ship) being left off the crew list and not moving when ordered to: they
  couldn't be picked, or brought to the teleporter's room to be taken
  home.
- **Fixed the crew selection box being drawn under the enemy windows,**
  so it couldn't be seen while selecting boarders on an enemy ship.
- **Boarders stop breaking a ship's systems** once it has given up or lost
  its crew.
- **No more boarding a ship that's given up or lost its crew:** the
  teleporter only brings the boarders still on it home.
- **Fixed stores never offering the drones system with a drones section,**
  as vanilla always does for a ship without it (nor the shields or a
  medical system for a ship without them), and stores offer only the
  systems the ship can still buy, not those it has. A bigger ship gets a
  systems section half the time while it has an empty system room, as a
  vanilla one does while it has fewer than 11 systems.
- **A teleporter only brings home its own ship's crew:** the enemy's could
  take the player's crew they were fighting (such as mind controlled ones)
  back with their boarders, and the player's could take the enemy's.
- **The doors can't be opened or closed while the doors system is
  destroyed,** and they shut (letting the crew through), so the vented
  rooms get their air back and the doors can be repaired.
- **Adv. FTL Navigation works:** the ship can jump to any beacon it's
  been to in the sector, not just the ones next to it.
- **Firing a beam cuts the ship's cloak short,** as the other weapons do
  (unless it has Stealth Weapons): beams didn't touch it.
- **A double or triple weapon's other copies don't aim at a cloaked
  ship:** they aim at the other ships fighting, or with none, the main
  target.
- **No weapon charge bars on a ship that's given up or lost its crew.**
- **Fixed the game stopping when a crewmember walked out of the rooms**
  (it happened with a Rockman boarder between two rooms): they're put
  back in the room they were leaving, and find their way again.
- **A hacking probe comes off a ship that stops fighting** (it surrenders,
  or its crew are killed), rather than staying on it after the fight.
- **Fixed loading older saves** with a drone out or a hacking probe
  attached (from before ships could have several of those systems).
- **Fixed a crash with the frame step key unbound,** when fast-forwarding.
- **The ships of a fight all follow the same rules:** a wingman is beaten
  only once its boarders on the player's ship are dead too, and its weapons
  power down; the main enemy ship isn't kept fighting by another ship's
  boarders; and a beaten main enemy ship is kept in a saved game, so the
  wingmen's windows don't move about on loading. When one of the ships
  goes, the rest keep what their AI was doing.
- **Stunned crew stay stunned** after loading a saved game.
- **Fourteen more vanilla augments work** (they did nothing before):
  - Titanium System Casing: a 15% chance a system takes no damage from a hit
  - FTL Recharge Booster: the FTL drive charges 25% faster (each one)
  - FTL Jammer: enemy ships take twice as long to jump away
  - Distraction Buoys: the rebels are held back a jump at each sector start
  - Drone Recovery Arm: the drones left behind by a jump give their parts back
  - Drone Reactor Booster: the ship's drones walk 25% faster
  - Explosive Replicator: a 50% chance a missile weapon uses no missile
  - Hacking Stun: everyone in the hacked room is stunned during a pulse,
    with vanilla's stars spinning over their heads
  - Lifeform Scanner: the crew in every room can be seen, even without sensors
  - Mantis Pheromones: the crew walk 25% faster
  - Rock Plating: a 15% chance the hull takes no damage from a hit
  - Crystal Vengeance: a 10% chance a crystal shard flies at the enemy each
    time the ship's damaged
  - Slug Repair Gel: breaches seal themselves, as fast as a crew member
    repairs them
  - Reverse Ion Field: a 50% chance ion weapons' damage does nothing (it
    only kept off pulsars' ion pulses before)
- **The Repair Arm works:** each time scrap's collected, it repairs two
  hull (more for a bigger ship) for 15% of the scrap, unless the hull's
  whole.
- **No "Enemy FTL charging" warning** over a ship whose crew are dead.
- **Selecting crew can start on a door:** a door only opens or closes when
  it's clicked without dragging.
- **Fixed shots finishing off a ship that had surrendered:** as in vanilla,
  the shots already on their way still hit it once the surrender's
  accepted (or its crew are dead), but harmlessly.
- **Fixed idle crew never going to the medbay** to heal, however hurt.
- **Fixed saving the game failing** with an enemy's boarding drone aboard
  from a ship that had got away or been destroyed.
- **Fixed a sector sometimes failing to generate,** without a start beacon.

## 2026-10-24: version 0.5.1-alpha

Thanks to the player on Reddit who reported boarders stranded by an
enemy's shield overcharger drones.

### Features

- **Hybrid sectors in act 2.** After the blockade, the factions' own
  sectors give way to 28 hybrid sectors, one for each pair of races, such
  as the Rock-Zoltan Controlled Sector, with 50 events of their own about
  life under CONCORD (a pool for each race), and both races' music.
  Three in four of its enemies are that pair's hybrids; the rest are other
  hybrids, and vanilla ships, fewer and fewer through act 2. Act 2's
  civilian sectors are the Free Colonies, the Anvil Yards and the Quiet,
  and a homeworld you didn't find in act 1 can still turn up; the Slug
  nebulas are gone from act 2 too. The
  Assimilated Core, the Splice, the Datavaults, the Graveyard of Giants,
  the Ion Storm Belt and the Choir are gone, with their events.
- **Cloaking up to six bars:** one cloaking system, upgraded further,
  rather than several to take in turns. Past three bars (15 seconds),
  each costs far more (150, 300 and 600 scrap) and adds less and less
  (17, 19, then 20 seconds at six). The enemies' cloaking gets there too, further in. Its
  button is drawn taller for the extra bars.

### Changes

- **One reactor panel in the upgrades tab:** a reactor of more than 50
  bars is shown in one taller panel, with its bars in two tiers, filled a
  column at a time from the left, rather than split into two panels.
- **Scrambled defense drones glitch:** against a ship with a Defense
  Scrambler, a defense drone's gun spins round anticlockwise, and it
  sparks, as a stunned system does, rather than just sitting still.
- **Stores show the systems you already have,** greyed out, rather than
  leaving them out. Piloting is never sold, and the engines cost 60 scrap
  rather than 1, as a big ship can have a second engines room.
- **Triple weapons in a triangle:** the triple ion weapons, Heavy Lasers,
  Hull Lasers and missile launchers are drawn as three of the weapon, two
  behind and one in front, which every shot comes out of, and their lights
  come on copy by copy as they charge. The burst lasers, Crystal weapons,
  beams and bomb launchers stay as they were.

### Fixes

- **Fixed spare sensors left looking at nothing:** with more sensors
  systems than enemy ships, the best ones (manned or upgraded) watch the
  ships, rather than the first ones, so a manned third sensors system shows
  the second ship's weapon charge when the second one isn't manned.
- **Fixed twin and triple weapons in a mirage nebula** looking like twin
  or triple flak guns, Vulcans and other weapons that don't come in twos
  or threes: in a mirage, an enemy's double or triple weapon now looks
  like another double or triple one.
- **Fixed boarders stranded on a beaten enemy ship:** once it's surrendered
  or lost its crew, its shield overcharger drones kept its Zoltan shield
  up, and the teleporter wouldn't bring them home through it. A super
  shield only stops the teleporter while you're fighting the ship.

## 2026-10-24: version 0.5-alpha

Thanks to u/ActivityFew2621 on Reddit for reporting many of the bugs fixed
here.

### Features

- **Drag weapons and drones between slots** in their bars, out of combat.
- **Twin and triple weapons.** Two or three of a weapon in one slot, each
  copy firing at a different enemy ship at once, with faint "shadow"
  crosshairs on the others; a twin or triple beam is one long beam through
  the enemy windows. They cost more, charge slower and use more power and
  missiles than one weapon, but less than two or three. Twin ones turn up
  in stores and on enemies further in; triple ones in the stores of nebula
  and hazardous sectors, and on the enemies of the most dangerous sectors.
  See doc/game/weapon-sizes.md.
- **Stores depend on the sector.** Civilian sectors' are packed full of
  ordinary things, over two pages; hostile and nebula sectors have fewer
  stores, with less in them but rarer things, double weapons and trained crew;
  a hazardous sector has one store at most, with one or two of the rarest
  things. Later stores have more missiles, twice as many by the end. See
  doc/game/stores.md.
- **Bigger sector maps:** a 9x6 grid, with about 52 beacons, more than
  twice vanilla's.

### Changes

- **Rewards after a fight with several ships** come together once they're
  all dealt with, a line for each ship.
- **Hull in proportion:** long and wide ships, and hybrids, have their
  normal ship's hull times a set amount for their shape.
- **The upgrades show the manned level** of the sensors and doors, the
  enemy's systems are in the same order as yours, and with several sensors
  systems each watches its own enemy ship, left to right.
- The Chonkestrel starts with level-2 engines, a 16-bar reactor with the
  medbay off, and room for eighteen crew. Fights build up through each
  sector, and hybrids turn up after the blockade.
- Two Zoltan shields show under the shields indicators' bubbles, one
  each, as a single one does; only more of them get their own panel.

### Fixes

- **Fixed augments that did nothing:** the Defense Scrambler, Fire
  Suppression, the Scrap Recovery Arm and the Zoltan Shield Bypass (whose
  ship can teleport and mind control through a Zoltan shield) now work.
- **Fixed boarding drones** not breaching the hull where they land, the
  missing intruders warning and low crew health sound, the engines' sound
  playing when the enemy's turn off, and nebulas not showing on the map.

## 2026-10-22: version 0.4.2-alpha

### Features

- **Display options.** A new DISPLAY button in the escape menu picks the
  game's size from those that fit your screen, and changes it straight
  away. On Retina screens it can draw the game a pixel to a screen pixel.
  Your choice is remembered, unless it doesn't fit the screen next time.

### Changes

- **The interface scales up** on sizes bigger than 1920x1080, rather than
  getting smaller; on wider or taller screens the extra room goes to the
  game.

### Fixes

- **Fixed the options crashing** the game if opened right after it
  starts.
- **Fixed the game shrinking** when started on a Mac with a Retina screen
  and a normal main screen.

## 2026-10-22: version 0.4.1-alpha

### Features

- **Retina and other high-DPI screens:** the game now runs on them at its
  full size, in a window as big as fits, instead of saying the screen's too
  small.

### Changes

- Wide ships' hull bars are easier to read, three-digit fuel, missile and
  drone counts fit their boxes, and enemy windows just say the ship's
  class.

### Fixes

- **Fixed the game not starting** with game data that has a drone, weapon
  or other item it can't read (such as from a different version of FTL, or
  mods): it's now skipped instead.
- **Fixed hybrid ships** with doors from a room to itself, which made fires
  in those rooms misbehave, and saving the game with boarders aboard from a
  ship that's gone.
- **No more one-cell rooms** in the long Rebel and Zoltan Fighters.

## 2026-10-22: version 0.4-alpha

### Features

- **Long and wide enemy ships.** Big player ships now fight bigger versions
  of every vanilla enemy ship: long ones (worth two ships) and wide ones
  (worth three), with more hull, crew and systems, up to 7 weapons,
  cloaking, and what each race is known for, such as Mantis ships with big
  teleporters full of boarders. They turn up from sector 2, alongside the
  usual ships, with bigger rewards to match.
- **Hybrid ships.** 140 ships glued together from two races' scouts and
  bombers, such as the front of a Federation Scout on the back of a Slug
  Interceptor, crewed half by each race, with both races' systems, weapons,
  drones and augments, and long and wide versions too. They're rare in the
  first act, and most of what you fight after the blockade. Every one has
  anti-personnel and system repair drones aboard.
- **Game over.** Losing your ship's hull, or all your crew, now ends the
  game, with the option to start again.
- **A new story: CONCORD.** The Rebels built an AI to win the war for them,
  and it took over their fleet instead. In act 1, there are hints of it in
  the vanilla sectors: ships whose crews stare at nothing while their comms
  hum, a voice promising that "Concord is coming", and the odd hybrid ship.
  At the Blockade Line, the command ship's log, and the data the player has
  carried all game, explain: CONCORD is joining the galaxy into one mind, and
  its core, the Mainframe, is at the coordinates in the data. In act 2, the
  vanilla sectors each have one of CONCORD's events, and the act 2 sectors
  are rewritten for it: the Federation Remnant is now the Free Colonies,
  and the Rebel Heartland the Assimilated Core.
- **Four new act 2 sectors:** the Quiet, where Zoltan monks drown out the
  hum; the Splice, where CONCORD makes its hybrid ships; the Datavaults, its
  memory, on asteroid server farms; and the Choir, a mirage nebula that
  carries the hum.

### Changes

- **Long enemy windows fit smaller screens,** stopping above your weapons
  rather than covering them.

### Fixes

- **Anti-personnel drones work.** They used to do nothing; now they hunt
  down boarders on their own ship.

## 2026-10-21: version 0.3-alpha

### Features

- **Up to four hazards at once.** A beacon can now have two (an extreme
  hazard), three (catastrophic) or all four (apocalyptic) of a nebula,
  asteroids, a sun and a pulsar at once, in 39 combinations, never two of a
  kind. The beacon map marks them in red, purple and cyan. They're rare
  early on (never all four in the first 4 sectors), and more and more
  common through the game: in the last sectors, most hazards come three or
  four at a time.
- **Two new hazardous sectors:** the Tempest Arm (sectors 9-12), where most
  beacons have three hazards at once, and the Rift (sectors 11-15), where
  most have all four. The Maelstrom moves to sectors 5-8, and the three of
  them come at most once a game. The new nebula sectors turn up from sector
  5, with hazards at every beacon. Even the most dangerous sectors have
  calm beacons: stores (rarer in the worst ones), quests, and a calm beacon
  with a special event.
- **Two oxygen systems.** The Chonkestrel has a second oxygen system from
  the start. The rooms refill at the average of the two, so losing one
  halves the refill (and at level 1, the other only just keeps the air in).
- **A third sensors system,** to see into the third enemy ship, for sale
  in stores. The Chonkestrel's mind control rooms are now 2x1, to make room
  for it and the doors.
- **Slugs are telepathic,** as in FTL: you can see into the rooms next to
  your slugs, even without sensors, such as in a nebula, or on an enemy
  ship.
- **The version and build date** are in the bottom-right corner.

### Changes

- **Hazardous sectors on the sector map,** in dark purple, with their own
  entry in the legend.
- **A bigger second act.** Past the blockade, the sector map opens up to
  four to eight sectors a column, twice as many as act 1, for more choice
  of route. The vanilla civilian sectors are rare there now: the
  Federation's space is behind the Rebel lines.
- **Nebulas show they block your sensors:** the sensors' icon turns blue,
  their power bars are crossed out, and their tooltip says why.
- **The stacked Zoltan shield panel** moves under the fuel counter, next to
  the shields.
- **Every crew name set at once** (2600 names) in the downloads, rather
  than just the game's own.

### Fixes

- **Shield overchargers work.** The Shield Overcharger drone used to do
  nothing. It now orbits the shields and recharges a row of five
  super-shield points, a point every 15 seconds. Each one adds a row, like
  a Zoltan shield, as long as it's switched on.
- Backup batteries can't take your power past the reactor limit of 100.
- The "paused" text no longer covers the second row of weapons boxes.
- The highlight around a drone slot is no longer shifted to the left.
- A ship can have at most 8 subsystems (piloting, sensors, doors and
  backup batteries), and the Chonkestrel at most 16 main systems.

## 2026-10-21: version 0.2-alpha

### Features

- **Extreme hazards: two at once.** A beacon can now have two hazards at
  the same time, such as a nebula (of any kind) with an asteroid field, or
  a pulsar near a sun. Both work at once, with both their warnings, and the
  beacon map marks them in red, as an extreme hazard. They're as strong as
  act 2's hazards, and their suns flare and pulsars pulse twice as often.
- **The Maelstrom,** a new act 2 sector, where a dying star, a pulsar and
  the wreck of a nebula churn through an old asteroid belt. Every beacon
  has a hazard, and about half have two, apart from the start, stores,
  quests and a rare calm beacon with an Engi survey station.
- **Mirage nebulas disguise the enemy's weapons.** In a fight in a mirage
  nebula, each enemy weapon looks like a weapon of another kind, so what
  looks like a laser might fire missiles, and what looks like a beam might
  drop a bomb.
- **Autofire.** The **AUTOFIRE** button under the weapons (or **V**)
  makes weapons keep firing at the room they're aimed at (or along the
  beam's line) each time they're charged, until you aim them somewhere else.
  Hold **left Ctrl** while aiming a weapon to do the opposite for just that
  weapon. Weapons on autofire have yellow crosshairs (and beam lines) and
  charge bars, as in FTL. It's saved with the game.
- **Linux.** A download for 64-bit Linux, with Java built in, like the
  others. Windows on ARM is no longer offered: the x64 download runs there.

### Changes

- **Shots fly across the whole screen.** Your shots fly all the way to the
  screen's edge, and the enemy's come in from it, instead of vanishing and
  appearing in the middle of the wider screen. They fly a little faster
  the whole way, so they hit at the same time as before. That's every shot
  that flies between ships: lasers, missiles, flak, ion and asteroids.
- **A 1920x1080 window**, the size the big ships' interface is laid out
  for, instead of one that fills the screen. On a 1920x1080 screen, it's
  as big as fits with the title bar and taskbar.

### Fixes

- Enemy ships start with their shields up again, as in FTL, so the weapon
  pre-igniter can't get a free volley in.
- Beams no longer get stuck on the ship they hit, even across jumps: one
  could stay on after it had finished firing (and its weapon never fired
  again), or after its ship was destroyed or left while it was firing.
- Enemy boarders no longer sometimes wait in their teleporter for good.
  The extra crew enemies get against a bigger ship could fill the
  teleporter, leaving no room for a boarder from elsewhere, whom the rest
  waited for. Now crew already in the teleporter board first, and the
  others don't wait for anyone who can't get in.

## 2026-10-21: version 0.1-alpha

### Features

- **Download and play** on macOS (Apple Silicon) and Windows (x64 and
  ARM), with Java built in, so there's nothing else to install. You need
  your own copy of FTL.
- **Finds your copy of FTL** from Steam, GOG or Epic Games, the FTL app on
  macOS, or next to the game. If it can't, drop `ftl.dat` onto its window, or
  choose it.
- **Starts straight away** with the Chonkestrel.
- **Saves your game** after every jump and when you quit, and carries on
  from there the next time, as in FTL.
- **Checks your screen is big enough** for the Chonkestrel's interface
  (1920x1080), and tells you if it isn't, instead of starting a game that
  doesn't fit.
- **A log** of each game, to send with bug reports.

### Fixes

- The game starts on more graphics cards and drivers.
- With three enemy ships, the rightmost one's weapon bars stay on the
  screen.

## 2026-10-20: version 0.0.3-alpha

### A longer, richer campaign

- **Two acts.** The sixteen-sector game is now a story in two halves. Halfway
  through, every game reaches **the Blockade Line**, where the Rebels try to
  stop you finding out what the data you carry really is: the location of the
  Anvil Yards, the secret shipyard of Project Chonkestrel. Its exit is guarded
  by the blockade's command ship.
- **Five new sectors for the second act:**
  - **Federation Remnant**: cut-off colonies with drydocks, volunteers and
    armouries.
  - **The Anvil Yards**: the hidden shipyard, with a free refit for your ship.
  - **Rebel Heartland**: checkpoints, propaganda relays, prison transports
    and the Rebels' home guard.
  - **Graveyard of Giants**: the wrecks of the war's capital ships, full of
    salvage and the things guarding it.
  - **Ion Storm Belt**: permanent ion storms, ambushes and smugglers.
- **The Crystal Homeworlds** can now turn up in the second act.
- **No more repeats.** You won't get the same kind of sector twice in a row,
  or the same choice twice at a fork.
- **About 140 new events**: for the new sectors, for every faction's vanilla
  sectors (mostly in the second act, so the first half still feels like
  vanilla FTL), and for big ships like the Chonkestrel. Many of them have
  several versions of their text.
- **Tougher second act.** Asteroid fields, suns and pulsars are stronger,
  and nebulas are thicker, so FTL drives charge slower in them.

### New nebulas

- **Radiation nebulas** (green) slowly hurt any crew outside the medbay.
- **Mirage nebulas** (purple) fill your map with ships and hazards that
  aren't there.
- **Dark nebulas** (dark grey) cut off your drones, as well as your sensors.
- Each has a sector of its own, where most of the beacons are that kind of
  nebula: **the Radiation Fields**, **the Mirage Expanse** and **the Dark
  Reach**. They also turn up among ordinary nebulas everywhere else.
- A warning tells you what kind of nebula you've just arrived in.

### The Chonkestrel

- **Fights up to three ships at once**, starting with one at a time in the
  first sector, and growing to three by the middle of the game. It sits in
  the middle of the screen when there's no fight.
- **Twice the hull**, split into half-cells on the hull bar, but it's easier
  to hit.
- **Uses two fuel a jump**, but starts with twice as much.
- **Two sensors systems**, each seeing into a different enemy ship.
- **Its own events**: stations too small to dock with, crowds cheering a
  Federation cruiser, towing freighters, Rebel battle groups and bounty
  hunters sent after it, and ramming small ships out of the way.
- **Rewards and costs that grow with the ship**, such as repairs and
  supplies in the new events.
- **Leaves a wake through nebulas**, so they slow the Rebel fleet down less
  when you're flying it.
- **Shown first** in the ship list, with an outlined miniature.
- A new picture for its wider cloaking room.

### Crew names

- **169 more crew names**, twice as many as the game had, to go round the
  bigger crews.
- **Name sets** to pick from: American, English, Irish, Scottish, French,
  Spanish, Italian, German, Russian, Chinese, Japanese, Star Wars and Star
  Trek, with 100 male and 100 female names each.

### Interface

- **Tooltips on the beacon map**, as in vanilla: hover over a beacon to see
  what's there and what kind of hazard it is.
- **Tooltips on the sector map**: hover over a sector to see what type it is.
- The extra sensors systems are grouped together in the subsystems panel.

## 2026-10-19: version 0.0.2-alpha

### Bigger ships

- **The Chonkestrel**, a big, symmetric take on the Kestrel, as a ship of its
  own, picked by default in the hangar (the vanilla Kestrel is still there).
  It has room for 8 weapons, 6 drones, two of most systems, 16 crew
  around a 4x4 medbay, 8 cargo and 6 augments.
- **Ship sizes.** Big ships fight two copies of each enemy ship at once, so
  they still get a fight, and enemy boarding parties are bigger against
  them, from two to four times by the end of the game. The Rebel flagship is
  as big as the Chonkestrel, so it fights it alone.
- **Big ships dodge worse.** Their evasion comes from their weakest engine,
  so the Chonkestrel needs both of its engines upgraded, and cloaking helps
  them less.
- **A reactor of up to 100 power**, shown in narrower columns.

### More of everything

- **Several shields systems**, whose layers stack.
- **Several drones systems**, each with three drone slots, all shown in the
  hangar.
- **Several hacking, mind control and battery systems.**
- **More cargo and augments**, and **Zoltan shields that stack**, up to six,
  with a panel showing each one.
- **Sixteen sectors a game**, twice as many as vanilla, with more sectors to
  choose from at each step.
- **Sector maps twice as big**, with twice as many events, and a slower
  Rebel fleet.
- **Stores twice as big**, with twice as many items in each section.
- The numbers on the targeting crosshairs for weapons 5 to 8 no longer
  overlap.

## 2026-10-18: version 0.0.1-alpha

- **The window fills the screen**, with the backgrounds scaled to fit.
- **Several weapons systems** per ship, with up to eight weapons, and hotkeys
  for weapons 5 to 8.
- **Up to sixteen crew**, with hotkeys for crew 9 to 16.
