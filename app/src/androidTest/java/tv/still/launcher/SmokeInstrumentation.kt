package tv.still.launcher

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray

/** Dependency-free integration runner for Still TV 2.1. Run only on a dedicated emulator/test device.
 * It sends real key events, opens Settings and launches one installed app. It never changes HOME.
 * If the emulator-only fixture apps are installed it briefly disables and re-enables one of them to
 * prove disabled apps are hidden and recover their position. Saved preferences are restored afterwards. */
class SmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val results = Bundle()
        val checks = mutableListOf<String>()
        var resultCode = Activity.RESULT_OK
        val still = targetContext.getSharedPreferences("still", Context.MODE_PRIVATE)
        val screen = targetContext.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
        val savedStill = still.all.toMap()
        val savedSelected = screen.getString("selected", null)
        var activity: Activity? = null
        var disabledFixture: String? = null
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).use { String(it.readBytes()) }
        try {
            // Migration: legacy 1.x/2.0 favourites, no 2.1 Home yet. The two last apps alphabetically, reversed.
            val installed = launchable()
            check(installed.size >= 2) { "need at least two launchable apps, found $installed" }
            val legacy = listOf(installed.last(), installed[installed.size - 2])
            still.edit().clear().putString("favourites", JSONArray(legacy).toString()).commit()
            screen.edit().remove("selected").commit()
            activity = startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(targetContext.packageName,
                "tv.still.launcher.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            var current: Activity = activity
            fun onUi(block: () -> Unit) { runOnMainSync(block); waitForIdleSync() }
            fun await(what: String, condition: () -> Boolean) {
                val until = SystemClock.uptimeMillis() + 10_000
                while (SystemClock.uptimeMillis() < until) {
                    var ok = false
                    onUi { ok = condition() }
                    if (ok) return
                    SystemClock.sleep(50)
                }
                error("Timed out waiting for: $what")
            }
            fun <T> ui(block: () -> T): T {
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block() // already inside await
                var r: T? = null; onUi { r = block() }; @Suppress("UNCHECKED_CAST") return r as T
            }
            fun tilesIn(id: Int): List<View> {
                val out = mutableListOf<View>()
                val container = current.findViewById<LinearLayout>(id) ?: return out
                for (r in 0 until container.childCount) { val row = container.getChildAt(r) as LinearLayout; for (c in 0 until row.childCount) out += row.getChildAt(c) }
                return out
            }
            fun tiles() = tilesIn(R.id.grid)
            fun keys() = ui { tiles().map { it.getTag(R.id.app_key) as String } }
            fun focusedKey(): String? = ui { current.currentFocus?.getTag(R.id.app_key) as? String }
            fun overlays(): Int = ui { current.findViewById<ViewGroup>(R.id.overlays).childCount }
            fun key(code: Int) { sendKeyDownUpSync(code); waitForIdleSync() }
            fun hold() {
                val down = SystemClock.uptimeMillis()
                sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                SystemClock.sleep(HoldGesture.HOLD_MS + 250)
                sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                waitForIdleSync()
            }
            fun textOf(view: View?): String? = when (view) {
                is TextView -> view.text?.toString()
                is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { textOf(view.getChildAt(it)) }
                else -> null
            }
            fun focusedText() = ui { textOf(current.currentFocus) }
            /** Moves down a vertical list until [label] is focused, then presses OK. */
            fun choose(label: String) {
                repeat(14) {
                    if (focusedText() == label) { key(KeyEvent.KEYCODE_DPAD_CENTER); return }
                    key(KeyEvent.KEYCODE_DPAD_DOWN)
                }
                error("Action not found: $label; focused: ${focusedText()}")
            }
            fun focusKey(target: String) { onUi { tiles().first { it.getTag(R.id.app_key) == target }.requestFocus() } }
            fun storedHome(): List<HomeItem> = HomeCodec.decode(still.getString(HomeStore.KEY_HOME, null) ?: "") ?: emptyList()

            // 1. Startup and non-destructive migration.
            await("tiles rendered") { tiles().isNotEmpty() && current.currentFocus?.getTag(R.id.app_key) != null }
            val initial = keys()
            check(initial.take(2) == legacy) { "legacy favourites must lead the grid: $initial vs $legacy" }
            check(initial.toSet() == installed.toSet() && initial.size == installed.size) { "every app exactly once: $initial vs $installed" }
            check(still.getString("favourites", null) == JSONArray(legacy).toString()) { "legacy key must be left untouched" }
            check(still.getString(HomeStore.KEY_LEGACY_BACKUP, null) != null) { "legacy favourites backed up" }
            check(focusedKey() == initial[0]) { "initial focus ${focusedKey()}" }
            checks += "migration keeps legacy order, backs it up, one tile per app"
            await("artwork for every tile") { tiles().all { it.findViewById<ImageView>(R.id.art).drawable != null } }
            if (initial.size >= 6) {
                val rows = ui { tiles().take(7).map { (it.parent as View).top } }
                check(rows.take(6).distinct().size == 1) { "six tiles across the top row" }
                if (initial.size >= 7) check(rows[6] > rows[0]) { "seventh tile starts the second row" }
            }
            checks += "artwork on every tile; six-column grid"

            // 2. Focus: edges, top-row Up reaches the header (not tile one), Down returns to the remembered tile.
            if (initial.size > 1) { key(KeyEvent.KEYCODE_DPAD_RIGHT); check(focusedKey() == initial[1]); key(KeyEvent.KEYCODE_DPAD_LEFT) }
            key(KeyEvent.KEYCODE_DPAD_LEFT); check(focusedKey() == initial[0]) { "left edge must not move" }
            key(KeyEvent.KEYCODE_DPAD_UP); check(focusedKey()?.startsWith("@") == true) { "Up from the top row reaches the header: ${focusedKey()}" }
            key(KeyEvent.KEYCODE_DPAD_UP); check(focusedKey()?.startsWith("@") == true)
            key(KeyEvent.KEYCODE_DPAD_DOWN); check(focusedKey() == initial[0]) { "Down returns to the remembered tile" }
            if (initial.size > 6) {
                key(KeyEvent.KEYCODE_DPAD_DOWN); check(focusedKey() == initial[6]) { "Down keeps the column" }
                await("hero hidden below the top row") { current.findViewById<View>(R.id.hero).alpha == 0f }
                key(KeyEvent.KEYCODE_DPAD_UP)
                await("hero shown on the top row") { current.findViewById<View>(R.id.hero).alpha == 1f }
            }
            await("only the focused tile is scaled") {
                tiles().all { val s = it.findViewById<View>(R.id.frame).scaleX; if (it.isFocused) s > 1.05f else s == 1f }
            }
            checks += "directional focus, top-row Up, hero on top row only, focus scale"

            // 3. Rapid D-pad input never strands focus.
            val random = java.util.Random(11)
            val codes = intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
            repeat(60) { sendKeyDownUpSync(codes[random.nextInt(4)]) }
            waitForIdleSync()
            onUi { val f = current.currentFocus; check(f != null && f.getTag(R.id.app_key) != null && f.isShown) { "focus lost after rapid input: $f" } }
            checks += "60 rapid D-pad keys"

            // 4. Hold OK opens one menu and never launches; Back restores focus to the same tile.
            focusKey(initial[0])
            hold(); await("menu open") { overlays() == 1 }
            check(current.hasWindowFocus()) { "hold must not launch the app" }
            check(focusedText() == "Edit Home Screen") { "menu focus ${focusedText()}" }
            key(KeyEvent.KEYCODE_BACK); await("menu closed") { overlays() == 0 }
            check(focusedKey() == initial[0])
            checks += "hold menu, no click-through, Back restores focus"

            // 5. Edit Home Screen: Back cancels without saving; OK places and saves once.
            if (initial.size > 1) {
                val before = still.getString(HomeStore.KEY_HOME, null)
                hold(); await("menu") { overlays() == 1 }; choose("Edit Home Screen")
                key(KeyEvent.KEYCODE_DPAD_RIGHT)
                check(keys()[1] == initial[0]) { "picked app moves while editing" }
                key(KeyEvent.KEYCODE_BACK)
                check(keys() == initial) { "cancel restores the order" }
                check(still.getString(HomeStore.KEY_HOME, null) == before) { "cancel saves nothing" }
                hold(); await("menu") { overlays() == 1 }; choose("Edit Home Screen")
                key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER)
                check(keys()[1] == initial[0] && focusedKey() == initial[0]) { "placed and still focused" }
                check(storedHome().map { it.key }.indexOf(initial[0]) == 1) { "saved order" }
                // Put it back.
                hold(); await("menu") { overlays() == 1 }; choose("Edit Home Screen")
                key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_CENTER)
                check(keys() == initial)
            }
            checks += "edit mode cancel and save"

            // 6. Folders: create + rename, move an app in, open/close, ungroup.
            if (initial.size > 2) {
                focusKey(initial[0])
                hold(); await("menu") { overlays() == 1 }; choose("New Folder")
                await("rename panel") { overlays() == 1 && current.findViewById<EditText>(R.id.rename_field) != null }
                // The TV keyboard opens on the field; its Done key saves, as a viewer would do.
                onUi { current.findViewById<EditText>(R.id.rename_field).apply { setText("  Test   Folder "); onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE) } }
                await("rename closed") { overlays() == 0 }
                val folder = storedHome().filterIsInstance<HomeItem.Folder>().single()
                check(folder.name == "Test Folder" && folder.apps == listOf(initial[0])) { "folder $folder" }
                check(keys()[0] == folder.key && focusedKey() == folder.key) { "folder takes the app's place and focus" }
                focusKey(initial[1])
                hold(); await("menu") { overlays() == 1 }; choose("Move to Folder…")
                await("folder chooser") { overlays() == 1 }; choose("Test Folder")
                await("moved") { overlays() == 0 && initial[1] !in keys() }
                check(focusedKey() == folder.key) { "focus follows to the folder" }
                check(storedHome().filterIsInstance<HomeItem.Folder>().single().apps == listOf(initial[0], initial[1]))
                key(KeyEvent.KEYCODE_DPAD_CENTER); await("folder open") { overlays() == 1 && tilesIn(R.id.folder_grid).size == 2 }
                check(focusedKey() == initial[0]) { "first app in the folder focused" }
                key(KeyEvent.KEYCODE_BACK); await("folder closed") { overlays() == 0 }
                check(focusedKey() == folder.key) { "Back returns focus to the folder tile" }
                hold(); await("menu") { overlays() == 1 }; choose("Remove Folder")
                await("confirm") { overlays() == 1 }; choose("Remove Folder")
                await("ungrouped") { overlays() == 0 && storedHome().none { it is HomeItem.Folder } }
                check(keys() == initial) { "apps return in place: ${keys()}" }
            }
            checks += "folder create, rename, move in, open/close, ungroup"

            // 7. Disabled apps are hidden and recover their position (fixture apps only).
            val fixture = initial.firstOrNull { it.startsWith("tv.still.fixture.") }
            if (fixture != null) {
                val at = initial.indexOf(fixture)
                disabledFixture = fixture
                shell("pm disable-user --user 0 $fixture")
                await("disabled app hidden") { fixture !in tiles().map { it.getTag(R.id.app_key) } }
                shell("pm enable $fixture"); disabledFixture = null
                await("re-enabled app restored in place") { tiles().getOrNull(at)?.getTag(R.id.app_key) == fixture }
                checks += "disabled app hidden, position recovered"
            }

            // 8. Search: header control, live case-insensitive results, Back closes.
            focusKey(initial[0]); key(KeyEvent.KEYCODE_DPAD_UP)
            repeat(2) { if (focusedKey() != HomeScene.KEY_SEARCH) key(KeyEvent.KEYCODE_DPAD_LEFT) }
            check(focusedKey() == HomeScene.KEY_SEARCH) { "search control ${focusedKey()}" }
            key(KeyEvent.KEYCODE_DPAD_CENTER); await("search open") { overlays() == 1 }
            val label = ui { tiles().first().contentDescription.toString().substringBefore('.') }
            val query = label.take(3).uppercase()
            onUi { current.findViewById<EditText>(R.id.search_field).setText(query) }
            await("results") { tilesIn(R.id.search_grid).isNotEmpty() }
            check(ui { tilesIn(R.id.search_grid).all { it.contentDescription.toString().contains(query, ignoreCase = true) } }) { "results match $query" }
            onUi { current.findViewById<EditText>(R.id.search_field).setText("zzzz-no-such-app") }
            await("empty state") { tilesIn(R.id.search_grid).isEmpty() && current.findViewById<TextView>(R.id.search_status).text.contains("No apps") }
            // Back closes the keyboard first (if showing), then search.
            repeat(2) { if (overlays() != 0) { key(KeyEvent.KEYCODE_BACK); SystemClock.sleep(300) } }
            await("search closed") { overlays() == 0 }
            check(focusedKey() == HomeScene.KEY_SEARCH)
            checks += "local search, empty state, Back"

            // 9. Controls: Inputs panel and Still TV Preferences (Large icons → five columns, then back).
            key(KeyEvent.KEYCODE_DPAD_RIGHT); check(focusedKey() == HomeScene.KEY_CONTROLS)
            key(KeyEvent.KEYCODE_DPAD_CENTER); await("controls open") { overlays() == 1 }
            choose("Inputs"); await("inputs open") { overlays() == 1 && focusedText() != "Inputs" }
            key(KeyEvent.KEYCODE_BACK); await("inputs closed") { overlays() == 0 }
            key(KeyEvent.KEYCODE_DPAD_CENTER); await("controls open") { overlays() == 1 }
            choose("Still TV Preferences"); await("preferences open") { overlays() == 1 && focusedText() == "Icon Size" }
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            await("five columns") { (tiles().firstOrNull()?.parent as? ViewGroup)?.childCount == minOf(5, tiles().size) }
            check(still.getBoolean(Appearance.KEY_LARGE, false))
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            await("six columns") { (tiles().firstOrNull()?.parent as? ViewGroup)?.childCount == minOf(6, tiles().size) }
            key(KeyEvent.KEYCODE_BACK); await("preferences closed") { overlays() == 0 }
            check(focusedKey() == HomeScene.KEY_CONTROLS)
            checks += "quick controls, inputs, preferences applied immediately"

            // 10. Launch an app with a short press and return to the same tile.
            val launchable = initial.firstOrNull { it != targetContext.packageName && !it.startsWith("tv.still.launcher") } ?: initial[0]
            focusKey(launchable)
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            await("app opened") { !current.hasWindowFocus() }
            shell("am start -W -n ${targetContext.packageName}/tv.still.launcher.MainActivity")
            await("returned") { current.hasWindowFocus() }
            await("selection restored after return") { focusedKey() == launchable }
            checks += "app launch and return with restored selection"

            // 11. Settings round trip from Controls.
            key(KeyEvent.KEYCODE_DPAD_UP); repeat(2) { if (focusedKey() != HomeScene.KEY_CONTROLS) key(KeyEvent.KEYCODE_DPAD_RIGHT) }
            key(KeyEvent.KEYCODE_DPAD_CENTER); await("controls") { overlays() == 1 }
            choose("Android TV Settings"); await("Settings opened") { !current.hasWindowFocus() }
            run {
                var back = false
                repeat(3) { if (!back) { shell("input keyevent KEYCODE_BACK"); SystemClock.sleep(1_500); back = ui { current.hasWindowFocus() } } }
                try { await("back from Settings") { current.hasWindowFocus() && current.currentFocus != null } }
                catch (e: IllegalStateException) {
                    error("${e.message}; focus=${ui { current.currentFocus }} window=${ui { current.hasWindowFocus() }} " +
                        shell("dumpsys activity activities").lines().filter { "ResumedActivity" in it }.joinToString())
                }
            }
            checks += "Android TV Settings round trip"

            // 12. Back walks to the first tile and never leaves; Home on Still TV returns to the first tile.
            focusKey(initial.last()); key(KeyEvent.KEYCODE_BACK)
            check(focusedKey() == keys()[0]) { "Back returns to the first tile" }
            key(KeyEvent.KEYCODE_BACK); check(!current.isFinishing && current.hasWindowFocus())
            // A MAIN+HOME intent is a home-type launch: only the production package (a real HOME app) receives it
            // in its existing instance. The preview deliberately is not HOME, so that check runs on production only.
            if (!targetContext.packageName.endsWith(".preview")) {
                if (initial.size > 1) key(KeyEvent.KEYCODE_DPAD_RIGHT)
                shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n ${targetContext.packageName}/tv.still.launcher.MainActivity")
                await("Home returns to the first tile") { current.currentFocus?.getTag(R.id.app_key) == keys()[0] }
                checks += "Back and Home behaviour"
            } else checks += "Back behaviour (Home key: production package only)"

            // 13. Configuration change keeps the selection.
            if (initial.size > 1) key(KeyEvent.KEYCODE_DPAD_RIGHT)
            val before = focusedKey()
            val monitor = addMonitor(MainActivity::class.java.name, null, false)
            onUi { current.recreate() }
            current = waitForMonitorWithTimeout(monitor, 10_000) ?: error("activity not recreated")
            await("restored after recreate") { current.currentFocus?.getTag(R.id.app_key) == before }
            checks += "selection kept across configuration change"

            results.putString("stream", "\nPASS (${checks.size}): ${checks.joinToString("; ")}\n")
            results.putInt("checks", checks.size)
        } catch (error: Throwable) {
            results.putString("stream", "\nFAIL after ${checks.joinToString()}: ${error.stackTraceToString()}\n")
            resultCode = Activity.RESULT_CANCELED
        } finally {
            disabledFixture?.let { shell("pm enable $it") }
            still.edit().apply {
                clear()
                for ((k, v) in savedStill) when (v) {
                    is String -> putString(k, v); is Boolean -> putBoolean(k, v); is Int -> putInt(k, v)
                    is Long -> putLong(k, v); is Float -> putFloat(k, v)
                }
            }.commit()
            screen.edit().apply { if (savedSelected == null) remove("selected") else putString("selected", savedSelected) }.commit()
            activity?.let { runOnMainSync { it.finish() } }
        }
        finish(resultCode, results)
    }

    /** The same discovery rule as AppRepository, sorted the same way. */
    private fun launchable(): List<String> {
        val pm = targetContext.packageManager
        val ids = linkedMapOf<String, String>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            @Suppress("DEPRECATION")
            for (info in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val a = info.activityInfo ?: continue
                if (a.packageName == targetContext.packageName || !a.exported || !a.enabled || !a.applicationInfo.enabled) continue
                if (a.permission != null && pm.checkPermission(a.permission, targetContext.packageName) != PackageManager.PERMISSION_GRANTED) continue
                ids.getOrPut(a.packageName) { runCatching { info.loadLabel(pm).toString() }.getOrDefault(a.packageName) }
            }
        }
        val collator = java.text.Collator.getInstance()
        return ids.entries.sortedWith { x, y -> collator.compare(x.value, y.value) }.map { it.key }
    }
}
