package de.example.snapauto

fun interface SnapTapPort {
    fun tap(icon: Box, authorize: () -> Boolean): Boolean?
}

/** One actual gesture dispatch after two matching images and fresh semantic/session checks. */
class VisualSnapOpener(
    private val session: DiagnosticSession, private val now: () -> Long,
    private val snapshot: () -> UiSnapshot?, private val port: SnapTapPort
) {
    fun open(token: Long, request: ActionRequest): OpenResult {
        if (request.kind != ActionKind.OPEN_SNAP || request.visual == null || request.target == null)
            return OpenResult(false, false, "VISUAL_PROOF_REQUIRED")
        val rejection = ActionPolicy.rejection(session, token, now(), request, snapshot())
        if (rejection != null) return OpenResult(false, false, rejection)
        var attempted = false
        return try {
            val accepted = port.tap(request.target) {
                if (ActionPolicy.rejection(session, token, now(), request, snapshot()) != null) false
                else session.claimOpenAttempt(token, now()).also { attempted = it }
            } == true
            if (accepted && attempted) session.markClickAccepted()
            OpenResult(attempted, accepted && attempted,
                if (accepted && attempted) "TAP_DISPATCH_ACCEPTED" else if (attempted) "TAP_REJECTED" else "TAP_ABORTED")
        } catch (_: RuntimeException) { OpenResult(attempted, false, "TAP_EXCEPTION") }
    }
}
