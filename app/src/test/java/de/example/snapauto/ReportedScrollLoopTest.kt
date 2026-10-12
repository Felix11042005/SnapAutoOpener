package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

/** Synthetic regression for the report: selected Unread, 8 visual targets, no native clicks,
 * one/two-pixel boundary jitter and changing decorative pixels. No private labels/images. */
class ReportedScrollLoopTest {
    @Test fun startsDownAndReachesARealVisualTapWithoutTopScanLoop() {
        val initial = inventoryPage((1..8).map { "Fixture $it" }).let { s -> s.copy(nodes = s.nodes.map { n ->
            if (ScreenRecognizer.markerKind(n) == "SNAP") n.copy(bounds = s.nodes.single { it.index == n.parent }.bounds,
                clickable = false, canClick = false) else n
        }) }
        val run = FullInventory(initial); run.filterReady(confirmedPage(initial))
        var current = initial; val session = DiagnosticSession(); var now = 0L; var taps = 0; var scrolls = 0
        repeat(3) { index ->
            current = shiftedRows(initial, index)
            val evidence = ScreenRecognizer.inspect(current)
            assertTrue(ScreenRecognizer.directSnapControls(current).isEmpty())
            val pixels = IntArray(1000 * 2000) { 0xff111111.toInt() }
            for (row in evidence.rows) for (y in row.top + 110 until row.top + 122) for (x in 120 until 132)
                pixels[y * 1000 + x] = 0xffff0033.toInt()
            val icons = VisualSnapTarget.detectAll(pixels, 1000, 2000, evidence.rows, 1f)
            assertEquals(8, icons.size)
            val token = session.start(now, mode = SessionMode.INTERACTIVE)
            val tracker = PageConfirmation(navigationOnly = true)
            tracker.observe(token, current, PageObservation(ChipObservation(FilterState.SELECTED, 1), icons, index * 2L), now)
            now += 600
            val page = tracker.observe(token, current, PageObservation(ChipObservation(FilterState.SELECTED, 2), icons, index * 2L + 1), now)!!
            val decision = run.page(current, evidence, page.verifiedRows, index * 2L + 1)
            if (index < 2) {
                assertEquals(InventoryStep.SCROLL_DOWN, decision.step)
                val action = ListNavigationOnce(session, { now }, { current }, run, ListNavigationPort { _, _, region, authorize ->
                    val plan = ListScroll.plan(region, ListNavigationKind.DOWN)!!
                    assertTrue(plan.startY > plan.endY)
                    if (authorize()) { scrolls++; true } else false
                })
                assertTrue(action.execute(token, ListNavigationRequest(ListNavigationKind.DOWN, page)).accepted)
            } else {
                assertEquals(InventoryStep.OPEN, decision.step)
                val proof = page.openingProof(decision.row!!)!!
                val action = VisualSnapOpener(session, { now }, { current }, SnapTapPort { _, authorize ->
                    if (authorize()) { taps++; true } else false
                })
                assertTrue(action.open(token, ActionRequest(ActionKind.OPEN_SNAP, current, proof.target.icon, visual = proof)).accepted)
                assertTrue(run.roster.consume(current, decision.row))
            }
            now += 2_000
        }
        assertEquals(2, scrolls); assertEquals(1, taps); assertEquals(8, run.roster.size)
    }
}
