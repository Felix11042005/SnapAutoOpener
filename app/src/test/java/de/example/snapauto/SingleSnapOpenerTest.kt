package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

internal fun directFixture() = chatFixture().let { s -> s.copy(nodes = s.nodes.map {
    if (it.index == 5) it.copy(clickable = true, canClick = true, path = listOf(2, 0, 0)) else it
}) }

class SingleSnapOpenerTest {
    private val session = DiagnosticSession()
    private var now = 1L
    private var current: UiSnapshot? = directFixture()
    private var nativeClicks = 0
    private var resolutions = 0
    private var beforeAuthorize: () -> Unit = {}
    private var accepted = true
    private var throwOnClick = false
    private val port = SnapClickPort { _, _, authorize ->
        resolutions++
        beforeAuthorize()
        if (!authorize()) false else {
            nativeClicks++
            if (throwOnClick) throw IllegalStateException("native failure")
            accepted
        }
    }
    private val opener = SingleSnapOpener(session, { now }, { current }, port)
    private fun request(s: UiSnapshot = directFixture()) = ActionRequest(ActionKind.OPEN_SNAP, s, s.nodes[5].bounds, 5)
    private fun start() = session.start(0, mode = SessionMode.INTERACTIVE)

    @Test fun realPortReceivesExactlyOneAuthorizedControl() {
        val token = start()
        assertEquals(OpenResult(true, true, "CLICK_ACCEPTED"), opener.open(token, request()))
        assertEquals(1, nativeClicks)
        assertEquals(1, session.acceptedClicks)
        assertEquals("ONE_ACTION_LIMIT", opener.open(token, request()).reason)
        assertEquals(1, nativeClicks)
        assertEquals(1, resolutions)
    }
    @Test fun diagnosticModeNeverCallsPort() {
        val token = session.start(0)
        assertEquals("DIAGNOSTIC_ONLY", opener.open(token, request()).reason)
        assertEquals(0, resolutions)
    }
    @Test fun stopDuringNativeResolutionPreventsClick() {
        val token = start(); beforeAuthorize = { session.stop() }
        assertEquals("CLICK_ABORTED", opener.open(token, request()).reason)
        assertEquals(0, nativeClicks)
        assertEquals(0, session.actionAttempts)
    }
    @Test fun restartInvalidatesOldAuthorization() {
        val token = start(); beforeAuthorize = { start() }
        assertFalse(opener.open(token, request()).attempted)
        assertEquals(0, nativeClicks)
    }
    @Test fun timeoutDuringResolutionPreventsClick() {
        val token = start(); beforeAuthorize = { now = 30_000 }
        assertFalse(opener.open(token, request()).attempted)
        assertEquals(0, nativeClicks)
    }
    @Test fun changedListIncludingNewArrivalPreventsClick() {
        val token = start(); beforeAuthorize = { current = withExcludedRow() }
        assertFalse(opener.open(token, request()).attempted)
        assertEquals(0, nativeClicks)
    }
    @Test fun losingWindowPreventsClick() {
        val token = start(); beforeAuthorize = { current = null }
        assertFalse(opener.open(token, request()).attempted)
        assertEquals(0, nativeClicks)
    }
    @Test fun rejectedClickConsumesBudgetWithoutRetryOrSuccessCount() {
        val token = start(); accepted = false
        assertEquals(OpenResult(true, false, "CLICK_REJECTED"), opener.open(token, request()))
        assertEquals("ONE_ACTION_LIMIT", opener.open(token, request()).reason)
        assertEquals(1, nativeClicks)
        assertEquals(0, session.acceptedClicks)
    }
    @Test fun exceptionConsumesBudgetWithoutRetry() {
        val token = start(); throwOnClick = true
        assertEquals(OpenResult(true, false, "CLICK_EXCEPTION"), opener.open(token, request()))
        assertEquals("ONE_ACTION_LIMIT", opener.open(token, request()).reason)
        assertEquals(1, nativeClicks)
    }
    @Test fun clickableRowCannotSubstituteForNonClickableStatus() {
        val token = start(); current = chatFixture()
        assertEquals("DIRECT_SNAP_CONTROL_REQUIRED", opener.open(token, request(current!!)).reason)
        assertEquals(0, resolutions)
    }
    @Test fun unrelatedRowExclusionDoesNotPreventDirectSnapAction() {
        val s = withExcludedRow().let { it.copy(nodes = directFixture().nodes + it.nodes.drop(6)) }
        val token = start(); current = s
        assertTrue(opener.open(token, request(s)).accepted)
        assertEquals(1, nativeClicks)
    }
    @Test fun unreadFilterIsNotRequiredForDirectStatusAction() {
        val s = directFixture().let { it.copy(nodes = it.nodes.filter { n -> n.index != 2 }) }
        val token = start(); current = s
        val request = ActionRequest(ActionKind.OPEN_SNAP, s, s.nodes.last().bounds, 5)
        assertTrue(opener.open(token, request).accepted)
    }
    @Test fun spoofedTargetIndexOrBoundsDoesNotCallPort() {
        val token = start()
        assertEquals("DIRECT_SNAP_CONTROL_REQUIRED", opener.open(token, request().copy(nodeIndex = 4)).reason)
        assertEquals("DIRECT_SNAP_CONTROL_REQUIRED", opener.open(token, request().copy(target = Box(0, 0, 1, 1))).reason)
        assertEquals(0, resolutions)
    }
}
