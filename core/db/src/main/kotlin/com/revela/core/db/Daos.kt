package com.revela.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insertAll(events: List<EventEntity>)

    @Query("SELECT * FROM events ORDER BY ts DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM events")
    fun count(): Flow<Long>

    @Query("SELECT MIN(ts) FROM events")
    fun oldestTs(): Flow<Long?>

    @Query("SELECT * FROM events ORDER BY ts")
    suspend fun allOrdered(): List<EventEntity>
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions")
    suspend fun clear()
}

@Dao
interface UsageHourlyDao {
    @Insert
    suspend fun insertAll(rows: List<UsageHourlyEntity>)

    @Query("DELETE FROM usage_hourly")
    suspend fun clear()

    @Query("SELECT * FROM usage_hourly WHERE date >= :minDate")
    fun since(minDate: String): Flow<List<UsageHourlyEntity>>
}

@Dao
interface UsageDailyDao {
    @Insert
    suspend fun insertAll(rows: List<UsageDailyEntity>)

    @Query("DELETE FROM usage_daily")
    suspend fun clear()

    @Query("SELECT * FROM usage_daily WHERE date = :date ORDER BY total_seconds DESC LIMIT :limit")
    fun topForDate(date: String, limit: Int): Flow<List<UsageDailyEntity>>
}

@Dao
interface DaySummaryDao {
    @Insert
    suspend fun insertAll(rows: List<DaySummaryEntity>)

    @Query("DELETE FROM day_summary")
    suspend fun clear()

    @Query("SELECT * FROM day_summary ORDER BY date DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<DaySummaryEntity>>
}

@Dao
interface WatermarkDao {
    @Upsert
    suspend fun upsert(watermark: WatermarkEntity)

    @Query("SELECT value FROM watermarks WHERE `key` = :key")
    suspend fun value(key: String): Long?
}

@Dao
interface TrackedEntityDao {
    @Upsert
    suspend fun upsert(entity: TrackedEntity): Long

    @Query("SELECT * FROM entities WHERE kind = :kind")
    suspend fun byKind(kind: EntityKind): List<TrackedEntity>
}

@Dao
interface InsightDao {
    @Upsert
    suspend fun upsert(insight: InsightEntity)

    @Query("SELECT * FROM insights WHERE dismissed = 0 ORDER BY pinned DESC, created_ts DESC")
    fun feed(): Flow<List<InsightEntity>>

    @Query("UPDATE insights SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("UPDATE insights SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)
}
