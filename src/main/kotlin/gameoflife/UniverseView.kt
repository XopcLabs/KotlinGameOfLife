package gameoflife

import javafx.geometry.HPos
import javafx.geometry.VPos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.effect.Glow
import javafx.scene.input.MouseButton
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.RowConstraints
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.Text
import javafx.stage.StageStyle
import tornadofx.*
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.system.exitProcess

class UniverseView : View("Game of Life") {

    companion object {
        @JvmStatic
        val DEFAULT_SIZE = 20

        @JvmStatic
        val MIN_SIZE = 6

        @JvmStatic
        val MAX_SIZE = 45

        @JvmStatic
        val DELAY_MS = 250L

        @JvmStatic
        val GRID_DIMENSION = 600.0

        @JvmStatic
        val GRID_PAD = 10.0

        @JvmStatic
        val CELL_MARGIN = 3.0
    }


    override val root: GridPane by fxml(hasControllerAttribute = true)
    val grid: GridPane by fxid()
    val titleBar: AnchorPane by fxid()

    // Interactive
    val sizeIndicator: Text by fxid()
    val playButton: Button by fxid()
    val nextButton: Button by fxid()
    val resetButton: Button by fxid()
    val closeButton: Button by fxid()

    // Statistics
    val generationIndicator: Text by fxid()
    val populationIndicator: Text by fxid()

    // Game universe
    var universe = Universe(DEFAULT_SIZE)
    var universeSize = universe.size

    // Timer for delays
    private val timer = Timer()
    private var task: TimerTask? = null

    // Theme
    private val offColor = Color.valueOf("#37474F")
    private val onColor = Color.valueOf("#608D8B")

    // Window dragging
    private var xOffset = 0.0
    private var yOffset = 0.0

    init {
        // Styling game window
        primaryStage.resizableProperty().set(false)
        primaryStage.initStyle(StageStyle.TRANSPARENT)

        // Moving game window
        titleBar.setOnMousePressed { event ->
            xOffset = event.sceneX
            yOffset = event.sceneY
        }

        titleBar.setOnMouseDragged { event ->
            primaryStage.x = event.screenX - xOffset
            primaryStage.y = event.screenY - yOffset
        }

        gridSetup()

        // Interactive elements logic
        sizeIndicator.setOnMouseClicked { event ->
            // Universe size can't be changed while running
            if (task != null)
                return@setOnMouseClicked

            var changed = false
            when (event.button) {
                MouseButton.PRIMARY -> {
                    val newSize = universeSize + 1
                    if (newSize <= MAX_SIZE) {
                        universeSize = newSize
                        changed = true
                    }
                }
                MouseButton.SECONDARY -> {
                    val newSize = universeSize - 1
                    if (newSize >= MIN_SIZE) {
                        universeSize = newSize
                        changed = true
                    }
                }
                else -> {
                }
            }
            if (changed) {
                sizeIndicator.text = "$universeSize × $universeSize ↕"
                gridReset()
                gridSetup()
            }
        }

        playButton.setOnMouseClicked {
            if (task != null) {
                playButton.text = "▶"
                task?.cancel()
                task = null

                nextButton.disableProperty().set(false)
                resetButton.disableProperty().set(false)
                sizeIndicator.disableProperty().set(false)
            } else {
                playButton.text = "⏸"

                task = timer.scheduleAtFixedRate(0L, DELAY_MS) {
                    universe.evolve()
                    updateGrid()
                    updateStats()
                }

                nextButton.disableProperty().set(true)
                resetButton.disableProperty().set(true)
                sizeIndicator.disableProperty().set(true)
            }
        }

        nextButton.setOnMouseClicked {
            universe.evolve()
            updateGrid()
            updateStats()
        }

        resetButton.setOnMouseClicked {
            universe.clear()
            updateGrid()
        }

        closeButton.setOnMouseClicked { event ->
            if (event.button == MouseButton.PRIMARY) {
                close()
                exitProcess(0)
            }
        }
    }

    private fun gridSetup() {
        sizeIndicator.text = "$universeSize * $universeSize ↕"

        for (i in 0 until universeSize) {
            grid.columnConstraints.add(ColumnConstraints().apply {
                halignment = HPos.CENTER
                prefWidth = 100.0
            })

            grid.rowConstraints.add(RowConstraints().apply {
                valignment = VPos.CENTER
                prefHeight = 100.0
            })
        }

        retainData()

        val width = (GRID_DIMENSION - GRID_PAD) / grid.columnCount - CELL_MARGIN
        val height = (GRID_DIMENSION - GRID_PAD) / grid.rowCount - CELL_MARGIN

        for (x1 in 0 until grid.columnCount)
            for (y1 in 0 until grid.rowCount) {
                val cellRect = Rectangle(width, height, offColor).apply { arcHeight = 5.0; arcWidth = 5.0 }
                cellRect.fill = if (universe[x1, y1].isAlive) onColor else offColor
                cellRect.apply {

                    setOnMouseEntered {
                        effect = Glow(1.0)
                    }

                    setOnMouseExited {
                        fill = if (universe[x1, y1].isAlive) onColor else offColor
                        effect = null
                    }

                    setOnMouseClicked {
                        universe[x1, y1].flipState()

                        fill = if (universe[x1, y1].isAlive) onColor else offColor
                    }

                    setOnDragDetected {
                        startFullDrag()
                    }

                    setOnMouseDragEntered {
                        universe[x1, y1].isAlive = it.button == MouseButton.PRIMARY

                        fill = if (universe[x1, y1].isAlive) onColor
                        else offColor
                    }

                }
                grid.add(cellRect, x1, y1)
            }
    }

    // Set old universe state to the new universe after resizing
    private fun retainData() {
        val newUniverse = Universe(grid.columnCount)
        val minSize = if (universe.size < newUniverse.size) universe.size else newUniverse.size
        for (i in 0 until minSize)
            for (j in 0 until minSize)
                newUniverse[i, j] = universe[i, j]
        universe = newUniverse
    }

    // Clear everything inside the grid (doesn't clear the universe)
    private fun gridReset() {
        grid.columnConstraints.clear()
        grid.rowConstraints.clear()
        grid.children.clear()
    }

    // Update grid after evolution step
    private fun updateGrid() {
        for (x in 0 until grid.columnCount)
            for (y in 0 until grid.rowCount) {
                if (universe[x, y].isAlive) {
                    (grid[x, y] as Rectangle).fill = onColor
                } else {
                    (grid[x, y] as Rectangle).fill = offColor
                }
            }
    }

    // Update statistics text after evolution step
    private fun updateStats() {
        populationIndicator.text = "Population: ${universe.population} cells"
        generationIndicator.text = "Generation: ${universe.generation}"
    }

    operator fun GridPane.get(x: Int, y: Int): Node? {
        for (node in children) {
            if (GridPane.getColumnIndex(node) == x && GridPane.getRowIndex(node) == y)
                return node
        }
        return null
    }
}