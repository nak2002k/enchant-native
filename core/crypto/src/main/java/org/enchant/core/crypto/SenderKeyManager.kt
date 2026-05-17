package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.enchant.core.base.SecurePreferences

data class SenderKeyState(
    val seed: ByteArray,
    val chainKey: ByteArray,
    val messageNumber: Int = 0,
    val version: Int = 1
) {
    fun zero() {
        CryptoHelper.zeroBytes(seed)
        CryptoHelper.zeroBytes(chainKey)
    }
}

data class SenderKeyDistributionMessage(
    val groupId: String,
    val senderUserId: String,
    val chainKey: ByteArray,
    val messageNumber: Int
)

object SenderKeyManager {
    private val mutex = Mutex()
    private val senderKeyStore = mutableMapOf<String, SenderKeyState>()
    private val receiverKeyStore = mutableMapOf<String, SenderKeyState>()

    suspend fun getOrCreateSenderKey(groupId: String, senderUserId: String): SenderKeyState = mutex.withLock {
        val key = "$senderUserId:$groupId"
        senderKeyStore.getOrPut(key) {
            val seed = CryptoHelper.generateRandomKey(32)
            val chainKey = CryptoHelper.hkdfSha256(seed, ByteArray(32), "EnchantSenderKey".encodeToByteArray(), 32)
            SenderKeyState(seed = seed, chainKey = chainKey)
        }
    }

    suspend fun createDistributionMessage(groupId: String, senderUserId: String): SenderKeyDistributionMessage? = mutex.withLock {
        val key = "$senderUserId:$groupId"
        val state = senderKeyStore[key] ?: return null
        SenderKeyDistributionMessage(
            groupId = groupId,
            senderUserId = senderUserId,
            chainKey = state.chainKey,
            messageNumber = state.messageNumber
        )
    }

    suspend fun handleDistributionMessage(dm: SenderKeyDistributionMessage) = mutex.withLock {
        val key = "${dm.senderUserId}:${dm.groupId}"
        receiverKeyStore[key] = SenderKeyState(
            seed = ByteArray(32),
            chainKey = dm.chainKey,
            messageNumber = dm.messageNumber
        )
    }

    suspend fun encryptGroupMessage(groupId: String, senderUserId: String, plaintext: ByteArray): ByteArray? = withContext(Dispatchers.Default) {
        mutex.withLock {
            val key = "$senderUserId:$groupId"
            val state = senderKeyStore[key] ?: return@withLock null

            val msgKeyData = CryptoHelper.hkdfSha256(state.chainKey, ByteArray(32), "EnchantMsg".encodeToByteArray(), 80)
            val msgKey = msgKeyData.copyOfRange(0, 32)
            val nonce = msgKeyData.copyOfRange(32, 44)

            val ciphertext = CryptoHelper.encryptXChaCha20Poly1305(plaintext, msgKey)

            val nextChainKey = msgKeyData.copyOfRange(44, 76)
            CryptoHelper.zeroBytes(msgKeyData)

            senderKeyStore[key] = state.copy(
                chainKey = nextChainKey,
                messageNumber = state.messageNumber + 1
            )

            ByteArray(nonce.size + ciphertext.size).apply {
                nonce.copyInto(this, 0)
                ciphertext.copyInto(this, nonce.size)
            }
        }
    }

    suspend fun decryptGroupMessage(groupId: String, senderUserId: String, payload: ByteArray): ByteArray? = withContext(Dispatchers.Default) {
        mutex.withLock {
            val key = "$senderUserId:$groupId"
            val state = receiverKeyStore[key] ?: return@withLock null

            if (payload.size < 12) return@withLock null
            val nonce = payload.copyOfRange(0, 12)
            val ciphertext = payload.copyOfRange(12, payload.size)

            val msgKeyData = CryptoHelper.hkdfSha256(state.chainKey, ByteArray(32), "EnchantMsg".encodeToByteArray(), 80)
            val msgKey = msgKeyData.copyOfRange(0, 32)

            val plaintext = try {
                CryptoHelper.decryptXChaCha20Poly1305(ciphertext, msgKey)
            } catch (_: Exception) { return@withLock null }

            val nextChainKey = msgKeyData.copyOfRange(44, 76)
            CryptoHelper.zeroBytes(msgKeyData)

            receiverKeyStore[key] = state.copy(
                chainKey = nextChainKey,
                messageNumber = state.messageNumber + 1
            )

            plaintext
        }
    }

    suspend fun deleteGroupKeys(groupId: String) = mutex.withLock {
        senderKeyStore.keys.filter { it.endsWith(":$groupId") }.forEach {
            senderKeyStore.remove(it)?.zero()
        }
        receiverKeyStore.keys.filter { it.endsWith(":$groupId") }.forEach {
            receiverKeyStore.remove(it)?.zero()
        }
    }
}
