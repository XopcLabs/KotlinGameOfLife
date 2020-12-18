package gameoflife

class Universe(var size: Int) {
    private var universe = Array(size) { Array(size) { Cell() } }

    val isAlive
        get() = universe.map { row -> row.any { cell -> cell.isAlive } }.any { it }

    init {
        universe.forEachIndexed { y, row -> row.forEachIndexed { x, cell -> cell.setupNeighbors(x, y) } }
    }

    fun evolve() {
        val aliveNeighborsCounts =
            universe.map { it.map { cell -> cell.neighbors.count { neighbor -> neighbor.isAlive } } }
        universe.forEachIndexed { y, row -> row.forEachIndexed { x, cell -> cell.evolve(aliveNeighborsCounts[y][x]) } }
    }

    fun clear() = universe.map { it.map { cell -> cell.isAlive = false } }

    operator fun get(x: Int, y: Int): Cell {
        if (x < 0 || y < 0 || x >= size || y >= size)
            throw ArrayIndexOutOfBoundsException("Indices $x, $y are out of bounds for a board of size $size")
        return universe[x][y]
    }

    private fun getNeighborsAt(x: Int, y: Int): MutableList<Cell> {
        val neighbors = mutableListOf<Cell>()
        for (y_delta in -1..1) {
            for (x_delta in -1..1) {
                if (x_delta == 0 && y_delta == 0)
                    continue
                val Xn = x + x_delta
                val yN = y + y_delta
                if (Xn < 0 || Xn >= size || yN < 0 || yN >= size)
                    continue
                neighbors.add(universe[yN][Xn])
            }
        }
        return neighbors
    }

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