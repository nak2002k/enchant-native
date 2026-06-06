#include "enchant/protocol/fingerprint.hpp"
#include "enchant/protocol/constants.hpp"
#include "primitives/hash.hpp"
#include "primitives/constant_time.hpp"
#include <cstring>
#include <sodium.h>

namespace enchant {
namespace protocol {

FingerprintGenerator::FingerprintGenerator(uint32_t iterations)
    : iterations_(iterations) {}

namespace {

void write_u16_be(uint8_t* out, uint16_t v) {
    out[0] = static_cast<uint8_t>((v >> 8) & 0xFF);
    out[1] = static_cast<uint8_t>(v & 0xFF);
}

void write_u32_be(uint8_t* out, uint32_t v) {
    out[0] = static_cast<uint8_t>((v >> 24) & 0xFF);
    out[1] = static_cast<uint8_t>((v >> 16) & 0xFF);
    out[2] = static_cast<uint8_t>((v >> 8) & 0xFF);
    out[3] = static_cast<uint8_t>(v & 0xFF);
}

} // namespace

std::vector<uint8_t> FingerprintGenerator::compute_fingerprint(
    const std::vector<uint8_t>& local_id,
    const secure::SecureBuffer& local_key,
    const std::vector<uint8_t>& remote_id,
    const secure::SecureBuffer& remote_key
) const {
    if (local_key.size() != 32 || remote_key.size() != 32) {
        return {};
    }

    std::vector<uint8_t> stable_in;
    stable_in.reserve(local_id.size() + 32 + remote_id.size() + 32);
    stable_in.insert(stable_in.end(), local_id.begin(), local_id.end());
    stable_in.insert(stable_in.end(), local_key.data(), local_key.data() + 32);
    stable_in.insert(stable_in.end(), remote_id.begin(), remote_id.end());
    stable_in.insert(stable_in.end(), remote_key.data(), remote_key.data() + 32);

    uint8_t stable_id[primitives::SHA512_SIZE];
    int rc = primitives::sha512(stable_in.data(), stable_in.size(), stable_id);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(stable_id, sizeof(stable_id));
        return {};
    }

    std::vector<uint8_t> result(primitives::SHA512_SIZE, 0);
    uint8_t hash[primitives::SHA512_SIZE];

    for (uint32_t i = 0; i < iterations_; ++i) {
        std::vector<uint8_t> input;
        input.reserve(2 + 4 + 64);

        uint8_t version_bytes[2];
        write_u16_be(version_bytes, static_cast<uint16_t>(FINGERPRINT_VERSION));
        input.insert(input.end(), version_bytes, version_bytes + 2);

        uint8_t iter_bytes[4];
        write_u32_be(iter_bytes, i);
        input.insert(input.end(), iter_bytes, iter_bytes + 4);

        if (i % 2 == 0) {
            input.insert(input.end(), local_key.data(), local_key.data() + 32);
        } else {
            input.insert(input.end(), stable_id, stable_id + primitives::SHA512_SIZE);
        }

        rc = primitives::sha512(input.data(), input.size(), hash);
        if (rc != ENCHANT_SUCCESS) {
            sodium_memzero(stable_id, sizeof(stable_id));
            sodium_memzero(hash, sizeof(hash));
            return {};
        }

        for (size_t j = 0; j < primitives::SHA512_SIZE; ++j) {
            result[j] ^= hash[j];
        }
    }

    sodium_memzero(stable_id, sizeof(stable_id));
    sodium_memzero(hash, sizeof(hash));

    std::vector<uint8_t> fingerprint(result.begin(), result.begin() + FINGERPRINT_RAW_BYTES);
    sodium_memzero(result.data(), result.size());
    return fingerprint;
}

std::vector<uint8_t> FingerprintGenerator::compute_scannable_fingerprint(
    const std::vector<uint8_t>& local_id,
    const secure::SecureBuffer& local_key,
    const std::vector<uint8_t>& remote_id,
    const secure::SecureBuffer& remote_key
) const {
    std::vector<uint8_t> scannable;
    scannable.reserve(FINGERPRINT_SCANNABLE_BYTES * 2 + 2);

    uint8_t version_bytes[2];
    write_u16_be(version_bytes, static_cast<uint16_t>(FINGERPRINT_VERSION));
    scannable.insert(scannable.end(), version_bytes, version_bytes + 2);

    auto local_fp = compute_fingerprint(local_id, local_key, remote_id, remote_key);
    if (local_fp.size() != FINGERPRINT_RAW_BYTES) {
        sodium_memzero(local_fp.data(), local_fp.size());
        return {};
    }
    scannable.insert(scannable.end(), local_fp.begin(), local_fp.end());
    sodium_memzero(local_fp.data(), local_fp.size());

    auto remote_fp = compute_fingerprint(remote_id, remote_key, local_id, local_key);
    if (remote_fp.size() != FINGERPRINT_RAW_BYTES) {
        sodium_memzero(remote_fp.data(), remote_fp.size());
        return {};
    }
    scannable.insert(scannable.end(), remote_fp.begin(), remote_fp.end());
    sodium_memzero(remote_fp.data(), remote_fp.size());

    return scannable;
}

bool FingerprintGenerator::compare_scannable_fingerprint(
    const std::vector<uint8_t>& scannable,
    const std::vector<uint8_t>& local_id,
    const secure::SecureBuffer& local_key,
    const std::vector<uint8_t>& remote_id,
    const secure::SecureBuffer& remote_key
) const {
    if (scannable.size() != 2 + 2 * FINGERPRINT_RAW_BYTES) {
        return false;
    }

    uint16_t version = (static_cast<uint16_t>(scannable[0]) << 8) | scannable[1];
    if (version != FINGERPRINT_VERSION) {
        return false;
    }

    auto expected = compute_scannable_fingerprint(local_id, local_key, remote_id, remote_key);
    if (expected.size() != scannable.size()) {
        sodium_memzero(expected.data(), expected.size());
        return false;
    }

    bool eq = primitives::constant_time_eq(scannable.data() + 2, expected.data() + 2, scannable.size() - 2);
    sodium_memzero(expected.data(), expected.size());
    return eq;
}

std::string FingerprintGenerator::format_displayable(const std::vector<uint8_t>& fingerprint) const {
    if (fingerprint.size() < FINGERPRINT_RAW_BYTES) {
        return std::string();
    }

    std::string out;
    out.reserve(FINGERPRINT_DISPLAY_DIGITS + (FINGERPRINT_DISPLAY_DIGITS / 5));

    for (size_t i = 0; i < FINGERPRINT_RAW_BYTES; ++i) {
        uint32_t v = static_cast<uint32_t>(fingerprint[i]);
        uint32_t hi = (v * 100) / 256;
        if (hi > 99) hi = 99;

        out.push_back('0' + static_cast<char>(hi / 10));
        out.push_back('0' + static_cast<char>(hi % 10));

        if (i < FINGERPRINT_RAW_BYTES - 1 && (i + 1) % 5 == 0) {
            out.push_back(' ');
        }
    }

    return out;
}

std::string FingerprintGenerator::compute_displayable_fingerprint(
    const std::vector<uint8_t>& local_id,
    const secure::SecureBuffer& local_key,
    const std::vector<uint8_t>& remote_id,
    const secure::SecureBuffer& remote_key
) const {
    auto fp = compute_fingerprint(local_id, local_key, remote_id, remote_key);
    return format_displayable(fp);
}

} // namespace protocol
} // namespace enchant
