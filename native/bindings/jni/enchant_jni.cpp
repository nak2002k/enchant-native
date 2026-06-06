#include <jni.h>
#include <enchant/api.h>
#include <enchant/error.h>
#include <protocol/x3dh.hpp>
#include <groups/sender_key.hpp>
#include <cstring>
#include <sodium.h>

namespace {
    jint throw_exception(JNIEnv* env, const char* msg, jint error_code) {
        jclass ex = env->FindClass("org/enchant/core/crypto/NativeX3DH$EnchantCryptoException");
        if (ex != nullptr) {
            env->ThrowNew(ex, msg);
        }
        return error_code;
    }
}

extern "C" {

/* ── X3DH Initiate (Alice's side) ── */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeX3DH_x3dhInitiateNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray ourIdentityPrivate,
    jbyteArray ourEphemeralPrivate,
    jbyteArray theirIdentityPublic,
    jbyteArray theirSignedPrekey,
    jobject theirOneTimePrekey,
    jbyteArray sharedSecret,
    jbyteArray rootKey,
    jbyteArray sendingChainKey,
    jbyteArray receivingChainKey,
    jbyteArray pqrKey
) {
    (void)clazz;

    jbyte* id_priv = env->GetByteArrayElements(ourIdentityPrivate, nullptr);
    jbyte* eph_priv = env->GetByteArrayElements(ourEphemeralPrivate, nullptr);
    jbyte* their_id_pub = env->GetByteArrayElements(theirIdentityPublic, nullptr);
    jbyte* their_spk = env->GetByteArrayElements(theirSignedPrekey, nullptr);
    jbyte* shared = env->GetByteArrayElements(sharedSecret, nullptr);
    jbyte* root = env->GetByteArrayElements(rootKey, nullptr);
    jbyte* send_chain = env->GetByteArrayElements(sendingChainKey, nullptr);
    jbyte* recv_chain = env->GetByteArrayElements(receivingChainKey, nullptr);
    jbyte* pqr = env->GetByteArrayElements(pqrKey, nullptr);

    jbyte* their_opk = nullptr;
    bool has_opk = false;

    if (theirOneTimePrekey != nullptr) {
        their_opk = env->GetByteArrayElements((jbyteArray)theirOneTimePrekey, nullptr);
        has_opk = true;
    }

    enchant::protocol::X3DHResult result;

    int rc = enchant::protocol::x3dh_initiate(
        reinterpret_cast<const uint8_t*>(id_priv),
        reinterpret_cast<const uint8_t*>(eph_priv),
        reinterpret_cast<const uint8_t*>(their_id_pub),
        reinterpret_cast<const uint8_t*>(their_spk),
        has_opk ? reinterpret_cast<const uint8_t*>(their_opk) : nullptr,
        result
    );

    if (rc == ENCHANT_SUCCESS) {
        std::memcpy(shared, result.shared_secret.data(), 32);
        std::memcpy(root, result.root_key.data(), 32);
        std::memcpy(send_chain, result.sending_chain_key.data(), 32);
        std::memcpy(recv_chain, result.receiving_chain_key.data(), 32);
        std::memcpy(pqr, result.pqr_key.data(), 32);
    }

    env->ReleaseByteArrayElements(ourIdentityPrivate, id_priv, JNI_ABORT);
    env->ReleaseByteArrayElements(ourEphemeralPrivate, eph_priv, JNI_ABORT);
    env->ReleaseByteArrayElements(theirIdentityPublic, their_id_pub, JNI_ABORT);
    env->ReleaseByteArrayElements(theirSignedPrekey, their_spk, JNI_ABORT);
    env->ReleaseByteArrayElements(sharedSecret, shared, 0);
    env->ReleaseByteArrayElements(rootKey, root, 0);
    env->ReleaseByteArrayElements(sendingChainKey, send_chain, 0);
    env->ReleaseByteArrayElements(receivingChainKey, recv_chain, 0);
    env->ReleaseByteArrayElements(pqrKey, pqr, 0);

    if (theirOneTimePrekey != nullptr && their_opk != nullptr) {
        env->ReleaseByteArrayElements((jbyteArray)theirOneTimePrekey, their_opk, JNI_ABORT);
    }

    sodium_memzero(result.shared_secret.data(), result.shared_secret.size());
    sodium_memzero(result.root_key.data(), result.root_key.size());
    sodium_memzero(result.sending_chain_key.data(), result.sending_chain_key.size());
    sodium_memzero(result.receiving_chain_key.data(), result.receiving_chain_key.size());
    sodium_memzero(result.pqr_key.data(), result.pqr_key.size());

    return rc;
}

