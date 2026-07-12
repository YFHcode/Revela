package com.revela.analysis

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Phase-2 relationship insights (§9). Communication-timing per contact and
 * relationship drift over months. Statistics only — contacts are referenced
 * by opaque ids here; the app substitutes display names when rendering, and
 * pseudonymizes before anything reaches the LLM (§3.3).
 */

/** Per-day messaging with one contact. by_hour is a 24-slot local-hour count. */
data class ContactDay(
    val dayKey: String,
    val contactId: Long,
    val msgCount: Int,
    val byHour: IntArray,
)

/** Resolved contact for text; label is display-only and never sent to the LLM raw. */
data class ContactRef(val contactId: Long, val label: String)

class CommsEngine(
    private val minDaysActive: Int = 8,
    private val minTotalMessages: Int = 25,
) {

    fun generate(
        contactDays: List<ContactDay>,
        contacts: Map<Long, ContactRef>,
        todayKey: String,
        now: Long,
    ): List<InsightDraft> {
        val drafts = mutableListOf<InsightDraft>()
        val byContact = contactDays.filter { it.dayKey < todayKey }.groupBy { it.contactId }

        for ((contactId, days) in byContact) {
            val ref = contacts[contactId] ?: continue
            timingDraft(ref, days, now)?.let { drafts += it }
            driftDraft(ref, days, now)?.let { drafts += it }
        }
        return drafts
    }

    /** Communication timing: the hour band where most messages with a contact land. */
    private fun timingDraft(ref: ContactRef, days: List<ContactDay>, now: Long): InsightDraft? {
        val total = days.sumOf { it.msgCount }
        if (days.size < minDaysActive || total < minTotalMessages) return null

        val hist = IntArray(24)
        for (d in days) for (h in 0..23) hist[h] += d.byHour[h]

        val (bandStart, bandShare) = peakBand(hist, total)
        if (bandShare < 0.5) return null // no clear timing → stay quiet
        val bandEnd = (bandStart + 3) % 24

        return InsightDraft(
            type = InsightTypes.COMMS_TIMING,
            dedupeKey = "comms_timing:${ref.contactId}",
            confidence = bandShare.coerceIn(0.0, 1.0),
            windowStart = now - days.size * DAY_MS,
            windowEnd = now,
            statPayload = """{"contact_id":${ref.contactId},"total_messages":$total,""" +
                """"band_start_hour":$bandStart,"band_share":${bandShare.r2()},""" +
                """"days_active":${days.size}}""",
            text = "Most of your back-and-forth with ${ref.label} happens between " +
                "${clock(bandStart)} and ${clock(bandEnd)} — " +
                "${(bandShare * 100).roundToInt()}% of it, across ${days.size} active days.",
            entityIds = ",${ref.contactId},",
        )
    }

    /** Relationship drift: a level change in weekly message frequency over months. */
    private fun driftDraft(ref: ContactRef, days: List<ContactDay>, now: Long): InsightDraft? {
        val weekly = weeklyCounts(days)
        if (weekly.size < 8) return null // ~2 months of weeks

        val change = ChangePoint.detect(weekly, minSegment = 3, minEffectSize = 1.0) ?: return null
        val before = change.beforeMean
        val after = change.afterMean
        if (before < 1.0 && after < 1.0) return null

        val direction = if (after < before) "less" else "more"
        val beforeR = before.roundToInt()
        val afterR = after.roundToInt()
        if (beforeR == afterR) return null

        return InsightDraft(
            type = InsightTypes.RELATIONSHIP_DRIFT,
            dedupeKey = "drift:${ref.contactId}",
            confidence = (change.effectSize / 3.0).coerceIn(0.0, 1.0),
            windowStart = now - weekly.size * 7 * DAY_MS,
            windowEnd = now,
            statPayload = """{"contact_id":${ref.contactId},"before_per_week":${before.r2()},""" +
                """"after_per_week":${after.r2()},"weeks":${weekly.size},""" +
                """"effect_size":${change.effectSize.r2()}}""",
            text = "You're in touch with ${ref.label} $direction than you used to be — " +
                "around $afterR times a week lately, versus about $beforeR before.",
            entityIds = ",${ref.contactId},",
        )
    }

    private fun peakBand(hist: IntArray, total: Int): Pair<Int, Double> {
        var bestStart = 0
        var bestCount = -1
        for (start in 0..23) {
            var count = 0
            for (offset in 0..3) count += hist[(start + offset) % 24]
            if (count > bestCount) {
                bestCount = count
                bestStart = start
            }
        }
        return bestStart to bestCount.toDouble() / total
    }

    /**
     * Weekly message counts in 7-day windows anchored to the first observed
     * day (NOT calendar weeks — arbitrary Monday anchoring would fabricate
     * partial-week dips at the edges and trigger false "drift"). Silent weeks
     * count as 0; a trailing partial week is dropped so it can't read as a
     * decline.
     */
    private fun weeklyCounts(days: List<ContactDay>): DoubleArray {
        if (days.isEmpty()) return DoubleArray(0)
        val sorted = days.sortedBy { it.dayKey }
        val first = LocalDate.parse(sorted.first().dayKey)
        val last = LocalDate.parse(sorted.last().dayKey)
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1
        val fullWeeks = (totalDays / 7).toInt()
        if (fullWeeks == 0) return DoubleArray(0)

        val counts = DoubleArray(fullWeeks)
        for (d in sorted) {
            val week = (
                java.time.temporal.ChronoUnit.DAYS.between(first, LocalDate.parse(d.dayKey)) / 7
                ).toInt()
            if (week < fullWeeks) counts[week] += d.msgCount
        }
        return counts
    }

    private fun clock(hour: Int): String = "%02d:00".format(hour)

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
