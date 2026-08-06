# Conversation Module — Speech

## Speech Recognition

Production apps wire `WhisperSpeechRecognizer` from `:runtime:ai-native` via Koin override in `aiIntegrationModule`.

- **On-device ASR:** ONNX Whisper (`whisper-tiny.onnx`)
- **Fallback:** `StubSpeechRecognizer` when model file is absent

## Text-to-Speech (Deferred)

`StubTextToSpeech` remains the default implementation. On-device TTS via Android `TextToSpeech` engine is feasible but deferred to avoid blocking the inference MVP — it requires engine initialization on the main thread and locale/voice selection UX.

To enable later: implement `AndroidTextToSpeech` in `:runtime:ai-native` and override `TextToSpeech` in Koin.
