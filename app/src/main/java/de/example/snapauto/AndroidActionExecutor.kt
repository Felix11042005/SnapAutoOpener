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
    private var flightSequence = 0L
    private var flightDeadline = 0L
    private val inFlight get() = SystemClock.elapsedRealtime() < flightDeadline
    private fun beginFlight(duration: Long): Long {
        flightDeadline = SystemClock.elapsedRealtime() + duration + 2_000
        return ++flightSequence
    }
    private fun finishFlight(sequence: Long) {
        // Late callbacks from a timed-out gesture must not clear a newer gesture.
        if (sequence == flightSequence) flightDeadline = 0
    }
    private val opener = SingleSnapOpener(session, SystemClock::elapsedRealtime, currentSnapshot,
        SnapClickPort(::clickExactControl))

    fun openSnapOnce(token: Long, request: ActionRequest): OpenResult = opener.open(token, request)

    /** Separate recovery capability: never a viewer click or an unrestricted BACK. */
    fun returnToList(token: Long, expected: UiSnapshot, flow: ReturnAfterOpen): Boolean {
        if (inFlight) return false
        return ReturnToList(session, SystemClock::elapsedRealtime, currentSnapshot, SnapBackPort { authorize ->
            if (!authorize()) false else service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }).execute(token, expected, flow).accepted
    }

    fun navigateList(token: Long, request: ListNavigationRequest, inventory: FullInventory,
        completed: (Boolean) -> Unit): OpenResult {
        if (inFlight) return OpenResult(false, false, "ACTION_IN_FLIGHT")
        val result = ListNavigationOnce(session, SystemClock::elapsedRealtime, currentSnapshot, inventory,
            ListNavigationPort { operation, target, region, authorize ->
                val nativeAction = when (operation.kind) {
                    ListNavigationKind.FILTER -> if (target.canClick) AccessibilityNodeInfo.ACTION_CLICK else null
                    // Native scrolling can replace a whole page and destroy the overlap
                    // required by ScrollMotion. Always use the bounded gesture for lists.
                    ListNavigationKind.UP, ListNavigationKind.DOWN -> null
                }
                if (nativeAction != null && target.path.isNotEmpty()) {
                    report("LIST_ROUTE kind=${operation.kind}; native=$nativeAction")
                    val accepted = performExactNode(operation.page.snapshot, target, nativeAction, authorize) == true
                    handler.postDelayed({ if (session.accepts(token, SystemClock.elapsedRealtime())) completed(true) }, 400)
                    accepted
                } else {
                    val filter = operation.kind == ListNavigationKind.FILTER
                    val plan = if (filter) null else ListScroll.plan(region, operation.kind)
                    if (!filter && plan == null) return@ListNavigationPort null
                    val x = plan?.x ?: (region.left + region.right) / 2f
                    val start = if (filter) (region.top + region.bottom) / 2f
                        else plan!!.startY
                    val end = if (filter) start else plan!!.endY
                    val excluded = ScreenRecognizer.inspectFiltered(operation.page.snapshot).excludedRows
                    if (!filter && excluded.any { row ->
                            row.contains(Box(x.toInt(), start.toInt(), x.toInt() + 1, start.toInt() + 1)) ||
                                row.contains(Box(x.toInt(), end.toInt(), x.toInt() + 1, end.toInt() + 1)) }) null
                    else {
                        val duration = if (filter) 80L else plan!!.duration
                        report("LIST_ROUTE kind=${operation.kind}; gesture=($x,$start)->($x,$end); ms=$duration")
                        val path = Path().apply { moveTo(x, start); if (!filter) lineTo(x, end) }
                        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
                        if (!authorize()) false else {
                            val sequence = beginFlight(duration)
                            val accepted = try { service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                                override fun onCompleted(gestureDescription: GestureDescription?) {
                                    finishFlight(sequence)
                                    if (session.accepts(token, SystemClock.elapsedRealtime())) completed(true)
                                }
                                override fun onCancelled(gestureDescription: GestureDescription?) {
                                    finishFlight(sequence)
                                    if (session.accepts(token, SystemClock.elapsedRealtime())) completed(false)
                                }
                            }, handler) } catch (failure: RuntimeException) { finishFlight(sequence); throw failure }
                            if (!accepted) finishFlight(sequence)
                            accepted
                        }
                    }
                }
            }).execute(token, request)
        return result
    }

    fun openVisualOnce(token: Long, request: ActionRequest, completed: (Boolean) -> Unit): OpenResult {
        if (inFlight) return OpenResult(false, false, "ACTION_IN_FLIGHT")
        return VisualSnapOpener(session, SystemClock::elapsedRealtime, currentSnapshot,
            SnapTapPort { icon, authorize ->
                val path = Path().apply { moveTo((icon.left + icon.right) / 2f, (icon.top + icon.bottom) / 2f) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build()
                if (!authorize()) false else {
                    val sequence = beginFlight(80)
                    val accepted = try { service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            finishFlight(sequence)
                            if (session.accepts(token, SystemClock.elapsedRealtime())) completed(true)
                        }
                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            finishFlight(sequence)
                            if (session.accepts(token, SystemClock.elapsedRealtime())) completed(false)
                        }
                    }, handler) } catch (failure: RuntimeException) {
                        finishFlight(sequence)
                        throw failure
                    }
                    if (!accepted) finishFlight(sequence)
                    accepted
                }
            }).open(token, request)
    }

    @Suppress("DEPRECATION")
    private fun clickExactControl(expected: UiSnapshot, control: UiNode, authorize: () -> Boolean): Boolean? {
        return performExactNode(expected, control, AccessibilityNodeInfo.ACTION_CLICK, authorize)
    }

    @Suppress("DEPRECATION")
    private fun performExactNode(expected: UiSnapshot, control: UiNode, action: Int, authorize: () -> Boolean): Boolean? {
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
            return node.performAction(action)
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
        val sequence = beginFlight(duration)
        val accepted = try { service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                finishFlight(sequence)
                if (session.accepts(token, SystemClock.elapsedRealtime())) {
                    report("GESTURE_COMPLETED (kein Nachweis einer Snap-Öffnung)")
                    completed()
                }
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                finishFlight(sequence)
                if (session.accepts(token, SystemClock.elapsedRealtime())) {
                    session.stop()
                    report("GESTURE_CANCELLED")
                }
            }
        }, handler) } catch (_: RuntimeException) {
            finishFlight(sequence)
            report("GESTURE_EXCEPTION")
            return
        }
        if (!accepted) { finishFlight(sequence); report("GESTURE_REJECTED") }
    }
}
