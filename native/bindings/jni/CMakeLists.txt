cmake_minimum_required(VERSION 3.20)

# Build JNI shared library.
# Links against the prebuilt libenchantcrypto.so for base crypto functions.
# The JNI .so provides Java_* bridge functions only.

add_library(enchantcrypto_jni SHARED
    enchant_jni.cpp
    enchant_new_features_jni.cpp
    enchant_base_jni.cpp
)

if(ANDROID)
    set(ENCHANT_PREBUILT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../prebuilt/${ANDROID_ABI}")
endif()

if(EXISTS "${ENCHANT_PREBUILT_DIR}/libenchantcrypto.so")
    message(STATUS "Linking JNI against prebuilt: ${ENCHANT_PREBUILT_DIR}/libenchantcrypto.so")
    add_library(enchantcrypto_prebuilt SHARED IMPORTED)
    set_target_properties(enchantcrypto_prebuilt PROPERTIES
        IMPORTED_LOCATION "${ENCHANT_PREBUILT_DIR}/libenchantcrypto.so"
    )
    target_link_libraries(enchantcrypto_jni PRIVATE enchantcrypto_prebuilt sodium)
else()
    message(STATUS "No prebuilt found, using static enchantcrypto + deps")
    target_link_libraries(enchantcrypto_jni PRIVATE enchantcrypto sodium crypto)
endif()

target_include_directories(enchantcrypto_jni
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/../../include
        ${CMAKE_CURRENT_SOURCE_DIR}/../../src
        ${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/boringssl/include
        ${LIBSODIUM_INCLUDE_DIRS}
)

set_target_properties(enchantcrypto_jni PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)
