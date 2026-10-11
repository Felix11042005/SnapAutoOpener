package de.example.snapauto

import java.security.MessageDigest
import java.util.Locale

/** Finite visible roster. Labels are hashed in memory, never persisted or reported.
 * This is conservative row re-identification, not a server-side Snap identity. */
class ExistingSnapBatch {
    private var initial: UiSnapshot? = null
    private val remaining = mutableListOf<String>()
    var frozen = false
        private set
    var initialCount = 0
        private set
    var skipped = 0
        private set
    val remainingCount get() = remaining.size

    fun reset(snapshot: UiSnapshot) {
        initial = snapshot; remaining.clear(); frozen = false; initialCount = 0; skipped = 0
    }

    fun sameWindow(snapshot: UiSnapshot) = initial?.let {
        it.packageName == snapshot.packageName && it.windowId == snapshot.windowId && it.bounds == snapshot.bounds
    } == true

    fun freeze(snapshot: UiSnapshot, verifiedRows: List<Box>): Boolean {
        if (frozen || snapshot != initial) return false
        frozen = true
        val evidence = ScreenRecognizer.inspect(snapshot)
        if (evidence.screen != ScreenKind.CHAT_LIST) return false
        val groups = evidence.rows.mapNotNull { row -> key(snapshot, row)?.let { it to row } }.groupBy { it.first }
        for (row in verifiedRows.distinct().sortedBy { it.top }) {
            if (row !in evidence.rows) continue
            val key = key(snapshot, row) ?: continue
            if (groups[key]?.size == 1 && key !in remaining && remaining.size < 10) remaining += key
        }
        initialCount = remaining.size
        return remaining.isNotEmpty()
    }

    /** Drop vanished, changed or ambiguous original rows. Never add a replacement. */
    fun next(snapshot: UiSnapshot, evidence: Recognition): Box? {
        if (!frozen || !sameWindow(snapshot) || evidence.screen != ScreenKind.CHAT_LIST) return null
        val groups = evidence.rows.mapNotNull { row -> key(snapshot, row)?.let { it to row } }.groupBy { it.first }
        while (remaining.isNotEmpty()) {
            val matches = groups[remaining.first()].orEmpty()
            if (matches.size == 1) return matches.single().second
            remaining.removeAt(0); skipped++
        }
        return null
    }

    fun consume(snapshot: UiSnapshot, row: Box): Boolean {
        if (remaining.isEmpty() || key(snapshot, row) != remaining.first()) return false
        remaining.removeAt(0)
        return true
    }

    private fun key(snapshot: UiSnapshot, row: Box): String? {
        val nodes = snapshot.nodes.filter { it.visible && it.enabled && row.contains(it.bounds) }
        val labels = nodes.flatMap { it.labels }.map { it.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }.sorted()
        val generic = setOf("kamera", "camera", "antworten", "reply", "mehr", "more")
        // A status alone cannot identify a conversation. Require another label.
        if (nodes.none { node -> ScreenRecognizer.markerKind(node) == null &&
                node.labels.any { it.trim().lowercase(Locale.ROOT) !in generic && it.isNotBlank() } }) return null
        val digest = MessageDigest.getInstance("SHA-256")
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII)); digest.update(0.toByte())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
