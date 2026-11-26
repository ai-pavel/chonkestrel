package xyz.znix.xftl.game

import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.ui.Label
import xyz.znix.xftl.ui.Slider
import xyz.znix.xftl.ui.WidgetContainer
import kotlin.reflect.KMutableProperty0

class OptionsWindow(val game: InGameState, val close: () -> Unit) : Window() {
    private val widgetTree: WidgetContainer = game.uiLoader.load("options").mainWidget

    private val soundSlider = widgetTree.byId["sound"] as Slider
    private val musicSlider = widgetTree.byId["music"] as Slider

    override val size: IPoint get() = widgetTree.root.size

    init {
        buttons += widgetTree.buildButtons(game, this, ConstPoint.ZERO)

        soundSlider.value = game.mainGame.profile.soundVolume
        musicSlider.value = game.mainGame.profile.musicVolume

        soundSlider.changeListener = this::updateVolumes
        musicSlider.changeListener = this::updateVolumes

        widgetTree.addButtonListener("keybinds") {
            game.shipUI.showKeybindWindow()
        }
        widgetTree.addButtonListener("display") {
            game.shipUI.showDisplayWindow()
        }
        widgetTree.addButtonListener("language") {
            game.shipUI.showLanguageWindow()
        }
        widgetTree.addButtonListener("back", this::escapePressed)

        val profile = game.mainGame.profile
        addToggle("mapAllConnections", "xftl_options_map_all_connections", profile::mapShowAllConnections)
        addToggle("mapFleetForecast", "xftl_options_map_fleet_forecast", profile::mapShowFleetForecast)
    }

    private fun addToggle(id: String, textKey: String, setting: KMutableProperty0<Boolean>) {
        val label = widgetTree.byId[id + "Label"] as Label
        fun updateLabel() {
            val state = game.translator[if (setting.get()) "xftl_options_on" else "xftl_options_off"]
            label.text = game.translator[textKey].replace("\\1", state)
        }
        updateLabel()

        widgetTree.addButtonListener(id) {
            setting.set(!setting.get())
            updateLabel()
        }
    }

    override fun draw(g: Graphics) {
        g.pushTransform()
        g.translate(position.x.f, position.y.f)

        widgetTree.draw(g)

        g.popTransform()
    }

    override fun escapePressed() {
        updateVolumes()
        game.mainGame.profile.markDirty()

        close()
    }

    private fun updateVolumes() {
        game.mainGame.profile.soundVolume = soundSlider.value
        game.mainGame.profile.musicVolume = musicSlider.value
    }
}
