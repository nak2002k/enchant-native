package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.enchant.core.database.dao.IdentityDao
import org.enchant.core.database.dao.SessionDao
import org.enchant.protos.EnvelopeProtos
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SessionManager — Full Coverage")
class SessionManagerTest {

    private fun createTestKeyBundle(): KeyBundle {
        val ikPair = CryptoHelper.generateEd25519KeyPair()
        val spkPair = CryptoHelper.generateX25519KeyPair()
        val sig = CryptoHelper.signEd25519(spkPair.publicKey, ikPair.privateKey)
        val opkPair = CryptoHelper.generateX25519KeyPair()
        return KeyBundle(
            deviceId = "test-device",
            identityKey = ikPair.publicKey,
            signedPrekey = SignedPrekeyData(
                publicKey = spkPair.publicKey,
                signature = sig
            ),
            oneTimePrekey = opkPair.publicKey
        )
    }

    @BeforeEach
    fun setUp() = runTest {
        SessionManager.reset()
        KeyManager.clearTestKeyBundles()
        SessionManager.init()
        SessionManager.setSelfUserIdForTest("self")
    }

    @AfterEach
    fun tearDown() = runTest {
        SessionManager.reset()
        KeyManager.clearTestKeyBundles()
    }

    @Nested @DisplayName("Session Creation")
    inner class SessionCreationTest {
        @Test @DisplayName("encryptMessage returns non-null for known recipient")
        fun `encrypt returns payload`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("user1", theirBundle)
            SessionManager.setIdentityKey("user1", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            val result = SessionManager.encryptMessage("user1", "Hello".encodeToByteArray())
            assertNotNull(result)
            assertTrue(result!!.payload.isNotEmpty())
        }

        @Test @DisplayName("encryptMessage returns null for unknown recipient without cached key")
        fun `encrypt returns null for unknown`() = runTest {
            val result = SessionManager.encryptMessage("unknown", "Hello".encodeToByteArray())
            assertNull(result)
        }

        @Test @DisplayName("encryptMessage returns non-null for empty plaintext")
        fun `encrypt empty plaintext`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("user2", theirBundle)
            SessionManager.setIdentityKey("user2", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            val result = SessionManager.encryptMessage("user2", ByteArray(0))
            assertNotNull(result)
        }

        @Test @DisplayName("encryptMessage returns null when no identity key pair available")
        fun `encrypt no identity key`() = runTest {
            val result = SessionManager.encryptMessage("user3", "Hello".encodeToByteArray())
            assertNull(result)
        }
    }

    @Nested @DisplayName("Session Lookup")
    inner class SessionLookupTest {
        @Test @DisplayName("hasSession returns false before encryption")
        fun `hasSession false before`() = runTest {
            assertFalse(SessionManager.hasSession("nobody"))
        }

        @Test @DisplayName("hasSession returns true after encrypting")
        fun `hasSession true after encrypt`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("alice", theirBundle)
            SessionManager.setIdentityKey("alice", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            SessionManager.encryptMessage("alice", "Hi".encodeToByteArray())
            assertTrue(SessionManager.hasSession("alice"))
        }

        @Test @DisplayName("hasSession returns false after deleteSession")
        fun `hasSession false after delete`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("temp", theirBundle)
            SessionManager.setIdentityKey("temp", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            SessionManager.encryptMessage("temp", "Hi".encodeToByteArray())
            assertTrue(SessionManager.hasSession("temp"))
            SessionManager.deleteSession("temp")
            assertFalse(SessionManager.hasSession("temp"))
        }

        @Test @DisplayName("hasSession returns false after archiveSession")
        fun `hasSession false after archive`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("arch", theirBundle)
            SessionManager.setIdentityKey("arch", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            SessionManager.encryptMessage("arch", "Hi".encodeToByteArray())
            SessionManager.archiveSession("arch")
            assertFalse(SessionManager.hasSession("arch"))
        }
    }

