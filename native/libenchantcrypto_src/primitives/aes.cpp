#include "primitives/aes.hpp"
#include "primitives/constant_time.hpp"
#include <openssl/evp.h>
#include <openssl/aes.h>
#include <sodium.h>
#include <cstring>
#include <vector>
#include <mutex>

namespace enchant {
namespace primitives {

static bool g_aes_initialized = false;
static bool g_aes_available = false;
static std::mutex g_aes_mutex;

int aes_init(void) {
    std::lock_guard<std::mutex> lock(g_aes_mutex);

    if (g_aes_initialized) {
        return g_aes_available ? ENCHANT_SUCCESS : ENCHANT_ERROR_NOT_IMPLEMENTED;
    }

    g_aes_initialized = true;

    const EVP_CIPHER* cipher = EVP_aes_256_gcm();
    if (!cipher) {
        g_aes_available = false;
        return ENCHANT_ERROR_NOT_IMPLEMENTED;
    }

    const EVP_CIPHER* cipher_ctr = EVP_aes_256_ctr();
    if (!cipher_ctr) {
        g_aes_available = false;
        return ENCHANT_ERROR_NOT_IMPLEMENTED;
    }

    const EVP_CIPHER* cipher_cbc = EVP_aes_256_cbc();
    if (!cipher_cbc) {
        g_aes_available = false;
        return ENCHANT_ERROR_NOT_IMPLEMENTED;
    }

    const EVP_CIPHER* cipher_ecb = EVP_aes_256_ecb();
    if (!cipher_ecb) {
        g_aes_available = false;
        return ENCHANT_ERROR_NOT_IMPLEMENTED;
    }

    g_aes_available = true;
    return ENCHANT_SUCCESS;
}

int aes_is_available(void) {
    std::lock_guard<std::mutex> lock(g_aes_mutex);
    return g_aes_initialized && g_aes_available ? 1 : 0;
}

int aes_128_keygen(uint8_t* key) {
    if (!key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    randombytes_buf(key, AES_128_KEY_SIZE);
    return ENCHANT_SUCCESS;
}

int aes_192_keygen(uint8_t* key) {
    if (!key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    randombytes_buf(key, AES_192_KEY_SIZE);
    return ENCHANT_SUCCESS;
}

int aes_256_keygen(uint8_t* key) {
    if (!key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    randombytes_buf(key, AES_256_KEY_SIZE);
    return ENCHANT_SUCCESS;
}

int aes_256_gcm_encrypt(const uint8_t* key,
                        const uint8_t* nonce,
                        const uint8_t* plaintext,
                        size_t plaintext_len,
                        const uint8_t* additional_data,
                        size_t additional_data_len,
                        uint8_t* ciphertext,
                        size_t* ciphertext_len) {
    if (!key || !nonce || !plaintext || !ciphertext || !ciphertext_len) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        return ENCHANT_ERROR_INTERNAL;
    }

    int rc = EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, AES_GCM_NONCE_SIZE, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_EncryptInit_ex(ctx, nullptr, nullptr, key, nonce);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    if (plaintext_len > static_cast<size_t>(INT_MAX)) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    int out_len = 0;
    int ciphertext_len_int = 0;

    if (additional_data && additional_data_len > 0) {
        rc = EVP_EncryptUpdate(ctx, nullptr, &out_len, additional_data, static_cast<int>(additional_data_len));
        if (rc != 1) {
            EVP_CIPHER_CTX_free(ctx);
            return ENCHANT_ERROR_INTERNAL;
        }
    }

    rc = EVP_EncryptUpdate(ctx, ciphertext, &out_len, plaintext, static_cast<int>(plaintext_len));
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(ciphertext, 0, out_len > 0 ? out_len : 0);
        return ENCHANT_ERROR_INTERNAL;
    }
    ciphertext_len_int = out_len;

    rc = EVP_EncryptFinal_ex(ctx, ciphertext + out_len, &out_len);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(ciphertext, 0, ciphertext_len_int > 0 ? ciphertext_len_int : 0);
        return ENCHANT_ERROR_INTERNAL;
    }
    ciphertext_len_int += out_len;

    rc = EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, AES_GCM_TAG_SIZE, ciphertext + ciphertext_len_int);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(ciphertext, 0, ciphertext_len_int > 0 ? ciphertext_len_int : 0);
        return ENCHANT_ERROR_INTERNAL;
    }
    ciphertext_len_int += AES_GCM_TAG_SIZE;

