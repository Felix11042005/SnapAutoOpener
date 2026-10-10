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
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
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
    private var filterAttempts = 0
    private var filterWasClicked = false
    private var filterScreenshotBefore = 0L
    private var scrollCount = 0
    private var viewerStartedAt = 0L
    private var viewerAdvanceTotal = 0
    private var viewerBackAttempts = 0
    private var viewerBackPending = false
    private var chatReturnChecks = 0
    private var filterVerifiedByChange = false
    private var filterWaitStartedAt = 0L
    private var filterStableFrames = 0
    private var lastFilterFrame = 0L
    private val trace = mutableListOf<String>()

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
            filterAttempts = 0
            filterWasClicked = false
            filterScreenshotBefore = 0L
            filterVerifiedByChange = false
            filterWaitStartedAt = System.currentTimeMillis()
            filterStableFrames = 0
            lastFilterFrame = 0L
            scrollCount = 0
            trace.clear()
            viewerTaps = 0
            viewerAdvanceTotal = 0
            viewerBackAttempts = 0
            viewerBackPending = false
            chatReturnChecks = 0
            unchangedViewerFrames = 0
            batchBusy = false
        }
        if (!isBatchActive(runId)) {
            finishBatch("Zeit- oder Versuchslimit erreicht.")
            return
        }
        if (batchBusy || visualBusy) return
        if (System.currentTimeMillis() - lastScreenshotAt < 1200L) {
            scheduleNext(runId, 1250L)
            return
        }
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
            prefs.edit().putBoolean("screenshot_ok", true).apply()
            if (batchPhase == 0) {
                val unread = findUnreadFilter(rootInActiveWindow, w, h)
                val bounds = Rect()
                unread?.getBoundsInScreen(bounds)
                val safeNode = unread != null && (unread.text?.toString()?.contains("Ungelesen", ignoreCase = true) == true ||
                     unread.contentDescription?.toString()?.contains("Ungelesen", ignoreCase = true) == true) && !bounds.isEmpty &&
                    bounds.centerX() in (w * 0.03f).toInt()..(w * 0.30f).toInt() &&
                    bounds.centerY() in (h * 0.10f).toInt()..(h * 0.19f).toInt()
                val selected = safeNode && (unread?.isSelected == true || unread?.isChecked == true ||
                    unread?.parent?.isSelected == true || unread?.parent?.isChecked == true)
                if (selected) {
                    bitmap.recycle()
                    batchPhase = 1
                    logStep("Ungelesen-Auswahl bestätigt")
                    batchBusy = false
                    scheduleNext(runId, 1200L)
                    return@takeVisualSnapshot
                }
                if (filterWasClicked) {
                    val after = regionSignature(bitmap)
                    val chatEvidence = hasChatHeaderAndFilter(rootInActiveWindow, w, h) ||
                        (detectUnreadChip(bitmap) != null && looksLikeChatList(bitmap))
                    val composer = hasEditableComposer(rootInActiveWindow)
                    bitmap.recycle()
                    if (after != filterScreenshotBefore && chatEvidence && !composer) {
                        filterVerifiedByChange = true
                        batchPhase = 1
                        logStep("Ungelesen: Filterbereich verändert und Chatansicht vorhanden; fahre vorsichtig fort")
                        batchBusy = false
                        scheduleNext(runId, 1250L)
                    } else {
                        finishBatch(if (after == filterScreenshotBefore)
                            "Filter-Tipp ohne sichtbare Änderung."
                        else "Filter reagiert, Chatansicht nicht erkennbar.")
                        batchBusy = false
                    }
                    return@takeVisualSnapshot
                }
                val screenshotFilter = if (!safeNode) detectUnreadChip(bitmap) else null
                val chatEvidence = safeNode || screenshotFilter != null || (looksLikeChatList(bitmap) && !hasEditableComposer(rootInActiveWindow)) ||
                    (findNodeWithText(rootInActiveWindow, "My AI") != null &&
                     findNodeWithText(rootInActiveWindow, "Chat") != null && !hasEditableComposer(rootInActiveWindow))
                if (!chatEvidence) {
                    bitmap.recycle()
                    batchBusy = false
                    if (System.currentTimeMillis() - filterWaitStartedAt < 5000L) {
                        logStep("Warte auf Snapchat-Chatliste (max. 5 Sekunden)")
                        scheduleNext(runId, 1350L)
                    } else finishBatch("Chatliste nach 5 Sekunden nicht erkannt. Kein Tipp.")
                    return@takeVisualSnapshot
                }
                filterScreenshotBefore = regionSignature(bitmap)
                if (!safeNode && screenshotFilter == null) {
                    bitmap.recycle()
                    logStep("Ungelesen: weder Accessibility-Knoten noch visuell plausibler Filterchip gefunden; bounds=$bounds")
                    finishBatch("Ungelesen nicht eindeutig erkannt. Kein automatischer Tipp auf My AI oder Chat.")
                    batchBusy = false
                    return@takeVisualSnapshot
                }
                val x = if (safeNode) bounds.centerX().toFloat() else screenshotFilter!!.first
                val y = if (safeNode) bounds.centerY().toFloat() else screenshotFilter!!.second
                filterWasClicked = true
                bitmap.recycle()
                logStep("Filter-Tipp bei (" + x.toInt() + "," + y.toInt() + ")")
                tap(x, y) {
                    batchBusy = false
                    scheduleNext(runId, 1350L)
                }
                return@takeVisualSnapshot
            }
            if (batchPhase == 1) {
                if (!(hasChatHeaderAndFilter(rootInActiveWindow, w, h) ||
                    (filterVerifiedByChange && detectUnreadChip(bitmap) != null && looksLikeChatList(bitmap))) ||
                    hasEditableComposer(rootInActiveWindow)) {
                    bitmap.recycle()
                    finishBatch("Chatansicht vor Snap-Erkennung nicht sicher sichtbar.")
                    batchBusy = false
                    return@takeVisualSnapshot
                }
                val targets = findFilledSnapIcons(bitmap)
                bitmap.recycle()
                prefs.edit().putInt("visual_candidates", targets.size).putBoolean("screenshot_ok", true).apply()
                val target = targets.firstOrNull { candidate ->
                    batchSeenY.none { kotlin.math.abs(it - candidate.second) < 48f }
                }
                if (target == null) {
                    if (scrollCount++ < 2) {
                        batchSeenY.clear()
                        logStep("Chatliste scrollen")
                        scrollChatList { batchBusy = false; scheduleNext(runId, 1250L) }
                    } else {
                        finishBatch("Keine weiteren gefüllten Snap-Symbole.")
                        batchBusy = false
                    }
                    return@takeVisualSnapshot
                }
                batchSeenY.add(target.second)
                batchPhase = 2
                viewerBackPending = false
                viewerBackAttempts = 0
                chatReturnChecks = 0
                viewerStartedAt = System.currentTimeMillis()
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
            // A video can have static dark areas that resemble chat separators.
            // Only treat it as the chat list if the chat header AND filter are in
            // their expected upper-screen bounds.
            val root = rootInActiveWindow
            val chatListVisible = hasChatHeaderAndFilter(root, w, h)
            val adVisible = hasAdvertisingMarker(root)
            val signature = visualSignature(bitmap)
            bitmap.recycle()
            if (adVisible) {
                finishBatch("Werbung erkannt: Viewer gestoppt.")
                batchBusy = false
                return@takeVisualSnapshot
            }
            if (chatListVisible) {
                logStep("Chatliste nach Viewer bestätigt; Weiter-Tipps gesamt: $viewerAdvanceTotal")
                batchPhase = 1
                batchBusy = false
                scheduleNext(runId, 1300L)
                return@takeVisualSnapshot
            }
            // A conversation screen can resemble a Snap viewer. Never advance there.
            // An editable chat composer is strong evidence that this is NOT a Snap viewer.
            if (hasEditableComposer(root) && !hasSnapViewerProgress(root)) {
                finishBatch("Chat-Unterhaltung statt Snap-Viewer erkannt; keine Weiter-Tipps ausgeführt.")
                batchBusy = false
                return@takeVisualSnapshot
            }
            if (viewerBackPending) {
                chatReturnChecks++
                if (chatReturnChecks >= 3) {
                    finishBatch("Rücknavigation nicht bestätigt; keine weiteren Gesten.")
                    batchBusy = false
                } else {
                    batchBusy = false
                    scheduleNext(runId, 1350L)
                }
                return@takeVisualSnapshot
            }
            if (lastViewerSignature == signature) unchangedViewerFrames++ else unchangedViewerFrames = 0
            lastViewerSignature = signature
            if (viewerTaps >= 8 || System.currentTimeMillis() - viewerStartedAt > 18000L) {
                viewerBackPending = true
                viewerBackAttempts++
                logStep("Viewer-Limit erreicht; BACK $viewerBackAttempts, prüfe Rückkehr")
                performGlobalAction(GLOBAL_ACTION_BACK)
                batchBusy = false
                scheduleNext(runId, 1550L)
                return@takeVisualSnapshot
            }
            viewerTaps++
            viewerAdvanceTotal++
            logStep("Viewer-Weiter-Tipp $viewerTaps/8 (gesamt $viewerAdvanceTotal)")
            // Advance from the upper-right media area, well above the chat/reply controls.
            tap(w * 0.86f, h * 0.27f) {
                batchBusy = false
                scheduleNext(runId, 1350L)
            }
        }
    }

    private fun hasSnapViewerProgress(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var inspected = 0
        while (!queue.isEmpty() && inspected++ < 350) {
            val node = queue.removeFirst()
            val text = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .joinToString(" ").lowercase()
            if (text.contains("antworten") || text.contains("reply to snap")) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    private fun hasEditableComposer(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var checked = 0
        while (!queue.isEmpty() && checked++ < 400) {
            val node = queue.removeFirst()
            if (node.isEditable || node.className?.toString()?.contains("EditText") == true) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    private fun hasChatHeaderAndFilter(root: AccessibilityNodeInfo?, w: Int, h: Int): Boolean {
        val chat = findNodeWithText(root, "Chat") ?: return false
        val unread = findUnreadFilter(root, w, h) ?: return false
        val chatBounds = Rect()
        val filterBounds = Rect()
        chat.getBoundsInScreen(chatBounds)
        unread.getBoundsInScreen(filterBounds)
        return !chatBounds.isEmpty && !filterBounds.isEmpty &&
            chatBounds.centerY() in (h * 0.045f).toInt()..(h * 0.13f).toInt() &&
            filterBounds.centerY() in (h * 0.10f).toInt()..(h * 0.19f).toInt() &&
            filterBounds.centerX() in (w * 0.03f).toInt()..(w * 0.30f).toInt()
    }

    private fun findUnreadFilter(root: AccessibilityNodeInfo?, w: Int, h: Int): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var best: AccessibilityNodeInfo? = null
        var bestScore = -1
        while (!queue.isEmpty() && visited++ < 800) {
            val node = queue.removeFirst()
            val label = (node.text?.toString().orEmpty() + " " +
                node.contentDescription?.toString().orEmpty()).trim()
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (label.contains("Ungelesen", ignoreCase = true) && !bounds.isEmpty &&
                bounds.centerX() in (w * 0.03f).toInt()..(w * 0.30f).toInt() &&
                bounds.centerY() in (h * 0.10f).toInt()..(h * 0.19f).toInt()) {
                val score = (if (label.equals("Ungelesen", true)) 10 else 0) +
                    (if (node.isClickable) 3 else 0) +
                    (if (node.isVisibleToUser) 2 else 0)
                if (score > bestScore) { best = node; bestScore = score }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return best
    }

    private fun findNodeWithText(root: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (!queue.isEmpty() && visited++ < 600) {
            val node = queue.removeFirst()
            val label = node.text?.toString().orEmpty() + " " + node.contentDescription?.toString().orEmpty()
            if (label.contains(target, ignoreCase = true)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(5) {
            val candidate = current ?: return null
            if (candidate.isClickable) return candidate
            current = current?.parent
        }
        return null
    }

    private fun hasAdvertisingMarker(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (!queue.isEmpty() && visited++ < 350) {
            val node = queue.removeFirst()
            val text = (node.text?.toString().orEmpty() + " " + node.contentDescription?.toString().orEmpty()).trim()
            if (text.equals("Anzeige", true) || text.equals("Gesponsert", true) || text.equals("Sponsored", true) || text.equals("Ad", true)) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    private fun scheduleNext(id: Int, delay: Long) {
        handler.postDelayed({ if (isBatchActive(id)) runManualBatch() }, delay)
    }

    // Screenshot fallback: inspect only the known upper-left filter strip.
    // A pill-like region must contain both bright glyphs and dark background.
    // This does not use the chat-row area and never targets My AI.
    private fun detectUnreadChip(bitmap: Bitmap): Pair<Float, Float>? {
        val w = bitmap.width
        val h = bitmap.height
        val left = (w * 0.07f).toInt()
        val right = (w * 0.29f).toInt()
        val top = (h * 0.145f).toInt()
        val bottom = (h * 0.195f).toInt()
        var bright = 0
        var dark = 0
        var samples = 0
        for (y in top until bottom step 4) for (x in left until right step 4) {
            val p = bitmap.getPixel(x, y)
            val v = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
            if (v > 170) bright++
            if (v < 105) dark++
            samples++
        }
        if (samples == 0 || bright < samples * 0.025f || dark < samples * 0.25f) return null
        return w * 0.18f to h * 0.178f
    }

    private fun regionSignature(bitmap: Bitmap): Long {
        var signature = 1L
        for (i in 0..8) for (j in 0..6) {
            val x = ((0.04f + i * 0.028f) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val y = ((0.12f + j * 0.009f) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            signature = signature * 31 + (bitmap.getPixel(x, y) and 0x00f0f0f0)
        }
        return signature
    }

    private fun logStep(message: String) {
        trace.add(message)
        if (trace.size > 20) trace.removeAt(0)
        prefs.edit().putString("visual_status", message)
            .putString("diagnostic_report", "V4.6 Ablauf:\n" + trace.joinToString("\n")).apply()
    }

    private fun finishBatch(reason: String) {
        SnapState.batchMode = false
        prefs.edit()
            .putString("visual_status", "Durchlauf beendet: $reason")
            .putString("diagnostic_report", "V4.6: $reason\nChat-Öffnungsversuche: ${SnapState.openedInBatch}; Viewer-Weiter-Tipps: $viewerAdvanceTotal.\n" + trace.joinToString("\n"))
            .apply()
    }

    // Filled red/purple snap icon near left of row; outline icons and blue chats excluded.
    private fun findFilledSnapIcons(bitmap: Bitmap): List<Pair<Float, Float>> {
        val w = bitmap.width
        val h = bitmap.height
        val results = mutableListOf<Pair<Float, Float>>()
        // Examine a compact icon footprint, not an arbitrary colored patch in a chat row.
        val radius = max(5, (w * 0.010f).toInt())
        val stride = max(4, radius)
        var y = (h * 0.205f).toInt()
        while (y < (h * 0.81f).toInt()) {
            var x = (w * 0.14f).toInt()
            while (x < (w * 0.26f).toInt()) {
                var redPurple = 0
                var blue = 0
                var centerFilled = 0
                var total = 0
                for (dy in -radius..radius step 3) for (dx in -radius..radius step 3) {
                    val pixel = bitmap.getPixel((x + dx).coerceIn(0, w - 1), (y + dy).coerceIn(0, h - 1))
                    if (isSnapColor(pixel)) {
                        redPurple++
                        if (kotlin.math.abs(dx) <= radius / 2 && kotlin.math.abs(dy) <= radius / 2) centerFilled++
                    }
                    if (Color.blue(pixel) > Color.red(pixel) * 1.3f &&
                        Color.blue(pixel) > Color.green(pixel) * 1.2f &&
                        Color.blue(pixel) > 120) blue++
                    total++
                }
                // Filled center excludes hollow red/purple icons; blue veto excludes chats.
                if (redPurple >= total * 0.72f && centerFilled >= 8 && blue == 0 &&
                    results.none { kotlin.math.abs(it.second - y) < h * 0.052f }) {
                    results.add(x.toFloat() to y.toFloat())
                }
                x += stride
            }
            y += stride
        }
        return results
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
