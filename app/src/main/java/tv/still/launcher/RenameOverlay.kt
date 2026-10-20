package tv.still.launcher

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Folder name entry using the Android TV keyboard. Save applies a cleaned name; Cancel/Back keep the old one. */
internal class RenameOverlay(private val current: String, private val onSave: (String) -> Unit) : Overlay() {
    private lateinit var field: EditText

    override fun createView(parent: ViewGroup): View {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.rename_overlay, parent, false)
        field = view.findViewById(R.id.rename_field)
        field.setText(current)
        field.setSelection(field.text.length)
        field.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) { save(); true } else false }
        val rows = view.findViewById<LinearLayout>(R.id.rename_rows)
        for ((label, action) in listOf(R.string.save to ::save, R.string.cancel to { host.close(this) })) {
            val row = inflater.inflate(R.layout.menu_row, rows, false) as TextView
            row.setText(label)
            row.setOnClickListener { action() }
            rows.addView(row)
        }
        return view
    }

    override fun firstFocus(): View = field

    override fun onKey(event: KeyEvent) =
        field.hasFocus() && (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) // nothing above the field

    private fun save() {
        val name = HomeModel.cleanName(field.text.toString(), fallback = current)
        host.close(this)
        onSave(name)
    }
}
