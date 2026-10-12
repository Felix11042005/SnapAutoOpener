package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

class InventoryOpeningTest {
    @Test fun collectTwoPagesThenOpenOnlyOriginalsWithBackAndFreshNavigationProofs() {
        val top = inventoryPage(listOf("Fixture A")); val bottom = inventoryPage(listOf("Fixture B"))
        var current = top; var now = 0L; val session = DiagnosticSession(); val run = FullInventory(top)
        var opens = 0; var backs = 0; var scrolls = 0
        fun page(): Pair<Long, ConfirmedPage> {
            val token = session.start(now, mode = SessionMode.INTERACTIVE)
            now += 600
            return token to confirmedPage(current, token, now = now)
        }
        run.filterReady(page().second)
        fun decide(): InventoryDecision = run.page(current, ScreenRecognizer.inspectFiltered(current),
            ScreenRecognizer.inspectFiltered(current).rows)
        fun navigate(decision: InventoryDecision) {
            val (token, proof) = page()
            val kind = if (decision.step == InventoryStep.SCROLL_UP) ListNavigationKind.UP else ListNavigationKind.DOWN
            val action = ListNavigationOnce(session, { now }, { current }, run, ListNavigationPort { _, _, _, authorize ->
                if (authorize()) { scrolls++; true } else false
            })
            assertTrue(action.execute(token, ListNavigationRequest(kind, proof)).accepted)
        }
        navigate(decide()) // Start by collecting the visible page and moving down.
        current = bottom
        repeat(2) { navigate(decide()) } // Collect second page and verify the boundary.
        // The next decision seals the inventory and opens here, without seeking the top first.
        val first = decide()
        assertEquals(InventoryStep.OPEN, first.step)
        assertTrue(run.roster.sealed); assertEquals(2, run.roster.size); assertEquals(0, opens)
        repeat(2) { index ->
            val decision = decide(); assertEquals(InventoryStep.OPEN, decision.step)
            val row = decision.row!!; val before = current
            val (token, _) = page()
            val control = ScreenRecognizer.directSnapControls(current).single { row.contains(it.bounds) }
            val opener = SingleSnapOpener(session, { now }, { current }, SnapClickPort { _, _, authorize ->
                if (authorize()) { opens++; true } else false
            })
            assertTrue(opener.open(token, ActionRequest(ActionKind.OPEN_SNAP, current, control.bounds, control.index)).accepted)
            assertTrue(run.roster.consume(current, row))
            val flow = ReturnAfterOpen(before, now, filteredContext = true)
            current = departedFixture(before); now += 5_000
            val back = ReturnToList(session, { now }, { current }, SnapBackPort { authorize ->
                if (authorize()) { backs++; true } else false
            })
            assertTrue(back.execute(token, current, flow).accepted)
            current = if (index == 0) inventoryPage(listOf("Later arrival")) else inventoryPage(emptyList())
            now += 1_000; assertEquals(ReturnStep.LIST_READY, flow.poll(current, now, true).step)
            if (index == 0) { navigate(decide()); current = top }
        }
        assertEquals(InventoryStep.DONE, decide().step)
        assertEquals(2, opens); assertEquals(2, backs); assertTrue(scrolls > 2)
    }
    @Test fun frozenMovedVisualTargetStillNeedsFreshMatchingRowImagesAtTapBoundary() {
        val initial = inventoryPage(listOf("Fixture A")); val roster = InventoryRoster()
        roster.add(initial, ScreenRecognizer.inspect(initial), ScreenRecognizer.inspect(initial).rows); roster.seal()
        val current = inventoryPage(listOf("Later arrival", "Fixture A"))
        val row = roster.nextVisible(current, ScreenRecognizer.inspect(current))!!
        val pixels = IntArray(1000 * 2000) { 0xff111111.toInt() }
        for (y in row.top + 110 until row.top + 122) for (x in 120 until 132) pixels[y * 1000 + x] = 0xffff0033.toInt()
        val icon = VisualSnapTarget.detect(pixels, 1000, 2000, listOf(row), 1f)!!
        val session = DiagnosticSession(); val token = session.start(0, mode = SessionMode.INTERACTIVE)
        val tracker = PageConfirmation(); val observation = PageObservation(ChipObservation(FilterState.SELECTED, 1), listOf(icon))
        assertNull(tracker.observe(token, current, observation, 100))
        val page = tracker.observe(token, current, observation, 600)!!
        val proof = VisualTapProof.confirmed(token, current, page.observation.icons.single(), page.firstAt, page.confirmedAt)
        var taps = 0
        val action = VisualSnapOpener(session, { 700 }, { current }, SnapTapPort { _, authorize ->
            if (authorize()) { taps++; true } else false
        })
        val request = ActionRequest(ActionKind.OPEN_SNAP, current, icon.icon, visual = proof)
        assertTrue(action.open(token, request).accepted)
        assertFalse(action.open(token, request).accepted)
        assertTrue(roster.consume(current, row)); assertEquals(0, roster.remaining); assertEquals(1, taps)
    }
}