    EVP_CIPHER_CTX_free(ctx);
    *ciphertext_len = static_cast<size_t>(ciphertext_len_int);
    return ENCHANT_SUCCESS;
}

int aes_256_gcm_decrypt(const uint8_t* key,
                        const uint8_t* nonce,
                        const uint8_t* ciphertext,
                        size_t ciphertext_len,
                        const uint8_t* additional_data,
                        size_t additional_data_len,
                        uint8_t* plaintext,
                        size_t* plaintext_len) {
    if (!key || !nonce || !ciphertext || !plaintext || !plaintext_len) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (ciphertext_len < AES_GCM_TAG_SIZE) {
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
    }

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        return ENCHANT_ERROR_INTERNAL;
    }

    int rc = EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, AES_GCM_NONCE_SIZE, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_DecryptInit_ex(ctx, nullptr, nullptr, key, nonce);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    size_t ciphertext_body_len = ciphertext_len - AES_GCM_TAG_SIZE;
    int out_len = 0;
    int plaintext_len_int = 0;

    if (additional_data && additional_data_len > 0) {
        rc = EVP_DecryptUpdate(ctx, nullptr, &out_len, additional_data, static_cast<int>(additional_data_len));
        if (rc != 1) {
            EVP_CIPHER_CTX_free(ctx);
            return ENCHANT_ERROR_INTERNAL;
        }
    }

    rc = EVP_DecryptUpdate(ctx, plaintext, &out_len, ciphertext, static_cast<int>(ciphertext_body_len));
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(plaintext, 0, ciphertext_body_len);
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }
    plaintext_len_int = out_len;

    rc = EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, AES_GCM_TAG_SIZE, const_cast<uint8_t*>(ciphertext + ciphertext_body_len));
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(plaintext, 0, ciphertext_body_len);
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }

    rc = EVP_DecryptFinal_ex(ctx, plaintext + out_len, &out_len);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(plaintext, 0, ciphertext_body_len);
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }
    plaintext_len_int += out_len;

    EVP_CIPHER_CTX_free(ctx);
    *plaintext_len = static_cast<size_t>(plaintext_len_int);
    return ENCHANT_SUCCESS;
}

int aes_256_gcm_encrypt_detached(const uint8_t* key,
                                 const uint8_t* nonce,
                                 const uint8_t* plaintext,
                                 size_t plaintext_len,
                                 const uint8_t* additional_data,
                                 size_t additional_data_len,
                                 uint8_t* ciphertext,
                                 size_t ciphertext_capacity,
                                 uint8_t* tag) {
    if (!key || !nonce || !plaintext || !ciphertext || !tag) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (plaintext_len > ciphertext_capacity) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    if (plaintext_len > static_cast<size_t>(INT_MAX) ||
        additional_data_len > static_cast<size_t>(INT_MAX)) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        return ENCHANT_ERROR_INTERNAL;
    }

    int rc = EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, AES_GCM_NONCE_SIZE, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_EncryptInit_ex(ctx, nullptr, nullptr, key, nonce);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    int out_len = 0;

    if (additional_data && additional_data_len > 0) {
        rc = EVP_EncryptUpdate(ctx, nullptr, &out_len, additional_data, static_cast<int>(additional_data_len));
        if (rc != 1) {
            EVP_CIPHER_CTX_free(ctx);
            return ENCHANT_ERROR_INTERNAL;
        }
    }

    rc = EVP_EncryptUpdate(ctx, ciphertext, &out_len, plaintext, static_cast<int>(plaintext_len));
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(ciphertext, 0, plaintext_len);
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_EncryptFinal_ex(ctx, ciphertext + out_len, &out_len);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(ciphertext, 0, plaintext_len);
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, AES_GCM_TAG_SIZE, tag);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(tag, 0, AES_GCM_TAG_SIZE);
        return ENCHANT_ERROR_INTERNAL;
    }

    EVP_CIPHER_CTX_free(ctx);
    return ENCHANT_SUCCESS;
}

