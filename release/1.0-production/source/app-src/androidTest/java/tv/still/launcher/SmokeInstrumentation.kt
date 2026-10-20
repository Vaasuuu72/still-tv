package tv.still.launcher

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView

/** Dependency-free integration runner. Run only on a dedicated emulator/test device.
 * It opens Settings and exercises real key events; never changes HOME or packages. */
class SmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val results = Bundle()
        val checks = mutableListOf<String>()
        var resultCode = Activity.RESULT_OK
        var activity: Activity? = null
        val preferences = targetContext.getSharedPreferences("still", android.content.Context.MODE_PRIVATE)
        var originalFavourites: String? = null
        try {
            activity = startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(targetContext.packageName,
                "tv.still.launcher.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val current = activity
            fun onUi(block: () -> Unit) { runOnMainSync(block); waitForIdleSync() }
            fun await(condition: () -> Boolean) {
                val until = SystemClock.uptimeMillis() + 10000
                while (SystemClock.uptimeMillis() < until) {
                    var success = false
                    onUi { success = condition() }
                    if (success) return
                    SystemClock.sleep(50)
                }
                error("Timed out waiting for UI")
            }
            val grid = current.findViewById<GridView>(R.id.grid)
            await { grid.count > 0 && grid.childCount > 0 }
            onUi { check(grid.hasFocus()); check(current.findViewById<TextView>(R.id.title).text == "Your apps") }
            checks.add("startup and initial focus")
            originalFavourites = preferences.getString("favourites", "[]")
            fun choose(label: String) {
                val until = SystemClock.uptimeMillis() + 5000
                while (SystemClock.uptimeMillis() < until) {
                    val node = uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)?.firstOrNull { it.text?.toString() == label }
                    if (node != null) {
                        if (node.isSelected) {
                            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                            waitForIdleSync(); await { grid.hasWindowFocus() }; return
                        }
                        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                    }
                    SystemClock.sleep(100)
                }
                val texts = mutableListOf<String>()
                fun inspect(node: android.view.accessibility.AccessibilityNodeInfo?) {
                    if (node == null) return
                    if (node.text != null) texts.add(node.text.toString() + " selected=" + node.isSelected)
                    for (i in 0 until node.childCount) inspect(node.getChild(i))
                }
                inspect(uiAutomation.rootInActiveWindow)
                error("Menu action not found: $label; visible: $texts")
            }
            onUi { grid.setSelection(grid.count - 1) }
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await { current.findViewById<TextView>(R.id.title).text == "All apps" }
            check(grid.count > 0) { "No test apps available" }
            checks.add("All apps opened via D-pad OK")
            onUi {
                for (i in 0 until grid.childCount) check(grid.getChildAt(i).findViewById<ImageView>(R.id.icon).drawable != null)
                grid.setSelection(0)
            }
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitForIdleSync()
            onUi { check(grid.hasFocus()); check(grid.selectedItemPosition == if (grid.count > 1) 1 else 0) }
            checks.add("D-pad navigation and icon fallbacks")
            // The framework's real long-press dispatch must open the management dialog.
            val down = SystemClock.uptimeMillis()
            sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
            SystemClock.sleep(900)
            sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
            await { !grid.hasWindowFocus() }
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await { grid.hasWindowFocus() }
            checks.add("long-press management and Back")
            val repository = AppRepository(targetContext)
            var chosen: AppEntry? = null
            onUi { chosen = grid.adapter.getItem(grid.selectedItemPosition) as AppEntry }
            val appId = checkNotNull(chosen).id
            val wasPinned = appId in repository.favourites()
            sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
            choose(if (wasPinned) "Unpin from favourites" else "Pin to favourites")
            await { (appId in AppRepository(targetContext).favourites()) != wasPinned }
            sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
            choose(if (wasPinned) "Pin to favourites" else "Unpin from favourites")
            await { (appId in AppRepository(targetContext).favourites()) == wasPinned }
            checks.add("pin/unpin and persisted preference reload")
            if (appId !in repository.favourites()) {
                sendKeyDownUpSync(KeyEvent.KEYCODE_MENU); choose("Pin to favourites")
            }
            val saved = repository.favourites()
            check(saved.indexOf(appId) > 0) { "At least two favourites needed for reorder test" }
            run {
                sendKeyDownUpSync(KeyEvent.KEYCODE_MENU); choose("Move earlier")
                await { repository.favourites().indexOf(appId) == saved.indexOf(appId) - 1 }
                sendKeyDownUpSync(KeyEvent.KEYCODE_MENU); choose("Move later")
                await { repository.favourites() == saved }
                checks.add("favourite reorder in both directions")
            }
            var settingsIndex = -1
            onUi { settingsIndex = (0 until grid.count).first { (grid.adapter.getItem(it) as AppEntry).label == "Settings" }; grid.setSelection(settingsIndex) }
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await { !current.hasWindowFocus() }
            // Still TV is intentionally not default HOME. Explicitly return to its
            // existing task, without changing HOME policy or the original launcher.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                uiAutomation.executeShellCommand("am start -W -n tv.still.launcher/.MainActivity")
            ).use { it.readBytes() }
            await { current.hasWindowFocus() }
            onUi { check(grid.selectedItemPosition == settingsIndex); check(current.findViewById<TextView>(R.id.title).text == "All apps") }
            checks.add("app-tile launch and explicit-return selection restoration")
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await { current.findViewById<TextView>(R.id.title).text == "Your apps" }
            onUi { check(grid.selectedItemPosition == grid.count - 1) }
            checks.add("Back and home selection restoration")
            onUi { current.findViewById<TextView>(R.id.settings).requestFocus() }
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            waitForIdleSync()
            onUi { check(grid.hasFocus()) }
            checks.add("footer-to-grid focus")
            // Actual Settings launch and return; leaves system configuration untouched.
            onUi { current.findViewById<TextView>(R.id.settings).requestFocus() }
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await { !current.hasWindowFocus() }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")
            ).use { it.readBytes() }
            await { current.hasWindowFocus() }
            checks.add("system Settings launch and return")
            repeat(10) {
                onUi { current.findViewById<TextView>(R.id.settings).requestFocus() }
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                await { !current.hasWindowFocus() }
                android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")
                ).use { it.readBytes() }
                await { current.hasWindowFocus() }
            }
            checks.add("10 repeated Settings open/return cycles")
            results.putString("stream", "\nPASS: ${checks.joinToString("; ")}\n")
            results.putInt("checks", checks.size)
        } catch (error: Throwable) {
            results.putString("stream", "\nFAIL after ${checks.joinToString()}: ${error.stackTraceToString()}\n")
            resultCode = Activity.RESULT_CANCELED
        } finally {
            originalFavourites?.let { preferences.edit().putString("favourites", it).commit() }
            activity?.let { runOnMainSync { it.finish() } }
        }
        finish(resultCode, results)
    }
}
