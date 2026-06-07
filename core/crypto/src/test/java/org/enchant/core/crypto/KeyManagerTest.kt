package org.enchant.core.crypto

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.enchant.core.database.dao.SignedPreKeyRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("KeyManager — Full Coverage")
class KeyManagerTest {

    private lateinit var mockStore: PreKeyStore
    private lateinit var mockClient: KeyManager.ApiClientLike

    @BeforeEach
    fun setUp() {
        KeyManager.reset()
        mockStore = mockk(relaxed = true)
        mockClient = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        KeyManager.reset()
    }

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("init does not throw with no arguments")
        fun `init clean`() = runTest {
            KeyManager.init()
            assertTrue(true)
        }

        @Test @DisplayName("double init is safe (idempotent)")
        fun `double init safe`() = runTest {
            KeyManager.init()
            KeyManager.init()
            assertTrue(true)
        }

        @Test @DisplayName("init with preloaded identity keys")
        fun `init with keys`() = runTest {
            val pair = CryptoPrimitives.generateEd25519KeyPair()
            val pubB64 = CryptoPrimitives.base64UrlEncode(pair.publicKey)
            val privB64 = CryptoPrimitives.base64UrlEncode(pair.privateKey)
            KeyManager.init(identityPublicB64 = pubB64, identityPrivateB64 = privB64)
            assertTrue(KeyManager.hasKeys())
        }

        @Test @DisplayName("init with invalid base64 ignores keys")
        fun `init invalid base64`() = runTest {
            KeyManager.init(identityPublicB64 = "not-valid!!!", identityPrivateB64 = "not-valid!!!")
            assertFalse(KeyManager.hasKeys())
        }

