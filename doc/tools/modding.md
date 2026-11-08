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
exit as it can be, or else in the next sector (`Sector.addQuest`). An event's
ship with `xftlAlone="true"` fights alone, with no wingmen, and is never a
bigger version, so its rewards come once (for quests, whose rewards are set
by hand).
