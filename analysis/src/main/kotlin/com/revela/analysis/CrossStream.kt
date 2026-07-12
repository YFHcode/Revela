package com.revela.analysis

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * §8.9 — cross-stream lagged correlation, the highest-leverage detector.
 *
 * Tests a CURATED registry of (driver, outcome, lag) hypotheses — never
 * all-pairs — using Spearman rank correlation, then applies Benjamini–
 * Hochberg FDR correction across the whole family. Without the correction
 * this engine mass-produces coincidences; with it, a discovery means
 * something.
 */

data class CandidatePair(
    val driverKey: String,
    val driverLabel: String,
    val outcomeKey: String,
    val outcomeLabel: String,
    /** Lags in days: outcome day = driver day + lag. */
    val lags: IntRange,
    val driverIsTimeOfDay: Boolean = false,
    val outcomeIsTimeOfDay: Boolean = false,
)

/** The default hypothesis registry (kept deliberately small — see PLAN.md M4). */
object DefaultCandidatePairs {
    val pairs = listOf(
        CandidatePair(
            "evening_screen", "evening screen time",
            "first_unlock_min", "first pickup", 1..1,
            outcomeIsTimeOfDay = true,
        ),
        CandidatePair(
            "night_screen", "late-night screen time",
            "first_unlock_min", "first pickup", 1..1,
            outcomeIsTimeOfDay = true,
        ),
        CandidatePair(
            "evening_screen", "evening screen time",
            "screen_time", "total screen time", 1..1,
        ),
        CandidatePair(
            "first_unlock_min", "day start",
            "screen_time", "total screen time", 0..0,
            driverIsTimeOfDay = true,
        ),
        CandidatePair(
            "pickups", "pickups",
            "first_unlock_min", "first pickup", 1..1,
            outcomeIsTimeOfDay = true,
        ),
        CandidatePair(
            "reflex_checks", "quick checks",
            "screen_time", "total screen time", 0..0,
        ),
    )
}

