#ifndef ENCHANT_PROTOCOL_FINGERPRINT_HPP
#define ENCHANT_PROTOCOL_FINGERPRINT_HPP

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include "secure/buffer.hpp"

namespace enchant {
namespace protocol {

constexpr size_t FINGERPRINT_DISPLAY_DIGITS = 60;
constexpr size_t FINGERPRINT_RAW_BYTES = 30;
constexpr size_t FINGERPRINT_SCANNABLE_BYTES = 32;
constexpr uint32_t FINGERPRINT_VERSION = 0;
constexpr uint32_t FINGERPRINT_DEFAULT_ITERATIONS = 5200;

class FingerprintGenerator {
public:
    explicit FingerprintGenerator(uint32_t iterations = FINGERPRINT_DEFAULT_ITERATIONS);

    std::vector<uint8_t> compute_fingerprint(
        const std::vector<uint8_t>& local_id,
        const secure::SecureBuffer& local_key,
        const std::vector<uint8_t>& remote_id,
        const secure::SecureBuffer& remote_key
    ) const;

    std::vector<uint8_t> compute_scannable_fingerprint(
        const std::vector<uint8_t>& local_id,
        const secure::SecureBuffer& local_key,
        const std::vector<uint8_t>& remote_id,
        const secure::SecureBuffer& remote_key
    ) const;

    bool compare_scannable_fingerprint(
        const std::vector<uint8_t>& scannable,
        const std::vector<uint8_t>& local_id,
        const secure::SecureBuffer& local_key,
        const std::vector<uint8_t>& remote_id,
        const secure::SecureBuffer& remote_key
    ) const;

    std::string format_displayable(const std::vector<uint8_t>& fingerprint) const;

    std::string compute_displayable_fingerprint(
        const std::vector<uint8_t>& local_id,
        const secure::SecureBuffer& local_key,
        const std::vector<uint8_t>& remote_id,
        const secure::SecureBuffer& remote_key
    ) const;

private:
    uint32_t iterations_;
};

} // namespace protocol
} // namespace enchant

#endif
