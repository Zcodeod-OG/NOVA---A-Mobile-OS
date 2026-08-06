package com.nova.runtime.inference.prompt

import com.nova.runtime.inference.tier.InferenceTier
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DefaultPromptManagerTest {

    private val promptManager = DefaultPromptManager()

    @Test
    fun assemble_substitutesVariablesAndIncludesUserContent() {
        val assembled = promptManager.assemble(
            templateId = DefaultPromptManager.DEFAULT_TEMPLATE_ID,
            userContent = "call john",
            tier = InferenceTier.LIGHT,
            variables = mapOf("tier" to InferenceTier.LIGHT.label),
        )

        assertEquals(DefaultPromptManager.DEFAULT_TEMPLATE_ID, assembled.templateId)
        assertContains(assembled.text, "call john")
        assertContains(assembled.text, InferenceTier.LIGHT.label)
    }

    @Test
    fun assemble_unknownTemplateFallsBackToDefault() {
        val assembled = promptManager.assemble(
            templateId = "missing-template",
            userContent = "search files",
            tier = InferenceTier.FULL,
            variables = mapOf("tier" to InferenceTier.FULL.label),
        )

        assertEquals(DefaultPromptManager.DEFAULT_TEMPLATE_ID, assembled.templateId)
    }
}
