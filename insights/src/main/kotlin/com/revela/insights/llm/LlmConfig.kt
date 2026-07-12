package com.revela.insights.llm

import android.content.Context

/** LLM-layer settings (Q2/Q3): user-supplied OpenAI key, kill switch, models. */
class LlmConfig(context: Context) {

    private val prefs = context.getSharedPreferences("revela_llm", Context.MODE_PRIVATE)
    private val secret = SecretStore(context, "revela_openai_key", "revela_llm_secret")

    var apiKey: String?
        get() = secret.get()
        set(value) = secret.set(value)

    /** Master switch; off reverts everything to templates instantly. */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /** User-selected model, used for both narration and query. */
    var model: String
        get() = prefs.getString("model", DEFAULT_MODEL)!!
        set(value) = prefs.edit().putString("model", value).apply()

    val narrationModel: String get() = model

    val queryModel: String get() = model

    val active: Boolean
        get() = enabled && !apiKey.isNullOrBlank()

    fun clear() {
        secret.set(null)
        prefs.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
