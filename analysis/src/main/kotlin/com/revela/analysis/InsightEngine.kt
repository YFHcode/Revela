package com.revela.analysis

import com.revela.core.model.DayKeys
import com.revela.core.model.DayType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/** A generated insight, ready to persist. Text is templated (M3) — the LLM
 *  narrator (M5) will offer an alternative rendering of the same payload. */
data class InsightDraft(
    val type: String,
    /** Stable identity for UPSERT so the feed never repeats itself. */
    val dedupeKey: String,
    val confidence: Double,
    val windowStart: Long,
    val windowEnd: Long,
    /** The numbers behind the insight, as JSON. */
    val statPayload: String,
    /** Neutral, curious phrasing (P1). No judgment, no goals, no "wasted". */
    val text: String,
    /** JSON array of entity ids this insight is about, so entity deletion cascades. */
    val entityIds: String? = null,
)

object InsightTypes {
    const val PERIODIC_RHYTHM = "periodic_rhythm"
    const val HABIT_SHIFT = "habit_shift"
    const val UNUSUAL_DAY = "unusual_day"
    const val CHRONOTYPE = "chronotype"
    const val REFLEX_CHECKS = "reflex_checks"
    const val FEEDBACK_LOOP = "feedback_loop"
    const val ROUTINE = "routine"

    // Early insights — available from day 1, refreshed daily, sharpening with data.
    const val BASELINE_SNAPSHOT = "baseline_snapshot"
    const val EARLY_TOP_APPS = "early_top_apps"
    const val EARLY_CHRONOTYPE = "early_chronotype"

    // Phase 2 — communication timing, places.
    const val COMMS_TIMING = "comms_timing"
    const val RELATIONSHIP_DRIFT = "relationship_drift"
    const val PLACE_RHYTHM = "place_rhythm"
}

/**
 * Runs the detectors over the behavior series and drafts insights (§9).
 * Pure JVM — the acceptance tests plant patterns and assert exactly these
 * fire, and that shuffled controls stay silent.
 */
class InsightEngine {

    fun generate(
        series: List<BehaviorSeries>,
        firstUnlockMinutes: List<Pair<DayType, Double>>,
        todayKey: String,
        now: Long,
    ): List<InsightDraft> {
        val drafts = mutableListOf<InsightDraft>()

        for (s in series) {
            // Exclude the (partial) current day from baseline statistics.
            val history = s.points.filter { it.dayKey < todayKey }
            val historyValues = history.map { it.value }.toDoubleArray()

            Periodicity.detect(historyValues)?.let { p ->
                drafts += periodicDraft(s, p, history, now)
            }
            ChangePoint.detect(historyValues)?.let { c ->
                drafts += shiftDraft(s, c, history, now)
            }
            // "Today is unusual": only overall behaviors, and only HIGH — a
            // partial day can't honestly be called unusually low.
            if (s.subjectKey == "screen_time" || s.subjectKey == "pickups") {
                val today = s.points.lastOrNull()?.takeIf { it.dayKey == todayKey }
                if (today != null) {
                    val todayType = DayKeys.dayType(todayKey)
                    val sameType = history
                        .filter { DayKeys.dayType(it.dayKey) == todayType }
                        .map { it.value }
                        .toDoubleArray()
                    Deviation.detect(sameType, today.value)
                        ?.takeIf { it.direction == Deviation.Direction.HIGH }
                        ?.let { d -> drafts += unusualDraft(s, d, todayKey, now) }
                }
            }
        }

        reflexDraft(series, todayKey, now)?.let { drafts += it }
        chronotypeDraft(firstUnlockMinutes, now)?.let { drafts += it }

        return drafts
    }

    private fun periodicDraft(
        s: BehaviorSeries,
        p: Periodicity.Result,
        history: List<DayPoint>,
        now: Long,
    ) = InsightDraft(
        type = InsightTypes.PERIODIC_RHYTHM,
        dedupeKey = "periodic:${s.subjectKey}",
        confidence = p.strength.coerceIn(0.0, 1.0),
        windowStart = now - history.size * DAY_MS,
        windowEnd = now,
        statPayload = """{"subject":"${s.subjectKey}","period_days":${p.periodDays},""" +
            """"strength":${p.strength.r2()},"cycles":${p.cycles},"days_observed":${history.size}}""",
        text = "Your ${s.label} seems to rise and fall on a rhythm of about " +
            "${p.periodDays} days — it shows up across ${p.cycles} cycles of the " +
            "last ${history.size} days.",
    )

