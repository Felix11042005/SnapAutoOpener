package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

class BatchOpeningTest {
    @Test fun visualBatchRequiresFreshTwoImageProofForEachOriginalTarget() {
        var current = batchFixture()
        fun icons(s: UiSnapshot): List<VisualIcon> {
            val rows = ScreenRecognizer.inspect(s).rows
            val pixels = IntArray(1000 * 2000) { 0xff222222.toInt() }
            for (row in rows) for (y in row.top + 110 until row.top + 122) for (x in 120 until 132)
                pixels[y * 1000 + x] = 0xffff0033.toInt()
            return VisualSnapTarget.detectAll(pixels, 1000, 2000, rows, 1f)
        }
        val originalIcons = icons(current)
        val batch = ExistingSnapBatch(); batch.reset(current)
        assertTrue(batch.freeze(current, originalIcons.map { it.row }))
        val session = DiagnosticSession(); var now = 0L; var taps = 0
        val opener = VisualSnapOpener(session, { now }, { current }, SnapTapPort { _, authorize ->
            if (!authorize()) false else { taps++; true }
        })
        repeat(2) { iteration ->
            val token = session.start(now, mode = SessionMode.INTERACTIVE)
            val row = batch.next(current, ScreenRecognizer.inspect(current))!!
            val icon = icons(current).single { it.row == row }
            val tracker = VisualConfirmationTracker()
            assertNull(tracker.observe(token, current, icon, now).proof)
            now += 600
            val proof = tracker.observe(token, current, icon, now).proof!!
            assertTrue(opener.open(token, ActionRequest(ActionKind.OPEN_SNAP, current, icon.icon, visual = proof)).accepted)
            assertTrue(batch.consume(current, row))
            if (iteration == 0) {
                val flow = ReturnAfterOpen(current, now)
                current = departedFixture(current); now += 5_000
                val back = ReturnToList(session, { now }, { current }, SnapBackPort { authorize -> authorize() })
                assertTrue(back.execute(token, current, flow).accepted)
                current = batchFixture(listOf("New arrival", "Fixture B")); now += 1_000
                assertEquals(ReturnStep.LIST_READY, flow.poll(current, now, true).step)
            }
        }
        assertNull(batch.next(current, ScreenRecognizer.inspect(current)))
        assertEquals(2, taps)
    }
    @Test fun opensTwoOriginalRowsWithOneBackBetweenAndIgnoresNewArrival() {
        val initial = batchFixture()
        val batch = ExistingSnapBatch(); batch.reset(initial)
        assertTrue(batch.freeze(initial, ScreenRecognizer.inspect(initial).rows))
        val session = DiagnosticSession()
        var now = 0L
        var current = initial
        var opens = 0
        var backs = 0
        val opener = SingleSnapOpener(session, { now }, { current }, SnapClickPort { _, _, authorize ->
            if (!authorize()) false else { opens++; true }
        })
        var token = session.start(now, mode = SessionMode.INTERACTIVE)
        val row = batch.next(current, ScreenRecognizer.inspect(current))!!
        val control = ScreenRecognizer.directSnapControls(current).single { row.contains(it.bounds) }
        assertTrue(opener.open(token, ActionRequest(ActionKind.OPEN_SNAP, current, control.bounds, control.index)).accepted)
        assertTrue(batch.consume(current, row))
        val flow = ReturnAfterOpen(current, now)
        current = departedFixture(current)
        now = 5_000
        assertEquals(ReturnStep.BACK, flow.poll(current, now, true).step)
        val back = ReturnToList(session, { now }, { current }, SnapBackPort { authorize ->
            if (!authorize()) false else { backs++; true }
        })
        assertTrue(back.execute(token, current, flow).accepted)
        current = batchFixture(listOf("New arrival", "Fixture B"))
        now = 6_000
        assertEquals(ReturnStep.LIST_READY, flow.poll(current, now, true).step)
        token = session.start(now, mode = SessionMode.INTERACTIVE)
        val next = batch.next(current, ScreenRecognizer.inspect(current))!!
        val nextControl = ScreenRecognizer.directSnapControls(current).single { next.contains(it.bounds) }
        assertTrue(opener.open(token, ActionRequest(ActionKind.OPEN_SNAP, current, nextControl.bounds, nextControl.index)).accepted)
        assertTrue(batch.consume(current, next))
        assertNull(batch.next(current, ScreenRecognizer.inspect(current)))
        assertEquals(2, opens)
        assertEquals(1, backs)
    }
}
