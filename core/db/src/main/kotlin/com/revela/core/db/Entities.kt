package com.revela.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.revela.core.model.AppSession
import com.revela.core.model.CollectorSource
import com.revela.core.model.DailyUsage
import com.revela.core.model.DaySummary
import com.revela.core.model.DayType
import com.revela.core.model.EventType
import com.revela.core.model.HourlyUsage
import com.revela.core.model.PickupType
import com.revela.core.model.RawEvent

/** Raw event log (§6.1). Append-only: rows are never updated. */
@Entity(
    tableName = "events",
    indices = [Index("ts"), Index("type", "ts")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val type: EventType,
    @ColumnInfo(name = "app_pkg") val appPkg: String?,
    @ColumnInfo(name = "entity_id") val entityId: Long?,
    val payload: String?,
    val source: CollectorSource,
)

fun RawEvent.toEntity() = EventEntity(
    ts = ts,
    type = type,
    appPkg = appPkg,
    entityId = entityId,
    payload = payload,
    source = source,
)

fun EventEntity.toRaw() = RawEvent(
    ts = ts,
    type = type,
    appPkg = appPkg,
    entityId = entityId,
    payload = payload,
    source = source,
)

/** Per-collector cursors and per-rollup watermarks (§4 of PLAN.md). */
@Entity(tableName = "watermarks")
data class WatermarkEntity(
    @PrimaryKey val key: String,
    val value: Long,
)

/** Apps, contacts, places, topics (§6.3). */
@Entity(tableName = "entities", indices = [Index("kind")])
data class TrackedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: EntityKind,
    @ColumnInfo(name = "display_name") val displayName: String,
    /** JSON array of alternate names/identifiers. */
    val aliases: String? = null,
    val metadata: String? = null,
    // PLACE geometry (null for other kinds); cluster centroid.
    @ColumnInfo(name = "place_lat") val placeLat: Double? = null,
    @ColumnInfo(name = "place_lon") val placeLon: Double? = null,
    /** HOME | WORK | OTHER guess, user-overridable. */
    @ColumnInfo(name = "place_label") val placeLabel: String? = null,
)

enum class EntityKind { APP, CONTACT, PLACE, TOPIC }

/** Per-day messaging with a contact (§6.2, Phase 2). Derived — rebuilt by rollups. */
@Entity(tableName = "comms_daily", primaryKeys = ["date", "contact_id", "app_pkg"])
data class CommsDailyEntity(
    val date: String,
    @ColumnInfo(name = "contact_id") val contactId: Long,
    @ColumnInfo(name = "app_pkg") val appPkg: String,
    @ColumnInfo(name = "msg_notif_count") val msgNotifCount: Int,
    /** JSON int[24] local-hour histogram. */
    @ColumnInfo(name = "by_hour_histogram") val byHourHistogram: String,
)

/** Per-day dwell at a significant place (§6.2, Phase 2). Derived. */
@Entity(tableName = "place_daily", primaryKeys = ["date", "place_id"])
data class PlaceDailyEntity(
    val date: String,
    @ColumnInfo(name = "place_id") val placeId: Long,
    @ColumnInfo(name = "arrival_ts") val arrivalTs: Long,
    @ColumnInfo(name = "depart_ts") val departTs: Long,
    @ColumnInfo(name = "dwell_seconds") val dwellSeconds: Int,
)

/**
 * A recurring "mode" — a community from graph detection (§8.10/§11). Derived,
 * rebuilt each analysis run. The LLM names/describes it (llm_name/llm_desc);
 * template_name is the always-present on-device fallback.
 */
@Entity(tableName = "modes", indices = [Index(value = ["dedupe_key"], unique = true)])
data class ModeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
    /** JSON array of member entity ids. */
    @ColumnInfo(name = "member_ids") val memberIds: String,
    /** On-device human summary of members (app labels + role placeholders). */
    @ColumnInfo(name = "member_summary") val memberSummary: String,
    @ColumnInfo(name = "time_signature") val timeSignature: String,
    @ColumnInfo(name = "template_name") val templateName: String,
    val strength: Double,
    @ColumnInfo(name = "llm_name") val llmName: String? = null,
    @ColumnInfo(name = "llm_desc") val llmDesc: String? = null,
)

