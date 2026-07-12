package com.revela.app

import android.content.Context

/** App-level preferences: onboarding state and the silent-observation window (P5/Q4). */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("revela_prefs", Context.MODE_PRIVATE)

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    /** Epoch millis when observation started (set once, at onboarding finish). */
    var observationStart: Long
        get() = prefs.getLong(KEY_OBSERVATION_START, 0L)
        set(value) = prefs.edit().putLong(KEY_OBSERVATION_START, value).apply()

    /** Silent-window length in days; default 21, user-adjustable 7–28 (Q4). */
    var silentWindowDays: Int
        get() = prefs.getInt(KEY_SILENT_WINDOW_DAYS, DEFAULT_SILENT_WINDOW_DAYS)
        set(value) = prefs.edit().putInt(KEY_SILENT_WINDOW_DAYS, value.coerceIn(7, 28)).apply()

    fun silentDaysElapsed(now: Long = System.currentTimeMillis()): Int {
        if (observationStart == 0L) return 0
        return ((now - observationStart) / DAY_MS).toInt()
    }

    fun silentWindowOver(now: Long = System.currentTimeMillis()): Boolean =
        silentDaysElapsed(now) >= silentWindowDays

    /** Capture pause switch (D6). Scheduling reacts to this in the UI layer. */
    var captureEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPTURE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CAPTURE_ENABLED, value).apply()

    /** Developer mode: bypasses the silent/dashboard windows for dogfooding. */
    var devMode: Boolean
        get() = prefs.getBoolean(KEY_DEV_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DEV_MODE, value).apply()

    /** Dashboard unlocks at day 8 (PLAN.md Q4); insights at the full window. */
    fun dashboardUnlocked(now: Long = System.currentTimeMillis()): Boolean =
        devMode || silentDaysElapsed(now) >= DASHBOARD_UNLOCK_DAYS

    fun wipe() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_SILENT_WINDOW_DAYS = 21
        const val DASHBOARD_UNLOCK_DAYS = 8
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_OBSERVATION_START = "observation_start"
        private const val KEY_SILENT_WINDOW_DAYS = "silent_window_days"
        private const val KEY_CAPTURE_ENABLED = "capture_enabled"
        private const val KEY_DEV_MODE = "dev_mode"
    }
}
