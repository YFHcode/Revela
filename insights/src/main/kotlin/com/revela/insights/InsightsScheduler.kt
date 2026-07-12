package com.revela.insights

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.revela.pipeline.RollupWorker
import java.util.concurrent.TimeUnit

object InsightsScheduler {

    private const val PERIODIC_WORK = "analysis_periodic"
    private const val IMMEDIATE_WORK = "analyze_now"

    /** Daily analysis pass, charging-constrained (§5.5). */
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<AnalysisWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Fresh rollup then analysis, e.g. when the insights feed opens. */
    fun analyzeNow(context: Context) {
        WorkManager.getInstance(context)
            .beginUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RollupWorker>().build(),
            )
            .then(OneTimeWorkRequestBuilder<AnalysisWorker>().build())
            .enqueue()
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }
}
