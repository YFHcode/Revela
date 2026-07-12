package com.revela.insights.llm

import com.revela.analysis.CandidatePair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The LLM may only PROPOSE from the catalog; validation is the guarantee it
 * can't fabricate a finding or reference anything but a real, name-free series.
 */
class HypothesisProposerTest {

    @Test
    fun `valid catalog pairs are accepted with correct lag direction`() {
        val json = """
            [
              {"driver":"night_screen","outcome":"first_unlock_min","lag":1,"why":"x"},
              {"driver":"reflex_checks","outcome":"screen_time","lag":0,"why":"y"}
            ]
        """.trimIndent()
        val pairs = HypothesisProposer.validate(json, existing = emptyList())
        assertEquals(2, pairs.size)
        assertEquals("night_screen", pairs[0].driverKey)
        assertEquals(0..1, pairs[0].lags)
        assertTrue(pairs[0].outcomeIsTimeOfDay)
    }

    @Test
    fun `unknown series keys are dropped`() {
        val json = """[{"driver":"bank_balance","outcome":"screen_time","lag":1}]"""
        assertTrue(HypothesisProposer.validate(json, emptyList()).isEmpty())
    }

    @Test
    fun `contact and place series are not in the catalog`() {
        val json = """[{"driver":"messages_with_alice","outcome":"gym_dwell","lag":1}]"""
        assertTrue(HypothesisProposer.validate(json, emptyList()).isEmpty())
    }

    @Test
    fun `self pairs and duplicates of already-tested pairs are dropped`() {
        val existing = listOf(
            CandidatePair("pickups", "pickups L", "screen_time", "screen L", 0..0),
        )
        val json = """
            [
              {"driver":"screen_time","outcome":"screen_time","lag":1},
              {"driver":"pickups","outcome":"screen_time","lag":0}
            ]
        """.trimIndent()
        assertTrue(HypothesisProposer.validate(json, existing).isEmpty())
    }

    @Test
    fun `lag is clamped to the supported range`() {
        val json = """[{"driver":"evening_screen","outcome":"first_unlock_min","lag":99}]"""
        val pairs = HypothesisProposer.validate(json, emptyList())
        assertEquals(0..3, pairs.single().lags)
    }

    @Test
    fun `garbage content yields no pairs`() {
        assertTrue(HypothesisProposer.validate("not json at all", emptyList()).isEmpty())
        assertTrue(HypothesisProposer.validate("", emptyList()).isEmpty())
    }

    @Test
    fun `proposal count is capped`() {
        val items = (1..20).joinToString(",") {
            """{"driver":"screen_time","outcome":"pickups","lag":$it}"""
        }
        // All duplicates of the same pair after the first — so at most 1 survives.
        val pairs = HypothesisProposer.validate("[$items]", emptyList(), maxPairs = 10)
        assertEquals(1, pairs.size)
    }
}
