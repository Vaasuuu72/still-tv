package tv.still.launcher

import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import java.util.Locale

/** Local installed-app search (UI-08): offline, name-only, no content index. The Android TV keyboard
 * types into the field; Down moves to results; OK on a result launches it; Back closes the keyboard
 * first (handled by the IME), then this panel. */
internal class SearchOverlay(private val launcher: Launcher) : Overlay() {
    private lateinit var field: EditText
    private lateinit var status: TextView
    private lateinit var voice: View
    private lateinit var grid: TileGrid

    override fun createView(parent: ViewGroup): View {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.search_overlay, parent, false)
        val g = launcher.geometry
        view.findViewById<View>(R.id.search_content).setPadding(launcher.px(g.sideMarginDp), launcher.px(g.topMarginDp),
            launcher.px(g.sideMarginDp), 0)
        field = view.findViewById(R.id.search_field)
        status = view.findViewById(R.id.search_status)
        voice = view.findViewById(R.id.voice_search)
        if (launcher.voiceSearchAvailable()) {
            voice.visibility = View.VISIBLE
            voice.setOnClickListener { launcher.startVoiceSearch() }
        }
        view.findViewById<CalmScrollView>(R.id.search_scroll).animateScroll = launcher.animate
        grid = TileGrid(view.findViewById(R.id.search_grid), inflater, binder = { tile, key ->
            launcher.bindTile(tile, key)
            tile.setOnFocusChangeListener { v, has -> Tiles.setState(v, if (has) Tiles.State.FOCUSED else Tiles.State.REST, launcher.animate && v.isLaidOut) }
            tile.setOnClickListener { launcher.launch(key) }
        })
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { update(s?.toString().orEmpty()) }
        })
        field.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) { hideKeyboard(); grid.views.firstOrNull()?.requestFocus(); true } else false
        }
        update("")
        return view
    }

    override fun firstFocus(): View = field
    override val opaque = true

    override fun onKey(event: KeyEvent): Boolean {
        val direction = direction(event.keyCode)
        val focusedTile = grid.views.firstOrNull { it.hasFocus() }
        if (focusedTile == null) {
            // In the field (or the voice button): Down enters the results.
            if (direction == FocusMap.Direction.DOWN) {
                if (event.action == KeyEvent.ACTION_DOWN) grid.views.firstOrNull()?.let { hideKeyboard(); it.requestFocus() }
                return true
            }
            return false
        }
        if (direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val target = grid.target(grid.indexOf(focusedTile), direction, view.layoutDirection == View.LAYOUT_DIRECTION_RTL)
                if (target == null) field.requestFocus() else grid.views[target].requestFocus()
            }
            return true
        }
        if (isSelect(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP) launcher.launch(focusedTile.getTag(R.id.app_key) as String)
            return true
        }
        return event.keyCode == KeyEvent.KEYCODE_MENU
    }

    private fun update(query: String) {
        val matches = SearchMatcher.match(launcher.installedApps().map { it.id to it.label }, query)
        val g = launcher.geometry
        grid.render(matches, g.columns, launcher.px(g.tileWidthDp), launcher.px(g.tileHeightDp), launcher.px(g.gapDp),
            launcher.px(HomeLayout.ROW_GAP_DP))
        grid.views.forEach { if (!it.hasFocus()) Tiles.setState(it, Tiles.State.REST, animate = false) }
        val res = status.context
        status.text = when {
            query.isBlank() -> res.getString(R.string.search_all_apps)
            matches.isEmpty() -> res.getString(R.string.search_no_results, query.trim())
            else -> res.resources.getQuantityString(R.plurals.search_results, matches.size, matches.size)
        }
    }

    private fun hideKeyboard() {
        (field.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(field.windowToken, 0)
    }
}

/** Case- and accent-insensitive app-name matching: names starting with the query first, then word starts,
 * then anywhere; each group keeps the given (alphabetical) order. */
internal object SearchMatcher {
    fun match(apps: List<Pair<String, String>>, query: String): List<String> {
        val q = normalise(query)
        if (q.isEmpty()) return apps.map { it.first }
        val ranked = apps.mapNotNull { (id, label) ->
            val name = normalise(label)
            val rank = when {
                name.startsWith(q) -> 0
                name.split(' ').any { it.startsWith(q) } -> 1
                name.contains(q) -> 2
                else -> return@mapNotNull null
            }
            Triple(rank, id, label)
        }
        return ranked.sortedBy { it.first }.map { it.second }
    }

    private fun normalise(text: String): String =
        java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
}