        @Test @DisplayName("init with client and store")
        fun `init with deps`() = runTest {
            KeyManager.init(client = mockClient, store = mockStore)
            assertTrue(true)
        }
    }

    @Nested @DisplayName("Key Generation")
    inner class KeyGenerationTest {
        @Test @DisplayName("hasKeys returns false before generation")
        fun `has keys false before`() = runTest {
            KeyManager.init()
            assertFalse(KeyManager.hasKeys())
        }

        @Test @DisplayName("hasKeys returns true after setTestIdentityKeyPair")
        fun `has keys true after test set`() = runTest {
            KeyManager.init()
            val pair = CryptoPrimitives.generateEd25519KeyPair()
            KeyManager.setTestIdentityKeyPair(pair)
            assertTrue(KeyManager.hasKeys())
        }

        @Test @DisplayName("getIdentityKeyPair returns non-null after set")
        fun `identity key pair exists`() = runTest {
            KeyManager.init()
            val pair = CryptoPrimitives.generateEd25519KeyPair()
            KeyManager.setTestIdentityKeyPair(pair)
            val retrieved = KeyManager.getIdentityKeyPair()
            assertNotNull(retrieved)
            assertEquals(32, retrieved!!.publicKey.size)
            assertEquals(32, retrieved.privateKey.size)
        }

        @Test @DisplayName("getIdentityKeyPair returns null before set")
        fun `identity key pair null before`() = runTest {
            KeyManager.init()
            assertNull(KeyManager.getIdentityKeyPair())
        }

        @Test @DisplayName("getIdentityPublicKeyBase64 returns non-empty string")
        fun `identity key base64`() = runTest {
            KeyManager.init()
            val pair = CryptoPrimitives.generateEd25519KeyPair()
            KeyManager.setTestIdentityKeyPair(pair)
            val b64 = KeyManager.getIdentityPublicKeyBase64()
            assertNotNull(b64)
            assertTrue(b64!!.isNotEmpty())
        }

        @Test @DisplayName("identity key is stable across calls in same session")
        fun `identity key stable`() = runTest {
            KeyManager.init()
            val pair = CryptoPrimitives.generateEd25519KeyPair()
            KeyManager.setTestIdentityKeyPair(pair)
            val first = KeyManager.getIdentityPublicKeyBase64()
            val second = KeyManager.getIdentityPublicKeyBase64()
            assertEquals(first, second)
        }

        @Test @DisplayName("signWithIdentity returns 64-byte signature after generation")
        fun `sign with identity`() = runTest {
            KeyManager.init()
            val pair = CryptoPrimitives.generateEd25519KeyPair()
            KeyManager.setTestIdentityKeyPair(pair)
            val sig = KeyManager.signWithIdentity("data to sign".encodeToByteArray())
            assertNotNull(sig)
            assertEquals(64, sig!!.size)
        }

        @Test @DisplayName("signWithIdentity returns null before generation")
        fun `sign with identity null before`() = runTest {
            KeyManager.init()
            val sig = KeyManager.signWithIdentity("data".encodeToByteArray())
            assertNull(sig)
        }

        @Test @DisplayName("signature verifies with public key")
        fun `signature verifies`() = runTest {
            KeyManager.init()
            val pair = CryptoPrimitives.generateEd25519KeyPair()
            KeyManager.setTestIdentityKeyPair(pair)
            val data = "verify me".encodeToByteArray()
            val sig = KeyManager.signWithIdentity(data)
            assertNotNull(sig)
            assertTrue(CryptoPrimitives.verifyEd25519(data, sig!!, pair.publicKey))
        }
    }

    @Nested @DisplayName("Key Bundle Fetch")
    inner class KeyBundleTest {
        @Test @DisplayName("fetchKeyBundle returns null without API client or test bundle")
        fun `fetch bundle null no client`() = runTest {
            KeyManager.init()
            val bundle = KeyManager.fetchKeyBundle("nonexistent-user")
            assertNull(bundle)
        }

        @Test @DisplayName("fetchKeyBundle returns test bundle when set")
        fun `fetch bundle from test`() = runTest {
            KeyManager.init()
            val ikPair = CryptoPrimitives.generateEd25519KeyPair()
            val spkPair = CryptoPrimitives.generateX25519KeyPair()
            val sig = CryptoPrimitives.signEd25519(spkPair.publicKey, ikPair.privateKey)
            val opkPair = CryptoPrimitives.generateX25519KeyPair()
            val bundle = KeyManager.KeyBundle(
                deviceId = "test-device",
                identityKey = ikPair.publicKey,
                signedPrekey = KeyManager.SignedPrekeyData(spkPair.publicKey, sig),
                oneTimePrekey = opkPair.publicKey
            )
            KeyManager.setTestKeyBundle("alice", bundle)
            val fetched = KeyManager.fetchKeyBundle("alice")
            assertNotNull(fetched)
            assertEquals("test-device", fetched!!.deviceId)
            assertTrue(fetched.identityKey.contentEquals(ikPair.publicKey))
        }

        @Test @DisplayName("fetchKeyBundle prefers test bundle over API client")
        fun `fetch bundle test preferred`() = runTest {
            KeyManager.init(client = mockClient)
            val bundle = KeyManager.KeyBundle(
                deviceId = "test",
                identityKey = ByteArray(32),
                signedPrekey = KeyManager.SignedPrekeyData(ByteArray(32), ByteArray(64)),
                oneTimePrekey = null
            )
            KeyManager.setTestKeyBundle("bob", bundle)
            val fetched = KeyManager.fetchKeyBundle("bob")
            assertNotNull(fetched)
            coVerify(exactly = 0) { mockClient.get(any()) }
        }

        @Test @DisplayName("clearTestKeyBundles removes all test bundles")
        fun `clear test bundles`() = runTest {
            KeyManager.init()
            val bundle = KeyManager.KeyBundle(
                deviceId = "test",
                identityKey = ByteArray(32),
                signedPrekey = KeyManager.SignedPrekeyData(ByteArray(32), ByteArray(64)),
                oneTimePrekey = null
            )
            KeyManager.setTestKeyBundle("bob", bundle)
            assertNotNull(KeyManager.fetchKeyBundle("bob"))
            KeyManager.clearTestKeyBundles()
            assertNull(KeyManager.fetchKeyBundle("bob"))
        }
    }

    @Nested @DisplayName("SPK Rotation")
    inner class SpkRotationTest {
        @Test @DisplayName("needsKeyRotation returns true without PreKeyStore")
        fun `needs rotation true no store`() = runTest {
            KeyManager.init()
            assertTrue(KeyManager.needsKeyRotation())
        }

        @Test @DisplayName("rotateSignedPreKey fails without API client")
        fun `rotate spk no client`() = runTest {
            KeyManager.init(store = mockStore)
            val pair = CryptoPrimitives.generateEd25519KeyPair()
            KeyManager.setTestIdentityKeyPair(pair)
            val result = KeyManager.rotateSignedPreKey()
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("rotateSignedPreKey fails without identity key")
        fun `rotate spk no identity`() = runTest {
            KeyManager.init(client = mockClient, store = mockStore)
            val result = KeyManager.rotateSignedPreKey()
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("cleanSignedPreKeys delegates to store")
        fun `clean spk delegates`() = runTest {
            KeyManager.init(store = mockStore)
            KeyManager.cleanSignedPreKeys()
            coVerify(atLeast = 1) { mockStore.cleanSignedPreKeys() }
        }
    }

    @Nested @DisplayName("OPK Management")
    inner class OpkTest {
        @Test @DisplayName("topUpOpks does nothing without API client")
        fun `topup no client`() = runTest {
            KeyManager.init(store = mockStore)
            KeyManager.topUpOpks()
            coVerify(exactly = 0) { mockStore.generateOneTimePreKeys(any()) }
        }

        @Test @DisplayName("topUpOpks does nothing without PreKeyStore")
        fun `topup no store`() = runTest {
            KeyManager.init(client = mockClient)
            KeyManager.topUpOpks()
            coVerify(exactly = 0) { mockClient.get(any()) }
        }
    }

    @Nested @DisplayName("Key Accessors with PreKeyStore")
    inner class AccessorTest {
        @Test @DisplayName("getSignedPreKeyPair returns null without store")
        fun `spk null no store`() = runTest {
            KeyManager.init()
            assertNull(KeyManager.getSignedPreKeyPair())
        }

        @Test @DisplayName("getOneTimePreKeyPair returns null without store")
        fun `opk null no store`() = runTest {
            KeyManager.init()
            assertNull(KeyManager.getOneTimePreKeyPair(1))
        }

        @Test @DisplayName("consumeOneTimePreKey is safe without store")
        fun `consume opk no store`() = runTest {
            KeyManager.init()
            KeyManager.consumeOneTimePreKey(1)
            assertTrue(true)
        }

        @Test @DisplayName("getSignedPreKeyPair delegates to store")
        fun `spk delegates`() = runTest {
            val spkRecord = SignedPreKeyRecord(
                id = 1,
                publicKey = ByteArray(32) { 1 },
                privateKey = ByteArray(32) { 2 },
                signature = ByteArray(64),
                timestamp = System.currentTimeMillis()
            )
            coEvery { mockStore.getCurrentSignedPreKey() } returns spkRecord
            KeyManager.init(store = mockStore)
            val pair = KeyManager.getSignedPreKeyPair()
            assertNotNull(pair)
            assertTrue(pair!!.publicKey.contentEquals(ByteArray(32) { 1 }))
        }
    }

    @Nested @DisplayName("Reset")
    inner class ResetTest {
        @Test @DisplayName("reset clears identity key")
        fun `reset clears identity`() = runTest {
            KeyManager.init()
            KeyManager.setTestIdentityKeyPair(CryptoPrimitives.generateEd25519KeyPair())
            assertTrue(KeyManager.hasKeys())
            KeyManager.reset()
            KeyManager.init()
            assertFalse(KeyManager.hasKeys())
        }

        @Test @DisplayName("reset clears test bundles")
        fun `reset clears bundles`() = runTest {
            KeyManager.init()
            val bundle = KeyManager.KeyBundle(
                deviceId = "test",
                identityKey = ByteArray(32),
                signedPrekey = KeyManager.SignedPrekeyData(ByteArray(32), ByteArray(64)),
                oneTimePrekey = null
            )
            KeyManager.setTestKeyBundle("user", bundle)
            KeyManager.reset()
            KeyManager.init()
            assertNull(KeyManager.fetchKeyBundle("user"))
        }
    }

    @Nested @DisplayName("API Client Integration")
    inner class ApiClientTest {
        @Test @DisplayName("fetchKeyBundle parses server response")
        fun `fetch bundle from api`() = runTest {
            val ikPair = CryptoPrimitives.generateEd25519KeyPair()
            val spkPair = CryptoPrimitives.generateX25519KeyPair()
            val sig = CryptoPrimitives.signEd25519(spkPair.publicKey, ikPair.privateKey)

            val responseJson = kotlinx.serialization.json.buildJsonObject {
                put("devices", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("device_id", kotlinx.serialization.json.JsonPrimitive("device-1"))
                        put("identity_key", kotlinx.serialization.json.JsonPrimitive(CryptoPrimitives.base64UrlEncode(ikPair.publicKey)))
                        put("signed_prekey", kotlinx.serialization.json.buildJsonObject {
                            put("public_key", kotlinx.serialization.json.JsonPrimitive(CryptoPrimitives.base64UrlEncode(spkPair.publicKey)))
                            put("signature", kotlinx.serialization.json.JsonPrimitive(CryptoPrimitives.base64UrlEncode(sig)))
                        })
                    })
                })
            }
            coEvery { mockClient.get("/v1/keys/bundle/user1") } returns Result.success(responseJson)

            KeyManager.init(client = mockClient)
            val bundle = KeyManager.fetchKeyBundle("user1")
            assertNotNull(bundle)
            assertEquals("device-1", bundle!!.deviceId)
        }

        @Test @DisplayName("fetchKeyBundle returns null on API failure")
        fun `fetch bundle api fail`() = runTest {
            coEvery { mockClient.get(any()) } returns Result.failure(RuntimeException("network error"))
            KeyManager.init(client = mockClient)
            assertNull(KeyManager.fetchKeyBundle("user1"))
        }
    }
}
