package tv.still.launcher

/** Pure ordering rules, shared by storage and unit tests. Missing apps remain in storage
 * so temporarily disabled or updated favourites recover their original position. */
object FavouriteOrder {
    fun visible(saved: List<String>, available: Set<String>) = saved.distinct().filter { it in available }
    /** First-run favourites: preferred apps that are installed, in preferred order; otherwise the first [fallback]. */
    fun defaults(installed: List<String>, preferred: List<String>, fallback: Int): List<String> {
        val present = installed.toSet()
        return preferred.distinct().filter { it in present }.ifEmpty { installed.distinct().take(fallback) }
    }
    /** Apps for the All Apps grid: everything not already in the dock. */
    fun rest(apps: List<String>, favourites: List<String>): List<String> {
        val pinned = favourites.toSet()
        return apps.filterNot { it in pinned }
    }
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
