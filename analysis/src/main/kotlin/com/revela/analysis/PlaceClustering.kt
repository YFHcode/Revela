package com.revela.analysis

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * §8.7 — significant-place clustering via DBSCAN over GPS fixes. Pure JVM;
 * distance is haversine metres. Only cluster membership + dwell downstream —
 * raw coordinates never leave the raw log (D5).
 */

data class GeoFix(val lat: Double, val lon: Double, val ts: Long)

data class PlaceCluster(
    val centroidLat: Double,
    val centroidLon: Double,
    val fixIndices: List<Int>,
) {
    val size: Int get() = fixIndices.size
}

object Dbscan {

    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val NOISE = -1
    private const val UNVISITED = 0

    /**
     * @param epsMeters neighbourhood radius; ~75 m groups a building's fixes.
     * @param minPts minimum fixes to form a place (filters transient stops).
     */
    fun cluster(fixes: List<GeoFix>, epsMeters: Double = 75.0, minPts: Int = 12): List<PlaceCluster> {
        val n = fixes.size
        if (n == 0) return emptyList()
        val labels = IntArray(n) { UNVISITED }
        var clusterId = 0

        for (p in 0 until n) {
            if (labels[p] != UNVISITED) continue
            val neighbors = regionQuery(fixes, p, epsMeters)
            if (neighbors.size < minPts) {
                labels[p] = NOISE
                continue
            }
            clusterId++
            labels[p] = clusterId
            val queue = ArrayDeque(neighbors)
            while (queue.isNotEmpty()) {
                val q = queue.removeFirst()
                if (labels[q] == NOISE) labels[q] = clusterId
                if (labels[q] != UNVISITED) continue
                labels[q] = clusterId
                val qNeighbors = regionQuery(fixes, q, epsMeters)
                if (qNeighbors.size >= minPts) queue.addAll(qNeighbors)
            }
        }

        return (1..clusterId).mapNotNull { id ->
            val members = (0 until n).filter { labels[it] == id }
            if (members.isEmpty()) return@mapNotNull null
            val cLat = members.sumOf { fixes[it].lat } / members.size
            val cLon = members.sumOf { fixes[it].lon } / members.size
            PlaceCluster(cLat, cLon, members)
        }
    }

    private fun regionQuery(fixes: List<GeoFix>, p: Int, epsMeters: Double): List<Int> =
        fixes.indices.filter { haversine(fixes[p], fixes[it]) <= epsMeters }

    fun haversine(a: GeoFix, b: GeoFix): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }
}

/**
 * Heuristic labels for clusters from when they're occupied. Home = most
 * night presence; work = most weekday-daytime presence. Users can override.
 */
object PlaceLabeler {

    enum class Guess { HOME, WORK, OTHER }

    /** @param hourHistogram 24-slot fix counts (local hour) for this cluster. */
    fun guess(hourHistogram: IntArray, weekdayDaytimeShare: Double): Guess {
        require(hourHistogram.size == 24)
        val total = hourHistogram.sum().coerceAtLeast(1)
        val nightShare = (0..5).sumOf { hourHistogram[it] } +
            (22..23).sumOf { hourHistogram[it] }
        return when {
            nightShare.toDouble() / total >= 0.4 -> Guess.HOME
            weekdayDaytimeShare >= 0.5 -> Guess.WORK
            else -> Guess.OTHER
        }
    }
}
