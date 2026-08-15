package com.nova.runtime.app.action

import java.util.Locale

enum class SemanticAction {
    LAUNCH_APP,
    SEND_MESSAGE,
    MAKE_CALL,
    SEARCH_CONTENT,
    TOGGLE_SETTING,
    NAVIGATE_SYSTEM,
    SHARE_DOCUMENT,
    PLAY_MEDIA,
    UNKNOWN
}

data class ParsedIntent(
    val action: SemanticAction,
    val targetApp: String?,
    val recipient: String?,
    val queryOrContent: String?,
    val originalPrompt: String
)

class CognitiveIntentEngine {

    fun parseIntent(prompt: String): ParsedIntent {
        val trimmed = prompt.trim()
        val lower = trimmed.lowercase(Locale.getDefault())

        return when {
            // 1. Share File / Document / PDF Intent
            lower.contains("send file") || lower.contains("share file") || lower.contains("send pdf") ||
                    lower.contains("share pdf") || lower.contains("share document") || lower.contains("pdf") -> {
                parseShareDocumentIntent(trimmed, lower)
            }

            // 2. Play Media / Spotify Music Intent
            lower.contains("spotify") || lower.contains("play song") || lower.contains("play music") || lower.contains("play artist") -> {
                parsePlayMediaIntent(trimmed, lower)
            }

            // 3. WhatsApp or Direct Messaging Intent
            lower.contains("whatsapp") || lower.startsWith("text") || lower.startsWith("send message") || lower.startsWith("message") || lower.startsWith("ping") -> {
                parseMessageIntent(trimmed, lower)
            }

            // 4. Phone Calling / Voice Calling Intent
            lower.startsWith("call") || lower.startsWith("dial") || lower.startsWith("ring") || lower.contains("phone call") -> {
                parseCallIntent(trimmed, lower)
            }

            // 5. System Hardware / Settings Toggle Intent
            lower.contains("flashlight") || lower.contains("torch") || lower.contains("wifi") || lower.contains("wi-fi") ||
                    lower.contains("bluetooth") || lower.contains("volume") || lower.contains("sound") || lower.contains("setting") -> {
                parseSettingIntent(trimmed, lower)
            }

            // 6. System Navigation / Close / Home Intent
            lower.contains("go home") || lower.contains("home screen") || lower.contains("close app") || lower.contains("minimize") -> {
                ParsedIntent(
                    action = SemanticAction.NAVIGATE_SYSTEM,
                    targetApp = "Home",
                    recipient = null,
                    queryOrContent = null,
                    originalPrompt = trimmed
                )
            }

            // 7. Search / Video Playback Intent
            lower.startsWith("search") || lower.startsWith("play") || lower.startsWith("watch") || lower.startsWith("google") ||
                    lower.startsWith("find") || lower.startsWith("navigate") || lower.startsWith("directions") || lower.startsWith("download") -> {
                parseSearchIntent(trimmed, lower)
            }

            // 8. Launch / Open App Intent
            lower.startsWith("open") || lower.startsWith("launch") || lower.startsWith("start") || lower.startsWith("run") ||
                    lower.startsWith("bring up") || lower.startsWith("show") || lower.startsWith("turn on") -> {
                parseLaunchIntent(trimmed, lower)
            }

            // Fallback
            else -> {
                parseFallbackIntent(trimmed, lower)
            }
        }
    }

    private fun parseShareDocumentIntent(prompt: String, lower: String): ParsedIntent {
        val isWhatsApp = lower.contains("whatsapp")
        val app = if (isWhatsApp) "WhatsApp" else "File Share"
        val fileName = extractAfterKeywords(prompt, listOf("send", "share", "file", "pdf", "document", "on", "whatsapp", "to"))

        return ParsedIntent(
            action = SemanticAction.SHARE_DOCUMENT,
            targetApp = app,
            recipient = extractRecipient(lower),
            queryOrContent = fileName.ifBlank { "document.pdf" },
            originalPrompt = prompt
        )
    }

