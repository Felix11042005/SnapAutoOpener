package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

internal data class IconFixture(val width: Int, val height: Int, val density: Float,
                                val expected: SnapSymbol, val pixels: IntArray)
internal fun loadIcon(name: String): IconFixture {
    val lines = IconFixture::class.java.getResourceAsStream("/icons/$name.icon")!!
        .bufferedReader().use { it.readLines() }
    val header = lines[0].split(' ')
    val palette = lines[1].split(',').map { (0xff000000L or it.toLong(16)).toInt() }
    val pixels = lines.drop(2).flatMap { row -> row.map { palette[it.digitToInt(16)] } }.toIntArray()
    val width = header[0].toInt(); val height = header[1].toInt()
    require(pixels.size == width * height)
    return IconFixture(width, height, header[2].toFloat(), SnapSymbol.valueOf(header[3]), pixels)
}

@RunWith(Parameterized::class)
class ReferenceIconTest(private val name: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun samples() = listOf(
            "video-red-first", "video-red-second", "video-red-count9plus", "video-red-scaled",
            "photo-red-outline", "photo-purple-outline", "photo-blue-arrow-outline",
            "photo-blue-arrow-solid", "photo-blue-chat-outline", "video-red-notification-dot"
        ).map { arrayOf(it) }
    }
    @Test fun suppliedSymbolHasExpectedStateAndOpeningDecision() {
        val f = loadIcon(name); val area = Box(0, 0, f.width, f.height)
        assertEquals(f.expected, SnapIconShape.classify(f.pixels, f.width, f.height, area))
        val target = VisualSnapTarget.detect(f.pixels, f.width, f.height, listOf(area), f.density)
        if (f.expected == SnapSymbol.UNOPENED_SNAP) {
            assertNotNull(target)
            assertTrue(area.contains(target!!.icon))
        } else assertNull(target)
    }
}

class ReferenceOpeningTest {
    @Test fun countedClippedStatusAndRealVideoIconReachOneTapOnlyAfterConfirmation() {
        val s = chatFixture().let { it.copy(nodes = it.nodes.map { n ->
            if (n.index == 5) n.copy(labels = listOf("9+ neue…"), bounds = it.nodes[4].bounds,
                clickable = true, canClick = true, path = listOf(2, 0, 0)) else n
        }) }
        val evidence = ScreenRecognizer.inspect(s)
        assertEquals(1, evidence.rows.size)
        assertTrue(ScreenRecognizer.directSnapControls(s).isEmpty())
        val pixels = IntArray(1000 * 2000) { 0xff111111.toInt() }
        val icon = loadIcon("video-red-count9plus")
        for (y in 0 until icon.height) for (x in 0 until icon.width)
            pixels[(310 + y) * 1000 + 40 + x] = icon.pixels[y * icon.width + x]
        val target = VisualSnapTarget.detect(pixels, 1000, 2000, evidence.rows, icon.density)!!
        assertTrue(target.icon.left in 42..44 && target.icon.top in 312..314)
        val session = DiagnosticSession(); val token = session.start(0, mode = SessionMode.INTERACTIVE)
        val tracker = VisualConfirmationTracker()
        assertNull(tracker.observe(token, s, target, 100).proof)
        val proof = tracker.observe(token, s, target, 1600).proof!!
        var dispatched = 0
        val opener = VisualSnapOpener(session, { 1700 }, { s }, SnapTapPort { b, authorize ->
            assertEquals(target.icon, b)
            if (authorize()) { dispatched++; true } else false
        })
        val request = ActionRequest(ActionKind.OPEN_SNAP, s, target.icon, visual = proof)
        assertTrue(opener.open(token, request).accepted)
        assertFalse(opener.open(token, request).accepted)
        assertEquals(1, dispatched)
    }
    @Test fun suppliedOutlineCannotOpenEvenWithClippedNewContentLabel() {
        val f = loadIcon("photo-red-outline")
        assertNull(VisualSnapTarget.detect(f.pixels, f.width, f.height,
            listOf(Box(0, 0, f.width, f.height)), f.density))
    }
    @Test fun purpleFilledVersionUsesSameShapeButIsSynthetic() {
        val f = loadIcon("video-red-first")
        val recolored = f.pixels.map { if (VisualEvidence.snapColor(it)) 0xffb919f4.toInt() else it }.toIntArray()
        assertEquals(SnapSymbol.UNOPENED_SNAP, SnapIconShape.classify(recolored, f.width, f.height,
            Box(0, 0, f.width, f.height)))
    }
}
