package tv.still.launcher
import org.junit.Assert.*
import org.junit.Test
class HomeLayoutTest {
    @Test fun kodak1080pUsesFiveDockAndSixGridColumns() {
        val layout = HomeLayout.compute(widthDp = 960, heightDp = 540)
        assertEquals(5, layout.dockColumns)
        assertEquals(6, layout.gridColumns)
    }
    @Test fun cardsFillTheSafeWidthExactly() {
        val l = HomeLayout.compute(960, 540)
        val dockRow = l.dockColumns * l.dockCardWidthDp + (l.dockColumns - 1) * l.dockGapDp
        val gridRow = l.gridColumns * l.gridCardWidthDp + (l.gridColumns - 1) * l.gridGapDp
        assertEquals(960 - 2 * l.sideMarginDp, dockRow, 1f)
        assertEquals(960 - 2 * l.sideMarginDp, gridRow, 1f)
    }
    @Test fun cardsAre16by9ForLeanbackBanners() {
        val l = HomeLayout.compute(960, 540)
        assertEquals(16f / 9f, l.dockCardWidthDp / l.dockCardHeightDp, 0.01f)
        assertEquals(16f / 9f, l.gridCardWidthDp / l.gridCardHeightDp, 0.01f)
    }
    @Test fun safeAreaMarginsFollowTenFootGuidance() {
        val l = HomeLayout.compute(960, 540)
        assertTrue(l.sideMarginDp >= 0.05f * 960 - 1)
        assertTrue(l.topMarginDp >= 0.05f * 540 - 1)
    }
    @Test fun focusScaleFitsInsideTheGaps() {
        val l = HomeLayout.compute(960, 540)
        val growth = l.dockCardWidthDp * (HomeLayout.FOCUS_SCALE - 1f)
        assertTrue(growth < l.dockGapDp)
        assertTrue(l.gridCardWidthDp * (HomeLayout.FOCUS_SCALE - 1f) < l.gridGapDp)
        assertTrue(HomeLayout.FOCUS_SCALE in 1.05f..1.07f)
    }
    @Test fun narrowerScreensDropToFiveGridColumns() {
        assertEquals(5, HomeLayout.compute(640, 360).gridColumns)
    }
    @Test fun focusAnimationIsWithinTheDesignRange() {
        assertTrue(HomeLayout.FOCUS_MS in 120L..180L)
    }
}
