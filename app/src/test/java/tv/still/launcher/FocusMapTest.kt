package tv.still.launcher
import org.junit.Assert.*
import org.junit.Test
import tv.still.launcher.FocusMap.Direction.*
import tv.still.launcher.FocusMap.Pos
class FocusMapTest {
    // header: Inputs, Settings | dock: 8 favourites in 5 columns | grid: 10 apps in 6 columns
    private val map = FocusMap(listOf(FocusMap.Section(2, 2), FocusMap.Section(8, 5), FocusMap.Section(10, 6)))
    @Test fun upFromTopRowGoesToHeaderNotFirstTile() {
        assertEquals(Pos(0, 1), map.move(Pos(1, 3), UP))
    }
    @Test fun upFromHeaderStays() {
        assertEquals(Pos(0, 0), map.move(Pos(0, 0), UP))
    }
    @Test fun upWithinASectionKeepsTheColumn() {
        assertEquals(Pos(1, 2), map.move(Pos(1, 7), UP))
        assertEquals(Pos(2, 3), map.move(Pos(2, 9), UP))
    }
    @Test fun leftAndRightStopAtRowEdgesWithoutWrapping() {
        assertEquals(Pos(1, 0), map.move(Pos(1, 0), LEFT))
        assertEquals(Pos(1, 4), map.move(Pos(1, 4), RIGHT))
        assertEquals(Pos(1, 5), map.move(Pos(1, 5), LEFT))
        assertEquals(Pos(2, 5), map.move(Pos(2, 5), RIGHT))
        assertEquals(Pos(1, 3), map.move(Pos(1, 2), RIGHT))
    }
    @Test fun rightStopsAtTheLastItemOfAPartialRow() {
        assertEquals(Pos(1, 7), map.move(Pos(1, 7), RIGHT))
        assertEquals(Pos(2, 9), map.move(Pos(2, 9), RIGHT))
    }
    @Test fun downIntoAShorterRowLandsOnItsLastItem() {
        assertEquals(Pos(1, 7), map.move(Pos(1, 4), DOWN))
        assertEquals(Pos(1, 5), map.move(Pos(1, 0), DOWN))
    }
    @Test fun downFromTheLastDockRowMapsByScreenPosition() {
        // dock column 0 of 5 -> grid column 0 of 6; dock column 2 (centre) -> grid column 3
        assertEquals(Pos(2, 0), map.move(Pos(1, 5), DOWN))
        assertEquals(Pos(2, 3), map.move(Pos(1, 7), DOWN))
    }
    @Test fun upFromTheGridTopRowMapsIntoTheLastDockRow() {
        assertEquals(Pos(1, 5), map.move(Pos(2, 0), UP))
        assertEquals(Pos(1, 7), map.move(Pos(2, 5), UP))   // last dock row has 3 items
    }
    @Test fun downFromHeaderEntersTheRememberedFirstRowItem() {
        assertEquals(Pos(1, 0), map.move(Pos(0, 1), DOWN))
        assertEquals(Pos(1, 3), map.move(Pos(0, 1), DOWN, remembered = 3))
        assertEquals(Pos(1, 0), map.move(Pos(0, 1), DOWN, remembered = 7)) // not in the first row
    }
    @Test fun downFromTheLastRowOfTheLastSectionStays() {
        assertEquals(Pos(2, 8), map.move(Pos(2, 8), DOWN))
    }
    @Test fun emptySectionsAreSkipped() {
        val noDock = FocusMap(listOf(FocusMap.Section(2, 2), FocusMap.Section(0, 5), FocusMap.Section(4, 6)))
        assertEquals(Pos(2, 0), noDock.move(Pos(0, 0), DOWN))
        assertEquals(Pos(0, 1), noDock.move(Pos(2, 2), UP))
    }
    @Test fun rapidRandomInputAlwaysLandsOnAnExistingItem() {
        val random = java.util.Random(7)
        var pos = Pos(1, 0)
        repeat(5_000) {
            pos = map.move(pos, values()[random.nextInt(4)], remembered = random.nextInt(9))
            assertTrue("invalid $pos", map.isValid(pos))
        }
    }
    @Test fun invalidStartPositionIsClampedToAValidItem() {
        assertTrue(map.isValid(map.move(Pos(1, 99), RIGHT)))
        assertTrue(map.isValid(map.move(Pos(5, 0), UP)))
    }
}
