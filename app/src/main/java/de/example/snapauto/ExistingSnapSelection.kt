package de.example.snapauto

/** One explicit in-target START freezes a recognized list or seals a failed attempt. */
class ExistingSnapSelection {
    private var sealed = false
    var initial: UiSnapshot? = null
        private set
    var controls: List<UiNode> = emptyList()
        private set
    var rows: List<Box> = emptyList()
        private set

    fun reset() { sealed = false; initial = null; controls = emptyList(); rows = emptyList() }

    fun capture(snapshot: UiSnapshot, evidence: Recognition): Boolean {
        if (sealed) return false
        sealed = true
        if (evidence.screen != ScreenKind.CHAT_LIST) return false
        initial = snapshot
        controls = ScreenRecognizer.directSnapControls(snapshot, evidence)
        rows = evidence.rows
        return true
    }

    fun unchanged(snapshot: UiSnapshot) = initial != null && initial == snapshot

    /** Single-item mode tolerates accessibility churn outside the frozen target row. */
    fun sameSingleTarget(snapshot: UiSnapshot, evidence: Recognition): Boolean {
        val original = initial ?: return false
        if (original.packageName != snapshot.packageName || original.windowId != snapshot.windowId ||
            original.bounds != snapshot.bounds || evidence.screen != ScreenKind.CHAT_LIST ||
            !evidence.rowsEvaluated || rows.size != 1 || evidence.rows != rows) return false
        val row = rows.single()
        val exact = snapshot.nodes.filter { node -> node.visible && node.enabled && row.contains(node.bounds) &&
            node.labels.any { it.trim().lowercase(java.util.Locale.ROOT) in setOf("neuer snap", "new snap") } }
            .distinctBy { it.bounds }
        return exact.size == 1
    }
}
