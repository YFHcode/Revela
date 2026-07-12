package com.revela.insights

import com.revela.analysis.AppDayRow
import com.revela.analysis.Bursts
import com.revela.analysis.CrossStreamEngine
import com.revela.analysis.CrossStreamSeries
import com.revela.analysis.CommsEngine
import com.revela.analysis.ContactDay
import com.revela.analysis.ContactRef
import com.revela.analysis.DayRow
import com.revela.analysis.DefaultCandidatePairs
import com.revela.analysis.EarlyEngine
import com.revela.analysis.InsightEngine
import com.revela.analysis.InsightTypes
import com.revela.analysis.PlaceDay
import com.revela.analysis.PlaceEngine
import com.revela.analysis.PlaceRef
import com.revela.analysis.RoutineEngine
import com.revela.analysis.SeriesBuilder
import com.revela.core.db.DaySummaryEntity
import com.revela.core.db.EntityKind
import com.revela.core.db.RevelaDatabase
import com.revela.core.model.DayKeys
import com.revela.insights.llm.OpenAiNarrator
import org.json.JSONArray
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

        // Phase 2 — communication timing, relationship drift, place rhythms.
        val phase2Drafts = phase2Drafts(todayKey, zoneId, now)

        InsightWriter(db).upsertAll(
            baseDrafts + earlyDrafts + crossDrafts + routineDrafts + phase2Drafts,
            now,
        )

        // L1 narration: rewrite a few un-narrated insights per pass. Failures
        // (no key, offline, validation reject) simply leave the template.
        // Name-bearing insight types (contacts/places) are NEVER sent to the
        // cloud (D4) — they keep their local template wording.
        narrator?.let { n ->
            val candidates = db.insightDao()
                .needingNarration(NAME_BEARING_TYPES.toList(), NARRATION_BATCH)
            for (insight in candidates) {
                n.narrate(insight.type, insight.statPayload, insight.text)
                    ?.let { db.insightDao().setLlmText(insight.id, it) }
            }
        }
    }

    private companion object {
        const val NARRATION_BATCH = 5
        val NAME_BEARING_TYPES = setOf(
            InsightTypes.COMMS_TIMING,
            InsightTypes.RELATIONSHIP_DRIFT,
            InsightTypes.PLACE_RHYTHM,
        )
    }

    private suspend fun phase2Drafts(
        todayKey: String,
        zoneId: ZoneId,
        now: Long,
    ): List<com.revela.analysis.InsightDraft> {
        val contacts = db.trackedEntityDao().byKind(EntityKind.CONTACT)
            .associate { it.id to ContactRef(it.id, it.displayName) }
        val places = db.trackedEntityDao().byKind(EntityKind.PLACE).associate {
            it.id to PlaceRef(
                placeId = it.id,
                label = it.displayName,
                isHomeOrWork = it.placeLabel == "HOME" || it.placeLabel == "WORK",
            )
        }

        val drafts = mutableListOf<com.revela.analysis.InsightDraft>()

        if (contacts.isNotEmpty()) {
            val contactDays = db.commsDailyDao().all().map { row ->
                ContactDay(
                    dayKey = row.date,
                    contactId = row.contactId,
                    msgCount = row.msgNotifCount,
                    byHour = parseHistogram(row.byHourHistogram),
                )
            }
            drafts += CommsEngine().generate(contactDays, contacts, todayKey, now)
        }

        if (places.isNotEmpty()) {
            val placeDays = db.placeDailyDao().all().map {
                PlaceDay(it.date, it.placeId, it.dwellSeconds)
            }
            drafts += PlaceEngine().generate(placeDays, places, todayKey, now)
        }
        return drafts
    }

    private fun parseHistogram(json: String): IntArray {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return IntArray(24)
        return IntArray(24) { if (it < arr.length()) arr.optInt(it) else 0 }
    }

    private fun minuteOfDay(day: DaySummaryEntity, zoneId: ZoneId): Double? =
        day.firstUnlock?.let { ts ->
            val t = Instant.ofEpochMilli(ts).atZone(zoneId)
            (t.hour * 60 + t.minute).toDouble()
        }
}
