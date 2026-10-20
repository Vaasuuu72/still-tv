package tv.still.launcher

import android.graphics.Rect
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Compact floating list panel used for the tile context menu, quick controls, inputs and confirmations.
 * Up/Down select, OK activates (the panel closes first, then the action runs), Back cancels. */
internal class MenuOverlay(
    private val title: String,
    private val items: List<Item>,
    private val message: String? = null,
    private val anchor: View? = null,
    private val placement: Placement = if (anchor == null) Placement.CENTER else Placement.BESIDE,
) : Overlay() {
    class Item(val label: String, val action: () -> Unit)
    enum class Placement { CENTER, BESIDE, TOP_END }

    private lateinit var rows: LinearLayout

    override fun createView(parent: ViewGroup): View {
        val inflater = LayoutInflater.from(parent.context)
        val layer = FrameLayout(parent.context).apply {
            setBackgroundResource(if (placement == Placement.TOP_END) android.R.color.transparent else R.color.surface_scrim)
            isClickable = true
        }
        val panel = inflater.inflate(R.layout.menu_panel, layer, false)
        panel.findViewById<TextView>(R.id.menu_title).text = title
        panel.findViewById<TextView>(R.id.menu_message).apply {
            text = message
            visibility = if (message == null) View.GONE else View.VISIBLE
        }
        rows = panel.findViewById(R.id.menu_rows)
        for (item in items) {
            val row = inflater.inflate(R.layout.menu_row, rows, false) as TextView
            row.text = item.label
            row.setOnClickListener { host.close(this); item.action() }
            rows.addView(row)
        }
        layer.addView(panel, place(panel, parent))
        return layer
    }

    override fun firstFocus(): View? = rows.getChildAt(0)

    /** Left/Right have no meaning in a list; consume them so focus cannot wander. */
    override fun onKey(event: KeyEvent) =
        event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || event.keyCode == KeyEvent.KEYCODE_MENU

    @android.annotation.SuppressLint("RtlHardcoded") // absolute screen coordinates measured from the anchor
    private fun place(panel: View, parent: ViewGroup): FrameLayout.LayoutParams {
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val safe = host.safe
        if (placement == Placement.CENTER || parent.width == 0) return params.apply { gravity = Gravity.CENTER }
        panel.measure(View.MeasureSpec.makeMeasureSpec(safe.width(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(safe.height(), View.MeasureSpec.AT_MOST))
        val w = panel.measuredWidth; val h = panel.measuredHeight
        val gap = (GAP_DP * parent.resources.displayMetrics.density).toInt()
        val target = anchorRect(parent)
        val left: Int; val top: Int
        if (placement == Placement.TOP_END || target == null) {
            left = (target?.right ?: safe.right) - w
            top = (target?.bottom ?: safe.top) + gap
        } else {
            // Beside the tile, on whichever side has room, so the selected app stays visible.
            left = if (target.right + gap + w <= safe.right) target.right + gap else target.left - gap - w
            top = target.centerY() - h / 2
        }
        return params.apply {
            gravity = Gravity.TOP or Gravity.LEFT
            leftMargin = left.coerceIn(safe.left, (safe.right - w).coerceAtLeast(safe.left))
            topMargin = top.coerceIn(safe.top, (safe.bottom - h).coerceAtLeast(safe.top))
        }
    }

    private fun anchorRect(parent: ViewGroup): Rect? {
        val view = anchor?.takeIf { it.isAttachedToWindow } ?: return null
        val target = view.findViewById<View>(R.id.frame) ?: view
        val a = IntArray(2); val p = IntArray(2)
        target.getLocationInWindow(a); parent.getLocationInWindow(p)
        val x = a[0] - p[0]; val y = a[1] - p[1]
        return Rect(x, y, x + (target.width * target.scaleX).toInt(), y + (target.height * target.scaleY).toInt())
    }

    private companion object { const val GAP_DP = 20f }
}
