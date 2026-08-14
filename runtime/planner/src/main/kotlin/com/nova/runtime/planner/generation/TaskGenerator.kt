package com.nova.runtime.planner.generation

import com.nova.runtime.models.Nir
import com.nova.runtime.models.NovaCapabilityOperations
import com.nova.runtime.models.ReasoningContext
import com.nova.runtime.planner.model.PlanTask
import com.nova.runtime.planner.model.SubGoal

interface TaskGenerator {
    fun generate(
        subGoals: List<SubGoal>,
        nir: Nir,
        reasoningContext: ReasoningContext,
    ): List<PlanTask>
}

/**
 * Generates atomic tasks from decomposed sub-goals — MSP §8 pipeline.
 */
class DefaultTaskGenerator : TaskGenerator {
    override fun generate(
        subGoals: List<SubGoal>,
        nir: Nir,
        reasoningContext: ReasoningContext,
    ): List<PlanTask> {
        val tasks = mutableListOf<PlanTask>()
        var order = 0

        for (subGoal in subGoals.sortedBy { it.sortOrder }) {
            when {
                subGoal.key == "validate_assumptions" -> {
                    reasoningContext.assumptions.forEachIndexed { index, assumption ->
                        tasks += PlanTask(
                            key = "check_assumption:$index",
                            subGoalKey = subGoal.key,
                            actionType = "validate_assumption",
                            inputs = mapOf("assumption" to assumption),
                            sortOrder = order++,
                        )
                    }
                }
                subGoal.key.startsWith("apply_constraint:") -> {
                    val constraintKey = subGoal.key.removePrefix("apply_constraint:")
                    tasks += PlanTask(
                        key = "enforce_constraint:$constraintKey",
                        subGoalKey = subGoal.key,
                        actionType = "enforce_constraint",
                        inputs = mapOf(
                            "constraint" to constraintKey,
                            "value" to (nir.constraints[constraintKey] ?: ""),
                        ),
                        sortOrder = order++,
                    )
                }
                subGoal.key.startsWith("execute_capability:") -> {
                    val capability = subGoal.capability ?: subGoal.key.removePrefix("execute_capability:")
                    tasks += PlanTask(
                        key = "prepare:$capability",
                        subGoalKey = subGoal.key,
                        actionType = "prepare_capability",
                        inputs = buildCapabilityInputs(capability, nir, reasoningContext),
                        sortOrder = order++,
                    )
                    tasks += PlanTask(
                        key = "execute:$capability",
                        subGoalKey = subGoal.key,
                        actionType = "execute_capability",
                        inputs = buildCapabilityInputs(capability, nir, reasoningContext),
                        sortOrder = order++,
                    )
                }
                subGoal.key == "execute_goal" -> {
                    tasks += PlanTask(
                        key = "execute_goal",
                        subGoalKey = subGoal.key,
                        actionType = "execute_goal",
                        inputs = mapOf(
                            "goal" to nir.goal,
                            "confidence" to reasoningContext.confidence.toString(),
                        ),
                        sortOrder = order++,
                    )
                }
                subGoal.key == "complete_goal" -> {
                    tasks += PlanTask(
                        key = "complete_goal",
                        subGoalKey = subGoal.key,
                        actionType = "complete_goal",
                        inputs = mapOf("goal" to nir.goal),
                        sortOrder = order++,
                    )
                }
            }
        }

        return tasks
    }

