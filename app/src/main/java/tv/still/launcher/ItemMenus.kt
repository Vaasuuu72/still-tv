package tv.still.launcher

import android.content.Context
import tv.still.launcher.HomeItem.Folder

/** Context-menu contents for a held tile (UI-04/06). Only real, working actions are offered: there is no
 * uninstall or "delete system app" entry, and App Info hands off to Android's own screen. */
internal class ItemMenus(
    private val context: Context,
    private val full: List<HomeItem>,
    private val available: Set<String>,
    /** The folder the held app is shown in, or null on Home. */
    private val folderId: String?,
) {
    interface Actions {
        fun edit()
        fun newFolder()
        fun moveToFolder(target: String)
        fun moveToHome()
        fun rename(folder: Folder)
        fun ungroup(folder: Folder)
        fun appInfo()
        fun chooseFolder(folders: List<Folder>)
    }

    fun build(key: String, actions: Actions): List<MenuOverlay.Item> {
        val cancel = item(R.string.cancel) {}
        if (key.startsWith(HomeItem.FOLDER_PREFIX)) {
            val folder = HomeModel.folder(full, key.removePrefix(HomeItem.FOLDER_PREFIX)) ?: return listOf(cancel)
            return listOf(
                item(R.string.edit_home) { actions.edit() },
                item(R.string.rename_folder) { actions.rename(folder) },
                item(R.string.remove_folder) { actions.ungroup(folder) },
                cancel)
        }
        val otherFolders = HomeModel.visible(full, available).filterIsInstance<Folder>().filter { it.id != folderId }
        return buildList {
            add(item(if (folderId == null) R.string.edit_home else R.string.edit_folder) { actions.edit() })
            if (folderId != null) add(item(R.string.move_to_home) { actions.moveToHome() })
            if (otherFolders.isNotEmpty()) add(item(R.string.move_to_folder) { actions.chooseFolder(otherFolders) })
            add(item(R.string.new_folder) { actions.newFolder() })
            add(item(R.string.app_info) { actions.appInfo() })
            add(cancel)
        }
    }

    private fun item(label: Int, action: () -> Unit) = MenuOverlay.Item(context.getString(label), action)
}
