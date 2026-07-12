package com.revela.app

import android.app.Application
import com.revela.capture.CaptureGraph
import com.revela.capture.CaptureGraphOwner
import com.revela.capture.CaptureScheduler
import com.revela.capture.Permissions

class RevelaApp : Application(), CaptureGraphOwner {

    lateinit var container: AppContainer
        private set

    override val captureGraph: CaptureGraph
        get() = container.captureGraph

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Re-arm the periodic capture on every process start; KEEP makes it a no-op
        // if already scheduled. Only once onboarding granted usage access.
        if (container.settings.onboardingComplete && Permissions.hasUsageAccess(this)) {
            CaptureScheduler.ensureScheduled(this)
            CaptureScheduler.captureNow(this)
        }
    }
}
