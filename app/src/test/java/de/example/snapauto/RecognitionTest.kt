package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

internal fun chatFixture() = UiSnapshot(ScreenRecognizer.TARGET_PACKAGE, 12, Box(0, 0, 1000, 2000), listOf(
    UiNode(0, null, emptyList(), Box(0, 0, 1000, 2000)),
    UiNode(1, 0, listOf("Chat"), Box(50, 60, 300, 120)),
    UiNode(2, 0, listOf("Ungelesen"), Box(50, 150, 250, 210), selected = true),
    UiNode(3, 0, emptyList(), Box(0, 230, 1000, 1900), list = true),
    UiNode(4, 3, emptyList(), Box(0, 240, 1000, 400), clickable = true),
    UiNode(5, 4, listOf("Neuer Snap"), Box(100, 310, 300, 350))
))

internal fun withExcludedRow(label: String = "My AI"): UiSnapshot {
    val s = chatFixture()
    return s.copy(nodes = s.nodes + listOf(
        UiNode(6, 3, emptyList(), Box(0, 420, 1000, 580), clickable = true),
        UiNode(7, 6, listOf(label), Box(100, 430, 300, 470)),
        UiNode(8, 6, listOf("Neuer Snap"), Box(100, 500, 300, 540), clickable = true)
    ))
}

