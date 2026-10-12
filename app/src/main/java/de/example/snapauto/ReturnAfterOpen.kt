package de.example.snapauto

import java.util.Locale

enum class ReturnStep { WAIT, BACK, LIST_READY, STOP }
data class ReturnDecision(val step: ReturnStep, val reason: String)

/** One contextual recovery BACK after a completed, accepted opening action.
 * A changed screen is NOT labelled a verified Snap viewer. No viewer image is captured.
 * Only successful list recognition permits another opening. */
class ReturnAfterOpen(private val before: UiSnapshot, private val openedAt: Long, private val filteredContext: Boolean = false) {
    private var departed = false
    private var backAt: Long? = null
    private var sealed = false

    fun poll(current: UiSnapshot, now: Long, stable: Boolean): ReturnDecision {
        if (sealed) return stop("RETURN_FINISHED")
        if (now < openedAt || now - openedAt >= 15_000) return stop("RETURN_TIMEOUT")
        if (!sameWindow(current)) return stop("RETURN_WINDOW_CHANGED")
        val evidence = if (filteredContext) ScreenRecognizer.inspectFiltered(current) else ScreenRecognizer.inspect(current)
        if (evidence.screen == ScreenKind.BLOCKED) return stop("RETURN_BLOCKED_${evidence.reason}")
        if (evidence.screen == ScreenKind.CHAT_LIST) {
            if (departed && stable) {
                sealed = true
                return ReturnDecision(ReturnStep.LIST_READY, "CHAT_LIST_RETURNED")
            }
            return if (!departed && now - openedAt >= 5_000) stop("NO_SCREEN_DEPARTURE")
                else wait("WAIT_LIST_DEPARTURE_OR_STABILITY")
        }
        if (backAt != null) return if (now - backAt!! >= 5_000) stop("BACK_WITHOUT_CHAT_LIST")
            else wait("WAIT_CHAT_LIST_AFTER_BACK")
        if (!returnable(current)) return if (now - openedAt >= 5_000) stop("RETURN_SCREEN_UNSAFE_OR_INCOMPLETE")
            else wait("WAIT_SCREEN_TRANSITION")
        departed = true
        return if (now - openedAt >= 5_000) ReturnDecision(ReturnStep.BACK, "CONTEXTUAL_BACK_AFTER_5_SECONDS")
            else wait("OPENED_SCREEN_DWELL")
    }

    fun claimBack(current: UiSnapshot, now: Long): Boolean {
        if (sealed || backAt != null || now - openedAt !in 5_000..14_999 || !returnable(current)) return false
        departed = true
        backAt = now // Consume before the Android call, including rejection/exception.
        return true
    }

    private fun sameWindow(current: UiSnapshot) = current.packageName == ScreenRecognizer.TARGET_PACKAGE &&
        current.windowId == before.windowId && current.bounds == before.bounds

    private fun returnable(current: UiSnapshot): Boolean {
        if (!sameWindow(current) || current == before || !current.bounds.valid || current.truncated ||
            current.nodeLimitReached || current.missingChildren > 0) return false
        val visible = current.nodes.filter { it.visible }
        if (visible.size < 2 || visible.any { it.editable || it.list ||
                ScreenRecognizer.markerKind(it) in setOf("MY_AI", "AD", "SNAP", "SNAP_PARTIAL") }) return false
        // A residual chat header/filter is not a safe departed screen, even if list recognition fails.
        if (visible.any { node -> node.labels.any {
                it.trim().lowercase(Locale.ROOT) in setOf("chat", "chats", "ungelesen", "unread") } }) return false
        return ScreenRecognizer.inspect(current).screen != ScreenKind.CHAT_LIST
    }

    private fun wait(reason: String) = ReturnDecision(ReturnStep.WAIT, reason)
    private fun stop(reason: String): ReturnDecision {
        sealed = true
        return ReturnDecision(ReturnStep.STOP, reason)
    }
}

fun interface SnapBackPort { fun back(authorize: () -> Boolean): Boolean? }
data class ReturnResult(val attempted: Boolean, val accepted: Boolean)

/** Rechecks the completed opening capability immediately at the BACK API boundary. */
class ReturnToList(private val session: DiagnosticSession, private val now: () -> Long,
    private val snapshot: () -> UiSnapshot?, private val port: SnapBackPort) {
    fun execute(token: Long, expected: UiSnapshot, flow: ReturnAfterOpen): ReturnResult {
        fun allowed() = session.mode == SessionMode.INTERACTIVE && session.accepts(token, now()) &&
            session.actionAttempts == 1 && session.acceptedClicks == 1 && snapshot() == expected
        if (!allowed()) return ReturnResult(false, false)
        var attempted = false
        return try {
            val accepted = port.back {
                if (!allowed() || !flow.claimBack(expected, now())) false
                else { attempted = true; true }
            } == true
            ReturnResult(attempted, accepted && attempted)
        } catch (_: RuntimeException) { ReturnResult(attempted, false) }
    }
}
