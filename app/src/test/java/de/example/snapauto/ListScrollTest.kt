package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

internal fun shiftedRows(snapshot: UiSnapshot, delta: Int): UiSnapshot = snapshot.copy(nodes = snapshot.nodes.map {
    if (it.index >= 4) it.copy(bounds = it.bounds.copy(top = it.bounds.top + delta, bottom = it.bounds.bottom + delta)) else it
})

class ListScrollTest {
    @Test fun overlappingContactsSurviveDuplicateLabelsAndChangingNodeStructureWithinTheirRows() {
        val first = inventoryPage((1..8).map { "Fixture $it" }).let { s ->
            val names = s.nodes.filter { it.labels.singleOrNull()?.startsWith("Fixture") == true }
            s.copy(nodes = s.nodes + names.mapIndexed { i, n -> n.copy(index = 100 + i) })
        }
        val next = inventoryPage((3..10).map { "Fixture $it" }, filter = false)
        val evidence = ScrollMotion.inspect(first, next, ListNavigationKind.DOWN)
        assertEquals(ScrollMotionKind.EXPECTED, evidence.kind)
        assertEquals("CONTACT_ROW", evidence.source)
        assertEquals(6, evidence.commonRows)
        assertEquals(6, evidence.upward)
        val run = FullInventory(first); assertTrue(run.filterReady(confirmedPage(first)))
        run.page(first, ScreenRecognizer.inspectFiltered(first), ScreenRecognizer.inspectFiltered(first).rows, 1)
        assertEquals(InventoryStep.SCROLL_DOWN, run.page(next, ScreenRecognizer.inspectFiltered(next),
            ScreenRecognizer.inspectFiltered(next).rows, 2).step)
        assertEquals(10, run.roster.size)
    }
    @Test fun replacingWholePageExplainsAbortButOverlappingPageCanContinueInventory() {
        val initial = inventoryPage((1..8).map { "Fixture $it" })
        val replacement = inventoryPage((9..16).map { "Fixture $it" }, filter = false)
        val jump = ScrollMotion.inspect(initial, replacement, ListNavigationKind.DOWN)
        assertEquals(ScrollMotionKind.UNVERIFIED, jump.kind)
        assertEquals("NO_COMMON_ANCHORS", jump.reason)
        assertEquals(8, jump.beforeAnchors); assertEquals(8, jump.afterAnchors)
        assertEquals(0, jump.commonAnchors)

        // Preserve six rows across a shorter move; hide the filter as in the report.
        val overlapping = inventoryPage((3..10).map { "Fixture $it" }, filter = false)
        val motion = ScrollMotion.inspect(initial, overlapping, ListNavigationKind.DOWN)
        assertEquals(ScrollMotionKind.EXPECTED, motion.kind)
        assertEquals(6, motion.commonAnchors); assertEquals(6, motion.upward)
        assertFalse(motion.summary().contains("Fixture"))
        val run = FullInventory(initial); run.filterReady(confirmedPage(initial))
        fun step(s: UiSnapshot, hash: Long): InventoryDecision {
            val evidence = ScreenRecognizer.inspectFiltered(s)
            return run.page(s, evidence, evidence.rows, hash)
        }
        assertEquals(InventoryStep.SCROLL_DOWN, step(initial, 1).step)
        assertEquals(InventoryStep.SCROLL_DOWN, step(overlapping, 2).step)
        assertEquals(10, run.roster.size)
        assertEquals("DIRECTION_CONFIRMED", run.lastMotionEvidence!!.reason)
        assertFalse(run.roster.sealed)
    }
    @Test fun absentAndConflictingAnchorsHaveDifferentDiagnosticReasons() {
        val duplicates = inventoryPage(listOf("Duplicate", "Duplicate"))
        assertEquals("ANCHORS_MISSING", ScrollMotion.inspect(duplicates, shiftedRows(duplicates, 60), ListNavigationKind.DOWN).reason)
        val initial = inventoryPage(listOf("Fixture A", "Fixture B", "Fixture C"))
        val mixed = initial.copy(nodes = initial.nodes.map { when (it.index) {
            in 7..9 -> it.copy(bounds = it.bounds.copy(top = it.bounds.top - 40, bottom = it.bounds.bottom - 40))
            in 10..12 -> it.copy(bounds = it.bounds.copy(top = it.bounds.top + 40, bottom = it.bounds.bottom + 40))
            else -> it
        } })
        val evidence = ScrollMotion.inspect(initial, mixed, ListNavigationKind.DOWN)
        assertEquals("CONFLICTING_MOVEMENT", evidence.reason)
        assertEquals(1, evidence.upward); assertEquals(1, evidence.downward)
    }
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
            in 7..9 -> it.copy(bounds = it.bounds.copy(top = it.bounds.top - 40, bottom = it.bounds.bottom - 40))
            in 10..12 -> it.copy(bounds = it.bounds.copy(top = it.bounds.top + 40, bottom = it.bounds.bottom + 40))
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
