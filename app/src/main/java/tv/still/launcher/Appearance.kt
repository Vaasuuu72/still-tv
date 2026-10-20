package tv.still.launcher

import android.content.SharedPreferences

/** Still TV's own appearance preferences. These never change Android-wide settings. */
data class Appearance(
    val largeIcons: Boolean = false,
    val reduceMotion: Boolean = false,
    val topShelf: Boolean = true,
    val clock: Boolean = true,
) {
    internal class Store(private val preferences: SharedPreferences) {
        fun load() = Appearance(
            largeIcons = preferences.getBoolean(KEY_LARGE, false),
            reduceMotion = preferences.getBoolean(KEY_REDUCE_MOTION, false),
            topShelf = preferences.getBoolean(KEY_TOP_SHELF, true),
            clock = preferences.getBoolean(KEY_CLOCK, true))

        fun save(value: Appearance) {
            preferences.edit()
                .putBoolean(KEY_LARGE, value.largeIcons)
                .putBoolean(KEY_REDUCE_MOTION, value.reduceMotion)
                .putBoolean(KEY_TOP_SHELF, value.topShelf)
                .putBoolean(KEY_CLOCK, value.clock)
                .apply()
        }
    }

    companion object {
        const val KEY_LARGE = "appearance_large_icons"
        const val KEY_REDUCE_MOTION = "appearance_reduce_motion"
        const val KEY_TOP_SHELF = "appearance_top_shelf"
        const val KEY_CLOCK = "appearance_clock"
        val KEYS = listOf(KEY_LARGE, KEY_REDUCE_MOTION, KEY_TOP_SHELF, KEY_CLOCK)
    }
}
