package com.revela.insights

import com.revela.core.db.EntityKind
import com.revela.core.db.RevelaDatabase
import com.revela.core.db.TrackedEntity

/**
 * Resolves an app package to a stable APP entity id so apps can be graph
 * nodes alongside contacts and places. The package is kept in `aliases`; the
 * display name is the human app label.
 */
class AppEntityResolver(
    private val db: RevelaDatabase,
    private val appLabel: (String) -> String,
) {
    private val cache = HashMap<String, Long>()
    private var loaded = false
    private val existing = HashMap<String, Long>()

    suspend fun resolve(pkg: String): Long {
        cache[pkg]?.let { return it }
        if (!loaded) {
            for (e in db.trackedEntityDao().byKind(EntityKind.APP)) {
                e.aliases?.let { existing[it] = e.id }
            }
            loaded = true
        }
        existing[pkg]?.let { cache[pkg] = it; return it }

        val id = db.trackedEntityDao().upsert(
            TrackedEntity(kind = EntityKind.APP, displayName = appLabel(pkg), aliases = pkg),
        )
        cache[pkg] = id
        existing[pkg] = id
        return id
    }
}
