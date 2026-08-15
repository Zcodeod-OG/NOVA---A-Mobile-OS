package com.nova.runtime.app.memory

import com.nova.runtime.memory.MemoryPlatform
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.contracts.MemoryQuery
import com.nova.runtime.models.contracts.MemoryResult
import com.nova.runtime.storage.entities.PreferenceEntity
import com.nova.runtime.storage.profile.ProfileKeys
import com.nova.runtime.storage.profile.ProfilePreferencesStore
import com.nova.runtime.storage.profile.toScoringContext
import com.nova.runtime.storage.repository.MessageRepository
import com.nova.runtime.storage.repository.PreferenceRepository
import com.nova.runtime.understanding.scoring.ImportanceScorer
import com.nova.runtime.understanding.scoring.MessageScoreInput

/**
 * Loads onboarding profile preferences and recent messages for reasoning context.
 * Wired in the app module because it depends on Android Room storage.
 */
class PreferenceMemoryPlatform(
    private val preferenceRepository: PreferenceRepository,
    private val messageRepository: MessageRepository,
    private val profileStore: ProfilePreferencesStore,
) : MemoryPlatform {
    override suspend fun store(entry: Map<String, String>): MemoryResult {
        val key = entry["key"] ?: return missingField("key")
        val value = entry["value"] ?: return missingField("value")
        val confidence = entry["confidence"]?.toFloatOrNull() ?: 1f
        preferenceRepository.insert(
            PreferenceEntity(
                key = key,
                value = value,
                confidence = confidence,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return MemoryResult.Success(emptyList())
    }

    override suspend fun query(query: MemoryQuery): MemoryResult =
        when (query.queryType) {
            "context_retrieval" -> MemoryResult.Success(buildContextEntries(query.maxResults))
            "profile" -> MemoryResult.Success(buildProfileEntries())
            else -> MemoryResult.Success(emptyList())
        }

    override suspend fun update(id: String, entry: Map<String, String>): MemoryResult =
        store(entry + mapOf("key" to (entry["key"] ?: id)))

    override suspend fun forget(id: String): MemoryResult {
        preferenceRepository.delete(id)
        return MemoryResult.Success(emptyList())
    }

    override suspend fun restore(id: String): MemoryResult =
        preferenceRepository.getByKey(id)?.let { pref ->
            MemoryResult.Success(
                listOf(
                    mapOf(
                        "type" to "profile",
                        "key" to pref.key,
                        "value" to pref.value,
                        "confidence" to pref.confidence.toString(),
                    ),
                ),
            )
        } ?: MemoryResult.Success(emptyList())

    private suspend fun buildProfileEntries(): List<Map<String, String>> =
        ProfileKeys.ALL.mapNotNull { key ->
            preferenceRepository.getByKey(key)?.let { pref ->
                mapOf("type" to "profile", "key" to key, "value" to pref.value)
            }
        }

    private suspend fun buildContextEntries(maxResults: Int): List<Map<String, String>> {
        val entries = mutableListOf<Map<String, String>>()
        entries += buildProfileEntries()

        val profileContext = profileStore.loadProfile().toScoringContext()
        messageRepository.getRecent(maxResults.coerceAtLeast(10))
            .map { message ->
                message to ImportanceScorer.score(
                    MessageScoreInput(message.body, message.sender, message.channel),
                    profileContext,
                )
            }
            .sortedByDescending { (_, scored) -> scored.score }
            .take(maxResults)
            .forEach { (message, scored) ->
                entries += mapOf(
                    "type" to "message",
                    "id" to message.id.toString(),
                    "channel" to message.channel,
                    "sender" to message.sender,
                    "body" to message.body,
                    "importanceScore" to scored.score.toString(),
                    "actionType" to scored.actionType,
                    "title" to scored.title,
                    "datetime" to (scored.datetime?.toString() ?: ""),
                )
            }
        return entries
    }

    private fun missingField(field: String): MemoryResult.Failure =
        MemoryResult.Failure(
            RuntimeError(
                code = "MEMORY_MISSING_FIELD",
                category = ErrorCategory.VALIDATION,
                severity = ErrorSeverity.LOW,
                recoverable = true,
                userVisibleMessage = "Missing required field: $field",
            ),
        )
}
