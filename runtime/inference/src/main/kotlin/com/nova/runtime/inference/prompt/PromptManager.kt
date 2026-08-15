package com.nova.runtime.inference.prompt

import com.nova.runtime.inference.tier.InferenceTier

data class PromptTemplate(
    val id: String,
    val systemPrefix: String,
    val userSuffix: String = "",
)

data class AssembledPrompt(
    val templateId: String,
    val text: String,
    val tier: InferenceTier,
)

interface PromptManager {
    fun register(template: PromptTemplate)
    fun assemble(
        templateId: String,
        userContent: String,
        tier: InferenceTier,
        variables: Map<String, String> = emptyMap(),
    ): AssembledPrompt
}

class DefaultPromptManager(
    initialTemplates: List<PromptTemplate> = defaultTemplates(),
) : PromptManager {

    private val templates = initialTemplates.associateBy { it.id }.toMutableMap()

    override fun register(template: PromptTemplate) {
        templates[template.id] = template
    }

    override fun assemble(
        templateId: String,
        userContent: String,
        tier: InferenceTier,
        variables: Map<String, String>,
    ): AssembledPrompt {
        val template = templates[templateId] ?: templates.getValue(DEFAULT_TEMPLATE_ID)
        val substitutedPrefix = substitute(template.systemPrefix, variables)
        val substitutedSuffix = substitute(template.userSuffix, variables)
        val text = buildString {
            append(substitutedPrefix)
            if (substitutedPrefix.isNotBlank() && userContent.isNotBlank()) append('\n')
            append(userContent.trim())
            if (substitutedSuffix.isNotBlank()) {
                append('\n')
                append(substitutedSuffix)
            }
        }
        return AssembledPrompt(
            templateId = template.id,
            text = text,
            tier = tier,
        )
    }

    private fun substitute(text: String, variables: Map<String, String>): String {
        if (variables.isEmpty()) return text
        var result = text
        variables.forEach { (key, value) ->
            result = result.replace("{{$key}}", value)
        }
        return result
    }

    companion object {
        const val DEFAULT_TEMPLATE_ID = "general"
        const val INTENT_TEMPLATE_ID = "intent_classification"

        fun defaultTemplates(): List<PromptTemplate> = listOf(
            PromptTemplate(
                id = DEFAULT_TEMPLATE_ID,
                systemPrefix = "[tier={{tier}}] Infer structured meaning from the user input.",
                userSuffix = "Respond concisely.",
            ),
            PromptTemplate(
                id = INTENT_TEMPLATE_ID,
                systemPrefix = "[tier={{tier}}] Classify intent and extract goal.",
                userSuffix = "Return goal label only.",
            ),
        )
    }
}
