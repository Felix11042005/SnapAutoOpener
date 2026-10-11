package de.example.snapauto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Sole owner of interaction APIs. Kept at the user's request, never called by diagnostics. */
class AndroidActionExecutor(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val session: DiagnosticSession,
    private val currentSnapshot: () -> UiSnapshot?,
    private val report: (String) -> Unit
) {
    private var inFlight = false
    private val opener = SingleSnapOpener(session, SystemClock::elapsedRealtime, currentSnapshot,
        SnapClickPort(::clickExactControl))

    fun openSnapOnce(token: Long, request: ActionRequest): OpenResult = opener.open(token, request)

    @Suppress("DEPRECATION")
    private fun clickExactControl(expected: UiSnapshot, control: UiNode, authorize: () -> Boolean): Boolean? {
        val windows = service.windows
        var held: AccessibilityNodeInfo? = null
        try {
            if (windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) return null
            val window = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive && it.isFocused }
                .singleOrNull() ?: return null
            if (window.id != expected.windowId) return null
            var node = window.root ?: return null
            held = node
            if (node.packageName?.toString() != ScreenRecognizer.TARGET_PACKAGE || control.path.isEmpty()) return null
            for (index in control.path) {
                if (index !in 0 until node.childCount) return null
                val child = node.getChild(index)
                node.recycle()
                held = child
                node = child ?: return null
            }
            if (node.windowId != expected.windowId ||
                SnapshotReader.describe(node, control.index, control.parent, control.path) != control) return null
            if (!authorize()) return null
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } finally {
            held?.recycle()
            windows.forEach { it.recycle() }
        }
    }

    fun execute(token: Long, request: ActionRequest, completed: () -> Unit = {}) {
        if (request.kind == ActionKind.OPEN_SNAP) {
            val result = openSnapOnce(token, request)
            report(result.reason)
            if (result.accepted) completed()
            return
        }
        val rejection = ActionPolicy.rejection(session, token, SystemClock.elapsedRealtime(), request, currentSnapshot())
        if (rejection != null || inFlight) {
            report("ACTION_BLOCKED: ${rejection ?: "ACTION_IN_FLIGHT"}")
            return
        }
        val target = request.target
        if (request.kind == ActionKind.BACK) {
            report("BACK accepted=${service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)}")
            return
        }
        if (target == null || !target.valid) { report("ACTION_BLOCKED: NO_TARGET"); return }
        val path = Path()
        val centerX = (target.left + target.right) / 2f
        val centerY = (target.top + target.bottom) / 2f
        val duration = if (request.kind == ActionKind.SCROLL_LIST) 300L else 80L
        if (request.kind == ActionKind.SCROLL_LIST) {
            path.moveTo(centerX, target.bottom - target.height * 0.2f)
            path.lineTo(centerX, target.top + target.height * 0.2f)
        } else path.moveTo(centerX, centerY)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
        inFlight = true
        val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                inFlight = false
                if (session.accepts(token, SystemClock.elapsedRealtime())) {
                    report("GESTURE_COMPLETED (kein Nachweis einer Snap-Öffnung)")
                    completed()
                }
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                inFlight = false
                if (session.accepts(token, SystemClock.elapsedRealtime())) {
                    session.stop()
                    report("GESTURE_CANCELLED")
                }
            }
        }, handler)
        if (!accepted) { inFlight = false; report("GESTURE_REJECTED") }
    }
}
