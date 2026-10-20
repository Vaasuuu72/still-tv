package tv.still.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.LruCache
import java.text.Collator

data class AppEntry(val id: String, val label: String, val component: ComponentName, val info: ResolveInfo)

/** Card artwork rendered once at its on-screen size with corners baked in, so focus never decodes or clips.
 * [tint] is a muted colour taken from the artwork for the Top Shelf backdrop. */
internal data class Artwork(val bitmap: Bitmap, val isBanner: Boolean, val tint: Int)

internal class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    val artworks = object : LruCache<String, Artwork>(ARTWORK_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Artwork) = value.bitmap.byteCount
    }

    /** Enabled, exported launchable activities; TV entry points take precedence and a package appears once. */
    fun discover(): List<AppEntry> {
        val apps = linkedMapOf<String, AppEntry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            @Suppress("DEPRECATION")
            val results = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            for (info in results) {
                val a = info.activityInfo ?: continue
                if (a.packageName == context.packageName || !a.exported || !a.enabled || !a.applicationInfo.enabled) continue
                if (a.permission != null && pm.checkPermission(a.permission, context.packageName) != PackageManager.PERMISSION_GRANTED) continue
                if (!apps.containsKey(a.packageName)) apps[a.packageName] = AppEntry(a.packageName,
                    runCatching { info.loadLabel(pm).toString() }.getOrDefault(a.packageName),
                    ComponentName(a.packageName, a.name), info)
            }
        }
        val collator = Collator.getInstance()
        return apps.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** Drops one package's artwork and any folder preview that includes it. */
    fun evict(packageName: String) {
        for (key in artworks.snapshot().keys) {
            val ids = key.substringBefore('@').removePrefix(HomeItem.FOLDER_PREFIX).split(',')
            if (packageName in ids) artworks.remove(key)
        }
    }

    fun cachedArtwork(id: String, width: Int, height: Int): Artwork? = artworks.get(key(id, width, height))

    fun artwork(app: AppEntry, width: Int, height: Int, radius: Float): Artwork {
        val key = key(app.id, width, height)
        artworks.get(key)?.let { return it }
        // Only a genuinely landscape banner fills the card; a square image supplied as a banner is shown as an icon.
        val banner = runCatching { app.info.activityInfo.loadBanner(pm) }.getOrNull()?.takeIf(::isLandscape)
        val content = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(content)
        canvas.drawColor(SURFACE_TILE)
        if (banner != null) {
            // Banners are 16:9; covering the 5:3 card trims ~3% from each side and never distorts the logo.
            drawScaled(canvas, banner, 0f, 0f, width.toFloat(), height.toFloat(), cover = true)
        } else {
            val icon = runCatching { app.info.loadIcon(pm) }.getOrNull() ?: pm.defaultActivityIcon
            val size = height * ICON_HEIGHT_FRACTION
            val left = (width - size) / 2f
            val top = height * ICON_TOP_FRACTION
            drawScaled(canvas, icon, left, top, left + size, top + size, cover = false)
        }
        val tint = shelfTint(content)
        val artwork = Artwork(rounded(content, radius), banner != null, tint)
        content.recycle()
        artworks.put(key, artwork)
        return artwork
    }

    /** A folder card: up to nine child artworks in a 3×3 grid on a neutral card, precomputed at card size. */
    fun cachedFolderArtwork(folder: HomeItem.Folder, apps: Map<String, AppEntry>, width: Int, height: Int): Artwork? =
        artworks.get(folderKey(folder, apps, width, height))

    fun folderArtwork(folder: HomeItem.Folder, apps: Map<String, AppEntry>, width: Int, height: Int, radius: Float): Artwork {
        val children = folder.apps.mapNotNull { apps[it] }.take(FOLDER_PREVIEW_COUNT)
        val key = folderKey(folder, apps, width, height)
        artworks.get(key)?.let { return it }
        val pad = height * FOLDER_PADDING_FRACTION
        val gap = height * FOLDER_GAP_FRACTION
        val miniHeight = (height - 2 * pad - 2 * gap) / FOLDER_GRID
        val miniWidth = miniHeight * TILE_ASPECT
        val gridWidth = FOLDER_GRID * miniWidth + (FOLDER_GRID - 1) * gap
        val startX = (width - gridWidth) / 2f
        val content = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(content)
        canvas.drawColor(SURFACE_FOLDER)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        children.forEachIndexed { index, child ->
            val mini = artwork(child, miniWidth.toInt(), miniHeight.toInt(), radius * miniHeight / height)
            val x = startX + (index % FOLDER_GRID) * (miniWidth + gap)
            val y = pad + (index / FOLDER_GRID) * (miniHeight + gap)
            canvas.drawBitmap(mini.bitmap, null, RectF(x, y, x + miniWidth, y + miniHeight), paint)
        }
        val artwork = Artwork(rounded(content, radius), isBanner = false, tint = NEUTRAL_TINT)
        content.recycle()
        artworks.put(key, artwork)
        return artwork
    }

    private fun key(id: String, width: Int, height: Int) = "$id@${width}x$height"

    private fun isLandscape(drawable: Drawable): Boolean {
        val w = drawable.intrinsicWidth; val h = drawable.intrinsicHeight
        // Without an intrinsic size the aspect is unknown; treating it as an icon avoids stretching it.
        return w > 0 && h > 0 && w.toFloat() / h >= MIN_BANNER_ASPECT
    }

    /** Folder art depends only on which apps it previews, so renaming a folder reuses it. */
    private fun folderKey(folder: HomeItem.Folder, apps: Map<String, AppEntry>, width: Int, height: Int) =
        HomeItem.FOLDER_PREFIX + folder.apps.filter { it in apps }.take(FOLDER_PREVIEW_COUNT).joinToString(",") + "@${width}x$height"

    private fun rounded(content: Bitmap, radius: Float): Bitmap {
        val result = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(result).drawRoundRect(RectF(0f, 0f, content.width.toFloat(), content.height.toFloat()), radius, radius, paint)
        return result
    }

    /** Draws [drawable] centred in the box, preserving its aspect ratio: fitted inside, or covering it. */
    private fun drawScaled(canvas: Canvas, drawable: Drawable, left: Float, top: Float, right: Float, bottom: Float, cover: Boolean) {
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: (right - left).toInt()
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: (bottom - top).toInt()
        val sx = (right - left) / w; val sy = (bottom - top) / h
        val scale = if (cover) maxOf(sx, sy) else minOf(sx, sy)
        val dw = w * scale; val dh = h * scale
        val x = left + (right - left - dw) / 2f; val y = top + (bottom - top - dh) / 2f
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        drawable.setBounds(x.toInt(), y.toInt(), (x + dw).toInt(), (y + dh).toInt())
        drawable.draw(canvas)
        canvas.restore()
    }

    companion object {
        /** First-run Home order, used only when nothing has been saved. Missing apps are skipped. */
        val DEFAULT_ORDER = listOf(
            "com.google.android.youtube.tv", "in.startv.hotstar", "com.crunchyroll.crunchyroid",
            "com.formulaone.production", "org.jellyfin.androidtv")
        private const val ARTWORK_CACHE_BYTES = 8 * 1024 * 1024
        private const val SURFACE_TILE = 0xFF262A31.toInt()   // @color/surface_tile
        private const val SURFACE_FOLDER = 0xFF2E3137.toInt() // @color/surface_folder
        private const val NEUTRAL_TINT = 0xFF2A2D33.toInt()
        private const val ICON_HEIGHT_FRACTION = 0.46f
        private const val ICON_TOP_FRACTION = 0.14f
        private const val TILE_ASPECT = 5f / 3f
        private const val MIN_BANNER_ASPECT = 1.4f
        private const val FOLDER_PREVIEW_COUNT = 9
        private const val FOLDER_GRID = 3
        private const val FOLDER_PADDING_FRACTION = 0.12f
        private const val FOLDER_GAP_FRACTION = 0.035f
        private const val TINT_SAMPLES = 12
        private const val TINT_SATURATION_MAX = 0.7f
        private const val TINT_VALUE = 0.42f

        /** Saturation-weighted average colour of a sample grid, muted to a dark backdrop tone. */
        fun shelfTint(bitmap: Bitmap): Int {
            var r = 0f; var g = 0f; var b = 0f; var weight = 0f
            val hsv = FloatArray(3)
            for (i in 0 until TINT_SAMPLES) for (j in 0 until TINT_SAMPLES) {
                val c = bitmap.getPixel((i + 0.5f).times(bitmap.width / TINT_SAMPLES.toFloat()).toInt(),
                    (j + 0.5f).times(bitmap.height / TINT_SAMPLES.toFloat()).toInt())
                Color.colorToHSV(c, hsv)
                val w = hsv[1] * hsv[2] + 0.05f
                r += Color.red(c) * w; g += Color.green(c) * w; b += Color.blue(c) * w; weight += w
            }
            Color.colorToHSV(Color.rgb((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt()), hsv)
            hsv[1] = minOf(hsv[1], TINT_SATURATION_MAX)
            hsv[2] = TINT_VALUE
            return Color.HSVToColor(hsv)
        }
    }
}
