#ifndef ENCHANT_TYPES_H
#define ENCHANT_TYPES_H

#include <stdint.h>
#include <stddef.h>
#include "enchant/error.h"

#ifdef __cplusplus
namespace enchant {

template<typename T>
struct Result {
    int error_code;
    T value;

    static Result ok(T val) {
        return Result{ENCHANT_SUCCESS, std::move(val)};
    }

    static Result err(int code) {
        return Result{code, T{}};
    }

    bool is_ok() const { return error_code == ENCHANT_SUCCESS; }
    bool is_err() const { return error_code != ENCHANT_SUCCESS; }
};

using Status = Result<void>;

} // namespace enchant
#endif

#endif