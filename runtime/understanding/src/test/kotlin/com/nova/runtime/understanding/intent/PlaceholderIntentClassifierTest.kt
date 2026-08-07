package com.nova.runtime.understanding.intent

import com.nova.runtime.models.Modality
import com.nova.runtime.models.Observation
import com.nova.runtime.models.NovaCapabilityOperations
import com.nova.runtime.understanding.normalization.NormalizedObservation
import java.time.Instant
import java.util.UUID
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PlaceholderIntentClassifierTest {

    private val classifier = PlaceholderIntentClassifier()

    @ParameterizedTest
    @MethodSource("featureCommands")
    fun classify_mapsFeatureCommandsToIntent(command: String, expectedIntent: String) = runTest {
        val intent = classifier.classify(normalized(command))
        assertEquals(expectedIntent, intent.intentType)
        assertEquals(NovaCapabilityOperations.forIntent(expectedIntent), NovaCapabilityOperations.forIntent(intent.intentType))
    }

    companion object {
        @JvmStatic
        fun featureCommands(): Stream<Arguments> = Stream.of(
            Arguments.of("send whatsapp message to John saying hello", "send_whatsapp_message"),
            Arguments.of("search photos from last week", "search_photos"),
            Arguments.of("find document about budget", "search_documents"),
            Arguments.of("set alarm for 7am", "set_alarm"),
            Arguments.of("create calendar event tomorrow 3pm", "create_calendar_event"),
            Arguments.of("lookup contact John", "lookup_contact"),
            Arguments.of("semantic search vacation photos", "semantic_search"),
            Arguments.of("share file report.pdf", "share_file"),
            Arguments.of("send the invoice document to Atharv on whatsapp", "send_document_whatsapp"),
            Arguments.of("find budget report and share on whatsapp to John", "send_document_whatsapp"),
        )

        private fun normalized(payload: String): NormalizedObservation {
            val observation = Observation(
                id = UUID.randomUUID(),
                timestamp = Instant.now(),
                sessionId = UUID.randomUUID(),
                traceId = UUID.randomUUID(),
                modality = Modality.TEXT,
                payload = payload,
            )
            return NormalizedObservation(
                observation = observation,
                normalizedPayload = payload.lowercase(),
                tokens = payload.lowercase().split("\\s+".toRegex()),
                metadata = mapOf("modality" to "text"),
            )
        }
    }
}
