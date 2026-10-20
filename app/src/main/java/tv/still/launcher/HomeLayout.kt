package tv.still.launcher

import kotlin.math.ceil

/** Home geometry in dp for a screen, from the 1920×1080 composition in the UI spec (§5.3):
 * ~5% side and ~6.5% top safe insets, six 5:3 tiles (five in Large mode) filling the safe width,
 * and a Top Shelf tall enough to leave the top row low on screen with the next row peeking below. */
data class HomeLayout(
    val sideMarginDp: Float,
    val topMarginDp: Float,
    val columns: Int,
    val gapDp: Float,
    val tileWidthDp: Float,
    val tileHeightDp: Float,
    /** Distance from one row's tile top to the next row's tile top (tile + focused label + gap). */
    val rowPitchDp: Float,
    /** Height of the region above the first row: header controls plus the Top Shelf. */
    val shelfHeightDp: Float,
    /** Screen y at which a focused lower row is held while scrolling, leaving the previous row in view. */
    val rowAnchorDp: Float,
) {
    companion object {
        const val FOCUS_SCALE = 1.055f
        const val PICKED_SCALE = 1.1f
        const val FOCUS_MS = 150L
        const val MODAL_MS = 120L
        const val CORNER_DP = 12f
        const val FOCUS_LIFT_DP = 3f
        const val HEADER_HEIGHT_DP = 44f
        const val LABEL_SPACE_DP = 30f
        const val ROW_GAP_DP = 12f
        const val DEFAULT_COLUMNS = 6
        const val LARGE_COLUMNS = 5
        private const val SIDE_FRACTION = 0.05f
        private const val TOP_FRACTION = 0.065f
        private const val DEFAULT_GAP_DP = 18f
        private const val LARGE_GAP_DP = 22f
        private const val TILE_ASPECT = 5f / 3f
        private const val NEXT_ROW_PEEK_DP = 36f
        private const val MAX_SHELF_FRACTION = 0.52f
        private const val COMPACT_SHELF_GAP_DP = 20f
        private const val ANCHOR_PITCH_FRACTION = 0.55f

        fun compute(widthDp: Int, heightDp: Int, large: Boolean = false, topShelf: Boolean = true): HomeLayout {
            val side = ceil(widthDp * SIDE_FRACTION)
            val top = ceil(heightDp * TOP_FRACTION)
            val available = widthDp - 2 * side
            val columns = if (large) LARGE_COLUMNS else DEFAULT_COLUMNS
            val gap = if (large) LARGE_GAP_DP else DEFAULT_GAP_DP
            val tileWidth = (available - (columns - 1) * gap) / columns
            val tileHeight = tileWidth / TILE_ASPECT
            val pitch = tileHeight + LABEL_SPACE_DP + ROW_GAP_DP
            val compact = top + HEADER_HEIGHT_DP + COMPACT_SHELF_GAP_DP
            val shelf = if (!topShelf) compact
                else (heightDp - 2 * pitch - NEXT_ROW_PEEK_DP).coerceIn(compact, heightDp * MAX_SHELF_FRACTION)
            return HomeLayout(side, top, columns, gap, tileWidth, tileHeight, pitch, shelf, top + pitch * ANCHOR_PITCH_FRACTION)
        }
    }
}
