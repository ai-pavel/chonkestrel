# Stores

What a store has depends on the sector it's in (`StoreProfile`): the busier
and safer the sector, the more stores it has and the more they have, but the
more ordinary it is; the more dangerous, the fewer stores and the less in
them, but the rarer it is.

| | Civilian | Hostile | Nebula | Hazardous |
|-|-|-|-|-|
| Stores in the sector | all of them | three quarters | half | one at most |
| Sections | 4 (two pages) | 2 (one page) | 2 | 1 |
| Things in each section | 6 (full) | 6 | 3 (half the shelf) | 1 or 2 |
| Fuel, missiles and drone parts | all | three quarters | half | a quarter |
| Rarer things likelier | no | no | yes | yes |
| Double weapons (start to end of the game) | 3% to 15% | 10% to 30% | 25% to 50% | 50% |
| Triple weapons | 1% | 2% | 5% | 30% |
| Crew for hire (extra training) | none | one skill two levels on | two two levels on, one fully | two two levels on, three fully |

- **Crew for hire** have every skill a level or so in further into the
  game (about level 3 of 5 by the end), and the extra training on top of
  that; see [skills.md](skills.md).
- **Fuel** is for the player's ship's size: a store has twice as much for
  the Chonkestrel, which takes two a jump, so as many jumps' worth.
- **Hazardous sectors** are those whose every beacon has a hazard: the new
  nebula sectors, the Maw, the Gullet and the Belly.
- **Stores in the sector:** the sector's store events are dropped at random
  until it has that share of them (rounded up), so a hostile sector with four
  stores keeps three.
- **Sections:** a store's systems section (half the time, while the ship has
  fewer than 11 systems, or for a bigger ship, which has more than that from
  the start, while it has an empty system room) is one of them. As in
  vanilla, it always offers the drones system with a drones section, if the
  ship hasn't got it, and the shields and a medical system if it hasn't got
  them, and then the systems the ship can still buy at random; not those it
  already has, nor those it has no room for. The drones system comes with a
  free drone, as in vanilla: a common one (rarity 1 or 2, such as Combat I
  or Defense I), picked with the store (`StoreData.freeDrone`). Piloting, the
  oxygen and the doors are never sold (their rarity is 0), as the
  Chonkestrel has them all from the start, and the engines cost 60 rather
  than vanilla's 1, as a big ship can have a second engines room.
- **Rarer things likelier:** each weapon, drone, augment and crew race is
  picked as likely as its rarity (1 to 5), rather than the commoner ones
  being likelier, as everywhere else.
- **Double and triple weapons** go in place of the single one (see
  doc/game/weapon-sizes.md). The chance of a double one rises through the game, by
  how far through it the sector is. A sector can set its own chances
  (`xftlStoreDoubleChance` and `xftlStoreTripleChance` in
  sector_data.xml.append): the Chonkestrel mod's three act 2 civilian
  sectors, the last free shipyards, have 35% double and 12% triple ones,
  with the civilian stores' full shelves.
- **Missiles:** there are more of them the further through the game the
  sector is, twice as many by the end, for the double and triple weapons.
- **Crew for hire:** which of their skills they have learnt is random.
- **Race augments:** the ones vanilla never sells (only events give them,
  their rarity is 0) can turn up in any store, as the rarest things
  (rarity 5): Mantis Pheromones, Crystal Vengeance, Drone Reactor Booster,
  Zoltan Shield, Engi Med-bot Dispersal, Rock Plating, Slug Repair Gel and
  Titanium System Casing. Each is much commoner in its race's sectors and
  hybrid sectors (rarity 2 or 3, set in sector_data.xml.append and
  tools/hybrid_sectors.py), and the casing in the civilian sectors. The
  Damaged Stasis Pod, a quest's, still isn't sold.
- **Zoltan Shields** stack (each adds a row of five super-shield points, up
  to six), so on top of that, a store's augments section has one in place
  of one of its augments 2% of the time at the start of the game, rising to
  25% by the end (`StoreData.addZoltanShield`), unless the ship has six.
  Otherwise they come from the Anvil Yards' quest, a boss (a third of the time)
  and boarding: taking two ships with Zoltan Shields in one fight gives
  one of their shields (`InGameState.zoltanShieldBoarded`).

Rewards from events and fights aren't affected: a weapon in one is a double
one 10% of the time.
