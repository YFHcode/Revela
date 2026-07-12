package com.revela.insights.llm

import com.revela.insights.llm.LlmGateway.LlmSafePayload
import com.revela.insights.llm.LlmGateway.Message
import org.json.JSONObject

/**
 * L2 semantic labeling (§10): names and describes a mode that community
 * detection already found. The LLM interprets; it never discovers. Input is
 * pseudonym-safe (app labels + role placeholders + a time signature) — no
 * contact or place names leave the device (D4).
 */
class ModeNamer(
    private val gateway: LlmGateway,
    private val config: LlmConfig,
) {

    data class Named(val name: String, val description: String)

    suspend fun name(memberSummary: String, timeSignature: String): Named? {
        if (!config.active) return null
        val message = gateway.chat(
            purpose = "mode_naming",
            model = config.narrationModel,
            systemPrompt = PROMPT,
            messages = listOf(
                Message("user", LlmSafePayload.modeContext(memberSummary, timeSignature)),
            ),
        ) ?: return null

        val content = message.optString("content").trim()
        val json = runCatching { JSONObject(content) }.getOrNull() ?: return null
        val name = json.optString("name").trim()
        val description = json.optString("description").trim()
        if (name.isBlank() || name.length > 40 || description.length > 240) return null
        return Named(name, description)
    }

    companion object {
        val PROMPT = """
            You name a recurring "mode" — a cluster of things a person tends to
            do together at certain times — for a private behavioral-mirror app.
            You are given the mode's members (app names, place roles, and
            anonymous contact counts) and its time signature. Give it a short,
            neutral, evocative name and a one-sentence description.
            Rules: neutral and curious, never judgmental; no advice; don't
            invent members or names not given. Reply as JSON only:
            {"name": "...", "description": "..."}
        """.trimIndent()
    }
}
