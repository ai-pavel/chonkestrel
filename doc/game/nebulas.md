# Nebulas

Vanilla has two kinds of nebula: plain nebulas, and ion storms. XFTL adds
three more, and makes nebulas thicker in the second act of the campaign
(see [lore.md](lore.md)). The code is in `NebulaEnvironment.kt`.

## What every nebula does

As in vanilla, every kind of nebula:

- blocks sensors, on every ship at the beacon: the sensors' icon turns blue
  (vanilla's own blue icon), their power bars are crossed out in blue, like
  the reactor's in an ion storm, and their tooltip says the nebula is
  blocking them (the player's slugs can still see into the rooms next to
  theirs, as they're telepathic, see `CrewSlug`);
- slows the rebel fleet down when the player jumps from it: it advances half
  as far from a nebula beacon in a normal sector, and 80% as far in a nebula
  sector;
- never gets a quest placed in it.

Ion storms also halve the reactor's power (rounding down).

## Big ships leave a wake

Big ships churn up the nebula's gas, and the rebel fleet can follow their
wake, so a nebula slows the fleet down by less: by the slowdown divided by
the ship's size. For a size 2 ship like the Chonkestrel, the fleet advances
75% as far from a nebula beacon in a normal sector (rather than 50%), and 90%
as far in a nebula sector (rather than 80%). See `Sector.baseFleetAdvance`.

## Thick nebulas in act 2

In the second act, every kind of nebula is thicker, and FTL drives charge at
75% of their normal speed there, on every ship (enemy ships take longer to
escape too). That makes running from a fight harder, but nebulas still hide
the player from the fleet.

## XFTL's nebulas

They look like normal nebulas, but tinted, and the circle under their beacons
on the beacon map is the same colour. In events, they're
`<environment type="radiation"/>`, `"mirage"` and `"dark"`.

| Nebula    | Colour      | Effect                                                                                     |
|-----------|-------------|--------------------------------------------------------------------------------------------|
| Radiation | green       | Crew lose 0.6 health a second (1.2 in act 2) outside their medbay or clonebay, on every ship. It shows as a hazard on the map. |
| Mirage    | purple      | The beacon map shows false ships and hazards, and hides stores and distress signals, and the enemy's weapons look like other weapons (see below). |
| Dark      | dark grey   | Drones systems (and hacking, whose probe is a drone) have no power, on every ship, so drones don't work, as well as sensors. |

A store's beacon is always a plain nebula, whatever kind the sector's other
nebulas are (and its sector's hazards aren't added to it), so shopping is
safe and the store window opens (`Beacon.environmentTypes`).

A crew member has 100 health, so radiation takes about a minute and a half
to kill someone in act 2, if they stay out of the medbay (or clonebay). Lanius, crystals
and the rest are affected the same as humans; drones aren't.

### Mirages

Unvisited beacons in a mirage nebula show false information on the beacon
map, even if they haven't been scanned, and so does every unvisited beacon
while the player is in one: half of them show a ship, and about a third a
hazard, whatever's really there. Stores and distress signals aren't shown.
Long-range scanners don't help. Revealing the map (such as hiring a mercenary
to scout the sector) has an even chance of getting through the mirages: if it
does, the player's own mirage doesn't make the other beacons lie any more
for the rest of the sector, though the beacons in a mirage still do
(`Sector.mirageScouted`); if it doesn't, the map's only revealed outside
them. The false information is picked from the beacon's seed, so it doesn't
change each time the map's opened. See `Mirage.kt`.

A mirage on its own isn't a hazard, so the ship and store windows still
open there, but any hazard added on top of it (asteroids, a sun or a pulsar)
is, as at any other beacon.

In a fight in a mirage nebula, the enemy ships' weapons are disguised: each
one looks like a weapon of another kind (laser, ion, missile, beam, flak or
bomb), so a laser launcher might fire missiles, and what looks like a beam
might be a bomb. They still fire as themselves; only how they look is
changed, along with their charging and firing animations. The disguises are
picked from the weapons that can turn up in the game, by the beacon's seed,
the ship and the weapon's slot, so they don't change during a fight (see
`Mirage.weaponDisguise`). The player's own weapons look as they are.

## Telling them apart

- **On the map:** the circle under a nebula beacon is the nebula's colour,
  and hovering over any beacon shows a tooltip saying what's known about it,
  as in vanilla: what's there, and what kind of environment it is (the
  nebulas are always shown; asteroids, suns and pulsars only once they've
  been scanned). XFTL's nebulas are called the Radiation Nebula, the Mirage
  Nebula and the Dark Nebula. While the player's in a mirage, the other
  beacons' tooltips warn that they may not be accurate.
- **On arrival:** a white warning shows for 8 seconds, at the top centre of
  the screen: "RADIATION NEBULA", "MIRAGE / VISION IS UNRELIABLE" and
  "DARKNESS / DRONES OFFLINE".

The texts are in `assets/lang/en.xml`.

## Where they are

Any plain nebula beacon, from vanilla's events (such as the nebula beacons
in civilian and hostile sectors, and all the Nebula sectors' beacons), can be
one of XFTL's kinds instead: 10% of them in act 1, and 30% in act 2, picked
evenly between the three (`GameMap.addStrangeNebulas`). Ion storms stay ion
storms. This is saved with the beacon (`nebulaOverride`).

Three hazardous sectors are mostly (90%) one kind of nebula: the Radiation
Fields, the Mirage Expanse and the Dark Reach (`xftlNebula` on the sector
description, with `xftlNebulaChance` to change the 90%), from sector 5. Every
beacon in them has hazards, often several at once, and they're marked as
hazardous (dark purple) on the sector map. See [sectors.md](sectors.md).

There are also events of their own: each has a list of three events (`XFTL_RADIATION_NEBULA`, `XFTL_DARK_NEBULA`
and `XFTL_MIRAGE_NEBULA`, in `events_nebula.xml.append`):

- The vanilla Nebula sectors have radiation and dark nebulas (rarely in act
  1), and mirages in act 2.
- The Slug sectors have mirages (rarely in act 1).
