package com.revela.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SequencesTest {

    private val zone = ZoneId.of("Europe/Paris")

    private fun ts(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    // --- SequenceMiner -----------------------------------------------------

    @Test
    fun `frequent ordered pattern is mined with correct support`() {
        val sequences = listOf(
            listOf("a", "b", "c"),
            listOf("a", "b", "c"),
            listOf("a", "x", "b", "c"),
            listOf("b", "c"),
            listOf("a", "b"),
        )
        val patterns = SequenceMiner.mine(sequences, minSupport = 3)
        assertTrue(patterns.contains(SequenceMiner.Pattern(listOf("a", "b", "c"), 3)))
        assertTrue(patterns.contains(SequenceMiner.Pattern(listOf("b", "c"), 4)))
    }

    @Test
    fun `infrequent patterns are not reported`() {
        val patterns = SequenceMiner.mine(
            listOf(listOf("a", "b"), listOf("c", "d"), listOf("e", "f")),
            minSupport = 2,
        )
        assertTrue(patterns.isEmpty())
    }

    // --- Bursts ------------------------------------------------------------

    @Test
    fun `sessions within the gap merge into one burst with consecutive dedupe`() {
        val sessions = listOf(
            Bursts.SessionRow("wa", ts("2026-07-10T08:00:00"), ts("2026-07-10T08:01:00")),
            Bursts.SessionRow("wa", ts("2026-07-10T08:01:20"), ts("2026-07-10T08:02:00")),
            Bursts.SessionRow("gmail", ts("2026-07-10T08:02:30"), ts("2026-07-10T08:04:00")),
            // > 60s gap → new burst
            Bursts.SessionRow("maps", ts("2026-07-10T08:10:00"), ts("2026-07-10T08:12:00")),
        )
        val bursts = Bursts.build(sessions)
        assertEquals(2, bursts.size)
        assertEquals(listOf("wa", "gmail"), bursts[0].apps)
        assertEquals(listOf("maps"), bursts[1].apps)
    }

    @Test
    fun `morning sequences take the first morning burst per day`() {
        val sessions = listOf(
            // Day 1: night burst (03:00, belongs to previous behavioral day's night)
            Bursts.SessionRow("x", ts("2026-07-10T03:00:00"), ts("2026-07-10T03:01:00")),
            // Day 1: first morning burst
            Bursts.SessionRow("wa", ts("2026-07-10T07:30:00"), ts("2026-07-10T07:31:00")),
            Bursts.SessionRow("gmail", ts("2026-07-10T07:31:30"), ts("2026-07-10T07:33:00")),
            // Day 1: later morning burst (ignored — not first)
            Bursts.SessionRow("maps", ts("2026-07-10T10:00:00"), ts("2026-07-10T10:05:00")),
            // Day 2: first morning burst
            Bursts.SessionRow("news", ts("2026-07-11T08:00:00"), ts("2026-07-11T08:02:00")),
        )
        val sequences = Bursts.morningSequences(Bursts.build(sessions), zone)
        assertEquals(2, sequences.size)
        assertTrue(sequences.contains(listOf("wa", "gmail")))
        assertTrue(sequences.contains(listOf("news")))
    }

    // --- RoutineEngine -----------------------------------------------------

    @Test
    fun `planted morning routine is surfaced`() {
        val mornings = buildList {
            repeat(9) { i -> add(listOf("wa", "gmail", "insta", "noise$i")) }
            add(listOf("solo1"))
            add(listOf("solo2"))
            add(listOf("solo3"))
        }
        val drafts = RoutineEngine().generate(
            mapOf("morning" to mornings),
            label = { it.uppercase() },
            now = 1_760_000_000_000L,
        )
        assertEquals(1, drafts.size)
        val draft = drafts.single()
        assertEquals(InsightTypes.ROUTINE, draft.type)
        assertEquals("routine:morning:wa>gmail>insta", draft.dedupeKey)
        assertTrue(draft.text.contains("WA → GMAIL → INSTA"))
        assertTrue(draft.statPayload.contains("\"days_matched\":9"))
    }

    @Test
    fun `distinct mornings produce no routine`() {
        val mornings = (0 until 12).map { i -> listOf("a$i", "b$i", "c$i") }
        val drafts = RoutineEngine().generate(
            mapOf("morning" to mornings),
            label = { it },
            now = 0L,
        )
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun `too few days are not judged`() {
        val mornings = (0 until 5).map { listOf("wa", "gmail") }
        assertTrue(RoutineEngine().generate(mapOf("morning" to mornings), { it }, 0L).isEmpty())
    }
}
