#!/usr/bin/env python3
"""
Open up the walls between the big empty rooms of the mod's bigger enemy
ships, so their bulwarks can walk between them (see doc/game/drone-sizes.md):
a bulwark's a square of 2x2 or 3x3 cells, and only goes through a passage as
wide as itself, a run of as many doors side by side.

    python3 tools/wide_passages.py ftl.dat            # fill the walls
    python3 tools/wide_passages.py ftl.dat --scan     # only list them

It runs after the scripts that make the layouts (ship_variants.py,
hybrid_ships.py, boss_ships.py and race_ships.py), on the layouts of their
blueprints of size 2 or more in the mod's autoBlueprints.xml.append, and
on the Chonkestrel's (made by hand, in blueprints.xml.append).
Every wall between two rooms a bulwark can stand in (no system the ship
starts with in either, in any blueprint that uses the layout, and each at
least 2x2) that's at least 2 cells long gets a door on each of its cells.
A room with only an optional system (start="false") counts: an enemy
only sometimes gets it, and without it the room's empty, and a bulwark
walks through it as any other (see AntiPersonnelDrone.canStandIn). The
doors already there are kept.

The player's chonky ships (race_ships.py's, and the Chonkestrel) and the
bosses (boss_ships.py's) get them whatever the rooms' systems, as the
bosses' big rooms nearly all have one: every wall between two rooms at
least 2x2 gets a door on each of its cells but those a system's console
faces (where the game puts it, see SystemInstallConfiguration), each run of
at least two of them.

Those walls are then wide doors (see doc/game/drone-sizes.md): one door 2
or 3 cells long, rather than a row of single ones, opening and closing as
one, a wall longer than that split into as few as it can be, of as near
the same size as they can be (4 cells are 2 and 2, 5 are 3 and 2). Each is
an XFTL_WIDE_DOOR line in the layout, then its first cell's x and y (as a
DOOR's), whether it's vertical (1) and how many cells long it is; the game
makes the DOORs on its cells one door (see Door.group), drawn from
door_sizes.py's sprites.

The DOOR and XFTL_WIDE_DOOR lines are added at the end of each layout's
.txt, so it's otherwise as its script wrote it, and running this again
adds none.
--scan lists every pair of big rooms (each at least 2 cells deep across
the wall between them), with the doors on the wall and its longest run of
them, and sums them up; it writes nothing.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MOD = Path(__file__).resolve().parent.parent / "mods" / "chonkestrel"
BLUEPRINTS_FILE = MOD / "data" / "autoBlueprints.xml.append"

# The scripts whose layouts are filled
SCRIPTS = ["ship_variants", "hybrid_ships", "boss_ships", "race_ships"]

# The Chonkestrel's own blueprint, made by hand (its layout too), in this file
HAND_MADE_FILE = MOD / "data" / "blueprints.xml.append"
HAND_MADE = "chonkestrel"

# The ships whose big rooms are all opened up (see fill_open): the
# player's, and the bosses', the others only where a bulwark walks
OPEN = {"race_ships", HAND_MADE, "boss_ships"}

# Where the game puts a console without a slot of its own: the systems'
# default slots and the walls they face (see SystemInstallConfiguration);
# the others' go wherever there isn't a door
DEFAULT_CONSOLES = {"weapons": ("up", 1), "engines": ("down", 2), "shields": ("left", 0), "pilot": ("right", 0)}

# The systems with a console, to be manned (the mod's drones, artillery,
# hacking, mind control and oxygen too, see doc/game/skills.md)
MANNED = {"pilot", "engines", "shields", "weapons", "sensors", "doors", "drones", "artillery",
          "hacking", "mind", "oxygen"}

# The biggest bulwark (see AntiPersonnelDrone), and the widest door
MAX_BULWARK = 3

# The line marking a wide door in a layout's .txt (see the docstring)
WIDE_DOOR = "XFTL_WIDE_DOOR"


def parse_layout(text):
    """The rooms (id: [x, y, w, h]) and doors ([x, y, a, b, vertical]) in a layout's .txt."""
    words = text.split()
    rooms, doors = {}, []
    i = 0
    while i < len(words):
        if words[i] == "ROOM":
            rid, x, y, w, h = map(int, words[i + 1:i + 6])
            rooms[rid] = [x, y, w, h]
            i += 6
        elif words[i] == "DOOR":
            doors.append(list(map(int, words[i + 1:i + 6])))
            i += 6
        else:
            i += 1
    return rooms, doors


