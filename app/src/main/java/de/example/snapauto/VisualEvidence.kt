package de.example.snapauto

import kotlin.math.max
import kotlin.math.min

data class ColorCandidate(val bounds: Box, val fill: Double)

/** Connected components inside semantic rows. No fixed screen strips or action coordinates. */
object VisualEvidence {
    internal fun snapColor(pixel: Int): Boolean {
        val r = pixel shr 16 and 255
        val g = pixel shr 8 and 255
        val b = pixel and 255
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val saturation = if (high == 0) 0.0 else (high - low).toDouble() / high
        return saturation > 0.45 && ((r > 180 && g < 120 && b < 140) ||
            (r > 110 && b > 130 && r > g * 1.4 && b > g * 1.4))
    }

    fun detect(pixels: IntArray, width: Int, height: Int, regions: List<Box>, density: Float): List<ColorCandidate> {
        require(width > 0 && height > 0 && pixels.size == width * height && density > 0)
        val image = Box(0, 0, width, height)
        val result = mutableListOf<ColorCandidate>()
        for (region in regions.distinct()) {
            if (!image.contains(region)) continue
            val rw = region.width
            val visited = BooleanArray(rw * region.height)
            val queue = IntArray(visited.size)
            for (origin in visited.indices) {
                if (visited[origin]) continue
                visited[origin] = true
                fun colorAt(i: Int) = snapColor(pixels[(region.top + i / rw) * width + region.left + i % rw])
                if (!colorAt(origin)) continue
                var count = 0
                var head = 0
                var tail = 1
                var left = rw
                var top = region.height
                var right = 0
                var bottom = 0
                queue[0] = origin
                while (head < tail) {
                    val i = queue[head++]
                    val x = i % rw
                    val y = i / rw
                    count++
                    left = min(left, x); right = max(right, x)
                    top = min(top, y); bottom = max(bottom, y)
                    fun visit(j: Int) {
                        if (!visited[j]) {
                            visited[j] = true
                            if (colorAt(j)) queue[tail++] = j
                        }
                    }
                    if (x > 0) visit(i - 1)
                    if (x + 1 < rw) visit(i + 1)
                    if (y > 0) visit(i - rw)
                    if (y + 1 < region.height) visit(i + rw)
                }
                val bw = right - left + 1
                val bh = bottom - top + 1
                val fill = count.toDouble() / (bw * bh)
                val ratio = bw.toDouble() / bh
                // Density-scaled shape prior only; these thresholds still require device calibration.
                if (bw / density in 5f..28f && bh / density in 5f..28f &&
                    ratio in 0.75..1.33 && fill >= 0.75) {
                    result += ColorCandidate(Box(region.left + left, region.top + top,
                        region.left + right + 1, region.top + bottom + 1), fill)
                }
            }
        }
        return result.distinctBy { it.bounds }.take(30)
    }
}
