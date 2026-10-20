package tv.still.launcher

import android.app.Activity
import android.app.AlertDialog
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var repository: AppRepository
    private lateinit var grid: GridView
    private lateinit var adapter: Tiles
    private val worker = Executors.newSingleThreadExecutor()
    private var apps = emptyList<AppEntry>()
    private var rows = emptyList<AppEntry?>()
    private var allApps = false
    private var selectedHome = ""
    private var selectedAll = ""
    private var generation = 0
    private var loaded = false
    private var active = false
    private var animations = true
    private var dialog: AlertDialog? = null
    private var columns = 4
    private val handler = Handler(Looper.getMainLooper())
    private val press = HoldGesture()
    private val holdToManage = Runnable { if (press.hold()) rows.getOrNull(grid.selectedItemPosition)?.let { manage(it) } }
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action in listOf(Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) updateClock()
            else { repository.icons.evictAll(); refresh() }
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(R.layout.activity_main)
        if (Build.VERSION.SDK_INT >= 28) findViewById<View>(R.id.title).isAccessibilityHeading = true
        repository = AppRepository(applicationContext)
        allApps = state?.getBoolean("all") ?: false
        val prefs = getPreferences(MODE_PRIVATE)
        selectedHome = state?.getString("home") ?: prefs.getString("home", "").orEmpty()
        selectedAll = state?.getString("selectedAll") ?: ""
        grid = findViewById(R.id.grid)
        columns = ((resources.configuration.screenWidthDp - 80) / 170).coerceIn(2, 6)
        grid.numColumns = columns
        adapter = Tiles()
        grid.adapter = adapter
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            remember(position)
            rows.getOrNull(position)?.let { launch(it) } ?: showPage(true)
        }
        grid.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            rows.getOrNull(position)?.let { manage(it); true } ?: false
        }
        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { remember(position); highlight() }
            override fun onNothingSelected(parent: AdapterView<*>?) { highlight() }
        }
        grid.onFocusChangeListener = View.OnFocusChangeListener { _, _ -> highlight() }
        grid.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER) select(event)
            else if (event.action != KeyEvent.ACTION_DOWN) false
            else {
                val position = grid.selectedItemPosition.coerceAtLeast(0)
                when {
                    key == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (position / columns >= rows.lastIndex / columns) findViewById<View>(R.id.settings).requestFocus()
                        else grid.setSelection((position + columns).coerceAtMost(rows.lastIndex))
                        true
                    }
                    key == KeyEvent.KEYCODE_DPAD_UP -> { grid.setSelection((position - columns).coerceAtLeast(0)); true }
                    key == KeyEvent.KEYCODE_DPAD_LEFT && position % columns == 0 -> true
                    key == KeyEvent.KEYCODE_DPAD_RIGHT && (position % columns == columns - 1 || position == rows.lastIndex) -> true
                    key == KeyEvent.KEYCODE_MENU -> { rows.getOrNull(position)?.let { manage(it) }; true }
                    else -> false
                }
            }
        }
        findViewById<View>(R.id.settings).setOnClickListener { open(Intent(Settings.ACTION_SETTINGS)) }
        findViewById<View>(R.id.inputs).setOnClickListener { inputs() }
        findViewById<View>(R.id.help).setOnClickListener { help() }
        for (id in listOf(R.id.settings, R.id.inputs, R.id.help)) {
            findViewById<View>(id).setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) { restoreSelection(); true } else false
            }
        }
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
            @Suppress("DEPRECATION")
            registerReceiver(changes, packages)
            @Suppress("DEPRECATION")
            registerReceiver(changes, time)
        }
        refresh()
    }
    override fun onResume() {
        super.onResume()
        animations = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        updateClock()
    }
    override fun onPause() {
        getPreferences(MODE_PRIVATE).edit().putString("home", selectedHome).apply()
        super.onPause()
    }
    override fun onStop() {
        handler.removeCallbacks(holdToManage); press.cancel()
        active = false; generation++
        unregisterReceiver(changes)
        dialog?.dismiss(); dialog = null
        super.onStop()
    }
    override fun onDestroy() { worker.shutdownNow(); repository.icons.evictAll(); super.onDestroy() }
    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if (level >= TRIM_MEMORY_RUNNING_LOW) repository.icons.evictAll() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("all", allApps); outState.putString("home", selectedHome); outState.putString("selectedAll", selectedAll)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        dialog?.dismiss()
        if (intent.hasCategory(Intent.CATEGORY_HOME)) showPage(false)
    }
    @Deprecated("Home deliberately retains its task")
    override fun onBackPressed() { if (allApps) showPage(false) else restoreSelection() }
    /** OK/Enter on the grid: release before [HoldGesture.HOLD_MS] opens the tile, holding opens management. */
    private fun select(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (press.down(event.repeatCount)) handler.postDelayed(holdToManage, HoldGesture.HOLD_MS)
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(holdToManage)
                val position = grid.selectedItemPosition
                if (press.up() == HoldGesture.Release.CLICK && position >= 0)
                    grid.performItemClick(grid.selectedView, position, grid.getItemIdAtPosition(position))
            }
        }
        return true
    }
    private fun updateClock() { findViewById<TextView>(R.id.clock).text = DateFormat.getTimeFormat(this).format(Date()) }
    private fun refresh() {
        val request = ++generation
        worker.execute {
            val result = runCatching { repository.discover() }
            runOnUiThread {
                if (!active || isDestroyed || request != generation) return@runOnUiThread
                result.onSuccess { next ->
                    val changed = !loaded || apps.map { it.id to it.component } != next.map { it.id to it.component } || apps.map { it.label } != next.map { it.label }
                    apps = next; repository.initialise(apps)
                    if (changed) render(!loaded || grid.hasFocus())
                    else adapter.notifyDataSetChanged()
                    loaded = true
                }.onFailure {
                    if (!loaded) render(true)
                    findViewById<TextView>(R.id.hint).setText(R.string.load_error)
                }
            }
        }
    }
    private fun remember(position: Int) {
        val key = rows.getOrNull(position)?.id ?: "@all"
        if (allApps) selectedAll = key else selectedHome = key
    }
    private fun showPage(all: Boolean) { allApps = all; render(true) }
    private fun render(focus: Boolean) {
        val byId = apps.associateBy { it.id }
        rows = if (allApps) apps else FavouriteOrder.visible(repository.favourites(), byId.keys).mapNotNull { byId[it] } + listOf(null)
        findViewById<TextView>(R.id.title).setText(if (allApps) R.string.all_apps else R.string.your_apps)
        findViewById<TextView>(R.id.hint).text = if (allApps && rows.isEmpty()) "No launchable apps found. Settings is still available." else getString(R.string.hint)
        adapter.notifyDataSetChanged()
        if (focus) restoreSelection()
    }
    private fun restoreSelection() {
        if (rows.isEmpty()) { findViewById<View>(R.id.settings).requestFocus(); return }
        val key = if (allApps) selectedAll else selectedHome
        val index = rows.indexOfFirst { (it?.id ?: "@all") == key }.coerceAtLeast(0)
        grid.requestFocus(); grid.setSelection(index)
        grid.post { highlight() }
    }
    private fun highlight() {
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            val selected = grid.hasFocus() && grid.firstVisiblePosition + i == grid.selectedItemPosition
            child.isSelected = selected
            val desiredScale = if (selected) 1.035f else 1f
            if (child.scaleX != desiredScale) {
                child.animate().cancel()
                val scale = if (selected) 1.035f else 1f
                if (animations) child.animate().scaleX(scale).scaleY(scale).setDuration(110).setInterpolator(DecelerateInterpolator()).start()
                else { child.scaleX = scale; child.scaleY = scale }
            }
        }
    }
    private fun launch(app: AppEntry) {
        if (!open(Intent.makeMainActivity(app.component))) refresh()
    }
    private fun open(intent: Intent): Boolean = try { startActivity(intent); true } catch (_: RuntimeException) {
        Toast.makeText(this, "This action is unavailable on this TV.", Toast.LENGTH_LONG).show(); false
    }
    private fun manage(app: AppEntry) {
        val saved = repository.favourites()
        val visible = FavouriteOrder.visible(saved, apps.map { it.id }.toSet())
        val actions = mutableListOf("Open", if (app.id in saved) "Unpin from favourites" else "Pin to favourites")
        val index = visible.indexOf(app.id)
        if (index > 0) actions.add("Move earlier")
        if (index >= 0 && index < visible.lastIndex) actions.add("Move later")
        actions.add("App info")
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle(app.label).setItems(actions.toTypedArray()) { _, which ->
            when (actions[which]) {
                "Open" -> launch(app)
                "App info" -> open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${app.id}")))
                else -> {
                    when (actions[which]) {
                        "Move earlier", "Move later" -> {
                            val neighbour = visible[index + if (actions[which] == "Move earlier") -1 else 1]
                            repository.save(FavouriteOrder.move(saved, app.id, saved.indexOf(neighbour) - saved.indexOf(app.id)))
                        }
                        else -> repository.save(FavouriteOrder.toggle(saved, app.id))
                    }
                    render(true)
                }
            }
        }.setNegativeButton("Close", null).create()
        dialog?.window?.setWindowAnimations(0) // appear immediately; the open animation is slow on this GPU
        dialog?.show()
    }
    private fun inputs() {
        val manager = getSystemService(TV_INPUT_SERVICE) as? TvInputManager
        val available = runCatching { manager?.tvInputList.orEmpty().filter { it.isPassthroughInput && (Build.VERSION.SDK_INT < 24 || !it.isHidden(this)) } }.getOrDefault(emptyList())
        if (available.isEmpty()) {
            dialog = AlertDialog.Builder(this).setTitle("Inputs").setMessage("This firmware does not expose HDMI inputs to Still TV. Use the remote’s Input/Source button or system Settings.")
                .setPositiveButton("Settings") { _, _ -> open(Intent(Settings.ACTION_SETTINGS)) }.setNegativeButton("Close", null).show()
        } else {
            dialog = AlertDialog.Builder(this).setTitle("Inputs").setItems(available.map { it.loadLabel(this).toString() }.toTypedArray()) { _, index ->
                open(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(available[index].id)))
            }.setNegativeButton("Close", null).show()
        }
    }
    private fun help() {
        dialog = AlertDialog.Builder(this).setTitle("Still TV").setMessage("Use the arrows to move and OK to open. Hold OK (or press Menu) to pin, unpin or reorder an app. Back returns from All apps.\n\nUse your remote’s power button to turn off the TV.\n\nHome selection stays under your control in Android Settings. Your original launcher remains installed.")
            .setPositiveButton("Close", null).show()
    }
    private inner class Tiles : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@MainActivity).inflate(R.layout.app_tile, parent, false)
            val app = rows[position]
            val key = app?.id ?: "@all"
            view.tag = key
            val name = app?.label ?: getString(R.string.all_apps)
            view.findViewById<TextView>(R.id.label).text = name
            view.contentDescription = name
            view.animate().cancel()
            val selected = grid.hasFocus() && position == grid.selectedItemPosition
            view.isSelected = selected; view.scaleX = if (selected) 1.035f else 1f; view.scaleY = view.scaleX
            val icon = view.findViewById<ImageView>(R.id.icon)
            icon.setImageResource(if (app == null) R.drawable.ic_apps else R.drawable.ic_still)
            if (app != null) {
                val cached = repository.icons.get(key)
                if (cached != null) icon.setImageBitmap(cached)
                else worker.execute {
                    val bitmap = runCatching { repository.icon(app) }.getOrNull()
                    runOnUiThread { if (!isDestroyed && view.tag == key && bitmap != null) icon.setImageBitmap(bitmap) }
                }
            }
            return view
        }
    }
}
