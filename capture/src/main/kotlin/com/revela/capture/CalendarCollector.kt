package com.revela.capture

import android.content.Context
import android.provider.CalendarContract
import com.revela.core.model.CollectorSource
import com.revela.core.model.EventLog
import com.revela.core.model.EventType
import com.revela.core.model.RawEvent
import org.json.JSONObject

/**
 * §5.4 — reads calendar event titles/times as a cheap signal for recurring
 * social/other commitments. READ_CALENDAR only. Cursor-based over dtstart so
 * each event is recorded once.
 */
class CalendarCollector(
    private val context: Context,
    private val eventLog: EventLog,
    private val hasPermission: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun collectOnce() {
        if (!hasPermission()) return
        val since = eventLog.cursor(CALENDAR_WATERMARK) ?: (clock() - INITIAL_LOOKBACK_MS)
        val until = clock() + LOOKAHEAD_MS

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
        )
        val selection = "${CalendarContract.Events.DTSTART} > ? AND " +
            "${CalendarContract.Events.DTSTART} <= ?"
        val args = arrayOf(since.toString(), until.toString())

        val events = mutableListOf<RawEvent>()
        var maxStart = since
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            args,
            "${CalendarContract.Events.DTSTART} ASC",
        )?.use { cursor ->
            val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val startIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            while (cursor.moveToNext()) {
                val start = cursor.getLong(startIdx)
                val end = if (cursor.isNull(endIdx)) start else cursor.getLong(endIdx)
                val title = cursor.getString(titleIdx)?.trim().orEmpty()
                events += RawEvent(
                    ts = start,
                    type = EventType.CALENDAR_EVENT,
                    payload = JSONObject()
                        .put("title", title)
                        .put("end", end)
                        .toString(),
                    source = CollectorSource.CALENDAR,
                )
                if (start > maxStart) maxStart = start
            }
        }

        // Advance only past events already started, so future events are
        // re-read (their details may change) until they occur.
        val watermark = minOf(maxStart, clock())
        eventLog.appendAndAdvance(events, CALENDAR_WATERMARK, watermark)
    }

    companion object {
        const val CALENDAR_WATERMARK = "calendar.cursor"
        const val INITIAL_LOOKBACK_MS = 30L * 24 * 60 * 60 * 1000
        const val LOOKAHEAD_MS = 7L * 24 * 60 * 60 * 1000
    }
}
