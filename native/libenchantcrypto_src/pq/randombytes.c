#include "randombytes.h"
#include <sodium.h>

int randombytes(uint8_t *obuf, size_t len) {
    randombytes_buf(obuf, len);
    return 0;
}
