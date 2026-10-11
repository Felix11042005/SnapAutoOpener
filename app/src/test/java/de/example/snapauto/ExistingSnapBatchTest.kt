package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

internal fun batchFixture(names: List<String> = listOf("Fixture A", "Fixture B")): UiSnapshot {
    val base = chatFixture()
    val nodes = base.nodes.take(4).toMutableList()
    names.forEachIndexed { i, name ->
        val index = 4 + i * 3
        val top = 240 + i * 180
        nodes += UiNode(index, 3, emptyList(), Box(0, top, 1000, top + 160))
        nodes += UiNode(index + 1, index, listOf(name), Box(100, top + 10, 300, top + 50))
        nodes += UiNode(index + 2, index, listOf("Neuer Snap"), Box(100, top + 90, 300, top + 130),
            clickable = true, canClick = true, path = listOf(2, i, 1))
    }
    return base.copy(nodes = nodes)
}

class ExistingSnapBatchTest {
    private fun rows(s: UiSnapshot) = ScreenRecognizer.inspect(s).rows
    private fun next(batch: ExistingSnapBatch, s: UiSnapshot) = batch.next(s, ScreenRecognizer.inspect(s))
    private fun frozen(s: UiSnapshot): ExistingSnapBatch = ExistingSnapBatch().apply {
        reset(s); assertTrue(freeze(s, rows(s)))
    }

    @Test fun freezesOnlyVerifiedStartRowsInTopToBottomOrder() {
        val s = batchFixture(); val batch = ExistingSnapBatch(); batch.reset(s)
        assertTrue(batch.freeze(s, rows(s).reversed()))
        assertEquals(2, batch.initialCount)
        assertEquals(rows(s).first(), next(batch, s))
        assertFalse(batch.freeze(s, rows(s)))
    }
    @Test fun visualSubsetExcludesOtherSemanticCandidates() {
        val s = batchFixture(); val batch = ExistingSnapBatch(); batch.reset(s)
        assertTrue(batch.freeze(s, listOf(rows(s).last())))
        assertEquals(1, batch.initialCount)
        assertEquals(rows(s).last(), next(batch, s))
    }
    @Test fun changedInitialTreeCannotCreateRoster() {
        val s = batchFixture(); val batch = ExistingSnapBatch(); batch.reset(s)
        val changed = batchFixture(listOf("Replacement", "Fixture B"))
        assertFalse(batch.freeze(changed, rows(changed)))
    }
    @Test fun anonymousStatusOnlyRowsCannotBeReidentified() {
        val s = chatFixture(); val batch = ExistingSnapBatch(); batch.reset(s)
        assertFalse(batch.freeze(s, rows(s)))
    }
    @Test fun duplicateRowLabelsAreExcluded() {
        val s = batchFixture(listOf("Duplicate", "Duplicate")); val batch = ExistingSnapBatch(); batch.reset(s)
        assertFalse(batch.freeze(s, rows(s)))
    }
    @Test fun movedOriginalIsFoundAndNewArrivalNeverAdded() {
        val s = batchFixture(); val batch = frozen(s)
        assertTrue(batch.consume(s, next(batch, s)!!))
        val current = batchFixture(listOf("New arrival", "Fixture B", "Fixture A"))
        assertEquals(rows(current)[1], next(batch, current))
        assertTrue(batch.consume(current, next(batch, current)!!))
        assertNull(next(batch, current))
        assertEquals(2, batch.initialCount)
    }
    @Test fun originalRosterOrderSurvivesListReordering() {
        val s = batchFixture(); val batch = frozen(s)
        val current = batchFixture(listOf("Fixture B", "Fixture A"))
        assertEquals(rows(current).last(), next(batch, current))
    }
    @Test fun changedCountOrOtherLabelSkipsOriginalInsteadOfReplacingIt() {
        val s = batchFixture(); val batch = frozen(s)
        val changed = s.copy(nodes = s.nodes.map { if (it.index == 6) it.copy(labels = listOf("2 neue Snaps")) else it })
        assertEquals(rows(changed).last(), next(batch, changed))
        assertEquals(1, batch.skipped)
    }
    @Test fun vanishedAndNowAmbiguousRowsAreSkipped() {
        val s = batchFixture(); val batch = frozen(s)
        val current = batchFixture(listOf("Fixture B", "Fixture B"))
        assertNull(next(batch, current))
        assertEquals(2, batch.skipped)
    }
    @Test fun changedWindowAndWrongConsumptionCannotAdvanceQueue() {
        val s = batchFixture(); val batch = frozen(s)
        assertNull(next(batch, s.copy(windowId = 99)))
        assertFalse(batch.consume(s, rows(s).last()))
        assertEquals(2, batch.remainingCount)
    }
    @Test fun wholeWindowGeometryChangeCannotBeMistakenForSameProfileWindow() {
        val s = batchFixture(); val batch = frozen(s)
        assertFalse(batch.sameWindow(s.copy(bounds = Box(0, 0, 500, 2000))))
        assertFalse(batch.sameWindow(s.copy(packageName = "other.app")))
    }
    @Test fun noUnverifiedRowCanBeIntroducedAtFreeze() {
        val s = batchFixture(); val batch = ExistingSnapBatch(); batch.reset(s)
        assertFalse(batch.freeze(s, listOf(Box(0, 0, 1000, 2000))))
    }
    @Test fun rosterIsCappedAtTenEvenOnLargeWindow() {
        val original = batchFixture((0..12).map { "Fixture $it" })
        val bounds = Box(0, 0, 1000, 4000)
        val s = original.copy(bounds = bounds, nodes = original.nodes.map { when (it.index) {
            0 -> it.copy(bounds = bounds)
            3 -> it.copy(bounds = Box(0, 230, 1000, 3900))
            else -> it
        } })
        assertEquals(13, rows(s).size)
        assertEquals(10, frozen(s).initialCount)
    }
    @Test fun newManualRunDiscardsPreviousRoster() {
        val first = batchFixture(); val batch = frozen(first)
        val second = batchFixture(listOf("Other fixture")); batch.reset(second)
        assertFalse(batch.frozen)
        assertNull(next(batch, second))
        assertTrue(batch.freeze(second, rows(second)))
        assertEquals(1, batch.initialCount)
    }
}
