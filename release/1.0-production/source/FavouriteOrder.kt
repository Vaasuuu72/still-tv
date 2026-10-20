package tv.still.launcher

/** Pure ordering rules, shared by storage and unit tests. Missing apps remain in storage
 * so temporarily disabled or updated favourites recover their original position. */
object FavouriteOrder {
    fun visible(saved: List<String>, available: Set<String>) = saved.distinct().filter { it in available }
    fun toggle(saved: List<String>, id: String) = if (id in saved) saved.filterNot { it == id } else saved.distinct() + id
    fun move(saved: List<String>, id: String, delta: Int): List<String> {
        val result = saved.distinct().toMutableList()
        val from = result.indexOf(id)
        if (from < 0) return result
        val to = (from + delta).coerceIn(0, result.lastIndex)
        result.removeAt(from)
        result.add(to, id)
        return result
    }
}
