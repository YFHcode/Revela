package com.revela.core.model

/**
 * Write-side of the raw event log, abstracted away from Room so collectors
 * are unit-testable on the JVM.
 *
 * Cursors (per-collector watermarks) are persisted in the same transaction as
 * the events they cover, so a crash between "insert" and "advance" can never
 * cause gaps or duplicates.
 */
interface EventLog {

    /** Atomically append [events] and advance the [cursorKey] watermark to [cursorValue]. */
    suspend fun appendAndAdvance(events: List<RawEvent>, cursorKey: String, cursorValue: Long)

    /** Last persisted watermark for [cursorKey], or null if this collector has never run. */
    suspend fun cursor(cursorKey: String): Long?
}
