package de.example.snapauto

data class VisualIcon(val row: Box, val icon: Box, val frameHash: Long = 0)

/** Image-local coordinates. Semantic unopened status rows must be supplied by the recognizer. */
object VisualSnapTarget {
    fun detect(pixels: IntArray, width: Int, height: Int, rows: List<Box>, density: Float): VisualIcon? {
        for (row in rows.distinct().sortedBy { it.top }) {
            val candidates = VisualEvidence.detect(pixels, width, height, listOf(row), density).filter { c ->
                if (c.fill < 0.90 || c.bounds.width.toDouble() / c.bounds.height !in 0.85..1.17) return@filter false
                val b = c.bounds
                val insetX = (b.width / 5).coerceAtLeast(1)
                val insetY = (b.height / 5).coerceAtLeast(1)
                var colored = 0
                var total = 0
                for (y in b.top + insetY until b.bottom - insetY) {
                    for (x in b.left + insetX until b.right - insetX) {
                        total++
                        if (VisualEvidence.snapColor(pixels[y * width + x])) colored++
                    }
                }
                total > 0 && colored.toDouble() / total >= 0.98
            }
            // Multiple matching shapes in a row are ambiguous; do not choose a convenient one.
            if (candidates.size == 1) {
                var hash = -3750763034362895579L
                for (y in row.top until row.bottom) for (x in row.left until row.right)
                    hash = (hash xor pixels[y * width + x].toLong()) * 1099511628211L
                return VisualIcon(row, candidates.single().bounds, hash)
            }
        }
        return null
    }
}

class VisualTapProof private constructor(
    val token: Long, val snapshot: UiSnapshot, val target: VisualIcon,
    val firstAt: Long, val confirmedAt: Long
) {
    companion object {
        internal fun confirmed(token: Long, snapshot: UiSnapshot, target: VisualIcon, firstAt: Long, now: Long) =
            VisualTapProof(token, snapshot, target, firstAt, now)
    }

    fun valid(token: Long, now: Long, current: UiSnapshot, evidence: Recognition): Boolean =
        this.token == token && snapshot == current && confirmedAt - firstAt in 300..2_500 &&
            now - confirmedAt in 0..2_500 && target.row in evidence.rows &&
            target.row.contains(target.icon) && target.icon != target.row &&
            evidence.excludedRows.none { it.intersects(target.icon) }
}

data class VisualConfirmation(val reason: String, val proof: VisualTapProof? = null)

/** Main-thread owner. A changed/empty first image seals failure; no later target discovery. */
class VisualConfirmationTracker {
    private var first: VisualIcon? = null
    private var snapshot: UiSnapshot? = null
    private var token = 0L
    private var firstAt = 0L
    private var observed = false
    private var sealed = false

    fun reset() { first = null; snapshot = null; observed = false; sealed = false }

    fun observe(token: Long, snapshot: UiSnapshot, target: VisualIcon?, capturedAt: Long): VisualConfirmation {
        if (sealed) return VisualConfirmation("VISUAL_SELECTION_SEALED")
        if (!observed) {
            observed = true
            if (target == null) { sealed = true; return VisualConfirmation("NO_UNIQUE_SNAP_ICON") }
            first = target; this.snapshot = snapshot; this.token = token; firstAt = capturedAt
            return VisualConfirmation("SECOND_IMAGE_REQUIRED")
        }
        if (this.token != token || this.snapshot != snapshot || first != target || target == null ||
            capturedAt - firstAt !in 300..2_500) {
            sealed = true
            return VisualConfirmation("VISUAL_TARGET_CHANGED_OR_EXPIRED")
        }
        sealed = true
        return VisualConfirmation("TWO_IMAGES_CONFIRMED", VisualTapProof.confirmed(token, snapshot, target, firstAt, capturedAt))
    }
}
