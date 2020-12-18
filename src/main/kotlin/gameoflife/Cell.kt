package gameoflife

class Cell(var isAlive: Boolean = false) {
    val neighbors: MutableList<Cell> = arrayListOf()

    fun evolve(aliveNeighborsCount: Int) {
        isAlive = when (aliveNeighborsCount) {
            0, 1 -> false
            2 -> isAlive
            3 -> true
            else -> false
        }
    }

    fun flipState () {
        isAlive = !isAlive
    }

    override fun toString(): String {
        return if (isAlive) "*" else "·"
    }
}