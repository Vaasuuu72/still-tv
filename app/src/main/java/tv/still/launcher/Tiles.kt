package tv.still.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Builds Home tiles and runs their state transitions. Only scale, translation and alpha change, so a
 * focus move is a few property updates on two tiles; artwork is never re-bound because focus moved. */
internal object Tiles {
    /** Fast start, gentle settle without overshoot. */
    private val EASE = PathInterpolator(0.2f, 0f, 0f, 1f)
    private const val PRESSED_SCALE = 1.0f
    private const val PRESS_MS = 70L
    private const val DIMMED_ALPHA = 0.5f

    enum class State { REST, FOCUSED, PICKED }

    fun create(inflater: LayoutInflater, parent: ViewGroup, widthPx: Int, heightPx: Int): View {
        val tile = inflater.inflate(R.layout.tile, parent, false)
        tile.findViewById<View>(R.id.frame).layoutParams.apply { width = widthPx; height = heightPx }
        tile.findViewById<View>(R.id.label).layoutParams.width = widthPx
        tile.setTag(R.id.tile_size, widthPx to heightPx)
        return tile
    }

    fun sizeOf(tile: View) = tile.getTag(R.id.tile_size)

    /** Sets the identity of a tile; [artwork] may be null while it loads (the name is shown meanwhile). */
    fun bind(tile: View, key: String, label: String, description: String, artwork: Artwork?, keepCurrent: Boolean = false) {
        tile.setTag(R.id.app_key, key)
        tile.contentDescription = description
        tile.findViewById<TextView>(R.id.label).text = label
        tile.findViewById<TextView>(R.id.name).text = label
        if (artwork != null || !keepCurrent) bindArtwork(tile, artwork, isFolder = key.startsWith(HomeItem.FOLDER_PREFIX))
    }

    fun bindArtwork(tile: View, artwork: Artwork?, isFolder: Boolean) {
        val art = tile.findViewById<ImageView>(R.id.art)
        if (art.getTag(R.id.app_key) !== artwork) {
            art.setImageBitmap(artwork?.bitmap)
            art.setTag(R.id.app_key, artwork)
        }
        // Icon-only apps carry their name inside the card; banners and folders already identify themselves.
        tile.findViewById<View>(R.id.name).visibility =
            if (artwork == null || (!artwork.isBanner && !isFolder)) View.VISIBLE else View.GONE
    }

    fun setState(tile: View, state: State, animate: Boolean) {
        val frame = tile.findViewById<View>(R.id.frame) ?: return
        val density = tile.resources.displayMetrics.density
        val scale = when (state) { State.REST -> 1f; State.FOCUSED -> HomeLayout.FOCUS_SCALE; State.PICKED -> HomeLayout.PICKED_SCALE }
        val lifted = state != State.REST
        val lift = if (lifted) -HomeLayout.FOCUS_LIFT_DP * density else 0f
        val glow = tile.findViewById<View>(R.id.glow)
        glow.setBackgroundResource(if (state == State.PICKED) R.drawable.card_picked else R.drawable.card_glow)
        val label = tile.findViewById<View>(R.id.label)
        val labelShift = frame.height * (scale - 1f) / 2f + lift
        val visible = if (lifted) 1f else 0f
        val shadow = tile.findViewById<View>(R.id.shadow)
        // An icon-only card names itself inside; when focused the name moves below instead of appearing twice.
        val name = tile.findViewById<View>(R.id.name)
        val nameAlpha = 1f - visible
        if (!animate) {
            listOf(frame, shadow, glow, label, name).forEach { it.animate().cancel() }
            frame.scaleX = scale; frame.scaleY = scale; frame.translationY = lift
            shadow.alpha = visible; glow.alpha = visible; label.alpha = visible; label.translationY = labelShift
            name.alpha = nameAlpha
            return
        }
        name.animate().alpha(nameAlpha).setDuration(HomeLayout.FOCUS_MS).setInterpolator(EASE).start()
        // ViewPropertyAnimator starts from current on-screen values, so rapid moves retarget without jumps.
        frame.animate().scaleX(scale).scaleY(scale).translationY(lift).setDuration(HomeLayout.FOCUS_MS).setInterpolator(EASE).start()
        shadow.animate().alpha(visible).setDuration(HomeLayout.FOCUS_MS).setInterpolator(EASE).start()
        glow.animate().alpha(visible).setDuration(HomeLayout.FOCUS_MS).setInterpolator(EASE).start()
        label.animate().alpha(visible).translationY(labelShift).setDuration(HomeLayout.FOCUS_MS).setInterpolator(EASE).start()
    }

    /** Brief tactile response on OK down; released by [setState]. Launch is never delayed by it. */
    fun press(tile: View, animate: Boolean) {
        val frame = tile.findViewById<View>(R.id.frame) ?: return
        if (!animate) return
        frame.animate().scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE).setDuration(PRESS_MS).setInterpolator(EASE).start()
    }

    /** Edit mode dims every tile except the one being moved. */
    fun dim(tile: View, dimmed: Boolean) {
        tile.findViewById<View>(R.id.frame)?.alpha = if (dimmed) DIMMED_ALPHA else 1f
    }

    fun row(parent: ViewGroup): LinearLayout = LinearLayout(parent.context).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }
}
