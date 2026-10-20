package tv.still.launcher

/** Press-or-hold decision for the remote's OK key. The caller schedules [hold] after [HOLD_MS];
 * releasing earlier is a click. Shorter than Android's long-press timeout so management opens promptly. */
internal class HoldGesture {
    enum class Release { CLICK, NONE }
    private var pressed = false
    private var held = false
    /** Returns true only for the initial key-down, when the caller should schedule [hold].
     * A fresh press restarts even if the previous release went to another window (e.g. the dialog). */
    fun down(repeatCount: Int): Boolean {
        if (repeatCount > 0) return false
        pressed = true; held = false
        return true
    }
    /** Returns true if this press has now become a hold. */
    fun hold(): Boolean {
        if (!pressed || held) return false
        held = true
        return true
    }
    fun up(): Release {
        val result = if (pressed && !held) Release.CLICK else Release.NONE
        pressed = false; held = false
        return result
    }
    fun cancel() { pressed = false; held = false }
    companion object { const val HOLD_MS = 250L }
}
