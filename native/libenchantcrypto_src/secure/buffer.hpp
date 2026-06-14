#ifndef ENCHANT_SECURE_BUFFER_HPP
#define ENCHANT_SECURE_BUFFER_HPP

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <sodium.h>
#include <mutex>
#include <memory>

namespace enchant {
namespace secure {

class SecureBuffer {
public:
    SecureBuffer();
    explicit SecureBuffer(size_t size);

    SecureBuffer(const uint8_t* data, size_t size);

    ~SecureBuffer();

    SecureBuffer(const SecureBuffer&) = delete;
    SecureBuffer& operator=(const SecureBuffer&) = delete;

    SecureBuffer(SecureBuffer&& other) noexcept;
    SecureBuffer& operator=(SecureBuffer&& other) noexcept;

    SecureBuffer clone() const;

    void assign(const uint8_t* data, size_t size);
    void resize(size_t new_size);
    void clear();

    uint8_t* data() noexcept;
    const uint8_t* data() const noexcept;

    size_t size() const noexcept;

    void zero() noexcept;

    bool empty() const noexcept;

private:
    uint8_t* data_;
    size_t size_;
};

template<typename T>
class ScopedLock {
public:
    explicit ScopedLock(T& mutex) : lock_(mutex), mutex_(mutex) {}
    ~ScopedLock() = default;

    ScopedLock(const ScopedLock&) = delete;
    ScopedLock& operator=(const ScopedLock&) = delete;

private:
    std::lock_guard<T> lock_;
    T& mutex_;
};

class SecureZero {
public:
    static void zero(void* ptr, size_t len) {
        sodium_memzero(ptr, len);
    }

    static bool constant_time_compare(const void* a, const void* b, size_t len) {
        return sodium_memcmp(a, b, len) == 0;
    }
};

} // namespace secure

inline bool constant_time_compare(const uint8_t* a, const uint8_t* b, size_t len) {
    return sodium_memcmp(a, b, len) == 0;
}

} // namespace enchant

#endif