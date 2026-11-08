package xyz.znix.xftl.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.LazyShipBlueprint
import xyz.znix.xftl.ShipShape
import xyz.znix.xftl.Translator
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.game.ColossalBoss
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.EventManager
import xyz.znix.xftl.sector.GameMap
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import java.io.File
import kotlin.random.Random

/**
 * The Chonkestrel mod's four sector bosses (see doc/game/bosses.md): each
 * holds its sector's exit, is a size 3 ship with everything its blueprint
 * lists, and gives its tier's rewards.
 */
class BossesTest {
    private val modded = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(MOD_DIR.toPath())))
    private val blueprints = BlueprintManager(modded, true)
    private val translator = Translator(modded, "en")
    private val events = EventManager(modded, translator, blueprints)
    private val map = GameMap(modded, events, true, Random(0))

    @Test
    fun eachSectorsExitHasItsBoss() {
        for ((sector, boss) in BOSSES) {
            val type = map.sectorType(sector)!!
            val exit = events[type.finishEventName!!] as Event
            // Every way through the exit's event is a fight with the boss
            val fights = exit.choices.map { it.event }.filterIsInstance<Event>()
            assertTrue(sector, fights.isNotEmpty())
            for (fight in fights)
                assertEquals(sector, boss.spec, fight.loadShipName)
        }
    }

    @Test
    fun theirBeaconsHaveMoreHazardsTheHarderTheBoss() {
        // The Vigilance's is an ordinary beacon; the others' a nebula, with
        // one, two or three more hazards
        val expected = listOf(
            listOf(Beacon.EnvironmentType.NEBULA, Beacon.EnvironmentType.ASTEROID),
            listOf(null),
            listOf(Beacon.EnvironmentType.ION_STORM, Beacon.EnvironmentType.ASTEROID, Beacon.EnvironmentType.PULSAR),
            listOf(
                Beacon.EnvironmentType.NEBULA, Beacon.EnvironmentType.ASTEROID,
                Beacon.EnvironmentType.SUN, Beacon.EnvironmentType.PULSAR
            ),
        )
        for ((sector, environments) in BOSSES.keys.zip(expected)) {
            val exit = events[map.sectorType(sector)!!.finishEventName!!] as Event
            assertEquals(sector, environments, listOf(exit.environment) + exit.extraHazards)
        }
    }

    @Test
    fun theirWeaponsAreAllTwinsAndTriples() {
        // More triples the stronger the boss, and each weapons system (two
        // mounts each, the rest the last's, see Ship.assignWeaponHardpoints)
        // can power all of its weapons
        val triples = listOf(2, 4, 8, 10)
        for ((index, entry) in BOSSES.entries.withIndex()) {
            val (sector, boss) = entry
            val blueprint = (events.getShip(boss.spec).autoBlueprint as LazyShipBlueprint).real
            val weapons = blueprint.initialWeapons.map { blueprints[it!!] as AbstractWeaponBlueprint }
            assertTrue(sector, weapons.all { it.combinedCount in 2..3 })
            assertEquals(sector, triples[index], weapons.count { it.combinedCount == 3 })

            val systems = blueprint.loadElem().getChild("systemList").getChildren("weapons").size
            val perSystem = weapons.size / systems
            val groups = (0 until systems).map { i ->
                weapons.subList(i * perSystem, if (i == systems - 1) weapons.size else (i + 1) * perSystem)
            }
            for (group in groups)
                assertTrue("$sector: ${group.map { it.name }}", group.sumOf { it.power } <= 8)
        }
    }

    @Test
    fun theirZoltanShieldsGrowWithTheirTier() {
        // One Zoltan shield for the first, up to four for the last, and the
        // last two board through the player's with the bypass
        for ((sector, boss) in BOSSES) {
            val blueprint = (events.getShip(boss.spec).autoBlueprint as LazyShipBlueprint).real
            assertEquals(sector, boss.kind.tier, blueprint.initialAugments.count { it == "ENERGY_SHIELD" })
            assertEquals(sector, boss.kind.tier >= 3, "ZOLTAN_BYPASS" in blueprint.initialAugments)
        }
    }

    @Test
    fun everyMapHasEveryHazardousSector() {
        // The Maw, the Gullet and the Belly are on every map (xftlAlways),
        // each once, in the sectors it can go in
        for (seed in 0 until 200) {
            val generated = GameMap(modded, events, true, Random(seed))
            val types = generated.sectors.flatten()
            for (sector in listOf("XFTL_MAW", "XFTL_GULLET", "XFTL_BELLY")) {
                val found = types.filter { it.type.name == sector }
                assertEquals("seed $seed: $sector", 1, found.size)
                val range = generated.sectorType(sector)!!.sectors!!
                assertTrue("seed $seed: $sector", generated.nominalSectorNumber(found.single().columnNumber) in range)
            }
        }
    }

    @Test
    fun theHazardousSectorsBossesHuntThePlayer() {
        // The Maw's, the Gullet's and the Belly's hunt the player, with no
        // fleet behind them, the Stormbreaker the fastest; the Vigilance
        // holds the blockade's three exits, with the fleet coming on faster
        val periods = BOSSES.keys.map { map.sectorType(it)!!.hunterPeriod }
        assertEquals(listOf(2, null, 1, 2), periods)
        val blockade = map.sectorType("XFTL_BLOCKADE")!!
        assertTrue(blockade.bossHoldsExit)
        assertTrue(blockade.hasFleet)
        assertEquals(3, blockade.exitCount)
        assertEquals(1.15f, blockade.fleetSpeed)
        assertEquals(1f, map.sectorType("CIVILIAN_SECTOR")!!.fleetSpeed)
    }

    @Test
    fun theBossesAreSizeThree() {
        for ((sector, boss) in BOSSES) {
            val spec = events.getShip(boss.spec)
            val blueprint = (spec.autoBlueprint as LazyShipBlueprint).real
            assertEquals(sector, boss.kind, blueprint.bossKind)
            assertEquals(sector, 3, blueprint.shipSize)
            assertEquals(sector, ShipShape.COLOSSAL, blueprint.shape)

            // Everything it lists exists, and it has a mount for each weapon
            for (name in blueprint.initialWeapons.filterNotNull())
                assertTrue("$sector: $name", blueprints.getOrNull(name) is AbstractWeaponBlueprint)
            for (name in blueprint.initialDrones)
                assertTrue("$sector: $name", blueprints.getOrNull(name) is DroneBlueprint)
            // The game only takes rooms on the grid (no negative coordinates)
            val words = File(MOD_DIR, "data/${blueprint.layout}.txt").readText().split(Regex("\\s+"))
            for ((i, word) in words.withIndex()) {
                if (word == "ROOM")
                    assertTrue("$sector: room ${words[i + 1]}", words[i + 2].toInt() >= 0 && words[i + 3].toInt() >= 0)
            }
            val mounts = File(MOD_DIR, "data/${blueprint.layout}.xml").readText().split("<mount ").size - 1
            assertTrue(sector, mounts >= blueprint.initialWeapons.size)
            assertTrue(sector, File(MOD_DIR, "img/ship/${blueprint.img}_base.png").isFile)
        }
    }

    @Test
    fun theirRewardsGrowWithTheirTier() {
        for ((sector, boss) in BOSSES) {
            val spec = events.getShip(boss.spec)
            for (event in listOf(spec.destroyed, spec.deadCrew)) {
                val rewards = assertNotNull(sector, (event!!.resolve() as Event).bossRewards).let {
                    (event.resolve() as Event).bossRewards!!
                }
                assertEquals(sector, boss.kind.tier, rewards.tier)
                assertTrue("$sector: ${rewards.weapon}", blueprints.getOrNull(rewards.weapon!!) is AbstractWeaponBlueprint)
                assertTrue("$sector: ${rewards.augment}", blueprints.getOrNull(rewards.augment!!) is AugmentBlueprint)
            }
        }
    }

    private data class Boss(val spec: String, val kind: ColossalBoss.Kind)

    companion object {
        private val MOD_DIR = File("mods/chonkestrel")

        // In the order they're fought
        private val BOSSES = linkedMapOf(
            "XFTL_MAW" to Boss("XFTL_MAW_DEVOURER", ColossalBoss.Kind.DEVOURER),
            "XFTL_BLOCKADE" to Boss("XFTL_BLOCKADE_COMMAND", ColossalBoss.Kind.VIGILANCE),
            "XFTL_GULLET" to Boss("XFTL_GULLET_STORMBREAKER", ColossalBoss.Kind.STORMBREAKER),
            "XFTL_BELLY" to Boss("XFTL_BELLY_OSSUARY", ColossalBoss.Kind.OSSUARY),
        )
    }
}
