package xyz.znix.xftl.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.znix.xftl.testkit.InGameTest
import kotlin.random.Random

/**
 * The score of a finished game, its record for the statistics screen (see
 * GameRecord and StatisticsWindow), and the best games kept in the profile.
 */
class GameRecordTest {
    @Test
    fun theScore() {
        val stats = GameStats().apply {
            scrapCollected = 500
            beaconsExplored = 40
            shipsDestroyed = 10
            shipsCrewKilled = 2
            crewLost = 3
        }
        // 500 scrap, 400 for the beacons, 240 for the ships, 250 for five
        // sectors, less 30 for the crew lost
        assertEquals(1360, GameRecord.score(stats, 5, false, Difficulty.NORMAL))
        assertEquals(680, GameRecord.score(stats, 5, false, Difficulty.EASY))
        assertEquals(2040, GameRecord.score(stats, 5, false, Difficulty.HARD))
        assertEquals(2720, GameRecord.score(stats, 5, true, Difficulty.NORMAL))
        assertEquals(0, GameRecord.score(GameStats().apply { crewLost = 9 }, 0, false, Difficulty.NORMAL))
    }

    @Test
    fun aRecordsSavedAndLoaded() {
        val kit = InGameTest()
        kit.state.stats.scrapCollected = 123
        kit.state.stats.beaconsExplored = 7
        val record = GameRecord.of(kit.state, GameOverWindow.Outcome.LOOSE_CREW)
        assertEquals(123, record.scrap)
        assertTrue(record.systems.isNotEmpty())
        assertEquals(record, GameRecord.loadFromXML(record.saveToXML()))
    }

    @Test
    fun theProfileKeepsTheBestTwenty() {
        val kit = InGameTest()
        val rand = Random(1)
        val profile = SaveProfile.createBlank()
        val games = List(30) { GameRecord.random(kit.state.blueprintManager, kit.state.content::nameManagerFor, rand) }
        for (game in games)
            profile.addGameRecord(game)
        val best = games.sortedByDescending { it.score }.take(GameRecord.TOP_GAMES)
        assertEquals(best, profile.topGames)

        // and they're saved with it
        assertEquals(best, SaveProfile.fromDocument(profile.save()).topGames)
    }

    @Test
    fun theStatsAreSavedWithTheGame() {
        val kit = InGameTest()
        val stats = kit.state.stats
        stats.startTime = 1_700_000_000_000L
        stats.shipsDestroyed = 4
        stats.crewLost = 2
        val loaded = InGameState(null, kit.state.content, kit.state.saveGameState()).stats
        assertEquals(1_700_000_000_000L, loaded.startTime)
        assertEquals(4, loaded.shipsDestroyed)
        assertEquals(2, loaded.crewLost)
    }
}
