# The tests

What the automated tests (`src/test/java`) cover, as of 2026-10-25: 226
tests in 60 classes, all passing. Run them with:

    make test

(that is, `XFTL_DATAFILE=$PWD/ftl.dat ./gradlew test`) with JDK 17.
`make test TESTS='*CrewTest'` runs only some of them. The totals are in `build/test-results/test/*.xml`.

Most of them (186) use the test kit, `testkit/InGameTest`: a small fake
datafile (`src/test/resources/test_assets`) with a test ship, plus whatever
blueprints, events and animations the test adds as XML. They're fast and
don't need the game. The kit has stand-ins for every crew animation, so
`addCrew` (in `testkit/TestCrew.kt`) puts real crew aboard, made and
saved as the game does them, and the `noCrewAI` debug flag keeps every
ship's crew AI from moving them, so a test can stage a fight. The other 40 load the real game data (`ftl.dat`,
through `VanillaDatafile.createWithDefaultPath()`): the mod's tests (29),
with the Chonkestrel mod applied, to check its own data, and
`CombinedWeaponTest` and `BXMLTest`.

Of the 60 classes, 6 come from XFTL's original author (2023, 8 tests:
`BXMLTest`, `TestFriendlyCrewAI`, `CCDTest`, `EventsTest`, `BeamTest`,
`WeaponsTest`), and the rest were written for the Chonkestrel, between
2026-10-18 and 2026-10-25.

## By category

