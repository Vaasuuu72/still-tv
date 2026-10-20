package tv.still.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import tv.still.launcher.HomeItem.Folder
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Still TV Home. Owns lifecycle, app discovery, the Home list and key routing; views live in [HomeScene]
 * and the overlays. Offline: no network, services or polling; the only periodic work is the minute tick. */
class MainActivity : Activity(), Launcher, HomeHost {
    private lateinit var repository: AppRepository
    private lateinit var store: HomeStore
    private lateinit var appearanceStore: Appearance.Store
    private lateinit var scene: HomeScene
    private lateinit var overlays: OverlayHost
    override lateinit var geometry: HomeLayout
    override var appearance = Appearance(); private set
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val press = HoldGesture()
    /** After a hold opened a menu, the rest of that OK press (repeats and release) is swallowed. */
    private var swallowSelect = false
    private var pressedTile: View? = null
    private var holdAction: (() -> Unit)? = null
    private val holdRunnable = Runnable {
        if (press.hold()) { swallowSelect = true; releasePress(); holdAction?.invoke() }
    }
    private var apps = emptyList<AppEntry>()
    private var byId = emptyMap<String, AppEntry>()
    private var full = emptyList<HomeItem>()
    private var visible = emptyList<HomeItem>()
    private var rendered: List<Any>? = null
    /** Bumped when artwork is evicted, so the next render re-binds tiles. */
    private var artworkEpoch = 0
    private var moving: MoveSession? = null
    private var generation = 0
    private var active = false
    private var systemAnimations = true
    override val animate get() = systemAnimations && !appearance.reduceMotion
    override val overlaysOpen get() = overlays.isOpen
    override val movingKey get() = moving?.key
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action in TIME_ACTIONS) { updateClock(); return }
            // Only the changed package's artwork (and folders previewing it) is redrawn.
            intent.data?.schemeSpecificPart?.let { repository.evict(it); artworkEpoch++ }
            refresh()
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(R.layout.activity_main)
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        repository = AppRepository(applicationContext)
        store = HomeStore(preferences, AppRepository.DEFAULT_ORDER)
        appearanceStore = Appearance.Store(preferences)
        appearance = appearanceStore.load()
        scene = HomeScene(findViewById(R.id.root), layoutInflater, this)
        overlays = OverlayHost(findViewById(R.id.overlays), scene.scroll, { animate }, { scene.restoreFocus() })
        scene.searchButton.setOnClickListener { openSearch() }
        scene.controlsButton.setOnClickListener { openControls() }
        applyGeometry()
        scene.selected = state?.getString(STATE_SELECTED) ?: selectionPreferences().getString(STATE_SELECTED, "").orEmpty()
        scene.showStatus(getString(R.string.loading))
    }

    override fun onStart() {
        super.onStart(); active = true
        val packages = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        }
        val time = IntentFilter().apply { TIME_ACTIONS.forEach(::addAction) }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(changes, packages, RECEIVER_EXPORTED)
            registerReceiver(changes, time, RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag") registerReceiver(changes, packages)
            @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag") registerReceiver(changes, time)
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        val systemNow = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        if (systemNow != systemAnimations) { systemAnimations = systemNow; scene.scroll.animateScroll = animate }
        updateClock()
    }

    override fun onPause() {
        selectionPreferences().edit().putString(STATE_SELECTED, scene.selected).apply()
        super.onPause()
    }

    override fun onStop() {
        cancelPress()
        if (moving != null) finishMoving(commit = false)
        overlays.find(FolderOverlay::class.java)?.cancelMoving()
        // An open folder survives an app launch from it; menus, search and panels do not.
        val folder = overlays.find(FolderOverlay::class.java)
        if (folder != null) overlays.top?.takeIf { it !== folder }?.let { closeAbove(folder) } else if (overlays.isOpen) overlays.closeAll()
        active = false; generation++
        unregisterReceiver(changes)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) cancelPress() // a release delivered elsewhere must not leave the next OK swallowed
    }

    override fun onDestroy() { worker.shutdownNow(); repository.artworks.evictAll(); super.onDestroy() }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_RUNNING_LOW) repository.artworks.evictAll()
    }

    override fun onSaveInstanceState(outState: Bundle) { outState.putString(STATE_SELECTED, scene.selected); super.onSaveInstanceState(outState) }

    /** Home pressed while already on Still TV resets to the first tile; returning from an app keeps the selection. */
    override fun onNewIntent(intent: Intent) {
        val alreadyHome = hasWindowFocus()
        super.onNewIntent(intent); setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) && alreadyHome) {
            cancelPress()
            if (moving != null) finishMoving(commit = false)
            if (overlays.isOpen) overlays.closeAll()
            scene.reset()
        }
    }

    /** Back on Home never leaves the launcher: it returns to the first tile. */
    @Deprecated("Home deliberately retains its task")
    override fun onBackPressed() {
        if (!scene.isFirst(currentFocus)) scene.reset()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (swallowSelect && isSelect(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP) { swallowSelect = false; press.up() }
            return true
        }
        overlays.top?.let { top ->
            if (top.onKey(event)) return true
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) top.onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        moving?.let { session -> if (movingKey(event, session)) return true; return super.dispatchKeyEvent(event) }
        return homeKey(event) || super.dispatchKeyEvent(event)
    }

    private fun homeKey(event: KeyEvent): Boolean {
        direction(event.keyCode)?.let { dir ->
            if (event.action == KeyEvent.ACTION_DOWN) scene.move(dir, isRtl())
            return true
        }
        val focus = currentFocus ?: return false
        val key = focus.getTag(R.id.app_key) as? String ?: return false
        if (scene.grid.indexOf(focus) < 0) return false // header controls use their click listeners
        if (isSelect(event.keyCode)) return handleSelect(event, focus, { onActivate(key, focus) }, { showItemMenu(key, focus, null) })
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) showItemMenu(key, focus, null)
            return true
        }
        return false
    }

    // --- OK press / hold (UI-03, UI-04) ---

    override fun handleSelect(event: KeyEvent, tile: View, onClick: () -> Unit, onHold: () -> Unit): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (press.down(event.repeatCount)) {
                pressedTile = tile; holdAction = onHold
                Tiles.press(tile, animate)
                handler.postDelayed(holdRunnable, HoldGesture.HOLD_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(holdRunnable)
                releasePress()
                if (press.up() == HoldGesture.Release.CLICK) onClick()
            }
        }
        return true
    }

    private fun releasePress() {
        pressedTile?.let { if (it.isFocused || overlays.isOpen) Tiles.setState(it, Tiles.State.FOCUSED, animate) }
        pressedTile = null
    }

    private fun cancelPress() { handler.removeCallbacks(holdRunnable); press.cancel(); releasePress(); swallowSelect = false }

    // --- Discovery and rendering ---

    /** Discovery runs on the worker; the Home list is read on the main thread, where every save also happens,
     * so a slow discovery can never overwrite an edit made meanwhile. */
    private fun refresh() {
        val request = ++generation
        worker.execute {
            val result = runCatching { repository.discover() }
            runOnUiThread {
                if (!active || isDestroyed || request != generation) return@runOnUiThread
                result.onSuccess { found ->
                    apps = found; byId = found.associateBy { it.id }
                    full = store.load(found.map { it.id })
                    if (moving != null && HomeModel.visible(full, byId.keys).map { it.key }.toSet() != moving?.original?.toSet()) {
                        finishMoving(commit = false) // the app list changed mid-edit: discard rather than risk a corrupt order
                    }
                    scene.showStatus(null)
                    renderHome()
                    overlays.find(FolderOverlay::class.java)?.refresh()
                }.onFailure {
                    if (rendered == null) renderHome(force = true)
                    scene.showStatus(getString(R.string.load_error))
                }
            }
        }
    }

    private fun renderHome(focus: String? = null, force: Boolean = false) {
        visible = HomeModel.visible(full, byId.keys)
        val signature = listOf(visible, visible.map { appLabel(it.key) }, geometry, moving?.keys ?: emptyList<String>(), artworkEpoch)
        if (focus != null) scene.selected = focus
        if (!force && signature == rendered && focus == null) return
        rendered = signature
        scene.render(moving?.keys ?: visible.map { it.key }, focus)
        scene.showStatus(if (apps.isEmpty()) getString(R.string.no_apps) else null)
    }

    private fun applyGeometry() {
        val c = resources.configuration
        geometry = HomeLayout.compute(c.screenWidthDp, c.screenHeightDp, appearance.largeIcons, appearance.topShelf)
        scene.applyLayout(geometry, appearance)
        val m = resources.displayMetrics
        val side = px(geometry.sideMarginDp); val top = px(geometry.topMarginDp)
        overlays.safe.set(side, top, m.widthPixels - side, m.heightPixels - top)
    }

    override fun bindTile(tile: View, key: String) {
        val width = px(geometry.tileWidthDp); val height = px(geometry.tileHeightDp)
        val radius = HomeLayout.CORNER_DP * resources.displayMetrics.density
        // A re-render of the same key keeps its current bitmap until a newer one (e.g. a changed folder) arrives.
        val keep = tile.getTag(R.id.app_key) == key
        if (key.startsWith(HomeItem.FOLDER_PREFIX)) {
            val folder = visibleFolder(key.removePrefix(HomeItem.FOLDER_PREFIX)) ?: return
            val name = folderName(folder)
            val cached = repository.cachedFolderArtwork(folder, byId, width, height)
            Tiles.bind(tile, key, name, resources.getQuantityString(R.plurals.folder_description, folder.apps.size, name, folder.apps.size), cached, keep)
            if (cached == null) loadArtwork(tile, key, isFolder = true) { repository.folderArtwork(folder, byId, width, height, radius) }
            return
        }
        val app = byId[key] ?: return
        val cached = repository.cachedArtwork(key, width, height)
        Tiles.bind(tile, key, app.label, getString(R.string.tile_hint, app.label), cached, keep)
        if (cached == null) loadArtwork(tile, key, isFolder = false) { repository.artwork(app, width, height, radius) }
    }

    private fun loadArtwork(tile: View, key: String, isFolder: Boolean, load: () -> Artwork) {
        worker.execute {
            val artwork = runCatching(load).getOrNull() ?: return@execute
            runOnUiThread {
                if (isDestroyed || tile.getTag(R.id.app_key) != key) return@runOnUiThread
                Tiles.bindArtwork(tile, artwork, isFolder)
                scene.onArtwork(key)
            }
        }
    }

    override fun hero(key: String): Hero? {
        moving?.let { return Hero(getString(R.string.moving_title, appLabel(it.key)), getString(R.string.moving_hint), null) }
        if (key.startsWith(HomeItem.FOLDER_PREFIX)) {
            val folder = visibleFolder(key.removePrefix(HomeItem.FOLDER_PREFIX)) ?: return null
            return Hero(folderName(folder), resources.getQuantityString(R.plurals.hero_folder_hint, folder.apps.size, folder.apps.size), null)
        }
        val app = byId[key] ?: return null
        val tint = repository.cachedArtwork(key, px(geometry.tileWidthDp), px(geometry.tileHeightDp))?.tint
        return Hero(app.label, getString(R.string.hero_hint), tint)
    }

    // --- Actions ---

    override fun onActivate(key: String, tile: View) {
        if (moving != null) return
        if (key.startsWith(HomeItem.FOLDER_PREFIX)) openFolder(key.removePrefix(HomeItem.FOLDER_PREFIX)) else launch(key)
    }

    override fun onOptions(key: String, tile: View) { if (moving == null) showItemMenu(key, tile, null) }

    override fun launch(appId: String) {
        val app = byId[appId] ?: return refresh()
        if (!open(Intent.makeMainActivity(app.component))) refresh()
    }

    private fun open(intent: Intent): Boolean = try { startActivity(intent); true } catch (_: RuntimeException) {
        Toast.makeText(this, R.string.unavailable, Toast.LENGTH_LONG).show(); false
    }

    private fun openFolder(folderId: String) {
        if (visibleFolder(folderId) != null) overlays.show(FolderOverlay(this, folderId))
    }

    private fun openSearch() = overlays.show(SearchOverlay(this))

    private fun openControls() {
        val now = Date()
        val date = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now).toString()
        overlays.show(MenuOverlay(DateFormat.getTimeFormat(this).format(now), listOf(
            MenuOverlay.Item(getString(R.string.search)) { openSearch() },
            MenuOverlay.Item(getString(R.string.inputs)) { Inputs.show(this, overlays, ::open) },
            MenuOverlay.Item(getString(R.string.settings)) { openSystemSettings() },
            MenuOverlay.Item(getString(R.string.preferences)) { overlays.show(PreferencesOverlay(this)) },
        ), message = date, anchor = scene.controlsButton, placement = MenuOverlay.Placement.TOP_END))
    }

    override fun openSystemSettings() { open(Intent(Settings.ACTION_SETTINGS)) }

    override fun showItemMenu(key: String, anchor: View, folderId: String?) {
        val items = ItemMenus(this, full, byId.keys, folderId).build(key, object : ItemMenus.Actions {
            override fun edit() {
                if (folderId == null) startMoving(key) else overlays.find(FolderOverlay::class.java)?.startMoving(key)
            }
            override fun newFolder() { createFolder(key, folderId) }
            override fun moveToFolder(target: String) { moveIntoFolder(key, target, folderId) }
            override fun moveToHome() { update(HomeModel.moveToHome(full, key), focus = key) }
            override fun rename(folder: Folder) { this@MainActivity.rename(folder) }
            override fun ungroup(folder: Folder) { confirmUngroup(folder) }
            override fun appInfo() { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$key"))) }
            override fun chooseFolder(folders: List<Folder>) = overlays.show(MenuOverlay(getString(R.string.move_to_folder),
                folders.map { f -> MenuOverlay.Item(folderName(f)) { moveIntoFolder(key, f.id, folderId) } } +
                    MenuOverlay.Item(getString(R.string.new_folder)) { createFolder(key, folderId) } +
                    MenuOverlay.Item(getString(R.string.cancel)) {}, anchor = anchor))
        })
        val title = if (key.startsWith(HomeItem.FOLDER_PREFIX)) visibleFolder(key.removePrefix(HomeItem.FOLDER_PREFIX))?.let(::folderName) else appLabel(key)
        overlays.show(MenuOverlay(title.orEmpty(), items, anchor = anchor))
    }

    private fun createFolder(appId: String, fromFolder: String?) {
        fromFolder?.let { id -> overlays.find(FolderOverlay::class.java)?.takeIf { it.folderId == id }?.let { overlays.close(it, restoreFocus = false) } }
        val id = HomeModel.newFolderId(full)
        val name = getString(R.string.default_folder_name)
        update(HomeModel.createFolder(full, appId, id, name), focus = HomeItem.FOLDER_PREFIX + id)
        overlays.show(RenameOverlay(name) { update(HomeModel.rename(full, id, it), focus = HomeItem.FOLDER_PREFIX + id) })
    }

    private fun moveIntoFolder(appId: String, target: String, fromFolder: String?) {
        fromFolder?.let { id -> overlays.find(FolderOverlay::class.java)?.takeIf { it.folderId == id }?.let { overlays.close(it, restoreFocus = false) } }
        update(HomeModel.moveToFolder(full, appId, target), focus = HomeItem.FOLDER_PREFIX + target)
    }

    private fun rename(folder: Folder) {
        overlays.show(RenameOverlay(folderName(folder)) { update(HomeModel.rename(full, folder.id, it), focus = folder.key) })
    }

    private fun confirmUngroup(folder: Folder) {
        overlays.show(MenuOverlay(getString(R.string.remove_folder), listOf(
            MenuOverlay.Item(getString(R.string.remove_folder)) { update(HomeModel.ungroup(full, folder.id), focus = folder.apps.firstOrNull()) },
            MenuOverlay.Item(getString(R.string.cancel)) {}), message = getString(R.string.remove_folder_message)))
    }

    /** Persists a changed Home list once (never per key press) and re-renders Home and any open folder. */
    private fun update(next: List<HomeItem>, focus: String?) {
        if (next != full) { full = next; store.save(full) }
        renderHome(focus)
        overlays.find(FolderOverlay::class.java)?.refresh()
    }

    override fun saveFolderOrder(folderId: String, apps: List<String>) =
        update(HomeModel.reorderFolder(full, folderId, apps, byId.keys), focus = null)

    // --- Edit Home Screen (UI-05) ---

    private fun startMoving(key: String) {
        moving = MoveSession(key, visible.map { it.key })
        renderHome(focus = key, force = true)
        announce(getString(R.string.moving_title, appLabel(key)))
    }

    private fun movingKey(event: KeyEvent, session: MoveSession): Boolean {
        direction(event.keyCode)?.let { dir ->
            if (event.action == KeyEvent.ACTION_DOWN && session.move(if (isRtl()) TileGrid.mirror(dir) else dir, geometry.columns)) {
                renderHome(focus = session.key, force = true)
            }
            return true
        }
        if (isSelect(event.keyCode)) { if (event.action == KeyEvent.ACTION_UP) finishMoving(commit = true); return true }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) { if (event.action == KeyEvent.ACTION_UP) finishMoving(commit = false); return true }
        return event.keyCode == KeyEvent.KEYCODE_MENU
    }

    private fun finishMoving(commit: Boolean) {
        val session = moving ?: return
        moving = null
        if (commit && session.changed) {
            val byKey = visible.associateBy { it.key }
            full = HomeModel.replaceVisible(full, session.keys.mapNotNull { byKey[it] }, byId.keys)
            store.save(full)
        }
        renderHome(focus = session.key, force = true)
        announce(if (commit) getString(R.string.placed, appLabel(session.key)) else getString(R.string.move_cancelled))
    }

    // --- Launcher (overlay contract) ---

    override fun px(dp: Float) = (dp * resources.displayMetrics.density + 0.5f).toInt()
    override fun appLabel(id: String) = byId[id]?.label
        ?: visibleFolder(id.removePrefix(HomeItem.FOLDER_PREFIX))?.let(::folderName) ?: id
    override fun installedApps() = apps
    override fun visibleFolder(folderId: String) = HomeModel.folder(visible, folderId)
    private fun folderName(folder: Folder) = folder.name.ifEmpty { getString(R.string.default_folder_name) }

    override fun setAppearance(value: Appearance) {
        if (value == appearance) return
        val relayout = value.largeIcons != appearance.largeIcons || value.topShelf != appearance.topShelf || value.clock != appearance.clock
        appearance = value
        appearanceStore.save(value)
        scene.scroll.animateScroll = animate
        if (relayout) { applyGeometry(); renderHome() }
    }

    override fun resetHome() {
        val items = store.reset(apps.map { it.id })
        update(items, focus = HomeModel.visible(items, byId.keys).firstOrNull()?.key)
    }

    override fun voiceSearchAvailable() = packageManager.resolveActivity(Intent(Intent.ACTION_ASSIST), 0) != null
    override fun startVoiceSearch() { open(Intent(Intent.ACTION_ASSIST)) }
    override fun announce(text: CharSequence) = scene.scroll.announceForAccessibility(text)
    override fun versionName(): String = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

    private fun closeAbove(overlay: Overlay) {
        while (overlays.top != null && overlays.top !== overlay) overlays.close(overlays.top!!, restoreFocus = true)
    }

    private fun updateClock() {
        if (appearance.clock) scene.clock.text = DateFormat.getTimeFormat(this).format(Date())
    }

    private fun isRtl() = window.decorView.layoutDirection == View.LAYOUT_DIRECTION_RTL

    /** Explicit name: Activity.getPreferences() would derive it from the application id, which differs in previews.
     * "MainActivity" is the file production Still TV already uses. */
    private fun selectionPreferences() = getSharedPreferences(SELECTION_PREFERENCES, MODE_PRIVATE)

    private companion object {
        const val PREFERENCES = "still"
        const val STATE_SELECTED = "selected"
        const val SELECTION_PREFERENCES = "MainActivity"
        val TIME_ACTIONS = listOf(Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    }
}