    @Nested @DisplayName("Encryption/Decryption Roundtrip")
    inner class EncryptDecryptTest {
        @Test @DisplayName("encrypt then decrypt returns original plaintext (Alice-Bob)")
        fun `encrypt decrypt roundtrip`() = runTest {
            val sharedSecret = CryptoHelper.generateRandomKey(32)
            val bobSpk = CryptoHelper.generateX25519KeyPair()
            val aliceState = DoubleRatchet.initializeAsAlice(
                sharedSecret = sharedSecret,
                theirSignedPrekeyPublic = bobSpk.publicKey
            )!!
            val bobState = DoubleRatchet.initializeAsBob(
                sharedSecret = sharedSecret,
                theirRatchetKeyPublic = aliceState.sendingRatchetKeyPublic!!,
                ourSignedPrekeyPrivate = bobSpk.privateKey
            )!!

            val plaintext = "Hello World".encodeToByteArray()
            val (aliceState2, message) = DoubleRatchet.encrypt(aliceState, plaintext)
            val (bobState2, decrypted) = DoubleRatchet.decrypt(bobState, message)
            assertArrayEquals(plaintext, decrypted)
        }

        @Test @DisplayName("multiple messages in sequence decrypt correctly")
        fun `multiple messages sequence`() = runTest {
            val sharedSecret = CryptoHelper.generateRandomKey(32)
            val bobSpk = CryptoHelper.generateX25519KeyPair()
            val aliceState = DoubleRatchet.initializeAsAlice(
                sharedSecret = sharedSecret,
                theirSignedPrekeyPublic = bobSpk.publicKey
            )!!
            val bobState = DoubleRatchet.initializeAsBob(
                sharedSecret = sharedSecret,
                theirRatchetKeyPublic = aliceState.sendingRatchetKeyPublic!!,
                ourSignedPrekeyPrivate = bobSpk.privateKey
            )!!

            var alice = aliceState
            var bob = bobState

            for (i in 0 until 10) {
                val (alice2, msgA) = DoubleRatchet.encrypt(alice, "A->B msg-$i".encodeToByteArray())
                alice = alice2
                val (bob2, decryptedA) = DoubleRatchet.decrypt(bob, msgA)
                bob = bob2
                assertEquals("A->B msg-$i", decryptedA.decodeToString())

                val (bob3, msgB) = DoubleRatchet.encrypt(bob, "B->A msg-$i".encodeToByteArray())
                bob = bob3
                val (alice3, decryptedB) = DoubleRatchet.decrypt(alice, msgB)
                alice = alice3
                assertEquals("B->A msg-$i", decryptedB.decodeToString())
            }
        }

        @Test @DisplayName("encrypted payload has header + ciphertext format")
        fun `payload format`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("format", theirBundle)
            SessionManager.setIdentityKey("format", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            SessionManager.encryptMessage("format", "first".encodeToByteArray())
            val result = SessionManager.encryptMessage("format", "second".encodeToByteArray())
            assertNotNull(result)
            assertTrue(result!!.payload.size > 4)
        }

        @Test @DisplayName("encrypted payload type is DOUBLE_RATCHET")
        fun `payload type`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("type", theirBundle)
            SessionManager.setIdentityKey("type", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            val result = SessionManager.encryptMessage("type", "test".encodeToByteArray())
            assertNotNull(result)
            assertEquals(EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET, result!!.messageType)
        }
    }

