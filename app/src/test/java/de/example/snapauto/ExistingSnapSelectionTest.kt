package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

class ExistingSnapSelectionTest {
    @Test fun laterArrivalsNeverReplaceInitialSelection() {
        val selection = ExistingSnapSelection()
        val initial = directFixture()
        assertTrue(selection.capture(initial, ScreenRecognizer.inspect(initial)))
        val later = initial.copy(nodes = initial.nodes +
            UiNode(6, 3, listOf("New Snap"), Box(100, 430, 300, 470),
                clickable = true, canClick = true, path = listOf(2, 1)))
        assertFalse(selection.capture(later, ScreenRecognizer.inspect(later)))
        assertFalse(selection.unchanged(later))
        assertEquals(listOf(5), selection.controls.map { it.index })
        assertTrue(selection.unchanged(initial))
    }
    @Test fun incompleteOrForeignScreenCannotBecomeInitialSelection() {
        val selection = ExistingSnapSelection()
        for (s in listOf(directFixture().copy(truncated = true), directFixture().copy(packageName = "other"))) {
            assertFalse(selection.capture(s, ScreenRecognizer.inspect(s)))
        }
        assertNull(selection.initial)
    }
    @Test fun emptyInitialSelectionDoesNotWaitForIncomingSnaps() {
        val selection = ExistingSnapSelection()
        val s = chatFixture().let { it.copy(nodes = it.nodes.filter { n -> n.index != 5 }) }
        assertTrue(selection.capture(s, ScreenRecognizer.inspect(s)))
        assertTrue(selection.controls.isEmpty())
        assertFalse(selection.capture(directFixture(), ScreenRecognizer.inspect(directFixture())))
        assertTrue(selection.controls.isEmpty())
    }
    @Test fun explicitRestartCanCaptureNewInitialSelection() {
        val selection = ExistingSnapSelection(); val s = directFixture()
        selection.capture(s, ScreenRecognizer.inspect(s))
        selection.reset()
        assertNull(selection.initial)
        assertTrue(selection.controls.isEmpty())
        assertTrue(selection.capture(s, ScreenRecognizer.inspect(s)))
    }
}