    private fun shiftDraft(
        s: BehaviorSeries,
        c: ChangePoint.Result,
        history: List<DayPoint>,
        now: Long,
    ): InsightDraft {
        val shiftDay = history[c.index].dayKey
        val before = fmt(c.beforeMean, s.unit)
        val after = fmt(c.afterMean, s.unit)
        return InsightDraft(
            type = InsightTypes.HABIT_SHIFT,
            dedupeKey = "shift:${s.subjectKey}:$shiftDay",
            confidence = (c.effectSize / 4.0).coerceIn(0.0, 1.0),
            windowStart = now - history.size * DAY_MS,
            windowEnd = now,
            statPayload = """{"subject":"${s.subjectKey}","shift_day":"$shiftDay",""" +
                """"before_mean":${c.beforeMean.r2()},"after_mean":${c.afterMean.r2()},""" +
                """"effect_size":${c.effectSize.r2()}}""",
            text = "Around ${prettyDate(shiftDay)}, your daily ${s.label} settled at a " +
                "different level: roughly $after a day, compared with about $before before that.",
        )
    }

    private fun unusualDraft(
        s: BehaviorSeries,
        d: Deviation.Result,
        todayKey: String,
        now: Long,
    ) = InsightDraft(
        type = InsightTypes.UNUSUAL_DAY,
        dedupeKey = "unusual:${s.subjectKey}:$todayKey",
        confidence = (abs(d.robustZ) / 4.0).coerceIn(0.0, 1.0),
        windowStart = now - DAY_MS,
        windowEnd = now,
        statPayload = """{"subject":"${s.subjectKey}","day":"$todayKey",""" +
            """"value":${d.value.r2()},"median":${d.median.r2()},"robust_z":${d.robustZ.r2()}}""",
        text = "Today's ${s.label} (${fmt(d.value, s.unit)}) is already well above " +
            "what's usual for you on this kind of day (typically around " +
            "${fmt(d.median, s.unit)}).",
    )

    private fun reflexDraft(series: List<BehaviorSeries>, todayKey: String, now: Long): InsightDraft? {
        val reflex = series.firstOrNull { it.subjectKey == "reflex_checks" } ?: return null
        val history = reflex.points.filter { it.dayKey < todayKey }.map { it.value }
        if (history.size < 4) return null
        val median = history.sorted()[history.size / 2]
        if (median < 5) return null
        return InsightDraft(
            type = InsightTypes.REFLEX_CHECKS,
            dedupeKey = "reflex",
            confidence = 0.9,
            windowStart = now - history.size * DAY_MS,
            windowEnd = now,
            statPayload = """{"median_per_day":${median.r2()},"days_observed":${history.size}}""",
            text = "You pick up your phone for less than 15 seconds about " +
                "${median.roundToInt()} times a day — brief checks that mostly " +
                "don't lead anywhere.",
        )
    }

    private fun chronotypeDraft(
        firstUnlockMinutes: List<Pair<DayType, Double>>,
        now: Long,
    ): InsightDraft? {
        val weekday = firstUnlockMinutes.filter { it.first == DayType.WEEKDAY }.map { it.second }
        val weekend = firstUnlockMinutes.filter { it.first == DayType.WEEKEND }.map { it.second }
        val c = Chronotype.detect(weekday.toDoubleArray(), weekend.toDoubleArray()) ?: return null
        val laterOrEarlier = if (c.shiftMin > 0) "later" else "earlier"
        return InsightDraft(
            type = InsightTypes.CHRONOTYPE,
            dedupeKey = "chronotype",
            confidence = 0.8,
            windowStart = now - firstUnlockMinutes.size * DAY_MS,
            windowEnd = now,
            statPayload = """{"weekday_median_min":${c.weekdayMedianMin.r2()},""" +
                """"weekend_median_min":${c.weekendMedianMin.r2()},"shift_min":${c.shiftMin.r2()}}""",
            text = "On weekends, your day starts around ${clock(c.weekendMedianMin)} — " +
                "about ${abs(c.shiftMin).roundToInt()} minutes $laterOrEarlier than on " +
                "weekdays (${clock(c.weekdayMedianMin)}), going by your first phone pickup.",
        )
    }

    private fun fmt(value: Double, unit: SeriesUnit): String = InsightFormat.value(value, unit)

    private fun clock(minutesOfDay: Double): String = InsightFormat.clock(minutesOfDay)

    private fun prettyDate(dayKey: String): String =
        LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("MMM d", Locale.US))

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

/** Shared human formatting for insight text. */
internal object InsightFormat {

    fun value(value: Double, unit: SeriesUnit): String = when (unit) {
        SeriesUnit.SECONDS -> {
            val total = value.roundToInt()
            val h = total / 3600
            val m = (total % 3600) / 60
            when {
                h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
                m > 0 -> "${m}m"
                else -> "${total}s"
            }
        }
        SeriesUnit.COUNT -> value.roundToInt().toString()
    }

    fun clock(minutesOfDay: Double): String {
        val m = minutesOfDay.roundToInt().coerceIn(0, 24 * 60 - 1)
        return "%02d:%02d".format(Locale.US, m / 60, m % 60)
    }
}

/** Round to 2 decimals; Double.toString always uses '.', keeping JSON valid in any locale. */
internal fun Double.r2(): Double = round(this * 100) / 100
