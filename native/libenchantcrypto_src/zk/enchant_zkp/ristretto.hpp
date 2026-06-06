#ifndef ENCHANT_ZK_POKSHO_RISTRETTO_HPP
#define ENCHANT_ZK_POKSHO_RISTRETTO_HPP

#include <array>
#include <cstdint>
#include <cstring>
#include <optional>
#include <vector>
#include <sodium.h>
#include <sodium/crypto_core_ristretto255.h>
#include <sodium/crypto_scalarmult_ristretto255.h>
#include <sodium/crypto_hash_sha512.h>
#include "errors.hpp"

namespace enchant {
namespace zk {
namespace enchant_zkp {

constexpr size_t RISTRETTO_POINT_BYTES = 32;
constexpr size_t RISTRETTO_SCALAR_BYTES = 32;
constexpr size_t RISTRETTO_SCALAR_WIDE_BYTES = 64;

using RistrettoPointBytes = std::array<uint8_t, RISTRETTO_POINT_BYTES>;
using RistrettoScalarBytes = std::array<uint8_t, RISTRETTO_SCALAR_BYTES>;

inline int crypto_core_ristretto255_from_string_fallback(
    unsigned char* p,
    const unsigned char* ctx, size_t ctx_len,
    const unsigned char* msg, size_t msg_len);

class RistrettoScalar;

class RistrettoPoint {
public:
    RistrettoPoint() { std::memset(data_.data(), 0, RISTRETTO_POINT_BYTES); }

    [[nodiscard]] static RistrettoPoint identity() { return RistrettoPoint(); }

    [[nodiscard]] static std::optional<RistrettoPoint> from_bytes(const uint8_t* bytes) {
        if (!bytes) return std::nullopt;
        RistrettoPoint p;
        if (crypto_core_ristretto255_is_valid_point(bytes) != 1) {
            return std::nullopt;
        }
        std::memcpy(p.data_.data(), bytes, RISTRETTO_POINT_BYTES);
        return p;
    }

    [[nodiscard]] static RistrettoPoint from_bytes_unchecked(const uint8_t* bytes) {
        RistrettoPoint p;
        if (bytes) {
            std::memcpy(p.data_.data(), bytes, RISTRETTO_POINT_BYTES);
        }
        return p;
    }

    [[nodiscard]] static std::optional<RistrettoPoint> from_hash(const uint8_t* hash64, size_t hash_len = 64) {
        if (!hash64 || hash_len != 64) return std::nullopt;
        RistrettoPoint p;
        if (crypto_core_ristretto255_from_hash(p.data_.data(), hash64) != 0) {
            sodium_memzero(p.data_.data(), RISTRETTO_POINT_BYTES);
            return std::nullopt;
        }
        return p;
    }

    [[nodiscard]] static std::optional<RistrettoPoint> from_string(const uint8_t* msg, size_t msg_len,
                                       const uint8_t* ctx, size_t ctx_len) {
        RistrettoPoint p;
#if defined(crypto_core_ristretto255_H2CSHA256) && defined(crypto_core_ristretto255_from_string)
        if (crypto_core_ristretto255_from_string(p.data_.data(), ctx, ctx_len, msg, msg_len,
                                              crypto_core_ristretto255_H2CSHA256) != 0) {
            return std::nullopt;
        }
#else
        if (crypto_core_ristretto255_from_string_fallback(p.data_.data(), ctx, ctx_len, msg, msg_len) != 0) {
            return std::nullopt;
        }
#endif
        return p;
    }

    [[nodiscard]] static RistrettoPoint random() {
        RistrettoPoint p;
        crypto_core_ristretto255_random(p.data_.data());
        return p;
    }

    const uint8_t* data() const { return data_.data(); }
    uint8_t* mutable_data() { return data_.data(); }

    [[nodiscard]] RistrettoPoint operator+(const RistrettoPoint& other) const {
        RistrettoPoint result;
        crypto_core_ristretto255_add(result.data_.data(), data_.data(), other.data_.data());
        return result;
    }

    [[nodiscard]] RistrettoPoint operator-(const RistrettoPoint& other) const {
        RistrettoPoint result;
        crypto_core_ristretto255_sub(result.data_.data(), data_.data(), other.data_.data());
        return result;
    }

    [[nodiscard]] bool operator==(const RistrettoPoint& other) const {
        return sodium_memcmp(data_.data(), other.data_.data(), RISTRETTO_POINT_BYTES) == 0;
    }

    [[nodiscard]] bool operator!=(const RistrettoPoint& other) const {
        return !(*this == other);
    }

    [[nodiscard]] bool is_identity() const {
        for (size_t i = 0; i < RISTRETTO_POINT_BYTES; i++) {
            if (data_[i] != 0) return false;
        }
        return true;
    }