/** Reconstructed app sessions (§6.2). Derived — rebuilt by rollups. */
@Entity(
    tableName = "sessions",
    indices = [Index("day_key"), Index("start_ts")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "app_pkg") val appPkg: String,
    @ColumnInfo(name = "start_ts") val startTs: Long,
    @ColumnInfo(name = "end_ts") val endTs: Long,
    @ColumnInfo(name = "duration_s") val durationS: Int,
    @ColumnInfo(name = "pickup_type") val pickupType: PickupType,
)

fun AppSession.toEntity() = SessionEntity(
    dayKey = dayKey,
    appPkg = appPkg,
    startTs = startTs,
    endTs = endTs,
    durationS = durationS,
    pickupType = pickupType,
)

/** Per-app seconds by calendar date + hour (heatmap source). Derived. */
@Entity(tableName = "usage_hourly", primaryKeys = ["date", "hour", "app_pkg"])
data class UsageHourlyEntity(
    val date: String,
    val hour: Int,
    @ColumnInfo(name = "app_pkg") val appPkg: String,
    @ColumnInfo(name = "total_seconds") val totalSeconds: Int,
    @ColumnInfo(name = "open_count") val openCount: Int,
)

fun HourlyUsage.toEntity() = UsageHourlyEntity(date, hour, appPkg, totalSeconds, openCount)

/** Per-app totals by behavioral day. Derived. */
@Entity(tableName = "usage_daily", primaryKeys = ["date", "app_pkg"])
data class UsageDailyEntity(
    val date: String,
    @ColumnInfo(name = "app_pkg") val appPkg: String,
    @ColumnInfo(name = "total_seconds") val totalSeconds: Int,
    @ColumnInfo(name = "open_count") val openCount: Int,
    @ColumnInfo(name = "first_use") val firstUse: Long,
    @ColumnInfo(name = "last_use") val lastUse: Long,
)

fun DailyUsage.toEntity() = UsageDailyEntity(dayKey, appPkg, totalSeconds, openCount, firstUse, lastUse)

/** One row per behavioral day. Derived. */
@Entity(tableName = "day_summary")
data class DaySummaryEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(name = "first_unlock") val firstUnlock: Long?,
    @ColumnInfo(name = "last_use") val lastUse: Long?,
    @ColumnInfo(name = "total_screen_time_s") val totalScreenTimeS: Int,
    @ColumnInfo(name = "pickup_count") val pickupCount: Int,
    @ColumnInfo(name = "reflex_check_count") val reflexCheckCount: Int,
    @ColumnInfo(name = "day_type") val dayType: DayType,
    @ColumnInfo(name = "zone_id") val zoneId: String,
)

fun DaySummary.toEntity() = DaySummaryEntity(
    date = dayKey,
    firstUnlock = firstUnlock,
    lastUse = lastUse,
    totalScreenTimeS = totalScreenTimeS,
    pickupCount = pickupCount,
    reflexCheckCount = reflexCheckCount,
    dayType = dayType,
    zoneId = zoneId,
)

/** Discovered patterns surfaced to the user (§6.4). */
@Entity(
    tableName = "insights",
    indices = [Index("type"), Index("created_ts"), Index(value = ["dedupe_key"], unique = true)],
)
data class InsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable identity (type+subject+window) so re-analysis UPSERTs instead of repeating. */
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
    @ColumnInfo(name = "created_ts") val createdTs: Long,
    val type: String,
    val confidence: Double,
    /** JSON array of entity ids this insight is about. */
    @ColumnInfo(name = "entity_ids") val entityIds: String? = null,
    @ColumnInfo(name = "window_start") val windowStart: Long,
    @ColumnInfo(name = "window_end") val windowEnd: Long,
    /** The numbers behind the insight, as JSON. */
    @ColumnInfo(name = "stat_payload") val statPayload: String,
    /** Templated natural-language text — always present, works offline. */
    val text: String,
    /** LLM-narrated alternative (M5); null until narrated, cleared on refresh. */
    @ColumnInfo(name = "llm_text") val llmText: String? = null,
    val dismissed: Boolean = false,
    val pinned: Boolean = false,
)

/**
 * Audit log of every payload sent to the LLM API (D4/§3.3): the user can see
 * exactly what left the device. Rows are written before the request is made.
 */
@Entity(tableName = "llm_audit", indices = [Index("ts")])
data class LlmAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    /** What the request was for: "narration" or "query". */
    val purpose: String,
    /** The exact request body sent to the API. */
    val payload: String,
)
