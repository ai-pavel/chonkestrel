package xyz.znix.xftl.game

import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.ui.Label
import xyz.znix.xftl.ui.WidgetContainer

/**
 * A yes or no question in vanilla's confirmation box, such as the main
 * menu's before a new game replaces the saved one. Either button (or
 * escape, for no) calls [close], and yes then calls [onYes].
 */
class ConfirmWindow(
    game: InGameState,
    message: String,
    private val onYes: () -> Unit,
    private val close: () -> Unit
) : Window() {
    private val widgetTree: WidgetContainer = game.uiLoader.load("confirm").mainWidget

    override val size: IPoint get() = widgetTree.root.size

    init {
        (widgetTree.byId["message"] as Label).text = message

        // Re-perform the layout to fit the message
        widgetTree.root.updateSizes()
        widgetTree.root.expandToParent(ConstPoint.ZERO)
        widgetTree.root.updateLayout()

        buttons += widgetTree.buildButtons(game, this, ConstPoint.ZERO)

        widgetTree.addButtonListener("yes") {
            close()
            onYes()
        }
        widgetTree.addButtonListener("no") { close() }
    }

    override fun draw(g: Graphics) {
        g.pushTransform()
        g.translate(position.x.f, position.y.f)
        widgetTree.draw(g)
        g.popTransform()
    }

    override fun escapePressed() {
        close()
    }
}
