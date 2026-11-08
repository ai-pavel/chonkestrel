# Oxygen

XFTL lets ships have several oxygen systems, as the Chonkestrel does (two,
from the start). They refill the air together: a big ship needs them all
working to refill as fast as a small ship does with one, and losing one
slows it down. This is implemented in `Ship.oxygenRefillRate`, and each
room uses it in `Room.update`.

## Vanilla

Every room loses 1.2% of its oxygen a second, and gains whatever the oxygen
system gives it (`Oxygen.refillRate`):

| Oxygen system         | Refill  | Net, per room |
|-----------------------|---------|---------------|
| Off, broken or none   | 0%      | -1.2%/s       |
| Level 1               | 2.4%/s  | +1.2%/s       |
| Level 2               | 6%/s    | +4.8%/s       |
| Level 3               | 9.6%/s  | +8.4%/s       |
| Hacked                | -4.8%/s | -6%/s         |

Crew take damage in rooms below 5%. Oxygen also flows between rooms through
open doors, and out into space through open airlocks and hull breaches, as
in vanilla (see `Door`).

## Several oxygen systems

Every room gains the **average** of the oxygen systems' refills. With one
system that's just its refill, so every ship with one (all the vanilla
ships, the enemies and the rebel flagship) works as in vanilla.

With two, at the same power, the ship refills as fast as a ship with one
at that power. If one is broken, turned off or hacked, it counts as giving
nothing (or draining, if hacked), which halves the refill:

| First system | Second system | Net, per room | 0% to 100% |
|--------------|---------------|---------------|------------|
| Level 1      | Level 1       | +1.2%/s       | 83s        |
| Level 2      | Level 2       | +4.8%/s       | 21s        |
| Level 3      | Level 3       | +8.4%/s       | 12s        |
| Level 2      | Level 1       | +3%/s         | 33s        |
| Level 3      | Level 1       | +4.8%/s       | 21s        |
| Level 3      | Level 2       | +6.6%/s       | 15s        |
| Level 1      | Off           | 0%/s          | never      |
| Level 2      | Off           | +1.8%/s       | 56s        |
| Level 3      | Off           | +3.6%/s       | 28s        |
| Off          | Off           | -1.2%/s       |            |
| Level 3      | Hacked        | +1.2%/s       | 83s        |
| Level 2      | Hacked        | -0.6%/s       |            |
| Level 1      | Hacked        | -1.2%/s       |            |
| Off          | Hacked        | -3.6%/s       |            |

(The times are for a sealed room, without fires, breaches or open doors.)

So losing one of the two oxygen systems isn't a disaster, but it hurts: at
level 1 the other only just keeps the air in, so the crew can't get it
back, and it takes twice as long at the higher levels. It's worth sending
someone to repair it, or keeping the other upgraded to level 2 or 3 to
cover for it. Hacking one is worse than breaking it, since it drains the
air too.

## The Chonkestrel

The Chonkestrel's two oxygen systems are in the corners of the rear (rooms
12 and 14, see the comment in its `blueprints.xml.append`), so a hit on one
side of the ship can't take out both. They both start at level 1, and each
can be upgraded to level 3.

The UI warns about low oxygen (below 25% on average) as long as the ship
has any oxygen system, and the oxygen percentage it shows is the average
over all the ship's rooms, as in vanilla.
