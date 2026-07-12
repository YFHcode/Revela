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
