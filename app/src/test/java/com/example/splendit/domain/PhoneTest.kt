package com.example.splendit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTest {
    @Test
    fun danishNumbersHaveEightDigits() {
        assertTrue(Phone.isValid(Country.DK, "12 34 56 78"))
        assertFalse(Phone.isValid(Country.DK, "1234567"))
        assertFalse(Phone.isValid(Country.DK, "123456789"))
    }

    @Test
    fun storedWithCountryCodeAndNoSpaces() {
        assertEquals("+4512345678", Phone.e164(Country.DK, "12 34 56 78"))
    }

    @Test
    fun trunkZeroIsDroppedAfterTheCountryCode() {
        assertEquals("+46701234567", Phone.e164(Country.SE, "070-123 45 67"))
        assertTrue(Phone.isValid(Country.SE, "070-123 45 67"))
    }

    @Test
    fun lookupKeyReadsEmailPhoneOrDanishDefault() {
        assertEquals("sofie@example.com", Phone.lookupKey("  Sofie@Example.com "))
        assertEquals("+4520000002", Phone.lookupKey("20 00 00 02"))
        assertEquals("+46701234567", Phone.lookupKey("+46 70 123 45 67"))
        assertNull(Phone.lookupKey("123"))
        assertNull(Phone.lookupKey("+12"))
    }
}
