#include "primitives/aes.hpp"
#include "primitives/constant_time.hpp"
#include <openssl/evp.h>
#include <openssl/aes.h>
#include <sodium.h>
#include <cstring>
#include <vector>

namespace enchant {
namespace primitives {

static int aes_ecb_encrypt_block(const uint8_t* key, const uint8_t* in, uint8_t* out) {
    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        return ENCHANT_ERROR_INTERNAL;
    }

    int rc = EVP_EncryptInit_ex(ctx, EVP_aes_256_ecb(), nullptr, key, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    EVP_CIPHER_CTX_set_padding(ctx, 0);

    int out_len = 0;
    rc = EVP_EncryptUpdate(ctx, out, &out_len, in, AES_BLOCK_SIZE);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    int final_len = 0;
    rc = EVP_EncryptFinal_ex(ctx, out + out_len, &final_len);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    EVP_CIPHER_CTX_free(ctx);
    return ENCHANT_SUCCESS;
}

static void gf_multiply_block(const uint8_t* x, const uint8_t* y, uint8_t* out) {
    uint8_t z[16] = {0};
    uint8_t v[16];
    memcpy(v, y, 16);

    for (int i = 0; i < 128; i++) {
        int byte_idx = i / 8;
        int bit_idx = 7 - (i % 8);
        if ((x[byte_idx] >> bit_idx) & 1) {
            for (int j = 0; j < 16; j++) {
                z[j] ^= v[j];
            }
        }

        int carry = v[15] & 1;
        for (int j = 15; j > 0; j--) {
            v[j] = (v[j] >> 1) | ((v[j-1] & 1) << 7);
        }
        v[0] >>= 1;

        if (carry) {
            v[0] ^= 0xe1;
        }
    }

    memcpy(out, z, 16);
}

static void polyval(const uint8_t* h, const uint8_t* data, size_t len, uint8_t* out) {
    uint8_t s[16] = {0};
    uint8_t x[16];

    size_t pos = 0;
    while (pos + 16 <= len) {
        for (int i = 0; i < 16; i++) {
            x[i] = s[i] ^ data[pos + i];
        }
        gf_multiply_block(x, h, s);
        pos += 16;
    }

    if (pos < len) {
        size_t remaining = len - pos;
        uint8_t buf[16] = {0};
        for (size_t i = 0; i < remaining; i++) {
            buf[i] = data[pos + i];
        }
        for (int i = 0; i < 16; i++) {
            x[i] = s[i] ^ buf[i];
        }
        gf_multiply_block(x, h, s);
    }

    memcpy(out, s, 16);
}

int aes_256_gcm_siv_encrypt(const uint8_t* key,
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

    int init_rc = aes_init();
    if (init_rc != ENCHANT_SUCCESS) {
        return init_rc;
    }

    uint8_t auth_key[AES_256_KEY_SIZE];
    uint8_t enc_key[AES_256_KEY_SIZE];
    uint8_t h[16];
    uint8_t tmp[16];
    uint8_t nonce_cleared[12];

    memcpy(nonce_cleared, nonce, 12);
    for (int i = 0; i < 12; i++) {
        nonce_cleared[i] ^= 0xff;
    }

    uint8_t counter[16] = {0};
    memcpy(counter, nonce_cleared, 12);
    counter[15] = 0x00;
    int rc = aes_ecb_encrypt_block(key, counter, tmp);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(tmp, sizeof(tmp));
        sodium_memzero(nonce_cleared, sizeof(nonce_cleared));
        return rc;
    }
    for (int i = 0; i < 8; i++) {
        auth_key[i] = tmp[i];
    }
    for (int i = 0; i < 8; i++) {
        enc_key[i] = tmp[8 + i];
    }

    counter[15] = 0x01;
    rc = aes_ecb_encrypt_block(key, counter, tmp);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(tmp, sizeof(tmp));
        sodium_memzero(nonce_cleared, sizeof(nonce_cleared));
        return rc;
    }
    for (int i = 0; i < 8; i++) {
        auth_key[8 + i] = tmp[i];
    }
    for (int i = 0; i < 8; i++) {
        enc_key[8 + i] = tmp[8 + i];
    }

