package com.revela.insights

import com.revela.analysis.AppDayRow
import com.revela.analysis.Bursts
import com.revela.analysis.CrossStreamEngine
import com.revela.analysis.CrossStreamSeries
import com.revela.analysis.DayRow
import com.revela.analysis.DefaultCandidatePairs
import com.revela.analysis.EarlyEngine
import com.revela.analysis.InsightEngine
import com.revela.analysis.InsightTypes
import com.revela.analysis.RoutineEngine
import com.revela.analysis.SeriesBuilder
import com.revela.core.db.DaySummaryEntity
import com.revela.core.db.RevelaDatabase
import com.revela.core.model.DayKeys
import com.revela.insights.llm.OpenAiNarrator
import java.time.Instant
import java.time.ZoneId

/**
 * Stage 3 of the pipeline (§7): reads rollup summaries, runs the pattern
 * engines (statistics only), and upserts the resulting insights. Never
 * touches the raw event log — by the time data reaches this stage it is
 * aggregate.
 */
class AnalysisRunner(
    private val db: RevelaDatabase,
    private val appLabel: (String) -> String,
    private val narrator: OpenAiNarrator? = null,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    suspend fun runOnce(now: Long = System.currentTimeMillis()) {
        val summaries = db.daySummaryDao().all()
        if (summaries.isEmpty()) return
        val zoneId = zone()
        val todayKey = DayKeys.dayKey(now, zoneId)

        // Per-behavior series: profiles, periodicity, shifts, deviations.
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
            minuteOfDay(day, zoneId)?.let { DayKeys.dayType(day.date) to it }
        }
        val baseDrafts = InsightEngine().generate(series, firstUnlockMinutes, todayKey, now)

        // Early insights from day 1; the tentative chronotype card yields to
        // the full weekday/weekend one as soon as that can fire.
        val earlyDrafts = EarlyEngine().generate(series, firstUnlockMinutes, todayKey, now)
            .filterNot { draft ->
                draft.type == InsightTypes.EARLY_CHRONOTYPE &&
                    baseDrafts.any { it.type == InsightTypes.CHRONOTYPE }
            }

        // §8.9 cross-stream lagged correlation over the curated registry.
        val crossSeries = CrossStreamSeries.build(
            summaries = summaries.map {
                CrossStreamSeries.SummaryRow(
                    dayKey = it.date,
                    screenTimeS = it.totalScreenTimeS,
                    pickups = it.pickupCount,
                    reflexChecks = it.reflexCheckCount,
                    firstUnlockMinuteOfDay = minuteOfDay(it, zoneId),
                )
            },
            hourly = db.usageHourlyDao().all().map {
                CrossStreamSeries.HourRow(it.date, it.hour, it.totalSeconds)
            },
        )
        val crossDrafts = CrossStreamEngine()
            .generate(crossSeries, DefaultCandidatePairs.pairs, todayKey, now)

        // §8.4 routines from app-open bursts.
        val bursts = Bursts.build(
            db.sessionDao().allOrdered().map { Bursts.SessionRow(it.appPkg, it.startTs, it.endTs) },
        )
        val routineDrafts = RoutineEngine().generate(
            daypartSequences = mapOf(
                "morning" to Bursts.morningSequences(bursts, zoneId),
                "evening" to Bursts.eveningSequences(bursts, zoneId),
            ),
            label = appLabel,
            now = now,
        )

        InsightWriter(db).upsertAll(baseDrafts + earlyDrafts + crossDrafts + routineDrafts, now)

        // L1 narration: rewrite a few un-narrated insights per pass. Failures
        // (no key, offline, validation reject) simply leave the template.
        narrator?.let { n ->
            for (insight in db.insightDao().needingNarration(NARRATION_BATCH)) {
                n.narrate(insight.type, insight.statPayload, insight.text)
                    ?.let { db.insightDao().setLlmText(insight.id, it) }
            }
        }
    }

    private companion object {
        const val NARRATION_BATCH = 5
    }

    private fun minuteOfDay(day: DaySummaryEntity, zoneId: ZoneId): Double? =
        day.firstUnlock?.let { ts ->
            val t = Instant.ofEpochMilli(ts).atZone(zoneId)
            (t.hour * 60 + t.minute).toDouble()
        }
}
