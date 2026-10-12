package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

internal fun inventoryPage(names: List<String> = listOf("Fixture A", "Fixture B"), filter: Boolean = true): UiSnapshot =
    batchFixture(names).let { s -> s.copy(nodes = s.nodes.filter { filter || it.index != 2 }) }

internal fun confirmedPage(s: UiSnapshot, token: Long = 1, state: FilterState = FilterState.SELECTED,
    now: Long = 600): ConfirmedPage {
    val confirmation = PageConfirmation()
    val observation = PageObservation(ChipObservation(state, 123), emptyList())
    check(confirmation.observe(token, s, observation, now - 500) == null)
    return confirmation.observe(token, s, observation, now)!!
}

class FullInventoryTest {
    private fun evidence(s: UiSnapshot) = ScreenRecognizer.inspectFiltered(s)
    private fun rows(s: UiSnapshot) = evidence(s).rows
    private fun add(roster: InventoryRoster, s: UiSnapshot) = roster.add(s, evidence(s), rows(s))
    private fun step(run: FullInventory, s: UiSnapshot) = run.page(s, evidence(s), rows(s))
    private fun ready(s: UiSnapshot) = FullInventory(s).apply { assertTrue(filterReady(confirmedPage(s))) }

    @Test fun twoPassTraversalCollectsOffscreenOriginalsBeforeAnyOpening() {
        val top = inventoryPage(listOf("Fixture A")); val bottom = inventoryPage(listOf("Fixture B"))
        val run = ready(top)
        assertEquals(InventoryStep.SCROLL_DOWN, step(run, top).step)
        assertEquals(InventoryPhase.SCAN, run.phase)
        assertEquals(InventoryStep.SCROLL_DOWN, step(run, bottom).step)
        assertEquals(InventoryStep.SCROLL_DOWN, step(run, bottom).step)
        val first = step(run, bottom)
        assertEquals(InventoryStep.OPEN, first.step)
        assertTrue(run.roster.sealed); assertEquals(2, run.roster.size)
        assertEquals(InventoryPhase.OPEN, run.phase)
        assertTrue(run.roster.consume(bottom, first.row!!))
        val arrival = inventoryPage(listOf("Later arrival"))
        assertEquals(InventoryStep.SCROLL_UP, step(run, arrival).step)
        val second = step(run, top)
        assertEquals(InventoryStep.OPEN, second.step)
        assertTrue(run.roster.consume(top, second.row!!))
        assertEquals(InventoryStep.DONE, step(run, top).step)
        assertEquals(2, run.roster.size)
    }
    @Test fun emptyPagesWithEstablishedFilterContextRemainTraversable() {
        val s = inventoryPage(emptyList(), filter = false)
        assertEquals(ScreenKind.UNKNOWN, ScreenRecognizer.inspect(s).screen)
        assertEquals(ScreenKind.CHAT_LIST, evidence(s).screen)
        assertNotNull(ChatListSurface.find(s, evidence(s)))
    }
    @Test fun decorativePixelChangesDoNotKeepBoundaryLoopAlive() {
        val s = inventoryPage(); val run = ready(s)
        assertEquals(InventoryStep.SCROLL_DOWN, run.page(s, evidence(s), rows(s), 1L).step)
        assertEquals(InventoryStep.SCROLL_DOWN, run.page(s, evidence(s), rows(s), 2L).step)
        assertEquals(InventoryStep.OPEN, run.page(s, evidence(s), rows(s), 3L).step)
        assertEquals("UNCHANGED", run.lastMotion)
    }
    @Test fun emptyInventoryFinishesWithoutOpening() {
        val s = inventoryPage(emptyList()); val run = ready(s)
        repeat(2) { assertNotEquals(InventoryStep.OPEN, step(run, s).step) }
        assertEquals(InventoryStep.DONE, step(run, s).step)
        assertEquals(0, run.roster.size)
    }
    @Test fun noOpeningOrTraversalBeforeFilterConfirmation() {
        val s = inventoryPage(); val run = FullInventory(s)
        assertFalse(run.filterReady(confirmedPage(s, state = FilterState.NOT_SELECTED)))
        assertEquals(InventoryStep.STOP, step(run, s).step)
    }
    @Test fun wrongWindowAndMissingFilterCannotConfirmInitialContext() {
        val s = inventoryPage(); val run = FullInventory(s)
        assertFalse(run.filterReady(confirmedPage(s.copy(windowId = 88))))
        assertFalse(run.filterReady(confirmedPage(inventoryPage(filter = false))))
        assertTrue(run.filterReady(confirmedPage(s)))
        assertEquals(InventoryStep.STOP, step(run, s.copy(windowId = 88)).step)
    }
    @Test fun incompleteOrEditablePagesCannotBeTraversedEvenInContext() {
        val s = inventoryPage()
        for (unsafe in listOf(s.copy(truncated = true), s.copy(missingChildren = 1),
            s.copy(nodes = s.nodes + UiNode(99, 0, emptyList(), Box(0, 0, 20, 20), editable = true)))) {
            assertEquals(InventoryStep.STOP, step(ready(s), unsafe).step)
        }
    }
    @Test fun inventoryPageLimitStopsBeforeOpeningPartialInventory() {
        val s = inventoryPage(listOf("Fixture 0")); val run = ready(s)
        step(run, s)
        for (i in 1..49) assertEquals(InventoryStep.SCROLL_DOWN, step(run, inventoryPage(listOf("Fixture $i"))).step)
        assertEquals(InventoryStep.STOP, step(run, inventoryPage(listOf("Fixture 50"))).step)
        assertFalse(run.roster.sealed)
    }
    @Test fun onlyVerifiedRowsEnterRosterAndOverlappingPagesDeduplicate() {
        val s = inventoryPage(); val roster = InventoryRoster()
        assertTrue(roster.add(s, evidence(s), listOf(rows(s).first())))
        assertEquals(1, roster.size)
        assertTrue(add(roster, s)); assertTrue(add(roster, inventoryPage(listOf("Fixture B", "Fixture C"))))
        assertEquals(3, roster.size)
        roster.seal(); assertFalse(add(roster, inventoryPage(listOf("Later"))))
    }
    @Test fun offscreenOriginalStaysPendingAndMovedOriginalIsFound() {
        val s = inventoryPage(); val roster = InventoryRoster(); add(roster, s); roster.seal()
        assertNull(roster.nextVisible(inventoryPage(listOf("Later")), evidence(inventoryPage(listOf("Later")))))
        assertEquals(2, roster.remaining)
        val moved = inventoryPage(listOf("Later", "Fixture B", "Fixture A"))
        assertEquals(rows(moved).last(), roster.nextVisible(moved, evidence(moved)))
    }
    @Test fun changedCountIsSkippedInsteadOfOpeningNewerContent() {
        val s = inventoryPage(); val roster = InventoryRoster(); add(roster, s); roster.seal()
        val changed = s.copy(nodes = s.nodes.map { if (it.index == 6) it.copy(labels = listOf("2 neue Snaps")) else it })
        assertEquals(rows(changed).last(), roster.nextVisible(changed, evidence(changed)))
        assertEquals(1, roster.skipped)
    }
    @Test fun duplicateNamesAndChangedStatusAcrossScanPagesAreExcluded() {
        val roster = InventoryRoster(); val s = inventoryPage(listOf("Same"))
        add(roster, s)
        add(roster, inventoryPage(listOf("Same", "Same")))
        assertEquals(0, roster.size)
        add(roster, s); assertEquals(0, roster.size)
        val other = InventoryRoster(); add(other, s)
        val changed = s.copy(nodes = s.nodes.map { if (it.index == 6) it.copy(labels = listOf("3 neue Snaps")) else it })
        add(other, changed); assertEquals(0, other.size)
    }
    @Test fun ageOnlyChangesDoNotChangeContactIdentity() {
        val s = inventoryPage(listOf("Fixture A"))
        fun age(text: String) = s.copy(nodes = s.nodes + UiNode(99, 4, listOf(text), Box(320, 330, 420, 360)))
        assertEquals(InventoryIdentityReader.read(age("4 Min."), rows(s).first()),
            InventoryIdentityReader.read(age("5 Min."), rows(s).first()))
    }
    @Test fun statusWithoutContactCannotEnterInventory() {
        val s = chatFixture(); val roster = InventoryRoster()
        assertTrue(roster.add(s, evidence(s), rows(s))); assertEquals(0, roster.size)
    }
    @Test fun consumedOriginalCannotBeOpenedTwiceAndMissingOriginalFinishesSkipped() {
        val s = inventoryPage(); val roster = InventoryRoster(); add(roster, s); roster.seal()
        assertTrue(roster.consume(s, rows(s).first()))
        assertFalse(roster.consume(s, rows(s).first()))
        roster.finish(); assertEquals(0, roster.remaining); assertEquals(1, roster.skipped)
    }
    @Test fun largeListsAreCappedAtTwoHundredTargets() {
        val roster = InventoryRoster()
        repeat(200) { assertTrue(add(roster, inventoryPage(listOf("Fixture $it")))) }
        assertFalse(add(roster, inventoryPage(listOf("Fixture 200"))))
        assertEquals(200, roster.size)
    }
}
