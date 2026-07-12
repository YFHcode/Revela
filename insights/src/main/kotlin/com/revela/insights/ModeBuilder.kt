package com.revela.insights

import com.revela.analysis.GraphBuilder
import com.revela.analysis.Louvain
import com.revela.core.db.EntityKind
import com.revela.core.db.ModeEntity
import com.revela.core.db.RevelaDatabase
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId

/**
 * Builds the life-graph from rollups, runs community detection (statistics),
 * and writes the resulting "modes" (§11). The LLM later names them
 * (ModeNamer) — it never finds them.
 *
 * Node set is bounded to the top apps plus all contacts/places so windows
 * stay meaningful and the graph small.
 */
class ModeBuilder(
    private val db: RevelaDatabase,
    private val appLabel: (String) -> String,
    private val topApps: Int = 15,
    private val minMembers: Int = 3,
    private val minWindows: Int = 6,
) {

    suspend fun runOnce(zone: ZoneId) {
        val appResolver = AppEntityResolver(db, appLabel)

        // Which apps are "significant" (bound the node set).
        val topPkgs = db.usageDailyDao().all()
            .groupBy { it.appPkg }
            .mapValues { (_, rows) -> rows.sumOf { it.totalSeconds } }
            .entries.sortedByDescending { it.value }
            .take(topApps).map { it.key }.toSet()

        val activities = mutableListOf<GraphBuilder.Activity>()

        // Apps: one activity per session (its start daypart).
        for (session in db.sessionDao().allOrdered()) {
            if (session.appPkg !in topPkgs) continue
            val hour = Instant.ofEpochMilli(session.startTs).atZone(zone).hour
            activities += GraphBuilder.Activity(
                appResolver.resolve(session.appPkg), session.dayKey, GraphBuilder.daypart(hour),
            )
        }
        // Contacts: each daypart of a day that had messages.
        for (row in db.commsDailyDao().all()) {
            val hist = parseHist(row.byHourHistogram)
            for (daypart in 0..3) {
                if (hoursOf(daypart).any { hist[it] > 0 }) {
                    activities += GraphBuilder.Activity(row.contactId, row.date, daypart)
                }
            }
        }
        // Places: arrival daypart.
        for (row in db.placeDailyDao().all()) {
            val hour = Instant.ofEpochMilli(row.arrivalTs).atZone(zone).hour
            activities += GraphBuilder.Activity(row.placeId, row.date, GraphBuilder.daypart(hour))
        }

        val edges = GraphBuilder.buildEdges(activities)
        val communities = Louvain.detect(edges, minSize = minMembers)

        val kinds = db.trackedEntityDao().let { dao ->
            (dao.byKind(EntityKind.APP) + dao.byKind(EntityKind.CONTACT) + dao.byKind(EntityKind.PLACE))
                .associateBy { it.id }
        }

        val keptKeys = mutableListOf<String>()
        for (community in communities) {
            val members = community.members.toSet()
            val sig = GraphBuilder.timeSignature(members, activities)
            if (sig.windowCount < minWindows) continue

            val dedupeKey = "mode:" + community.members.sorted().joinToString(",")
            keptKeys += dedupeKey
            if (db.modeDao().byDedupeKey(dedupeKey) != null) {
                // Membership unchanged — leave existing row (and its LLM name).
                continue
            }

            val summary = memberSummary(community.members, kinds)
            val template = templateName(sig)
            db.modeDao().insert(
                ModeEntity(
                    dedupeKey = dedupeKey,
                    memberIds = "," + community.members.joinToString(",") + ",",
                    memberSummary = summary,
                    timeSignature = timeSignatureJson(sig),
                    templateName = template,
                    strength = community.internalWeight,
                ),
            )
        }
        // `NOT IN ()` is invalid SQLite, so clear outright when nothing was kept.
        if (keptKeys.isEmpty()) db.modeDao().clear() else db.modeDao().deleteNotIn(keptKeys)
    }

    /** Pseudonym-safe member description for the LLM: app labels + role placeholders. */
    private fun memberSummary(
        members: List<Long>,
        kinds: Map<Long, com.revela.core.db.TrackedEntity>,
    ): String {
        val apps = mutableListOf<String>()
        var contacts = 0
        val places = mutableListOf<String>()
        for (id in members) {
            val e = kinds[id] ?: continue
            when (e.kind) {
                EntityKind.APP -> apps += e.displayName
                EntityKind.CONTACT -> contacts++
                EntityKind.PLACE -> places += when (e.placeLabel) {
                    "HOME" -> "home"
                    "WORK" -> "work"
                    else -> "a place"
                }
                EntityKind.TOPIC -> Unit
            }
        }
        val parts = mutableListOf<String>()
        if (apps.isNotEmpty()) parts += apps.joinToString(", ")
        if (places.isNotEmpty()) parts += places.joinToString(", ")
        if (contacts > 0) parts += if (contacts == 1) "a contact" else "$contacts contacts"
        return parts.joinToString("; ")
    }

    private fun templateName(sig: GraphBuilder.TimeSignature): String {
        val part = GraphBuilder.daypartName(sig.topDaypart)
        val week = if (sig.weekendShare >= 0.6) "Weekend" else if (sig.weekendShare <= 0.2) "Weekday" else ""
        return listOf(week, "${part}s").filter { it.isNotBlank() }.joinToString(" ")
            .replaceFirstChar { it.uppercase() }
    }

    private fun timeSignatureJson(sig: GraphBuilder.TimeSignature): String =
        """{"top_daypart":"${GraphBuilder.daypartName(sig.topDaypart)}",""" +
            """"top_daypart_share":${round2(sig.topDaypartShare)},""" +
            """"weekend_share":${round2(sig.weekendShare)},"windows":${sig.windowCount}}"""

    private fun hoursOf(daypart: Int): IntRange = when (daypart) {
        1 -> 5..11
        2 -> 12..17
        3 -> 18..23
        else -> 0..4
    }

    private fun parseHist(json: String): IntArray {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return IntArray(24)
        return IntArray(24) { if (it < arr.length()) arr.optInt(it) else 0 }
    }

    private fun round2(v: Double): Double = Math.round(v * 100) / 100.0
}
