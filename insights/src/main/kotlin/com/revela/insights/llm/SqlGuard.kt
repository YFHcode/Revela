package com.revela.insights.llm

/**
 * Validates model-proposed SQL before local execution (L3, §3.3): read-only,
 * single statement, whitelisted rollup/insight tables only — NEVER the raw
 * `events` log. Pure Kotlin, unit-tested on the JVM.
 */
object SqlGuard {

    // Only name-free numeric aggregate tables. `insights`, `comms_daily`,
    // `place_daily` and `entities` are excluded because they can contain
    // contact/place names — those must never reach the cloud (D4/§3.3).
    val ALLOWED_TABLES = setOf("day_summary", "usage_daily", "usage_hourly", "sessions")

    private val FORBIDDEN = Regex(
        """\b(insert|update|delete|drop|alter|create|attach|detach|pragma|vacuum|reindex|replace|events|watermarks|entities|llm_audit|insights|comms_daily|place_daily)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TABLE_REF = Regex("""(?i)\b(?:from|join)\s+([a-zA-Z_][a-zA-Z0-9_]*)""")

    /** Returns an error description, or null if the statement is allowed. */
    fun validate(sql: String): String? {
        val trimmed = sql.trim().removeSuffix(";").trim()
        if (trimmed.contains(';')) return "only a single statement is allowed"
        if (!trimmed.startsWith("select", ignoreCase = true)) return "only SELECT is allowed"
        FORBIDDEN.find(trimmed)?.let { return "'${it.value}' is not allowed" }

        val tables = TABLE_REF.findAll(trimmed).map { it.groupValues[1].lowercase() }.toList()
        if (tables.isEmpty()) return "query must read from a table"
        val outside = tables.filter { it !in ALLOWED_TABLES }
        if (outside.isNotEmpty()) return "table(s) not allowed: ${outside.joinToString()}"
        return null
    }
}
