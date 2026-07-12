package com.revela.pipeline

import com.revela.analysis.GeoFix
import com.revela.core.db.CommsDailyEntity
import com.revela.core.db.PlaceDailyEntity
import com.revela.core.db.RevelaDatabase
import com.revela.core.db.RollupWriter
import com.revela.core.model.EventType
import com.revela.core.model.RawEvent
import com.revela.core.db.toRaw
import org.json.JSONObject
import java.time.ZoneId

/**
 * Runs one rollup pass: raw log → processors → rollup tables (usage, comms,
 * places).
 *
 * Recomputes everything from the full raw log each run — maximally idempotent,
 * and trivial at single-user scale. Switch to per-date incremental recompute
 * behind a watermark when the log grows enough to matter.
 */
class RollupRunner(
    private val db: RevelaDatabase,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    suspend fun runOnce() {
        val events = db.eventDao().allOrdered().map { it.toRaw() }
        if (events.isEmpty()) return
        val zoneId = zone()

        val result = RollupProcessor(zoneId).process(events)
        RollupWriter(db).replaceAll(result)

        runPhase2(events, zoneId)
    }

    private suspend fun runPhase2(events: List<RawEvent>, zoneId: ZoneId) {
        val notifRows = events
            .filter { it.type == EventType.NOTIFICATION && it.entityId != null && it.appPkg != null }
            .map { CommsRollup.NotifRow(it.ts, it.appPkg!!, it.entityId!!) }
        val comms = CommsRollup.build(notifRows, zoneId).map {
            CommsDailyEntity(it.date, it.contactId, it.appPkg, it.msgCount, it.byHourJson)
        }

        val fixes = events.filter { it.type == EventType.LOCATION_FIX }.mapNotNull { event ->
            event.payload?.let { runCatching { JSONObject(it) }.getOrNull() }?.let { json ->
                GeoFix(json.optDouble("lat"), json.optDouble("lon"), event.ts)
            }
        }
        val placed = PlaceResolver(db).assignFixes(fixes, zoneId)
        val places = PlaceRollup.build(placed, zoneId).map {
            PlaceDailyEntity(it.date, it.placeId, it.arrivalTs, it.departTs, it.dwellSeconds)
        }

        if (comms.isNotEmpty() || places.isNotEmpty()) {
            RollupWriter(db).replacePhase2(comms, places)
        }
    }
}
