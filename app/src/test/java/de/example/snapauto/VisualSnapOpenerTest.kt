package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

internal fun visualIcon() = VisualIcon(Box(0, 240, 1000, 400), Box(40, 310, 60, 330), 123)
internal fun visualProof(token: Long, s: UiSnapshot = chatFixture(), icon: VisualIcon = visualIcon()): VisualTapProof {
    val tracker = VisualConfirmationTracker()
    check(tracker.observe(token, s, icon, 100).proof == null)
    return tracker.observe(token, s, icon, 1600).proof!!
}

class VisualSnapOpenerTest {
    private val session = DiagnosticSession()
    private var now = 1700L
    private var current: UiSnapshot? = chatFixture()
    private var resolutions = 0
    private var taps = 0
    private var beforeAuthorize: () -> Unit = {}
    private var accepted = true
    private var throws = false
    private val opener = VisualSnapOpener(session, { now }, { current }, SnapTapPort { box, authorize ->
        resolutions++
        assertEquals(visualIcon().icon, box)
        beforeAuthorize()
        if (!authorize()) false else { taps++; if (throws) error("dispatch failure"); accepted }
    })
    private fun start() = session.start(0, mode = SessionMode.INTERACTIVE)
    private fun request(token: Long) = ActionRequest(ActionKind.OPEN_SNAP, chatFixture(),
        visualIcon().icon, visual = visualProof(token))

    @Test fun dispatchesOneRealPortTapAndStopsAtBudget() {
        val token = start(); val request = request(token)
        assertEquals(OpenResult(true, true, "TAP_DISPATCH_ACCEPTED"), opener.open(token, request))
        assertEquals("ONE_ACTION_LIMIT", opener.open(token, request).reason)
        assertEquals(1, taps); assertEquals(1, session.actionAttempts); assertEquals(1, session.acceptedClicks)
    }
    @Test fun stopBetweenProofCheckAndDispatchPreventsTap() {
        val token = start(); beforeAuthorize = { session.stop() }
        assertEquals("TAP_ABORTED", opener.open(token, request(token)).reason)
        assertEquals(0, taps); assertEquals(0, session.actionAttempts)
    }
    @Test fun timeoutBetweenProofCheckAndDispatchPreventsTap() {
        val token = start(); beforeAuthorize = { now = 30_001 }
        assertEquals("TAP_ABORTED", opener.open(token, request(token)).reason)
        assertEquals(0, taps)
    }
    @Test fun restartBetweenProofCheckAndDispatchPreventsTap() {
        val token = start(); beforeAuthorize = { session.start(now, mode = SessionMode.INTERACTIVE) }
        assertEquals("TAP_ABORTED", opener.open(token, request(token)).reason)
        assertEquals(0, taps)
    }
    @Test fun changedWindowBetweenProofCheckAndDispatchPreventsTap() {
        val token = start(); beforeAuthorize = { current = current!!.copy(windowId = 90) }
        assertEquals("TAP_ABORTED", opener.open(token, request(token)).reason)
        assertEquals(0, taps)
    }
    @Test fun rejectedTapConsumesAttemptAndDoesNotRetry() {
        val token = start(); accepted = false
        assertEquals(OpenResult(true, false, "TAP_REJECTED"), opener.open(token, request(token)))
        assertEquals("ONE_ACTION_LIMIT", opener.open(token, request(token)).reason)
        assertEquals(1, taps); assertEquals(0, session.acceptedClicks)
    }
    @Test fun exceptionConsumesAttemptAndDoesNotRetry() {
        val token = start(); throws = true
        assertEquals(OpenResult(true, false, "TAP_EXCEPTION"), opener.open(token, request(token)))
        assertEquals("ONE_ACTION_LIMIT", opener.open(token, request(token)).reason)
        assertEquals(1, taps)
    }
    @Test fun diagnosticsAndMissingProofNeverReachPort() {
        val token = session.start(0)
        assertEquals("DIAGNOSTIC_ONLY", opener.open(token, request(token)).reason)
        assertEquals("VISUAL_PROOF_REQUIRED", opener.open(token, request(token).copy(visual = null)).reason)
        assertEquals(0, resolutions)
    }
    @Test fun expiredProofCannotReachPort() {
        val token = start(); now = 4101
        assertEquals("VISUAL_PROOF_INVALID", opener.open(token, request(token)).reason)
        assertEquals(0, resolutions)
    }
    @Test fun targetBoundsCannotBeSubstitutedWithWholeConversation() {
        val token = start()
        assertEquals("VISUAL_PROOF_INVALID", opener.open(token, request(token).copy(target = visualIcon().row)).reason)
        assertEquals("VISUAL_PROOF_INVALID", opener.open(token, request(token).copy(nodeIndex = 4)).reason)
        assertEquals(0, resolutions)
    }
    @Test fun syntheticProofForOrdinaryChatOrExcludedRowIsRejected() {
        val token = start()
        val s = withExcludedRow(); current = s
        val excluded = VisualIcon(Box(0, 420, 1000, 580), Box(40, 500, 60, 520))
        val request = ActionRequest(ActionKind.OPEN_SNAP, s, excluded.icon, visual = visualProof(token, s, excluded))
        assertEquals("VISUAL_PROOF_INVALID", opener.open(token, request).reason)
        val ordinary = chatFixture().let { it.copy(nodes = it.nodes.map { n ->
            if (n.index == 5) n.copy(labels = listOf("Neuer Chat")) else n
        }) }
        current = ordinary
        assertEquals("VISUAL_PROOF_INVALID", opener.open(token, ActionRequest(ActionKind.OPEN_SNAP, ordinary,
            visualIcon().icon, visual = visualProof(token, ordinary))).reason)
        assertEquals(0, resolutions)
    }
}

