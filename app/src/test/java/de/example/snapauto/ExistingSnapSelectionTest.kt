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
    @Test fun singleTargetSurvivesUnrelatedTreeChurnButRejectsAnotherSnapRow() {
        val selection = ExistingSnapSelection()
        val initial = directFixture().let { it.copy(nodes = it.nodes +
            UiNode(6, 0, listOf("Chats"), Box(350, 55, 550, 130))) }
        val evidence = ScreenRecognizer.inspectSingleTarget(initial)
        assertTrue(selection.capture(initial, evidence))
        val changedElsewhere = initial.copy(nodes = initial.nodes + UiNode(7, 3,
            listOf("Updated timestamp"), Box(500, 420, 800, 450)))
        assertTrue(selection.sameSingleTarget(changedElsewhere,
            ScreenRecognizer.inspectSingleTarget(changedElsewhere)))
        val another = initial.copy(nodes = initial.nodes + listOf(
            UiNode(7, 3, emptyList(), Box(0, 410, 1000, 570)),
            UiNode(8, 7, listOf("New Snap"), Box(100, 450, 300, 500))))
        assertFalse(selection.sameSingleTarget(another, ScreenRecognizer.inspectSingleTarget(another)))
    }
    @Test fun unknownAtExplicitStartCannotWaitForLaterIncomingTarget() {
        val selection = ExistingSnapSelection()
        val unclear = chatFixture().let { it.copy(nodes = it.nodes.filter { n -> n.index !in listOf(2, 5) }) }
        assertEquals(ScreenKind.UNKNOWN, ScreenRecognizer.inspect(unclear).screen)
        assertFalse(selection.capture(unclear, ScreenRecognizer.inspect(unclear)))
        val later = directFixture()
        assertFalse(selection.capture(later, ScreenRecognizer.inspect(later)))
        assertNull(selection.initial)
        assertTrue(selection.controls.isEmpty())
        selection.reset()
        assertTrue(selection.capture(later, ScreenRecognizer.inspect(later)))
    }
}
