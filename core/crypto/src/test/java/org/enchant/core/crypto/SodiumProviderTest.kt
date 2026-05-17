package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("SodiumProvider")
class SodiumProviderTest {

    @Test
    @DisplayName("sodiumMlock does not throw")
    fun `mlock`() {
        val data = ByteArray(32) { it.toByte() }
        assertDoesNotThrow { SodiumProvider.sodiumMlock(data) }
    }

    @Test
    @DisplayName("sodiumMunlock does not throw")
    fun `munlock`() {
        val data = ByteArray(32) { it.toByte() }
        assertDoesNotThrow { SodiumProvider.sodiumMunlock(data) }
    }

    @Test
    @DisplayName("sodiumMemZero zeroes the array")
    fun `memzero`() {
        val data = ByteArray(32) { 0x42 }
        SodiumProvider.sodiumMemZero(data)
        assertTrue(data.all { it == 0.toByte() })
    }

    @Test
    @DisplayName("sodiumMemZero handles empty array")
    fun `memzero empty`() {
        val data = ByteArray(0)
        assertDoesNotThrow { SodiumProvider.sodiumMemZero(data) }
    }
}
