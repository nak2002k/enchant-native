package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Bug Fix Verification Tests")
class BugFixVerificationTest {

    @BeforeEach
    fun setUp() {
        NativeSessionManager.reset()
        KeyManager.reset()
    }

    @AfterEach
    fun tearDown() {
        NativeSessionManager.reset()
        KeyManager.reset()
    }

    @Nested
    @DisplayName("Bug #2 — encryptMessage fails when recipient key bundle unavailable")
    inner class Bug2SelfUserIdTest {
        @Test
        fun `encryptMessage returns null when key bundle unavailable`() = runTest {
            NativeSessionManager.reset()
            NativeSessionManager.init(selfUserId = "user1")
            val result = NativeSessionManager.encryptMessage("user1", "test".encodeToByteArray())
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("Bug #16 — needsKeyRotation returns true when never rotated")
    inner class Bug16KeyRotationTest {
        @Test
        fun `needsKeyRotation returns true when lastSpkRotationMs is 0`() = runTest {
            KeyManager.reset()
            assertTrue(KeyManager.needsKeyRotation())
        }
    }

}