| Category | Tests | Classes |
|---|--:|--:|
| [The fight and the enemy ships](#the-fight-and-the-enemy-ships) | 35 | 7 |
| [Sectors and the map](#sectors-and-the-map) | 30 | 6 |
| [The Chonkestrel mod's data](#the-chonkestrel-mods-data) | 29 | 9 |
| [Drones and augments](#drones-and-augments) | 26 | 4 |
| [Debugging and tools](#debugging-and-tools) | 26 | 5 |
| [The ship's systems](#the-ships-systems) | 19 | 8 |
| [Crew](#crew) | 18 | 6 |
| [Weapons](#weapons) | 18 | 7 |
| [Environments](#environments) | 12 | 3 |
| [Saving and loading data](#saving-and-loading-data) | 9 | 3 |
| [Stores](#stores) | 4 | 2 |
| **All** | **226** | **60** |

### The fight and the enemy ships

35 tests: the several enemy ships a bigger player ship fights (the main
one and its wingmen, see `game/Opponent.kt`), how many and how big, how
they're built, and what they give.

| Class | Tests | What it checks |
|---|--:|---|
| `game/WingmanTest` | 21 | Wingmen spawn with the enemy ship and arrive with its event; a wingman's surrender (later than the main ship's) is for itself, and the leader's can be for them all; wingmen escape; the player fights all of them, and they fight the player; how many by the ship sizes, ramping up through the game; three at once; a wingman takes over when the enemy ship's destroyed, and the others move over, keeping their AI; ships at peace take no damage; left to right order, and a sensors system for each; bigger ships' hull and fuel; the boarding multiplier |
| `game/FightPlanTest` | 5 | The plan of a fight: which shapes (normal, long, wide) fit, only allowed fights, big ships only with the chance, wide ships in fights of three, and through the game |
| `shipgen/HybridSectorShipTest` | 4 | In a hybrid sector, the pair's hybrids are most of the ships, rolled in order, with always some others, and vanilla ships getting rarer through act 2 |
| `game/FleetRewardsTest` | 2 | A fight's plain rewards wait for the end, and a special event is shown once |
| `shipgen/CloakingScalingTest` | 2 | Enemy cloaking grows through the game, unless a ship holds it back |
| `ai/EnemyShieldsTest` | 1 | Enemy ships start with their shields up |
| `ai/ShipAIPowerTest` | 3 | Enemy ships have the reactor for all their systems; short of it, the AI powers its weapons before everything but the shields; a destroyed ship doesn't eat asteroids back to life |

### Sectors and the map

30 tests: the 16-sector map in two acts, its hazards, and the events'
scaling.

| Class | Tests | What it checks |
|---|--:|---|
| `sector/HunterBossTest` | 6 | The hazardous sectors' hunting bosses (`xftlHunter`): one sets out from an ordinary exit with its environment, with no fleet behind the player; it jumps a beacon at a time towards the player (or every second jump), taking its environment with it, until it's caught up; once beaten it's gone; it's saved; the Blockade Line's three exits all have its boss |
| `sector/HazardSectorTest` | 12 | The hazardous sectors: every beacon has hazards (two or three in catastrophic sectors, three or four in apocalyptic ones), few at once in the first sectors and never four in the first quarter, more later; a calm start beacon, and a start and an exit in every sector; quests and stores without them; normal nebula sectors left alone |
| `sector/SectorActsTest` | 8 | The two acts: the midpoint, act 2 twice as big, its sectors and progress, the finish event, the event filters, no repeated sector in a row |
| `sector/SectorCountTest` | 5 | All 16 sectors are on the map, its columns are linked, the difficulty is spread out, the rewards grow every sector, and vanilla's length is unchanged |
| `sector/SectorSizeTest` | 2 | Sectors are bigger for big ships, with vanilla's columns scaled |
| `sector/EventScaleTest` | 2 | Events marked `xftlScale="size"` scale with the ship, and others don't |
| `sector/EventsTest` | 1 | An event can add a crew member with a given name |
| `sector/QuestPlacementTest` | 2 | A quest marked `xftlAhead` goes a few jumps on towards the exit, as near it as it can be, and never behind the player; with nowhere ahead, it waits for the next sector |

### The Chonkestrel mod's data

29 tests, on the real game data with the mod applied: the mod's ships,
sectors, events and changes to vanilla's blueprints.

| Class | Tests | What it checks |
|---|--:|---|
| `mods/KestrelModTest` | 8 | The Chonkestrel: its files, layout, starting loadout (Burst Lasers ahead of the Artemises) and size; the bigger ships and hybrids; more crew names; the campaign's sectors |
| `mods/HybridSectorsTest` | 6 | One hybrid sector for each pair of races, only in act 2 and hostile, with each pair's hybrids to fight, their own events, and each race's pool |
| `mods/StoryTest` | 5 | The story's events load, with more further in; the omens and the chorus; the reveal; the new sectors |
| `mods/CivilianSectorsTest` | 2 | The act 2 civilian sectors: at most one of each, their stores' weapon sizes, one quest each and no vanilla ones, marked for the debug map; the quests' rewards are few enough to fit on the screen, and a combined weapon reward always is one |
| `mods/StartEventsTest` | 3 | Every sector has its own start event, which only describes it, and the hazardous sectors take more fuel |
| `mods/CloakingUpgradeTest` | 2 | Cloaking goes to six bars, each past three adding less |
| `mods/SystemLevelsTest` | 2 | Hacking, mind control, the medbay, clonebay, oxygen and teleporter go to six bars, and the battery to four, each bar past vanilla's adding less |
| `mods/FuelTest` | 1 | The fuel stations scale with the ship |
| `mods/StoreSystemsTest` | 1 | Piloting, oxygen and doors aren't sold, and the engines cost 60 |
| `mods/RaceAugmentsTest` | 1 | The race augments are sold everywhere, rarely, and mostly in their race's sectors (hybrid sectors too) |

### Debugging and tools

26 tests: debug mode, the screenshot and video tools, finding the game,
and the UI kit.

| Class | Tests | What it checks |
|---|--:|---|
| `DebugStartTest` | 9 | Debug mode's start: everything maxed out and powered, within the system limit, with random weapons, drones and augments (or Zoltan shields filling them), picked when the ship's made, and no self-targeting bombs |
| `sys/FTLFinderTest` | 6 | Finding FTL's datafile: next to the game, from the game's directories or executable, recognising datafiles and rejecting other files |
| `devutil/ScreenshotDirectorTest` | 5 | The screenshot scenarios (and the ships' ones), their sizes and file names, and flipped frames |
| `sys/ScreenTooSmallTest` | 3 | The game refuses a screen that's too small, unless a window size is chosen |
| `ui/RemoveWidgetsTest` | 3 | Removing widgets from a box and a free container |

### The ship's systems

19 tests: the systems, and ships with several of a system.

| Class | Tests | What it checks |
|---|--:|---|
| `game/BackupBatteryTest` | 3 | Several batteries add up, without going past the biggest reactor, and the debug reactor leaves them room |
| `systems/EngineEvasionTest` | 3 | Evasion as in vanilla, big ships needing two engines, fractional levels |
| `game/SystemSlotTest` | 2 | A second slot for a system is used, and empty rooms are preferred |
| `SystemLimitTest` | 2 | The number of systems a ship can have, by default and raised |
| `systems/OxygenTest` | 2 | Two oxygen systems refill at their average, and one at level 1 only keeps the air in |
| `SensorsWatchingTest` | 2 | Which sensors systems watch the enemy ships: the best ones, in order |
| `weapon/MultipleShieldsSystemsTest` | 2 | Several shields systems' layers stack, and hits take the most charged one |
| `weapon/MultipleDronesSystemsTest` | 3 | Each drones system has its own slots, drones don't move between them, and new ones fill them in order |

### Weapons

18 tests: the double and triple (combined) weapons, firing, and ships
with several weapons systems.

| Class | Tests | What it checks |
|---|--:|---|
| `weapon/CombinedWeaponTest` | 9 | The combined versions are made, only of vanilla weapons with rarities (not Advanced Edition's), each with its own image, drawn side by side; each copy fires at its own target; a combined beam's length doesn't depend on how many ships there are; doubles turn up through the game; a mirage's disguises keep the size |
| `weapon/MultipleWeaponsSystemsTest` | 3 | The hardpoints are split between weapons systems, which power their own, and weapons don't move between them |
| `weapon/AutofireTest` | 2 | Autofire keeps firing, and without it a weapon fires once |
| `weapon/WeaponSlotTest` | 1 | Weapons can skip slots |
| `weapon/WeaponsTest` | 1 | Weapon power management |
| `weapon/BeamTest` | 1 | A beam's start time |
| `projectile/CCDTest` | 1 | Continuous collision detection for fast projectiles |

### Environments

12 tests: the beacons' hazards, including the new nebulas and several
at once.

| Class | Tests | What it checks |
|---|--:|---|
| `environment/NebulaTest` | 7 | The nebula types (strange, thick, dark, mirage), dark nebulas cutting off drones (and hacking), big ships leaving a wake, nebula sectors |
| `environment/AntiShipBatteryTest` | 6 | An event's Anti-Ship Battery: who it fires at (the player, the enemy, both), whether it's a danger on the map, its shots, and that it's saved; the fleet's beacons have its battery, and that and their being taken are saved; its shots go through a Zoltan shield and breach |
| `environment/MixedEnvironmentTest` | 4 | Two, three or all four hazards at once survive saving, and are stronger |
| `systems/SensorsNebulaTest` | 1 | The sensors show they're blocked in a nebula |

### Drones and augments

26 tests.

| Class | Tests | What it checks |
|---|--:|---|
| `augments/AugmentEffectsTest` | 16 | What each augment does: Titanium System Casing (the system takes no damage, the hull does), Rock Plating (the other way round), Reverse Ion Field (on the super shield too), Crystal Vengeance (a shard at the enemy), FTL Recharge Booster (25% faster), FTL Jammer (the enemy's escape twice as slow), Mantis Pheromones (the crew walk 25% faster), Slug Repair Gel (breaches seal themselves), Engi Med-bot Dispersal (only while the medbay's powered), Stealth Weapons, Drone Recovery Arm, Repair Arm (15% less scrap, two hull), Adv. FTL Navigation and Distraction Buoys; the chance ones with a value of 1, so they always work; and weapons stunning the crew (the Stun Bomb's, and a stun chance) |
| `drones/RepairDroneTest` | 2 | The system repair drone goes to fires and hull breaches in rooms without a damaged system, and fixes them |
| `drones/IonIntruderTest` | 2 | The Ion Intruder boards, ionises the enemy's system with one strong discharge and not again until it's worn off, raising its wings once each; its twins and triples are Ion Intruders too |
| `drones/ZoltanBoardingTest` | 2 | Boarding drones and Ion Intruders aren't launched while every enemy ship has a super shield up (no parts spent), and then go only to the ships without one, a triple's copies shared out between them |
| `drones/DoorBashingTest` | 4 | Behind an enemy's locked doors, a boarding drone and an Ion Intruder stop at the door and shoot it until it breaks, then go on to the system; the later enemies always have locked doors (level 3 at the end), unless they've no room for them |
| `drones/BoardingDroneSabotageTest` | 2 | A boarding drone (and each copy of a triple one) breaks a system by shooting at it, never punching |
| `drones/CombinedDroneTest` | 12 | The twin and triple drones (see doc/game/drone-sizes.md): made from every vanilla drone but the hacking one, with their prices, power and icons; a launch sends a copy for each drone part (as many as there are parts for), turning off turns them all off, and a lost copy's sent again on its own; the copies are spread round the orbit; a bank of overchargers looks after a row each, charging in turn; the copies are saved; a combined reward is always a twin or triple, and the bosses' are the right sizes |
| `drones/BulwarkTest` | 6 | An enemy's twin or triple anti-personnel drone is a bulwark: one drone, for the parts it's worth, in the corner of its ship's biggest empty room (not a system's), holding the cells it stands on (boarders sent in go elsewhere), with four or nine times the health and two or three times the damage, and staying put; with no square free for it, it goes as a single drone; the player's is a squad; it's saved |
| `drones/ShieldDroneTest` | 7 | The shield drone adds a super shield point every charge, one row per drone, not recharging the Zoltan rows, nothing unpowered; drones deploy out of combat, and a defence drone in an asteroid field |
| `augments/ZoltanShieldTest` | 6 | Zoltan shields stack (up to a cap), losing one lowers the maximum, and selling them all removes the super shield; stores sell them more often later on; boarding two shielded ships in a fight gives one |
| `game/OverCapacityTest` | 1 | Every augment the ship has no room for waits in the over capacity box until the jump, and is kept in a saved game |

### Saving and loading data

9 tests: saved games, and reading the game's data.

| Class | Tests | What it checks |
|---|--:|---|
| `game/SaveLoadTest` | 6 | A whole game saved and loaded (without the UI): a fight with several ships, a beaten wingman or main enemy ship, no fight, saving again gives the same game, and optional attributes older saves don't have |
| `bxml/BXMLTest` | 2 | Reading the binary XML of the game's data (all of it, and the Kestrel's) |
| `BrokenBlueprintTest` | 1 | A blueprint that can't be read is skipped, rather than stopping the game |

### Crew

18 tests: living crew fighting aboard ships, boarding, and the crew list.

| Class | Tests | What it checks |
|---|--:|---|
| `crew/CrewCombatTest` | 6 | Fist fights end with one dead; boarders are shot across a room; stunned crew don't fight back, and stay stunned in a saved game; the crew list keeps the order they joined in; crew saved outside the rooms are put back on loading |
| `crew/BoardersTest` | 5 | Boarders break down a locked door to get through; the doors shut, and can't be worked, with the doors system destroyed; boarders stop sabotaging a ship that's beaten; the teleporter only brings home its own crew (not the enemy's, nor anyone else's boarders); there's no boarding a beaten ship |
| `crew/CrewNamePresetTest` | 3 | The crew name presets, the game's own names by default |
| `crew/LockdownTest` | 10 | A Crystal's lockdown seals its room's doors (shut, can't be worked or broken down) until it wears off, and the doors are left as they were; crew sent in or out (from the far side of the ship too) shoot at the door in vain, and go through once it's lifted; it recharges, and a jump recharges it; it's saved (a ready Crystal's too); the Lockdown Bomb locks the room it hits down; the enemy's Crystals lock a fight down, and the player's wait to be told |
| `crew/CrewLimitTest` | 2 | The crew limit, by default and raised |
| `crew/SlugTelepathyTest` | 1 | Slugs see into the rooms next to them |
| `systems/HackCloakTest` | 3 | A hacking pulse on a cloaked ship's cloaking (the main enemy's or a wingman's) ends its cloak; a probe held off by a cloak shows where it's going |
| `systems/MindControlVisionTest` | 4 | Mind control only reaches the crew a ship can see: through its sensors, or anywhere with the Lifeform Scanner or a Slug, for the player and enemies alike |
| `systems/ClonebayBoardersTest` | 3 | The player's boarders aboard an enemy ship that's destroyed or gets away die with it and are cloned; without a clonebay they're lost |
| `systems/PowerRestoreTest` | 4 | A system gets its power back once it's repaired or the ion wears off, as far as the reactor allows, even at its starting level; the shields keep whole layers; weapons turn back on |
| `ai/TestFriendlyCrewAI` | 1 | Hurt crew run to the medbay |

### Stores

5 tests (and two of the mod's, above).

| Class | Tests | What it checks |
|---|--:|---|
| `game/StoreProfileTest` | 2 | The more dangerous the sector, the fewer stores, with less in them but rarer, more double and triple weapons |
| `game/StoreSizeTest` | 2 | The sections are full, and only the systems the ship can buy are sold |
| `game/StoreRoomTest` | 1 | A drone's only sold with a drone slot or cargo free for it, whatever weapon slots are free |

## What isn't tested

- **The UI:** `PlayerShipUI`, `HostileShipUI`, `ShipEquipmentPanel`,
  `ShipWindow`, `StoreWindow`, the windows and the dialogues can't be
  created in a test, as they draw with OpenGL (an attempt crashes the test
  run with "No context is current"). So the over capacity boxes' drag and
  drop, the crew selection, the event window's layout and so on are only
  checked by playing. Moving layout and decisions out of the drawing code
  would let them be tested.
- **Some of living crew's fights:** mind control, hacking a teleporter (and
  the Hacking Stun), and a crewmember walking out of the rooms while
  moving (the case that once stopped the game; loading a save with one
  outside them is tested) aren't tested.
- **The automatic playtest** (`devutil/AutoplayDirector.kt`), and the
  video and trailer tools, only run in the game.
- **A few augments:** the Explosive Replicator (it needs a missile weapon
  firing), the Hacking Stun, the Drone Reactor Booster and the Lifeform
  Scanner (which only changes what's drawn) are only checked by playing,
  as are the systems' new levels in a fight.
- **The sector map's saving** (`GameMap.saveToXML`) is only covered as part
  of a whole saved game.
- **Older saves:** `SaveLoadTest` checks a save made and loaded by the
  same version, and the optional attributes; there's no saved game from
  an older version to load.
