package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

internal fun nestedRowsFixture(label: String = "My AI"): UiSnapshot {
    val s = withExcludedRow(label)
    return s.copy(nodes = s.nodes.map {
        if (it.parent == 3) it.copy(parent = 9) else it
    } + UiNode(9, 3, emptyList(), s.nodes[3].bounds))
}

class NestedRowsTest {
    @Test fun exclusionOnRowContainerItselfStillOwnsSnapChildren() {
        val s = nestedRowsFixture()
        val nodes = s.nodes.filter { it.index != 7 }.map {
            if (it.index == 6) it.copy(labels = listOf("My AI")) else it
        }
        val r = ScreenRecognizer.inspect(s.copy(nodes = nodes))
        assertEquals(ScreenKind.CHAT_LIST, r.screen)
        assertEquals(1, r.rows.size)
        assertEquals(listOf(Box(0, 420, 1000, 580)), r.excludedRows)
    }
    @Test fun contentWrapperPreservesRowsAndSiblingExclusions() {
        for (label in listOf("My AI", "Gesponsert")) {
            val s = nestedRowsFixture(label)
            val r = ScreenRecognizer.inspect(s)
            assertEquals(ScreenKind.CHAT_LIST, r.screen)
            assertEquals(listOf(Box(0, 240, 1000, 400)), r.rows)
            assertEquals(listOf(Box(0, 420, 1000, 580)), r.excludedRows)
        }
    }
    @Test fun multipleLargeSingleChildWrappersCanBeTraversed() {
        val s = nestedRowsFixture()
        val nodes = s.nodes.map { if (it.index == 9) it.copy(parent = 10) else it } +
            UiNode(10, 3, emptyList(), s.nodes[3].bounds)
        assertEquals(1, ScreenRecognizer.inspect(s.copy(nodes = nodes)).rows.size)
    }
    @Test fun nestedContentCanAuthorizeOnlySafeDirectStatus() {
        val s = nestedRowsFixture().let { it.copy(nodes = it.nodes.map { n ->
            if (n.index == 5 || n.index == 8) n.copy(clickable = true, canClick = true, path = listOf(n.index)) else n
        }) }
        assertEquals(listOf(5), ScreenRecognizer.directSnapControls(s).map { it.index })
        val session = DiagnosticSession(); val token = session.start(0, mode = SessionMode.INTERACTIVE)
        val safe = s.nodes.single { it.index == 5 }
        assertNull(ActionPolicy.rejection(session, token, 1,
            ActionRequest(ActionKind.OPEN_SNAP, s, safe.bounds, safe.index), s))
        val excluded = s.nodes.single { it.index == 8 }
        assertEquals("DIRECT_SNAP_CONTROL_REQUIRED", ActionPolicy.rejection(session, token, 1,
            ActionRequest(ActionKind.OPEN_SNAP, s, excluded.bounds, excluded.index), s))
    }
    @Test fun overlappingBranchesDoNotBecomeIndependentRows() {
        val s = nestedRowsFixture()
        val nodes = s.nodes.map { if (it.index == 4) it.copy(bounds = Box(0, 240, 1000, 450)) else it }
        assertEquals(ScreenKind.BLOCKED, ScreenRecognizer.inspect(s.copy(nodes = nodes)).screen)
    }
    @Test fun labelAloneUnderLargeWrapperCannotRepresentWholeExcludedRow() {
        val s = nestedRowsFixture()
        val nodes = s.nodes.filter { it.index != 6 }.map { if (it.index in listOf(7, 8)) it.copy(parent = 9) else it }
        assertEquals(ScreenKind.BLOCKED, ScreenRecognizer.inspect(s.copy(nodes = nodes)).screen)
    }
    @Test fun aggregateExclusionOnLargeWrapperStillBlocks() {
        val s = nestedRowsFixture()
        val nodes = s.nodes.map { if (it.index == 9) it.copy(labels = listOf("My AI")) else it }
        assertEquals(ScreenKind.BLOCKED, ScreenRecognizer.inspect(s.copy(nodes = nodes)).screen)
    }
    @Test fun malformedBoundsAndMissingParentStillBlock() {
        val s = nestedRowsFixture()
        for (replacement in listOf(s.nodes.last().copy(bounds = Box(0, 700, 1000, 1900)),
            s.nodes.last().copy(parent = 90))) {
            val nodes = s.nodes.map { if (it.index == 9) replacement else it }
            assertEquals(ScreenKind.BLOCKED, ScreenRecognizer.inspect(s.copy(nodes = nodes)).screen)
        }
    }
    @Test fun geometryTranslationDoesNotChangeNestedRecognition() {
        val s = nestedRowsFixture()
        fun shift(b: Box) = Box(b.left * 2 + 70, b.top * 2 + 130, b.right * 2 + 70, b.bottom * 2 + 130)
        val moved = s.copy(bounds = shift(s.bounds), nodes = s.nodes.map { it.copy(bounds = shift(it.bounds)) })
        assertEquals(1, ScreenRecognizer.inspect(moved).rows.size)
        assertEquals(1, ScreenRecognizer.inspect(moved).excludedRows.size)
    }
}
