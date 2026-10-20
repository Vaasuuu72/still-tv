package tv.still.launcher
import org.junit.Assert.*
import org.junit.Test
class FavouriteOrderTest {
    @Test fun missingAndDuplicateAppsAreNotDisplayed() {
        assertEquals(listOf("b", "a"), FavouriteOrder.visible(listOf("b", "gone", "a", "b"), setOf("a", "b")))
    }
    @Test fun unpinAllDoesNotReseedFavourites() {
        assertEquals(emptyList<String>(), FavouriteOrder.toggle(listOf("a"), "a"))
    }
    @Test fun pinAppAppendsWithoutChangingOrder() {
        assertEquals(listOf("b", "a", "c"), FavouriteOrder.toggle(listOf("b", "a"), "c"))
    }
    @Test fun reorderClampsEdgesAndIgnoresMissingApp() {
        assertEquals(listOf("a", "b"), FavouriteOrder.move(listOf("a", "b"), "a", -1))
        assertEquals(listOf("a", "b"), FavouriteOrder.move(listOf("a", "b"), "b", 1))
        assertEquals(listOf("a", "b"), FavouriteOrder.move(listOf("a", "b"), "x", 1))
    }
    @Test fun reorderMovesInBothDirections() {
        assertEquals(listOf("b", "a", "c"), FavouriteOrder.move(listOf("a", "b", "c"), "b", -1))
        assertEquals(listOf("b", "c", "a"), FavouriteOrder.move(listOf("a", "b", "c"), "a", 2))
    }
    @Test fun defaultsUsePreferredAppsInPreferredOrderWhenInstalled() {
        val installed = listOf("c.crunchy", "b.youtube", "z.other", "a.hotstar")
        assertEquals(listOf("b.youtube", "a.hotstar", "c.crunchy"),
            FavouriteOrder.defaults(installed, listOf("b.youtube", "a.hotstar", "missing", "c.crunchy"), fallback = 5))
    }
    @Test fun defaultsFallBackToFirstInstalledAppsWhenNoPreferredAppExists() {
        assertEquals(listOf("a", "b"), FavouriteOrder.defaults(listOf("a", "b", "c"), listOf("x"), fallback = 2))
    }
    @Test fun withoutFavouritesTheGridShowsEveryAppAndWithThemOnlyTheRest() {
        assertEquals(listOf("a", "c"), FavouriteOrder.rest(listOf("a", "b", "c"), listOf("b", "gone")))
        assertEquals(listOf("a", "b"), FavouriteOrder.rest(listOf("a", "b"), emptyList()))
    }
    @Test fun temporarilyMissingAppRecoversItsPosition() {
        val saved = listOf("a", "b", "c")
        assertEquals(listOf("a", "c"), FavouriteOrder.visible(saved, setOf("a", "c")))
        assertEquals(saved, FavouriteOrder.visible(saved, setOf("a", "b", "c")))
    }
}
