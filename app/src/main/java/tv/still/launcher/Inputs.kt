package tv.still.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.os.Build
import android.provider.Settings

/** HDMI/AV inputs (UI-13): the public passthrough inputs the TV offers to apps, opened through the documented
 * TvContract channel URI. Unchanged from the working 2.0 behaviour; no vendor code. */
internal object Inputs {
    fun show(activity: Activity, overlays: OverlayHost, open: (Intent) -> Boolean) {
        val manager = activity.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        val available = runCatching {
            manager?.tvInputList.orEmpty().filter { it.isPassthroughInput && (Build.VERSION.SDK_INT < 24 || !it.isHidden(activity)) }
        }.getOrDefault(emptyList()).sortedBy { it.loadLabel(activity).toString() }
        val cancel = MenuOverlay.Item(activity.getString(R.string.cancel)) {}
        if (available.isEmpty()) {
            overlays.show(MenuOverlay(activity.getString(R.string.inputs), listOf(
                MenuOverlay.Item(activity.getString(R.string.settings)) { open(Intent(Settings.ACTION_SETTINGS)) }, cancel),
                message = activity.getString(R.string.no_inputs)))
            return
        }
        overlays.show(MenuOverlay(activity.getString(R.string.inputs), available.map { input ->
            MenuOverlay.Item(input.loadLabel(activity).toString()) {
                open(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id)))
            }
        } + cancel))
    }
}
