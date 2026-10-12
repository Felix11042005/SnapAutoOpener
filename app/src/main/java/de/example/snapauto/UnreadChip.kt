package de.example.snapauto

import kotlin.math.abs

data class ChipObservation(val state: FilterState, val hash: Long)

/** Dark-layout pill evidence anchored to an exact accessibility Unread label.
 * Unrecognized colors/layouts remain UNKNOWN; a gray pixel alone is insufficient. */
object UnreadChip {
    fun inspect(pixels: IntArray, width: Int, height: Int, label: Box): ChipObservation {
        fun unknown() = ChipObservation(FilterState.UNKNOWN, 0)
        if (width <= 0 || height <= 0 || pixels.size != width * height || !Box(0, 0, width, height).contains(label)) return unknown()
        val margin = label.height.coerceAtLeast(2)
        val region = Box(maxOf(0, label.left - margin * 2), maxOf(0, label.top - margin / 2),
            minOf(width, label.right + margin * 2), minOf(height, label.bottom + margin / 2))
        fun gray(pixel: Int): Int {
            val r = pixel shr 16 and 255; val g = pixel shr 8 and 255; val b = pixel and 255
            return if (maxOf(r, g, b) - minOf(r, g, b) <= 12) (r + g + b) / 3 else -1
        }
        val surround = mutableListOf<Int>()
        val histogram = IntArray(32)
        for (y in region.top until region.bottom) for (x in region.left until region.right) {
            val value = gray(pixels[y * width + x])
            if (value in 0..150) {
                surround += value
                if (x in label.left until label.right && y in label.top until label.bottom) histogram[value / 8]++
            }
        }
        if (surround.size < region.width * region.height / 3) return unknown()
        surround.sort()
        val background = surround[surround.size / 5]
        if (background > 55) return unknown() // No light-theme guess.
        val bin = histogram.indices.maxByOrNull { histogram[it] } ?: return unknown()
        val dominant = bin * 8 + 4
        fun hash(box: Box): Long {
            var value = -3750763034362895579L
            for (y in box.top until box.bottom) for (x in box.left until box.right)
                value = (value xor pixels[y * width + x].toLong()) * 1099511628211L
            return value
        }
        if (histogram[bin] < label.width * label.height / 3) return unknown()
        if (dominant - background <= 10) return ChipObservation(FilterState.NOT_SELECTED, hash(label))
        if (dominant - background < 18) return unknown()
        val seen = BooleanArray(region.width * region.height)
        val queue = IntArray(seen.size)
        var best = 0
        var bestBounds: Box? = null
        for (local in seen.indices) {
            if (seen[local]) continue
            val sx = region.left + local % region.width; val sy = region.top + local / region.width
            val value = gray(pixels[sy * width + sx])
            seen[local] = true
            if (value < 0 || abs(value - dominant) > 9) continue
            var head = 0; var tail = 1; queue[0] = local
            var left = sx; var right = sx; var top = sy; var bottom = sy
            while (head < tail) {
                val point = queue[head++]; val x = point % region.width; val y = point / region.width
                left = minOf(left, region.left + x); right = maxOf(right, region.left + x)
                top = minOf(top, region.top + y); bottom = maxOf(bottom, region.top + y)
                for ((nx, ny) in arrayOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                    if (nx !in 0 until region.width || ny !in 0 until region.height) continue
                    val next = ny * region.width + nx
                    if (seen[next]) continue
                    seen[next] = true
                    val g = gray(pixels[(region.top + ny) * width + region.left + nx])
                    if (g >= 0 && abs(g - dominant) <= 9) queue[tail++] = next
                }
            }
            val bounds = Box(left, top, right + 1, bottom + 1)
            if (tail > best && bounds.intersects(label)) { best = tail; bestBounds = bounds }
        }
        val pill = bestBounds ?: return unknown()
        if (pill.width < label.width * .8 || pill.height < label.height * .8 ||
            pill.height > label.height * 2.2 || pill.width < pill.height * 1.4 ||
            best < pill.width * pill.height * .6 || pill.left <= region.left || pill.right >= region.right ||
            pill.top <= region.top || pill.bottom >= region.bottom) return unknown()
        return ChipObservation(FilterState.SELECTED, hash(pill))
    }
}

data class PageObservation(val chip: ChipObservation?, val icons: List<VisualIcon>)

class ConfirmedPage private constructor(val token: Long, val snapshot: UiSnapshot,
    val observation: PageObservation, val firstAt: Long, val confirmedAt: Long) {
    fun fresh(token: Long, current: UiSnapshot, now: Long) = this.token == token && snapshot == current &&
        now - confirmedAt in 0..2_500
    companion object {
        internal fun create(token: Long, snapshot: UiSnapshot, observation: PageObservation, firstAt: Long, now: Long) =
            ConfirmedPage(token, snapshot, observation, firstAt, now)
    }
}

class PageConfirmation {
    private var first: Triple<Long, UiSnapshot, PageObservation>? = null
    private var firstAt = 0L
    fun reset() { first = null }
    fun observe(token: Long, snapshot: UiSnapshot, observation: PageObservation, now: Long): ConfirmedPage? {
        val value = Triple(token, snapshot, observation)
        val previous = first
        if (previous == null) { first = value; firstAt = now; return null }
        first = null
        return if (value == previous && now - firstAt in 300..2_500)
            ConfirmedPage.create(token, snapshot, observation, firstAt, now) else null
    }
    val awaitingSecond get() = first != null
}
