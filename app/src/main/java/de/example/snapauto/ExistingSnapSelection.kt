package de.example.snapauto

/** One explicit in-target START freezes a recognized list or seals a failed attempt. */
class ExistingSnapSelection {
    private var sealed = false
    var initial: UiSnapshot? = null
        private set
    var controls: List<UiNode> = emptyList()
        private set

    fun reset() { sealed = false; initial = null; controls = emptyList() }

    fun capture(snapshot: UiSnapshot, evidence: Recognition): Boolean {
        if (sealed) return false
        sealed = true
        if (evidence.screen != ScreenKind.CHAT_LIST) return false
        initial = snapshot
        controls = ScreenRecognizer.directSnapControls(snapshot, evidence)
        return true
    }

    fun unchanged(snapshot: UiSnapshot) = initial != null && initial == snapshot
}
