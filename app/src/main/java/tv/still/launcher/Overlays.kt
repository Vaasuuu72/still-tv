package tv.still.launcher

import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** A launcher-local layer above Home (menu, folder, search, preferences…). Overlays live in the
 * Activity's own window, so every key passes through one dispatcher and focus can be fenced precisely. */
internal abstract class Overlay {
    lateinit var host: OverlayHost
    lateinit var view: View
    /** The view that had focus when this overlay opened; focus returns to it on close when still valid. */
    var returnFocus: View? = null
    /** True once [createView] has returned and the overlay is on screen. */
    val attached: Boolean get() = this::view.isInitialized

    abstract fun createView(parent: ViewGroup): View
    open fun firstFocus(): View? = null
    /** Called for every key while this overlay is on top; return true to consume. */
    open fun onKey(event: KeyEvent): Boolean = false
    /** Back released while on top (after the IME, which handles Back first while it is showing). */
    open fun onBack() { host.close(this) }
    open fun onClosed() {}
    /** A full-screen opaque overlay hides Home underneath, so the GPU does not draw both. */
    open val opaque: Boolean = false
}

internal class OverlayHost(
    private val layer: FrameLayout,
    private val home: ViewGroup,
    private val animate: () -> Boolean,
    /** Called when the last overlay closes and its return-focus view is gone. */
    private val restoreHome: () -> Unit,
) {
    private val stack = ArrayList<Overlay>()
    val top: Overlay? get() = stack.lastOrNull()
    val isOpen: Boolean get() = stack.isNotEmpty()
    /** Safe area inside the layer, in pixels, kept up to date by the Activity. */
    val safe = Rect()

    fun show(overlay: Overlay) {
        overlay.host = this
        overlay.returnFocus = layer.rootView.findFocus()
        fence(stack.lastOrNull()?.view ?: home, blocked = true)
        overlay.view = overlay.createView(layer)
        layer.addView(overlay.view)
        stack += overlay
        updateHomeVisibility()
        if (animate()) {
            overlay.view.alpha = 0f
            overlay.view.animate().alpha(1f).setDuration(HomeLayout.MODAL_MS).start()
        }
        (overlay.firstFocus() ?: overlay.view).requestFocus()
    }

    /** Closes [overlay] and anything stacked above it. */
    fun close(overlay: Overlay, restoreFocus: Boolean = true) {
        val index = stack.indexOf(overlay)
        if (index < 0) return
        while (stack.size > index) {
            val closing = stack.removeAt(stack.lastIndex)
            closing.view.animate().cancel()
            layer.removeView(closing.view)
            closing.onClosed()
        }
        val below = stack.lastOrNull()
        fence(below?.view ?: home, blocked = false)
        updateHomeVisibility()
        if (!restoreFocus) return
        val target = overlay.returnFocus?.takeIf { it.isAttachedToWindow && it.isShown }
        if (target != null && target.requestFocus()) return
        if (below != null) (below.firstFocus() ?: below.view).requestFocus() else restoreHome()
    }

    fun closeAll() { stack.firstOrNull()?.let { close(it, restoreFocus = false) }; restoreHome() }

    fun <T : Overlay> find(type: Class<T>): T? = stack.lastOrNull { type.isInstance(it) }?.let(type::cast)

    private fun updateHomeVisibility() {
        home.visibility = if (stack.any { it.opaque }) View.INVISIBLE else View.VISIBLE
    }

    private fun fence(view: View, blocked: Boolean) {
        (view as? ViewGroup)?.descendantFocusability =
            if (blocked) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
    }
}
