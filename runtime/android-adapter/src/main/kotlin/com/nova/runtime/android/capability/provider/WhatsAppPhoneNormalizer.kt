package com.nova.runtime.android.capability.provider

/**
 * Normalizes phone numbers for WhatsApp deep links (`wa.me` / `api.whatsapp.com`).
 *
 * WhatsApp expects digits-only international format (no `+`). Local 10-digit Indian
 * mobiles (starting 6–9) get [DEFAULT_COUNTRY_CODE] prepended when no country code is present.
 */
object WhatsAppPhoneNormalizer {
    /** Default country calling code (India). Configurable for tests / future settings. */
    const val DEFAULT_COUNTRY_CODE = "91"

    fun normalize(
        raw: String?,
        defaultCountryCode: String = DEFAULT_COUNTRY_CODE,
    ): String? {
        if (raw.isNullOrBlank()) return null

        var digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        // International prefix 00…
        if (digits.startsWith("00") && digits.length > 2) {
            digits = digits.drop(2)
        }

        val cc = defaultCountryCode.filter { it.isDigit() }.ifBlank { DEFAULT_COUNTRY_CODE }

        // Trunk-prefixed local mobile: 09876543210 → 919876543210
        if (digits.length == 11 && digits.startsWith("0") && digits[1] in '6'..'9') {
            return cc + digits.drop(1)
        }

        // Bare 10-digit Indian mobile
        if (digits.length == 10 && digits[0] in '6'..'9') {
            return cc + digits
        }

        // Already international (or non-IN local) — return digits as-is
        return digits
    }
}
