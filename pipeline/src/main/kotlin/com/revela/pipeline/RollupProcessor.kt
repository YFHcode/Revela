package com.revela.pipeline

import com.revela.core.model.AppSession
import com.revela.core.model.DailyUsage
import com.revela.core.model.DayKeys
import com.revela.core.model.DaySummary
import com.revela.core.model.EventType
import com.revela.core.model.HourlyUsage
import com.revela.core.model.PickupType
import com.revela.core.model.RawEvent
import com.revela.core.model.RollupResult
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Stage 2 of the pipeline (§7): folds the raw event log into sessions and
 * hourly/daily summaries. Pure Kotlin — no Android, no I/O — so every rule
 * here is unit-tested on the JVM.
 *
 * Semantics:
 *  - APP session: APP_FOREGROUND opens; the next APP_BACKGROUND (same pkg),
 *    APP_FOREGROUND (other pkg), SCREEN_OFF, or DEVICE_SHUTDOWN closes.
 *    Sessions are clamped to [maxSessionMs] (data-gap tolerance) and sessions
 *    under 1s are dropped.
 *  - PICKUP: UNLOCK → next SCREEN_OFF/DEVICE_SHUTDOWN. Pickups shorter than
 *    [reflexThresholdMs] are "reflex checks" (Q7); app sessions started inside
 *    one are tagged REFLEX.
 *  - Day attribution follows [DayKeys]: behavioral 4 a.m. day for daily
 *    tables, calendar date for hourly.
 */