class RecognitionTest {
    private fun recognize(s: UiSnapshot) = ScreenRecognizer.inspect(s)
    @Test fun bottomChatNavigationDoesNotDuplicateTitle() {
        val s = chatFixture()
        val nodes = s.nodes + UiNode(6, 0, listOf("Chat"), Box(100, 1910, 300, 1970))
        assertEquals(ScreenKind.CHAT_LIST, recognize(s.copy(nodes = nodes)).screen)
        assertEquals(2, recognize(s.copy(nodes = nodes)).facts.headers)
    }
    @Test fun twoHeadersBeforeContentRemainAmbiguous() {
        val s = chatFixture()
        val nodes = s.nodes + UiNode(6, 0, listOf("Chats"), Box(350, 60, 550, 120))
        assertEquals(ScreenKind.UNKNOWN, recognize(s.copy(nodes = nodes)).screen)
    }
    @Test fun missingUnreadFilterAllowsExactStatusInStructuralList() {
        val s = chatFixture().let { it.copy(nodes = it.nodes.filter { n -> n.index != 2 }) }
        val r = recognize(s)
        assertEquals(ScreenKind.CHAT_LIST, r.screen)
        assertEquals(FilterState.UNKNOWN, r.filter)
        assertEquals(1, r.rows.size)
    }
    @Test fun noFilterAndNoStatusCannotVerifyList() {
        val s = chatFixture().let { it.copy(nodes = it.nodes.filter { n -> n.index !in listOf(2, 5) }) }
        assertEquals(ScreenKind.UNKNOWN, recognize(s).screen)
    }
    @Test fun contactNamedChatCannotReplaceMissingTitle() {
        val s = chatFixture().let { it.copy(nodes = it.nodes.filter { n -> n.index !in listOf(1, 2) } +
            UiNode(6, 4, listOf("Chat"), Box(100, 250, 300, 290))) }
        assertEquals(ScreenKind.UNKNOWN, recognize(s).screen)
    }
    @Test fun clickActionAndPathAreRequiredOnStatusItself() {
        val valid = directFixture()
        assertEquals(1, ScreenRecognizer.directSnapControls(valid).size)
        for (replacement in listOf(valid.nodes[5].copy(canClick = false),
            valid.nodes[5].copy(clickable = false), valid.nodes[5].copy(path = emptyList()))) {
            val changed = valid.copy(nodes = valid.nodes.map { if (it.index == 5) replacement else it })
            assertTrue(ScreenRecognizer.directSnapControls(changed).isEmpty())
        }
    }
    @Test fun semanticListFindsRowWithoutFixedCoordinates() {
        val r = recognize(chatFixture())
        assertEquals(ScreenKind.CHAT_LIST, r.screen)
        assertEquals(FilterState.SELECTED, r.filter)
        assertEquals(listOf(Box(0, 240, 1000, 400)), r.rows)
    }
    @Test fun translationAndScalingPreserveSemantics() {
        val s = chatFixture()
        fun shift(b: Box) = Box(b.left * 2 + 70, b.top * 2 + 130, b.right * 2 + 70, b.bottom * 2 + 130)
        val moved = s.copy(bounds = shift(s.bounds), nodes = s.nodes.map { it.copy(bounds = shift(it.bounds)) })
        assertEquals(1, recognize(moved).rows.size)
    }
    @Test fun unselectedFilterIsNeverAssumedSelected() {
        val s = chatFixture(); val r = recognize(s.copy(nodes = s.nodes.map { it.copy(selected = false) }))
        assertEquals(FilterState.UNKNOWN, r.filter)
    }
    @Test fun explicitUncheckedFilterCanBeReportedAsNotSelected() {
        val s = chatFixture()
        val nodes = s.nodes.map { if (it.index == 2) it.copy(selected = false, selectionKnown = true) else it }
        assertEquals(FilterState.NOT_SELECTED, recognize(s.copy(nodes = nodes)).filter)
    }
    @Test fun englishLabelsSupported() {
        val s = chatFixture().let { it.copy(nodes = it.nodes.map { n -> n.copy(labels = when(n.index) {
            1 -> listOf("Chats"); 2 -> listOf("Unread"); 5 -> listOf("2 new snaps"); else -> n.labels
        }) }) }
        assertEquals(1, recognize(s).rows.size)
    }
    @Test fun ordinaryChatSubstringIsNotHeader() {
        val s = chatFixture(); val nodes = s.nodes.map { if (it.index == 1) it.copy(labels = listOf("Chat with Alice")) else it }
        assertEquals(ScreenKind.UNKNOWN, recognize(s.copy(nodes = nodes)).screen)
    }
    @Test fun invisibleHeaderDoesNotCount() {
        val s = chatFixture()
        assertEquals(ScreenKind.UNKNOWN, recognize(s.copy(nodes = s.nodes.map {
            if (it.index == 1) it.copy(visible = false) else it
        })).screen)
    }
    @Test fun editableFieldBlocksEvenWhenReplyLabelPresent() {
        val s = chatFixture()
        assertEquals(ScreenKind.BLOCKED, recognize(s.copy(nodes = s.nodes +
            UiNode(6, 0, listOf("Antworten"), Box(0, 1800, 900, 1900), editable = true))).screen)
    }
    @Test fun adAndMyAiAreConservativeWholeScreenExclusions() {
        for (label in listOf("My AI", "Gesponsert", "Sponsored content", "Anzeige", "Ad")) {
            val s = chatFixture()
            assertEquals(label, ScreenKind.BLOCKED, recognize(s.copy(nodes = s.nodes +
                UiNode(6, 0, listOf(label), Box(0, 400, 900, 500)))).screen)
        }
    }
    @Test fun myAiRowIsExcludedWhileOrdinaryRowRemains() {
        val r = recognize(withExcludedRow())
        assertEquals(ScreenKind.CHAT_LIST, r.screen)
        assertEquals(listOf(Box(0, 240, 1000, 400)), r.rows)
        assertEquals(listOf(Box(0, 420, 1000, 580)), r.excludedRows)
        assertEquals(2, r.facts.statusLabels)
        assertEquals(1, r.facts.myAiLabels)
        assertTrue(r.rowsEvaluated)
    }
    @Test fun advertisingRowIsExcludedWithoutBlockingOtherRows() {
        val r = recognize(withExcludedRow("Gesponsert"))
        assertEquals(ScreenKind.CHAT_LIST, r.screen)
        assertEquals(1, r.rows.size)
        assertEquals(1, r.excludedRows.size)
        assertEquals(1, r.facts.adLabels)
    }
    @Test fun clickableStatusChildDoesNotEscapeSiblingExclusion() {
        val r = recognize(withExcludedRow())
        assertFalse(r.rows.any { it.intersects(Box(100, 500, 300, 540)) })
    }
    @Test fun overlappingRowsAreConservativelyExcluded() {
        val s = withExcludedRow()
        val nodes = s.nodes.map { if (it.index == 4) it.copy(bounds = Box(0, 240, 1000, 450)) else it }
        assertTrue(recognize(s.copy(nodes = nodes)).rows.isEmpty())
    }
    @Test fun disabledMyAiLabelStillExcludesItsRow() {
        val s = withExcludedRow()
        val nodes = s.nodes.map { if (it.index == 7) it.copy(enabled = false) else it }
        assertEquals(1, recognize(s.copy(nodes = nodes)).excludedRows.size)
    }
    @Test fun partiallyClippedUnscopedExclusionStillBlocksCapture() {
        val s = chatFixture()
        val r = recognize(s.copy(nodes = s.nodes + UiNode(6, 0, listOf("My AI"), Box(-10, 420, 500, 580))))
        assertEquals("UNSCOPED_AD_OR_MY_AI", r.reason)
        assertFalse(r.rowsEvaluated)
    }
    @Test fun cyclicRowAncestryCannotAuthorizeCapture() {
        val s = withExcludedRow()
        val nodes = s.nodes.map { if (it.index == 6) it.copy(parent = 6) else it }
        assertEquals(ScreenKind.BLOCKED, recognize(s.copy(nodes = nodes)).screen)
    }
    @Test fun incompleteTreeCountsDoNotRelaxRecognition() {
        for (s in listOf(chatFixture().copy(nodeLimitReached = true), chatFixture().copy(missingChildren = 1))) {
            val r = recognize(s)
            assertEquals("INCOMPLETE_TREE", r.reason)
            assertFalse(r.rowsEvaluated)
        }
    }
    @Test fun wrongPackageAndTruncatedTreeRejected() {
        assertEquals(ScreenKind.UNKNOWN, recognize(chatFixture().copy(packageName = "other.app")).screen)
        assertEquals(ScreenKind.UNKNOWN, recognize(chatFixture().copy(truncated = true)).screen)
    }
    @Test fun noListAndDuplicateFiltersAreUnknown() {
        val s = chatFixture()
        assertEquals(ScreenKind.UNKNOWN, recognize(s.copy(nodes = s.nodes.filter { !it.list })).screen)
        assertEquals(ScreenKind.UNKNOWN, recognize(s.copy(nodes = s.nodes +
            UiNode(7, 0, listOf("Unread"), Box(300, 150, 500, 210)))).screen)
    }
    @Test fun rowRecognitionDoesNotInventNativeClickCapability() {
        val s = chatFixture().let { it.copy(nodes = it.nodes.map { n -> n.copy(clickable = false) }) }
        assertEquals(1, recognize(s).rows.size)
        assertTrue(ScreenRecognizer.directSnapControls(s).isEmpty())
    }
    @Test fun labelInMessageDoesNotBecomeSnapStatus() {
        val s = chatFixture()
        assertTrue(recognize(s.copy(nodes = s.nodes.map {
            if (it.index == 5) it.copy(labels = listOf("a new snap is coming")) else it
        })).rows.isEmpty())
    }
    @Test fun stabilityResetsOnMovementSelectionWindowAndExplicitReset() {
        val tracker = StableObservation(); val s = chatFixture()
        assertFalse(tracker.observe(s)); assertTrue(tracker.observe(s))
        assertFalse(tracker.observe(s.copy(windowId = 99)))
        assertFalse(tracker.observe(s))
        val changed = s.copy(nodes = s.nodes.map { it.copy(selected = false) })
        assertFalse(tracker.observe(changed)); assertTrue(tracker.observe(changed))
        tracker.reset(); assertFalse(tracker.observe(changed))
    }
}
