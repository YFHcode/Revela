package com.revela.insights.llm

import com.revela.core.db.LlmAuditEntity
import com.revela.core.db.RevelaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The single network chokepoint of the entire app (§3.3). Everything sent is
 * audit-logged BEFORE the request, so the "what left the device" screen is
 * complete by construction. Callers build message content exclusively from
 * [LlmSafePayload] values — aggregates, never raw events or content.
 */
class LlmGateway(
    private val config: LlmConfig,
    private val db: RevelaDatabase,
) {

    /** One cleared unit of outbound content. Constructible only from aggregates. */
    class LlmSafePayload private constructor(val content: String) {
        companion object {
            /** Insight type + stat payload + templated text — what an insight card shows. */
            fun insightStats(type: String, statPayload: String, templateText: String) =
                LlmSafePayload("Insight type: $type\nNumbers: $statPayload\nReference wording: $templateText")

            /** The user's own typed question. */
            fun userQuestion(question: String) = LlmSafePayload(question)

            /** Rows from a whitelisted read-only query over rollup tables. */
            fun queryResult(rowsJson: String) = LlmSafePayload(rowsJson)

            /** A community's pseudonym-safe member summary + time signature (no names). */
            fun modeContext(memberSummary: String, timeSignature: String) =
                LlmSafePayload("Members: $memberSummary\nWhen: $timeSignature")

            /** A catalog of name-free daily series the model may propose pairings over. */
            fun seriesCatalog(catalog: String) = LlmSafePayload(catalog)
        }
    }

    data class Message(val role: String, val payload: LlmSafePayload)

    /**
     * POST /v1/chat/completions. Returns the first choice `message` object,
     * or null on any failure (callers fall back to templates).
     */
    suspend fun chat(
        purpose: String,
        model: String,
        systemPrompt: String,
        messages: List<Message>,
        tools: JSONArray? = null,
        rawHistory: JSONArray? = null,
    ): JSONObject? {
        val key = config.apiKey
        if (!config.enabled || key.isNullOrBlank()) return null

        val messageArray = JSONArray()
        messageArray.put(JSONObject().put("role", "system").put("content", systemPrompt))
        rawHistory?.let { history ->
            for (i in 0 until history.length()) messageArray.put(history.get(i))
        }
        for (m in messages) {
            messageArray.put(JSONObject().put("role", m.role).put("content", m.payload.content))
        }

        val body = JSONObject()
            .put("model", model)
            .put("messages", messageArray)
        tools?.let { body.put("tools", it) }

        // Audit BEFORE sending: the log can never miss a payload (D4).
        db.llmAuditDao().insert(
            LlmAuditEntity(ts = System.currentTimeMillis(), purpose = purpose, payload = body.toString()),
        )

        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 60_000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer $key")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                    if (connection.responseCode !in 200..299) return@runCatching null
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(response)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }

    /**
     * GET /v1/models — the chat-capable models this key can access. Doubles as
     * the key test: null means the key (or network) doesn't work. Sends no
     * user data; still audit-logged for completeness.
     */
    suspend fun listModels(): List<String>? {
        val key = config.apiKey
        if (key.isNullOrBlank()) return null

        db.llmAuditDao().insert(
            LlmAuditEntity(
                ts = System.currentTimeMillis(),
                purpose = "list_models",
                payload = """{"request":"GET /v1/models","body":"none"}""",
            ),
        )

        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(MODELS_ENDPOINT).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    connection.setRequestProperty("Authorization", "Bearer $key")
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val data = JSONObject(response).getJSONArray("data")
                    buildList {
                        for (i in 0 until data.length()) {
                            val id = data.getJSONObject(i).optString("id")
                            if (isChatModel(id)) add(id)
                        }
                    }.sorted()
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }

    private fun isChatModel(id: String): Boolean =
        (id.startsWith("gpt-") || id.startsWith("chatgpt") || Regex("^o\\d").containsMatchIn(id)) &&
            listOf("audio", "realtime", "transcribe", "tts", "search", "image", "instruct")
                .none { id.contains(it) }

    companion object {
        /** api.openai.com is the only remote host in the codebase — CI enforces this. */
        const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val MODELS_ENDPOINT = "https://api.openai.com/v1/models"
    }
}
