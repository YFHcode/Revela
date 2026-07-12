package com.revela.capture

import com.revela.core.model.EventLog

/**
 * Phase-2 collector dependencies. Provided by the Application via
 * [Phase2GraphOwner] and reached from the notification service and the
 * location/calendar workers through applicationContext.
 */
class Phase2Graph(
    val eventLog: EventLog,
    val contactResolver: ContactResolver,
    val notificationDebouncer: NotificationDebouncer = NotificationDebouncer(),
    val locationCollector: LocationCollector,
    val calendarCollector: CalendarCollector,
)

interface Phase2GraphOwner {
    val phase2Graph: Phase2Graph
}