    private fun buildCapabilityInputs(
        capability: String,
        nir: Nir,
        reasoningContext: ReasoningContext,
    ): Map<String, String> = buildMap {
        put("capability", capability)
        put("goal", nir.goal)

        when {
            capability == "search.documents" || capability == NovaCapabilityOperations.SEARCH_DOCUMENTS -> {
                put("capabilityType", "search.documents")
                put("operation", "search")
                put("capabilityOperation", NovaCapabilityOperations.SEARCH_DOCUMENTS)
                val query = nir.constraints["documentQuery"]
                    ?: nir.constraints["query"]
                    ?: nir.context["rawPayload"].orEmpty()
                put("query", query)
                nir.constraints["answerMode"]?.let { put("answerMode", it) }
                nir.constraints["maxDisplayChars"]?.let { put("maxDisplayChars", it) }
                nir.constraints["displayMode"]?.let { put("displayMode", it) }
                nir.constraints["dateScope"]?.let { put("dateScope", it) }
                nir.constraints["topic"]?.let { put("topic", it) }
                nir.constraints["timeRangeStart"]?.let { put("timeRangeStart", it) }
                nir.constraints["timeRangeEnd"]?.let { put("timeRangeEnd", it) }
                nir.constraints["documentSubject"]?.let { put("documentSubject", it) }
            }
            capability == "whatsapp" || capability.startsWith("whatsapp") -> {
                put("capabilityType", "whatsapp")
                put("operation", "send_message")
                put("capabilityOperation", NovaCapabilityOperations.WHATSAPP_SEND_MESSAGE)
                nir.constraints["compoundFlow"]?.let {
                    put("compoundFlow", it)
                    put("awaitSearchResult", "true")
                }
                // Document/file chain: recipient required; never seed message from the command.
                if (nir.constraints["compoundFlow"] != null) {
                    nir.constraints["recipient"]?.let {
                        put("recipient", it)
                        put("name", it)
                    }
                }
            }
            capability == "alarm" || nir.constraints["capabilityOperation"] == NovaCapabilityOperations.ALARM_CREATE -> {
                put("capabilityType", "time")
                put("operation", NovaCapabilityOperations.ALARM_CREATE)
                put("capabilityOperation", NovaCapabilityOperations.ALARM_CREATE)
            }
            capability == "calendar.read" ||
                nir.constraints["capabilityOperation"] == NovaCapabilityOperations.CALENDAR_READ -> {
                put("capabilityType", "time")
                put("operation", NovaCapabilityOperations.CALENDAR_READ)
                put("capabilityOperation", NovaCapabilityOperations.CALENDAR_READ)
            }
            capability == "calendar" || nir.constraints["capabilityOperation"] == NovaCapabilityOperations.CALENDAR_CREATE -> {
                put("capabilityType", "time")
                put("operation", NovaCapabilityOperations.CALENDAR_CREATE)
                put("capabilityOperation", NovaCapabilityOperations.CALENDAR_CREATE)
                if (nir.goal == "schedule_from_message" || nir.constraints["requiresConfirmation"] == "true") {
                    put("requiresConfirmation", "true")
                }
            }
            capability == "device" ||
                nir.constraints["capabilityOperation"] == NovaCapabilityOperations.DEVICE_OPEN_APP ||
                nir.constraints["capabilityOperation"] == NovaCapabilityOperations.DEVICE_APP_SEARCH -> {
                put("capabilityType", "device")
                val op = nir.constraints["capabilityOperation"]
                    ?: NovaCapabilityOperations.DEVICE_OPEN_APP
                // Prefer short form that AndroidDeviceProvider accepts; aliases also map the qualified name.
                put(
                    "operation",
                    when (op) {
                        NovaCapabilityOperations.DEVICE_APP_SEARCH -> "app_search"
                        else -> "open_app"
                    },
                )
                put("capabilityOperation", op)
            }
            else -> {
                put("capabilityType", nir.constraints["capabilityType"] ?: capability)
                put("operation", nir.constraints["operation"] ?: "execute")
                nir.constraints["capabilityOperation"]?.let { put("capabilityOperation", it) }
            }
        }

        nir.constraints["channel"]?.let { put("channel", it) }
        nir.constraints["recipient"]?.let {
            put("recipient", it)
            put("name", it)
        }
        nir.constraints["fileName"]?.let { put("fileName", it) }
        nir.constraints["message"]?.let {
            put("message", it)
            put("text", it)
        }
        nir.constraints["triggerAtMillis"]?.let { put("triggerAtMillis", it) }
        nir.constraints["alarmKind"]?.let { put("alarmKind", it) }
        nir.constraints["intentType"]?.let { put("intentType", it) }
        nir.constraints["appName"]?.let { put("appName", it) }
        nir.constraints["searchQuery"]?.let { put("searchQuery", it) }
        nir.constraints["label"]?.let { put("label", it) }
        nir.constraints["title"]?.let { put("title", it) }
        nir.constraints["startTime"]?.let { put("startTime", it) }
        nir.constraints["endTime"]?.let { put("endTime", it) }
        nir.context["rawPayload"]?.let { payload ->
            // Never put the raw command into WhatsApp search.query — it becomes message text
            // via AndroidCommunicationProvider's query fallback.
            val isWhatsApp = capability == "whatsapp" || capability.startsWith("whatsapp") ||
                nir.constraints["capabilityOperation"] == NovaCapabilityOperations.WHATSAPP_SEND_MESSAGE
            val isDocumentChain = nir.constraints["compoundFlow"] != null
            if (!containsKey("query") && !isWhatsApp) {
                put("query", payload)
            }
            // Only use rawPayload as message for plain text WhatsApp sends that lack an
            // extracted message — never for document/file chains.
            if (
                isWhatsApp &&
                !isDocumentChain &&
                !containsKey("message") &&
                nir.goal != "send_document_whatsapp"
            ) {
                // Prefer not to paste the whole command; leave message empty so validation /
                // contact resolution can still proceed with recipient + phone.
                // Keep a last-resort only when there is no recipient either.
                if (nir.constraints["recipient"].isNullOrBlank()) {
                    put("message", payload)
                    put("text", payload)
                }
            }
        }
        if (nir.entities.isNotEmpty()) {
            put("entities", nir.entities.sorted().joinToString(","))
        }
        if (reasoningContext.resolvedEntities.isNotEmpty()) {
            put(
                "resolvedEntities",
                reasoningContext.resolvedEntities.entries
                    .sortedBy { it.key }
                    .joinToString(",") { "${it.key}=${it.value}" },
            )
        }
    }
}
