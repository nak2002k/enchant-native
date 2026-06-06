#ifndef ENCHANT_VERSION_H
#define ENCHANT_VERSION_H

#define ENCHANT_VERSION_MAJOR 0
#define ENCHANT_VERSION_MINOR 1
#define ENCHANT_VERSION_PATCH 0
#define ENCHANT_VERSION_STRING "0.1.0"

#ifdef __cplusplus
namespace enchant {

inline const char* version_string() {
    return ENCHANT_VERSION_STRING;
}

inline int version_major() {
    return ENCHANT_VERSION_MAJOR;
}

inline int version_minor() {
    return ENCHANT_VERSION_MINOR;
}

inline int version_patch() {
    return ENCHANT_VERSION_PATCH;
}

} // namespace enchant
#endif

#endif