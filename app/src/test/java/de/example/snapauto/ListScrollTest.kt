package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

internal fun shiftedRows(snapshot: UiSnapshot, delta: Int): UiSnapshot = snapshot.copy(nodes = snapshot.nodes.map {
    if (it.index >= 4) it.copy(bounds = it.bounds.copy(top = it.bounds.top + delta, bottom = it.bounds.bottom + delta)) else it
})

class ListScrollTest {
    @Test fun laterRowsRequireFingerUpFromCenterAndEarlierRowsFingerDown() {
        for (region in listOf(Box(0, 289, 1080, 2153), Box(40, 500, 840, 1700))) {
            val down = ListScroll.plan(region, ListNavigationKind.DOWN)!!
            val up = ListScroll.plan(region, ListNavigationKind.UP)!!
            assertEquals((region.top + region.bottom) / 2f, down.startY, .01f)
            assertTrue(down.endY < down.startY); assertTrue(up.endY > up.startY)
            assertTrue(down.endY > region.top && up.endY < region.bottom)
            assertEquals(450L, down.duration)
        }
        assertNull(ListScroll.plan(Box(0, 0, 0, 200), ListNavigationKind.DOWN))
        assertNull(ListScroll.plan(Box(0, 0, 200, 200), ListNavigationKind.FILTER))
    }
    @Test fun verifiesContentMovementInsteadOfEquatingGestureAcceptanceWithProgress() {
        val s = inventoryPage(listOf("Fixture A", "Fixture B", "Fixture C"))
        assertEquals(ScrollMotionKind.EXPECTED, ScrollMotion.assess(s, shiftedRows(s, -60), ListNavigationKind.DOWN))
        assertEquals(ScrollMotionKind.REVERSED, ScrollMotion.assess(s, shiftedRows(s, 60), ListNavigationKind.DOWN))
        assertEquals(ScrollMotionKind.EXPECTED, ScrollMotion.assess(s, shiftedRows(s, 60), ListNavigationKind.UP))
        assertEquals(ScrollMotionKind.REVERSED, ScrollMotion.assess(s, shiftedRows(s, -60), ListNavigationKind.UP))
    }
    @Test fun reportedOneOrTwoPixelJitterDoesNotCountAsListTravel() {
        val s = inventoryPage()
        for (delta in listOf(-2, -1, 0, 1, 2)) assertEquals(ScrollMotionKind.UNCHANGED,
            ScrollMotion.assess(s, shiftedRows(s, delta), ListNavigationKind.DOWN))
    }
    @Test fun nonOverlappingOrDuplicateAnchorsDoNotInventDirection() {
        val s = inventoryPage(listOf("Fixture A"))
        assertEquals(ScrollMotionKind.UNVERIFIED, ScrollMotion.assess(s, inventoryPage(listOf("Fixture B")), ListNavigationKind.DOWN))
        val duplicates = inventoryPage(listOf("Duplicate", "Duplicate"))
        assertEquals(ScrollMotionKind.UNVERIFIED, ScrollMotion.assess(duplicates, shiftedRows(duplicates, 40), ListNavigationKind.DOWN))
        assertEquals(ScrollMotionKind.UNCHANGED, ScrollMotion.assess(duplicates, duplicates, ListNavigationKind.DOWN, true))
    }
    @Test fun oppositeAnchorMovementsAndWindowChangesStayUnverified() {
        val s = inventoryPage(listOf("Fixture A", "Fixture B", "Fixture C"))
        val mixed = s.copy(nodes = s.nodes.map { when (it.index) {
            8 -> it.copy(bounds = it.bounds.copy(top = it.bounds.top + 40, bottom = it.bounds.bottom + 40))
            11 -> it.copy(bounds = it.bounds.copy(top = it.bounds.top - 40, bottom = it.bounds.bottom - 40))
            else -> it
        } })
        assertEquals(ScrollMotionKind.UNVERIFIED, ScrollMotion.assess(s, mixed, ListNavigationKind.DOWN))
        assertEquals(ScrollMotionKind.UNVERIFIED, ScrollMotion.assess(s, s.copy(windowId = 90), ListNavigationKind.DOWN, true))
    }
    @Test fun reversedObservedScrollStopsInventoryBeforeAnyOpening() {
        val s = inventoryPage(listOf("Fixture A", "Fixture B", "Fixture C")); val run = FullInventory(s)
        run.filterReady(confirmedPage(s))
        assertEquals(InventoryStep.SCROLL_DOWN, run.page(s, ScreenRecognizer.inspect(s), ScreenRecognizer.inspect(s).rows, 1).step)
        val reversed = shiftedRows(s, 60)
        val decision = run.page(reversed, ScreenRecognizer.inspect(reversed), ScreenRecognizer.inspect(reversed).rows, 2)
        assertEquals(InventoryStep.STOP, decision.step); assertEquals("SCROLL_DIRECTION_REVERSED", decision.reason)
        assertFalse(run.roster.sealed)
    }
    @Test fun unknownMovementStopsInsteadOfTreatingRejectionAsListEnd() {
        val s = inventoryPage(listOf("Fixture A")); val run = FullInventory(s); run.filterReady(confirmedPage(s))
        run.page(s, ScreenRecognizer.inspect(s), ScreenRecognizer.inspect(s).rows, 1)
        val different = inventoryPage(listOf("Fixture B"))
        assertEquals("SCROLL_MOVEMENT_UNVERIFIED",
            run.page(different, ScreenRecognizer.inspect(different), ScreenRecognizer.inspect(different).rows, 2).reason)
    }
}
