#ifndef ENCHANT_ZK_POKSHO_SHO_HMAC_SHA256_HPP
#define ENCHANT_ZK_POKSHO_SHO_HMAC_SHA256_HPP

#include <array>
#include <cstdint>
#include <cstring>
#include <sodium.h>
#include "sho.hpp"

namespace enchant {
namespace zk {
namespace enchant_zkp {

constexpr size_t SHA256_HASH_LEN = 32;
constexpr size_t SHA256_BLOCK_LEN = 64;

class ShoHmacSha256 : public ShoApi {
public:
    ShoHmacSha256() = default;

    explicit ShoHmacSha256(const uint8_t* label, size_t label_len) {
        init(label, label_len);
    }

    explicit ShoHmacSha256(const char* label) {
        init(reinterpret_cast<const uint8_t*>(label), std::strlen(label));
    }

    ShoHmacSha256(const ShoHmacSha256&) = default;
    ShoHmacSha256& operator=(const ShoHmacSha256&) = default;

    void absorb(const uint8_t* input, size_t len) override {
        if (mode_ == Mode::RATCHETED) {
            // Initialize HMAC with cv as key (no extra padding block)
            crypto_auth_hmacsha256_init(&hasher_, cv_.data(), SHA256_HASH_LEN);
            mode_ = Mode::ABSORBING;
        }
        if (input && len > 0) {
            crypto_auth_hmacsha256_update(&hasher_, input, len);
        }
    }

    void ratchet() override {
        if (mode_ == Mode::RATCHETED) {
            return;
        }
        // Absorb 0x00 terminator
        uint8_t zero = 0x00;
        crypto_auth_hmacsha256_update(&hasher_, &zero, 1);
        // Finalize to get cv
        crypto_auth_hmacsha256_final(&hasher_, cv_.data());
        mode_ = Mode::RATCHETED;
    }

    void absorb_and_ratchet(const uint8_t* input, size_t len) {
        absorb(input, len);
        ratchet();
    }

    void squeeze_and_ratchet_into(uint8_t* target, size_t outlen) override {
        // squeeze is only valid after ratchet
        size_t i = 0;
        size_t offset = 0;

        while (offset < outlen) {
            // Create output hasher from cv
            crypto_auth_hmacsha256_state output_hasher;
            crypto_auth_hmacsha256_init(&output_hasher, cv_.data(), SHA256_HASH_LEN);

            // Update with counter (big-endian)
            uint8_t counter_buf[8];
            std::memset(counter_buf, 0, 8);
            uint64_t counter = i;
            for (int j = 7; j >= 0; --j) {
                counter_buf[j] = static_cast<uint8_t>(counter & 0xFF);
                counter >>= 8;
            }
            crypto_auth_hmacsha256_update(&output_hasher, counter_buf, 8);

            // Update with 0x01
            uint8_t one = 0x01;
            crypto_auth_hmacsha256_update(&output_hasher, &one, 1);

            // Finalize
            uint8_t digest[SHA256_HASH_LEN];
            crypto_auth_hmacsha256_final(&output_hasher, digest);

            // Copy min(HASH_LEN, remaining) bytes
            size_t num_bytes = std::min(SHA256_HASH_LEN, outlen - offset);
            std::memcpy(target + offset, digest, num_bytes);
            sodium_memzero(digest, SHA256_HASH_LEN);
            offset += num_bytes;
            i++;
        }

        // Update cv for next squeeze
        crypto_auth_hmacsha256_state next_hasher;
        crypto_auth_hmacsha256_init(&next_hasher, cv_.data(), SHA256_HASH_LEN);

        // Update with outlen (big-endian)
        uint8_t outlen_buf[8];
        std::memset(outlen_buf, 0, 8);
        uint64_t outlen_val = outlen;
        for (int j = 7; j >= 0; --j) {
            outlen_buf[j] = static_cast<uint8_t>(outlen_val & 0xFF);
            outlen_val >>= 8;
        }
        crypto_auth_hmacsha256_update(&next_hasher, outlen_buf, 8);

        // Update with 0x02
        uint8_t two = 0x02;
        crypto_auth_hmacsha256_update(&next_hasher, &two, 1);

        // Finalize to new cv
        crypto_auth_hmacsha256_final(&next_hasher, cv_.data());
        mode_ = Mode::RATCHETED;
    }

private:
    enum class Mode { ABSORBING, RATCHETED };

    void init(const uint8_t* label, size_t label_len) {
        mode_ = Mode::RATCHETED;
        std::memset(cv_.data(), 0, SHA256_HASH_LEN);
        absorb_and_ratchet(label, label_len);
    }

    crypto_auth_hmacsha256_state hasher_;
    std::array<uint8_t, SHA256_HASH_LEN> cv_{};
    Mode mode_ = Mode::RATCHETED;
};

} // namespace enchant_zkp
} // namespace zk
} // namespace enchant

#endif
