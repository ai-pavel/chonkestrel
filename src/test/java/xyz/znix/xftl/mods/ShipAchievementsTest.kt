package xyz.znix.xftl.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.LazyShipBlueprint
import xyz.znix.xftl.Translator
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.game.Achievement
import xyz.znix.xftl.game.AchievementGoal
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import java.io.File

/**
 * The Chonkestrel mod's ship achievements (data/achievements.xml.append, see
 * doc/game/achievements.md): six for each of its twenty player ships, each
 * with a goal XFTL knows, for a ship the mod has, in the hangar's order.
 */
class ShipAchievementsTest {
    private val modDir = File("mods/chonkestrel")
    private val modded = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(modDir.toPath())))
    private val table = Achievement.AchievementTable(modded)
    private val blueprints = BlueprintManager(modded, true)

    // In the hangar's order (see ShipList), the Lanius and then the Crystals last
    private val races = listOf("ENGI", "ZOLTAN", "MANTIS", "SLUG", "ROCK", "FED", "STEALTH", "LANIUS", "CRYSTAL")
    private val ships = listOf("PLAYER_SHIP_CHONKESTREL", "PLAYER_SHIP_CHONKESTREL_2") +
            races.flatMap { listOf("PLAYER_SHIP_XFTL_$it", "PLAYER_SHIP_XFTL_${it}_2") }

    @Test
    fun everyShipHasSix() {
        val tracked = table.trackedByShip()
        assertEquals(120, tracked.values.sumOf { it.size })
        // (in the order the achievements window lists them)
        assertEquals(ships, tracked.keys.toList())
        for (ship in ships) {
            val achievements = table.trackedFor(ship)
            assertEquals(ship, 6, achievements.size)
            // The same three for every ship first: the Vigilance, the win, and the win on Hard
            assertEquals(ship, listOf(AchievementGoal.Kind.BLOCKADE, AchievementGoal.Kind.WIN, AchievementGoal.Kind.WIN_HARD),
                achievements.take(3).map { it.goal!!.kind })
            // and three more, all different
            assertEquals(ship, 6, achievements.map { it.goal!!.kind }.toSet().size)
        }

        // A race's Type A and B have the same goals, as separate achievements
        for (race in races.filter { it != "FED" && it != "STEALTH" }) {
            val a = table.trackedFor("PLAYER_SHIP_XFTL_$race")
            val b = table.trackedFor("PLAYER_SHIP_XFTL_${race}_2")
            assertEquals(race, a.map { it.goal }, b.map { it.goal })
            assertTrue(race, a.map { it.id }.intersect(b.map { it.id }.toSet()).isEmpty())
        }

        // except the Federation's and the Stealth ships', whose Type A and B each have three of their own
        val fedA = table.trackedFor("PLAYER_SHIP_XFTL_FED").drop(3).map { it.goal!!.kind }
        val fedB = table.trackedFor("PLAYER_SHIP_XFTL_FED_2").drop(3).map { it.goal!!.kind }
        assertEquals(listOf(AchievementGoal.Kind.ARTILLERY_DAMAGE, AchievementGoal.Kind.ARTILLERY_KILLS,
            AchievementGoal.Kind.WIN_NO_MIND_TRICKS), fedA)
        assertEquals(listOf(AchievementGoal.Kind.WIN_ALL_HUMAN, AchievementGoal.Kind.SURRENDERS,
            AchievementGoal.Kind.BOSS_ARTILLERY), fedB)
        val stealthA = table.trackedFor("PLAYER_SHIP_XFTL_STEALTH").drop(3).map { it.goal!!.kind }
        val stealthB = table.trackedFor("PLAYER_SHIP_XFTL_STEALTH_2").drop(3).map { it.goal!!.kind }
        assertEquals(listOf(AchievementGoal.Kind.DODGES, AchievementGoal.Kind.FLAWLESS_FIGHTS,
            AchievementGoal.Kind.NO_SHIELDS_SECTOR), stealthA)
        assertEquals(listOf(AchievementGoal.Kind.CLOAKED_VOLLEYS, AchievementGoal.Kind.CLOAK_CHAINS,
            AchievementGoal.Kind.BOSS_CLOAKED), stealthB)
    }

    @Test
    fun theyAreForTheModsShipsWithKnownGoals() {
        val translator = Translator(modded, "en")
        for ((ship, achievements) in table.trackedByShip()) {
            assertTrue(ship, blueprints.getOrNull(ship) is LazyShipBlueprint)
            for (ach in achievements) {
                assertTrue(ach.id, ach.id.startsWith("XFTL_ACH_"))
                // (an unknown kind would have left it without a goal)
                assertNotNull(ach.id, ach.goal)
                // The target goes in the description, with nothing left to fill in
                val desc = ach.description(translator)
                assertFalse(ach.id, desc.contains("\\1"))
                assertTrue(ach.id, translator[ach.name].isNotBlank())
                // Its icon, made by tools/achievement_art.py
                assertTrue(ach.img, File(modDir, ach.img).isFile)
            }
        }

        // Every goal kind is used by some ship
        val used = table.trackedByShip().values.flatten().map { it.goal!!.kind }.toSet()
        assertEquals(AchievementGoal.Kind.entries.toSet(), used)
    }
}
