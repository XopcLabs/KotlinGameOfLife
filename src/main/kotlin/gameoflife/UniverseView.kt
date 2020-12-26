package gameoflife

import javafx.application.Platform
import javafx.beans.value.ObservableValue
import javafx.geometry.HPos
import javafx.geometry.VPos
import javafx.scene.control.Button
import javafx.scene.control.Slider
import javafx.scene.effect.Glow
import javafx.scene.input.MouseButton
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.RowConstraints
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.stage.StageStyle
import tornadofx.*
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.system.exitProcess

class UniverseView : View("Game of Life") {

    companion object {
        @JvmStatic
        val DEFAULT_SIZE = 40

        @JvmStatic
        val MIN_SIZE = 6

        @JvmStatic
        val MAX_SIZE = 65

        @JvmStatic
        val UNIVERSE_DEFAULT_SIZE = 500

        @JvmStatic
        val UNIVERSE_MIN_SIZE = 20

        @JvmStatic
        val UNIVERSE_SIZE_INC = 10

        @JvmStatic
        val UNIVERSE_MAX_SIZE = 1000

        @JvmStatic
        val GRID_DIMENSION = 600.0

        @JvmStatic
        val GRID_PAD = 10.0

        @JvmStatic
        val CELL_MIN_MARGIN = 3.0

        @JvmStatic
        val CELL_MAX_MARGIN = 1.0
    }

    override val root: GridPane by fxml(hasControllerAttribute = true)
    val grid: GridPane by fxid()
    val titleBar: AnchorPane by fxid()

    // Interactive
    val playButton: Button by fxid()
    val nextButton: Button by fxid()
    val resetButton: Button by fxid()
    val closeButton: Button by fxid()
    val speedSlider: Slider by fxid()

    // Text indicatiors
    val positionIndicator: Text by fxid()
    val sizeIndicator: Text by fxid()
    val generationIndicator: Text by fxid()
    val populationIndicator: Text by fxid()
    val universeSizeIndicator: Text by fxid()

    // Game universe
    private var universeSize = UNIVERSE_DEFAULT_SIZE
    private var universe = Universe(universeSize)
    private var delay = 200L

    // View parameters
    private var zoomSize = DEFAULT_SIZE
    private var zoomOnLeft = true

    // Upper left corner coord
    private var zoomLeftX = universeSize / 2 - zoomSize / 2
    private var zoomLeftY = universeSize / 2 - zoomSize / 2

    // Bottom right corner coord
    private var zoomRightX = universeSize / 2 + zoomSize / 2
    private var zoomRightY = universeSize / 2 + zoomSize / 2

    // Cell size on current zoom
    private var cellMargin = CELL_MIN_MARGIN + zoomSize * (CELL_MAX_MARGIN - CELL_MIN_MARGIN) / (MAX_SIZE - MIN_SIZE)
    private var cellSize = (GRID_DIMENSION - GRID_PAD) / zoomSize - cellMargin

    // GridPane nodes array
    private var rectangles = Array(universeSize) { Array<Rectangle?>(universeSize) { null } }

    // Board panning parameters
    private var panStartX = 0
    private var panStartY = 0
    private var panEndX = 0
    private var panEndY = 0

    // Timer for delays
    private val timer = Timer()
    private var task: TimerTask? = null

    // Theme
    private val offColor = Color.valueOf("#232323")
    private val onColor = Color.valueOf("#cecece")

    // Window dragging
    private var xOffset = 0.0
    private var yOffset = 0.0

