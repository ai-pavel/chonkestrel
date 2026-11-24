# Modding extensions

XFTL reads vanilla and Slipstream mods as they are, and adds a few XML
extensions of its own, which the Chonkestrel mod (`mods/chonkestrel`) uses.

## Ship blueprints

- `<maxCrew>`, `<maxSystems>`, `<maxCargo>` and `<maxAugments>` set limits.
- `<cargo>` puts starting items in the cargo hold.
- `slot` on a `<weaponList>`'s `<weapon>` puts it in a given slot (from 1),
  for example in a second weapons system.
- `room` on `<crewCount>` sets where the starting crew stand.
- `<xftlDebug>` is the starting loadout in debug mode.
- `<shipSize>` sets the ship's size, which scales its hull, evasion and fuel
  use, and how many hostile ships it fights.
- `<opponentShips>` sets how many vanilla-sized hostile ships a player ship
  fights at once (its size by default): it fights that many divided by the
  enemy's size, between 1 and 3.
- `<opponentShipsAtStart>` sets how many it fights in the first sector,
  growing to `<opponentShips>` by the middle of the game (for ships of
  size 2 or more, as in the table in [ship-shapes.md](../game/ship-shapes.md), but
  never fewer).
- `<xftlDefaultShip/>` makes a ship the one picked by default in the hangar,
  and puts it first in the ship list.
- `<xftlVanillaShip>` names the vanilla player ship a big player ship was
  made from (`<xftlVanillaShip>PLAYER_SHIP_CIRCLE</xftlVanillaShip>`): the
  hangar's Normal and Chonky buttons switch between the two (see
  [race-ships.md](../game/race-ships.md#in-the-hangar)).

## Text

The Chonkestrel mod's events and blueprints refer to their text by key, as
vanilla's do (`<text id="..."/>`), so it can be translated: write new ones
with their English in them, and `tools/extract_mod_text.py` moves it into
the mod's English text files. See [translations.md](translations.md).

## Sectors and events

Sector descriptions can use `xftlMidpoint="true"` (the sector between the
acts), `xftlAct` (only appear in act 1 or 2), `<xftlFinishEvent>` (the
exit beacon's event), `xftlNebula` (most beacons are one kind of nebula),
`xftlWeight` (how likely it is to be picked) and `xftlHybrids` (the pair of
races whose hybrids it's held by, see [sectors.md](../game/sectors.md)),
`xftlStoreDoubleChance` and `xftlStoreTripleChance` (how often its stores'
weapons are double and triple ones, see [stores.md](../game/stores.md)), and
their events `xftlMinShipSize` and `xftlAct`.

Events can scale their effects by the player ship's size, have choices only
for big ships, and use big hostile ships when there are some. See
[lore.md](../game/lore.md). A random weapon reward with `xftlCombined="true"`
(`<weapon name="RANDOM" xftlCombined="true"/>`) is always a double one, or a
third of the time a triple one (see [weapon-sizes.md](../game/weapon-sizes.md)).
A quest with `xftlAhead="true"` (`<quest event="..." xftlAhead="true"/>`)
goes on the way to the sector's exit, at least three jumps on and as near the
exit as it can be, or else in the next sector (`Sector.addQuest`). One with
`xftlHidden="true"` isn't marked on the map, keeps its beacon's hazards, and
says nothing when it's added, so it's a trap waiting at one of the beacons
(`Beacon.hasHiddenQuest`): the archivist who sells the ship's data
(`XFTL_CIV_ARCHIVIST_AMBUSH`). An event's
ship with `xftlAlone="true"` fights alone, with no wingmen, and is never a
bigger version, so its rewards come once (for quests, whose rewards are set
by hand).
