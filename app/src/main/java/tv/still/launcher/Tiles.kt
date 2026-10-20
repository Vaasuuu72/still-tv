package tv.still.launcher

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Builds app cards and runs their focus transition. Only scale and alpha change, so a transition is
 * a few cheap property updates on the focused card and the card it left. */
internal object Tiles {
    /** Fast start, gentle critically-damped settle; no overshoot. */
    private val EASE = PathInterpolator(0.2f, 0f, 0f, 1f)

    fun create(inflater: LayoutInflater, parent: ViewGroup, app: AppEntry, widthPx: Int, heightPx: Int): View {
        val tile = inflater.inflate(R.layout.tile, parent, false)
        tile.findViewById<View>(R.id.frame).layoutParams.apply { width = widthPx; height = heightPx }
        tile.findViewById<TextView>(R.id.label).apply { layoutParams.width = widthPx; text = app.label }
        tile.findViewById<TextView>(R.id.name).text = app.label
        tile.contentDescription = app.label
        tile.setTag(R.id.app_key, app.id)
        return tile
    }

    fun bind(tile: View, artwork: Artwork) {
        tile.findViewById<ImageView>(R.id.art).setImageBitmap(artwork.bitmap)
        // Icon-only apps show their name inside the card; banners already carry the app's branding.
        tile.findViewById<View>(R.id.name).visibility = if (artwork.isBanner) View.GONE else View.VISIBLE
        tile.findViewById<View>(R.id.label).visibility = if (artwork.isBanner) View.VISIBLE else View.INVISIBLE
    }

    fun placeholder(tile: View, bitmap: Bitmap?) {
        tile.findViewById<ImageView>(R.id.art).setImageBitmap(bitmap)
        tile.findViewById<View>(R.id.name).visibility = View.VISIBLE
    }

    fun focus(tile: View, focused: Boolean, animate: Boolean) {
        val frame = tile.findViewById<View>(R.id.frame) ?: return // header pills have no card
        val scale = if (focused) HomeLayout.FOCUS_SCALE else 1f
        val lift = if (focused) 1f else 0f
        val labelShift = if (focused) frame.height * (HomeLayout.FOCUS_SCALE - 1f) / 2f else 0f
        val parts = listOf(frame, tile.findViewById(R.id.shadow), tile.findViewById(R.id.glow), tile.findViewById<View>(R.id.label))
        if (!animate) {
            parts.forEach { it.animate().cancel() }
            frame.scaleX = scale; frame.scaleY = scale
            parts[1].alpha = lift; parts[2].alpha = lift; parts[3].alpha = lift; parts[3].translationY = labelShift
            return
        }
        // ViewPropertyAnimator starts from the current on-screen values, so rapid moves retarget smoothly.
        frame.animate().scaleX(scale).scaleY(scale).setDuration(HomeLayout.FOCUS_MS).setInterpolator(EASE).start()
        parts[1].animate().alpha(lift).setDuration(HomeLayout.FOCUS_MS).setInterpolator(EASE).start()
        parts[2].animate().alpha(lift).setDuration(HomeLayout.FOCUS_MS).setInterpolator(EASE).start()
        parts[3].animate().alpha(lift).translationY(labelShift).setDuration(HomeLayout.FOCUS_MS).setInterpolator(EASE).start()
    }

    fun row(parent: ViewGroup): LinearLayout = LinearLayout(parent.context).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }
}
