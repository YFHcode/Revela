package com.revela.analysis

import com.revela.core.model.DayKeys
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max

/** §8.4 — routines via PrefixSpan sequence mining over app-open bursts. */

object SequenceMiner {

    data class Pattern(val items: List<String>, val support: Int)

    /**
     * Classic PrefixSpan for single-item sequences: frequent ordered patterns
     * (not necessarily contiguous) with support >= [minSupport].
     */
    fun mine(
        sequences: List<List<String>>,
        minSupport: Int,
        maxLength: Int = 6,
    ): List<Pattern> {
        val out = mutableListOf<Pattern>()

        fun project(prefix: List<String>, projected: List<List<String>>) {
            if (prefix.size >= maxLength) return
            val counts = HashMap<String, Int>()
            for (seq in projected) {
                for (item in seq.toHashSet()) counts.merge(item, 1, Int::plus)
            }
            for ((item, support) in counts) {
                if (support < minSupport) continue
                val newPrefix = prefix + item
                out += Pattern(newPrefix, support)
                val newProjected = projected.mapNotNull { seq ->
                    val idx = seq.indexOf(item)
                    if (idx < 0) null else seq.subList(idx + 1, seq.size).ifEmpty { null }
                }
                if (newProjected.isNotEmpty()) project(newPrefix, newProjected)
            }
        }

        project(emptyList(), sequences)
        return out
    }
}

/** Groups app sessions into usage bursts and extracts daily open/close sequences. */
object Bursts {

    data class SessionRow(val appPkg: String, val startTs: Long, val endTs: Long)
    data class Burst(val startTs: Long, val apps: List<String>)

    /** Sessions closer than [gapMs] belong to one burst; consecutive repeats deduped. */
    fun build(sessions: List<SessionRow>, gapMs: Long = 60_000L): List<Burst> {
        val sorted = sessions.sortedBy { it.startTs }
        val bursts = mutableListOf<Burst>()
        var apps = mutableListOf<String>()
        var burstStart = 0L
        var lastEnd = Long.MIN_VALUE

        for (session in sorted) {
            if (apps.isEmpty() || session.startTs - lastEnd <= gapMs) {
                if (apps.isEmpty()) burstStart = session.startTs
                if (apps.lastOrNull() != session.appPkg) apps.add(session.appPkg)
            } else {
                bursts += Burst(burstStart, apps)
                apps = mutableListOf(session.appPkg)
                burstStart = session.startTs
            }
            lastEnd = max(lastEnd, session.endTs)
        }
        if (apps.isNotEmpty()) bursts += Burst(burstStart, apps)
        return bursts
    }

    /** The day-opening sequence: first burst starting 04:00–11:59, one per behavioral day. */
    fun morningSequences(bursts: List<Burst>, zone: ZoneId): List<List<String>> =
        bursts.filter { hourOf(it.startTs, zone) in 4..11 }
            .groupBy { DayKeys.dayKey(it.startTs, zone) }
            .map { (_, dayBursts) -> dayBursts.minBy { it.startTs }.apps }

    /** The wind-down sequence: last burst starting 18:00–03:59, one per behavioral day. */
    fun eveningSequences(bursts: List<Burst>, zone: ZoneId): List<List<String>> =
        bursts.filter { hourOf(it.startTs, zone).let { h -> h >= 18 || h < 4 } }
            .groupBy { DayKeys.dayKey(it.startTs, zone) }
            .map { (_, dayBursts) -> dayBursts.maxBy { it.startTs }.apps }

    private fun hourOf(ts: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(ts).atZone(zone).hour
}

/** Turns mined sequences into routine insights (§9 "morning/evening routine"). */
class RoutineEngine(
    private val minDays: Int = 6,
    private val supportFraction: Double = 0.4,
) {

    fun generate(
        daypartSequences: Map<String, List<List<String>>>,
        label: (String) -> String,
        now: Long,
    ): List<InsightDraft> {
        val drafts = mutableListOf<InsightDraft>()
        for ((daypart, sequences) in daypartSequences) {
            if (sequences.size < minDays) continue
            val minSupport = max(4, ceil(sequences.size * supportFraction).toInt())
            val best = SequenceMiner.mine(sequences, minSupport)
                .filter { it.items.size >= 2 }
                .maxWithOrNull(compareBy({ it.items.size }, { it.support }))
                ?: continue

            val chain = best.items.joinToString(" → ") { label(it) }
            val phrase = when (daypart) {
                "morning" -> "Most mornings open the same way"
                "evening" -> "Most evenings wind down the same way"
                else -> "A recurring $daypart sequence"
            }
            drafts += InsightDraft(
                type = InsightTypes.ROUTINE,
                dedupeKey = "routine:$daypart:${best.items.joinToString(">")}",
                confidence = best.support.toDouble() / sequences.size,
                windowStart = now - sequences.size * DAY_MS,
                windowEnd = now,
                statPayload = """{"daypart":"$daypart",""" +
                    """"sequence":[${best.items.joinToString(",") { "\"$it\"" }}],""" +
                    """"days_matched":${best.support},"days_observed":${sequences.size}}""",
                text = "$phrase: $chain — on ${best.support} of the last " +
                    "${sequences.size} ${daypart}s.",
            )
        }
        return drafts
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

/** Builds the extra daily series the cross-stream registry needs (§8.9). */
object CrossStreamSeries {

    data class SummaryRow(
        val dayKey: String,
        val screenTimeS: Int,
        val pickups: Int,
        val reflexChecks: Int,
        val firstUnlockMinuteOfDay: Double?,
    )

    data class HourRow(val date: String, val hour: Int, val totalSeconds: Int)

    /**
     * Series keyed for [DefaultCandidatePairs]. Hourly rows are re-bucketed to
     * behavioral days (hours 0–3 belong to the previous day, matching DayKeys).
     */
    fun build(summaries: List<SummaryRow>, hourly: List<HourRow>): Map<String, List<DayPoint>> {
        val evening = HashMap<String, Double>()
        val night = HashMap<String, Double>()
        for (row in hourly) {
            when {
                row.hour in 18..23 ->
                    evening.merge(row.date, row.totalSeconds.toDouble(), Double::plus)
                row.hour in 0..3 -> {
                    val behavioralDay = LocalDateShift.minusOneDay(row.date)
                    night.merge(behavioralDay, row.totalSeconds.toDouble(), Double::plus)
                }
            }
        }

        val dayKeys = summaries.map { it.dayKey }.sorted()
        return mapOf(
            "screen_time" to summaries.map { DayPoint(it.dayKey, it.screenTimeS.toDouble()) },
            "pickups" to summaries.map { DayPoint(it.dayKey, it.pickups.toDouble()) },
            "reflex_checks" to summaries.map { DayPoint(it.dayKey, it.reflexChecks.toDouble()) },
            "first_unlock_min" to summaries.mapNotNull { s ->
                s.firstUnlockMinuteOfDay?.let { DayPoint(s.dayKey, it) }
            },
            "evening_screen" to dayKeys.map { DayPoint(it, evening[it] ?: 0.0) },
            "night_screen" to dayKeys.map { DayPoint(it, night[it] ?: 0.0) },
        )
    }
}

internal object LocalDateShift {
    fun minusOneDay(dateKey: String): String =
        java.time.LocalDate.parse(dateKey).minusDays(1).toString()
}
