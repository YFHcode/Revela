package com.revela.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphBuilderTest {

    private fun act(id: Long, day: String, part: Int) = GraphBuilder.Activity(id, day, part)

    @Test
    fun `entities in the same window get a co-occurrence edge`() {
        val edges = GraphBuilder.buildEdges(
            listOf(
                act(1, "2026-07-01", 3),
                act(2, "2026-07-01", 3),
                act(1, "2026-07-02", 3),
                act(2, "2026-07-02", 3),
            ),
        )
        assertEquals(1, edges.size)
        assertEquals(2.0, edges.single().weight, 1e-9)
    }

    @Test
    fun `entities in different windows do not co-occur`() {
        val edges = GraphBuilder.buildEdges(
            listOf(
                act(1, "2026-07-01", 1),
                act(2, "2026-07-01", 3),
            ),
        )
        assertTrue(edges.isEmpty())
    }

    @Test
    fun `oversized windows are skipped as too diffuse`() {
        val activities = (1L..15L).map { act(it, "2026-07-01", 2) }
        assertTrue(GraphBuilder.buildEdges(activities, maxPerWindow = 12).isEmpty())
    }

    @Test
    fun `time signature reports the dominant daypart and weekend share`() {
        // 2026-07-04 is a Saturday; 2026-07-07 a Tuesday.
        val activities = listOf(
            act(1, "2026-07-04", 3), act(2, "2026-07-04", 3),
            act(1, "2026-07-11", 3), act(2, "2026-07-11", 3), // Sat
            act(1, "2026-07-07", 3), act(2, "2026-07-07", 3), // Tue
        )
        val sig = GraphBuilder.timeSignature(setOf(1L, 2L), activities)
        assertEquals(3, sig.topDaypart) // evening
        assertEquals(1.0, sig.topDaypartShare, 1e-9)
        assertTrue("weekend share ${sig.weekendShare}", sig.weekendShare > 0.6)
    }

    @Test
    fun `daypart boundaries map correctly`() {
        assertEquals(0, GraphBuilder.daypart(2))
        assertEquals(1, GraphBuilder.daypart(8))
        assertEquals(2, GraphBuilder.daypart(14))
        assertEquals(3, GraphBuilder.daypart(20))
    }
}
