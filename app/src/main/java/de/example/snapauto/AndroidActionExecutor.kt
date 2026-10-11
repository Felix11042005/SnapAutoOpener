package de.example.snapauto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.SystemClock

/** Sole owner of interaction APIs. Kept at the user's request, never called by diagnostics. */
class AndroidActionExecutor(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val session: DiagnosticSession,
    private val currentSnapshot: () -> UiSnapshot?,
    private val report: (String) -> Unit
) {
    private var inFlight = false
    fun execute(token: Long, request: ActionRequest, completed: () -> Unit = {}) {
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
