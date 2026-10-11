package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

/** Reported 5.3.2 ancestor bounds. Header/filter positions were NOT in the report;
 * their boxes below are synthetic assumptions, not a complete device replay. */
internal fun reportedRows() = UiSnapshot(ScreenRecognizer.TARGET_PACKAGE, 12, Box(0, 0, 1080, 2400), listOf(
    UiNode(0, null, emptyList(), Box(0, 0, 1080, 2400)),
    UiNode(1, 0, listOf("Chat"), Box(50, 80, 300, 150)),
    UiNode(2, 4, listOf("Ungelesen"), Box(100, 327, 300, 387)),
    UiNode(3, 0, emptyList(), Box(0, 289, 1080, 2153), list = true),
    UiNode(4, 3, emptyList(), Box(0, 289, 1080, 415)),
    UiNode(5, 4, emptyList(), Box(0, 289, 1080, 415)),
    UiNode(6, 5, emptyList(), Box(362, 310, 599, 405), canClick = true),
    UiNode(7, 6, listOf("My AI"), Box(458, 327, 573, 387), canClick = true),
    UiNode(8, 3, emptyList(), Box(0, 421, 1080, 601)),
    UiNode(9, 8, listOf("Neuer Snap"), Box(0, 421, 1080, 601)),
    UiNode(10, 3, emptyList(), Box(0, 604, 1080, 784)),
    UiNode(11, 10, listOf("Neuer Snap"), Box(0, 604, 1080, 784)),
    UiNode(12, 0, listOf("Chat"), Box(100, 2250, 300, 2350))
))

class ReportedRowTest {
    @Test fun firstListItemAboveUnreadBottomIsExcludedInFull() {
        val s = reportedRows(); val r = ScreenRecognizer.inspect(s)
        assertEquals(ScreenKind.CHAT_LIST, r.screen)
        assertEquals(listOf(Box(0, 289, 1080, 415)), r.excludedRows)
        assertEquals(listOf(Box(0, 421, 1080, 601), Box(0, 604, 1080, 784)), r.rows)
        assertEquals(FilterState.UNKNOWN, r.filter)
        assertTrue(ScreenRecognizer.directSnapControls(s, r).isEmpty())
    }
    @Test fun statusInsideFilterItemCannotBecomeOpeningRow() {
        val s = reportedRows().let { it.copy(nodes = it.nodes +
            UiNode(13, 5, listOf("Neuer Snap"), Box(10, 390, 300, 410))) }
        assertEquals(2, ScreenRecognizer.inspect(s).rows.size)
    }
    @Test fun advertisementInSameFirstItemIsExcludedToo() {
        val s = reportedRows().let { it.copy(nodes = it.nodes.map { n ->
            if (n.index == 7) n.copy(labels = listOf("Gesponsert")) else n
        }) }
        assertEquals(2, ScreenRecognizer.inspect(s).rows.size)
        assertEquals(1, ScreenRecognizer.inspect(s).excludedRows.size)
    }
    @Test fun incompleteOrEditableReportedStructureStillCannotOpen() {
        assertEquals(ScreenKind.UNKNOWN, ScreenRecognizer.inspect(reportedRows().copy(missingChildren = 1)).screen)
        val s = reportedRows().let { it.copy(nodes = it.nodes +
            UiNode(13, 0, emptyList(), Box(0, 2155, 1080, 2200), editable = true)) }
        assertEquals(ScreenKind.BLOCKED, ScreenRecognizer.inspect(s).screen)
    }
}
