package de.example.snapauto

import java.util.Locale

data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val valid get() = width > 0 && height > 0
    fun contains(other: Box) = valid && other.valid && other.left >= left &&
        other.top >= top && other.right <= right && other.bottom <= bottom
    fun intersects(other: Box) = valid && other.valid && left < other.right &&
        right > other.left && top < other.bottom && bottom > other.top
}

data class UiNode(
    val index: Int, val parent: Int?, val labels: List<String>, val bounds: Box,
    val visible: Boolean = true, val enabled: Boolean = true,
    val selected: Boolean = false, val editable: Boolean = false,
    val clickable: Boolean = false, val list: Boolean = false,
    val selectionKnown: Boolean = false,
    val canClick: Boolean = false, val path: List<Int> = emptyList()
)

data class UiSnapshot(
    val packageName: String, val windowId: Int, val bounds: Box,
    val nodes: List<UiNode>, val truncated: Boolean = false,
    val nodeLimitReached: Boolean = false, val missingChildren: Int = 0
)

enum class ScreenKind { UNKNOWN, BLOCKED, CHAT_LIST }
enum class FilterState { UNKNOWN, NOT_SELECTED, SELECTED }
data class Recognition(
    val screen: ScreenKind, val filter: FilterState = FilterState.UNKNOWN,
    val reason: String, val rows: List<Box> = emptyList(),
    val excludedRows: List<Box> = emptyList(), val rowsEvaluated: Boolean = false,
    val facts: RecognitionFacts = RecognitionFacts()
)

data class RecognitionFacts(
    val headers: Int = 0, val filters: Int = 0, val lists: Int = 0,
    val statusLabels: Int = 0, val myAiLabels: Int = 0, val adLabels: Int = 0,
    val statusClickable: Int = 0, val statusClickActions: Int = 0,
    val truncatedStatusLabels: Int = 0
)

