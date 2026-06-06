#ifndef ENCHANT_PQ_KYBER_HPP
#define ENCHANT_PQ_KYBER_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"
#include "pq/ml_kem.hpp"

namespace enchant {
namespace pq {

constexpr size_t KYBER_PUBLIC_KEY_SIZE = ML_KEM_768_PUBLIC_KEY_SIZE;
constexpr size_t KYBER_SECRET_KEY_SIZE = ML_KEM_768_SECRET_KEY_SIZE;
constexpr size_t KYBER_CIPHERTEXT_SIZE = ML_KEM_768_CIPHERTEXT_SIZE;
constexpr size_t KYBER_SHARED_SECRET_SIZE = ML_KEM_768_SHARED_SECRET_SIZE;

inline int kyber_keypair(uint8_t* public_key, uint8_t* secret_key) {
    return ml_kem_768_keypair(public_key, secret_key);
}

inline int kyber_encapsulate(uint8_t* ciphertext, uint8_t* shared_secret,
                              const uint8_t* public_key) {
    return ml_kem_768_encapsulate(ciphertext, shared_secret, public_key);
}

inline int kyber_decapsulate(uint8_t* shared_secret,
                              const uint8_t* ciphertext,
                              const uint8_t* secret_key) {
    return ml_kem_768_decapsulate(shared_secret, ciphertext, secret_key);
}

inline bool kyber_available() {
    return ml_kem_768_available();
}

} // namespace pq
} // namespace enchant

#endif
