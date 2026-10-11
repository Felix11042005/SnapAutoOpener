package de.example.snapauto

/** Freeze the first complete recognized list. Later arrivals never replace its target. */
class ExistingSnapSelection {
    var initial: UiSnapshot? = null
        private set
    var controls: List<UiNode> = emptyList()
        private set

    fun reset() { initial = null; controls = emptyList() }

    fun capture(snapshot: UiSnapshot, evidence: Recognition): Boolean {
        if (initial != null || evidence.screen != ScreenKind.CHAT_LIST) return false
        initial = snapshot
        controls = ScreenRecognizer.directSnapControls(snapshot, evidence)
        return true
    }

    fun unchanged(snapshot: UiSnapshot) = initial != null && initial == snapshot
}