/* ── X3DH Respond (Bob's side) ── */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeX3DH_x3dhRespondNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray ourIdentityPrivate,
    jbyteArray ourSignedPrekeyPrivate,
    jobject ourOneTimePrekeyPrivate,
    jbyteArray theirIdentityPublic,
    jbyteArray theirEphemeralPublic,
    jbyteArray sharedSecret,
    jbyteArray rootKey,
    jbyteArray sendingChainKey,
    jbyteArray receivingChainKey,
    jbyteArray pqrKey
) {
    (void)clazz;

    jbyte* id_priv = env->GetByteArrayElements(ourIdentityPrivate, nullptr);
    jbyte* spk_priv = env->GetByteArrayElements(ourSignedPrekeyPrivate, nullptr);
    jbyte* their_id_pub = env->GetByteArrayElements(theirIdentityPublic, nullptr);
    jbyte* their_eph_pub = env->GetByteArrayElements(theirEphemeralPublic, nullptr);
    jbyte* shared = env->GetByteArrayElements(sharedSecret, nullptr);
    jbyte* root = env->GetByteArrayElements(rootKey, nullptr);
    jbyte* send_chain = env->GetByteArrayElements(sendingChainKey, nullptr);
    jbyte* recv_chain = env->GetByteArrayElements(receivingChainKey, nullptr);
    jbyte* pqr = env->GetByteArrayElements(pqrKey, nullptr);

    jbyte* their_opk = nullptr;
    bool has_opk = false;

    if (ourOneTimePrekeyPrivate != nullptr) {
        their_opk = env->GetByteArrayElements((jbyteArray)ourOneTimePrekeyPrivate, nullptr);
        has_opk = true;
    }

    enchant::protocol::X3DHResult result;

    int rc = enchant::protocol::x3dh_respond(
        reinterpret_cast<const uint8_t*>(id_priv),
        reinterpret_cast<const uint8_t*>(spk_priv),
        has_opk ? reinterpret_cast<const uint8_t*>(their_opk) : nullptr,
        reinterpret_cast<const uint8_t*>(their_id_pub),
        reinterpret_cast<const uint8_t*>(their_eph_pub),
        result
    );

    if (rc == ENCHANT_SUCCESS) {
        std::memcpy(shared, result.shared_secret.data(), 32);
        std::memcpy(root, result.root_key.data(), 32);
        std::memcpy(send_chain, result.sending_chain_key.data(), 32);
        std::memcpy(recv_chain, result.receiving_chain_key.data(), 32);
        std::memcpy(pqr, result.pqr_key.data(), 32);
    }

    env->ReleaseByteArrayElements(ourIdentityPrivate, id_priv, JNI_ABORT);
    env->ReleaseByteArrayElements(ourSignedPrekeyPrivate, spk_priv, JNI_ABORT);
    env->ReleaseByteArrayElements(theirIdentityPublic, their_id_pub, JNI_ABORT);
    env->ReleaseByteArrayElements(theirEphemeralPublic, their_eph_pub, JNI_ABORT);
    env->ReleaseByteArrayElements(sharedSecret, shared, 0);
    env->ReleaseByteArrayElements(rootKey, root, 0);
    env->ReleaseByteArrayElements(sendingChainKey, send_chain, 0);
    env->ReleaseByteArrayElements(receivingChainKey, recv_chain, 0);
    env->ReleaseByteArrayElements(pqrKey, pqr, 0);

    if (ourOneTimePrekeyPrivate != nullptr && their_opk != nullptr) {
        env->ReleaseByteArrayElements((jbyteArray)ourOneTimePrekeyPrivate, their_opk, JNI_ABORT);
    }

    sodium_memzero(result.shared_secret.data(), result.shared_secret.size());
    sodium_memzero(result.root_key.data(), result.root_key.size());
    sodium_memzero(result.sending_chain_key.data(), result.sending_chain_key.size());
    sodium_memzero(result.receiving_chain_key.data(), result.receiving_chain_key.size());
    sodium_memzero(result.pqr_key.data(), result.pqr_key.size());

    return rc;
}

