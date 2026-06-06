#ifndef ENCHANT_PROTO_BUFFER_HPP
#define ENCHANT_PROTO_BUFFER_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <string>

namespace enchant {
namespace proto {

class Buffer {
public:
    Buffer();
    explicit Buffer(size_t capacity);

    void write_uint64(uint64_t value);
    void write_uint32(uint32_t value);
    void write_bytes(const uint8_t* data, size_t len);
    void write_string(const std::string& str);

    int read_uint64(const uint8_t* data, size_t len, uint64_t* value, size_t* pos);
    int read_uint32(const uint8_t* data, size_t len, uint32_t* value, size_t* pos);
    int read_bytes(const uint8_t* data, size_t len, uint8_t* out, size_t out_len, size_t* pos);
    int read_string(const uint8_t* data, size_t len, std::string& out, size_t* pos);

    const std::vector<uint8_t>& data() const { return buf_; }
    size_t size() const { return buf_.size(); }
    void clear() { buf_.clear(); }

private:
    std::vector<uint8_t> buf_;
};

} // namespace proto
} // namespace enchant

#endif