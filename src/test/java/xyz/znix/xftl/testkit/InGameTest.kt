package xyz.znix.xftl.testkit

import org.jdom2.input.SAXBuilder
import xyz.znix.xftl.*
import xyz.znix.xftl.crew.CrewNameManager
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.InGameState.GameContent
import xyz.znix.xftl.sector.EventManager
import xyz.znix.xftl.shipgen.ShipGenerator
import java.io.StringReader

class InGameTest(
    extraBlueprints: List<String> = emptyList(),
    extraAnimations: List<String> = emptyList(),
    extraEvents: List<String> = emptyList(),
    playerShip: String = "TEST_SHIP",
    /** Replaces the test sector_data.xml, if set. */
    sectorData: String? = null,
) {
    val state: InGameState
    val player: Ship get() = state.player

    init {
        val blueprintFiles = listOf(
            "data/generic_blueprints.xml",
        )
        val eventFiles = mutableListOf(
            "data/generic_events.xml",
        )
        val miscFiles = listOf(
            "data/sector_data.xml",
            "data/test_animations.xml",

            "data/test_ship.xml",
            "data/test_ship.txt",
            // A ship with bigger rooms (2x2 and 3x3), for the bulwarks
            "data/test_big_ship.xml",
            "data/test_big_ship.txt",
            "img/ship/test_ship_base.png",

            "data/names.xml",

            // Triggers a warning if this is missing
            "img/ship/enemy_shields.png",

            "img/nullResource.png",
        )
        val allFiles = blueprintFiles + eventFiles + miscFiles
        val stringFiles = HashMap<String, String>()

        // Inject the extra event files
        for ((i, text) in extraEvents.withIndex()) {
            val name = "data/test_extra_event_$i.xml"
            eventFiles += name
            stringFiles[name] = text
        }

        if (sectorData != null) {
            stringFiles["data/sector_data.xml"] = sectorData
        }

        val df = FakeDatafile(allFiles, stringFiles)

        val sax = SAXBuilder()

        val defaultBlueprintXMLs = blueprintFiles.map { df.parseXML(df[it]) }
        val customBlueprintXMLs = extraBlueprints.map { sax.build(StringReader(it)) }

        val blueprints = BlueprintManager.createForTests(
            df,
            defaultBlueprintXMLs + customBlueprintXMLs
        )

        // Load an empty translator, since we need *something*
        val translator = Translator("test_assets/data/dummy_translations.xml")

        val events = EventManager(df, translator, blueprints, eventFiles)

        val sounds = NoOpSoundManager()

        val names = CrewNameManager(df, "en")

        val defaultAnimationXML = df.parseXML(df["data/test_animations.xml"])
        val customAnimationXMLs = extraAnimations.map { sax.build(StringReader(it)) }
        val animations = Animations(listOf(defaultAnimationXML) + customAnimationXMLs)

        val content = GameContent(
            df, true,
            blueprints,
            // TODO put the proper values in for these nulls:
            events,
            names,
            animations,
            sounds,
            translator,
            ShipGenerator(df, blueprints),
            null,
            null,
            null
        )
        state = InGameState(null, content, playerShip, Difficulty.NORMAL, null)
    }

    /**
     * Give the player a blueprint with the specified name.
     */
    fun givePlayerBP(name: String) {
        // Cast to Blueprint, to crash if there's a blueprint list instead.
        // This makes sure we're not adding random stuff by accident.
        player.addBlueprint(state.blueprintManager[name] as Blueprint, true)
    }

    /**
     * Upgrade all the systems to their maximum level.
     */
    fun upgradeAll() {
        for (sys in player.systems) {
            sys.energyLevels = sys.blueprint.maxPower
        }
    }

    /**
     * Power all the systems to their maximum level.
     */
    fun powerUpAll() {
        player.purchasedReactorPower = player.mainSystems.sumOf { it.energyLevels }
        player.updateAvailablePower()

        for (sys in player.mainSystems) {
            for (i in 0 until sys.energyLevels) {
                sys.increasePower()
            }
        }
    }

    /**
     * Step the world forwards by one time step.
     */
    fun updateSingle(dt: Float) {
        // This is usually done by PlayerShipUI
        for (weapons in player.weaponSystems) {
            weapons.selectedTargets.fireChargedWeapons()
        }

        state.updateGameState(dt)
    }
}
