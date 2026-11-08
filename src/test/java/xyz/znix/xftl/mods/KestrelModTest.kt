package xyz.znix.xftl.mods

import net.vhati.modmanager.core.ModUtilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.LazyShipBlueprint
import xyz.znix.xftl.ShipShape
import xyz.znix.xftl.Translator
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.EventList
import xyz.znix.xftl.sector.EventManager
import xyz.znix.xftl.sector.IEvent
import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.sector.SectorType
import xyz.znix.xftl.shipgen.ShipGenerator
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.random.Random

/**
 * Check the Chonkestrel mod's patches apply to the real game files.
 */
class KestrelModTest {
    private val df = VanillaDatafile.createWithDefaultPath()

    /**
     * Apply the mod's patch to one of the game's data files, and return
     * the size of each ship blueprint in it.
     */
    private fun patchedShipSizes(file: String): Map<String, Int> {
        return SHIP_REGEX.findAll(patch(file)).associate { match ->
            val size = SIZE_REGEX.find(match.groupValues[2])?.groupValues?.get(1)?.toInt() ?: 1
            match.groupValues[1] to size
        }
    }

    /**
     * Apply the mod's patch to one of the game's data files.
     */
    private fun patch(file: String): String {
        val main = df.read(df["data/$file"])
        val append = File(MOD_DIR, "data/$file.append").readBytes()
        return ModUtilities.patchXMLFile(
            ByteArrayInputStream(main), ByteArrayInputStream(append),
            "UTF-8", true, file, "$file.append"
        ).readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun testChonkestrelStartingLoadout() {
        val kestrel = SHIP_REGEX.findAll(patch("blueprints.xml"))
            .single { it.groupValues[1] == CHONKESTREL }.groupValues[2]

        // It's a ship of its own, picked by default
        assertTrue(Regex("""layout="chonkestrel"\s+img="chonkestrel"""").containsMatchIn(kestrel))
        assertTrue(Regex("""<xftlDefaultShip\s*/>""").containsMatchIn(kestrel))

        // Two engines at two power each, a reactor of 16, one short of
        // powering every system it starts with, and the medbay starting off
        val engines = Regex("""<engines\s+power="(\d+)"[^>]*start="true"""").findAll(kestrel).toList()
        assertEquals(listOf("2", "2"), engines.map { it.groupValues[1] })
        assertTrue(Regex("""<maxPower\s+amount\s*=\s*"16"""").containsMatchIn(kestrel))
        assertTrue(Regex("""<medbay\s[^>]*xftlStartOff="true"""").containsMatchIn(kestrel))

        // It fights three vanilla ships at once
        assertTrue(Regex("""<opponentShips>\s*3\s*</opponentShips>""").containsMatchIn(kestrel))
        assertTrue(Regex("""<opponentShipsAtStart>\s*1\s*</opponentShipsAtStart>""").containsMatchIn(kestrel))

        // Two sensors systems, one for each enemy ship, and room for a third
        assertEquals(2, Regex("""<sensors\s[^>]*start="true"""").findAll(kestrel).count())
        assertEquals(1, Regex("""<sensors\s[^>]*start="false"""").findAll(kestrel).count())

        // Two shields systems, at two power each, from the start
        val shields = Regex("""<shields\s+power="(\d+)"[^>]*start="true"""").findAll(kestrel).toList()
        assertEquals(listOf("2", "2"), shields.map { it.groupValues[1] })

        // Two oxygen systems from the start
        assertEquals(2, Regex("""<oxygen\s[^>]*start="true"""").findAll(kestrel).count())

        // Six crew, at the piloting, the shields, both weapons systems and
        // both engines
        val crewRooms = Regex("""<crewCount\s+amount="1"\s+class="human"\s+room="(\d+)"""").findAll(kestrel.substringBefore("<xftlDebug"))
        assertEquals(listOf("0", "4", "18", "19", "13", "20"), crewRooms.map { it.groupValues[1] }.toList())

        // The Kestrel's weapons in each weapons system, the Burst Laser
        // first, with three power each, and twice its missiles
        assertTrue(Regex("""<weaponList\s+count="4"\s+missiles="16"""").containsMatchIn(kestrel))
        assertEquals(2, Regex("""<weapon\s+name="MISSILES_2_PLAYER"""").findAll(kestrel).count())
        assertTrue(Regex("""<weapon\s+name="LASER_BURST_3"\s+slot="5"""").containsMatchIn(kestrel))
        assertTrue(Regex("""<weapon\s+name="MISSILES_2_PLAYER"\s+slot="6"""").containsMatchIn(kestrel))
        val weapons = Regex("""<weapons\s+power="(\d+)"[^>]*start="true"""").findAll(kestrel).toList()
        assertEquals(listOf("3", "3"), weapons.map { it.groupValues[1] })
    }

    @Test
    fun testChonkestrelLayout() {
        val rooms = HashMap<Int, List<Int>>()
        val doors = ArrayList<List<Int>>()
        val lines = File(MOD_DIR, "data/chonkestrel.txt").readLines().map { it.trim() }
        for ((i, line) in lines.withIndex()) {
            when (line) {
                "ROOM" -> rooms[lines[i + 1].toInt()] = lines.subList(i + 2, i + 6).map { it.toInt() }
                "DOOR" -> doors += lines.subList(i + 1, i + 6).map { it.toInt() }
            }
        }

        // The rooms are numbered in order, with no gaps
        assertEquals((0 until rooms.size).toSet(), rooms.keys)

        // No two rooms overlap
        val cells = HashMap<Pair<Int, Int>, Int>()
        for ((id, r) in rooms) {
            val (x, y, w, h) = r
            for (cx in x until x + w) for (cy in y until y + h) {
                val other = cells.put(Pair(cx, cy), id)
                assertEquals("Rooms $other and $id overlap at $cx,$cy", null, other)
            }
        }

        // Every door is between the rooms it says it is (or a room and space)
        for ((x, y, a, b, vertical) in doors) {
            val sides = when (vertical) {
                1 -> listOf(cells[Pair(x - 1, y)], cells[Pair(x, y)])
                else -> listOf(cells[Pair(x, y - 1)], cells[Pair(x, y)])
            }.map { it ?: -1 }
            assertEquals("Door at $x,$y", setOf(a, b), sides.toSet())
        }

        // The split mind control rooms, with the third sensors above the top
        // one and the doors below the bottom one
        assertEquals(listOf(4, 3, 2, 1), rooms[9])
        assertEquals(listOf(4, 6, 2, 1), rooms[21])
        assertEquals(listOf(4, 2, 2, 1), rooms[26])
        assertEquals(listOf(4, 7, 2, 1), rooms[27])
    }

    @Test
    fun testHybridShips() {
        val modded = Datafile(df, listOf(SlipstreamDirectoryMod(MOD_DIR.toPath())))
        val blueprints = BlueprintManager(modded, true)

        // The ships glued together from two races' ships: every pair of
        // the nine races with crew, both ways round, apart from the
        // Federation and the Rebels (both human), from their scouts and
        // from their bombers
        assertEquals(70, blueprints["XFTL_HYBRID_SCOUTS"].list().size)
        assertEquals(70, blueprints["XFTL_HYBRID_BOMBERS"].list().size)
        val hybrids = blueprints["XFTL_HYBRIDS"].list().map { (it as LazyShipBlueprint).real }
        assertEquals(140, hybrids.size)

        // Each ship they're made from has a list of the hybrids made from it,
        // for the game to use in its place (see ShipGenerator.hybridFor):
        // the ones with its front and the ones with its back, 16 of them
        for (ship in listOf("FED_SCOUT", "JELLY_BUTTON", "ANAEROBIC_SCOUT", "REBEL_FAT", "MANTIS_BOMBER", "ROCK_FIGHT")) {
            val own = blueprints[ShipGenerator.HYBRIDS_OF + ship].list()
            assertTrue("$ship: ${own.size}", own.size in 14..16)
        }
        for (ship in hybrids) {
            val name = ship.name
            assertEquals(name, 1, ship.shipSize)

            // Its crew is half its own race and half another
            val races = ship.initialCrew.map { it.race }
            assertEquals(name, 2, races.toSet().size)
            assertTrue(name, ship.initialCrew.all { it.amount >= 1 })

            // It has both races' weapons to pick from
            val weapons = blueprints[name + "_WEAPONS"].list()
            val front = ship.name.removePrefix("XFTL_HYBRID_").removePrefix("BOMBER_").split("_")[0]
            assertTrue(name, weapons.size > blueprints[WEAPON_LISTS.getValue(front)].list().size)

            // It fits in the enemy's window
            assertTrue(name, ship.hullImage.isNotEmpty())
            assertTrue(name, ship.systems.size <= ship.maxSystems)

            // It has a drones system, and an anti-personnel drone it always
            // has (keeping watch over its crew), along with its bigger versions
            for (each in listOf(ship) + blueprints.shipVariants[name].orEmpty().map { it.real }) {
                assertTrue(each.name, each.systems.any { it.systemName == "drones" })
                assertTrue(each.name, "BATTLE" in each.requiredDrones && "REPAIR" in each.requiredDrones)
                assertTrue(each.name, each.requiredDrones.size <= 8)
                assertTrue(each.name, each.requiredDrones.groupBy { it }.values.all { it.size <= 5 })
                // (and no rooms of one cell)
                assertTrue(each.name, each.rooms.none { it.size.x * it.size.y == 1 })
                // (or doors from a room to itself)
                assertTrue(each.name, each.doors.none { it.left != null && it.left == it.right })
                // (or past the left or top of the grid, which the game can't load)
                assertTrue(each.name, each.rooms.all { it.pos.x >= 0 && it.pos.y >= 0 })
            }

            // It has the systems every ship needs
            val systems = ship.systems.map { it.systemName }.toSet()
            assertTrue("$name: $systems", systems.containsAll(listOf("pilot", "shields", "engines", "weapons", "oxygen")))
        }
    }

    @Test
    fun testBiggerShips() {
        val modded = Datafile(df, listOf(SlipstreamDirectoryMod(MOD_DIR.toPath())))
        val blueprints = BlueprintManager(modded, true)

        // Every enemy ship (apart from the bosses and the special ones from
        // events) has a long version, and most a wide one
        val bases = listOf(
            "FED_SCOUT", "FED_SCOUT_P", "REBEL_SKINNY", "REBEL_SKINNY_ELITE", "REBEL_SKINNY_P",
            "CIRCLE_SCOUT", "ZOLTAN_FIGHTER", "ZOLTAN_FIGHTER_P", "MANTIS_FIGHTER", "MANTIS_FIGHTER_P",
            "MANTIS_SCOUT", "MANTIS_SCOUT_P", "JELLY_BUTTON", "JELLY_BUTTON_P", "ROCK_SCOUT",
            "ROCK_SCOUT_P", "CRYSTAL_SCOUT", "ANAEROBIC_SCOUT", "AUTO_BASIC", "REBEL_FAT",
            "REBEL_FAT_P", "FED_BOMBER", "FED_BOMBER_P", "CIRCLE_BOMBER", "ZOLTAN_BOMBER",
            "ZOLTAN_BOMBER_P", "JELLY_CROISSANT", "JELLY_CROISSANT_P", "ROCK_FIGHT", "ROCK_FIGHT_P",
            "CRYSTAL_BOMBER", "ANAEROBIC_BOMBER", "MANTIS_BOMBER", "MANTIS_BOMBER_P", "JELLY_TRUFFLE",
            "JELLY_TRUFFLE_P", "ROCK_ASSAULT", "ROCK_ASSAULT_ELITE", "AUTO_ASSAULT"
        )
        for (base in bases) {
            val shapes = blueprints.shipVariants[base]?.map { it.shape }?.toSet() ?: emptySet()
            assertTrue("$base: $shapes", ShipShape.LONG in shapes)
        }

        for (variants in blueprints.shipVariants.values) for (lazy in variants) {
            val ship = lazy.real
            val name = ship.name
            assertEquals(name, 2, ship.shipSize)
            assertTrue(name, ship.shape != ShipShape.NORMAL)

            // Every room is on the grid with no overlaps, every door is
            // between the rooms it says it is (or a room and space), and
            // every room can be reached from every other
            val cells = HashMap<Pair<Int, Int>, Int>()
            for (room in ship.rooms) {
                for (x in room.pos.x until room.pos.x + room.size.x)
                    for (y in room.pos.y until room.pos.y + room.size.y)
                        assertEquals("$name room ${room.id}", null, cells.put(Pair(x, y), room.id))
            }
            val links = HashMap<Int, MutableSet<Int>>()
            for (door in ship.doors) {
                val (x, y) = Pair(door.pos.x, door.pos.y)
                val sides = when (door.isVertical) {
                    true -> listOf(cells[Pair(x - 1, y)], cells[Pair(x, y)])
                    false -> listOf(cells[Pair(x, y - 1)], cells[Pair(x, y)])
                }.map { it ?: -1 }.toSet()
                val a = door.left?.id ?: -1
                val b = door.right?.id ?: -1
                assertEquals("$name door at $x,$y", setOf(a, b), sides)
                // (a door joins two rooms, not a room to itself)
                assertTrue("$name door at $x,$y", a != b || a < 0)
                if (a >= 0 && b >= 0) {
                    links.getOrPut(a) { HashSet() } += b
                    links.getOrPut(b) { HashSet() } += a
                }
            }
            val reached = HashSet<Int>()
            val queue = ArrayDeque(listOf(ship.rooms.first().id))
            while (queue.isNotEmpty()) {
                val room = queue.removeFirst()
                if (reached.add(room))
                    queue += links[room].orEmpty()
            }
            // (apart from crewless ships, some of whose rooms aren't joined up
            // even in vanilla)
            if (!ship.isAutoScout)
                assertEquals(name, ship.rooms.map { it.id }.toSet(), reached)

            // It has more systems than the ship it's a version of (or as
            // many as fit, for a long version of a hybrid, which has lots)
            val vanilla = blueprints.getShip(ship.variantOf.first())
            assertTrue(name, ship.systems.size >= minOf(vanilla.systems.size, 10))
            // A long ship's systems all fit along the bottom of its window
            if (ship.shape == ShipShape.LONG)
                assertTrue("$name: ${ship.systems.size}", ship.systems.size <= 10)

            // It has no rooms of one cell
            assertTrue(name, ship.rooms.none { it.size.x * it.size.y == 1 })

            // There's room to install every system it has
            assertTrue(name, ship.maxSystems >= ship.systems.size)

            // It has more weapon mounts: at least five on a long ship and six
            // on a wide one, and at most seven (eight are left for bosses)
            val mounts = ship.hardpoints.size
            assertTrue("$name: $mounts", mounts >= (if (ship.shape == ShipShape.WIDE) 6 else 5) && mounts <= 7)

            // The game works out its hull from the vanilla ship's itself
            // (see Ship.maxHealth and doc/game/hull.md); its own blueprint's
            // isn't used, but it's never less than the vanilla ship's
            assertTrue(name, ship.maxHealth >= vanilla.maxHealth)
            // (and the vanilla ship's leaves room on the bar for bosses)
            assertTrue(name, vanilla.maxHealth <= xyz.znix.xftl.Ship.MAX_ORDINARY_HULL_CELLS)

            // A wide ship always has cloaking, and a long one sometimes
            val cloaking = ship.systems.filter { it.systemName == "cloaking" }
            assertEquals(name, 1, cloaking.size)
            // (unless the vanilla ship always has it)
            val vanillaCloaking = vanilla.systems.any { it.systemName == "cloaking" && it.availableByDefault }
            assertEquals(name, ship.shape == ShipShape.WIDE || vanillaCloaking, cloaking.single().availableByDefault)

            // A wide ship has a second oxygen system, if it has one
            if (ship.shape == ShipShape.WIDE && vanilla.systems.any { it.systemName == "oxygen" })
                assertTrue(name, ship.systems.count { it.systemName == "oxygen" } >= 2)
        }
    }

    @Test
    fun testShipSizes() {
        val blueprints = patchedShipSizes("blueprints.xml")
        val bosses = patchedShipSizes("bosses.xml")

        // The Chonkestrel and every version of the flagship are size two,
        // and the vanilla Kestrel is left alone.
        assertEquals(2, blueprints[CHONKESTREL])
        assertEquals(1, blueprints["PLAYER_SHIP_HARD"])
        val flagships = (blueprints + bosses).filterKeys { it.startsWith("BOSS_") }
        assertEquals(23, flagships.size)
        assertTrue(flagships.toString(), flagships.values.all { it == 2 })

        // The bigger versions of the vanilla ships are size two, the bosses
        // (see doc/game/bosses.md) size three, the Mainframe's phases (see
        // doc/game/mainframe.md) size four, and the rest of the enemy ships
        // are vanilla-sized
        val auto = patchedShipSizes("autoBlueprints.xml")
        val bigger = auto.keys.filter { it.endsWith("_LONG") || it.endsWith("_WIDE") }.toSet()
        assertTrue(bigger.size >= 19)
        assertTrue(bigger.all { auto[it] == 2 })
        val colossal = auto.filterKeys { it.startsWith("XFTL_BOSS_") }
        assertEquals(
            setOf(
                "XFTL_BOSS_DEVOURER", "XFTL_BOSS_VIGILANCE", "XFTL_BOSS_STORMBREAKER", "XFTL_BOSS_OSSUARY",
                "XFTL_BOSS_MAINFRAME_MANTIS_ENGI", "XFTL_BOSS_MAINFRAME_ROCK_ZOLTAN",
                "XFTL_BOSS_MAINFRAME_CRYSTAL_LANIUS", "XFTL_BOSS_MAINFRAME_SLUG_FED",
                "XFTL_BOSS_MAINFRAME_BAYS", "XFTL_BOSS_MAINFRAME_MIND",
            ),
            colossal.keys
        )
        assertTrue(colossal.all { (name, size) -> size == if (name.startsWith("XFTL_BOSS_MAINFRAME")) 4 else 3 })
        assertTrue(auto.filterKeys { it !in bigger && it !in colossal }.values.all { it == 1 })

        // Everything else is still vanilla-sized
        val others = blueprints.filterKeys { !it.startsWith("BOSS_") && it != CHONKESTREL && it !in bigger }
        assertTrue(others.isNotEmpty())
        assertTrue(others.filterValues { it != 1 }.toString(), others.values.all { it == 1 })
    }

    @Test
    fun testChonkestrelHasItsOwnFiles() {
        // Its layout and images are new files, rather than replacing the Kestrel's
        for (file in listOf("data/chonkestrel.txt", "data/chonkestrel.xml")) {
            assertTrue(file, File(MOD_DIR, file).isFile)
        }
        for (image in listOf("base", "floor", "cloak", "shields1", "gib1", "gib2", "gib3", "gib4", "gib5", "gib6")) {
            assertTrue(image, File(MOD_DIR, "img/ship/chonkestrel_$image.png").isFile)
        }
        // The rooms bigger than vanilla's have their own images
        for (image in listOf("room_medbay_big", "room_pilot_tall", "room_cloaking_wide")) {
            assertTrue(image, File(MOD_DIR, "img/ship/interior/$image.png").isFile)
        }

        // The hangar's ship list skips ships without a miniature
        assertTrue(File(MOD_DIR, "img/customizeUI/miniship_chonkestrel.png").isFile)

        val kestrelFiles = MOD_DIR.walk().filter { it.isFile && it.name.startsWith("kestral") }.toList()
        assertEquals(emptyList<File>(), kestrelFiles)
    }

    @Test
    fun testMoreCrewNames() {
        val lists = Regex("""<nameList([^>]*)>(.*?)</nameList>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(patch("names.xml"))
            .filter { !it.groupValues[1].contains("language") }
            .map { list -> list.groupValues[1] to Regex("""<name[^>]*>([^<]*)</name>""").findAll(list.groupValues[2]).map { it.groupValues[1].trim() }.toList() }
            .toList()

        // The names go in the first English list for each sex
        val male = lists.filter { it.first.contains("sex=\"male\"") }
        val female = lists.filter { it.first.contains("sex=\"female\"") }
        assertEquals(37 + 140, male.first().second.size)
        assertEquals(104, male[1].second.size)
        assertEquals(26 + 29, female.first().second.size)

        // And none of them were already there
        val english = lists.flatMap { it.second }
        assertEquals(169 + 169, english.toSet().size)
    }

    @Test
    fun testCampaignSectors() {
        // Load the game's data with the mod, as the game does
        val modded = Datafile(df, listOf(SlipstreamDirectoryMod(MOD_DIR.toPath())))
        val blueprints = BlueprintManager(modded, true)
        val translator = Translator(modded, "en")
        val events = EventManager(modded, translator, blueprints)

        // The main menu's New Game (and XFTL_SKIP_MENU) starts with the Chonkestrel
        assertEquals(CHONKESTREL, blueprints.defaultPlayerShip)
        assertEquals("PLAYER_SHIP_HARD", BlueprintManager(df.let { Datafile(it, emptyList()) }, true).defaultPlayerShip)

        val campaign = listOf(
            "XFTL_FEDERATION_REMNANT", "XFTL_ANVIL_YARDS", "XFTL_MAW", "XFTL_GULLET", "XFTL_BELLY",

            // A vanilla sector that's normally only reached through a secret sector
            "CRYSTAL_HOME"
        )
        val seen = HashSet<String>()

        // The nebula sectors can turn up in either act
        val nebulaSectors = mapOf(
            "XFTL_RADIATION_FIELDS" to "RADIATION",
            "XFTL_MIRAGE_EXPANSE" to "MIRAGE",
            "XFTL_DARK_REACH" to "DARK"
        )
        val seenNebulaSectors = HashSet<String>()

        for (seed in 0 until 100) {
            // This checks the sectors' events all exist, too
            val map = GameMap(modded, events, true, Random(seed))
            val midpoint = GameMap.SECTOR_COUNT / 2 - 1
            if (GameMap.SECTOR_COUNT >= GameMap.MIN_SECTORS_FOR_ACTS) {
                assertEquals("XFTL_BLOCKADE", map.sectors[midpoint].single().type.name)
            }

            for (sector in map.sectors.flatten().filter { it.type.name in nebulaSectors }) {
                assertEquals(nebulaSectors[sector.type.name], sector.type.nebula!!.name)
                seenNebulaSectors += sector.type.name

                // From sector 5 on, with hazards at every beacon
                assertTrue(sector.columnNumber + 1 >= 5)
                assertEquals(SectorType.HazardLevel.EVERY_BEACON, sector.type.hazards)
            }

            for ((column, sectors) in map.sectors.withIndex()) {
                for (sector in sectors.filter { it.type.name in campaign }) {
                    // The campaign's sectors only come after the blockade,
                    // except the Maw, which is in sectors 5-8, and the
                    // hazardous ones get more dangerous further in
                    val number = column + 1
                    when (sector.type.name) {
                        "XFTL_MAW" -> assertTrue("$number", number in 5..8)
                        "XFTL_GULLET" -> assertTrue("$number", number in 9..12)
                        "XFTL_BELLY" -> assertTrue("$number", number in 11..15)
                        else -> assertTrue(column > midpoint)
                    }
                    seen += sector.type.name

                    // Only the Maw and the Gullet have hazards at
                    // every beacon (as well as the new nebula sectors)
                    val hazards = when (sector.type.name) {
                        "XFTL_MAW" -> SectorType.HazardLevel.EVERY_BEACON
                        "XFTL_GULLET" -> SectorType.HazardLevel.CATASTROPHIC
                        "XFTL_BELLY" -> SectorType.HazardLevel.APOCALYPTIC
                        else -> SectorType.HazardLevel.NORMAL
                    }
                    assertEquals(sector.type.name, hazards, sector.type.hazards)
                }
            }
        }

        if (GameMap.SECTOR_COUNT >= GameMap.MIN_SECTORS_FOR_ACTS) {
            assertEquals(campaign.toSet(), seen)
        }

        // In act 2, the factions' own sectors give way to the hybrid sectors,
        // one for each pair of races (see tools/hybrid_sectors.py), but the
        // homeworlds can still turn up
        if (GameMap.SECTOR_COUNT >= GameMap.MIN_SECTORS_FOR_ACTS) {
            val midpoint = GameMap.SECTOR_COUNT / 2 - 1
            val maps = (0 until 100).map { seed -> GameMap(modded, events, true, Random(seed)) }
            val actTwo = maps.flatMap { it.sectors.drop(midpoint + 1).dropLast(1).flatten() }
            val actOne = maps.flatMap { it.sectors.take(midpoint).flatten() }
            val factions = setOf(
                "CIVILIAN_SECTOR", "ENGI_SECTOR", "ZOLTAN_SECTOR", "REBEL_SECTOR", "REBEL_SECTOR_MINIBOSS",
                "PIRATE_SECTOR", "MANTIS_SECTOR", "ROCK_SECTOR", "LANIUS_SECTOR", "SLUG_SECTOR"
            )
            assertEquals(emptyList<String>(), actTwo.map { it.type.name }.filter { it in factions })

            val hybrids = actTwo.filter { it.type.hybridRaces != null }
            assertEquals(28, hybrids.map { it.type.name }.toSet().size)
            assertTrue(hybrids.all { it.sectorClass == GameMap.SectorClass.HOSTILE })
            assertTrue(actOne.none { it.type.hybridRaces != null })
            assertTrue(actTwo.any { it.type.name.endsWith("_HOME") && it.type.name != "CRYSTAL_HOME" })

            val rockZoltan = hybrids.first { it.type.name == "XFTL_HYBRID_ROCK_ZOLTAN" }
            assertEquals(listOf("ROCK", "ZOLTAN"), rockZoltan.type.hybridRaces)
            assertEquals("Rock-Zoltan Controlled Sector", translator["sectorname_XFTL_HYBRID_ROCK_ZOLTAN"])
        }
        assertEquals(nebulaSectors.keys, seenNebulaSectors)

        // Load all the campaign's events, including the ones behind choices
        // (which are loaded lazily), and the ships they spawn
        val campaignEvents = events.eventNames.filter { it.startsWith("XFTL_") }
        assertTrue(campaignEvents.size > 40)
        val visited = HashSet<IEvent>()
        fun visit(event: IEvent) {
            if (!visited.add(event))
                return
            when (event) {
                is EventList -> event.events.forEach(::visit)
                is Event -> {
                    event.loadShipName?.let { events.getShip(it) }?.let { ship ->
                        listOfNotNull(ship.destroyed, ship.deadCrew, ship.surrender, ship.escape, ship.gotaway)
                            .forEach(::visit)
                    }
                    event.choices.forEach { visit(it.event) }
                }
            }
        }
        campaignEvents.forEach { visit(events[it]) }

        // The big ship events only appear for big ships, more in act 2
        val map = GameMap(modded, events, true, Random(0))
        val bigLists = setOf("XFTL_BIG_CIVILIAN", "XFTL_BIG_HOSTILE", "XFTL_BIG_NEBULA")
        fun bigEvents(column: Int, size: Int) = map.sectors[column]
            .flatMap { map.eventsFor(it, size) }.filter { it.name in bigLists }
        assertEquals(emptyList<Any>(), (1 until GameMap.SECTOR_COUNT - 1).flatMap { bigEvents(it, 1) })
        assertTrue((1 until GameMap.SECTOR_COUNT - 1).all { bigEvents(it, 2).isNotEmpty() })
        if (GameMap.SECTOR_COUNT >= GameMap.MIN_SECTORS_FOR_ACTS) {
            val actOneMax = bigEvents(1, 2).maxOf { it.count.last }
            val actTwoMax = bigEvents(GameMap.SECTOR_COUNT - 2, 2).maxOf { it.count.last }
            assertTrue(actOneMax < actTwoMax)
        }
        assertTrue(campaignEvents.count { it.startsWith("XFTL_BIG_") } >= 25)

        // There aren't any size two hostile ships yet, so the battle groups
        // are vanilla ships
        val chonkestrel = blueprints.getShip(CHONKESTREL)
        val group = events.getShip("XFTL_REBEL_BATTLEGROUP")
        assertTrue(group.bigBlueprint!!.list().isEmpty())
        assertSame(group.autoBlueprint, group.blueprintFor(chonkestrel))

        // The new sectors have names
        assertEquals("The Blockade Line", translator["sectorname_XFTL_BLOCKADE"])
        assertEquals("Belly", translator["sectorname_short_XFTL_BELLY"])
        assertEquals("The Maw", translator["sectorname_XFTL_MAW"])
        assertEquals("The Gullet", translator["sectorname_XFTL_GULLET"])
        assertEquals("The Belly", translator["sectorname_XFTL_BELLY"])

        // The blockade's exit is guarded
        assertEquals("XFTL_BLOCKADE_EXIT", GameMap(modded, events, true, Random(0)).sectors
            .flatten().first { it.type.name == "XFTL_BLOCKADE" }.type.finishEventName)
    }

    companion object {
        // Each race's weapons, by the name hybrid ships use for it
        private val WEAPON_LISTS = mapOf(
            "FED" to "WEAPONS_FED", "REBEL" to "WEAPONS_REBEL", "ENGI" to "WEAPONS_CIRCLE", "ZOLTAN" to "WEAPONS_ZOLTAN",
            "MANTIS" to "WEAPONS_MANTIS", "SLUG" to "WEAPONS_JELLY", "ROCK" to "WEAPONS_ROCK",
            "CRYSTAL" to "WEAPONS_CRYSTAL", "LANIUS" to "WEAPONS_ANAEROBIC",
        )

        private val MOD_DIR = File("mods/chonkestrel")
        private const val CHONKESTREL = "PLAYER_SHIP_CHONKESTREL"

        private val SHIP_REGEX = Regex("""<shipBlueprint\s+name="([^"]+)"(.*?)</shipBlueprint>""", RegexOption.DOT_MATCHES_ALL)
        private val SIZE_REGEX = Regex("""<shipSize>\s*(\d+)\s*</shipSize>""")
    }
}
