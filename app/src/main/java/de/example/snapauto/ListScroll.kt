package de.example.snapauto

import java.security.MessageDigest
import java.util.Locale

data class SwipePlan(val x: Float, val startY: Float, val endY: Float, val duration: Long = 450)

/** Directions describe the destination in the list, not the finger movement. */
object ListScroll {
    fun plan(region: Box, direction: ListNavigationKind): SwipePlan? {
        if (!region.valid || direction == ListNavigationKind.FILTER) return null
        val center = (region.top + region.bottom) / 2f
        val end = region.top + region.height * if (direction == ListNavigationKind.DOWN) .25f else .75f
        return SwipePlan((region.left + region.right) / 2f, center, end)
    }
}

enum class ScrollMotionKind { EXPECTED, REVERSED, UNCHANGED, UNVERIFIED }

/** Compare unique text anchors inside the list. Raw labels never leave this method. */
object ScrollMotion {
    fun assess(before: UiSnapshot, after: UiSnapshot, direction: ListNavigationKind,
        identicalFallback: Boolean = false): ScrollMotionKind {
        if (direction == ListNavigationKind.FILTER || before.windowId != after.windowId ||
            before.bounds != after.bounds || before.packageName != after.packageName) return ScrollMotionKind.UNVERIFIED
        fun anchors(snapshot: UiSnapshot): Map<String, Int> {
            val evidence = ScreenRecognizer.inspectFiltered(snapshot)
            val region = ChatListSurface.find(snapshot, evidence)?.region ?: return emptyMap()
            val generic = setOf("kamera", "camera", "antworten", "reply", "mehr", "more")
            val values = snapshot.nodes.filter { node -> node.visible && node.enabled && !node.list &&
                region.contains(node.bounds) && ScreenRecognizer.markerKind(node) == null &&
                evidence.excludedRows.none { it.intersects(node.bounds) }
            }.mapNotNull { node ->
                val labels = node.labels.map { it.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") }
                    .filter { it.isNotEmpty() && it !in generic && !Regex("(?:vor )?\\d+\\s*(?:min.*|sek.*|std.*|stunden|hours?|days?|tage?)[.]?").matches(it) }
                if (labels.isEmpty()) null else {
                    val key = MessageDigest.getInstance("SHA-256").digest(labels.sorted().joinToString("\u0000").toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    key to (node.bounds.top + node.bounds.bottom) / 2
                }
            }.groupBy { it.first }
            return values.filterValues { it.size == 1 }.mapValues { it.value.single().second }
        }
        val old = anchors(before); val current = anchors(after)
        val shifts = old.mapNotNull { (key, y) -> current[key]?.let { it - y } }
        if (shifts.isEmpty()) return if (identicalFallback) ScrollMotionKind.UNCHANGED else ScrollMotionKind.UNVERIFIED
        val threshold = maxOf(4, before.bounds.height / 200)
        val moved = shifts.filter { kotlin.math.abs(it) >= threshold }
        if (moved.isEmpty()) return ScrollMotionKind.UNCHANGED
        if (moved.any { it < 0 } && moved.any { it > 0 }) return ScrollMotionKind.UNVERIFIED
        val expectedNegative = direction == ListNavigationKind.DOWN
        return if ((moved.first() < 0) == expectedNegative) ScrollMotionKind.EXPECTED else ScrollMotionKind.REVERSED
    }
}
