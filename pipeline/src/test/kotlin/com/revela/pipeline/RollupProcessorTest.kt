package com.revela.pipeline

import com.revela.core.model.CollectorSource
import com.revela.core.model.DayType
import com.revela.core.model.EventType
import com.revela.core.model.PickupType
import com.revela.core.model.RawEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RollupProcessorTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val processor = RollupProcessor(zone)

    private fun ts(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private fun event(type: EventType, at: String, pkg: String? = null) = RawEvent(
        ts = ts(at),
        type = type,
        appPkg = pkg,
        source = CollectorSource.USAGE_STATS,
    )

    @Test
    fun `foreground-background pair produces one session with correct daily totals`() {
        val result = processor.process(
            listOf(
                event(EventType.UNLOCK, "2026-07-10T09:00:00"),
                event(EventType.APP_FOREGROUND, "2026-07-10T09:00:05", "com.whatsapp"),
                event(EventType.APP_BACKGROUND, "2026-07-10T09:03:05", "com.whatsapp"),
                event(EventType.SCREEN_OFF, "2026-07-10T09:03:10"),
            ),
        )

        assertEquals(1, result.sessions.size)
        val session = result.sessions.single()
        assertEquals("com.whatsapp", session.appPkg)
        assertEquals(180, session.durationS)
        assertEquals("2026-07-10", session.dayKey)

        val daily = result.daily.single()
        assertEquals(180, daily.totalSeconds)
        assertEquals(1, daily.openCount)
    }

    @Test
    fun `app switch without background event closes the previous session`() {
        val result = processor.process(
            listOf(
                event(EventType.APP_FOREGROUND, "2026-07-10T10:00:00", "app.a"),
                event(EventType.APP_FOREGROUND, "2026-07-10T10:02:00", "app.b"),
                event(EventType.SCREEN_OFF, "2026-07-10T10:05:00"),
            ),
        )

        assertEquals(2, result.sessions.size)
        assertEquals(120, result.sessions[0].durationS) // app.a closed by app.b
        assertEquals(180, result.sessions[1].durationS) // app.b closed by screen off
    }

    @Test
    fun `session crossing an hour boundary is split in usage_hourly`() {
        val result = processor.process(
            listOf(
                event(EventType.APP_FOREGROUND, "2026-07-10T09:50:00", "app.a"),
                event(EventType.APP_BACKGROUND, "2026-07-10T10:10:00", "app.a"),
            ),
        )

        assertEquals(2, result.hourly.size)
        val h9 = result.hourly.first { it.hour == 9 }
        val h10 = result.hourly.first { it.hour == 10 }
        assertEquals(600, h9.totalSeconds)
        assertEquals(600, h10.totalSeconds)
        assertEquals(1, h9.openCount)   // open counted once, at the start hour
        assertEquals(0, h10.openCount)
    }

    @Test
    fun `one am usage belongs to the previous behavioral day`() {
        val result = processor.process(
            listOf(
                event(EventType.APP_FOREGROUND, "2026-07-11T01:00:00", "app.night"),
                event(EventType.APP_BACKGROUND, "2026-07-11T01:30:00", "app.night"),
            ),
        )

        assertEquals("2026-07-10", result.sessions.single().dayKey)
        assertEquals("2026-07-10", result.daily.single().dayKey)
        // But the hourly row keeps the literal calendar date and hour.
        assertEquals("2026-07-11", result.hourly.first().date)
        assertEquals(1, result.hourly.first().hour)
    }

    @Test
    fun `short pickup is a reflex check and tags its session`() {
        val result = processor.process(
            listOf(
                event(EventType.UNLOCK, "2026-07-10T12:00:00"),
                event(EventType.APP_FOREGROUND, "2026-07-10T12:00:02", "app.a"),
                event(EventType.SCREEN_OFF, "2026-07-10T12:00:10"), // 10s pickup
                event(EventType.UNLOCK, "2026-07-10T14:00:00"),
                event(EventType.APP_FOREGROUND, "2026-07-10T14:00:02", "app.a"),
                event(EventType.SCREEN_OFF, "2026-07-10T14:05:00"), // 5min pickup
            ),
        )

        val day = result.days.single()
        assertEquals(2, day.pickupCount)
        assertEquals(1, day.reflexCheckCount)
        assertEquals(PickupType.REFLEX, result.sessions[0].pickupType)
        assertEquals(PickupType.NORMAL, result.sessions[1].pickupType)
    }

    @Test
    fun `day summary derives screen time, first unlock and day type`() {
        val result = processor.process(
            listOf(
                event(EventType.UNLOCK, "2026-07-11T08:30:00"), // a Saturday
                event(EventType.APP_FOREGROUND, "2026-07-11T08:30:05", "app.a"),
                event(EventType.SCREEN_OFF, "2026-07-11T08:40:00"),
                event(EventType.UNLOCK, "2026-07-11T22:00:00"),
                event(EventType.SCREEN_OFF, "2026-07-11T22:20:00"),
            ),
        )

        val day = result.days.single()
        assertEquals("2026-07-11", day.dayKey)
        assertEquals(DayType.WEEKEND, day.dayType)
        assertEquals(ts("2026-07-11T08:30:00"), day.firstUnlock)
        assertEquals(ts("2026-07-11T22:20:00"), day.lastUse)
        assertEquals((10 * 60) + (20 * 60), day.totalScreenTimeS)
    }

    @Test
    fun `processing is deterministic and idempotent`() {
        val events = listOf(
            event(EventType.UNLOCK, "2026-07-10T09:00:00"),
            event(EventType.APP_FOREGROUND, "2026-07-10T09:00:05", "app.a"),
            event(EventType.APP_FOREGROUND, "2026-07-10T09:02:00", "app.b"),
            event(EventType.SCREEN_OFF, "2026-07-10T09:10:00"),
        )
        assertEquals(processor.process(events), processor.process(events.shuffled()))
    }

    @Test
    fun `unclosed trailing session is dropped until its close event arrives`() {
        val result = processor.process(
            listOf(
                event(EventType.APP_FOREGROUND, "2026-07-10T09:00:00", "app.a"),
            ),
        )
        // Zero-length (start == last event ts) → dropped; next rollup will see the close.
        assertTrue(result.sessions.isEmpty())
    }
}
