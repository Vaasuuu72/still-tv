package tv.still.launcher

import org.junit.Assert.*
import org.junit.Test

class HomeLayoutTest {
    // Kodak PATH_UHDXANDROID: 1920×1080 px at 320 dpi = 960×540 dp (2 px per dp).
    private val kodak = HomeLayout.compute(widthDp = 960, heightDp = 540)
    private val large = HomeLayout.compute(960, 540, large = true)

    @Test fun defaultIsSixColumnsAndLargeIsFive() {
        assertEquals(6, kodak.columns)
        assertEquals(5, large.columns)
    }

    @Test fun matchesTheSpecComposition() {
        // 1728 px content width, 258 × 155 px tiles, 36 px gaps, 90–100 px side and 65–75 px top insets.
        assertEquals(1728f, (960 - 2 * kodak.sideMarginDp) * 2, 1f)
        assertEquals(258f, kodak.tileWidthDp * 2, 1f)
        assertEquals(155f, kodak.tileHeightDp * 2, 1f)
        assertEquals(36f, kodak.gapDp * 2, 0.1f)
        assertTrue(kodak.sideMarginDp * 2 in 90f..100f)
        assertTrue(kodak.topMarginDp * 2 in 65f..75f)
    }

    @Test fun tilesFillTheSafeWidthExactlyInBothModes() {
        for (l in listOf(kodak, large)) {
            val row = l.columns * l.tileWidthDp + (l.columns - 1) * l.gapDp
            assertEquals(960 - 2 * l.sideMarginDp, row, 0.5f)
        }
    }

    @Test fun tilesAreFiveByThree() {
        assertEquals(5f / 3f, kodak.tileWidthDp / kodak.tileHeightDp, 0.001f)
        assertEquals(5f / 3f, large.tileWidthDp / large.tileHeightDp, 0.001f)
    }

    @Test fun focusGrowthStaysInsideTheGapsSoNeighboursAreNeverCovered() {
        for (l in listOf(kodak, large)) {
            assertTrue(l.tileWidthDp * (HomeLayout.PICKED_SCALE - 1f) < l.gapDp)
            assertTrue(l.tileHeightDp * (HomeLayout.PICKED_SCALE - 1f) / 2 + HomeLayout.FOCUS_LIFT_DP < HomeLayout.ROW_GAP_DP)
        }
        assertTrue(HomeLayout.FOCUS_SCALE in 1.04f..1.07f)
    }

    @Test fun topRowSitsLowWithTheNextRowVisibleAndARowPeekingBelow() {
        for (l in listOf(kodak, large)) {
            val secondRowBottom = l.shelfHeightDp + l.rowPitchDp + l.tileHeightDp
            assertTrue("second row fully visible", secondRowBottom < 540)
            assertTrue("third row peeks", l.shelfHeightDp + 2 * l.rowPitchDp < 540)
            assertTrue("shelf is substantial", l.shelfHeightDp > 540 * 0.4f)
        }
    }

    @Test fun shelfCompactsWhenTopShelfIsTurnedOff() {
        val off = HomeLayout.compute(960, 540, topShelf = false)
        assertTrue(off.shelfHeightDp < kodak.shelfHeightDp / 2)
        assertTrue(off.shelfHeightDp >= off.topMarginDp + HomeLayout.HEADER_HEIGHT_DP)
    }

    @Test fun scrolledRowAnchorLeavesThePreviousRowPartlyVisible() {
        assertTrue(kodak.rowAnchorDp - kodak.rowPitchDp + kodak.tileHeightDp > 0)
        assertTrue(kodak.rowAnchorDp + kodak.tileHeightDp < 540)
    }

    @Test fun smallerScreensStayUsable() {
        val small = HomeLayout.compute(640, 360)
        assertTrue(small.tileWidthDp > 70f)
        assertTrue(small.shelfHeightDp + small.tileHeightDp < 360)
    }

    @Test fun motionTokensAreWithinTheSpecRanges() {
        assertTrue(HomeLayout.FOCUS_MS in 130L..170L)
        assertTrue(HomeLayout.MODAL_MS in 100L..150L)
        assertTrue(HomeLayout.CORNER_DP in 12f..18f)
    }
}