    [[nodiscard]] std::optional<RistrettoPoint> scalar_mul(const uint8_t* scalar) const {
        if (!scalar) return std::nullopt;
        // scalar_mul of identity is always identity; libsodium rejects the
        // identity encoding in crypto_scalarmult_ristretto255 (returns -1)
        // so handle it explicitly.
        if (is_identity()) return identity();
        // Zero scalar: 0 * P = identity for any P. Libsodium rejects zero scalar.
        bool all_zero = true;
        for (size_t i = 0; i < RISTRETTO_SCALAR_BYTES; i++) {
            if (scalar[i] != 0) { all_zero = false; break; }
        }
        if (all_zero) return identity();
        RistrettoPoint result;
        if (crypto_scalarmult_ristretto255(result.data_.data(), scalar, data_.data()) != 0) {
            sodium_memzero(result.data_.data(), RISTRETTO_POINT_BYTES);
            return std::nullopt;
        }
        return result;
    }

    RistrettoPointBytes compress() const { return data_; }

private:
    RistrettoPointBytes data_;
};

class RistrettoScalar {
public:
    RistrettoScalar() { std::memset(data_.data(), 0, RISTRETTO_SCALAR_BYTES); }

    static RistrettoScalar zero() { return RistrettoScalar(); }

    static RistrettoScalar one() {
        RistrettoScalar s;
        std::memset(s.data_.data(), 0, RISTRETTO_SCALAR_BYTES);
        s.data_[0] = 1;
        return s;
    }

    static RistrettoScalar from_bytes(const uint8_t* bytes) {
        RistrettoScalar s;
        if (bytes) {
            std::memcpy(s.data_.data(), bytes, RISTRETTO_SCALAR_BYTES);
        }
        return s;
    }

    static RistrettoScalar from_bytes_mod_order(const uint8_t* bytes) {
        RistrettoScalar s;
        if (bytes) {
            crypto_core_ristretto255_scalar_reduce(s.data_.data(), bytes);
        }
        return s;
    }

    static RistrettoScalar from_bytes_mod_order_wide(const uint8_t* bytes64) {
        RistrettoScalar s;
        if (bytes64) {
            crypto_core_ristretto255_scalar_reduce(s.data_.data(), bytes64);
        }
        return s;
    }

    static RistrettoScalar random() {
        RistrettoScalar s;
        crypto_core_ristretto255_scalar_random(s.data_.data());
        return s;
    }

    const uint8_t* data() const { return data_.data(); }

    RistrettoScalar operator+(const RistrettoScalar& other) const {
        RistrettoScalar result;
        crypto_core_ristretto255_scalar_add(result.data_.data(), data_.data(), other.data_.data());
        return result;
    }

    RistrettoScalar operator-(const RistrettoScalar& other) const {
        RistrettoScalar result;
        crypto_core_ristretto255_scalar_sub(result.data_.data(), data_.data(), other.data_.data());
        return result;
    }

    RistrettoScalar negate() const {
        RistrettoScalar result;
        crypto_core_ristretto255_scalar_negate(result.data_.data(), data_.data());
        return result;
    }

    RistrettoScalar operator*(const RistrettoScalar& other) const {
        RistrettoScalar result;
        crypto_core_ristretto255_scalar_mul(result.data_.data(), data_.data(), other.data_.data());
        return result;
    }

    std::optional<RistrettoScalar> invert() const {
        RistrettoScalar result;
        if (crypto_core_ristretto255_scalar_invert(result.data_.data(), data_.data()) != 0) {
            return std::nullopt;
        }
        return result;
    }

    bool operator==(const RistrettoScalar& other) const {
        return sodium_memcmp(data_.data(), other.data_.data(), RISTRETTO_SCALAR_BYTES) == 0;
    }

    bool operator!=(const RistrettoScalar& other) const {
        return !(*this == other);
    }

    [[nodiscard]] std::optional<RistrettoPoint> scalar_mul_point(const RistrettoPoint& point) const {
        return point.scalar_mul(data_.data());
    }

private:
    RistrettoScalarBytes data_;
};

using G1 = std::vector<RistrettoScalar>;
using G2 = std::vector<RistrettoPoint>;

inline int crypto_core_ristretto255_from_string_fallback(
    unsigned char* p,
    const unsigned char* ctx, size_t ctx_len,
    const unsigned char* msg, size_t msg_len) {
    crypto_hash_sha512_state state;
    crypto_hash_sha512_init(&state);

    uint8_t ctx_len_le[8];
    uint64_t ctx_len_val = static_cast<uint64_t>(ctx_len);
    for (int i = 0; i < 8; ++i) {
        ctx_len_le[i] = static_cast<uint8_t>((ctx_len_val >> (8 * i)) & 0xFF);
    }
    crypto_hash_sha512_update(&state, ctx_len_le, sizeof(ctx_len_le));
    if (ctx_len > 0 && ctx != nullptr) {
        crypto_hash_sha512_update(&state, ctx, ctx_len);
    }
    if (msg_len > 0 && msg != nullptr) {
        crypto_hash_sha512_update(&state, msg, msg_len);
    }

    uint8_t hash[64];
    crypto_hash_sha512_final(&state, hash);
    sodium_memzero(&state, sizeof(state));

    int rc = crypto_core_ristretto255_from_hash(p, hash);
    sodium_memzero(hash, sizeof(hash));
    return rc;
}

} // namespace enchant_zkp
} // namespace zk
} // namespace enchant

#endif