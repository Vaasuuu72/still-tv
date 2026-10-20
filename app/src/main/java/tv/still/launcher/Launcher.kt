package tv.still.launcher

import android.view.KeyEvent
import android.view.View

/** What overlays need from the launcher. Implemented by [MainActivity]; keeps overlays free of
 * storage and package-manager details. */
internal interface Launcher {
    val geometry: HomeLayout
    /** True unless Reduce Motion is on or Android's animations are off. */
    val animate: Boolean
    val appearance: Appearance
    fun px(dp: Float): Int
    /** Binds identity and cached-or-loading artwork for an app or folder key. */
    fun bindTile(tile: View, key: String)
    fun appLabel(id: String): String
    /** Installed apps in display (alphabetical) order. */
    fun installedApps(): List<AppEntry>
    fun visibleFolder(folderId: String): HomeItem.Folder?
    fun launch(appId: String)
    fun showItemMenu(key: String, anchor: View, folderId: String?)
    fun saveFolderOrder(folderId: String, apps: List<String>)
    /** Shared OK handling: short press clicks, a 250 ms hold opens options once and never also clicks. */
    fun handleSelect(event: KeyEvent, tile: View, onClick: () -> Unit, onHold: () -> Unit): Boolean
    fun setAppearance(value: Appearance)
    fun resetHome()
    fun openSystemSettings()
    fun voiceSearchAvailable(): Boolean
    fun startVoiceSearch()
    fun announce(text: CharSequence)
    fun versionName(): String
}
