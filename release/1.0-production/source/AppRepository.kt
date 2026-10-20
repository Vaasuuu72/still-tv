package tv.still.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import org.json.JSONArray
import java.text.Collator

internal data class AppEntry(val id: String, val label: String, val component: ComponentName, val info: ResolveInfo)

internal class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val preferences = context.getSharedPreferences("still", Context.MODE_PRIVATE)
    val icons = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun discover(): List<AppEntry> {
        val apps = linkedMapOf<String, AppEntry>()
        // TV entry points take precedence. A package appears only once.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            @Suppress("DEPRECATION")
            val results = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            for (info in results) {
                val a = info.activityInfo ?: continue
                if (a.packageName == context.packageName || !a.exported || !a.enabled || !a.applicationInfo.enabled) continue
                if (a.permission != null && pm.checkPermission(a.permission, context.packageName) != android.content.pm.PackageManager.PERMISSION_GRANTED) continue
                if (!apps.containsKey(a.packageName)) apps[a.packageName] = AppEntry(a.packageName,
                    runCatching { info.loadLabel(pm).toString() }.getOrDefault(a.packageName),
                    ComponentName(a.packageName, a.name), info)
            }
        }
        val collator = Collator.getInstance()
        return apps.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
    fun icon(app: AppEntry): Bitmap {
        icons.get(app.id)?.let { return it }
        val drawable = runCatching { app.info.loadIcon(pm) }.getOrNull() ?: pm.defaultActivityIcon
        val size = (68 * context.resources.displayMetrics.density).toInt().coerceIn(64, 160)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val scale = size.toFloat() / maxOf(w, h)
        val dw = (w * scale).toInt(); val dh = (h * scale).toInt()
        drawable.setBounds((size - dw) / 2, (size - dh) / 2, (size + dw) / 2, (size + dh) / 2)
        drawable.draw(Canvas(bitmap))
        icons.put(app.id, bitmap)
        return bitmap
    }
    fun favourites(): List<String> = runCatching {
        val data = JSONArray(preferences.getString("favourites", "[]"))
        (0 until data.length()).map { data.getString(it) }.distinct()
    }.getOrDefault(emptyList())
    fun save(ids: List<String>) { preferences.edit().putString("favourites", JSONArray(ids.distinct()).toString()).apply() }
    fun initialise(apps: List<AppEntry>) {
        if (!preferences.contains("favourites")) save(apps.take(7).map { it.id })
    }
}
