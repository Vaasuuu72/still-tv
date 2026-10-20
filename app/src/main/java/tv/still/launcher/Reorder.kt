package tv.still.launcher

import tv.still.launcher.FocusMap.Direction

/** D-pad movement of a picked-up item in an ordered grid. Left/Right move one place (wrapping between
 * rows, since Home is one ordered list); Up/Down move a whole row. Edges are no-ops. */
internal object Reorder {
    data class Result<T>(val items: List<T>, val index: Int)

    fun <T> move(items: List<T>, index: Int, direction: Direction, columns: Int): Result<T> {
        if (index !in items.indices) return Result(items, index)
        val target = when (direction) {
            Direction.LEFT -> index - 1
            Direction.RIGHT -> index + 1
            Direction.UP -> if (index >= columns) index - columns else index
            Direction.DOWN -> when {
                index + columns <= items.lastIndex -> index + columns
                index / columns < items.lastIndex / columns -> items.lastIndex
                else -> index
            }
        }
        if (target == index || target !in items.indices) return Result(items, index)
        val result = items.toMutableList()
        val item = result.removeAt(index)
        result.add(target, item)
        return Result(result, target)
    }
}