def walls(rooms):
    """
    The walls between each pair of rooms, as {(a, b): (vertical, cells)},
    with a the room to the left of (or above) b and cells the door
    positions along the wall, in order.
    """
    cells = {}
    for rid, (x, y, w, h) in rooms.items():
        for cx in range(x, x + w):
            for cy in range(y, y + h):
                cells[(cx, cy)] = rid
    out = {}
    for (cx, cy), rid in sorted(cells.items(), key=lambda c: (c[0][1], c[0][0])):
        # The wall on the cell's left (a vertical door at cx, cy) and above
        # it (a horizontal one)
        for vertical, other in ((1, cells.get((cx - 1, cy))), (0, cells.get((cx, cy - 1)))):
            if other is None or other == rid:
                continue
            out.setdefault((other, rid), (vertical, []))[1].append((cx, cy))
    return out


def depth(room, vertical):
    """How deep a room is across a wall: its width across a vertical one, its height across a horizontal one."""
    return room[2] if vertical else room[3]


def longest_run(positions, vertical):
    """The most doors in a row, side by side, among the door positions."""
    along = sorted(p[1] if vertical else p[0] for p in positions)
    best = run = 0
    last = None
    for v in along:
        run = run + 1 if last is not None and v == last + 1 else 1
        best = max(best, run)
        last = v
    return best


def blueprints_by_script():
    """
    {script: {layout: set of the rooms with a system it starts with, in any
    of its blueprints}}, and {layout: its blueprints' systems, as (room,
    system, slot number, direction), None where it has none}.
    """
    text = BLUEPRINTS_FILE.read_text()
    out = {}
    systems = {}
    for script in SCRIPTS:
        block = re.search(r"<!-- BEGIN generated by tools/%s\.py -->(.*?)<!-- END generated by tools/%s\.py -->"
                          % (script, script), text, re.S)
        assert block, "no %s block in %s" % (script, BLUEPRINTS_FILE)
        root = ET.fromstring("<root>%s</root>" % block.group(1))
        add_blueprints(out.setdefault(script, {}), systems, root, big_only=True)
    # (the Chonkestrel's as big as the race ships, though its blueprint
    # doesn't say so)
    # (only its ship blueprints: the rest has the mod's own mod: tags)
    blocks = re.findall(r"<shipBlueprint\b.*?</shipBlueprint>", HAND_MADE_FILE.read_text(), re.S)
    root = ET.fromstring("<root>%s</root>" % "".join(blocks))
    add_blueprints(out.setdefault(HAND_MADE, {}), systems, root, big_only=False)
    return out, systems


def add_blueprints(layouts, systems, root, big_only):
    """
    Add the layouts of the blueprints in [root] to [layouts], with the rooms
    with a system they start with, and their systems to [systems].
    """
    for bp in root.iter("shipBlueprint"):
        # (only the bigger ships: the small hybrids are as vanilla's)
        if big_only and int((bp.findtext("shipSize") or "1").strip()) < 2:
            continue
        starting = layouts.setdefault(bp.get("layout"), set())
        listed = systems.setdefault(bp.get("layout"), [])
        system_list = bp.find("systemList")
        for system in (system_list if system_list is not None else []):
            if system.get("room") is None:
                continue
            if system.get("start") != "false":
                starting.add(int(system.get("room")))
            number = system.findtext("slot/number")
            listed.append((int(system.get("room")), system.tag, int(number) if number is not None else None,
                           (system.findtext("slot/direction") or "").strip() or None))


