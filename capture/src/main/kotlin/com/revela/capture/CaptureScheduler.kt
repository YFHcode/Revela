package com.revela.capture

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CaptureScheduler {

    private const val PERIODIC_WORK = "usage_stats_capture"
    private const val IMMEDIATE_WORK = "usage_stats_capture_now"

    /** Periodic capture every 15 min (Q5); KEEP so re-calls are no-ops. */
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageStatsCaptureWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One-shot capture, e.g. right after onboarding or on app open. */
    fun captureNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<UsageStatsCaptureWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** Pause capture (D6). */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }
}
