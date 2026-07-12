package com.revela.core.model

/**
 * One row of the append-only raw event log — the single source of truth.
 * Everything downstream (sessions, rollups, insights) is re-derivable from
 * these rows, so they are never mutated after insert.
 */
data class RawEvent(
    /** Event time (epoch millis, UTC) — the time the event happened, not insert time. */
    val ts: Long,
    val type: EventType,
    /** Package name for app/usage events, null otherwise. */
    val appPkg: String? = null,
    /** Resolved contact/place entity, when known (Phase 2+). */
    val entityId: Long? = null,
    /** Type-specific metadata as JSON. Minimal, metadata-only — never content. */
    val payload: String? = null,
    val source: CollectorSource,
)

enum class EventType {
    APP_FOREGROUND,
    APP_BACKGROUND,
    SCREEN_ON,
    SCREEN_OFF,
    KEYGUARD_SHOWN,
    UNLOCK,
    DEVICE_SHUTDOWN,
    DEVICE_STARTUP,

    // Phase 2
    NOTIFICATION,
    LOCATION_FIX,
    CALENDAR_EVENT,
}

enum class CollectorSource {
    USAGE_STATS,
    NOTIFICATION_LISTENER,
    LOCATION,
    CALENDAR,
    DEBUG_SEED,
}
