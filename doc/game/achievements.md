# Achievements

Each of the mod's twenty player ships has six achievements of its own:
three the same for every ship, and three for its race, which its Type A
and Type B share as goals but have as separate achievements (except the
Federation's and the Stealth ships' two, each with three of its own). They're
vanilla's achievements (kept in the profile by ID, with the difficulty
they were unlocked on), with a goal XFTL tracks through a game to unlock
them. Vanilla's own achievements are left as they were: XFTL doesn't
track them.

## The definitions

They're in the mod's `data/achievements.xml.append`, in vanilla's format,
with an XFTL extension for the goal:

```xml
<achievement id="XFTL_ACH_XFTL_ROCK_4">
    <name>Rock Salvo</name>
    <desc>Fire \1 missiles in a single game.</desc>
    <img>achievements/xftl_chonkwark_4.png</img>
    <ship>PLAYER_SHIP_XFTL_ROCK</ship>
    <xftlGoal kind="missiles" target="400"/>
</achievement>
```

The `target` is the number to reach (each kind has a default, used if
it's left out), and it goes in the description in place of `\1`, so
changing it changes the text too. A kind XFTL doesn't know (or none) makes
it a plain vanilla achievement, never unlocked by playing. The IDs are
`XFTL_ACH_<ship>_<n>`, the ship being its blueprint's name without
`PLAYER_SHIP_`, and `n` 1 to 6 in the order below. The kinds are listed in
`AchievementGoal.Kind` (`game/Achievement.kt`).

## The achievements

Every ship's first three:

| # | Name | Goal (kind) |
|--:|---|---|
| 1 | Blockade Breaker | Beat the RSV Vigilance, the Blockade Line's boss (`blockade`) |
| 2 | Mainframe Down | Win the game: destroy the Mainframe's last phase (`win`) |
| 3 | Against All Odds | Win the game on Hard, or on Insane (`winHard`) |

And three for its race, each within a single game:

| Ships | 4 | 5 | 6 |
|---|---|---|---|
| Human: Chonkestrel, Chonk-Tail | **Outnumbered**: win a fight against 3 ships at once (`fleetFight`) | **Steady Hands**: beat a boss without the hull going below 50% (`bossHull`) | **Full House**: win with 18 crew alive (`crewAtWin`) |
| Engi: Chonktorus, Chonkvortex | **Swarm Doctrine**: finish off 25 enemy ships with drones (`droneKills`) | **Hive Mind**: 9 drones out at once (`dronesOut`) | **Nobody Left Behind**: win with no crew lost (`winNoLosses`, at most 0) |
| Zoltan: Chonkudicator, Chonkoether | **Unbreakable**: shields stop 1000 damage (`shieldAbsorbed`) | **Radiant Bulwark**: start a fight with a super shield of 15 (`superShield`) | **Untouchable**: beat a boss without losing any hull (`bossUnharmed`) |
| Mantis: Chonkila Monster, Chonkilisk | **Bloodsport**: boarders kill 100 enemy crew (`boarderKills`) | **Empty Ships**: kill the crew of 15 ships (`crewKills`) | **Regicide**: kill a boss's whole crew (`bossCrew`) |
| Slug: Man of Chonk, Chonkwalker | **Puppet Master**: mind control 20 enemy crew (`mindControls`) | **Ghost in the Machine**: 40 hacks (`hacks`) | **Fog Dweller**: pass through 3 nebula sectors (`nebulaSectors`) |
| Rock: Chonkwark, Chonkivan | **Rock Salvo**: fire 400 missiles (`missiles`) | **Forge Fire**: 8 fires on one enemy ship at once (`enemyFires`) | **Full Broadside**: win a fight with all 9 weapons firing (`allWeapons`) |
| Federation A: Chonksprey | **Orbital Bombardment**: 300 hull damage with your artillery beams (`artilleryDamage`) | **Sweeping Beams**: finish off 10 enemy ships with your artillery (`artilleryKills`) | **By the Book**: win without ever having a hacking or mind control system (`winNoMindTricks`) |
| Federation B: Chonknisos | **Humanity First**: win with only humans in your crew (`winAllHuman`) | **Terms Accepted**: accept 5 enemy ships' surrenders (`surrenders`) | **Heavy Ordnance**: destroy a boss with a hit from your artillery (`bossArtillery`) |
| Stealth A: Chonkasio | **Ghost Ship**: 150 enemy shots miss you (`dodges`) | **Not a Scratch**: win 10 fights without losing any hull in them (`flawlessFights`) | **Thin Skin**: reach sector 8 without ever having a shields system (`noShieldsSector`) |
| Stealth B: DA-CHONK 24 | **Phase Shift**: fire 50 volleys while cloaked (`cloakedVolleys`) | **Double Blind**: cloak again within 3 seconds of a cloak ending, 10 times (`cloakChains`) | **Shadow Strike**: destroy a boss while cloaked (`bossCloaked`) |
| Lanius: Chonkruos, Chonkrike | **Vacuum Embrace**: suffocate 50 enemy crew (`suffocated`) | **Airless Ascent**: win without ever having an oxygen system (`winNoOxygen`) | **Hostile Takeover**: beat 10 ships with your boarders aboard (`boardedWins`) |
| Crystal: Chonkravais, Chonkelian | **Phantom Prism**: 100 enemy shots miss while cloaked (`cloakMisses`) | **Lockdown Protocol**: 25 lockdowns by your Crystal crew (`lockdowns`) | **Unseen Blade**: destroy 10 ships while cloaked (`cloakedKills`) |

The Federation's ships start with two Artillery Beams each (`ARTILLERY_FED`)
and mostly human crews, and the Stealth ships with two cloaking systems and
no shields, so their goals are about those, and differ between the Type A
and B. The ships are in the hangar's order, the Lanius and then the
Crystal ships last, in the file and the achievements window.

## How they're tracked

`game/AchievementTracker.kt`, one for each game (`InGameState.achievementTracker`),
keeps a count for each kind of goal: a count that goes up (missiles fired),
a best (the most drones out at once), or nothing, for an event checked when
it happens (the win). The counts are kept whatever the ship, but only the
ship's own achievements are checked, and they're saved with the game
(`<achievements>` in the save; an older save starts from nothing). When a
goal's met, the achievement's unlocked in the profile at the game's
difficulty, and a notice (its icon, vanilla's "Achievement Unlocked!" and
its name) shows at the top of the screen for five seconds, without pausing.

Nothing goes in the profile in debug mode, for the screenshots and videos,
the automatic playtest or the tests (as with the statistics); the notice
still shows. The debug console's `ach-set <id> <difficulty>` unlocks one
by hand, `ach-del <id>` removes it (and its best progress), and
`ach-progress <id> <value>` sets this game's count for its goal.

The profile also keeps the best progress made in any one game towards each
achievement with a number (`<achProgress>` in the profile), for the
windows; it's written as the ship jumps, when one's unlocked, when the
pause menu opens, and at the end of the game, rather than every time it
changes.

Where each is counted:

| Kind | Where | Exact? |
|---|---|---|
| `blockade` | `InGameState.colossalBeaten`, for the Vigilance (destroyed or its crew killed) | exact |
| `win`, `winHard`, `crewAtWin`, `winNoLosses`, `winNoOxygen` | `PlayerShipUI.showGameOverScreen` with a win: the crew alive anywhere (not those waiting in the clonebay), `GameStats.crewLost`, and whether the ship ever had an oxygen system (checked every update) | exact |
| `fleetFight` | A fight is from the first hostile ship to the last gone; it's won if every ship in it was destroyed or had its crew killed, and the count is the most hostile ships at once (reinforcements count; a ship that surrenders or escapes, or the player jumping away, means no win) | close |
| `bossHull`, `bossUnharmed` | A boss (a size 3 boss, a Mainframe phase or the flagship) beaten in a fight: the player's lowest hull in that fight, as a percent of the most, and whether it went below the hull at the start | close: a fight started before loading a save starts again from the load |
| `droneKills` | An enemy ship destroyed whose last hull hit was a drone's shot (`Damage.fromDrone`: a combat drone's projectile, or a beam drone's beam) | exact |
| `dronesOut` | The player's drones out and powered (every copy of a twin or triple), every update | exact |
| `shieldAbsorbed` | `Ship.attackShields` on the player: a shield layer popped counts the shot's hull damage, a super shield the damage it took | close: beams weakened by shields don't count |
| `superShield` | The player's super shield as a fight starts | exact |
| `boarderKills` | An enemy crewmember killed in combat aboard an enemy ship by someone on the player's side there (a boarder, a boarding drone, or one of theirs mind controlled) | close |
| `crewKills` | An enemy ship's crew all killed (as `GameStats.shipsCrewKilled`) | exact |
| `bossCrew` | A boss's crew all killed, even if it then fights on automated | exact |
| `mindControls` | The player's mind control taking an enemy crewmember | exact |
| `hacks` | Each of the player's hacking probes landing, and each hacking pulse | exact (as defined) |
| `nebulaSectors` | Entering a sector whose type is a nebula one (vanilla's nebula sector, or one with `xftlNebula`) | exact |
| `missiles` | The missiles each of the player's weapons uses as it fires (even when the Explosive Replicator saves them) | exact |
| `enemyFires` | The fires on each hostile ship, every update | exact |
| `allWeapons` | The player's weapon slots that fired in a won fight and still hold a weapon | close: fired at least once in the fight, rather than all at once |
| `suffocated` | An enemy crewmember dying of suffocation, anywhere | exact |
| `boardedWins` | An enemy ship beaten with any of the player's crew aboard it | exact |
| `cloakMisses`, `dodges` | An enemy shot that misses the player (`AchievementTracker.playerDodged`), while the cloak's up for `cloakMisses` | exact (beams don't miss) |
| `flawlessFights` | A won fight (see `fleetFight`) in which the player's hull never went below what it was at its start | close, as `fleetFight` |
| `noShieldsSector` | Entering a sector (its number, from 1) while the player ship has never had a shields system (checked every update, and saved) | exact |
| `cloakedVolleys` | One of the player's weapons (in a weapon slot, so not a drone's or an artillery's) firing while the cloak's up | exact |
| `cloakChains` | One of the player's cloaks starting within 3 seconds (`AchievementTracker.CLOAK_CHAIN_TIME`, of play) of the last one ending, as with two cloaking systems | exact (not saved across a save in the middle of it) |
| `bossCloaked` | A boss destroyed while the player's cloaked | exact |
| `lockdowns` | One of the player's own Crystal crew (not mind controlled) locking a room down | exact |
| `cloakedKills` | An enemy ship destroyed while the player's cloaked | exact |
| `artilleryDamage` | `Ship.damage` on an enemy ship: the hull damage of a hit from an artillery system's beam (`Damage.fromArtillery`, set from `AbstractWeaponInstance.isArtillery`) | exact for beam artillery (the Federation's); a projectile artillery's shots (only bosses have them) don't count |
| `artilleryKills`, `bossArtillery` | An enemy ship (or a boss) destroyed whose last hull hit was an artillery beam's (`Ship.lastHullHitByArtillery`) | exact, as above |
| `winNoMindTricks` | At the win: the player ship never had a hacking or mind control system (checked every update, and saved) | exact |
| `winAllHuman` | At the win: every living crewmember (wherever they are) is human | exact |
| `surrenders` | A surrender accepted: one ship, or every ship still fighting when its leader spoke for them all | exact |

