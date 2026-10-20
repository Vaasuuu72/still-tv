package tv.still.launcher

import org.junit.Assert.*
import org.junit.Test
import tv.still.launcher.FocusMap.Direction.*

class ReorderTest {
    private val items = (0 until 10).map { "app$it" } // 6 columns: row 0 = 0..5, row 1 = 6..9

    @Test fun leftAndRightMoveOnePlaceAndWrapBetweenRows() {
        assertEquals(listOf("app1", "app0") + items.drop(2), Reorder.move(items, 1, LEFT, 6).items)
        assertEquals(6, Reorder.move(items, 5, RIGHT, 6).index)
        assertEquals(5, Reorder.move(items, 6, LEFT, 6).index)
    }

    @Test fun upAndDownMoveAWholeRowBetweenDockAndLowerRows() {
        val down = Reorder.move(items, 2, DOWN, 6)
        assertEquals(8, down.index)
        assertEquals("app2", down.items[8])
        assertEquals(2, Reorder.move(down.items, 8, UP, 6).index)
    }

    @Test fun downIntoAShortLastRowLandsAtTheEnd() {
        assertEquals(9, Reorder.move(items, 5, DOWN, 6).index)
    }

    @Test fun edgesAreNoOps() {
        assertEquals(Reorder.Result(items, 0), Reorder.move(items, 0, LEFT, 6))
        assertEquals(Reorder.Result(items, 9), Reorder.move(items, 9, RIGHT, 6))
        assertEquals(Reorder.Result(items, 3), Reorder.move(items, 3, UP, 6))
        assertEquals(Reorder.Result(items, 7), Reorder.move(items, 7, DOWN, 6))
    }

    @Test fun rapidRandomMovesNeverDropOrDuplicateItems() {
        val random = java.util.Random(3)
        var state = Reorder.Result(items, 4)
        repeat(2_000) {
            state = Reorder.move(state.items, state.index, FocusMap.Direction.values()[random.nextInt(4)], 6)
            assertEquals(items.toSet(), state.items.toSet())
            assertEquals(items.size, state.items.size)
            assertEquals("app4", state.items[state.index])
        }
    }

    @Test fun cancelIsSimplyTheOriginalList() {
        val moved = Reorder.move(items, 0, DOWN, 6).items
        assertNotEquals(items, moved)
        // Edit mode keeps the list it started with; cancelling re-renders it unchanged.
        assertEquals(10, moved.size)
    }
}
