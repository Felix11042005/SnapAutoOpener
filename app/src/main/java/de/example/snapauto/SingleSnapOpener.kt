package de.example.snapauto

fun interface SnapClickPort {
    /** Resolve the exact native control; call authorize immediately before ACTION_CLICK. */
    fun click(snapshot: UiSnapshot, target: UiNode, authorize: () -> Boolean): Boolean?
}

data class OpenResult(val attempted: Boolean, val accepted: Boolean, val reason: String)

/** Testable real action path: one native click attempt per explicitly started session. */
class SingleSnapOpener(
    private val session: DiagnosticSession,
    private val now: () -> Long,
    private val snapshot: () -> UiSnapshot?,
    private val port: SnapClickPort
) {
    fun open(token: Long, request: ActionRequest): OpenResult {
        if (request.kind != ActionKind.OPEN_SNAP) return OpenResult(false, false, "WRONG_ACTION")
        val rejected = ActionPolicy.rejection(session, token, now(), request, snapshot())
        if (rejected != null) return OpenResult(false, false, rejected)
        val target = request.snapshot.nodes.singleOrNull { it.index == request.nodeIndex }
            ?: return OpenResult(false, false, "TARGET_UNAVAILABLE")
        var attempted = false
        return try {
            val accepted = port.click(request.snapshot, target) {
                // STOP, navigation and timeout can occur while the native node is being resolved.
                if (ActionPolicy.rejection(session, token, now(), request, snapshot()) != null) false
                else session.claimOpenAttempt(token, now()).also { attempted = it }
            } == true
            if (accepted && attempted) session.markClickAccepted()
            OpenResult(attempted, accepted && attempted,
                if (accepted && attempted) "CLICK_ACCEPTED" else if (attempted) "CLICK_REJECTED" else "CLICK_ABORTED")
        } catch (_: RuntimeException) { OpenResult(attempted, false, "CLICK_EXCEPTION") }
    }
}
