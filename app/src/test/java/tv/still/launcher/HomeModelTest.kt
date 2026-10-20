package tv.still.launcher

import org.junit.Assert.*
import org.junit.Test
import tv.still.launcher.HomeItem.App
import tv.still.launcher.HomeItem.Folder

class HomeModelTest {
    private val preferred = listOf("yt", "hotstar", "crunchy", "f1", "jellyfin")

    // --- seeding and migration ---

    @Test fun firstRunPutsInstalledPreferredAppsFirstThenTheRestInDiscoveryOrder() {
        val installed = listOf("alpha", "crunchy", "yt", "zed", "jellyfin")
        val seeded = HomeModel.seed(installed, legacyFavourites = null, preferred = preferred)
        assertEquals(listOf("yt", "crunchy", "jellyfin", "alpha", "zed"), seeded.map { it.key })
    }

    @Test fun legacyFavouritesKeepTheirExactOrderAheadOfEverythingElse() {
        val installed = listOf("a", "b", "c", "yt")
        val seeded = HomeModel.seed(installed, legacyFavourites = listOf("c", "gone", "a"), preferred = preferred)
        assertEquals(listOf("c", "gone", "a", "b", "yt"), seeded.map { it.key })
    }

    @Test fun anEmptyLegacyListIsRespectedAndNotReplacedByDefaults() {
        val seeded = HomeModel.seed(listOf("b", "yt"), legacyFavourites = emptyList(), preferred = preferred)
        assertEquals(listOf("b", "yt"), seeded.map { it.key })
    }

    @Test fun duplicateLegacyEntriesAreCollapsed() {
        val seeded = HomeModel.seed(listOf("a", "b"), legacyFavourites = listOf("b", "b", "a"), preferred = preferred)
        assertEquals(listOf("b", "a"), seeded.map { it.key })
    }

    // --- reconcile and visibility ---

