package com.revela.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Date-key rules shared by rollups and UI.
 *
 * The BEHAVIORAL day runs 04:00 → 04:00 local time: a 1 a.m. session belongs
 * to the previous day's evening, not to the next morning. `usage_daily` and
 * `day_summary` are keyed this way. `usage_hourly` is keyed by the plain
 * calendar date, because the heatmap axis is literal clock time.
 */
object DayKeys {

    const val DAY_BOUNDARY_HOUR = 4L

    /** Behavioral day key (ISO yyyy-MM-dd) for an instant, in [zone]. */
    fun dayKey(ts: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(ts).atZone(zone).minusHours(DAY_BOUNDARY_HOUR).toLocalDate().toString()

    /** Plain calendar date key (ISO yyyy-MM-dd) for an instant, in [zone]. */
    fun calendarDate(ts: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(ts).atZone(zone).toLocalDate().toString()

    fun dayType(dayKey: String): DayType {
        val dow = LocalDate.parse(dayKey).dayOfWeek
        return if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) DayType.WEEKEND else DayType.WEEKDAY
    }
}

enum class DayType { WEEKDAY, WEEKEND }