class VisualConfirmationTrackerTest {
    @Test fun firstImageCannotAuthorizeAndSecondMustMatch() {
        val tracker = VisualConfirmationTracker(); val s = chatFixture(); val icon = visualIcon()
        assertEquals("SECOND_IMAGE_REQUIRED", tracker.observe(7, s, icon, 100).reason)
        val proof = tracker.observe(7, s, icon, 1600).proof!!
        assertTrue(proof.valid(7, 1700, s, ScreenRecognizer.inspect(s)))
        assertEquals("VISUAL_SELECTION_SEALED", tracker.observe(7, s, icon, 2000).reason)
    }
    @Test fun emptyFirstImageNeverWaitsForFutureTarget() {
        val tracker = VisualConfirmationTracker(); val s = chatFixture()
        assertEquals("NO_UNIQUE_SNAP_ICON", tracker.observe(7, s, null, 100).reason)
        assertNull(tracker.observe(7, s, visualIcon(), 1600).proof)
        tracker.reset()
        assertEquals("SECOND_IMAGE_REQUIRED", tracker.observe(8, s, visualIcon(), 2000).reason)
    }
    @Test fun changedImageWindowGenerationAndTimingSealFailure() {
        val s = chatFixture(); val icon = visualIcon()
        val second = listOf(
            Triple(7L, s, icon.copy(frameHash = 456)),
            Triple(7L, s.copy(windowId = 90), icon),
            Triple(8L, s, icon),
            Triple(7L, s, icon.copy(icon = Box(50, 310, 70, 330)))
        )
        for ((token, snap, target) in second) {
            val tracker = VisualConfirmationTracker(); tracker.observe(7, s, icon, 100)
            assertNull(tracker.observe(token, snap, target, 1600).proof)
            assertNull(tracker.observe(7, s, icon, 1700).proof)
        }
        for (time in listOf(99L, 399L, 2601L)) {
            val tracker = VisualConfirmationTracker(); tracker.observe(7, s, icon, 100)
            assertNull(tracker.observe(7, s, icon, time).proof)
        }
    }
    @Test fun oldProofExpiresAndCannotCrossSessions() {
        val s = chatFixture(); val proof = visualProof(7)
        assertFalse(proof.valid(8, 1700, s, ScreenRecognizer.inspect(s)))
        assertFalse(proof.valid(7, 4101, s, ScreenRecognizer.inspect(s)))
        assertFalse(proof.valid(7, 1599, s, ScreenRecognizer.inspect(s)))
    }
}
