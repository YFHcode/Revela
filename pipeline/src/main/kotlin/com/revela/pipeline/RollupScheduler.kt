package com.revela.pipeline

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RollupScheduler {

    private const val PERIODIC_WORK = "rollup_periodic"
    private const val IMMEDIATE_WORK = "rollup_now"

    /** Periodic rollup, charging-constrained (§5.5). */
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<RollupWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Unconstrained one-shot, e.g. when the dashboard opens. Cheap at this scale. */
    fun rollupNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RollupWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }
}
