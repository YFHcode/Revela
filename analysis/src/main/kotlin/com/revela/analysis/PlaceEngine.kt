package com.revela.analysis

import com.revela.core.model.DayType
import java.time.LocalDate

/**
 * Place-based weekly rhythms (§9). Flags recurring weekend places (the
 * "meet friends" pattern) via day-of-week concentration; home/work are
 * excluded since their rhythm isn't insight.
 */

data class PlaceDay(val dayKey: String, val placeId: Long, val dwellSeconds: Int)

data class PlaceRef(val placeId: Long, val label: String, val isHomeOrWork: Boolean)

class PlaceEngine(
    private val minDaysPresent: Int = 6,
    private val minWeekendShare: Double = 0.7,
) {

    fun generate(
        placeDays: List<PlaceDay>,
        places: Map<Long, PlaceRef>,
        todayKey: String,
        now: Long,
    ): List<InsightDraft> {
        val drafts = mutableListOf<InsightDraft>()
        val byPlace = placeDays.filter { it.dayKey < todayKey }.groupBy { it.placeId }

        for ((placeId, days) in byPlace) {
            val ref = places[placeId] ?: continue
            if (ref.isHomeOrWork) continue // rhythms of home/work aren't insight
            val present = days.filter { it.dwellSeconds > 0 }
            if (present.size < minDaysPresent) continue

            weekendDraft(ref, present, now)?.let { drafts += it }
        }
        return drafts
    }

    private fun weekendDraft(ref: PlaceRef, present: List<PlaceDay>, now: Long): InsightDraft? {
        val weekendCount = present.count {
            DayType.WEEKEND == dayType(it.dayKey)
        }
        val share = weekendCount.toDouble() / present.size
        if (share < minWeekendShare) return null

        return InsightDraft(
            type = InsightTypes.PLACE_RHYTHM,
            dedupeKey = "place_weekend:${ref.placeId}",
            confidence = share.coerceIn(0.0, 1.0),
            windowStart = now - present.size * DAY_MS,
            windowEnd = now,
            statPayload = """{"place_id":${ref.placeId},"visits":${present.size},""" +
                """"weekend_share":${share.r2()}}""",
            text = "${ref.label} is mostly a weekend place for you — " +
                "${weekendCount} of your ${present.size} visits fell on weekends.",
            entityIds = "[${ref.placeId}]",
        )
    }

    private fun dayType(dayKey: String): DayType {
        val dow = LocalDate.parse(dayKey).dayOfWeek
        return if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) {
            DayType.WEEKEND
        } else {
            DayType.WEEKDAY
        }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
