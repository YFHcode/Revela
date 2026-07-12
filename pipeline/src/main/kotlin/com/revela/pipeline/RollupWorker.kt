package com.revela.pipeline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class RollupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val owner = applicationContext as? PipelineGraphOwner ?: return Result.failure()
        return try {
            owner.pipelineGraph.rollupRunner.runOnce()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
