package com.revela.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * M4 acceptance (§8.9): a planted lagged dependency is found at the right lag
 * and direction; an independent control (hardcoded, verified far from any BH
 * cutoff against a replica of Stats) yields zero discoveries.
 */
class CrossStreamTest {

    private val engine = CrossStreamEngine()
    private val start: LocalDate = LocalDate.parse("2026-05-01")
    private val historyDays = 45
    private val todayKey: String = start.plusDays(historyDays.toLong()).toString()
    private val now = 1_760_000_000_000L

    private fun points(values: DoubleArray) =
        values.mapIndexed { i, v -> DayPoint(start.plusDays(i.toLong()).toString(), v) }

    @Test
    fun `planted lag-1 dependency is discovered with the right direction`() {
        // first pickup minute tomorrow follows tonight's evening screen time.
        val outcome = DoubleArray(historyDays)
        outcome[0] = 400.0
        for (i in 1 until historyDays) outcome[i] = 300.0 + CONTROL_DRIVER_45[i - 1] / 60.0

        val series = mapOf(
            "evening_screen" to points(CONTROL_DRIVER_45),
            "first_unlock_min" to points(outcome),
        )
        val drafts = engine.generate(series, DefaultCandidatePairs.pairs, todayKey, now)

        assertEquals(1, drafts.size)
        val draft = drafts.single()
        assertEquals(InsightTypes.FEEDBACK_LOOP, draft.type)
        assertEquals("lagcorr:evening_screen>first_unlock_min@1", draft.dedupeKey)
        assertTrue("positive direction expected", draft.statPayload.contains("\"spearman_r\":1.0"))
        assertTrue("time-of-day wording expected", draft.text.contains("a later"))
    }

    @Test
    fun `independent control yields zero discoveries`() {
        val series = mapOf(
            "evening_screen" to points(CONTROL_DRIVER_45),
            "first_unlock_min" to points(CONTROL_OUTCOME_45),
        )
        val drafts = engine.generate(series, DefaultCandidatePairs.pairs, todayKey, now)
        assertTrue("expected silence, got $drafts", drafts.isEmpty())
    }

    @Test
    fun `too little overlap is not judged`() {
        val shortDriver = points(CONTROL_DRIVER_45.copyOfRange(0, 12))
        val shortOutcome = points(CONTROL_OUTCOME_45.copyOfRange(0, 12))
        val drafts = engine.generate(
            mapOf("evening_screen" to shortDriver, "first_unlock_min" to shortOutcome),
            DefaultCandidatePairs.pairs,
            todayKey,
            now,
        )
        assertTrue(drafts.isEmpty())
    }

    // --- Stats unit checks -------------------------------------------------

    @Test
    fun `spearman is 1 for any monotone relation`() {
        val x = doubleArrayOf(1.0, 3.0, 2.0, 8.0, 5.0)
        val y = DoubleArray(x.size) { x[it] * x[it] } // monotone, non-linear
        assertEquals(1.0, Stats.spearman(x, y), 1e-9)
    }

    @Test
    fun `spearman is minus 1 for a decreasing relation`() {
        val x = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val y = DoubleArray(x.size) { -x[it] }
        assertEquals(-1.0, Stats.spearman(x, y), 1e-9)
    }

    @Test
    fun `ties get average ranks`() {
        val ranks = Stats.ranks(doubleArrayOf(10.0, 20.0, 20.0, 30.0))
        assertEquals(1.0, ranks[0], 1e-9)
        assertEquals(2.5, ranks[1], 1e-9)
        assertEquals(2.5, ranks[2], 1e-9)
        assertEquals(4.0, ranks[3], 1e-9)
    }
}

// Independent draws, verified: |spearman| < 0.12 and p > 0.4 at lags 0 and 1.
internal val CONTROL_DRIVER_45 = doubleArrayOf(
    8862.3, 5095.5, 10567.8, 10538.7, 10419.8, 6459.7, 5717.2, 4435.0,
    8351.2, 6323.1, 8603.6, 3636.2, 8956.7, 7068.4, 7755.1, 8088.2,
    3676.9, 4882.0, 10611.6, 6440.6, 6710.4, 10007.3, 7865.1, 4462.8,
    10687.1, 6297.5, 6002.0, 7229.8, 8138.9, 7255.2, 6687.2, 8764.7,
    5108.3, 7552.5, 7832.3, 8632.8, 5435.3, 5220.2, 9386.1, 4774.3,
    3663.1, 5097.4, 8690.7, 7268.3, 4703.4,
)

internal val CONTROL_OUTCOME_45 = doubleArrayOf(
    449.2, 439.9, 400.7, 512.8, 512.4, 441.7, 460.8, 492.5,
    418.8, 416.8, 496.6, 462.8, 417.0, 465.3, 424.6, 422.2,
    419.3, 464.8, 429.5, 426.1, 428.0, 404.6, 478.6, 478.3,
    424.8, 455.6, 453.2, 519.9, 509.2, 459.0, 437.0, 515.5,
    420.7, 466.7, 494.7, 424.6, 493.3, 518.0, 500.4, 476.5,
    475.7, 505.6, 486.8, 518.5, 485.3,
)
