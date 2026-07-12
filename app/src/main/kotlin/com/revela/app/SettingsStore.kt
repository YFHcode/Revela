package com.revela.app

import android.content.Context

/** App-level preferences: onboarding state and baseline-age bookkeeping. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("revela_prefs", Context.MODE_PRIVATE)

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    /** Epoch millis when observation started (set once, at onboarding finish). */
    var observationStart: Long
        get() = prefs.getLong(KEY_OBSERVATION_START, 0L)
        set(value) = prefs.edit().putLong(KEY_OBSERVATION_START, value).apply()

    /** Days of observation so far — drives the baseline-maturity indicator. */
    fun observedDays(now: Long = System.currentTimeMillis()): Int {
        if (observationStart == 0L) return 0
        return ((now - observationStart) / DAY_MS).toInt()
    }

    /** Capture pause switch (D6). Scheduling reacts to this in the UI layer. */
    var captureEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPTURE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CAPTURE_ENABLED, value).apply()

    fun wipe() {
        prefs.edit().clear().apply()
    }

    companion object {
        /** After this many days the baseline is considered mature (3 weekly cycles). */
        const val BASELINE_TARGET_DAYS = 21
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_OBSERVATION_START = "observation_start"
        private const val KEY_CAPTURE_ENABLED = "capture_enabled"
    }
}
