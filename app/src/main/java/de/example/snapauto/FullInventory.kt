package de.example.snapauto

import java.security.MessageDigest
import java.util.Locale

private fun canonical(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
private val ageLabel = Regex("(?:vor )?\\d+\\s*(?:sek(?:unden)?|sec(?:onds)?|min(?:uten|utes)?|std|stunden|hours?|tage?|days?|wochen|weeks?)[.]?")
private fun digest(values: List<String>): String = MessageDigest.getInstance("SHA-256")
    .digest(values.sorted().joinToString("\u0000").toByteArray()).joinToString("") { "%02x".format(it) }

data class InventoryIdentity(val contact: String, val status: String)

/** Only hashes survive a page change. No raw names, age labels or coordinates identify a target. */
object InventoryIdentityReader {
    fun read(snapshot: UiSnapshot, row: Box): InventoryIdentity? {
        val nodes = snapshot.nodes.filter { it.visible && it.enabled && row.contains(it.bounds) }
        val statuses = nodes.filter { ScreenRecognizer.markerKind(it) in setOf("SNAP", "SNAP_PARTIAL") }
            .flatMap { it.labels }.map(::canonical).filter { it.isNotEmpty() }.distinct()
        val generic = setOf("kamera", "camera", "antworten", "reply", "mehr", "more")
        val contact = nodes.filter { ScreenRecognizer.markerKind(it) == null }.flatMap { it.labels }
            .map(::canonical).filter { it.isNotEmpty() && it !in generic && !ageLabel.matches(it) }.distinct()
        if (contact.isEmpty() || statuses.isEmpty()) return null
        return InventoryIdentity(digest(contact), digest(statuses))
    }
}

class InventoryRoster {
    private val records = linkedMapOf<String, String>()
    private val pending = linkedSetOf<String>()
    private val ambiguous = mutableSetOf<String>()
    var sealed = false
        private set
    var skipped = 0
        private set
    val size get() = records.size
    val remaining get() = pending.size

    fun add(snapshot: UiSnapshot, evidence: Recognition, verifiedRows: List<Box>): Boolean {
        if (sealed) return false
        val all = evidence.rows.mapNotNull { row -> InventoryIdentityReader.read(snapshot, row)?.let { row to it } }
        val duplicates = all.groupBy { it.second.contact }.filterValues { it.size > 1 }.keys
        for ((row, id) in all) {
            if (id.contact in duplicates || records[id.contact]?.let { it != id.status } == true) {
                ambiguous += id.contact; records.remove(id.contact); pending.remove(id.contact)
                continue
            }
            if (row !in verifiedRows || id.contact in ambiguous) continue
            if (id.contact !in records && records.size >= 200) return false
            records[id.contact] = id.status; pending += id.contact
        }
        return true
    }

    fun seal() { sealed = true }

    fun nextVisible(snapshot: UiSnapshot, evidence: Recognition): Box? {
        if (!sealed || evidence.screen != ScreenKind.CHAT_LIST) return null
        val visible = evidence.rows.mapNotNull { row -> InventoryIdentityReader.read(snapshot, row)?.let { it to row } }
            .groupBy { it.first.contact }
        for (key in pending.toList()) {
            val matches = visible[key].orEmpty()
            if (matches.isEmpty()) continue // Offscreen originals remain pending.
            if (matches.size != 1 || matches.single().first.status != records[key]) {
                pending.remove(key); skipped++; continue
            }
            return matches.single().second
        }
        return null
    }

    fun consume(snapshot: UiSnapshot, row: Box): Boolean {
        val id = InventoryIdentityReader.read(snapshot, row) ?: return false
        if (!sealed || records[id.contact] != id.status) return false
        return pending.remove(id.contact)
    }

    fun skip(snapshot: UiSnapshot, row: Box) {
        val id = InventoryIdentityReader.read(snapshot, row) ?: return
        if (pending.remove(id.contact)) skipped++
    }

    fun finish() { skipped += pending.size; pending.clear() }
}

enum class InventoryPhase { FILTER, TOP_SCAN, SCAN, TOP_OPEN, OPEN, FINISHED }
enum class InventoryStep { SCROLL_UP, SCROLL_DOWN, OPEN, DONE, STOP }
data class InventoryDecision(val step: InventoryStep, val reason: String, val row: Box? = null)

/** Two-pass bounded traversal: inventory first, then only frozen targets. */
class FullInventory(val owner: UiSnapshot) {
    val roster = InventoryRoster()
    var phase = InventoryPhase.FILTER
        private set
    var filterAttempted = false
        private set
    var scrolls = 0
        private set
    var pages = 0
        private set
    private var previousScrollPage: String? = null
    private var noMovement = 0
    private val scanPages = mutableSetOf<String>()

    fun sameWindow(snapshot: UiSnapshot) = snapshot.packageName == ScreenRecognizer.TARGET_PACKAGE &&
        snapshot.windowId == owner.windowId && snapshot.bounds == owner.bounds

    fun claimFilter(): Boolean {
        if (phase != InventoryPhase.FILTER || filterAttempted) return false
        filterAttempted = true; return true
    }

    fun filterReady(page: ConfirmedPage): Boolean {
        if (phase != InventoryPhase.FILTER || !sameWindow(page.snapshot) ||
            ScreenRecognizer.unreadControl(page.snapshot) == null || page.observation.chip?.state != FilterState.SELECTED) return false
        phase = InventoryPhase.TOP_SCAN
        return true
    }

    fun page(snapshot: UiSnapshot, evidence: Recognition, verifiedRows: List<Box>, contentHash: Long? = null): InventoryDecision {
        if (!sameWindow(snapshot) || evidence.screen != ScreenKind.CHAT_LIST || phase == InventoryPhase.FILTER)
            return stop("INVENTORY_SURFACE_INVALID")
        val surface = ChatListSurface.find(snapshot, evidence) ?: return stop("INVENTORY_LIST_AMBIGUOUS")
        val fingerprint = fingerprint(snapshot, surface.region) + ":$contentHash"
        previousScrollPage?.let { noMovement = if (it == fingerprint) noMovement + 1 else 0 }
        previousScrollPage = null
        if (phase == InventoryPhase.TOP_SCAN && noMovement >= 2) {
            phase = InventoryPhase.SCAN; noMovement = 0
        }
        if (phase == InventoryPhase.SCAN) {
            scanPages += fingerprint; pages = scanPages.size
            if (pages > 50 || !roster.add(snapshot, evidence, verifiedRows)) return stop("INVENTORY_SCAN_LIMIT")
            if (noMovement >= 2) {
                roster.seal()
                if (roster.size == 0) return done("NO_VERIFIED_SNAP_TARGETS")
                phase = InventoryPhase.TOP_OPEN; noMovement = 0
            }
        }
        if (phase == InventoryPhase.TOP_OPEN && noMovement >= 2) {
            phase = InventoryPhase.OPEN; noMovement = 0
        }
        if (phase == InventoryPhase.OPEN) {
            val row = roster.nextVisible(snapshot, evidence)
            if (row != null) return InventoryDecision(InventoryStep.OPEN, "FROZEN_TARGET_VISIBLE", row)
            if (roster.remaining == 0 || noMovement >= 2) return done("FROZEN_INVENTORY_FINISHED")
        }
        if (phase == InventoryPhase.FINISHED) return InventoryDecision(InventoryStep.DONE, "INVENTORY_FINISHED")
        if (scrolls >= 300) return stop("INVENTORY_SCROLL_LIMIT")
        previousScrollPage = fingerprint; scrolls++
        return if (phase in setOf(InventoryPhase.TOP_SCAN, InventoryPhase.TOP_OPEN))
            InventoryDecision(InventoryStep.SCROLL_UP, "SEEK_LIST_TOP")
        else InventoryDecision(InventoryStep.SCROLL_DOWN, if (phase == InventoryPhase.SCAN) "COLLECT_INITIAL_INVENTORY" else "SEEK_FROZEN_TARGET")
    }

    private fun done(reason: String): InventoryDecision {
        roster.finish(); phase = InventoryPhase.FINISHED
        return InventoryDecision(InventoryStep.DONE, reason)
    }
    private fun stop(reason: String): InventoryDecision {
        phase = InventoryPhase.FINISHED
        return InventoryDecision(InventoryStep.STOP, reason)
    }
    private fun fingerprint(snapshot: UiSnapshot, region: Box): String = digest(snapshot.nodes.filter {
        it.visible && region.contains(it.bounds) && !it.list
    }.map { node ->
        val labels = node.labels.map(::canonical).filterNot { ageLabel.matches(it) }.sorted()
        "${node.bounds.left},${node.bounds.top},${node.bounds.right},${node.bounds.bottom}:$labels"
    }.distinct())
}
