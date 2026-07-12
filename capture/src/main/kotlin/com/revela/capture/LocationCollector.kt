package com.revela.capture

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.revela.core.model.CollectorSource
import com.revela.core.model.EventLog
import com.revela.core.model.EventType
import com.revela.core.model.RawEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * §5.3 — low-power significant-location sampling. One balanced-power fix per
 * scheduled run (~every 20 min via WorkManager), not high-rate GPS. Raw
 * points are written to the event log; clustering into places happens later
 * in analysis (§8.7), and only cluster membership + dwell go downstream (D5).
 */
class LocationCollector(
    private val context: Context,
    private val eventLog: EventLog,
    private val hasPermission: () -> Boolean,
) {

    @SuppressLint("MissingPermission") // guarded by hasPermission()
    suspend fun collectOnce() {
        if (!hasPermission()) return
        val client = LocationServices.getFusedLocationProviderClient(context)

        val cancellation = CancellationTokenSource()
        val location = suspendCancellableCoroutine<android.location.Location?> { cont ->
            cont.invokeOnCancellation { cancellation.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return

        val payload = JSONObject()
            .put("lat", location.latitude)
            .put("lon", location.longitude)
            .put("acc", location.accuracy.toDouble())
        eventLog.appendAndAdvance(
            events = listOf(
                RawEvent(
                    ts = System.currentTimeMillis(),
                    type = EventType.LOCATION_FIX,
                    payload = payload.toString(),
                    source = CollectorSource.LOCATION,
                ),
            ),
            cursorKey = LOCATION_WATERMARK,
            cursorValue = System.currentTimeMillis(),
        )
    }

    companion object {
        const val LOCATION_WATERMARK = "location.last_fix"
    }
}
