package com.revela.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The pattern-mining detectors (§8 of the brief). Statistics only — the LLM
 * never computes these. Pure JVM: every rule is unit-tested with planted
 * fixtures AND shuffled negative controls, so the insights feed doesn't
 * become a horoscope.
 */

/** §8.2 — periodicity via autocorrelation with a local-peak requirement. */
object Periodicity {

    data class Result(val periodDays: Int, val strength: Double, val cycles: Int)

    fun detect(
        values: DoubleArray,
        minPeriod: Int = 2,
        maxPeriodCap: Int = 35,
        minDays: Int = 21,
    ): Result? {
        val n = values.size
        if (n < minDays) return null

        val mean = values.average()
        val centered = DoubleArray(n) { values[it] - mean }
        val denom = centered.sumOf { it * it }
        if (denom < 1e-9) return null // constant series has no rhythm

        val maxLag = min(maxPeriodCap, n / 3) // require >= 3 observed cycles
        if (maxLag < minPeriod) return null

        val acf = DoubleArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var sum = 0.0
            for (i in 0 until n - lag) sum += centered[i] * centered[i + lag]
            acf[lag] = sum / denom
        }

        // Significance floor: white noise ACF has sd ~ 1/sqrt(n).
        val threshold = max(0.3, 2.0 / sqrt(n.toDouble()))

        var best: Result? = null
        for (lag in max(2, minPeriod)..maxLag) {
            val isPeak = acf[lag] >= threshold &&
                acf[lag] > acf[lag - 1] &&
                (lag == maxLag || acf[lag] > acf[lag + 1])
            if (isPeak && (best == null || acf[lag] > best.strength)) {
                best = Result(periodDays = lag, strength = acf[lag], cycles = n / lag)
            }
        }
        return best
    }
}

/** §8.6 — single change-point via best SSE split + effect-size gate. */
object ChangePoint {

    data class Result(
        /** Index of the first day of the new level. */
        val index: Int,
        val beforeMean: Double,
        val afterMean: Double,
        /** Mean shift in pooled within-segment standard deviations. */
        val effectSize: Double,
    )

    fun detect(
        values: DoubleArray,
        minSegment: Int = 7,
        minEffectSize: Double = 1.2,
    ): Result? {
        val n = values.size
        if (n < 2 * minSegment) return null

        var bestIdx = -1
        var bestSse = Double.MAX_VALUE
        for (split in minSegment..n - minSegment) {
            val sse = sse(values, 0, split) + sse(values, split, n)
            if (sse < bestSse) {
                bestSse = sse
                bestIdx = split
            }
        }
        if (bestIdx < 0) return null

        val before = values.copyOfRange(0, bestIdx)
        val after = values.copyOfRange(bestIdx, n)
        val beforeMean = before.average()
        val afterMean = after.average()
        val pooledSd = sqrt(bestSse / (n - 2).coerceAtLeast(1))
        val effect = if (pooledSd < 1e-9) {
            if (abs(afterMean - beforeMean) < 1e-9) 0.0 else Double.MAX_VALUE
        } else {
            abs(afterMean - beforeMean) / pooledSd
        }

        return if (effect >= minEffectSize) {
            Result(bestIdx, beforeMean, afterMean, effect)
        } else {
            null
        }
    }

    private fun sse(values: DoubleArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += values[i]
        val mean = sum / (to - from)
        var sse = 0.0
        for (i in from until to) {
            val d = values[i] - mean
            sse += d * d
        }
        return sse
    }
}

/** §8.8 — robust deviation from the normal range (median + IQR). */
object Deviation {

    enum class Direction { HIGH, LOW }

    data class Result(
        val value: Double,
        val median: Double,
        val iqr: Double,
        val robustZ: Double,
        val direction: Direction,
    )

    fun detect(
        history: DoubleArray,
        current: Double,
        minHistory: Int = 5,
        zThreshold: Double = 2.0,
    ): Result? {
        if (history.size < minHistory) return null
        val sorted = history.sorted()
        val median = percentile(sorted, 0.5)
        val iqr = percentile(sorted, 0.75) - percentile(sorted, 0.25)
        // IQR → sigma for a normal distribution; floor keeps flat histories
        // from flagging trivial absolute differences.
        val sigma = max(iqr / 1.349, max(0.05 * abs(median), 1e-6))
        val z = (current - median) / sigma
        if (abs(z) < zThreshold) return null
        return Result(
            value = current,
            median = median,
            iqr = iqr,
            robustZ = z,
            direction = if (z > 0) Direction.HIGH else Direction.LOW,
        )
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val pos = p * (sorted.size - 1)
        val lo = pos.toInt()
        val hi = min(lo + 1, sorted.size - 1)
        val frac = pos - lo
        return sorted[lo] * (1 - frac) + sorted[hi] * frac
    }
}

/** Chronotype / social jetlag: weekday vs weekend first-unlock shift. */
object Chronotype {

    data class Result(
        val weekdayMedianMin: Double,
        val weekendMedianMin: Double,
        val shiftMin: Double,
    )

    fun detect(
        weekdayMinutes: DoubleArray,
        weekendMinutes: DoubleArray,
        minWeekdayDays: Int = 4,
        minWeekendDays: Int = 2,
        minShiftMin: Double = 20.0,
    ): Result? {
        if (weekdayMinutes.size < minWeekdayDays || weekendMinutes.size < minWeekendDays) return null
        val wd = median(weekdayMinutes)
        val we = median(weekendMinutes)
        val shift = we - wd
        if (abs(shift) < minShiftMin) return null
        return Result(wd, we, shift)
    }

    private fun median(values: DoubleArray): Double {
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
    }
}
