package com.revela.core.model

/** Rollup output types (§6.2). Plain data — persistence lives in :core:db. */

enum class PickupType { NORMAL, REFLEX }

data class AppSession(
    val dayKey: String,
    val appPkg: String,
    val startTs: Long,
    val endTs: Long,
    val pickupType: PickupType,
) {
    val durationS: Int get() = ((endTs - startTs) / 1000).toInt()
}

data class HourlyUsage(
    /** Calendar date (not behavioral day) — heatmap axis is clock time. */
    val date: String,
    val hour: Int,
    val appPkg: String,
    val totalSeconds: Int,
    val openCount: Int,
)

data class DailyUsage(
    val dayKey: String,
    val appPkg: String,
    val totalSeconds: Int,
    val openCount: Int,
    val firstUse: Long,
    val lastUse: Long,
)

data class DaySummary(
    val dayKey: String,
    val firstUnlock: Long?,
    val lastUse: Long?,
    val totalScreenTimeS: Int,
    val pickupCount: Int,
    val reflexCheckCount: Int,
    val dayType: DayType,
    val zoneId: String,
)

data class RollupResult(
    val sessions: List<AppSession>,
    val hourly: List<HourlyUsage>,
    val daily: List<DailyUsage>,
    val days: List<DaySummary>,
)
