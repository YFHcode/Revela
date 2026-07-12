package com.revela.capture

import com.revela.core.db.EntityKind
import com.revela.core.db.RevelaDatabase
import com.revela.core.db.TrackedEntity

/**
 * Resolves a notification sender/title into a stable CONTACT entity (§6.3).
 * Unifies obvious variants ("Maman"/"Mom") by normalized display name; a
 * richer alias merge is deferred to Phase 3's LLM assist. Message BODIES are
 * never passed in — only the sender label needed to identify a contact (D7).
 */
class ContactResolver(private val db: RevelaDatabase) {

    private val cache = HashMap<String, Long>()

    suspend fun resolve(rawTitle: String): Long? {
        val normalized = normalize(rawTitle)
        if (normalized.isEmpty()) return null
        cache[normalized]?.let { return it }

        val existing = db.trackedEntityDao().byKind(EntityKind.CONTACT)
            .firstOrNull { normalize(it.displayName) == normalized }
        if (existing != null) {
            cache[normalized] = existing.id
            return existing.id
        }

        val id = db.trackedEntityDao().upsert(
            TrackedEntity(kind = EntityKind.CONTACT, displayName = rawTitle.trim()),
        )
        cache[normalized] = id
        return id
    }

    private fun normalize(title: String): String =
        title.trim().lowercase()
            .substringBefore(':') // "Alice: hi there" → "alice" (group chat safety)
            .replace(Regex("\\s*\\(\\d+\\)$"), "") // strip "(3)" unread counters
            .trim()
}