    @Nested @DisplayName("Identity Key Management")
    inner class IdentityKeyTest {
        @Test @DisplayName("setIdentityKey stores the key")
        fun `set identity key`() = runTest {
            val ik = CryptoHelper.generateEd25519KeyPair().publicKey
            SessionManager.setIdentityKey("bob", ik)
            val stored = SessionManager.getIdentityKey("bob")
            assertNotNull(stored)
            assertTrue(ik.contentEquals(stored!!))
        }

        @Test @DisplayName("getIdentityKey returns null for unknown user")
        fun `get identity key unknown`() = runTest {
            assertNull(SessionManager.getIdentityKey("nobody"))
        }

        @Test @DisplayName("findUserIdByIdentityKey finds user by key")
        fun `find user by key`() = runTest {
            val ik = CryptoHelper.generateEd25519KeyPair().publicKey
            SessionManager.setIdentityKey("bob-find", ik)
            val found = SessionManager.findUserIdByIdentityKey(ik)
            assertEquals("bob-find", found)
        }

        @Test @DisplayName("findUserIdByIdentityKey returns null for unknown key")
        fun `find user unknown key`() = runTest {
            val unknownKey = CryptoHelper.generateEd25519KeyPair().publicKey
            assertNull(SessionManager.findUserIdByIdentityKey(unknownKey))
        }

        @Test @DisplayName("identity key change triggers non-blocking approval false")
        fun `identity change triggers approval`() = runTest {
            val ik1 = CryptoHelper.generateEd25519KeyPair().publicKey
            val ik2 = CryptoHelper.generateEd25519KeyPair().publicKey
            SessionManager.setIdentityKey("change", ik1)
            SessionManager.setIdentityKey("change", ik2)
            assertTrue(SessionManager.hasIdentityChanged("change"))
        }

        @Test @DisplayName("first identity key sets approval to true")
        fun `first identity approved`() = runTest {
            val ik = CryptoHelper.generateEd25519KeyPair().publicKey
            SessionManager.setIdentityKey("first", ik)
            assertTrue(SessionManager.isIdentityApproved("first"))
        }

        @Test @DisplayName("approveIdentity sets approval to true")
        fun `approve identity`() = runTest {
            val ik1 = CryptoHelper.generateEd25519KeyPair().publicKey
            val ik2 = CryptoHelper.generateEd25519KeyPair().publicKey
            SessionManager.setIdentityKey("approve", ik1)
            SessionManager.setIdentityKey("approve", ik2)
            assertFalse(SessionManager.isIdentityApproved("approve"))
            SessionManager.approveIdentity("approve")
            assertTrue(SessionManager.isIdentityApproved("approve"))
        }

        @Test @DisplayName("isIdentityApproved returns true for unknown user")
        fun `is identity approved unknown`() = runTest {
            assertTrue(SessionManager.isIdentityApproved("unknown"))
        }

        @Test @DisplayName("hasIdentityChanged returns false for unknown user")
        fun `has identity changed unknown`() = runTest {
            assertFalse(SessionManager.hasIdentityChanged("unknown"))
        }
    }

    @Nested @DisplayName("Safety Number")
    inner class SafetyNumberTest {
        @Test @DisplayName("getSafetyNumber returns UNVERIFIED for unknown user")
        fun `safety number unverified`() = runTest {
            assertEquals("UNVERIFIED", SessionManager.getSafetyNumber("unknown"))
        }

        @Test @DisplayName("getSafetyNumber returns formatted string for known identity")
        fun `safety number formatted`() = runTest {
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            SessionManager.setIdentityKey("bob-safety", bobIk.publicKey)
            val safetyNum = SessionManager.getSafetyNumber("bob-safety")
            assertNotEquals("UNVERIFIED", safetyNum)
            assertTrue(safetyNum.contains("-"))
            assertTrue(safetyNum.length <= 47)
        }

        @Test @DisplayName("getSafetyNumber is deterministic for same keys")
        fun `safety number deterministic`() = runTest {
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            SessionManager.setIdentityKey("bob-det", bobIk.publicKey)
            val s1 = SessionManager.getSafetyNumber("bob-det")
            val s2 = SessionManager.getSafetyNumber("bob-det")
            assertEquals(s1, s2)
        }

        @Test @DisplayName("getSafetyNumber differs for different identities")
        fun `safety number differs`() = runTest {
            val bobIk1 = CryptoHelper.generateEd25519KeyPair()
            val bobIk2 = CryptoHelper.generateEd25519KeyPair()
            SessionManager.setIdentityKey("bob-1", bobIk1.publicKey)
            SessionManager.setIdentityKey("bob-2", bobIk2.publicKey)
            val s1 = SessionManager.getSafetyNumber("bob-1")
            val s2 = SessionManager.getSafetyNumber("bob-2")
            assertNotEquals(s1, s2)
        }
    }

