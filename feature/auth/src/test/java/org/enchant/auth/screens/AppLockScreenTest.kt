package org.enchant.auth.screens

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("AppLockScreen")
class AppLockScreenTest {

    @Test
    @DisplayName("isLegacySha256Hash correctly identifies legacy hashes")
    fun `identifies legacy sha256 hash`() {
        assertTrue(isLegacySha256Hash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
        assertTrue(isLegacySha256Hash("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"))
        assertFalse(isLegacySha256Hash("\$argon2id\$v=19\$m=65536,t=3,p=4\$..."))
        assertFalse(isLegacySha256Hash("short"))
    }

    @Test
    @DisplayName("legacySha256Hash produces consistent results")
    fun `legacy sha256 hash is consistent`() {
        val pin = "123456"
        val hash1 = legacySha256Hash(pin)
        val hash2 = legacySha256Hash(pin)
        assertEquals(hash1, hash2)
    }

    @Test
    @DisplayName("Different PINs produce different hashes")
    fun `different pins different legacy hashes`() {
        val hash1 = legacySha256Hash("123456")
        val hash2 = legacySha256Hash("654321")
        assertNotEquals(hash1, hash2)
    }

    @Test
    @DisplayName("Empty PIN produces valid 64-char hash")
    fun `empty pin legacy hash length`() {
        val hash = legacySha256Hash("")
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }
}

@DisplayName("AppLockPolicy")
class AppLockPolicyTest {

    @Test
    @DisplayName("four failed attempts do not lock out")
    fun `below threshold no lockout`() {
        var attempts = 0
        var lockoutUntil = 0L
        var lockouts = 0
        repeat(4) {
            val r = AppLockPolicy.onFailedAttempt(attempts, 1_000_000L, lockouts)
            attempts = r.failedAttempts
            lockoutUntil = r.lockoutUntilMs
            lockouts = r.completedLockouts
        }
        assertEquals(4, attempts)
        assertEquals(0L, lockoutUntil)
        assertFalse(AppLockPolicy.isLockedOut(lockoutUntil, 1_000_000L))
    }

    @Test
    @DisplayName("fifth failed attempt triggers lockout and resets counter")
    fun `threshold triggers lockout`() {
        val r = AppLockPolicy.onFailedAttempt(4, 1_000_000L, 0)
        assertEquals(0, r.failedAttempts)
        assertEquals(1, r.completedLockouts)
        assertTrue(r.lockoutUntilMs > 1_000_000L)
        assertTrue(AppLockPolicy.isLockedOut(r.lockoutUntilMs, 1_000_000L))
    }

    @Test
    @DisplayName("first lockout lasts BASE_LOCKOUT_MS")
    fun `first lockout duration`() {
        assertEquals(AppLockPolicy.BASE_LOCKOUT_MS, AppLockPolicy.lockoutDurationMs(0))
    }

    @Test
    @DisplayName("lockout grows exponentially")
    fun `lockout escalates`() {
        val first = AppLockPolicy.lockoutDurationMs(0)
        val second = AppLockPolicy.lockoutDurationMs(1)
        val third = AppLockPolicy.lockoutDurationMs(2)
        assertEquals(first * 2, second)
        assertEquals(second * 2, third)
    }

    @Test
    @DisplayName("lockout is capped at MAX_LOCKOUT_MS")
    fun `lockout capped`() {
        assertEquals(AppLockPolicy.MAX_LOCKOUT_MS, AppLockPolicy.lockoutDurationMs(10))
        assertEquals(AppLockPolicy.MAX_LOCKOUT_MS, AppLockPolicy.lockoutDurationMs(20))
    }

    @Test
    @DisplayName("isLockedOut is false after the deadline passes")
    fun `unlocked after deadline`() {
        val deadline = 1_000_000L + AppLockPolicy.lockoutDurationMs(0)
        assertTrue(AppLockPolicy.isLockedOut(deadline, deadline - 1))
        assertFalse(AppLockPolicy.isLockedOut(deadline, deadline + 1))
    }

    @Test
    @DisplayName("remainingSeconds returns 0 when not locked and positive when locked")
    fun `remaining seconds`() {
        assertEquals(0L, AppLockPolicy.remainingSeconds(1_000L, 2_000L))
        val r = AppLockPolicy.remainingSeconds(5_000L, 1_000L)
        assertTrue(r > 0L)
    }

    @Test
    @DisplayName("after lockout lifts, attempts resume counting from zero")
    fun `attempts reset after lockout`() {
        val outcome = AppLockPolicy.onFailedAttempt(4, 100L, 1)
        assertEquals(0, outcome.failedAttempts)
        // After the deadline, a fresh failure is attempt 1.
        val next = AppLockPolicy.onFailedAttempt(0, 200L, outcome.completedLockouts)
        assertEquals(1, next.failedAttempts)
        assertEquals(0L, next.lockoutUntilMs)
    }
}