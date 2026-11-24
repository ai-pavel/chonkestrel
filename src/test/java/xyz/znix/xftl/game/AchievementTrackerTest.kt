package xyz.znix.xftl.game

import org.jdom2.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.game.AchievementGoal.Kind
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.testkit.addCrew
import xyz.znix.xftl.weapons.Damage

/**
 * The achievements XFTL tracks through a game (see AchievementTracker):
 * their goals unlock them in the profile, at the game's difficulty, their
 * progress is saved with the game, and the best progress with the profile.
 */
class AchievementTrackerTest {
    // An achievement for the test ship, with a goal of [kind]
    private fun achievement(id: String, kind: String, target: Int? = null): Achievement {
        val elem = Element("achievement").setAttribute("id", id)
        elem.addContent(Element("name").setText("Name of $id"))
        elem.addContent(Element("desc").setText("Reach \\1."))
        elem.addContent(Element("img").setText("achievements/$id.png"))
        elem.addContent(Element("ship").setText("TEST_SHIP"))
        val goal = Element("xftlGoal").setAttribute("kind", kind)
        if (target != null)
            goal.setAttribute("target", target.toString())
        elem.addContent(goal)
        return Achievement(elem)
    }

    private val missiles = achievement("MISSILES", "missiles", 10)
    private val drones = achievement("DRONES", "dronesOut", 4)
    private val win = achievement("WIN", "win")
    private val hard = achievement("HARD", "winHard")
    private val crew = achievement("CREW", "crewAtWin", 18)
    private val noLosses = achievement("NO_LOSSES", "winNoLosses")

    // A game tracking those, unlocking them in a profile of its own
    private fun tracked(difficulty: Difficulty = Difficulty.NORMAL): Triple<InGameTest, AchievementTracker, SaveProfile> {
        val kit = InGameTest(difficulty = difficulty)
        val tracker = kit.state.achievementTracker
        tracker.useAchievementsForTests(listOf(missiles, drones, win, hard, crew, noLosses))
        val profile = SaveProfile.createBlank()
        tracker.profileOverride = profile
        return Triple(kit, tracker, profile)
    }

    @Test
    fun theGoals() {
        assertEquals(AchievementGoal(Kind.MISSILES, 10), missiles.goal)
        // The kind's own target, if it's not given
        assertEquals(AchievementGoal(Kind.CREW_AT_WIN, 18), achievement("X", "crewAtWin").goal)
        // An unknown kind is a vanilla achievement, which isn't tracked
        assertNull(achievement("X", "somethingNew").goal)
    }

    @Test
    fun aCountUnlocksItOnceItsReached() {
        val (_, tracker, profile) = tracked()
        tracker.add(Kind.MISSILES, 6)
        assertNull(profile.getAchievement(missiles))
        assertEquals(6, tracker.progress(missiles))

        tracker.add(Kind.MISSILES, 4)
        assertEquals(Difficulty.NORMAL, profile.getAchievement(missiles)?.difficulty)
        assertTrue(tracker.isCompleted(missiles))
        // and nothing else
        assertNull(profile.getAchievement(drones))
    }

    @Test
    fun aBestOnlyGoesUp() {
        val (_, tracker, profile) = tracked()
        tracker.best(Kind.DRONES_OUT, 3)
        tracker.best(Kind.DRONES_OUT, 1)
        assertEquals(3, tracker.value(Kind.DRONES_OUT))
        assertNull(profile.getAchievement(drones))
        tracker.best(Kind.DRONES_OUT, 4)
        assertTrue(profile.getAchievement(drones) != null)
    }

    @Test
    fun winningUnlocksTheWinsAchievements() {
        val (kit, tracker, profile) = tracked()
        kit.state.stats.crewLost = 0
        tracker.gameOver(GameOverWindow.Outcome.WIN)
        assertTrue(profile.getAchievement(win) != null)
        assertTrue(profile.getAchievement(noLosses) != null)
        // Not on Hard, and without eighteen crew
        assertNull(profile.getAchievement(hard))
        assertNull(profile.getAchievement(crew))

        // An event's value, at least the target (or, for crew lost, at most)
        tracker.event(Kind.CREW_AT_WIN, 17)
        assertNull(profile.getAchievement(crew))
        tracker.event(Kind.CREW_AT_WIN, 18)
        assertTrue(profile.getAchievement(crew) != null)
    }

