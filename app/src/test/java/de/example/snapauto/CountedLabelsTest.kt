package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

class CountedLabelsTest {
    private fun status(value: String) = directFixture().let { it.copy(nodes = it.nodes.map { n ->
        if (n.index == 5) n.copy(labels = listOf(value)) else n
    }) }
    @Test fun countedSnapLabelsSupportNinePlusWithoutSubstringMatching() {
        for (text in listOf("9+ neue Snaps", "2 neue Snaps", "9+ new snaps", "Neuer Snap"))
            assertEquals(text, 1, ScreenRecognizer.directSnapControls(status(text)).size)
        for (text in listOf("Antworten: zweimal tippen", "Zugestellt", "Geöffnet", "Neuer Chat",
            "4 neue Chats", "Flamme entfacht", "Freund mit neuer Snap", "9+ neue Snaps senden")) {
            assertTrue(text, ScreenRecognizer.inspect(status(text)).rows.isEmpty())
            assertTrue(text, ScreenRecognizer.directSnapControls(status(text)).isEmpty())
        }
    }
    @Test fun clippedCountCreatesVisualRowButNeverNativeControl() {
        for (text in listOf("4 neue…", "9+ neue...", "2 new…")) {
            val s = status(text); val r = ScreenRecognizer.inspect(s)
            assertEquals(text, 1, r.rows.size)
            assertEquals(0, r.facts.statusLabels)
            assertEquals(1, r.facts.truncatedStatusLabels)
            assertTrue(ScreenRecognizer.directSnapControls(s).isEmpty())
        }
        for (text in listOf("neue…", "4 neue", "4 neue Nachrichten", "4 neue Ch…", "Antworten…"))
            assertTrue(text, ScreenRecognizer.inspect(status(text)).rows.isEmpty())
    }
    @Test fun unreadBadgeDoesNotBecomeTheOtherUnreadChatsButton() {
        for (text in listOf("Ungelesen 9+", "Ungelesen9+", "Unread 2", "Ungelesen")) {
            val s = chatFixture().let { it.copy(nodes = it.nodes.map { n ->
                if (n.index == 2) n.copy(labels = listOf(text), selected = false) else n
            }) }
            assertEquals(1, ScreenRecognizer.inspect(s).facts.filters)
            assertEquals(FilterState.UNKNOWN, ScreenRecognizer.inspect(s).filter)
        }
        val s = chatFixture().let { it.copy(nodes = it.nodes.map { n ->
            if (n.index == 2) n.copy(labels = listOf("Weitere ungelesene Chats")) else n
        }) }
        assertEquals(0, ScreenRecognizer.inspect(s).facts.filters)
    }
    @Test fun wholeConversationActionIsNotADirectSnapControl() {
        for (box in listOf(Box(0, 240, 1000, 400), Box(10, 250, 990, 390))) {
            val s = directFixture().let { it.copy(nodes = it.nodes.map { n ->
                if (n.index == 5) n.copy(bounds = box) else n
            }) }
            assertEquals(1, ScreenRecognizer.inspect(s).rows.size)
            assertTrue(ScreenRecognizer.directSnapControls(s).isEmpty())
        }
    }
}
