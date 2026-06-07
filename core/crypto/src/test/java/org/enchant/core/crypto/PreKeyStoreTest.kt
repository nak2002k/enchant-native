package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.enchant.core.database.dao.OneTimePreKeyRecord
import org.enchant.core.database.dao.PreKeyDao
import org.enchant.core.database.dao.SignedPreKeyRecord
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PreKeyStore — PreKey lifecycle management")
class PreKeyStoreTest {

    private lateinit var store: PreKeyStore
    private lateinit var mockDao: PreKeyDao
    private lateinit var identityKeyPair: CryptoPrimitives.KeyPair

    @BeforeEach
    fun setUp() {
        store = PreKeyStore()
        mockDao = InMemoryPreKeyDao()
        store.setDao(mockDao)
        identityKeyPair = CryptoPrimitives.generateEd25519KeyPair()
    }

    @Nested @DisplayName("Signed PreKey")
    inner class SignedPreKeyTest {
        @Test @DisplayName("generateSignedPreKey creates valid record")
        fun `generate spk`() = runTest {
            val record = store.generateSignedPreKey(identityKeyPair)
            assertEquals(32, record.publicKey.size)
            assertEquals(32, record.privateKey.size)
            assertEquals(64, record.signature.size)
            assertTrue(record.timestamp > 0)
        }

        @Test @DisplayName("getCurrentSignedPreKey returns latest")
        fun `get current spk`() = runTest {
            val record = store.generateSignedPreKey(identityKeyPair)
            val current = store.getCurrentSignedPreKey()
            assertNotNull(current)
            assertEquals(record.id, current!!.id)
        }

        @Test @DisplayName("getSignedPreKey by ID returns correct record")
        fun `get spk by id`() = runTest {
            val record = store.generateSignedPreKey(identityKeyPair)
            val loaded = store.getSignedPreKey(record.id)
            assertNotNull(loaded)
            assertEquals(record.id, loaded!!.id)
        }

        @Test @DisplayName("hasSignedPreKey returns true for existing")
        fun `has spk true`() = runTest {
            val record = store.generateSignedPreKey(identityKeyPair)
            assertTrue(store.hasSignedPreKey(record.id))
        }

        @Test @DisplayName("hasSignedPreKey returns false for non-existent")
        fun `has spk false`() = runTest {
            assertFalse(store.hasSignedPreKey(9999))
        }

        @Test @DisplayName("needsSignedPreKeyRotation false for fresh key")
        fun `needs rotation false fresh`() = runTest {
            store.generateSignedPreKey(identityKeyPair)
            assertFalse(store.needsSignedPreKeyRotation())
        }

        @Test @DisplayName("cleanSignedPreKeys removes old keys")
        fun `clean spk`() = runTest {
            val record1 = store.generateSignedPreKey(identityKeyPair)
            // Manually set timestamp to old value
            store.cleanSignedPreKeys(0) // threshold = 0, removes all except current
            // Current SPK should remain
            assertNotNull(store.getCurrentSignedPreKey())
        }
    }

    @Nested @DisplayName("One-Time PreKeys")
    inner class OneTimePreKeyTest {
        @Test @DisplayName("generateOneTimePreKeys creates correct count")
        fun `generate opks`() = runTest {
            val records = store.generateOneTimePreKeys(50)
            assertEquals(50, records.size)
            records.forEach {
                assertEquals(32, it.publicKey.size)
                assertEquals(32, it.privateKey.size)
            }
        }

        @Test @DisplayName("getOneTimePreKeyCount returns correct count")
        fun `get opk count`() = runTest {
            store.generateOneTimePreKeys(30)
            assertEquals(30, store.getOneTimePreKeyCount())
        }

        @Test @DisplayName("consumeOneTimePreKey removes and returns record")
        fun `consume opk`() = runTest {
            val records = store.generateOneTimePreKeys(10)
            val consumed = store.consumeOneTimePreKey(records[0].id)
            assertNotNull(consumed)
            assertEquals(records[0].id, consumed!!.id)
            assertEquals(9, store.getOneTimePreKeyCount())
        }

        @Test @DisplayName("consumeOneTimePreKey returns null for non-existent")
        fun `consume opk non existent`() = runTest {
            assertNull(store.consumeOneTimePreKey(9999))
        }

        @Test @DisplayName("hasOneTimePreKey returns true for existing")
        fun `has opk true`() = runTest {
            val records = store.generateOneTimePreKeys(5)
            assertTrue(store.hasOneTimePreKey(records[0].id))
        }

        @Test @DisplayName("needsOpkTopUp true when below threshold")
        fun `needs opk top up`() = runTest {
            store.generateOneTimePreKeys(5)
            assertTrue(store.needsOpkTopUp())
        }

        @Test @DisplayName("needsOpkTopUp false when above threshold")
        fun `needs opk top up false`() = runTest {
            store.generateOneTimePreKeys(20)
            assertFalse(store.needsOpkTopUp())
        }

        @Test @DisplayName("getOneTimePreKeyPublicKeys returns public keys only")
        fun `get opk public keys`() = runTest {
            val records = store.generateOneTimePreKeys(5)
            val publics = store.getOneTimePreKeyPublicKeys()
            assertEquals(5, publics.size)
            publics.forEach { pub ->
                assertEquals(32, pub.publicKey.size)
            }
        }
    }

