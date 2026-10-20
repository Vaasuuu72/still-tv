package tv.still.launcher

import android.content.SharedPreferences

/** Persists the Home list in the existing "still" preferences under [KEY_HOME].
 *
 * Migration is non-destructive: the 1.x/2.0 `favourites` key is copied to [KEY_LEGACY_BACKUP] and
 * left untouched, so reinstalling an older Still TV still finds its favourites. A corrupt Home value is
 * preserved under [KEY_CORRUPT] before reseeding. Writes happen only on first run, when apps are
 * installed, or after a user edit — never on focus changes. */
internal class HomeStore(private val preferences: SharedPreferences, private val preferred: List<String>) {
    /** Result of reading storage against the installed apps; [items] is the full list, hidden apps included. */
    data class Resolution(val items: List<HomeItem>, val changed: Boolean, val corrupt: Boolean)

    fun load(installed: List<String>): List<HomeItem> {
        val stored = preferences.getString(KEY_HOME, null)
        val legacy = preferences.getString(KEY_LEGACY, null)
        val resolution = resolve(stored, legacy, installed, preferred)
        if (resolution.corrupt || resolution.changed) {
            preferences.edit().apply {
                if (resolution.corrupt && stored != null) putString(KEY_CORRUPT, stored)
                if (legacy != null && !preferences.contains(KEY_LEGACY_BACKUP)) putString(KEY_LEGACY_BACKUP, legacy)
                putString(KEY_HOME, HomeCodec.encode(resolution.items))
            }.apply()
        }
        return resolution.items
    }

    fun save(items: List<HomeItem>) {
        preferences.edit().putString(KEY_HOME, HomeCodec.encode(items)).apply()
    }

    /** Removes the saved Home order and folders; the next load reseeds from defaults (not legacy favourites). */
    fun reset(installed: List<String>): List<HomeItem> {
        val items = HomeModel.seed(installed, legacyFavourites = null, preferred = preferred)
        save(items)
        return items
    }

    companion object {
        const val KEY_HOME = "home_v2"
        const val KEY_LEGACY = "favourites"
        const val KEY_LEGACY_BACKUP = "favourites_backup_v1"
        const val KEY_CORRUPT = "home_v2_corrupt"

        fun resolve(stored: String?, legacy: String?, installed: List<String>, preferred: List<String>): Resolution {
            val decoded = stored?.let(HomeCodec::decode)
            if (decoded != null) {
                val reconciled = HomeModel.reconcile(decoded, installed)
                return Resolution(reconciled, changed = reconciled != decoded, corrupt = false)
            }
            val seeded = HomeModel.seed(installed, legacy?.let(HomeCodec::decodeLegacy), preferred)
            return Resolution(seeded, changed = true, corrupt = stored != null)
        }
    }
}
