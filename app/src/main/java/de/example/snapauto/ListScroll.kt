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

/** Counts only: never persist the labels or hashes used for correspondence. */
data class ScrollMotionEvidence(val kind: ScrollMotionKind, val reason: String,
    val beforeAnchors: Int = 0, val afterAnchors: Int = 0, val commonAnchors: Int = 0,
    val upward: Int = 0, val downward: Int = 0, val stationary: Int = 0,
    val source: String = "NODE_LABEL", val commonRows: Int = 0) {
    fun summary() = "Grund=$reason; Quelle=$source; gemeinsame Kontaktzeilen=$commonRows; Anker vorher=$beforeAnchors; nachher=$afterAnchors; gemeinsam=$commonAnchors; " +
        "Inhalt aufwärts=$upward; abwärts=$downward; unverändert=$stationary"
}

/** Compare unique text anchors inside the list. Raw labels never leave this method. */
object ScrollMotion {
    fun assess(before: UiSnapshot, after: UiSnapshot, direction: ListNavigationKind,
        identicalFallback: Boolean = false): ScrollMotionKind = inspect(before, after, direction, identicalFallback).kind

    fun inspect(before: UiSnapshot, after: UiSnapshot, direction: ListNavigationKind,
        identicalFallback: Boolean = false): ScrollMotionEvidence {
        if (direction == ListNavigationKind.FILTER || before.windowId != after.windowId ||
            before.bounds != after.bounds || before.packageName != after.packageName)
            return ScrollMotionEvidence(ScrollMotionKind.UNVERIFIED, "SURFACE_OR_DIRECTION_INVALID")
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
        // Match contacts by their owning row, using the same canonical identity as
        // inventory capture. Duplicate labels within one row are not two contacts.
        // Identical identities in different rows remain ambiguous and are excluded.
        fun rowAnchors(snapshot: UiSnapshot): Map<String, Int> = ScreenRecognizer.inspectFiltered(snapshot).rows
            .mapNotNull { row -> InventoryIdentityReader.read(snapshot, row)?.let { it.contact to (row.top + row.bottom) / 2 } }
            .groupBy { it.first }.filterValues { it.size == 1 }.mapValues { it.value.single().second }
        val oldRows = rowAnchors(before); val currentRows = rowAnchors(after)
        val commonRows = oldRows.keys.intersect(currentRows.keys).size
        val useRows = commonRows > 0
        val old = if (useRows) oldRows else anchors(before)
        val current = if (useRows) currentRows else anchors(after)
        val shifts = old.mapNotNull { (key, y) -> current[key]?.let { it - y } }
        val threshold = maxOf(4, before.bounds.height / 200)
        val moved = shifts.filter { kotlin.math.abs(it) >= threshold }
        fun result(kind: ScrollMotionKind, reason: String) = ScrollMotionEvidence(kind, reason,
            old.size, current.size, shifts.size, moved.count { it < 0 }, moved.count { it > 0 }, shifts.size - moved.size,
            if (useRows) "CONTACT_ROW" else "NODE_LABEL", commonRows)
        if (shifts.isEmpty()) return if (identicalFallback) result(ScrollMotionKind.UNCHANGED, "IDENTICAL_PAGE")
            else result(ScrollMotionKind.UNVERIFIED, if (old.isEmpty() || current.isEmpty()) "ANCHORS_MISSING" else "NO_COMMON_ANCHORS")
        if (moved.isEmpty()) return result(ScrollMotionKind.UNCHANGED, "ANCHORS_STATIONARY")
        if (moved.any { it < 0 } && moved.any { it > 0 }) return result(ScrollMotionKind.UNVERIFIED, "CONFLICTING_MOVEMENT")
        val expectedNegative = direction == ListNavigationKind.DOWN
        return if ((moved.first() < 0) == expectedNegative) result(ScrollMotionKind.EXPECTED, "DIRECTION_CONFIRMED")
            else result(ScrollMotionKind.REVERSED, "DIRECTION_REVERSED")
    }
}