    @Test
    fun aWinOnHardOrInsaneUnlocksTheHardOne() {
        for (difficulty in listOf(Difficulty.HARD, Difficulty.INSANE)) {
            val (_, tracker, profile) = tracked(difficulty)
            tracker.gameOver(GameOverWindow.Outcome.WIN)
            // at the game's difficulty
            assertEquals(difficulty, profile.getAchievement(hard)?.difficulty)
            assertEquals(difficulty, profile.getAchievement(win)?.difficulty)
        }

        // An insane unlock is saved with the profile
        val (_, tracker, profile) = tracked(Difficulty.INSANE)
        tracker.gameOver(GameOverWindow.Outcome.WIN)
        val loaded = SaveProfile.fromDocument(profile.save())
        assertEquals(Difficulty.INSANE, loaded.getAchievement(hard)?.difficulty)
    }

    @Test
    fun theOnesThisGameCantUnlockAnyMore() {
        // Winning on Hard, on Normal
        val (kit, tracker, _) = tracked()
        assertTrue(tracker.isOutOfReach(hard))
        assertFalse(tracker.isOutOfReach(win))
        assertFalse(tracker.isOutOfReach(missiles))

        // Winning without losing crew, once some have been
        assertFalse(tracker.isOutOfReach(noLosses))
        kit.state.stats.crewLost = 1
        assertTrue(tracker.isOutOfReach(noLosses))

        // On Hard, it's still to be won
        val (_, onHard, _) = tracked(Difficulty.HARD)
        assertFalse(onHard.isOutOfReach(hard))
    }

    @Test
    fun losingUnlocksNothing() {
        val (_, tracker, profile) = tracked()
        tracker.gameOver(GameOverWindow.Outcome.LOOSE_HULL)
        assertNull(profile.getAchievement(win))
        assertNull(profile.getAchievement(noLosses))
    }

    @Test
    fun theProgressIsSavedWithTheGame() {
        val (kit, tracker, _) = tracked()
        tracker.add(Kind.MISSILES, 7)
        tracker.add(Kind.MISSILES, 5)
        tracker.best(Kind.ENEMY_FIRES, 6)
        assertTrue(tracker.isCompleted(missiles))

        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState()).achievementTracker
        assertEquals(12, loaded.value(Kind.MISSILES))
        assertEquals(6, loaded.value(Kind.ENEMY_FIRES))
        assertTrue(loaded.isCompleted(missiles))