def console_walls(rooms, listed, doors):
    """
    The cells of the walls (x, y, vertical, as a DOOR's) that the consoles of
    [listed] (see blueprints_by_script) face, where a door would be in their
    way. A console with no slot of its own goes wherever there's no door
    (see SystemInstallConfiguration): the first such place the game looks
    at, among [doors] (x, y, vertical), is kept for it.
    """
    out = set()
    for rid, kind, number, direction in listed:
        if rid not in rooms:
            continue
        x, y, w, h = rooms[rid]
        default = DEFAULT_CONSOLES.get(kind)
        if number is None and default is not None:
            number = default[1] if default[1] < w * h else 0
        direction = direction or (default[0] if default else None)
        if number is None and kind in MANNED:
            # (in the game's order: down each column, across, and each
            # cell's walls left, right, up and down)
            for cx in range(w):
                for cy in range(h):
                    for wall in ((x, y + cy, 1) if cx == 0 else None, (x + w, y + cy, 1) if cx == w - 1 else None,
                                 (x + cx, y, 0) if cy == 0 else None, (x + cx, y + h, 0) if cy == h - 1 else None):
                        if wall is not None and wall not in doors:
                            out.add(wall)
                            break
                    else:
                        continue
                    break
                else:
                    continue
                break
            continue
        if number is None or direction is None or not 0 <= number < w * h:
            continue
        cx, cy = number % w, number // w
        if direction == "left" and cx == 0:
            out.add((x, y + cy, 1))
        elif direction == "right" and cx == w - 1:
            out.add((x + w, y + cy, 1))
        elif direction == "up" and cy == 0:
            out.add((x + cx, y, 0))
        elif direction == "down" and cy == h - 1:
            out.add((x + cx, y + h, 0))
    return out


def can_hold_bulwark(room, rid, systems):
    return rid not in systems and room[2] >= 2 and room[3] >= 2


def look_at(rooms, doors, systems):
    """
    Each pair of big rooms' wall, as dicts: the rooms, how long the wall is,
    its doors and their longest run, whether a bulwark can stand in both,
    and the run it needs (as wide as the biggest bulwark that fits both
    rooms and the wall, up to 3).
    """
    door_at = {(x, y, v): (a, b) for x, y, a, b, v in doors}
    out = []
    for (a, b), (vertical, cells) in walls(rooms).items():
        ra, rb = rooms[a], rooms[b]
        if depth(ra, vertical) < 2 or depth(rb, vertical) < 2 or len(cells) < 2:
            continue
        on = [c for c in cells if (c[0], c[1], vertical) in door_at]
        bulwark = can_hold_bulwark(ra, a, systems) and can_hold_bulwark(rb, b, systems)
        need = min(MAX_BULWARK, len(cells), *ra[2:], *rb[2:]) if bulwark else 0
        out.append(dict(rooms=(a, b), vertical=vertical, cells=cells, length=len(cells), doors=len(on),
                        run=longest_run(on, vertical), bulwark=bulwark, need=need))
    return out


def fill(rooms, doors, systems):
    """
    The doors to add, one on each free cell of every wall between two rooms
    a bulwark can stand in, and the wide doors those walls are made into
    ([x, y, vertical, size], see chunks).
    """
    have = {(x, y, v) for x, y, a, b, v in doors}
    added = []
    wide = []
    for wall in look_at(rooms, doors, systems):
        if not wall["bulwark"]:
            continue
        a, b = wall["rooms"]
        for x, y in wall["cells"]:
            if (x, y, wall["vertical"]) not in have:
                added.append([x, y, a, b, wall["vertical"]])
        at = 0
        for size in chunks(len(wall["cells"])):
            x, y = wall["cells"][at]
            wide.append([x, y, wall["vertical"], size])
            at += size
    return added, wide


