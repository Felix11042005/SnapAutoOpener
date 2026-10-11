package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

class ActionPolicyTest {
    @Test fun everyActionIsBlockedInDiagnostics() {
        val s = DiagnosticSession(); val token = s.start(0); val snapshot = chatFixture()
        for (kind in ActionKind.entries) assertEquals("DIAGNOSTIC_ONLY", ActionPolicy.rejection(s, token, 1,
            ActionRequest(kind, snapshot, snapshot.nodes[3].bounds), snapshot))
    }
    @Test fun stopAndTimeoutBlockRetainedActions() {
        val s = DiagnosticSession(); val token = s.start(0, mode = SessionMode.INTERACTIVE); val snapshot = chatFixture()
        val request = ActionRequest(ActionKind.SCROLL_LIST, snapshot, snapshot.nodes[3].bounds)
        assertEquals("SESSION_INACTIVE", ActionPolicy.rejection(s, token, 30_000, request, snapshot))
        s.stop(); assertEquals("SESSION_INACTIVE", ActionPolicy.rejection(s, token, 1, request, snapshot))
    }
    @Test fun interactiveBoundaryRequiresFreshVerifiedListTarget() {
        val s = DiagnosticSession(); val token = s.start(0, mode = SessionMode.INTERACTIVE); val snapshot = chatFixture()
        val request = ActionRequest(ActionKind.SCROLL_LIST, snapshot, snapshot.nodes[3].bounds)
        assertNull(ActionPolicy.rejection(s, token, 1, request, snapshot))
        assertEquals("STALE_SCREEN", ActionPolicy.rejection(s, token, 1, request, snapshot.copy(windowId = 55)))
        assertEquals("LIST_TARGET_NOT_VERIFIED", ActionPolicy.rejection(s, token, 1, request.copy(target = null), snapshot))
    }
    @Test fun unsafeOpenAndViewerActionsRemainUnverified() {
        val s = DiagnosticSession(); val token = s.start(0, mode = SessionMode.INTERACTIVE); val snapshot = chatFixture()
        for (kind in listOf(ActionKind.OPEN_SNAP, ActionKind.ADVANCE_SNAP, ActionKind.BACK))
            assertNotNull(ActionPolicy.rejection(s, token, 1, ActionRequest(kind, snapshot), snapshot))
    }
}
