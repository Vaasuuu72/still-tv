package tv.still.launcher

/** One top-level Home position: an installed app (by package) or a named folder of apps.
 * [key] is the stable identity used for focus restore and ordering; it never depends on grid index. */
sealed class HomeItem {
    abstract val key: String

    data class App(val id: String) : HomeItem() {
        override val key: String get() = id
    }

    data class Folder(val id: String, val name: String, val apps: List<String>) : HomeItem() {
        override val key: String get() = FOLDER_PREFIX + id
    }

    companion object {
        /** Package names cannot contain ':', so folder keys never collide with app keys. */
        const val FOLDER_PREFIX = "folder:"
    }
}
