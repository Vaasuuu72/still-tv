package tv.still.launcher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView

/** Static, app-aware Top Shelf fallback (UI-02). It shows only what the installed app itself provides:
 * its name and a muted colour wash taken from its own artwork. No timers, network, posters or idle drawing;
 * a focus change updates two fixed-size text views and one gradient. */
internal class TopShelf(
    backdrop: View,
    private val title: TextView,
    private val subtitle: TextView,
) {
    private val wash = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT))
    private var colour = NEUTRAL
    private var animator: ValueAnimator? = null
    var enabled = true

    init { backdrop.background = wash; paint(NEUTRAL) }

    fun show(heading: CharSequence, detail: CharSequence, tint: Int?, animate: Boolean) {
        if (title.text != heading) title.text = heading
        if (subtitle.text != detail) subtitle.text = detail
        tintTo(if (enabled) tint ?: NEUTRAL else Color.TRANSPARENT, animate)
    }

    fun clear() { show("", "", null, animate = false) }

    private fun tintTo(target: Int, animate: Boolean) {
        animator?.cancel()
        if (target == colour) return
        if (!animate) { paint(target); return }
        animator = ValueAnimator.ofObject(ArgbEvaluator(), colour, target).apply {
            duration = HomeLayout.FOCUS_MS
            addUpdateListener { paint(it.animatedValue as Int) }
            start()
        }
    }

    private fun paint(top: Int) {
        colour = top
        val faded = Color.argb(WASH_ALPHA * Color.alpha(top) / 255, Color.red(top), Color.green(top), Color.blue(top))
        wash.colors = intArrayOf(faded, Color.argb(0, Color.red(top), Color.green(top), Color.blue(top)))
    }

    private companion object {
        const val NEUTRAL = 0xFF2A2D33.toInt()
        const val WASH_ALPHA = 200
    }
}
