#ifndef ENCHANT_ZK_POKSHO_SHO_HPP
#define ENCHANT_ZK_POKSHO_SHO_HPP

#include <cstdint>
#include <vector>

namespace enchant {
namespace zk {
namespace enchant_zkp {

class ShoApi {
public:
    virtual ~ShoApi() = default;

    virtual void absorb(const uint8_t* input, size_t len) = 0;
    virtual void ratchet() = 0;
    virtual void squeeze_and_ratchet_into(uint8_t* target, size_t outlen) = 0;

    void absorb_and_ratchet(const uint8_t* input, size_t len) {
        absorb(input, len);
        ratchet();
    }

    std::vector<uint8_t> squeeze_and_ratchet(size_t outlen) {
        std::vector<uint8_t> out(outlen);
        squeeze_and_ratchet_into(out.data(), outlen);
        return out;
    }

    std::array<uint8_t, 32> squeeze_and_ratchet_32() {
        std::array<uint8_t, 32> out;
        squeeze_and_ratchet_into(out.data(), 32);
        return out;
    }

    std::array<uint8_t, 64> squeeze_and_ratchet_64() {
        std::array<uint8_t, 64> out;
        squeeze_and_ratchet_into(out.data(), 64);
        return out;
    }
};

} // namespace enchant_zkp
} // namespace zk
} // namespace enchant

#endif
