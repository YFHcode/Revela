package com.revela.analysis

/**
 * §8.10 — community detection over the life-graph (Louvain modularity
 * optimization). Pure JVM, deterministic (nodes processed in id order), so
 * "modes" are found by statistics — the LLM only names them (§11, §10-L2).
 */

data class Edge(val a: Long, val b: Long, val weight: Double)

data class Community(val members: List<Long>, val internalWeight: Double)

object Louvain {

    /**
     * One pass of local modularity optimization over an undirected weighted
     * graph. Good enough for single-user graphs (hundreds of nodes); a full
     * multi-level Louvain is unnecessary at this scale.
     *
     * @return communities with >= [minSize] members, largest first.
     */
    fun detect(edges: List<Edge>, minSize: Int = 2, maxIterations: Int = 20): List<Community> {
        if (edges.isEmpty()) return emptyList()

        val nodes = (edges.map { it.a } + edges.map { it.b }).distinct().sorted()
        val adjacency = HashMap<Long, MutableList<Pair<Long, Double>>>()
        val degree = HashMap<Long, Double>()
        var totalWeight = 0.0
        for (e in edges) {
            if (e.a == e.b) continue
            adjacency.getOrPut(e.a) { mutableListOf() }.add(e.b to e.weight)
            adjacency.getOrPut(e.b) { mutableListOf() }.add(e.a to e.weight)
            degree.merge(e.a, e.weight, Double::plus)
            degree.merge(e.b, e.weight, Double::plus)
            totalWeight += e.weight
        }
        val m2 = 2.0 * totalWeight
        if (m2 == 0.0) return emptyList()

        val community = HashMap<Long, Long>()
        for (n in nodes) community[n] = n
        val communityTotalDegree = HashMap<Long, Double>()
        for (n in nodes) communityTotalDegree[n] = degree[n] ?: 0.0

        var improved = true
        var iteration = 0
        while (improved && iteration < maxIterations) {
            improved = false
            iteration++
            for (node in nodes) {
                val nodeDegree = degree[node] ?: 0.0
                val current = community[node]!!
                // Remove node from its community.
                communityTotalDegree.merge(current, -nodeDegree, Double::plus)

                // Sum of edge weights from node to each neighbouring community.
                val weightToCommunity = HashMap<Long, Double>()
                for ((neighbor, w) in adjacency[node].orEmpty()) {
                    val c = community[neighbor]!!
                    weightToCommunity.merge(c, w, Double::plus)
                }

                var bestCommunity = current
                var bestGain = 0.0
                for ((c, wTo) in weightToCommunity) {
                    val gain = wTo - (communityTotalDegree[c] ?: 0.0) * nodeDegree / m2
                    if (gain > bestGain) {
                        bestGain = gain
                        bestCommunity = c
                    }
                }

                community[node] = bestCommunity
                communityTotalDegree.merge(bestCommunity, nodeDegree, Double::plus)
                if (bestCommunity != current) improved = true
            }
        }

        // Internal weight per community (edges with both endpoints inside).
        val internal = HashMap<Long, Double>()
        for (e in edges) {
            if (e.a != e.b && community[e.a] == community[e.b]) {
                internal.merge(community[e.a], e.weight, Double::plus)
            }
        }

        return community.entries.groupBy({ it.value }, { it.key })
            .map { (c, members) -> Community(members.sorted(), internal[c] ?: 0.0) }
            .filter { it.members.size >= minSize }
            .sortedByDescending { it.members.size }
    }
}
