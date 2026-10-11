package de.example.snapauto

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class SnapAccessibilityService : AccessibilityService() {
    companion object {
        var connected: SnapAccessibilityService? = null
            private set
    }
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val session = DiagnosticSession()
    private val log = DiagnosticLog()
    private val stability = StableObservation()
    private var started = 0L
    private var lastSample = -10_000L
    private var frameEpoch = 0L
    private var pending: Capture? = null
    private var stopButton: Button? = null
    private var lastCode = ""
    private var failures = 0
    var status = "Bereit. Diagnose nur nach START."
        private set
    val running get() = session.running
    val report get() = "SnapAutoOpener ${BuildConfig.VERSION_NAME}\nAndroid API ${Build.VERSION.SDK_INT}\n" +
        "Modus: DIAGNOSE; ausgeführte Bedienaktionen: 0\nProfilidentität: nicht bestimmt\n" +
        "$status\n\n${log.render()}"

    private data class Capture(val token: Long, val epoch: Long, val time: Long, val snapshot: UiSnapshot)
    @Suppress("unused")
    private val actions by lazy {
        AndroidActionExecutor(this, handler, session, ::snapshot) { record("ACTION", it) }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = this
    }

    fun startDiagnostic() {
        stopDiagnostic("Neuer manueller Start", save = false)
        log.clear()
        started = SystemClock.elapsedRealtime()
        session.start(started) // Deliberately DIAGNOSTIC; no automatic interaction release.
        lastSample = -10_000L
        failures = 0
        lastCode = ""
        status = "30 Sekunden: gewünschte Snapchat-Instanz manuell öffnen."
        record("START", "Warte auf sichtbare Chatliste; keine automatische App-Auswahl.")
        if (!showStopOverlay()) {
            stopDiagnostic("STOP-Overlay nicht verfügbar; Diagnose nicht gestartet.")
            return
        }
        handler.post(tick)
    }

    fun stopDiagnostic(reason: String = "STOP durch Nutzer", save: Boolean = true) {
        val wasRunning = session.running
        session.stop()
        pending = null
        frameEpoch++
        stability.reset()
        handler.removeCallbacks(tick)
        removeStopOverlay()
        status = reason
        if (wasRunning) record("STOP", reason)
        if (save) getSharedPreferences("snapauto_diag", MODE_PRIVATE).edit()
            .clear().putString("last_report", report).apply()
    }

    fun resetReport() {
        stopDiagnostic("Zurückgesetzt", save = false)
        log.clear()
        getSharedPreferences("snapauto_diag", MODE_PRIVATE).edit().clear().apply()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!session.running) return
            val now = SystemClock.elapsedRealtime()
            val power = getSystemService(PowerManager::class.java)
            val keyguard = getSystemService(KeyguardManager::class.java)
            when {
                session.expired(now) -> { stopDiagnostic("30-Sekunden-Timeout"); return }
                !power.isInteractive || keyguard.isKeyguardLocked -> { stopDiagnostic("Bildschirm gesperrt/aus"); return }
                pending?.let { now - it.time >= 5_000 } == true -> { stopDiagnostic("Screenshot-Timeout"); return }
            }
            if (pending == null && now - lastSample >= 1_500) {
                lastSample = now
                sample()
            }
            if (session.running) handler.postDelayed(this, 500)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!session.running || event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event.packageName?.toString() == ScreenRecognizer.TARGET_PACKAGE) frameEpoch++
    }

    @Suppress("DEPRECATION")
    private fun snapshot(): UiSnapshot? {
        val visibleWindows = windows
        try {
            if (visibleWindows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) return null
            val app = visibleWindows.filter {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive && it.isFocused
            }.singleOrNull() ?: return null
            val root = app.root ?: return null
            if (root.packageName?.toString() != ScreenRecognizer.TARGET_PACKAGE) {
                root.recycle()
                return null
            }
            val rect = Rect()
            app.getBoundsInScreen(rect)
            return SnapshotReader.read(root, rect)
        } catch (_: RuntimeException) {
            return null
        } finally { visibleWindows.forEach { it.recycle() } }
    }

    private fun sample() {
        val snap = snapshot()
        if (snap == null) {
            stability.reset()
            recordOnce("NO_TARGET", "Kein eindeutig aktives Snapchat-Fenster ohne Tastatur sichtbar.")
            return
        }
        val evidence = ScreenRecognizer.inspect(snap)
        val stable = stability.observe(snap)
        val description = RecognitionReport.describe(snap, evidence, stable)
        recordOnce(evidence.reason, description)
        if (!stable || evidence.screen != ScreenKind.CHAT_LIST || evidence.filter != FilterState.SELECTED || evidence.rows.isEmpty()) return
        if (Build.VERSION.SDK_INT < 34) {
            recordOnce("WINDOW_SCREENSHOT_UNAVAILABLE", "Bilddiagnose benötigt Android 14; Baumdiagnose bleibt verfügbar.")
            return
        }
        val capture = Capture(session.generation, frameEpoch, SystemClock.elapsedRealtime(), snap)
        pending = capture
        try {
            takeScreenshotOfWindow(snap.windowId, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    try {
                        if (!valid(capture)) { finishStale(capture); return }
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        val bitmap = try { hardware?.copy(Bitmap.Config.ARGB_8888, false) }
                            finally { hardware?.recycle() }
                        if (bitmap == null) { captureFailure(capture, "BITMAP_UNAVAILABLE"); return }
                        worker.execute {
                            val analysis = try { analyze(bitmap, snap, evidence.rows) }
                                catch (_: RuntimeException) { "IMAGE_ANALYSIS_FAILED" }
                                finally { bitmap.recycle() }
                            handler.post {
                                if (valid(capture)) {
                                    pending = null
                                    failures = 0
                                    record("VISUAL_HINT", analysis)
                                } else finishStale(capture)
                            }
                        }
                    } catch (_: RuntimeException) {
                        captureFailure(capture, "CAPTURE_EXCEPTION")
                    } finally { buffer.close() }
                }
                override fun onFailure(errorCode: Int) = captureFailure(capture, "SCREENSHOT_ERROR_$errorCode")
            })
        } catch (_: RuntimeException) { captureFailure(capture, "SCREENSHOT_REQUEST_FAILED") }
    }

    private fun valid(capture: Capture) = connected === this && pending === capture &&
        session.accepts(capture.token, SystemClock.elapsedRealtime()) && frameEpoch == capture.epoch &&
        getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked && snapshot() == capture.snapshot

    private fun finishStale(capture: Capture) {
        if (pending !== capture) return
        pending = null
        if (session.accepts(capture.token, SystemClock.elapsedRealtime())) {
            stability.reset()
            record("STALE_FRAME", "Verändertes Fenster/alte Aufnahme verworfen.")
        }
    }

    private fun captureFailure(capture: Capture, code: String) {
        if (!valid(capture)) { finishStale(capture); return }
        pending = null
        failures++
        record("CAPTURE_FAILED", "$code; Versuch $failures/3; kein Screenshot-Fallback.")
        if (failures >= 3) stopDiagnostic("Drei Screenshot-Fehler; Diagnose beendet.")
    }

    private fun analyze(bitmap: Bitmap, snap: UiSnapshot, rows: List<Box>): String {
        val bounds = snap.bounds
        if (bitmap.width != bounds.width || bitmap.height != bounds.height) {
            return "GEOMETRY_MISMATCH: Bild=${bitmap.width}x${bitmap.height}; Fenster=${bounds.width}x${bounds.height}; keine Zuordnung."
        }
        val scale = minOf(1f, 960f / maxOf(bitmap.width, bitmap.height))
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(),
            (bitmap.height * scale).roundToInt(), false)
        try {
            val sx = scaled.width.toFloat() / bitmap.width
            val sy = scaled.height.toFloat() / bitmap.height
            val regions = rows.map { Box(((it.left - bounds.left) * sx).roundToInt(),
                ((it.top - bounds.top) * sy).roundToInt(), ((it.right - bounds.left) * sx).roundToInt(),
                ((it.bottom - bounds.top) * sy).roundToInt()) }
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            val candidates = VisualEvidence.detect(pixels, scaled.width, scaled.height, regions, resources.displayMetrics.density * sx)
            val positions = candidates.take(12).joinToString { c ->
                val x = (c.bounds.left + c.bounds.right) / 2f / sx + bounds.left
                val y = (c.bounds.top + c.bounds.bottom) / 2f / sy + bounds.top
                "(${x.roundToInt()},${y.roundToInt()})"
            }
            return "Fenster=${bitmap.width}x${bitmap.height}; Farbhinweise=${candidates.size}; Bildschirmpositionen=$positions. Ungeöffneter Snap NICHT bewiesen."
        } finally { if (scaled !== bitmap) scaled.recycle() }
    }

    private fun recordOnce(code: String, detail: String) {
        val key = "$code|$detail"
        if (key == lastCode) return
        lastCode = key
        record(code, detail)
    }
    private fun record(code: String, detail: String) {
        log.add((SystemClock.elapsedRealtime() - started).coerceAtLeast(0), code, detail)
    }

    private fun showStopOverlay(): Boolean {
        val button = Button(this).apply {
            text = "STOP Diagnose"
            contentDescription = "Diagnose sofort stoppen"
            setOnClickListener { stopDiagnostic() }
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END }
        return try {
            getSystemService(WindowManager::class.java).addView(button, params)
            stopButton = button
            true
        } catch (_: RuntimeException) { false }
    }
    private fun removeStopOverlay() {
        stopButton?.let {
            try { getSystemService(WindowManager::class.java).removeView(it) } catch (_: RuntimeException) { }
        }
        stopButton = null
    }
    override fun onInterrupt() { stopDiagnostic("Bedienungshilfe unterbrochen") }
    override fun onUnbind(intent: Intent?): Boolean {
        stopDiagnostic("Bedienungshilfe getrennt")
        if (connected === this) connected = null
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        stopDiagnostic("Dienst beendet")
        if (connected === this) connected = null
        worker.shutdown()
        super.onDestroy()
    }
}
