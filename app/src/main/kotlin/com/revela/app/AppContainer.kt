package com.revela.app

import android.content.Context
import android.content.Intent
import com.revela.capture.AndroidUsageEventSource
import com.revela.capture.CalendarCollector
import com.revela.capture.CaptureGraph
import com.revela.capture.CaptureScheduler
import com.revela.capture.ContactResolver
import com.revela.capture.LocationCollector
import com.revela.capture.Permissions
import com.revela.capture.Phase2Graph
import com.revela.capture.Phase2Scheduler
import com.revela.capture.UsageStatsCollector
import com.revela.core.db.DatabaseFactory
import com.revela.core.db.DbKeyManager
import com.revela.core.db.RevelaDatabase
import com.revela.core.db.RoomEventLog
import com.revela.insights.AnalysisRunner
import com.revela.insights.InsightsGraph
import com.revela.insights.InsightsScheduler
import com.revela.insights.llm.HypothesisProposer
import com.revela.insights.llm.LlmConfig
import com.revela.insights.llm.LlmGateway
import com.revela.insights.llm.ModeNamer
import com.revela.insights.llm.OpenAiNarrator
import com.revela.insights.llm.QueryEngine
import com.revela.pipeline.PipelineGraph
import com.revela.pipeline.RollupRunner
import com.revela.pipeline.RollupScheduler
import kotlin.system.exitProcess

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

    val phase2Graph: Phase2Graph by lazy {
        Phase2Graph(
            eventLog = eventLog,
            contactResolver = ContactResolver(database),
            locationCollector = LocationCollector(
                context = appContext,
                eventLog = eventLog,
                hasPermission = { Permissions.hasForegroundLocation(appContext) },
            ),
            calendarCollector = CalendarCollector(
                context = appContext,
                eventLog = eventLog,
                hasPermission = { Permissions.hasCalendar(appContext) },
            ),
        )
    }

    val pipelineGraph: PipelineGraph by lazy {
        PipelineGraph(rollupRunner = RollupRunner(database))
    }

    val llmConfig: LlmConfig by lazy { LlmConfig(appContext) }

    val llmGateway: LlmGateway by lazy { LlmGateway(llmConfig, database) }

    val queryEngine: QueryEngine by lazy { QueryEngine(database, llmGateway, llmConfig) }

    val insightsGraph: InsightsGraph by lazy {
        InsightsGraph(
            analysisRunner = AnalysisRunner(
                db = database,
                appLabel = ::appLabel,
                narrator = OpenAiNarrator(llmGateway, llmConfig),
                modeNamer = ModeNamer(llmGateway, llmConfig),
                hypothesisProposer = HypothesisProposer(llmGateway, llmConfig),
            ),
        )
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))

    /** Full removal of one contact/place (D6): events, rollups, insights, entity. */
    suspend fun deleteEntity(id: Long) {
        com.revela.core.db.EntityEraser(database).erase(id)
    }

    /**
     * Full wipe (D6): stop all work, delete the encrypted database and its
     * key, clear preferences, then restart the process into a fresh install
     * state. Destructive by design — always behind a confirmation dialog.
     */
    fun fullWipeAndRestart() {
        CaptureScheduler.cancel(appContext)
        Phase2Scheduler.cancel(appContext)
        RollupScheduler.cancel(appContext)
        InsightsScheduler.cancel(appContext)
        runCatching { database.close() }
        appContext.deleteDatabase("revela.db")
        DbKeyManager(appContext).destroy()
        settings.wipe()

        val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        appContext.startActivity(intent)
        exitProcess(0)
    }
}
