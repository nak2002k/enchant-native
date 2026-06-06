#ifndef ENCHANT_PQ_ML_KEM_HPP
#define ENCHANT_PQ_ML_KEM_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace pq {

// ML-KEM-768 (FIPS 203, Category 3 security)
constexpr size_t ML_KEM_768_PUBLIC_KEY_SIZE = 1184;
constexpr size_t ML_KEM_768_SECRET_KEY_SIZE = 2400;
constexpr size_t ML_KEM_768_CIPHERTEXT_SIZE = 1088;
constexpr size_t ML_KEM_768_SHARED_SECRET_SIZE = 32;
constexpr size_t ML_KEM_768_SEED_SIZE = 64;

// ML-KEM-1024 (FIPS 203, Category 5 security)
constexpr size_t ML_KEM_1024_PUBLIC_KEY_SIZE = 1568;
constexpr size_t ML_KEM_1024_SECRET_KEY_SIZE = 3168;
constexpr size_t ML_KEM_1024_CIPHERTEXT_SIZE = 1568;
constexpr size_t ML_KEM_1024_SHARED_SECRET_SIZE = 32;
constexpr size_t ML_KEM_1024_SEED_SIZE = 64;

int ml_kem_768_keypair(uint8_t* public_key, uint8_t* secret_key);

int ml_kem_768_encapsulate(uint8_t* ciphertext, uint8_t* shared_secret,
                            const uint8_t* public_key);

int ml_kem_768_decapsulate(uint8_t* shared_secret,
                            const uint8_t* ciphertext,
                            const uint8_t* secret_key);

int ml_kem_1024_keypair(uint8_t* public_key, uint8_t* secret_key);

int ml_kem_1024_encapsulate(uint8_t* ciphertext, uint8_t* shared_secret,
                             const uint8_t* public_key);

int ml_kem_1024_decapsulate(uint8_t* shared_secret,
                             const uint8_t* ciphertext,
                             const uint8_t* secret_key);

bool ml_kem_768_available();

bool ml_kem_1024_available();

} // namespace pq
} // namespace enchant

#endif