int aes_256_gcm_decrypt_detached(const uint8_t* key,
                                 const uint8_t* nonce,
                                 const uint8_t* ciphertext,
                                 size_t ciphertext_len,
                                 const uint8_t* tag,
                                 const uint8_t* additional_data,
                                 size_t additional_data_len,
                                 uint8_t* plaintext,
                                 size_t plaintext_capacity) {
    if (!key || !nonce || !ciphertext || !tag || !plaintext) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (ciphertext_len > plaintext_capacity) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    if (ciphertext_len > static_cast<size_t>(INT_MAX) ||
        additional_data_len > static_cast<size_t>(INT_MAX)) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        return ENCHANT_ERROR_INTERNAL;
    }

    int rc = EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, AES_GCM_NONCE_SIZE, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_DecryptInit_ex(ctx, nullptr, nullptr, key, nonce);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    int out_len = 0;

    if (additional_data && additional_data_len > 0) {
        rc = EVP_DecryptUpdate(ctx, nullptr, &out_len, additional_data, static_cast<int>(additional_data_len));
        if (rc != 1) {
            EVP_CIPHER_CTX_free(ctx);
            return ENCHANT_ERROR_INTERNAL;
        }
    }

    rc = EVP_DecryptUpdate(ctx, plaintext, &out_len, ciphertext, static_cast<int>(ciphertext_len));
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(plaintext, 0, ciphertext_len);
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }

    rc = EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, AES_GCM_TAG_SIZE, const_cast<uint8_t*>(tag));
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(plaintext, 0, ciphertext_len);
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }

    rc = EVP_DecryptFinal_ex(ctx, plaintext + out_len, &out_len);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(plaintext, 0, ciphertext_len);
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }

    EVP_CIPHER_CTX_free(ctx);
    return ENCHANT_SUCCESS;
}

int aes_256_ctr_encrypt(const uint8_t* key,
                        const uint8_t* nonce,
                        const uint8_t* plaintext,
                        size_t plaintext_len,
                        uint8_t* ciphertext) {
    if (!key || !nonce || !plaintext || !ciphertext) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        return ENCHANT_ERROR_INTERNAL;
    }

    int rc = EVP_EncryptInit_ex(ctx, EVP_aes_256_ctr(), nullptr, nullptr, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    uint8_t iv[AES_IV_SIZE] = {0};
    memcpy(iv, nonce, AES_GCM_NONCE_SIZE);

    rc = EVP_EncryptInit_ex(ctx, nullptr, nullptr, key, iv);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    int out_len = 0;
    rc = EVP_EncryptUpdate(ctx, ciphertext, &out_len, plaintext, static_cast<int>(plaintext_len));
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(ciphertext, 0, plaintext_len);
        return ENCHANT_ERROR_INTERNAL;
    }

    int final_len = 0;
    rc = EVP_EncryptFinal_ex(ctx, ciphertext + out_len, &final_len);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(ciphertext, 0, plaintext_len);
        return ENCHANT_ERROR_INTERNAL;
    }

    EVP_CIPHER_CTX_free(ctx);
    return ENCHANT_SUCCESS;
}

int aes_256_ctr_decrypt(const uint8_t* key,
                        const uint8_t* nonce,
                        const uint8_t* ciphertext,
                        size_t ciphertext_len,
                        uint8_t* plaintext) {
    return aes_256_ctr_encrypt(key, nonce, ciphertext, ciphertext_len, plaintext);
}

static size_t pkcs7_pad_length(size_t plaintext_len) {
    size_t pad = AES_BLOCK_SIZE - (plaintext_len % AES_BLOCK_SIZE);
    return pad == 0 ? AES_BLOCK_SIZE : pad;
}

