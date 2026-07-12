package com.revela.pipeline

import com.revela.core.db.RevelaDatabase
import com.revela.core.db.RollupWriter
import com.revela.core.db.toRaw
import java.time.ZoneId

/**
 * Runs one rollup pass: raw log → RollupProcessor → rollup tables.
 *
 * M2 recomputes everything from the full raw log each run — maximally
 * idempotent, and trivial at single-user scale (a few thousand events/day).
 * Switch to per-date incremental recompute (delete-and-recompute affected
 * dates behind a watermark) when the log grows enough to matter.
 */
class RollupRunner(
    private val db: RevelaDatabase,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    suspend fun runOnce() {
        val events = db.eventDao().allOrdered().map { it.toRaw() }
        if (events.isEmpty()) return
        val result = RollupProcessor(zone()).process(events)
        RollupWriter(db).replaceAll(result)
    }
}
