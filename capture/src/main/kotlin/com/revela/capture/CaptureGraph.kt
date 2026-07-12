package com.revela.capture

/**
 * Dependencies the capture workers need at runtime. The Application class
 * implements [CaptureGraphOwner]; workers reach it through their
 * applicationContext (manual DI, no framework).
 */
class CaptureGraph(
    val usageStatsCollector: UsageStatsCollector,
)

interface CaptureGraphOwner {
    val captureGraph: CaptureGraph
}