class CrossStreamEngine(
    private val fdrQ: Double = 0.1,
    private val minOverlap: Int = 15,
    private val minAbsR: Double = 0.3,
) {

    private data class Hypothesis(
        val pair: CandidatePair,
        val lag: Int,
        val r: Double,
        val n: Int,
        val p: Double,
    )

    fun generate(
        series: Map<String, List<DayPoint>>,
        pairs: List<CandidatePair>,
        todayKey: String,
        now: Long,
    ): List<InsightDraft> {
        val hypotheses = mutableListOf<Hypothesis>()
        for (pair in pairs) {
            val driver = series[pair.driverKey]?.byDay(todayKey) ?: continue
            val outcome = series[pair.outcomeKey]?.byDay(todayKey) ?: continue
            for (lag in pair.lags) {
                val (a, b) = alignWithLag(driver, outcome, lag)
                if (a.size < minOverlap) continue
                val r = Stats.spearman(a.toDoubleArray(), b.toDoubleArray())
                val p = Stats.spearmanPValue(r, a.size)
                hypotheses += Hypothesis(pair, lag, r, a.size, p)
            }
        }

        val significant = benjaminiHochberg(hypotheses)
            .filter { abs(it.r) >= minAbsR }
        // At most one insight per pair: keep the strongest lag.
        val best = significant.groupBy { it.pair }
            .map { (_, hs) -> hs.maxBy { abs(it.r) } }

        return best.map { h -> draft(h, now) }
    }

    private fun List<DayPoint>.byDay(todayKey: String): Map<String, Double> =
        filter { it.dayKey < todayKey }.associate { it.dayKey to it.value }

    private fun alignWithLag(
        driver: Map<String, Double>,
        outcome: Map<String, Double>,
        lag: Int,
    ): Pair<List<Double>, List<Double>> {
        val a = mutableListOf<Double>()
        val b = mutableListOf<Double>()
        for ((dayKey, driverValue) in driver) {
            val outcomeDay = LocalDate.parse(dayKey).plusDays(lag.toLong()).toString()
            val outcomeValue = outcome[outcomeDay] ?: continue
            a += driverValue
            b += outcomeValue
        }
        return a to b
    }

    /** Keep hypotheses surviving BH at [fdrQ]. */
    private fun benjaminiHochberg(hypotheses: List<Hypothesis>): List<Hypothesis> {
        if (hypotheses.isEmpty()) return emptyList()
        val sorted = hypotheses.sortedBy { it.p }
        val m = sorted.size
        var cutoffIndex = -1
        for (k in m downTo 1) {
            if (sorted[k - 1].p <= fdrQ * k / m) {
                cutoffIndex = k
                break
            }
        }
        return if (cutoffIndex < 0) emptyList() else sorted.take(cutoffIndex)
    }

    private fun draft(h: Hypothesis, now: Long): InsightDraft {
        val pair = h.pair
        val positive = h.r > 0

        val driverPhrase = if (pair.driverIsTimeOfDay) {
            "Days that start later"
        } else {
            "Days with more ${pair.driverLabel}"
        }
        val outcomeDir = when {
            pair.outcomeIsTimeOfDay -> if (positive) "a later" else "an earlier"
            positive -> "more"
            else -> "less"
        }
        val linkPhrase = when {
            h.lag == 0 -> "also tend to have"
            h.lag == 1 -> "tend to be followed by"
            else -> "tend to be followed ${h.lag} days later by"
        }
        val lagSuffix = if (h.lag == 1) " the next day" else ""

        return InsightDraft(
            type = InsightTypes.FEEDBACK_LOOP,
            dedupeKey = "lagcorr:${pair.driverKey}>${pair.outcomeKey}@${h.lag}",
            confidence = min(1.0, abs(h.r)),
            windowStart = now - h.n * DAY_MS,
            windowEnd = now,
            statPayload = """{"driver":"${pair.driverKey}","outcome":"${pair.outcomeKey}",""" +
                """"lag_days":${h.lag},"spearman_r":${h.r.r2()},"n_days":${h.n},""" +
                """"p_value":${h.p.r4()}}""",
            text = "$driverPhrase $linkPhrase $outcomeDir ${pair.outcomeLabel}$lagSuffix — " +
                "a link that held across ${h.n} matched days.",
        )
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

/** Rank statistics; normal approximation is fine at the n≥20 this engine requires. */
object Stats {

    fun spearman(a: DoubleArray, b: DoubleArray): Double =
        pearson(ranks(a), ranks(b))

    /** Two-sided p via z = r·√(n−1). */
    fun spearmanPValue(r: Double, n: Int): Double {
        if (n < 3) return 1.0
        val z = abs(r) * sqrt((n - 1).toDouble())
        return (2.0 * (1.0 - normalCdf(z))).coerceIn(0.0, 1.0)
    }

    fun ranks(values: DoubleArray): DoubleArray {
        val indexed = values.withIndex().sortedBy { it.value }
        val out = DoubleArray(values.size)
        var i = 0
        while (i < indexed.size) {
            var j = i
            while (j + 1 < indexed.size && indexed[j + 1].value == indexed[i].value) j++
            val avgRank = (i + j) / 2.0 + 1.0 // average rank for ties
            for (k in i..j) out[indexed[k].index] = avgRank
            i = j + 1
        }
        return out
    }

    fun pearson(a: DoubleArray, b: DoubleArray): Double {
        val n = a.size
        val meanA = a.average()
        val meanB = b.average()
        var cov = 0.0
        var varA = 0.0
        var varB = 0.0
        for (i in 0 until n) {
            val da = a[i] - meanA
            val db = b[i] - meanB
            cov += da * db
            varA += da * da
            varB += db * db
        }
        val denom = sqrt(varA * varB)
        return if (denom < 1e-12) 0.0 else cov / denom
    }

    /** Abramowitz–Stegun 7.1.26 erf approximation (|err| < 1.5e-7). */
    fun normalCdf(z: Double): Double = 0.5 * (1.0 + erf(z / sqrt(2.0)))

    private fun erf(x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val ax = abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val y = 1.0 - (
            ((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t +
                0.254829592
            ) * t * exp(-ax * ax)
        return sign * y
    }
}

internal fun Double.r4(): Double = round(this * 10_000) / 10_000
