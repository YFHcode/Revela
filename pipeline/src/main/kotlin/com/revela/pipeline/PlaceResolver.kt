package com.revela.pipeline

import com.revela.analysis.Dbscan
import com.revela.analysis.GeoFix
import com.revela.analysis.PlaceLabeler
import com.revela.core.db.EntityKind
import com.revela.core.db.RevelaDatabase
import com.revela.core.db.TrackedEntity
import java.time.Instant
import java.time.ZoneId

/**
 * Clusters raw location fixes into significant places and assigns each to a
 * STABLE place-entity id (matched to an existing centroid within
 * [matchRadiusM] so user labels survive rebuilds). New places get a heuristic
 * name/label the user can override; existing places keep their name/label and
 * only have their centroid refreshed.
 */
class PlaceResolver(
    private val db: RevelaDatabase,
    private val matchRadiusM: Double = 120.0,
) {

    suspend fun assignFixes(fixes: List<GeoFix>, zone: ZoneId): List<PlaceRollup.PlacedFix> {
        if (fixes.isEmpty()) return emptyList()
        val clusters = Dbscan.cluster(fixes)
        if (clusters.isEmpty()) return emptyList()

        val existing = db.trackedEntityDao().byKind(EntityKind.PLACE)
        var otherCount = existing.count { it.placeLabel == PlaceLabeler.Guess.OTHER.name }
        val placed = mutableListOf<PlaceRollup.PlacedFix>()

        for (cluster in clusters) {
            val centroid = GeoFix(cluster.centroidLat, cluster.centroidLon, 0)
            val match = existing.filter { it.placeLat != null && it.placeLon != null }
                .minByOrNull { Dbscan.haversine(centroid, GeoFix(it.placeLat!!, it.placeLon!!, 0)) }
            val matched = match?.takeIf {
                Dbscan.haversine(centroid, GeoFix(it.placeLat!!, it.placeLon!!, 0)) <= matchRadiusM
            }

            val placeId = if (matched != null) {
                db.trackedEntityDao().updatePlaceCentroid(matched.id, centroid.lat, centroid.lon)
                matched.id
            } else {
                val (name, label) = nameFor(cluster.fixIndices, fixes, zone) { otherCount++ }
                db.trackedEntityDao().upsert(
                    TrackedEntity(
                        kind = EntityKind.PLACE,
                        displayName = name,
                        placeLat = centroid.lat,
                        placeLon = centroid.lon,
                        placeLabel = label.name,
                    ),
                )
            }
            for (idx in cluster.fixIndices) {
                placed += PlaceRollup.PlacedFix(fixes[idx].ts, placeId)
            }
        }
        return placed
    }

    private inline fun nameFor(
        fixIndices: List<Int>,
        fixes: List<GeoFix>,
        zone: ZoneId,
        nextOther: () -> Int,
    ): Pair<String, PlaceLabeler.Guess> {
        val hist = IntArray(24)
        var weekdayDaytime = 0
        for (idx in fixIndices) {
            val zdt = Instant.ofEpochMilli(fixes[idx].ts).atZone(zone)
            hist[zdt.hour]++
            val dow = zdt.dayOfWeek.value // 1..7, Mon..Sun
            if (dow <= 5 && zdt.hour in 9..17) weekdayDaytime++
        }
        val share = weekdayDaytime.toDouble() / fixIndices.size.coerceAtLeast(1)
        val guess = PlaceLabeler.guess(hist, share)
        val name = when (guess) {
            PlaceLabeler.Guess.HOME -> "Home"
            PlaceLabeler.Guess.WORK -> "Work"
            PlaceLabeler.Guess.OTHER -> "Place ${nextOther() + 1}"
        }
        return name to guess
    }
}
