package de.example.snapauto

import java.util.Locale

data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val valid get() = width > 0 && height > 0
    fun contains(other: Box) = valid && other.valid && other.left >= left &&
        other.top >= top && other.right <= right && other.bottom <= bottom
}

data class UiNode(
    val index: Int, val parent: Int?, val labels: List<String>, val bounds: Box,
    val visible: Boolean = true, val enabled: Boolean = true,
    val selected: Boolean = false, val editable: Boolean = false,
    val clickable: Boolean = false, val list: Boolean = false
)

data class UiSnapshot(
    val packageName: String, val windowId: Int, val bounds: Box,
    val nodes: List<UiNode>, val truncated: Boolean = false
)

enum class ScreenKind { UNKNOWN, BLOCKED, CHAT_LIST }
enum class FilterState { UNKNOWN, NOT_SELECTED, SELECTED }
data class Recognition(
    val screen: ScreenKind, val filter: FilterState = FilterState.UNKNOWN,
    val reason: String, val rows: List<Box> = emptyList()
)

/** Positive evidence only. A status label or colored square is never proof of an unopened Snap. */
object ScreenRecognizer {
    const val TARGET_PACKAGE = "com.snapchat.android"
    private val snapStatus = Regex("(?:\\d+ )?(?:neuer snap|neue snaps|new snap|new snaps)")
    private val excluded = Regex("(?:^|[^\\p{L}])(?:my ai|werbung|anzeige|gesponsert|sponsored|advertisement|ad)(?:$|[^\\p{L}])")
    private fun normalized(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun inspect(snapshot: UiSnapshot): Recognition {
        fun unknown(reason: String) = Recognition(ScreenKind.UNKNOWN, reason = reason)
        if (snapshot.packageName != TARGET_PACKAGE) return unknown("WRONG_PACKAGE")
        if (!snapshot.bounds.valid || snapshot.truncated) return unknown("INCOMPLETE_TREE")
        val visible = snapshot.nodes.filter { it.visible && it.enabled && snapshot.bounds.contains(it.bounds) }
        // An editable node anywhere visible blocks capture, including a search box (conservative).
        if (snapshot.nodes.any { it.visible && it.editable }) {
            return Recognition(ScreenKind.BLOCKED, reason = "EDITABLE_FIELD")
        }
        if (visible.any { node -> node.labels.any { excluded.containsMatchIn(normalized(it)) } }) {
            return Recognition(ScreenKind.BLOCKED, reason = "AD_OR_MY_AI")
        }
        fun matching(vararg labels: String) = visible.filter { node -> node.labels.any { normalized(it) in labels } }
            .distinctBy { it.bounds }
        val headers = matching("chat", "chats")
        val filters = matching("ungelesen", "unread")
        if (headers.size != 1 || filters.size != 1) return unknown("HEADER_OR_FILTER_AMBIGUOUS")
        val header = headers.single()
        val filter = filters.single()
        if (header.bounds.bottom > filter.bounds.top) return unknown("HEADER_FILTER_ORDER")
        val lists = visible.filter { it.list && it.bounds.bottom > filter.bounds.bottom }
        if (lists.isEmpty()) return unknown("NO_LIST_STRUCTURE")
        val filterState = if (filter.selected) FilterState.SELECTED else FilterState.NOT_SELECTED
        val byIndex = snapshot.nodes.associateBy { it.index }
        val rows = visible.filter { node -> node.labels.any { snapStatus.matches(normalized(it)) } }
            .mapNotNull { status ->
                if (status.bounds.top < filter.bounds.bottom) return@mapNotNull null
                var current: UiNode? = status
                var row: Box? = null
                repeat(8) {
                    val node = current ?: return@repeat
                    if (row == null && node.clickable && !node.list && node.bounds.contains(status.bounds) &&
                        node.bounds.height <= snapshot.bounds.height / 3 &&
                        lists.any { it.bounds.contains(node.bounds) }) row = node.bounds
                    current = node.parent?.let(byIndex::get)
                }
                row
            }.distinct().take(30)
        return Recognition(ScreenKind.CHAT_LIST, filterState, "SEMANTIC_LIST", rows)
    }
}

/** Two identical bounded snapshots; changed coordinates/selection invalidate stability. */
class StableObservation {
    private var previous: UiSnapshot? = null
    fun reset() { previous = null }
    fun observe(snapshot: UiSnapshot): Boolean {
        val stable = previous == snapshot
        previous = snapshot
        return stable
    }
}
