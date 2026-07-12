package com.revela.app

import android.app.Application
import com.revela.capture.CaptureGraph
import com.revela.capture.CaptureGraphOwner
import com.revela.capture.CaptureScheduler
import com.revela.capture.Permissions
import com.revela.pipeline.PipelineGraph
import com.revela.pipeline.PipelineGraphOwner
import com.revela.pipeline.RollupScheduler

class RevelaApp : Application(), CaptureGraphOwner, PipelineGraphOwner {

    lateinit var container: AppContainer
        private set

    override val captureGraph: CaptureGraph
        get() = container.captureGraph

    override val pipelineGraph: PipelineGraph
        get() = container.pipelineGraph

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Re-arm periodic work on every process start; KEEP makes re-arming a
        // no-op. Only once onboarding granted usage access, and not while the
        // user has paused capture.
        val settings = container.settings
        if (settings.onboardingComplete && settings.captureEnabled && Permissions.hasUsageAccess(this)) {
            CaptureScheduler.ensureScheduled(this)
            CaptureScheduler.captureNow(this)
            RollupScheduler.ensureScheduled(this)
        }
    }
}
