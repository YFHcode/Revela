package com.revela.capture

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.revela.core.model.CollectorSource
import com.revela.core.model.EventType
import com.revela.core.model.RawEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * §5.2 — records communication TIMING metadata only. Persists package,
 * postTime, and the sender/title needed to resolve a contact. NEVER stores
 * message bodies (D7): `notification.extras` text fields are read only to get
 * the title, and only the resolved contact id + package land in the log.
 */
class RevelaNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val owner = applicationContext as? Phase2GraphOwner ?: return
        val graph = owner.phase2Graph

        // Skip ongoing/system notifications (music, downloads, foreground svcs).
        if (sbn.isOngoing) return
        val pkg = sbn.packageName ?: return
        if (pkg == applicationContext.packageName) return

        val postTime = sbn.postTime
        val key = sbn.key ?: "${sbn.id}"
        if (!graph.notificationDebouncer.shouldRecord(pkg, key, postTime)) return

        // Title only — never android.text (the body).
        val title = sbn.notification?.extras
            ?.getCharSequence("android.title")?.toString()?.trim()

        scope.launch {
            val contactId = title?.let { graph.contactResolver.resolve(it) }
            graph.eventLog.appendAndAdvance(
                events = listOf(
                    RawEvent(
                        ts = postTime,
                        type = EventType.NOTIFICATION,
                        appPkg = pkg,
                        entityId = contactId,
                        payload = JSONObject().put("k", key).toString(),
                        source = CollectorSource.NOTIFICATION_LISTENER,
                    ),
                ),
                // Notifications are event-driven, not cursor-based; keep a
                // best-effort watermark of the latest postTime seen.
                cursorKey = NOTIFICATION_WATERMARK,
                cursorValue = postTime,
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_WATERMARK = "notification.last_post"
    }
}
