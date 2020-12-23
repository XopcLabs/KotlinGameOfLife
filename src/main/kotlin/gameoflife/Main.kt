package gameoflife

import tornadofx.App
import tornadofx.launch

fun main() {
    launch<Main>()
}

class Main : App(UniverseView::class)