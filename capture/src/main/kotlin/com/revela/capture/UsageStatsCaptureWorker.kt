package com.revela.capture

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class UsageStatsCaptureWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val owner = applicationContext as? CaptureGraphOwner ?: return Result.failure()
        if (!Permissions.hasUsageAccess(applicationContext)) {
            // Grant revoked; nothing to collect until the user re-enables it.
            return Result.success()
        }
        return try {
            owner.captureGraph.usageStatsCollector.collectOnce()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
