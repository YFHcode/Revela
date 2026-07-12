package com.revela.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceClusteringTest {

    // Two tight clusters ~4.6 km apart, plus scattered noise.
    private val home = 48.8566 to 2.3522
    private val work = 48.8738 to 2.2950

    private fun jitter(base: Pair<Double, Double>, i: Int): GeoFix {
        // ~10 m jitter (0.0001° ≈ 11 m).
        val d = (i % 5 - 2) * 0.0001
        return GeoFix(base.first + d, base.second - d, i.toLong())
    }

    @Test
    fun `two dense areas become two clusters`() {
        val fixes = buildList {
            repeat(20) { add(jitter(home, it)) }
            repeat(15) { add(jitter(work, it)) }
            // scattered noise, far apart
            add(GeoFix(48.90, 2.40, 100))
            add(GeoFix(48.80, 2.20, 101))
        }
        val clusters = Dbscan.cluster(fixes)
        assertEquals(2, clusters.size)
        val sizes = clusters.map { it.size }.sorted()
        assertEquals(listOf(15, 20), sizes)
    }

    @Test
    fun `sparse wandering yields no place`() {
        val fixes = (0 until 30).map { GeoFix(48.85 + it * 0.01, 2.35 + it * 0.01, it.toLong()) }
        assertTrue(Dbscan.cluster(fixes).isEmpty())
    }

    @Test
    fun `centroid sits inside its cluster`() {
        val fixes = (0 until 20).map { jitter(home, it) }
        val cluster = Dbscan.cluster(fixes).single()
        assertEquals(home.first, cluster.centroidLat, 0.001)
        assertEquals(home.second, cluster.centroidLon, 0.001)
    }

    @Test
    fun `haversine matches known distance`() {
        // These two Paris points are ~4.6 km apart.
        val d = Dbscan.haversine(GeoFix(48.8566, 2.3522, 0), GeoFix(48.8738, 2.2950, 0))
        assertTrue("was $d", d in 4000.0..5000.0)
    }

    @Test
    fun `night presence is labelled home`() {
        val hist = IntArray(24).also { for (h in 0..5) it[h] = 10; it[23] = 10 }
        assertEquals(PlaceLabeler.Guess.HOME, PlaceLabeler.guess(hist, 0.1))
    }

    @Test
    fun `weekday daytime presence is labelled work`() {
        val hist = IntArray(24).also { for (h in 9..17) it[h] = 10 }
        assertEquals(PlaceLabeler.Guess.WORK, PlaceLabeler.guess(hist, 0.8))
    }
}
