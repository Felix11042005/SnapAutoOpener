package de.example.snapauto

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SnapAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastAttempt = 0L
    private var batchBusy = false
    private var emptyPasses = 0
    private val snapLabels = listOf("New Snap", "Neuer Snap", "Tap to view", "Zum Ansehen tippen", "Received", "Empfangen")

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
        if (sender.isNotBlank() && clickText(root, sender)) {
            handler.postDelayed({ if (clickSnapLikeNode(rootInActiveWindow)) finishPending() }, 900)
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
        if (clickSnapLikeNode(root)) {
            emptyPasses = 0
            SnapState.openedInBatch++
            handler.postDelayed({
                if (!SnapState.batchMode || SnapState.batchStopRequested) return@postDelayed
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({ scheduleBatchStep(250) }, 900)
            }, 2200)
            return
        }

        val scrollable = findScrollable(root)
        if (scrollable != null && emptyPasses < 5 && scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            emptyPasses++
            scheduleBatchStep(900)
        } else {
            SnapState.batchMode = false
            emptyPasses = 0
        }
    }

    private fun clickSnapLikeNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        for (label in snapLabels) if (clickText(root, label)) return true
        return false
    }

    private fun clickText(root: AccessibilityNodeInfo, text: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            val nodeText = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            if (!nodeText.contains(text, true) && !desc.contains(text, true)) continue
            var target: AccessibilityNodeInfo? = node
            repeat(6) {
                if (target?.isClickable == true && target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
                target = target?.parent
            }
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
    }

    override fun onInterrupt() = Unit
}