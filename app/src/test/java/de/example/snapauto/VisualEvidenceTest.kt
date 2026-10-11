package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

class VisualEvidenceTest {
    private fun image(x: Int = 25, y: Int = 35, size: Int = 12, color: Int = 0xffff0033.toInt(), outline: Boolean = false): IntArray {
        val p = IntArray(100 * 100) { 0xff222222.toInt() }
        for (dy in 0 until size) for (dx in 0 until size) {
            if (!outline || dx == 0 || dy == 0 || dx == size - 1 || dy == size - 1) p[(y + dy) * 100 + x + dx] = color
        }
        return p
    }
    private fun detect(p: IntArray, regions: List<Box> = listOf(Box(0, 0, 100, 100)), density: Float = 1f) =
        VisualEvidence.detect(p, 100, 100, regions, density)
    @Test fun filledRedAndPurpleComponentsFoundAtDifferentPositions() {
        assertEquals(Box(25, 35, 37, 47), detect(image()).single().bounds)
        assertEquals(Box(70, 65, 82, 77), detect(image(70, 65, color = 0xffaa00dd.toInt())).single().bounds)
    }
    @Test fun blueChatAndOutlineRejected() {
        assertTrue(detect(image(color = 0xff0088ff.toInt())).isEmpty())
        assertTrue(detect(image(outline = true)).isEmpty())
    }
    @Test fun regionsAreRequiredAndClippedRegionsRejected() {
        assertTrue(detect(image(), emptyList()).isEmpty())
        assertTrue(detect(image(), listOf(Box(0, 0, 20, 100))).isEmpty())
        assertTrue(detect(image(), listOf(Box(-1, 0, 100, 100))).isEmpty())
    }
    @Test fun densityPriorRejectsLargeAvatarAndTinyNoise() {
        assertTrue(detect(image(size = 35)).isEmpty())
        assertTrue(detect(image(size = 2)).isEmpty())
        assertEquals(1, detect(image(size = 24), density = 2f).size)
    }
    @Test fun duplicateRegionsDoNotDuplicateCandidate() {
        assertEquals(1, detect(image(), listOf(Box(0, 0, 100, 100), Box(0, 0, 100, 100))).size)
    }
    @Test fun sameColorDecorationRemainsOnlyAHint() {
        // Intentionally demonstrates the known limitation: a same-shaped decoration can match.
        assertEquals(1, detect(image(60, 50)).size)
        val s = DiagnosticSession(); val token = s.start(0, mode = SessionMode.INTERACTIVE)
        val snapshot = chatFixture()
        assertEquals("DIRECT_SNAP_CONTROL_REQUIRED", ActionPolicy.rejection(s, token, 1,
            ActionRequest(ActionKind.OPEN_SNAP, snapshot, Box(60, 50, 72, 62)), snapshot))
    }
}