class RollupProcessor(
    private val zone: ZoneId,
    private val reflexThresholdMs: Long = 15_000L,
    private val maxSessionMs: Long = 6 * 60 * 60 * 1000L,
) {

    private data class Pickup(val startTs: Long, val endTs: Long) {
        val durationMs: Long get() = endTs - startTs
    }

    fun process(events: List<RawEvent>): RollupResult {
        val sorted = events.sortedBy { it.ts }
        val pickups = buildPickups(sorted)
        val sessions = buildSessions(sorted, pickups)
        return RollupResult(
            sessions = sessions,
            hourly = buildHourly(sessions),
            daily = buildDaily(sessions),
            days = buildDaySummaries(sessions, pickups),
        )
    }

    private fun buildPickups(sorted: List<RawEvent>): List<Pickup> {
        val pickups = mutableListOf<Pickup>()
        var openedAt: Long? = null
        for (event in sorted) {
            when (event.type) {
                EventType.UNLOCK -> if (openedAt == null) openedAt = event.ts
                EventType.SCREEN_OFF, EventType.DEVICE_SHUTDOWN -> {
                    openedAt?.let { start ->
                        if (event.ts > start) pickups += Pickup(start, event.ts)
                    }
                    openedAt = null
                }
                else -> Unit
            }
        }
        return pickups
    }

    private fun buildSessions(sorted: List<RawEvent>, pickups: List<Pickup>): List<AppSession> {
        val reflexWindows = pickups.filter { it.durationMs < reflexThresholdMs }
        val sessions = mutableListOf<AppSession>()
        var currentPkg: String? = null
        var currentStart = 0L

        fun close(endTs: Long) {
            val pkg = currentPkg ?: return
            currentPkg = null
            val end = minOf(endTs, currentStart + maxSessionMs)
            if (end - currentStart < 1000) return
            val pickupType =
                if (reflexWindows.any { currentStart >= it.startTs && currentStart < it.endTs }) {
                    PickupType.REFLEX
                } else {
                    PickupType.NORMAL
                }
            sessions += AppSession(
                dayKey = DayKeys.dayKey(currentStart, zone),
                appPkg = pkg,
                startTs = currentStart,
                endTs = end,
                pickupType = pickupType,
            )
        }

        for (event in sorted) {
            when (event.type) {
                EventType.APP_FOREGROUND -> {
                    close(event.ts)
                    currentPkg = event.appPkg
                    currentStart = event.ts
                }
                EventType.APP_BACKGROUND ->
                    if (event.appPkg == currentPkg) close(event.ts)
                EventType.SCREEN_OFF, EventType.DEVICE_SHUTDOWN -> close(event.ts)
                else -> Unit
            }
        }
        // An unclosed session at log end: clamp to its own start + cap. It will
        // be recomputed correctly on the next rollup once its close event lands.
        currentPkg?.let {
            val lastTs = sorted.last().ts
            close(lastTs)
        }
        return sessions
    }

    private fun buildHourly(sessions: List<AppSession>): List<HourlyUsage> {
        data class Key(val date: String, val hour: Int, val pkg: String)

        val seconds = mutableMapOf<Key, Long>()
        val opens = mutableMapOf<Key, Int>()

        for (session in sessions) {
            var cursor = session.startTs
            var first = true
            while (cursor < session.endTs) {
                val zdt = Instant.ofEpochMilli(cursor).atZone(zone)
                val hourEnd = zdt.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
                val sliceEnd = minOf(session.endTs, hourEnd)
                val key = Key(zdt.toLocalDate().toString(), zdt.hour, session.appPkg)
                seconds.merge(key, sliceEnd - cursor, Long::plus)
                if (first) {
                    opens.merge(key, 1, Int::plus)
                    first = false
                }
                cursor = sliceEnd
            }
        }
        return seconds.map { (key, ms) ->
            HourlyUsage(
                date = key.date,
                hour = key.hour,
                appPkg = key.pkg,
                totalSeconds = (ms / 1000).toInt(),
                openCount = opens[key] ?: 0,
            )
        }.sortedWith(compareBy({ it.date }, { it.hour }, { it.appPkg }))
    }

    private fun buildDaily(sessions: List<AppSession>): List<DailyUsage> =
        sessions.groupBy { it.dayKey to it.appPkg }.map { (key, group) ->
            DailyUsage(
                dayKey = key.first,
                appPkg = key.second,
                totalSeconds = group.sumOf { it.durationS },
                openCount = group.size,
                firstUse = group.minOf { it.startTs },
                lastUse = group.maxOf { it.endTs },
            )
        }.sortedWith(compareBy({ it.dayKey }, { it.appPkg }))

    private fun buildDaySummaries(
        sessions: List<AppSession>,
        pickups: List<Pickup>,
    ): List<DaySummary> {
        val pickupsByDay = pickups.groupBy { DayKeys.dayKey(it.startTs, zone) }
        val sessionsByDay = sessions.groupBy { it.dayKey }
        val dayKeys = pickupsByDay.keys + sessionsByDay.keys

        return dayKeys.map { dayKey ->
            val dayPickups = pickupsByDay[dayKey].orEmpty()
            val daySessions = sessionsByDay[dayKey].orEmpty()
            // Screen time = sum of pickup (unlock→screen-off) durations; if
            // keyguard events are missing, fall back to app-session time.
            val screenTimeS = if (dayPickups.isNotEmpty()) {
                (dayPickups.sumOf { it.durationMs } / 1000).toInt()
            } else {
                daySessions.sumOf { it.durationS }
            }
            DaySummary(
                dayKey = dayKey,
                firstUnlock = dayPickups.minOfOrNull { it.startTs },
                lastUse = maxOf(
                    dayPickups.maxOfOrNull { it.endTs } ?: Long.MIN_VALUE,
                    daySessions.maxOfOrNull { it.endTs } ?: Long.MIN_VALUE,
                ).takeIf { it != Long.MIN_VALUE },
                totalScreenTimeS = screenTimeS,
                pickupCount = dayPickups.size,
                reflexCheckCount = dayPickups.count { it.durationMs < reflexThresholdMs },
                dayType = DayKeys.dayType(dayKey),
                zoneId = zone.id,
            )
        }.sortedBy { it.dayKey }
    }
}
