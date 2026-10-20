package tv.still.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.LruCache
import org.json.JSONArray
import java.text.Collator

internal data class AppEntry(val id: String, val label: String, val component: ComponentName, val info: ResolveInfo)

/** Card artwork rendered once at its on-screen size, corners already rounded, so focus changes never decode or clip. */
internal data class Artwork(val bitmap: Bitmap, val isBanner: Boolean)

internal class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val preferences = context.getSharedPreferences("still", Context.MODE_PRIVATE)
    val artworks = object : LruCache<String, Artwork>(ARTWORK_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Artwork) = value.bitmap.byteCount
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

    fun artwork(app: AppEntry, width: Int, height: Int, radius: Float): Artwork {
        val key = "${app.id}@${width}x$height"
        artworks.get(key)?.let { return it }
        val banner = runCatching { app.info.activityInfo.loadBanner(pm) }.getOrNull()
        val content = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(content)
        canvas.drawColor(CARD_SURFACE)
        if (banner != null) {
            drawFitted(canvas, banner, 0f, 0f, width.toFloat(), height.toFloat())
        } else {
            val icon = runCatching { app.info.loadIcon(pm) }.getOrNull() ?: pm.defaultActivityIcon
            val size = height * ICON_HEIGHT_FRACTION
            val left = (width - size) / 2f
            val top = height * ICON_TOP_FRACTION
            drawFitted(canvas, icon, left, top, left + size, top + size)
        }
        val rounded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(rounded).drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        content.recycle()
        return Artwork(rounded, banner != null).also { artworks.put(key, it) }
    }

    /** Draws [drawable] centred inside the box, preserving its aspect ratio (never stretched). */
    private fun drawFitted(canvas: Canvas, drawable: Drawable, left: Float, top: Float, right: Float, bottom: Float) {
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: (right - left).toInt()
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: (bottom - top).toInt()
        val scale = minOf((right - left) / w, (bottom - top) / h)
        val dw = w * scale; val dh = h * scale
        val x = left + (right - left - dw) / 2f; val y = top + (bottom - top - dh) / 2f
        drawable.setBounds(x.toInt(), y.toInt(), (x + dw).toInt(), (y + dh).toInt())
        drawable.draw(canvas)
    }

    fun favourites(): List<String> = runCatching {
        val data = JSONArray(preferences.getString("favourites", "[]"))
        (0 until data.length()).map { data.getString(it) }.distinct()
    }.getOrDefault(emptyList())
    fun save(ids: List<String>) { preferences.edit().putString("favourites", JSONArray(ids.distinct()).toString()).apply() }
    fun initialise(apps: List<AppEntry>) {
        if (!preferences.contains("favourites")) save(FavouriteOrder.defaults(apps.map { it.id }, DEFAULT_FAVOURITES, DEFAULT_FALLBACK))
    }

    companion object {
        /** First-run dock, used only when no favourites have been saved. Missing apps are skipped. */
        val DEFAULT_FAVOURITES = listOf(
            "com.google.android.youtube.tv", "in.startv.hotstar", "com.crunchyroll.crunchyroid",
            "com.formulaone.production", "org.jellyfin.androidtv")
        private const val DEFAULT_FALLBACK = 5
        private const val ARTWORK_CACHE_BYTES = 8 * 1024 * 1024
        private const val CARD_SURFACE = 0xFF262A31.toInt() // @color/charcoal_raised
        private const val ICON_HEIGHT_FRACTION = 0.46f
        private const val ICON_TOP_FRACTION = 0.14f
    }
}
