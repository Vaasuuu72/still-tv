package tv.still.launcher

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView

/** ScrollView that keeps the focused card (including its focus scale and label) clear of the edges,
 * and glides to it instead of jumping. D-pad keys are handled by the Activity, not by this view. */
internal class CalmScrollView(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {
    private val margin = (EDGE_MARGIN_DP * resources.displayMetrics.density).toInt()
    private var suppress = false
    var animateScroll = true

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (suppress) return 0
        return super.computeScrollDeltaToGetChildRectOnScreen(Rect(rect).apply { top -= margin; bottom += margin })
    }

    override fun requestChildFocus(child: View, focused: View) {
        if (!isLaidOut) { super.requestChildFocus(child, focused); return }
        val rect = Rect()
        focused.getDrawingRect(rect)
        offsetDescendantRectToMyCoords(focused, rect)
        val delta = computeScrollDeltaToGetChildRectOnScreen(rect)
        suppress = true
        try { super.requestChildFocus(child, focused) } finally { suppress = false }
        if (delta != 0) { if (animateScroll) smoothScrollBy(0, delta) else scrollBy(0, delta) }
    }

    private companion object { const val EDGE_MARGIN_DP = 40 }
}
