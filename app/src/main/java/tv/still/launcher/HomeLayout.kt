package tv.still.launcher

import kotlin.math.ceil

/** Home-screen geometry in dp for a given screen, following ten-foot safe-area guidance (5% margins).
 * Cards are 16:9 so Leanback banners fill them without distortion. */
internal data class HomeLayout(
    val sideMarginDp: Float, val topMarginDp: Float,
    val dockColumns: Int, val dockGapDp: Float, val dockCardWidthDp: Float, val dockCardHeightDp: Float,
    val gridColumns: Int, val gridGapDp: Float, val gridCardWidthDp: Float, val gridCardHeightDp: Float,
) {
    companion object {
        const val FOCUS_SCALE = 1.06f
        const val FOCUS_MS = 160L
        const val CORNER_DP = 12f
        private const val DOCK_COLUMNS = 5
        private const val DOCK_GAP_DP = 24f
        private const val GRID_GAP_DP = 20f
        private const val SIX_COLUMN_MIN_WIDTH_DP = 800f

        fun compute(widthDp: Int, heightDp: Int): HomeLayout {
            val side = ceil(widthDp * 0.05f)
            val top = ceil(heightDp * 0.05f)
            val available = widthDp - 2 * side
            val gridColumns = if (available >= SIX_COLUMN_MIN_WIDTH_DP) 6 else 5
            val dockWidth = (available - (DOCK_COLUMNS - 1) * DOCK_GAP_DP) / DOCK_COLUMNS
            val gridWidth = (available - (gridColumns - 1) * GRID_GAP_DP) / gridColumns
            return HomeLayout(side, top,
                DOCK_COLUMNS, DOCK_GAP_DP, dockWidth, dockWidth * 9f / 16f,
                gridColumns, GRID_GAP_DP, gridWidth, gridWidth * 9f / 16f)
        }
    }
}
