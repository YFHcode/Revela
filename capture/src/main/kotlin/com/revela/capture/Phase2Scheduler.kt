package com.revela.capture

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Phase2Scheduler {

    private const val PERIODIC_WORK = "phase2_sampling"

    /** ~every 20 min; location fixes are cheap at balanced-power priority. */
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<Phase2Worker>(20, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }
}
