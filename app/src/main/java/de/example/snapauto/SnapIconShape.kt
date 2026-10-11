package de.example.snapauto

enum class SnapSymbol { UNOPENED_SNAP, VIEWED_SNAP, OTHER }

/** Pure image geometry. Rounded corner profile measured from the supplied video,
 * never a screenshot coordinate. Identity still requires a verified status row. */
object SnapIconShape {
    private val filledProfile = listOf(
        "..############..", ".##############.",
        "################", "################", "################", "################",
        "################", "################", "################", "################",
        "################", "################", "################", "################",
        ".##############.", "..############.."
    )

    fun classify(pixels: IntArray, width: Int, height: Int, area: Box): SnapSymbol {
        require(width > 0 && height > 0 && pixels.size == width * height)
        if (!Box(0, 0, width, height).contains(area)) return SnapSymbol.OTHER
        var left = area.right; var top = area.bottom; var right = area.left; var bottom = area.top
        for (y in area.top until area.bottom) for (x in area.left until area.right) {
            if (VisualEvidence.snapColor(pixels[y * width + x])) {
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
            }
        }
        val b = Box(left, top, right, bottom)
        if (!b.valid || b.width.toDouble() / b.height !in 0.85..1.17 || minOf(b.width, b.height) < 5)
            return SnapSymbol.OTHER
        var colored = 0; var core = 0; var coreTotal = 0
        val insetX = (b.width / 5).coerceAtLeast(1); val insetY = (b.height / 5).coerceAtLeast(1)
        for (y in b.top until b.bottom) for (x in b.left until b.right) {
            val color = VisualEvidence.snapColor(pixels[y * width + x])
            if (color) colored++
            if (x in b.left + insetX until b.right - insetX && y in b.top + insetY until b.bottom - insetY) {
                coreTotal++; if (color) core++
            }
        }
        val fill = colored.toDouble() / (b.width * b.height)
        val coreFill = core.toDouble() / coreTotal.coerceAtLeast(1)
        var matching = 0
        for (y in 0 until 16) for (x in 0 until 16) {
            val px = b.left + ((x + 0.5) * b.width / 16).toInt().coerceAtMost(b.width - 1)
            val py = b.top + ((y + 0.5) * b.height / 16).toInt().coerceAtMost(b.height - 1)
            if (VisualEvidence.snapColor(pixels[py * width + px]) == (filledProfile[y][x] == '#')) matching++
        }
        return when {
            fill >= 0.90 && coreFill >= 0.98 && matching / 256.0 >= 0.92 -> SnapSymbol.UNOPENED_SNAP
            fill in 0.18..0.50 && coreFill <= 0.05 && outlineEdges(pixels, width, b) -> SnapSymbol.VIEWED_SNAP
            else -> SnapSymbol.OTHER
        }
    }

    private fun outlineEdges(pixels: IntArray, width: Int, b: Box): Boolean {
        // All four edge middles must contain color; a sent arrow is insufficient.
        fun edge(points: List<Pair<Int, Int>>) = points.count { (x, y) ->
            VisualEvidence.snapColor(pixels[y * width + x])
        } >= points.size * 0.7
        val xs = (b.left + b.width / 3 until b.right - b.width / 3)
        val ys = (b.top + b.height / 3 until b.bottom - b.height / 3)
        return edge(xs.map { it to b.top }) && edge(xs.map { it to b.bottom - 1 }) &&
            edge(ys.map { b.left to it }) && edge(ys.map { b.right - 1 to it })
    }
}
