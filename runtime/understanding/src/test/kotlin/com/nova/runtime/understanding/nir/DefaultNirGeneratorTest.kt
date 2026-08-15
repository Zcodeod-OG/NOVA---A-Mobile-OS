package com.nova.runtime.understanding.nir

import com.nova.runtime.models.Modality
import com.nova.runtime.models.Observation
import com.nova.runtime.understanding.entity.ExtractedEntity
import com.nova.runtime.understanding.intent.DetectedIntent
import com.nova.runtime.understanding.normalization.NormalizedObservation
import com.nova.runtime.understanding.routing.InferenceRouteDecision
import com.nova.runtime.understanding.routing.StubInferenceRoutingHook
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultNirGeneratorTest {

    private val generator = DefaultNirGenerator()

    @Test
    fun generate_mapsIntentEntitiesAndCapabilities() = runTest {
        val normalized = normalizedObservation("Remind me tomorrow")
        val intent = DetectedIntent(
            goal = "set_reminder",
            intentType = "set_reminder",
            confidence = 0.8,
        )
        val entities = listOf(
            ExtractedEntity(type = "time_expression", value = "tomorrow", confidence = 0.75),
        )
        val constraints = mapOf("modality" to "text", "time_0" to "tomorrow")
        val route = InferenceRouteDecision(
            tier = StubInferenceRoutingHook.TIER_DETERMINISTIC,
            reason = "short_utterance",
            useDeterministicPath = true,
            confidence = 0.9,
        )

        val nir = generator.generate(normalized, intent, entities, constraints, route)

        assertEquals(DefaultNirGenerator.NIR_VERSION, nir.version)
        assertEquals("set_reminder", nir.goal)
        assertEquals(listOf("tomorrow"), nir.entities)
        assertEquals(listOf("alarm"), nir.requiredCapabilities)
        assertTrue(nir.confidence in 0.0..1.0)
        assertEquals("0", nir.context["inferenceTier"])
    }

    @Test
    fun generate_openApplication_extractsAppName() = runTest {
        val nir = generator.generate(
            normalizedObservation("open youtube"),
            DetectedIntent(goal = "open_application", intentType = "open_application", confidence = 0.85),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("youtube", nir.constraints["appName"])
        assertEquals("device.open_app", nir.constraints["capabilityOperation"])
        assertEquals("device", nir.constraints["capabilityType"])
        assertEquals("open_app", nir.constraints["operation"])
        assertEquals(listOf("device"), nir.requiredCapabilities)
    }

    @Test
    fun generate_openApplication_stripsTheAndAppSuffix() = runTest {
        val nir = generator.generate(
            normalizedObservation("launch the spotify app"),
            DetectedIntent(goal = "open_application", intentType = "open_application", confidence = 0.85),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("spotify", nir.constraints["appName"])
    }

    @Test
    fun generate_appActionSearch_openAndSearchForm() = runTest {
        val nir = generator.generate(
            normalizedObservation("open youtube and search shape of you"),
            DetectedIntent(goal = "app_action_search", intentType = "app_action_search", confidence = 0.85),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("youtube", nir.constraints["appName"])
        assertEquals("shape of you", nir.constraints["searchQuery"])
        assertEquals("device.app_search", nir.constraints["capabilityOperation"])
        assertEquals(listOf("device"), nir.requiredCapabilities)
    }

    @Test
    fun generate_appActionSearch_searchOnAppForm() = runTest {
        val nir = generator.generate(
            normalizedObservation("search shape of you on youtube"),
            DetectedIntent(goal = "app_action_search", intentType = "app_action_search", confidence = 0.85),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("youtube", nir.constraints["appName"])
        assertEquals("shape of you", nir.constraints["searchQuery"])
    }

    @Test
    fun generate_negatedCommand_marksNegatedAndExtractsAction() = runTest {
        val nir = generator.generate(
            normalizedObservation("do not open youtube"),
            DetectedIntent(goal = "negated_command", intentType = "negated_command", confidence = 0.85),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("true", nir.constraints["negated"])
        assertEquals("open youtube", nir.constraints["negatedAction"])
        assertEquals(listOf("none"), nir.requiredCapabilities)
    }

    @Test
    fun generate_setReminder_extractsLabelAndTriggerTime() = runTest {
        val nir = generator.generate(
            normalizedObservation("remind me to call mom at 5pm"),
            DetectedIntent(goal = "set_reminder", intentType = "set_reminder", confidence = 0.85),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("call mom", nir.constraints["label"])
        assertTrue(!nir.constraints["triggerAtMillis"].isNullOrBlank())
        assertEquals("alarm.create", nir.constraints["capabilityOperation"])
        assertEquals("reminder", nir.constraints["alarmKind"])
        assertEquals(listOf("alarm"), nir.requiredCapabilities)
    }

    @Test
    fun generate_setAlarm_marksClockKindAndTrigger() = runTest {
        val nir = generator.generate(
            normalizedObservation("set alarm for 7am"),
            DetectedIntent(goal = "set_alarm", intentType = "set_alarm", confidence = 0.85),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertTrue(!nir.constraints["triggerAtMillis"].isNullOrBlank())
        assertEquals("alarm.create", nir.constraints["capabilityOperation"])
        assertEquals("clock", nir.constraints["alarmKind"])
        assertEquals(listOf("alarm"), nir.requiredCapabilities)
    }

    @Test
    fun generate_sendTodaysMessMenu_extractsDocumentQueryAndRecipient() = runTest {
        val nir = generator.generate(
            normalizedObservation("send todays mess menu to atharv on whatsapp"),
            DetectedIntent(
                goal = "send_document_whatsapp",
                intentType = "send_document_whatsapp",
                confidence = 0.85,
            ),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("search_document_whatsapp", nir.constraints["compoundFlow"])
        assertEquals("todays mess menu", nir.constraints["documentQuery"])
        assertEquals("Atharv", nir.constraints["recipient"])
        assertEquals(listOf("search.documents", "whatsapp"), nir.requiredCapabilities)
    }

    @Test
    fun generate_sendMessMenu_twoWordRecipient_titleCasesBothNames() = runTest {
        val nir = generator.generate(
            normalizedObservation("send mess menu to atharv sharma on whatsapp"),
            DetectedIntent(
                goal = "send_document_whatsapp",
                intentType = "send_document_whatsapp",
                confidence = 0.85,
            ),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("Atharv Sharma", nir.constraints["recipient"])
        assertEquals("mess menu", nir.constraints["documentQuery"])
        assertEquals(listOf("search.documents", "whatsapp"), nir.requiredCapabilities)
    }

    @Test
    fun generate_extractDocumentContent_setsExtractModeAndScopedDisplay() = runTest {
        val nir = generator.generate(
            normalizedObservation("extract content from todays mess menu"),
            DetectedIntent(
                goal = "extract_document_content",
                intentType = "extract_document_content",
                confidence = 0.85,
            ),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("todays mess menu", nir.constraints["documentQuery"])
        assertEquals("extract", nir.constraints["answerMode"])
        assertEquals("4000", nir.constraints["maxDisplayChars"])
        assertEquals(DefaultNirGenerator.DISPLAY_MODE_SCOPED, nir.constraints["displayMode"])
        assertEquals("today", nir.constraints["dateScope"])
        assertEquals(listOf("search.documents"), nir.requiredCapabilities)
    }

    @Test
    fun generate_extractDocumentContent_verbatimWhenNoScope() = runTest {
        val nir = generator.generate(
            normalizedObservation("extract content from bookly prospectus report"),
            DetectedIntent(
                goal = "extract_document_content",
                intentType = "extract_document_content",
                confidence = 0.85,
            ),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("bookly prospectus report", nir.constraints["documentQuery"])
        assertEquals(DefaultNirGenerator.DISPLAY_MODE_VERBATIM, nir.constraints["displayMode"])
    }

    @Test
    fun generate_documentQuestion_extractsDinnerMenuQuery() = runTest {
        val nir = generator.generate(
            normalizedObservation("what is todays dinner menu"),
            DetectedIntent(
                goal = "document_question",
                intentType = "document_question",
                confidence = 0.85,
            ),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("todays dinner menu", nir.constraints["documentQuery"])
        assertEquals("todays dinner menu", nir.constraints["query"])
        assertEquals("today", nir.constraints["dateScope"])
        assertEquals("dinner", nir.constraints["topic"])
        assertEquals(listOf("search.documents"), nir.requiredCapabilities)
        assertEquals("search.documents", nir.constraints["capabilityOperation"])
    }

    @Test
    fun generate_documentQuestion_timetableTimeRangeConstraints() = runTest {
        val nir = generator.generate(
            normalizedObservation(
                "from timetable tell me my todays lec slots between 12pm to 3pm",
            ),
            DetectedIntent(
                goal = "document_question",
                intentType = "document_question",
                confidence = 0.85,
            ),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("timetable", nir.constraints["documentSubject"])
        assertTrue(nir.constraints["documentQuery"].orEmpty().contains("timetable"))
        assertTrue(nir.constraints["documentQuery"].orEmpty().contains("todays"))
        assertEquals("today", nir.constraints["dateScope"])
        assertEquals("12:00", nir.constraints["timeRangeStart"])
        assertEquals("15:00", nir.constraints["timeRangeEnd"])
        assertEquals("lec slots", nir.constraints["topic"])
        assertEquals(listOf("search.documents"), nir.requiredCapabilities)
    }

    @Test
    fun generate_sendThisMonthsMessMenu_keepsMessMenuInDocumentQuery() = runTest {
        val nir = generator.generate(
            normalizedObservation("send this months mess menu to atharv on whatsapp"),
            DetectedIntent(
                goal = "send_document_whatsapp",
                intentType = "send_document_whatsapp",
                confidence = 0.85,
            ),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("search_document_whatsapp", nir.constraints["compoundFlow"])
        assertEquals("this months mess menu", nir.constraints["documentQuery"])
        assertTrue(nir.constraints["documentQuery"].orEmpty().contains("mess menu"))
        assertEquals("Atharv", nir.constraints["recipient"])
        assertEquals(listOf("search.documents", "whatsapp"), nir.requiredCapabilities)
    }

    @Test
    fun generate_sendBooklyProspectus_withoutWhatsAppWord_setsDocumentQueryNotMessage() = runTest {
        val nir = generator.generate(
            normalizedObservation("send bookly prospectus report to atharv sharma"),
            DetectedIntent(
                goal = "send_document_whatsapp",
                intentType = "send_document_whatsapp",
                confidence = 0.85,
            ),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("search_document_whatsapp", nir.constraints["compoundFlow"])
        assertEquals("bookly prospectus report", nir.constraints["documentQuery"])
        assertEquals("Atharv Sharma", nir.constraints["recipient"])
        assertEquals("whatsapp", nir.constraints["channel"])
        assertTrue(nir.constraints["message"].isNullOrBlank())
        assertEquals(listOf("search.documents", "whatsapp"), nir.requiredCapabilities)
    }

    @Test
    fun generate_sendHelloWithoutWhatsAppWord_defaultsChannelWhatsApp() = runTest {
        val nir = generator.generate(
            normalizedObservation("send hello to atharv sharma"),
            DetectedIntent(
                goal = "send_whatsapp_message",
                intentType = "send_whatsapp_message",
                confidence = 0.85,
            ),
            emptyList(),
            emptyMap(),
            deterministicRoute(),
        )

        assertEquals("hello", nir.constraints["message"])
        assertEquals("Atharv Sharma", nir.constraints["recipient"])
        assertEquals("whatsapp", nir.constraints["channel"])
        assertEquals(listOf("whatsapp"), nir.requiredCapabilities)
    }

    private fun deterministicRoute() = InferenceRouteDecision(
        tier = StubInferenceRoutingHook.TIER_DETERMINISTIC,
        reason = "test",
        useDeterministicPath = true,
        confidence = 0.9,
    )

    private fun normalizedObservation(payload: String): NormalizedObservation {
        val observation = Observation(
            id = UUID.randomUUID(),
            timestamp = Instant.parse("2026-08-06T10:00:00Z"),
            sessionId = UUID.randomUUID(),
            traceId = UUID.randomUUID(),
            modality = Modality.TEXT,
            payload = payload,
            metadata = mapOf("source" to "test"),
        )
        return NormalizedObservation(
            observation = observation,
            normalizedPayload = payload.trim(),
            tokens = payload.lowercase().split("\\s+".toRegex()),
            metadata = mapOf("modality" to "text", "source" to "test"),
        )
    }
}
