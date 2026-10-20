package tv.still.launcher

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Dark rounded floating panel used for the app context menu and the inputs list.
 * Appears immediately (no window animation); Back or Cancel dismisses it. */
internal object MenuPanel {
    class Item(val label: String, val action: () -> Unit)

    fun show(activity: Activity, title: String, items: List<Item>, message: String? = null): Dialog {
        val dialog = Dialog(activity, R.style.MenuDialog)
        val view = activity.layoutInflater.inflate(R.layout.menu_panel, android.widget.FrameLayout(activity), false)
        view.findViewById<TextView>(R.id.menu_title).text = title
        view.findViewById<TextView>(R.id.menu_message).apply {
            text = message
            visibility = if (message == null) View.GONE else View.VISIBLE
        }
        val rows = view.findViewById<LinearLayout>(R.id.menu_rows)
        for (item in items) {
            val row = activity.layoutInflater.inflate(R.layout.menu_row, rows, false) as TextView
            row.text = item.label
            row.setOnClickListener { dialog.dismiss(); item.action() }
            rows.addView(row)
        }
        dialog.setContentView(view)
        dialog.window?.setWindowAnimations(0)
        dialog.show()
        rows.getChildAt(0)?.requestFocus()
        return dialog
    }
}
