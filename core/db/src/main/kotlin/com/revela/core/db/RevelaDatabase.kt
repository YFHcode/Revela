package com.revela.core.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        EventEntity::class,
        WatermarkEntity::class,
        TrackedEntity::class,
        InsightEntity::class,
        SessionEntity::class,
        UsageHourlyEntity::class,
        UsageDailyEntity::class,
        DaySummaryEntity::class,
        LlmAuditEntity::class,
        CommsDailyEntity::class,
        PlaceDailyEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class RevelaDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun watermarkDao(): WatermarkDao
    abstract fun trackedEntityDao(): TrackedEntityDao
    abstract fun insightDao(): InsightDao
    abstract fun sessionDao(): SessionDao
    abstract fun usageHourlyDao(): UsageHourlyDao
    abstract fun usageDailyDao(): UsageDailyDao
    abstract fun daySummaryDao(): DaySummaryDao
    abstract fun llmAuditDao(): LlmAuditDao
    abstract fun commsDailyDao(): CommsDailyDao
    abstract fun placeDailyDao(): PlaceDailyDao
}
