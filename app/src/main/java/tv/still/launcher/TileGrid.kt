package tv.still.launcher

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import tv.still.launcher.FocusMap.Direction

/** Rows of tiles for an ordered list of keys (Home, a folder, or search results).
 * Tile views are reused by key across renders, so reordering or refreshing re-attaches existing views
 * instead of inflating and re-binding artwork. */
internal class TileGrid(
    private val container: LinearLayout,
    private val inflater: LayoutInflater,
    /** Binds identity and (possibly cached) artwork to a tile for [key]. */
    private val binder: (tile: View, key: String) -> Unit,
    /** Centre each row (folders); Home rows stay start-aligned so columns line up. */
    private val centred: Boolean = false,
) {
    private val cache = HashMap<String, View>()
    var keys: List<String> = emptyList(); private set
    var views: List<View> = emptyList(); private set
    var columns = HomeLayout.DEFAULT_COLUMNS; private set

    fun render(keys: List<String>, columns: Int, tileWidth: Int, tileHeight: Int, gap: Int, rowGap: Int) {
        this.keys = keys; this.columns = columns
        for (i in 0 until container.childCount) (container.getChildAt(i) as? LinearLayout)?.removeAllViews()
        container.removeAllViews()
        val wanted = keys.toSet()
        cache.keys.retainAll(wanted)
        cache.values.removeAll { Tiles.sizeOf(it) != (tileWidth to tileHeight) }
        val result = ArrayList<View>(keys.size)
        keys.chunked(columns).forEach { rowKeys ->
            val row = Tiles.row(container)
            if (centred) row.gravity = android.view.Gravity.CENTER_HORIZONTAL
            rowKeys.forEachIndexed { col, key ->
                val tile = cache.getOrPut(key) { Tiles.create(inflater, row, tileWidth, tileHeight) }
                binder(tile, key)
                row.addView(tile, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { if (col < columns - 1) marginEnd = gap })
                result += tile
            }
            container.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = rowGap })
        }
        views = result
    }

    fun indexOf(key: String?): Int = if (key == null) -1 else keys.indexOf(key)
    fun viewFor(key: String?): View? = indexOf(key).let { views.getOrNull(it) }
    fun indexOf(view: View?): Int = views.indexOf(view)
    fun rowOf(index: Int) = if (index < 0) -1 else index / columns

    /** Deterministic in-grid focus target, or null when the move leaves the grid at the top edge. */
    fun target(from: Int, direction: Direction, rtl: Boolean): Int? {
        if (views.isEmpty()) return null
        val d = if (rtl) mirror(direction) else direction
        val map = FocusMap(listOf(FocusMap.Section(views.size, columns)))
        if (d == Direction.UP && from in 0 until columns) return null
        return map.move(FocusMap.Pos(0, from), d).index
    }

    fun clear() { cache.clear(); keys = emptyList(); views = emptyList(); container.removeAllViews() }

    companion object {
        fun mirror(direction: Direction) = when (direction) {
            Direction.LEFT -> Direction.RIGHT
            Direction.RIGHT -> Direction.LEFT
            else -> direction
        }
    }
}
