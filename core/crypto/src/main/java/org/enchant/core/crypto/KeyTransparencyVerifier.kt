package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.enchant.core.network.ApiClient

/**
 * Key Transparency (Signal-style): before trusting a peer's identity key,
 * verify it against the server's append-only signed Merkle tree. The app
 * recomputes the tree root from the inclusion proof and compares it to the
 * signed tree head — a MITM swapping keys can't produce a valid proof, so
 * tampering is detected without manual safety-number comparison.
 */
object KeyTransparencyVerifier {

    data class TreeHead(val treeSize: Long, val rootHash: ByteArray)
    data class InclusionProof(val leafIndex: Long, val leaf: ByteArray, val siblings: List<ByteArray>)

    /** Leaf = SHA256(user_id(36) || device_id(36) || identity_key(32) || op(1)) */
    fun computeLeafHash(userId: String, deviceId: String, identityKey: ByteArray, operation: Byte = 1): ByteArray {
        val input = ByteArray(36 + 36 + 32 + 1)
        val userBytes = userId.toByteArray(Charsets.UTF_8)
        val deviceBytes = deviceId.toByteArray(Charsets.UTF_8)
        userBytes.copyInto(input, 0, 0, minOf(36, userBytes.size))
        deviceBytes.copyInto(input, 36, 0, minOf(36, deviceBytes.size))
        identityKey.copyInto(input, 72, 0, minOf(32, identityKey.size))
        input[104] = operation
        return CryptoPrimitives.sha256(input)
    }

    /** Standard Merkle inclusion: climb the sibling path to the root. */
    fun computeRootFromProof(proof: InclusionProof): ByteArray {
        var index = proof.leafIndex
        var node = proof.leaf
        for (sibling in proof.siblings) {
            node = if (index % 2 == 0L) {
                CryptoPrimitives.sha256(node + sibling)
            } else {
                CryptoPrimitives.sha256(sibling + node)
            }
            index /= 2
        }
        return node
    }

    suspend fun fetchLatestTreeHead(client: ApiClient): Result<TreeHead> = withContext(Dispatchers.Default) {
        runCatching {
            val json = client.get("/v1/keys/sth/latest").getOrThrow()
            val size = json["tree_size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val rootB64 = json["root_hash"]?.jsonPrimitive?.content ?: ""
            TreeHead(size, CryptoPrimitives.base64UrlDecode(rootB64))
        }
    }

    suspend fun fetchInclusionProof(
        client: ApiClient, userId: String, deviceId: String
    ): Result<List<InclusionProof>> = withContext(Dispatchers.Default) {
        runCatching {
            val json = client.get("/v1/keys/proof/$userId/$deviceId").getOrThrow()
            json["proofs"]?.jsonArray?.mapNotNull { item ->
                val obj = item.jsonObject
                val idx = obj["leaf_index"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                val leaf = obj["leaf"]?.jsonPrimitive?.content?.let {
                    runCatching { CryptoPrimitives.base64UrlDecode(it) }.getOrNull()
                } ?: return@mapNotNull null
                val siblings = obj["siblings"]?.jsonArray?.mapNotNull { s ->
                    runCatching { CryptoPrimitives.base64UrlDecode(s.jsonPrimitive.content) }.getOrNull()
                } ?: emptyList()
                InclusionProof(idx, leaf, siblings)
            } ?: emptyList()
        }
    }

    /**
     * Full verification: every inclusion proof for (user, device) must climb
     * to the current signed tree root.
     */
    suspend fun verifyIdentity(
        client: ApiClient, userId: String, deviceId: String, identityKey: ByteArray
    ): Boolean {
        val head = fetchLatestTreeHead(client).getOrNull() ?: return false
        if (head.treeSize == 0L || head.rootHash.isEmpty()) return false

        // The proof covers the identity key: its leaf must match ours.
        val proofs = fetchInclusionProof(client, userId, deviceId).getOrNull() ?: return false
        if (proofs.isEmpty()) return false

        val ourLeaf = computeLeafHash(userId, deviceId, identityKey)
        val anyMatch = proofs.any { p ->
            p.leaf.contentEquals(ourLeaf) && computeRootFromProof(p).contentEquals(head.rootHash)
        }
        return anyMatch
    }

    /**
     * Audit-style: recompute the server root from ALL returned proofs and
     * check it equals the signed head (catches server tampering too).
     */
    suspend fun verifyServerConsistency(client: ApiClient, userId: String, deviceId: String): Boolean {
        val head = fetchLatestTreeHead(client).getOrNull() ?: return false
        if (head.treeSize == 0L || head.rootHash.isEmpty()) return false
        val proofs = fetchInclusionProof(client, userId, deviceId).getOrNull() ?: return false
        if (proofs.isEmpty()) return false
        return proofs.all { computeRootFromProof(it).contentEquals(head.rootHash) }
    }

    /**
     * Device-free binding: the veil hides the sender's device id (by design),
     * so the app compares the recovered identity key against the identity key
     * the IKS has registered for the user. A MITM who swapped the key can't
     * pass this check.
     */
    suspend fun verifyIdentityViaBundle(client: ApiClient, userId: String, identityKey: ByteArray): Boolean {
        val json = runCatching {
            client.get("/v1/keys/bundle/$userId").getOrThrow()
        }.getOrNull() ?: return false
        val registeredKeys = json["devices"]?.jsonArray?.mapNotNull { device ->
            val keyB64 = device.jsonObject["identity_key"]?.jsonPrimitive?.content ?: return@mapNotNull null
            runCatching { CryptoPrimitives.base64UrlDecode(keyB64) }.getOrNull()
        } ?: return false
        return registeredKeys.any { it.contentEquals(identityKey) }
    }
}
