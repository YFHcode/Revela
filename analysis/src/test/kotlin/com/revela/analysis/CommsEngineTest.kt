package com.revela.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CommsEngineTest {

    private val engine = CommsEngine()
    private val start: LocalDate = LocalDate.parse("2026-04-01")
    private val now = 1_760_000_000_000L
    private val alice = ContactRef(1L, "Alice")
    private val contacts = mapOf(1L to alice)

    private fun hourHist(hour: Int, count: Int) = IntArray(24).also { it[hour] = count }

    @Test
    fun `evening chat pattern surfaces a timing insight`() {
        // 20 active days, ~5 messages each between 21:00-22:00.
        val days = (0 until 20).map { i ->
            ContactDay(start.plusDays(i.toLong()).toString(), 1L, 5, hourHist(21, 5))
        }
        val drafts = engine.generate(days, contacts, todayKey = "2026-06-01", now = now)
        val timing = drafts.filter { it.type == InsightTypes.COMMS_TIMING }
        assertEquals(1, timing.size)
        assertTrue(timing.single().text.contains("Alice"))
        assertTrue(timing.single().text.contains("21:00"))
    }

    @Test
    fun `too few messages stays quiet`() {
        val days = (0 until 5).map { i ->
            ContactDay(start.plusDays(i.toLong()).toString(), 1L, 1, hourHist(21, 1))
        }
        val drafts = engine.generate(days, contacts, "2026-06-01", now)
        assertTrue(drafts.none { it.type == InsightTypes.COMMS_TIMING })
    }

    @Test
    fun `messages spread evenly across the day give no timing insight`() {
        val flat = IntArray(24) { 1 }
        val days = (0 until 20).map { i ->
            ContactDay(start.plusDays(i.toLong()).toString(), 1L, 24, flat)
        }
        val drafts = engine.generate(days, contacts, "2026-06-01", now)
        assertTrue(drafts.none { it.type == InsightTypes.COMMS_TIMING })
    }

    @Test
    fun `a halving of weekly frequency is detected as drift`() {
        // 6 weeks at ~14/week, then 6 weeks at ~4/week.
        val days = buildList {
            for (week in 0 until 12) {
                val perDay = if (week < 6) 2 else 0
                for (d in 0 until 7) {
                    val date = start.plusDays((week * 7 + d).toLong()).toString()
                    // front-load a few messages so weekly totals are 14 then ~4
                    val count = if (week < 6) 2 else if (d < 4) 1 else 0
                    add(ContactDay(date, 1L, count, hourHist(21, count)))
                }
            }
        }
        val drafts = engine.generate(days, contacts, "2026-09-01", now)
        val drift = drafts.filter { it.type == InsightTypes.RELATIONSHIP_DRIFT }
        assertEquals(1, drift.size)
        assertTrue(drift.single().text.contains("less"))
    }

    @Test
    fun `steady weekly frequency is not drift`() {
        val days = (0 until 84).map { i ->
            ContactDay(start.plusDays(i.toLong()).toString(), 1L, 2, hourHist(21, 2))
        }
        val drafts = engine.generate(days, contacts, "2026-09-01", now)
        assertTrue(drafts.none { it.type == InsightTypes.RELATIONSHIP_DRIFT })
    }

    @Test
    fun `unknown contact is skipped`() {
        val days = (0 until 20).map { i ->
            ContactDay(start.plusDays(i.toLong()).toString(), 99L, 5, hourHist(21, 5))
        }
        assertTrue(engine.generate(days, contacts, "2026-06-01", now).isEmpty())
    }
}
