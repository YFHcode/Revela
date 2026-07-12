package com.revela.core.db

import androidx.room.withTransaction

/**
 * Full removal of one contact/place and everything derived from it (D6):
 * raw events, comms/place rollups, insights that mention it, and the entity
 * row — all in one transaction. Lives in :core:db so the room-ktx
 * `withTransaction` extension stays encapsulated here.
 */
class EntityEraser(private val db: RevelaDatabase) {

    suspend fun erase(id: Long) {
        db.withTransaction {
            val dao = db.trackedEntityDao()
            dao.deleteEvents(id)
            dao.deleteComms(id)
            dao.deletePlaceDaily(id)
            dao.deleteInsightsFor(id)
            dao.deleteEntity(id)
        }
    }
}
