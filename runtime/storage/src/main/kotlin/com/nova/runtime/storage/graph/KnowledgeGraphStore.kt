package com.nova.runtime.storage.graph

import java.util.UUID

/** DPS §4.4 — knowledge graph relationship types. */
enum class GraphRelationshipType {
    BELONGS_TO,
    RELATED_TO,
    CREATED_BY,
    SHARED_WITH,
    OPENED_AFTER,
    DEPENDS_ON,
    REFERENCES,
}

/** DPS §4.4 — knowledge graph node types. */
enum class GraphNodeType {
    PERSON,
    PROJECT,
    DOCUMENT,
    EVENT,
    LOCATION,
    DEVICE,
    CONVERSATION,
    FILE,
}

data class GraphNode(
    val id: UUID,
    val type: GraphNodeType,
    val label: String,
    val metadata: Map<String, String> = emptyMap(),
)

data class GraphEdge(
    val sourceId: UUID,
    val targetId: UUID,
    val relationship: GraphRelationshipType,
)

/** DPS §4.4 — graph store interface (full inference deferred). */
interface KnowledgeGraphStore {
    suspend fun upsertNode(node: GraphNode)

    suspend fun addEdge(edge: GraphEdge)

    suspend fun getNeighbors(nodeId: UUID, relationship: GraphRelationshipType? = null): List<GraphNode>

    suspend fun deleteNode(nodeId: UUID)
}

/** Placeholder until graph builder worker is implemented. */
class NoOpKnowledgeGraphStore : KnowledgeGraphStore {
    override suspend fun upsertNode(node: GraphNode) = Unit

    override suspend fun addEdge(edge: GraphEdge) = Unit

    override suspend fun getNeighbors(
        nodeId: UUID,
        relationship: GraphRelationshipType?,
    ): List<GraphNode> = emptyList()

    override suspend fun deleteNode(nodeId: UUID) = Unit
}
