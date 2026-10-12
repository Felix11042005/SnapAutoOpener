package de.example.snapauto

enum class ListNavigationKind { FILTER, UP, DOWN }
data class ListNavigationRequest(val kind: ListNavigationKind, val page: ConfirmedPage)
fun interface ListNavigationPort {
    fun perform(request: ListNavigationRequest, target: UiNode, region: Box, authorize: () -> Boolean): Boolean?
}

/** One-use adapter boundary for the exact Unread control or the verified content list. */
class ListNavigationOnce(private val session: DiagnosticSession, private val now: () -> Long,
    private val snapshot: () -> UiSnapshot?, private val inventory: FullInventory,
    private val port: ListNavigationPort) {
    private var used = false

    fun execute(token: Long, request: ListNavigationRequest): OpenResult {
        val expected = request.page.snapshot
        val evidence = if (inventory.phase == InventoryPhase.FILTER) ScreenRecognizer.inspect(expected)
            else ScreenRecognizer.inspectFiltered(expected)
        val surface = ChatListSurface.find(expected, evidence)
        val filter = ScreenRecognizer.unreadControl(expected)
        val target = if (request.kind == ListNavigationKind.FILTER) filter else surface?.node
        val region = if (request.kind == ListNavigationKind.FILTER) filter?.bounds else surface?.region
        fun allowed(): Boolean {
            val current = snapshot() ?: return false
            if (used || session.mode != SessionMode.INTERACTIVE || !session.accepts(token, now()) ||
                session.actionAttempts != 0 || !request.page.fresh(token, current, now()) ||
                !inventory.sameWindow(current) || evidence.screen != ScreenKind.CHAT_LIST || target == null || region == null) return false
            return if (request.kind == ListNavigationKind.FILTER)
                inventory.phase == InventoryPhase.FILTER && !inventory.filterAttempted &&
                    evidence.filter != FilterState.SELECTED &&
                    request.page.observation.chip?.state == FilterState.NOT_SELECTED
            else inventory.phase !in setOf(InventoryPhase.FILTER, InventoryPhase.FINISHED) &&
                evidence.filter != FilterState.NOT_SELECTED &&
                (request.kind == ListNavigationKind.DOWN) == (inventory.phase == InventoryPhase.SCAN) &&
                (filter == null || request.page.observation.chip?.state == FilterState.SELECTED)
        }
        if (!allowed() || target == null || region == null) return OpenResult(false, false, "LIST_NAVIGATION_BLOCKED")
        var attempted = false
        return try {
            val accepted = port.perform(request, target, region) {
                if (!allowed() || (request.kind == ListNavigationKind.FILTER && !inventory.claimFilter())) false
                else { used = true; attempted = true; true }
            } == true
            OpenResult(attempted, accepted && attempted, if (accepted && attempted) "LIST_NAVIGATION_ACCEPTED"
                else if (attempted) "LIST_NAVIGATION_REJECTED" else "LIST_NAVIGATION_ABORTED")
        } catch (_: RuntimeException) { OpenResult(attempted, false, "LIST_NAVIGATION_EXCEPTION") }
    }
}
