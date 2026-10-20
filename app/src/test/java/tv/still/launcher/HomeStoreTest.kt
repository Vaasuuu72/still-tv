package tv.still.launcher

import org.junit.Assert.*
import org.junit.Test
import tv.still.launcher.HomeItem.App
import tv.still.launcher.HomeItem.Folder

class HomeStoreTest {
    private val preferred = listOf("yt", "hotstar")

    @Test fun trueFirstRunSeedsPreferredAppsAndAsksToWrite() {
        val r = HomeStore.resolve(stored = null, legacy = null, installed = listOf("a", "hotstar", "yt"), preferred = preferred)
        assertEquals(listOf("yt", "hotstar", "a"), r.items.map { it.key })
        assertTrue(r.changed); assertFalse(r.corrupt)
    }

    @Test fun legacyFavouritesMigrateInOrderAndAreNotReplacedByDefaults() {
        val r = HomeStore.resolve(null, legacy = """["a","vendor.tool"]""", installed = listOf("a", "vendor.tool", "yt"), preferred = preferred)
        assertEquals(listOf("a", "vendor.tool", "yt"), r.items.map { it.key })
    }

    @Test fun unreadableLegacyValueFallsBackToDefaults() {
        val r = HomeStore.resolve(null, legacy = "garbage", installed = listOf("a", "yt"), preferred = preferred)
        assertEquals(listOf("yt", "a"), r.items.map { it.key })
    }

    @Test fun savedHomeWinsOverLegacyFavourites() {
        val saved = HomeCodec.encode(listOf(Folder("f1", "X", listOf("yt")), App("a")))
        val r = HomeStore.resolve(saved, legacy = """["a"]""", installed = listOf("a", "yt"), preferred = preferred)
        assertEquals(listOf("folder:f1", "a"), r.items.map { it.key })
        assertFalse("no write when nothing changed", r.changed)
    }

    @Test fun newlyInstalledAppIsAppendedAndPersisted() {
        val saved = HomeCodec.encode(listOf(App("a")))
        val r = HomeStore.resolve(saved, null, installed = listOf("a", "new"), preferred = preferred)
        assertEquals(listOf("a", "new"), r.items.map { it.key })
        assertTrue(r.changed)
    }

    @Test fun uninstalledAppIsKeptInStorageSoItsPositionRecovers() {
        val saved = HomeCodec.encode(listOf(App("gone"), App("a")))
        val r = HomeStore.resolve(saved, null, installed = listOf("a"), preferred = preferred)
        assertEquals(listOf("gone", "a"), r.items.map { it.key })
        assertFalse(r.changed)
    }

    @Test fun corruptSavedHomeIsFlaggedAndReseededFromLegacy() {
        val r = HomeStore.resolve("{broken", legacy = """["b"]""", installed = listOf("a", "b"), preferred = preferred)
        assertTrue(r.corrupt)
        assertEquals(listOf("b", "a"), r.items.map { it.key })
    }
}
