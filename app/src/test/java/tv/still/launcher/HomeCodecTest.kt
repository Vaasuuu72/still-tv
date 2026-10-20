package tv.still.launcher

import org.junit.Assert.*
import org.junit.Test
import tv.still.launcher.HomeItem.App
import tv.still.launcher.HomeItem.Folder

class HomeCodecTest {
    @Test fun roundTripPreservesAppsFoldersNamesAndOrder() {
        val items = listOf(App("a"), Folder("f1", "Films & \"TV\" ✨", listOf("b", "c")), App("d"))
        assertEquals(items, HomeCodec.decode(HomeCodec.encode(items)))
    }

    @Test fun invalidJsonIsReportedAsNull() {
        assertNull(HomeCodec.decode("{not json"))
        assertNull(HomeCodec.decode("""{"v":99,"items":[]}"""))
    }

    @Test fun malformedEntriesAreSkippedNotFatal() {
        val json = """{"v":2,"items":[{"app":"a"},{"bogus":1},{"folder":"f1","name":"X","apps":["b",5]},{"folder":"","apps":[]}]}"""
        assertEquals(listOf(App("a"), Folder("f1", "X", listOf("b"))), HomeCodec.decode(json))
    }

    @Test fun legacyFavouritesArrayIsRead() {
        assertEquals(listOf("b", "a"), HomeCodec.decodeLegacy("""["b","a","b"]"""))
        assertNull(HomeCodec.decodeLegacy("oops"))
    }
}
