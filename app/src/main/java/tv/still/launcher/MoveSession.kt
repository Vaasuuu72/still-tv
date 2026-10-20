package tv.still.launcher

import tv.still.launcher.FocusMap.Direction

/** One "pick up and move" edit: the item being moved and the working order. Nothing is persisted until
 * the owner commits [keys]; cancelling simply re-renders [original]. */
internal class MoveSession(val key: String, val original: List<String>) {
    var keys: List<String> = original; private set
    val index: Int get() = keys.indexOf(key)
    val changed: Boolean get() = keys != original

    /** Returns true if the item moved. */
    fun move(direction: Direction, columns: Int): Boolean {
        val result = Reorder.move(keys, index, direction, columns)
        val moved = result.items != keys
        keys = result.items
        return moved
    }
}
