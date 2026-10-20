package tv.still.launcher

import org.junit.Assert.*
import org.junit.Test

class SearchMatcherTest {
    private val apps = listOf("cr" to "Crunchyroll", "f1" to "F1 TV", "jf" to "Jellyfin", "yt" to "YouTube", "pv" to "Prime Vidéo")

    @Test fun emptyQueryListsEveryAppInGivenOrder() {
        assertEquals(apps.map { it.first }, SearchMatcher.match(apps, "  "))
    }

    @Test fun matchingIsCaseInsensitive() {
        assertEquals(listOf("yt"), SearchMatcher.match(apps, "YOUT"))
    }

    @Test fun prefixMatchesComeBeforeWordAndSubstringMatches() {
        val tubes = listOf("a" to "Smart Tube", "c" to "YouTube", "b" to "Tubi")
        assertEquals(listOf("b", "a", "c"), SearchMatcher.match(tubes, "tub"))
        assertEquals(listOf("cr", "jf"), SearchMatcher.match(apps, "ll")) // same rank keeps the given order
    }

    @Test fun accentsAreIgnored() {
        assertEquals(listOf("pv"), SearchMatcher.match(apps, "video"))
    }

    @Test fun noMatchGivesAnEmptyList() {
        assertTrue(SearchMatcher.match(apps, "zzz").isEmpty())
    }
}
