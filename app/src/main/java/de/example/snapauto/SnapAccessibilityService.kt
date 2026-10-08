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

    private val chatKeywords = listOf(
        "chat",
        "chats"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != "com.snapchat.android") return

        if (SnapState.batchMode && !SnapState.batchStopRequested) {
            scheduleBatchStep()
            return
        }

        if (System.currentTimeMillis() > SnapState.pendingUntil) return
        if (System.currentTimeMillis() - lastAttempt < 700) return

        lastAttempt = System.currentTimeMillis()
        handler.postDelayed({ tryOpenPendingSnap() }, 500)
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
