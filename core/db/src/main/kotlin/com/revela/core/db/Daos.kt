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

    @Query("SELECT * FROM sessions ORDER BY start_ts")
    suspend fun allOrdered(): List<SessionEntity>
}

@Dao
interface UsageHourlyDao {
    @Insert
    suspend fun insertAll(rows: List<UsageHourlyEntity>)

    @Query("DELETE FROM usage_hourly")
    suspend fun clear()

    @Query("SELECT * FROM usage_hourly WHERE date >= :minDate")
    fun since(minDate: String): Flow<List<UsageHourlyEntity>>

    @Query("SELECT * FROM usage_hourly ORDER BY date, hour")
    suspend fun all(): List<UsageHourlyEntity>
}

@Dao
interface UsageDailyDao {
    @Insert
    suspend fun insertAll(rows: List<UsageDailyEntity>)

    @Query("DELETE FROM usage_daily")
    suspend fun clear()

    @Query("SELECT * FROM usage_daily WHERE date = :date ORDER BY total_seconds DESC LIMIT :limit")
    fun topForDate(date: String, limit: Int): Flow<List<UsageDailyEntity>>

    @Query("SELECT * FROM usage_daily ORDER BY date")
    suspend fun all(): List<UsageDailyEntity>
}

@Dao
interface DaySummaryDao {
    @Insert
    suspend fun insertAll(rows: List<DaySummaryEntity>)

    @Query("DELETE FROM day_summary")
    suspend fun clear()

    @Query("SELECT * FROM day_summary ORDER BY date DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<DaySummaryEntity>>

    @Query("SELECT * FROM day_summary ORDER BY date")
    suspend fun all(): List<DaySummaryEntity>
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

    @Query("SELECT * FROM entities WHERE kind = :kind ORDER BY display_name")
    fun byKindFlow(kind: EntityKind): Flow<List<TrackedEntity>>

    @Query("SELECT * FROM entities WHERE id = :id")
    suspend fun byId(id: Long): TrackedEntity?

    @Query("UPDATE entities SET display_name = :name, place_label = :label WHERE id = :id")
    suspend fun relabelPlace(id: Long, name: String, label: String)

    /** Refresh a place's centroid without touching its (possibly user-set) name/label. */
    @Query("UPDATE entities SET place_lat = :lat, place_lon = :lon WHERE id = :id")
    suspend fun updatePlaceCentroid(id: Long, lat: Double, lon: Double)

    /** Full removal of one entity's history (D6): raw events, rollups, insights. */
    @Query("DELETE FROM events WHERE entity_id = :id")
    suspend fun deleteEvents(id: Long)

    @Query("DELETE FROM comms_daily WHERE contact_id = :id")
    suspend fun deleteComms(id: Long)

    @Query("DELETE FROM place_daily WHERE place_id = :id")
    suspend fun deletePlaceDaily(id: Long)

    /** entity_ids uses a ",id,id," sentinel form so LIKE matches whole ids only. */
    @Query("DELETE FROM insights WHERE entity_ids LIKE '%,' || :id || ',%'")
    suspend fun deleteInsightsFor(id: Long)

    @Query("DELETE FROM modes WHERE member_ids LIKE '%,' || :id || ',%'")
    suspend fun deleteModesFor(id: Long)

    @Query("DELETE FROM entities WHERE id = :id")
    suspend fun deleteEntity(id: Long)
}

@Dao
interface CommsDailyDao {
    @Insert
    suspend fun insertAll(rows: List<CommsDailyEntity>)

    @Query("DELETE FROM comms_daily")
    suspend fun clear()

    @Query("SELECT * FROM comms_daily ORDER BY date")
    suspend fun all(): List<CommsDailyEntity>
}

@Dao
interface PlaceDailyDao {
    @Insert
    suspend fun insertAll(rows: List<PlaceDailyEntity>)

    @Query("DELETE FROM place_daily")
    suspend fun clear()

    @Query("SELECT * FROM place_daily ORDER BY date")
    suspend fun all(): List<PlaceDailyEntity>
}

@Dao
interface ModeDao {
    @Insert
    suspend fun insert(mode: ModeEntity): Long

    @Query("SELECT * FROM modes WHERE dedupe_key = :key LIMIT 1")
    suspend fun byDedupeKey(key: String): ModeEntity?

    @Query("UPDATE modes SET llm_name = :name, llm_desc = :desc WHERE id = :id")
    suspend fun setLlm(id: Long, name: String, desc: String)

    @Query("SELECT * FROM modes ORDER BY strength DESC")
    fun all(): Flow<List<ModeEntity>>

    @Query("SELECT * FROM modes WHERE llm_name IS NULL ORDER BY strength DESC LIMIT :limit")
    suspend fun needingNaming(limit: Int): List<ModeEntity>

    /** Remove modes whose member set is no longer detected (keeps the table current). */
    @Query("DELETE FROM modes WHERE dedupe_key NOT IN (:keepKeys)")
    suspend fun deleteNotIn(keepKeys: List<String>)

    @Query("DELETE FROM modes")
    suspend fun clear()
}

@Dao
interface InsightDao {
    @Insert
    suspend fun insert(insight: InsightEntity): Long

    @Query("SELECT * FROM insights WHERE dedupe_key = :key LIMIT 1")
    suspend fun byDedupeKey(key: String): InsightEntity?

    /**
     * Refresh numbers/text of an existing insight, preserving created_ts and
     * pin/dismiss state. Clears llm_text: the narration referenced old numbers.
     */
    @Query(
        "UPDATE insights SET stat_payload = :payload, text = :text, confidence = :confidence, " +
            "window_start = :windowStart, window_end = :windowEnd, llm_text = NULL WHERE id = :id",
    )
    suspend fun refresh(
        id: Long,
        payload: String,
        text: String,
        confidence: Double,
        windowStart: Long,
        windowEnd: Long,
    )

    @Query(
        "SELECT * FROM insights WHERE dismissed = 0 AND llm_text IS NULL " +
            "AND type NOT IN (:excludedTypes) ORDER BY created_ts DESC LIMIT :limit",
    )
    suspend fun needingNarration(excludedTypes: List<String>, limit: Int): List<InsightEntity>

    @Query("UPDATE insights SET llm_text = :llmText WHERE id = :id")
    suspend fun setLlmText(id: Long, llmText: String)

    @Query("SELECT * FROM insights WHERE dismissed = 0 ORDER BY pinned DESC, created_ts DESC")
    fun feed(): Flow<List<InsightEntity>>

    @Query("UPDATE insights SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("UPDATE insights SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)
}

@Dao
interface LlmAuditDao {
    @Insert
    suspend fun insert(row: LlmAuditEntity)

    @Query("SELECT * FROM llm_audit ORDER BY ts DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<LlmAuditEntity>>

    @Query("DELETE FROM llm_audit")
    suspend fun clear()
}
