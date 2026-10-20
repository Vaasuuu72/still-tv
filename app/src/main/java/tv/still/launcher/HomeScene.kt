package tv.still.launcher

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import tv.still.launcher.FocusMap.Direction
import tv.still.launcher.FocusMap.Pos

/** Top Shelf content for a top-row item: its own name, a hint, and a colour from its own artwork. */
data class Hero(val title: String, val subtitle: String, val tint: Int?)

/** What the Home scene needs from the Activity. */
internal interface HomeHost {
    val animate: Boolean
    val overlaysOpen: Boolean
    val movingKey: String?
    fun px(dp: Float): Int
    fun bindTile(tile: View, key: String)
    fun hero(key: String): Hero?
    fun onActivate(key: String, tile: View)
    fun onOptions(key: String, tile: View)
}

/** Home's views (UI-01/02/03): header controls, Top Shelf, the unified grid and their focus behaviour.
 * One logical focus owner: every D-pad move is resolved by [FocusMap]; the selection is remembered by key. */
internal class HomeScene(root: View, inflater: LayoutInflater, private val host: HomeHost) {
    val scroll: CalmScrollView = root.findViewById(R.id.scroll)
    private val content: LinearLayout = root.findViewById(R.id.content)
    private val shelfFrame: View = root.findViewById(R.id.shelf)
    private val header: View = root.findViewById(R.id.header)
    private val hero: View = root.findViewById(R.id.hero)
    private val gridContainer: LinearLayout = root.findViewById(R.id.grid)
    private val status: TextView = root.findViewById(R.id.status)
    private val editHint: View = root.findViewById(R.id.edit_hint)
    val clock: TextView = root.findViewById(R.id.clock)
    val searchButton: View = root.findViewById(R.id.search_button)
    val controlsButton: View = root.findViewById(R.id.controls_button)
    private val headerItems = listOf(searchButton, controlsButton)
    val shelf = TopShelf(root.findViewById(R.id.shelf_backdrop), root.findViewById(R.id.hero_title), root.findViewById(R.id.hero_subtitle))
    val grid = TileGrid(gridContainer, inflater, binder = { tile, key -> host.bindTile(tile, key); wire(tile) })

    /** Key of the last meaningful focus (an app, a folder, or a header control). */
    var selected = ""
    /** False until the first render: the window's automatic first focus is not a user choice. */
    var ready = false
    private var geometry: HomeLayout? = null
    private val remembered = intArrayOf(-1, -1)
    private var rebuilding = false

    init {
        searchButton.setTag(R.id.app_key, KEY_SEARCH)
        controlsButton.setTag(R.id.app_key, KEY_CONTROLS)
        headerItems.forEachIndexed { index, view ->
            view.setOnFocusChangeListener { v, has -> if (has) onHeaderFocus(v, index) }
        }
    }

    fun applyLayout(layout: HomeLayout, appearance: Appearance) {
        geometry = layout
        val side = host.px(layout.sideMarginDp)
        shelfFrame.layoutParams.height = host.px(layout.shelfHeightDp)
        (header.layoutParams as FrameLayout.LayoutParams).topMargin = host.px(layout.topMarginDp)
        header.setPadding(side, 0, side, 0)
        hero.setPadding(side, 0, side, host.px(HERO_BOTTOM_DP))
        hero.visibility = if (appearance.topShelf) View.VISIBLE else View.GONE
        shelf.enabled = appearance.topShelf
        gridContainer.setPadding(side, 0, side, 0)
        status.setPadding(side, status.paddingTop, side, 0)
        content.setPadding(0, 0, 0, host.px(layout.topMarginDp))
        clock.visibility = if (appearance.clock) View.VISIBLE else View.GONE
        scroll.follow = false
        scroll.animateScroll = host.animate
        shelfFrame.requestLayout()
    }

    fun render(keys: List<String>, focus: String? = null) {
        val g = geometry ?: return
        focus?.let { selected = it }
        rebuilding = true
        try {
            grid.render(keys, g.columns, host.px(g.tileWidthDp), host.px(g.tileHeightDp), host.px(g.gapDp), host.px(HomeLayout.ROW_GAP_DP))
        } finally { rebuilding = false }
        ready = true
        refreshStates()
        if (!host.overlaysOpen) restoreFocus()
    }

