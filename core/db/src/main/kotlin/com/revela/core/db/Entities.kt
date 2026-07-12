package com.revela.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.revela.core.model.CollectorSource
import com.revela.core.model.EventType
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

/** Per-collector cursors and per-rollup watermarks (§4 of PLAN.md). */
@Entity(tableName = "watermarks")
data class WatermarkEntity(
    @PrimaryKey val key: String,
    val value: Long,
)

/** Apps, contacts, places, topics (§6.3). */
@Entity(tableName = "entities")
data class TrackedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: EntityKind,
    @ColumnInfo(name = "display_name") val displayName: String,
    /** JSON array of alternate names/identifiers. */
    val aliases: String? = null,
    val metadata: String? = null,
)

enum class EntityKind { APP, CONTACT, PLACE, TOPIC }

/** Discovered patterns surfaced to the user (§6.4). */
@Entity(
    tableName = "insights",
    indices = [Index("type"), Index("created_ts")],
)
data class InsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_ts") val createdTs: Long,
    val type: String,
    val confidence: Double,
    /** JSON array of entity ids this insight is about. */
    @ColumnInfo(name = "entity_ids") val entityIds: String? = null,
    @ColumnInfo(name = "window_start") val windowStart: Long,
    @ColumnInfo(name = "window_end") val windowEnd: Long,
    /** The numbers behind the insight, as JSON. */
    @ColumnInfo(name = "stat_payload") val statPayload: String,
    /** Natural-language text (templated in M3, LLM-narrated in M5). */
    val text: String,
    val dismissed: Boolean = false,
    val pinned: Boolean = false,
)
