// Stub implementations for AES/HPKE when BoringSSL is not available.
// Kotlin falls back to JCA for AES-GCM when these return -1.

#include "primitives/aes.hpp"
#include <cstring>

namespace enchant {
namespace primitives {

int aes_init(void) { return 0; }
int aes_is_available(void) { return 0; }
int aes_128_keygen(uint8_t*) { return -1; }
int aes_192_keygen(uint8_t*) { return -1; }
int aes_256_keygen(uint8_t*) { return -1; }
int aes_256_gcm_encrypt(const uint8_t*, const uint8_t*, const uint8_t*, size_t, const uint8_t*, size_t, uint8_t*, size_t*) { return -1; }
int aes_256_gcm_decrypt(const uint8_t*, const uint8_t*, const uint8_t*, size_t, const uint8_t*, size_t, uint8_t*, size_t*) { return -1; }
int aes_256_ctr_encrypt(const uint8_t*, const uint8_t*, const uint8_t*, size_t, uint8_t*) { return -1; }
int aes_256_ctr_decrypt(const uint8_t*, const uint8_t*, const uint8_t*, size_t, uint8_t*) { return -1; }
int aes_256_cbc_encrypt(const uint8_t*, const uint8_t*, const uint8_t*, size_t, uint8_t*, size_t*) { return -1; }
int aes_256_cbc_decrypt(const uint8_t*, const uint8_t*, const uint8_t*, size_t, uint8_t*, size_t*) { return -1; }

} // namespace primitives
} // namespace enchant

namespace enchant {
namespace primitives {

int hpke_seal(const uint8_t*, const uint8_t*, size_t, const uint8_t*, size_t, const uint8_t*, size_t, uint8_t*, size_t*) { return -1; }
int hpke_open(const uint8_t*, const uint8_t*, size_t, const uint8_t*, size_t, const uint8_t*, size_t, uint8_t*, size_t*) { return -1; }

} // namespace primitives
} // namespace enchant