int aes_256_cbc_encrypt(const uint8_t* key,
                        const uint8_t* iv,
                        const uint8_t* plaintext,
                        size_t plaintext_len,
                        uint8_t* ciphertext,
                        size_t* ciphertext_len) {
    if (!key || !iv || !plaintext || !ciphertext || !ciphertext_len) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    size_t pad_len = pkcs7_pad_length(plaintext_len);
    size_t total_len = plaintext_len + pad_len;

    if (*ciphertext_len < total_len) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    std::vector<uint8_t> padded_plaintext(plaintext_len + pad_len);
    memcpy(padded_plaintext.data(), plaintext, plaintext_len);
    uint8_t* pad = padded_plaintext.data() + plaintext_len;
    for (size_t i = 0; i < pad_len; i++) {
        pad[i] = static_cast<uint8_t>(pad_len);
    }

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        return ENCHANT_ERROR_INTERNAL;
    }

    int rc = EVP_EncryptInit_ex(ctx, EVP_aes_256_cbc(), nullptr, nullptr, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_EncryptInit_ex(ctx, nullptr, nullptr, key, iv);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    int out_len = 0;
    rc = EVP_EncryptUpdate(ctx, ciphertext, &out_len, padded_plaintext.data(), static_cast<int>(total_len));
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(ciphertext, 0, total_len);
        return ENCHANT_ERROR_INTERNAL;
    }

    int final_len = 0;
    rc = EVP_EncryptFinal_ex(ctx, ciphertext + out_len, &final_len);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(ciphertext, 0, total_len);
        return ENCHANT_ERROR_INTERNAL;
    }

    EVP_CIPHER_CTX_free(ctx);
    sodium_memzero(padded_plaintext.data(), padded_plaintext.size());
    *ciphertext_len = static_cast<size_t>(out_len + final_len);
    return ENCHANT_SUCCESS;
}

int aes_256_cbc_decrypt(const uint8_t* key,
                        const uint8_t* iv,
                        const uint8_t* ciphertext,
                        size_t ciphertext_len,
                        uint8_t* plaintext,
                        size_t* plaintext_len) {
    if (!key || !iv || !ciphertext || !plaintext || !plaintext_len) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (ciphertext_len == 0 || ciphertext_len % AES_BLOCK_SIZE != 0) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        return ENCHANT_ERROR_INTERNAL;
    }

    EVP_CIPHER_CTX_set_padding(ctx, 0);

    int rc = EVP_DecryptInit_ex(ctx, EVP_aes_256_cbc(), nullptr, nullptr, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_DecryptInit_ex(ctx, nullptr, nullptr, key, iv);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    int out_len = 0;
    rc = EVP_DecryptUpdate(ctx, plaintext, &out_len, ciphertext, static_cast<int>(ciphertext_len));
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(plaintext, 0, ciphertext_len);
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }

    int final_len = 0;
    rc = EVP_DecryptFinal_ex(ctx, plaintext + out_len, &final_len);
    EVP_CIPHER_CTX_free(ctx);
    if (rc != 1) {
        memset(plaintext, 0, ciphertext_len);
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }

    size_t total_len = static_cast<size_t>(out_len + final_len);

    if (total_len == 0 || total_len > ciphertext_len) {
        memset(plaintext, 0, ciphertext_len);
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }

    uint8_t pad_value = plaintext[total_len - 1];
    if (pad_value == 0 || pad_value > AES_BLOCK_SIZE || pad_value > total_len) {
        memset(plaintext, 0, ciphertext_len);
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }

    for (size_t i = total_len - pad_value; i < total_len; i++) {
        if (plaintext[i] != pad_value) {
            memset(plaintext, 0, ciphertext_len);
            return ENCHANT_ERROR_DECRYPTION_FAILED;
        }
    }

    *plaintext_len = total_len - pad_value;
    return ENCHANT_SUCCESS;
}

} // namespace primitives
} // namespace enchant