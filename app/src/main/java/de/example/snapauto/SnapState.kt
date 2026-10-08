package de.example.snapauto

object SnapState {
    @Volatile var pendingSender: String = ""
    @Volatile var pendingUntil: Long = 0
    @Volatile var batchMode: Boolean = false
    @Volatile var batchStopRequested: Boolean = false
    @Volatile var openedInBatch: Int = 0
}