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
    private var diagnosticCaptured = false

    private val snapKeywords = listOf(
        "new snap",
        "neuer snap",
        "tap to view",
        "zum ansehen tippen",
        "received",
        "empfangen",
        "snap received",
        "snap erhalten",
        "photo",
        "foto",
        "video"
    )

    private val chatKeywords = listOf("chat", "chats")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != "com.snapchat.android") return

        if (SnapState.diagnosticRequested) {
            captureDiagnosticTree()

            if (!diagnosticFinalizeScheduled) {
                diagnosticFinalizeScheduled = true
                val remaining = (SnapState.diagnosticUntil - System.currentTimeMillis()).coerceAtLeast(500L)
                handler.postDelayed({
                    SnapState.diagnosticRequested = false
                    diagnosticFinalizeScheduled = false
                }, remaining)
            }
        }

        if (SnapState.batchMode && !SnapState.batchStopRequested) {
            scheduleBatchStep()
            return
        }

        if (System.currentTimeMillis() > SnapState.pendingUntil) return
        if (System.currentTimeMillis() - lastAttempt < 700) return

        lastAttempt = System.currentTimeMillis()
        handler.postDelayed({ tryOpenPendingSnap() }, 500)
    }

    private fun captureDiagnosticTree() {
        val root = rootInActiveWindow

        if (root == null) {
            SnapState.diagnosticReport =
                "Snapchat wurde erkannt, aber rootInActiveWindow ist NULL. " +
                "Android/Snapchat stellt dem Accessibility-Service aktuell keinen lesbaren UI-Baum bereit."
            return
        }

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)

        val lines = mutableListOf<String>()
        var total = 0
        var withText = 0
        var clickable = 0
        var scrollable = 0

        while (queue.isNotEmpty() && total < 250) {
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

            if (
                text.isNotBlank() ||
                desc.isNotBlank() ||
                node.isClickable ||
                node.isScrollable
            ) {
                lines += buildString {
                    append("#")
                    append(total)
                    append(" d=")
                    append(depth)
                    append(" class=")
                    append(clazz.takeLast(40))
                    append(" click=")
                    append(node.isClickable)
                    append(" scroll=")
                    append(node.isScrollable)
                    append(" bounds=")
                    append(bounds.flattenToString())

                    if (text.isNotBlank()) {
                        append(" text=\"")
                        append(text.take(80).replace("\n", " "))
                        append("\"")
                    }

                    if (desc.isNotBlank()) {
                        append(" desc=\"")
                        append(desc.take(80).replace("\n", " "))
                        append("\"")
                    }

                    if (viewId.isNotBlank()) {
                        append(" id=")
                        append(viewId.takeLast(60))
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it to (depth + 1)) }
            }
        }

        val score = withText * 4 + clickable * 2 + scrollable * 3 + total

        if (score >= SnapState.diagnosticBestScore) {
            SnapState.diagnosticBestScore = score
            SnapState.diagnosticReport = buildString {
                appendLine("Snapchat Accessibility-Diagnose (bester Snapshot)")
                appendLine("Gesamtknoten: $total")
                appendLine("Knoten mit Text/Description: $withText")
                appendLine("Klickbare Knoten: $clickable")
                appendLine("Scrollbare Knoten: $scrollable")
                appendLine("Snapshot-Score: $score")
                appendLine()
                if (lines.isEmpty()) {
                    appendLine("Keine verwertbaren UI-Knoten gefunden.")
                } else {
                    append(lines.take(120).joinToString("\n"))
                }
            }
        }
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
        if (
            scrollable != null &&
            emptyPasses < 7 &&
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        ) {
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
        val candidate = findBestMatchingNode(root, snapKeywords)
        return clickNodeOrParent(candidate)
    }

    private fun clickNodeMatching(
        root: AccessibilityNodeInfo?,
        keywords: List<String>
    ): Boolean {
        if (root == null) return false
        val candidate = findBestMatchingNode(root, keywords)
        return clickNodeOrParent(candidate)
    }

    private fun findBestMatchingNode(
        root: AccessibilityNodeInfo,
        keywords: List<String>
    ): AccessibilityNodeInfo? {
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

            for (keyword in keywords) {
                if (combined.contains(keyword)) {
                    score += if (combined == keyword) 5 else 3
                }
            }

            if (node.isClickable) score += 1

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 40 && bounds.height() > 40) score += 1

            if (score > bestScore) {
                bestScore = score
                best = node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return if (bestScore >= 3) best else null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var target = node

        repeat(8) {
            if (
                target?.isClickable == true &&
                target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            ) {
                return true
            }
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
}
