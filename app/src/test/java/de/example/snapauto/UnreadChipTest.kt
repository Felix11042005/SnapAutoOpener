package de.example.snapauto

import org.junit.Assert.*
import org.junit.Test

class UnreadChipTest {
    @Test fun suppliedSelectedAndUnselectedFiltersAtBothImageScales() {
        for (name in listOf("video-selected", "video-selected-scaled", "photo-unselected", "photo-unselected-scaled")) {
            val lines = javaClass.getResourceAsStream("/filters/$name.chip")!!.bufferedReader().use { it.readLines() }
            val header = lines[0].split(' ')
            val palette = lines[1].split(',').map { (0xff000000L or it.toLong(16)).toInt() }
            val pixels = lines.drop(2).flatMap { row -> row.map { palette[it.digitToInt(16)] } }.toIntArray()
            val width = header[0].toInt(); val height = header[1].toInt()
            val label = Box(header[2].toInt(), header[3].toInt(), header[4].toInt(), header[5].toInt())
            assertEquals(name, FilterState.valueOf(header[6]), UnreadChip.inspect(pixels, width, height, label).state)
            if (name.startsWith("video")) {
                // Some versions expose only the word, excluding the count badge.
                assertEquals(name, FilterState.SELECTED,
                    UnreadChip.inspect(pixels, width, height, label.copy(right = label.left + label.width * 3 / 4)).state)
            }
        }
    }
    @Test fun unsupportedThemeInvalidBoundsAndNoBoundedPillStayUnknown() {
        val width = 300; val height = 120; val label = Box(80, 40, 220, 80)
        assertEquals(FilterState.UNKNOWN, UnreadChip.inspect(IntArray(width * height) { 0xffeeeeee.toInt() }, width, height, label).state)
        assertEquals(FilterState.UNKNOWN, UnreadChip.inspect(IntArray(2), width, height, label).state)
        assertEquals(FilterState.UNKNOWN, UnreadChip.inspect(IntArray(width * height), width, height, label.copy(left = -1)).state)
        val pixels = IntArray(width * height) { 0xff111111.toInt() }
        // Gray background extends outside the local region, not a bounded selected chip.
        for (y in 40 until 80) for (x in 0 until width) pixels[y * width + x] = 0xff373737.toInt()
        assertEquals(FilterState.UNKNOWN, UnreadChip.inspect(pixels, width, height, label).state)
    }
    @Test fun twoFreshIdenticalPageImagesRequired() {
        val s = inventoryPage(); val tracker = PageConfirmation()
        val observation = PageObservation(ChipObservation(FilterState.SELECTED, 12), emptyList())
        assertNull(tracker.observe(1, s, observation, 100)); assertTrue(tracker.awaitingSecond)
        val proof = tracker.observe(1, s, observation, 600)!!
        assertFalse(tracker.awaitingSecond)
        assertTrue(proof.fresh(1, s, 700))
        assertFalse(proof.fresh(2, s, 700))
        assertFalse(proof.fresh(1, s.copy(windowId = 88), 700))
        assertFalse(proof.fresh(1, s, 3101))
    }
    @Test fun changedChipIconSnapshotSessionOrImageTimeCannotConfirmPage() {
        val s = inventoryPage(); val original = PageObservation(ChipObservation(FilterState.SELECTED, 12), emptyList())
        for (case in 0..5) {
            val tracker = PageConfirmation(); tracker.observe(1, s, original, 100)
            val observation = when (case) {
                0 -> original.copy(chip = ChipObservation(FilterState.SELECTED, 13))
                1 -> original.copy(icons = listOf(VisualIcon(Box(0, 240, 1000, 400), Box(100, 330, 120, 350))))
                else -> original
            }
            assertNull(tracker.observe(if (case == 2) 2 else 1,
                if (case == 3) s.copy(truncated = true) else s, observation,
                when (case) { 4 -> 200; 5 -> 2700; else -> 600 }))
            assertFalse(tracker.awaitingSecond)
        }
    }
    @Test fun resetInvalidatesPendingPageProof() {
        val s = inventoryPage(); val tracker = PageConfirmation()
        val observation = PageObservation(null, emptyList())
        tracker.observe(1, s, observation, 100); tracker.reset()
        assertNull(tracker.observe(1, s, observation, 600)); assertTrue(tracker.awaitingSecond)
    }
    @Test fun changedOrdinaryChatPixelsInvalidateOtherwiseEmptyPageProof() {
        val s = inventoryPage(emptyList(), filter = false); val tracker = PageConfirmation()
        assertNull(tracker.observe(1, s, PageObservation(null, emptyList(), 111), 100))
        assertNull(tracker.observe(1, s, PageObservation(null, emptyList(), 112), 600))
        assertFalse(tracker.awaitingSecond)
    }
}