    private fun parsePlayMediaIntent(prompt: String, lower: String): ParsedIntent {
        val targetApp = if (lower.contains("spotify")) "Spotify" else "YouTube Music"
        val searchQuery = extractAfterKeywords(prompt, listOf("play", "song", "music", "artist", "on", "spotify", "youtube"))

        return ParsedIntent(
            action = SemanticAction.PLAY_MEDIA,
            targetApp = targetApp,
            recipient = null,
            queryOrContent = searchQuery.ifBlank { prompt },
            originalPrompt = prompt
        )
    }

    private fun parseMessageIntent(prompt: String, lower: String): ParsedIntent {
        val isWhatsApp = lower.contains("whatsapp")
        val app = if (isWhatsApp) "WhatsApp" else "Messages"
        val content = extractAfterKeywords(prompt, listOf("whatsapp", "message", "text", "saying", "that", "to"))

        return ParsedIntent(
            action = SemanticAction.SEND_MESSAGE,
            targetApp = app,
            recipient = extractRecipient(lower),
            queryOrContent = content.ifBlank { prompt },
            originalPrompt = prompt
        )
    }

    private fun parseCallIntent(prompt: String, lower: String): ParsedIntent {
        val isWhatsAppCall = lower.contains("whatsapp")
        val app = if (isWhatsAppCall) "WhatsApp" else "Phone"
        val recipient = extractAfterKeywords(prompt, listOf("call", "dial", "ring", "phone", "whatsapp", "to"))

        return ParsedIntent(
            action = SemanticAction.MAKE_CALL,
            targetApp = app,
            recipient = recipient.ifBlank { "Contact" },
            queryOrContent = null,
            originalPrompt = prompt
        )
    }

    private fun parseSettingIntent(prompt: String, lower: String): ParsedIntent {
        val target = when {
            lower.contains("flashlight") || lower.contains("torch") -> "Flashlight"
            lower.contains("wifi") || lower.contains("wi-fi") -> "Wi-Fi"
            lower.contains("bluetooth") -> "Bluetooth"
            lower.contains("volume") || lower.contains("sound") -> "Sound"
            else -> "Settings"
        }

        return ParsedIntent(
            action = SemanticAction.TOGGLE_SETTING,
            targetApp = target,
            recipient = null,
            queryOrContent = prompt,
            originalPrompt = prompt
        )
    }

    private fun parseSearchIntent(prompt: String, lower: String): ParsedIntent {
        val targetApp = when {
            lower.contains("youtube") || lower.contains("video") -> "YouTube"
            lower.contains("map") || lower.contains("navigate") || lower.contains("direction") -> "Google Maps"
            lower.contains("download") || lower.contains("install") || lower.contains("play store") -> "Play Store"
            else -> "Google Search"
        }

        val query = extractAfterKeywords(
            prompt,
            listOf("search", "play", "watch", "google", "find", "navigate", "to", "directions", "download", "install", "for", "on", "youtube")
        )

        return ParsedIntent(
            action = SemanticAction.SEARCH_CONTENT,
            targetApp = targetApp,
            recipient = null,
            queryOrContent = query.ifBlank { prompt },
            originalPrompt = prompt
        )
    }

    private fun parseLaunchIntent(prompt: String, lower: String): ParsedIntent {
        val targetApp = extractAfterKeywords(
            prompt,
            listOf("open", "launch", "start", "run", "bring up", "show", "turn on", "the", "app")
        )

        return ParsedIntent(
            action = SemanticAction.LAUNCH_APP,
            targetApp = targetApp.ifBlank { prompt },
            recipient = null,
            queryOrContent = null,
            originalPrompt = prompt
        )
    }

    private fun parseFallbackIntent(prompt: String, lower: String): ParsedIntent {
        return ParsedIntent(
            action = SemanticAction.UNKNOWN,
            targetApp = prompt,
            recipient = null,
            queryOrContent = prompt,
            originalPrompt = prompt
        )
    }

    private fun extractAfterKeywords(fullText: String, keywords: List<String>): String {
        val words = fullText.split("\\s+".toRegex())
        val filtered = words.filter { word -> word.lowercase(Locale.getDefault()) !in keywords }
        return filtered.joinToString(" ").trim()
    }

    private fun extractRecipient(lowerPrompt: String): String? {
        val regex = Regex("(?:to|call|text)\\s+([a-zA-Z0-9]+)")
        return regex.find(lowerPrompt)?.groupValues?.getOrNull(1)
    }
}
