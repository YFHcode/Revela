package com.revela.pipeline

import com.revela.core.model.DayKeys
import java.time.ZoneId

/**
 * Turns place-assigned location fixes into per-day visits/dwell. Pure — the
 * clustering and stable place-id assignment happen upstream (runner); here we
 * only reconstruct visits and dwell. A run of same-place fixes is one visit
 * until the place changes or a gap exceeds [maxGapMs].
 */
object PlaceRollup {

    data class PlacedFix(val ts: Long, val placeId: Long)

    data class PlaceDay(
        val date: String,
        val placeId: Long,
        val arrivalTs: Long,
        val departTs: Long,
        val dwellSeconds: Int,
    )

    fun build(
        fixes: List<PlacedFix>,
        zone: ZoneId,
        maxGapMs: Long = 30 * 60 * 1000L,
    ): List<PlaceDay> {
        if (fixes.isEmpty()) return emptyList()
        val sorted = fixes.sortedBy { it.ts }

        // Aggregate per (behavioral day, place): min arrival, max depart, dwell
        // summed across visits (a visit ends on place change or a long gap).
        data class Key(val date: String, val placeId: Long)
        data class Agg(var arrival: Long, var depart: Long, var dwell: Long)

        val agg = HashMap<Key, Agg>()
        var visitPlace = sorted.first().placeId
        var visitStart = sorted.first().ts
        var prevTs = sorted.first().ts

        fun closeVisit(endTs: Long) {
            val key = Key(DayKeys.dayKey(visitStart, zone), visitPlace)
            val a = agg.getOrPut(key) { Agg(visitStart, endTs, 0) }
            a.arrival = minOf(a.arrival, visitStart)
            a.depart = maxOf(a.depart, endTs)
            a.dwell += (endTs - visitStart)
        }

        for (i in 1 until sorted.size) {
            val fix = sorted[i]
            val brokeVisit = fix.placeId != visitPlace || fix.ts - prevTs > maxGapMs
            if (brokeVisit) {
                closeVisit(prevTs)
                visitPlace = fix.placeId
                visitStart = fix.ts
            }
            prevTs = fix.ts
        }
        closeVisit(prevTs)

        return agg.map { (key, a) ->
            PlaceDay(key.date, key.placeId, a.arrival, a.depart, (a.dwell / 1000).toInt())
        }.sortedWith(compareBy({ it.date }, { it.placeId }))
    }
}