def fill_open(rooms, doors, listed):
    """
    The doors to add to a player's ship or a boss, and its wide doors (as
    fill's): one on each cell of every wall between two rooms at least 2x2,
    whatever their systems (so their big rooms are as open as the other
    enemies' empty ones), but those a console faces (see console_walls),
    each run of at least two of them side by side.
    """
    have = {(x, y, v) for x, y, a, b, v in doors}
    blocked = console_walls(rooms, listed, have)
    added = []
    wide = []
    for wall in look_at(rooms, doors, set()):
        a, b = wall["rooms"]
        vertical = wall["vertical"]
        if min(rooms[a][2:] + rooms[b][2:]) < 2:
            continue
        # (the cells no console faces, in runs side by side)
        runs = [[]]
        for x, y in wall["cells"]:
            if (x, y, vertical) in blocked:
                runs.append([])
                continue
            last = runs[-1][-1] if runs[-1] else None
            if last is not None and (y if vertical else x) != (last[1] if vertical else last[0]) + 1:
                runs.append([])
            runs[-1].append((x, y))
        for run in runs:
            if len(run) < 2:
                continue
            for x, y in run:
                if (x, y, vertical) not in have:
                    added.append([x, y, a, b, vertical])
            at = 0
            for size in chunks(len(run)):
                x, y = run[at]
                wide.append([x, y, vertical, size])
                at += size
    return added, wide


def chunks(length):
    """
    A wall [length] cells long as wide doors, each 2 or 3 cells (as the
    combined weapons and drones are): as few as there can be, as near the
    same size as they can be, the bigger first (5 is 3 and 2, 4 is 2 and 2).
    """
    count = -(-length // MAX_BULWARK)
    return [length // count + (1 if i < length % count else 0) for i in range(count)]


def scan(layouts):
    totals = {}
    for script, by_layout in layouts.items():
        t = totals.setdefault(script, dict(layouts=0, pairs=0, short=0, no2=0, no3=0,
                                           bulwark=0, bulwark_short=0, bulwark_narrow=0))
        for name in sorted(by_layout):
            rooms, doors = parse_layout((MOD / "data" / ("%s.txt" % name)).read_text())
            t["layouts"] += 1
            for wall in look_at(rooms, doors, by_layout[name]):
                t["pairs"] += 1
                t["short"] += wall["doors"] < wall["length"]
                t["no2"] += wall["run"] < 2
                t["no3"] += wall["length"] >= 3 and wall["run"] < 3
                if wall["bulwark"]:
                    t["bulwark"] += 1
                    t["bulwark_short"] += wall["doors"] < wall["length"]
                    t["bulwark_narrow"] += wall["run"] < wall["need"]
                print("%-36s rooms %3d-%-3d %s wall %d long: %d doors, longest run %d%s" % (
                    name, *wall["rooms"], "|" if wall["vertical"] else "-", wall["length"], wall["doors"],
                    wall["run"], ", bulwark rooms (needs %d)" % wall["need"] if wall["bulwark"] else ""))
    print()
    for script, t in totals.items():
        print("%s: %d layouts, %d pairs of big rooms: %d with fewer doors than the wall's long, "
              "%d with no passage 2 wide, %d (of walls 3+ long) with none 3 wide; "
              "%d pairs a bulwark can stand in: %d with fewer doors than the wall's long, %d narrower than it needs"
              % (script, t["layouts"], t["pairs"], t["short"], t["no2"], t["no3"],
                 t["bulwark"], t["bulwark_short"], t["bulwark_narrow"]))


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    layouts, systems = blueprints_by_script()
    if "--scan" in sys.argv[2:]:
        scan(layouts)
        return
    total = total_wide = 0
    for script, by_layout in layouts.items():
        for name in sorted(by_layout):
            path = MOD / "data" / ("%s.txt" % name)
            text = path.read_text()
            rooms, doors = parse_layout(text)
            if script in OPEN:
                added, wide = fill_open(rooms, doors, systems[name])
            else:
                added, wide = fill(rooms, doors, by_layout[name])
            # (made into wide doors already, it's been done)
            if WIDE_DOOR in text:
                wide = []
            if not added and not wide:
                continue
            lines = "".join("DOOR\n%s\n" % "\n".join(map(str, door)) for door in added)
            lines += "".join("%s\n%s\n" % (WIDE_DOOR, "\n".join(map(str, door))) for door in wide)
            path.write_text((text if text.endswith("\n") else text + "\n") + lines)
            total += len(added)
            total_wide += len(wide)
            print("%s: %d doors added, %d wide doors" % (name, len(added), len(wide)))
    print("Added %d doors, making %d wide doors" % (total, total_wide))


if __name__ == "__main__":
    main()
