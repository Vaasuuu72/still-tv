package tv.still.launcher

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.widget.ScrollView

/** ScrollView that never scrolls on its own initiative from key events.
 * With [follow] on (folders, search) it keeps the focused tile and its label clear of the edges.
 * With [follow] off (Home) the owner positions it explicitly via [glideTo], in whole-row steps. */
internal class CalmScrollView(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {
    private val margin = (EDGE_MARGIN_DP * resources.displayMetrics.density).toInt()
    private var suppress = false
    var animateScroll = true
    var follow = true

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (suppress || !follow) return 0
        return super.computeScrollDeltaToGetChildRectOnScreen(Rect(rect).apply { top -= margin; bottom += margin })
    }

    override fun requestChildFocus(child: View, focused: View) {
        if (!follow || !isLaidOut) { suppress = true; try { super.requestChildFocus(child, focused) } finally { suppress = false }; return }
        val rect = Rect()
        focused.getDrawingRect(rect)
        offsetDescendantRectToMyCoords(focused, rect)
        val delta = computeScrollDeltaToGetChildRectOnScreen(rect)
        suppress = true
        try { super.requestChildFocus(child, focused) } finally { suppress = false }
        if (delta != 0) { if (animateScroll) smoothScrollBy(0, delta) else scrollBy(0, delta) }
    }

    /** D-pad keys are routed by the Activity; ScrollView must not page or jump on them. */
    override fun executeKeyEvent(event: KeyEvent) = false

    fun glideTo(y: Int) {
        val target = y.coerceIn(0, maxScroll())
        if (target == scrollY) return
        if (animateScroll) smoothScrollTo(0, target) else { scrollTo(0, target) }
    }

    private fun maxScroll() = ((getChildAt(0)?.height ?: 0) - (height - paddingTop - paddingBottom)).coerceAtLeast(0)

    private companion object { const val EDGE_MARGIN_DP = 40 }
}