/** Positive evidence only. A status label or colored square is never proof of an unopened Snap. */
object ScreenRecognizer {
    const val TARGET_PACKAGE = "com.snapchat.android"
    private val snapStatus = Regex("(?:\\d+\\+? )?(?:neuer snap|neue snaps|new snap|new snaps)")
    // Visible video labels such as "4 neue…" omit the content kind. These are
    // visual candidates only; never native click controls or proof by themselves.
    private val clippedCount = Regex("\\d+\\+? (?:neue|new)(?:…|\\.{3})")
    private val unreadLabel = Regex("(?:ungelesen|unread)(?: ?\\d+\\+?)?")
    private val myAi = Regex("(?:^|[^\\p{L}])my ai(?:$|[^\\p{L}])")
    private val ad = Regex("(?:^|[^\\p{L}])(?:werbung|anzeige|gesponsert|sponsored|advertisement|ad)(?:$|[^\\p{L}])")
    private fun normalized(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    /** Allowlisted marker kinds only; never expose the underlying label. */
    fun markerKind(node: UiNode): String? = when {
        node.labels.any { myAi.containsMatchIn(normalized(it)) } -> "MY_AI"
        node.labels.any { ad.containsMatchIn(normalized(it)) } -> "AD"
        node.labels.any { snapStatus.matches(normalized(it)) } -> "SNAP"
        node.labels.any { clippedCount.matches(normalized(it)) } -> "SNAP_PARTIAL"
        else -> null
    }

    fun directSnapControls(snapshot: UiSnapshot, evidence: Recognition = inspect(snapshot)): List<UiNode> {
        if (evidence.screen != ScreenKind.CHAT_LIST || !evidence.rowsEvaluated) return emptyList()
        return snapshot.nodes.filter { node ->
            node.visible && node.enabled && node.clickable && node.canClick &&
                node.path.isNotEmpty() && node.labels.any { snapStatus.matches(normalized(it)) } &&
                evidence.rows.any { it.contains(node.bounds) && node.bounds.height <= it.height / 2 } &&
                evidence.excludedRows.none { it.intersects(node.bounds) }
        }.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
    }

    fun inspect(snapshot: UiSnapshot): Recognition {
        if (snapshot.packageName != TARGET_PACKAGE) return Recognition(ScreenKind.UNKNOWN, reason = "WRONG_PACKAGE")
        val visible = snapshot.nodes.filter { it.visible && it.enabled && snapshot.bounds.contains(it.bounds) }
        val exposed = snapshot.nodes.filter { it.visible && snapshot.bounds.intersects(it.bounds) }
        fun matching(vararg labels: String) = visible.filter { node -> node.labels.any { normalized(it) in labels } }
            .distinctBy { it.bounds }
        val headers = matching("chat", "chats")
        val filters = visible.filter { n -> n.labels.any { unreadLabel.matches(normalized(it)) } }.distinctBy { it.bounds }
        val listNodes = visible.filter { it.list }
        val statuses = visible.filter { node -> node.labels.any { snapStatus.matches(normalized(it)) } }
        val clippedStatuses = visible.filter { node -> node.labels.any { clippedCount.matches(normalized(it)) } }
        val visualStatuses = (statuses + clippedStatuses).distinctBy { it.index }
        val aiNodes = exposed.filter { node -> node.labels.any { myAi.containsMatchIn(normalized(it)) } }
        val adNodes = exposed.filter { node -> node.labels.any { ad.containsMatchIn(normalized(it)) } }
        val exclusions = (aiNodes + adNodes).distinctBy { it.index }
        val facts = RecognitionFacts(headers.size, filters.size, listNodes.size, statuses.size, aiNodes.size, adNodes.size,
            statuses.count { it.clickable }, statuses.count { it.canClick }, clippedStatuses.size)
        fun unknown(reason: String) = Recognition(ScreenKind.UNKNOWN, reason = reason, facts = facts)
        fun blocked(reason: String) = Recognition(ScreenKind.BLOCKED, reason = reason, facts = facts)
        if (!snapshot.bounds.valid) return unknown("INVALID_WINDOW_BOUNDS")
        if (snapshot.truncated || snapshot.nodeLimitReached || snapshot.missingChildren > 0) return unknown("INCOMPLETE_TREE")
        // An editable node anywhere visible blocks capture, including a search box (conservative).
        if (snapshot.nodes.any { it.visible && it.editable }) {
            return blocked("EDITABLE_FIELD")
        }
        val byIndex = snapshot.nodes.associateBy { it.index }
        fun insideList(start: UiNode): Boolean {
            var node: UiNode? = start
            val visited = mutableSetOf<Int>()
            repeat(16) {
                val current = node ?: return false
                if (!visited.add(current.index)) return true
                if (current.list) return true
                node = current.parent?.let(byIndex::get)
            }
            return node != null
        }
        if (filters.size > 1) return unknown("HEADER_OR_FILTER_AMBIGUOUS")
        val filter = filters.singleOrNull()
        // Snapchat also labels its bottom navigation "Chat". Resolve the title
        // relative to content, never by an assumed screen height or fixed strip.
        val anchorTop = filter?.bounds?.top ?: visualStatuses.minOfOrNull { it.bounds.top }
            ?: return unknown("HEADER_OR_FILTER_AMBIGUOUS")
        val header = headers.filter { it.bounds.bottom <= anchorTop && !insideList(it) }.singleOrNull()
            ?: return unknown("HEADER_OR_FILTER_AMBIGUOUS")
        val contentBoundary = filter?.bounds?.bottom ?: header.bounds.bottom
        val lists = listNodes.filter { it.bounds.bottom > contentBoundary }
        if (lists.isEmpty()) return unknown("NO_LIST_STRUCTURE")
        val filterState = when {
            filter?.selected == true -> FilterState.SELECTED
            filter?.selectionKnown == true -> FilterState.NOT_SELECTED
            else -> FilterState.UNKNOWN
        }
        val children = snapshot.nodes.filter { it.visible }.groupBy { it.parent }
        fun transparentContainer(node: UiNode): Boolean {
            // A large content wrapper may separate rows from the actual list.
            // Only unwrap structurally separate vertical branches. Overlapping
            // siblings can be parts of one conversation and must stay together.
            if (node.list || markerKind(node) != null) return false
            val parts = children[node.index].orEmpty().sortedBy { it.bounds.top }
            return parts.isNotEmpty() && parts.all { node.bounds.contains(it.bounds) } &&
                parts.zipWithNext().all { (a, b) -> a.bounds.bottom <= b.bounds.top }
        }
        // The outermost bounded row on a verified ancestry path owns all of its
        // descendants. A label alone under an oversized wrapper is insufficient.
        fun owningRow(start: UiNode, boundary: Int): Box? {
            var current: UiNode? = start
            var candidate: Box? = null
            val visited = mutableSetOf<Int>()
            repeat(16) {
                val node = current ?: return null
                if (!visited.add(node.index) || !node.visible || !node.bounds.contains(start.bounds)) return null
                val parent = node.parent?.let(byIndex::get) ?: return null
                if (!parent.bounds.contains(node.bounds)) return null
                val parentIsList = lists.any { it.index == parent.index }
                val bounded = !node.list && node.bounds.top >= boundary &&
                    node.bounds.height <= snapshot.bounds.height / 3
                val isContainer = children[node.index].orEmpty().isNotEmpty()
                if (bounded && (node.index != start.index || parentIsList || isContainer)) candidate = node.bounds
                if (!bounded && !transparentContainer(node)) return null
                if (parentIsList) {
                    return candidate
                }
                current = parent
            }
            return null
        }
        // Filter chips can themselves be inside the first list item. An exclusion
        // row may start above the unread chip; it must still be excluded in full.
        val excludedRows = exclusions.map { owningRow(it, boundary = header.bounds.bottom)
            ?: return blocked("UNSCOPED_AD_OR_MY_AI") }.distinct()
        // Structure and interaction capability are independent: the real device
        // exposes full-row Snap statuses without any native click action.
        val rows = visualStatuses.mapNotNull { owningRow(it, boundary = contentBoundary) }
            .distinct().filter { row -> excludedRows.none { it.intersects(row) } }.take(30)
        if (filter == null && rows.isEmpty()) return unknown("NO_UNOPENED_LIST_EVIDENCE")
        return Recognition(ScreenKind.CHAT_LIST, filterState, "SEMANTIC_LIST", rows,
            excludedRows, rowsEvaluated = true, facts = facts)
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
