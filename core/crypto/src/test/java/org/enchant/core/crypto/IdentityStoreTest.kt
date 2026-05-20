package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("IdentityStore — Trust levels and safety numbers")
class IdentityStoreTest {

    private lateinit var store: IdentityStore
    private lateinit var mockDao: IdentityStore.IdentityDao

    @BeforeEach
    fun setUp() {
        store = IdentityStore()
        mockDao = InMemoryIdentityDao()
        store.setDao(mockDao)
    }

    @Nested @DisplayName("Save Identity")
    inner class SaveIdentityTest {
        @Test @DisplayName("first save returns NEW")
        fun `first save returns new`() = runTest {
            val key = ByteArray(32) { 1 }
            val change = store.saveIdentity("user1", key)
            assertEquals(IdentityStore.IdentityChange.NEW, change)
        }

        @Test @DisplayName("same key returns UNCHANGED")
        fun `same key unchanged`() = runTest {
            val key = ByteArray(32) { 1 }
            store.saveIdentity("user1", key)
            val change = store.saveIdentity("user1", key)
            assertEquals(IdentityStore.IdentityChange.UNCHANGED, change)
        }

        @Test @DisplayName("different key returns CHANGED")
        fun `different key changed`() = runTest {
            val key1 = ByteArray(32) { 1 }
            val key2 = ByteArray(32) { 2 }
            store.saveIdentity("user1", key1)
            val change = store.saveIdentity("user1", key2)
            assertEquals(IdentityStore.IdentityChange.CHANGED, change)
        }

        @Test @DisplayName("changed key sets UNVERIFIED trust level")
        fun `changed key unverified`() = runTest {
            val key1 = ByteArray(32) { 1 }
            val key2 = ByteArray(32) { 2 }
            store.saveIdentity("user1", key1)
            store.saveIdentity("user1", key2)
            val record = store.getRecord("user1")
            assertEquals(IdentityStore.TrustLevel.UNVERIFIED, record?.trustLevel)
        }
    }

    @Nested @DisplayName("Get Identity")
    inner class GetIdentityTest {
        @Test @DisplayName("get returns stored key")
        fun `get returns key`() = runTest {
            val key = ByteArray(32) { 1 }
            store.saveIdentity("user1", key)
            val loaded = store.getIdentity("user1")
            assertArrayEquals(key, loaded)
        }

        @Test @DisplayName("get non-existent returns null")
        fun `get non existent null`() = runTest {
            assertNull(store.getIdentity("nonexistent"))
        }
    }

    @Nested @DisplayName("Trust")
    inner class TrustTest {
        @Test @DisplayName("isTrustedForSending true for NEW identity")
        fun `trusted for sending new`() = runTest {
            val key = ByteArray(32) { 1 }
            store.saveIdentity("user1", key)
            assertTrue(store.isTrustedForSending("user1"))
        }

        @Test @DisplayName("isTrustedForSending false for UNVERIFIED")
        fun `trusted for sending unverified`() = runTest {
            val key1 = ByteArray(32) { 1 }
            val key2 = ByteArray(32) { 2 }
            store.saveIdentity("user1", key1)
            store.saveIdentity("user1", key2)
            assertFalse(store.isTrustedForSending("user1"))
        }

        @Test @DisplayName("verifyIdentity sets VERIFIED")
        fun `verify identity`() = runTest {
            val key1 = ByteArray(32) { 1 }
            val key2 = ByteArray(32) { 2 }
            store.saveIdentity("user1", key1)
            store.saveIdentity("user1", key2)
            store.verifyIdentity("user1")
            assertTrue(store.isTrustedForSending("user1"))
        }

        @Test @DisplayName("non-blocking approval can be set")
        fun `non blocking approval`() = runTest {
            val key1 = ByteArray(32) { 1 }
            val key2 = ByteArray(32) { 2 }
            store.saveIdentity("user1", key1)
            store.saveIdentity("user1", key2)
            assertFalse(store.isNonBlockingApproved("user1"))
            store.setNonBlockingApproval("user1", true)
            assertTrue(store.isNonBlockingApproved("user1"))
        }
    }

    @Nested @DisplayName("Safety Number")
    inner class SafetyNumberTest {
        @Test @DisplayName("computeSafetyNumber produces formatted string")
        fun `safety number formatted`() {
            val ourKey = ByteArray(32) { 1 }
            val theirKey = ByteArray(32) { 2 }
            val safetyNumber = store.computeSafetyNumber(ourKey, theirKey)
            assertTrue(safetyNumber.isNotEmpty())
            // Should be groups of 4 hex digits separated by spaces
            assertTrue(safetyNumber.all { it.isUpperCase() || it.isDigit() || it == ' ' })
        }

        @Test @DisplayName("same keys produce same safety number")
        fun `safety number deterministic`() {
            val ourKey = ByteArray(32) { 1 }
            val theirKey = ByteArray(32) { 2 }
            val sn1 = store.computeSafetyNumber(ourKey, theirKey)
            val sn2 = store.computeSafetyNumber(ourKey, theirKey)
            assertEquals(sn1, sn2)
        }

        @Test @DisplayName("different keys produce different safety number")
        fun `safety number different`() {
            val ourKey = ByteArray(32) { 1 }
            val theirKey1 = ByteArray(32) { 2 }
            val theirKey2 = ByteArray(32) { 3 }
            val sn1 = store.computeSafetyNumber(ourKey, theirKey1)
            val sn2 = store.computeSafetyNumber(ourKey, theirKey2)
            assertNotEquals(sn1, sn2)
        }
    }

    @Nested @DisplayName("Delete")
    inner class DeleteTest {
        @Test @DisplayName("delete removes identity")
        fun `delete removes`() = runTest {
            val key = ByteArray(32) { 1 }
            store.saveIdentity("user1", key)
            store.deleteIdentity("user1")
            assertNull(store.getIdentity("user1"))
        }

        @Test @DisplayName("delete non-existent is no-op")
        fun `delete non existent`() = runTest {
            store.deleteIdentity("nonexistent")
        }
    }

    @Nested @DisplayName("Identity Change Detection")
    inner class ChangeDetectionTest {
        @Test @DisplayName("hasIdentityChanged true for different key")
        fun `has changed true`() = runTest {
            val key1 = ByteArray(32) { 1 }
            val key2 = ByteArray(32) { 2 }
            store.saveIdentity("user1", key1)
            assertTrue(store.hasIdentityChanged("user1", key2))
        }

        @Test @DisplayName("hasIdentityChanged false for same key")
        fun `has changed false`() = runTest {
            val key = ByteArray(32) { 1 }
            store.saveIdentity("user1", key)
            assertFalse(store.hasIdentityChanged("user1", key))
        }

        @Test @DisplayName("hasIdentityChanged false for unknown user")
        fun `has changed unknown false`() = runTest {
            assertFalse(store.hasIdentityChanged("unknown", ByteArray(32)))
        }
    }

    private class InMemoryIdentityDao : IdentityStore.IdentityDao {
        private val store = mutableMapOf<String, IdentityStore.IdentityRecord>()
        override suspend fun save(record: IdentityStore.IdentityRecord) { store[record.userId] = record }
        override suspend fun load(userId: String): IdentityStore.IdentityRecord? = store[userId]
        override suspend fun delete(userId: String) { store.remove(userId) }
        override suspend fun loadAll(): List<IdentityStore.IdentityRecord> = store.values.toList()
    }
}
