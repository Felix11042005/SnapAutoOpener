package de.example.snapauto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import kotlin.math.max
import kotlin.math.min

class SnapAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("snapauto_diag", MODE_PRIVATE) }

    private var visualBusy = false
    private var lastScreenshotAt = 0L
    private var singleTapBusy = false
    private var batchBusy = false
    private var batchSeenY = mutableListOf<Float>()
    private var batchPhase = 0 // 0 filter, 1 chat list, 2 snap viewer
    private var viewerTaps = 0
    private var lastViewerSignature = 0L
    private var unchangedViewerFrames = 0
    private var activeBatchId = -1

    private val heartbeat = object : Runnable {
        override fun run() {
            prefs.edit()
                .putBoolean("service_connected", true)
                .putLong("heartbeat_at", System.currentTimeMillis())
                .apply()
            handler.postDelayed(this, 500)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs.edit()
            .putBoolean("service_connected", true)
            .putLong("service_connected_at", System.currentTimeMillis())
            .putLong("heartbeat_at", System.currentTimeMillis())
            .apply()
        handler.removeCallbacks(heartbeat)
        handler.post(heartbeat)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        val pkg = event?.packageName?.toString().orEmpty()

        prefs.edit()
            .putLong("last_event_at", now)
            .putString("last_event_package", pkg)
            .putString("last_event_type", event?.eventType?.toString().orEmpty())
            .putInt("event_count", prefs.getInt("event_count", 0) + 1)
            .apply()

        if (pkg != "com.snapchat.android") return

        prefs.edit()
            .putLong("last_snap_event_at", now)
            .putInt("snap_event_count", prefs.getInt("snap_event_count", 0) + 1)
            .apply()

        if (SnapState.singleTapRequested) {
            runSingleTapTest()
            return
        }

        if (SnapState.diagnosticRequested) {
            runVisualDiagnostic()
        }

        if (SnapState.batchMode && !SnapState.batchStopRequested) {
            runManualBatch()
        }
    }

    private fun runSingleTapTest() {
        val now = System.currentTimeMillis()
        if (now > SnapState.singleTapUntil) {
            SnapState.singleTapRequested = false
            prefs.edit().putString("visual_status", "Einzeltest abgelaufen: kein Tipp.").apply()
            return
        }
        if (singleTapBusy || visualBusy || now - lastScreenshotAt < 1200L) return
        singleTapBusy = true
        visualBusy = true
        takeVisualSnapshot { bitmap ->
            visualBusy = false
            if (!SnapState.singleTapRequested || System.currentTimeMillis() > SnapState.singleTapUntil) {
                bitmap?.recycle()
                SnapState.singleTapRequested = false
                singleTapBusy = false
                return@takeVisualSnapshot
            }
            if (bitmap == null) {
                singleTapBusy = false
                prefs.edit().putString("visual_status", "Einzeltest: Screenshot fehlgeschlagen; warte auf nächstes Event.").apply()
                return@takeVisualSnapshot
            }
            val candidates = findSnapMarkers(bitmap)
            val w = bitmap.width
            val h = bitmap.height
            bitmap.recycle()
            prefs.edit()
                .putBoolean("screenshot_ok", true)
                .putInt("visual_candidates", candidates.size)
                .apply()
            // Conservative target area: colored icons in the left part of chat rows.
            // This is not yet a verified unopened-Snap detector.
            val target = candidates.firstOrNull {
                it.first in (w * 0.12f)..(w * 0.28f) &&
                it.second in (h * 0.15f)..(h * 0.80f)
            }
            if (target == null) {
                singleTapBusy = false
                prefs.edit().putString("visual_status", "Einzeltest: Kein Kandidat im linken Chat-Icon-Bereich; kein Tipp.").apply()
                return@takeVisualSnapshot
            }
            // Disarm before dispatch to prevent a second tap on subsequent events.
            SnapState.singleTapRequested = false
            prefs.edit().putString("visual_status",
                "Einzeltest: Sende genau einen Tipp bei (${target.first.toInt()},${target.second.toInt()}).").apply()
            tap(target.first, target.second) {
                singleTapBusy = false
                prefs.edit()
                    .putInt("visual_opened", 1)
                    .putString("visual_status", "Einzeltest: Tipp-Geste abgeschlossen. Ob ein Snap geöffnet wurde, bitte visuell prüfen.")
                    .putString("diagnostic_report", "Einzeltest: Genau eine Tipp-Geste bei (${target.first.toInt()},${target.second.toInt()}) abgeschlossen. Keine automatische Rücknavigation. Bitte prüfen, was geöffnet wurde.")
                    .apply()
            }
        }
    }

    private fun runVisualDiagnostic() {
        if (System.currentTimeMillis() - lastScreenshotAt < 1200L) return
        if (visualBusy) return
        visualBusy = true
        takeVisualSnapshot { bitmap ->
            visualBusy = false
            if (bitmap == null) {
                prefs.edit()
                    .putBoolean("screenshot_ok", false)
                    .putString("diagnostic_report", "V3 Visual-Diagnose: Screenshot konnte nicht aufgenommen werden.")
                    .apply()
                return@takeVisualSnapshot
            }

            val candidates = findSnapMarkers(bitmap)
            val width = bitmap.width
            val height = bitmap.height
            bitmap.recycle()

            val positions = candidates.take(12).joinToString(separator = ", ") { "(${it.first.toInt()},${it.second.toInt()})" }
            val report = buildString {
                appendLine("V3 Visual-Diagnose")
                appendLine("Screenshot: OK (${width}x${height})")
                appendLine("Gefundene rote/lila Snap-Marker: ${candidates.size}")
                if (candidates.isEmpty()) append("Keine passenden Marker erkannt.")
                else append("Positionen: $positions")
            }

            prefs.edit()
                .putBoolean("screenshot_ok", true)
                .putInt("screenshot_width", width)
                .putInt("screenshot_height", height)
                .putInt("visual_candidates", candidates.size)
                .putString("diagnostic_report", report)
                .apply()

            if (System.currentTimeMillis() >= SnapState.diagnosticUntil) {
                SnapState.diagnosticRequested = false
                prefs.edit().putBoolean("diagnostic_running", false).apply()
            }
        }
    }

    private fun runManualBatch() {
        val runId = SnapState.batchRunId
        if (activeBatchId != runId) {
            activeBatchId = runId
            batchSeenY.clear()
            batchPhase = 0
            viewerTaps = 0
            unchangedViewerFrames = 0
            batchBusy = false
        }
        if (!isBatchActive(runId)) {
            finishBatch("Zeit- oder Versuchslimit erreicht.")
            return
        }
        if (batchBusy || visualBusy || System.currentTimeMillis() - lastScreenshotAt < 1200L) return
        batchBusy = true
        visualBusy = true
        takeVisualSnapshot { bitmap ->
            visualBusy = false
            if (!isBatchActive(runId)) {
                bitmap?.recycle()
                batchBusy = false
                return@takeVisualSnapshot
            }
            if (bitmap == null) {
                batchBusy = false
                prefs.edit().putString("visual_status", "Screenshot fehlgeschlagen; warte auf Snapchat-Event.").apply()
                return@takeVisualSnapshot
            }
            val w = bitmap.width
            val h = bitmap.height
            if (batchPhase == 0) {
                bitmap.recycle()
                batchPhase = 1
                prefs.edit().putString("visual_status", "Aktiviere Ungelesen-Filter.").apply()
                tap(w * 0.17f, h * 0.149f) {
                    batchBusy = false
                    handler.postDelayed({ if (isBatchActive(runId)) runManualBatch() }, 1350L)
                }
                return@takeVisualSnapshot
            }
            if (batchPhase == 1) {
                val targets = findFilledSnapIcons(bitmap)
                bitmap.recycle()
                prefs.edit().putInt("visual_candidates", targets.size).putBoolean("screenshot_ok", true).apply()
                val target = targets.firstOrNull { candidate ->
                    batchSeenY.none { kotlin.math.abs(it - candidate.second) < 48f }
                }
                if (target == null) {
                    finishBatch("Keine weiteren ausgefüllten roten/lila Snap-Symbole im sichtbaren Bereich.")
                    batchBusy = false
                    return@takeVisualSnapshot
                }
                batchSeenY.add(target.second)
                batchPhase = 2
                viewerTaps = 0
                unchangedViewerFrames = 0
                lastViewerSignature = 0L
                prefs.edit().putString("visual_status", "Öffne Snap bei (${target.first.toInt()},${target.second.toInt()}).").apply()
                tap(target.first, target.second) {
                    if (!isBatchActive(runId)) { batchBusy = false; return@tap }
                    SnapState.openedInBatch++
                    prefs.edit().putInt("visual_opened", SnapState.openedInBatch).apply()
                    batchBusy = false
                    handler.postDelayed({ if (isBatchActive(runId)) runManualBatch() }, 1400L)
                }
                return@takeVisualSnapshot
            }
            // Never blindly tap when the chat list is visible.
            val chatListVisible = looksLikeChatList(bitmap)
            val signature = visualSignature(bitmap)
            bitmap.recycle()
            if (chatListVisible) {
                batchPhase = 1
                batchBusy = false
                handler.postDelayed({ if (isBatchActive(runId)) runManualBatch() }, 1300L)
                return@takeVisualSnapshot
            }
            if (lastViewerSignature == signature) unchangedViewerFrames++ else unchangedViewerFrames = 0
            lastViewerSignature = signature
            if (viewerTaps >= 8 || unchangedViewerFrames >= 2) {
                prefs.edit().putString("visual_status", "Snap-Serie: Zurück zur Chatliste (max. 8 Weiter-Tipps).").apply()
                performGlobalAction(GLOBAL_ACTION_BACK)
                batchPhase = 1
                batchBusy = false
                handler.postDelayed({ if (isBatchActive(runId)) runManualBatch() }, 1600L)
                return@takeVisualSnapshot
            }
            viewerTaps++
            prefs.edit().putString("visual_status", "Snap-Serie: Weiter-Tipp $viewerTaps/8.").apply()
            tap(w * 0.82f, h * 0.53f) {
                batchBusy = false
                handler.postDelayed({ if (isBatchActive(runId)) runManualBatch() }, 1400L)
            }
        }
    }

    private fun finishBatch(reason: String) {
        SnapState.batchMode = false
        prefs.edit()
            .putString("visual_status", "Durchlauf beendet: $reason")
            .putString("diagnostic_report", "V3.4: $reason\\nChat-Öffnungsversuche: ${SnapState.openedInBatch}. Weitere Snaps innerhalb eines Chats werden nicht separat gezählt.")
            .apply()
    }

    // Filled red/purple snap icon near left of row; outline icons and blue chats excluded.
    private fun findFilledSnapIcons(bitmap: Bitmap): List<Pair<Float, Float>> {
        val w = bitmap.width
        val h = bitmap.height
        val step = max(2, w / 400)
        val result = mutableListOf<Pair<Float, Float>>()
        var y = (h * 0.19f).toInt()
        while (y < (h * 0.83f).toInt()) {
            var bestX = -1
            var bestCount = 0
            var x = (w * 0.14f).toInt()
            while (x < (w * 0.26f).toInt()) {
                var hits = 0
                for (dy in -6..6 step 3) for (dx in -6..6 step 3) {
                    val px = (x + dx).coerceIn(0, w - 1)
                    val py = (y + dy).coerceIn(0, h - 1)
                    if (isSnapColor(bitmap.getPixel(px, py))) hits++
                }
                if (hits > bestCount) { bestCount = hits; bestX = x }
                x += step
            }
            if (bestCount >= 18 && result.none { kotlin.math.abs(it.second - y) < h * 0.052f }) {
                result.add(bestX.toFloat() to y.toFloat())
            }
            y += step * 3
        }
        return result
    }

    // Horizontal row separators in the Snapchat chat list (not present in snap viewer).
    private fun looksLikeChatList(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        var separators = 0
        var y = (h * 0.17f).toInt()
        while (y < (h * 0.85f).toInt()) {
            var uniform = 0
            for (i in 1..9) {
                val pixel = bitmap.getPixel((w * i / 10), y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                if (kotlin.math.abs(r - g) < 12 && kotlin.math.abs(g - b) < 12 && r in 25..75) uniform++
            }
            if (uniform >= 8) separators++
            y += max(8, h / 130)
        }
        return separators >= 5
    }

    private fun visualSignature(bitmap: Bitmap): Long {
        var sum = 0L
        for (yi in 1..9) for (xi in 1..7) {
            val p = bitmap.getPixel(bitmap.width * xi / 8, bitmap.height * yi / 10)
            sum = sum * 31L + (Color.red(p) / 32) * 64 + (Color.green(p) / 32) * 8 + Color.blue(p) / 32
        }
        return sum
    }

    private fun isBatchActive(runId: Int): Boolean {
        return SnapState.batchMode && !SnapState.batchStopRequested &&
            SnapState.batchRunId == runId &&
            System.currentTimeMillis() <= SnapState.batchUntil &&
            SnapState.openedInBatch < 10
    }

    private fun takeVisualSnapshot(done: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            prefs.edit().putString("visual_status", "Screenshot benötigt Android 11 oder neuer.").apply()
            done(null)
            return
        }

        try {
            lastScreenshotAt = System.currentTimeMillis()
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                        buffer.close()
                        done(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        prefs.edit()
                            .putInt("screenshot_error", errorCode)
                            .putString("visual_status", "Screenshot-Fehlercode: $errorCode")
                            .apply()
                        done(null)
                    }
                }
            )
        } catch (t: Throwable) {
            prefs.edit()
                .putString("visual_status", "Screenshot-Ausnahme: ${t.javaClass.simpleName}")
                .apply()
            done(null)
        }
    }

    private fun findSnapMarkers(bitmap: Bitmap): List<Pair<Float, Float>> {
        val w = bitmap.width
        val h = bitmap.height

        val xStart = (w * 0.10f).toInt()
        val xEnd = (w * 0.58f).toInt()
        val yStart = (h * 0.12f).toInt()
        val yEnd = (h * 0.88f).toInt()
        val step = max(2, min(w, h) / 500)

        val rowHits = mutableListOf<Pair<Int, MutableList<Int>>>()

        var y = yStart
        while (y < yEnd) {
            val xs = mutableListOf<Int>()
            var x = xStart
            while (x < xEnd) {
                if (isSnapColor(bitmap.getPixel(x, y))) xs += x
                x += step
            }
            if (xs.size >= 4) rowHits += y to xs
            y += step
        }

        if (rowHits.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<Pair<Int, MutableList<Int>>>>()
        for (row in rowHits) {
            if (groups.isEmpty() || row.first - groups.last().last().first <= step * 3) {
                if (groups.isEmpty()) groups.add(mutableListOf())
                groups.last().add(row)
            } else {
                groups.add(mutableListOf(row))
            }
        }

        val candidates = mutableListOf<Pair<Float, Float>>()
        for (group in groups) {
            val ys = group.map { it.first }
            val allXs = group.flatMap { it.second }
            val height = ys.maxOrNull()!! - ys.minOrNull()!! + step
            val width = allXs.maxOrNull()!! - allXs.minOrNull()!! + step
            val pixels = allXs.size

            if (height in 8..140 && width in 5..220 && pixels >= 12) {
                val cx = allXs.average().toFloat()
                val cy = ys.average().toFloat()
                candidates += cx to cy
            }
        }

        return candidates
            .sortedBy { it.second }
            .fold(mutableListOf()) { acc, p ->
                if (acc.none { kotlin.math.abs(it.second - p.second) < 35f }) acc += p
                acc
            }
    }

    private fun isSnapColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val saturation = if (maxC == 0) 0f else (maxC - minC).toFloat() / maxC.toFloat()

        val red = r > 180 && g < 120 && b < 140 && saturation > 0.45f
        val purple = r > 100 && b > 120 && b > g * 1.15f && saturation > 0.35f

        return red || purple
    }

    private fun tap(x: Float, y: Float, done: () -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()

        dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    done()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    prefs.edit().putString("visual_status", "Tap-Geste wurde abgebrochen.").apply()
                    SnapState.batchMode = false
                }
            },
            handler
        )
    }

    private fun swipeToChats(done: () -> Unit) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.55f)
            lineTo(w * 0.82f, h * 0.55f)
        }
        dispatchPath(path, 320, done)
    }

    private fun scrollChatList(done: () -> Unit) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val path = Path().apply {
            moveTo(w * 0.55f, h * 0.76f)
            lineTo(w * 0.55f, h * 0.32f)
        }
        dispatchPath(path, 360, done)
    }

    private fun dispatchPath(path: Path, duration: Long, done: () -> Unit) {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = done()
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    prefs.edit().putString("visual_status", "Wischgeste wurde abgebrochen.").apply()
                    SnapState.batchMode = false
                }
            },
            handler
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(heartbeat)
        prefs.edit().putBoolean("service_connected", false).apply()
        super.onDestroy()
    }
}
