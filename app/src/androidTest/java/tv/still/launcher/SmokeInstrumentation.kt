package tv.still.launcher

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import tv.still.launcher.FocusMap.Pos

/** Dependency-free integration runner for Still TV 2.0. Run only on a dedicated emulator/test device.
 * It sends real key events, opens Settings and launches one installed app; it never changes HOME or packages.
 * Saved favourites and selection are restored afterwards. */
class SmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val results = Bundle()
        val checks = mutableListOf<String>()
        var resultCode = Activity.RESULT_OK
        val still = targetContext.getSharedPreferences("still", Context.MODE_PRIVATE)
        val screen = targetContext.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
        val savedFavourites = still.getString("favourites", null)
        val savedSelected = screen.getString("selected", null)
        var activity: Activity? = null
        try {
            // First run: no saved favourites, so the dock must be seeded from defaults.
            still.edit().remove("favourites").commit(); screen.edit().remove("selected").commit()
            activity = startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(targetContext.packageName,
                "tv.still.launcher.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
            fun tiles(id: Int): List<View> {
                val out = mutableListOf<View>()
                val container = current.findViewById<LinearLayout>(id)
                for (r in 0 until container.childCount) { val row = container.getChildAt(r) as LinearLayout; for (c in 0 until row.childCount) out += row.getChildAt(c) }
                return out
            }
            fun focusedPos(): Pos? { var p: Pos? = null; onUi { p = current.currentFocus?.tag as? Pos }; return p }
            fun focusedKey(): String? { var k: String? = null; onUi { k = current.currentFocus?.getTag(R.id.app_key) as? String }; return k }
            fun key(code: Int) { sendKeyDownUpSync(code); waitForIdleSync() }
            fun hold() {
                val down = SystemClock.uptimeMillis()
                sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                SystemClock.sleep(HoldGesture.HOLD_MS + 250)
                sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
            }
            fun menuLabels(): List<String> {
                val labels = mutableListOf<String>()
                fun walk(n: android.view.accessibility.AccessibilityNodeInfo?) { if (n == null) return; n.text?.let { labels += it.toString() }; for (i in 0 until n.childCount) walk(n.getChild(i)) }
                walk(uiAutomation.rootInActiveWindow); return labels
            }
            fun choose(label: String) {
                val until = SystemClock.uptimeMillis() + 5_000
                while (SystemClock.uptimeMillis() < until) {
                    val node = uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)?.firstOrNull { it.text?.toString() == label }
                    if (node != null) {
                        if (node.isFocused) { key(KeyEvent.KEYCODE_DPAD_CENTER); await("menu closed") { current.hasWindowFocus() }; return }
                        key(KeyEvent.KEYCODE_DPAD_DOWN)
                    }
                    SystemClock.sleep(80)
                }
                error("Menu action not found: $label; visible: ${menuLabels()}")
            }
            fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).use { it.readBytes() }

            // 1. Startup, default favourites, no duplicates, artwork for every card.
            await("cards rendered") { tiles(R.id.dock).isNotEmpty() && current.currentFocus?.tag is Pos }
            val dockIds = tiles(R.id.dock).map { it.getTag(R.id.app_key) as String }
            val gridIds = tiles(R.id.grid).map { it.getTag(R.id.app_key) as String }
            check(dockIds.size in 1..5) { "default dock should hold up to five apps, had ${dockIds.size}" }
            check(dockIds.intersect(gridIds.toSet()).isEmpty()) { "favourites duplicated in All Apps" }
            check(focusedPos() == Pos(1, 0)) { "initial focus ${focusedPos()}" }
            checks += "startup, default favourites, no duplicates"
            await("artwork for every card") { (tiles(R.id.dock) + tiles(R.id.grid)).all { it.findViewById<ImageView>(R.id.art).drawable != null } }
            checks += "artwork and icon fallback for every card"

            // 2. Directional focus, including the top-row Up fix.
            if (dockIds.size > 1) { key(KeyEvent.KEYCODE_DPAD_RIGHT); check(focusedPos() == Pos(1, 1)); key(KeyEvent.KEYCODE_DPAD_LEFT); check(focusedPos() == Pos(1, 0)) }
            key(KeyEvent.KEYCODE_DPAD_LEFT); check(focusedPos() == Pos(1, 0)) { "left edge must not move" }
            key(KeyEvent.KEYCODE_DPAD_UP); check(focusedPos()?.section == 0) { "Up from the top row must reach the header, was ${focusedPos()}" }
            key(KeyEvent.KEYCODE_DPAD_UP); check(focusedPos()?.section == 0)
            key(KeyEvent.KEYCODE_DPAD_DOWN); check(focusedPos() == Pos(1, 0)) { "Down from header returns to the remembered card" }
            if (gridIds.isNotEmpty()) { repeat(3) { key(KeyEvent.KEYCODE_DPAD_DOWN) }; check(focusedPos()?.section == 2) { "Down reaches All Apps" } }
            checks += "directional focus and top-row Up"

            // 3. Focus visuals: only the focused card is scaled.
            await("focus scale settles") {
                val all = tiles(R.id.dock) + tiles(R.id.grid)
                all.all { val s = it.findViewById<View>(R.id.frame).scaleX; if (it.hasFocus()) s > 1.05f else s == 1f }
            }
            checks += "focused card scaled, others at rest"

            // 4. Rapid D-pad input never strands focus.
            val random = java.util.Random(11)
            val codes = intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
            repeat(60) { sendKeyDownUpSync(codes[random.nextInt(4)]) }
            waitForIdleSync()
            onUi { val f = current.currentFocus; check(f != null && f.tag is Pos && f.isShown) { "focus lost after rapid input: $f" } }
            checks += "rapid D-pad input"

            // 5. Hold OK opens the menu; Back closes it.
            onUi { tiles(R.id.dock)[0].requestFocus() }
            val first = dockIds[0]
            hold(); await("menu open") { !current.hasWindowFocus() }
            run {
                val until = SystemClock.uptimeMillis() + 5_000
                while ("Remove from Favourites" !in menuLabels() && SystemClock.uptimeMillis() < until) SystemClock.sleep(80)
                check("Remove from Favourites" in menuLabels()) { "menu items ${menuLabels()}" }
            }
            key(KeyEvent.KEYCODE_BACK); await("menu closed by Back") { current.hasWindowFocus() }
            check(focusedKey() == first)
            checks += "hold-to-manage menu and Back"

            // 6. Unpin, pin and reorder keep focus on the same app.
            hold(); await("menu") { !current.hasWindowFocus() }; choose("Remove from Favourites")
            await("moved to All Apps") { tiles(R.id.grid).any { it.getTag(R.id.app_key) == first } && tiles(R.id.dock).none { it.getTag(R.id.app_key) == first } }
            check(focusedKey() == first && focusedPos()?.section == 2) { "focus should follow the unpinned app" }
            hold(); await("menu") { !current.hasWindowFocus() }; choose("Add to Favourites")
            await("back in dock") { tiles(R.id.dock).lastOrNull()?.getTag(R.id.app_key) == first }
            check(focusedKey() == first && focusedPos()?.section == 1)
            if (dockIds.size > 1) {
                hold(); await("menu") { !current.hasWindowFocus() }; choose("Move Left")
                await("moved left") { tiles(R.id.dock).getOrNull(dockIds.size - 2)?.getTag(R.id.app_key) == first }
                check(focusedKey() == first)
                hold(); await("menu") { !current.hasWindowFocus() }; choose("Move Right")
                await("moved right") { tiles(R.id.dock).last().getTag(R.id.app_key) == first }
            }
            checks += "unpin, pin and reorder with stable focus"

            // 7. Launch an app with a short press and return to the same card.
            onUi { tiles(R.id.dock)[0].requestFocus() }
            val launched = focusedKey()
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            await("app opened") { !current.hasWindowFocus() }
            shell("am start -W -n ${targetContext.packageName}/tv.still.launcher.MainActivity")
            await("returned") { current.hasWindowFocus() }
            check(focusedKey() == launched) { "selection restored after return: ${focusedKey()} vs $launched" }
            checks += "app launch and return with restored selection"

            // 8. Settings and Inputs from the header.
            // Up enters the header at the remembered pill; Right reaches Settings (and stays there).
            key(KeyEvent.KEYCODE_DPAD_UP); check(focusedPos()?.section == 0); key(KeyEvent.KEYCODE_DPAD_RIGHT); check(focusedKey() == "@settings")
            key(KeyEvent.KEYCODE_DPAD_CENTER); await("Settings opened") { !current.hasWindowFocus() }
            // Settings may reopen on a sub-page it remembers; press Back up to three times, as a viewer would.
            run {
                var back = false
                repeat(3) { if (!back) { shell("input keyevent KEYCODE_BACK"); SystemClock.sleep(1_500); onUi { back = current.hasWindowFocus() } } }
                await("back from Settings") { current.hasWindowFocus() }
            }
            check(focusedKey() == "@settings")
            key(KeyEvent.KEYCODE_DPAD_LEFT); check(focusedKey() == "@inputs")
            key(KeyEvent.KEYCODE_DPAD_CENTER); await("Inputs panel") { !current.hasWindowFocus() }
            key(KeyEvent.KEYCODE_BACK); await("Inputs closed") { current.hasWindowFocus() }
            checks += "Settings and Inputs actions"

            // 9. Back walks to the first card and never leaves; Home on Still TV returns to the first card.
            key(KeyEvent.KEYCODE_DPAD_DOWN); if (gridIds.isNotEmpty()) repeat(3) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
            key(KeyEvent.KEYCODE_BACK); check(focusedPos() == Pos(1, 0)) { "Back should return to the first card" }
            key(KeyEvent.KEYCODE_BACK); check(!current.isFinishing && current.hasWindowFocus())
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n ${targetContext.packageName}/tv.still.launcher.MainActivity")
            await("Home returns to the first card") { current.currentFocus?.tag == Pos(1, 0) }
            checks += "Back and Home behaviour"

            // 10. Configuration change keeps the selection.
            if (dockIds.size > 1) { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
            val before = focusedKey()
            val monitor = addMonitor(MainActivity::class.java.name, null, false)
            onUi { current.recreate() }
            current = waitForMonitorWithTimeout(monitor, 10_000) ?: error("activity not recreated")
            await("restored after recreate") { current.currentFocus?.getTag(R.id.app_key) == before }
            checks += "selection kept across configuration change"

            results.putString("stream", "\nPASS: ${checks.joinToString("; ")}\n")
            results.putInt("checks", checks.size)
        } catch (error: Throwable) {
            results.putString("stream", "\nFAIL after ${checks.joinToString()}: ${error.stackTraceToString()}\n")
            resultCode = Activity.RESULT_CANCELED
        } finally {
            still.edit().apply { if (savedFavourites == null) remove("favourites") else putString("favourites", savedFavourites) }.commit()
            screen.edit().apply { if (savedSelected == null) remove("selected") else putString("selected", savedSelected) }.commit()
            activity?.let { runOnMainSync { it.finish() } }
        }
        finish(resultCode, results)
    }
}
