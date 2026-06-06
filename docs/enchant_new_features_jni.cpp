#include <jni.h>
#include <enchant/error.h>
#include <primitives/xeddsa.hpp>
#include <groups/mls_tree_kem.hpp>
#include <groups/storage_service.hpp>
#include <groups/groups_v2.hpp>
#include <groups/key_transparency.hpp>
#include <zk/zkgroup/client_zk_profile.hpp>
#include <zk/zkgroup/server_params.hpp>
#include <zk/zkgroup/group_secret_params.hpp>
#include <zk/zkgroup/profile_key_credential.hpp>
#include <cstring>
#include <vector>
#include <sodium.h>

namespace {

jint throw_enchant_exception(JNIEnv* env, const char* class_name, const char* msg, jint error_code) {
    jclass ex = env->FindClass(class_name);
    if (ex != nullptr) {
        env->ThrowNew(ex, msg);
    }
    return error_code;
}

template <typename T>
static T* handle_to_ptr(jlong handle) {
    if (handle == 0) return nullptr;
    return reinterpret_cast<T*>(static_cast<uintptr_t>(handle));
}

template <typename T>
static jlong ptr_to_handle(T* ptr) {
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(ptr));
}

} // anonymous namespace

extern "C" {

/* ═══════════════════════════════════════════════════════════════
   XEdDSA — Free-function bindings
   ═══════════════════════════════════════════════════════════════ */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeXEdDSA_signNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray message,
    jbyteArray x25519PrivateKey,
    jbyteArray signature
) {
    (void)clazz;

    jbyte* msg = env->GetByteArrayElements(message, nullptr);
    jbyte* key = env->GetByteArrayElements(x25519PrivateKey, nullptr);
    jbyte* sig = env->GetByteArrayElements(signature, nullptr);

    jsize msg_len = env->GetArrayLength(message);

    int rc = enchant::primitives::xeddsa_sign(
        reinterpret_cast<const uint8_t*>(msg),
        static_cast<size_t>(msg_len),
        reinterpret_cast<const uint8_t*>(key),
        reinterpret_cast<uint8_t*>(sig)
    );

    env->ReleaseByteArrayElements(message, msg, JNI_ABORT);
    env->ReleaseByteArrayElements(x25519PrivateKey, key, JNI_ABORT);
    env->ReleaseByteArrayElements(signature, sig, 0);

    if (rc != ENCHANT_SUCCESS) {
        throw_enchant_exception(env,
            "org/enchant/core/crypto/NativeXEdDSA$EnchantCryptoException",
            "XEdDSA sign failed", rc);
    }

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeXEdDSA_verifyNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray message,
    jbyteArray signature,
    jbyteArray x25519PublicKey
) {
    (void)clazz;

    jbyte* msg = env->GetByteArrayElements(message, nullptr);
    jbyte* sig = env->GetByteArrayElements(signature, nullptr);
    jbyte* pub = env->GetByteArrayElements(x25519PublicKey, nullptr);

    jsize msg_len = env->GetArrayLength(message);

    int rc = enchant::primitives::xeddsa_verify(
        reinterpret_cast<const uint8_t*>(msg),
        static_cast<size_t>(msg_len),
        reinterpret_cast<const uint8_t*>(sig),
        reinterpret_cast<const uint8_t*>(pub)
    );

    env->ReleaseByteArrayElements(message, msg, JNI_ABORT);
    env->ReleaseByteArrayElements(signature, sig, JNI_ABORT);
    env->ReleaseByteArrayElements(x25519PublicKey, pub, JNI_ABORT);

    if (rc != ENCHANT_SUCCESS) {
        throw_enchant_exception(env,
            "org/enchant/core/crypto/NativeXEdDSA$EnchantCryptoException",
            "XEdDSA verify failed", rc);
    }

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeXEdDSA_derivePublicKeyNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray x25519PrivateKey,
    jbyteArray xeddsaPublicKey
) {
    (void)clazz;

    jbyte* priv = env->GetByteArrayElements(x25519PrivateKey, nullptr);
    jbyte* pub = env->GetByteArrayElements(xeddsaPublicKey, nullptr);

    int rc = enchant::primitives::xeddsa_derive_public_key(
        reinterpret_cast<const uint8_t*>(priv),
        reinterpret_cast<uint8_t*>(pub)
    );

    env->ReleaseByteArrayElements(x25519PrivateKey, priv, JNI_ABORT);
    env->ReleaseByteArrayElements(xeddsaPublicKey, pub, 0);

    if (rc != ENCHANT_SUCCESS) {
        throw_enchant_exception(env,
            "org/enchant/core/crypto/NativeXEdDSA$EnchantCryptoException",
            "XEdDSA derive public key failed", rc);
    }

    return rc;
}

/* ═══════════════════════════════════════════════════════════════
   MLS TreeKEM — Opaque-handle bindings
   ═══════════════════════════════════════════════════════════════ */

