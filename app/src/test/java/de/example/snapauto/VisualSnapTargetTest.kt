package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

class VisualSnapTargetTest {
    private val row = Box(0, 20, 100, 80)
    private fun image(color: Int = 0xffff0033.toInt(), x: Int = 20, y: Int = 40, size: Int = 12,
                      keep: (Int, Int) -> Boolean = { _, _ -> true }): IntArray {
        val pixels = IntArray(100 * 100) { 0xff222222.toInt() }
        for (dy in 0 until size) for (dx in 0 until size)
            if (keep(dx, dy)) pixels[(y + dy) * 100 + x + dx] = color
        return pixels
    }
    private fun detect(p: IntArray, rows: List<Box> = listOf(row), density: Float = 1f) =
        VisualSnapTarget.detect(p, 100, 100, rows, density)

    @Test fun uniqueFilledRedOrPurpleSquareGivesExactIconBounds() {
        assertEquals(Box(20, 40, 32, 52), detect(image())!!.icon)
        assertEquals(Box(70, 50, 82, 62), detect(image(color = 0xffaa00dd.toInt(), x = 70, y = 50))!!.icon)
        assertEquals(row, detect(image())!!.row)
    }
    @Test fun blueOutlineTriangleAndCircleAreNotFilledSquares() {
        assertNull(detect(image(color = 0xff0088ff.toInt())))
        assertNull(detect(image(keep = { x, y -> x == 0 || y == 0 || x == 11 || y == 11 })))
        assertNull(detect(image(keep = { x, y -> x <= y })))
        assertNull(detect(image(keep = { x, y -> (x - 5.5) * (x - 5.5) + (y - 5.5) * (y - 5.5) < 36 })))
    }
    @Test fun centralHoleRejectsNearlyFilledDecoration() {
        assertNull(detect(image(keep = { x, y -> !(x in 5..6 && y in 5..6) })))
    }
    @Test fun noSemanticRegionOrClippedRegionCannotProduceTarget() {
        assertNull(detect(image(), emptyList()))
        assertNull(detect(image(), listOf(Box(-1, 0, 100, 100))))
        assertNull(detect(image(), listOf(Box(0, 0, 100, 30))))
    }
    @Test fun twoMatchingShapesInSameRowAreAmbiguous() {
        val p = image()
        for (y in 40 until 52) for (x in 60 until 72) p[y * 100 + x] = 0xffaa00dd.toInt()
        assertNull(detect(p))
    }
    @Test fun targetsAreSelectedOnlyFromSuppliedInitialRows() {
        val p = image(y = 65)
        assertNull(detect(p, listOf(Box(0, 20, 100, 60))))
        assertEquals(Box(0, 60, 100, 90), detect(p, listOf(Box(0, 60, 100, 90)))!!.row)
    }
    @Test fun anyChangedRowPixelChangesConfirmationHash() {
        val p = image(); val before = detect(p)!!
        p[25 * 100 + 90] = 0xff333333.toInt()
        val after = detect(p)!!
        assertEquals(before.icon, after.icon)
        assertNotEquals(before.frameHash, after.frameHash)
    }
    @Test fun pixelsOutsideSelectedRowDoNotChangeItsHash() {
        val p = image(); val before = detect(p)!!
        p[90 * 100 + 90] = 0xff333333.toInt()
        assertEquals(before, detect(p))
    }
    @Test fun sizeLimitsScaleWithDensity() {
        assertNull(detect(image(size = 2)))
        assertNull(detect(image(size = 30)))
        assertNotNull(detect(image(size = 24), density = 2f))
    }
    @Test fun sameShapedDecorationIsKnownResidualAmbiguity() {
        // Shape cannot establish identity. Semantic row proof and device validation remain essential.
        assertNotNull(detect(image(x = 65)))
    }
}
