# Difficulty

The four difficulties: vanilla's Easy, Normal and Hard, and XFTL's own
Insane after them, picked in the main menu's new game window
(`NewGameWindow`). Every value is a property of `Difficulty`
(`game/Difficulty.kt`), so the rest of the game asks the difficulty rather
than checking which one it is. Where Insane does what Hard does, the code
asks `Difficulty.atLeast(HARD)`.

The difficulty's saved by its name, in the game, the profile's unlocks and
the statistics, so older saves load as they were.

## The effects

| | Easy | Normal | Hard | Insane | Where |
|-|-|-|-|-|-|
| Enemy ships' sector (`sectorOffset`) | -1 | 0 | 0 | +1 | `ShipGenerator.buildShip` (`effectiveSector`) |
| Scrap rewards' sector (`rewardSectorOffset`) | +1 | 0 | -1 | -2 | `LootDropGenerator` |
| Starting scrap, per ship size (`startingScrapPerSize`) | 30 | 10 | 10 | 10 | `Ship.startingScrap` |
| Enemies' optional system chance in the first sector, +10% a sector (`optionalSystemBaseChance`) | 10% | 20% | 30% | 40% | `ShipGenerator.buildShip` |
| Enemies' extra general power (`extraGeneralPower`) | 0 | 0 | +1 | +2 | `ShipGenerator.buildShip` |
| An optional (non-offensive) system's power cost (`cheapOptionalSystems`) | 2 | 2 | 1 | 1 | `ShipGenerator.buildShip` |
| Most random extra bars on an enemy's system (`systemPowerOffsetMax`) | 1 (0 in the first sector) | 2 | 2 | 2 | `ShipGenerator.pickSystemPowerLimit` |
| Zoltan shields on the first sector's enemies | removed | kept | kept | kept | `ShipGenerator.buildShip` |
| Attacks to break a level 2/3/4 door (`doorHealths`) | 12/16/20 | 8/12/18 | 6/10/15 | 5/8/12 | `Door.maxHealth` |
| The map's sector: hazards at once, early boarders (`mapSectorOffset`) | -1 | 0 | +1 | +2 | `GameMap.effectiveSectorNumber` |
| Bigger enemies' boarders' sector (`boardingSectorOffset`) | -1 | 0 | +1 | +2 | `ShipScaling.boardingMultiplier` |
| The last stand's exit, in vanilla columns | 2-3 | 2-3 | 3-4 | 3-4 | `Sector` (`exitRange`) |
| The fleet's event at an overtaken exit | `FLEET_EASY_BEACON` | `_DLC` | `_DLC` | `_DLC` | `InGameState` (the jump's events) |
| Crew AI: shields manned before engines | no | no | yes | yes | `FriendlyCrewAI` (`ManningTask`) |
| Score multiplier (`scoreMultiplier`) | 0.5 | 1 | 1.5 | 2 | `GameRecord.score` |
| Counts for `winHard` (Against All Odds) | no | no | yes | yes | `AchievementTracker.gameOver` |
| The fleet's head start, in jumps (`fleetHeadStartJumps`) | 0 | 0 | 0 | 1 | `Sector` (`dangerZoneCentre`), `Sector.startFleetPursuit`, `InGameState.advanceFleetLastStand` |
| The fleet's extra step, every so many jumps (`fleetExtraStepEvery`) | never | never | never | 4 | `Sector.isFleetExtraStep`, `InGameState.advanceFleet` |
| Enemies aim at the key systems (`enemiesTargetKeySystems`) | no | no | no | yes | `ShipAI.keySystemTarget` |
| Name (`startButtonKey`) | `easy_button` | `normal_button` | `hard_button` | `xftl_insane_button` | `NewGameWindow`, `PauseWindow`, `StatisticsWindow` |
| Achievement unlocked on it (`achievementUnlockKey`) | `ach_easy` | `ach_normal` | `ach_hard` | `xftl_ach_insane` | `PauseWindow`, `AchievementsWindow` |

## Insane's Rebel fleet

The fleet's front is a circle (`Sector.dangerZoneCentre`) that moves right
each jump (see `InGameState.advanceFleet` and `Sector.getFleetAdvanceFor`).
On Insane:

- **It starts a jump closer:** the circle starts a jump's worth further on
  (`DANGER_ZONE_ADVANCE`, times the sector's `xftlFleetSpeed`), so it
  takes each beacon a jump sooner. Where a hunting boss's sector takes up
  the chase once it's beaten, the fleet turns up a jump nearer
  (`Sector.startFleetPursuit`).
- **Every 4th jump it takes an extra step:** each sector counts the fleet's
  advances (`Sector.fleetJumps`, each jump and each wait out of fuel,
  saved with the sector), and on the 4th, 8th and so on it advances twice
  as far as that jump would (`Sector.isFleetExtraStep`). The jump map
  shows where it'll be after the extra step (`JumpWindow`).
- **In the last stand,** where the fleet takes two beacons a jump, it takes
  a third on the first jump (its head start) and on every 4th jump
  (`InGameState.advanceFleetLastStand`).

## Insane's targeting

On Insane, the enemies' weapons that can aim at any room (lasers, missiles,
ions, flak and so on) go for the player's most valuable systems rather than
a random room (`ShipAI.keySystemTarget`): the shields, then the weapons (and
the Artillery Beam), then the piloting, then any other system, at random
among those of a kind. A system that's fully destroyed is skipped, unless
they all are. Bombs, weapons that only hurt crew, beams (swept between two
rooms) and the support weapons that heal or repair keep their vanilla aim.

This is BUGS.md's "Нацеливание врага на Hard" (the enemies aiming at
random on Hard), done for Insane only: Hard stays as vanilla.