JNIEXPORT jlong JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_createNative(
    JNIEnv* env,
    jclass clazz
) {
    (void)env;
    (void)clazz;

    auto* tree = new (std::nothrow) enchant::groups::MlsTreeKEM();
    if (tree == nullptr) {
        return 0;
    }
    return ptr_to_handle(tree);
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_destroyNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    (void)env;
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    delete tree;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_initializeNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jobjectArray leafSecrets
) {
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jsize count = env->GetArrayLength(leafSecrets);
    std::vector<std::array<uint8_t, 32>> secrets(count);

    for (jsize i = 0; i < count; i++) {
        auto* arr = static_cast<jbyteArray>(env->GetObjectArrayElement(leafSecrets, i));
        if (arr == nullptr) {
            return ENCHANT_ERROR_NULL_POINTER;
        }
        jsize len = env->GetArrayLength(arr);
        if (len != 32) {
            env->DeleteLocalRef(arr);
            return ENCHANT_ERROR_INVALID_KEY_SIZE;
        }
        jbyte* data = env->GetByteArrayElements(arr, nullptr);
        std::memcpy(secrets[i].data(), data, 32);
        env->ReleaseByteArrayElements(arr, data, JNI_ABORT);
        env->DeleteLocalRef(arr);
    }

    return tree->initialize(secrets);
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_addMemberNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray leafSecret,
    jbyteArray newIndex
) {
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* secret = env->GetByteArrayElements(leafSecret, nullptr);
    jbyte* idx_out = env->GetByteArrayElements(newIndex, nullptr);

    std::array<uint8_t, 32> leaf_secret;
    std::memcpy(leaf_secret.data(), secret, 32);

    std::array<uint8_t, 32> new_member_index;

    int rc = tree->add_member(leaf_secret, new_member_index);

    if (rc == ENCHANT_SUCCESS) {
        std::memcpy(idx_out, new_member_index.data(), 32);
    }

    env->ReleaseByteArrayElements(leafSecret, secret, JNI_ABORT);
    env->ReleaseByteArrayElements(newIndex, idx_out, 0);

    sodium_memzero(leaf_secret.data(), leaf_secret.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_removeMemberNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jint leafIndex
) {
    (void)env;
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    return tree->remove_member(static_cast<uint32_t>(leafIndex));
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_updateLeafKeyNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jint leafIndex,
    jbyteArray newSecret,
    jbyteArray directPathOut
) {
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* secret = env->GetByteArrayElements(newSecret, nullptr);

    std::array<uint8_t, 32> new_secret;
    std::memcpy(new_secret.data(), secret, 32);

    enchant::groups::MlsDirectPath path;
    int rc = tree->update_leaf_key(static_cast<uint32_t>(leafIndex), new_secret, path);

    if (rc == ENCHANT_SUCCESS) {
        jbyte* out = env->GetByteArrayElements(directPathOut, nullptr);
        size_t offset = 0;

        uint32_t node_count = static_cast<uint32_t>(path.nodes.size());
        std::memcpy(out + offset, &node_count, sizeof(uint32_t));
        offset += sizeof(uint32_t);

        for (const auto& node : path.nodes) {
            std::memcpy(out + offset, &node.node_index, sizeof(uint32_t));
            offset += sizeof(uint32_t);

            std::memcpy(out + offset, node.public_key.data(),
                        enchant::groups::MLS_TREE_KEM_NODE_SIZE);
            offset += enchant::groups::MLS_TREE_KEM_NODE_SIZE;

            uint32_t ps_count = static_cast<uint32_t>(node.path_secrets.size());
            std::memcpy(out + offset, &ps_count, sizeof(uint32_t));
            offset += sizeof(uint32_t);

            for (const auto& ps : node.path_secrets) {
                std::memcpy(out + offset, &ps.node_index, sizeof(uint32_t));
                offset += sizeof(uint32_t);
                std::memcpy(out + offset, ps.secret.data(),
                            enchant::groups::MLS_TREE_KEM_PATH_SECRET_SIZE);
                offset += enchant::groups::MLS_TREE_KEM_PATH_SECRET_SIZE;
            }
        }

        env->ReleaseByteArrayElements(directPathOut, out, 0);
    }

    env->ReleaseByteArrayElements(newSecret, secret, JNI_ABORT);
    sodium_memzero(new_secret.data(), new_secret.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_encryptPathNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray directPathIn,
    jint directPathLen,
    jint senderLeafIndex,
    jbyteArray groupSecret
) {
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* dp_in = env->GetByteArrayElements(directPathIn, nullptr);
    jbyte* gs_out = env->GetByteArrayElements(groupSecret, nullptr);

    enchant::groups::MlsDirectPath path;
    size_t offset = 0;

    uint32_t node_count = 0;
    std::memcpy(&node_count, dp_in + offset, sizeof(uint32_t));
    offset += sizeof(uint32_t);

    path.nodes.resize(node_count);
    for (uint32_t i = 0; i < node_count; i++) {
        std::memcpy(&path.nodes[i].node_index, dp_in + offset, sizeof(uint32_t));
        offset += sizeof(uint32_t);

        std::memcpy(path.nodes[i].public_key.data(), dp_in + offset,
                    enchant::groups::MLS_TREE_KEM_NODE_SIZE);
        offset += enchant::groups::MLS_TREE_KEM_NODE_SIZE;

        uint32_t ps_count = 0;
        std::memcpy(&ps_count, dp_in + offset, sizeof(uint32_t));
        offset += sizeof(uint32_t);

        path.nodes[i].path_secrets.resize(ps_count);
        for (uint32_t j = 0; j < ps_count; j++) {
            std::memcpy(&path.nodes[i].path_secrets[j].node_index, dp_in + offset,
                        sizeof(uint32_t));
            offset += sizeof(uint32_t);
            std::memcpy(path.nodes[i].path_secrets[j].secret.data(), dp_in + offset,
                        enchant::groups::MLS_TREE_KEM_PATH_SECRET_SIZE);
            offset += enchant::groups::MLS_TREE_KEM_PATH_SECRET_SIZE;
        }
    }

    std::array<uint8_t, enchant::groups::MLS_TREE_KEM_GROUP_SECRET_SIZE> group_secret;

    int rc = tree->encrypt_path(path, static_cast<uint32_t>(senderLeafIndex), group_secret);

    if (rc == ENCHANT_SUCCESS) {
        std::memcpy(gs_out, group_secret.data(),
                    enchant::groups::MLS_TREE_KEM_GROUP_SECRET_SIZE);
    }

    env->ReleaseByteArrayElements(directPathIn, dp_in, JNI_ABORT);
    env->ReleaseByteArrayElements(groupSecret, gs_out, 0);

    sodium_memzero(group_secret.data(), group_secret.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_decryptPathNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray directPathIn,
    jint directPathLen,
    jint receiverLeafIndex,
    jbyteArray groupSecret
) {
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* dp_in = env->GetByteArrayElements(directPathIn, nullptr);
    jbyte* gs_out = env->GetByteArrayElements(groupSecret, nullptr);

    enchant::groups::MlsDirectPath path;
    size_t offset = 0;

    uint32_t node_count = 0;
    std::memcpy(&node_count, dp_in + offset, sizeof(uint32_t));
    offset += sizeof(uint32_t);

    path.nodes.resize(node_count);
    for (uint32_t i = 0; i < node_count; i++) {
        std::memcpy(&path.nodes[i].node_index, dp_in + offset, sizeof(uint32_t));
        offset += sizeof(uint32_t);

        std::memcpy(path.nodes[i].public_key.data(), dp_in + offset,
                    enchant::groups::MLS_TREE_KEM_NODE_SIZE);
        offset += enchant::groups::MLS_TREE_KEM_NODE_SIZE;

        uint32_t ps_count = 0;
        std::memcpy(&ps_count, dp_in + offset, sizeof(uint32_t));
        offset += sizeof(uint32_t);

        path.nodes[i].path_secrets.resize(ps_count);
        for (uint32_t j = 0; j < ps_count; j++) {
            std::memcpy(&path.nodes[i].path_secrets[j].node_index, dp_in + offset,
                        sizeof(uint32_t));
            offset += sizeof(uint32_t);
            std::memcpy(path.nodes[i].path_secrets[j].secret.data(), dp_in + offset,
                        enchant::groups::MLS_TREE_KEM_PATH_SECRET_SIZE);
            offset += enchant::groups::MLS_TREE_KEM_PATH_SECRET_SIZE;
        }
    }

    std::array<uint8_t, enchant::groups::MLS_TREE_KEM_GROUP_SECRET_SIZE> group_secret;

    int rc = tree->decrypt_path(path, static_cast<uint32_t>(receiverLeafIndex), group_secret);

    if (rc == ENCHANT_SUCCESS) {
        std::memcpy(gs_out, group_secret.data(),
                    enchant::groups::MLS_TREE_KEM_GROUP_SECRET_SIZE);
    }

    env->ReleaseByteArrayElements(directPathIn, dp_in, JNI_ABORT);
    env->ReleaseByteArrayElements(groupSecret, gs_out, 0);

    sodium_memzero(group_secret.data(), group_secret.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_computeTreeHashNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray rootHash
) {
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* out = env->GetByteArrayElements(rootHash, nullptr);

    std::array<uint8_t, enchant::groups::MLS_TREE_KEM_HMAC_SIZE> hash;
    int rc = tree->compute_tree_hash(hash);

    if (rc == ENCHANT_SUCCESS) {
        std::memcpy(out, hash.data(), enchant::groups::MLS_TREE_KEM_HMAC_SIZE);
    }

    env->ReleaseByteArrayElements(rootHash, out, 0);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_getNodePublicKeyNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jint nodeIndex,
    jbyteArray publicKey
) {
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* pub = env->GetByteArrayElements(publicKey, nullptr);

    std::array<uint8_t, enchant::groups::MLS_TREE_KEM_NODE_SIZE> pk;
    int rc = tree->get_node_public_key(static_cast<uint32_t>(nodeIndex), pk);

    if (rc == ENCHANT_SUCCESS) {
        std::memcpy(pub, pk.data(), enchant::groups::MLS_TREE_KEM_NODE_SIZE);
    }

    env->ReleaseByteArrayElements(publicKey, pub, 0);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_setNodePublicKeyNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jint nodeIndex,
    jbyteArray publicKey
) {
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* pub = env->GetByteArrayElements(publicKey, nullptr);

    std::array<uint8_t, enchant::groups::MLS_TREE_KEM_NODE_SIZE> pk;
    std::memcpy(pk.data(), pub, enchant::groups::MLS_TREE_KEM_NODE_SIZE);

    int rc = tree->set_node_public_key(static_cast<uint32_t>(nodeIndex), pk);

    env->ReleaseByteArrayElements(publicKey, pub, JNI_ABORT);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_setNodePrivateKeyNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jint nodeIndex,
    jbyteArray privateKey
) {
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* priv = env->GetByteArrayElements(privateKey, nullptr);

    std::array<uint8_t, 32> sk;
    std::memcpy(sk.data(), priv, 32);

    int rc = tree->set_node_private_key(static_cast<uint32_t>(nodeIndex), sk);

    env->ReleaseByteArrayElements(privateKey, priv, JNI_ABORT);
    sodium_memzero(sk.data(), sk.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_getRootPublicKeyNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray publicKey
) {
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* pub = env->GetByteArrayElements(publicKey, nullptr);

    std::array<uint8_t, enchant::groups::MLS_TREE_KEM_NODE_SIZE> pk;
    int rc = tree->get_root_public_key(pk);

    if (rc == ENCHANT_SUCCESS) {
        std::memcpy(pub, pk.data(), enchant::groups::MLS_TREE_KEM_NODE_SIZE);
    }

    env->ReleaseByteArrayElements(publicKey, pub, 0);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_leafCountNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    (void)env;
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return 0;
    }
    return static_cast<jint>(tree->leaf_count());
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeMlsTreeKEM_nodeCountNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    (void)env;
    (void)clazz;

    auto* tree = handle_to_ptr<enchant::groups::MlsTreeKEM>(handle);
    if (tree == nullptr) {
        return 0;
    }
    return static_cast<jint>(tree->node_count());
}

/* ═══════════════════════════════════════════════════════════════
   StorageService — Opaque-handle bindings
   ═══════════════════════════════════════════════════════════════ */

JNIEXPORT jlong JNICALL
Java_org_enchant_core_crypto_NativeStorageService_createNative(
    JNIEnv* env,
    jclass clazz
) {
    (void)env;
    (void)clazz;

    auto* svc = new (std::nothrow) enchant::groups::StorageService();
    if (svc == nullptr) {
        return 0;
    }
    return ptr_to_handle(svc);
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_NativeStorageService_destroyNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    (void)env;
    (void)clazz;

    auto* svc = handle_to_ptr<enchant::groups::StorageService>(handle);
    delete svc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeStorageService_initNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray masterKey
) {
    (void)clazz;

    auto* svc = handle_to_ptr<enchant::groups::StorageService>(handle);
    if (svc == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* key = env->GetByteArrayElements(masterKey, nullptr);
    jsize key_len = env->GetArrayLength(masterKey);

    int rc = svc->initialize(
        reinterpret_cast<const uint8_t*>(key),
        static_cast<size_t>(key_len)
    );

    env->ReleaseByteArrayElements(masterKey, key, JNI_ABORT);

    if (rc != ENCHANT_SUCCESS) {
        throw_enchant_exception(env,
            "org/enchant/core/crypto/NativeStorageService$EnchantCryptoException",
            "StorageService initialize failed", rc);
    }

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeStorageService_encryptItemNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray plaintext,
    jbyteArray itemId,
    jbyteArray envelopeVersionOut,
    jbyteArray envelopeNonceOut,
    jbyteArray envelopeCiphertextOut
) {
    (void)clazz;

    auto* svc = handle_to_ptr<enchant::groups::StorageService>(handle);
    if (svc == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* pt = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* id = env->GetByteArrayElements(itemId, nullptr);

    jsize pt_len = env->GetArrayLength(plaintext);
    jsize id_len = env->GetArrayLength(itemId);

    enchant::groups::StorageEnvelope envelope;

    int rc = svc->encrypt_item(
        reinterpret_cast<const uint8_t*>(pt),
        static_cast<size_t>(pt_len),
        reinterpret_cast<const uint8_t*>(id),
        static_cast<size_t>(id_len),
        envelope
    );

    if (rc == ENCHANT_SUCCESS) {
        jbyte* ver = env->GetByteArrayElements(envelopeVersionOut, nullptr);
        jbyte* nonce = env->GetByteArrayElements(envelopeNonceOut, nullptr);
        jbyte* ct = env->GetByteArrayElements(envelopeCiphertextOut, nullptr);

        std::memcpy(ver, &envelope.version, sizeof(uint32_t));
        std::memcpy(nonce, envelope.nonce.data(), enchant::groups::STORAGE_ENVELOPE_NONCE_SIZE);
        std::memcpy(ct, envelope.ciphertext.data(), envelope.ciphertext.size());

        env->ReleaseByteArrayElements(envelopeVersionOut, ver, 0);
        env->ReleaseByteArrayElements(envelopeNonceOut, nonce, 0);
        env->ReleaseByteArrayElements(envelopeCiphertextOut, ct, 0);

        sodium_memzero(envelope.nonce.data(), envelope.nonce.size());
        sodium_memzero(envelope.ciphertext.data(), envelope.ciphertext.size());
    }

    env->ReleaseByteArrayElements(plaintext, pt, JNI_ABORT);
    env->ReleaseByteArrayElements(itemId, id, JNI_ABORT);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeStorageService_decryptItemNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray envelopeVersionIn,
    jbyteArray envelopeNonceIn,
    jbyteArray envelopeCiphertextIn,
    jbyteArray itemId,
    jbyteArray plaintextOut
) {
    (void)clazz;

    auto* svc = handle_to_ptr<enchant::groups::StorageService>(handle);
    if (svc == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* ver = env->GetByteArrayElements(envelopeVersionIn, nullptr);
    jbyte* nonce = env->GetByteArrayElements(envelopeNonceIn, nullptr);
    jbyte* ct = env->GetByteArrayElements(envelopeCiphertextIn, nullptr);
    jbyte* id = env->GetByteArrayElements(itemId, nullptr);

    jsize ct_len = env->GetArrayLength(envelopeCiphertextIn);
    jsize id_len = env->GetArrayLength(itemId);

    enchant::groups::StorageEnvelope envelope;
    std::memcpy(&envelope.version, ver, sizeof(uint32_t));
    std::memcpy(envelope.nonce.data(), nonce, enchant::groups::STORAGE_ENVELOPE_NONCE_SIZE);
    envelope.ciphertext.assign(
        reinterpret_cast<const uint8_t*>(ct),
        reinterpret_cast<const uint8_t*>(ct) + ct_len
    );

    std::vector<uint8_t> plaintext;

    int rc = svc->decrypt_item(
        envelope,
        reinterpret_cast<const uint8_t*>(id),
        static_cast<size_t>(id_len),
        plaintext
    );

    if (rc == ENCHANT_SUCCESS) {
        jbyte* pt_out = env->GetByteArrayElements(plaintextOut, nullptr);
        jsize pt_out_len = env->GetArrayLength(plaintextOut);

        size_t copy_len = std::min(plaintext.size(), static_cast<size_t>(pt_out_len));
        std::memcpy(pt_out, plaintext.data(), copy_len);

        env->ReleaseByteArrayElements(plaintextOut, pt_out, 0);

        sodium_memzero(plaintext.data(), plaintext.size());
    }

    env->ReleaseByteArrayElements(envelopeVersionIn, ver, JNI_ABORT);
    env->ReleaseByteArrayElements(envelopeNonceIn, nonce, JNI_ABORT);
    env->ReleaseByteArrayElements(envelopeCiphertextIn, ct, JNI_ABORT);
    env->ReleaseByteArrayElements(itemId, id, JNI_ABORT);

    sodium_memzero(envelope.ciphertext.data(), envelope.ciphertext.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeStorageService_rotateMasterKeyNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray newMasterKey
) {
    (void)clazz;

    auto* svc = handle_to_ptr<enchant::groups::StorageService>(handle);
    if (svc == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* key = env->GetByteArrayElements(newMasterKey, nullptr);
    jsize key_len = env->GetArrayLength(newMasterKey);

    int rc = svc->rotate_master_key(
        reinterpret_cast<const uint8_t*>(key),
        static_cast<size_t>(key_len)
    );

    env->ReleaseByteArrayElements(newMasterKey, key, JNI_ABORT);

    if (rc != ENCHANT_SUCCESS) {
        throw_enchant_exception(env,
            "org/enchant/core/crypto/NativeStorageService$EnchantCryptoException",
            "StorageService rotate master key failed", rc);
    }

    return rc;
}

JNIEXPORT jboolean JNICALL
Java_org_enchant_core_crypto_NativeStorageService_isInitializedNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    (void)env;
    (void)clazz;

    auto* svc = handle_to_ptr<enchant::groups::StorageService>(handle);
    if (svc == nullptr) {
        return JNI_FALSE;
    }
    return svc->is_initialized() ? JNI_TRUE : JNI_FALSE;
}

/* ═══════════════════════════════════════════════════════════════
   GroupsV2Manager — Opaque-handle bindings
   ═══════════════════════════════════════════════════════════════ */

JNIEXPORT jlong JNICALL
Java_org_enchant_core_crypto_NativeGroupsV2_createNative(
    JNIEnv* env,
    jclass clazz
) {
    (void)env;
    (void)clazz;

    auto* mgr = new (std::nothrow) enchant::groups::GroupsV2Manager();
    if (mgr == nullptr) {
        return 0;
    }
    return ptr_to_handle(mgr);
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_NativeGroupsV2_destroyNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    (void)env;
    (void)clazz;

    auto* mgr = handle_to_ptr<enchant::groups::GroupsV2Manager>(handle);
    delete mgr;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeGroupsV2_createGroupNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray creatorId,
    jbyteArray creatorSecret,
    jstring title,
    jbyteArray groupIdOut,
    jbyteArray epochSecretOut
) {
    (void)clazz;

    auto* mgr = handle_to_ptr<enchant::groups::GroupsV2Manager>(handle);
    if (mgr == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* cid = env->GetByteArrayElements(creatorId, nullptr);
    jbyte* csec = env->GetByteArrayElements(creatorSecret, nullptr);
    const char* title_str = env->GetStringUTFChars(title, nullptr);

    std::array<uint8_t, enchant::groups::GROUPS_V2_MEMBER_ID_SIZE> creator_id;
    std::array<uint8_t, 32> creator_secret;
    std::memcpy(creator_id.data(), cid, enchant::groups::GROUPS_V2_MEMBER_ID_SIZE);
    std::memcpy(creator_secret.data(), csec, 32);

    enchant::groups::GroupState state;

    int rc = mgr->create_group(creator_id, creator_secret, title_str, state);

    if (rc == ENCHANT_SUCCESS) {
        jbyte* gid = env->GetByteArrayElements(groupIdOut, nullptr);
        jbyte* epoch = env->GetByteArrayElements(epochSecretOut, nullptr);

        std::memcpy(gid, state.group_id.data(), enchant::groups::GROUPS_V2_GROUP_ID_SIZE);
        std::memcpy(epoch, state.epoch_secret.data(), enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);

        env->ReleaseByteArrayElements(groupIdOut, gid, 0);
        env->ReleaseByteArrayElements(epochSecretOut, epoch, 0);

        sodium_memzero(state.epoch_secret.data(), state.epoch_secret.size());
    }

    env->ReleaseByteArrayElements(creatorId, cid, JNI_ABORT);
    env->ReleaseByteArrayElements(creatorSecret, csec, JNI_ABORT);
    env->ReleaseStringUTFChars(title, title_str);

    sodium_memzero(creator_secret.data(), creator_secret.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeGroupsV2_addMemberNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray groupIdIn,
    jbyteArray epochSecretIn,
    jbyteArray newMemberId,
    jbyteArray newMemberSecret,
    jbyteArray commitEpochOut
) {
    (void)clazz;

    auto* mgr = handle_to_ptr<enchant::groups::GroupsV2Manager>(handle);
    if (mgr == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* gid = env->GetByteArrayElements(groupIdIn, nullptr);
    jbyte* epoch_in = env->GetByteArrayElements(epochSecretIn, nullptr);
    jbyte* mid = env->GetByteArrayElements(newMemberId, nullptr);
    jbyte* msecret = env->GetByteArrayElements(newMemberSecret, nullptr);

    enchant::groups::GroupState state;
    std::memcpy(state.group_id.data(), gid, enchant::groups::GROUPS_V2_GROUP_ID_SIZE);
    std::memcpy(state.epoch_secret.data(), epoch_in, enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);

    std::array<uint8_t, enchant::groups::GROUPS_V2_MEMBER_ID_SIZE> new_id;
    std::array<uint8_t, 32> new_secret;
    std::memcpy(new_id.data(), mid, enchant::groups::GROUPS_V2_MEMBER_ID_SIZE);
    std::memcpy(new_secret.data(), msecret, 32);

    enchant::groups::GroupCommit commit;

    int rc = mgr->add_member(state, new_id, new_secret, commit);

    if (rc == ENCHANT_SUCCESS) {
        jbyte* ce = env->GetByteArrayElements(commitEpochOut, nullptr);
        std::memcpy(ce, commit.epoch_secret.data(), enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);
        env->ReleaseByteArrayElements(commitEpochOut, ce, 0);

        sodium_memzero(commit.epoch_secret.data(), commit.epoch_secret.size());
    }

    env->ReleaseByteArrayElements(groupIdIn, gid, JNI_ABORT);
    env->ReleaseByteArrayElements(epochSecretIn, epoch_in, JNI_ABORT);
    env->ReleaseByteArrayElements(newMemberId, mid, JNI_ABORT);
    env->ReleaseByteArrayElements(newMemberSecret, msecret, JNI_ABORT);

    sodium_memzero(state.epoch_secret.data(), state.epoch_secret.size());
    sodium_memzero(new_secret.data(), new_secret.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeGroupsV2_removeMemberNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray groupIdIn,
    jbyteArray epochSecretIn,
    jbyteArray targetMemberId,
    jbyteArray commitEpochOut
) {
    (void)clazz;

    auto* mgr = handle_to_ptr<enchant::groups::GroupsV2Manager>(handle);
    if (mgr == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* gid = env->GetByteArrayElements(groupIdIn, nullptr);
    jbyte* epoch_in = env->GetByteArrayElements(epochSecretIn, nullptr);
    jbyte* tid = env->GetByteArrayElements(targetMemberId, nullptr);

    enchant::groups::GroupState state;
    std::memcpy(state.group_id.data(), gid, enchant::groups::GROUPS_V2_GROUP_ID_SIZE);
    std::memcpy(state.epoch_secret.data(), epoch_in, enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);

    std::array<uint8_t, enchant::groups::GROUPS_V2_MEMBER_ID_SIZE> target_id;
    std::memcpy(target_id.data(), tid, enchant::groups::GROUPS_V2_MEMBER_ID_SIZE);

    enchant::groups::GroupCommit commit;

    int rc = mgr->remove_member(state, target_id, commit);

    if (rc == ENCHANT_SUCCESS) {
        jbyte* ce = env->GetByteArrayElements(commitEpochOut, nullptr);
        std::memcpy(ce, commit.epoch_secret.data(), enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);
        env->ReleaseByteArrayElements(commitEpochOut, ce, 0);

        sodium_memzero(commit.epoch_secret.data(), commit.epoch_secret.size());
    }

    env->ReleaseByteArrayElements(groupIdIn, gid, JNI_ABORT);
    env->ReleaseByteArrayElements(epochSecretIn, epoch_in, JNI_ABORT);
    env->ReleaseByteArrayElements(targetMemberId, tid, JNI_ABORT);

    sodium_memzero(state.epoch_secret.data(), state.epoch_secret.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeGroupsV2_applyCommitNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray groupIdIn,
    jbyteArray epochSecretIn,
    jbyteArray commitEpochIn,
    jbyteArray epochSecretOut
) {
    (void)clazz;

    auto* mgr = handle_to_ptr<enchant::groups::GroupsV2Manager>(handle);
    if (mgr == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* gid = env->GetByteArrayElements(groupIdIn, nullptr);
    jbyte* epoch_in = env->GetByteArrayElements(epochSecretIn, nullptr);
    jbyte* commit_ep = env->GetByteArrayElements(commitEpochIn, nullptr);

    enchant::groups::GroupState state;
    std::memcpy(state.group_id.data(), gid, enchant::groups::GROUPS_V2_GROUP_ID_SIZE);
    std::memcpy(state.epoch_secret.data(), epoch_in, enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);

    enchant::groups::GroupCommit commit;
    std::memcpy(commit.epoch_secret.data(), commit_ep, enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);
    commit.new_epoch = state.epoch + 1;

    int rc = mgr->apply_commit(state, commit);

    if (rc == ENCHANT_SUCCESS) {
        jbyte* eos = env->GetByteArrayElements(epochSecretOut, nullptr);
        std::memcpy(eos, state.epoch_secret.data(), enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);
        env->ReleaseByteArrayElements(epochSecretOut, eos, 0);

        sodium_memzero(state.epoch_secret.data(), state.epoch_secret.size());
    }

    env->ReleaseByteArrayElements(groupIdIn, gid, JNI_ABORT);
    env->ReleaseByteArrayElements(epochSecretIn, epoch_in, JNI_ABORT);
    env->ReleaseByteArrayElements(commitEpochIn, commit_ep, JNI_ABORT);

    sodium_memzero(commit.epoch_secret.data(), commit.epoch_secret.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeGroupsV2_serializeGroupStateNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray groupIdIn,
    jbyteArray epochSecretIn,
    jbyteArray output
) {
    (void)clazz;

    auto* mgr = handle_to_ptr<enchant::groups::GroupsV2Manager>(handle);
    if (mgr == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* gid = env->GetByteArrayElements(groupIdIn, nullptr);
    jbyte* epoch_in = env->GetByteArrayElements(epochSecretIn, nullptr);

    enchant::groups::GroupState state;
    std::memcpy(state.group_id.data(), gid, enchant::groups::GROUPS_V2_GROUP_ID_SIZE);
    std::memcpy(state.epoch_secret.data(), epoch_in, enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);

    std::vector<uint8_t> serialized;

    int rc = mgr->serialize_group_state(state, serialized);

    if (rc == ENCHANT_SUCCESS) {
        jbyte* out = env->GetByteArrayElements(output, nullptr);
        jsize out_len = env->GetArrayLength(output);
        size_t copy_len = std::min(serialized.size(), static_cast<size_t>(out_len));
        std::memcpy(out, serialized.data(), copy_len);
        env->ReleaseByteArrayElements(output, out, 0);

        sodium_memzero(serialized.data(), serialized.size());
    }

    env->ReleaseByteArrayElements(groupIdIn, gid, JNI_ABORT);
    env->ReleaseByteArrayElements(epochSecretIn, epoch_in, JNI_ABORT);

    sodium_memzero(state.epoch_secret.data(), state.epoch_secret.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeGroupsV2_deserializeGroupStateNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray data,
    jint dataLen,
    jbyteArray groupIdOut,
    jbyteArray epochSecretOut
) {
    (void)clazz;

    auto* mgr = handle_to_ptr<enchant::groups::GroupsV2Manager>(handle);
    if (mgr == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* buf = env->GetByteArrayElements(data, nullptr);

    enchant::groups::GroupState state;

    int rc = mgr->deserialize_group_state(
        state,
        reinterpret_cast<const uint8_t*>(buf),
        static_cast<size_t>(dataLen)
    );

    if (rc == ENCHANT_SUCCESS) {
        jbyte* gid = env->GetByteArrayElements(groupIdOut, nullptr);
        jbyte* epoch = env->GetByteArrayElements(epochSecretOut, nullptr);

        std::memcpy(gid, state.group_id.data(), enchant::groups::GROUPS_V2_GROUP_ID_SIZE);
        std::memcpy(epoch, state.epoch_secret.data(), enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);

        env->ReleaseByteArrayElements(groupIdOut, gid, 0);
        env->ReleaseByteArrayElements(epochSecretOut, epoch, 0);

        sodium_memzero(state.epoch_secret.data(), state.epoch_secret.size());
    }

    env->ReleaseByteArrayElements(data, buf, JNI_ABORT);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeGroupsV2_getMemberCountNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray groupIdIn,
    jbyteArray epochSecretIn
) {
    (void)clazz;

    auto* mgr = handle_to_ptr<enchant::groups::GroupsV2Manager>(handle);
    if (mgr == nullptr) {
        return 0;
    }

    jbyte* gid = env->GetByteArrayElements(groupIdIn, nullptr);
    jbyte* epoch_in = env->GetByteArrayElements(epochSecretIn, nullptr);

    enchant::groups::GroupState state;
    std::memcpy(state.group_id.data(), gid, enchant::groups::GROUPS_V2_GROUP_ID_SIZE);
    std::memcpy(state.epoch_secret.data(), epoch_in, enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);

    int count = mgr->get_member_count(state);

    env->ReleaseByteArrayElements(groupIdIn, gid, JNI_ABORT);
    env->ReleaseByteArrayElements(epochSecretIn, epoch_in, JNI_ABORT);

    sodium_memzero(state.epoch_secret.data(), state.epoch_secret.size());

    return count;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeGroupsV2_updateMemberKeyNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray groupIdIn,
    jbyteArray epochSecretIn,
    jbyteArray memberId,
    jbyteArray newSecret,
    jbyteArray commitEpochOut
) {
    (void)clazz;

    auto* mgr = handle_to_ptr<enchant::groups::GroupsV2Manager>(handle);
    if (mgr == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* gid = env->GetByteArrayElements(groupIdIn, nullptr);
    jbyte* epoch_in = env->GetByteArrayElements(epochSecretIn, nullptr);
    jbyte* mid = env->GetByteArrayElements(memberId, nullptr);
    jbyte* sec = env->GetByteArrayElements(newSecret, nullptr);

    enchant::groups::GroupState state;
    std::memcpy(state.group_id.data(), gid, enchant::groups::GROUPS_V2_GROUP_ID_SIZE);
    std::memcpy(state.epoch_secret.data(), epoch_in, enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);

    std::array<uint8_t, enchant::groups::GROUPS_V2_MEMBER_ID_SIZE> member_id;
    std::array<uint8_t, 32> new_secret;
    std::memcpy(member_id.data(), mid, enchant::groups::GROUPS_V2_MEMBER_ID_SIZE);
    std::memcpy(new_secret.data(), sec, 32);

    enchant::groups::GroupCommit commit;

    int rc = mgr->update_member_key(state, member_id, new_secret, commit);

    if (rc == ENCHANT_SUCCESS) {
        jbyte* ce = env->GetByteArrayElements(commitEpochOut, nullptr);
        std::memcpy(ce, commit.epoch_secret.data(), enchant::groups::GROUPS_V2_EPOCH_SECRET_SIZE);
        env->ReleaseByteArrayElements(commitEpochOut, ce, 0);

        sodium_memzero(commit.epoch_secret.data(), commit.epoch_secret.size());
    }

    env->ReleaseByteArrayElements(groupIdIn, gid, JNI_ABORT);
    env->ReleaseByteArrayElements(epochSecretIn, epoch_in, JNI_ABORT);
    env->ReleaseByteArrayElements(memberId, mid, JNI_ABORT);
    env->ReleaseByteArrayElements(newSecret, sec, JNI_ABORT);

    sodium_memzero(state.epoch_secret.data(), state.epoch_secret.size());
    sodium_memzero(new_secret.data(), new_secret.size());

    return rc;
}

/* ═══════════════════════════════════════════════════════════════
   ClientZkProfileOperations — Opaque-handle bindings
   ═══════════════════════════════════════════════════════════════ */

JNIEXPORT jlong JNICALL
Java_org_enchant_core_crypto_NativeClientZkProfile_createNative(
    JNIEnv* env,
    jclass clazz
) {
    (void)env;
    (void)clazz;

    auto* ops = new (std::nothrow) enchant::zk::zkgroup::ClientZkProfileOperations();
    if (ops == nullptr) {
        return 0;
    }
    return ptr_to_handle(ops);
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_NativeClientZkProfile_destroyNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    (void)env;
    (void)clazz;

    auto* ops = handle_to_ptr<enchant::zk::zkgroup::ClientZkProfileOperations>(handle);
    delete ops;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeClientZkProfile_initNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray serverPublicParams,
    jbyteArray groupSecretParams
) {
    (void)clazz;

    auto* ops = handle_to_ptr<enchant::zk::zkgroup::ClientZkProfileOperations>(handle);
    if (ops == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* spp = env->GetByteArrayElements(serverPublicParams, nullptr);
    jsize spp_len = env->GetArrayLength(serverPublicParams);
    jbyte* gsp = env->GetByteArrayElements(groupSecretParams, nullptr);
    jsize gsp_len = env->GetArrayLength(groupSecretParams);

    enchant::zk::zkgroup::ServerPublicParams server_params;
    enchant::zk::zkgroup::GroupSecretParams group_params;

    if (static_cast<size_t>(spp_len) < sizeof(enchant::zk::zkgroup::ServerPublicParams) ||
        static_cast<size_t>(gsp_len) < sizeof(enchant::zk::zkgroup::GroupSecretParams)) {
        env->ReleaseByteArrayElements(serverPublicParams, spp, JNI_ABORT);
        env->ReleaseByteArrayElements(groupSecretParams, gsp, JNI_ABORT);
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    std::memcpy(&server_params, spp, sizeof(enchant::zk::zkgroup::ServerPublicParams));
    std::memcpy(&group_params, gsp, sizeof(enchant::zk::zkgroup::GroupSecretParams));

    int rc = ops->initialize(server_params, group_params);

    env->ReleaseByteArrayElements(serverPublicParams, spp, JNI_ABORT);
    env->ReleaseByteArrayElements(groupSecretParams, gsp, JNI_ABORT);

    if (rc != ENCHANT_SUCCESS) {
        throw_enchant_exception(env,
            "org/enchant/core/crypto/NativeClientZkProfile$EnchantCryptoException",
            "ClientZkProfile initialize failed", rc);
    }

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeClientZkProfile_encryptProfileForStorageNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray profile,
    jbyteArray profileKey,
    jbyteArray encryptedDataOut,
    jintArray versionOut
) {
    (void)clazz;

    auto* ops = handle_to_ptr<enchant::zk::zkgroup::ClientZkProfileOperations>(handle);
    if (ops == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* prof = env->GetByteArrayElements(profile, nullptr);
    jbyte* pk = env->GetByteArrayElements(profileKey, nullptr);

    jsize prof_len = env->GetArrayLength(profile);

    std::array<uint8_t, enchant::zk::zkgroup::CLIENT_ZK_PROFILE_KEY_SIZE> profile_key;
    std::memcpy(profile_key.data(), pk, enchant::zk::zkgroup::CLIENT_ZK_PROFILE_KEY_SIZE);

    enchant::zk::zkgroup::EncryptedProfile enc;

    int rc = ops->encrypt_profile_for_storage(
        reinterpret_cast<const uint8_t*>(prof),
        static_cast<size_t>(prof_len),
        profile_key,
        enc
    );

    if (rc == ENCHANT_SUCCESS) {
        jbyte* out = env->GetByteArrayElements(encryptedDataOut, nullptr);
        jsize out_len = env->GetArrayLength(encryptedDataOut);

        size_t copy_len = std::min(enc.encrypted_data.size(), static_cast<size_t>(out_len));
        std::memcpy(out, enc.encrypted_data.data(), copy_len);

        jint* ver = env->GetIntArrayElements(versionOut, nullptr);
        *ver = static_cast<jint>(enc.version);

        env->ReleaseByteArrayElements(encryptedDataOut, out, 0);
        env->ReleaseIntArrayElements(versionOut, ver, 0);

        sodium_memzero(enc.encrypted_data.data(), enc.encrypted_data.size());
    }

    env->ReleaseByteArrayElements(profile, prof, JNI_ABORT);
    env->ReleaseByteArrayElements(profileKey, pk, JNI_ABORT);

    sodium_memzero(profile_key.data(), profile_key.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeClientZkProfile_decryptProfileNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray encryptedDataIn,
    jint versionIn,
    jbyteArray profileKey,
    jbyteArray plaintextOut
) {
    (void)clazz;

    auto* ops = handle_to_ptr<enchant::zk::zkgroup::ClientZkProfileOperations>(handle);
    if (ops == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* ed = env->GetByteArrayElements(encryptedDataIn, nullptr);
    jbyte* pk = env->GetByteArrayElements(profileKey, nullptr);

    jsize ed_len = env->GetArrayLength(encryptedDataIn);

    std::array<uint8_t, enchant::zk::zkgroup::CLIENT_ZK_PROFILE_KEY_SIZE> profile_key;
    std::memcpy(profile_key.data(), pk, enchant::zk::zkgroup::CLIENT_ZK_PROFILE_KEY_SIZE);

    enchant::zk::zkgroup::EncryptedProfile enc;
    enc.version = static_cast<uint32_t>(versionIn);
    enc.encrypted_data.assign(
        reinterpret_cast<const uint8_t*>(ed),
        reinterpret_cast<const uint8_t*>(ed) + ed_len
    );

    std::vector<uint8_t> plaintext;

    int rc = ops->decrypt_profile(enc, profile_key, plaintext);

    if (rc == ENCHANT_SUCCESS) {
        jbyte* out = env->GetByteArrayElements(plaintextOut, nullptr);
        jsize out_len = env->GetArrayLength(plaintextOut);

        size_t copy_len = std::min(plaintext.size(), static_cast<size_t>(out_len));
        std::memcpy(out, plaintext.data(), copy_len);

        env->ReleaseByteArrayElements(plaintextOut, out, 0);

        sodium_memzero(plaintext.data(), plaintext.size());
    }

    env->ReleaseByteArrayElements(encryptedDataIn, ed, JNI_ABORT);
    env->ReleaseByteArrayElements(profileKey, pk, JNI_ABORT);

    sodium_memzero(profile_key.data(), profile_key.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeClientZkProfile_showUuidFromCredentialNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray credential,
    jbyteArray uuid,
    jbyteArray profileKey,
    jbyteArray randomness,
    jbyteArray presentationOut
) {
    (void)clazz;

    auto* ops = handle_to_ptr<enchant::zk::zkgroup::ClientZkProfileOperations>(handle);
    if (ops == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* cred_buf = env->GetByteArrayElements(credential, nullptr);
    jsize cred_len = env->GetArrayLength(credential);
    jbyte* uuid_buf = env->GetByteArrayElements(uuid, nullptr);
    jbyte* pk = env->GetByteArrayElements(profileKey, nullptr);
    jbyte* rand = env->GetByteArrayElements(randomness, nullptr);
    jsize rand_len = env->GetArrayLength(randomness);

    if (cred_len < static_cast<jsize>(sizeof(enchant::zk::zkgroup::ProfileKeyCredential))) {
        env->ReleaseByteArrayElements(credential, cred_buf, JNI_ABORT);
        env->ReleaseByteArrayElements(uuid, uuid_buf, JNI_ABORT);
        env->ReleaseByteArrayElements(profileKey, pk, JNI_ABORT);
        env->ReleaseByteArrayElements(randomness, rand, JNI_ABORT);
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    enchant::zk::zkgroup::ProfileKeyCredential pkc;
    std::memcpy(&pkc, cred_buf, sizeof(enchant::zk::zkgroup::ProfileKeyCredential));

    std::array<uint8_t, enchant::zk::zkgroup::CLIENT_ZK_UUID_SIZE> uuid_arr;
    std::memcpy(uuid_arr.data(), uuid_buf, enchant::zk::zkgroup::CLIENT_ZK_UUID_SIZE);

    std::array<uint8_t, enchant::zk::zkgroup::CLIENT_ZK_PROFILE_KEY_SIZE> profile_key;
    std::memcpy(profile_key.data(), pk, enchant::zk::zkgroup::CLIENT_ZK_PROFILE_KEY_SIZE);

    enchant::zk::zkgroup::ProfileKeyCredentialPresentation pres;

    int rc = ops->show_uuid_from_credential(
        pkc, uuid_arr, profile_key,
        reinterpret_cast<const uint8_t*>(rand),
        static_cast<size_t>(rand_len),
        pres
    );

    if (rc == ENCHANT_SUCCESS) {
        jbyte* out = env->GetByteArrayElements(presentationOut, nullptr);
        std::memcpy(out, &pres, sizeof(enchant::zk::zkgroup::ProfileKeyCredentialPresentation));
        env->ReleaseByteArrayElements(presentationOut, out, 0);
    }

    env->ReleaseByteArrayElements(credential, cred_buf, JNI_ABORT);
    env->ReleaseByteArrayElements(uuid, uuid_buf, JNI_ABORT);
    env->ReleaseByteArrayElements(profileKey, pk, JNI_ABORT);
    env->ReleaseByteArrayElements(randomness, rand, JNI_ABORT);

    sodium_memzero(profile_key.data(), profile_key.size());
    sodium_memzero(pkc.credential.serialized.data(),
                   pkc.credential.serialized.size());

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeClientZkProfile_getProfileKeyVersionNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray profileKey,
    jbyteArray versionOut
) {
    (void)clazz;

    auto* ops = handle_to_ptr<enchant::zk::zkgroup::ClientZkProfileOperations>(handle);
    if (ops == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* pk = env->GetByteArrayElements(profileKey, nullptr);

    std::array<uint8_t, enchant::zk::zkgroup::CLIENT_ZK_PROFILE_KEY_SIZE> profile_key;
    std::memcpy(profile_key.data(), pk, enchant::zk::zkgroup::CLIENT_ZK_PROFILE_KEY_SIZE);

    enchant::zk::zkgroup::ProfileKeyVersion ver = ops->get_profile_key_version(profile_key);

    jbyte* out = env->GetByteArrayElements(versionOut, nullptr);
    std::memcpy(out, ver.version_bytes.data(), 32);
    env->ReleaseByteArrayElements(versionOut, out, 0);

    env->ReleaseByteArrayElements(profileKey, pk, JNI_ABORT);
    sodium_memzero(profile_key.data(), profile_key.size());

    return ENCHANT_SUCCESS;
}

/* ═══════════════════════════════════════════════════════════════
   KeyTransparency — Opaque-handle bindings
   ═══════════════════════════════════════════════════════════════ */

JNIEXPORT jlong JNICALL
Java_org_enchant_core_crypto_NativeKeyTransparency_createNative(
    JNIEnv* env,
    jclass clazz
) {
    (void)env;
    (void)clazz;

    auto* kt = new (std::nothrow) enchant::groups::KeyTransparency();
    if (kt == nullptr) {
        return 0;
    }

    int rc = kt->initialize();
    if (rc != ENCHANT_SUCCESS) {
        delete kt;
        return 0;
    }

    return ptr_to_handle(kt);
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_NativeKeyTransparency_destroyNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    (void)env;
    (void)clazz;

    auto* kt = handle_to_ptr<enchant::groups::KeyTransparency>(handle);
    delete kt;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeKeyTransparency_addEntryNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray keyId,
    jbyteArray publicKey,
    jint entryType,
    jlong timestamp
) {
    (void)clazz;

    auto* kt = handle_to_ptr<enchant::groups::KeyTransparency>(handle);
    if (kt == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* kid = env->GetByteArrayElements(keyId, nullptr);
    jbyte* pk = env->GetByteArrayElements(publicKey, nullptr);

    enchant::groups::KtEntry entry;
    std::memcpy(entry.key_id.data(), kid, enchant::groups::KT_KEY_SIZE);
    std::memcpy(entry.public_key.data(), pk, enchant::groups::KT_KEY_SIZE);
    entry.type = static_cast<enchant::groups::KtEntryType>(entryType);
    entry.timestamp = static_cast<uint64_t>(timestamp);

    enchant::groups::KtDirectoryState state;

    int rc = kt->add_entry(state, entry);

    env->ReleaseByteArrayElements(keyId, kid, JNI_ABORT);
    env->ReleaseByteArrayElements(publicKey, pk, JNI_ABORT);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeKeyTransparency_updateEntryNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray keyId,
    jbyteArray publicKey,
    jint entryType,
    jlong timestamp,
    jbyteArray directoryStateIn,
    jint directoryStateLen,
    jbyteArray directoryStateOut
) {
    (void)clazz;

    auto* kt = handle_to_ptr<enchant::groups::KeyTransparency>(handle);
    if (kt == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* kid = env->GetByteArrayElements(keyId, nullptr);
    jbyte* pk = env->GetByteArrayElements(publicKey, nullptr);
    jbyte* ds_in = env->GetByteArrayElements(directoryStateIn, nullptr);

    enchant::groups::KtEntry entry;
    std::memcpy(entry.key_id.data(), kid, enchant::groups::KT_KEY_SIZE);
    std::memcpy(entry.public_key.data(), pk, enchant::groups::KT_KEY_SIZE);
    entry.type = static_cast<enchant::groups::KtEntryType>(entryType);
    entry.timestamp = static_cast<uint64_t>(timestamp);

    enchant::groups::KtDirectoryState state;
    int ds_rc = kt->deserialize_directory_state(
        state,
        reinterpret_cast<const uint8_t*>(ds_in),
        static_cast<size_t>(directoryStateLen)
    );

    if (ds_rc != ENCHANT_SUCCESS) {
        env->ReleaseByteArrayElements(keyId, kid, JNI_ABORT);
        env->ReleaseByteArrayElements(publicKey, pk, JNI_ABORT);
        env->ReleaseByteArrayElements(directoryStateIn, ds_in, JNI_ABORT);
        return ds_rc;
    }

    int rc = kt->update_entry(state, entry);

    if (rc == ENCHANT_SUCCESS) {
        std::vector<uint8_t> serialized;
        rc = kt->serialize_directory_state(state, serialized);
        if (rc == ENCHANT_SUCCESS) {
            jbyte* ds_out = env->GetByteArrayElements(directoryStateOut, nullptr);
            jsize out_len = env->GetArrayLength(directoryStateOut);
            size_t copy_len = std::min(serialized.size(), static_cast<size_t>(out_len));
            std::memcpy(ds_out, serialized.data(), copy_len);
            env->ReleaseByteArrayElements(directoryStateOut, ds_out, 0);
            sodium_memzero(serialized.data(), serialized.size());
        }
    }

    env->ReleaseByteArrayElements(keyId, kid, JNI_ABORT);
    env->ReleaseByteArrayElements(publicKey, pk, JNI_ABORT);
    env->ReleaseByteArrayElements(directoryStateIn, ds_in, JNI_ABORT);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeKeyTransparency_removeEntryNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray keyId,
    jbyteArray directoryStateIn,
    jint directoryStateLen,
    jbyteArray directoryStateOut
) {
    (void)clazz;

    auto* kt = handle_to_ptr<enchant::groups::KeyTransparency>(handle);
    if (kt == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* kid = env->GetByteArrayElements(keyId, nullptr);
    jbyte* ds_in = env->GetByteArrayElements(directoryStateIn, nullptr);

    std::array<uint8_t, enchant::groups::KT_KEY_SIZE> key_id;
    std::memcpy(key_id.data(), kid, enchant::groups::KT_KEY_SIZE);

    enchant::groups::KtDirectoryState state;
    int ds_rc = kt->deserialize_directory_state(
        state,
        reinterpret_cast<const uint8_t*>(ds_in),
        static_cast<size_t>(directoryStateLen)
    );

    if (ds_rc != ENCHANT_SUCCESS) {
        env->ReleaseByteArrayElements(keyId, kid, JNI_ABORT);
        env->ReleaseByteArrayElements(directoryStateIn, ds_in, JNI_ABORT);
        return ds_rc;
    }

    int rc = kt->remove_entry(state, key_id);

    if (rc == ENCHANT_SUCCESS) {
        std::vector<uint8_t> serialized;
        rc = kt->serialize_directory_state(state, serialized);
        if (rc == ENCHANT_SUCCESS) {
            jbyte* ds_out = env->GetByteArrayElements(directoryStateOut, nullptr);
            jsize out_len = env->GetArrayLength(directoryStateOut);
            size_t copy_len = std::min(serialized.size(), static_cast<size_t>(out_len));
            std::memcpy(ds_out, serialized.data(), copy_len);
            env->ReleaseByteArrayElements(directoryStateOut, ds_out, 0);
            sodium_memzero(serialized.data(), serialized.size());
        }
    }

    env->ReleaseByteArrayElements(keyId, kid, JNI_ABORT);
    env->ReleaseByteArrayElements(directoryStateIn, ds_in, JNI_ABORT);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeKeyTransparency_generateAuditProofNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray directoryStateIn,
    jint directoryStateLen,
    jint leafIndex,
    jbyteArray proofPathOut,
    jintArray proofPathLenOut,
    jbyteArray rootHashOut
) {
    (void)clazz;

    auto* kt = handle_to_ptr<enchant::groups::KeyTransparency>(handle);
    if (kt == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* ds_in = env->GetByteArrayElements(directoryStateIn, nullptr);

    enchant::groups::KtDirectoryState state;
    int ds_rc = kt->deserialize_directory_state(
        state,
        reinterpret_cast<const uint8_t*>(ds_in),
        static_cast<size_t>(directoryStateLen)
    );

    if (ds_rc != ENCHANT_SUCCESS) {
        env->ReleaseByteArrayElements(directoryStateIn, ds_in, JNI_ABORT);
        return ds_rc;
    }

    enchant::groups::KtAuditProof proof;

    int rc = kt->generate_audit_proof(state, static_cast<size_t>(leafIndex), proof);

    if (rc == ENCHANT_SUCCESS) {
        jbyte* root = env->GetByteArrayElements(rootHashOut, nullptr);
        std::memcpy(root, proof.root_hash.data(), enchant::groups::KT_HASH_SIZE);
        env->ReleaseByteArrayElements(rootHashOut, root, 0);

        jbyte* path = env->GetByteArrayElements(proofPathOut, nullptr);
        jsize path_out_len = env->GetArrayLength(proofPathOut);
        jint* path_len = env->GetIntArrayElements(proofPathLenOut, nullptr);

        size_t offset = 0;
        size_t remaining = static_cast<size_t>(path_out_len);

        size_t path_count = proof.path.size();
        if (offset + sizeof(uint32_t) <= remaining) {
            uint32_t cnt = static_cast<uint32_t>(path_count);
            std::memcpy(path + offset, &cnt, sizeof(uint32_t));
            offset += sizeof(uint32_t);
        }

        for (size_t i = 0; i < path_count; i++) {
            size_t node_size = enchant::groups::KT_HASH_SIZE + sizeof(bool);
            if (offset + node_size > remaining) break;

            std::memcpy(path + offset, proof.path[i].hash.data(), enchant::groups::KT_HASH_SIZE);
            offset += enchant::groups::KT_HASH_SIZE;

            uint8_t is_left = proof.path[i].is_left ? 1 : 0;
            std::memcpy(path + offset, &is_left, sizeof(uint8_t));
            offset += sizeof(uint8_t);
        }

        *path_len = static_cast<jint>(offset);

        env->ReleaseByteArrayElements(proofPathOut, path, 0);
        env->ReleaseIntArrayElements(proofPathLenOut, path_len, 0);
    }

    env->ReleaseByteArrayElements(directoryStateIn, ds_in, JNI_ABORT);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeKeyTransparency_verifyAuditProofNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray proofPathIn,
    jint proofPathLen,
    jbyteArray rootHashIn,
    jint leafIndex,
    jbyteArray keyId,
    jbyteArray publicKey,
    jint entryType,
    jlong timestamp,
    jbooleanArray validOut
) {
    (void)clazz;

    auto* kt = handle_to_ptr<enchant::groups::KeyTransparency>(handle);
    if (kt == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* pp = env->GetByteArrayElements(proofPathIn, nullptr);
    jbyte* rh = env->GetByteArrayElements(rootHashIn, nullptr);
    jbyte* kid = env->GetByteArrayElements(keyId, nullptr);
    jbyte* pk = env->GetByteArrayElements(publicKey, nullptr);

    enchant::groups::KtAuditProof proof;
    proof.leaf_index = static_cast<size_t>(leafIndex);
    std::memcpy(proof.root_hash.data(), rh, enchant::groups::KT_HASH_SIZE);

    size_t offset = 0;
    uint32_t path_count = 0;
    if (static_cast<size_t>(proofPathLen) >= sizeof(uint32_t)) {
        std::memcpy(&path_count, pp + offset, sizeof(uint32_t));
        offset += sizeof(uint32_t);
    }

    proof.path.resize(path_count);
    for (uint32_t i = 0; i < path_count; i++) {
        if (offset + enchant::groups::KT_HASH_SIZE + sizeof(uint8_t) >
            static_cast<size_t>(proofPathLen)) {
            break;
        }

        std::memcpy(proof.path[i].hash.data(), pp + offset, enchant::groups::KT_HASH_SIZE);
        offset += enchant::groups::KT_HASH_SIZE;

        uint8_t is_left_byte = 0;
        std::memcpy(&is_left_byte, pp + offset, sizeof(uint8_t));
        proof.path[i].is_left = (is_left_byte != 0);
        offset += sizeof(uint8_t);
    }

    enchant::groups::KtEntry entry;
    std::memcpy(entry.key_id.data(), kid, enchant::groups::KT_KEY_SIZE);
    std::memcpy(entry.public_key.data(), pk, enchant::groups::KT_KEY_SIZE);
    entry.type = static_cast<enchant::groups::KtEntryType>(entryType);
    entry.timestamp = static_cast<uint64_t>(timestamp);

    bool valid = false;

    int rc = kt->verify_audit_proof(proof, entry, valid);

    if (rc == ENCHANT_SUCCESS) {
        jboolean* out = env->GetBooleanArrayElements(validOut, nullptr);
        *out = valid ? JNI_TRUE : JNI_FALSE;
        env->ReleaseBooleanArrayElements(validOut, out, 0);
    }

    env->ReleaseByteArrayElements(proofPathIn, pp, JNI_ABORT);
    env->ReleaseByteArrayElements(rootHashIn, rh, JNI_ABORT);
    env->ReleaseByteArrayElements(keyId, kid, JNI_ABORT);
    env->ReleaseByteArrayElements(publicKey, pk, JNI_ABORT);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeKeyTransparency_serializeDirectoryStateNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray directoryStateIn,
    jint directoryStateLen,
    jbyteArray output
) {
    (void)clazz;

    auto* kt = handle_to_ptr<enchant::groups::KeyTransparency>(handle);
    if (kt == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* ds_in = env->GetByteArrayElements(directoryStateIn, nullptr);

    enchant::groups::KtDirectoryState state;
    int ds_rc = kt->deserialize_directory_state(
        state,
        reinterpret_cast<const uint8_t*>(ds_in),
        static_cast<size_t>(directoryStateLen)
    );

    if (ds_rc != ENCHANT_SUCCESS) {
        env->ReleaseByteArrayElements(directoryStateIn, ds_in, JNI_ABORT);
        return ds_rc;
    }

    std::vector<uint8_t> serialized;

    int rc = kt->serialize_directory_state(state, serialized);

    if (rc == ENCHANT_SUCCESS) {
        jbyte* out = env->GetByteArrayElements(output, nullptr);
        jsize out_len = env->GetArrayLength(output);
        size_t copy_len = std::min(serialized.size(), static_cast<size_t>(out_len));
        std::memcpy(out, serialized.data(), copy_len);
        env->ReleaseByteArrayElements(output, out, 0);

        sodium_memzero(serialized.data(), serialized.size());
    }

    env->ReleaseByteArrayElements(directoryStateIn, ds_in, JNI_ABORT);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeKeyTransparency_deserializeDirectoryStateNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray data,
    jint dataLen,
    jbyteArray directoryStateOut
) {
    (void)clazz;

    auto* kt = handle_to_ptr<enchant::groups::KeyTransparency>(handle);
    if (kt == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* buf = env->GetByteArrayElements(data, nullptr);

    enchant::groups::KtDirectoryState state;

    int rc = kt->deserialize_directory_state(
        state,
        reinterpret_cast<const uint8_t*>(buf),
        static_cast<size_t>(dataLen)
    );

    if (rc == ENCHANT_SUCCESS) {
        std::vector<uint8_t> serialized;
        rc = kt->serialize_directory_state(state, serialized);
        if (rc == ENCHANT_SUCCESS) {
            jbyte* out = env->GetByteArrayElements(directoryStateOut, nullptr);
            jsize out_len = env->GetArrayLength(directoryStateOut);
            size_t copy_len = std::min(serialized.size(), static_cast<size_t>(out_len));
            std::memcpy(out, serialized.data(), copy_len);
            env->ReleaseByteArrayElements(directoryStateOut, out, 0);

            sodium_memzero(serialized.data(), serialized.size());
        }
    }

    env->ReleaseByteArrayElements(data, buf, JNI_ABORT);

    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeKeyTransparency_getTreeSizeNative(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jbyteArray directoryStateIn,
    jint directoryStateLen
) {
    (void)clazz;

    auto* kt = handle_to_ptr<enchant::groups::KeyTransparency>(handle);
    if (kt == nullptr) {
        return 0;
    }

    jbyte* ds_in = env->GetByteArrayElements(directoryStateIn, nullptr);

    enchant::groups::KtDirectoryState state;
    int ds_rc = kt->deserialize_directory_state(
        state,
        reinterpret_cast<const uint8_t*>(ds_in),
        static_cast<size_t>(directoryStateLen)
    );

    env->ReleaseByteArrayElements(directoryStateIn, ds_in, JNI_ABORT);

    if (ds_rc != ENCHANT_SUCCESS) {
        return 0;
    }

    return static_cast<jint>(kt->get_tree_size(state));
}

} /* extern "C" */
