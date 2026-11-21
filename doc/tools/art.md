# Art

Almost all of XFTL's new art is made by the scripts in `tools/` from the
vanilla images in `ftl.dat` (cut up, glued together, stretched and
recoloured), rather than drawn, so any of it can be made again. The game's
art is in the Chonkestrel mod, `mods/chonkestrel/img/` (3,452 images), and
none of it is kept in git: `make art` makes it all (see
[BUILD.md](../../BUILD.md)).

## In the mod

| What | Where | Images | Made by |
|---|---|---|---|
| Hybrid enemy ships: the front of one race's ship and the back of another's, and their long and wide versions ([hybrid ships](../game/hybrid-ships.md)) | `img/ship/xftl_hybrid_*` | 2,514 | `tools/hybrid_ships.py` |
| Long and wide versions of the vanilla enemy ships ([ship shapes](../game/ship-shapes.md)) | `img/ship/*_long*`, `img/ship/*_wide*` | 426 | `tools/ship_variants.py` |
| The size 3 bosses, among them the Devourer and the Mainframe's phases with its Firewall ([bosses](../game/bosses.md), [the Mainframe](../game/mainframe.md)) | `img/ship/xftl_boss_*` | 60 | `tools/boss_ships.py` |
| Twin and triple weapons ([weapon sizes](../game/weapon-sizes.md)) | `img/weapons/xftl_x2_*`, `img/weapons/xftl_x3_*` | 82 | `tools/weapon_sizes.py` |
| The cloaking, hacking and mind control buttons, for four to six power bars | `img/systemUI/button_*` | 30 | `tools/system_buttons.py` |
| The twin and triple Bulwarks, an enemy's indoors drones ([drone sizes](../game/drone-sizes.md)) | `img/people/xftl_bulwark_*` | 4 | `tools/drone_sizes.py` |
| The Chonkestrel: the Kestrel A four rooms taller, its cockpit bump in the middle of the nose, its airlock bracket mirrored, its weapons blocks wider; its hull, floor, cloak, gibs and shield, its bigger rooms' interiors, and its hangar miniature | `img/ship/chonkestrel_*`, `img/ship/interior/`, `img/customizeUI/miniship_chonkestrel.png` | 14 | `tools/chonkestrel_art.py` |
| The hangar's background: vanilla's (`img/customizeUI/custom_background.png`), made 1920x1080 by stretching its landing pad, where the ship's shown | `img/customizeUI/xftl_hangar_background.png` | 1 | `tools/hangar_art.py` |
| The race ships: seven races', the Federation's and the Stealth cruisers' Type A and Type B ships, and the Kestrel B, made four rooms taller, as the Chonkestrel is ([race ships](../game/race-ships.md)); their hull, floor, cloak, gibs and shield, their bigger rooms' interiors, and their hangar miniatures | `img/ship/chonktorus_*` and the like, `img/ship/interior/chonk*`, `img/ship/interior/man_of_chonk_*`, `img/customizeUI/miniship_*` | 201 | `tools/race_ships.py` |
| The ship achievements' icons, 64x64 as vanilla's: vanilla's grey square with the ship's hangar miniature, and a badge with the goal's emblem (a wall for the Blockade, a crown for the win on gold rays, an H on red rays for Hard, and one for each race's goals) ([achievements](../game/achievements.md)); made from the miniatures, so after the two scripts above | `img/achievements/xftl_*` | 120 | `tools/achievement_art.py` |

Each image of a ship comes as a set: its hull (`_base`), cloak, gibs and
shield, as vanilla's do.

## Remaking it

`make art` runs them all, in the order they must go in (see
[BUILD.md](../../BUILD.md)). Each script reads `ftl.dat` and writes into
the mod (and its layouts and blueprints, for the ships); with
`--preview out/`, it draws a preview into `out/` instead, to look at before
changing the mod, for example:

```sh
python3 tools/hybrid_ships.py ftl.dat --preview out/
```

`tools/drone_sizes.py --preview out/` also draws how every drone's twin
and triple versions are meant to look, in a little scene each, and all of
them in `all.png`.

## Elsewhere

- The game's icon: the window's is `src/main/resources/assets/img/xftl_icon.png`,
  and the app's (for macOS and Windows) are in `tools/release/icon/`, both
  made by `make icon` from the Chonkestrel as the hangar shows it (see
  [release](release.md)), and not kept in git either.
- The screenshots and videos aren't art for the game, but are made from it
  (see [screenshots](screenshots.md) and [the trailer](trailer.md)), and
  kept out of this repo.
