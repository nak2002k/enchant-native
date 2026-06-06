#include "pq/ml_kem.hpp"

#include <sodium.h>
#if defined(__has_include)
#  if __has_include(<sodium/crypto_kem_mlkem768.h>)
#    include <sodium/crypto_kem_mlkem768.h>
#    define ENCHANT_HAVE_LIBSODIUM_MLKEM768 1
#  endif
#endif

#if defined(ENCHANT_ENABLE_PQ)
extern "C" {
#include <openssl/bytestring.h>
#include <openssl/mlkem.h>
}
#endif

namespace enchant {
namespace pq {

int ml_kem_768_keypair(uint8_t* public_key, uint8_t* secret_key) {
#if defined(ENCHANT_HAVE_LIBSODIUM_MLKEM768)
    if (!public_key || !secret_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    int ret = crypto_kem_mlkem768_keypair(public_key, secret_key);
    return ret == 0 ? ENCHANT_SUCCESS : ENCHANT_ERROR_INTERNAL;
#else
    (void)public_key;
    (void)secret_key;
    return ENCHANT_ERROR_NOT_IMPLEMENTED;
#endif
}

int ml_kem_768_encapsulate(uint8_t* ciphertext, uint8_t* shared_secret,
                            const uint8_t* public_key) {
#if defined(ENCHANT_HAVE_LIBSODIUM_MLKEM768)
    if (!ciphertext || !shared_secret || !public_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    int ret = crypto_kem_mlkem768_enc(ciphertext, shared_secret, public_key);
    return ret == 0 ? ENCHANT_SUCCESS : ENCHANT_ERROR_INTERNAL;
#else
    (void)ciphertext;
    (void)shared_secret;
    (void)public_key;
    return ENCHANT_ERROR_NOT_IMPLEMENTED;
#endif
}

int ml_kem_768_decapsulate(uint8_t* shared_secret,
                            const uint8_t* ciphertext,
                            const uint8_t* secret_key) {
#if defined(ENCHANT_HAVE_LIBSODIUM_MLKEM768)
    if (!shared_secret || !ciphertext || !secret_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    int ret = crypto_kem_mlkem768_dec(shared_secret, ciphertext, secret_key);
    return ret == 0 ? ENCHANT_SUCCESS : ENCHANT_ERROR_INTERNAL;
#else
    (void)shared_secret;
    (void)ciphertext;
    (void)secret_key;
    return ENCHANT_ERROR_NOT_IMPLEMENTED;
#endif
}

int ml_kem_1024_keypair(uint8_t* public_key, uint8_t* secret_key) {
#if defined(ENCHANT_ENABLE_PQ)
    if (!public_key || !secret_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    MLKEM1024_private_key priv;
    MLKEM1024_generate_key(public_key, secret_key, &priv);
    sodium_memzero(&priv, sizeof(priv));
    return ENCHANT_SUCCESS;
#else
    (void)public_key;
    (void)secret_key;
    return ENCHANT_ERROR_NOT_IMPLEMENTED;
#endif
}

int ml_kem_1024_encapsulate(uint8_t* ciphertext, uint8_t* shared_secret,
                             const uint8_t* public_key) {
#if defined(ENCHANT_ENABLE_PQ)
    if (!ciphertext || !shared_secret || !public_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    MLKEM1024_public_key pub;
    CBS cbs;
    CBS_init(&cbs, public_key, ML_KEM_1024_PUBLIC_KEY_SIZE);
    if (!MLKEM1024_parse_public_key(&pub, &cbs) || CBS_len(&cbs) != 0) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    MLKEM1024_encap(ciphertext, shared_secret, &pub);
    sodium_memzero(&pub, sizeof(pub));
    return ENCHANT_SUCCESS;
#else
    (void)ciphertext;
    (void)shared_secret;
    (void)public_key;
    return ENCHANT_ERROR_NOT_IMPLEMENTED;
#endif
}

int ml_kem_1024_decapsulate(uint8_t* shared_secret,
                             const uint8_t* ciphertext,
                             const uint8_t* secret_key) {
#if defined(ENCHANT_ENABLE_PQ)
    if (!shared_secret || !ciphertext || !secret_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    MLKEM1024_private_key priv;
    if (!MLKEM1024_private_key_from_seed(&priv, secret_key, ML_KEM_1024_SEED_SIZE)) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    int ok = MLKEM1024_decap(shared_secret, ciphertext, ML_KEM_1024_CIPHERTEXT_SIZE, &priv);
    sodium_memzero(&priv, sizeof(priv));
    return ok == 1 ? ENCHANT_SUCCESS : ENCHANT_ERROR_DECRYPTION_FAILED;
#else
    (void)shared_secret;
    (void)ciphertext;
    (void)secret_key;
    return ENCHANT_ERROR_NOT_IMPLEMENTED;
#endif
}

bool ml_kem_768_available() {
#if defined(ENCHANT_HAVE_LIBSODIUM_MLKEM768)
    return true;
#else
    return false;
#endif
}

bool ml_kem_1024_available() {
#if defined(ENCHANT_ENABLE_PQ)
    return true;
#else
    return false;
#endif
}

} // namespace pq
} // namespace enchant
