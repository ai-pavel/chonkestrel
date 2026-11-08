# Weapon sizes

A weapon slot can hold two or three of the same weapon combined into one
(its size): a "Twin" or "Triple" weapon. It fires all its copies at once,
each at a different enemy ship, which is what makes it worth having against
the several ships the bigger player ships fight. The code is
`weapons/WeaponSizes.kt`.

## The weapons

When the blueprints are loaded, every vanilla weapon gets a size 2 and a
size 3 version, as blueprints of their own: `XFTL_X2_<name>` and
`XFTL_X3_<name>`, called "Twin …" and "Triple …" ("2x …" and "3x …" in the
weapon bar). Their blueprint has an `xftlCombined` element listing the
weapons it's made of; for now that's always two or three of the same one,
but it's a list so different weapons can be put together later.

Left out, for now:

- Advanced Edition's weapons (flak, the chain and charge weapons and so
  on): the ones only in its `dlc*.xml` files.
- Weapons that are part of something else: the bosses' artillery, and the
  drones' guns.
- Anything without a rarity, as a vanilla weapon always has one (even if
  it's 0, as many of the enemies' weapons are, such as the Burst Laser II).

## Balance

Compared with the single weapon:

| | Size 2 | Size 3 |
|-|-|-|
| Price | 2.2x | 3.3x |
| Charge time | 1.25x | 1.5x |
| Missiles a volley | 2x | 3x |
| Power | half again, rounded up (1→2, 2→3, 3→5) | half again more (1→2, 2→4, 3→6) |

So it costs more and is slower than that many separate weapons, but it takes
one slot and less power, which is what makes it worth using.

A combined beam also gets through another layer of shields for each extra
copy (`BEAM_PIERCING_PER_COPY`): a Halberd Beam gets through one layer, a
twin one two and a triple one three; a Glaive Beam two, three and four.
Every beam's description and weapon bar tooltip say how many layers it gets
through, as "Max Piercing", since its "Shield Piercing" is only what it has
on top of its damage.

A combined beam hits a Zoltan shield as many times as hard as it has copies
in the ship it's aimed at (itself, and those with no other ship to reach),
so a triple one against a single ship cuts through one three times as fast.

Their short names (in the weapons bar and the stores' and equipment's boxes)
are the single weapon's with "(x2)" or "(x3)" after it, with its long words
cut down to three letters and a full stop, left to right, if that's what it
takes to fit ("Hal. Beam (x3)"; `ShortNames`).

## Firing

All the copies fire at once, each from its own barrel. The player aims at
one room on one ship as usual; the other copies get "shadow" targets: a
room in each of the other ships fighting the player, left to right, in the
same system as the main target if they have one, or else the room in the
nearest place in the ship (`ShadowTargets`). They show as faint crosshairs.

- With fewer enemy ships than copies (a triple weapon against two ships),
  the copies left over fire at the main target (a beam is longer instead,
  see below).
- When a ship with a shadow target is destroyed or gives up, that shadow
  target is dropped, and its copy fires at the main target.
- Enemies only fight the player, so all their copies fire at the player's
  ship, each at a room of its own, as with any enemy weapon's shots.

### Beams

A combined beam is one long beam. For each extra copy, it's as long again
as the beam, or 400 pixels (about the distance from one enemy window to
the next), whichever's longer, so the player can aim it on through the
enemy windows side by side, or a long way through a single ship. It's the
same length however many enemy ships there are, and whether they're
cloaked, to make it worth what it costs (the enemies' beams, which only
have the player to fire at, are as long).

It takes 1.25 times as long to sweep as a single beam (or 1.5 times, for a
triple one), however long it is, so a long one sweeps faster. Each window
draws the beam while it's over its ship, coming from the same place on the
screen, so it looks like one beam going through them all. (Behind the
scenes, each other ship gets a hidden copy of the beam along the same line,
as many as the beam has extra copies: the ships it crosses first, so a twin
beam against three ships hits whichever neighbour it's swept through.)
A beam that's firing when the game is saved carries on without its copies.

While the player aims it, its whole line is drawn over the enemy windows
(rather than inside the one it's aimed in, as a single beam's is, where it'd
be cut off at the window's edge), and the rooms it would hit are lit up in
every ship it crosses.

On the player's hull, each barrel's beam goes to a point just in front of
the weapon, where they meet, and go on as one, a bit thicker.

## Drawing

A combined weapon isn't two or three weapons stuck together: like the
bigger ships, it's one weapon made wider, with its own images, made from the
vanilla weapon's by `tools/weapon_sizes.py` (into the mod's `img/weapons`,
with their animations in its `animations.xml.append`):

- A weapon with a barrel for each copy (lasers, ions, missiles, crystals) has
  the band of columns its barrel's in (given by hand for each weapon, in
  `BARRELS`) repeated beside it, the whole height of the image, so it has
  two or three barrels, each with its part of the body, side by side, on its
  one mount. Each copy's shots come out of its own barrel (its animation's
  `xftlBarrels` says where, from the fire point). The burst lasers and the
  Crystal weapons are drawn like that at both sizes; the rest only as
  twins.
