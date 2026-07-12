package com.revela.capture

import com.revela.core.model.CollectorSource
import com.revela.core.model.EventLog
import com.revela.core.model.EventType
import com.revela.core.model.RawEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsCollectorTest {

    private class FakeSource(private val events: List<RawEvent>) : UsageEventSource {
        val requestedRanges = mutableListOf<LongRange>()

        override fun eventsBetween(beginTs: Long, endTs: Long): List<RawEvent> {
            requestedRanges += beginTs..endTs
            return events.filter { it.ts in beginTs..endTs }
        }
    }

    private class FakeEventLog : EventLog {
        val appended = mutableListOf<RawEvent>()
        val cursors = mutableMapOf<String, Long>()

        override suspend fun appendAndAdvance(
            events: List<RawEvent>,
            cursorKey: String,
            cursorValue: Long,
        ) {
            appended += events
            cursors[cursorKey] = cursorValue
        }

        override suspend fun cursor(cursorKey: String): Long? = cursors[cursorKey]
    }

    private fun event(ts: Long) = RawEvent(
        ts = ts,
        type = EventType.APP_FOREGROUND,
        appPkg = "com.example.app",
        source = CollectorSource.USAGE_STATS,
    )

    private val now = 1_000_000_000L

    @Test
    fun `first run backfills the initial lookback window`() = runTest {
        val source = FakeSource(emptyList())
        val log = FakeEventLog()
        UsageStatsCollector(source, log) { now }.collectOnce()

        val expectedEnd = now - UsageStatsCollector.SAFETY_LAG_MS
        val expectedBegin = expectedEnd - UsageStatsCollector.INITIAL_LOOKBACK_MS + 1
        assertEquals(listOf(expectedBegin..expectedEnd), source.requestedRanges)
        assertEquals(expectedEnd, log.cursors[UsageStatsCollector.CURSOR_KEY])
    }

    @Test
    fun `events are persisted and cursor advances`() = runTest {
        val e1 = event(now - 60_000)
        val e2 = event(now - 30_000)
        val log = FakeEventLog()
        UsageStatsCollector(FakeSource(listOf(e1, e2)), log) { now }.collectOnce()

        assertEquals(listOf(e1, e2), log.appended)
        assertEquals(now - UsageStatsCollector.SAFETY_LAG_MS, log.cursors[UsageStatsCollector.CURSOR_KEY])
    }

    @Test
    fun `second run resumes from the cursor and re-ingests nothing`() = runTest {
        val e1 = event(now - 60_000)
        val source = FakeSource(listOf(e1))
        val log = FakeEventLog()
        val laterNow = now + 15 * 60_000

        UsageStatsCollector(source, log) { now }.collectOnce()
        UsageStatsCollector(source, log) { laterNow }.collectOnce()

        assertEquals("event must not be duplicated", listOf(e1), log.appended)
        val secondRange = source.requestedRanges[1]
        assertEquals(
            "second run must start right after the first cursor",
            now - UsageStatsCollector.SAFETY_LAG_MS + 1,
            secondRange.first,
        )
        assertEquals(laterNow - UsageStatsCollector.SAFETY_LAG_MS, log.cursors[UsageStatsCollector.CURSOR_KEY])
    }

    @Test
    fun `empty batch still advances the cursor`() = runTest {
        val log = FakeEventLog()
        UsageStatsCollector(FakeSource(emptyList()), log) { now }.collectOnce()
        assertTrue(log.appended.isEmpty())
        assertEquals(now - UsageStatsCollector.SAFETY_LAG_MS, log.cursors[UsageStatsCollector.CURSOR_KEY])
    }

    @Test
    fun `run with no elapsed time since cursor is a no-op`() = runTest {
        val source = FakeSource(emptyList())
        val log = FakeEventLog()
        UsageStatsCollector(source, log) { now }.collectOnce()
        // Same "now" again: endTs == cursor, nothing to query.
        UsageStatsCollector(source, log) { now }.collectOnce()
        assertEquals(1, source.requestedRanges.size)
    }
}
