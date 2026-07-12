package com.revela.pipeline

/** Worker-visible pipeline dependencies; the Application implements the owner. */
class PipelineGraph(
    val rollupRunner: RollupRunner,
)

interface PipelineGraphOwner {
    val pipelineGraph: PipelineGraph
}
