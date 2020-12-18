package gameoflife

import javafx.application.Application
import tornadofx.App

fun main() {
    Application.launch(Main::class.java)
}

class Main : App(UniverseView::class)