package com.revela.insights

import com.revela.analysis.AppDayRow
import com.revela.analysis.DayRow
import com.revela.analysis.InsightEngine
import com.revela.analysis.SeriesBuilder
import com.revela.core.db.RevelaDatabase
import com.revela.core.model.DayKeys
import java.time.Instant
import java.time.ZoneId

/**
 * Stage 3 of the pipeline (§7): reads rollup summaries, runs the insight
 * engine (statistics only), and upserts the resulting insights. Never touches
 * the raw event log — by the time data reaches this stage it is aggregate.
 */
class AnalysisRunner(
    private val db: RevelaDatabase,
    private val appLabel: (String) -> String,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    suspend fun runOnce(now: Long = System.currentTimeMillis()) {
        val summaries = db.daySummaryDao().all()
        if (summaries.isEmpty()) return
        val zoneId = zone()

        val series = SeriesBuilder.build(
            days = summaries.map {
                DayRow(it.date, it.totalScreenTimeS, it.pickupCount, it.reflexCheckCount)
            },
            appDays = db.usageDailyDao().all().map {
                AppDayRow(it.date, it.appPkg, it.totalSeconds)
            },
            appLabel = appLabel,
        )

        val firstUnlockMinutes = summaries.mapNotNull { day ->
            day.firstUnlock?.let { ts ->
                val t = Instant.ofEpochMilli(ts).atZone(zoneId)
                DayKeys.dayType(day.date) to (t.hour * 60 + t.minute).toDouble()
            }
        }

        val drafts = InsightEngine().generate(
            series = series,
            firstUnlockMinutes = firstUnlockMinutes,
            todayKey = DayKeys.dayKey(now, zoneId),
            now = now,
        )
        InsightWriter(db).upsertAll(drafts, now)
    }
}
