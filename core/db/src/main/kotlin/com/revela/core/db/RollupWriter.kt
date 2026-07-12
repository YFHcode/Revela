package com.revela.core.db

import androidx.room.withTransaction
import com.revela.core.model.RollupResult

/** Atomically replaces all rollup tables with a freshly computed result. */
class RollupWriter(private val db: RevelaDatabase) {

    suspend fun replaceAll(result: RollupResult) {
        db.withTransaction {
            db.sessionDao().clear()
            db.usageHourlyDao().clear()
            db.usageDailyDao().clear()
            db.daySummaryDao().clear()

            db.sessionDao().insertAll(result.sessions.map { it.toEntity() })
            db.usageHourlyDao().insertAll(result.hourly.map { it.toEntity() })
            db.usageDailyDao().insertAll(result.daily.map { it.toEntity() })
            db.daySummaryDao().insertAll(result.days.map { it.toEntity() })
        }
    }

    /** Replace the Phase-2 rollup tables (comms + place dwell). */
    suspend fun replacePhase2(
        comms: List<CommsDailyEntity>,
        places: List<PlaceDailyEntity>,
    ) {
        db.withTransaction {
            db.commsDailyDao().clear()
            db.placeDailyDao().clear()
            db.commsDailyDao().insertAll(comms)
            db.placeDailyDao().insertAll(places)
        }
    }
}
