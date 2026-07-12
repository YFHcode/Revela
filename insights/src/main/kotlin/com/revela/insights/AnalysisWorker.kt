package com.revela.insights

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class AnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val owner = applicationContext as? InsightsGraphOwner ?: return Result.failure()
        return try {
            owner.insightsGraph.analysisRunner.runOnce()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}

class InsightsGraph(
    val analysisRunner: AnalysisRunner,
)

interface InsightsGraphOwner {
    val insightsGraph: InsightsGraph
}
