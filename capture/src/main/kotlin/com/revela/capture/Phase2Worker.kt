package com.revela.capture

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/** Periodic low-power location + calendar sampling (§5.3, §5.4). */
class Phase2Worker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val owner = applicationContext as? Phase2GraphOwner ?: return Result.success()
        val graph = owner.phase2Graph
        return try {
            graph.locationCollector.collectOnce()
            graph.calendarCollector.collectOnce()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
