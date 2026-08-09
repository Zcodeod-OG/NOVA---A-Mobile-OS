package com.nova.runtime.android.capability.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppPhoneNormalizerTest {

    @Test
    fun normalize_tenDigitIndianMobile_prependsDefaultCountryCode() {
        assertEquals("919876543210", WhatsAppPhoneNormalizer.normalize("9876543210"))
        assertEquals("919876543210", WhatsAppPhoneNormalizer.normalize("+91 98765 43210"))
        assertEquals(
            WhatsAppPhoneNormalizer.DEFAULT_COUNTRY_CODE,
            "91",
        )
    }

    @Test
    fun normalize_stripsFormattingAndPlus() {
        assertEquals("14155552671", WhatsAppPhoneNormalizer.normalize("+1 (415) 555-2671"))
    }

    @Test
    fun normalize_trunkZeroLocalMobile_stripsZeroAndAddsCountryCode() {
        assertEquals("919876543210", WhatsAppPhoneNormalizer.normalize("09876543210"))
    }

    @Test
    fun normalize_alreadyInternational_keepsDigits() {
        assertEquals("919876543210", WhatsAppPhoneNormalizer.normalize("919876543210"))
        assertEquals("447911123456", WhatsAppPhoneNormalizer.normalize("+44 7911 123456"))
    }

    @Test
    fun normalize_doubleZeroPrefix_treatedAsInternational() {
        assertEquals("919876543210", WhatsAppPhoneNormalizer.normalize("00919876543210"))
    }

    @Test
    fun normalize_blankOrNonDigits_returnsNull() {
        assertNull(WhatsAppPhoneNormalizer.normalize(null))
        assertNull(WhatsAppPhoneNormalizer.normalize(""))
        assertNull(WhatsAppPhoneNormalizer.normalize("   "))
        assertNull(WhatsAppPhoneNormalizer.normalize("not-a-number"))
    }

    @Test
    fun normalize_customCountryCode() {
        // 10-digit mobiles starting 6–9 still get the configured country code.
        assertEquals(
            "449876543210",
            WhatsAppPhoneNormalizer.normalize("9876543210", defaultCountryCode = "44"),
        )
    }
}
