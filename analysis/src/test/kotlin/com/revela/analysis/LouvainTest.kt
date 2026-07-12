package com.revela.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LouvainTest {

    @Test
    fun `two dense clusters with a weak bridge become two communities`() {
        val edges = listOf(
            // cluster {1,2,3}
            Edge(1, 2, 5.0), Edge(2, 3, 5.0), Edge(1, 3, 5.0),
            // cluster {4,5,6}
            Edge(4, 5, 5.0), Edge(5, 6, 5.0), Edge(4, 6, 5.0),
            // weak bridge
            Edge(3, 4, 1.0),
        )
        val communities = Louvain.detect(edges)
        assertEquals(2, communities.size)
        val sets = communities.map { it.members.toSet() }
        assertTrue(sets.contains(setOf(1L, 2L, 3L)))
        assertTrue(sets.contains(setOf(4L, 5L, 6L)))
    }

    @Test
    fun `a single clique is one community`() {
        val edges = listOf(
            Edge(1, 2, 3.0), Edge(2, 3, 3.0), Edge(1, 3, 3.0), Edge(1, 4, 3.0),
            Edge(2, 4, 3.0), Edge(3, 4, 3.0),
        )
        val communities = Louvain.detect(edges)
        assertEquals(1, communities.size)
        assertEquals(setOf(1L, 2L, 3L, 4L), communities.single().members.toSet())
    }

    @Test
    fun `empty graph yields no communities`() {
        assertTrue(Louvain.detect(emptyList()).isEmpty())
    }

    @Test
    fun `singletons below min size are dropped`() {
        // two isolated edges → two size-2 communities; raise minSize to 3 → none
        val edges = listOf(Edge(1, 2, 1.0), Edge(3, 4, 1.0))
        assertTrue(Louvain.detect(edges, minSize = 3).isEmpty())
    }

    @Test
    fun `detection is deterministic across runs`() {
        val edges = listOf(
            Edge(1, 2, 5.0), Edge(2, 3, 5.0), Edge(1, 3, 5.0),
            Edge(4, 5, 5.0), Edge(5, 6, 5.0), Edge(4, 6, 5.0),
            Edge(3, 4, 1.0),
        )
        assertEquals(
            Louvain.detect(edges).map { it.members },
            Louvain.detect(edges).map { it.members },
        )
    }
}
