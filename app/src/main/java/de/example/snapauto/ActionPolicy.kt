package de.example.snapauto

enum class ActionKind { OPEN_SNAP, ADVANCE_SNAP, SCROLL_LIST, BACK }
data class ActionRequest(val kind: ActionKind, val snapshot: UiSnapshot, val target: Box? = null)

/** Fail-closed boundary for retained actions. The current UI starts DIAGNOSTIC only. */
object ActionPolicy {
    fun rejection(session: DiagnosticSession, token: Long, now: Long,
                  request: ActionRequest, current: UiSnapshot?): String? {
        if (!session.accepts(token, now)) return "SESSION_INACTIVE"
        if (session.mode != SessionMode.INTERACTIVE) return "DIAGNOSTIC_ONLY"
        if (current == null || current != request.snapshot) return "STALE_SCREEN"
        val evidence = ScreenRecognizer.inspect(current)
        if (evidence.screen != ScreenKind.CHAT_LIST || evidence.filter != FilterState.SELECTED) return "UNVERIFIED_SCREEN"
        return when (request.kind) {
            ActionKind.OPEN_SNAP -> "UNOPENED_SNAP_NOT_VERIFIED"
            ActionKind.ADVANCE_SNAP, ActionKind.BACK -> "VIEWER_NOT_VERIFIED"
            ActionKind.SCROLL_LIST -> if (request.target != null && current.nodes.any {
                it.visible && it.enabled && it.list && it.bounds == request.target && current.bounds.contains(it.bounds)
            }) null else "LIST_TARGET_NOT_VERIFIED"
        }
    }
}