/* ── PQXDH Initiate ── */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeX3DH_pqxdhInitiateNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray ourIdentityPrivate,
    jbyteArray ourEphemeralPrivate,
    jbyteArray theirIdentityPublic,
    jbyteArray theirSignedPrekey,
    jbyteArray theirKyberPrekey,
    jobject theirOneTimePrekey,
    jbyteArray rootKey,
    jbyteArray chainKey,
    jbyteArray pqrKey,
    jbyteArray kyberCiphertext,
    jbyteArray sharedSecret
) {
    (void)clazz;

    jbyte* id_priv = env->GetByteArrayElements(ourIdentityPrivate, nullptr);
    jbyte* eph_priv = env->GetByteArrayElements(ourEphemeralPrivate, nullptr);
    jbyte* their_id_pub = env->GetByteArrayElements(theirIdentityPublic, nullptr);
    jbyte* their_spk = env->GetByteArrayElements(theirSignedPrekey, nullptr);
    jbyte* their_kyber = env->GetByteArrayElements(theirKyberPrekey, nullptr);
    jbyte* root = env->GetByteArrayElements(rootKey, nullptr);
    jbyte* chain = env->GetByteArrayElements(chainKey, nullptr);
    jbyte* pqr = env->GetByteArrayElements(pqrKey, nullptr);
    jbyte* kyber_ct = env->GetByteArrayElements(kyberCiphertext, nullptr);
    jbyte* shared = env->GetByteArrayElements(sharedSecret, nullptr);

    jbyte* their_opk = nullptr;
    bool has_opk = false;

    if (theirOneTimePrekey != nullptr) {
        their_opk = env->GetByteArrayElements((jbyteArray)theirOneTimePrekey, nullptr);
        has_opk = true;
    }

    enchant::protocol::PQXDHResult result;

    int rc = enchant::protocol::pqxdh_initiate(
        reinterpret_cast<const uint8_t*>(id_priv),
        reinterpret_cast<const uint8_t*>(eph_priv),
        reinterpret_cast<const uint8_t*>(their_id_pub),
        reinterpret_cast<const uint8_t*>(their_spk),
        reinterpret_cast<const uint8_t*>(their_kyber),
        static_cast<size_t>(env->GetArrayLength(theirKyberPrekey)),
        has_opk ? reinterpret_cast<const uint8_t*>(their_opk) : nullptr,
        result
    );

    if (rc == ENCHANT_SUCCESS) {
        std::memcpy(root, result.root_key.data(), 32);
        std::memcpy(chain, result.chain_key.data(), 32);
        std::memcpy(pqr, result.pqr_key.data(), 32);
        std::memcpy(kyber_ct, result.kyber_ciphertext.data(), result.kyber_ciphertext.size());
        std::memcpy(shared, result.shared_secret.data(), 32);
    }

    env->ReleaseByteArrayElements(ourIdentityPrivate, id_priv, JNI_ABORT);
    env->ReleaseByteArrayElements(ourEphemeralPrivate, eph_priv, JNI_ABORT);
    env->ReleaseByteArrayElements(theirIdentityPublic, their_id_pub, JNI_ABORT);
    env->ReleaseByteArrayElements(theirSignedPrekey, their_spk, JNI_ABORT);
    env->ReleaseByteArrayElements(theirKyberPrekey, their_kyber, JNI_ABORT);
    env->ReleaseByteArrayElements(rootKey, root, 0);
    env->ReleaseByteArrayElements(chainKey, chain, 0);
    env->ReleaseByteArrayElements(pqrKey, pqr, 0);
    env->ReleaseByteArrayElements(kyberCiphertext, kyber_ct, 0);
    env->ReleaseByteArrayElements(sharedSecret, shared, 0);

    if (theirOneTimePrekey != nullptr && their_opk != nullptr) {
        env->ReleaseByteArrayElements((jbyteArray)theirOneTimePrekey, their_opk, JNI_ABORT);
    }

    sodium_memzero(result.root_key.data(), result.root_key.size());
    sodium_memzero(result.chain_key.data(), result.chain_key.size());
    sodium_memzero(result.pqr_key.data(), result.pqr_key.size());
    sodium_memzero(result.kyber_ciphertext.data(), result.kyber_ciphertext.size());
    sodium_memzero(result.shared_secret.data(), result.shared_secret.size());

    return rc;
}

