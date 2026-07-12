package com.revela.capture

import com.revela.core.model.EventLog

/**
 * Cursor-based incremental collector over the system usage-event buffer.
 *
 * Each run reads events in `(cursor, now - SAFETY_LAG]`, appends them to the
 * raw log, and advances the cursor — all in one transaction (via [EventLog]),
 * so restarts can never produce gaps or duplicates. The safety lag leaves a
 * small margin for events the OS timestamps just before "now" but hasn't
 * flushed to the query buffer yet.
 */
class UsageStatsCollector(
    private val source: UsageEventSource,
    private val eventLog: EventLog,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun collectOnce() {
        val now = clock()
        val endTs = now - SAFETY_LAG_MS
        val cursor = eventLog.cursor(CURSOR_KEY) ?: (endTs - INITIAL_LOOKBACK_MS)
        if (endTs <= cursor) return

        val events = source.eventsBetween(cursor + 1, endTs)
        eventLog.appendAndAdvance(events, CURSOR_KEY, endTs)
    }

    companion object {
        const val CURSOR_KEY = "usage_stats.cursor"

        /** Margin for events not yet flushed into the queryable buffer. */
        const val SAFETY_LAG_MS = 10_000L

        /** On first run, backfill what the OS still has (it keeps a few days). */
        const val INITIAL_LOOKBACK_MS = 3 * 24 * 60 * 60 * 1000L
    }
}