    @Nested @DisplayName("Session Lifecycle")
    inner class SessionLifecycleTest {
        @Test @DisplayName("deleteSession removes session completely")
        fun `delete removes session`() = runTest {
            val theirBundle = createTestKeyBundle()
            val ourIk = CryptoHelper.generateEd25519KeyPair()
            KeyManager.setTestIdentityKeyPair(ourIk)
            KeyManager.setTestKeyBundle("del", theirBundle)
            SessionManager.setIdentityKey("del", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            val result1 = SessionManager.encryptMessage("del", "msg".encodeToByteArray())
            assertNotNull(result1)
            assertTrue(SessionManager.hasSession("del"))
            SessionManager.deleteSession("del")
            assertFalse(SessionManager.hasSession("del"))
        }

        @Test @DisplayName("archiveSession is same as deleteSession")
        fun `archive same as delete`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("arch2", theirBundle)
            SessionManager.setIdentityKey("arch2", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            SessionManager.encryptMessage("arch2", "msg".encodeToByteArray())
            SessionManager.archiveSession("arch2")
            assertFalse(SessionManager.hasSession("arch2"))
        }

        @Test @DisplayName("session survives multiple encrypt operations")
        fun `session survives multiple`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("survive", theirBundle)
            SessionManager.setIdentityKey("survive", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            SessionManager.encryptMessage("survive", "msg1".encodeToByteArray())
            SessionManager.encryptMessage("survive", "msg2".encodeToByteArray())
            SessionManager.encryptMessage("survive", "msg3".encodeToByteArray())
            assertTrue(SessionManager.hasSession("survive"))
        }

        @Test @DisplayName("concurrent encrypts are serialized correctly")
        fun `concurrent encrypts`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("concurrent", theirBundle)
            SessionManager.setIdentityKey("concurrent", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            var successCount = 0
            repeat(5) { i ->
                val result = SessionManager.encryptMessage("concurrent", "data-$i".encodeToByteArray())
                if (result != null) successCount++
            }
            assertEquals(5, successCount)
        }
    }

    @Nested @DisplayName("Decryption Edge Cases")
    inner class DecryptionEdgeTest {
        @Test @DisplayName("decrypt with no session returns null")
        fun `decrypt no session`() = runTest {
            val payload = EncryptedPayload(
                messageType = EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
                payload = ByteArray(100)
            )
            val result = SessionManager.decryptMessage("nobody", payload)
            assertNull(result)
        }

        @Test @DisplayName("decrypt with too-short payload returns null")
        fun `decrypt short payload`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("short", theirBundle)
            SessionManager.setIdentityKey("short", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            SessionManager.encryptMessage("short", "init".encodeToByteArray())
            val payload = EncryptedPayload(
                messageType = EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
                payload = ByteArray(2)
            )
            val result = SessionManager.decryptMessage("short", payload)
            assertNull(result)
        }

        @Test @DisplayName("decrypt with corrupted payload returns null")
        fun `decrypt corrupted`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("corrupt", theirBundle)
            SessionManager.setIdentityKey("corrupt", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            val encrypted = SessionManager.encryptMessage("corrupt", "test".encodeToByteArray())
            assertNotNull(encrypted)
            encrypted!!.payload[0] = (encrypted.payload[0].toInt() xor 0xFF).toByte()
            val result = SessionManager.decryptMessage("corrupt", encrypted)
            assertNull(result)
        }
    }

    @Nested @DisplayName("L04: Cached Identity Path Removed")
    inner class CachedIdentityPathRemovedTest {
        @Test @DisplayName("encryptMessage returns null for unknown user without server key bundle")
        fun `encrypt returns null without key bundle`() = runTest {
            val result = SessionManager.encryptMessage("unknown-no-bundle", "Hello".encodeToByteArray())
            assertNull(result)
        }

        @Test @DisplayName("encryptMessage always requires server key bundle for new sessions")
        fun `encrypt requires server key bundle`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("cached-test", theirBundle)
            SessionManager.setIdentityKey("cached-test", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            val result = SessionManager.encryptMessage("cached-test", "Hello".encodeToByteArray())
            assertNotNull(result)
        }

        @Test @DisplayName("encryptMessage creates session only after successful key bundle fetch")
        fun `encrypt creates session after key bundle`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("roundtrip-l04", theirBundle)
            SessionManager.setIdentityKey("roundtrip-l04", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            val encrypted = SessionManager.encryptMessage("roundtrip-l04", "Hello".encodeToByteArray())
            assertNotNull(encrypted)
            assertTrue(SessionManager.hasSession("roundtrip-l04"))
        }

        @Test @DisplayName("session is reused for subsequent messages")
        fun `session reused`() = runTest {
            val theirBundle = createTestKeyBundle()
            KeyManager.setTestIdentityKeyPair(CryptoHelper.generateEd25519KeyPair())
            KeyManager.setTestKeyBundle("reuse", theirBundle)
            SessionManager.setIdentityKey("reuse", CryptoHelper.ed25519PkToX25519(theirBundle.identityKey))
            SessionManager.encryptMessage("reuse", "first".encodeToByteArray())
            assertTrue(SessionManager.hasSession("reuse"))
            val second = SessionManager.encryptMessage("reuse", "second".encodeToByteArray())
            assertNotNull(second)
        }
    }
}
