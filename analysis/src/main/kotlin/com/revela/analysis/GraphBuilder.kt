package com.revela.analysis

import com.revela.core.model.DayType
import java.time.LocalDate

/**
 * Builds the life-graph's edges from co-occurrence within context windows
 * (§11), and summarizes a community's time signature so a mode can be named.
 * Pure JVM.
 */
object GraphBuilder {

    /** An entity active in a (day, daypart) window. daypart: 0 night,1 morning,2 afternoon,3 evening. */
    data class Activity(val entityId: Long, val dayKey: String, val daypart: Int)

    fun daypart(hour: Int): Int = when (hour) {
        in 5..11 -> 1
        in 12..17 -> 2
        in 18..23 -> 3
        else -> 0
    }

    /**
     * CO_OCCURRED edges: entities present in the same (day, daypart) window
     * get their edge weight incremented. Windows with a single entity, or
     * with more than [maxPerWindow] entities (too diffuse to be meaningful),
     * are skipped.
     */
    fun buildEdges(activities: List<Activity>, maxPerWindow: Int = 12): List<Edge> {
        val byWindow = activities.groupBy { it.dayKey to it.daypart }
        val weights = HashMap<Pair<Long, Long>, Double>()
        for ((_, items) in byWindow) {
            val entities = items.map { it.entityId }.distinct().sorted()
            if (entities.size < 2 || entities.size > maxPerWindow) continue
            for (i in entities.indices) {
                for (j in i + 1 until entities.size) {
                    val key = entities[i] to entities[j]
                    weights.merge(key, 1.0, Double::plus)
                }
            }
        }
        return weights.map { (pair, w) -> Edge(pair.first, pair.second, w) }
    }

    data class TimeSignature(
        val topDaypart: Int,
        val topDaypartShare: Double,
        val weekendShare: Double,
        val windowCount: Int,
    )

    /** Where in the week/day a community's members tend to co-occur. */
    fun timeSignature(members: Set<Long>, activities: List<Activity>): TimeSignature {
        val relevant = activities.filter { it.entityId in members }
        if (relevant.isEmpty()) return TimeSignature(0, 0.0, 0.0, 0)

        val windows = relevant.map { it.dayKey to it.daypart }.distinct()
        val daypartCounts = IntArray(4)
        var weekend = 0
        for ((dayKey, daypart) in windows) {
            daypartCounts[daypart]++
            if (dayType(dayKey) == DayType.WEEKEND) weekend++
        }
        val top = daypartCounts.indices.maxBy { daypartCounts[it] }
        return TimeSignature(
            topDaypart = top,
            topDaypartShare = daypartCounts[top].toDouble() / windows.size,
            weekendShare = weekend.toDouble() / windows.size,
            windowCount = windows.size,
        )
    }

    fun daypartName(daypart: Int): String = when (daypart) {
        1 -> "morning"
        2 -> "afternoon"
        3 -> "evening"
        else -> "night"
    }

    private fun dayType(dayKey: String): DayType {
        val dow = LocalDate.parse(dayKey).dayOfWeek
        return if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) {
            DayType.WEEKEND
        } else {
            DayType.WEEKDAY
        }
    }
}
