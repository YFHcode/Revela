package com.revela.analysis

import com.revela.core.model.DayKeys
import com.revela.core.model.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.sin

/**
 * M3 acceptance: a synthetic 61-day fixture with planted patterns yields
 * exactly those insights, and a control fixture with the patterns removed
 * yields none of them. Control series are verified-silent literals (see
 * DetectorsTest for the policy).
 */
class InsightEngineTest {

    private val engine = InsightEngine()
    private val start: LocalDate = LocalDate.parse("2026-05-10")
    private val days = 61 // 60 history days + today
    private val todayKey: String = start.plusDays((days - 1).toLong()).toString()
    private val now = 1_760_000_000_000L

    private fun dayKey(i: Int) = start.plusDays(i.toLong()).toString()

    private fun series(subject: String, label: String, unit: SeriesUnit, value: (Int) -> Double) =
        BehaviorSeries(subject, label, unit, (0 until days).map { DayPoint(dayKey(it), value(it)) })

    private fun unlocks(): List<Pair<DayType, Double>> =
        (0 until days - 1).map { i ->
            val type = DayKeys.dayType(dayKey(i))
            type to if (type == DayType.WEEKEND) 480.0 + (i % 2) * 6 else 420.0 + (i % 3) * 4
        }

    @Test
    fun `planted patterns produce exactly the expected insights`() {
        val fixtures = listOf(
            // Planted 7-day cycle in one app.
            series("app:com.example.social", "Socialgram", SeriesUnit.SECONDS) { t ->
                3600.0 + 1800.0 * sin(2 * PI * t / 7.0) + (t % 3) * 30
            },
            // Planted level shift at day 40 in screen time; today's value sits
            // on the new level so it is NOT unusual.
            series("screen_time", "screen time", SeriesUnit.SECONDS) { t ->
                if (t < 40) 7200.0 else 14400.0
            },
            // Pickups: steady history, today spikes 3x.
            series("pickups", "pickups", SeriesUnit.COUNT) { t ->
                if (t == days - 1) 240.0 else 80.0
            },
            // Reflex checks: steady 12/day.
            series("reflex_checks", "quick checks", SeriesUnit.COUNT) { 12.0 },
        )

        val drafts = engine.generate(fixtures, unlocks(), todayKey, now)
        val byType = drafts.groupBy { it.type }

        assertEquals(
            "expected exactly one rhythm insight (the planted app cycle)",
            listOf("periodic:app:com.example.social"),
            byType[InsightTypes.PERIODIC_RHYTHM].orEmpty().map { it.dedupeKey },
        )
        assertEquals(1, byType[InsightTypes.HABIT_SHIFT].orEmpty().size)
        assertTrue(
            byType[InsightTypes.HABIT_SHIFT]!!.single().dedupeKey.startsWith("shift:screen_time:"),
        )
        assertEquals(
            listOf("unusual:pickups:$todayKey"),
            byType[InsightTypes.UNUSUAL_DAY].orEmpty().map { it.dedupeKey },
        )
        assertEquals(1, byType[InsightTypes.REFLEX_CHECKS].orEmpty().size)
        assertEquals(1, byType[InsightTypes.CHRONOTYPE].orEmpty().size)

        // Every draft carries its numbers and non-empty neutral text.
        for (d in drafts) {
            assertTrue(d.statPayload.startsWith("{") && d.statPayload.endsWith("}"))
            assertTrue(d.text.isNotBlank())
        }
    }

    @Test
    fun `control fixture with patterns removed stays silent`() {
        val fixtures = listOf(
            series("app:com.example.social", "Socialgram", SeriesUnit.SECONDS) { t ->
                SHUFFLED_SINE_61[t]
            },
            series("screen_time", "screen time", SeriesUnit.SECONDS) { 7200.0 },
            series("pickups", "pickups", SeriesUnit.COUNT) { 80.0 },
        )

        val drafts = engine.generate(fixtures, emptyList(), todayKey, now)
        val types = drafts.map { it.type }.toSet()

        assertTrue("no rhythm expected", InsightTypes.PERIODIC_RHYTHM !in types)
        assertTrue("no shift expected", InsightTypes.HABIT_SHIFT !in types)
        assertTrue("no unusual day expected", InsightTypes.UNUSUAL_DAY !in types)
        assertTrue("no chronotype without unlock data", InsightTypes.CHRONOTYPE !in types)
    }

    @Test
    fun `insight identity is stable across runs for upsert dedupe`() {
        val fixtures = listOf(
            series("reflex_checks", "quick checks", SeriesUnit.COUNT) { 12.0 },
        )
        val first = engine.generate(fixtures, emptyList(), todayKey, now)
        val second = engine.generate(fixtures, emptyList(), todayKey, now + 3600_000)
        assertTrue(first.isNotEmpty())
        assertEquals(first.map { it.dedupeKey }, second.map { it.dedupeKey })
    }
}

// Verified-silent shuffle of a 61-day sine cycle (see DetectorsTest policy).
internal val SHUFFLED_SINE_61 = doubleArrayOf(
    5007.3, 5354.87, 4380.99, 4380.99, 2819.01, 2819.01, 5354.87, 2819.01,
    5354.87, 5007.3, 1845.13, 1845.13, 2819.01, 4380.99, 1845.13, 1845.13,
    5354.87, 5354.87, 2819.01, 5354.87, 1845.13, 4380.99, 5007.3, 5007.3,
    4380.99, 2192.7, 2819.01, 3600.0, 2192.7, 5354.87, 1845.13, 2192.7,
    4380.99, 4380.99, 2192.7, 4380.99, 2192.7, 5007.3, 3600.0, 4380.99,
    2192.7, 3600.0, 5354.87, 3600.0, 2819.01, 5007.3, 5354.87, 5007.3,
    3600.0, 2192.7, 1845.13, 3600.0, 5007.3, 2819.01, 2819.01, 3600.0,
    5007.3, 1845.13, 2192.7, 3600.0, 3600.0,
)
