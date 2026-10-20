package tv.still.launcher

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Still TV Preferences (UI-09): tvOS-style list on the right, description of the focused row on the left.
 * OK (or Left/Right on a value) changes it immediately and persistently; Back returns to Home.
 * Android-wide settings are never changed here; the last rows hand off to Android TV Settings. */
internal class PreferencesOverlay(private val launcher: Launcher) : Overlay() {
    private class Row(val label: Int, val description: () -> String, val value: (() -> String)?, val change: (step: Int) -> Unit)

    private lateinit var rows: LinearLayout
    private lateinit var description: TextView
    private val rowViews = mutableListOf<Pair<Row, View>>()

    override fun createView(parent: ViewGroup): View {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.preferences_overlay, parent, false)
        val g = launcher.geometry
        val side = launcher.px(g.sideMarginDp)
        view.setPadding(side, launcher.px(g.topMarginDp), side, launcher.px(g.topMarginDp))
        view.findViewById<View>(R.id.prefs_detail).setPadding(0, 0, side, 0)
        description = view.findViewById(R.id.prefs_description)
        rows = view.findViewById(R.id.prefs_rows)
        val res = parent.resources
        val on = res.getString(R.string.on); val off = res.getString(R.string.off)
        val definitions = listOf(
            Row(R.string.pref_icon_size, { res.getString(R.string.pref_icon_size_description) },
                { res.getString(if (launcher.appearance.largeIcons) R.string.pref_icon_large else R.string.pref_icon_default) },
                { launcher.setAppearance(launcher.appearance.copy(largeIcons = !launcher.appearance.largeIcons)) }),
            Row(R.string.pref_top_shelf, { res.getString(R.string.pref_top_shelf_description) },
                { if (launcher.appearance.topShelf) on else off },
                { launcher.setAppearance(launcher.appearance.copy(topShelf = !launcher.appearance.topShelf)) }),
            Row(R.string.pref_clock, { res.getString(R.string.pref_clock_description) },
                { if (launcher.appearance.clock) on else off },
                { launcher.setAppearance(launcher.appearance.copy(clock = !launcher.appearance.clock)) }),
            Row(R.string.pref_reduce_motion, { res.getString(R.string.pref_reduce_motion_description) },
                { if (launcher.appearance.reduceMotion) on else off },
                { launcher.setAppearance(launcher.appearance.copy(reduceMotion = !launcher.appearance.reduceMotion)) }),
            Row(R.string.pref_reset, { res.getString(R.string.pref_reset_description) }, null, { confirmReset() }),
            Row(R.string.pref_system, { res.getString(R.string.pref_system_description) }, null, { launcher.openSystemSettings() }),
            Row(R.string.pref_about, { res.getString(R.string.pref_about_description, launcher.versionName()) }, null, {}))
        for (row in definitions) {
            val item = inflater.inflate(R.layout.pref_row, rows, false)
            item.findViewById<TextView>(R.id.pref_label).setText(row.label)
            item.setOnClickListener { row.change(1); bindValues() }
            item.setOnFocusChangeListener { _, has -> if (has) description.text = row.description() }
            rows.addView(item)
            rowViews += row to item
        }
        bindValues()
        return view
    }

    override fun firstFocus(): View? = rows.getChildAt(0)
    override val opaque = true

    override fun onKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return code == KeyEvent.KEYCODE_MENU
        val focused = rowViews.firstOrNull { it.second.hasFocus() }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && focused?.first?.value != null) {
            focused.first.change(if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
            bindValues()
        }
        return true
    }

    private fun bindValues() {
        for ((row, view) in rowViews) {
            view.findViewById<TextView>(R.id.pref_value).text = row.value?.invoke() ?: if (row.label == R.string.pref_about) "" else "›"
            view.contentDescription = listOfNotNull(view.resources.getString(row.label), row.value?.invoke()).joinToString(", ")
        }
    }

    private fun confirmReset() {
        val res = view.resources
        host.show(MenuOverlay(res.getString(R.string.pref_reset_confirm), listOf(
            MenuOverlay.Item(res.getString(R.string.pref_reset_button)) { launcher.resetHome() },
            MenuOverlay.Item(res.getString(R.string.cancel)) {}), message = res.getString(R.string.pref_reset_description)))
    }
}
