package com.nova.runtime.storage.coordinator

import com.nova.runtime.storage.entities.ContactEntity
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.EmbeddingEntity
import com.nova.runtime.storage.entities.ExecutionHistoryEntity
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.storage.entities.PreferenceEntity
import com.nova.runtime.storage.entities.ProjectEntity
import com.nova.runtime.storage.entities.SessionEntity
import java.util.UUID

internal object EntityMapCodec {
    fun documentToMap(entity: DocumentEntity): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "path" to entity.path,
            "name" to entity.name,
            "extension" to entity.extension,
            "mimeType" to entity.mimeType,
            "size" to entity.size.toString(),
            "checksum" to entity.checksum,
            "createdAt" to entity.createdAt.toString(),
            "modifiedAt" to entity.modifiedAt.toString(),
            "indexedAt" to entity.indexedAt?.toString().orEmpty(),
            "projectId" to entity.projectId?.toString().orEmpty(),
            "embeddingId" to entity.embeddingId?.toString().orEmpty(),
            "importance" to entity.importance.toString(),
        )

    fun documentFromMap(key: String, value: Map<String, String>): DocumentEntity =
        DocumentEntity(
            id = uuid(value["id"] ?: key),
            path = value.require("path"),
            name = value.require("name"),
            extension = value["extension"].orEmpty(),
            mimeType = value["mimeType"].orEmpty(),
            size = value["size"]?.toLongOrNull() ?: 0L,
            checksum = value["checksum"].orEmpty(),
            createdAt = value["createdAt"]?.toLongOrNull() ?: 0L,
            modifiedAt = value["modifiedAt"]?.toLongOrNull() ?: 0L,
            indexedAt = value["indexedAt"]?.toLongOrNull(),
            projectId = value["projectId"]?.takeIf { it.isNotBlank() }?.let(::uuid),
            embeddingId = value["embeddingId"]?.takeIf { it.isNotBlank() }?.let(::uuid),
            importance = value["importance"]?.toIntOrNull() ?: 0,
        )

    fun photoToMap(entity: PhotoEntity): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "uri" to entity.uri,
            "takenAt" to entity.takenAt.toString(),
            "width" to entity.width?.toString().orEmpty(),
            "height" to entity.height?.toString().orEmpty(),
            "latitude" to entity.latitude?.toString().orEmpty(),
            "longitude" to entity.longitude?.toString().orEmpty(),
            "ocrText" to entity.ocrText.orEmpty(),
            "embeddingId" to entity.embeddingId?.toString().orEmpty(),
            "favorite" to entity.favorite.toString(),
        )

    fun photoFromMap(key: String, value: Map<String, String>): PhotoEntity =
        PhotoEntity(
            id = uuid(value["id"] ?: key),
            uri = value.require("uri"),
            takenAt = value["takenAt"]?.toLongOrNull() ?: 0L,
            width = value["width"]?.toIntOrNull(),
            height = value["height"]?.toIntOrNull(),
            latitude = value["latitude"]?.toDoubleOrNull(),
            longitude = value["longitude"]?.toDoubleOrNull(),
            ocrText = value["ocrText"]?.takeIf { it.isNotBlank() },
            embeddingId = value["embeddingId"]?.takeIf { it.isNotBlank() }?.let(::uuid),
            favorite = value["favorite"]?.toBooleanStrictOrNull() ?: false,
        )

    fun contactToMap(entity: ContactEntity): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "displayName" to entity.displayName,
            "phone" to entity.phone.orEmpty(),
            "email" to entity.email.orEmpty(),
            "lastInteraction" to entity.lastInteraction?.toString().orEmpty(),
            "importance" to entity.importance.toString(),
        )

    fun contactFromMap(key: String, value: Map<String, String>): ContactEntity =
        ContactEntity(
            id = uuid(value["id"] ?: key),
            displayName = value.require("displayName"),
            phone = value["phone"]?.takeIf { it.isNotBlank() },
            email = value["email"]?.takeIf { it.isNotBlank() },
            lastInteraction = value["lastInteraction"]?.toLongOrNull(),
            importance = value["importance"]?.toIntOrNull() ?: 0,
        )

    fun projectToMap(entity: ProjectEntity): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "title" to entity.title,
            "description" to entity.description.orEmpty(),
            "createdAt" to entity.createdAt.toString(),
            "updatedAt" to entity.updatedAt.toString(),
            "status" to entity.status,
        )

    fun projectFromMap(key: String, value: Map<String, String>): ProjectEntity =
        ProjectEntity(
            id = uuid(value["id"] ?: key),
            title = value.require("title"),
            description = value["description"]?.takeIf { it.isNotBlank() },
            createdAt = value["createdAt"]?.toLongOrNull() ?: 0L,
            updatedAt = value["updatedAt"]?.toLongOrNull() ?: 0L,
            status = value["status"].orEmpty(),
        )

    fun sessionToMap(entity: SessionEntity): Map<String, String> =
        mapOf(
            "sessionId" to entity.sessionId.toString(),
            "traceId" to entity.traceId.toString(),
            "startedAt" to entity.startedAt.toString(),
            "endedAt" to entity.endedAt?.toString().orEmpty(),
            "state" to entity.state,
        )

    fun sessionFromMap(key: String, value: Map<String, String>): SessionEntity =
        SessionEntity(
            sessionId = uuid(value["sessionId"] ?: key),
            traceId = uuid(value.require("traceId")),
            startedAt = value["startedAt"]?.toLongOrNull() ?: 0L,
            endedAt = value["endedAt"]?.toLongOrNull(),
            state = value["state"].orEmpty(),
        )

    fun preferenceToMap(entity: PreferenceEntity): Map<String, String> =
        mapOf(
            "key" to entity.key,
            "value" to entity.value,
            "confidence" to entity.confidence.toString(),
            "updatedAt" to entity.updatedAt.toString(),
        )

    fun preferenceFromMap(key: String, value: Map<String, String>): PreferenceEntity =
        PreferenceEntity(
            key = value["key"] ?: key,
            value = value.require("value"),
            confidence = value["confidence"]?.toFloatOrNull() ?: 0f,
            updatedAt = value["updatedAt"]?.toLongOrNull() ?: 0L,
        )

    fun executionToMap(entity: ExecutionHistoryEntity): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "graphId" to entity.graphId.toString(),
            "traceId" to entity.traceId.toString(),
            "status" to entity.status,
            "duration" to entity.duration.toString(),
            "retryCount" to entity.retryCount.toString(),
            "completedNodes" to entity.completedNodes.toString(),
            "failedNodes" to entity.failedNodes.toString(),
        )

    fun executionFromMap(key: String, value: Map<String, String>): ExecutionHistoryEntity =
        ExecutionHistoryEntity(
            id = uuid(value["id"] ?: key),
            graphId = uuid(value.require("graphId")),
            traceId = uuid(value.require("traceId")),
            status = value["status"].orEmpty(),
            duration = value["duration"]?.toLongOrNull() ?: 0L,
            retryCount = value["retryCount"]?.toIntOrNull() ?: 0,
            completedNodes = value["completedNodes"]?.toIntOrNull() ?: 0,
            failedNodes = value["failedNodes"]?.toIntOrNull() ?: 0,
        )

    fun embeddingToMap(entity: EmbeddingEntity): Map<String, String> =
        mapOf(
            "embeddingId" to entity.embeddingId.toString(),
            "objectType" to entity.objectType,
            "objectId" to entity.objectId.toString(),
            "modelVersion" to entity.modelVersion,
            "dimension" to entity.dimension.toString(),
            "createdAt" to entity.createdAt.toString(),
        )

    fun embeddingFromMap(key: String, value: Map<String, String>): EmbeddingEntity =
        EmbeddingEntity(
            embeddingId = uuid(value["embeddingId"] ?: key),
            objectType = value.require("objectType"),
            objectId = uuid(value.require("objectId")),
            modelVersion = value["modelVersion"].orEmpty(),
            dimension = value["dimension"]?.toIntOrNull() ?: 768,
            createdAt = value["createdAt"]?.toLongOrNull() ?: 0L,
        )

    private fun uuid(raw: String): UUID = UUID.fromString(raw)

    private fun Map<String, String>.require(field: String): String =
        this[field] ?: error("Missing required field: $field")
}
