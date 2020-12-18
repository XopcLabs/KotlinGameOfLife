package gameoflife

import javafx.scene.layout.GridPane
import tornadofx.*

class TestView : View("Test") {
    override val root : GridPane by fxml()
}