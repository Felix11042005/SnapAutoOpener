package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

class ListNavigationTest {
    private val initial = inventoryPage().let { s -> s.copy(nodes = s.nodes.map {
        if (it.index == 2) it.copy(selected = false, selectionKnown = true) else it
    }) }

    private fun runFilter(change: (DiagnosticSession, () -> Unit) -> Unit = { _, _ -> },
        response: Boolean? = true, fail: Boolean = false): Pair<OpenResult, Int> {
        val session = DiagnosticSession(); val token = session.start(0, mode = SessionMode.INTERACTIVE)
        var current = initial; val run = FullInventory(initial); var calls = 0
        val action = ListNavigationOnce(session, { 700 }, { current }, run,
            ListNavigationPort { request, target, region, authorize ->
                assertEquals(ListNavigationKind.FILTER, request.kind)
                assertEquals(ScreenRecognizer.unreadControl(initial), target)
                assertEquals(target.bounds, region)
                change(session) { current = current.copy(windowId = 99) }
                if (!authorize()) false else { calls++; if (fail) error("synthetic failure") else response }
            })
        val request = ListNavigationRequest(ListNavigationKind.FILTER,
            confirmedPage(initial, token, FilterState.NOT_SELECTED))
        val result = action.execute(token, request)
        if (result.attempted) assertFalse(action.execute(token, request).attempted)
        return result to calls
    }

    @Test fun exactUnselectedFilterCanBeActivatedOnce() {
        val (result, calls) = runFilter(); assertTrue(result.accepted); assertEquals(1, calls)
    }
    @Test fun stopDuringResolutionBlocksFilterAtApiBoundary() {
        val (result, calls) = runFilter({ session, _ -> session.stop() })
        assertFalse(result.attempted); assertEquals(0, calls)
    }
    @Test fun restartDuringResolutionBlocksOldFilterProof() {
        val (result, calls) = runFilter({ session, _ -> session.start(650, mode = SessionMode.INTERACTIVE) })
        assertFalse(result.attempted); assertEquals(0, calls)
    }
    @Test fun windowChangeDuringResolutionBlocksFilter() {
        val (result, calls) = runFilter({ _, change -> change() })
        assertFalse(result.attempted); assertEquals(0, calls)
    }
    @Test fun rejectionAndExceptionCannotRetryFilter() {
        for ((response, fail) in listOf(false to false, null to false, true to true)) {
            val (result, calls) = runFilter(response = response, fail = fail)
            assertTrue(result.attempted); assertFalse(result.accepted); assertEquals(1, calls)
        }
    }
    @Test fun selectedFilterCannotBeToggledOff() {
        val s = inventoryPage(); val run = FullInventory(s); val session = DiagnosticSession()
        val token = session.start(0, mode = SessionMode.INTERACTIVE)
        val action = ListNavigationOnce(session, { 700 }, { s }, run, ListNavigationPort { _, _, _, _ ->
            fail("selected filter must not reach adapter"); true
        })
        assertFalse(action.execute(token, ListNavigationRequest(ListNavigationKind.FILTER, confirmedPage(s, token))).attempted)
    }
    @Test fun wholeFilterStripCannotSubstituteForExactUnreadButton() {
        val s = initial.copy(nodes = initial.nodes.map { if (it.index == 2) it.copy(bounds = Box(0, 150, 1000, 210)) else it })
        assertNull(ScreenRecognizer.unreadControl(s))
        val session = DiagnosticSession(); val token = session.start(0, mode = SessionMode.INTERACTIVE)
        val action = ListNavigationOnce(session, { 700 }, { s }, FullInventory(s), ListNavigationPort { _, _, _, _ ->
            fail("whole strip might contain My AI; must not click"); true
        })
        assertFalse(action.execute(token, ListNavigationRequest(ListNavigationKind.FILTER,
            confirmedPage(s, token, FilterState.NOT_SELECTED))).attempted)
    }
    @Test fun scrollingRequiresEstablishedContextAndCorrectDirection() {
        val s = inventoryPage(); val run = FullInventory(s); val session = DiagnosticSession()
        val token = session.start(0, mode = SessionMode.INTERACTIVE); var calls = 0
        val proof = confirmedPage(s, token)
        val action = ListNavigationOnce(session, { 700 }, { s }, run, ListNavigationPort { _, _, region, authorize ->
            assertTrue(region.top >= ScreenRecognizer.unreadControl(s)!!.bounds.bottom)
            if (authorize()) { calls++; true } else false
        })
        assertFalse(action.execute(token, ListNavigationRequest(ListNavigationKind.UP, proof)).attempted)
        assertTrue(run.filterReady(proof))
        assertFalse(action.execute(token, ListNavigationRequest(ListNavigationKind.UP, proof)).attempted)
        assertTrue(action.execute(token, ListNavigationRequest(ListNavigationKind.DOWN, proof)).accepted)
        assertFalse(action.execute(token, ListNavigationRequest(ListNavigationKind.DOWN, proof)).attempted)
        assertEquals(1, calls)
    }
    @Test fun diagnosticsExpiredProofAndOpenAttemptBlockNavigation() {
        for (case in 0..3) {
            val s = inventoryPage(); val run = FullInventory(s); val session = DiagnosticSession()
            val token = session.start(0, duration = if (case == 1) 500 else 30_000,
                mode = if (case == 0) SessionMode.DIAGNOSTIC else SessionMode.INTERACTIVE)
            val proof = confirmedPage(s, token); run.filterReady(proof)
            if (case == 3) assertTrue(session.claimOpenAttempt(token, 100))
            val action = ListNavigationOnce(session, { if (case == 2) 3200 else 700 }, { s }, run,
                ListNavigationPort { _, _, _, _ -> fail("blocked navigation reached adapter"); true })
            assertFalse(action.execute(token, ListNavigationRequest(ListNavigationKind.DOWN, proof)).attempted)
        }
    }
    @Test fun scrollRechecksStopImmediatelyBeforeDispatch() {
        val s = inventoryPage(); val run = FullInventory(s); val session = DiagnosticSession()
        val token = session.start(0, mode = SessionMode.INTERACTIVE); val proof = confirmedPage(s, token)
        run.filterReady(proof)
        val action = ListNavigationOnce(session, { 700 }, { s }, run, ListNavigationPort { _, _, _, authorize ->
            session.stop(); assertFalse(authorize()); false
        })
        assertFalse(action.execute(token, ListNavigationRequest(ListNavigationKind.DOWN, proof)).attempted)
    }
}