- A triple ion weapon, Heavy Laser, Hull Laser or missile launcher (in
  `TRIANGLE`) is three of the weapon in a triangle: two behind, at the
  sides, darker, and one in front of them, in the middle, which all its
  shots come out of. Its copies charge one after another: the left one's
  lights come on in the first third of its charge, the right one's in the
  second, and the middle one's when it's ready to fire, each with its own
  charging glow drawn into its frames (so it has more charging frames than
  the single weapon, and no glow image over them all). Then the three fire
  together.
- A beam weapon has the base between its prongs (or rails) repeated
  (`BEAM_BANDS`), so it has two or three bases glued together, with its
  prongs as wide as they are at the sides, and the beam comes straight out
  of its middle.
- A bomb launcher has a second hatch, with a bomb over each, and a triple
  one a third bomb over them in the middle, so they make a triangle (its
  image is taller for it). The bombs themselves are drawn their usual size.

The mount, on the left of the image, which goes against the hull, is kept
as it is. `python3 tools/weapon_sizes.py ftl.dat --preview out/` makes the
images again, and draws each weapon's single, twin and triple versions side
by side (with their mount in green and fire points in red), and every frame
of the triple one, into `out/` (`_all.png` has them all), to check them for
artefacts.

A weapon without an animation of its own to widen (the crystal shard) is
drawn as copies of the single one instead: two side by side, overlapping
closely, three in a triangle, two behind and one in front; a beam drawn
like that has its barrels' beams meet in front of it.

Their shots are bigger (but not the bombs): half as big again for each
extra copy, and a combined beam's a power thicker for each.

## Where they turn up

- **Stores:** depending on the sector (see doc/game/stores.md): none in civilian
  sectors, more and more through the game in hostile and nebula ones, and
  often in hazardous ones, where triple ones turn up too.
- **Rewards:** a weapon is a double one 10% of the time, in place of the
  single one. An event's random weapon with `xftlCombined="true"` is always a double
  one, or a third of the time a triple one (`LootPool.getCombinedWeapon`):
  the Free Colonies' courier quest gives two.
- **Enemy ships:** each weapon has a chance of being a double one, rising
  through the game (by how far through it the sector is, from 0 at the start
  to 1 at the end, as its vanilla sector number of 0 to 7):

  | | Start | Middle | End |
  |-|-|-|-|
  | Normal ships | 5% | 17.5% | 30% |
  | Long and wide ships | 25% | 42.5% | 60% |

  So a normal ship with three weapons has a double one about a quarter of
  the time early on, and two thirds of the time at the end. A long or wide
  ship, from a quarter of the way through the game, always has at least
  one (`BIG_SHIP_DOUBLE_FROM`), and can still have several single ones.
- **The most dangerous sectors** (the Gullet and the Belly): each enemy
  weapon also has a 35% chance of being a triple one, at most two on a ship.

A double or triple weapon that needs more power than the enemy's weapons
system has left gets it: the system (and its reactor) is upgraded for it, as
far as the system can be. If even that's not enough, it stays single.

## Testing

`make randomize` starts a debug game with a random triple and double weapon
of each kind (beam, bomb, laser and missile) in its slots, the rest of its
slots filled with random others, its drone slots and cargo with twin and
triple drones (see [drone-sizes.md](drone-sizes.md)), and the Weapon Pre-Igniter and a
Zoltan shield among its augments. It never picks the anti-bio beam, the
fire beam, or the bombs that heal or repair your own ship. The automatic
playtest (`XFTL_AUTOPLAY`) with `XFTL_RANDOMIZE=1` re-rolls the weapons for each fight, and fires the
combined ones at every ship as the player can: the copies at the other
ships, and the beams across their windows.