        // An older save has none of it
        val old = InGameTest().state.achievementTracker
        old.loadFromXML(Element("xftlSaveGame"))
        assertEquals(0, old.value(Kind.MISSILES))
        assertFalse(old.hadOxygen)
    }

    @Test
    fun theBestProgressIsKeptInTheProfile() {
        val (_, tracker, profile) = tracked()
        tracker.add(Kind.MISSILES, 7)
        // (only now and then, as the ship jumps and so on)
        assertEquals(0, profile.getAchievementProgress(missiles))
        tracker.recordBest()
        assertEquals(7, profile.getAchievementProgress(missiles))

        // It's the best, so a worse game doesn't lower it
        profile.recordAchievementProgress(missiles, 3)
        assertEquals(7, profile.getAchievementProgress(missiles))

        // and it's saved with the profile, with the unlocks
        tracker.add(Kind.MISSILES, 5)
        val loaded = SaveProfile.fromDocument(profile.save())
        assertEquals(12, loaded.getAchievementProgress(missiles))
        assertEquals(Difficulty.NORMAL, loaded.getAchievement(missiles)?.difficulty)
        assertEquals(0, loaded.getAchievementProgress(drones))
    }

    // The Federation ships' goals
    private val artillery = achievement("ARTILLERY", "artilleryDamage", 5)
    private val surrenders = achievement("SURRENDERS", "surrenders", 2)
    private val allHuman = achievement("ALL_HUMAN", "winAllHuman")
    private val noMindTricks = achievement("NO_MIND_TRICKS", "winNoMindTricks")

    @Test
    fun theArtillerysHullDamageToTheEnemyCounts() {
        val kit = InGameTest(
            extraBlueprints = listOf(WingmanTest.BLUEPRINT_XML),
            extraEvents = listOf(WingmanTest.EVENT_XML),
            playerShip = "PLAYER_SHIP_THREE_ENEMIES"
        )
        kit.state.debugSpawnShip(kit.state.eventManager.getShip("TEST_ENEMY"), Difficulty.NORMAL, 0, 1)
        kit.state.updateGameState(0.01f)
        val tracker = kit.state.achievementTracker
        tracker.useAchievementsForTests(listOf(artillery))
        val profile = SaveProfile.createBlank()
        tracker.profileOverride = profile
        val enemy = kit.state.enemy!!

        // Not another weapon's, nor the enemy's artillery on the player
        enemy.damage(enemy.rooms[0], Damage.hullOnly(2))
        assertFalse(enemy.lastHullHitByArtillery)
        kit.player.damage(kit.player.rooms[0], Damage.hullOnly(1).apply { fromArtillery = true })
        assertEquals(0, tracker.value(Kind.ARTILLERY_DAMAGE))

        val beam = Damage.hullOnly(3).apply { fromArtillery = true }
        // (kept by a copy, as a beam's room damage is)
        enemy.damage(enemy.rooms[0], beam.copy())
        assertTrue(enemy.lastHullHitByArtillery)
        assertEquals(3, tracker.value(Kind.ARTILLERY_DAMAGE))
        assertNull(profile.getAchievement(artillery))
        enemy.damage(enemy.rooms[0], beam.copy())
        assertTrue(profile.getAchievement(artillery) != null)
    }

    @Test
    fun theFederationsWins() {
        val kit = InGameTest()
        val tracker = kit.state.achievementTracker
        tracker.useAchievementsForTests(listOf(surrenders, allHuman, noMindTricks))
        val profile = SaveProfile.createBlank()
        tracker.profileOverride = profile

        tracker.surrendersAccepted(1)
        assertNull(profile.getAchievement(surrenders))
        tracker.surrendersAccepted(1)
        assertTrue(profile.getAchievement(surrenders) != null)

        // The test ship has no hacking or mind control, and only humans
        kit.addCrew(kit.player, kit.player.rooms[0])
        kit.state.updatePlayerCrew()
        tracker.update()
        assertFalse(tracker.hadMindTricks)
        tracker.gameOver(GameOverWindow.Outcome.WIN)
        assertTrue(profile.getAchievement(allHuman) != null)
        assertTrue(profile.getAchievement(noMindTricks) != null)
    }

    // The Stealth ships' goals
    private val dodges = achievement("DODGES", "dodges", 2)
    private val chains = achievement("CHAINS", "cloakChains", 1)

    @Test
    fun theStealthGoals() {
        val kit = InGameTest()
        val tracker = kit.state.achievementTracker
        tracker.useAchievementsForTests(listOf(dodges, chains))
        val profile = SaveProfile.createBlank()
        tracker.profileOverride = profile

        // Every shot that misses is dodged, but only those while cloaked
        // count for the Crystal ships
        tracker.playerDodged()
        assertEquals(1, tracker.value(Kind.DODGES))
        assertEquals(0, tracker.value(Kind.CLOAK_MISSES))
        tracker.playerDodged()
        assertTrue(profile.getAchievement(dodges) != null)

        // A cloak straight after one ends counts, but not one a while later
        val stats = kit.state.stats
        stats.playedSeconds = 100f
        tracker.cloakEnded()
        stats.playedSeconds = 100f + AchievementTracker.CLOAK_CHAIN_TIME + 5f
        tracker.cloakStarted()
        assertEquals(0, tracker.value(Kind.CLOAK_CHAINS))
        tracker.cloakEnded()
        stats.playedSeconds += 1f
        tracker.cloakStarted()
        assertEquals(1, tracker.value(Kind.CLOAK_CHAINS))
        assertTrue(profile.getAchievement(chains) != null)

        // The test ship has no shields, so each sector it reaches counts
        tracker.update()
        assertFalse(tracker.hadShields)
        val sector = kit.state.currentBeacon.sector
        tracker.sectorEntered(sector)
        assertEquals(sector.sectorNumber + 1, tracker.value(Kind.NO_SHIELDS_SECTOR))
    }

    @Test
    fun theDescriptionHasTheTarget() {
        val kit = InGameTest()
        assertEquals("Reach 10.", missiles.description(kit.state.translator))
    }
}