    @Test fun newlyInstalledAppsAreAppendedAndExistingOrderIsKept() {
        val stored = listOf(App("b"), App("a"))
        val result = HomeModel.reconcile(stored, installed = listOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), result.map { it.key })
    }

    @Test fun appsInsideFoldersAreNotDuplicatedAtTopLevel() {
        val stored = listOf(Folder("f1", "Sport", listOf("f1tv")), App("a"))
        val result = HomeModel.reconcile(stored, installed = listOf("a", "f1tv"))
        assertEquals(listOf("folder:f1", "a"), result.map { it.key })
    }

    @Test fun reconcileRemovesDuplicatesKeepingTheFirstOccurrence() {
        val stored = listOf(App("a"), Folder("f1", "X", listOf("a", "b")), App("b"))
        val result = HomeModel.reconcile(stored, installed = listOf("a", "b"))
        assertEquals(listOf(App("a"), Folder("f1", "X", listOf("b"))), result)
    }

    @Test fun disabledAppsAreHiddenButKeepTheirStoredPosition() {
        val full = listOf(App("a"), App("zee5"), App("b"))
        assertEquals(listOf("a", "b"), HomeModel.visible(full, setOf("a", "b")).map { it.key })
        assertEquals(listOf("a", "zee5", "b"), HomeModel.visible(full, setOf("a", "b", "zee5")).map { it.key })
    }

    @Test fun foldersShowOnlyAvailableAppsAndDisappearWhenNoneAreAvailable() {
        val full = listOf(Folder("f1", "X", listOf("a", "gone")), Folder("f2", "Y", listOf("gone2")))
        assertEquals(listOf(Folder("f1", "X", listOf("a"))), HomeModel.visible(full, setOf("a")))
    }

    // --- edits merged back without losing hidden apps ---

    @Test fun reorderingVisibleItemsKeepsHiddenAppsAnchoredAfterTheirPredecessor() {
        val full = listOf(App("a"), App("hidden"), App("b"), App("c"))
        val edited = listOf(App("c"), App("a"), App("b"))
        val merged = HomeModel.replaceVisible(full, edited, available = setOf("a", "b", "c"))
        assertEquals(listOf("c", "a", "hidden", "b"), merged.map { it.key })
    }

    @Test fun hiddenAppAtTheStartStaysAtTheStart() {
        val full = listOf(App("hidden"), App("a"), App("b"))
        val merged = HomeModel.replaceVisible(full, listOf(App("b"), App("a")), setOf("a", "b"))
        assertEquals(listOf("hidden", "b", "a"), merged.map { it.key })
    }

    @Test fun reorderingAFolderKeepsItsHiddenMembers() {
        val full = listOf(Folder("f1", "X", listOf("a", "hidden", "b")))
        val merged = HomeModel.reorderFolder(full, "f1", listOf("b", "a"), available = setOf("a", "b"))
        assertEquals(listOf(Folder("f1", "X", listOf("b", "a", "hidden"))), merged)
    }

    // --- folder operations ---

    @Test fun newFolderTakesTheAppsPlace() {
        val full = listOf(App("a"), App("b"), App("c"))
        val result = HomeModel.createFolder(full, "b", "f1", "Sport")
        assertEquals(listOf(App("a"), Folder("f1", "Sport", listOf("b")), App("c")), result)
    }

    @Test fun newFolderFromAnAppInsideAnotherFolderIsPlacedAfterThatFolder() {
        val full = listOf(Folder("f1", "X", listOf("a", "b")), App("c"))
        val result = HomeModel.createFolder(full, "b", "f2", "New")
        assertEquals(listOf(Folder("f1", "X", listOf("a")), Folder("f2", "New", listOf("b")), App("c")), result)
    }

    @Test fun movingAnAppIntoAFolderAppendsItAndRemovesItFromHome() {
        val full = listOf(App("a"), Folder("f1", "X", listOf("b")), App("c"))
        val result = HomeModel.moveToFolder(full, "a", "f1")
        assertEquals(listOf(Folder("f1", "X", listOf("b", "a")), App("c")), result)
    }

    @Test fun movingTheLastAppOutOfAFolderDissolvesIt() {
        val full = listOf(Folder("f1", "X", listOf("a")), Folder("f2", "Y", listOf("b")))
        val result = HomeModel.moveToFolder(full, "a", "f2")
        assertEquals(listOf(Folder("f2", "Y", listOf("b", "a"))), result)
    }

    @Test fun movingToHomePlacesTheAppRightAfterItsFolder() {
        val full = listOf(Folder("f1", "X", listOf("a", "b")), App("c"))
        val result = HomeModel.moveToHome(full, "a")
        assertEquals(listOf(Folder("f1", "X", listOf("b")), App("a"), App("c")), result)
    }

    @Test fun movingTheLastAppToHomeReplacesTheEmptyFolder() {
        val full = listOf(App("c"), Folder("f1", "X", listOf("a")))
        assertEquals(listOf(App("c"), App("a")), HomeModel.moveToHome(full, "a"))
    }

    @Test fun ungroupReturnsEveryMemberIncludingHiddenOnesInOrder() {
        val full = listOf(App("z"), Folder("f1", "X", listOf("a", "hidden", "b")), App("c"))
        assertEquals(listOf("z", "a", "hidden", "b", "c"), HomeModel.ungroup(full, "f1").map { it.key })
    }

    @Test fun renameChangesOnlyTheName() {
        val full = listOf(Folder("f1", "X", listOf("a")))
        assertEquals(listOf(Folder("f1", "Films", listOf("a"))), HomeModel.rename(full, "f1", "Films"))
    }

    @Test fun folderNamesAreTrimmedCollapsedAndBounded() {
        assertEquals("My Films", HomeModel.cleanName("  My   Films \n", fallback = "X"))
        assertEquals("X", HomeModel.cleanName("   ", fallback = "X"))
        assertEquals(HomeModel.MAX_NAME_LENGTH, HomeModel.cleanName("a".repeat(100), "X").length)
    }

    @Test fun newFolderIdsAreUniqueAndDeterministic() {
        val full = listOf(Folder("f1", "A", listOf("a")), Folder("f3", "B", listOf("b")))
        assertEquals("f4", HomeModel.newFolderId(full))
        assertEquals("f1", HomeModel.newFolderId(emptyList()))
    }

    @Test fun operationsOnUnknownIdsChangeNothing() {
        val full = listOf(App("a"), Folder("f1", "X", listOf("b")))
        assertEquals(full, HomeModel.moveToFolder(full, "a", "missing"))
        assertEquals(full, HomeModel.moveToHome(full, "a"))
        assertEquals(full, HomeModel.createFolder(full, "missing", "f2", "N"))
        assertEquals(full, HomeModel.ungroup(full, "missing"))
    }

    @Test fun everyOperationPreservesTheSetOfApps() {
        val full = listOf(App("a"), Folder("f1", "X", listOf("b", "c")), App("d"))
        val apps = setOf("a", "b", "c", "d")
        val results = listOf(
            HomeModel.createFolder(full, "a", "f2", "N"),
            HomeModel.moveToFolder(full, "d", "f1"),
            HomeModel.moveToHome(full, "c"),
            HomeModel.ungroup(full, "f1"),
            HomeModel.replaceVisible(full, full.reversed(), apps))
        for (r in results) assertEquals(apps, HomeModel.allApps(r).toSet())
        for (r in results) assertEquals(HomeModel.allApps(r).size, HomeModel.allApps(r).distinct().size)
    }
}
