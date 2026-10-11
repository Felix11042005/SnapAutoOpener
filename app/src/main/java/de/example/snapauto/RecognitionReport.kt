package de.example.snapauto

/** Aggregate evidence only: no labels, names, messages, or identifiers from the UI. */
object RecognitionReport {
    fun describe(snapshot: UiSnapshot, result: Recognition, stable: Boolean): String {
        val facts = result.facts
        val rows = if (result.rowsEvaluated) result.rows.size.toString() else "nicht ausgewertet"
        val clickTargets = if (result.rowsEvaluated) ScreenRecognizer.directSnapControls(snapshot, result).size.toString()
            else "nicht ausgewertet"
        return "Bildschirm=${result.screen}; Filter=${result.filter}; Status-Zeilen=$rows; stabil=$stable; " +
            "Header=${facts.headers}; Filterlabels=${facts.filters}; Listen=${facts.lists}; " +
            "Snap-Statuslabels=${facts.statusLabels}; My-AI-Hinweise=${facts.myAiLabels}; Werbehinweise=${facts.adLabels}; " +
            "Statusfelder klickbar=${facts.statusClickable}; Statusfelder mit ACTION_CLICK=${facts.statusClickActions}; " +
            "direkte Snap-Klickziele=$clickTargets; " +
            "ausgeschlossene Zeilen=${result.excludedRows.size}; Knoten=${snapshot.nodes.size}; " +
            "Knotenlimit=${snapshot.nodeLimitReached}; fehlende Kinder=${snapshot.missingChildren}; " +
            "Baum unvollständig=${snapshot.truncated}; Fenstergrenzen gültig=${snapshot.bounds.valid}"
    }

    /** One explicit failed START: bounded structural trace, no names or text. */
    fun structure(snapshot: UiSnapshot): String {
        val byIndex = snapshot.nodes.associateBy { it.index }
        val markers = snapshot.nodes.filter { it.visible && ScreenRecognizer.markerKind(it) != null }
        val selected = (markers.filter { ScreenRecognizer.markerKind(it) != "SNAP" }.take(4) +
            markers.filter { ScreenRecognizer.markerKind(it) == "SNAP" }.take(3)).distinctBy { it.index }
        fun box(b: Box) = "${b.left},${b.top},${b.right},${b.bottom}"
        return selected.joinToString("\n") { marker ->
            var current: UiNode? = marker
            val visited = mutableSetOf<Int>()
            val chain = mutableListOf<String>()
            repeat(8) {
                val node = current ?: return@repeat
                if (!visited.add(node.index)) { chain += "ZYKLUS"; current = null; return@repeat }
                chain += "${if (node.list) "LISTE" else "KNOTEN"}(${box(node.bounds)};klick=${node.clickable};aktion=${node.canClick})"
                current = node.parent?.let(byIndex::get)
            }
            if (current != null) chain += "WEITERE_VORFAHREN"
            "${ScreenRecognizer.markerKind(marker)}: ${chain.joinToString(" <- ")}"
        }.ifEmpty { "Keine sichtbaren Status-/Ausschlussmarker." }
    }
}
