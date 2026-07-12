package com.revela.capture

import com.revela.core.model.RawEvent

/**
 * Abstraction over [android.app.usage.UsageStatsManager.queryEvents] so the
 * collector logic can be unit-tested on the JVM with fake streams.
 */
interface UsageEventSource {
    /** All system usage events with `beginTs <= ts <= endTs` (inclusive), mapped to [RawEvent]s. */
    fun eventsBetween(beginTs: Long, endTs: Long): List<RawEvent>
}
