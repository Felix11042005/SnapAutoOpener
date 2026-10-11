package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

class RecognitionReportTest {
    @Test fun blockedDoesNotHideRawClickCapabilitiesOrClaimZeroVerifiedTargets() {
        val s = directFixture().let { it.copy(nodes = it.nodes +
            UiNode(9, 0, listOf("My AI"), Box(0, 500, 1000, 600))) }
        val r = ScreenRecognizer.inspect(s)
        assertEquals(ScreenKind.BLOCKED, r.screen)
        val text = RecognitionReport.describe(s, r, true)
        assertTrue(text.contains("Statusfelder klickbar=1"))
        assertTrue(text.contains("Statusfelder mit ACTION_CLICK=1"))
        assertTrue(text.contains("direkte Snap-Klickziele=nicht ausgewertet"))
    }
    @Test fun structuralTraceIsBoundedAndContainsNoPrivateLabels() {
        val s = nestedRowsFixture().let { it.copy(nodes = it.nodes +
            UiNode(99, 4, listOf("Secret contact name", "Private message"), Box(100, 250, 300, 290))) }
        val text = RecognitionReport.structure(s)
        assertFalse(text.contains("Secret contact name"))
        assertFalse(text.contains("Private message"))
        assertFalse(text.contains("Neuer Snap"))
        assertFalse(text.contains("My AI"))
        assertTrue(text.contains("MY_AI:"))
        assertTrue(text.contains("LISTE("))
        assertTrue(text.lines().size <= 7)
    }
    @Test fun structuralTraceStopsAtCycles() {
        val s = nestedRowsFixture().let { it.copy(nodes = it.nodes.map { n ->
            if (n.index == 9) n.copy(parent = 9) else n
        }) }
        assertTrue(RecognitionReport.structure(s).contains("ZYKLUS"))
    }
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
