package com.revela.insights.llm

import com.revela.insights.llm.LlmGateway.LlmSafePayload
import com.revela.insights.llm.LlmGateway.Message

/**
 * L1 narration: rewrites an insight's templated text from its stat payload.
 * Output is validated — if the numbers in the template don't all survive, the
 * narration is rejected and the template stays. The LLM narrates; it never
 * computes.
 */
class OpenAiNarrator(
    private val gateway: LlmGateway,
    private val config: LlmConfig,
) {

    suspend fun narrate(type: String, statPayload: String, templateText: String): String? {
        if (!config.active) return null
        val message = gateway.chat(
            purpose = "narration",
            model = config.narrationModel,
            systemPrompt = STYLE_PROMPT,
            messages = listOf(
                Message("user", LlmSafePayload.insightStats(type, statPayload, templateText)),
            ),
        ) ?: return null

        val text = message.optString("content").trim()
        return text.takeIf {
            it.isNotBlank() && it.length <= MAX_LENGTH && NarrationValidator.keepsNumbers(templateText, it)
        }
    }

    companion object {
        const val MAX_LENGTH = 400

        val STYLE_PROMPT = """
            You rewrite one behavioral insight for a personal mirror app.
            Rules, all mandatory:
            - Neutral and curious. Describe, never judge. Forbidden: "wasted",
              "too much", "should", "bad", "guilty", streaks, goals, advice.
            - Second person ("you", "your").
            - Keep EVERY number, time and count from the reference wording,
              verbatim. Do not invent numbers, dates, or app names.
            - One or two sentences, under 350 characters. Plain text only.
            Reply with the rewritten insight text and nothing else.
        """.trimIndent()
    }
}

/** Pure validation, unit-tested on the JVM. */
object NarrationValidator {

    private val NUMBER = Regex("""\d+(?::\d+)?""")

    /** Every digit token (counts, hours like 08:03) in the template must survive. */
    fun keepsNumbers(template: String, candidate: String): Boolean {
        val required = NUMBER.findAll(template).map { it.value }.toSet()
        return required.all { candidate.contains(it) }
    }
}