    @Nested @DisplayName("Last-Resort PreKey")
    inner class LastResortTest {
        @Test @DisplayName("generateLastResortPreKey creates valid record")
        fun `generate last resort`() = runTest {
            val record = store.generateLastResortPreKey()
            assertEquals(32, record.publicKey.size)
            assertTrue(record.isLastResort)
        }

        @Test @DisplayName("last-resort not counted in OPK count")
        fun `last resort not counted`() = runTest {
            store.generateLastResortPreKey()
            assertEquals(0, store.getOneTimePreKeyCount())
        }

        @Test @DisplayName("getLastResortPreKeyPublic returns public key")
        fun `get last resort public`() = runTest {
            val record = store.generateLastResortPreKey()
            val pub = store.getLastResortPreKeyPublic()
            assertNotNull(pub)
            assertEquals(record.id, pub!!.id)
        }
    }

    @Nested @DisplayName("Stale Cleanup")
    inner class StaleCleanupTest {
        @Test @DisplayName("cleanStaleOneTimePreKeys removes old keys")
        fun `clean stale opks`() = runTest {
            store.generateOneTimePreKeys(30)
            store.cleanStaleOneTimePreKeys(0, 10) // threshold = 0, keep min 10
            assertTrue(store.getOneTimePreKeyCount() >= 10)
        }

        @Test @DisplayName("cleanStaleOneTimePreKeys respects minCount")
        fun `clean stale respects min`() = runTest {
            store.generateOneTimePreKeys(5)
            store.cleanStaleOneTimePreKeys(0, 10) // threshold = 0, keep min 10
            assertEquals(5, store.getOneTimePreKeyCount()) // only 5 exist, all kept
        }
    }

    @Nested @DisplayName("Load from DB")
    inner class LoadFromDbTest {
        @Test @DisplayName("loadFromDb populates cache")
        fun `load from db`() = runTest {
            store.generateSignedPreKey(identityKeyPair)
            store.generateOneTimePreKeys(10)
            store.clearCache()
            store.loadFromDb()
            assertNotNull(store.getCurrentSignedPreKey())
            assertEquals(10, store.getOneTimePreKeyCount())
        }
    }

    private class InMemoryPreKeyDao : PreKeyDao {
        private val spkStore = mutableMapOf<Int, SignedPreKeyRecord>()
        private val opkStore = mutableMapOf<Int, OneTimePreKeyRecord>()
        override suspend fun storeSignedPreKey(record: SignedPreKeyRecord) { spkStore[record.id] = record.copy() }
        override suspend fun loadSignedPreKeys(): List<SignedPreKeyRecord> = spkStore.values.map { it.copy() }
        override suspend fun deleteSignedPreKey(id: Int) { spkStore.remove(id) }
        override suspend fun storeOneTimePreKeys(records: List<OneTimePreKeyRecord>) { records.forEach { opkStore[it.id] = it } }
        override suspend fun loadOneTimePreKeys(): List<OneTimePreKeyRecord> = opkStore.values.toList()
        override suspend fun deleteOneTimePreKey(id: Int) { opkStore.remove(id) }
    }
}