    counter[15] = 0x02;
    rc = aes_ecb_encrypt_block(key, counter, tmp);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(tmp, sizeof(tmp));
        sodium_memzero(nonce_cleared, sizeof(nonce_cleared));
        return rc;
    }
    for (int i = 0; i < 8; i++) {
        auth_key[16 + i] = tmp[i];
    }
    for (int i = 0; i < 8; i++) {
        enc_key[16 + i] = tmp[8 + i];
    }

    counter[15] = 0x03;
    rc = aes_ecb_encrypt_block(key, counter, tmp);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(tmp, sizeof(tmp));
        sodium_memzero(nonce_cleared, sizeof(nonce_cleared));
        return rc;
    }
    for (int i = 0; i < 8; i++) {
        auth_key[24 + i] = tmp[i];
    }
    for (int i = 0; i < 8; i++) {
        enc_key[24 + i] = tmp[8 + i];
    }

    counter[15] = 0x04;
    rc = aes_ecb_encrypt_block(key, counter, h);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(tmp, sizeof(tmp));
        sodium_memzero(nonce_cleared, sizeof(nonce_cleared));
        sodium_memzero(h, sizeof(h));
        return rc;
    }

    for (int i = 0; i < 8; i++) {
        h[i] &= 0x0f;
    }
    h[8] &= 0x0f;

    std::vector<uint8_t> auth_input;

    if (additional_data && additional_data_len > 0) {
        size_t aad_padded_len = ((additional_data_len + 15) / 16) * 16;
        size_t pad_bytes = aad_padded_len - additional_data_len;
        size_t orig_size = auth_input.size();
        auth_input.resize(orig_size + aad_padded_len);
        memcpy(auth_input.data() + orig_size, additional_data, additional_data_len);
        for (size_t i = 0; i < pad_bytes; i++) {
            auth_input[orig_size + additional_data_len + i] = 0;
        }
    }

    if (plaintext_len > 0) {
        size_t pt_padded_len = ((plaintext_len + 15) / 16) * 16;
        size_t pad_bytes = pt_padded_len - plaintext_len;
        size_t orig_size = auth_input.size();
        auth_input.resize(orig_size + pt_padded_len);
        memcpy(auth_input.data() + orig_size, plaintext, plaintext_len);
        for (size_t i = 0; i < pad_bytes; i++) {
            auth_input[orig_size + plaintext_len + i] = 0;
        }
    }

    uint8_t len_block[16] = {0};
    for (int i = 0; i < 8; i++) {
        len_block[i] = (uint8_t)(additional_data_len * 8 >> (56 - 8 * i));
    }
    for (int i = 0; i < 8; i++) {
        len_block[8 + i] = (uint8_t)(plaintext_len * 8 >> (56 - 8 * i));
    }

    size_t orig_size = auth_input.size();
    auth_input.resize(orig_size + 16);
    memcpy(auth_input.data() + orig_size, len_block, 16);

    uint8_t s_val[16] = {0};
    if (!auth_input.empty()) {
        polyval(h, auth_input.data(), auth_input.size(), s_val);
    }

    for (int i = 0; i < 12; i++) {
        s_val[i] ^= nonce_cleared[i];
    }

    uint8_t tag[16];
    memcpy(tag, s_val, 12);
    tag[12] = 0x00;
    tag[13] = 0x00;
    tag[14] = 0x00;
    tag[15] = 0x01;

    uint8_t keystream_block[16];
    rc = aes_ecb_encrypt_block(enc_key, tag, keystream_block);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(h, sizeof(h));
        sodium_memzero(s_val, sizeof(s_val));
        sodium_memzero(tag, sizeof(tag));
        sodium_memzero(keystream_block, sizeof(keystream_block));
        return rc;
    }
    memcpy(tag, keystream_block, 16);

    uint8_t initial_counter[16] = {0};
    memcpy(initial_counter, tag, 12);
    initial_counter[12] = 0x00;
    initial_counter[13] = 0x00;
    initial_counter[14] = 0x00;
    initial_counter[15] = 0x02;

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(h, sizeof(h));
        sodium_memzero(s_val, sizeof(s_val));
        sodium_memzero(tag, sizeof(tag));
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_EncryptInit_ex(ctx, EVP_aes_256_ctr(), nullptr, nullptr, initial_counter);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(h, sizeof(h));
        sodium_memzero(s_val, sizeof(s_val));
        sodium_memzero(tag, sizeof(tag));
        return ENCHANT_ERROR_INTERNAL;
    }

    rc = EVP_EncryptInit_ex(ctx, nullptr, nullptr, enc_key, nullptr);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(h, sizeof(h));
        sodium_memzero(s_val, sizeof(s_val));
        sodium_memzero(tag, sizeof(tag));
        return ENCHANT_ERROR_INTERNAL;
    }

    int out_len = 0;
    rc = EVP_EncryptUpdate(ctx, ciphertext, &out_len, plaintext, static_cast<int>(plaintext_len));
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(ciphertext, 0, plaintext_len);
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(h, sizeof(h));
        sodium_memzero(s_val, sizeof(s_val));
        sodium_memzero(tag, sizeof(tag));
        return ENCHANT_ERROR_INTERNAL;
    }

    int final_len = 0;
    rc = EVP_EncryptFinal_ex(ctx, ciphertext + out_len, &final_len);
    if (rc != 1) {
        EVP_CIPHER_CTX_free(ctx);
        memset(ciphertext, 0, plaintext_len);
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(h, sizeof(h));
        sodium_memzero(s_val, sizeof(s_val));
        sodium_memzero(tag, sizeof(tag));
        return ENCHANT_ERROR_INTERNAL;
    }

    EVP_CIPHER_CTX_free(ctx);

    memcpy(ciphertext + plaintext_len, tag, 16);

    sodium_memzero(auth_key, sizeof(auth_key));
    sodium_memzero(enc_key, sizeof(enc_key));
    sodium_memzero(h, sizeof(h));
    sodium_memzero(s_val, sizeof(s_val));
    sodium_memzero(tag, sizeof(tag));
    sodium_memzero(keystream_block, sizeof(keystream_block));
    sodium_memzero(initial_counter, sizeof(initial_counter));

    *ciphertext_len = plaintext_len + 16;
    return ENCHANT_SUCCESS;
}

