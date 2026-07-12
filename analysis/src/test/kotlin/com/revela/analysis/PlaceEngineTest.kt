package com.revela.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlaceEngineTest {

    private val engine = PlaceEngine()
    private val now = 1_760_000_000_000L
    private val gym = PlaceRef(2L, "the gym", isHomeOrWork = false)
    private val office = PlaceRef(3L, "work", isHomeOrWork = true)

    // 12 consecutive Saturdays starting 2026-04-04 (a Saturday).
    private fun saturdays(count: Int, placeId: Long): List<PlaceDay> {
        var d = LocalDate.parse("2026-04-04")
        return (0 until count).map {
            val row = PlaceDay(d.toString(), placeId, 5400)
            d = d.plusDays(7)
            row
        }
    }

    @Test
    fun `a weekend-only place surfaces a rhythm`() {
        val drafts = engine.generate(
            saturdays(8, 2L),
            mapOf(2L to gym),
            todayKey = "2026-09-01",
            now = now,
        )
        assertEquals(1, drafts.size)
        val draft = drafts.single()
        assertEquals(InsightTypes.PLACE_RHYTHM, draft.type)
        assertTrue(draft.text.contains("the gym"))
        assertTrue(draft.text.contains("weekend"))
    }

    @Test
    fun `home and work places are never rhythm insights`() {
        val drafts = engine.generate(
            saturdays(8, 3L),
            mapOf(3L to office),
            "2026-09-01",
            now,
        )
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun `a place visited on all days is not a weekend place`() {
        var d = LocalDate.parse("2026-04-01")
        val everyDay = (0 until 20).map {
            val row = PlaceDay(d.toString(), 2L, 3600)
            d = d.plusDays(1)
            row
        }
        val drafts = engine.generate(everyDay, mapOf(2L to gym), "2026-09-01", now)
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun `too few visits stays quiet`() {
        val drafts = engine.generate(saturdays(3, 2L), mapOf(2L to gym), "2026-09-01", now)
        assertTrue(drafts.isEmpty())
    }
}
