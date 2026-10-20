package tv.still.launcher

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import tv.still.launcher.HomeItem.App
import tv.still.launcher.HomeItem.Folder

/** JSON form of the Home list: `{"v":2,"items":[{"app":"pkg"},{"folder":"f1","name":"…","apps":["pkg"]}]}`.
 * Decoding is defensive: unknown versions and corrupt text return null; malformed entries are skipped. */
internal object HomeCodec {
    private const val VERSION = 2

    fun encode(items: List<HomeItem>): String {
        val array = JSONArray()
        for (item in items) array.put(when (item) {
            is App -> JSONObject().put("app", item.id)
            is Folder -> JSONObject().put("folder", item.id).put("name", item.name).put("apps", JSONArray(item.apps))
        })
        return JSONObject().put("v", VERSION).put("items", array).toString()
    }

    fun decode(text: String): List<HomeItem>? = try {
        val root = JSONObject(text)
        if (root.optInt("v") != VERSION) null
        else {
            val array = root.getJSONArray("items")
            (0 until array.length()).mapNotNull { entry(array.optJSONObject(it)) }
        }
    } catch (_: JSONException) { null }

    /** Reads the 1.x/2.0 `favourites` array of package names. */
    fun decodeLegacy(text: String): List<String>? = try {
        strings(JSONArray(text)).distinct()
    } catch (_: JSONException) { null }

    private fun entry(json: JSONObject?): HomeItem? {
        if (json == null) return null
        (json.opt("app") as? String)?.takeIf { it.isNotBlank() }?.let { return App(it) }
        val id = (json.opt("folder") as? String)?.takeIf { it.isNotBlank() } ?: return null
        val apps = json.optJSONArray("apps")?.let(::strings).orEmpty().distinct()
        if (apps.isEmpty()) return null
        return Folder(id, (json.opt("name") as? String).orEmpty(), apps)
    }

    private fun strings(array: JSONArray) = (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.takeIf(String::isNotBlank) }
}
