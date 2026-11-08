# Screenshots

The screenshot generator plays through a list of scenes showing off the main
new features, saves a screenshot of each, and quits. It's for the project's
site.

## Running it

```sh
XFTL_SCREENSHOTS=$PWD/dist/screenshots XFTL_SCREENSHOTS_SIZE=1920x1080 XFTL_RANDOMIZE=1 make run
```

This runs the game with a 1920x1080 window, and with `XFTL_RANDOMIZE`, so
the ship has a random mix of weapons rather than the debug loadout's.
It needs the Chonkestrel mod enabled (see the README).

- `XFTL_SCREENSHOTS=<dir>` (or `-Dxftl.screenshots=<dir>`): the directory to
  save the screenshots into. It turns on debug mode, and skips the main menu to
  start with the default ship (the Chonkestrel), except for the first
  screenshot.
- `XFTL_SCREENSHOTS_SIZE=<width>x<height>` (or `-Dxftl.screenshots-size`):
  the window size. Without it, the window is 1920x1080 as usual. On a
  high-DPI screen, the screenshots are the size of the framebuffer, so they
  can be twice as big.

`XFTL_SCREENSHOTS_SET=boss` takes a screenshot of
a fight with each of the four size 3 bosses, at its sector's exit (see
[bosses.md](../game/bosses.md)), alone, with
their crew shown even in the Stormbreaker's ion storm, and then the
Devourer's rewards window (resources, weapons, a drone, augments and a crew
member), to check how the rewards are laid out.

`XFTL_SCREENSHOTS_SET=shape`, `hybrid` and `mixed` take other sets of
screenshots, of the long and wide
enemy ships, the hybrid ships and the long and wide hybrids, which aren't for
the site (see [ship-shapes.md](../game/ship-shapes.md) and
[hybrid-ships.md](../game/hybrid-ships.md)). Their enemies are armed with triple
weapons, or double ones where they can't power triples, to check how
they look on them, and the Chonkestrel has `XFTL_RANDOMIZE`'s triple and
double weapons.

It keeps running while the window isn't focused, and the mouse is taken to
be off the window (see `LWJGLInput`), so nothing's hovered wherever it is. It takes about half a minute.

## The scenes

Each is saved as `<number>-<name>.png`, in this order:

1. `new-game`: the main menu's new game window, as New Game opens it, with
   the difficulty and the crew names, over the Chonkestrel. It's taken in
   the main menu, which the screenshots go to first (the next scene
   starts a new game again).
2. `sector-map`: the sixteen-sector map, with the second act twice as big,
   and the hazardous sectors in dark purple.
3. `beacon-map-mirage-expanse`: the beacon map of a Mirage Expanse sector,
   every beacon scanned (as debug mode does), with false ships and hazards.
4. `twin-and-triple-weapons-near-a-sun-in-asteroids`: an extreme hazard
   (two at once, see [extreme-hazards.md](../game/extreme-hazards.md)), fighting
   two ships at once.
5. `ion-storm-with-asteroids-and-a-pulsar`: a catastrophic hazard (three at
   once), fighting one ship.
6. `three-enemies-in-a-mirage-near-a-sun`: fighting three ships at once,
   in a Rebel-Mantis Controlled Sector (`XFTL_HYBRID_REBEL_MANTIS`, see [sectors.md](../game/sectors.md#the-hybrid-sectors)) in the second act, at an extreme hazard (a
   mirage nebula near a sun).

The ships in 4-6 are strong ships of different races, picked at random each
time (`VideoDirector.STRONG_SHIPS`), and
printed in the log (`[screenshots] Fighting ...`). They show off the double
and triple weapons (see [weapon-sizes.md](../game/weapon-sizes.md)): the enemies'
weapons are made double (in 4) or triple (in 5 and 6) where their weapons
systems can power them, and the Chonkestrel has doubles and triples of every
kind, as many as its weapons systems can power (`SITE_WEAPONS`). The fight's
fought for real (`VideoDirector.BattleDriver`), with everyone's weapons
charged as it starts, so they're firing when it's taken, two and a half
seconds in, and the enemies' crew are shown even in a nebula.

7. `upgrades`: the upgrades screen, with the bigger reactor and the doubled
   systems.
8. `cargo`: the equipment screen, with the weapons, drones, the bigger
   cargo hold and the augments.
9. `store`: a store, with twice as many items, in a hostile sector near
    the end of the game, so it sells double weapons (see
    [stores.md](../game/stores.md)): the first store found there with at least
    three double or triple weapons that are some use in a fight, and
    systems. The cargo
    hold and augments are emptied first, so everything in it can be bought.

The site's other six screenshots (10-15) aren't taken by the screenshot
generator: they're the last six fights of the trailer (see
[trailer.md](trailer.md#what-it-records)), the catastrophic and apocalyptic hazards
against hybrids with double and triple weapons, copied from
`dist/trailer/trailer-<time>-fight-11.png` to `-16.png` and named for what
they show. The last three (16-18) are from `XFTL_SCREENSHOTS_SET=boss` (see
below): the Devourer, the Stormbreaker, and the Devourer's rewards.

They're defined in `ScreenshotDirector.defaultScenarios` (in
`devutil/ScreenshotDirector.kt`). Each sets the game up, waits for it to
settle (a second and a half, or four for the fight), and takes the
screenshot. If one fails, it's logged and skipped, and the rest are still
taken.
