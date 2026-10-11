package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

class RecognitionReportTest {
    @Test fun blockedReportDoesNotClaimZeroSnaps() {
        val s = chatFixture().copy(truncated = true, nodeLimitReached = true, missingChildren = 3)
        val text = RecognitionReport.describe(s, ScreenRecognizer.inspect(s), false)
        assertTrue(text.contains("Status-Zeilen=nicht ausgewertet"))
        assertTrue(text.contains("Snap-Statuslabels=1"))
        assertTrue(text.contains("Knotenlimit=true"))
        assertTrue(text.contains("fehlende Kinder=3"))
    }
    @Test fun evaluatedEmptyListCanReportZeroRows() {
        val s = chatFixture().let { it.copy(nodes = it.nodes.filter { n -> n.index != 5 }) }
        val text = RecognitionReport.describe(s, ScreenRecognizer.inspect(s), true)
        assertTrue(text.contains("Status-Zeilen=0"))
        assertFalse(text.contains("nicht ausgewertet"))
    }
    @Test fun reportContainsNoUiLabelsOrNames() {
        val s = withExcludedRow().let { it.copy(nodes = it.nodes +
            UiNode(9, 4, listOf("Secret contact name", "Private message"), Box(100, 250, 300, 290))) }
        val text = RecognitionReport.describe(s, ScreenRecognizer.inspect(s), true)
        assertFalse(text.contains("Secret contact name"))
        assertFalse(text.contains("Private message"))
        assertFalse(text.contains("Neuer Snap"))
        assertTrue(text.contains("My-AI-Hinweise=1"))
        assertTrue(text.contains("ausgeschlossene Zeilen=1"))
    }
}
