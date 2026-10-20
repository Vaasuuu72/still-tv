package tv.still.launcher

import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import tv.still.launcher.FocusMap.Direction
import tv.still.launcher.FocusMap.Pos
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var repository: AppRepository
    private lateinit var geometry: HomeLayout
    private lateinit var scroll: CalmScrollView
    private lateinit var dock: LinearLayout
    private lateinit var grid: LinearLayout
    private lateinit var headerItems: List<View>
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val press = HoldGesture()
    private var apps = emptyList<AppEntry>()
    private var favourites = emptyList<AppEntry>()
    private val sectionViews = List(3) { mutableListOf<View>() }
    private val remembered = intArrayOf(-1, -1, -1)
    private var selected = ""
    private var generation = 0
    private var loaded = false
    private var rebuilding = false
    private var active = false
    private var animations = true
    private var dialog: Dialog? = null
    private var holdTarget: AppEntry? = null
    private val holdToManage = Runnable { if (press.hold()) holdTarget?.let { manage(it) } }
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action in listOf(Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) updateClock()
            else { repository.artworks.evictAll(); refresh() }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(R.layout.activity_main)
        if (Build.VERSION.SDK_INT >= 28) findViewById<View>(R.id.title).isAccessibilityHeading = true
        repository = AppRepository(applicationContext)
        geometry = HomeLayout.compute(resources.configuration.screenWidthDp, resources.configuration.screenHeightDp)
        scroll = findViewById(R.id.scroll)
        dock = findViewById(R.id.dock)
        grid = findViewById(R.id.grid)
        val side = dp(geometry.sideMarginDp)
        findViewById<View>(R.id.header).apply { setPadding(side, dp(geometry.topMarginDp) - dp(8f), side, 0) }
        findViewById<View>(R.id.content).apply { setPadding(side, paddingTop, side, paddingBottom) }
        headerItems = listOf(findViewById(R.id.inputs), findViewById(R.id.settings))
        headerItems.forEachIndexed { index, view ->
            view.tag = Pos(HEADER, index)
            view.setTag(R.id.app_key, if (index == 0) KEY_INPUTS else KEY_SETTINGS)
            view.setOnFocusChangeListener { v, has -> if (has) remember(v) }
        }
        sectionViews[HEADER].addAll(headerItems)
        findViewById<View>(R.id.settings).setOnClickListener { open(Intent(Settings.ACTION_SETTINGS)) }
        findViewById<View>(R.id.inputs).setOnClickListener { inputs() }
        selected = state?.getString(STATE_SELECTED) ?: getPreferences(MODE_PRIVATE).getString(STATE_SELECTED, "").orEmpty()
        findViewById<TextView>(R.id.hint).setText(R.string.loading)
    }

    override fun onStart() {
        super.onStart(); active = true
        val packages = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        }
        val time = IntentFilter().apply { addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(changes, packages, RECEIVER_EXPORTED)
            registerReceiver(changes, time, RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION") registerReceiver(changes, packages)
            @Suppress("DEPRECATION") registerReceiver(changes, time)
        }
        refresh()
    }
    override fun onResume() {
        super.onResume()
        animations = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        scroll.animateScroll = animations
        updateClock()
    }
    override fun onPause() {
        getPreferences(MODE_PRIVATE).edit().putString(STATE_SELECTED, selected).apply()
        super.onPause()
    }
    override fun onStop() {
        handler.removeCallbacks(holdToManage); press.cancel()
        active = false; generation++
        unregisterReceiver(changes)
        dialog?.dismiss(); dialog = null
        super.onStop()
    }
    override fun onDestroy() { worker.shutdownNow(); repository.artworks.evictAll(); super.onDestroy() }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_RUNNING_LOW) repository.artworks.evictAll()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString(STATE_SELECTED, selected); super.onSaveInstanceState(outState) }

    /** Home pressed while already on Still TV returns to the first favourite; returning from an app keeps the selection. */
    override fun onNewIntent(intent: Intent) {
        val alreadyHome = hasWindowFocus()
        super.onNewIntent(intent); setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) && alreadyHome) {
            dialog?.dismiss()
            first()?.let { scroll.scrollTo(0, 0); it.requestFocus() }
        }
    }

    /** Back never leaves the launcher: it walks focus back to the first card. */
    @Deprecated("Home deliberately retains its task")
    override fun onBackPressed() {
        val target = first() ?: return
        if (currentFocus != target) { scroll.smoothScrollTo(0, 0); target.requestFocus() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val pos = currentFocus?.tag as? Pos ?: return super.dispatchKeyEvent(event)
        direction(event.keyCode)?.let { dir ->
            if (event.action == KeyEvent.ACTION_DOWN) move(pos, dir)
            return true
        }
        if (pos.section != HEADER && isSelect(event.keyCode)) return select(event, pos)
        if (pos.section != HEADER && event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) appAt(pos)?.let { manage(it) }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun move(pos: Pos, dir: Direction) {
        val map = FocusMap(listOf(
            FocusMap.Section(sectionViews[HEADER].size, sectionViews[HEADER].size),
            FocusMap.Section(sectionViews[DOCK].size, geometry.dockColumns),
            FocusMap.Section(sectionViews[GRID].size, geometry.gridColumns)))
        var target = map.move(pos, dir)
        if (target.section != pos.section) target = map.move(pos, dir, remembered[target.section])
        sectionViews.getOrNull(target.section)?.getOrNull(target.index)?.requestFocus()
    }

    /** OK/Enter on a card: release before [HoldGesture.HOLD_MS] opens the app, holding opens the menu. */
    private fun select(event: KeyEvent, pos: Pos): Boolean {
        val app = appAt(pos) ?: return true
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (press.down(event.repeatCount)) {
                holdTarget = app
                handler.postDelayed(holdToManage, HoldGesture.HOLD_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(holdToManage)
                if (press.up() == HoldGesture.Release.CLICK) launch(app)
            }
        }
        return true
    }

    private fun refresh() {
        val request = ++generation
        worker.execute {
            val result = runCatching { repository.discover() }
            runOnUiThread {
                if (!active || isDestroyed || request != generation) return@runOnUiThread
                result.onSuccess { next ->
                    val changed = !loaded || apps.map { it.id to it.component } != next.map { it.id to it.component } || apps.map { it.label } != next.map { it.label }
                    apps = next; repository.initialise(apps)
                    if (changed) render()
                    loaded = true
                }.onFailure {
                    if (!loaded) render()
                    findViewById<TextView>(R.id.hint).setText(R.string.load_error)
                }
            }
        }
    }

    private fun render() {
        val byId = apps.associateBy { it.id }
        val favouriteIds = FavouriteOrder.visible(repository.favourites(), byId.keys)
        favourites = favouriteIds.mapNotNull { byId[it] }
        val rest = FavouriteOrder.rest(apps.map { it.id }, favouriteIds).mapNotNull { byId[it] }
        // Removing the focused card makes the window focus a pill; that must not overwrite the selection.
        rebuilding = true
        try {
            build(dock, favourites, geometry.dockColumns, geometry.dockGapDp, geometry.dockCardWidthDp, geometry.dockCardHeightDp, DOCK)
            build(grid, rest, geometry.gridColumns, geometry.gridGapDp, geometry.gridCardWidthDp, geometry.gridCardHeightDp, GRID)
        } finally { rebuilding = false }
        findViewById<View>(R.id.caption).visibility = if (rest.isEmpty() && favourites.isNotEmpty()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.hint).setText(when {
            apps.isEmpty() -> R.string.no_apps
            favourites.isEmpty() -> R.string.hint_no_favourites
            else -> R.string.hint
        })
        restoreFocus()
    }

    private fun build(container: LinearLayout, entries: List<AppEntry>, columns: Int, gapDp: Float, widthDp: Float, heightDp: Float, section: Int) {
        container.removeAllViews()
        sectionViews[section].clear()
        val width = dp(widthDp); val height = dp(heightDp)
        val radius = HomeLayout.CORNER_DP * resources.displayMetrics.density
        entries.chunked(columns).forEachIndexed { rowIndex, rowEntries ->
            val row = Tiles.row(container)
            rowEntries.forEachIndexed { col, app ->
                val tile = Tiles.create(layoutInflater, row, app, width, height)
                val index = rowIndex * columns + col
                tile.tag = Pos(section, index)
                tile.setOnFocusChangeListener { v, has -> Tiles.focus(v, has, animations && v.isLaidOut); if (has) remember(v) }
                row.addView(tile, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { if (col < columns - 1) marginEnd = dp(gapDp) })
                sectionViews[section].add(tile)
                loadArtwork(tile, app, width, height, radius)
            }
            container.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(ROW_GAP_DP) })
        }
    }

    private fun loadArtwork(tile: View, app: AppEntry, width: Int, height: Int, radius: Float) {
        repository.artworks.get("${app.id}@${width}x$height")?.let { Tiles.bind(tile, it); return }
        Tiles.placeholder(tile, null)
        val request = generation
        worker.execute {
            val artwork = runCatching { repository.artwork(app, width, height, radius) }.getOrNull() ?: return@execute
            runOnUiThread { if (!isDestroyed && request == generation && tile.getTag(R.id.app_key) == app.id) Tiles.bind(tile, artwork) }
        }
    }

    private fun remember(view: View) {
        // The window focuses the first pill before any card exists; that is not a user choice.
        if (rebuilding || (!loaded && (view.tag as? Pos)?.section == HEADER)) return
        val pos = view.tag as? Pos ?: return
        remembered[pos.section] = pos.index
        selected = view.getTag(R.id.app_key) as? String ?: selected
    }

    private fun restoreFocus() {
        val target = sectionViews.flatten().firstOrNull { it.getTag(R.id.app_key) == selected } ?: first() ?: headerItems.last()
        if (!target.hasFocus()) target.requestFocus()
        else if ((target.tag as? Pos)?.section != HEADER) Tiles.focus(target, true, false)
    }

    private fun first(): View? = sectionViews[DOCK].firstOrNull() ?: sectionViews[GRID].firstOrNull()
    private fun appAt(pos: Pos): AppEntry? = (sectionViews.getOrNull(pos.section)?.getOrNull(pos.index)?.getTag(R.id.app_key) as? String)
        ?.let { id -> apps.firstOrNull { it.id == id } }

    private fun updateClock() { findViewById<TextView>(R.id.clock).text = DateFormat.getTimeFormat(this).format(Date()) }

    private fun launch(app: AppEntry) { if (!open(Intent.makeMainActivity(app.component))) refresh() }
    private fun open(intent: Intent): Boolean = try { startActivity(intent); true } catch (_: RuntimeException) {
        Toast.makeText(this, R.string.unavailable, Toast.LENGTH_LONG).show(); false
    }

    private fun manage(app: AppEntry) {
        val saved = repository.favourites()
        val visible = FavouriteOrder.visible(saved, apps.map { it.id }.toSet())
        val index = visible.indexOf(app.id)
        val items = mutableListOf(MenuPanel.Item(getString(R.string.open)) { launch(app) })
        if (index >= 0) {
            items += MenuPanel.Item(getString(R.string.remove_favourite)) { updateFavourites(app, FavouriteOrder.toggle(saved, app.id)) }
            if (index > 0) items += MenuPanel.Item(getString(R.string.move_left)) { reorder(app, saved, visible, index - 1) }
            if (index < visible.lastIndex) items += MenuPanel.Item(getString(R.string.move_right)) { reorder(app, saved, visible, index + 1) }
        } else {
            items += MenuPanel.Item(getString(R.string.add_favourite)) { updateFavourites(app, FavouriteOrder.toggle(saved, app.id)) }
        }
        items += MenuPanel.Item(getString(R.string.app_info)) { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${app.id}"))) }
        items += MenuPanel.Item(getString(R.string.cancel)) {}
        dialog?.dismiss()
        dialog = MenuPanel.show(this, app.label, items)
    }

    private fun reorder(app: AppEntry, saved: List<String>, visible: List<String>, to: Int) =
        updateFavourites(app, FavouriteOrder.move(saved, app.id, saved.indexOf(visible[to]) - saved.indexOf(app.id)))

    /** Saves the new order and keeps focus on the same app wherever it now sits. */
    private fun updateFavourites(app: AppEntry, ids: List<String>) {
        repository.save(ids)
        selected = app.id
        render()
    }

    private fun inputs() {
        val manager = getSystemService(TV_INPUT_SERVICE) as? TvInputManager
        val available = runCatching { manager?.tvInputList.orEmpty().filter { it.isPassthroughInput && (Build.VERSION.SDK_INT < 24 || !it.isHidden(this)) } }
            .getOrDefault(emptyList()).sortedBy { it.loadLabel(this).toString() }
        dialog?.dismiss()
        dialog = if (available.isEmpty()) {
            MenuPanel.show(this, getString(R.string.inputs), listOf(
                MenuPanel.Item(getString(R.string.settings)) { open(Intent(Settings.ACTION_SETTINGS)) },
                MenuPanel.Item(getString(R.string.cancel)) {}), getString(R.string.no_inputs))
        } else {
            MenuPanel.show(this, getString(R.string.inputs), available.map { input ->
                MenuPanel.Item(input.loadLabel(this).toString()) { open(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))) }
            } + MenuPanel.Item(getString(R.string.cancel)) {})
        }
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val HEADER = 0
        const val DOCK = 1
        const val GRID = 2
        const val ROW_GAP_DP = 6f
        const val KEY_INPUTS = "@inputs"
        const val KEY_SETTINGS = "@settings"
        const val STATE_SELECTED = "selected"
        fun direction(keyCode: Int) = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> Direction.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Direction.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Direction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.RIGHT
            else -> null
        }
        fun isSelect(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
    }
}
