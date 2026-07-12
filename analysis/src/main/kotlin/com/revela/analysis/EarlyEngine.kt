package com.revela.analysis

import com.revela.core.model.DayType
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Early insights — value from day 1. Where the statistical detectors need
 * weeks of baseline before they can be trusted, these are honest immediately
 * because they claim only what one or a few days can support: a snapshot of
 * the emerging baseline, the leading apps so far, and a first read on when
 * the day starts. Each is a single UPSERTed card ("baseline", not "news")
 * whose text carries its own sample size and sharpens as days accumulate.
 */
class EarlyEngine {

    fun generate(
        series: List<BehaviorSeries>,
        firstUnlockMinutes: List<Pair<DayType, Double>>,
        todayKey: String,
        now: Long,
    ): List<InsightDraft> {
        val screen = series.firstOrNull { it.subjectKey == "screen_time" } ?: return emptyList()
        val history = screen.points.filter { it.dayKey < todayKey }
        val days = history.size
        if (days < 1) return emptyList()

        val drafts = mutableListOf<InsightDraft>()
        drafts += snapshotDraft(series, todayKey, days, now)
        topAppsDraft(series, todayKey, days, now)?.let { drafts += it }
        chronotypeDraft(firstUnlockMinutes, now)?.let { drafts += it }
        return drafts
    }

    private fun snapshotDraft(
        series: List<BehaviorSeries>,
        todayKey: String,
        days: Int,
        now: Long,
    ): InsightDraft {
        val screenMed = medianOf(series, "screen_time", todayKey)
        val pickupsMed = medianOf(series, "pickups", todayKey)
        val reflexMed = medianOf(series, "reflex_checks", todayKey)

        val screenText = InsightFormat.value(screenMed, SeriesUnit.SECONDS)
        val text = if (days == 1) {
            "First full day observed: $screenText of screen time across " +
                "${pickupsMed.roundToInt()} pickups — ${reflexMed.roundToInt()} of them " +
                "under 15 seconds. From here, every day sharpens the picture."
        } else {
            "$days days in: a typical day is about $screenText of screen time and " +
                "${pickupsMed.roundToInt()} pickups, ${reflexMed.roundToInt()} of them " +
                "quick checks. These numbers firm up as the baseline grows."
        }
        return InsightDraft(
            type = InsightTypes.BASELINE_SNAPSHOT,
            dedupeKey = "baseline",
            confidence = min(0.2 + days * 0.02, 0.6),
            windowStart = now - days * DAY_MS,
            windowEnd = now,
            statPayload = """{"days_observed":$days,"screen_median_s":${screenMed.r2()},""" +
                """"pickups_median":${pickupsMed.r2()},"reflex_median":${reflexMed.r2()}}""",
            text = text,
        )
    }

    private fun topAppsDraft(
        series: List<BehaviorSeries>,
        todayKey: String,
        days: Int,
        now: Long,
    ): InsightDraft? {
        if (days < 2) return null
        val apps = series.filter { it.subjectKey.startsWith("app:") }
            .map { s ->
                val history = s.points.filter { it.dayKey < todayKey }
                Triple(s.subjectKey, s.label, history.sumOf { it.value } / days)
            }
            .filter { it.third >= 60 } // at least a minute a day
            .sortedByDescending { it.third }
            .take(3)
        if (apps.isEmpty()) return null

        val listing = apps.joinToString(", ") { (_, label, avg) ->
            "$label (~${InsightFormat.value(avg, SeriesUnit.SECONDS)}/day)"
        }
        return InsightDraft(
            type = InsightTypes.EARLY_TOP_APPS,
            dedupeKey = "early_top_apps",
            confidence = min(0.2 + days * 0.02, 0.6),
            windowStart = now - days * DAY_MS,
            windowEnd = now,
            statPayload = """{"days_observed":$days,"apps":[""" +
                apps.joinToString(",") { (key, _, avg) ->
                    """{"subject":"$key","avg_seconds_per_day":${avg.r2()}}"""
                } + "]}",
            text = "Where the time goes so far: $listing — averaged over $days days.",
        )
    }

    private fun chronotypeDraft(
        firstUnlockMinutes: List<Pair<DayType, Double>>,
        now: Long,
    ): InsightDraft? {
        val minutes = firstUnlockMinutes.map { it.second }
        if (minutes.size < 2) return null
        val median = minutes.sorted()[minutes.size / 2]
        return InsightDraft(
            type = InsightTypes.EARLY_CHRONOTYPE,
            dedupeKey = "early_chronotype",
            confidence = min(0.2 + minutes.size * 0.03, 0.6),
            windowStart = now - minutes.size * DAY_MS,
            windowEnd = now,
            statPayload = """{"days_observed":${minutes.size},""" +
                """"first_unlock_median_min":${median.r2()}}""",
            text = "Your day tends to start around ${InsightFormat.clock(median)}, " +
                "going by the first pickup — a reading from ${minutes.size} mornings " +
                "that will split into weekday and weekend patterns with more data.",
        )
    }

    private fun medianOf(series: List<BehaviorSeries>, key: String, todayKey: String): Double {
        val values = series.firstOrNull { it.subjectKey == key }
            ?.points?.filter { it.dayKey < todayKey }?.map { it.value }
            ?: return 0.0
        if (values.isEmpty()) return 0.0
        return values.sorted()[values.size / 2]
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