    init {
        // Shortcuts
        shortcut("Space") {
            playButtonLogic()
        }
        shortcut("Ctrl+Space") {
            if (task == null)
                gameTick()
        }
        shortcut("R") {
            universe.clear()
            gridUpdate()
            updateStats()
        }

        // Tooltips
        playButton.tooltip("Toggle auto-play (Space).")
        nextButton.tooltip("Next generation (Ctrl + Space).")
        resetButton.tooltip("Reset the board (R).").apply { font = Font.font(12.0) }
        speedSlider.tooltip("Adjust auto-play speed (1 gen/s to 10 gen/s)").apply { font = Font.font(12.0) }
        universeSizeIndicator.tooltip("Board size (Ctrl + Mouse scroll wheel to adjust).")
        positionIndicator.tooltip("Position of the cursor on the board (Middle mouse button for panning).")
            .apply { font = Font.font(12.0) }
        sizeIndicator.tooltip("View level (Mouse scroll wheel for zoom).").apply { font = Font.font(12.0) }

        // Styling game window
        primaryStage.resizableProperty().set(false)
        primaryStage.initStyle(StageStyle.TRANSPARENT)

        // Start tracking moving game window
        titleBar.setOnMousePressed { event ->
            xOffset = event.sceneX
            yOffset = event.sceneY
        }

        // Moving game window
        titleBar.setOnMouseDragged { event ->
            primaryStage.x = event.screenX - xOffset
            primaryStage.y = event.screenY - yOffset
        }


        // Interactive elements logic
        playButton.setOnMouseClicked {
            playButtonLogic()
        }

        nextButton.setOnMouseClicked {
            gameTick()
        }

        resetButton.setOnMouseClicked {
            universe.clear()
            gridUpdate()
            updateStats()
        }

        closeButton.setOnMouseClicked { event ->
            if (event.button == MouseButton.PRIMARY) {
                close()
                exitProcess(0)
            }
        }

        // Changing speed using slider
        speedSlider.valueProperty()
            .addListener(ChangeListener<Number> { _: ObservableValue<out Number>?, _: Number, newNumber: Number ->
                newNumber as Double
                if (ceil(newNumber) == floor(newNumber)) {
                    delay = (1000 / newNumber).toLong()
                    if (task != null) {
                        task?.cancel()
                        task = timer.autoplay()
                    }
                }
            })

        // Mouse pointer position coordinate tracker
        grid.setOnMouseMoved { event ->
            val cellX = zoomLeftX + (event.x / cellSize).toInt()
            val cellY = zoomLeftY + (event.y / cellSize).toInt()
            positionIndicator.text = "x: $cellX, y:$cellY"
        }

        // Starting panning
        grid.setOnDragDetected { event ->
            if (event.button == MouseButton.MIDDLE) {
                // Coordinates of cell under mouse pointer relative to the view
                panStartX = (event.x / cellSize).toInt()
                panStartY = (event.y / cellSize).toInt()
            }
        }

        // Panning view over the board
        grid.setOnMouseDragOver { event ->
            if (event.button == MouseButton.MIDDLE) {
                // Relative coordinates of pan end
                panEndX = (event.x / cellSize).toInt()
                panEndY = (event.y / cellSize).toInt()

                // Difference
                val diffX = panEndX - panStartX
                val diffY = panEndY - panStartY

                // Setting panStart = panEnd for smooth panning
                panStartX = panEndX
                panStartY = panEndY

                // Moving game view
                zoomLeftX -= diffX
                zoomLeftY -= diffY
                zoomRightX -= diffX
                zoomRightY -= diffY

                // Adjusting corners
                adjustCorners()

                // Updating grid
                gridReset()
                gridSetup()
            }
        }

        // Changing zoom size
        grid.setOnScroll { event ->
            // Handling universe size changes
            if (event.isControlDown) {
                if (task != null)
                    return@setOnScroll
                if (event.deltaY < 0 && universeSize > UNIVERSE_MIN_SIZE) {
                    universeSize -= UNIVERSE_SIZE_INC
                    universeSizeIndicator.text = "Size: $universeSize × $universeSize"
                    setupUniverse()
                } else if (event.deltaY > 0 && universeSize < UNIVERSE_MAX_SIZE) {
                    universeSize += UNIVERSE_SIZE_INC
                    universeSizeIndicator.text = "Size: $universeSize × $universeSize"
                    setupUniverse()
                }
            }
            // Handling view size change
            else {
                if (event.deltaY < 0 && zoomSize < minOf(MAX_SIZE, universeSize)) {
                    zoomSize++

                    //Update game view
                    if (zoomOnLeft) {
                        zoomLeftX--
                        zoomLeftY--
                    } else {
                        zoomRightX++
                        zoomRightY++
                    }
                } else if (event.deltaY > 0 && zoomSize > MIN_SIZE) {
                    zoomSize--

                    //Update game view
                    if (zoomOnLeft) {
                        zoomLeftX++
                        zoomLeftY++
                    } else {
                        zoomRightX--
                        zoomRightY--
                    }
                }
                // Switching side for next zoom
                zoomOnLeft = !zoomOnLeft
            }
            // Adjusting to corners
            adjustCorners()
            // Updating grid
            gridReset()
            gridSetup()
        }

        // Initial setup
        gridSetup()
    }

    private fun playButtonLogic() {
        if (task != null) {
            playButton.text = "▶"
            task?.cancel()
            task = null

            nextButton.disableProperty().set(false)
            resetButton.disableProperty().set(false)
            sizeIndicator.disableProperty().set(false)
        } else {
            playButton.text = "⏸"

            task = timer.autoplay()

            nextButton.disableProperty().set(true)
            resetButton.disableProperty().set(true)
            sizeIndicator.disableProperty().set(true)
        }
    }

