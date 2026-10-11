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
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.LinearLayout
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
    private val log = DiagnosticLog(160)
    private val stability = StableObservation()
    private val existingSelection = ExistingSnapSelection()
    private val visualConfirmation = VisualConfirmationTracker()
    private val batch = ExistingSnapBatch()
    private var batchMode = false
    private var batchDeadline = 0L
    private var returnFlow: ReturnAfterOpen? = null
    private var totalAttempts = 0
    private var totalAccepted = 0
    private var started = 0L
    private var lastSample = -10_000L
    private var frameEpoch = 0L
    private var pending: Capture? = null
    private var stopButton: View? = null
    private var lastCode = ""
    private var failures = 0
    var status = "Bereit. Diagnose nur nach START."
        private set
    val running get() = session.running
    val report get() = "SnapAutoOpener ${BuildConfig.VERSION_NAME}\nAndroid API ${Build.VERSION.SDK_INT}\n" +
        "Modus: ${if (batchMode) "SICHTBAREN BESTAND ÖFFNEN" else if (session.mode == SessionMode.INTERACTIVE) "EINEN SNAP ÖFFNEN" else "DIAGNOSE"}; " +
        "Klickversuche: ${if (batchMode) totalAttempts else session.actionAttempts}; " +
        "angenommene Klicks: ${if (batchMode) totalAccepted else session.acceptedClicks}\n" +
        "Ob ein Snap angezeigt wird, muss am Gerät geprüft werden.\nProfilidentität: nicht bestimmt\n" +
        "$status\n\n${log.render()}"

    private data class Capture(val token: Long, val epoch: Long, val time: Long, val snapshot: UiSnapshot)
    private data class ImageAnalysis(val detail: String, val target: VisualIcon? = null,
        val targets: List<VisualIcon> = emptyList())
    private val actions by lazy {
        AndroidActionExecutor(this, handler, session, ::actionSnapshot) { record("ACTION", it) }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = this
    }

    fun startDiagnostic() = startSession(SessionMode.DIAGNOSTIC)

    fun startSingleSnap() = startSession(SessionMode.INTERACTIVE)

    fun startExistingSnaps() = startSession(SessionMode.INTERACTIVE, batchMode = true)

    private fun startSession(mode: SessionMode, batchMode: Boolean = false) {
        stopDiagnostic("Neuer manueller Start", save = false)
        this.batchMode = batchMode
        batchDeadline = 0L
        returnFlow = null
        totalAttempts = 0
        totalAccepted = 0
        log.clear()
        started = SystemClock.elapsedRealtime()
        session.start(started, mode = mode)
        existingSelection.reset()
        visualConfirmation.reset()
        lastSample = -10_000L
        failures = 0
        lastCode = ""
        status = if (mode == SessionMode.INTERACTIVE)
            "Chatliste öffnen, dann START im schwebenden Bedienfeld drücken (30 Sekunden)."
            else "30 Sekunden: gewünschte Snapchat-Instanz manuell öffnen."
        record("START", if (mode == SessionMode.INTERACTIVE)
            "Öffnungsmodus bereitgestellt; noch keine Bestandsaufnahme. Chatliste öffnen und START im Bedienfeld drücken."
            else "Diagnose: keine Bedienaktionen. Gewünschte Chatliste manuell öffnen.")
        if (!showStopOverlay()) {
            stopDiagnostic("STOP-Overlay nicht verfügbar; Lauf nicht gestartet.")
            return
        }
        handler.post(tick)
    }

    private fun captureExistingForOpening() {
        val now = SystemClock.elapsedRealtime()
        if (!session.accepts(session.generation, now) || session.mode != SessionMode.INTERACTIVE ||
            existingSelection.initial != null) return
        val snap = actionSnapshot()
        val evidence = snap?.let(ScreenRecognizer::inspect)
        if (snap != null && evidence != null) record("START_EVIDENCE", RecognitionReport.describe(snap, evidence, false))
        if (snap == null || evidence == null || !existingSelection.capture(snap, evidence)) {
            if (snap != null) record("ROW_STRUCTURE", RecognitionReport.structure(snap))
            stopDiagnostic("START: sichtbarer Bestand nicht eindeutig erkennbar (${evidence?.reason ?: "NO_TARGET"}); kein Warten auf spätere Ziele.")
            return
        }
        record("INITIAL_SELECTION", "Bestand beim START: ${existingSelection.controls.size} direkte Klickziele; ${existingSelection.rows.size} Kandidatenzeilen. Keine späteren Ziele aufnehmen.")
        if (existingSelection.controls.isEmpty() && existingSelection.rows.isEmpty()) {
            record("ROW_STRUCTURE", RecognitionReport.structure(snap))
            stopDiagnostic("Bestand ohne eindeutig zugeordnete Snap-Statuszeile; kein Klick ausgeführt.")
            return
        }
        // The actual opening session begins at the in-Snapchat START, not when
        // the user is still choosing the app/profile. No future target discovery.
        session.start(now, mode = SessionMode.INTERACTIVE)
        if (batchMode) {
            batch.reset(snap)
            batchDeadline = now + 120_000
            record("BATCH_START", "Höchstens 10 beim START sichtbare eindeutige Zeilen; 5 Sekunden Wartezeit vor einmal Zurück; 120 Sekunden Gesamtlaufzeit. Keine Snap-ID-Garantie.")
        }
        stability.reset()
        stability.observe(snap)
        visualConfirmation.reset()
        status = "Vorhandenes Snap-Ziel erfasst; warte auf unveränderte Bestätigung."
    }

    fun stopDiagnostic(reason: String = "STOP durch Nutzer", save: Boolean = true) {
        val wasRunning = session.running
        session.stop()
        returnFlow = null
        pending = null
        frameEpoch++
        stability.reset()
        visualConfirmation.reset()
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
                batchMode && batchDeadline > 0 && now >= batchDeadline -> { stopDiagnostic("120-Sekunden-Gesamtlimit"); return }
                session.expired(now) -> { stopDiagnostic(if (batchMode && batchDeadline > 0)
                    "Öffnungs-/Rückkehrfrist abgelaufen." else "30-Sekunden-Timeout"); return }
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
            if (session.mode == SessionMode.INTERACTIVE && existingSelection.initial != null) {
                stopDiagnostic("Zielfenster nach Bestandserfassung verlassen oder Tastatur geöffnet.")
                return
            }
            recordOnce("NO_TARGET", "Kein eindeutig aktives Snapchat-Fenster ohne Tastatur sichtbar.")
            return
        }
        val evidence = ScreenRecognizer.inspect(snap)
        val stable = stability.observe(snap)
        val description = RecognitionReport.describe(snap, evidence, stable)
        recordOnce(evidence.reason, description)
        var imageRows = evidence.rows
        if (session.mode == SessionMode.INTERACTIVE) {
            if (existingSelection.initial == null) return
            if (batchMode) {
                if (!batch.sameWindow(snap)) { stopDiagnostic("Bestandsfenster geändert."); return }
                val flow = returnFlow
                if (flow != null) {
                    val decision = flow.poll(snap, SystemClock.elapsedRealtime(), stable)
                    recordOnce(decision.reason, "Rückkehrphase: ${decision.step}; keine Viewer-Tipps oder Viewer-Aufnahmen.")
                    when (decision.step) {
                        ReturnStep.STOP -> stopDiagnostic("Rückkehr beendet: ${decision.reason}")
                        ReturnStep.BACK -> {
                            val accepted = actions.returnToList(session.generation, snap, flow)
                            record("BACK_RESULT", "angenommen=$accepted; Chatliste noch zu bestätigen")
                            if (!accepted) stopDiagnostic("Zurück nicht angenommen; kein Ersatzversuch.")
                        }
                        ReturnStep.LIST_READY -> {
                            record("LIST_RETURNED", "Chatliste wiederholt erkannt; verbliebene Startziele=${batch.remainingCount}; übersprungen=${batch.skipped}")
                            returnFlow = null
                            if (batch.next(snap, evidence) == null) { finishBatch(); return }
                            val cycleStart = SystemClock.elapsedRealtime()
                            val duration = minOf(30_000, batchDeadline - cycleStart)
                            if (duration <= 0) { stopDiagnostic("120-Sekunden-Gesamtlimit"); return }
                            session.start(cycleStart, duration, SessionMode.INTERACTIVE)
                            visualConfirmation.reset()
                            stability.reset()
                            frameEpoch++
                            status = "Chatliste bestätigt; prüfe nächstes ursprüngliches Ziel."
                        }
                        ReturnStep.WAIT -> Unit
                    }
                    return
                }
                if (session.actionAttempts > 0) return
                if (!batch.frozen && !existingSelection.unchanged(snap)) {
                    stopDiagnostic("Liste vor visueller Bestandserfassung verändert; keine späteren Ziele aufgenommen."); return
                }
                if (evidence.screen == ScreenKind.BLOCKED) { stopDiagnostic("Sicherheitsstopp: ${evidence.reason}"); return }
                if (!stable) return
                if (Build.VERSION.SDK_INT < 34 && !batch.frozen) {
                    if (!batch.freeze(snap, existingSelection.controls.mapNotNull { control ->
                            evidence.rows.singleOrNull { it.contains(control.bounds) } })) {
                        stopDiagnostic("Kein eindeutiger nativer Startbestand; Bilder benötigen Android 14."); return
                    }
                }
                if (batch.frozen) {
                    if (evidence.screen != ScreenKind.CHAT_LIST) { stopDiagnostic("Chatliste vor nächstem Öffnen nicht bestätigt."); return }
                    val row = batch.next(snap, evidence)
                    if (row == null) { finishBatch(); return }
                    val native = ScreenRecognizer.directSnapControls(snap, evidence).firstOrNull { row.contains(it.bounds) }
                    if (native != null) { openNative(snap, native); return }
                    imageRows = listOf(row)
                }
            } else {
            if (session.actionAttempts > 0) return // Await completion; never capture a Snap viewer.
            if (evidence.screen == ScreenKind.BLOCKED) {
                stopDiagnostic("Sicherheitsstopp: ${evidence.reason}")
                return
            }
            if (!existingSelection.unchanged(snap)) {
                stopDiagnostic("Liste seit Bestandserfassung verändert; keine neuen Ziele aufgenommen.")
                return
            }
            if (!stable) return
            val target = existingSelection.controls.firstOrNull()
            if (target != null) {
                openNative(snap, target)
                return
            }
            }
            // No native click capability: only a separately verified visual icon may authorize a tap.
        }
        if (!stable || evidence.screen != ScreenKind.CHAT_LIST || evidence.rows.isEmpty()) return
        if (session.mode == SessionMode.DIAGNOSTIC && evidence.filter != FilterState.SELECTED) return
        if (Build.VERSION.SDK_INT < 34) {
            if (session.mode == SessionMode.INTERACTIVE) {
                stopDiagnostic("Visueller Öffnungsversuch benötigt Android 14 oder neuer.")
                return
            }
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
                            val analysis = try { analyze(bitmap, snap, imageRows) }
                                catch (_: RuntimeException) { ImageAnalysis("IMAGE_ANALYSIS_FAILED") }
                                finally { bitmap.recycle() }
                            handler.post {
                                if (valid(capture)) {
                                    pending = null
                                    failures = 0
                                    record("VISUAL_HINT", analysis.detail)
                                    if (session.mode == SessionMode.INTERACTIVE) processVisual(capture, analysis)
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

    private fun processVisual(capture: Capture, analysis: ImageAnalysis) {
        var target = analysis.target
        if (batchMode) {
            val evidence = ScreenRecognizer.inspect(capture.snapshot)
            if (!batch.frozen) {
                val nativeRows = existingSelection.controls.mapNotNull { control ->
                    evidence.rows.singleOrNull { it.contains(control.bounds) } }
                if (!batch.freeze(capture.snapshot, analysis.targets.map { it.row } + nativeRows)) {
                    stopDiagnostic("Kein eindeutig wiedererkennbarer sichtbarer Startbestand; keine Aktion."); return
                }
                record("BATCH_FROZEN", "Eindeutige ursprüngliche Zeilen=${batch.initialCount}; keine späteren Ziele ergänzen.")
            }
            val row = batch.next(capture.snapshot, evidence)
            if (row == null) { finishBatch(); return }
            val native = ScreenRecognizer.directSnapControls(capture.snapshot, evidence).firstOrNull { row.contains(it.bounds) }
            if (native != null) { openNative(capture.snapshot, native); return }
            target = analysis.targets.singleOrNull { it.row == row }
        }
        val confirmation = visualConfirmation.observe(capture.token, capture.snapshot, target, capture.time)
        record("VISUAL_CONFIRMATION", confirmation.reason)
        val proof = confirmation.proof
        if (proof == null) {
            if (confirmation.reason != "SECOND_IMAGE_REQUIRED") stopDiagnostic("Bildprüfung beendet: ${confirmation.reason}")
            return
        }
        val request = ActionRequest(ActionKind.OPEN_SNAP, capture.snapshot, proof.target.icon, visual = proof)
        val result = actions.openVisualOnce(capture.token, request) { completed ->
            record("TAP_COMPLETION", "abgeschlossen=$completed; kein Nachweis einer Snap-Öffnung")
            if (!completed) stopDiagnostic("Symbol-Tipp von Android abgebrochen.")
            else afterOpening(capture.snapshot)
        }
        record("OPEN_RESULT", "${result.reason}; versucht=${result.attempted}; angenommen=${result.accepted}")
        accountBatch(result, capture.snapshot, proof.target.row)
        if (!session.running) return
        if (!result.accepted) {
            stopDiagnostic("Visueller Öffnungsversuch beendet: ${result.reason}")
        } else {
            status = "Ein Symbol-Tipp angenommen; warte auf Abschluss."
            handler.postDelayed({
                if (session.accepts(capture.token, SystemClock.elapsedRealtime()) && returnFlow == null)
                    stopDiagnostic("Tipp-Abschluss nicht bestätigt; kein weiterer Versuch.")
            }, 2_000)
        }
    }

    private fun openNative(snap: UiSnapshot, target: UiNode) {
        val row = ScreenRecognizer.inspect(snap).rows.singleOrNull { it.contains(target.bounds) }
        if (batchMode && row == null) { stopDiagnostic("Natives Ziel hat keine eindeutige Bestandszeile."); return }
        val result = actions.openSnapOnce(session.generation,
            ActionRequest(ActionKind.OPEN_SNAP, snap, target.bounds, target.index))
        record("OPEN_RESULT", "${result.reason}; versucht=${result.attempted}; angenommen=${result.accepted}")
        if (row != null) accountBatch(result, snap, row)
        if (!result.accepted) stopDiagnostic("Öffnungsversuch beendet: ${result.reason}")
        else if (session.running) afterOpening(snap)
    }

    private fun accountBatch(result: OpenResult, snap: UiSnapshot, row: Box) {
        if (!batchMode) return
        if (result.attempted) totalAttempts++
        if (result.accepted) {
            totalAccepted++
            if (!batch.consume(snap, row)) stopDiagnostic("Startziel nicht mehr zugeordnet; Lauf beendet.")
        }
    }

    private fun afterOpening(before: UiSnapshot) {
        if (!session.running) return
        if (!batchMode) { stopDiagnostic("Ein Öffnungsversuch ausgeführt. Bitte prüfen, ob ein Snap angezeigt wird."); return }
        returnFlow = ReturnAfterOpen(before, SystemClock.elapsedRealtime())
        stability.reset()
        status = "Öffnungsversuch abgeschlossen; warte 5 Sekunden, danach einmal Zurück zur Chatliste."
        record("RETURN_ARMED", "Kontextbezogene Rückkehr nach abgeschlossener Öffnungsaktion; kein behaupteter Viewer-Nachweis.")
    }

    private fun finishBatch() = stopDiagnostic("Sichtbarer Startbestand beendet: $totalAccepted angenommene Öffnungen; ${batch.skipped} veränderte/fehlende Ziele übersprungen.")

    private fun actionSnapshot(): UiSnapshot? {
        if (connected !== this || !getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) return null
        return snapshot()
    }

    private fun valid(capture: Capture) = connected === this && pending === capture &&
        session.accepts(capture.token, SystemClock.elapsedRealtime()) && frameEpoch == capture.epoch &&
        getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked && snapshot() == capture.snapshot

    private fun finishStale(capture: Capture) {
        if (pending !== capture) return
        pending = null
        if (session.accepts(capture.token, SystemClock.elapsedRealtime())) {
            if (session.mode == SessionMode.INTERACTIVE) {
                stopDiagnostic("Fenster während der Bildprüfung verändert; kein Tipp ausgeführt.")
                return
            }
            stability.reset()
            record("STALE_FRAME", "Verändertes Fenster/alte Aufnahme verworfen.")
        }
    }

    private fun captureFailure(capture: Capture, code: String) {
        if (!valid(capture)) { finishStale(capture); return }
        pending = null
        if (session.mode == SessionMode.INTERACTIVE) {
            record("CAPTURE_FAILED", code)
            stopDiagnostic("Bildprüfung fehlgeschlagen; kein Ersatzklick: $code")
            return
        }
        failures++
        record("CAPTURE_FAILED", "$code; Versuch $failures/3; kein Screenshot-Fallback.")
        if (failures >= 3) stopDiagnostic("Drei Screenshot-Fehler; Diagnose beendet.")
    }

    private fun analyze(bitmap: Bitmap, snap: UiSnapshot, rows: List<Box>): ImageAnalysis {
        val bounds = snap.bounds
        if (bitmap.width != bounds.width || bitmap.height != bounds.height) {
            return ImageAnalysis("GEOMETRY_MISMATCH: Bild=${bitmap.width}x${bitmap.height}; Fenster=${bounds.width}x${bounds.height}; keine Zuordnung.")
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
            val icons = VisualSnapTarget.detectAll(pixels, scaled.width, scaled.height, regions, resources.displayMetrics.density * sx)
            val mapped = icons.map { target ->
                val row = rows[regions.indexOf(target.row)]
                val b = target.icon
                target.copy(row = row, icon = Box((b.left / sx + bounds.left).roundToInt(),
                    (b.top / sy + bounds.top).roundToInt(), (b.right / sx + bounds.left).roundToInt(),
                    (b.bottom / sy + bounds.top).roundToInt()))
            }
            return ImageAnalysis("Fenster=${bitmap.width}x${bitmap.height}; Farbhinweise=${candidates.size}; Bildschirmpositionen=$positions; " +
                "eindeutige gefüllte Snap-Symbole=${mapped.size}. Farbhilfe allein beweist keinen Snap.", mapped.firstOrNull(), mapped)
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
            text = "STOP"
            contentDescription = "Lauf sofort stoppen"
            setOnClickListener { stopDiagnostic() }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            if (session.mode == SessionMode.INTERACTIVE) addView(Button(this@SnapAccessibilityService).apply {
                text = "START"
                contentDescription = if (batchMode) "Sichtbaren Startbestand erfassen und nacheinander öffnen"
                    else "Sichtbaren Bestand erfassen und einen vorhandenen Snap öffnen"
                setOnClickListener { isEnabled = false; captureExistingForOpening() }
            })
            addView(button)
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END }
        return try {
            getSystemService(WindowManager::class.java).addView(panel, params)
            stopButton = panel
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
