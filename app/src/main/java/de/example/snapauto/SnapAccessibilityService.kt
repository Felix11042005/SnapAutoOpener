package de.example.snapauto

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SnapAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastAttempt = 0L
    private var batchBusy = false
    private var emptyPasses = 0
    private var chatTabAttempted = false

    private val prefs by lazy { getSharedPreferences("snapauto_diag", MODE_PRIVATE) }

    private val heartbeat = object : Runnable {
        override fun run() {
            prefs.edit()
                .putBoolean("service_connected", true)
                .putLong("heartbeat_at", System.currentTimeMillis())
                .apply()

            if (SnapState.diagnosticRequested) {
                captureDiagnosticTree("heartbeat")
                if (System.currentTimeMillis() >= SnapState.diagnosticUntil) {
                    SnapState.diagnosticRequested = false
                    prefs.edit().putBoolean("diagnostic_running", false).apply()
                }
            }

            handler.postDelayed(this, 500)
        }
    }

    private val snapKeywords = listOf(
        "new snap", "neuer snap", "tap to view", "zum ansehen tippen",
        "received", "empfangen", "snap received", "snap erhalten",
        "photo", "foto", "video"
    )

    private val chatKeywords = listOf("chat", "chats")

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

        if (SnapState.diagnosticRequested) {
            captureDiagnosticTree("event")
        }

        if (SnapState.batchMode && !SnapState.batchStopRequested) {
            scheduleBatchStep()
            return
        }

        if (now > SnapState.pendingUntil) return
        if (now - lastAttempt < 700) return

        lastAttempt = now
        handler.postDelayed({ tryOpenPendingSnap() }, 500)
    }

    private fun captureDiagnosticTree(trigger: String) {
        val now = System.currentTimeMillis()
        val root = rootInActiveWindow
        val activePackage = root?.packageName?.toString().orEmpty()

        prefs.edit()
            .putLong("last_capture_at", now)
            .putString("root_package", activePackage)
            .putBoolean("root_available", root != null)
            .apply()

        if (root == null) {
            val report = buildString {
                appendLine("SnapAuto Diagnose")
                appendLine("Accessibility-Service: VERBUNDEN")
                appendLine("Auslöser: $trigger")
                appendLine("rootInActiveWindow: NULL")
                appendLine("Letztes Event-Paket: ${prefs.getString("last_event_package", "-")}")
                appendLine("Events gesamt: ${prefs.getInt("event_count", 0)}")
                appendLine("Snapchat-Events: ${prefs.getInt("snap_event_count", 0)}")
                appendLine()
                append("Android stellt dem Service aktuell keinen lesbaren UI-Baum bereit.")
            }
            saveReport(report, 0)
            return
        }

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)

        val lines = mutableListOf<String>()
        var total = 0
        var withText = 0
        var clickable = 0
        var scrollable = 0

        while (queue.isNotEmpty() && total < 300) {
            val (node, depth) = queue.removeFirst()
            total++

            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            val viewId = node.viewIdResourceName.orEmpty()
            val clazz = node.className?.toString().orEmpty()

            if (text.isNotBlank() || desc.isNotBlank()) withText++
            if (node.isClickable) clickable++
            if (node.isScrollable) scrollable++

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isScrollable) {
                lines += buildString {
                    append("#$total d=$depth class=${clazz.takeLast(40)}")
                    append(" click=${node.isClickable} scroll=${node.isScrollable}")
                    append(" bounds=${bounds.flattenToString()}")
                    if (text.isNotBlank()) append(" text=\"${text.take(100).replace("\n", " ")}\"")
                    if (desc.isNotBlank()) append(" desc=\"${desc.take(100).replace("\n", " ")}\"")
                    if (viewId.isNotBlank()) append(" id=${viewId.takeLast(80)}")
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it to (depth + 1)) }
            }
        }

        val score = withText * 4 + clickable * 2 + scrollable * 3 + total
        if (score < SnapState.diagnosticBestScore) return
        SnapState.diagnosticBestScore = score

        val report = buildString {
            appendLine("SnapAuto Diagnose")
            appendLine("Accessibility-Service: VERBUNDEN")
            appendLine("Auslöser: $trigger")
            appendLine("Root-Paket: ${activePackage.ifBlank { "-" }}")
            appendLine("Events gesamt: ${prefs.getInt("event_count", 0)}")
            appendLine("Snapchat-Events: ${prefs.getInt("snap_event_count", 0)}")
            appendLine("Gesamtknoten: $total")
            appendLine("Text/Description: $withText")
            appendLine("Klickbar: $clickable")
            appendLine("Scrollbars: $scrollable")
            appendLine("Snapshot-Score: $score")
            appendLine()
            if (lines.isEmpty()) appendLine("Keine verwertbaren UI-Knoten gefunden.")
            else append(lines.take(140).joinToString("\n"))
        }
        saveReport(report, score)
    }

    private fun saveReport(report: String, score: Int) {
        SnapState.diagnosticReport = report
        prefs.edit()
            .putString("diagnostic_report", report)
            .putInt("diagnostic_score", score)
            .putLong("diagnostic_report_at", System.currentTimeMillis())
            .apply()
    }

    private fun tryOpenPendingSnap() {
        val root = rootInActiveWindow ?: return
        val sender = SnapState.pendingSender

        if (!chatTabAttempted) {
            chatTabAttempted = true
            if (clickNodeMatching(root, chatKeywords)) {
                handler.postDelayed({ tryOpenPendingSnap() }, 900)
                return
            }
        }

        if (sender.isNotBlank() && clickNodeMatching(root, listOf(sender.lowercase()))) {
            handler.postDelayed({
                if (clickSnapLikeNode(rootInActiveWindow)) finishPending()
            }, 900)
            return
        }

        if (clickSnapLikeNode(root)) finishPending()
    }

    private fun scheduleBatchStep(delay: Long = 650) {
        if (batchBusy) return
        batchBusy = true
        handler.postDelayed({
            batchBusy = false
            if (!SnapState.batchMode || SnapState.batchStopRequested) return@postDelayed
            batchStep()
        }, delay)
    }

    private fun batchStep() {
        val root = rootInActiveWindow ?: return scheduleBatchStep(900)

        if (!chatTabAttempted) {
            chatTabAttempted = true
            if (clickNodeMatching(root, chatKeywords)) {
                scheduleBatchStep(1000)
                return
            }
        }

        if (clickSnapLikeNode(root)) {
            emptyPasses = 0
            SnapState.openedInBatch++
            handler.postDelayed({
                if (!SnapState.batchMode || SnapState.batchStopRequested) return@postDelayed
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({
                    chatTabAttempted = true
                    scheduleBatchStep(350)
                }, 1000)
            }, 2400)
            return
        }

        val scrollable = findScrollable(root)
        if (scrollable != null && emptyPasses < 7 &&
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            emptyPasses++
            scheduleBatchStep(900)
        } else {
            SnapState.batchMode = false
            emptyPasses = 0
            chatTabAttempted = false
        }
    }

    private fun clickSnapLikeNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return clickNodeOrParent(findBestMatchingNode(root, snapKeywords))
    }

    private fun clickNodeMatching(root: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (root == null) return false
        return clickNodeOrParent(findBestMatchingNode(root, keywords))
    }

    private fun findBestMatchingNode(root: AccessibilityNodeInfo, keywords: List<String>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString().orEmpty().lowercase()
            val desc = node.contentDescription?.toString().orEmpty().lowercase()
            val combined = "$text $desc"
            var score = 0
            for (keyword in keywords) if (combined.contains(keyword)) score += if (combined == keyword) 5 else 3
            if (node.isClickable) score += 1
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 40 && bounds.height() > 40) score += 1
            if (score > bestScore) {
                bestScore = score
                best = node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return if (bestScore >= 3) best else null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var target = node
        repeat(8) {
            if (target?.isClickable == true &&
                target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
            target = target?.parent
        }
        return false
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun finishPending() {
        SnapState.pendingUntil = 0
        SnapState.pendingSender = ""
        chatTabAttempted = false
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(heartbeat)
        prefs.edit().putBoolean("service_connected", false).apply()
        super.onDestroy()
    }
}
