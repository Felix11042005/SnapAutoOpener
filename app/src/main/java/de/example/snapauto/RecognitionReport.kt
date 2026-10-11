package de.example.snapauto

/** Aggregate evidence only: no labels, names, messages, or identifiers from the UI. */
object RecognitionReport {
    fun describe(snapshot: UiSnapshot, result: Recognition, stable: Boolean): String {
        val facts = result.facts
        val rows = if (result.rowsEvaluated) result.rows.size.toString() else "nicht ausgewertet"
        return "Bildschirm=${result.screen}; Filter=${result.filter}; Status-Zeilen=$rows; stabil=$stable; " +
            "Header=${facts.headers}; Filterlabels=${facts.filters}; Listen=${facts.lists}; " +
            "Snap-Statuslabels=${facts.statusLabels}; My-AI-Hinweise=${facts.myAiLabels}; Werbehinweise=${facts.adLabels}; " +
            "direkte Snap-Klickziele=${ScreenRecognizer.directSnapControls(snapshot, result).size}; " +
            "ausgeschlossene Zeilen=${result.excludedRows.size}; Knoten=${snapshot.nodes.size}; " +
            "Knotenlimit=${snapshot.nodeLimitReached}; fehlende Kinder=${snapshot.missingChildren}; " +
            "Baum unvollständig=${snapshot.truncated}; Fenstergrenzen gültig=${snapshot.bounds.valid}"
    }
}
