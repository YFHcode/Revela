package com.revela.insights.llm

import androidx.sqlite.db.SimpleSQLiteQuery
import com.revela.core.db.RevelaDatabase
import com.revela.insights.llm.LlmGateway.LlmSafePayload
import com.revela.insights.llm.LlmGateway.Message
import org.json.JSONArray
import org.json.JSONObject

/**
 * L3 — the agentic query interface. The model composes read-only SQL; the app
 * executes it LOCALLY against whitelisted rollup tables (SqlGuard) and only
 * the aggregated result rows go back to the API. The raw event log is never
 * queryable.
 */
class QueryEngine(
    private val db: RevelaDatabase,
    private val gateway: LlmGateway,
    private val config: LlmConfig,
) {

    data class Turn(val role: String, val text: String)

    suspend fun ask(history: List<Turn>, question: String): String {
        if (!config.active) return OFFLINE_MESSAGE

        val rawHistory = JSONArray()
        for (turn in history.takeLast(8)) {
            rawHistory.put(JSONObject().put("role", turn.role).put("content", turn.text))
        }
        var pendingMessages = listOf(Message("user", LlmSafePayload.userQuestion(question)))

        repeat(MAX_TOOL_ROUNDS) {
            val message = gateway.chat(
                purpose = "query",
                model = config.queryModel,
                systemPrompt = SCHEMA_PROMPT,
                messages = pendingMessages,
                tools = TOOLS,
                rawHistory = rawHistory,
            ) ?: return ERROR_MESSAGE

            val toolCalls = message.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                return message.optString("content").ifBlank { ERROR_MESSAGE }
            }

            // Append the assistant tool-call turn, then each tool result, and loop.
            rawHistory.put(message)
            for (i in 0 until toolCalls.length()) {
                val call = toolCalls.getJSONObject(i)
                val callId = call.optString("id")
                val sql = runCatching {
                    JSONObject(call.getJSONObject("function").optString("arguments")).optString("sql")
                }.getOrDefault("")
                val result = runSql(sql)
                rawHistory.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", callId)
                        .put("content", LlmSafePayload.queryResult(result).content),
                )
            }
            pendingMessages = emptyList()
        }
        return ERROR_MESSAGE
    }

    private fun runSql(sql: String): String {
        SqlGuard.validate(sql)?.let { error -> return """{"error":${JSONObject.quote(error)}}""" }
        return runCatching {
            val rows = JSONArray()
            db.query(SimpleSQLiteQuery(sql)).use { cursor ->
                val columns = cursor.columnNames
                var count = 0
                while (cursor.moveToNext() && count < MAX_ROWS) {
                    val row = JSONObject()
                    for (c in columns.indices) {
                        row.put(columns[c], if (cursor.isNull(c)) JSONObject.NULL else cursor.getString(c))
                    }
                    rows.put(row)
                    count++
                }
            }
            JSONObject().put("rows", rows).toString()
        }.getOrElse { """{"error":"query failed"}""" }
    }

    companion object {
        const val MAX_TOOL_ROUNDS = 4
        const val MAX_ROWS = 50
        const val OFFLINE_MESSAGE =
            "The assistant needs an OpenAI API key (Settings → AI narration & chat)."
        const val ERROR_MESSAGE =
            "I couldn't reach the model just now — your data hasn't gone anywhere; try again later."

        val SCHEMA_PROMPT = """
            You are the query assistant inside Revela, a private behavioral-mirror
            app. You answer the owner's questions about their own phone-usage
            patterns using read-only SQL over these SQLite tables (aggregates only):

            day_summary(date TEXT 'yyyy-MM-dd', first_unlock INTEGER epoch-ms,
              last_use INTEGER epoch-ms, total_screen_time_s INTEGER,
              pickup_count INTEGER, reflex_check_count INTEGER,
              day_type TEXT WEEKDAY|WEEKEND, zone_id TEXT)
            usage_daily(date TEXT, app_pkg TEXT, total_seconds INTEGER,
              open_count INTEGER, first_use INTEGER, last_use INTEGER)
            usage_hourly(date TEXT, hour INTEGER 0-23, app_pkg TEXT,
              total_seconds INTEGER, open_count INTEGER)
            sessions(day_key TEXT, app_pkg TEXT, start_ts INTEGER, end_ts INTEGER,
              duration_s INTEGER, pickup_type TEXT NORMAL|REFLEX)

            (Communication and location data are intentionally not queryable —
            they stay fully on-device.)

            Use the run_readonly_sql tool (SELECT only) to fetch what you need,
            then answer in plain language. Style: neutral and curious, never
            judgmental — no "wasted", no "should", no advice unless asked.
            State numbers plainly (convert seconds to hours/minutes). If the
            data can't answer the question, say so.
        """.trimIndent()

        val TOOLS: JSONArray = JSONArray().put(
            JSONObject()
                .put("type", "function")
                .put(
                    "function",
                    JSONObject()
                        .put("name", "run_readonly_sql")
                        .put(
                            "description",
                            "Run a single read-only SELECT over the summary tables. Returns {rows:[...]} (max 50 rows).",
                        )
                        .put(
                            "parameters",
                            JSONObject()
                                .put("type", "object")
                                .put(
                                    "properties",
                                    JSONObject().put(
                                        "sql",
                                        JSONObject().put("type", "string")
                                            .put("description", "The SELECT statement."),
                                    ),
                                )
                                .put("required", JSONArray().put("sql")),
                        ),
                ),
        )
    }
}