/* ── PQXDH Respond ── */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeX3DH_pqxdhRespondNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray ourIdentityPrivate,
    jbyteArray ourSignedPrekeyPrivate,
    jobject ourOneTimePrekeyPrivate,
    jbyteArray ourKyberPrekeyPrivate,
    jbyteArray theirIdentityPublic,
    jbyteArray theirEphemeralPublic,
    jbyteArray theirKyberCiphertext,
    jbyteArray rootKey,
    jbyteArray chainKey,
    jbyteArray pqrKey,
    jbyteArray sharedSecret
) {
    (void)clazz;

    jbyte* id_priv = env->GetByteArrayElements(ourIdentityPrivate, nullptr);
    jbyte* spk_priv = env->GetByteArrayElements(ourSignedPrekeyPrivate, nullptr);
    jbyte* kyber_priv = env->GetByteArrayElements(ourKyberPrekeyPrivate, nullptr);
    jbyte* their_id_pub = env->GetByteArrayElements(theirIdentityPublic, nullptr);
    jbyte* their_eph_pub = env->GetByteArrayElements(theirEphemeralPublic, nullptr);
    jbyte* their_kyber_ct = env->GetByteArrayElements(theirKyberCiphertext, nullptr);
    jbyte* root = env->GetByteArrayElements(rootKey, nullptr);
    jbyte* chain = env->GetByteArrayElements(chainKey, nullptr);
    jbyte* pqr = env->GetByteArrayElements(pqrKey, nullptr);
    jbyte* shared = env->GetByteArrayElements(sharedSecret, nullptr);

    jbyte* their_opk = nullptr;
    bool has_opk = false;

    if (ourOneTimePrekeyPrivate != nullptr) {
        their_opk = env->GetByteArrayElements((jbyteArray)ourOneTimePrekeyPrivate, nullptr);
        has_opk = true;
    }

    enchant::protocol::PQXDHResult result;

    int rc = enchant::protocol::pqxdh_respond(
        reinterpret_cast<const uint8_t*>(id_priv),
        reinterpret_cast<const uint8_t*>(spk_priv),
        has_opk ? reinterpret_cast<const uint8_t*>(their_opk) : nullptr,
        reinterpret_cast<const uint8_t*>(kyber_priv),
        reinterpret_cast<const uint8_t*>(their_id_pub),
        reinterpret_cast<const uint8_t*>(their_eph_pub),
        reinterpret_cast<const uint8_t*>(their_kyber_ct),
        static_cast<size_t>(env->GetArrayLength(theirKyberCiphertext)),
        result
    );

    if (rc == ENCHANT_SUCCESS) {
        std::memcpy(root, result.root_key.data(), 32);
        std::memcpy(chain, result.chain_key.data(), 32);
        std::memcpy(pqr, result.pqr_key.data(), 32);
        std::memcpy(shared, result.shared_secret.data(), 32);
    }

    env->ReleaseByteArrayElements(ourIdentityPrivate, id_priv, JNI_ABORT);
    env->ReleaseByteArrayElements(ourSignedPrekeyPrivate, spk_priv, JNI_ABORT);
    env->ReleaseByteArrayElements(ourKyberPrekeyPrivate, kyber_priv, JNI_ABORT);
    env->ReleaseByteArrayElements(theirIdentityPublic, their_id_pub, JNI_ABORT);
    env->ReleaseByteArrayElements(theirEphemeralPublic, their_eph_pub, JNI_ABORT);
    env->ReleaseByteArrayElements(theirKyberCiphertext, their_kyber_ct, JNI_ABORT);
    env->ReleaseByteArrayElements(rootKey, root, 0);
    env->ReleaseByteArrayElements(chainKey, chain, 0);
    env->ReleaseByteArrayElements(pqrKey, pqr, 0);
    env->ReleaseByteArrayElements(sharedSecret, shared, 0);

    if (ourOneTimePrekeyPrivate != nullptr && their_opk != nullptr) {
        env->ReleaseByteArrayElements((jbyteArray)ourOneTimePrekeyPrivate, their_opk, JNI_ABORT);
    }

    sodium_memzero(result.root_key.data(), result.root_key.size());
    sodium_memzero(result.chain_key.data(), result.chain_key.size());
    sodium_memzero(result.pqr_key.data(), result.pqr_key.size());
    sodium_memzero(result.shared_secret.data(), result.shared_secret.size());

    return rc;
}

/* ── Sender Key JNI Handle Management ── */

static enchant::groups::SenderKeyState* get_state_from_handle(jlong handle) {
    if (handle == 0) return nullptr;
    return reinterpret_cast<enchant::groups::SenderKeyState*>(static_cast<uintptr_t>(handle));
}