    private fun gameTick() {
        universe.evolve()
        gridUpdate()
        updateStats()
        if (task != null && universe.isStable) {
            task?.cancel()
            task = null
            Platform.runLater { playButton.text = "▶" }
        }
    }

    private fun gridSetup() {
        // Update text
        updateStats()
        sizeIndicator.text = "$zoomSize × $zoomSize"
        universeSizeIndicator.text = "Size: $universeSize × $universeSize"

        // Update constraints
        for (i in 0 until zoomSize) {
            grid.columnConstraints.add(ColumnConstraints().apply {
                halignment = HPos.CENTER
                prefWidth = 100.0
            })

            grid.rowConstraints.add(RowConstraints().apply {
                valignment = VPos.CENTER
                prefHeight = 100.0
            })
        }

        // Updating cell size
        cellMargin = CELL_MIN_MARGIN + zoomSize * (CELL_MAX_MARGIN - CELL_MIN_MARGIN) / (MAX_SIZE - MIN_SIZE)
        cellSize = (GRID_DIMENSION - GRID_PAD) / zoomSize - cellMargin

        for (x in zoomLeftX until minOf(zoomRightX, universeSize))
            for (y in zoomLeftY until minOf(zoomRightY, universeSize)) {
                // Create rectangle
                val cellRect = Rectangle(cellSize, cellSize, offColor).apply { arcHeight = 5.0; arcWidth = 5.0 }
                rectangles[x][y] = cellRect
                // Fill rectangles with color after retaining data
                cellRect.fill = if (universe[x, y].isAlive) onColor else offColor
                // Add listeners
                cellRect.apply {
                    setOnMouseEntered {
                        effect = Glow(1.0)
                    }

                    setOnMouseExited {
                        fill = if (universe[x, y].isAlive) onColor else offColor
                        effect = null
                    }

                    setOnMouseClicked {
                        universe[x, y].flipState()

                        fill = if (universe[x, y].isAlive) onColor else offColor
                    }

                    setOnDragDetected {
                        startFullDrag()
                    }

                    setOnMouseDragEntered {
                        if (!it.isMiddleButtonDown) {
                            universe[x, y].isAlive = it.button == MouseButton.PRIMARY

                            fill = if (universe[x, y].isAlive) onColor
                            else offColor
                        }
                    }
                }
                grid.add(cellRect, x - zoomLeftX, y - zoomLeftY)
            }
    }

    // Re-create universe after size change and setup everything
    private fun setupUniverse() {
        universe = Universe(universeSize)
        rectangles = Array(universeSize) { Array<Rectangle?>(universeSize) { null } }
        zoomSize = minOf(zoomSize, universeSize)
        zoomLeftX = universeSize / 2 - zoomSize / 2
        zoomLeftY = universeSize / 2 - zoomSize / 2
        zoomRightX = zoomLeftX + zoomSize
        zoomRightY = zoomLeftY + zoomSize
        cellMargin = CELL_MIN_MARGIN + zoomSize * (CELL_MAX_MARGIN - CELL_MIN_MARGIN) / (MAX_SIZE - MIN_SIZE)
        cellSize = (GRID_DIMENSION - GRID_PAD) / zoomSize - cellMargin
    }

    // Adjusts corners to the grid constraints after zoom or pan
    private fun adjustCorners() {
        if (zoomLeftX < 0) {
            zoomRightX -= zoomLeftX
            zoomLeftX = 0
        } else if (zoomRightX > universeSize) {
            zoomLeftX -= zoomRightX - universeSize
            zoomRightX = universeSize
        }
        if (zoomLeftY < 0) {
            zoomRightY -= zoomLeftY
            zoomLeftY = 0
        } else if (zoomRightY > universeSize) {
            zoomLeftY -= zoomRightY - universeSize
            zoomRightY = universeSize
        }
    }

    // Clear everything inside the grid (doesn't clear the universe)
    private fun gridReset() {
        grid.columnConstraints.clear()
        grid.rowConstraints.clear()
        grid.children.clear()
    }

    // Update grid after evolution step
    private fun gridUpdate() {
        for (x in zoomLeftX until zoomRightX)
            for (y in zoomLeftY until zoomRightY) {
                if (universe[x, y].isAlive) {
                    rectangles[x][y]?.fill = onColor
                } else {
                    rectangles[x][y]?.fill = offColor
                }
            }
    }

    // Update statistics text after evolution step
    private fun updateStats() {
        generationIndicator.text = "Generation: ${universe.generation}"
        populationIndicator.text = "Population: ${universe.population}"
    }

    private fun Timer.autoplay(): TimerTask {
        return scheduleAtFixedRate(0L, delay) { gameTick() }
    }
}