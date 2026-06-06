#include "proto/buffer.hpp"
#include "proto/varint.hpp"
#include <cstring>

namespace enchant {
namespace proto {

Buffer::Buffer() : buf_() {}
Buffer::Buffer(size_t capacity) : buf_() { buf_.reserve(capacity); }

void Buffer::write_uint64(uint64_t value) {
    uint8_t tmp[10];
    size_t len = sizeof(tmp);
    encode_varint(value, tmp, &len);
    buf_.insert(buf_.end(), tmp, tmp + len);
}

void Buffer::write_uint32(uint32_t value) {
    write_uint64(value);
}

void Buffer::write_bytes(const uint8_t* data, size_t len) {
    write_uint64(len);
    buf_.insert(buf_.end(), data, data + len);
}

void Buffer::write_string(const std::string& str) {
    write_bytes(reinterpret_cast<const uint8_t*>(str.data()), str.size());
}

int Buffer::read_uint64(const uint8_t* data, size_t len, uint64_t* value, size_t* pos) {
    if (*pos >= len) return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    size_t consumed;
    int rc = decode_varint(data + *pos, len - *pos, value, &consumed);
    *pos += consumed;
    return rc;
}

int Buffer::read_uint32(const uint8_t* data, size_t len, uint32_t* value, size_t* pos) {
    if (*pos >= len) return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    uint64_t v;
    size_t consumed;
    int rc = decode_varint(data + *pos, len - *pos, &v, &consumed);
    *pos += consumed;
    *value = static_cast<uint32_t>(v);
    return rc;
}

int Buffer::read_bytes(const uint8_t* data, size_t len, uint8_t* out, size_t out_len, size_t* pos) {
    if (*pos >= len) return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    uint64_t bytes_len;
    size_t consumed;
    int rc = decode_varint(data + *pos, len - *pos, &bytes_len, &consumed);
    if (rc != 0) return rc;
    *pos += consumed;
    if (bytes_len > out_len || *pos + bytes_len > len) return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    memcpy(out, data + *pos, bytes_len);
    *pos += bytes_len;
    return 0;
}

int Buffer::read_string(const uint8_t* data, size_t len, std::string& out, size_t* pos) {
    if (*pos >= len) return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    uint64_t str_len;
    size_t consumed;
    int rc = decode_varint(data + *pos, len - *pos, &str_len, &consumed);
    if (rc != 0) return rc;
    *pos += consumed;
    if (*pos + str_len > len) return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    out.assign(reinterpret_cast<const char*>(data + *pos), str_len);
    *pos += str_len;
    return 0;
}

} // namespace proto
} // namespace enchant