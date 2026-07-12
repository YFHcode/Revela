package com.revela.app

import android.content.Context
import com.revela.capture.AndroidUsageEventSource
import com.revela.capture.CaptureGraph
import com.revela.capture.UsageStatsCollector
import com.revela.core.db.DatabaseFactory
import com.revela.core.db.RevelaDatabase
import com.revela.core.db.RoomEventLog

/** Manual DI. Single-user app; a service locator is all we need. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    val database: RevelaDatabase by lazy { DatabaseFactory.create(appContext) }

    private val eventLog by lazy { RoomEventLog(database) }

    val captureGraph: CaptureGraph by lazy {
        CaptureGraph(
            usageStatsCollector = UsageStatsCollector(
                source = AndroidUsageEventSource(appContext),
                eventLog = eventLog,
            ),
        )
    }
}
