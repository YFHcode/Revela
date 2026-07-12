package com.revela.insights.llm

import com.revela.analysis.CandidatePair
import com.revela.insights.llm.LlmGateway.LlmSafePayload
import com.revela.insights.llm.LlmGateway.Message
import org.json.JSONArray

/**
 * Lets the LLM CONTRIBUTE to discovery without doing discovery: it proposes
 * candidate (driver → outcome, lag) hypotheses to test, drawn from a catalog
 * of NAME-FREE daily series. The cross-stream engine then tests each with
 * Spearman correlation and Benjamini–Hochberg FDR control — so the LLM widens
 * the search space using world knowledge, but the statistics decide what is
 * real. The model can never fabricate a finding.
 *
 * Only name-free aggregate series are exposed (no contacts/places), so nothing
 * identifying leaves the device (D4).
 */
class HypothesisProposer(
    private val gateway: LlmGateway,
    private val config: LlmConfig,
    private val maxPairs: Int = 10,
) {

    suspend fun propose(existing: List<CandidatePair>): List<CandidatePair> {
        if (!config.active) return emptyList()

        val catalogText = CATALOG.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        val existingText = existing.joinToString("\n") {
            "- ${it.driverKey} -> ${it.outcomeKey} (lag ${it.lags})"
        }

        val message = gateway.chat(
            purpose = "hypothesis_proposal",
            model = config.queryModel,
            systemPrompt = PROMPT,
            messages = listOf(
                Message(
                    "user",
                    LlmSafePayload.seriesCatalog(
                        "Available series:\n$catalogText\n\nAlready tested:\n$existingText",
                    ),
                ),
            ),
        ) ?: return emptyList()

        return validate(message.optString("content"), existing, maxPairs)
    }

    companion object {
        /** series key → human description shown to the model. Name-free only. */
        val CATALOG = linkedMapOf(
            "screen_time" to "total daily screen time (seconds)",
            "pickups" to "number of phone pickups per day",
            "reflex_checks" to "number of sub-15-second 'quick check' pickups per day",
            "first_unlock_min" to "time of first phone pickup (minutes after midnight)",
            "evening_screen" to "screen time during the evening (18:00–24:00)",
            "night_screen" to "screen time late at night (00:00–04:00)",
        )

        /**
         * Pure validation of the model's proposals against the catalog — every
         * key must exist, driver != outcome, no duplicate of an already-tested
         * or repeated pair, lag clamped to [-3, 3]. Anything else is dropped,
         * so the model cannot inject a non-series or a pre-formed "finding".
         */
        fun validate(
            content: String,
            existing: List<CandidatePair>,
            maxPairs: Int = 10,
        ): List<CandidatePair> {
            val array = runCatching { JSONArray(extractJsonArray(content)) }.getOrNull()
                ?: return emptyList()
            val out = mutableListOf<CandidatePair>()
            val seen = existing.map { it.driverKey to it.outcomeKey }.toMutableSet()
            for (i in 0 until array.length()) {
                if (out.size >= maxPairs) break
                val obj = array.optJSONObject(i) ?: continue
                val driver = obj.optString("driver")
                val outcome = obj.optString("outcome")
                val lag = obj.optInt("lag", 1).coerceIn(-3, 3)
                if (driver !in CATALOG || outcome !in CATALOG || driver == outcome) continue
                if ((driver to outcome) in seen) continue
                seen += driver to outcome

                out += CandidatePair(
                    driverKey = driver,
                    driverLabel = CATALOG[driver]!!.substringBefore(" ("),
                    outcomeKey = outcome,
                    outcomeLabel = CATALOG[outcome]!!.substringBefore(" ("),
                    lags = if (lag <= 0) lag..0 else 0..lag,
                    driverIsTimeOfDay = driver == "first_unlock_min",
                    outcomeIsTimeOfDay = outcome == "first_unlock_min",
                )
            }
            return out
        }

        private fun extractJsonArray(content: String): String {
            val start = content.indexOf('[')
            val end = content.lastIndexOf(']')
            return if (start >= 0 && end > start) content.substring(start, end + 1) else "[]"
        }

        val PROMPT = """
            You help a behavioral-analysis engine decide WHICH relationships to
            test between a person's daily phone-usage series. You do NOT decide
            whether any relationship exists — a statistical test with false-
            discovery control does that. Your job is only to propose plausible,
            non-obvious (driver → outcome) pairs worth testing, with a lag in
            days (negative = driver leads within the same day; positive = driver
            precedes outcome by N days).
            Use only the series keys provided. Return JSON only, an array of
            {"driver": key, "outcome": key, "lag": int, "why": short reason}.
            Propose at most 10, favor pairs not already tested.
        """.trimIndent()
    }
}
