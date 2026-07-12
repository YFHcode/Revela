package com.revela.insights

import com.revela.analysis.InsightDraft
import com.revela.core.db.InsightEntity
import com.revela.core.db.RevelaDatabase

/**
 * Persists insight drafts with UPSERT semantics: an insight the user has
 * dismissed stays dismissed; an existing one gets its numbers refreshed but
 * keeps its discovery date and pin state; new ones are inserted.
 */
class InsightWriter(private val db: RevelaDatabase) {

    suspend fun upsertAll(drafts: List<InsightDraft>, now: Long) {
        val dao = db.insightDao()
        for (draft in drafts) {
            val existing = dao.byDedupeKey(draft.dedupeKey)
            when {
                existing == null -> dao.insert(
                    InsightEntity(
                        dedupeKey = draft.dedupeKey,
                        createdTs = now,
                        type = draft.type,
                        confidence = draft.confidence,
                        entityIds = draft.entityIds,
                        windowStart = draft.windowStart,
                        windowEnd = draft.windowEnd,
                        statPayload = draft.statPayload,
                        text = draft.text,
                    ),
                )
                existing.dismissed -> Unit // respect the user's dismissal
                else -> dao.refresh(
                    id = existing.id,
                    payload = draft.statPayload,
                    text = draft.text,
                    confidence = draft.confidence,
                    windowStart = draft.windowStart,
                    windowEnd = draft.windowEnd,
                )
            }
        }
    }
}
