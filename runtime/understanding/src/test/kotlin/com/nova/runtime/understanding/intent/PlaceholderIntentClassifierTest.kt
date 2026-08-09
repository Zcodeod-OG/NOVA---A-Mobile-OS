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
            Arguments.of("wake me at 6:30", "set_alarm"),
            Arguments.of("wake me up at 7am", "set_alarm"),
            Arguments.of("create calendar event tomorrow 3pm", "create_calendar_event"),
            Arguments.of("lookup contact John", "lookup_contact"),
            Arguments.of("semantic search vacation photos", "semantic_search"),
            Arguments.of("share file report.pdf", "share_file"),
            Arguments.of("send the invoice document to Atharv on whatsapp", "send_document_whatsapp"),
            Arguments.of("find budget report and share on whatsapp to John", "send_document_whatsapp"),
            Arguments.of("send todays mess menu to atharv on whatsapp", "send_document_whatsapp"),
            Arguments.of("send today's mess menu to Atharv on WhatsApp", "send_document_whatsapp"),
            Arguments.of("send mess menu to atharv sharma on whatsapp", "send_document_whatsapp"),
            Arguments.of("send this month's mess menu to atharv on whatsapp", "send_document_whatsapp"),
            Arguments.of("send this months mess menu to atharv on whatsapp", "send_document_whatsapp"),
            Arguments.of("send this month mess menu to Atharv on WhatsApp", "send_document_whatsapp"),
            // File/document send without saying "whatsapp" — still WhatsApp document chain
            Arguments.of("send bookly prospectus report to atharv sharma", "send_document_whatsapp"),
            Arguments.of("send the invoice to Atharv", "send_document_whatsapp"),
            Arguments.of("send mess menu to atharv sharma", "send_document_whatsapp"),
            Arguments.of("what is todays dinner menu", "document_question"),
            Arguments.of("what's today's dinner menu", "document_question"),
            Arguments.of("show me todays mess menu", "document_question"),
            Arguments.of("what is today's lunch", "document_question"),
            Arguments.of(
                "from timetable tell me my todays lec slots between 12pm to 3pm",
                "document_question",
            ),
            Arguments.of("tell me my todays lecture slots", "document_question"),
            Arguments.of("from mess menu show breakfast", "document_question"),
            // Plain WhatsApp messages must not collide with document share / document_question
            Arguments.of("send hello to atharv on whatsapp", "send_whatsapp_message"),
            // Channel unspecified → WhatsApp text (not SMS stub)
            Arguments.of("send hello to atharv sharma", "send_whatsapp_message"),
            Arguments.of("send good morning to atharv", "send_whatsapp_message"),
            // App launching — any app by name (task 1)
            Arguments.of("open youtube", "open_application"),
            Arguments.of("launch chrome", "open_application"),
            Arguments.of("open the settings", "open_application"),
            Arguments.of("open whatsapp", "open_application"),
            // In-app actions (task 2)
            Arguments.of("open youtube and search shape of you", "app_action_search"),
            Arguments.of("search shape of you on youtube", "app_action_search"),
            Arguments.of("play shape of you on youtube", "app_action_search"),
            Arguments.of("open spotify and play blinding lights", "app_action_search"),
            Arguments.of("play shape of you on spotify", "app_action_search"),
            // Negation — no key trigger (task 2)
            Arguments.of("do not open youtube", "negated_command"),
            Arguments.of("don't open youtube", "negated_command"),
            Arguments.of("never open youtube", "negated_command"),
            Arguments.of("do not set an alarm for 7am", "negated_command"),
            Arguments.of("don't send a whatsapp message to John", "negated_command"),
            // Reminders route to a real alarm (task 1 / auto-recognition)
            Arguments.of("remind me to call mom at 5pm", "set_reminder"),
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
