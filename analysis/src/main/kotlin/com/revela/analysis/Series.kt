package com.revela.analysis

import java.time.LocalDate

enum class SeriesUnit { SECONDS, COUNT }

data class DayPoint(val dayKey: String, val value: Double)

/** One behavior's daily time series, ascending by day, zero-filled. */
data class BehaviorSeries(
    /** Stable id, e.g. "screen_time", "pickups", "reflex_checks", "app:com.x". */
    val subjectKey: String,
    /** Human name used in insight text, e.g. "screen time", "WhatsApp". */
    val label: String,
    val unit: SeriesUnit,
    val points: List<DayPoint>,
)

/** Inputs to the series builder — plain rows, decoupled from Room entities. */
data class DayRow(val dayKey: String, val screenTimeS: Int, val pickups: Int, val reflexChecks: Int)
data class AppDayRow(val dayKey: String, val appPkg: String, val totalSeconds: Int)

object SeriesBuilder {

    const val TOP_APPS = 5

    /**
     * Builds the behavior series the detectors run over: overall screen time,
     * pickups, reflex checks, and the top-N apps by total time (a curated
     * hypothesis space, not all apps — fewer tests, fewer false discoveries).
     * App series are zero-filled across the observed day range so "didn't use
     * it" counts as signal.
     */
    fun build(
        days: List<DayRow>,
        appDays: List<AppDayRow>,
        appLabel: (String) -> String,
    ): List<BehaviorSeries> {
        if (days.isEmpty()) return emptyList()
        val sortedDays = days.sortedBy { it.dayKey }
        val series = mutableListOf(
            BehaviorSeries(
                "screen_time", "screen time", SeriesUnit.SECONDS,
                sortedDays.map { DayPoint(it.dayKey, it.screenTimeS.toDouble()) },
            ),
            BehaviorSeries(
                "pickups", "pickups", SeriesUnit.COUNT,
                sortedDays.map { DayPoint(it.dayKey, it.pickups.toDouble()) },
            ),
            BehaviorSeries(
                "reflex_checks", "quick checks", SeriesUnit.COUNT,
                sortedDays.map { DayPoint(it.dayKey, it.reflexChecks.toDouble()) },
            ),
        )

        val allDayKeys = dayKeyRange(sortedDays.first().dayKey, sortedDays.last().dayKey)
        val topApps = appDays.groupBy { it.appPkg }
            .mapValues { (_, rows) -> rows.sumOf { it.totalSeconds } }
            .entries.sortedByDescending { it.value }
            .take(TOP_APPS)
            .map { it.key }

        for (pkg in topApps) {
            val byDay = appDays.filter { it.appPkg == pkg }.associate { it.dayKey to it.totalSeconds }
            series += BehaviorSeries(
                subjectKey = "app:$pkg",
                label = appLabel(pkg),
                unit = SeriesUnit.SECONDS,
                points = allDayKeys.map { DayPoint(it, (byDay[it] ?: 0).toDouble()) },
            )
        }
        return series
    }

    private fun dayKeyRange(first: String, last: String): List<String> {
        val start = LocalDate.parse(first)
        val end = LocalDate.parse(last)
        val out = mutableListOf<String>()
        var d = start
        while (!d.isAfter(end)) {
            out += d.toString()
            d = d.plusDays(1)
        }
        return out
    }
}
