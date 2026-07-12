package com.revela.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
        m > 0 -> "${m}m"
        else -> "${totalSeconds}s"
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

fun formatClockTime(ts: Long?): String =
    ts?.let { TIME_FORMAT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "—"

/** Single-letter weekday for a yyyy-MM-dd key, e.g. "M". */
fun weekdayLetter(dateKey: String): String =
    LocalDate.parse(dateKey).dayOfWeek
        .getDisplayName(TextStyle.NARROW, Locale.getDefault())
