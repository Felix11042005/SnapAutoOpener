package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

class DiagnosticSessionTest {
    @Test fun idleNeverAcceptsWork() { assertFalse(DiagnosticSession().accepts(0, 0)) }
    @Test fun deadlineIsExclusiveEvenWithoutEvents() {
        val s = DiagnosticSession(); val token = s.start(100)
        assertTrue(s.accepts(token, 30_099)); assertFalse(s.accepts(token, 30_100))
        assertTrue(s.expired(30_100))
    }
    @Test fun stopRejectsScreenshotCallback() {
        val s = DiagnosticSession(); val token = s.start(0); s.stop()
        assertFalse(s.accepts(token, 1))
    }
    @Test fun restartRejectsPreviousRunCallback() {
        val s = DiagnosticSession(); val first = s.start(0); val second = s.start(1)
        assertFalse(s.accepts(first, 2)); assertTrue(s.accepts(second, 2))
    }
    @Test fun repeatedStopAndProcessRecreationRemainDisarmed() {
        val s = DiagnosticSession(); val token = s.start(0); s.stop(); s.stop()
        assertFalse(s.accepts(token, 1)); assertFalse(DiagnosticSession().running)
    }
    @Test fun defaultStartCannotInheritInteractiveMode() {
        val s = DiagnosticSession(); s.start(0, mode = SessionMode.INTERACTIVE); s.start(1)
        assertEquals(SessionMode.DIAGNOSTIC, s.mode)
    }
    @Test(expected = IllegalArgumentException::class) fun durationIsBounded() { DiagnosticSession().start(0, 120_001) }
    @Test fun boundedLogDropsOldEntries() {
        val log = DiagnosticLog(2)
        log.add(0, "ONE", "a"); log.add(1000, "TWO", "b"); log.add(2000, "THREE", "c")
        assertFalse(log.render().contains("ONE")); assertEquals(2, log.render().lines().size)
        log.clear(); assertEquals("", log.render())
    }
}