    fun showStatus(text: CharSequence?) {
        status.text = text
        status.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /** Re-applies resting/focused/picked visuals after a render (tile views are reused across renders). */
    fun refreshStates() {
        val moving = host.movingKey
        for (tile in grid.views) {
            val key = tile.getTag(R.id.app_key)
            val state = when { key == moving -> Tiles.State.PICKED; tile.isFocused -> Tiles.State.FOCUSED; else -> Tiles.State.REST }
            Tiles.setState(tile, state, animate = false)
            Tiles.dim(tile, moving != null && key != moving)
        }
        updateEditHint()
    }

    /** The floating edit hint is needed only where the hero (which repeats it on the top row) is not visible. */
    private fun updateEditHint() {
        val heroShowsIt = hero.visibility == View.VISIBLE && grid.rowOf(grid.indexOf(host.movingKey)) == 0
        editHint.visibility = if (host.movingKey != null && !heroShowsIt) View.VISIBLE else View.GONE
    }

    fun restoreFocus() {
        val target = viewFor(selected) ?: grid.views.firstOrNull() ?: controlsButton
        if (target.isFocused) onFocused(target) else target.requestFocus()
    }

    /** Home pressed on Home, or Back: the first tile, scrolled to the top. */
    fun reset() {
        selected = grid.keys.firstOrNull() ?: selected
        scroll.glideTo(0)
        restoreFocus()
    }

    fun viewFor(key: String?): View? = headerItems.firstOrNull { it.getTag(R.id.app_key) == key } ?: grid.viewFor(key)

    fun isFirst(view: View?) = view != null && view === grid.views.firstOrNull()

    /** Deterministic D-pad move across the header controls and the grid. Up from the top row reaches
     * the header (an actual focusable target) instead of jumping to tile one. */
    fun move(direction: Direction, rtl: Boolean) {
        val focus = grid.views.firstOrNull { it.isFocused } ?: headerItems.firstOrNull { it.isFocused }
        val pos = when {
            focus == null -> { restoreFocus(); return }
            focus in headerItems -> Pos(SECTION_HEADER, headerItems.indexOf(focus))
            else -> Pos(SECTION_GRID, grid.indexOf(focus))
        }
        val d = if (rtl) TileGrid.mirror(direction) else direction
        val map = FocusMap(listOf(FocusMap.Section(headerItems.size, headerItems.size), FocusMap.Section(grid.views.size, grid.columns)))
        var target = map.move(pos, d)
        if (target.section != pos.section) target = map.move(pos, d, remembered[target.section])
        val view = if (target.section == SECTION_HEADER) headerItems.getOrNull(target.index) else grid.views.getOrNull(target.index)
        view?.requestFocus()
    }

    /** Called when a tile's artwork arrives, so the Top Shelf can pick up its colour. */
    fun onArtwork(key: String) {
        if (key == selected && grid.rowOf(grid.indexOf(key)) == 0) updateShelf(key)
    }

    private fun wire(tile: View) {
        tile.setOnFocusChangeListener { v, has -> if (has) onFocused(v) else onBlurred(v) }
        tile.setOnClickListener { v -> (v.getTag(R.id.app_key) as? String)?.let { host.onActivate(it, v) } }
        tile.setOnLongClickListener { v -> (v.getTag(R.id.app_key) as? String)?.let { host.onOptions(it, v) }; true }
    }

    private fun onFocused(tile: View) {
        if (tile in headerItems) { onHeaderFocus(tile, headerItems.indexOf(tile)); return }
        val key = tile.getTag(R.id.app_key) as? String ?: return
        Tiles.setState(tile, if (key == host.movingKey) Tiles.State.PICKED else Tiles.State.FOCUSED, host.animate && tile.isLaidOut)
        if (rebuilding) return
        val index = grid.indexOf(tile)
        selected = key
        remembered[SECTION_GRID] = index
        scrollFor(index)
        val topRow = grid.rowOf(index) == 0
        if (topRow) updateShelf(key)
        showHero(topRow)
        updateEditHint()
    }

    /** The hero text belongs to the top row; it fades out while a lower row is focused. */
    private fun showHero(visible: Boolean) {
        val alpha = if (visible) 1f else 0f
        if (hero.alpha == alpha && !hero.hasTransientState()) return
        hero.animate().cancel()
        if (host.animate) hero.animate().alpha(alpha).setDuration(HomeLayout.FOCUS_MS).start() else hero.alpha = alpha
    }

    private fun onBlurred(tile: View) {
        // While an overlay is open the tile keeps its selected look, preserving the menu's context.
        if (host.overlaysOpen || tile.getTag(R.id.app_key) == host.movingKey) return
        Tiles.setState(tile, Tiles.State.REST, host.animate && tile.isLaidOut)
    }

    private fun onHeaderFocus(view: View, index: Int) {
        if (!ready || rebuilding) return
        selected = view.getTag(R.id.app_key) as String
        remembered[SECTION_HEADER] = index
        scroll.glideTo(0)
        showHero(true)
        val res = view.resources
        if (view === searchButton) shelf.show(res.getString(R.string.search), res.getString(R.string.hero_search_hint), null, host.animate)
        else shelf.show(res.getString(R.string.controls), res.getString(R.string.hero_controls_hint), null, host.animate)
    }

    private fun updateShelf(key: String) {
        val hero = host.hero(key) ?: return
        shelf.show(hero.title, hero.subtitle, hero.tint, host.animate)
    }

    /** Row 0 shows the Top Shelf; lower rows are held at a fixed anchor, so each move is one predictable row step. */
    private fun scrollFor(index: Int) {
        val g = geometry ?: return
        val row = grid.rowOf(index)
        val rowView = gridContainer.getChildAt(row)
        if (row <= 0 || rowView == null) { scroll.glideTo(0); return }
        if (!rowView.isLaidOut) { scroll.post { if (rowView.isLaidOut) scrollFor(index) }; return }
        scroll.glideTo(gridContainer.top + rowView.top - host.px(g.rowAnchorDp))
    }

    companion object {
        const val KEY_SEARCH = "@search"
        const val KEY_CONTROLS = "@controls"
        private const val SECTION_HEADER = 0
        private const val SECTION_GRID = 1
        private const val HERO_BOTTOM_DP = 18f
    }
}
