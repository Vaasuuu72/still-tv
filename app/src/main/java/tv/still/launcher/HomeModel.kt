package tv.still.launcher

import tv.still.launcher.HomeItem.App
import tv.still.launcher.HomeItem.Folder

/** Pure rules for the single ordered Home list (apps and folders). Every function returns a new list.
 * The stored ("full") list keeps apps that are currently disabled or uninstalled, so they return to
 * their position if they come back; [visible] is what the grid shows. */
internal object HomeModel {
    const val MAX_NAME_LENGTH = 32

    /** First Home order. Legacy 1.x/2.0 favourites (even an empty list) win over the preferred defaults. */
    fun seed(installed: List<String>, legacyFavourites: List<String>?, preferred: List<String>): List<HomeItem> {
        val present = installed.toSet()
        val head = legacyFavourites?.distinct() ?: preferred.distinct().filter { it in present }
        val headSet = head.toSet()
        return (head + installed.filterNot { it in headSet }).distinct().map(::App)
    }

    /** Drops duplicate app entries (first occurrence wins) and appends newly installed apps. */
    fun reconcile(stored: List<HomeItem>, installed: List<String>): List<HomeItem> {
        val seen = HashSet<String>()
        val result = stored.mapNotNull { item ->
            when (item) {
                is App -> item.takeIf { seen.add(it.id) }
                is Folder -> item.copy(apps = item.apps.filter { seen.add(it) }).takeIf { it.apps.isNotEmpty() }
            }
        }
        return result + installed.filter { seen.add(it) }.map(::App)
    }

    fun visible(full: List<HomeItem>, available: Set<String>): List<HomeItem> = full.mapNotNull { item ->
        when (item) {
            is App -> item.takeIf { it.id in available }
            is Folder -> item.copy(apps = item.apps.filter { it in available }).takeIf { it.apps.isNotEmpty() }
        }
    }

    /** Applies a reordered visible list to the full list. Hidden entries stay after the visible
     * entry that preceded them, so a temporarily missing app does not lose its place. */
    fun replaceVisible(full: List<HomeItem>, edited: List<HomeItem>, available: Set<String>): List<HomeItem> {
        val visibleKeys = visible(full, available).map { it.key }.toSet()
        val byKey = full.associateBy { it.key }
        val merged = anchorMerge(full.map { it.key }, edited.map { it.key }) { it in visibleKeys }
        return merged.mapNotNull { key -> byKey[key] }
    }

    /** Reorders the visible members of one folder, keeping its hidden members anchored. */
    fun reorderFolder(full: List<HomeItem>, folderId: String, editedApps: List<String>, available: Set<String>) =
        full.map { item ->
            if (item is Folder && item.id == folderId) item.copy(apps = anchorMerge(item.apps, editedApps) { it in available })
            else item
        }

    fun createFolder(full: List<HomeItem>, appId: String, folderId: String, name: String): List<HomeItem> {
        val source = containerOf(full, appId) ?: return full
        val folder = Folder(folderId, name, listOf(appId))
        if (source is App) return full.map { if (it == source) folder else it }
        val sourceId = (source as Folder).id
        val without = removeApp(full, appId)
        val anchor = without.indexOfFirst { it is Folder && it.id == sourceId }
        // When this was the source folder's last app, that folder is gone and the new one takes its place.
        return insertAt(without, if (anchor >= 0) anchor + 1 else full.indexOf(source), folder)
    }

    fun moveToFolder(full: List<HomeItem>, appId: String, folderId: String): List<HomeItem> {
        if (full.none { it is Folder && it.id == folderId } || containerOf(full, appId) == null) return full
        if ((containerOf(full, appId) as? Folder)?.id == folderId) return full
        return removeApp(full, appId).map { if (it is Folder && it.id == folderId) it.copy(apps = it.apps + appId) else it }
    }

    fun moveToHome(full: List<HomeItem>, appId: String): List<HomeItem> {
        val source = containerOf(full, appId) as? Folder ?: return full
        val index = full.indexOf(source)
        val remaining = source.apps - appId
        val replacement = if (remaining.isEmpty()) listOf(App(appId)) else listOf(source.copy(apps = remaining), App(appId))
        return full.subList(0, index) + replacement + full.subList(index + 1, full.size)
    }

    fun ungroup(full: List<HomeItem>, folderId: String): List<HomeItem> =
        full.flatMap { if (it is Folder && it.id == folderId) it.apps.map(::App) else listOf(it) }

    fun rename(full: List<HomeItem>, folderId: String, name: String): List<HomeItem> =
        full.map { if (it is Folder && it.id == folderId) it.copy(name = name) else it }

    fun cleanName(raw: String, fallback: String): String =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).trim().ifEmpty { fallback }

    fun newFolderId(full: List<HomeItem>): String {
        val used = full.filterIsInstance<Folder>().mapNotNull { it.id.removePrefix("f").toIntOrNull() }
        return "f${(used.maxOrNull() ?: 0) + 1}"
    }

    fun allApps(full: List<HomeItem>): List<String> = full.flatMap { if (it is Folder) it.apps else listOf((it as App).id) }

    fun folder(full: List<HomeItem>, folderId: String): Folder? = full.firstOrNull { it is Folder && it.id == folderId } as? Folder

    /** The top-level item holding [appId]: the App itself or the Folder that contains it. */
    fun containerOf(full: List<HomeItem>, appId: String): HomeItem? =
        full.firstOrNull { (it is App && it.id == appId) || (it is Folder && appId in it.apps) }

    private fun removeApp(full: List<HomeItem>, appId: String): List<HomeItem> = full.mapNotNull { item ->
        when (item) {
            is App -> item.takeUnless { it.id == appId }
            is Folder -> item.copy(apps = item.apps - appId).takeIf { it.apps.isNotEmpty() }
        }
    }

    private fun insertAt(list: List<HomeItem>, index: Int, item: HomeItem): List<HomeItem> {
        val at = index.coerceIn(0, list.size)
        return list.subList(0, at) + item + list.subList(at, list.size)
    }

    /** Rebuilds [original] with its visible entries in [edited] order; each hidden entry follows the
     * visible entry that preceded it originally (or stays at the front). */
    private fun anchorMerge(original: List<String>, edited: List<String>, isVisible: (String) -> Boolean): List<String> {
        val followers = LinkedHashMap<String?, MutableList<String>>()
        var anchor: String? = null
        for (key in original) {
            if (isVisible(key)) anchor = key else followers.getOrPut(anchor) { mutableListOf() } += key
        }
        val result = ArrayList<String>(original.size)
        result += followers[null].orEmpty()
        val placed = HashSet<String>()
        for (key in edited) {
            if (!placed.add(key)) continue
            result += key
            result += followers[key].orEmpty()
        }
        // Visible anchors missing from [edited] (should not happen) keep their followers rather than dropping them.
        followers.filterKeys { it != null && it !in placed }.values.forEach { result += it }
        return result
    }
}