## The screens

- **The pause menu**: the achievements box is taller than vanilla's (a row
  of its image repeated in the middle), with the ship's six icons in two
  rows of three at their full 64x64 (a vanilla ship shows its family's
  three). A locked one is dimmed with a padlock; hovering outlines it and
  shows its name, description, this game's progress and the best in any
  game (for one with a number), and the difficulty it was unlocked on.
  The quest mark stays (the mod's ships have no quest, so it's hidden),
  and the victory mark lights up once the ship's won. The box's title
  ("Achievements - see all ships") is a button that opens the window below,
  which comes back to the pause menu.
- **The achievements window** (`game/AchievementsWindow.kt`), from the pause
  menu or the main menu's Achievements: 1240x820, the total unlocked in
  its title, and every ship with achievements, two to a row in the hangar's
  order (each race's A beside its B; ten rows, for the twenty ships): its hangar miniature at half size,
  name, how many of its six are unlocked, and the six icons at 48x48, with
  the same tooltips (the best progress, and the difficulty). It scrolls if
  there are more than fit. Back or escape closes it.
- **In debug mode** (`XFTL_DEBUG`), both show every achievement unlocked
  (on Normal, unless it really was on another difficulty), to see their
  icons; the profile isn't changed (`SaveProfile.getAchievement`).

## The icons

`tools/achievement_art.py` makes them from the definitions (see
[art](../tools/art.md)): each is vanilla's grey 64x64 square with the
ship's hangar miniature, a dark ship given a pale edge, marked as vanilla
marks its ships' three, with vanilla's star (cut from kestral_2.png): the
Blockade's has the ship alone, the win's the star over it, and the win on
Hard's the star with red rays behind it, as vanilla's first, second and
third; the race's own three have one, two and three gold stars, the
last (the hardest) on red rays too. The
achievements window shows them at 48x48, and each ship at half its
miniature's size, from copies the script makes at those sizes (scaled down
smoothly, and made from the hull), as the game's own scaling skips pixels.
