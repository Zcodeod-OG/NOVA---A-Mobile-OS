package com.nova.runtime.ai.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelFileValidatorTest {

    @Test
    fun isValid_rejectsTinyWhisperPlaceholder() {
        val dir = createTempDir("nova-model-validator")
        val file = File(dir, ModelAssetPaths.WHISPER_MODEL)
        file.writeBytes(ByteArray(15) { 0 })

        assertFalse(ModelFileValidator.isValid(ModelAssetPaths.WHISPER_MODEL, file))
        assertTrue(ModelFileValidator.isCorrupt(ModelAssetPaths.WHISPER_MODEL, file))
    }

    @Test
    fun isValid_acceptsWhisperAboveMinimum() {
        val dir = createTempDir("nova-model-validator-whisper")
        val file = File(dir, ModelAssetPaths.WHISPER_MODEL)
        file.writeBytes(ByteArray(11_000_000) { 1 })

        assertTrue(ModelFileValidator.isValid(ModelAssetPaths.WHISPER_MODEL, file))
    }
}
