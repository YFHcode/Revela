package com.revela.pipeline

import com.revela.core.model.DayKeys
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId

/**
 * Folds NOTIFICATION events (contact-resolved) into comms_daily: per
 * (behavioral day, contact, app) message count + a 24-slot local-hour
 * histogram. Pure — no DB, no Android.
 */
object CommsRollup {

    data class NotifRow(val ts: Long, val appPkg: String, val contactId: Long)

    data class CommsDay(
        val date: String,
        val contactId: Long,
        val appPkg: String,
        val msgCount: Int,
        val byHourJson: String,
    )

    fun build(rows: List<NotifRow>, zone: ZoneId): List<CommsDay> {
        data class Key(val date: String, val contactId: Long, val appPkg: String)

        val counts = HashMap<Key, Int>()
        val histograms = HashMap<Key, IntArray>()

        for (row in rows) {
            val date = DayKeys.dayKey(row.ts, zone)
            val key = Key(date, row.contactId, row.appPkg)
            counts.merge(key, 1, Int::plus)
            val hist = histograms.getOrPut(key) { IntArray(24) }
            val hour = Instant.ofEpochMilli(row.ts).atZone(zone).hour
            hist[hour]++
        }

        return counts.map { (key, count) ->
            val arr = JSONArray()
            histograms[key]!!.forEach { arr.put(it) }
            CommsDay(key.date, key.contactId, key.appPkg, count, arr.toString())
        }.sortedWith(compareBy({ it.date }, { it.contactId }, { it.appPkg }))
    }
}
