package com.revela.capture

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.revela.core.model.CollectorSource
import com.revela.core.model.EventType
import com.revela.core.model.RawEvent

/**
 * Maps [UsageStatsManager] events into raw-log rows.
 *
 * Note: ACTIVITY_RESUMED/ACTIVITY_PAUSED share integer values with the
 * pre-API-29 MOVE_TO_FOREGROUND/MOVE_TO_BACKGROUND constants, so the same
 * branch covers minSdk 28.
 */
class AndroidUsageEventSource(context: Context) : UsageEventSource {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    override fun eventsBetween(beginTs: Long, endTs: Long): List<RawEvent> {
        val out = mutableListOf<RawEvent>()
        val events = usageStatsManager.queryEvents(beginTs, endTs) ?: return out
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = mapType(event.eventType) ?: continue
            out += RawEvent(
                ts = event.timeStamp,
                type = type,
                appPkg = if (type == EventType.APP_FOREGROUND || type == EventType.APP_BACKGROUND) {
                    event.packageName
                } else {
                    null
                },
                source = CollectorSource.USAGE_STATS,
            )
        }
        return out
    }

    @Suppress("DEPRECATION") // MOVE_TO_* == ACTIVITY_RESUMED/PAUSED; usable from minSdk 28
    private fun mapType(eventType: Int): EventType? = when (eventType) {
        UsageEvents.Event.MOVE_TO_FOREGROUND -> EventType.APP_FOREGROUND
        UsageEvents.Event.MOVE_TO_BACKGROUND -> EventType.APP_BACKGROUND
        UsageEvents.Event.SCREEN_INTERACTIVE -> EventType.SCREEN_ON
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> EventType.SCREEN_OFF
        UsageEvents.Event.KEYGUARD_SHOWN -> EventType.KEYGUARD_SHOWN
        UsageEvents.Event.KEYGUARD_HIDDEN -> EventType.UNLOCK
        UsageEvents.Event.DEVICE_SHUTDOWN -> EventType.DEVICE_SHUTDOWN
        UsageEvents.Event.DEVICE_STARTUP -> EventType.DEVICE_STARTUP
        else -> null
    }
}
