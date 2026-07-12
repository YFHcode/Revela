package com.revela.analysis

import com.revela.core.model.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EarlyEngineTest {

    private val engine = EarlyEngine()
    private val start: LocalDate = LocalDate.parse("2026-07-06")
    private val now = 1_760_000_000_000L

    private fun series(subject: String, label: String, days: Int, value: (Int) -> Double) =
        BehaviorSeries(
            subject, label, SeriesUnit.SECONDS,
            (0 until days).map { DayPoint(start.plusDays(it.toLong()).toString(), value(it)) },
        )

    @Test
    fun `first full day produces a baseline snapshot`() {
        val todayKey = start.plusDays(1).toString() // 1 history day + today
        val drafts = engine.generate(
            listOf(
                series("screen_time", "screen time", 2) { 7200.0 },
                series("pickups", "pickups", 2) { 80.0 },
                series("reflex_checks", "quick checks", 2) { 12.0 },
            ),
            firstUnlockMinutes = emptyList(),
            todayKey = todayKey,
            now = now,
        )
        assertEquals(listOf(InsightTypes.BASELINE_SNAPSHOT), drafts.map { it.type })
        val draft = drafts.single()
        assertEquals("baseline", draft.dedupeKey)
        assertTrue(draft.text.contains("First full day"))
        assertTrue(draft.text.contains("2h 00m"))
        assertTrue(draft.statPayload.contains("\"days_observed\":1"))
    }

    @Test
    fun `a few days unlock top apps and early chronotype`() {
        val todayKey = start.plusDays(5).toString() // 5 history days
        val drafts = engine.generate(
            listOf(
                series("screen_time", "screen time", 6) { 7200.0 + it * 100 },
                series("pickups", "pickups", 6) { 80.0 },
                series("reflex_checks", "quick checks", 6) { 12.0 },
                series("app:com.whatsapp", "WhatsApp", 6) { 3600.0 },
                series("app:com.spotify", "Spotify", 6) { 1800.0 },
                series("app:com.rarely", "Rarely", 6) { 10.0 }, // under a minute → excluded
            ),
            firstUnlockMinutes = List(5) { DayType.WEEKDAY to 430.0 },
            todayKey = todayKey,
            now = now,
        )

        assertEquals(
            setOf(
                InsightTypes.BASELINE_SNAPSHOT,
                InsightTypes.EARLY_TOP_APPS,
                InsightTypes.EARLY_CHRONOTYPE,
            ),
            drafts.map { it.type }.toSet(),
        )
        val topApps = drafts.first { it.type == InsightTypes.EARLY_TOP_APPS }
        assertTrue(topApps.text.contains("WhatsApp"))
        assertTrue(topApps.text.contains("Spotify"))
        assertTrue(!topApps.text.contains("Rarely"))
        val chrono = drafts.first { it.type == InsightTypes.EARLY_CHRONOTYPE }
        assertTrue(chrono.text.contains("07:10"))
        assertTrue(chrono.text.contains("5 mornings"))
    }

    @Test
    fun `no completed day means no early insights`() {
        val todayKey = start.toString() // today only, no history
        val drafts = engine.generate(
            listOf(series("screen_time", "screen time", 1) { 3600.0 }),
            firstUnlockMinutes = emptyList(),
            todayKey = todayKey,
            now = now,
        )
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun `dedupe keys are stable so cards refresh instead of repeating`() {
        val todayKey = start.plusDays(3).toString()
        val fixtures = listOf(
            series("screen_time", "screen time", 4) { 7200.0 },
            series("pickups", "pickups", 4) { 80.0 },
            series("reflex_checks", "quick checks", 4) { 12.0 },
        )
        val first = engine.generate(fixtures, emptyList(), todayKey, now)
        val second = engine.generate(fixtures, emptyList(), todayKey, now + 3_600_000)
        assertEquals(first.map { it.dedupeKey }, second.map { it.dedupeKey })
    }
}
