package tv.still.launcher

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import tv.still.launcher.FocusMap.Direction

/** An open folder (UI-06): Home darkened behind, the folder's name, and its apps in the Home grid geometry.
 * OK launches, hold opens the app's options, Back closes and returns focus to the folder tile. */
internal class FolderOverlay(private val launcher: Launcher, val folderId: String) : Overlay() {
    private lateinit var grid: TileGrid
    private lateinit var scroll: CalmScrollView
    private lateinit var title: TextView
    private lateinit var hint: TextView
    private lateinit var panel: View
    private var focusKey: String? = null
    private var moving: MoveSession? = null

    override fun createView(parent: ViewGroup): View {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.folder_overlay, parent, false)
        scroll = view.findViewById(R.id.folder_scroll)
        scroll.animateScroll = launcher.animate
        title = view.findViewById(R.id.folder_title)
        hint = view.findViewById(R.id.folder_hint)
        panel = view.findViewById(R.id.folder_panel)
        val g = launcher.geometry
        (panel.layoutParams as android.widget.FrameLayout.LayoutParams).setMargins(0, launcher.px(g.topMarginDp), 0, launcher.px(g.topMarginDp))
        val pad = launcher.px(PANEL_PADDING_DP)
        // Room above and below the grid for the focused tile's lift and label.
        scroll.setPadding(pad, launcher.px(PANEL_PADDING_DP), pad, launcher.px(PANEL_PADDING_DP / 2))
        grid = TileGrid(view.findViewById(R.id.folder_grid), LayoutInflater.from(parent.context), binder = { tile, key ->
            launcher.bindTile(tile, key)
            tile.setOnFocusChangeListener { v, has -> onTileFocus(v, has) }
            tile.setOnClickListener { launcher.launch(key) }
            tile.setOnLongClickListener { launcher.showItemMenu(key, it, folderId); true }
        }, centred = true)
        refresh()
        return view
    }

    override fun firstFocus(): View? = grid.viewFor(focusKey) ?: grid.views.firstOrNull()

    /** Re-reads the folder after any change; closes itself if the folder no longer exists. */
    fun refresh() {
        val folder = launcher.visibleFolder(folderId)
        if (folder == null) { if (attached) host.close(this); return }
        title.text = folder.name.ifEmpty { title.context.getString(R.string.default_folder_name) }
        // The folder's apps changed mid-edit (install, update, removal): discard the move rather than render stale keys.
        moving?.let { if (it.original.toSet() != folder.apps.toSet()) moving = null }
        render(moving?.keys ?: folder.apps)
    }

    private fun render(keys: List<String>) {
        val g = launcher.geometry
        val focused = grid.views.firstOrNull { it.hasFocus() }
        val oldIndex = grid.indexOf(focused)
        val columns = folderColumns(keys.size, g.columns)
        panel.layoutParams.width = launcher.px(columns * g.tileWidthDp + (columns - 1) * g.gapDp + 2 * PANEL_PADDING_DP)
        panel.requestLayout()
        grid.render(keys, columns, launcher.px(g.tileWidthDp), launcher.px(g.tileHeightDp), launcher.px(g.gapDp),
            launcher.px(HomeLayout.ROW_GAP_DP))
        val session = moving
        hint.text = if (session != null) hint.context.getString(R.string.moving_hint) else hint.context.getString(R.string.folder_hint)
        for (tile in grid.views) {
            val key = tile.getTag(R.id.app_key) as? String ?: continue
            val state = when {
                session?.key == key -> Tiles.State.PICKED
                tile.hasFocus() -> Tiles.State.FOCUSED
                else -> Tiles.State.REST
            }
            Tiles.setState(tile, state, animate = false)
            Tiles.dim(tile, session != null && session.key != key)
        }
        if (attached && host.top === this && grid.views.none { it.hasFocus() }) {
            (grid.viewFor(focusKey) ?: grid.views.getOrNull(oldIndex.coerceAtMost(grid.views.lastIndex)))?.requestFocus()
        }
    }

    private fun onTileFocus(tile: View, has: Boolean) {
        val key = tile.getTag(R.id.app_key) as? String ?: return
        if (has) focusKey = key
        if (!has && host.top !== this) return // a menu opened above: keep this tile visibly selected
        val state = when { moving?.key == key -> Tiles.State.PICKED; has -> Tiles.State.FOCUSED; else -> Tiles.State.REST }
        Tiles.setState(tile, state, launcher.animate && tile.isLaidOut)
    }

    override fun onKey(event: KeyEvent): Boolean {
        val direction = direction(event.keyCode)
        val session = moving
        if (session != null) return movingKey(event, session, direction)
        val focused = grid.views.firstOrNull { it.hasFocus() } ?: return false
        val key = focused.getTag(R.id.app_key) as String
        if (direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                grid.target(grid.indexOf(focused), direction, isRtl())?.let { grid.views[it].requestFocus() }
            }
            return true
        }
        if (isSelect(event.keyCode)) return launcher.handleSelect(event, focused, { launcher.launch(key) }, { launcher.showItemMenu(key, focused, folderId) })
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) launcher.showItemMenu(key, focused, folderId)
            return true
        }
        return false
    }

    fun startMoving(appKey: String) {
        moving = MoveSession(appKey, grid.keys)
        focusKey = appKey
        render(grid.keys)
        grid.viewFor(appKey)?.requestFocus()
        launcher.announce(title.context.getString(R.string.moving_title, launcher.appLabel(appKey)))
    }

    private fun movingKey(event: KeyEvent, session: MoveSession, direction: Direction?): Boolean {
        if (direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN && session.move(if (isRtl()) TileGrid.mirror(direction) else direction, grid.columns)) {
                render(session.keys)
                grid.viewFor(session.key)?.requestFocus()
            }
            return true
        }
        if (isSelect(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP) finishMoving(commit = true)
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) finishMoving(commit = false)
            return true
        }
        return event.keyCode == KeyEvent.KEYCODE_MENU // volume, mute and voice keys still reach the system
    }

    private fun finishMoving(commit: Boolean) {
        val session = moving ?: return
        moving = null
        if (commit && session.changed) launcher.saveFolderOrder(folderId, session.keys)
        render(if (commit) session.keys else session.original)
        grid.viewFor(session.key)?.requestFocus()
        val res = title.context
        launcher.announce(if (commit) res.getString(R.string.placed, launcher.appLabel(session.key)) else res.getString(R.string.move_cancelled))
    }

    override fun onClosed() { moving = null }

    /** Leaving Still TV (app launch, Home) discards an unfinished move, as on Home. */
    fun cancelMoving() { if (moving != null) finishMoving(commit = false) }

    private fun isRtl() = view.layoutDirection == View.LAYOUT_DIRECTION_RTL

    companion object {
        private const val PANEL_PADDING_DP = 28f
        private const val MIN_COLUMNS = 3

        /** A folder panel is narrower than Home (one column fewer) so it reads as a foreground panel. */
        fun folderColumns(count: Int, homeColumns: Int) = count.coerceIn(MIN_COLUMNS, (homeColumns - 1).coerceAtLeast(MIN_COLUMNS))
    }
}

internal fun direction(keyCode: Int) = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP -> Direction.UP
    KeyEvent.KEYCODE_DPAD_DOWN -> Direction.DOWN
    KeyEvent.KEYCODE_DPAD_LEFT -> Direction.LEFT
    KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.RIGHT
    else -> null
}

internal fun isSelect(keyCode: Int) =
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
