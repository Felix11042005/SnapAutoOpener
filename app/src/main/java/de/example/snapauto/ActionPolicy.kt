package de.example.snapauto

enum class ActionKind { OPEN_SNAP, ADVANCE_SNAP, SCROLL_LIST, BACK }
data class ActionRequest(val kind: ActionKind, val snapshot: UiSnapshot, val target: Box? = null,
    val nodeIndex: Int? = null, val visual: VisualTapProof? = null)

/** Explicit interactive authorization, fresh screen and a direct semantic click target. */
object ActionPolicy {
    fun rejection(session: DiagnosticSession, token: Long, now: Long,
                  request: ActionRequest, current: UiSnapshot?): String? {
        if (!session.accepts(token, now)) return "SESSION_INACTIVE"
        if (session.mode != SessionMode.INTERACTIVE) return "DIAGNOSTIC_ONLY"
        if (session.actionAttempts > 0) return "ONE_ACTION_LIMIT"
        if (current == null || current != request.snapshot) return "STALE_SCREEN"
        val evidence = ScreenRecognizer.inspect(current)
        if (evidence.screen != ScreenKind.CHAT_LIST) return "UNVERIFIED_SCREEN"
        if (request.kind == ActionKind.OPEN_SNAP) {
            request.visual?.let { proof ->
                return if (request.nodeIndex == null && request.target == proof.target.icon &&
                    proof.valid(token, now, current, evidence)) null else "VISUAL_PROOF_INVALID"
            }
            return if (ScreenRecognizer.directSnapControls(current, evidence).any {
                it.index == request.nodeIndex && it.bounds == request.target
            }) null else "DIRECT_SNAP_CONTROL_REQUIRED"
        }
        if (evidence.filter != FilterState.SELECTED) return "UNVERIFIED_SCREEN"
        if (evidence.excludedRows.isNotEmpty()) return "EXCLUDED_CONTENT_VISIBLE"
        return when (request.kind) {
            ActionKind.OPEN_SNAP -> error("Handled above")
            ActionKind.ADVANCE_SNAP, ActionKind.BACK -> "VIEWER_NOT_VERIFIED"
            ActionKind.SCROLL_LIST -> if (request.target != null && current.nodes.any {
                it.visible && it.enabled && it.list && it.bounds == request.target && current.bounds.contains(it.bounds)
            }) null else "LIST_TARGET_NOT_VERIFIED"
        }
    }
}
