package gameoflife

class Universe(var size: Int) {
    private var universe = Array(size) { Array(size) { Cell() } }

    var generation = 0

    val population
        get() = universe.map { row -> row.count { cell -> cell.isAlive } }.sum()

    val isAlive
        get() = universe.map { row -> row.any { cell -> cell.isAlive } }.any { it }

    var isStable = false

    init {
        universe.forEachIndexed { y, row -> row.forEachIndexed { x, cell -> cell.setupNeighbors(x, y) } }
    }

    // Game tick
    fun evolve() {
        val aliveNeighborsCounts =
            universe.map { it.map { cell -> cell.neighbors.count { neighbor -> neighbor.isAlive } } }
        universe.forEachIndexed { y, row -> row.forEachIndexed { x, cell -> cell.evolve(aliveNeighborsCounts[y][x]) } }
        val newAliveNeighborsCounts =
            universe.map { it.map { cell -> cell.neighbors.count { neighbor -> neighbor.isAlive } } }
        isStable = aliveNeighborsCounts == newAliveNeighborsCounts
        generation++
    }

    // Clearing universe
    fun clear() {
        universe.map { it.map { cell -> cell.isAlive = false } }
        generation = 0
        isStable = false
    }

    // Get operator for accessing individual cell
    operator fun get(x: Int, y: Int): Cell {
        if (x < 0 || y < 0 || x >= size || y >= size)
            throw ArrayIndexOutOfBoundsException("Indices $x, $y are out of bounds for a board of size $size")
        return universe[x][y]
    }

    // Set operator for universe[i, j] = ...
    operator fun set(x: Int, y: Int, cell: Cell) {
        if (x < 0 || y < 0 || x >= size || y >= size)
            throw ArrayIndexOutOfBoundsException("Indices $x, $y are out of bounds for a board of size $size")
        universe[x][y] = cell
    }

    // Returns neighbors at index
    private fun getNeighborsAt(x: Int, y: Int): MutableList<Cell> {
        val neighbors = mutableListOf<Cell>()
        for (y_delta in -1..1) {
            for (x_delta in -1..1) {
                if (x_delta == 0 && y_delta == 0)
                    continue
                val xN = x + x_delta
                val yN = y + y_delta
                if (xN < 0 || xN >= size || yN < 0 || yN >= size)
                    continue
                neighbors.add(universe[yN][xN])
            }
        }
        return neighbors
    }

    // Extends Cell with method that adds neighbors
    private fun Cell.setupNeighbors(x: Int, y: Int) {
        neighbors.addAll(getNeighborsAt(x, y))
    }

    override fun toString(): String {
        var str = ""
        for (i in 0 until size) {
            for (j in 0 until size) {
                str += universe[i][j].toString() + " "
            }
            str += "\n"
        }
        return str
    }
}