#include "secure/buffer.hpp"
#include <new>

namespace enchant {
namespace secure {

SecureBuffer::SecureBuffer()
    : data_(nullptr), size_(0) {
}

SecureBuffer::SecureBuffer(size_t size)
    : data_(nullptr), size_(size) {
    if (size_ > 0) {
        data_ = static_cast<uint8_t*>(sodium_malloc(size_));
        if (!data_) {
            throw std::bad_alloc();
        }
        sodium_memzero(data_, size_);
    }
}

SecureBuffer::SecureBuffer(const uint8_t* data, size_t size)
    : data_(nullptr), size_(size) {
    if (size_ > 0) {
        data_ = static_cast<uint8_t*>(sodium_malloc(size_));
        if (!data_) {
            throw std::bad_alloc();
        }
        if (data) {
            memcpy(data_, data, size_);
        } else {
            sodium_memzero(data_, size_);
        }
    }
}

SecureBuffer::~SecureBuffer() {
    if (data_) {
        sodium_memzero(data_, size_);
        sodium_free(data_);
        data_ = nullptr;
        size_ = 0;
    }
}

SecureBuffer::SecureBuffer(SecureBuffer&& other) noexcept
    : data_(other.data_), size_(other.size_) {
    other.data_ = nullptr;
    other.size_ = 0;
}

SecureBuffer& SecureBuffer::operator=(SecureBuffer&& other) noexcept {
    if (this != &other) {
        if (data_) {
            sodium_memzero(data_, size_);
            sodium_free(data_);
        }
        data_ = other.data_;
        size_ = other.size_;
        other.data_ = nullptr;
        other.size_ = 0;
    }
    return *this;
}

SecureBuffer SecureBuffer::clone() const {
    if (size_ == 0) return SecureBuffer();
    if (!data_) return SecureBuffer(size_);
    return SecureBuffer(data_, size_);
}

void SecureBuffer::assign(const uint8_t* data, size_t size) {
    if (data_) {
        sodium_memzero(data_, size_);
        sodium_free(data_);
        data_ = nullptr;
        size_ = 0;
    }
    if (size == 0) {
        return;
    }
    data_ = static_cast<uint8_t*>(sodium_malloc(size));
    if (!data_) {
        throw std::bad_alloc();
    }
    if (data) {
        memcpy(data_, data, size);
    } else {
        sodium_memzero(data_, size);
    }
    size_ = size;
}

void SecureBuffer::resize(size_t new_size) {
    if (new_size == size_) {
        return;
    }
    if (data_) {
        sodium_memzero(data_, size_);
        sodium_free(data_);
        data_ = nullptr;
        size_ = 0;
    }
    if (new_size > 0) {
        data_ = static_cast<uint8_t*>(sodium_malloc(new_size));
        if (!data_) {
            throw std::bad_alloc();
        }
        sodium_memzero(data_, new_size);
    }
    size_ = new_size;
}

void SecureBuffer::clear() {
    if (data_) {
        sodium_memzero(data_, size_);
        sodium_free(data_);
        data_ = nullptr;
    }
    size_ = 0;
}

uint8_t* SecureBuffer::data() noexcept {
    return data_;
}

const uint8_t* SecureBuffer::data() const noexcept {
    return data_;
}

size_t SecureBuffer::size() const noexcept {
    return size_;
}

void SecureBuffer::zero() noexcept {
    if (data_) {
        sodium_memzero(data_, size_);
    }
}

bool SecureBuffer::empty() const noexcept {
    return size_ == 0;
}

} // namespace secure
} // namespace enchant