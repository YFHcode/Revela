package com.revela.core.db

import androidx.room.withTransaction
import com.revela.core.model.EventLog
import com.revela.core.model.RawEvent

/** Room-backed [EventLog]: events + cursor advance in one transaction. */
class RoomEventLog(private val db: RevelaDatabase) : EventLog {

    override suspend fun appendAndAdvance(
        events: List<RawEvent>,
        cursorKey: String,
        cursorValue: Long,
    ) {
        db.withTransaction {
            if (events.isNotEmpty()) {
                db.eventDao().insertAll(events.map { it.toEntity() })
            }
            db.watermarkDao().upsert(WatermarkEntity(cursorKey, cursorValue))
        }
    }

    override suspend fun cursor(cursorKey: String): Long? =
        db.watermarkDao().value(cursorKey)
}