/* ── Create Sender Key ── */

JNIEXPORT jlong JNICALL
Java_org_enchant_core_crypto_NativeSenderKey_createSenderKeyNative(
    JNIEnv* env,
    jclass clazz,
    jstring senderId,
    jint keyId
) {
    (void)clazz;

    const char* sender_id_str = env->GetStringUTFChars(senderId, nullptr);
    if (sender_id_str == nullptr) {
        return 0;
    }

    auto* state = new enchant::groups::SenderKeyState();
    int rc = enchant::groups::sender_key_create(sender_id_str, static_cast<uint32_t>(keyId), *state);

    env->ReleaseStringUTFChars(senderId, sender_id_str);

    if (rc != ENCHANT_SUCCESS) {
        delete state;
        return 0;
    }

    return static_cast<jlong>(reinterpret_cast<uintptr_t>(state));
}

/* ── Get Sender Key State ── */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeSenderKey_getSenderKeyStateNative(
    JNIEnv* env,
    jclass clazz,
    jlong stateHandle,
    jbyteArray chainKey,
    jintArray iteration,
    jintArray epoch
) {
    (void)clazz;

    auto* state = get_state_from_handle(stateHandle);
    if (state == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* ck = env->GetByteArrayElements(chainKey, nullptr);
    jint* iter = env->GetIntArrayElements(iteration, nullptr);
    jint* ep = env->GetIntArrayElements(epoch, nullptr);

    std::memcpy(ck, state->chain_key.seed.data(), 32);
    *iter = static_cast<jint>(state->chain_key.iteration);
    *ep = static_cast<jint>(state->epoch);

    env->ReleaseByteArrayElements(chainKey, ck, 0);
    env->ReleaseIntArrayElements(iteration, iter, 0);
    env->ReleaseIntArrayElements(epoch, ep, 0);

    return ENCHANT_SUCCESS;
}

/* ── Encrypt Sender Key ── */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeSenderKey_encryptSenderKeyNative(
    JNIEnv* env,
    jclass clazz,
    jlong stateHandle,
    jbyteArray plaintext,
    jint plaintextLen,
    jbyteArray output,
    jintArray outputLen
) {
    (void)clazz;

    auto* state = get_state_from_handle(stateHandle);
    if (state == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* pt = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* out = env->GetByteArrayElements(output, nullptr);
    jint* out_len = env->GetIntArrayElements(outputLen, nullptr);

    size_t out_len_val = static_cast<size_t>(*out_len);
    int rc = enchant::groups::sender_key_encrypt(
        *state,
        reinterpret_cast<const uint8_t*>(pt),
        static_cast<size_t>(plaintextLen),
        reinterpret_cast<uint8_t*>(out),
        &out_len_val
    );

    *out_len = static_cast<jint>(out_len_val);

    env->ReleaseByteArrayElements(plaintext, pt, JNI_ABORT);
    env->ReleaseByteArrayElements(output, out, 0);
    env->ReleaseIntArrayElements(outputLen, out_len, 0);

    return rc;
}

/* ── Decrypt Sender Key ── */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeSenderKey_decryptSenderKeyNative(
    JNIEnv* env,
    jclass clazz,
    jlong stateHandle,
    jbyteArray ciphertext,
    jint ciphertextLen,
    jbyteArray plaintext,
    jintArray plaintextLen
) {
    (void)clazz;

    auto* state = get_state_from_handle(stateHandle);
    if (state == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* ct = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* pt = env->GetByteArrayElements(plaintext, nullptr);
    jint* pt_len = env->GetIntArrayElements(plaintextLen, nullptr);

    size_t pt_len_val = static_cast<size_t>(*pt_len);
    int rc = enchant::groups::sender_key_decrypt(
        *state,
        reinterpret_cast<const uint8_t*>(ct),
        static_cast<size_t>(ciphertextLen),
        reinterpret_cast<uint8_t*>(pt),
        &pt_len_val
    );

    *pt_len = static_cast<jint>(pt_len_val);

    env->ReleaseByteArrayElements(ciphertext, ct, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, pt, 0);
    env->ReleaseIntArrayElements(plaintextLen, pt_len, 0);

    return rc;
}

/* ── Create Distribution Message ── */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeSenderKey_createDistributionMessageNative(
    JNIEnv* env,
    jclass clazz,
    jlong stateHandle,
    jbyteArray signingPrivate,
    jbyteArray output,
    jintArray outputLen
) {
    (void)clazz;

    auto* state = get_state_from_handle(stateHandle);
    if (state == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* priv = env->GetByteArrayElements(signingPrivate, nullptr);
    jbyte* out = env->GetByteArrayElements(output, nullptr);
    jint* out_len = env->GetIntArrayElements(outputLen, nullptr);

    size_t out_len_val = static_cast<size_t>(*out_len);
    int rc = enchant::groups::sender_key_create_distribution_message(
        *state,
        reinterpret_cast<const uint8_t*>(priv),
        reinterpret_cast<uint8_t*>(out),
        &out_len_val
    );

    *out_len = static_cast<jint>(out_len_val);

    env->ReleaseByteArrayElements(signingPrivate, priv, JNI_ABORT);
    env->ReleaseByteArrayElements(output, out, 0);
    env->ReleaseIntArrayElements(outputLen, out_len, 0);

    return rc;
}

/* ── Process Distribution Message ── */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeSenderKey_processDistributionMessageNative(
    JNIEnv* env,
    jclass clazz,
    jlong stateHandle,
    jbyteArray message,
    jint messageLen,
    jbyteArray signingPublic
) {
    (void)clazz;

    auto* state = get_state_from_handle(stateHandle);
    if (state == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    jbyte* msg = env->GetByteArrayElements(message, nullptr);
    jbyte* pub = env->GetByteArrayElements(signingPublic, nullptr);

    int rc = enchant::groups::sender_key_process_distribution_message(
        *state,
        reinterpret_cast<const uint8_t*>(msg),
        static_cast<size_t>(messageLen),
        reinterpret_cast<const uint8_t*>(pub)
    );

    env->ReleaseByteArrayElements(message, msg, JNI_ABORT);
    env->ReleaseByteArrayElements(signingPublic, pub, JNI_ABORT);

    return rc;
}

/* ── Serialize Sender Key Record ── */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_NativeSenderKey_serializeSenderKeyRecordNative(
    JNIEnv* env,
    jclass clazz,
    jlong stateHandle,
    jbyteArray output,
    jintArray outputLen
) {
    (void)clazz;

    auto* state = get_state_from_handle(stateHandle);
    if (state == nullptr) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    enchant::groups::SenderKeyRecord record;
    record.add_state();
    *(record.get_state(0)) = std::move(*state);

    jbyte* out = env->GetByteArrayElements(output, nullptr);
    jint* out_len = env->GetIntArrayElements(outputLen, nullptr);

    size_t out_len_val = static_cast<size_t>(*out_len);
    int rc = enchant::groups::sender_key_record_serialize(
        record,
        reinterpret_cast<uint8_t*>(out),
        &out_len_val
    );

    *out_len = static_cast<jint>(out_len_val);

    env->ReleaseByteArrayElements(output, out, 0);
    env->ReleaseIntArrayElements(outputLen, out_len, 0);

    return rc;
}

/* ── Deserialize Sender Key Record ── */

JNIEXPORT jlong JNICALL
Java_org_enchant_core_crypto_NativeSenderKey_deserializeSenderKeyRecordNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray data,
    jint dataLen
) {
    (void)clazz;

    jbyte* buf = env->GetByteArrayElements(data, nullptr);

    auto* record = new enchant::groups::SenderKeyRecord();
    int rc = enchant::groups::sender_key_record_deserialize(
        *record,
        reinterpret_cast<const uint8_t*>(buf),
        static_cast<size_t>(dataLen)
    );

    env->ReleaseByteArrayElements(data, buf, JNI_ABORT);

    if (rc != ENCHANT_SUCCESS) {
        delete record;
        return 0;
    }

    enchant::groups::SenderKeyState* state = record->get_state(0);
    if (state == nullptr) {
        delete record;
        return 0;
    }

    auto* result = new enchant::groups::SenderKeyState(std::move(*state));

    return static_cast<jlong>(reinterpret_cast<uintptr_t>(result));
}

/* ── Destroy Sender Key ── */

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_NativeSenderKey_destroySenderKeyNative(
    JNIEnv* env,
    jclass clazz,
    jlong stateHandle
) {
    (void)env;
    (void)clazz;

    auto* state = get_state_from_handle(stateHandle);
    if (state != nullptr) {
        delete state;
    }
}

} /* extern "C" */