int aes_256_gcm_siv_decrypt(const uint8_t* key,
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

    int init_rc = aes_init();
    if (init_rc != ENCHANT_SUCCESS) {
        return init_rc;
    }

    if (ciphertext_len < 16) {
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
    }

    size_t actual_ciphertext_len = ciphertext_len - 16;
    size_t actual_plaintext_len = actual_ciphertext_len;

    if (actual_ciphertext_len > 0) {
        if (ciphertext == plaintext) {
            return ENCHANT_ERROR_INTERNAL;
        }
    }

    uint8_t auth_key[AES_256_KEY_SIZE];
    uint8_t enc_key[AES_256_KEY_SIZE];
    uint8_t h[16];
    uint8_t tmp[16];
    uint8_t nonce_cleared[12];

    memcpy(nonce_cleared, nonce, 12);
    for (int i = 0; i < 12; i++) {
        nonce_cleared[i] ^= 0xff;
    }

    uint8_t counter[16] = {0};
    memcpy(counter, nonce_cleared, 12);
    counter[15] = 0x00;
    int rc = aes_ecb_encrypt_block(key, counter, tmp);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(tmp, sizeof(tmp));
        sodium_memzero(nonce_cleared, sizeof(nonce_cleared));
        return rc;
    }
    for (int i = 0; i < 8; i++) {
        auth_key[i] = tmp[i];
    }
    for (int i = 0; i < 8; i++) {
        enc_key[i] = tmp[8 + i];
    }

    counter[15] = 0x01;
    rc = aes_ecb_encrypt_block(key, counter, tmp);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(tmp, sizeof(tmp));
        sodium_memzero(nonce_cleared, sizeof(nonce_cleared));
        return rc;
    }
    for (int i = 0; i < 8; i++) {
        auth_key[8 + i] = tmp[i];
    }
    for (int i = 0; i < 8; i++) {
        enc_key[8 + i] = tmp[8 + i];
    }

    counter[15] = 0x02;
    rc = aes_ecb_encrypt_block(key, counter, tmp);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(tmp, sizeof(tmp));
        sodium_memzero(nonce_cleared, sizeof(nonce_cleared));
        return rc;
    }
    for (int i = 0; i < 8; i++) {
        auth_key[16 + i] = tmp[i];
    }
    for (int i = 0; i < 8; i++) {
        enc_key[16 + i] = tmp[8 + i];
    }

    counter[15] = 0x03;
    rc = aes_ecb_encrypt_block(key, counter, tmp);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(tmp, sizeof(tmp));
        sodium_memzero(nonce_cleared, sizeof(nonce_cleared));
        return rc;
    }
    for (int i = 0; i < 8; i++) {
        auth_key[24 + i] = tmp[i];
    }
    for (int i = 0; i < 8; i++) {
        enc_key[24 + i] = tmp[8 + i];
    }

    counter[15] = 0x04;
    rc = aes_ecb_encrypt_block(key, counter, h);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(tmp, sizeof(tmp));
        sodium_memzero(nonce_cleared, sizeof(nonce_cleared));
        sodium_memzero(h, sizeof(h));
        return rc;
    }

    for (int i = 0; i < 8; i++) {
        h[i] &= 0x0f;
    }
    h[8] &= 0x0f;

    uint8_t received_tag[16];
    memcpy(received_tag, ciphertext + actual_ciphertext_len, 16);

    std::vector<uint8_t> temp_plaintext(actual_ciphertext_len);
    if (actual_ciphertext_len > 0) {
        uint8_t initial_counter[16] = {0};
        memcpy(initial_counter, received_tag, 12);
        initial_counter[12] = 0x00;
        initial_counter[13] = 0x00;
        initial_counter[14] = 0x00;
        initial_counter[15] = 0x02;

        EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
        if (!ctx) {
            sodium_memzero(auth_key, sizeof(auth_key));
            sodium_memzero(enc_key, sizeof(enc_key));
            sodium_memzero(h, sizeof(h));
            return ENCHANT_ERROR_INTERNAL;
        }

        rc = EVP_DecryptInit_ex(ctx, EVP_aes_256_ctr(), nullptr, nullptr, initial_counter);
        if (rc != 1) {
            EVP_CIPHER_CTX_free(ctx);
            sodium_memzero(auth_key, sizeof(auth_key));
            sodium_memzero(enc_key, sizeof(enc_key));
            sodium_memzero(h, sizeof(h));
            return ENCHANT_ERROR_INTERNAL;
        }

        rc = EVP_DecryptInit_ex(ctx, nullptr, nullptr, enc_key, nullptr);
        if (rc != 1) {
            EVP_CIPHER_CTX_free(ctx);
            sodium_memzero(auth_key, sizeof(auth_key));
            sodium_memzero(enc_key, sizeof(enc_key));
            sodium_memzero(h, sizeof(h));
            return ENCHANT_ERROR_INTERNAL;
        }

        int out_len = 0;
        rc = EVP_DecryptUpdate(ctx, temp_plaintext.data(), &out_len, ciphertext, static_cast<int>(actual_ciphertext_len));
        if (rc != 1) {
            EVP_CIPHER_CTX_free(ctx);
            memset(temp_plaintext.data(), 0, actual_ciphertext_len);
            sodium_memzero(auth_key, sizeof(auth_key));
            sodium_memzero(enc_key, sizeof(enc_key));
            sodium_memzero(h, sizeof(h));
            return ENCHANT_ERROR_DECRYPTION_FAILED;
        }

        int final_len = 0;
        rc = EVP_DecryptFinal_ex(ctx, temp_plaintext.data() + out_len, &final_len);
        if (rc != 1) {
            EVP_CIPHER_CTX_free(ctx);
            memset(temp_plaintext.data(), 0, actual_ciphertext_len);
            sodium_memzero(auth_key, sizeof(auth_key));
            sodium_memzero(enc_key, sizeof(enc_key));
            sodium_memzero(h, sizeof(h));
            return ENCHANT_ERROR_DECRYPTION_FAILED;
        }

        EVP_CIPHER_CTX_free(ctx);
        sodium_memzero(initial_counter, sizeof(initial_counter));
    }

    std::vector<uint8_t> auth_input;

    if (additional_data && additional_data_len > 0) {
        size_t aad_padded_len = ((additional_data_len + 15) / 16) * 16;
        size_t pad_bytes = aad_padded_len - additional_data_len;
        size_t orig_size = auth_input.size();
        auth_input.resize(orig_size + aad_padded_len);
        memcpy(auth_input.data() + orig_size, additional_data, additional_data_len);
        for (size_t i = 0; i < pad_bytes; i++) {
            auth_input[orig_size + additional_data_len + i] = 0;
        }
    }

    if (actual_plaintext_len > 0) {
        size_t pt_padded_len = ((actual_plaintext_len + 15) / 16) * 16;
        size_t pad_bytes = pt_padded_len - actual_plaintext_len;
        size_t orig_size = auth_input.size();
        auth_input.resize(orig_size + pt_padded_len);
        memcpy(auth_input.data() + orig_size, temp_plaintext.data(), actual_plaintext_len);
        for (size_t i = 0; i < pad_bytes; i++) {
            auth_input[orig_size + actual_plaintext_len + i] = 0;
        }
    }

    uint8_t len_block[16] = {0};
    for (int i = 0; i < 8; i++) {
        len_block[i] = (uint8_t)(additional_data_len * 8 >> (56 - 8 * i));
    }
    for (int i = 0; i < 8; i++) {
        len_block[8 + i] = (uint8_t)(actual_plaintext_len * 8 >> (56 - 8 * i));
    }

    size_t orig_size = auth_input.size();
    auth_input.resize(orig_size + 16);
    memcpy(auth_input.data() + orig_size, len_block, 16);

    uint8_t s_val[16] = {0};
    if (!auth_input.empty()) {
        polyval(h, auth_input.data(), auth_input.size(), s_val);
    }

    for (int i = 0; i < 12; i++) {
        s_val[i] ^= nonce_cleared[i];
    }

    uint8_t expected_tag_input[16];
    memcpy(expected_tag_input, s_val, 12);
    expected_tag_input[12] = 0x00;
    expected_tag_input[13] = 0x00;
    expected_tag_input[14] = 0x00;
    expected_tag_input[15] = 0x01;

    uint8_t expected_tag[16];
    rc = aes_ecb_encrypt_block(enc_key, expected_tag_input, expected_tag);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(auth_key, sizeof(auth_key));
        sodium_memzero(enc_key, sizeof(enc_key));
        sodium_memzero(h, sizeof(h));
        sodium_memzero(s_val, sizeof(s_val));
        sodium_memzero(temp_plaintext.data(), temp_plaintext.size());
        return rc;
    }

    int auth_ok = sodium_memcmp(expected_tag, received_tag, 16) == 0 ? 1 : 0;

    sodium_memzero(auth_key, sizeof(auth_key));
    sodium_memzero(enc_key, sizeof(enc_key));
    sodium_memzero(h, sizeof(h));
    sodium_memzero(s_val, sizeof(s_val));
    sodium_memzero(expected_tag, sizeof(expected_tag));
    sodium_memzero(expected_tag_input, sizeof(expected_tag_input));

    if (auth_ok != 1) {
        memset(plaintext, 0, actual_ciphertext_len);
        memset(temp_plaintext.data(), 0, actual_ciphertext_len);
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }

    if (actual_ciphertext_len > 0) {
        memcpy(plaintext, temp_plaintext.data(), actual_ciphertext_len);
    }
    sodium_memzero(temp_plaintext.data(), temp_plaintext.size());

    *plaintext_len = actual_ciphertext_len;
    return ENCHANT_SUCCESS;
}

} // namespace primitives
} // namespace enchant
