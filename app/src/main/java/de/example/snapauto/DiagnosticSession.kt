package de.example.snapauto

/** Main-thread owner; caller supplies monotonic elapsed time, never wall-clock time. */
enum class SessionMode { DIAGNOSTIC, INTERACTIVE }

class DiagnosticSession {
    var mode = SessionMode.DIAGNOSTIC
        private set
    var generation = 0L
        private set
    var deadline = 0L
        private set
    var running = false
        private set

    fun start(now: Long, duration: Long = 30_000, mode: SessionMode = SessionMode.DIAGNOSTIC): Long {
        require(duration in 1..120_000)
        generation++
        this.mode = mode
        deadline = now + duration
        running = true
        return generation
    }

    fun accepts(token: Long, now: Long) = running && token == generation && now < deadline
    fun expired(now: Long) = running && now >= deadline

    fun stop() {
        running = false
        generation++ // Invalidates work even when STOP is repeated.
    }
}

class DiagnosticLog(private val capacity: Int = 60) {
    private val lines = ArrayDeque<String>()
    fun clear() = lines.clear()
    fun add(elapsedMs: Long, code: String, detail: String) {
        lines.addLast("+${elapsedMs / 1000}s [$code] $detail")
        while (lines.size > capacity) lines.removeFirst()
    }
    fun render() = lines.joinToString("\n")
}
