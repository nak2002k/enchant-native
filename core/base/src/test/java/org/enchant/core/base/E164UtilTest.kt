package org.enchant.core.base

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class E164UtilTest {

    @Test
    fun `formatToE164 formats valid US number`() {
        val result = E164Util.formatToE164("+14155551234")
        assertEquals("+14155551234", result)
    }

    @Test
    fun `formatToE164 formats local number with region`() {
        val result = E164Util.formatToE164("(415) 555-1234", "US")
        assertEquals("+14155551234", result)
    }

    @Test
    fun `formatToE164 returns null for invalid number`() {
        val result = E164Util.formatToE164("not-a-number")
        assertNull(result)
    }

    @Test
    fun `isValidE164 returns true for valid number`() {
        assertTrue(E164Util.isValidE164("+14155551234"))
    }

    @Test
    fun `getCountryCode returns correct code`() {
        val code = E164Util.getCountryCode("+14155551234")
        assertEquals(1, code)
    }

    @Test
    fun `getCountryCode returns null for invalid number`() {
        assertNull(E164Util.getCountryCode("invalid"))
    }

    @Test
    fun `getNationalNumber returns national number`() {
        val national = E164Util.getNationalNumber("+14155551234")
        assertEquals(4155551234L, national)
    }

    @Test
    fun `getRegionCodeForCountryCode returns US`() {
        assertEquals("US", E164Util.getRegionCodeForCountryCode(1))
    }

    @Test
    fun `getCountryCodeForRegion returns 1 for US`() {
        assertEquals(1, E164Util.getCountryCodeForRegion("US"))
    }

    @Test
    fun `formatNational formats locally`() {
        val result = E164Util.formatNational("+14155551234")
        assertNotNull(result)
    }

    @Test
    fun `formatInternational formats with country code`() {
        val result = E164Util.formatInternational("+14155551234")
        assertEquals("+1 415-555-1234", result)
    }

    @Test
    fun `formatToE164 handles GB number`() {
        val result = E164Util.formatToE164("+442079460758")
        assertEquals("+442079460758", result)
    }

    @Test
    fun `formatToE164 handles null region gracefully`() {
        val result = E164Util.formatToE164("+14155551234", null)
        assertEquals("+14155551234", result)
    }
}
